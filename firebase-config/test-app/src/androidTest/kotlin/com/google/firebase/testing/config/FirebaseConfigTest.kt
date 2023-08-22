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

package com.google.firebase.testing.config

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.initialize
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirebaseConfigTest {
  @Before
  fun setUp() {
    Firebase.initialize(ApplicationProvider.getApplicationContext())
  }

  @After
  fun cleanUp() {
    FirebaseApp.clearInstancesForTest()
  }

  @Test
  fun initializeConfig_generatesSessionEvent() {

    // // Force the Firebase Config SDK to initialize.
    // assertThat(FirebaseConfig.instance).isNotNull()

    // // Add a fake dependency and register it, otherwise sessions will never send.
    // val fakeSessionSubscriber = FakeSessionSubscriber()
    // FirebaseConfig.instance.register(fakeSessionSubscriber)

    // // Wait for the session start event to send.
    // Thread.sleep(TIME_TO_LOG_SESSION)

    // // Assert that some session was generated and sent to the subscriber.
    // assertThat(fakeSessionSubscriber.sessionDetails).isNotNull()
  }

  companion object {
    private const val TIME_TO_LOG_SESSION = 60_000L

    // init {
    //   FirebaseConfigDependencies.addDependency(SessionSubscriber.Name.MATT_SAYS_HI)
    // }
  }

  // private class FakeSessionSubscriber(
  //   override val isDataCollectionEnabled: Boolean = true,
  //   override val sessionSubscriberName: SessionSubscriber.Name =
  // SessionSubscriber.Name.MATT_SAYS_HI
  // ) : SessionSubscriber {
  //   var sessionDetails: SessionSubscriber.SessionDetails? = null
  //     private set

  //   override fun onSessionChanged(sessionDetails: SessionSubscriber.SessionDetails) {
  //     this.sessionDetails = sessionDetails
  //   }
  // }
}
