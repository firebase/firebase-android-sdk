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

import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.inject.Deferred
import com.google.firebase.sql.core.OkHttpNetworkClient
import com.google.firebase.sql.core.OperationsExecutor
import com.google.firebase.sql.core.newOkHttpClient
import com.google.firebase.sql.core.requireNotReservedHeader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

/**
 * Creates and caches the [FirebaseSql] instances of one [FirebaseApp], and closes them when that
 * app is deleted.
 *
 * Every instance uses the project ID and the Firebase Authentication and Firebase App Check
 * providers of that app, and the instances share one [OkHttpClient], so that they share its threads
 * and connections.
 *
 * @param blockingDispatcher Looks up the token providers of the instances, which can read the disk,
 * and shuts down the [OkHttpClient] after the app is deleted.
 * @param nonBlockingDispatcher Runs the token requests of the instances.
 */
internal class FirebaseSqlComponent(
  private val app: FirebaseApp,
  private val authDeferred: Deferred<InternalAuthProvider>,
  private val appCheckDeferred: Deferred<InteropAppCheckTokenProvider>,
  private val blockingDispatcher: CoroutineDispatcher,
  private val nonBlockingDispatcher: CoroutineDispatcher
) {

  init {
    app.addLifecycleEventListener { _, _ -> close() }
  }

  /**
   * The key of a cached instance. [restUrl] is the URL that the settings resolve to, so that
   * settings that name the same service in different ways share an instance.
   */
  private data class FirebaseSqlInstanceKey(
    val appName: String,
    val schemaName: String?,
    val restUrl: HttpUrl
  )

  private sealed interface State {
    /**
     * The app is alive. [instances] are the cached instances, and [okHttpClient] is the client that
     * they share, which is created with the first instance.
     */
    data class Open(
      val instances: Map<FirebaseSqlInstanceKey, FirebaseSqlImpl>,
      val okHttpClient: OkHttpClient?
    ) : State

    /**
     * The app was deleted, and [instances] are the instances that it had. [shutdownJob] waits for
     * their requests to end, and then shuts down their [OkHttpClient].
     */
    data class Closing(val instances: List<FirebaseSqlImpl>, val shutdownJob: Job) : State

    /** The app was deleted, and the threads and the connections of its instances are released. */
    data object Closed : State
  }

  private val state =
    MutableStateFlow<State>(State.Open(instances = emptyMap(), okHttpClient = null))

  /**
   * The client with which every instance sends its requests, or `null` before the first instance
   * and after the app is deleted. Tests read it.
   */
  internal val okHttpClient: OkHttpClient?
    get() = (state.value as? State.Open)?.okHttpClient

  /**
   * The job that shuts down the client after the app is deleted, or `null` before that, and once it
   * has completed. Tests wait for it.
   */
  internal val shutdownJob: Job?
    get() = (state.value as? State.Closing)?.shutdownJob

  /**
   * Returns the instance for the given [settings], and creates it on the first call.
   *
   * The project ID of the service is always the one in the options of the [app]. Settings that
   * resolve to the same URL and schema give the same instance, for example settings without a host
   * and settings with the production host, even when their headers differ: the instance keeps the
   * headers that it was created with until it is terminated.
   *
   * @throws ReservedHeaderException When the headers of the settings have a reserved header.
   * @throws IllegalArgumentException When the settings do not name a valid service, or have an
   * empty schema name, or when the options of the [app] have no project ID.
   * @throws IllegalStateException When the [app] was deleted.
   */
  fun get(settings: SqlServiceSettings): FirebaseSqlImpl {
    val schemaName = settings.schemaName
    require(schemaName == null || schemaName.isNotEmpty()) {
      "SqlServiceSettings.schemaName must not be empty; use null to send no schema header"
    }
    settings.headers.keys.forEach { requireNotReservedHeader(it, "SqlServiceSettings.headers") }
    val restUrl = serviceUrlOf(settings, projectIdOfApp())
    val key = FirebaseSqlInstanceKey(app.name, schemaName, restUrl)

    while (true) {
      val current = state.value
      check(current is State.Open) { "FirebaseApp has been deleted" }
      // The cached instance keeps the settings that it was created with.
      current.instances[key]?.let {
        return it
      }

      val okHttpClient = current.okHttpClient ?: newOkHttpClient()
      val newInstance = newInstance(settings, restUrl, okHttpClient)
      val newState = State.Open(current.instances + (key to newInstance), okHttpClient)
      if (state.compareAndSet(current, newState)) {
        return newInstance
      }
      // Another call changed the state first, so the new instance was never returned: it is
      // terminated, and the next iteration sees the change. A client created for it needs no
      // shutdown: before its first call, it has no threads and no connections.
      newInstance.terminate()
    }
  }

  private fun newInstance(
    settings: SqlServiceSettings,
    restUrl: HttpUrl,
    okHttpClient: OkHttpClient
  ): FirebaseSqlImpl {
    val executor =
      OperationsExecutor(
        // Without a schema, no Accept-Profile or Content-Profile header is sent.
        schemaName = settings.schemaName,
        serviceUrl = restUrl,
        authDeferred = authDeferred,
        appCheckDeferred = appCheckDeferred,
        networkClient = OkHttpNetworkClient(okHttpClient),
        headers = settings.headers,
        appId = app.options.applicationId,
        blockingDispatcher = blockingDispatcher,
        nonBlockingDispatcher = nonBlockingDispatcher
      )
    return FirebaseSqlImpl(this, app, settings, executor)
  }

  /** Removes [instance] from the cache, so that [get] creates another instance for its settings. */
  fun remove(instance: FirebaseSqlImpl) {
    state.update { current ->
      if (current !is State.Open) return@update current
      val keys = current.instances.filterValues { it === instance }.keys
      check(keys.size <= 1) {
        "internal error: FirebaseSql instance $instance maps to ${keys.size} keys, but expected at " +
          "most 1"
      }
      if (keys.isEmpty()) current else current.copy(instances = current.instances - keys.single())
    }
  }

  /**
   * Terminates the instances, and returns once the shutdown of their client has started. Every call
   * does both, so that calls on several threads all return in the same state.
   */
  private fun close() {
    while (true) {
      val open =
        when (val current = state.value) {
          is State.Open -> current
          is State.Closing -> {
            current.instances.forEach(FirebaseSqlImpl::terminate)
            current.shutdownJob.start()
            return
          }
          State.Closed -> return
        }

      val instances = open.instances.values.toList()
      val okHttpClient = open.okHttpClient
      if (instances.isEmpty() && okHttpClient == null) {
        state.compareAndSet(open, State.Closed)
        continue
      }

      // Once the requests of the instances have ended, the threads and the connections of the
      // client are released in the background, because closing a connection can write to the
      // network.
      @OptIn(DelicateCoroutinesApi::class)
      val shutdownJob =
        GlobalScope.launch(blockingDispatcher, start = CoroutineStart.LAZY) {
          instances.forEach { it.suspendingTerminate() }
          if (okHttpClient != null) {
            okHttpClient.dispatcher.executorService.shutdown()
            okHttpClient.connectionPool.evictAll()
          }
        }
      val onCompletion = shutdownJob.invokeOnCompletion { state.value = State.Closed }
      if (!state.compareAndSet(open, State.Closing(instances, shutdownJob))) {
        // Another call changed the state first, so this job never starts.
        onCompletion.dispose()
        shutdownJob.cancel()
      }
    }
  }

  /**
   * Returns the project ID in the options of the [app], which names the project of every service
   * that the instances of this component send their requests to.
   *
   * @throws IllegalArgumentException When the options have no project ID, or a blank one.
   */
  private fun projectIdOfApp(): String {
    val projectId = app.options.projectId
    require(!projectId.isNullOrBlank()) {
      "The FirebaseOptions of the FirebaseApp named '${app.name}' must have a project ID, but " +
        "FirebaseOptions.projectId is ${projectId?.let { "'$it'" }}; add the project ID to " +
        "google-services.json, or set it with FirebaseOptions.Builder.setProjectId()"
    }
    return projectId
  }
}

