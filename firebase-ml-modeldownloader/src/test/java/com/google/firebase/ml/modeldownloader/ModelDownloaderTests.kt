/*
 * Copyright 2021 Google LLC
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

package com.google.firebase.ml.modeldownloader

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
import org.robolectric.RobolectricTestRunner

const val APP_ID = "1:1234567890:android:321abc456def7890"
const val API_KEY = "AIzaSyDOCAbC123dEf456GhI789jKl012-MnO"

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

@RunWith(AndroidJUnit4::class)
class ModelDownloaderTests : BaseTestCase() {

  @Test
  fun `modelDownloader should delegate to FirebaseModelDownloader#getInstance()`() {
    assertThat(Firebase.modelDownloader).isSameInstanceAs(FirebaseModelDownloader.getInstance())
  }

  @Test
  fun `Firebase#modelDownloader(FirebaseApp) should delegate to FirebaseModelDownloader#getInstance(FirebaseApp)`() {
    val app = Firebase.app(EXISTING_APP)
    assertThat(Firebase.modelDownloader(app))
      .isSameInstanceAs(FirebaseModelDownloader.getInstance(app))
  }

  @Test
  fun `CustomModelDownloadConditions builder works`() {
    val conditions = customModelDownloadConditions {
      requireCharging()
      requireDeviceIdle()
    }

    assertThat(conditions.isChargingRequired).isEqualTo(true)
    assertThat(conditions.isWifiRequired).isEqualTo(false)
    assertThat(conditions.isDeviceIdleRequired).isEqualTo(true)
  }

  @Test
  fun `CustomModel destructuring declarations work`() {
    val app = Firebase.app(EXISTING_APP)

    val modelName = "myModel"
    val modelHash = "someHash"
    val fileSize = 200L
    val downloadId = 258L

    val customModel =
      Firebase.modelDownloader(app).modelFactory.create(modelName, modelHash, fileSize, downloadId)

    val (_, size, id, hash, name) = customModel

    assertThat(name).isEqualTo(customModel.name)
    assertThat(hash).isEqualTo(customModel.modelHash)
    assertThat(size).isEqualTo(customModel.size)
    assertThat(id).isEqualTo(customModel.downloadId)
  }
}

@RunWith(RobolectricTestRunner::class)
class LibraryVersionTest : BaseTestCase() {
  @Test
  fun `library version should be registered with runtime`() {
    val publisher = Firebase.app.get(UserAgentPublisher::class.java)
  }
}
