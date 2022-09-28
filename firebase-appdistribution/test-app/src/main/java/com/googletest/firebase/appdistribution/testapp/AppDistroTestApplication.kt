package com.googletest.firebase.appdistribution.testapp

import android.app.Application

class AppDistroTestApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Default feedback triggers can also be initialized here
//        ShakeForFeedback.enable(this)
    }
}
