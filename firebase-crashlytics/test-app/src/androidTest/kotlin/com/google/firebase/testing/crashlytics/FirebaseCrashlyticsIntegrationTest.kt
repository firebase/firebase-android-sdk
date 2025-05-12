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
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import java.util.regex.Pattern
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

const val APP_NAME = "com.google.firebase.testing.crashlytics"

/**
 * Integration tests for Firebase Crashlytics scenarios. Each test: 1) Launches the app 2) Clicks a
 * specific button that sets user ID & triggers crash/no-crash logic 3) If there's a crash, relaunch
 * the app to send the crash 4) Then read the user ID from the textView (after crash & relaunch) 5)
 * Logs a console link for manual verification
 */
@RunWith(AndroidJUnit4::class)
class FirebaseCrashlyticsIntegrationTest {

  private lateinit var device: UiDevice

  @Before
  fun setup() {
    device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
  }

  @After
  fun cleanup() {
    // Force-stop the app after each test to start fresh next time
    Runtime.getRuntime().exec(arrayOf("am", "force-stop", APP_NAME))
  }

  /** Helper method: read logcat (only used to verify Crashlytics init in one test). */
  private fun readLogcat(tagFilter: String): Boolean {
    val logs = mutableListOf<String>()
    val process = Runtime.getRuntime().exec("logcat -d")
    process.inputStream.bufferedReader().useLines { lines ->
      lines.filter { it.contains(tagFilter) }.forEach { logs.add(it) }
    }
    return logs.any { it.contains(tagFilter) }
  }

  /** Helper: Build Crashlytics console search URL for a given userId. */
  private fun getCrashlyticsSearchUrl(userId: String): String {
    return "https://console.firebase.google.com/project/crashlytics-e2e/" +
      "crashlytics/app/android:com.google.firebase.testing.crashlytics/search" +
      "?time=last-seven-days&types=crash&q=$userId"
  }

  /** Helper: Launch the app from the home screen. */
  private fun launchApp() {
    device.pressHome()
    device.wait(Until.hasObject(By.pkg(device.launcherPackageName).depth(0)), LAUNCH_TIMEOUT)

    val context = ApplicationProvider.getApplicationContext<Context>()
    val intent =
      context.packageManager.getLaunchIntentForPackage(APP_NAME)?.apply {
        addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
      }
    context.startActivity(intent)

    device.wait(Until.hasObject(By.pkg(TEST_APP_PACKAGE).depth(0)), LAUNCH_TIMEOUT)
    device.waitForIdle()
  }

  /**
   * Helper: Find a button by text and click it. The app's buttons appear to be uppercase, so we do
   * uppercase() to match.
   */
  private fun clickButton(buttonText: String) {
    val uppercaseButtonText = buttonText.uppercase(Locale.getDefault())
    device.wait(Until.hasObject(By.text(uppercaseButtonText).depth(0)), TRANSITION_TIMEOUT)
    val buttonObj = device.findObject(By.text(uppercaseButtonText).clazz("android.widget.Button"))
    if (buttonObj == null) {
      fail("Could not locate button with text $buttonText")
    }
    buttonObj.click()
  }

  /**
   * Helper: Read the user ID from the textView that displays it in the app. e.g., "UserId:
   * SomeValue" Because we are reading AFTER the crash (app is relaunched), the app persists the
   * user ID via SharedPreferences.
   */
  private fun readDisplayedUserId(): String {
    // Wait up to 3 seconds for a TextView that matches the pattern "UserId: ..."
    device.wait(Until.hasObject(By.text(Pattern.compile("UserId:.*"))), 3000)

    // Find the object using the same pattern
    val userIdObj = device.findObject(By.text(Pattern.compile("UserId:.*")))

    // If found, remove the "UserId: " prefix
    return userIdObj?.text?.substringAfter("UserId: ") ?: "UNKNOWN_USER_ID"
  }

  /** Helper: Read the "Did crash previously?" text from the app. */
  private fun readDidCrashPreviouslyText(): String {
    // Wait for up to 3 seconds for the text
    device.wait(Until.hasObject(By.text(Pattern.compile("HasCrashed:.*"))), 3000)

    // Find the object by resource ID
    val didCrashObj = device.findObject(By.text(Pattern.compile("HasCrashed:.*")))
    return didCrashObj.text ?: "(unknown)"
  }

  // ---------------------------------------------------------------------------
  //  Shared / Common Tests
  // ---------------------------------------------------------------------------

