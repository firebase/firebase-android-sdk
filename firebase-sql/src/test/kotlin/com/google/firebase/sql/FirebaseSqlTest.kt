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
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.inject.Deferred
import com.google.firebase.platforminfo.UserAgentPublisher
import com.google.firebase.sql.builders.OperationBuilderImpl
import com.google.firebase.sql.core.QueryRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests how [FirebaseSql] instances are created, cached, validated, and terminated, and the URLs
 * that their requests go to.
 *
 * Robolectric provides the Android `Context` that [FirebaseApp] needs.
 */
@RunWith(RobolectricTestRunner::class)
class FirebaseSqlTest {

  private lateinit var app: FirebaseApp
  private lateinit var component: FirebaseSqlComponent

  @Before
  fun setUp() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    FirebaseApp.clearInstancesForTest()

    val options =
      FirebaseOptions.Builder()
        .setProjectId("sql-test-project")
        .setApplicationId("1:1234567890:android:321abc")
        .setApiKey("fake-api-key")
        .build()

    app = FirebaseApp.initializeApp(context, options)
    val dummyAuth = Deferred<InternalAuthProvider> { handler -> handler.handle { null } }
    val dummyAppCheck =
      Deferred<InteropAppCheckTokenProvider> { handler -> handler.handle { null } }

