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

package com.google.firebase.inappmessaging.display

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.app
import com.google.firebase.initialize
import com.google.firebase.platforminfo.UserAgentPublisher
import java.util.UUID
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

internal const val APP_ID = "APP:ID"
internal val API_KEY = "ABC" + UUID.randomUUID().toString()

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
class InAppMessagingDisplayTests : BaseTestCase() {

  @Test
  fun `inAppMessagingDisplay should delegate to FirebaseInAppMessagingDisplay#getInstance()`() {
    assertThat(Firebase.inAppMessagingDisplay)
      .isSameInstanceAs(FirebaseInAppMessagingDisplay.getInstance())
  }
}

@RunWith(RobolectricTestRunner::class)
class LibraryVersionTest : BaseTestCase() {
  @Test
  fun `library version should be registered with runtime`() {
    val publisher = Firebase.app.get(UserAgentPublisher::class.java)
  }
}
