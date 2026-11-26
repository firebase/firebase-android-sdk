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
 * The error of a change to a setting of a [FirebaseSql] instance that can no longer change.
 *
 * The setters of an instance, such as [FirebaseSql.setHeaders], throw it after the first call of
 * [FirebaseSql.from], [FirebaseSql.rpc], [FirebaseSql.terminate], or
 * [FirebaseSql.suspendingTerminate], for a value that is not equal to the current one. They do not
 * throw it for an equal value, so that code that runs again can set the same value. The message
 * names the setter.
 *
 * It is about the instance, not about the data: the errors of an insert, an update, or a delete are
 * [FirebaseSqlException]s.
 *
 * It is an [IllegalStateException], like the other errors of a call that the state of the instance
 * does not allow, so code that catches `IllegalStateException` also catches it. Tests can create
 * it, for example to check how an app handles it.
 *
 * A setter checks whether the instance is in use and changes the setting in one step. To set a
 * value unless another part of the app already uses the instance with another one, catch this
 * exception: a check before the call could be out of date by the time of the call.
 *
 * **Example: set the headers unless another part of the app uses the instance with others**
 * ```kotlin
 * val sql = Firebase.sql(ServiceConfig(serviceId = "my-service", location = "us-central1"))
 * try {
 *   sql.setHeaders(mapOf("X-Request-Source" to "sync"))
 * } catch (e: FirebaseSqlMutationException) {
 *   Log.w("Products", "The instance is already in use with other headers", e)
 * }
 * ```
 *
 * @param message The message of the error.
 */
public class FirebaseSqlMutationException(message: String) : IllegalStateException(message)
