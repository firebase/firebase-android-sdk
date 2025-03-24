/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.crashlytics

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.app
import com.google.firebase.initialize
import com.google.firebase.platforminfo.UserAgentPublisher
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CrashlyticsTests {
  @Before
  fun setUp() {
    Firebase.initialize(
      ApplicationProvider.getApplicationContext(),
      FirebaseOptions.Builder()
        .setApplicationId(APP_ID)
        .setApiKey(API_KEY)
        .setProjectId(PROJECT_ID)
        .build(),
    )
  }

  @After
  fun cleanUp() {
    FirebaseApp.clearInstancesForTest()
  }

  @Test
  fun firebaseCrashlyticsDelegates() {
    assertThat(Firebase.crashlytics).isSameInstanceAs(FirebaseCrashlytics.getInstance())
  }

  @Test
  fun libraryRegistrationAtRuntime() {
    Firebase.app.get(UserAgentPublisher::class.java)
  }

  @Test
  fun keyValueBuilder() {
    val keyValueBuilder = KeyValueBuilder()
    keyValueBuilder.key("string", "world")
    keyValueBuilder.key("int", Int.MAX_VALUE)
    keyValueBuilder.key("float", Float.MAX_VALUE)
    keyValueBuilder.key("boolean", true)
    keyValueBuilder.key("double", Double.MAX_VALUE)
    keyValueBuilder.key("long", Long.MAX_VALUE)

    val result: Map<String, String> = keyValueBuilder.build().keysAndValues

    val expectedKeys =
      mapOf(
        "string" to "world",
        "int" to "${Int.MAX_VALUE}",
        "float" to "${Float.MAX_VALUE}",
        "boolean" to "${true}",
        "double" to "${Double.MAX_VALUE}",
        "long" to "${Long.MAX_VALUE}"
      )
    assertThat(result).isEqualTo(expectedKeys)
  }

  @Test
  fun keyValueBuilder_withCrashlyticsInstance() {
    @Suppress("DEPRECATION") val keyValueBuilder = KeyValueBuilder(Firebase.crashlytics)
    keyValueBuilder.key("string", "world")
    keyValueBuilder.key("int", Int.MAX_VALUE)
    keyValueBuilder.key("float", Float.MAX_VALUE)
    keyValueBuilder.key("boolean", true)
    keyValueBuilder.key("double", Double.MAX_VALUE)
    keyValueBuilder.key("long", Long.MAX_VALUE)

    val result: Map<String, String> = keyValueBuilder.build().keysAndValues

    // The result is empty because it called crashlytics.setCustomKey for every key.
    assertThat(result).isEmpty()
  }

  companion object {
    private const val APP_ID = "1:1:android:1a"
    private const val API_KEY = "API-KEY-API-KEY-API-KEY-API-KEY-API-KEY"
    private const val PROJECT_ID = "PROJECT-ID"
  }
}
