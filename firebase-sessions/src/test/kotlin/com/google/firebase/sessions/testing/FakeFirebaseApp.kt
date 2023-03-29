package com.google.firebase.sessions.testing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.content.pm.PackageInfoBuilder
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.initialize
import org.robolectric.Shadows

class FakeFirebaseApp {
  companion object {
    internal val MOCK_PROJECT_ID = "project"
    internal val MOCK_APP_ID = "1:12345:android:app"
    internal val MOCK_API_KEY = "RANDOM_APIKEY_FOR_TESTING"
    internal val MOCK_APP_VERSION = "1.0.0"

    fun fakeFirebaseApp():FirebaseApp {
      val shadowPackageManager =
        Shadows.shadowOf(ApplicationProvider.getApplicationContext<Context>().packageManager)
      val packageInfo = PackageInfoBuilder.newBuilder().setPackageName(ApplicationProvider.getApplicationContext<Context>().packageName).build()
      packageInfo.versionName = MOCK_APP_VERSION
      shadowPackageManager.installPackage(packageInfo)

      val firebaseApp = Firebase.initialize(
        ApplicationProvider.getApplicationContext(),
        FirebaseOptions.Builder()
          .setApplicationId(MOCK_APP_ID)
          .setApiKey(MOCK_API_KEY)
          .setProjectId(MOCK_PROJECT_ID)
          .build()
      )
      return firebaseApp
    }
  }
}