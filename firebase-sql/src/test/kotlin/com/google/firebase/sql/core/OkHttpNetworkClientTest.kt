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

package com.google.firebase.sql.core

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.firebase.sql.FirebaseSqlException
import com.google.firebase.sql.FirebaseSqlExceptionCategory
import com.google.firebase.sql.takeRequestOrFail
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests the HTTP requests that [OkHttpNetworkClient] sends, and how it reads the status codes,
 * headers, and error bodies that PostgREST returns.
 *
 * Robolectric is needed because [FirebaseSqlException] is a `FirebaseException`, whose constructor
 * checks its message with `android.text.TextUtils`.
 */
@RunWith(RobolectricTestRunner::class)
class OkHttpNetworkClientTest {

  /** Started before each test and shut down after it. */
  @get:Rule val server = MockWebServer()

  private val client = OkHttpNetworkClient()

  // --- requests ---

  @Test
  fun sendsTheMethodThePathAndTheHeaders() {
    val (_, recorded) =
      send(
        headers = mapOf("Accept" to "application/vnd.pgrst.object+json", "Prefer" to "count=exact")
      )

    assertThat(recorded.method).isEqualTo("GET")
    assertThat(recorded.requestUrl!!.encodedPath).isEqualTo("/v1/services/default/products")
    assertThat(recorded.getHeader("Accept")).isEqualTo("application/vnd.pgrst.object+json")
    assertThat(recorded.getHeader("Prefer")).isEqualTo("count=exact")
  }

  @Test
  fun percentEncodesQueryParameters() {
    // PostgREST decodes `+` in the query string as a space, so a literal `+` must be sent as `%2B`.
    val value = "eq.a+b c&d=e%f#g"
    val orValue = "(name.eq.\"Doe, John\",id.in.(1,2))"

    val (_, recorded) = send(queryParams = listOf("name" to value, "or" to orValue))

    assertThat(recorded.requestUrl!!.queryParameter("name")).isEqualTo(value)
    assertThat(recorded.requestUrl!!.queryParameter("or")).isEqualTo(orValue)
    assertThat(recorded.requestUrl!!.encodedQuery).startsWith("name=eq.a%2Bb%20c%26d%3De%25f%23g&")
  }

  @Test
  fun keepsTheOrderOfRepeatedQueryParameters() {
    val (_, recorded) = send(queryParams = listOf("id" to "gt.1", "id" to "lt.5", "select" to "id"))

    assertThat(recorded.requestUrl!!.encodedQuery).isEqualTo("id=gt.1&id=lt.5&select=id")
  }

  @Test
  fun percentEncodesSemicolonsInQueryParameters() {
    // The backend splits the query string on `;` as well as on `&`.
    val (_, recorded) = send(queryParams = listOf("name" to "eq.a;b", "or" to "(id.eq.1;x)"))

    assertThat(recorded.requestUrl!!.encodedQuery).doesNotContain(";")
    assertThat(recorded.requestUrl!!.queryParameter("name")).isEqualTo("eq.a;b")
    assertThat(recorded.requestUrl!!.queryParameter("or")).isEqualTo("(id.eq.1;x)")
  }

  @Test
  fun sendsJsonElementBodiesAsJsonText() {
    val body = buildJsonObject {
      put("id", 1)
      put("name", "Apple")
    }

    val (_, recorded) = send(method = "POST", body = body)

    assertThat(recorded.body.readUtf8()).isEqualTo("""{"id":1,"name":"Apple"}""")
    assertThat(recorded.getHeader("Content-Type")).isEqualTo("application/json; charset=utf-8")
  }

  @Test
  fun sendsStringBodiesAsIs() {
    val (_, recorded) = send(method = "PATCH", body = """{"name": "Pear"}""")

    assertThat(recorded.body.readUtf8()).isEqualTo("""{"name": "Pear"}""")
  }

  @Test
  fun sendsByteArrayBodiesAsUtf8Text() {
    val (_, recorded) = send(method = "POST", body = """{"name":"Café"}""".toByteArray())

    assertThat(recorded.body.readUtf8()).isEqualTo("""{"name":"Café"}""")
  }

  @Test
  fun encodesOtherBodiesAsJson() {
    val body = mapOf("id" to 1, "tags" to listOf("a", "b"), "meta" to mapOf("k" to null))

    val (_, recorded) = send(method = "POST", body = body)

    assertThat(recorded.body.readUtf8())
      .isEqualTo("""{"id":1,"tags":["a","b"],"meta":{"k":null}}""")
  }

