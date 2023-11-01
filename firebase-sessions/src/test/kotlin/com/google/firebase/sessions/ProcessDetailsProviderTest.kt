package com.google.firebase.sessions

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.sessions.testing.FakeFirebaseApp
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProcessDetailsProviderTest {
  private lateinit var firebaseApp: FirebaseApp

  @Before
  fun before() {
    val metadata = Bundle()
    metadata.putDouble("firebase_sessions_sampling_rate", 0.5)
    firebaseApp = FakeFirebaseApp(metadata).firebaseApp
  }

  @After
  fun cleanUp() {
    FirebaseApp.clearInstancesForTest()
  }

  @Test
  fun getCurrentProcessDetails() {
    val processDetails =
      ProcessDetailsProvider.getCurrentProcessDetails(firebaseApp.applicationContext)
    assertThat(processDetails)
      .isEqualTo(ProcessDetails("com.google.firebase.sessions.test", 0, 100, false))
  }

  @Test
  fun getAppProcessDetails() {
    val processDetails = ProcessDetailsProvider.getAppProcessDetails(firebaseApp.applicationContext)
    assertThat(processDetails)
      .isEqualTo(listOf(ProcessDetails("com.google.firebase.sessions.test", 0, 100, false)))
  }
}
