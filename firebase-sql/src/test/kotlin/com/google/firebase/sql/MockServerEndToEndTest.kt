/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.sql

import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.GetTokenResult
import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests the whole SDK against a [MockWebServer]. Each request goes from the public API through
 * [FirebaseSqlComponent], the real `OperationsExecutor`, and the real `OkHttpNetworkClient`.
 *
 * The other unit tests check each layer alone, with fakes. These tests check that the layers agree,
 * for example that `maybeSingle()` finds the row count in the error details that
 * `OkHttpNetworkClient` reads from the response.
 *
 * Robolectric provides the Android `Context` that [FirebaseApp] needs.
 */
@RunWith(RobolectricTestRunner::class)
class MockServerEndToEndTest {

  /** Started before each test and shut down after it. */
  @get:Rule val server = MockWebServer()

  private lateinit var component: FirebaseSqlComponent

  @Before
  fun setUp() {
    FirebaseApp.clearInstancesForTest()
    val options =
      FirebaseOptions.Builder()
        .setProjectId(PROJECT_ID)
        .setApplicationId(APP_ID)
        .setApiKey("fake-api-key")
        .build()
    val app =
      FirebaseApp.initializeApp(InstrumentationRegistry.getInstrumentation().targetContext, options)
    val auth = authProvider(Tasks.forResult(GetTokenResult("auth-token", mutableMapOf())))
    val appCheck = appCheckProvider(Tasks.forResult(appCheckResult("app-check-token", null)))
    component =
      FirebaseSqlComponent(
        app,
        deferred(auth),
        deferred(appCheck),
        Dispatchers.IO,
        Dispatchers.Default
      )
  }

  @After
  fun tearDown() {
    FirebaseApp.clearInstancesForTest()
  }

  @Test
  fun insertSendsTheBodyAndTheHeadersOfEveryLayerAndDecodesTheRows() =
    runBlocking<Unit> {
      server.enqueue(
        MockResponse().setStatus("HTTP/1.1 201 Created").setBody("""[{"id":1,"name":"Apple"}]""")
      )

      val result =
        sql(schemaName = "inventory")
          .from("products")
          .insert(mapOf("id" to 1, "name" to "Apple"))
          .select()
          .execute()

      val request = server.takeRequestOrFail()
      assertThat(request.method).isEqualTo("POST")
      assertThat(request.path).isEqualTo("$SERVICE_PATH/products?select=*")
      assertThat(request.body.readUtf8()).isEqualTo("""{"id":1,"name":"Apple"}""")
      // The builder sets Prefer, OperationsExecutor adds the other headers, and
      // OkHttpNetworkClient sends the Content-Type of OperationsExecutor without a charset.
      assertThat(request.getHeader("Prefer")).isEqualTo("return=representation")
      assertThat(request.headers.values("Content-Type")).containsExactly("application/json")
      assertThat(request.getHeader("Content-Profile")).isEqualTo("inventory")
      assertThat(request.getHeader("Accept-Profile")).isNull()
      assertThat(request.getHeader("x-goog-api-client"))
        .endsWith(" fire/${BuildConfig.VERSION_NAME}")
      assertThat(request.getHeader("x-client-version"))
        .isEqualTo("android/${BuildConfig.VERSION_NAME}")
      // The component passes the app ID of the FirebaseApp.
      assertThat(request.getHeader("x-firebase-gmpid")).isEqualTo(APP_ID)
      assertThat(request.getHeader("x-firebase-auth-token")).isEqualTo("auth-token")
      assertThat(request.getHeader("X-Firebase-AppCheck")).isEqualTo("app-check-token")
      assertThat(result.status).isEqualTo(201)
      assertThat(result.decodeList<Product>()).containsExactly(Product(1, "Apple"))
    }

  @Test
  fun aReadIsRetriedAfterA503ByTheSdkAlone() =
    runBlocking<Unit> {
      // PostgREST answers 503 with Retry-After: 0 while it loads its schema cache. OkHttp would
      // send the request again at once by itself, without the X-Retry-Count header, but the SDK
      // ignores the header, waits about 1 second, and sends the retry with its number.
      server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
      server.enqueue(MockResponse().setBody("""[{"id":1}]"""))

      val result = sql().from("products").select().execute()

      assertThat(server.takeRequestOrFail().getHeader("X-Retry-Count")).isNull()
      assertThat(server.takeRequestOrFail().getHeader("X-Retry-Count")).isEqualTo("1")
      assertThat(server.requestCount).isEqualTo(2)
      assertThat(result.data).isEqualTo("""[{"id":1}]""")
    }

  @Test
  fun theContentRangeHeaderBecomesTheCountOfTheResult() =
    runBlocking<Unit> {
      server.enqueue(
        MockResponse()
          .setStatus("HTTP/1.1 206 Partial Content")
          .setHeader("Content-Range", "0-0/5")
          .setBody("""[{"id":1}]""")
      )

      val result = sql().from("products").select(count = Count.EXACT).limit(1).execute()

      assertThat(server.takeRequestOrFail().getHeader("Prefer")).isEqualTo("count=exact")
      assertThat(result.status).isEqualTo(206)
      assertThat(result.data).isEqualTo("""[{"id":1}]""")
      assertThat(result.countOrNull()).isEqualTo(5L)
      assertThat(result.rangeOrNull()).isEqualTo(0L..0L)
    }

