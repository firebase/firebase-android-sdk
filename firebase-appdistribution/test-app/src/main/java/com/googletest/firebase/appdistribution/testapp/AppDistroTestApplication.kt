package com.googletest.firebase.appdistribution.testapp

import android.app.Application

class AppDistroTestApplication : Application() {
  override fun onCreate() {
    super.onCreate()

    // The shake detection feedback trigger can optionally be enabled application-wide here
    //        ShakeDetectionFeedbackTrigger.enable(this)
  }
}
