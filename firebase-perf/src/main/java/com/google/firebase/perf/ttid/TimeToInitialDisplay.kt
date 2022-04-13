package com.google.firebase.perf.ttid

import android.view.Window
import com.google.firebase.perf.ttid.NextDrawListener.Companion.onNextDraw

class TimeToInitialDisplay {
    companion object {
        var firstDraw = false
        @JvmStatic
        fun registerTTID(window: Window) {
            if(firstDraw) return
            window.decorView.onNextDraw {

            }
        }
    }
}