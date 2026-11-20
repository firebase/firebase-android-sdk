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
 * The config of a [FirebaseSql] instance: the Firebase SQL service that it sends its requests to,
 * the schema of the requests, and how it reaches the service.
 *
 * Only [serviceId] and [location] are required. With the defaults, the requests use `https` and the
 * production host `firebasedataconnect.googleapis.com`, at the path
 * `/v1/projects/<projectId>/locations/<location>/services/<serviceId>/postgrest/`, followed by the
 * table name, or by `rpc/` and the function name. `<projectId>` is the project ID in the
 * `FirebaseOptions` of the `FirebaseApp` of the instance, which come from the
 * `google-services.json` file of the app: the config has no project ID.
 *
 * Every property identifies the instance: [FirebaseSql.getInstance] returns the same instance for
 * all the configs that resolve to the same URL and schema, ignoring case, and that instance keeps
 * the config that created it. The custom headers of the requests are not part of the config: set
 * them on the instance with [FirebaseSql.setHeaders].
 *
 * [FirebaseSql.getInstance] checks the config on every call, and throws an
 * [IllegalArgumentException] when a property breaks the constraint that it documents. Two configs
 * are equal when all their properties are equal.
 *
 * **Example: the production service**
 * ```kotlin
 * val sql = Firebase.sql(SqlServiceConfig(serviceId = "my-service", location = "us-central1"))
 * ```
 *
 * **Example: a local server, from the Android emulator**
 * ```kotlin
 * // 10.0.2.2 is the address of the computer that runs the Android emulator.
 * val local =
 *   Firebase.sql(
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
 * schema, call [FirebaseSql.getInstance] with a copy of the config, for example
 * `config.copy(schemaName = "inventory")`.
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
 * Firebase Authentication and Firebase App Check tokens in plain text: it must not be `false` with
 * the production host, which is the default [host]. On Android 9 (API level 28) and higher, the app
 * must also allow cleartext traffic to that host, for example in its network security
 * configuration; otherwise the requests fail with a status of `0`.
 */
public class SqlServiceConfig(
  public val serviceId: String,
  public val location: String,
  public val schemaName: String? = null,
  public val host: String? = null,
  public val port: Int? = null,
  public val sslEnabled: Boolean? = null
) {
  /**
   * Compares this config with another object.
   *
   * Configs that are not equal can still give the same instance in [FirebaseSql.getInstance], for
   * example a config without a [host] and a config with the production host.
   *
   * @param other The object to compare with this config.
   * @return `true` if, and only if, [other] is a [SqlServiceConfig] whose properties are all equal
   * to the properties of this config.
   */
  override fun equals(other: Any?): Boolean =
    other is SqlServiceConfig &&
      other.serviceId == serviceId &&
      other.location == location &&
      other.schemaName == schemaName &&
      other.host == host &&
      other.port == port &&
      other.sslEnabled == sslEnabled

  /** Returns a hash code of all the properties, consistent with [equals]. */
  override fun hashCode(): Int =
    Objects.hash(serviceId, location, schemaName, host, port, sslEnabled)

  /** Returns a text with the values of all the properties, for debugging. Its format can change. */
  override fun toString(): String =
    "SqlServiceConfig(serviceId=$serviceId, location=$location, schemaName=$schemaName, " +
      "host=$host, port=$port, sslEnabled=$sslEnabled)"
}

/**
 * Returns a copy of this config, with the given properties changed.
 *
 * [FirebaseSql.getInstance] checks the copy like any other config.
 *
 * **Example: the same service in another schema**
 * ```kotlin
 * val config = SqlServiceConfig(serviceId = "my-service", location = "us-central1")
 * val inventory = Firebase.sql(config.copy(schemaName = "inventory"))
 * ```
 *
 * @param serviceId The [SqlServiceConfig.serviceId] of the copy.
 * @param location The [SqlServiceConfig.location] of the copy.
 * @param schemaName The [SqlServiceConfig.schemaName] of the copy.
 * @param host The [SqlServiceConfig.host] of the copy.
 * @param port The [SqlServiceConfig.port] of the copy.
 * @param sslEnabled The [SqlServiceConfig.sslEnabled] of the copy.
 * @return A config with the given properties, and with the properties of this config for the
 * others.
 */
public fun SqlServiceConfig.copy(
  serviceId: String = this.serviceId,
  location: String = this.location,
  schemaName: String? = this.schemaName,
  host: String? = this.host,
  port: Int? = this.port,
  sslEnabled: Boolean? = this.sslEnabled
): SqlServiceConfig =
  SqlServiceConfig(
    serviceId = serviceId,
    location = location,
    schemaName = schemaName,
    host = host,
    port = port,
    sslEnabled = sslEnabled
  )
