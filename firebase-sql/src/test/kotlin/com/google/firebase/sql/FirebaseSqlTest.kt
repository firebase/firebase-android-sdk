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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests how [FirebaseSql] instances are created, cached, validated, and terminated, how their
 * headers are set, and the URLs that their requests go to.
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
  fun theInstanceKeepsTheGivenConfig() {
    val config = ServiceConfig(serviceId = SERVICE_ID, location = LOCATION)

    val fsql = component.get(config)

    // The defaults are applied when the URL is built; the config stays as it was given.
    assertThat(fsql.config).isEqualTo(config)
    assertThat(fsql.config.host).isNull()
    assertThat(fsql.config.sslEnabled).isNull()
    assertThat(fsql.config.schemaName).isNull()
  }

  @Test
  fun theDefaultsNameTheProductionService() {
    val url = serviceUrlOf(ServiceConfig(serviceId = SERVICE_ID, location = LOCATION), PROJECT)

    assertThat(url.toString()).isEqualTo("https://firebasedataconnect.googleapis.com$PATH")
  }

  @Test
  fun theHostThePortAndSslGiveTheBaseUrl() {
    val baseUrls =
      mapOf(
        config(host = "127.0.0.1", port = 9399, sslEnabled = null) to "https://127.0.0.1:9399",
        config(host = "127.0.0.1", port = 9399, sslEnabled = false) to "http://127.0.0.1:9399",
        config(host = "127.0.0.1:9399", sslEnabled = true) to "https://127.0.0.1:9399",
        config(host = "localhost", sslEnabled = false) to "http://localhost",
        // Host names are case-insensitive, so the URL has them in lowercase.
        config(host = "Emulator.Local:8080", sslEnabled = null) to "https://emulator.local:8080",
        config(host = "[::1]", port = 9399, sslEnabled = null) to "https://[::1]:9399",
        config(host = "[::1]:9399", sslEnabled = false) to "http://[::1]:9399",
        config(host = null, port = 8443, sslEnabled = null) to
          "https://firebasedataconnect.googleapis.com:8443",
        // The port replaces the port of the host.
        config(host = "127.0.0.1:8080", port = 9399, sslEnabled = false) to "http://127.0.0.1:9399"
      )

    for ((config, baseUrl) in baseUrls) {
      assertWithMessage("$config")
        .that(serviceUrlOf(config, PROJECT).toString())
        .isEqualTo("$baseUrl$PATH")
    }
  }

  @Test
  fun sslCannotBeTurnedOffForTheProductionHost() {
    // http would send the Firebase Authentication and Firebase App Check tokens in plain text.
    val configs =
      listOf(
        config(host = null, sslEnabled = false),
        config(host = null, port = 8080, sslEnabled = false),
        config(host = "FirebaseDataConnect.googleapis.com", sslEnabled = false),
        config(host = "firebasedataconnect.googleapis.com.:80", sslEnabled = false)
      )

    for (config in configs) {
      val error =
        assertThrows("$config", IllegalArgumentException::class.java) {
          serviceUrlOf(config, PROJECT)
        }
      assertWithMessage("$config")
        .that(error)
        .hasMessageThat()
        .contains("must not be false for the production host")
    }
  }

  @Test
  fun theIdsAreEncodedAsPathSegments() {
    val config = ServiceConfig(serviceId = "my service", location = "us/central1")

    val url = serviceUrlOf(config, "my project")

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
  fun configsThatNameTheSameServiceShareAnInstance() {
    // An unset property and its default value give the same instance.
    val base = ServiceConfig(serviceId = SERVICE_ID, location = LOCATION)
    val instance = component.get(base)
    val equivalents =
      listOf(
        base.copy(sslEnabled = true),
        base.copy(host = "firebasedataconnect.googleapis.com"),
        base.copy(host = "FirebaseDataConnect.googleapis.com"),
        base.copy(port = 443),
        base.copy()
      )

    for (equivalent in equivalents) {
      assertWithMessage("$equivalent").that(component.get(equivalent)).isSameInstanceAs(instance)
    }
  }

  @Test
  fun instanceCacheKeyIsCaseInsensitive() {
    val base =
      ServiceConfig(
        serviceId = "My-Service",
        location = "US-Central1",
        schemaName = "Inventory",
        host = "Custom.Example.Com"
      )
    val instance = component.get(base)
    val equivalents =
      listOf(
        base.copy(serviceId = "my-service"),
        base.copy(serviceId = "MY-SERVICE"),
        base.copy(location = "us-central1"),
        base.copy(schemaName = "inventory"),
        base.copy(schemaName = "INVENTORY"),
        base.copy(host = "custom.example.com"),
        ServiceConfig(
          serviceId = "my-service",
          location = "us-central1",
          schemaName = "inventory",
          host = "custom.example.com"
        )
      )

    for (equivalent in equivalents) {
      val cached = component.get(equivalent)
      assertWithMessage("$equivalent").that(cached).isSameInstanceAs(instance)
      assertThat(cached.config).isSameInstanceAs(base)
    }
  }

  @Test
  fun configsThatDifferGiveDifferentInstances() {
    val instance = component.get(config())
    val others =
      mapOf(
        "schemaName" to config(schemaName = "other_schema"),
        "host" to config(host = "other.url"),
        "port" to config(port = 8080),
        "sslEnabled" to config(sslEnabled = true),
        "location" to config(location = "europe-west1"),
        "serviceId" to config(serviceId = "other-service")
      )

    for ((property, other) in others) {
      assertWithMessage(property).that(component.get(other)).isNotSameInstanceAs(instance)
    }
  }

  // --- terminate() ---

  @Test
  fun appDeleteWithoutInstancesHasNothingToShutDown() {
    app.delete()

    assertThat(component.shutdownJob).isNull()
    assertThrows(IllegalStateException::class.java) { component.get(config()) }
  }

  @Test
  fun concurrentCallsWithTheSameConfigGetOneInstance() =
    runBlocking<Unit> {
      val instances = List(16) { async(Dispatchers.Default) { component.get(config()) } }.awaitAll()

      assertThat(instances.toSet()).hasSize(1)
      // The instances that lost the race were terminated, and the cache kept the one that won.
      assertThat(component.get(config())).isSameInstanceAs(instances.first())
    }

  @Test
  fun theInstancesThatLostTheRaceLeaveNoThreadBehind() =
    runBlocking<Unit> {
      val threadsBefore = Thread.getAllStackTraces().keys

      List(16) { async(Dispatchers.Default) { component.get(config()) } }.awaitAll()

      // Each call that found no client created one, and only the client of the instance that won
      // is kept. A client that sent no request has started no thread and opened no connection, so
      // the dropped clients need no shutdown.
      val newThreads = Thread.getAllStackTraces().keys - threadsBefore
      assertThat(newThreads.map { it.name }.filter { it.startsWith("OkHttp") }).isEmpty()
      assertThat(component.okHttpClient!!.connectionPool.connectionCount()).isEqualTo(0)
    }

  // --- Headers ---

  @Test
  fun setHeadersRejectsTheReservedHeaders() {
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
    val fsql = component.get(config())

    for (name in reservedHeaders) {
      val error =
        assertThrows(name, ReservedHeaderException::class.java) {
          fsql.setHeaders(mapOf("X-Custom" to "1", name to "value"))
        }
      assertWithMessage(name).that(error.headerName).isEqualTo(name)
      assertWithMessage(name).that(error).hasMessageThat().contains(name)
      assertWithMessage(name).that(error).hasMessageThat().contains("setHeaders()")
    }
    // Other headers are allowed.
    fsql.setHeaders(CUSTOM)
  }

  @Test
  fun aReservedHeaderIsAnIllegalArgument() {
    // Code that catches IllegalArgumentException, the usual error of an invalid argument, still
    // catches it.
    val fsql = component.get(config())

    assertThrows(IllegalArgumentException::class.java) {
      fsql.setHeaders(mapOf("Authorization" to "Bearer token"))
    }
  }

  @Test
  fun setHeadersRejectsAHeaderThatHttpDoesNotAllow() {
    // OkHttp would reject such a header on every request, once the settings can no longer change.
    val invalidHeaders =
      listOf(
        mapOf("" to "value"),
        mapOf("X Custom" to "value"),
        mapOf("X-Custom" to "line\nbreak"),
        mapOf("X-Custom" to "Jos\u00e9")
      )
    val fsql = component.get(config())

    for (headers in invalidHeaders) {
      assertThrows("$headers", IllegalArgumentException::class.java) { fsql.setHeaders(headers) }
    }
    // Nothing changed: the instance is still mutable, and accepts valid headers.
    assertThat(fsql.isMutable).isTrue()
    fsql.setHeaders(CUSTOM)
  }

  @Test
  fun anInvalidHeaderIsAnIllegalArgumentOnAnInstanceInUse() {
    val fsql = component.get(config())
    fsql.terminate()

    // Not a FirebaseSqlMutationException: the header is invalid whether or not the settings can
    // change.
    assertThrows(IllegalArgumentException::class.java) {
      fsql.setHeaders(mapOf("X Custom" to "value"))
    }
  }

  // --- isMutable, and setHeaders() on an instance in use ---

  @Test
  fun aNewInstanceIsMutable() {
    val fsql = component.get(config())

    assertThat(fsql.isMutable).isTrue()
    // Setting the headers does not use the instance.
    fsql.setHeaders(CUSTOM)
    assertThat(fsql.isMutable).isTrue()
  }

  @Test
  fun terminateMakesTheInstanceImmutable() =
    runBlocking<Unit> {
      val terminated = component.get(config())
      val suspendingTerminated = component.get(config(schemaName = "inventory"))

      terminated.terminate()
      suspendingTerminated.suspendingTerminate()

      assertThat(terminated.isMutable).isFalse()
      assertThat(suspendingTerminated.isMutable).isFalse()
      assertThrows(FirebaseSqlMutationException::class.java) { terminated.setHeaders(CUSTOM) }
    }

  // --- Validation of the config ---

  @Test
  fun theHostMustNameAHost() {
    val invalidHosts = listOf("", " ", "not a url", "custom.url:port", "[::1")
    for (host in invalidHosts) {
      val error =
        assertThrows(IllegalArgumentException::class.java) { component.get(config(host = host)) }

      assertWithMessage(host).that(error).hasMessageThat().contains("ServiceConfig.host")
    }
  }

  @Test
  fun anInvalidPortThrows() {
    for (port in listOf(0, -1, 65536)) {
      assertThrows(IllegalArgumentException::class.java) { component.get(config(port = port)) }
    }
  }

  @Test
  fun theHostMustNotHaveASchemeOrAPath() {
    // A URL parser reads `https://custom.url` as the host `https`, so the parsed URL is checked.
    val withScheme =
      listOf("https://custom.url", "http://10.0.2.2:9399", "HTTP://localhost", "ftp://custom.url")
    for (host in withScheme) {
      val error =
        assertThrows(IllegalArgumentException::class.java) { component.get(config(host = host)) }

      assertWithMessage(host).that(error).hasMessageThat().contains("ServiceConfig.sslEnabled")
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
        assertThrows(IllegalArgumentException::class.java) { component.get(config(host = host)) }

      assertWithMessage(host).that(error).hasMessageThat().contains("ServiceConfig.host")
    }
  }

  @Test
  fun theSchemaNameMustNotBeEmpty() {
    val error =
      assertThrows(IllegalArgumentException::class.java) { component.get(config(schemaName = "")) }

    assertThat(error).hasMessageThat().contains("ServiceConfig.schemaName")
  }

  @Test
  fun theLocationAndTheServiceIdMustNotBeEmpty() {
    val location =
      assertThrows(IllegalArgumentException::class.java) { component.get(config(location = "")) }
    val service =
      assertThrows(IllegalArgumentException::class.java) { component.get(config(serviceId = "")) }

    assertThat(location).hasMessageThat().contains("ServiceConfig.location")
    assertThat(service).hasMessageThat().contains("ServiceConfig.serviceId")
  }

  @Test
  fun whitespaceInTheNamesIsKept() {
    // Only empty names are rejected. The server answers with an error for a name that it does not
    // know.
    val config = config(serviceId = "my service ", location = " us-central1", schemaName = " ")

    val fsql = component.get(config)

    assertThat(fsql.config.schemaName).isEqualTo(" ")
    assertThat(serviceUrlOf(config, PROJECT).pathSegments)
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
          FirebaseSql.getInstance(appWithoutProject, config())
        }

      assertWithMessage(name).that(error).hasMessageThat().contains("FirebaseOptions.projectId")
      assertWithMessage(name).that(error).hasMessageThat().contains("'$name'")
    }
  }

  // --- FirebaseSql.getInstance(), which gets the component that FirebaseSqlRegistrar registers ---

  @Test
  fun getInstanceReturnsOneInstancePerAppAndConfig() {
    val otherApp = FirebaseApp.initializeApp(app.applicationContext, app.options, "other")

    val instance = FirebaseSql.getInstance(app, config())

    assertThat(instance.app).isSameInstanceAs(app)
    // An equal config, which is another object, gets the same instance.
    assertThat(FirebaseSql.getInstance(app, config())).isSameInstanceAs(instance)
    assertThat(FirebaseSql.getInstance(otherApp, config())).isNotSameInstanceAs(instance)
  }

  @Test
  fun theFirebaseSqlExtensionsReturnTheInstancesOfGetInstance() {
    val otherApp = FirebaseApp.initializeApp(app.applicationContext, app.options, "other")

    assertThat(Firebase.sql(config())).isSameInstanceAs(FirebaseSql.getInstance(config()))
    assertThat(Firebase.sql(otherApp, config()))
      .isSameInstanceAs(FirebaseSql.getInstance(otherApp, config()))
    assertThat(Firebase.sql(otherApp, config())).isNotSameInstanceAs(Firebase.sql(config()))
  }

  @Test
  fun getInstanceWithoutAnAppUsesTheDefaultApp() {
    val fsql = FirebaseSql.getInstance(config())

    assertThat(fsql.app).isSameInstanceAs(app)
    assertThat(fsql).isSameInstanceAs(FirebaseSql.getInstance(app, config()))
  }

  @Test
  fun getInstanceWithoutAnAppThrowsWhenThereIsNoDefaultApp() {
    FirebaseApp.clearInstancesForTest()

    assertThrows(IllegalStateException::class.java) { FirebaseSql.getInstance(config()) }
    assertThrows(IllegalStateException::class.java) { Firebase.sql(config()) }
  }

  @Test
  fun theLibraryVersionIsRegisteredForPlatformLogging() {
    // FirebaseApp reports every SDK that registers a LibraryVersionComponent in its user agent.
    val userAgent = app.get(UserAgentPublisher::class.java).userAgent

    assertThat(userAgent).contains("fire-sql/${BuildConfig.VERSION_NAME}")
  }

  /**
   * Returns a config for the service [SERVICE_ID] in [LOCATION], with the given values. SSL is off
   * by default, because [MockWebServer] serves plain http.
   */
  private fun config(
    host: String? = "custom.url",
    port: Int? = null,
    sslEnabled: Boolean? = false,
    location: String = LOCATION,
    serviceId: String = SERVICE_ID,
    schemaName: String? = null
  ) =
    ServiceConfig(
      serviceId = serviceId,
      location = location,
      schemaName = schemaName,
      host = host,
      port = port,
      sslEnabled = sslEnabled
    )

  private companion object {
    const val LOCATION = "us-central1"
    const val SERVICE_ID = "my-service"
    const val PROJECT = "my-project"

    /** Custom headers that are not reserved. */
    val CUSTOM = mapOf("X-Custom" to "1")

    /** The path of the service of [PROJECT], which comes after the base URL. */
    const val PATH = "/v1/projects/$PROJECT/locations/$LOCATION/services/$SERVICE_ID/postgrest"
  }
}
