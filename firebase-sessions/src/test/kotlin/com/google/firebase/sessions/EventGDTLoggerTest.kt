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

package com.google.firebase.sessions

import com.google.android.datatransport.Encoding
import com.google.android.datatransport.Event
import com.google.android.datatransport.TransportFactory
import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.sessions.settings.SessionsSettings
import com.google.firebase.sessions.testing.FakeFirebaseApp
import com.google.firebase.sessions.testing.FakeProvider
import com.google.firebase.sessions.testing.FakeSettingsProvider
import com.google.firebase.sessions.testing.FakeTransportFactory
import com.google.firebase.sessions.testing.TestSessionEventData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EventGDTLoggerTest {

  @Test
  fun event_logsToGoogleDataTransport() = runTest {
    val fakeFirebaseApp = FakeFirebaseApp()
    val sessionEvent =
      SessionEvents.buildSession(
        fakeFirebaseApp.firebaseApp,
        TestSessionEventData.TEST_SESSION_DETAILS,
        SessionsSettings(
          localOverrideSettings = FakeSettingsProvider(),
          remoteSettings = FakeSettingsProvider(),
        ),
      )
    val fakeTransportFactory = FakeTransportFactory()
    val fakeTransportFactoryProvider = FakeProvider(fakeTransportFactory as TransportFactory)
    val eventGDTLogger = EventGDTLogger(transportFactoryProvider = fakeTransportFactoryProvider)

    eventGDTLogger.log(sessionEvent = sessionEvent)

    assertThat(fakeTransportFactory.name).isEqualTo("FIREBASE_APPQUALITY_SESSION")
    assertThat(fakeTransportFactory.payloadEncoding).isEqualTo(Encoding.of("json"))
    assertThat(fakeTransportFactory.fakeTransport!!.sentEvent)
      .isEqualTo(Event.ofData(TestSessionEventData.TEST_SESSION_EVENT))
  }

  @After
  fun cleanUp() {
    FirebaseApp.clearInstancesForTest()
  }
}
