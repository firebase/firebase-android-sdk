package com.google.firebase.sessions

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

internal class SessionLifecycleService : Service() {

    override fun onBind(p0: Intent?): IBinder? {
        Log.d(TAG, "Service bound no-op")
        return null
    }

    internal companion object {
        const val TAG = "SessionLifecycleService"

    }
}