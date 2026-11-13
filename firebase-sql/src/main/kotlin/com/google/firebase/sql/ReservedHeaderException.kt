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

/**
 * The error of a custom header that the SDK reserves for itself.
 *
 * `setHeader()` throws it right away, and `Firebase.sql()` and [FirebaseSql.getInstance] throw it
 * for a header in [SqlServiceSettings.headers]. The reserved headers, compared ignoring case, are:
 * - the tokens, `x-firebase-auth-token` and `X-Firebase-AppCheck`, and `Authorization`, which the
 * server also reads as a credential;
 * - the schema, `Accept-Profile` and `Content-Profile`: choose the schema with
 * [SqlServiceSettings.schemaName] instead;
 * - the versions and the app ID, `x-goog-api-client`, `x-client-version`, and `x-firebase-gmpid`;
 * - `X-Retry-Count`, which numbers the retries of a request;
 * - the routing headers, `x-firebase-sqlconnect-affinity` and `x-goog-request-params`, and the
 * heartbeat headers, `x-firebase-client` and `x-firebase-client-log-type`, which Firebase reserves.
 *
 * The SDK sets most of these headers itself, so that every request carries the tokens of its app
 * and the schema of its settings, and the server records the real versions and app ID. It sends
 * none of the routing and heartbeat headers.
 *
 * It is an [IllegalArgumentException], like the other errors of an invalid argument, so code that
 * catches `IllegalArgumentException` also catches it. Tests can create it, for example to check how
 * an app handles it.
 *
 * **Example: skip the reserved headers of a configuration**
 * ```kotlin
 * val configured = mapOf("X-Request-Source" to "sync", "Authorization" to "Bearer abc")
 * var request = sql.from("products").select()
 * for ((name, value) in configured) {
 *   request =
 *     try {
 *       request.setHeader(name, value)
 *     } catch (e: ReservedHeaderException) {
 *       Log.w("Products", "Skipped the reserved header ${e.headerName}")
 *       request
 *     }
 * }
 * val result = request.execute()
 * ```
 *
 * @property headerName The name of the reserved header, as the app gave it.
 * @param message The message of the error.
 */
public class ReservedHeaderException(public val headerName: String, message: String) :
  IllegalArgumentException(message)
