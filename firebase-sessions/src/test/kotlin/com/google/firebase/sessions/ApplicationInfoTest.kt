package com.google.firebase.sessions

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.initialize
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApplicationInfoTest {
  private val MOCK_PROJECT_ID = "project"
  private val MOCK_APP_ID = "1:android:project:app"
  private val MOCK_API_KEY = "RANDOM_APIKEY_FOR_TESTING"

  @Test
  fun applicationInfo_populatesInfoCorrectly() {
    val firebaseApp = Firebase.initialize(
      ApplicationProvider.getApplicationContext(),
      FirebaseOptions.Builder()
        .setApplicationId(MOCK_APP_ID)
        .setApiKey(MOCK_API_KEY)
        .setProjectId(MOCK_PROJECT_ID)
        .build()
    )

    val applicationInfo = getApplicationInfo(firebaseApp)
    Truth.assertThat(applicationInfo)
      .isEqualTo(
        ApplicationInfo(appId = MOCK_APP_ID,
                        deviceModel = "",
                        sessionSdkVersion = "",
                        logEnvironment = LogEnvironment.LOG_ENVIRONMENT_PROD,
                        AndroidApplicationInfo(packageName = "",
                                               versionName = "")
        )
      )
  }
}