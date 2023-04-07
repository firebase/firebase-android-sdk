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

import android.os.Bundle
import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.sessions.settings.LocalOverrideSettings
import com.google.firebase.sessions.testing.FakeFirebaseApp
import kotlin.time.Duration.Companion.minutes
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalOverrideSettingsTest {

  @Test
  fun localOverrides_returnsNullByDefault() {
    val context = FakeFirebaseApp.fakeFirebaseApp().applicationContext

    val localSettings = LocalOverrideSettings(context)
    assertThat(localSettings.sessionEnabled).isNull()
    assertThat(localSettings.sessionRestartTimeout).isNull()
    assertThat(localSettings.samplingRate).isNull()
  }

  @Test
  fun localOverrides_validateIfOverrideValuesAreFetchedCorrectly() {
    val metadata = Bundle()
    metadata.putBoolean("firebase_sessions_enabled", false)
    metadata.putDouble("firebase_sessions_sampling_rate", 0.5)
    metadata.putInt("firebase_sessions_sessions_restart_timeout", 180)
    val context = FakeFirebaseApp.fakeFirebaseApp(metadata).applicationContext

    val localSettings = LocalOverrideSettings(context)
    assertThat(localSettings.sessionEnabled).isFalse()
    assertThat(localSettings.sessionRestartTimeout).isEqualTo(3.minutes)
    assertThat(localSettings.samplingRate).isEqualTo(0.5)
  }

  @Test
  fun localOverridesForSomeFields_validateIfOverrideValuesAreFetchedCorrectly() {
    val metadata = Bundle()
    metadata.putBoolean("firebase_sessions_enabled", false)
    metadata.putInt("firebase_sessions_sessions_restart_timeout", 180)
    val context = FakeFirebaseApp.fakeFirebaseApp(metadata).applicationContext

    val localSettings = LocalOverrideSettings(context)
    assertThat(localSettings.sessionEnabled).isFalse()
    assertThat(localSettings.sessionRestartTimeout).isEqualTo(3.minutes)
    assertThat(localSettings.samplingRate).isNull()
  }

  @After
  fun cleanUp() {
    FirebaseApp.clearInstancesForTest()
  }
}
