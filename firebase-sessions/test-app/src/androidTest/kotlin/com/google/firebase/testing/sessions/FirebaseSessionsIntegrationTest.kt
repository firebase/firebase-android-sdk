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
import androidx.test.uiautomator.Until
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 18)
class FirebaseSessionsIntegrationTest {

  private lateinit var device: UiDevice

  @Before
  fun startMainActivityFromHomeScreen() {
    // Initialize UiDevice instance
    device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

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

  @Test
  fun crashMainProcess() {
    val crashButton = device.findObject(By.text("CRASH!").clazz("android.widget.Button"))

    if (crashButton != null) {
      crashButton.click()
    } else {
      fail("Could not locate crash button on app screen.")
    }
  }

  @Test
  fun nonFatalMainProcess() {
    val nonFatalButton = device.findObject(By.text("NON FATAL").clazz("android.widget.Button"))

    if (nonFatalButton != null) {
      nonFatalButton.click()
    } else {
      fail("Could not locate non fatal button on app screen.")
    }
  }

  @Test
  fun anrMainProcess() {
    val anrButton = device.findObject(By.text("ANR").clazz("android.widget.Button"))

    if (anrButton != null) {
      anrButton.click()
    } else {
      fail("Could not locate anr button on app screen.")
    }
  }

  @Test
  fun crashSecondaryProcess() {
    val nextActivityButton =
      device.findObject(By.text("NEXT ACTIVITY").clazz("android.widget.Button"))
    nextActivityButton?.click()
    device.wait(Until.hasObject(By.pkg(TEST_APP_PACKAGE).depth(0)), LAUNCH_TIMEOUT)
    val crashButton = device.findObject(By.text("CRASH!").clazz("android.widget.Button"))

    if (crashButton != null) {
      crashButton.click()
    } else {
      fail("Could not locate crash button on secondary app screen.")
    }
  }

  companion object {
    private const val TEST_APP_PACKAGE = "com.google.firebase.testing.sessions"
    private const val LAUNCH_TIMEOUT = 5_000L
  }
}
