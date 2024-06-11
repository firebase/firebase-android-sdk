/*
 * Copyright 2024 Google LLC
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

package com.google.firebase.dataconnect.testutil

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseAppTestUtils
import com.google.firebase.FirebaseOptions
import com.google.firebase.initialize

/**
 * A JUnit rule for use in _unit_ tests (not _integration_ tests) that sets up the default
 * [FirebaseApp] instance before the test, and deletes is _after_ the test. It can also be used to
 * create non-default instances of [FirebaseApp] by calling [newInstance].
 *
 * Unit tests using this rule must be in classes annotated with `@RunWith(AndroidJUnit4::class)`.
 *
 * The [appNameKey], [applicationIdKey], and [projectIdKey] should all be globally unique strings
 * and will be incorporated into [FirebaseApp] instances that this object creates. Using such values
 * enables easily correlating instances back to the place in the source code where they are created.
 *
 * Example:
 * ```
 * @get:Rule
 * val firebaseAppFactory = FirebaseAppUnitTestingRule(
 *   appNameKey = "bsv6ag4m76",
 *   applicationIdKey = "52jdwgz9s9",
 *   projectIdKey = "pf9yk3m5jw"
 * )
 * ```
 */
class FirebaseAppUnitTestingRule(
  private val appNameKey: String,
  private val applicationIdKey: String,
  private val projectIdKey: String,
) : FactoryTestRule<FirebaseApp, Nothing>() {

  private val context: Context
    get() = ApplicationProvider.getApplicationContext()

  override fun createInstance(params: Nothing?) = createInstance(randomAppName(appNameKey))

  private fun createInstance(appName: String): FirebaseApp {
    val firebaseOptions = newFirebaseOptions()
    val app = Firebase.initialize(context, firebaseOptions, appName)
    FirebaseAppTestUtils.initializeAllComponents(app)
    return app
  }

  private fun initializeDefaultApp(): FirebaseApp = createInstance(FirebaseApp.DEFAULT_APP_NAME)

  override fun destroyInstance(instance: FirebaseApp) {
    instance.delete()
  }

  override fun before() {
    super.before()
    FirebaseAppTestUtils.clearInstancesForTest()
    initializeDefaultApp()
  }

  override fun after() {
    FirebaseAppTestUtils.clearInstancesForTest()
    super.after()
  }

  private fun newFirebaseOptions() =
    FirebaseOptions.Builder()
      .setApplicationId(randomApplicationId(applicationIdKey))
      .setProjectId(randomProjectId(projectIdKey))
      .build()
}
