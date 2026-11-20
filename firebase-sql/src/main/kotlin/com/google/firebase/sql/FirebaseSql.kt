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
import com.google.firebase.annotations.InternalForInheritanceFirebaseApi

/**
 * The entry point of Firebase SQL: sends PostgREST requests to the Cloud SQL for PostgreSQL
 * database of a Firebase SQL service.
 *
 * Get an instance with `Firebase.sql(config)`, or with [getInstance]. To send custom headers with
 * every request, call [setHeaders] before the first request.
 *
 * Start each request with `from()` for a table or a view, or with `rpc()` for a PostgreSQL
 * function, add filters and modifiers, then call `execute()`, which sends the request and returns
 * an [OperationResult]. It throws a [FirebaseSqlException] when the server rejects the request, and
 * an `IOException` when no response arrives. Each call of the fluent API adds one part of the
 * PostgREST request, so filters, modifiers, and errors behave as described in the
 * [PostgREST API reference](https://docs.postgrest.org/en/stable/references/api.html).
 *
 * Every request carries the tokens of the [app]:
 * - The Firebase Authentication ID token of the signed-in user, in the `x-firebase-auth-token`
 * header. Without a signed-in user, or when the app does not use Firebase Authentication, the
 * request is sent without a token, and the server decides what a signed-out user can do. When the
 * token of the signed-in user cannot be retrieved, the request is not sent, so that it does not run
 * with the permissions of a signed-out user: `execute()` throws the error of Firebase
 * Authentication.
 * - The Firebase App Check token, in the `X-Firebase-AppCheck` header, when the app uses Firebase
 * App Check. When App Check fails to provide a valid token, the request carries the placeholder
 * token that App Check returns with its error, and the server decides whether to accept the
 * request.
 *
 * When the server answers `401` because it rejects a token of a request, the SDK refreshes the
 * tokens of the request and sends it once more, even a write: the server does not run a request
 * whose token it rejects. The requests that the server rejects with the same token share one
 * refresh. A `401` that PostgREST answers for a database error, for example `42501` for a request
 * without a signed-in user that the database does not allow, is returned as it is.
 *
 * The API is Kotlin-only: it is built on Kotlin coroutines, so `execute()` is a `suspend` function,
 * and on kotlinx.serialization, which encodes the request bodies and decodes the results into
 * classes annotated with `@Serializable`. The app adds both libraries, and applies the
 * `org.jetbrains.kotlin.plugin.serialization` Gradle plugin.
 *
 * An instance can be shared by several threads and coroutines. `execute()` suspends without
 * blocking its thread, and cancelling the coroutine that calls it cancels the request.
 *
 * Later versions can add members to this interface, so implementing it requires an opt-in to
 * [InternalForInheritanceFirebaseApi]. In tests, mock it instead.
 *
 * **Example: read rows**
 * ```kotlin
 * val sql = Firebase.sql(SqlServiceConfig(serviceId = "my-service", location = "us-central1"))
 *
 * try {
 *   val products = sql.from("products").select().lt("price", 10).execute().decodeList<Product>()
 * } catch (e: FirebaseSqlException) {
 *   Log.w("Products", "The server rejected the request", e)
 * } catch (e: IOException) {
 *   Log.w("Products", "No response", e)
 * }
 * ```
 *
 * **Example: insert a row, then call a function**
 * ```kotlin
 * sql
 *   .from("products")
 *   .insert(Product(id = 43, name = "Kiwi", price = 0.5, categoryId = 1))
 *   .execute()
 *
 * val sum = sql.rpc("add_numbers", mapOf("a" to 2, "b" to 3)).execute().decodeAs<Int>() // 5
 * ```
 *
 * **Example: send a custom header with every request**
 * ```kotlin
 * val sql = Firebase.sql(SqlServiceConfig(serviceId = "my-service", location = "us-central1"))
 * // Before the first request. Setting the same headers again later has no effect.
 * sql.setHeaders(mapOf("X-Request-Source" to "sync"))
 *
 * val products = sql.from("products").select().execute().decodeList<Product>()
 * ```
 *
 * When the headers can differ between the calls that get the instance, check [isMutable] before
 * [setHeaders]: once the instance is in use, [setHeaders] throws a [FirebaseSqlMutationException]
 * for other headers.
 */