  @Test
  fun shared_Initialize_Crashlytics() {
    launchApp()
    clickButton("Shared_Initialize_Crashlytics")

    // This test does NOT crash, so we read the ID in the same session
    val userId = readDisplayedUserId()

    // Check logs to confirm Crashlytics initialization
    val crashlyticsInitialized = readLogcat("Initializing Firebase Crashlytics")
    assertThat(crashlyticsInitialized).isTrue()

    Log.i(
      "TestInfo",
      "Check Crashlytics initialization. userId=$userId => ${getCrashlyticsSearchUrl(userId)}"
    )
  }

  @Test
  fun shared_Generate_Crash() {
    launchApp()
    clickButton("Shared_Generate_Crash")

    // Crash => relaunch
    launchApp()
    // Now read the user ID after the crash
    val userId = readDisplayedUserId()

    Log.i(
      "TestInfo",
      "Verify crash in console for userId=$userId => ${getCrashlyticsSearchUrl(userId)}"
    )
  }

  @Test
  fun shared_Verify_Crash() {
    launchApp()
    clickButton("Shared_Verify_Crash")

    // Crash => relaunch
    launchApp()
    val userId = readDisplayedUserId()

    Log.i("TestInfo", "After crashing, verify userId=$userId => ${getCrashlyticsSearchUrl(userId)}")
  }

  @Test
  fun shared_Verify_No_Crash() {
    launchApp()
    clickButton("Shared_Verify_No_Crash")

    // No crash, so read the user ID now
    val userId = readDisplayedUserId()

    Log.i("TestInfo", "Verify NO crash for userId=$userId => ${getCrashlyticsSearchUrl(userId)}")
  }

  // ---------------------------------------------------------------------------
  //  Core Scenario
  // ---------------------------------------------------------------------------

  @Test
  fun firebaseCore_Fatal_Error() {
    launchApp()
    clickButton("FirebaseCore_Fatal_Error")

    // Crash => relaunch
    launchApp()
    val userId = readDisplayedUserId()

    Log.i("TestInfo", "Check console for userId=$userId => ${getCrashlyticsSearchUrl(userId)}")
  }

  // ---------------------------------------------------------------------------
  //  Public APIs
  // ---------------------------------------------------------------------------

  @Test
  fun public_API_Log() {
    launchApp()
    clickButton("Public_API_Log")

    // Crash => relaunch
    launchApp()
    val userId = readDisplayedUserId()

    Log.i(
      "TestInfo",
      "Verify custom logs in Crashlytics for userId=$userId => ${getCrashlyticsSearchUrl(userId)}"
    )
  }

  @Test
  fun public_API_SetCustomValue() {
    launchApp()
    clickButton("Public_API_SetCustomValue")

    // Crash => relaunch
    launchApp()
    val userId = readDisplayedUserId()

    Log.i(
      "TestInfo",
      "Verify custom keys in Crashlytics for userId=$userId => ${getCrashlyticsSearchUrl(userId)}"
    )
  }

  @Test
  fun public_API_SetUserID() {
    launchApp()
    clickButton("Public_API_SetUserID")

    // Crash => relaunch
    launchApp()
    val userId = readDisplayedUserId()

    Log.i(
      "TestInfo",
      "Verify user ID in Crashlytics: userId=$userId => ${getCrashlyticsSearchUrl(userId)}"
    )
  }

  @Ignore("This test is temporarily ignored due workarounds for TestLab compatibility.")
  @Test
  fun public_API_DidCrashPreviously() {
    launchApp()

    // Close the app
    closeAppFromRecents(device)

    launchApp()
    val hasCrashedText = readDidCrashPreviouslyText()
    assertThat(hasCrashedText).contains("HasCrashed: false")

    clickButton("Public_API_DidCrashPreviously")
    // Crash => relaunch
    launchApp()
    val userId = readDisplayedUserId()
    val hasCrashedTextAfter = readDidCrashPreviouslyText()
    assertThat(hasCrashedTextAfter).contains("HasCrashed: true")

    Log.i(
      "TestInfo",
      "public_API_DidCrashPreviously => userId=$userId => ${getCrashlyticsSearchUrl(userId)}"
    )
  }

