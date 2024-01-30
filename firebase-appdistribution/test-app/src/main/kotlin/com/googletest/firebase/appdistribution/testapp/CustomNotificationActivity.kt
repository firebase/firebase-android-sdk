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

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.AlertDialog
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.*

class CustomNotificationActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_custom_notification)

    val launcher =
      registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
          Log.i(TAG, "Permission granted, showing notification")
          CustomNotificationFeedbackTrigger.showNotification(this)
        } else {
          Log.i(TAG, "Permission not granted")
          AlertDialog.Builder(this)
            .setMessage(
              "Because the notification permission has been denied, the app will not show a " +
                "notification that can be tapped to send feedback to the developer."
            )
            .setPositiveButton("OK") { _, _ -> }
            .show()
        }
      }

    if (ContextCompat.checkSelfPermission(this, POST_NOTIFICATIONS) == PERMISSION_DENIED) {
      if (shouldShowRequestPermissionRationale(POST_NOTIFICATIONS)) {
        Log.i(TAG, "Showing customer rationale for requesting permission.")
        AlertDialog.Builder(this)
          .setMessage(
            "Using a notification to initiate feedback to the developer. " +
              "To enable this feature, allow the app to post notifications."
          )
          .setPositiveButton("OK") { _, _ ->
            Log.i(TAG, "Launching request for permission.")
            launcher.launch(POST_NOTIFICATIONS)
          }
          .setNegativeButton("No thanks") { _, _ -> Log.i(TAG, "User denied permission request.") }
          .show()
      } else {
        Log.i(TAG, "Launching request for permission without rationale.")
        launcher.launch(POST_NOTIFICATIONS)
      }
    } else {
      CustomNotificationFeedbackTrigger.showNotification(this)
    }
  }

  override fun onDestroy() {
    CustomNotificationFeedbackTrigger.cancelNotification()
    super.onDestroy()
  }

  companion object {
    private const val TAG = "CustomNotificationActivity"
  }
}
