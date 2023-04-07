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
import com.google.firebase.sessions.settings.SessionsSettings
import com.google.firebase.sessions.testing.FakeFirebaseApp
import kotlin.time.Duration.Companion.minutes
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionsSettingsTest {

  @Test
  fun sessionSettings_fetchDefaults() {
    val context = FakeFirebaseApp.fakeFirebaseApp().applicationContext

    val sessionsSettings = SessionsSettings(context)
    assertThat(sessionsSettings.sessionsEnabled).isTrue()
    assertThat(sessionsSettings.samplingRate).isEqualTo(1.0)
    assertThat(sessionsSettings.sessionRestartTimeout).isEqualTo(30.minutes)
  }

  @Test
  fun sessionSettings_fetchOverridingConfigs() {
    val metadata = Bundle()
    metadata.putBoolean("firebase_sessions_enabled", false)
    metadata.putDouble("firebase_sessions_sampling_rate", 0.5)
    metadata.putInt("firebase_sessions_sessions_restart_timeout", 180)
    val context = FakeFirebaseApp.fakeFirebaseApp(metadata).applicationContext

    val sessionsSettings = SessionsSettings(context)
    assertThat(sessionsSettings.sessionsEnabled).isFalse()
    assertThat(sessionsSettings.samplingRate).isEqualTo(0.5)
    assertThat(sessionsSettings.sessionRestartTimeout).isEqualTo(3.minutes)
  }

  @Test
  fun sessionSettings_fetchOverridingConfigsOnlyWhenPresent() {
    val metadata = Bundle()
    metadata.putBoolean("firebase_sessions_enabled", false)
    metadata.putDouble("firebase_sessions_sampling_rate", 0.5)
    val context = FakeFirebaseApp.fakeFirebaseApp(metadata).applicationContext

    val sessionsSettings = SessionsSettings(context)
    assertThat(sessionsSettings.sessionsEnabled).isFalse()
    assertThat(sessionsSettings.samplingRate).isEqualTo(0.5)
    assertThat(sessionsSettings.sessionRestartTimeout).isEqualTo(30.minutes)
  }

  @After
  fun cleanUp() {
    FirebaseApp.clearInstancesForTest()
  }
}
