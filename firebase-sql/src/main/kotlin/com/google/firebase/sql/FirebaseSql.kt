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
import com.google.firebase.sql.builders.FilterBuilder
import com.google.firebase.sql.builders.QueryBuilder
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.serializer

/**
 * The entry point of Firebase SQL: sends PostgREST requests to the Cloud SQL for PostgreSQL
 * database of a Firebase SQL service.
 *
 * Get an instance with `Firebase.sql(settings)`, or with [getInstance].
 *
 * Start each request with [from] for a table or a view, or with [rpc] for a PostgreSQL function,
 * add filters and modifiers, then call `execute()`, which sends the request and returns an
 * [OperationResult]. It throws a [FirebaseSqlException] when the server rejects the request, and an
 * `IOException` when no response arrives. Each call of the fluent API adds one part of the
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
 * val sql =
 *   Firebase.sql(SqlServiceSettings(serviceId = "my-service", location = "us-central1"))
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
   * The settings that created this instance, as they were given: a property that was not set stays
   * `null`, for example [SqlServiceSettings.host] for the production host.
   *
   * When [getInstance] returns this instance for other settings that resolve to the same URL and
   * schema, this property keeps the settings of the call that created the instance. The settings
   * have no project ID: the project ID of the service is always the one in the options of the [app]
   * .
   */
  public val settings: SqlServiceSettings

  /**
   * Starts a request on the table or view named [table].
   *
   * Choose the operation on the returned [QueryBuilder]: [QueryBuilder.select] to read rows, or
   * [QueryBuilder.insert], [QueryBuilder.upsert], [QueryBuilder.update], or [QueryBuilder.delete]
   * to change them. Then add filters and modifiers, and call `execute()`.
   *
   * The table must be in the schema of this instance (see [SqlServiceSettings.schemaName]). Its
   * name is case-sensitive, and PostgreSQL stores unquoted names in lowercase. The name is sent as
   * one segment of the URL path, so it can contain any character, for example a space or a `/`.
   *
   * PostgREST: `/<table>`, for example `GET /products?select=*`
   *
   * **Example: read rows with a filter, an order, and a limit**
   * ```kotlin
   * // GET /products?select=*&category_id=eq.1&limit=10&order=price.asc
   * val products =
   *   sql
   *     .from("products")
   *     .select()
   *     .eq("category_id", 1)
   *     .order("price")
   *     .limit(10)
   *     .execute()
   *     .decodeList<Product>()
   * ```
   *
   * **Example: read rows with the row that they reference**
   * ```kotlin
   * @Serializable data class ProductName(val name: String, val categories: Category? = null)
   *
   * // Embeds the row of categories that the foreign key category_id references.
   * val names =
   *   sql
   *     .from("products")
   *     .select("name, categories(id, name)")
   *     .execute()
   *     .decodeList<ProductName>()
   * ```
   *
   * Response:
   * ```json
   * [{"name": "Apple", "categories": {"id": 1, "name": "Fruit"}}]
   * ```
   *
   * **Example: insert, update, and delete a row**
   * ```kotlin
   * // POST /products
   * sql
   *   .from("products")
   *   .insert(Product(id = 43, name = "Kiwi", price = 0.5))
   *   .execute()
   *
   * // PATCH /products?id=eq.43
   * sql.from("products").update(mapOf("price" to 0.45)).eq("id", 43).execute()
   *
   * // DELETE /products?id=eq.43
   * sql.from("products").delete().eq("id", 43).execute()
   * ```
   *
   * @param table The name of the table or view. It must not be empty, `.`, or `..`.
   * @return A [QueryBuilder] for [table].
   * @throws IllegalArgumentException If [table] is empty, `.`, or `..`.
   * @throws IllegalStateException If this instance is terminated.
   */
  public fun from(table: String): QueryBuilder

  /**
   * Calls the PostgreSQL function named [functionName].
   *
   * By default, the function is called with an HTTP POST request whose body is the JSON object of
   * the arguments; a call without arguments sends `{}`. A function that returns one value answers
   * with its JSON value, which `OperationResult.decodeAs<T>()` decodes. A function that returns
   * rows, with `returns setof` or `returns table`, can be filtered, ordered, and paginated like a
   * table, with the methods of the returned [FilterBuilder].
   *
   * With [RpcMethod.GET] or [RpcMethod.HEAD] as the [method], each argument is sent as a query
   * parameter with its name instead. A `null` argument is left out, so that the function uses the
   * default value of the parameter. A list is sent as a PostgreSQL array literal, for example
   * `{1,2}`, and an object as its JSON text. PostgREST runs GET and HEAD requests in a read-only
   * transaction, so a function that changes data fails with the PostgreSQL error `25006`, and must
   * be called with POST. Use POST too when an argument would not reach the function:
   * - a value that starts like a filter, for example `eq.1`, which PostgREST reads as a filter on
   * the result;
   * - an argument named `select`, `columns`, `on_conflict`, `order`, `limit`, `offset`, `and`, or
   * `or`, which PostgREST reads as that query parameter.
   *
   * With GET or HEAD, an argument name must not start with `$`, because the server drops such query
   * parameters: `execute()` throws an [IllegalArgumentException] for it.
   *
   * A call with GET or HEAD is retried up to 3 times after a transient failure, like the other GET
   * and HEAD requests, unless the request calls `noRetry()`. A POST call is not retried.
   *
   * PostgREST: `POST /rpc/<functionName>` with the arguments as a JSON object, or `GET
   * /rpc/<functionName>?<argument>=<value>` with [RpcMethod.GET], or `HEAD` with [RpcMethod.HEAD]
   *
   * **Example: call a function that returns a value**
   * ```kotlin
   * // POST /rpc/add_numbers with the body {"a":2,"b":3}
   * val sum = sql.rpc("add_numbers", mapOf("a" to 2, "b" to 3)).execute().decodeAs<Int>()
   * ```
   *
   * Response:
   * ```json
   * 5
   * ```
   *
   * **Example: filter and order the rows that a function returns**
   * ```kotlin
   * // POST /rpc/products_under?category_id=eq.1&order=price.asc with the body {"max_price":10}
   * val cheapFruit =
   *   sql
   *     .rpc("products_under", mapOf("max_price" to 10))
   *     .eq("category_id", 1)
   *     .order("price")
   *     .execute()
   *     .decodeList<Product>()
   * ```
   *
   * **Example: pass the arguments as a class**
   *
   * The `rpc` extension function of this package takes an object of a class annotated with
   * `@Serializable`, whose properties are the arguments.
   * ```kotlin
   * @Serializable data class PriceLimit(@SerialName("max_price") val maxPrice: Double)
   *
   * val cheap =
   *   sql.rpc("products_under", PriceLimit(maxPrice = 10.0)).execute().decodeList<Product>()
   * ```
   *
   * **Example: call a function with GET, and count its rows**
   * ```kotlin
   * // GET /rpc/products_under?max_price=10&limit=5 with the header Prefer: count=exact
   * val result =
   *   sql
   *     .rpc(
   *       "products_under",
   *       mapOf("max_price" to 10),
   *       method = RpcMethod.GET,
   *       count = Count.EXACT
   *     )
   *     .limit(5)
   *     .execute()
   * val firstProducts = result.decodeList<Product>() // At most 5 products.
   * val total = result.countOrNull() // Every product under 10, not only the first 5.
   * ```
   *
   * **Example: count the rows of a function without reading them**
   * ```kotlin
   * // HEAD /rpc/products_under?max_price=10 with the header Prefer: count=exact
   * val result =
   *   sql
   *     .rpc(
   *       "products_under",
   *       mapOf("max_price" to 10),
   *       method = RpcMethod.HEAD,
   *       count = Count.EXACT
   *     )
   *     .execute()
   * val numberOfProducts = result.countOrNull()
   * ```
   *
   * @param functionName The name of the function. It is case-sensitive, and the function must be in
   * the schema of this instance. It is sent as one segment of the URL path, so it must not be
   * empty, `.`, or `..`.
   * @param params The arguments of the function, by name, for example a kotlinx.serialization
   * `JsonObject`. A value can be a `String`, a number, a `Boolean`, `null`, an enum (sent as its
   * name), a `UUID`, a `Date`, a `ByteArray` (sent as a `bytea` in hex), a `List`, a `Map`, or a
   * `JsonElement`. An empty map, the default, calls the function without arguments. To pass the
   * arguments as an object of a class annotated with `@Serializable`, call the `rpc` extension
   * function.
   * @param method The HTTP method of the call: [RpcMethod.POST], the default, sends the arguments
   * as a JSON object in the body; [RpcMethod.GET] sends them as query parameters, for a function
   * that does not change data; [RpcMethod.HEAD] sends them like GET, and the response has no body,
   * so use it with [count] to get only the number of rows. See [RpcMethod].
   * @param count The algorithm with which the server counts the rows that the function returns,
   * sent in the `Prefer` header, for example `Prefer: count=exact`. The count is in
   * `OperationResult.countOrNull()`. `null`, the default, counts nothing. See [Count].
   * @return A [FilterBuilder] for the result of the function.
   * @throws IllegalArgumentException If [functionName] is empty, `.`, or `..`, or if a value of
   * [params] cannot be encoded to JSON, for example a `Double` that is not finite.
   * @throws IllegalStateException If this instance is terminated.
   */
  public fun rpc(
    functionName: String,
    params: Map<String, Any?> = emptyMap(),
    method: RpcMethod = RpcMethod.POST,
    count: Count? = null
  ): FilterBuilder

  /**
   * Calls the PostgreSQL function named [functionName] with the arguments in [params], encoded to a
   * JSON object with [serializer]: each property is an argument, by its serial name. A `null`
   * [params] calls the function without arguments. See the `rpc` that takes a `Map` for the
   * details.
   *
   * The [rpc] extension function finds the serializer when the app is compiled, so
   * `rpc("products_under", limit)` sends the same request as `rpc("products_under", limit,
   * PriceLimit.serializer())`.
   *
   * @param functionName The name of the function. See the `rpc` that takes a `Map`.
   * @param params The arguments, for example an object of a class annotated with `@Serializable`.
   * @param serializer Encodes [params], for example `PriceLimit.serializer()`.
   * @param method The HTTP method of the call: [RpcMethod.POST], the default, [RpcMethod.GET], or
   * [RpcMethod.HEAD]. See the `rpc` that takes a `Map`.
   * @param count The algorithm with which the server counts the rows that the function returns.
   * `null`, the default, counts nothing. See [Count].
   * @return A [FilterBuilder] for the result of the function.
   * @throws IllegalArgumentException If [functionName] is empty, `.`, or `..`, or if [serializer]
   * does not encode [params] as a JSON object or `null`, for example for a list.
   * @throws IllegalStateException If this instance is terminated.
   */
  public fun <T> rpc(
    functionName: String,
    params: T,
    serializer: SerializationStrategy<T>,
    method: RpcMethod = RpcMethod.POST,
    count: Count? = null
  ): FilterBuilder

  /**
   * Terminates this instance, cancels its requests in flight, and removes it from the cache. It
   * returns at once, without waiting for the requests in flight to end: [suspendingTerminate] waits
   * for them.
   *
   * After this call:
   * - [from] and [rpc] throw an [IllegalStateException], and so does `execute()` on every builder
   * of this instance, including the builders created earlier.
   * - A request in flight stops, whether it is getting its tokens, waiting for the server, or
   * waiting before a retry. Its `execute()` throws an [IllegalStateException] that says that the
   * instance is terminated.
   * - [getInstance] with the same settings creates another instance, which works normally.
   *
   * Calling [terminate] again has no effect. Terminating an instance does not terminate the other
   * instances. Deleting the [app] terminates all its instances.
   *
   * [getInstance] returns the cached instance with its own settings until it is terminated, so
   * terminate an instance to change its headers.
   *
   * **Example: change the headers of an instance**
   * ```kotlin
   * sql.terminate()
   * val withHeader =
   *   Firebase.sql(
   *     sql.app,
   *     sql.settings.copy(headers = mapOf("X-Request-Source" to "sync")),
   *   )
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
     * Returns the [FirebaseSql] instance of the default [FirebaseApp] for the given [settings], and
     * creates it on the first call.
     *
     * `Firebase.sql(settings)` is a shorthand for this call.
     *
     * Instances are cached by app, by the PostgREST URL that the settings resolve to, and by
     * schema:
     * - A call whose settings resolve to the same URL and schema as an open instance returns that
     * instance, which keeps the settings that created it. For example, settings without a host and
     * settings with the production host resolve to the same URL. To use other headers, call
     * [FirebaseSql.terminate] on that instance first, then call [getInstance] again.
     * - Settings with `schemaName = null` and settings with `schemaName = "public"` give two
     * different instances, even when they reach the same schema. To query another schema, call
     * [getInstance] with its name in [SqlServiceSettings.schemaName].
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
     * The settings are checked on every call, also when the instance is cached.
     *
     * **Example: the production service**
     * ```kotlin
     * val sql =
     *   FirebaseSql.getInstance(
     *     SqlServiceSettings(serviceId = "my-service", location = "us-central1")
     *   )
     * ```
     *
     * **Example: a local server, from the Android emulator**
     * ```kotlin
     * // 10.0.2.2 is the address of the computer that runs the Android emulator.
     * val local =
     *   FirebaseSql.getInstance(
     *     SqlServiceSettings(
     *       serviceId = "my-service",
     *       location = "us-central1",
     *       host = "10.0.2.2",
     *       port = 9399,
     *       sslEnabled = false,
     *     )
     *   )
     * ```
     *
     * **Example: the cache**
     * ```kotlin
     * val settings = SqlServiceSettings(serviceId = "my-service", location = "us-central1")
     * val sql = FirebaseSql.getInstance(settings)
     * val sync = mapOf("X-Request-Source" to "sync")
     *
     * // The same URL and schema give the same instance, with its own settings.
     * val same =
     *   FirebaseSql.getInstance(settings.copy(host = "firebasedataconnect.googleapis.com"))
     * // This instance still sends no custom header.
     * val alsoSame = FirebaseSql.getInstance(settings.copy(headers = sync))
     *
     * // Another schema gives another instance.
     * val inventory = FirebaseSql.getInstance(settings.copy(schemaName = "inventory"))
     *
     * // To change the headers, terminate the instance first.
     * sql.terminate()
     * val withHeader = FirebaseSql.getInstance(settings.copy(headers = sync))
     * ```
     *
     * @param settings The service, and how to send it the requests. See [SqlServiceSettings].
     * @return The cached instance for these settings, or a new instance.
     * @throws ReservedHeaderException When [SqlServiceSettings.headers] has a header that the SDK
     * reserves, such as `Authorization`.
     * @throws IllegalArgumentException When a property of [settings] breaks the constraint that
     * [SqlServiceSettings] documents for it: an empty service ID, location, or schema name, or an
     * invalid host or port. Also when the options of the default app have no project ID.
     * @throws IllegalStateException When the default [FirebaseApp] is not initialized, or was
     * deleted.
     */
    public fun getInstance(settings: SqlServiceSettings): FirebaseSql =
      getInstance(FirebaseApp.getInstance(), settings)

    /**
     * Returns the [FirebaseSql] instance of [app] for the given [settings], and creates it on the
     * first call.
     *
     * `Firebase.sql(app, settings)` is a shorthand for this call.
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
     * Instances are cached by app, by the PostgREST URL that the settings resolve to, and by
     * schema:
     * - A call whose settings resolve to the same URL and schema as an open instance returns that
     * instance, which keeps the settings that created it. For example, settings without a host and
     * settings with the production host resolve to the same URL. To use other headers, call
     * [FirebaseSql.terminate] on that instance first, then call [getInstance] again.
     * - Settings with `schemaName = null` and settings with `schemaName = "public"` give two
     * different instances, even when they reach the same schema. To query another schema, call
     * [getInstance] with its name in [SqlServiceSettings.schemaName].
     *
     * The settings are checked on every call, also when the instance is cached.
     *
     * **Example: an app other than the default app**
     * ```kotlin
     * val otherApp = FirebaseApp.getInstance("other")
     * val otherSql =
     *   FirebaseSql.getInstance(
     *     otherApp,
     *     SqlServiceSettings(serviceId = "my-service", location = "us-central1"),
     *   )
     * ```
     *
     * @param app The app whose project ID and tokens the instance uses.
     * @param settings The service, and how to send it the requests. See [SqlServiceSettings].
     * @return The cached instance for this app and these settings, or a new instance.
     * @throws ReservedHeaderException When [SqlServiceSettings.headers] has a header that the SDK
     * reserves, such as `Authorization`.
     * @throws IllegalArgumentException When a property of [settings] breaks the constraint that
     * [SqlServiceSettings] documents for it: an empty service ID, location, or schema name, or an
     * invalid host or port. Also when the options of [app] have no project ID.
     * @throws IllegalStateException When [app] was deleted.
     */
    @android.annotation.SuppressLint("FirebaseUseExplicitDependencies")
    public fun getInstance(app: FirebaseApp, settings: SqlServiceSettings): FirebaseSql =
      app.get(FirebaseSqlComponent::class.java).get(settings)
  }
}

