package com.googletest.firebase.appdistribution.testapp

import android.app.Application

class AppDistroTestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ShakeForFeedback.enable(this)
    }
}
