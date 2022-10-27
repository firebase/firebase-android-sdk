package com.googletest.firebase.appdistribution.testapp

import android.Manifest.permission.POST_NOTIFICATIONS
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.appdistribution.ktx.appDistribution
import com.google.firebase.ktx.Firebase
import java.io.IOException

@SuppressLint("StaticFieldLeak") // Reference to Activity is set to null in onActivityDestroyed
object CustomNotificationFeedbackTrigger : Application.ActivityLifecycleCallbacks {
  private const val TAG: String = "NotificationFeedbackTrigger"
  private const val FEEDBACK_NOTIFICATION_CHANNEL_ID = "InAppFeedbackNotification"
  private const val FEEDBACK_NOTIFICATION_ID = 1

  private var isEnabled = false
  private var hasRequestedPermission = false

  internal var activityToScreenshot: Activity? = null

  /**
   * Initialize the notification trigger for this application.
   *
   * This should be called during [Application.onCreate].
   * [enable] should then be called when you want to actually show the notification.
   *
   * @param application the [Application] object
   */
  fun initialize(application: Application) {
    // Create the NotificationChannel, but only on API 26+ because
    // the NotificationChannel class is new and not in the support library
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel =
        NotificationChannel(
          FEEDBACK_NOTIFICATION_CHANNEL_ID,
          application.getString(R.string.feedbackTriggerNotificationChannelName),
          NotificationManager.IMPORTANCE_HIGH
        )
      channel.description =
        application.getString(R.string.feedbackTriggerNotificationChannelDescription)
      application
        .getSystemService(NotificationManager::class.java)
        .createNotificationChannel(channel)
    }
    application.registerActivityLifecycleCallbacks(this)
  }

  /**
   * Requests permission to show notifications for this application.
   *
   * This must be called during [Activity.onCreate].
   * [enable] should then be called when you want to actually show the notification.
   *
   * @param activity the [Activity] object
   */
  @RequiresApi(Build.VERSION_CODES.TIRAMISU)
  fun <T> requestPermission(activity: T) where T : Activity, T : ActivityResultCaller {
    if (ContextCompat.checkSelfPermission(activity, POST_NOTIFICATIONS) == PERMISSION_GRANTED) {
      Log.i(TAG, "Already has permission.")
      return
    }

    if (hasRequestedPermission) {
      Log.i(TAG, "Already request permission; Not trying again.")
      return
    }

    val launcher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
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

  /**
   * Show notifications.
   *
   * This could be called during [Activity.onCreate].
   *
   * @param activity the [Activity] object
   */
  fun enable(activity: Activity) {
    activityToScreenshot = activity
    isEnabled = true
    showNotification(activity)
  }

  /** Hide notifications. */
  fun disable() {
    val activity = activityToScreenshot
    if (activity != null) {
      cancelNotification(activity)
    }
    isEnabled = false
    activityToScreenshot = null
  }

  private fun showNotification(context: Context) {
    if (ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS) == PERMISSION_DENIED) {
      Log.w(TAG, "Not showing notification because permission has not been granted.")
      return
    }

    val intent = Intent(context, TakeScreenshotAndTriggerFeedbackActivity::class.java)
    intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
    val pendingIntent =
      PendingIntent.getActivity(
        context,
        /* requestCode = */ 0,
        intent,
        PendingIntent.FLAG_IMMUTABLE
      )
    val builder =
      NotificationCompat.Builder(context, FEEDBACK_NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(context.getText(R.string.feedbackTriggerNotificationTitle))
        .setContentText(context.getText(R.string.feedbackTriggerNotificationText))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent)
    val notificationManager = NotificationManagerCompat.from(context)
    Log.i(TAG, "Showing notification")
    notificationManager.notify(FEEDBACK_NOTIFICATION_ID, builder.build())
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
        activityToScreenshot = activity
      }
    }
  }

  override fun onActivityDestroyed(activity: Activity) {
    if (activity == activityToScreenshot) {
      Log.d(TAG, "clearing current activity")
      activityToScreenshot = null
    }
  }

  // Other lifecycle methods
  override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
  override fun onActivityStarted(activity: Activity) {}
  override fun onActivityPaused(activity: Activity) {}
  override fun onActivityStopped(activity: Activity) {}
  override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
}

class TakeScreenshotAndTriggerFeedbackActivity : Activity() {
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
    Firebase.appDistribution.startFeedback(R.string.feedbackInfoText, screenshotUri)
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