/**
 * Calls the PostgreSQL function named [functionName] with the arguments in [params], an object of a
 * class annotated with `@Serializable`: each property is an argument, by its serial name.
 *
 * It calls [FirebaseSql.rpc] with the serializer of the type [T], which the compiler finds, so that
 * no class is looked up by reflection when the app runs. See the `rpc` that takes a `Map` for the
 * details.
 *
 * **Example: pass the arguments as a class**
 * ```kotlin
 * @Serializable data class PriceLimit(@SerialName("max_price") val maxPrice: Double)
 *
 * val cheap =
 *   sql.rpc("products_under", PriceLimit(maxPrice = 10.0)).execute().decodeList<Product>()
 * ```
 *
 * @param functionName The name of the function. See [FirebaseSql.rpc].
 * @param params The arguments of the function.
 * @param method The HTTP method of the call: [RpcMethod.POST], the default, [RpcMethod.GET], or
 * [RpcMethod.HEAD]. See [FirebaseSql.rpc].
 * @param count The algorithm with which the server counts the rows that the function returns.
 * `null`, the default, counts nothing. See [Count].
 * @return A [FilterBuilder] for the result of the function.
 * @throws IllegalArgumentException If [functionName] is empty, `.`, or `..`, or if [params] is not
 * encoded as a JSON object or `null`, for example a list.
 * @throws IllegalStateException If the instance is terminated.
 */
public inline fun <reified T> FirebaseSql.rpc(
  functionName: String,
  params: T,
  method: RpcMethod = RpcMethod.POST,
  count: Count? = null
): FilterBuilder = rpc(functionName, params, serializer<T>(), method, count)
