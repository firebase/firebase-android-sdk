/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.sessions.testing

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.content.pm.PackageInfoBuilder
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.initialize
import org.robolectric.Shadows

internal class FakeFirebaseApp(
  metadata: Bundle? = null,
  importance: Int = RunningAppProcessInfo.IMPORTANCE_FOREGROUND,
) {
  val firebaseApp: FirebaseApp

  init {
    val context: Context = ApplicationProvider.getApplicationContext()

    val packageInfo = PackageInfoBuilder.newBuilder().setPackageName(context.packageName).build()
    packageInfo.versionName = MOCK_APP_VERSION
    metadata?.let { packageInfo.applicationInfo.metaData = it }
    Shadows.shadowOf(context.packageManager).installPackage(packageInfo)

    val runningAppProcessInfo = RunningAppProcessInfo()
    runningAppProcessInfo.importance = importance
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    Shadows.shadowOf(activityManager).setProcesses(listOf(runningAppProcessInfo))

    firebaseApp =
      Firebase.initialize(
        context,
        FirebaseOptions.Builder()
          .setApplicationId(MOCK_APP_ID)
          .setApiKey(MOCK_API_KEY)
          .setProjectId(MOCK_PROJECT_ID)
          .build()
      )
  }

  companion object {
    internal const val MOCK_PROJECT_ID = "project"
    internal const val MOCK_APP_ID = "1:12345:android:app"
    internal const val MOCK_API_KEY = "RANDOM_APIKEY_FOR_TESTING"
    internal const val MOCK_APP_VERSION = "1.0.0"
    internal const val MOCK_APP_BUILD_VERSION = "0"
  }
}
