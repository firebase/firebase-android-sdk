package com.google.firebase

import android.annotation.SuppressLint

object FirebaseAppTestUtils {

  @SuppressLint("RestrictedApi", "VisibleForTests")
  fun initializeAllComponents(app: FirebaseApp) {
    app.initializeAllComponents()
  }

  @SuppressLint("RestrictedApi", "VisibleForTests")
  fun clearInstancesForTest() {
    FirebaseApp.clearInstancesForTest()
  }
}
