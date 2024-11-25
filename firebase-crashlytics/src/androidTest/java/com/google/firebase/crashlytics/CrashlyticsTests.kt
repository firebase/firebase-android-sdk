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
    keyValueBuilder.key("hello", "world")
    keyValueBuilder.key("hello2", 23)
    keyValueBuilder.key("hello3", 0.1)

    val result: Map<String, String> = keyValueBuilder.build().keysAndValues

    // The result is not empty because we need to pass the CustomKeysAndValues around.
    assertThat(result).isNotEmpty()
  }

  @Test
  fun keyValueBuilder_withCrashlyticsInstance() {
    val keyValueBuilder = KeyValueBuilder(Firebase.crashlytics)
    keyValueBuilder.key("hello", "world")
    keyValueBuilder.key("hello2", 23)
    keyValueBuilder.key("hello3", 0.1)

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
