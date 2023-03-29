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

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.content.pm.PackageInfoBuilder
import com.google.common.truth.Truth
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.initialize
import com.google.firebase.sessions.testing.FakeFirebaseApp
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowApplication
import org.robolectric.shadows.ShadowPackageManager

@RunWith(RobolectricTestRunner::class)
class ApplicationInfoTest {

  @Test
  fun applicationInfo_populatesInfoCorrectly() {
    val applicationInfo = getApplicationInfo(FakeFirebaseApp.fakeFirebaseApp())
    Truth.assertThat(applicationInfo)
      .isEqualTo(
        ApplicationInfo(appId = FakeFirebaseApp.MOCK_APP_ID,
                        deviceModel = "",
                        sessionSdkVersion = BuildConfig.VERSION_NAME,
                        logEnvironment = LogEnvironment.LOG_ENVIRONMENT_PROD,
                        AndroidApplicationInfo(packageName = ApplicationProvider.getApplicationContext<Context>().packageName,
                                               versionName = FakeFirebaseApp.MOCK_APP_VERSION)
        )
      )
  }

  @After
  fun cleanUp() {
    FirebaseApp.clearInstancesForTest()
  }
}