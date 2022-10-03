package com.googletest.firebase.appdistribution.testapp

import android.app.Application

class AppDistroTestApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Perform any required trigger initialization here
        ScreenshotDetectionFeedbackTrigger.initialize(this, R.string.terms_and_conditions);
        NotificationFeedbackTrigger.initialize(this);

        // Default feedback triggers can optionally be enabled application-wide here
//        ShakeForFeedback.enable(this)
//        ScreenshotDetectionFeedbackTrigger.enable()
//        NotificationFeedbackTrigger.enable()
    }
}