  @Test
  fun maybeSingleOnAMutationTurnsTheNoRowsErrorIntoNull() =
    runBlocking<Unit> {
      // For a mutation, maybeSingle() requests an object, and PostgREST answers 406 when the
      // mutation does not affect exactly one row. Only the error for 0 rows means "no row".
      server.enqueue(notSingular("The result contains 0 rows"))

      val result =
        sql()
          .from("products")
          .update(mapOf("name" to "Kiwi"))
          .eq("id", 1)
          .select()
          .maybeSingle()
          .execute()

      assertThat(server.takeRequestOrFail().getHeader("Accept"))
        .isEqualTo("application/vnd.pgrst.object+json")
      assertThat(result.data).isEqualTo("null")
    }

  @Test
  fun dryRunDetectsAWriteThatTheServerCommitted() =
    runBlocking<Unit> {
      // A server with db-tx-end = "commit" ignores tx=rollback and leaves it out of the header, so
      // the insert was committed.
      server.enqueue(
        MockResponse()
          .setStatus("HTTP/1.1 201 Created")
          .setHeader("Preference-Applied", "return=representation")
          .setBody("""[{"id":1}]""")
      )

      val error =
        assertThrows(FirebaseSqlException::class.java) {
          runBlocking {
            sql().from("products").insert(mapOf("id" to 1)).select().dryRun().execute()
          }
        }

      assertThat(server.takeRequestOrFail().getHeader("Prefer"))
        .isEqualTo("return=representation,tx=rollback")
      assertThat(error).hasMessageThat().contains("tx=rollback")
      assertThat(error.category).isEqualTo(FirebaseSqlExceptionCategory.CLIENT)
    }

  @Test
  fun aPostgrestErrorIsThrown() {
    server.enqueue(
      MockResponse()
        .setStatus("HTTP/1.1 404 Not Found")
        .setBody(
          // The body that the Firebase SQL service sends for a table that does not exist.
          """{"code":"PGRST205","details":null,""" +
            """"hint":"Perhaps you meant the table 'public.products'",""" +
            """"message":"Could not find the table 'public.product' in the schema cache"}"""
        )
    )
    val query = sql().from("product").select()

    val error = assertThrows(FirebaseSqlException::class.java) { runBlocking { query.execute() } }

    assertThat(error.status).isEqualTo(404)
    assertThat(error.code).isEqualTo("PGRST205")
    assertThat(error.category).isEqualTo(FirebaseSqlExceptionCategory.POSTGREST)
    assertThat(error)
      .hasMessageThat()
      .isEqualTo("Could not find the table 'public.product' in the schema cache")
    assertThat(error.details).isNull()
    assertThat(error.hint).isEqualTo("Perhaps you meant the table 'public.products'")
  }

  @Test
  fun anErrorWithAnEmptyMessageIsThrownWithTheStatusAndTheBody() {
    // An exception cannot have an empty message, yet PostgREST sends one for a function that runs
    // RAISE EXCEPTION USING MESSAGE = ''. Building the error used to throw on a thread of OkHttp,
    // which crashes the app, and the request then waited for its timeout of 100 seconds.
    val body = """{"code":"P0001","details":null,"hint":null,"message":""}"""
    server.enqueue(MockResponse().setStatus("HTTP/1.1 400 Bad Request").setBody(body))
    val call = sql().rpc("fail_without_message")

    val error =
      assertThrows(FirebaseSqlException::class.java) {
        runBlocking { withTimeout(10.seconds) { call.execute() } }
      }

    assertThat(error.status).isEqualTo(400)
    assertThat(error.code).isEqualTo("P0001")
    assertThat(error.category).isEqualTo(FirebaseSqlExceptionCategory.POSTGREST)
    assertThat(error).hasMessageThat().isEqualTo("HTTP 400: $body")
  }

  @Test
  fun aNetworkErrorIsThrownAsItIs() {
    // A port where nothing listens, so that the connection is refused.
    val unreachable = MockWebServer().apply { start() }
    val host = unreachable.hostName
    val port = unreachable.port
    unreachable.shutdown()
    // Without retries, so that the first attempt gives the error.
    val query = sql(host = host, port = port).from("products").select().noRetry()

    assertThrows(IOException::class.java) { runBlocking { query.execute() } }
  }

