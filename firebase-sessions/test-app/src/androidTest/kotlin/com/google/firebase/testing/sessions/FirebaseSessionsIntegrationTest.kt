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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import java.util.regex.Pattern
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirebaseSessionsIntegrationTest {

  private lateinit var device: UiDevice

  @Before
  fun setup() {
    // Initialize UiDevice instance
    device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
  }

  @After
  fun cleanup() {
    // Make sure all processes are killed
    Runtime.getRuntime().exec(arrayOf("am", "force-stop", TEST_APP_PACKAGE))
  }

  @Test
  fun sameSessionIdBetweenActivitiesOnDifferentProcesses() {
    launchApp()

    val sessionId1 = getCurrentSessionId()
    navigateToSecondActivity()
    Thread.sleep(TIME_TO_PROPAGATE_SESSION)
    val sessionId2 = getCurrentSessionId()

    assertThat(sessionId1).isEqualTo(sessionId2)
  }

  @Test
  fun sameSessionIdAfterQuickForegroundBackground() {
    launchApp()

    val sessionId1 = getCurrentSessionId()
    background()
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
    device.waitForIdle()
    Thread.sleep(TIME_TO_PROPAGATE_SESSION)
    val sessionId2 = getCurrentSessionId()

    assertThat(sessionId1).isNotEqualTo(sessionId2)
  }

  @Test
  fun newSessionFollowingCrash() {
    if (!BuildConfig.SHOULD_CRASH_APP) return

    launchApp()
    val origSession = getCurrentSessionId()
    getButton("CRASH!").click()
    dismissPossibleErrorDialog()

    launchApp()

    Thread.sleep(TIME_TO_PROPAGATE_SESSION)
    val newSession = getCurrentSessionId()
    assertThat(newSession).isNotEqualTo(origSession)
  }

  @Test
  fun nonFatalMainActivity() {
    launchApp()
    val origSession = getCurrentSessionId()

    getButton("NON FATAL").click()
    device.waitForIdle()

    Thread.sleep(TIME_TO_PROPAGATE_SESSION)
    val newSession = getCurrentSessionId()
    assertThat(origSession).isEqualTo(newSession)
  }

  @Test
  fun anrMainActivity() {
    if (!BuildConfig.SHOULD_CRASH_APP) return
    launchApp()
    val origSession = getCurrentSessionId()

    getButton("ANR").click()
    device.waitForIdle()
    dismissPossibleAnrDialog()

    launchApp()

    Thread.sleep(TIME_TO_PROPAGATE_SESSION)
    val newSession = getCurrentSessionId()
    assertThat(origSession).isNotEqualTo(newSession)
  }

  @Test
  fun crashSecondaryProcess() {
    if (!BuildConfig.SHOULD_CRASH_APP) return
    launchApp()
    navigateToSecondActivity()
    val origSession = getCurrentSessionId()

    getButton("CRASH!").click()
    dismissPossibleErrorDialog()

    launchApp()

    Thread.sleep(TIME_TO_PROPAGATE_SESSION)
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
    device.waitForIdle()
  }

  private fun navigateToSecondActivity() {
    device.wait(Until.hasObject(By.text("NEXT ACTIVITY").depth(0)), TRANSITION_TIMEOUT)
    val nextActivityButton =
      device.findObject(By.text("NEXT ACTIVITY").clazz("android.widget.Button"))
    nextActivityButton?.click()
    device.wait(Until.hasObject(By.pkg(TEST_APP_PACKAGE).depth(0)), TRANSITION_TIMEOUT)
  }

  private fun getButton(text: String): UiObject2 {
    device.wait(Until.hasObject(By.text(text).depth(0)), TRANSITION_TIMEOUT)
    val button = device.findObject(By.text(text).clazz("android.widget.Button"))
    if (button == null) {
      fail("Could not locate button with text $text")
    }
    return button
  }

  private fun dismissPossibleAnrDialog() {
    device.wait(
      Until.hasObject(By.clazz("com.android.server.am.AppNotRespondingDialog")),
      TRANSITION_TIMEOUT
    )
    device.findObject(By.text("Close app").clazz("android.widget.Button"))?.click()
  }

  private fun dismissPossibleErrorDialog() {
    device.wait(
      Until.hasObject(By.clazz("com.android.server.am.AppErrorDialog")),
      TRANSITION_TIMEOUT
    )
    device.findObject(By.text("Close app").clazz("android.widget.Button"))?.click()
  }

  private fun background() {
    device.pressHome()
    device.wait(Until.hasObject(By.pkg(device.launcherPackageName).depth(0)), TRANSITION_TIMEOUT)
  }

  private fun foreground() {
    device.pressRecentApps()
    Thread.sleep(1_000L)
    device.click(device.displayWidth / 2, device.displayHeight / 2)
    device.wait(Until.hasObject(By.pkg(TEST_APP_PACKAGE).depth(0)), TRANSITION_TIMEOUT)
    device.waitForIdle()
  }

  private fun getCurrentSessionId(): String? {
    device.wait(
      Until.hasObject(By.res(Pattern.compile(".*session_id_(fragment|second)_text")).depth(0)),
      TRANSITION_TIMEOUT
    )
    return device.findObject(By.res(Pattern.compile(".*session_id_(fragment|second)_text")))?.text
  }

  companion object {
    private const val TEST_APP_PACKAGE = "com.google.firebase.testing.sessions"
    private const val LAUNCH_TIMEOUT = 5_000L
    private const val TRANSITION_TIMEOUT = 1_000L
    private const val TIME_TO_PROPAGATE_SESSION = 5_000L
  }
}