  @Test
  fun keepsTheContentTypeOfTheRequest() {
    // PostgREST reads a `text/csv` body as CSV rows.
    val (_, recorded) =
      send(
        method = "POST",
        headers = mapOf("content-type" to "text/csv"),
        body = "id,name\n1,Apple"
      )

    assertThat(recorded.headers.values("Content-Type")).containsExactly("text/csv")
    assertThat(recorded.body.readUtf8()).isEqualTo("id,name\n1,Apple")
  }

  @Test
  fun sendsAnEmptyBodyForAPostWithoutBody() {
    // An RPC without arguments is a POST without a body, which PostgREST accepts.
    val (_, recorded) = send(method = "POST")

    assertThat(recorded.method).isEqualTo("POST")
    assertThat(recorded.bodySize).isEqualTo(0)
  }

  @Test
  fun sendsNoBodyForGetAndHead() {
    for (method in listOf("GET", "HEAD")) {
      val (_, recorded) = send(method = method, response = MockResponse())

      assertThat(recorded.method).isEqualTo(method)
      assertThat(recorded.bodySize).isEqualTo(0)
      assertThat(recorded.getHeader("Content-Length")).isNull()
    }
  }

  @Test
  fun sendsAnEmptyBodyForADelete() {
    // The empty body is one-shot, like the bodies of the other requests that change data.
    val (_, recorded) =
      send(
        method = "DELETE",
        headers = mapOf("Content-Type" to "application/json"),
        response = MockResponse()
      )

    assertThat(recorded.bodySize).isEqualTo(0)
    assertThat(recorded.getHeader("Content-Length")).isEqualTo("0")
    assertThat(recorded.headers.values("Content-Type")).containsExactly("application/json")
  }

  @Test
  fun okHttpDoesNotSendARequestThatChangesDataAgain() =
    runBlocking<Unit> {
      // OkHttp sends a request again by itself after a 408, unless its body is one-shot. A request
      // that changes data may have run, so it is sent only once, while a GET, which changes
      // nothing, is sent twice.
      server.dispatcher =
        object : Dispatcher() {
          override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(408)
        }
      for (method in listOf("GET", "POST", "PUT", "PATCH", "DELETE")) {
        val before = server.requestCount

        val response =
          client.sendRequest(
            server.url("/"),
            QueryRequest(method, listOf("products"), emptyList(), emptyMap())
          )

        assertWithMessage(method).that(response.status).isEqualTo(408)
        assertWithMessage(method)
          .that(server.requestCount - before)
          .isEqualTo(if (method == "GET") 2 else 1)
      }
    }

  @Test
  fun okHttpDoesNotSendARequestAgainAfterA503WithRetryAfterZero() =
    runBlocking<Unit> {
      // PostgREST sets Retry-After to 0 on every 503, after which OkHttp would send a GET or HEAD
      // again at once by itself, without the wait and the X-Retry-Count header of the retries of
      // the SDK.
      server.dispatcher =
        object : Dispatcher() {
          override fun dispatch(request: RecordedRequest) =
            MockResponse().setResponseCode(503).setHeader("Retry-After", "0")
        }
      for (method in listOf("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE")) {
        val before = server.requestCount

        val response =
          client.sendRequest(
            server.url("/"),
            QueryRequest(method, listOf("products"), emptyList(), emptyMap())
          )

        assertWithMessage(method).that(response.status).isEqualTo(503)
        assertWithMessage(method).that(server.requestCount - before).isEqualTo(1)
      }
    }

  @Test
  fun percentEncodesEachPathSegment() = runBlocking {
    // `rpc/x` as a table name must stay one segment instead of calling the function `x`.
    val specialNames = listOf("my table", "a?b=1", "a#b", "../products", "a%20b", "rpc/x")
    for (name in specialNames) {
      server.enqueue(MockResponse().setBody("[]"))
      client.sendRequest(
        server.url("/v1/services/default/"),
        QueryRequest("GET", listOf(name), listOf("select" to "*"), emptyMap())
      )
      val tableReq = server.takeRequestOrFail()
      assertThat(tableReq.requestUrl!!.pathSegments)
        .containsExactly("v1", "services", "default", name)
        .inOrder()
      assertThat(tableReq.requestUrl!!.queryParameterNames).containsExactly("select")

      server.enqueue(MockResponse().setBody("[]"))
      client.sendRequest(
        server.url("/v1/services/default/"),
        QueryRequest("POST", listOf("rpc", name), emptyList(), emptyMap())
      )
      val rpcReq = server.takeRequestOrFail()
      assertThat(rpcReq.requestUrl!!.pathSegments)
        .containsExactly("v1", "services", "default", "rpc", name)
        .inOrder()
      assertThat(rpcReq.requestUrl!!.queryParameterNames).isEmpty()
    }
  }