  @Test
  fun csvReturnsTheTextOfTheResponse() =
    runBlocking<Unit> {
      // A successful response that is not JSON is returned as it is; only decoding it fails.
      val csv = "id,name\n1,Apple\n2,\"Pear, green\"\n"
      server.enqueue(
        MockResponse().setHeader("Content-Type", "text/csv; charset=utf-8").setBody(csv)
      )

      val result = sql().from("products").select().csv().execute()

      assertThat(server.takeRequestOrFail().getHeader("Accept")).isEqualTo("text/csv")
      assertThat(result.status).isEqualTo(200)
      assertThat(result.data).isEqualTo(csv)
      assertThrows(SerializationException::class.java) { result.decodeList<Product>() }
    }

  @Test
  fun a404WithoutAnErrorBodyIsThrown() {
    // Neither an empty body nor an empty array turns a 404 into a success.
    for (body in listOf("", "[]")) {
      server.enqueue(MockResponse().setStatus("HTTP/1.1 404 Not Found").setBody(body))
      val query = sql().from("products").select()

      val error = assertThrows(FirebaseSqlException::class.java) { runBlocking { query.execute() } }

      assertWithMessage("body '$body'").that(error.status).isEqualTo(404)
      assertWithMessage("body '$body'")
        .that(error.category)
        .isEqualTo(FirebaseSqlExceptionCategory.PLATFORM)
    }
  }

  @Test
  fun aCountOfATableThatDoesNotExistIsThrown() {
    // The response to a HEAD request has no body, but its Proxy-Status header names the error.
    server.enqueue(
      MockResponse()
        .setStatus("HTTP/1.1 404 Not Found")
        .setHeader("Proxy-Status", "PostgREST; error=PGRST205")
    )
    val query = sql().from("product").select(head = true, count = Count.EXACT)

    val error = assertThrows(FirebaseSqlException::class.java) { runBlocking { query.execute() } }

    assertThat(server.takeRequestOrFail().method).isEqualTo("HEAD")
    assertThat(error.status).isEqualTo(404)
    assertThat(error.code).isEqualTo("PGRST205")
    assertThat(error.category).isEqualTo(FirebaseSqlExceptionCategory.POSTGREST)
  }

  @Test
  fun aReservedHeaderNameWithWhitespaceIsNeverSent() {
    // HTTP does not allow whitespace in a header name, so these names are not the reserved headers:
    // setHeader() and the settings accept them, and OkHttp then rejects the request before it is
    // sent. Either way, the server never gets a second credential.
    val names = listOf(" Authorization", "x-firebase-auth-token ", "\tAuthorization\n")
    val plain = sql()
    for (name in names) {
      val query = plain.from("products").select().setHeader(name, "Bearer token")
      assertThrows(name, IllegalArgumentException::class.java) { runBlocking { query.execute() } }
    }
    // The instance keeps the settings that created it, so it is terminated before the next ones.
    runBlocking { plain.suspendingTerminate() }
    for (name in names) {
      val global = sql(headers = mapOf(name to "Bearer token"))
      val query = global.from("products").select()
      assertThrows(name, IllegalArgumentException::class.java) { runBlocking { query.execute() } }
      runBlocking { global.suspendingTerminate() }
    }

    assertThat(server.requestCount).isEqualTo(0)
  }

  @Test
  fun aGlobalPreferIsSentAfterThePreferencesOfTheRequest() =
    runBlocking<Unit> {
      // The preferences of setHeader() come first, then those that the SDK computes, then those of
      // the settings. PostgREST uses the first value of each preference.
      server.enqueue(MockResponse().setBody("[]"))

      sql(headers = mapOf("Prefer" to "timezone=UTC"))
        .from("products")
        .select(count = Count.EXACT)
        .setHeader("Prefer", "handling=lenient")
        .execute()

      assertThat(server.takeRequestOrFail().headers.values("Prefer"))
        .containsExactly("handling=lenient,count=exact,timezone=UTC")
    }

  /**
   * Returns the instance for the service of the test project on [server], or on [host] and [port],
   * with [headers].
   */
  private fun sql(
    schemaName: String? = null,
    host: String = server.hostName,
    port: Int = server.port,
    headers: Map<String, String> = emptyMap()
  ): FirebaseSql =
    component.get(
      SqlServiceSettings(
        serviceId = SERVICE_ID,
        location = LOCATION,
        schemaName = schemaName,
        headers = headers,
        host = host,
        port = port,
        sslEnabled = false
      )
    )

  /** Returns the `PGRST116` error that PostgREST sends when a result is not exactly one row. */
  private fun notSingular(details: String) =
    MockResponse()
      .setStatus("HTTP/1.1 406 Not Acceptable")
      .setBody(
        """{"code":"PGRST116","details":"$details","hint":null,""" +
          """"message":"Cannot coerce the result to a single JSON object"}"""
      )

  @Serializable data class Product(val id: Int, val name: String)

  private companion object {
    const val PROJECT_ID = "sql-test-project"
    const val APP_ID = "1:1234567890:android:321abc"
    const val LOCATION = "us-central1"
    const val SERVICE_ID = "my-service"
    const val SERVICE_PATH =
      "/v1/projects/$PROJECT_ID/locations/$LOCATION/services/$SERVICE_ID/postgrest"
  }
}