    component =
      FirebaseSqlComponent(app, dummyAuth, dummyAppCheck, Dispatchers.IO, Dispatchers.Default)
  }

  @After
  fun tearDown() {
    FirebaseApp.clearInstancesForTest()
  }

  // --- Defaults and the URL of the service ---

  @Test
  fun theInstanceKeepsTheGivenSettings() {
    val settings = SqlServiceSettings(serviceId = SERVICE_ID, location = LOCATION)

    val fsql = component.get(settings)

    // The defaults are applied when the URL is built; the settings stay as they were given.
    assertThat(fsql.settings).isEqualTo(settings)
    assertThat(fsql.settings.host).isNull()
    assertThat(fsql.settings.sslEnabled).isNull()
    assertThat(fsql.settings.schemaName).isNull()
  }

  @Test
  fun theDefaultsNameTheProductionService() {
    val url = serviceUrlOf(SqlServiceSettings(serviceId = SERVICE_ID, location = LOCATION), PROJECT)

    assertThat(url.toString()).isEqualTo("https://firebasedataconnect.googleapis.com$PATH")
  }

  @Test
  fun theHostPortAndSslSettingsGiveTheBaseUrl() {
    val baseUrls =
      mapOf(
        settings(host = "127.0.0.1", port = 9399, sslEnabled = null) to "https://127.0.0.1:9399",
        settings(host = "127.0.0.1", port = 9399, sslEnabled = false) to "http://127.0.0.1:9399",
        settings(host = "127.0.0.1:9399", sslEnabled = true) to "https://127.0.0.1:9399",
        settings(host = "localhost", sslEnabled = false) to "http://localhost",
        // Host names are case-insensitive, so the URL has them in lowercase.
        settings(host = "Emulator.Local:8080", sslEnabled = null) to "https://emulator.local:8080",
        settings(host = "[::1]", port = 9399, sslEnabled = null) to "https://[::1]:9399",
        settings(host = "[::1]:9399", sslEnabled = false) to "http://[::1]:9399",
        settings(host = null, port = 8443, sslEnabled = null) to
          "https://firebasedataconnect.googleapis.com:8443",
        settings(host = null, sslEnabled = false) to "http://firebasedataconnect.googleapis.com",
        // The port replaces the port of the host.
        settings(host = "127.0.0.1:8080", port = 9399, sslEnabled = false) to
          "http://127.0.0.1:9399"
      )

    for ((settings, baseUrl) in baseUrls) {
      assertWithMessage("$settings")
        .that(serviceUrlOf(settings, PROJECT).toString())
        .isEqualTo("$baseUrl$PATH")
    }
  }

  @Test
  fun theIdsAreEncodedAsPathSegments() {
    val settings = SqlServiceSettings(serviceId = "my service", location = "us/central1")

    val url = serviceUrlOf(settings, "my project")

    assertThat(url.pathSegments)
      .containsExactly(
        "v1",
        "projects",
        "my project",
        "locations",
        "us/central1",
        "services",
        "my service",
        "postgrest"
      )
      .inOrder()
  }

  // --- Caching ---

  @Test
  fun settingsThatNameTheSameServiceShareAnInstance() {
    // An unset setting and its default value give the same instance.
    val base = SqlServiceSettings(serviceId = SERVICE_ID, location = LOCATION)
    val instance = component.get(base)
    val equivalents =
      listOf(
        base.copy(sslEnabled = true),
        base.copy(host = "firebasedataconnect.googleapis.com"),
        base.copy(host = "FirebaseDataConnect.googleapis.com"),
        base.copy(port = 443),
        base.copy(headers = emptyMap())
      )

    for (equivalent in equivalents) {
      assertWithMessage("$equivalent").that(component.get(equivalent)).isSameInstanceAs(instance)
    }
  }

  @Test
  fun settingsThatDifferGiveDifferentInstances() {
    val instance = component.get(settings())
    val others =
      mapOf(
        "schemaName" to settings(schemaName = "other_schema"),
        "host" to settings(host = "other.url"),
        "port" to settings(port = 8080),
        "sslEnabled" to settings(sslEnabled = true),
        "location" to settings(location = "europe-west1"),
        "serviceId" to settings(serviceId = "other-service")
      )

    for ((property, other) in others) {
      assertWithMessage(property).that(component.get(other)).isNotSameInstanceAs(instance)
    }
  }

  @Test
  fun otherHeadersGetTheInstanceWithItsFirstSettings() =
    runBlocking<Unit> {
      // The headers are not part of the cache key.
      withServer { server, host ->
        val first = settings(host = host, headers = mapOf("X-Custom" to "1"))
        val instance = component.get(first)

        for (other in
          listOf(
            first.copy(headers = mapOf("X-Custom" to "2")),
            first.copy(headers = emptyMap())
          )) {
          val cached = component.get(other)
          assertWithMessage("$other").that(cached).isSameInstanceAs(instance)
          assertWithMessage("$other").that(cached.settings).isEqualTo(first)
        }

        component.get(first.copy(headers = mapOf("X-Custom" to "2"))).from("t").select().execute()
        assertThat(server.takeRequestOrFail().getHeader("X-Custom")).isEqualTo("1")
      }
    }

  @Test
  fun theReservedHeadersAreRejected() {
    // The names are compared ignoring case, like HTTP header names.
    val reservedHeaders =
      listOf(
        "X-Firebase-Auth-Token",
        "x-firebase-appcheck",
        "X-Goog-Api-Client",
        "x-client-version",
        "X-FIREBASE-GMPID",
        "Accept-Profile",
        "CONTENT-PROFILE",
        "authorization",
        "x-retry-count",
        "x-firebase-sqlconnect-affinity",
        "X-Goog-Request-Params",
        "x-firebase-client",
        "X-Firebase-Client-Log-Type"
      )

    for (name in reservedHeaders) {
      val error =
        assertThrows(name, ReservedHeaderException::class.java) {
          component.get(settings(headers = mapOf(name to "value")))
        }
      assertWithMessage(name).that(error.headerName).isEqualTo(name)
      assertWithMessage(name).that(error).hasMessageThat().contains(name)
      assertWithMessage(name).that(error).hasMessageThat().contains("SqlServiceSettings.headers")
    }
    // Other headers are allowed.
    component.get(settings(headers = mapOf("X-Custom" to "1")))
  }

  @Test
  fun aReservedHeaderIsAnIllegalArgument() {
    // Code that catches IllegalArgumentException, the usual error of an invalid argument, still
    // catches it.
    assertThrows(IllegalArgumentException::class.java) {
      component.get(settings(headers = mapOf("Authorization" to "Bearer token")))
    }
  }

  // --- terminate() ---

  @Test
  fun aTerminatedInstanceCannotStartARequest() {
    val fsql = FirebaseSql.getInstance(app, settings())

    fsql.terminate()

    val errors =
      listOf(
        assertThrows(IllegalStateException::class.java) { fsql.from("products") },
        assertThrows(IllegalStateException::class.java) { fsql.rpc("my_function") },
        assertThrows(IllegalStateException::class.java) {
          fsql.rpc("my_function", mapOf("a" to 1), method = RpcMethod.GET)
        }
      )
    for (error in errors) {
      assertThat(error).hasMessageThat().contains("terminated")
    }
  }

  @Test
  fun theBuildersOfATerminatedInstanceCannotBeExecuted() =
    runBlocking<Unit> {
      withServer { server, host ->
        val fsql = FirebaseSql.getInstance(app, settings(host = host))
        // Builders of each kind, created before terminate().
        val builders =
          mapOf(
            "select" to fsql.from("products").select(),
            "insert" to fsql.from("products").insert(mapOf("id" to 1)),
            "update" to fsql.from("products").update(mapOf("name" to "x")).eq("id", 1),
            "delete" to fsql.from("products").delete().eq("id", 1),
            "rpc" to fsql.rpc("my_function"),
            "noRetry" to fsql.from("products").select().noRetry()
          )

        fsql.terminate()

        for ((kind, builder) in builders) {
          val error =
            assertThrows(kind, IllegalStateException::class.java) {
              runBlocking { builder.execute() }
            }
          assertWithMessage(kind).that(error).hasMessageThat().contains("terminated")
        }
        assertThat(server.requestCount).isEqualTo(0)
      }
    }

  @Test
  fun aBuilderThatWorkedStopsWorkingAfterTerminate() =
    runBlocking<Unit> {
      withServer { server, host ->
        val fsql = FirebaseSql.getInstance(app, settings(host = host))
        val query = fsql.from("products").select()
        assertThat(query.execute().data).isEqualTo("[]")

        fsql.terminate()

        assertThrows(IllegalStateException::class.java) { runBlocking { query.execute() } }
        assertThat(server.requestCount).isEqualTo(1)
      }
    }

  @Test
  fun terminateCanBeCalledTwice() {
    val fsql = FirebaseSql.getInstance(app, settings())

    fsql.terminate()
    fsql.terminate()

    assertThrows(IllegalStateException::class.java) { fsql.from("products") }
  }

  @Test
  fun getInstanceAfterTerminateReturnsANewInstanceThatWorks() =
    runBlocking<Unit> {
      withServer { server, host ->
        val terminated = FirebaseSql.getInstance(app, settings(host = host))
        terminated.terminate()

        val fsql = FirebaseSql.getInstance(app, settings(host = host))
        val result = fsql.from("products").select().execute()

        assertThat(fsql).isNotSameInstanceAs(terminated)
        assertThat(result.data).isEqualTo("[]")
        assertThat(server.requestCount).isEqualTo(1)
      }
    }

  @Test
  fun terminatingAnOldInstanceAgainKeepsItsReplacement() {
    val old = FirebaseSql.getInstance(app, settings())
    old.terminate()
    val replacement = FirebaseSql.getInstance(app, settings())

    // The cache removes an instance by identity, so the replacement is not removed.
    old.terminate()

    assertThat(FirebaseSql.getInstance(app, settings())).isSameInstanceAs(replacement)
    replacement.from("products")
  }

  @Test
  fun terminatingAnInstanceDoesNotTerminateTheOthers() =
    runBlocking<Unit> {
      withServer { server, host ->
        val terminated =
          FirebaseSql.getInstance(app, settings(host = host, schemaName = "inventory"))
        val other = FirebaseSql.getInstance(app, settings(host = host))

        terminated.terminate()

        val result = other.from("products").select().execute()
        assertThat(result.data).isEqualTo("[]")
        assertThat(server.requestCount).isEqualTo(1)
      }
    }

  @Test
  fun appDeleteTerminatesAllInstances() {
    val instances =
      listOf("us-east1", "europe-west1").map { component.get(settings(location = it)) }
    val queries = instances.map { it.from("products").select() }
    val okHttpClient = component.okHttpClient!!

    // Deleting the app fires the lifecycle listener, which terminates the component and all its
    // instances.
    app.delete()

    for (instance in instances) {
      assertThrows(IllegalStateException::class.java) { instance.from("products") }
    }
    for (query in queries) {
      assertThrows(IllegalStateException::class.java) { runBlocking { query.execute() } }
    }
    // Subsequent calls fail because the component is terminated.
    assertThrows(IllegalStateException::class.java) { component.get(settings()) }
    // Once the requests of the instances have ended, the shared client stops its threads.
    runBlocking { component.shutdownJob?.join() }
    assertThat(okHttpClient.dispatcher.executorService.isShutdown).isTrue()
  }

  @Test
  fun appDeleteWithoutInstancesHasNothingToShutDown() {
    app.delete()

    assertThat(component.shutdownJob).isNull()
    assertThrows(IllegalStateException::class.java) { component.get(settings()) }
  }

  @Test
  fun concurrentCallsWithTheSameSettingsGetOneInstance() =
    runBlocking<Unit> {
      val instances =
        List(16) { async(Dispatchers.Default) { component.get(settings()) } }.awaitAll()

      assertThat(instances.toSet()).hasSize(1)
      // The instances that lost the race were terminated, and the cache kept the one that won.
      assertThat(component.get(settings())).isSameInstanceAs(instances.first())
    }

  @Test
  fun theInstancesThatLostTheRaceLeaveNoThreadBehind() =
    runBlocking<Unit> {
      val threadsBefore = Thread.getAllStackTraces().keys

      List(16) { async(Dispatchers.Default) { component.get(settings()) } }.awaitAll()

      // Each call that found no client created one, and only the client of the instance that won
      // is kept. A client that sent no request has started no thread and opened no connection, so
      // the dropped clients need no shutdown.
      val newThreads = Thread.getAllStackTraces().keys - threadsBefore
      assertThat(newThreads.map { it.name }.filter { it.startsWith("OkHttp") }).isEmpty()
      assertThat(component.okHttpClient!!.connectionPool.connectionCount()).isEqualTo(0)
    }

  @Test
  fun suspendingTerminateTerminatesTheInstance() =
    runBlocking<Unit> {
      val fsql = component.get(settings())

      fsql.suspendingTerminate()

      assertThrows(IllegalStateException::class.java) { fsql.from("products") }
      assertThat(component.get(settings())).isNotSameInstanceAs(fsql)
    }

  @Test
  fun theInstancesShareOneHttpClient() =
    runBlocking<Unit> {
      withServer { server, host ->
        val first = component.get(settings(host = host))
        val second = component.get(settings(host = host, schemaName = "inventory"))

        first.from("products").select().execute()
        second.from("products").select().execute()

        assertThat(server.takeRequest().sequenceNumber).isEqualTo(0)
        // The second instance reuses the connection of the first one, which its client keeps.
        assertThat(server.takeRequest().sequenceNumber).isEqualTo(1)
      }
    }

  @Test
  fun terminatingAnInstanceKeepsTheSharedClientForTheOthers() =
    runBlocking<Unit> {
      withServer { _, host ->
        val terminated = component.get(settings(host = host, schemaName = "inventory"))
        val other = component.get(settings(host = host))
        terminated.suspendingTerminate()

        val result = other.from("products").select().execute()

        assertThat(result.data).isEqualTo("[]")
        assertThat(component.okHttpClient!!.dispatcher.executorService.isShutdown).isFalse()
      }
    }

  // --- rpc() ---

  @Test
  fun rpcEncodesAnObjectWithTheSerializerOfItsType() {
    val fsql = component.get(settings())
    val limit = PriceLimit(maxPrice = 10.0)

    val post = request(fsql.rpc("products_under", limit))
    val get = request(fsql.rpc("products_under", limit, method = RpcMethod.GET))

    assertThat(post.body.toString()).isEqualTo("""{"max_price":10.0}""")
    assertThat(get.queryParams).containsExactly("max_price" to "10.0")
    assertThat(request(fsql.rpc("products_under", limit, PriceLimit.serializer()))).isEqualTo(post)
  }

  @Test
  fun rpcWithANullObjectSendsNoArguments() {
    val fsql = component.get(settings())
    val noLimit: PriceLimit? = null

    assertThat(request(fsql.rpc("products_under", noLimit)).body.toString()).isEqualTo("{}")
    assertThat(request(fsql.rpc("products_under", noLimit, method = RpcMethod.GET)).queryParams)
      .isEmpty()
  }

  @Test
  fun rpcRejectsAnObjectThatIsNotEncodedAsAJsonObject() {
    val fsql = component.get(settings())

    assertThrows(IllegalArgumentException::class.java) {
      fsql.rpc("products_under", listOf(PriceLimit(1.0), PriceLimit(2.0)))
    }
  }

  @Test
  fun rpcRejectsAStringOrBytes() {
    val fsql = component.get(settings())

    // The rpc extension function encodes them to a JSON string and to a list, so neither is an
    // object of arguments.
    assertThrows(IllegalArgumentException::class.java) { fsql.rpc("echo", "one two") }
    assertThrows(IllegalArgumentException::class.java) { fsql.rpc("store", byteArrayOf(1, 2)) }
  }

  @Test
  fun rpcWithoutArgumentsSendsAnEmptyObject() {
    val fsql = component.get(settings())

    assertThat(request(fsql.rpc("my_function")).body.toString()).isEqualTo("{}")
    assertThat(request(fsql.rpc("my_function", emptyMap())))
      .isEqualTo(request(fsql.rpc("my_function")))
  }

  private fun request(builder: Any): QueryRequest = (builder as OperationBuilderImpl).toRequest()

  @Serializable data class PriceLimit(@SerialName("max_price") val maxPrice: Double)

  // --- Headers ---

  @Test
  fun settingsHeadersAreSentWithEveryRequest() =
    runBlocking<Unit> {
      withServer { server, host ->
        val settings =
          settings(
            host = host,
            schemaName = "inventory",
            headers = mapOf("X-Custom-Header" to "custom-value")
          )

        component.get(settings).from("products").select().execute()

        val request = server.takeRequestOrFail()
        assertThat(request.path).isEqualTo("/${SERVICE_PATH.joinToString("/")}/products?select=*")
        assertThat(request.getHeader("X-Custom-Header")).isEqualTo("custom-value")
        assertThat(request.getHeader("Accept-Profile")).isEqualTo("inventory")
      }
    }

  @Test
  fun noSchemaHeaderIsSentByDefault() =
    runBlocking<Unit> {
      // Without a schema header, the server uses its default schema.
      withServer { server, host ->
        val fsql = component.get(settings(host = host))

        fsql.from("products").select().execute()
        fsql.from("products").insert(mapOf("id" to 1)).execute()

        for (request in listOf(server.takeRequestOrFail(), server.takeRequestOrFail())) {
          assertWithMessage(request.method).that(request.getHeader("Accept-Profile")).isNull()
          assertWithMessage(request.method).that(request.getHeader("Content-Profile")).isNull()
        }
      }
    }

  @Test
  fun thePublicSchemaAndNoSchemaGiveDifferentInstances() =
    runBlocking<Unit> {
      // Without a schema header, PostgREST uses the first schema that it exposes, which is not
      // always public, so naming public sends other requests.
      withServer { server, host ->
        val noSchema = component.get(settings(host = host))
        val publicSchema = component.get(settings(host = host, schemaName = "public"))

        noSchema.from("products").select().execute()
        publicSchema.from("products").select().execute()

        assertThat(publicSchema).isNotSameInstanceAs(noSchema)
        assertThat(server.takeRequestOrFail().getHeader("Accept-Profile")).isNull()
        assertThat(server.takeRequestOrFail().getHeader("Accept-Profile")).isEqualTo("public")
      }
    }

  // --- Validation of the settings ---

  @Test
  fun theHostMustNameAHost() {
    val invalidHosts = listOf("", " ", "not a url", "custom.url:port", "[::1")
    for (host in invalidHosts) {
      val error =
        assertThrows(IllegalArgumentException::class.java) { component.get(settings(host = host)) }

      assertWithMessage(host).that(error).hasMessageThat().contains("SqlServiceSettings.host")
    }
  }

  @Test
  fun anInvalidPortThrows() {
    for (port in listOf(0, -1, 65536)) {
      assertThrows(IllegalArgumentException::class.java) { component.get(settings(port = port)) }
    }
  }

  @Test
  fun theHostMustNotHaveASchemeOrAPath() {
    // A URL parser reads `https://custom.url` as the host `https`, so the parsed URL is checked.
    val withScheme =
      listOf("https://custom.url", "http://10.0.2.2:9399", "HTTP://localhost", "ftp://custom.url")
    for (host in withScheme) {
      val error =
        assertThrows(IllegalArgumentException::class.java) { component.get(settings(host = host)) }

      assertWithMessage(host).that(error).hasMessageThat().contains("SqlServiceSettings.sslEnabled")
    }
    val withMoreThanAHost =
      listOf(
        "custom.url/prefix",
        "custom.url\\prefix",
        "custom.url?key=value",
        "custom.url#top",
        "user@custom.url"
      )
    for (host in withMoreThanAHost) {
      val error =
        assertThrows(IllegalArgumentException::class.java) { component.get(settings(host = host)) }

      assertWithMessage(host).that(error).hasMessageThat().contains("SqlServiceSettings.host")
    }
  }

  @Test
  fun theSchemaNameMustNotBeEmpty() {
    val error =
      assertThrows(IllegalArgumentException::class.java) {
        component.get(settings(schemaName = ""))
      }

    assertThat(error).hasMessageThat().contains("SqlServiceSettings.schemaName")
  }

  @Test
  fun theLocationAndTheServiceIdMustNotBeEmpty() {
    val location =
      assertThrows(IllegalArgumentException::class.java) { component.get(settings(location = "")) }
    val service =
      assertThrows(IllegalArgumentException::class.java) { component.get(settings(serviceId = "")) }

    assertThat(location).hasMessageThat().contains("SqlServiceSettings.location")
    assertThat(service).hasMessageThat().contains("SqlServiceSettings.serviceId")
  }

  @Test
  fun whitespaceInTheNamesIsKept() {
    // Only empty names are rejected. The server answers with an error for a name that it does not
    // know.
    val settings = settings(serviceId = "my service ", location = " us-central1", schemaName = " ")

    val fsql = component.get(settings)

    assertThat(fsql.settings.schemaName).isEqualTo(" ")
    assertThat(serviceUrlOf(settings, PROJECT).pathSegments)
      .containsAtLeast(" us-central1", "my service ")
      .inOrder()
  }

  @Test
  fun theAppMustHaveAProjectId() {
    // FirebaseOptions do not require a project ID, for example when they are built in code.
    val withoutProject =
      FirebaseOptions.Builder()
        .setApplicationId("1:1234567890:android:321abc")
        .setApiKey("fake-api-key")
        .build()
    val optionsByAppName =
      mapOf(
        "no-project" to withoutProject,
        "blank-project" to FirebaseOptions.Builder(withoutProject).setProjectId(" ").build()
      )

    for ((name, options) in optionsByAppName) {
      val appWithoutProject = FirebaseApp.initializeApp(app.applicationContext, options, name)

      val error =
        assertThrows(IllegalArgumentException::class.java) {
          FirebaseSql.getInstance(appWithoutProject, settings())
        }

      assertWithMessage(name).that(error).hasMessageThat().contains("FirebaseOptions.projectId")
      assertWithMessage(name).that(error).hasMessageThat().contains("'$name'")
    }
  }

  // --- FirebaseSql.getInstance(), which gets the component that FirebaseSqlRegistrar registers ---

  @Test
  fun getInstanceReturnsOneInstancePerAppAndSettings() {
    val otherApp = FirebaseApp.initializeApp(app.applicationContext, app.options, "other")

    val instance = FirebaseSql.getInstance(app, settings())

    assertThat(instance.app).isSameInstanceAs(app)
    assertThat(FirebaseSql.getInstance(app, settings())).isSameInstanceAs(instance)
    assertThat(FirebaseSql.getInstance(otherApp, settings())).isNotSameInstanceAs(instance)
  }

  @Test
  fun theProjectIdComesFromTheGivenApp() =
    runBlocking<Unit> {
      withServer { server, host ->
        // An app other than the default app must not use the project of the default app.
        val options = FirebaseOptions.Builder(app.options).setProjectId("other-project").build()
        val otherApp = FirebaseApp.initializeApp(app.applicationContext, options, "other")

        FirebaseSql.getInstance(otherApp, settings(host = host)).from("products").select().execute()

        assertThat(server.takeRequestOrFail().path)
          .isEqualTo(
            "/v1/projects/other-project/locations/$LOCATION/services/$SERVICE_ID/postgrest/" +
              "products?select=*"
          )
      }
    }

  @Test
  fun theFirebaseSqlExtensionsReturnTheInstancesOfGetInstance() {
    val otherApp = FirebaseApp.initializeApp(app.applicationContext, app.options, "other")

    assertThat(Firebase.sql(settings())).isSameInstanceAs(FirebaseSql.getInstance(settings()))
    assertThat(Firebase.sql(otherApp, settings()))
      .isSameInstanceAs(FirebaseSql.getInstance(otherApp, settings()))
    assertThat(Firebase.sql(otherApp, settings())).isNotSameInstanceAs(Firebase.sql(settings()))
  }

  @Test
  fun getInstanceWithoutAnAppUsesTheDefaultApp() {
    val fsql = FirebaseSql.getInstance(settings())

    assertThat(fsql.app).isSameInstanceAs(app)
    assertThat(fsql).isSameInstanceAs(FirebaseSql.getInstance(app, settings()))
  }

  @Test
  fun getInstanceWithoutAnAppThrowsWhenThereIsNoDefaultApp() {
    FirebaseApp.clearInstancesForTest()

    assertThrows(IllegalStateException::class.java) { FirebaseSql.getInstance(settings()) }
    assertThrows(IllegalStateException::class.java) { Firebase.sql(settings()) }
  }

  @Test
  fun getInstanceSendsTheRequestsToTheServiceOfTheProject() =
    runBlocking<Unit> {
      withServer { server, host ->
        FirebaseSql.getInstance(app, settings(host = host)).from("products").select().execute()

        val request = server.takeRequestOrFail()
        assertThat(request.path).isEqualTo("/${SERVICE_PATH.joinToString("/")}/products?select=*")
        // The app has neither Firebase Authentication nor App Check, so no token is sent.
        assertThat(request.getHeader("x-firebase-auth-token")).isNull()
        assertThat(request.getHeader("X-Firebase-AppCheck")).isNull()
      }
    }

  @Test
  fun theLibraryVersionIsRegisteredForPlatformLogging() {
    // FirebaseApp reports every SDK that registers a LibraryVersionComponent in its user agent.
    val userAgent = app.get(UserAgentPublisher::class.java).userAgent

    assertThat(userAgent).contains("fire-sql/${BuildConfig.VERSION_NAME}")
  }

  // --- Names of tables and functions ---

  @Test
  fun tableAndFunctionNamesAreSentAsOnePathSegment() =
    runBlocking<Unit> {
      withServer { server, host ->
        val fsql = FirebaseSql.getInstance(app, settings(host = host))
        for (name in NAMES_WITH_URL_CHARACTERS) {
          fsql.from(name).select().execute()

          val tableUrl = server.takeRequestOrFail().requestUrl!!
          assertWithMessage(name).that(tableUrl.pathSegments).isEqualTo(SERVICE_PATH + name)
          assertWithMessage(name).that(tableUrl.queryParameterNames).containsExactly("select")

          fsql.rpc(name).execute()

          val functionUrl = server.takeRequestOrFail().requestUrl!!
          assertWithMessage(name)
            .that(functionUrl.pathSegments)
            .isEqualTo(SERVICE_PATH + "rpc" + name)
          assertWithMessage(name).that(functionUrl.queryParameterNames).isEmpty()
        }
      }
    }

  /**
   * Returns settings for the service [SERVICE_ID] in [LOCATION], with the given values. SSL is off
   * by default, because [MockWebServer] serves plain http.
   */
  private fun settings(
    host: String? = "custom.url",
    port: Int? = null,
    sslEnabled: Boolean? = false,
    location: String = LOCATION,
    serviceId: String = SERVICE_ID,
    schemaName: String? = null,
    headers: Map<String, String> = emptyMap()
  ) =
    SqlServiceSettings(
      serviceId = serviceId,
      location = location,
      schemaName = schemaName,
      headers = headers,
      host = host,
      port = port,
      sslEnabled = sslEnabled
    )

  /**
   * Runs [block] with a server that answers every request with an empty list. The host is the
   * `<host>:<port>` of the server, without a scheme.
   */
  private inline fun withServer(block: (server: MockWebServer, host: String) -> Unit) {
    val server = MockWebServer()
    server.dispatcher =
      object : Dispatcher() {
        override fun dispatch(request: RecordedRequest) = MockResponse().setBody("[]")
      }
    server.start()
    try {
      block(server, "${server.hostName}:${server.port}")
    } finally {
      server.shutdown()
    }
  }

  private companion object {
    const val LOCATION = "us-central1"
    const val SERVICE_ID = "my-service"
    const val PROJECT = "my-project"

    /** The path of the service of [PROJECT], which comes after the base URL. */
    const val PATH = "/v1/projects/$PROJECT/locations/$LOCATION/services/$SERVICE_ID/postgrest"

    /** The path of the service of the test project, which comes before the table name. */
    val SERVICE_PATH =
      listOf(
        "v1",
        "projects",
        "sql-test-project",
        "locations",
        LOCATION,
        "services",
        SERVICE_ID,
        "postgrest"
      )

    /**
     * Names with characters that have a meaning in a URL. PostgREST reads the name from one path
     * segment, so these characters must not change the URL: `a?b=1` must not add a query parameter,
     * `../products` must not leave the path of the service, and a table named `rpc/x` must not call
     * the function `x`.
     */
    val NAMES_WITH_URL_CHARACTERS =
      listOf("my table", "a?b=1", "a#b", "../products", "a%20b", "rpc/x")
  }
}
