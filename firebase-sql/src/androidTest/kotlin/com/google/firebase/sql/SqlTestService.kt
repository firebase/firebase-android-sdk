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

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

/**
 * The Firebase SQL service that [CloudSqlEndToEndTest] calls with `Firebase.sql()`, like an app
 * would.
 *
 * Like in an app, the project comes from the `google-services.json` file of the module. The
 * required `firebaseSqlServiceId` and `firebaseSqlLocation` instrumentation arguments name the
 * service. The Gradle build sets them from the environment variables `FIREBASE_SQL_SERVICE_ID` and
 * `FIREBASE_SQL_LOCATION`.
 */
internal class SqlTestService(val serviceId: String, val location: String) {

  /** The options of the `google-services.json` file, which the Google Services plugin reads. */
  private val options: FirebaseOptions =
    checkNotNull(FirebaseOptions.fromResource(context())) {
      "The test app has no Firebase options. Set FIREBASE_GOOGLE_SERVICES_PATH to the " +
        "google-services.json file of the project of the Firebase SQL service."
    }

  /** Initializes a [FirebaseApp] named [name] for the project of the service. */
  fun firebaseApp(name: String = FirebaseApp.DEFAULT_APP_NAME): FirebaseApp {
    // No Firebase Authentication or App Check SDK is linked, so the requests carry no token, and
    // the service must allow requests without App Check.
    return FirebaseApp.initializeApp(context(), options, name)
  }

  /**
   * Returns the [FirebaseSql] instance of [app] for the service. A `null` [schema] sends no schema
   * header, so the service uses its own schema.
   */
  fun sql(app: FirebaseApp, schema: String? = null): FirebaseSql =
    Firebase.sql(app, settings(schema))

  /** The settings of the service, with [schema]. */
  fun settings(schema: String? = null): SqlServiceSettings =
    SqlServiceSettings(serviceId, location, schemaName = schema)

  override fun toString() =
    "Firebase SQL service $serviceId in $location of project ${options.projectId}"

  companion object {
    /** The service that the instrumentation arguments name. */
    fun fromArguments(arguments: Bundle = InstrumentationRegistry.getArguments()): SqlTestService =
      SqlTestService(
        serviceId = arguments.requireString("firebaseSqlServiceId"),
        location = arguments.requireString("firebaseSqlLocation")
      )

    private fun Bundle.requireString(name: String): String =
      requireNotNull(getString(name)?.trim()?.takeIf { it.isNotEmpty() }) {
        "The $name instrumentation argument is required. For Gradle, set the environment " +
          "variables FIREBASE_SQL_SERVICE_ID and FIREBASE_SQL_LOCATION. In an Android Studio " +
          "test run configuration, add the instrumentation arguments firebaseSqlServiceId and " +
          "firebaseSqlLocation. See firebase-sql/README.md."
      }

    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext
  }
}
