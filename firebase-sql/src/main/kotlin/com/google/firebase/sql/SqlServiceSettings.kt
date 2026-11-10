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

import java.util.Objects

/**
 * The settings of a [FirebaseSql] instance: the Firebase SQL service that it sends its requests to,
 * and how it sends them.
 *
 * Only [serviceId] and [location] are required. With the defaults, the requests use `https` and the
 * production host `firebasedataconnect.googleapis.com`, at the path
 * `/v1/projects/<projectId>/locations/<location>/services/<serviceId>/postgrest/`, followed by the
 * table name, or by `rpc/` and the function name. `<projectId>` is the project ID in the
 * `FirebaseOptions` of the `FirebaseApp` of the instance, which come from the
 * `google-services.json` file of the app: the settings have no project ID.
 *
 * [FirebaseSql.getInstance] checks the settings on every call, and throws an
 * [IllegalArgumentException] when a property breaks the constraint that it documents. Two settings
 * are equal when all their properties are equal.
 *
 * [FirebaseSql.getInstance] returns the same instance for all the settings that resolve to the same
 * URL and schema, and that instance keeps the settings that created it. To change the [headers] of
 * an instance, call [FirebaseSql.terminate] on it, then call [FirebaseSql.getInstance] again.
 *
 * **Example: the production service**
 * ```kotlin
 * val sql =
 *   Firebase.sql(SqlServiceSettings(serviceId = "my-service", location = "us-central1"))
 * ```
 *
 * **Example: a local server, from the Android emulator**
 * ```kotlin
 * // 10.0.2.2 is the address of the computer that runs the Android emulator.
 * val local =
 *   Firebase.sql(
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
 * On a physical device, use the IP address of the computer, or run `adb reverse tcp:9399 tcp:9399`
 * and use the host `127.0.0.1`. See [sslEnabled] for the cleartext traffic that `http` needs.
 *
 * @property serviceId The ID of the Firebase SQL service. It must not be empty.
 * @property location The region of the service, for example `us-central1`. It must not be empty.
 * @property schemaName The PostgreSQL schema of the requests. If `null`, the default, no schema
 * header is sent, and the server uses the default schema of the service. Otherwise every request
 * names the schema in the `Accept-Profile` header (GET and HEAD) or in the `Content-Profile` header
 * (POST, PATCH, PUT, and DELETE). The server must expose that schema; otherwise it answers every
 * request with the status `406` and the code `PGRST106`. It must not be empty. `null` and
 * `"public"` give two different instances, even when they reach the same schema. To query another
 * schema, call [FirebaseSql.getInstance] with a copy of the settings, for example
 * `settings.copy(schemaName = "inventory")`.
 * @property host The host of the service: a host name or an IP address, with an optional port, for
 * example `10.0.2.2:9399` or `localhost`. Write an IPv6 address in brackets, for example `[::1]`.
 * If `null`, the default, the production host `firebasedataconnect.googleapis.com` is used. It must
 * not be empty, and must not have a scheme, a path, a query, or a fragment: [sslEnabled] chooses
 * between `https` and `http`.
 * @property port The port of the service, from 1 to 65535. If `null`, the default, the port in
 * [host] is used, or else the default port of the scheme: 443 for `https`, and 80 for `http`. When
 * [host] has a port too, this port replaces it.
 * @property sslEnabled Whether the requests use `https` (`true`) or `http` (`false`). If `null`,
 * the default, they use `https`. Turn SSL off only for a local server, because `http` sends the
 * Firebase Authentication and Firebase App Check tokens in plain text. On Android 9 (API level 28)
 * and higher, the app must also allow cleartext traffic to that host, for example in its network
 * security configuration; otherwise the requests fail with a status of `0`.
 * @param headers Custom headers to send with every request of the instance. The map is copied.
 * Header names are compared ignoring case. A header that a request sets wins over the header with
 * the same name here: a header set with `setHeader()`, and the `Accept` header that the SDK
 * computes for a request, for example for `single()`. `Prefer` is merged instead: its preferences
 * here are added after those of the request, which the SDK computes, for example for a count, or
 * which `setHeader()` sets, so that the preferences of the request win. `Authorization`, the
 * headers that the SDK sets itself, and the headers that Firebase reserves are not allowed, in any
 * case: the tokens (`x-firebase-auth-token` and `X-Firebase-AppCheck`), the schema
 * (`Accept-Profile` and `Content-Profile`), the versions and the app ID (`x-goog-api-client`,
 * `x-client-version`, and `x-firebase-gmpid`), `X-Retry-Count`, the routing headers
 * (`x-firebase-sqlconnect-affinity` and `x-goog-request-params`), and the heartbeat headers
 * (`x-firebase-client` and `x-firebase-client-log-type`): [FirebaseSql.getInstance] throws a
 * [ReservedHeaderException] for them. Choose the schema with [schemaName].
 */