@SubclassOptInRequired(InternalForInheritanceFirebaseApi::class)
public interface FirebaseSql {
  /**
   * The [FirebaseApp] of this instance. The project ID of the service comes from its
   * `FirebaseOptions`, and the requests carry its Firebase Authentication and Firebase App Check
   * tokens.
   */
  public val app: FirebaseApp

  /**
   * The config that created this instance, as it was given: a property that was not set stays
   * `null`, for example [SqlServiceConfig.host] for the production host.
   *
   * When [getInstance] returns this instance for another config that resolves to the same URL and
   * schema, ignoring case, this property keeps the config of the call that created the instance.
   * The config has no project ID: the project ID of the service is always the one in the options of
   * the [app].
   */
  public val config: SqlServiceConfig

  /**
   * Whether the setters of this instance, such as [setHeaders], can still change its settings.
   *
   * It is `true` for a new instance, and becomes `false` on the first call of `from()`, `rpc()`,
   * [terminate], or [suspendingTerminate]: from then on, the requests of the instance use its
   * settings, or the instance is terminated. It never becomes `true` again. A call of `from()` or
   * `rpc()` that throws because of its arguments, such as `from("")`, does not count.
   *
   * [getInstance] returns the same instance for equal configs, so another part of the app can have
   * used it already, with its own settings. When a setting can differ between the calls that get
   * the instance, check this property before its setter, which throws a
   * [FirebaseSqlMutationException] for another value once the instance is in use. A setter called
   * with the current value never throws, so it needs no check.
   *
   * **Example: set the headers unless the instance is already in use**
   * ```kotlin
   * val sql = Firebase.sql(SqlServiceConfig(serviceId = "my-service", location = "us-central1"))
   * if (sql.isMutable) {
   *   sql.setHeaders(mapOf("X-Request-Source" to "sync"))
   * }
   * ```
   */
  public val isMutable: Boolean

  /**
   * Sets the custom headers that every request of this instance sends. Call it before the first
   * request: the first call of `from()`, `rpc()`, [terminate], or [suspendingTerminate] freezes the
   * settings of the instance, and [isMutable] becomes `false`.
   *
   * Each call replaces the headers of the earlier calls, and an empty map removes them. The map is
   * copied, so later changes to it have no effect. Header names are compared ignoring case. A
   * header that a request sets wins over the header with the same name here: a header set with
   * `setHeader()`, and the `Accept` header that the SDK computes for a request, for example for
   * `single()`. `Prefer` is merged instead: its preferences here are added after those of the
   * request, which the SDK computes, for example for a count, or which `setHeader()` sets, so that
   * the preferences of the request win.
   *
   * Once [isMutable] is `false`, a call with headers equal to the current headers has no effect, so
   * code that runs again, such as `onCreate()` after a rotation, can set the same headers safely.
   * Headers are equal when they have the same names, ignoring case, and the same values, ignoring
   * the whitespace at their start and end. A call with other headers throws a
   * [FirebaseSqlMutationException]. To send other headers, set a header for one request with
   * `setHeader()`, or terminate this instance and set the headers on the new instance that
   * [getInstance] returns.
   *
   * It can be called from any thread. When another thread calls `from()` or `rpc()` at the same
   * time, the headers change either before the instance uses them, or not at all: [setHeaders] then
   * throws for other headers, even right after [isMutable] returned `true`. The headers never
   * change once the instance is in use.
   *
   * **Example: send a custom header with every request**
   * ```kotlin
   * val sql = Firebase.sql(SqlServiceConfig(serviceId = "my-service", location = "us-central1"))
   * sql.setHeaders(mapOf("X-Request-Source" to "sync"))
   *
   * // GET /products?select=* with the header X-Request-Source: sync
   * val products = sql.from("products").select().execute().decodeList<Product>()
   * ```
   *
   * @param headers The custom headers, by name. `Authorization`, the headers that the SDK sets
   * itself, and the headers that Firebase reserves are not allowed, in any case: the tokens
   * (`x-firebase-auth-token` and `X-Firebase-AppCheck`), the schema (`Accept-Profile` and
   * `Content-Profile`), the versions and the app ID (`x-goog-api-client`, `x-client-version`, and
   * `x-firebase-gmpid`), `X-Retry-Count`, the routing headers (`x-firebase-sqlconnect-affinity` and
   * `x-goog-request-params`), and the heartbeat headers (`x-firebase-client` and
   * `x-firebase-client-log-type`). Choose the schema with [SqlServiceConfig.schemaName].
   * @throws ReservedHeaderException If [headers] has a header that the SDK reserves, whether or not
   * this instance is mutable. It is an `IllegalArgumentException`.
   * @throws IllegalArgumentException If [headers] has a name or a value that HTTP does not allow,
   * whether or not this instance is mutable: an empty name, a name with a space or a character
   * outside visible ASCII, or a value with a line break or a character outside ASCII, such as an
   * accented letter.
   * @throws FirebaseSqlMutationException If [isMutable] is `false`, and [headers] are not equal to
   * the current headers. It is an `IllegalStateException`.
   */
  public fun setHeaders(headers: Map<String, String>)