/** The host of the production service. */
private const val PROD_HOST = "firebasedataconnect.googleapis.com"

/**
 * Returns the URL of the service that the [settings] name in the project [projectId]:
 * `<scheme>://<host>[:<port>]` followed by
 * `/v1/projects/<projectId>/locations/<location>/services/<serviceId>/postgrest`.
 *
 * - Without a host, the production host is used. OkHttp parses `<scheme>://<host>`, and the host
 * must give only a host name or an IP address, with an optional port.
 * - The scheme is `http` when [SqlServiceSettings.sslEnabled] is `false`, and `https` otherwise.
 * - The port, when set, replaces the port of the host.
 *
 * Each ID is percent-encoded as one path segment.
 *
 * @throws IllegalArgumentException When the settings do not name a valid service.
 */
internal fun serviceUrlOf(settings: SqlServiceSettings, projectId: String): HttpUrl {
  require(settings.serviceId.isNotEmpty()) { "SqlServiceSettings.serviceId must not be empty" }
  require(settings.location.isNotEmpty()) { "SqlServiceSettings.location must not be empty" }

  val host = settings.host ?: PROD_HOST
  require(host.isNotEmpty()) {
    "SqlServiceSettings.host must not be empty; use null for the production host"
  }
  val scheme = if (settings.sslEnabled == false) "http" else "https"
  // OkHttp parses the host and its port. The parsed URL must have nothing else, because a URL
  // parser reads a scheme in the host as a host: `https://my.proxy` would be the host `https`.
  val base =
    "$scheme://$host".toHttpUrlOrNull()?.takeIf {
      it.encodedPath == "/" &&
        it.query == null &&
        it.fragment == null &&
        it.username.isEmpty() &&
        it.password.isEmpty()
    }
  requireNotNull(base) {
    "SqlServiceSettings.host must be a host name or an IP address with an optional port, for " +
      "example $PROD_HOST or 10.0.2.2:9399, without a scheme, a path, a query, or a fragment, " +
      "but is '$host'; SqlServiceSettings.sslEnabled chooses between https and http"
  }

  // The backend serves the tables and functions of a service under its `postgrest` path.
  val builder = base.newBuilder()
  // OkHttp rejects a port that is not from 1 to 65535 with an IllegalArgumentException.
  settings.port?.let { builder.port(it) }
  // addPathSegment() replaces the empty path segment of the base URL.
  return builder
    .addPathSegment("v1")
    .addPathSegment("projects")
    .addPathSegment(projectId)
    .addPathSegment("locations")
    .addPathSegment(settings.location)
    .addPathSegment("services")
    .addPathSegment(settings.serviceId)
    .addPathSegment("postgrest")
    .build()
}
