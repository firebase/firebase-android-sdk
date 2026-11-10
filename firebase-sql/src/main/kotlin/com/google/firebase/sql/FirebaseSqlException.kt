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

import com.google.firebase.FirebaseException

/**
 * The error of a Firebase SQL request.
 *
 * `execute()` throws it for the errors of the service, and for the errors that the SDK finds in a
 * successful response. It is a [FirebaseException].
 *
 * [category] tells where the error comes from:
 * - [FirebaseSqlExceptionCategory.POSTGREST]: PostgREST or PostgreSQL rejected the request, for
 * example because a column does not exist, a constraint is violated, or `single()` did not get
 * exactly one row. The error has the HTTP [status], its [code] is a PostgREST code, for example
 * `PGRST116`, or a PostgreSQL SQLSTATE, for example `23505`, and [details] and [hint] give the
 * server's explanation.
 * - [FirebaseSqlExceptionCategory.PLATFORM]: the Firebase SQL service in front of PostgREST
 * rejected the request, for example because of a token, App Check, a quota, or a disabled service;
 * or an HTTP error response did not come from PostgREST, for example the error page of a proxy. The
 * error has the HTTP [status], and its [code] is the status of the platform error, for example
 * `PERMISSION_DENIED`, or `null`.
 * - [FirebaseSqlExceptionCategory.CLIENT]: the SDK found the error itself in a successful response:
 * `dryRun()` when the server did not confirm the rollback. The error has the [status] of the
 * response, and no [code].
 *
 * Every HTTP error response gives an error with its [status]:
 * - PostgREST answers with a JSON object whose `message`, `code`, `details`, and `hint` fields give
 * the message, the [code], the [details], and the [hint]. The response to a HEAD request has no
 * body, so its error has only the [code], which the server sends in the `Proxy-Status` header.
 * - The Firebase SQL service gives its `message`, its `status` as the [code], and its `details` as
 * JSON text in [details].
 * - Another body, for example the error page of a proxy or an empty body, gives no [code], and the
 * message `HTTP <status>: <body>`.
 *
 * The SDK reports `maybeSingle()` with more than one row in the same form as PostgREST: status
 * `406` and code `PGRST116`.
 *
 * Common codes, with their HTTP status:
 * - `PGRST116` (`406`): `single()` got zero rows or more than one row, or `maybeSingle()` got more
 * than one row. [details] says how many, for example `The result contains 0 rows`.
 * - `PGRST106` (`406`): the server does not expose the schema of the instance.
 * - `PGRST124` (`400`): a mutation affected more rows than `maxAffected()` allows, so it was rolled
 * back.
 * - `42703` (`400`): a column of the request does not exist.
 * - `23505` (`409`): the row has the same unique key as another row.
 *
 * See the [PostgREST error reference](https://docs.postgrest.org/en/stable/references/errors.html)
 * for every PostgREST code and the HTTP status of each PostgreSQL error, and the
 * [PostgreSQL error codes](https://www.postgresql.org/docs/current/errcodes-appendix.html) for
 * every SQLSTATE.
 *
 * The other errors are thrown as they are, not as a [FirebaseSqlException]:
 * - When no HTTP response arrives, `execute()` throws the `IOException` of the network, for example
 * an `UnknownHostException` when the device is offline, or an `InterruptedIOException` when an
 * attempt takes longer than 100 seconds.
 * - When the Firebase Authentication token of the signed-in user cannot be retrieved, `execute()`
 * throws the error of Firebase Authentication, and the request is not sent.
 * - When the data does not fit the requested type, the decode functions throw the
 * `SerializationException` of kotlinx.serialization.
 * - Programming errors, such as invalid settings or a call on a terminated instance, are thrown as
 * an [IllegalArgumentException] or an [IllegalStateException]. A header that the SDK reserves gives
 * a [ReservedHeaderException], which is an [IllegalArgumentException].
 *
 * **Example: react to each category**
 * ```kotlin
 * try {
 *   val products = sql.from("products").select().execute().decodeList<Product>()
 * } catch (e: FirebaseSqlException) {
 *   when (e.category) {
 *     FirebaseSqlExceptionCategory.POSTGREST -> {
 *       // The request does not fit the database: fix the query or the data.
 *       Log.e("Products", "Database error ${e.status} ${e.code}: ${e.message}", e)
 *     }
 *     FirebaseSqlExceptionCategory.PLATFORM -> {
 *       // Check the sign-in, App Check, and the quotas of the project.
 *       Log.e("Products", "Service error ${e.status} ${e.code}: ${e.message}", e)
 *     }
 *     FirebaseSqlExceptionCategory.CLIENT -> {
 *       // The SDK found the error in a successful response, for example a dryRun() whose
 *       // rollback the server did not confirm.
 *       Log.e("Products", "Client error: ${e.message}", e)
 *     }
 *     else -> Log.e("Products", "Error: ${e.message}", e) // A category of a later version.
 *   }
 * } catch (e: IOException) {
 *   // No response, for example offline. A write that timed out may still have been applied.
 *   Log.w("Products", "No response: ${e.message}", e)
 * }
 * ```
 *
 * **Example: handle a unique violation**
 * ```kotlin
 * try {
 *   sql.from("categories").insert(mapOf("id" to 1, "name" to "Fruit")).execute()
 * } catch (e: FirebaseSqlException) {
 *   if (e.code != "23505") throw e
 *   // HTTP 409: a category with the ID 1 exists already.
 * }
 * ```
 *
 * **Example: find out why single() failed**
 * ```kotlin
 * try {
 *   val product =
 *     sql.from("products").select().eq("id", 42).single().execute().decodeSingle<Product>()
 * } catch (e: FirebaseSqlException) {
 *   if (e.code != "PGRST116") throw e
 *   // No product has the ID 42. maybeSingle() returns null instead of this error.
 *   Log.i("Products", "${e.status}: ${e.message} (${e.details})")
 * }
 * ```
 *
 * Response:
 * ```json
 * {
 *   "code": "PGRST116",
 *   "details": "The result contains 0 rows",
 *   "hint": null,
 *   "message": "Cannot coerce the result to a single JSON object"
 * }
 * ```
 *
 * @param message The message of the error. It must not be empty.
 * @property category Where the error comes from. See [FirebaseSqlExceptionCategory].
 * @property code The error code, or `null` when there is none, the default: a PostgREST code such
 * as `PGRST116`, a PostgreSQL SQLSTATE such as `23505`, or the status of a platform error such as
 * `PERMISSION_DENIED`. The errors of the [FirebaseSqlExceptionCategory.CLIENT] category that the
 * SDK reports have no code.
 * @property status The HTTP status of the response, for example `400`, `404`, `406`, or `409`. The
 * errors that the SDK throws always have one; `null` is the default.
 * @property details More information about the error, or `null`, the default. For a PostgREST
 * error, its `details` field, for example `The result contains 0 rows`. For an error of the
 * platform, its `details` array as JSON text, for example
 * `[{"@type":"type.googleapis.com/google.rpc.ErrorInfo",...}]`.
 * @property hint A suggestion to fix the error, from the `hint` field of a PostgREST error, for
 * example `You might need to add explicit type casts.`, or `null`, the default.
 * @param cause The underlying error, or `null`, the default.
 */
public class FirebaseSqlException(
  message: String,
  public val category: FirebaseSqlExceptionCategory,
  public val code: String? = null,
  public val status: Int? = null,
  public val details: String? = null,
  public val hint: String? = null,
  cause: Throwable? = null
) : FirebaseException(message) {

  init {
    // FirebaseException has no constructor that accepts a null cause.
    if (cause != null) initCause(cause)
  }
}
