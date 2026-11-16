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

import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp

/**
 * Returns the [FirebaseSql] instance of the default [FirebaseApp] for the given [config], and
 * creates it on the first call.
 *
 * It is a shorthand for [FirebaseSql.getInstance], which describes how the instances are cached. To
 * send custom headers with every request, call [FirebaseSql.setHeaders] on the instance before its
 * first request.
 *
 * **Example: the production service**
 * ```kotlin
 * val sql = Firebase.sql(SqlServiceConfig(serviceId = "my-service", location = "us-central1"))
 * ```
 *
 * @param config The service, and how to send it the requests. See [SqlServiceConfig].
 * @return The cached instance for this config, or a new instance.
 * @throws IllegalArgumentException When a property of [config] breaks the constraint that
 * [SqlServiceConfig] documents for it, or when the options of the default app have no project ID.
 * @throws IllegalStateException When the default [FirebaseApp] is not initialized, or was deleted.
 */
public fun Firebase.sql(config: SqlServiceConfig): FirebaseSql = FirebaseSql.getInstance(config)

/**
 * Returns the [FirebaseSql] instance of [app] for the given [config], and creates it on the first
 * call.
 *
 * It is a shorthand for [FirebaseSql.getInstance], which describes how the instances are cached. To
 * send custom headers with every request, call [FirebaseSql.setHeaders] on the instance before its
 * first request.
 *
 * **Example: an app other than the default app**
 * ```kotlin
 * val otherSql =
 *   Firebase.sql(otherApp, SqlServiceConfig(serviceId = "my-service", location = "us-central1"))
 * ```
 *
 * @param app The app whose project ID and tokens the instance uses.
 * @param config The service, and how to send it the requests. See [SqlServiceConfig].
 * @return The cached instance for this app and this config, or a new instance.
 * @throws IllegalArgumentException When a property of [config] breaks the constraint that
 * [SqlServiceConfig] documents for it, or when the options of [app] have no project ID.
 * @throws IllegalStateException When [app] was deleted.
 */
public fun Firebase.sql(app: FirebaseApp, config: SqlServiceConfig): FirebaseSql =
  FirebaseSql.getInstance(app, config)
