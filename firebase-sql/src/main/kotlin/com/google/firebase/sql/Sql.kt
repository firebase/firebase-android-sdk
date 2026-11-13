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
 * Returns the [FirebaseSql] instance of the default [FirebaseApp] for the given [settings], and
 * creates it on the first call.
 *
 * It is a shorthand for [FirebaseSql.getInstance], which describes how the instances are cached.
 *
 * **Example: the production service**
 * ```kotlin
 * val sql =
 *   Firebase.sql(SqlServiceSettings(serviceId = "my-service", location = "us-central1"))
 * ```
 *
 * @param settings The service, and how to send it the requests. See [SqlServiceSettings].
 * @return The cached instance for these settings, or a new instance.
 * @throws ReservedHeaderException When [SqlServiceSettings.headers] has a header that the SDK
 * reserves, such as `Authorization`.
 * @throws IllegalArgumentException When a property of [settings] breaks the constraint that
 * [SqlServiceSettings] documents for it, or when the options of the default app have no project ID.
 * @throws IllegalStateException When the default [FirebaseApp] is not initialized, or was deleted.
 */
public fun Firebase.sql(settings: SqlServiceSettings): FirebaseSql =
  FirebaseSql.getInstance(settings)

/**
 * Returns the [FirebaseSql] instance of [app] for the given [settings], and creates it on the first
 * call.
 *
 * It is a shorthand for [FirebaseSql.getInstance], which describes how the instances are cached.
 *
 * **Example: an app other than the default app**
 * ```kotlin
 * val otherSql =
 *   Firebase.sql(otherApp, SqlServiceSettings(serviceId = "my-service", location = "us-central1"))
 * ```
 *
 * @param app The app whose project ID and tokens the instance uses.
 * @param settings The service, and how to send it the requests. See [SqlServiceSettings].
 * @return The cached instance for this app and these settings, or a new instance.
 * @throws ReservedHeaderException When [SqlServiceSettings.headers] has a header that the SDK
 * reserves, such as `Authorization`.
 * @throws IllegalArgumentException When a property of [settings] breaks the constraint that
 * [SqlServiceSettings] documents for it, or when the options of [app] have no project ID.
 * @throws IllegalStateException When [app] was deleted.
 */
public fun Firebase.sql(app: FirebaseApp, settings: SqlServiceSettings): FirebaseSql =
  FirebaseSql.getInstance(app, settings)
