package com.google.firebase.perf.ttid

import android.app.Activity
import android.os.SystemClock
import android.os.Handler
import androidx.annotation.RequiresApi
import com.google.firebase.perf.ttid.NextDrawListener.Companion.onNextDraw
import com.google.firebase.perf.ttid.WindowDelegateCallback.Companion.onDecorViewReady

class TTIDMeasure {
    interface FirstDrawCallback {
        fun callback(): Unit
    }

    companion object {
        var firstDraw = false
        val handler = Handler()
        var firstDrawMs: Long = 0

        @JvmStatic
        @RequiresApi(android.os.Build.VERSION_CODES.KITKAT)
        fun measureTTID(activity: Activity, callback: FirstDrawCallback) {
            if (firstDraw) return
            val window = activity.window
            window.onDecorViewReady {
                window.decorView.onNextDraw {
                    if (firstDraw) return@onNextDraw
                    firstDraw = true
                    handler.postAtFrontOfQueue {
                        firstDrawMs = SystemClock.uptimeMillis()
                        callback.callback()
                    }
                }
            }
        }
    }
}