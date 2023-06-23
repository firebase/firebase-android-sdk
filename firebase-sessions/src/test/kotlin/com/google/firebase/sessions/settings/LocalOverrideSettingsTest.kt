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

package com.google.firebase.sessions.settings

import android.os.Bundle
import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.sessions.testing.FakeFirebaseApp
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalOverrideSettingsTest {
  @Test
  fun localOverrides_returnsNullByDefault() {
    val context = FakeFirebaseApp().firebaseApp.applicationContext

    val localSettings = LocalOverrideSettings(context)
    assertThat(localSettings.sessionEnabled).isNull()
    assertThat(localSettings.sessionRestartTimeout).isNull()
    assertThat(localSettings.samplingRate).isNull()
  }

  @Test
  fun localOverrides_overrideValuesAreFetchedCorrectly() {
    val metadata = Bundle()
    metadata.putBoolean("firebase_sessions_enabled", false)
    metadata.putDouble("firebase_sessions_sampling_rate", 0.5)
    metadata.putInt("firebase_sessions_sessions_restart_timeout", 180)
    val context = FakeFirebaseApp(metadata).firebaseApp.applicationContext

    val localSettings = LocalOverrideSettings(context)
    assertThat(localSettings.sessionEnabled).isFalse()
    assertThat(localSettings.sessionRestartTimeout).isEqualTo(3.minutes)
    assertThat(localSettings.samplingRate).isEqualTo(0.5)
  }

  @Test
  fun localOverridesForSomeFields_overrideValuesAreFetchedCorrectly() {
    val metadata = Bundle()
    metadata.putBoolean("firebase_sessions_enabled", false)
    metadata.putInt("firebase_sessions_sessions_restart_timeout", 180)
    val context = FakeFirebaseApp(metadata).firebaseApp.applicationContext

    val localSettings = LocalOverrideSettings(context)
    assertThat(localSettings.sessionEnabled).isFalse()
    assertThat(localSettings.sessionRestartTimeout).isEqualTo(3.minutes)
    assertThat(localSettings.samplingRate).isNull()
  }

  @Test
  fun localOverrides_invalidOverrideValuesAreFetchedAsSomething() {
    val metadata = Bundle()
    metadata.putString("firebase_sessions_enabled", "no")
    metadata.putDouble("firebase_sessions_sampling_rate", -2.0)
    metadata.putInt("firebase_sessions_sessions_restart_timeout", -2)
    val context = FakeFirebaseApp(metadata).firebaseApp.applicationContext

    // These values are not meaningful, but are fetched.
    val localSettings = LocalOverrideSettings(context)
    assertThat(localSettings.sessionEnabled).isFalse()
    assertThat(localSettings.sessionRestartTimeout).isEqualTo((-2).seconds)
    assertThat(localSettings.samplingRate).isEqualTo(-2.0)
  }

  @Test
  fun localOverrides_invalidOverrideValueTypesAreFetchedAsDefault() {
    val metadata = Bundle()
    // These are all the wrong type.
    metadata.putString("firebase_sessions_enabled", "true")
    metadata.putString("firebase_sessions_sampling_rate", "0.375")
    metadata.putString("firebase_sessions_sessions_restart_timeout", "9000.1")
    val context = FakeFirebaseApp(metadata).firebaseApp.applicationContext

    // These are the bundle default values.
    val localSettings = LocalOverrideSettings(context)
    assertThat(localSettings.sessionEnabled).isFalse()
    assertThat(localSettings.sessionRestartTimeout).isEqualTo(Duration.ZERO)
    assertThat(localSettings.samplingRate).isEqualTo(0.0)
  }

  @After
  fun cleanUp() {
    FirebaseApp.clearInstancesForTest()
  }
}