public class SqlServiceSettings(
  public val serviceId: String,
  public val location: String,
  public val schemaName: String? = null,
  headers: Map<String, String> = emptyMap(),
  public val host: String? = null,
  public val port: Int? = null,
  public val sslEnabled: Boolean? = null
) {
  /**
   * Custom headers to send with every request of the instance, empty by default.
   *
   * It is a copy of the map given to the constructor, so later changes to that map have no effect.
   * A header that a request sets, with `setHeader()` or computed by the SDK like `Accept`, wins
   * over the header with the same name here. A `Prefer` header here is added after the preferences
   * of the request instead, so that both apply, and those of the request win.
   * [FirebaseSql.getInstance] throws a [ReservedHeaderException] for `Authorization`, the headers
   * that the SDK sets itself, and the headers that Firebase reserves, in any case: the tokens
   * (`x-firebase-auth-token` and `X-Firebase-AppCheck`), the schema (`Accept-Profile` and
   * `Content-Profile`), the versions and the app ID (`x-goog-api-client`, `x-client-version`, and
   * `x-firebase-gmpid`), `X-Retry-Count`, the routing headers (`x-firebase-sqlconnect-affinity` and
   * `x-goog-request-params`), and the heartbeat headers (`x-firebase-client` and
   * `x-firebase-client-log-type`).
   */
  public val headers: Map<String, String> = headers.toMap()

  /**
   * Compares these settings with another object.
   *
   * Settings that are not equal can still give the same instance in [FirebaseSql.getInstance], for
   * example settings without a [host] and settings with the production host.
   *
   * @param other The object to compare with these settings.
   * @return `true` if, and only if, [other] is a [SqlServiceSettings] whose properties are all
   * equal to the properties of these settings.
   */
  override fun equals(other: Any?): Boolean =
    other is SqlServiceSettings &&
      other.serviceId == serviceId &&
      other.location == location &&
      other.schemaName == schemaName &&
      other.headers == headers &&
      other.host == host &&
      other.port == port &&
      other.sslEnabled == sslEnabled

  /** Returns a hash code of all the properties, consistent with [equals]. */
  override fun hashCode(): Int =
    Objects.hash(serviceId, location, schemaName, headers, host, port, sslEnabled)

  /**
   * Returns a text with the values of all the properties, for debugging. It includes the values of
   * the [headers], so do not log it when a header holds a secret. Its format can change.
   */
  override fun toString(): String =
    "SqlServiceSettings(serviceId=$serviceId, location=$location, schemaName=$schemaName, " +
      "headers=$headers, host=$host, port=$port, sslEnabled=$sslEnabled)"
}

/**
 * Returns a copy of these settings, with the given properties changed.
 *
 * [FirebaseSql.getInstance] checks the copy like any other settings.
 *
 * **Example: the same service in another schema**
 * ```kotlin
 * val settings = SqlServiceSettings(serviceId = "my-service", location = "us-central1")
 * val inventory = Firebase.sql(settings.copy(schemaName = "inventory"))
 * ```
 *
 * @param serviceId The [SqlServiceSettings.serviceId] of the copy.
 * @param location The [SqlServiceSettings.location] of the copy.
 * @param schemaName The [SqlServiceSettings.schemaName] of the copy.
 * @param headers The [SqlServiceSettings.headers] of the copy.
 * @param host The [SqlServiceSettings.host] of the copy.
 * @param port The [SqlServiceSettings.port] of the copy.
 * @param sslEnabled The [SqlServiceSettings.sslEnabled] of the copy.
 * @return Settings with the given properties, and with the properties of these settings for the
 * others.
 */
public fun SqlServiceSettings.copy(
  serviceId: String = this.serviceId,
  location: String = this.location,
  schemaName: String? = this.schemaName,
  headers: Map<String, String> = this.headers,
  host: String? = this.host,
  port: Int? = this.port,
  sslEnabled: Boolean? = this.sslEnabled
): SqlServiceSettings =
  SqlServiceSettings(
    serviceId = serviceId,
    location = location,
    schemaName = schemaName,
    headers = headers,
    host = host,
    port = port,
    sslEnabled = sslEnabled
  )
