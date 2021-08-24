// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.messaging.ktx

import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.app
import com.google.firebase.ktx.initialize
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.platforminfo.UserAgentPublisher
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

const val APP_ID = "APP_ID"
const val API_KEY = "API_KEY"

abstract class BaseTestCase {
    @Before
    fun setUp() {
        Firebase.initialize(
                RuntimeEnvironment.application,
                FirebaseOptions.Builder()
                        .setApplicationId(APP_ID)
                        .setApiKey(API_KEY)
                        .setProjectId("123")
                        .build()
        )
    }

    @After
    fun cleanUp() {
        FirebaseApp.clearInstancesForTest()
    }
}

@RunWith(RobolectricTestRunner::class)
class MessagingTests : BaseTestCase() {

    @Test
    fun `messaging should delegate to FirebaseMessaging#getInstance()`() {
        assertThat(Firebase.messaging).isSameInstanceAs(FirebaseMessaging.getInstance())
    }

    @Test
    fun `remoteMessage() should produce correct RemoteMessages`() {
        val recipient = "recipient"
        val expectedCollapseKey = "collapse"
        val msgId = "id"
        val msgType = "type"
        val timeToLive = 100
        val remoteMessage = remoteMessage(recipient) {
            collapseKey = expectedCollapseKey
            messageId = msgId
            messageType = msgType
            ttl = timeToLive
            addData("hello", "world")
        }
        assertThat(remoteMessage.to).isEqualTo(recipient)
        assertThat(remoteMessage.collapseKey).isEqualTo(expectedCollapseKey)
        assertThat(remoteMessage.messageId).isEqualTo(msgId)
        assertThat(remoteMessage.messageType).isEqualTo(msgType)
        assertThat(remoteMessage.data).isEqualTo(mapOf("hello" to "world"))
    }
}

@RunWith(RobolectricTestRunner::class)
class LibraryVersionTest : BaseTestCase() {
    @Test
    fun `library version should be registered with runtime`() {
        val publisher = Firebase.app.get(UserAgentPublisher::class.java)
        assertThat(publisher.userAgent).contains(LIBRARY_NAME)
    }
}
