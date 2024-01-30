/*
 * Copyright 2023 Google LLC
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

package com.google.firebase.sessions

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.initialize
import com.google.firebase.sessions.testing.FakeFirebaseApp
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ApplicationInfoTest {

  @Test
  fun applicationInfo_populatesInfoCorrectly() {
    val firebaseApp = FakeFirebaseApp().firebaseApp
    val actualCurrentProcessDetails =
      ProcessDetailsProvider.getCurrentProcessDetails(firebaseApp.applicationContext)
    val actualAppProcessDetails =
      ProcessDetailsProvider.getAppProcessDetails(firebaseApp.applicationContext)
    val applicationInfo = SessionEvents.getApplicationInfo(firebaseApp)
    assertThat(applicationInfo)
      .isEqualTo(
        ApplicationInfo(
          appId = FakeFirebaseApp.MOCK_APP_ID,
          deviceModel = Build.MODEL,
          sessionSdkVersion = BuildConfig.VERSION_NAME,
          osVersion = Build.VERSION.RELEASE,
          logEnvironment = LogEnvironment.LOG_ENVIRONMENT_PROD,
          AndroidApplicationInfo(
            packageName = ApplicationProvider.getApplicationContext<Context>().packageName,
            versionName = FakeFirebaseApp.MOCK_APP_VERSION,
            appBuildVersion = FakeFirebaseApp.MOCK_APP_BUILD_VERSION,
            deviceManufacturer = Build.MANUFACTURER,
            actualCurrentProcessDetails,
            actualAppProcessDetails,
          )
        )
      )
  }

  @Test
  fun applicationInfo_missiongVersionCode_populatesInfoCorrectly() {
    // Initialize Firebase with no version code set.
    val firebaseApp =
      Firebase.initialize(
        ApplicationProvider.getApplicationContext(),
        FirebaseOptions.Builder()
          .setApplicationId(FakeFirebaseApp.MOCK_APP_ID)
          .setApiKey(FakeFirebaseApp.MOCK_API_KEY)
          .setProjectId(FakeFirebaseApp.MOCK_PROJECT_ID)
          .build()
      )

    val actualCurrentProcessDetails =
      ProcessDetailsProvider.getCurrentProcessDetails(firebaseApp.applicationContext)
    val actualAppProcessDetails =
      ProcessDetailsProvider.getAppProcessDetails(firebaseApp.applicationContext)

    val applicationInfo = SessionEvents.getApplicationInfo(firebaseApp)

    assertThat(applicationInfo)
      .isEqualTo(
        ApplicationInfo(
          appId = FakeFirebaseApp.MOCK_APP_ID,
          deviceModel = Build.MODEL,
          sessionSdkVersion = BuildConfig.VERSION_NAME,
          osVersion = Build.VERSION.RELEASE,
          logEnvironment = LogEnvironment.LOG_ENVIRONMENT_PROD,
          AndroidApplicationInfo(
            packageName = ApplicationProvider.getApplicationContext<Context>().packageName,
            versionName = "0",
            appBuildVersion = "0",
            deviceManufacturer = Build.MANUFACTURER,
            actualCurrentProcessDetails,
            actualAppProcessDetails,
          )
        )
      )
  }

  @After
  fun cleanUp() {
    FirebaseApp.clearInstancesForTest()
  }
}
