/*
 * Copyright 2022 Google LLC
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

package com.google.firebase.appcheck.ktx

import androidx.test.core.app.ApplicationProvider
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.AppCheckToken
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.app
import com.google.firebase.ktx.initialize
import com.google.firebase.platforminfo.UserAgentPublisher
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

const val APP_ID = "APP_ID"
const val API_KEY = "API_KEY"

const val EXISTING_APP = "existing"

@RunWith(AndroidJUnit4ClassRunner::class)
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

@RunWith(AndroidJUnit4ClassRunner::class)
class FirebaseAppCheckTests : BaseTestCase() {
  @Test
  fun appCheck_default_callsDefaultGetInstance() {
    assertThat(Firebase.appCheck).isSameInstanceAs(FirebaseAppCheck.getInstance())
  }

  @Test
  fun appCheck_with_custom_firebaseapp_calls_GetInstance() {
    val app = Firebase.app(EXISTING_APP)
    assertThat(Firebase.appCheck(app)).isSameInstanceAs(FirebaseAppCheck.getInstance(app))
  }

  @Test
  fun appCheckToken_destructuring_declaration_works() {
    val mockAppCheckToken =
      object : AppCheckToken() {
        override fun getToken(): String = "randomToken"

        override fun getExpireTimeMillis(): Long = 23121997
      }

    val (token, expiration) = mockAppCheckToken

    assertThat(token).isEqualTo(mockAppCheckToken.token)
    assertThat(expiration).isEqualTo(mockAppCheckToken.expireTimeMillis)
  }
}

internal const val LIBRARY_NAME: String = "fire-app-check-ktx"

@RunWith(AndroidJUnit4ClassRunner::class)
class LibraryVersionTest : BaseTestCase() {
  @Test
  fun libraryRegistrationAtRuntime() {
    val publisher = Firebase.app.get(UserAgentPublisher::class.java)
    assertThat(publisher.userAgent).contains(LIBRARY_NAME)
  }
}
