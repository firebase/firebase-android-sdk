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

package com.google.firebase.firestore

import com.google.common.truth.Truth.assertThat
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.app
import com.google.firebase.options
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

const val APP_ID = "APP_ID"
const val API_KEY = "API_KEY"

const val EXISTING_APP = "existing"

@RunWith(RobolectricTestRunner::class)
class FirestoreTests {
    @Before
    fun setup() {
        FirebaseApp.initializeApp(
            RuntimeEnvironment.application,
            Firebase.options(applicationId = APP_ID, apiKey = API_KEY) {
                projectId = "123"
            }
        )

        FirebaseApp.initializeApp(
            RuntimeEnvironment.application,
            Firebase.options(applicationId = APP_ID, apiKey = API_KEY) {
                projectId = "123"
            },
            EXISTING_APP
        )
    }

    @After
    fun cleanup() {
        FirebaseApp.clearInstancesForTest()
    }

    @Test
    fun `firestore should delegate to FirebaseFirestore#getInstance()`() {
        assertThat(Firebase.firestore).isSameAs(FirebaseFirestore.getInstance())
    }

    @Test
    fun `FirebaseApp#firestore should delegate to FirebaseFirestore#getInstance(FirebaseApp)`() {
        assertThat(Firebase.app.firestore).isSameAs(FirebaseFirestore.getInstance(Firebase.app))
    }
}