package com.googletest.firebase.appdistribution.testapp

import android.app.Activity
import android.app.Application
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import com.google.firebase.appdistribution.ktx.appDistribution
import com.google.firebase.ktx.Firebase
import com.squareup.seismic.ShakeDetector

/**
 * Listens to the device accelerometer, and starts App Distribution in-app feedback when the user
 * shakes their device.
 */
object ShakeDetectionFeedbackTrigger : ShakeDetector.Listener, Application.ActivityLifecycleCallbacks {
  private const val TAG: String = "ShakeDetectionFeedbackTrigger"

  private val shakeDetector =
    ShakeDetector(this).also { it.setSensitivity(ShakeDetector.SENSITIVITY_LIGHT) }
  private var isEnabled = false

  /** Start listening for device shakes. */
  fun enable(application: Application, currentActivity: Activity? = null) {
    synchronized(this) {
      if (!isEnabled) {
        application.registerActivityLifecycleCallbacks(this)
        Log.i(TAG, "Shake detector registered")
        if (currentActivity != null) {
          listenForShakes(currentActivity)
        }
        isEnabled = true
      }
    }
  }

  /** Stop listening for device shakes. */
  fun disable(application: Application) {
    synchronized(this) {
      if (isEnabled) {
        stopListeningForShakes()
        application.unregisterActivityLifecycleCallbacks(this)
        Log.i(TAG, "Shake detector unregistered")
        isEnabled = false
      }
    }
  }

  private fun listenForShakes(activity: Activity) {
    val sensorManager = activity.getSystemService(Activity.SENSOR_SERVICE) as SensorManager
    shakeDetector.start(sensorManager, SensorManager.SENSOR_DELAY_NORMAL)
  }

  private fun stopListeningForShakes() {
    shakeDetector.stop()
  }

  override fun hearShake() {
    Log.i(TAG, "Shake detected")
    Firebase.appDistribution.startFeedback(R.string.feedbackInfoText)
  }

  override fun onActivityResumed(activity: Activity) {
    Log.i(TAG, "Shake detection started")
    listenForShakes(activity)
  }

  override fun onActivityPaused(activity: Activity) {
    Log.i(TAG, "Shake detection stopped")
    stopListeningForShakes()
  }

  // Other lifecycle methods
  override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
  override fun onActivityStarted(activity: Activity) {}
  override fun onActivityStopped(activity: Activity) {}
  override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
  override fun onActivityDestroyed(activity: Activity) {}
}