  /**
   * Terminates this instance, cancels its requests in flight, and removes it from the cache. It
   * returns at once, without waiting for the requests in flight to end: [suspendingTerminate] waits
   * for them.
   *
   * After this call:
   * - `from()` and `rpc()` throw an [IllegalStateException], and so does `execute()` on every
   * builder of this instance, including the builders created earlier.
   * - A request in flight stops, whether it is getting its tokens, waiting for the server, or
   * waiting before a retry. Its `execute()` throws an [IllegalStateException] that says that the
   * instance is terminated.
   * - [isMutable] is `false`, so [setHeaders] throws a [FirebaseSqlMutationException] for headers
   * that differ from the headers of this instance.
   * - [getInstance] with the same config creates another instance, which works normally.
   *
   * Calling [terminate] again has no effect. Terminating an instance does not terminate the other
   * instances. Deleting the [app] terminates all its instances.
   *
   * The headers of an instance can no longer change once it is in use, so terminate it to change
   * them: the new instance that [getInstance] returns has no custom headers until [setHeaders] sets
   * them.
   *
   * **Example: change the headers of an instance that is in use**
   * ```kotlin
   * sql.terminate()
   * val withHeader = Firebase.sql(sql.app, sql.config)
   * withHeader.setHeaders(mapOf("X-Request-Source" to "sync"))
   * ```
   */
  public fun terminate()

  /**
   * Terminates this instance like [terminate], and returns when its requests in flight have ended.
   *
   * Calling it again, or after [terminate], waits for the same requests.
   */
  public suspend fun suspendingTerminate()

