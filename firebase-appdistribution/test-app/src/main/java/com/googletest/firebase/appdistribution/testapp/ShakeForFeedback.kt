package com.googletest.firebase.appdistribution.testapp

import android.app.Activity
import android.app.Application
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import com.google.firebase.appdistribution.ktx.appDistribution
import com.google.firebase.ktx.Firebase
import com.squareup.seismic.ShakeDetector

class ShakeForFeedback private constructor() : ShakeDetector.Listener,
    Application.ActivityLifecycleCallbacks {
    private val shakeDetector = ShakeDetector(this)

    override fun hearShake() {
        Log.i(TAG, "Shake detected")
        Firebase.appDistribution.startFeedback(R.string.terms_and_conditions)
    }

    override fun onActivityResumed(activity: Activity) {
        Log.i(TAG, "Shake detection started")
        val sensorManager = activity.getSystemService(Activity.SENSOR_SERVICE) as SensorManager
        shakeDetector.start(sensorManager, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onActivityPaused(activity: Activity) {
        Log.i(TAG, "Shake detection stopped")
        shakeDetector.stop()
    }

    // Other lifecycle methods
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}

    companion object {
        const val TAG: String = "ShakeForFeedback"

        fun enable(application: Application) {
            application.registerActivityLifecycleCallbacks(ShakeForFeedback())
            Log.i(TAG, "Shake detector registered")
        }
    }
}