  // --- responses ---

  @Test
  fun returnsTheStatusAndTheBodyOfASuccessfulResponse() {
    val (response, _) =
      send(response = MockResponse().setStatus("HTTP/1.1 201 Created").setBody("""[{"id":1}]"""))

    assertThat(response.status).isEqualTo(201)
    assertThat(response.statusText).isEqualTo("Created")
    assertThat(response.body).isEqualTo("""[{"id":1}]""")
    assertThat(response.count).isNull()
    assertThat(response.error).isNull()
  }

  @Test
  fun returnsAnEmptyBodyForANoContentResponse() {
    val (response, _) =
      send(
        method = "PATCH",
        body = "{}",
        response = MockResponse().setStatus("HTTP/1.1 204 No Content")
      )

    assertThat(response.status).isEqualTo(204)
    assertThat(response.body).isEmpty()
    assertThat(response.error).isNull()
  }

  @Test
  fun readsTheCountFromTheContentRangeHeader() {
    // PostgREST sends the total after the slash, or `*` when it did not count the rows.
    val cases =
      listOf(
        "0-1/2" to 2L,
        "*/0" to 0L,
        "0-24/3573458" to 3573458L,
        "0-0/1" to 1L,
        "0-24/*" to null
      )
    for ((contentRange, count) in cases) {
      val (response, _) =
        send(
          headers = mapOf("prefer" to "count=exact"),
          response = MockResponse().setBody("[]").setHeader("Content-Range", contentRange)
        )

      assertThat(response.count).isEqualTo(count)
    }
  }

  @Test
  fun readsTheCountWhenTheRequestHasNoCountPreference() {
    // PostgREST only sends a total when a count was requested, for example with a custom header.
    val (response, _) =
      send(response = MockResponse().setBody("[]").setHeader("Content-Range", "0-1/2"))

    assertThat(response.count).isEqualTo(2L)
  }

  @Test
  fun readsThePositionsOfTheRowsFromContentRange() {
    val ranges =
      mapOf(
        "0-19/250" to 0L..19L,
        "20-39/*" to 20L..39L,
        "*/0" to null,
        "*/*" to null,
        "5-4/10" to null,
        "garbage" to null
      )
    for ((contentRange, range) in ranges) {
      val (response, _) =
        send(response = MockResponse().setBody("[]").setHeader("Content-Range", contentRange))

      assertWithMessage(contentRange).that(response.range).isEqualTo(range)
    }
    val (withoutHeader, _) = send(response = MockResponse().setBody("[]"))
    assertThat(withoutHeader.range).isNull()
  }

  @Test
  fun readsThePreferenceAppliedHeader() {
    val (response, _) =
      send(
        method = "POST",
        headers = mapOf("Prefer" to "return=representation,tx=rollback"),
        body = """{"id":1}""",
        response =
          MockResponse()
            .setStatus("HTTP/1.1 201 Created")
            .setHeader("Preference-Applied", "return=representation, tx=rollback")
            .setBody("""[{"id":1}]""")
      )

    assertThat(response.preferenceApplied).isEqualTo("return=representation, tx=rollback")
  }

  @Test
  fun readsAPostgrestErrorBody() {
    val (response, _) =
      send(
        response =
          MockResponse()
            .setStatus("HTTP/1.1 406 Not Acceptable")
            .setBody(
              """{"code":"PGRST116","details":"The result contains 0 rows","hint":null,""" +
                """"message":"Cannot coerce the result to a single JSON object"}"""
            )
      )

    assertThat(response.status).isEqualTo(406)
    assertThat(response.statusText).isEqualTo("Not Acceptable")
    assertThat(response.body).isNull()
    val error = response.error as FirebaseSqlException
    assertThat(error.status).isEqualTo(406)
    assertThat(error.code).isEqualTo("PGRST116")
    assertThat(error).hasMessageThat().isEqualTo("Cannot coerce the result to a single JSON object")
    assertThat(error.details).isEqualTo("The result contains 0 rows")
    assertThat(error.hint).isNull()
    assertThat(error.category).isEqualTo(FirebaseSqlExceptionCategory.POSTGREST)
  }