  /** Gets the [FirebaseSql] instances with [getInstance], or its shorthand `Firebase.sql()`. */
  public companion object {
    /**
     * Returns the [FirebaseSql] instance of the default [FirebaseApp] for the given [config], and
     * creates it on the first call.
     *
     * `Firebase.sql(config)` is a shorthand for this call.
     *
     * Instances are cached by app, by the PostgREST URL that the config resolves to, and by schema,
     * ignoring case:
     * - A call whose config resolves to the same URL and schema, ignoring case, as an open instance
     * returns that instance, which keeps the config that created it, and the headers that
     * [setHeaders] set on it. For example, a config without a host and a config with the production
     * host resolve to the same URL, and `"my-service"` and `"My-Service"` match.
     * - A config with `schemaName = null` and a config with `schemaName = "public"` give two
     * different instances, even when they reach the same schema. To query another schema, call
     * [getInstance] with its name in [SqlServiceConfig.schemaName].
     *
     * A new instance sends no custom header. To send custom headers with every request, call
     * [setHeaders] on the instance before its first request.
     *
     * The project ID of the service is the one in the `FirebaseOptions` of the default app, which
     * come from the `google-services.json` file of the app. The default app is initialized
     * automatically when the app has a `google-services.json` file and applies the
     * `com.google.gms.google-services` Gradle plugin.
     *
     * The requests carry the tokens of the default app: the Firebase Authentication ID token of the
     * signed-in user, if any, and the Firebase App Check token, if the app uses App Check. Without
     * a signed-in user, the requests are sent without a token. When the server answers `401` to a
     * request that carried a token, the SDK sends that request once more with refreshed tokens,
     * even a write: the server does not run a request whose token it rejects. See [FirebaseSql].
     *
     * The config is checked on every call, also when the instance is cached.
     *
     * **Example: the production service**
     * ```kotlin
     * val sql =
     *   FirebaseSql.getInstance(
     *     SqlServiceConfig(serviceId = "my-service", location = "us-central1")
     *   )
     * ```
     *
     * **Example: a local server, from the Android emulator**
     * ```kotlin
     * // 10.0.2.2 is the address of the computer that runs the Android emulator.
     * val local =
     *   FirebaseSql.getInstance(
     *     SqlServiceConfig(
     *       serviceId = "my-service",
     *       location = "us-central1",
     *       host = "10.0.2.2",
     *       port = 9399,
     *       sslEnabled = false,
     *     )
     *   )
     * ```
     *
     * **Example: the configs that share an instance**
     * ```kotlin
     * val config = SqlServiceConfig(serviceId = "my-service", location = "us-central1")
     * val sql = FirebaseSql.getInstance(config)
     * sql.setHeaders(mapOf("X-Request-Source" to "sync"))
     *
     * // The same URL and schema give the same instance, which sends the same headers.
     * val same = FirebaseSql.getInstance(config.copy(host = "firebasedataconnect.googleapis.com"))
     *
     * // Another schema gives another instance, without custom headers.
     * val inventory = FirebaseSql.getInstance(config.copy(schemaName = "inventory"))
     * ```
     *
     * @param config The service, and how to send it the requests. See [SqlServiceConfig].
     * @return The cached instance for this config, or a new instance.
     * @throws IllegalArgumentException When a property of [config] breaks the constraint that
     * [SqlServiceConfig] documents for it: an empty service ID, location, or schema name, an
     * invalid host or port, or SSL turned off for the production host. Also when the options of the
     * default app have no project ID.
     * @throws IllegalStateException When the default [FirebaseApp] is not initialized, or was
     * deleted.
     */
    public fun getInstance(config: SqlServiceConfig): FirebaseSql =
      getInstance(FirebaseApp.getInstance(), config)

    /**
     * Returns the [FirebaseSql] instance of [app] for the given [config], and creates it on the
     * first call.
     *
     * `Firebase.sql(app, config)` is a shorthand for this call.
     *
     * Use it for a [FirebaseApp] other than the default app. Each app has its own instances. The
     * project ID of the service is the one in the `FirebaseOptions` of [app].
     *
     * The requests carry the tokens of [app]: the Firebase Authentication ID token of its signed-in
     * user, if any, and its Firebase App Check token, if the app uses App Check. Without a
     * signed-in user, the requests are sent without a token. When the server answers `401` to a
     * request that carried a token, the SDK sends that request once more with refreshed tokens,
     * even a write: the server does not run a request whose token it rejects. See [FirebaseSql].
     *
     * Instances are cached by app, by the PostgREST URL that the config resolves to, and by schema,
     * ignoring case:
     * - A call whose config resolves to the same URL and schema, ignoring case, as an open instance
     * returns that instance, which keeps the config that created it, and the headers that
     * [setHeaders] set on it. For example, a config without a host and a config with the production
     * host resolve to the same URL, and `"my-service"` and `"My-Service"` match.
     * - A config with `schemaName = null` and a config with `schemaName = "public"` give two
     * different instances, even when they reach the same schema. To query another schema, call
     * [getInstance] with its name in [SqlServiceConfig.schemaName].
     *
     * A new instance sends no custom header. To send custom headers with every request, call
     * [setHeaders] on the instance before its first request.
     *
     * The config is checked on every call, also when the instance is cached.
     *
     * **Example: an app other than the default app**
     * ```kotlin
     * val otherApp = FirebaseApp.getInstance("other")
     * val otherSql =
     *   FirebaseSql.getInstance(
     *     otherApp,
     *     SqlServiceConfig(serviceId = "my-service", location = "us-central1"),
     *   )
     * ```
     *
     * @param app The app whose project ID and tokens the instance uses.
     * @param config The service, and how to send it the requests. See [SqlServiceConfig].
     * @return The cached instance for this app and this config, or a new instance.
     * @throws IllegalArgumentException When a property of [config] breaks the constraint that
     * [SqlServiceConfig] documents for it: an empty service ID, location, or schema name, an
     * invalid host or port, or SSL turned off for the production host. Also when the options of
     * [app] have no project ID.
     * @throws IllegalStateException When [app] was deleted.
     */
    @android.annotation.SuppressLint("FirebaseUseExplicitDependencies")
    public fun getInstance(app: FirebaseApp, config: SqlServiceConfig): FirebaseSql =
      app.get(FirebaseSqlComponent::class.java).get(config)
  }
}
