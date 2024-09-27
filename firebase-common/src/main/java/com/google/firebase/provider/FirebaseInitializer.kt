package com.google.firebase.provider

import android.content.Context
import android.util.Log
import androidx.startup.Initializer
import androidx.work.WorkManagerInitializer
import com.google.firebase.FirebaseApp
import com.google.firebase.StartupTime
import java.util.concurrent.atomic.AtomicBoolean

class FirebaseInitializer : Initializer<FirebaseApp> {

  private val TAG = "FirebaseInitProvider"

  /** @hide */
  private val currentlyInitializing = AtomicBoolean(false)

  companion object {
    private val startupTime: StartupTime = StartupTime.now()

    /** @hide */
    @JvmStatic
    fun getStartupTime(): StartupTime {
      return startupTime
    }
  }

  override fun create(context: Context): FirebaseApp {
    try {
      currentlyInitializing.set(true)
      val app = FirebaseApp.initializeApp(context)
      if (app == null) {
        Log.i(TAG, "FirebaseApp initialization unsuccessful")
        throw IllegalStateException("Firebase appears to be misconfigured")
      } else {
        Log.i(TAG, "FirebaseApp initialization successful")
        return app
      }
    } finally {
      currentlyInitializing.set(false)
    }
  }

  override fun dependencies(): List<Class<out Initializer<*>>> {
    return listOf(WorkManagerInitializer::class.java)
  }
}
