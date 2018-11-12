// Copyright 2018 Google LLC
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

package com.google.firebase

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

const val APP_ID = "APP_ID"
const val API_KEY = "API_KEY"
const val DB_URL = "DB_URL"
const val TRACKING_ID = "TRACKING_ID"
const val SENDER_ID = "SENDER_ID"
const val STORAGE_BUCKET = "STORAGE_BUCKET"
const val PROJECT_ID = "PROJECT_ID"

const val EXISTING_APP = "existing"
const val NON_EXISTENT_APP = "nonexistent"
const val ANOTHER_APP = "another"

@RunWith(RobolectricTestRunner::class)
class FirebaseOptionsTest {
    @Test
    fun `options builder should only set appId and apiKey`() {
        val options = Firebase.options(applicationId = APP_ID, apiKey = API_KEY)
        assertThat(options).isEqualTo(FirebaseOptions.Builder().setApplicationId(APP_ID).setApiKey(API_KEY).build())
    }

    @Test
    fun `options builder should propagate all setters to FirebaseOptions`() {
        val options = Firebase.options(applicationId = APP_ID, apiKey = API_KEY) {
            databaseUrl = DB_URL
            gaTrackingId = TRACKING_ID
            gcmSenderId = SENDER_ID
            storageBucket = STORAGE_BUCKET
            projectId = PROJECT_ID

        }
        assertThat(options).isEqualTo(
            FirebaseOptions.Builder()
                .setApplicationId(APP_ID)
                .setApiKey(API_KEY)
                .setDatabaseUrl(DB_URL)
                .setGaTrackingId(TRACKING_ID)
                .setGcmSenderId(SENDER_ID)
                .setStorageBucket(STORAGE_BUCKET)
                .setProjectId(PROJECT_ID)
                .build()
        )
    }
}

@RunWith(RobolectricTestRunner::class)
class FirebaseAppTest {

    @Before
    fun setup() {
        FirebaseApp.initializeApp(
            RuntimeEnvironment.application,
            Firebase.options(applicationId = APP_ID, apiKey = API_KEY)
        )

        FirebaseApp.initializeApp(
            RuntimeEnvironment.application,
            Firebase.options(applicationId = APP_ID, apiKey = API_KEY),
            EXISTING_APP
        )
    }

    @After
    fun cleanup() {
        FirebaseApp.clearInstancesForTest()
    }

    @Test
    fun `app should delegate to FirebaseApp#getInstance()`() {
        assertThat(Firebase.app).isSameAs(FirebaseApp.getInstance())
    }

    @Test
    fun `app should throw for nonexistent apps`() {
        try {
            Firebase.app(NON_EXISTENT_APP)
            fail("Expected exception not thrown")
        } catch (ex: IllegalStateException) {
        }
    }

    @Test
    fun `app should delegate to Firebase#getInstance(String)`() {
        assertThat(Firebase.app(EXISTING_APP)).isSameAs(FirebaseApp.getInstance(EXISTING_APP))
    }

    @Test
    fun `initializeApp should delegate to FirebaseApp#initializeApp`() {
        val opts = Firebase.options(applicationId = APP_ID, apiKey = API_KEY) {
            databaseUrl = DB_URL
        }
        val app = Firebase.initializeApp(
            RuntimeEnvironment.application,
            ANOTHER_APP,
            opts
        )
        assertThat(app).isSameAs(FirebaseApp.getInstance(ANOTHER_APP))
        assertThat(app.applicationContext).isSameAs(RuntimeEnvironment.application)
        assertThat(app.options).isEqualTo(opts)
    }
}