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

package com.google.firebase.testing.sessions

import android.content.Context
import android.content.Intent
import androidx.test.InstrumentationRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import androidx.test.runner.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import java.util.regex.Pattern
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 18)
class FirebaseSessionsIntegrationTest {

  private lateinit var device: UiDevice

  @Before
  fun setup() {
    // Initialize UiDevice instance
    device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
  }

  @Test
  fun sameSessionIdBetweenActivitiesOnDifferentProcesses() {
    launchApp()

    val sessionId1 = getCurrentSessionId()
    navigateToSecondActivity()
    val sessionId2 = getCurrentSessionId()

    assertThat(sessionId1).isEqualTo(sessionId2)
  }

  @Test
  fun sameSessionIdAfterQuickForegroundBackground() {
    launchApp()

    val sessionId1 = getCurrentSessionId()
    background()
    Thread.sleep(1_000)
    foreground()
    val sessionId2 = getCurrentSessionId()

    assertThat(sessionId1).isEqualTo(sessionId2)
  }

  @Test
  fun newSessionIdAfterLongBackground() {
    launchApp()

    val sessionId1 = getCurrentSessionId()
    background()
    // Test app overrides the background time from 30m, to 5s.
    Thread.sleep(6_000)
    foreground()
    val sessionId2 = getCurrentSessionId()

    assertThat(sessionId1).isNotEqualTo(sessionId2)
  }

  @Test
  fun newSessionFollowingCrash() {
    launchApp()
    val origSession = getCurrentSessionId()

    getButton("CRASH!").click()

    launchApp()
    val newSession = getCurrentSessionId()
    assertThat(newSession).isNotEqualTo(origSession)
  }

  @Test
  fun nonFatalMainProcess() {
    launchApp()
    val origSession = getCurrentSessionId()

    getButton("NON FATAL").click()

    val newSession = getCurrentSessionId()
    assertThat(origSession).isEqualTo(newSession)
  }

  @Test
  fun crashSecondaryProcess() {
    launchApp()
    navigateToSecondActivity()
    val origSession = getCurrentSessionId()

    getButton("CRASH!").click()

    launchApp()
    val newSession = getCurrentSessionId()
    assertThat(newSession).isNotEqualTo(origSession)
  }

  private fun launchApp() {
    // Start from the home screen
    device.pressHome()

    // Wait for launcher
    device.wait(Until.hasObject(By.pkg(device.launcherPackageName).depth(0)), LAUNCH_TIMEOUT)

    // Launch the app
    val context = ApplicationProvider.getApplicationContext<Context>()
    val intent =
      context.packageManager.getLaunchIntentForPackage(TEST_APP_PACKAGE)?.apply {
        // Clear out any previous instances
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
      }
    context.startActivity(intent)

    // Wait for the app to appear
    device.wait(Until.hasObject(By.pkg(TEST_APP_PACKAGE).depth(0)), LAUNCH_TIMEOUT)
  }

  private fun navigateToSecondActivity() {
    device.wait(Until.hasObject(By.text("NEXT ACTIVITY").depth(0)), LAUNCH_TIMEOUT)
    val nextActivityButton =
      device.findObject(By.text("NEXT ACTIVITY").clazz("android.widget.Button"))
    nextActivityButton?.click()
    device.wait(Until.hasObject(By.pkg(TEST_APP_PACKAGE).depth(0)), LAUNCH_TIMEOUT)
  }

  private fun navigateBackToMainActivity() {
    device.wait(Until.hasObject(By.text("PREVIOUS ACTIVITY").depth(0)), LAUNCH_TIMEOUT)
    val nextActivityButton =
      device.findObject(By.text("PREVIOUS ACTIVITY").clazz("android.widget.Button"))
    nextActivityButton?.click()
    device.wait(Until.hasObject(By.pkg(TEST_APP_PACKAGE).depth(0)), LAUNCH_TIMEOUT)
  }

  private fun getButton(text: String): UiObject2 {
    device.wait(Until.hasObject(By.text(text).depth(0)), LAUNCH_TIMEOUT)
    val button = device.findObject(By.text(text).clazz("android.widget.Button"))
    if (button == null) {
      fail("Could not locate button with text $text")
    }
    return button
  }

  private fun background() {
    device.pressHome()
    device.wait(Until.hasObject(By.pkg(device.launcherPackageName).depth(0)), LAUNCH_TIMEOUT)
  }

  private fun foreground() {
    device.pressRecentApps()
    device.wait(
      Until.hasObject(By.res(Pattern.compile("$TEST_APP_PACKAGE.*")).depth(0)),
      LAUNCH_TIMEOUT
    )
    device.findObject(By.res(Pattern.compile("$TEST_APP_PACKAGE.*")))?.click()
  }

  private fun getCurrentSessionId() =
    device.findObject(By.res(Pattern.compile(".*session_id_(fragment|second)_text")))?.getText()

  companion object {
    private const val TEST_APP_PACKAGE = "com.google.firebase.testing.sessions"
    private const val LAUNCH_TIMEOUT = 5_000L
  }
}