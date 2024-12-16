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
import com.google.common.truth.Truth.assertThat
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.util.regex.Pattern
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

const val APP_NAME = "com.google.firebase.testing.crashlytics"

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

  /**
   * Helper function to check logs for a certain term.
   */
  private fun readLogcat(tagFilter: String): Boolean {
    val logs = mutableListOf<String>()
    val process = Runtime.getRuntime().exec("logcat -d")
    process.inputStream.bufferedReader().useLines { lines ->
      lines.filter { it.contains(tagFilter) }.forEach { logs.add(it) }
    }
    return logs.any { it.contains(tagFilter) }
  }

  /**
   * Reusable steps and helpers.
   */

  @Test
  // Checks that Crashlytics is Enabled in Logcat
  fun sharedInitializeCrashlytics() {
    launchApp()

    val crashlyticsInitialized = readLogcat("Initializing Firebase Crashlytics")
    assertThat(crashlyticsInitialized).isTrue()
  }

  @Test
  // Crash can be found associated with set user id.
  fun sharedGenerateCrash() {
    launchApp()

    val crashlytics = FirebaseCrashlytics.getInstance()
    val testUserId = "TestUser123"
    crashlytics.setUserId(testUserId)

    getButton("CRASH!").click()

    Log.d("CrashlyticsTest", "User ID set: $testUserId")

    val userIdLogFound = readLogcat("TestUser123")
    val crashFound = readLogcat("Test")

    assertThat(userIdLogFound).isTrue()
    assertThat(crashFound).isTrue()
  }

  @Test
  fun sharedVerifyCrash() {
    launchApp()

    val crashlytics = FirebaseCrashlytics.getInstance()
    val testUserId = "TestUser123"
    val customValue = "TestValue"
    val customKey = "CustomKey"
    crashlytics.setCustomKey(customKey, customValue)
    crashlytics.setUserId(testUserId)

    Log.d("CrashlyticsTest", "Custom Key set: $customKey = $customValue")
    Log.d("CrashlyticsTest", "User ID set: $testUserId")
    Log.d("CrashlyticsTest", "This is a pre-crash log for verification.")

    getButton("CRASH!").click()

    val userIdLogFound = readLogcat("TestUser123")
    val customKeyLogFound = readLogcat("CustomKey")
    val preCrashLogFound = readLogcat("This is a pre-crash log for verification.")

    assertThat(userIdLogFound).isTrue()
    assertThat(customKeyLogFound).isTrue()
    assertThat(preCrashLogFound).isTrue()
  }

  @Test
  fun sharedVerifyNoCrash() {
    launchApp()

    val crashlytics = FirebaseCrashlytics.getInstance()
    val testUserId = "TestUser123"
    val customValue = "TestValue"
    val customKey = "CustomKey"
    crashlytics.setCustomKey(customKey, customValue)
    crashlytics.setUserId(testUserId)

    Log.d("CrashlyticsTest", "Custom Key set: $customKey = $customValue")
    Log.d("CrashlyticsTest", "User ID set: $testUserId")
    Log.d("CrashlyticsTest", "This is a pre-crash log for verification.")

    navigateToSecondActivity()
    Thread.sleep(2000)

    val userIdLogFound = readLogcat("TestUser123")
    val customKeyLogFound = readLogcat("CustomKey")
    val preCrashLogFound = readLogcat("This is a pre-crash log for verification.")

    assertThat(userIdLogFound).isTrue()
    assertThat(customKeyLogFound).isTrue()
    assertThat(preCrashLogFound).isTrue()
  }

  @Test
  // Fatal errors are reported by crashlytics
  fun firebaseCoreFatalError() {
    launchApp()
    getButton("CRASH!").click()

    val crashFound = readLogcat("Test")
    assertThat(crashFound).isTrue()
  }


  /**
   * Public API Tests
   */

  @Test
  fun public_API_Log() {
    /*
     * Steps:
     * 1. Initialize Crashlytics
     * 2. Call the log API multiple times with custom strings
     * 3. Generate a crash
     * 4. Verify crash is reported and all custom messages are associated
     */

    launchApp()

    getButton("CRASH WITH CUSTOM LOG").click()

    launchApp()

    // Verification instruction:
    Log.i(
      "TestInfo",
      "Verify on the Crashlytics console that the crash contains 'Custom log message 1' and 'Custom log message 2'. " +
              "View: https://console.firebase.google.com/project/crashlytics-e2e/crashlytics/app/android:com.google.firebase.testing.crashlytics/issues?time=last-seven-days&state=all&tag=all&sort=eventCount&types=crash&issuesQuery=CRASH%20WITH"
    )
  }

  @Test
  fun public_API_SetCustomValue() {
    /*
     * Steps:
     * 1. Initialize Crashlytics
     * 2. Set multiple custom keys
     * 3. Generate a crash
     * 4. Verify custom key-value pairs in the crash report
     */

    launchApp()
    val crashlytics = FirebaseCrashlytics.getInstance()

    crashlytics.setCustomKey("key", "value")
    crashlytics.setCustomKey("number", 42)
    Log.d("CrashlyticsTest", "Set custom keys: key=value, number=42")

    getButton("CRASH!").click()

    launchApp()

    // Verification instruction:
    Log.i(
      "TestInfo",
      "Verify on the Crashlytics console that the crash contains the custom keys 'key=value' and 'number=42'. " +
              "View: https://console.firebase.google.com/project/crashlytics-e2e/crashlytics/app/android:com.google.firebase.testing.crashlytics/issues?time=last-seven-days&state=open&tag=all&sort=eventCount&issuesQuery=test"
    )
  }

  @Test
  fun public_API_SetUserID() {
    /*
     * Steps:
     * 1. Initialize Crashlytics
     * 2. Set a known user ID
     * 3. Generate a crash
     * 4. Verify the user ID is associated with the crash
     */

    launchApp()
    val crashlytics = FirebaseCrashlytics.getInstance()

    val testUserId = "user-1"
    crashlytics.setUserId(testUserId)
    Log.d("CrashlyticsTest", "User ID set: $testUserId")

    getButton("CRASH!").click()

    val userIdFound = readLogcat("user-1")
    assertThat(userIdFound).isTrue()

    // Verification instruction:
    Log.i(
      "TestInfo",
      "Verify on the Crashlytics console that the user ID 'user-1' is associated with the crash. " +
              "View: https://console.firebase.google.com/project/crashlytics-e2e/crashlytics/app/android:com.google.firebase.testing.crashlytics/issues?time=last-seven-days&state=open&tag=all&sort=eventCount&issuesQuery=test"
    )
  }

  @Test
  fun public_API_DidCrashPreviously() {
    /*
     * Steps:
     * 1. Launch and initialize Crashlytics
     * 2. On first launch, didCrashOnPreviousExecution should be false
     * 3. Generate a crash
     * 4. Relaunch app, now didCrashOnPreviousExecution should be true
     * 5. Relaunch again without crashing, now didCrashOnPreviousExecution should be false
     *
     * Note: This test simulates the sequence by restarting the app and checking logs.
     * In a real scenario, you might need separate test steps or manual verification.
     */

    // First Launch
    launchApp()
    val crashlytics = FirebaseCrashlytics.getInstance()
    val firstCheck = crashlytics.didCrashOnPreviousExecution()
    Log.d("CrashlyticsTest", "Did crash previously (initial): $firstCheck")
    assertThat(firstCheck).isFalse()

    // Generate a crash
    getButton("CRASH!").click()
    // The crash terminates the app, so after the crash, we assume the test harness relaunches it.

    // Second Launch (after crash)
    launchApp()
    val crashlyticsSecond = FirebaseCrashlytics.getInstance()
    val secondCheck = crashlyticsSecond.didCrashOnPreviousExecution()
    Log.d("CrashlyticsTest", "Did crash previously (after crash): $secondCheck")
    assertThat(secondCheck).isTrue()

    // No crash this time, just close and relaunch again
    cleanup() // Force stop to simulate a fresh launch
    launchApp()
    val crashlyticsThird = FirebaseCrashlytics.getInstance()
    val thirdCheck = crashlyticsThird.didCrashOnPreviousExecution()
    Log.d("CrashlyticsTest", "Did crash previously (third launch): $thirdCheck")
    assertThat(thirdCheck).isFalse()

    // Verification instruction:
    Log.i(
      "TestInfo",
      "Check logs to ensure didCrashOnPreviousExecution transitioned as expected. " +
              "Also verify in Crashlytics console that the crash event is recorded. " +
              "View: https://console.firebase.google.com/project/crashlytics-e2e/crashlytics/app/android:com.google.firebase.testing.crashlytics/issues?time=last-seven-days&state=open&tag=all&sort=eventCount&issuesQuery=test"
    )
  }

  @Test
  fun public_API_RecordException() {
    /*
     * Steps:
     * 1. Initialize Crashlytics
     * 2. Set user ID
     * 3. Record multiple non-fatal exceptions
     * 4. Close and relaunch app
     * 5. Verify non-fatal exceptions are reported by filtering events as Non-fatals in the Crashlytics console
     *    and check for user info as keys/values.
     */

    launchApp()
    val crashlytics = FirebaseCrashlytics.getInstance()
    val testUserId = "user-2"
    crashlytics.setUserId(testUserId)
    crashlytics.setCustomKey("env", "test-environment")
    Log.d("CrashlyticsTest", "User ID set: $testUserId, custom key 'env=test-environment' set.")

    // Record multiple non-fatal exceptions
    val e1 = RuntimeException("non-fatal 1")
    val e2 = RuntimeException("non-fatal 2")
    crashlytics.recordException(e1)
    crashlytics.recordException(e2)
    Log.d("CrashlyticsTest", "Recorded two non-fatal exceptions.")

    // Simulate a relaunch by force stopping and then launching again
    cleanup()
    launchApp()

    // Verification instruction:
    Log.i(
      "TestInfo",
      "Verify non-fatal exceptions are reported in the Crashlytics console. Filter by Non-fatals and look for 'user-2' and key 'env=test-environment'. " +
              "View: https://console.firebase.google.com/project/crashlytics-e2e/crashlytics/app/android:com.google.firebase.testing.crashlytics/issues?time=last-seven-days&state=open&tag=all&sort=eventCount&issuesQuery=test"
    )
  }

  /**
   * Helper and navigation methods
   */

  private fun launchApp() {
    device.pressHome()
    device.wait(Until.hasObject(By.pkg(device.launcherPackageName).depth(0)), LAUNCH_TIMEOUT)

    val context = ApplicationProvider.getApplicationContext<Context>()
    val intent = context.packageManager.getLaunchIntentForPackage(APP_NAME)?.apply {
      addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
    context.startActivity(intent)

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

  companion object {
    private const val TEST_APP_PACKAGE = "com.google.firebase.testing.crashlytics"
    private const val LAUNCH_TIMEOUT = 5_000L
    private const val TRANSITION_TIMEOUT = 1_000L
  }
}