  @Test
  fun public_API_RecordException() {
    launchApp()
    clickButton("Public_API_RecordException")

    // This test does NOT crash, so read user ID in the same session
    val userId = readDisplayedUserId()

    Log.i(
      "TestInfo",
      "Check Crashlytics non-fatal events for userId=$userId => ${getCrashlyticsSearchUrl(userId)}"
    )
  }

  // ---------------------------------------------------------------------------
  //  Data Collection APIs
  // ---------------------------------------------------------------------------

  @Test
  fun dataCollection_Default() {
    launchApp()
    clickButton("DataCollection_Default")

    // Crash => relaunch
    launchApp()
    val userId = readDisplayedUserId()

    Log.i(
      "TestInfo",
      "Check default data collection, userId=$userId => ${getCrashlyticsSearchUrl(userId)}"
    )
  }

  @Test
  fun dataCollection_Firebase_Off() {
    launchApp()
    clickButton("DataCollection_Firebase_Off")

    // Crash => relaunch
    launchApp()
    val userId = readDisplayedUserId()

    Log.i(
      "TestInfo",
      "Verify no crash is reported for userId=$userId => ${getCrashlyticsSearchUrl(userId)}"
    )
  }

  @Test
  fun dataCollection_Crashlytics_Off() {
    launchApp()
    clickButton("DataCollection_Crashlytics_Off")

    // Crash => relaunch
    launchApp()
    val userId = readDisplayedUserId()

    Log.i(
      "TestInfo",
      "Crash should not be uploaded. userId=$userId => ${getCrashlyticsSearchUrl(userId)}"
    )
  }

  @Test
  fun dataCollection_Crashlytics_Off_Then_On() {
    launchApp()
    clickButton("DataCollection_Crashlytics_Off_Then_On")

    // Crash => relaunch
    launchApp()
    val userId = readDisplayedUserId()

    // In the real scenario, you'd setCrashlyticsCollectionEnabled(true) after the relaunch
    Log.i(
      "TestInfo",
      "Check if previously cached crash for userId=$userId is now sent => ${getCrashlyticsSearchUrl(userId)}"
    )
  }

  @Test
  fun dataCollection_Crashlytics_Off_Then_Send() {
    launchApp()
    clickButton("DataCollection_Crashlytics_Off_Then_Send")

    // Crash => relaunch
    launchApp()
    val userId = readDisplayedUserId()

    // e.g. FirebaseCrashlytics.getInstance().sendUnsentReports()
    Log.i(
      "TestInfo",
      "Check if crash is now sent for userId=$userId => ${getCrashlyticsSearchUrl(userId)}"
    )
  }

  @Test
  fun dataCollection_Crashlytics_Off_Then_Delete() {
    launchApp()
    clickButton("DataCollection_Crashlytics_Off_Then_Delete")

    // Crash => relaunch
    launchApp()
    val userId = readDisplayedUserId()

    // e.g. deleteUnsentReports() + sendUnsentReports()
    Log.i(
      "TestInfo",
      "Confirm no crash is uploaded for userId=$userId => ${getCrashlyticsSearchUrl(userId)}"
    )
  }

  @Test
  fun interoperability_IID() {
    launchApp()
    clickButton("Interoperability_IID")

    // The app crashes => relaunch
    launchApp()
    val userId = readDisplayedUserId()

    Log.i("TestInfo", "Interoperability_IID. userId=$userId => ${getCrashlyticsSearchUrl(userId)}")
  }

  // ---------------------------------------------------------------------------
  //  Navigation & UI Helpers
  // ---------------------------------------------------------------------------

  private fun closeAppFromRecents(
    device: UiDevice,
  ) {
    // 1) Open Recent Apps
    device.pressRecentApps()

    // 2) Wait a moment for Recents to appear
    Thread.sleep(1000)

    // 3) Swipe upward from the middle of the screen
    //    to about a quarter of the screen height (adjust as needed).
    val startX = device.displayWidth / 2
    val startY = device.displayHeight / 2
    val endX = device.displayWidth / 2
    val endY = device.displayHeight / 4

    // 'steps' parameter controls the speed/animation of the swipe
    // Larger = slower swipe, smaller = faster
    device.swipe(startX, startY, endX, endY, 5)

    // Wait a bit to ensure the system completes the action
    Thread.sleep(1000)
  }

  companion object {
    private const val TEST_APP_PACKAGE = "com.google.firebase.testing.crashlytics"
    private const val LAUNCH_TIMEOUT = 5_000L
    private const val TRANSITION_TIMEOUT = 1_000L
  }
}
