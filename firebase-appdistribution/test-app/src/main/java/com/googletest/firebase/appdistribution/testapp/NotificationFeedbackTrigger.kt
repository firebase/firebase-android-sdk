package com.googletest.firebase.appdistribution.testapp

import android.Manifest.permission.POST_NOTIFICATIONS
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.appdistribution.ktx.appDistribution
import com.google.firebase.ktx.Firebase
import com.googletest.firebase.appdistribution.testapp.NotificationFeedbackTrigger.SCREENSHOT_FILE_NAME
import com.googletest.firebase.appdistribution.testapp.NotificationFeedbackTrigger.takeScreenshot
import java.io.IOException

@SuppressLint("StaticFieldLeak") // Reference to Activity is set to null in onActivityDestroyed
object NotificationFeedbackTrigger : Application.ActivityLifecycleCallbacks {
  private const val TAG: String = "NotificationFeedbackTrigger"
  private const val FEEBACK_NOTIFICATION_CHANNEL_ID = "InAppFeedbackNotification"
  private const val FEEDBACK_NOTIFICATION_ID = 1
  const val SCREENSHOT_FILE_NAME = "com.googletest.firebase.appdistribution.testapp.screenshot.png"

  private var isEnabled = false
  private var hasRequestedPermission = false
  private var currentActivity: Activity? = null

  fun initialize(application: Application) {
    // Create the NotificationChannel, but only on API 26+ because
    // the NotificationChannel class is new and not in the support library
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel =
        NotificationChannel(
          FEEBACK_NOTIFICATION_CHANNEL_ID,
          application.getString(R.string.feedback_notification_channel_name),
          NotificationManager.IMPORTANCE_LOW
        )
      channel.description =
        application.getString(R.string.feedback_notification_channel_description)
      application
        .getSystemService(NotificationManager::class.java)
        .createNotificationChannel(channel)
    }
    application.registerActivityLifecycleCallbacks(this)
  }

  fun <T> registerPermissionLauncher(activity: T): ActivityResultLauncher<String>? where
  T : Activity,
  T : ActivityResultCaller {
    if (hasRequestedPermission) {
      Log.i(TAG, "Already request permission; Not trying again.")
      return null
    }

    return activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
      isGranted: Boolean ->
      if (!isEnabled) {
        Log.w(TAG, "Trigger disabled after permission check. Abandoning notification.")
      } else if (isGranted) {
        showNotification(activity)
      } else {
        Log.i(TAG, "Permission not granted")
        // TODO: Ideally we would show a message indicating the impact of not
        //   enabling the permission, but there's no way to know if they've
        //   permanently denied the permission, and we don't want to show them a
        //   message after each time we try to post a notification.
      }
    }
  }

  fun enable(activity: Activity? = null, launcher: ActivityResultLauncher<String>? = null) {
    currentActivity = activity
    isEnabled = true
    if (activity != null) {
      if (hasPermission(activity)) {
        showNotification(activity)
      } else {
        if (launcher != null) {
          requestPermission(activity, launcher)
        } else {
          Log.i(TAG, "Not requesting permission, because of no launcher was provided.")
        }
      }
    }
  }

  fun disable() {
    isEnabled = false
    val activity = currentActivity
    currentActivity = null
    if (activity != null) {
      cancelNotification(activity)
    }
  }

  private fun showNotification(activity: Activity) {
    val intent = Intent(activity, TakeScreenshotAndTriggerFeedbackActivity::class.java)
    intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
    val pendingIntent =
      PendingIntent.getActivity(
        activity,
        /* requestCode = */ 0,
        intent,
        PendingIntent.FLAG_IMMUTABLE
      )
    val builder =
      NotificationCompat.Builder(activity, FEEBACK_NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(activity.getText(R.string.feedback_notification_title))
        .setContentText(activity.getText(R.string.feedback_notification_text))
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(pendingIntent)
    val notificationManager = NotificationManagerCompat.from(activity)
    Log.i(TAG, "Showing notification")
    notificationManager.notify(FEEDBACK_NOTIFICATION_ID, builder.build())
  }

  private fun hasPermission(activity: Activity) =
    ContextCompat.checkSelfPermission(activity, POST_NOTIFICATIONS) == PERMISSION_GRANTED

  private fun requestPermission(activity: Activity, launcher: ActivityResultLauncher<String>) {
    if (hasRequestedPermission) {
      Log.i(TAG, "Already request permission; Not trying again.")
      return
    }
    if (activity.shouldShowRequestPermissionRationale(POST_NOTIFICATIONS)) {
      Log.i(TAG, "Showing customer rationale for requesting permission.")
      AlertDialog.Builder(activity)
        .setMessage(
          "Using a notification to initiate feedback to the developer. " +
            "To enable this feature, allow the app to post notifications."
        )
        .setPositiveButton("OK") { _, _ ->
          Log.i(TAG, "Launching request for permission.")
          launcher.launch(POST_NOTIFICATIONS)
        }
        .show()
    } else {
      Log.i(TAG, "Launching request for permission without rationale.")
      launcher.launch(POST_NOTIFICATIONS)
    }
    hasRequestedPermission = true
  }

  private fun cancelNotification(context: Context) {
    val notificationManager = NotificationManagerCompat.from(context)
    Log.i(TAG, "Cancelling notification")
    notificationManager.cancel(FEEDBACK_NOTIFICATION_ID)
  }

  override fun onActivityResumed(activity: Activity) {
    if (isEnabled) {
      if (activity !is TakeScreenshotAndTriggerFeedbackActivity) {
        Log.d(TAG, "setting current activity")
        currentActivity = activity
        showNotification(activity)
      }
    }
  }

  override fun onActivityPaused(activity: Activity) {
    cancelNotification(activity)
  }

  override fun onActivityDestroyed(activity: Activity) {
    if (activity == currentActivity) {
      Log.d(TAG, "clearing current activity")
      currentActivity = null
    }
  }

  // Other lifecycle methods
  override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
  override fun onActivityStarted(activity: Activity) {}
  override fun onActivityStopped(activity: Activity) {}
  override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

  fun takeScreenshot() {
    val activity = currentActivity
    if (activity != null) {
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
    } else {
      Log.e(TAG, "Can't take screenshot because current activity is unknown")
      return
    }
  }
}

class TakeScreenshotAndTriggerFeedbackActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    takeScreenshot() // at this point currentActivity still points to the previous activity
  }

  override fun onResume() {
    super.onResume()
    val screenshotUri = Uri.fromFile(getFileStreamPath(SCREENSHOT_FILE_NAME))
    Firebase.appDistribution.startFeedback(R.string.terms_and_conditions, screenshotUri)
  }
}