  @Test
  fun readsADatabaseErrorBodyWithAHint() {
    // Errors raised by PostgreSQL carry the SQLSTATE as the code.
    val (response, _) =
      send(
        response =
          MockResponse()
            .setStatus("HTTP/1.1 404 Not Found")
            .setBody(
              """{"code":"42883","details":null,"hint":"You might need to add explicit type""" +
                """ casts.","message":"operator does not exist: text > integer"}"""
            )
      )

    val error = response.error as FirebaseSqlException
    assertThat(error.status).isEqualTo(404)
    assertThat(error.code).isEqualTo("42883")
    assertThat(error).hasMessageThat().isEqualTo("operator does not exist: text > integer")
    assertThat(error.details).isNull()
    assertThat(error.hint).isEqualTo("You might need to add explicit type casts.")
    assertThat(error.category).isEqualTo(FirebaseSqlExceptionCategory.POSTGREST)
  }

  @Test
  fun readsAGoogleApiErrorBody() {
    // The Google API layer in front of PostgREST answers errors about tokens, App Check, quotas,
    // and disabled services in this format.
    val (response, _) =
      send(
        response =
          MockResponse()
            .setStatus("HTTP/1.1 403 Forbidden")
            .setBody(
              """{"error":{"code":403,"message":"Permission denied.",""" +
                """"status":"PERMISSION_DENIED"}}"""
            )
      )

    val error = response.error as FirebaseSqlException
    assertThat(error.status).isEqualTo(403)
    assertThat(error).hasMessageThat().isEqualTo("Permission denied.")
    assertThat(error.code).isEqualTo("PERMISSION_DENIED")
    assertThat(error.details).isNull()
    assertThat(error.hint).isNull()
    assertThat(error.category).isEqualTo(FirebaseSqlExceptionCategory.PLATFORM)
  }

  @Test
  fun readsTheDetailsOfAGoogleApiErrorAsJsonText() {
    val details =
      """[{"@type":"type.googleapis.com/google.rpc.ErrorInfo",""" +
        """"reason":"SERVICE_DISABLED"}]"""
    val (response, _) =
      send(
        response =
          MockResponse()
            .setStatus("HTTP/1.1 501 Not Implemented")
            .setBody(
              """{"error":{"code":501,"message":"PostgrestService is not enabled.",""" +
                """"status":"UNIMPLEMENTED","details":$details}}"""
            )
      )

    val error = response.error as FirebaseSqlException
    assertThat(error.status).isEqualTo(501)
    assertThat(error).hasMessageThat().isEqualTo("PostgrestService is not enabled.")
    assertThat(error.code).isEqualTo("UNIMPLEMENTED")
    assertThat(error.details).isEqualTo(details)
    assertThat(error.category).isEqualTo(FirebaseSqlExceptionCategory.PLATFORM)
  }

  @Test
  fun readsThePostgrestCodeOfAHeadErrorFromTheProxyStatusHeader() {
    // The server strips the body of the response to a HEAD request, but names the error code in
    // the Proxy-Status header.
    val (response, _) =
      send(
        method = "HEAD",
        response =
          MockResponse()
            .setStatus("HTTP/1.1 406 Not Acceptable")
            .setHeader("Proxy-Status", "PostgREST; error=PGRST116")
      )

    val error = response.error as FirebaseSqlException
    assertThat(error.status).isEqualTo(406)
    assertThat(error.code).isEqualTo("PGRST116")
    assertThat(error.category).isEqualTo(FirebaseSqlExceptionCategory.POSTGREST)
  }

  @Test
  fun usesTheStatusAndTheBodyAsTheMessageOfAnErrorWithoutMessage() {
    val body = """{"code":"XX000"}"""

    val (response, _) =
      send(response = MockResponse().setStatus("HTTP/1.1 500 Internal Server Error").setBody(body))

    val error = response.error as FirebaseSqlException
    assertThat(error).hasMessageThat().isEqualTo("HTTP 500: $body")
    assertThat(error.code).isEqualTo("XX000")
    // A code is enough to recognize a PostgREST error.
    assertThat(error.category).isEqualTo(FirebaseSqlExceptionCategory.POSTGREST)
  }

