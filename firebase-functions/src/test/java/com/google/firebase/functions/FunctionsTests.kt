/*
 * Copyright 2019 Google LLC
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

package com.google.firebase.functions

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.app
import com.google.firebase.initialize
import com.google.firebase.platforminfo.UserAgentPublisher
import java.net.URL
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

const val APP_ID = "APP_ID"
const val API_KEY = "API_KEY"

const val EXISTING_APP = "existing"

abstract class BaseTestCase {
  @Before
  fun setUp() {
    Firebase.initialize(
      ApplicationProvider.getApplicationContext(),
      FirebaseOptions.Builder()
        .setApplicationId(APP_ID)
        .setApiKey(API_KEY)
        .setProjectId("123")
        .build()
    )

    Firebase.initialize(
      ApplicationProvider.getApplicationContext(),
      FirebaseOptions.Builder()
        .setApplicationId(APP_ID)
        .setApiKey(API_KEY)
        .setProjectId("123")
        .build(),
      EXISTING_APP
    )
  }

  @After
  fun cleanUp() {
    FirebaseApp.clearInstancesForTest()
  }
}

@RunWith(RobolectricTestRunner::class)
class FunctionsTests : BaseTestCase() {

  @Test
  fun `functions should delegate to FirebaseFunctions#getInstance()`() {
    assertThat(Firebase.functions).isSameInstanceAs(FirebaseFunctions.getInstance())
  }

  @Test
  fun `FirebaseApp#functions should delegate to FirebaseFunctions#getInstance(FirebaseApp)`() {
    val app = Firebase.app(EXISTING_APP)
    assertThat(Firebase.functions(app)).isSameInstanceAs(FirebaseFunctions.getInstance(app))
  }

  @Test
  fun `Firebase#functions should delegate to FirebaseFunctions#getInstance(region)`() {
    val region = "valid_region"
    assertThat(Firebase.functions(region)).isSameInstanceAs(FirebaseFunctions.getInstance(region))
  }

  @Test
  fun `Firebase#functions should delegate to FirebaseFunctions#getInstance(FirebaseApp, region)`() {
    val app = Firebase.app(EXISTING_APP)
    val region = "valid_region"
    assertThat(Firebase.functions(app, region))
      .isSameInstanceAs(FirebaseFunctions.getInstance(app, region))
  }
}

@RunWith(RobolectricTestRunner::class)
class LibraryVersionTest : BaseTestCase() {
  @Test
  fun `library version should be registered with runtime`() {
    val publisher = Firebase.app.get(UserAgentPublisher::class.java)
  }
}

@RunWith(RobolectricTestRunner::class)
class AppCheckLimitedUseTest : BaseTestCase() {
  @Test
  fun `FirebaseFunctions#getHttpsCallable should not use limited-use tokens by default`() {
    val callable = Firebase.functions.getHttpsCallable("function")
    assertThat(callable.usesLimitedUseFacTokens()).isFalse()
  }

  @Test
  fun `FirebaseFunctions#getHttpsCallable should build callable with FAC settings (when true)`() {
    val callable =
      Firebase.functions.getHttpsCallable("function") { limitedUseAppCheckTokens = true }
    assertThat(callable.usesLimitedUseFacTokens()).isTrue()
  }

  @Test
  fun `FirebaseFunctions#getHttpsCallable should build callable with FAC settings (when false)`() {
    val callable =
      Firebase.functions.getHttpsCallable("function") { limitedUseAppCheckTokens = false }
    assertThat(callable.usesLimitedUseFacTokens()).isFalse()
  }

  @Test
  fun `FirebaseFunctions#getHttpsCallableFromUrl should not use limited-use tokens by default`() {
    val callable = Firebase.functions.getHttpsCallableFromUrl(URL("https://functions.test"))
    assertThat(callable.usesLimitedUseFacTokens()).isFalse()
  }

  @Test
  fun `FirebaseFunctions#getHttpsCallableFromUrl callable with FAC settings (when true)`() {
    val callable =
      Firebase.functions.getHttpsCallableFromUrl(URL("https://functions.test")) {
        limitedUseAppCheckTokens = true
      }
    assertThat(callable.usesLimitedUseFacTokens()).isTrue()
  }

  @Test
  fun `FirebaseFunctions#getHttpsCallableFromUrl callable with FAC settings (when false)`() {
    val callable =
      Firebase.functions.getHttpsCallableFromUrl(URL("https://functions.test")) {
        limitedUseAppCheckTokens = false
      }
    assertThat(callable.usesLimitedUseFacTokens()).isFalse()
  }
}
