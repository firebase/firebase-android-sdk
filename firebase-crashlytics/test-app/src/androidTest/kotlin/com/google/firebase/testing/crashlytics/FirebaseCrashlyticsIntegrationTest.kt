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

package com.google.firebase.testing.crashlytics

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.google.common.base.Verify.verify
import com.google.common.truth.Truth.assertThat
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.util.regex.Pattern
import org.junit.After
import org.junit.Assert
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

const val APP_NAME = "com.example.test_app"

@RunWith(AndroidJUnit4::class)
class FirebaseCrashlyticsIntegrationTest {

  private lateinit var device: UiDevice

  @Before
  fun setup() {
    // Initialize UiDevice instance
    device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
  }

  @After
  fun cleanup() {
    // Make sure all processes are killed
    Runtime.getRuntime().exec(arrayOf("am", "force-stop", APP_NAME))
  }

  // Reuseable function to check logs for a certain term.
  fun readLogcat(tagFilter: String): Boolean {
    val logs = mutableListOf<String>()
    val process = Runtime.getRuntime().exec("logcat -d") // Fetch error-level logs
    process.inputStream.bufferedReader().useLines { lines ->
      lines.filter { it.contains(tagFilter) }.forEach { logs.add(it) }
    }
    return logs.any { it.contains(tagFilter) }
  }

  // Shared/Common Test Steps

  @Test
  // Checks that Crashlytics is Enabled in Logcat
  fun sharedInitializeCrashlytics() {
    launchApp();

    val crashlyticsInitialized = readLogcat("Initializing Firebase Crashlytics");

    assertThat(crashlyticsInitialized).isTrue()
  }

  @Test
  // Crash can be found associated with set user id.
  fun sharedGenerateCrash() {
    launchApp()

    val crashlytics = FirebaseCrashlytics.getInstance()
    val testUserId = "TestUser123"
    crashlytics.setUserId("TestUser123")

    getButton("CRASH!").click();

    Log.d("CrashlyticsTest", "User ID set: $testUserId")

    val userIdLogFound = readLogcat("TestUser123")
    val crashFound = readLogcat("Test")

    assertThat(userIdLogFound).isTrue()
    assertThat(crashFound).isTrue()

  }

  @Test
  fun sharedVerifyCrash() {
    launchApp();

    // Initialize Crashlytics and set user metadata as they would online
    val crashlytics = FirebaseCrashlytics.getInstance()
    val testUserId = "TestUser123"
    val customValue = "TestValue"
    val customKey = "CustomKey"
    // Mock custom values
    crashlytics.setCustomKey(customKey, customValue)
    crashlytics.setUserId("TestUser123")

    Log.d("CrashlyticsTest", "Custom Key set: $customKey = $customValue")
    Log.d("CrashlyticsTest", "User ID set: $testUserId")
    Log.d("CrashlyticsTest","This is a pre-crash log for verification.")

    // Trigger the crash
    getButton("CRASH!").click()


    // Check logcat for defined fields
    val userIdLogFound = readLogcat("TestUser123")
    val customKeyLogFound = readLogcat("CustomKey")
    val preCrashLogFound = readLogcat("This is a pre-crash log for verification.")

    //Verify the logs were recorded
    assertThat(userIdLogFound).isTrue()
    assertThat(customKeyLogFound).isTrue()
    assertThat(preCrashLogFound).isTrue()
  }

  @Test
  fun sharedVerifyNoCrash() {
    launchApp();

    // Initialize Crashlytics and set user metadata as they would online
    val crashlytics = FirebaseCrashlytics.getInstance()
    val testUserId = "TestUser123"
    val customValue = "TestValue"
    val customKey = "CustomKey"
    // Mock custom values
    crashlytics.setCustomKey(customKey, customValue)
    crashlytics.setUserId("TestUser123")

    Log.d("CrashlyticsTest", "Custom Key set: $customKey = $customValue")
    Log.d("CrashlyticsTest", "User ID set: $testUserId")
    Log.d("CrashlyticsTest","This is a pre-crash log for verification.")

    // Check logcat for defined fields
    val userIdLogFound = readLogcat("TestUser123")
    val customKeyLogFound = readLogcat("CustomKey")
    val preCrashLogFound = readLogcat("This is a pre-crash log for verification.")

    // User activity and navigation
    navigateToSecondActivity();
    // Simulate user idling in app
    Thread.sleep(2000)

    //Verify the logs were recorded
    assertThat(userIdLogFound).isTrue()
    assertThat(customKeyLogFound).isTrue()
    assertThat(preCrashLogFound).isTrue()
  }

  // Core Scenario

  @Test
  // Fatal errors are reported by crashlytics
  fun firebaseCoreFatalError() {
    launchApp()

    getButton("CRASH!").click();

    val crashFound = readLogcat("Test")
    assertThat(crashFound).isTrue()
  }

  // Public APIs

//  @Test
//  fun publicAPILog() {
//
//  }
//
//  @Test
//  fun publicAPISetCustomValue() {
//
//  }
//
//  @Test
//  fun publicAPISetUserID() {
//
//  }
//
//  @Test
//  fun publicAPIDidCrashPreviously() {
//
//  }
//
//  @Test
//  fun publicAPIRecordException() {
//
//  }


  // Keeping here for now as an example
  @Test
  fun sameSessionIdBetweenActivitiesOnDifferentProcesses() {
    launchApp()

    val sessionId1 = getCurrentSessionId()
    navigateToSecondActivity()
    Thread.sleep(TIME_TO_PROPAGATE_SESSION)
    val sessionId2 = getCurrentSessionId()

    assertThat(sessionId1).isEqualTo(sessionId2)
  }


  private fun launchApp() {
    // Start from the home screen
    device.pressHome()

    // Wait for launcher
    device.wait(Until.hasObject(By.pkg(device.launcherPackageName).depth(0)), LAUNCH_TIMEOUT)

    // Launch the app
    val context = ApplicationProvider.getApplicationContext<Context>()
    val intent =
      context.packageManager.getLaunchIntentForPackage(APP_NAME)?.apply {
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
    private const val TEST_APP_PACKAGE = "com.google.firebase.testing.crashlytics"
    private const val LAUNCH_TIMEOUT = 5_000L
    private const val TRANSITION_TIMEOUT = 1_000L
    private const val TIME_TO_PROPAGATE_SESSION = 5_000L
  }
}
