package com.google.firebase.crashlytics

import android.annotation.SuppressLint

object FlutterFirebaseCrashlyticsInternal {
  @SuppressLint("VisibleForTests")
  fun recordFatalException(throwable: Throwable) {
    FirebaseCrashlytics.getInstance().core.logFatalException(throwable)
    FirebaseCrashlytics.getInstance().setUserId("900")
  }
}