  @Test
  fun returnsAPlatformErrorForAJsonObjectThatIsNotAPostgrestError() {
    val body = """{"reason":"overloaded"}"""

    val (response, _) =
      send(response = MockResponse().setStatus("HTTP/1.1 503 Service Unavailable").setBody(body))

    val error = response.error as FirebaseSqlException
    assertThat(error).hasMessageThat().isEqualTo("HTTP 503: $body")
    assertThat(error.code).isNull()
    assertThat(error.category).isEqualTo(FirebaseSqlExceptionCategory.PLATFORM)
  }

  @Test
  fun returnsAnErrorWithoutCodeForABodyThatIsNotAJsonObject() {
    // A proxy or load balancer can answer with any body. Every HTTP error response gives an error
    // with its status, so that callers can always read it.
    for (body in listOf("<html>Bad Gateway</html>", "[1,2]", "")) {
      val (response, _) =
        send(response = MockResponse().setStatus("HTTP/1.1 502 Bad Gateway").setBody(body))

      assertThat(response.status).isEqualTo(502)
      assertThat(response.body).isNull()
      val error = response.error as FirebaseSqlException
      assertThat(error).hasMessageThat().isEqualTo("HTTP 502: $body")
      assertThat(error.status).isEqualTo(502)
      assertThat(error.code).isNull()
      assertThat(error.details).isNull()
      assertThat(error.hint).isNull()
      assertThat(error.category).isEqualTo(FirebaseSqlExceptionCategory.PLATFORM)
    }
  }

  @Test
  fun a404WithAnEmptyBodyOrAnEmptyArrayIsAnError() {
    // A 404 is an error even without an error body: an empty array is not a PostgREST error, and
    // an empty body is how the error of a HEAD request arrives. Neither becomes a success.
    for (body in listOf("", "[]")) {
      val (response, _) =
        send(response = MockResponse().setStatus("HTTP/1.1 404 Not Found").setBody(body))

      assertWithMessage("body '$body'").that(response.status).isEqualTo(404)
      assertWithMessage("body '$body'").that(response.body).isNull()
      val error = response.error as FirebaseSqlException
      assertWithMessage("body '$body'").that(error).hasMessageThat().isEqualTo("HTTP 404: $body")
      assertWithMessage("body '$body'").that(error.status).isEqualTo(404)
      assertWithMessage("body '$body'").that(error.code).isNull()
      assertWithMessage("body '$body'")
        .that(error.category)
        .isEqualTo(FirebaseSqlExceptionCategory.PLATFORM)
    }
  }

  @Test
  fun returnsATransportErrorWhenTheServerIsUnreachable() =
    runBlocking<Unit> {
      // A server of its own, so that shutting it down does not affect the server of the rule.
      val unreachable = MockWebServer().apply { start() }
      val url = unreachable.url("/")
      unreachable.shutdown()

      val response =
        client.sendRequest(url, QueryRequest("GET", listOf("products"), emptyList(), emptyMap()))

      assertThat(response.status).isEqualTo(0)
      assertThat(response.statusText).isEqualTo("Transport Error")
      assertThat(response.body).isNull()
      // The IOException of OkHttp, as it is.
      assertThat(response.error).isInstanceOf(IOException::class.java)
      assertThat(response.isTransportError).isTrue()
    }

  @Test
  fun cancellingTheCoroutineCancelsTheHttpCall() =
    runBlocking<Unit> {
      val canceled = CountDownLatch(1)
      val okHttpClient =
        OkHttpClient.Builder()
          .eventListener(
            object : EventListener() {
              override fun canceled(call: Call) = canceled.countDown()
            }
          )
          .build()
      // The server reads the request and never answers, so the call is still running when the
      // coroutine is cancelled.
      server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

      val response =
        withTimeoutOrNull(200) {
          OkHttpNetworkClient(okHttpClient)
            .sendRequest(
              server.url("/"),
              QueryRequest("GET", listOf("products"), emptyList(), emptyMap())
            )
        }

      assertThat(response).isNull()
      assertThat(canceled.await(5, TimeUnit.SECONDS)).isTrue()
    }

  /** Sends one request to [server] and returns the response together with the recorded request. */
  private fun send(
    method: String = "GET",
    queryParams: List<Pair<String, String>> = emptyList(),
    headers: Map<String, String> = emptyMap(),
    body: Any? = null,
    response: MockResponse = MockResponse().setBody("[]")
  ): Pair<NetworkResponse, RecordedRequest> = runBlocking {
    server.enqueue(response)
    val networkResponse =
      client.sendRequest(
        server.url("/v1/services/default/"),
        QueryRequest(method, listOf("products"), queryParams, headers, body)
      )
    networkResponse to server.takeRequestOrFail()
  }
}
