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
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.appdistribution.ktx.appDistribution
import com.google.firebase.ktx.Firebase
import java.io.IOException

/**
 * Shows an ongoing notification that the user can tap to take a screenshot and send feedback to the
 * developer.
 */
@SuppressLint("StaticFieldLeak") // Reference to Activity is set to null in onActivityDestroyed
object CustomNotificationFeedbackTrigger {
  private const val TAG: String = "CustomNotificationFeedbackTrigger"
  private const val FEEDBACK_NOTIFICATION_CHANNEL_ID = "CustomNotificationFeedbackTrigger"
  private const val FEEDBACK_NOTIFICATION_ID = 1

  var activityToScreenshot: Activity? = null

  /**
   * Show an ongoing notification that the user can tap to take a screenshot of the current activity
   * and send feedback to the developer.
   *
   * The passed in activity must call [cancelNotification] in its [Activity.onDestroy].
   *
   * @param activity the current activity, which will be captured by the screenshot
   */
  fun showNotification(activity: Activity) {
    if (ContextCompat.checkSelfPermission(activity, POST_NOTIFICATIONS) == PERMISSION_DENIED) {
      Log.w(TAG, "Not showing notification because permission has not been granted.")
      return
    }

    // Create the NotificationChannel, but only on API 26+ because
    // the NotificationChannel class is new and not in the support library
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel =
        NotificationChannel(
          FEEDBACK_NOTIFICATION_CHANNEL_ID,
          activity.getString(R.string.feedbackTriggerNotificationChannelName),
          NotificationManager.IMPORTANCE_HIGH
        )
      channel.description =
        activity.getString(R.string.feedbackTriggerNotificationChannelDescription)
      activity.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    val intent = Intent(activity, CustomNotificationTakeScreenshotActivity::class.java)
    intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
    val pendingIntent =
      PendingIntent.getActivity(
        activity,
        /* requestCode = */ 0,
        intent,
        PendingIntent.FLAG_IMMUTABLE
      )
    val builder =
      NotificationCompat.Builder(activity, FEEDBACK_NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(activity.getText(R.string.feedbackTriggerNotificationTitle))
        .setContentText(activity.getText(R.string.feedbackTriggerNotificationText))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent)
        .setOngoing(true)
    val notificationManager = NotificationManagerCompat.from(activity)
    Log.i(TAG, "Showing notification")
    notificationManager.notify(FEEDBACK_NOTIFICATION_ID, builder.build())
    activityToScreenshot = activity
  }

  /**
   * Hide the notification.
   *
   * This must be called from the [Activity.onDestroy] of the activity showing the notification.
   */
  fun cancelNotification() {
    activityToScreenshot?.let {
      Log.i(TAG, "Cancelling notification")
      NotificationManagerCompat.from(it).cancel(FEEDBACK_NOTIFICATION_ID)
    }
  }
}

class CustomNotificationTakeScreenshotActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val activity = CustomNotificationFeedbackTrigger.activityToScreenshot
    if (activity == null) {
      Log.e(TAG, "Can't take screenshot because activity is unknown")
      return
    }
    takeScreenshot(activity)
  }

  override fun onResume() {
    super.onResume()
    val screenshotUri = Uri.fromFile(getFileStreamPath(SCREENSHOT_FILE_NAME))
    Firebase.appDistribution.startFeedback(R.string.feedbackAdditionalFormText, screenshotUri)
    finish()
  }

  private fun takeScreenshot(activity: Activity) {
    val view = activity.window.decorView.rootView
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.RGB_565)
    val canvas = Canvas(bitmap)
    view.draw(canvas)
    try {
      activity.openFileOutput(SCREENSHOT_FILE_NAME, Context.MODE_PRIVATE).use { outputStream ->
        bitmap.compress(Bitmap.CompressFormat.PNG, /* quality = */ 100, outputStream)
      }
      Log.i(TAG, "Wrote screenshot to $SCREENSHOT_FILE_NAME")
    } catch (e: IOException) {
      Log.e(TAG, "Can't write $SCREENSHOT_FILE_NAME", e)
    }
  }

  companion object {
    private const val TAG: String = "TakeScreenshotAndTriggerFeedbackActivity"
    private const val SCREENSHOT_FILE_NAME =
      "com.googletest.firebase.appdistribution.testapp.screenshot.png"
  }
}
