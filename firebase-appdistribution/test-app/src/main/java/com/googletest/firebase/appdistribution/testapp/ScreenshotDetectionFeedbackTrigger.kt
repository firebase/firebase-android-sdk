package com.googletest.firebase.appdistribution.testapp

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.READ_MEDIA_IMAGES
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.firebase.appdistribution.FirebaseAppDistribution
import java.util.*

class ScreenshotDetectionFeedbackTrigger
private constructor(private val infoTextResourceId: Int, handler: Handler) :
  ContentObserver(handler), Application.ActivityLifecycleCallbacks {

  private val seenImages = HashSet<Uri>()
  private var requestPermissionLauncher: ActivityResultLauncher<String>? = null
  private var currentActivity: Activity? = null
  private var currentUri: Uri? = null
  private var isEnabled = false
  private var hasRequestedPermission = false

  override fun onChange(selfChange: Boolean, uri: Uri?) {
    if (uri == null || !isExternalContent(uri) || seenImages.contains(uri)) {
      return
    }
    currentActivity?.let { activity ->
      if (isPermissionGranted(activity)) {
        maybeStartFeedbackForScreenshot(activity, uri)
      } else if (hasRequestedPermission) {
        info("We've already request permission. Not requesting again for the life of the activity.")
      } else {
        // Set an in memory flag so we don't ask them again right away
        hasRequestedPermission = true
        requestReadPermission(activity, uri)
      }
    }
  }

  override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
    if (activity is ActivityResultCaller) {
      requestPermissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
          if (!isEnabled) {
            warn("Trigger disabled after permission check. Abandoning screenshot detection.")
          } else if (isGranted) {
            maybeStartFeedbackForScreenshot(activity, currentUri!!)
          } else {
            info("Permission not granted")
            // TODO: Ideally we would show a message indicating the impact of not enabling
            // the permission, but there's no way to know if they've permanently denied
            // the permission, and we don't want to show them a message after every
            // screenshot.
          }
        }
    } else {
      warn("Not listening for screenshots because this activity can't register for permission request results: $activity")
    }
  }

  override fun onActivityResumed(activity: Activity) {
    currentActivity = activity
    if (isEnabled) {
      listenForScreenshots()
    }
  }

  override fun onActivityPaused(activity: Activity) {
    if (isEnabled) {
      stopListeningForScreenshots()
    }
    currentActivity = null
  }

  private fun requestReadPermission(activity: Activity, uri: Uri) {
    if (activity.shouldShowRequestPermissionRationale(permissionToRequest)) {
      info("Showing customer rationale for requesting permission.")
      AlertDialog.Builder(activity)
        .setMessage(
          "Taking a screenshot of the app can initiate feedback to the developer. To enable this feature, allow the app access to device storage."
        )
        .setPositiveButton("OK") { _, _ ->
          info("Launching request for permission.")
          currentUri = uri
          requestPermissionLauncher!!.launch(permissionToRequest)
        }
        .show()
    } else {
      info("Launching request for permission without rationale.")
      currentUri = uri
      requestPermissionLauncher!!.launch(permissionToRequest)
    }
  }

  private fun maybeStartFeedbackForScreenshot(activity: Activity, uri: Uri) {
    var cursor: Cursor? = null
    try {
      cursor = activity.contentResolver.query(uri, contentProjection, null, null, null)
      if (cursor != null && cursor.moveToFirst()) {
        val pendingColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_PENDING)
        if (shouldCheckIfPending && cursor.getInt(pendingColumn) == 1) {
          info("Ignoring pending image: $uri")
          return
        }
        seenImages.add(uri)
        val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
        info("Path: $path")
        if (path.lowercase(Locale.getDefault()).contains("screenshot")) {
          FirebaseAppDistribution.getInstance().startFeedback(infoTextResourceId, uri)
        }
      }
    } catch (e: Exception) {
      error("Could not determine if media change was due to taking a screenshot", e)
    } finally {
      cursor?.close()
    }
  }

  private fun listenForScreenshots() {
    currentActivity?.let { activity ->
      activity.contentResolver.registerContentObserver(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        /* notifyForDescendants= */ true,
        this
      )
    }
  }

  private fun stopListeningForScreenshots() {
    currentActivity?.let { activity -> activity.contentResolver.unregisterContentObserver(this) }
  }

  // Other lifecycle methods
  override fun onActivityDestroyed(activity: Activity) {}
  override fun onActivityStarted(activity: Activity) {}
  override fun onActivityStopped(activity: Activity) {}
  override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

  companion object {
    const val TAG = "ScreenshotDetectionFeedbackTrigger"

    private val permissionToRequest =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) READ_MEDIA_IMAGES
      else READ_EXTERNAL_STORAGE
    private val shouldCheckIfPending = Build.VERSION.SDK_INT >= 29
    private val contentProjection =
      if (shouldCheckIfPending)
        arrayOf(MediaStore.Images.Media.DATA, MediaStore.MediaColumns.IS_PENDING)
      else arrayOf(MediaStore.Images.Media.DATA)

    @SuppressLint("StaticFieldLeak") // Reference to Activity is set to null in onActivityPaused
    private var instance: ScreenshotDetectionFeedbackTrigger? = null

    /**
     * Initialize the screenshot detection trigger for this application.
     *
     * This should be called during [Application.onCreate]. [enable] should then be called when you
     * want to actually start detecting screenshots.
     *
     * @param application the [Application] object
     * @param infoTextResourceId resource ID of info text to show to testers before giving feedback
     */
    open fun initialize(application: Application, infoTextResourceId: Int) {
      if (instance == null) {
        val handlerThread = HandlerThread("AppDistroFeedbackTrigger")
        handlerThread.start()
        instance = ScreenshotDetectionFeedbackTrigger(infoTextResourceId, Handler(handlerThread.looper))
        application.registerActivityLifecycleCallbacks(instance)
      }
    }

    /**
     * Start listening for screenshots, and start feedback when a new screenshot is detected.
     *
     * @throws IllegalStateException if [initialize] has not been called yet
     */
    open fun enable() {
      instance?.also {
        it.isEnabled = true
        it.listenForScreenshots()
      } ?: throw IllegalStateException(
        "You must call initialize() in your Application.onCreate() before enabling screenshot detection"
      )
    }

    /**
     * Stop listening for screenshots.
     *
     * @throws IllegalStateException if [initialize] has not been called yet
     */
    open fun disable() {
      instance?.also {
        it.isEnabled = false
        it.stopListeningForScreenshots()
      } ?: throw IllegalStateException(
        "You must call initialize() in your Application.onCreate() before enabling screenshot detection"
      )
    }

    private fun isExternalContent(uri: Uri) =
      uri.toString().matches("${MediaStore.Images.Media.EXTERNAL_CONTENT_URI}/\\d+".toRegex())

    private fun isPermissionGranted(activity: Activity) =
      ContextCompat.checkSelfPermission(activity, permissionToRequest) == PERMISSION_GRANTED

    private fun info(msg: String) = Log.i(TAG, msg)

    private fun warn(msg: String) = Log.w(TAG, msg)

    private fun error(msg: String, e: Throwable) = Log.e(TAG, msg, e)
  }
}
