/*
 * Copyright 2022 Google LLC
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

package com.googletest.firebase.appdistribution.testapp

import android.app.AlertDialog
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.googletest.firebase.appdistribution.testapp.ScreenshotDetectionFeedbackTrigger.Companion.screenshotReadPermission
import java.util.*

class ScreenshotDetectionActivity : AppCompatActivity() {
  private val handlerThread = HandlerThread("AppDistroFeedbackTrigger").also { it.start() }
  private var screenshotDetectionFeedbackTrigger =
    ScreenshotDetectionFeedbackTrigger(
      this,
      R.string.feedbackAdditionalFormText,
      Handler(handlerThread.looper)
    )

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_screenshot_detection)

    val launcher =
      registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (!isGranted) {
          Log.i(TAG, "Permission not granted")
          AlertDialog.Builder(this)
            .setMessage(
              "Because storage permission has been denied, screenshots taken will not trigger feedback to the developer."
            )
            .setPositiveButton("OK") { _, _ -> }
            .show()
        }
      }

    if (ContextCompat.checkSelfPermission(this, screenshotReadPermission) == PERMISSION_DENIED) {
      if (shouldShowRequestPermissionRationale(screenshotReadPermission)) {
        Log.i(TAG, "Showing customer rationale for requesting permission.")
        AlertDialog.Builder(this)
          .setMessage(
            "Taking a screenshot of the app can initiate feedback to the developer. To enable this feature, allow the app access to device storage."
          )
          .setPositiveButton("OK") { _, _ ->
            Log.i(TAG, "Launching request for permission.")
            launcher.launch(screenshotReadPermission)
          }
          .setNegativeButton("No thanks") { _, _ -> Log.i(TAG, "User denied permission request.") }
          .show()
      } else {
        Log.i(TAG, "Launching request for permission without rationale.")
        launcher.launch(screenshotReadPermission)
      }
    }

    screenshotDetectionFeedbackTrigger.listenForScreenshots()
  }

  override fun onDestroy() {
    screenshotDetectionFeedbackTrigger.stopListeningForScreenshots()
    super.onDestroy()
  }

  companion object {
    private const val TAG = "ScreenshotDetectionActivity"
  }
}
