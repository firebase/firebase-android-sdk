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

package com.google.firebase.appdistribution.ktx

import androidx.test.core.app.ApplicationProvider
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appdistribution.AppDistributionRelease
import com.google.firebase.appdistribution.BinaryType
import com.google.firebase.appdistribution.FirebaseAppDistribution
import com.google.firebase.appdistribution.UpdateProgress
import com.google.firebase.appdistribution.UpdateStatus
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
class FirebaseAppDistributionTests : BaseTestCase() {
  @Test
  fun appDistribution_default_callsDefaultGetInstance() {
    assertThat(Firebase.appDistribution).isSameInstanceAs(FirebaseAppDistribution.getInstance())
  }

  @Test
  fun appDistributionReleaseDestructuringDeclarationsWork() {
    val mockAppDistributionRelease =
      object : AppDistributionRelease {
        override fun getDisplayVersion(): String = "1.0.0"

        override fun getVersionCode(): Long = 1L

        override fun getReleaseNotes(): String = "Changelog..."

        override fun getBinaryType(): BinaryType = BinaryType.AAB
      }

    val (type, displayVersion, versionCode, notes) = mockAppDistributionRelease

    assertThat(type).isEqualTo(mockAppDistributionRelease.binaryType)
    assertThat(displayVersion).isEqualTo(mockAppDistributionRelease.displayVersion)
    assertThat(versionCode).isEqualTo(mockAppDistributionRelease.versionCode)
    assertThat(notes).isEqualTo(mockAppDistributionRelease.releaseNotes)
  }

  @Test
  fun updateProgressDestructuringDeclarationsWork() {
    val mockUpdateProgress =
      object : UpdateProgress {
        override fun getApkBytesDownloaded(): Long = 1200L

        override fun getApkFileTotalBytes(): Long = 9000L

        override fun getUpdateStatus(): UpdateStatus = UpdateStatus.DOWNLOADING
      }

    val (downloaded, total, status) = mockUpdateProgress

    assertThat(downloaded).isEqualTo(mockUpdateProgress.apkBytesDownloaded)
    assertThat(total).isEqualTo(mockUpdateProgress.apkFileTotalBytes)
    assertThat(status).isEqualTo(mockUpdateProgress.updateStatus)
  }
}

internal const val LIBRARY_NAME: String = "fire-appdistribution-ktx"

@RunWith(AndroidJUnit4ClassRunner::class)
class LibraryVersionTest : BaseTestCase() {
  @Test
  fun libraryRegistrationAtRuntime() {
    val publisher = Firebase.app.get(UserAgentPublisher::class.java)
    assertThat(publisher.userAgent).contains(LIBRARY_NAME)
  }
}
