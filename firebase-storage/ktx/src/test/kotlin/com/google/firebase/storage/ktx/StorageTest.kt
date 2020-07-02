// Copyright 2019 Google LLC
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

package com.google.firebase.storage.ktx

import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.app
import com.google.firebase.ktx.initialize
import com.google.firebase.platforminfo.UserAgentPublisher
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

const val APP_ID = "APP_ID"
const val API_KEY = "API_KEY"

const val EXISTING_APP = "existing"

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

        Firebase.initialize(
                RuntimeEnvironment.application,
                FirebaseOptions.Builder()
                        .setApplicationId(APP_ID)
                        .setApiKey(API_KEY)
                        .setProjectId("123")
                        .build(),
                EXISTING_APP
        )
    }

    @After
    fun cleanUp() {
        FirebaseApp.clearInstancesForTest()
    }
}

@RunWith(RobolectricTestRunner::class)
class StorageTests : BaseTestCase() {

    @Test
    fun `storage should delegate to FirebaseStorage#getInstance()`() {
        assertThat(Firebase.storage).isSameInstanceAs(FirebaseStorage.getInstance())
    }

    @Test
    fun `FirebaseApp#storage should delegate to FirebaseStorage#getInstance(FirebaseApp)`() {
        val app = Firebase.app(EXISTING_APP)
        assertThat(Firebase.storage(app)).isSameInstanceAs(FirebaseStorage.getInstance(app))
    }

    @Test
    fun `Firebase#storage should delegate to FirebaseStorage#getInstance(url)`() {
        val url = "gs://valid.url"
        assertThat(Firebase.storage(url)).isSameInstanceAs(FirebaseStorage.getInstance(url))
    }

    @Test
    fun `Firebase#storage should delegate to FirebaseStorage#getInstance(FirebaseApp, url)`() {
        val app = Firebase.app(EXISTING_APP)
        val url = "gs://valid.url"
        assertThat(Firebase.storage(app, url)).isSameInstanceAs(FirebaseStorage.getInstance(app, url))
    }

    @Test
    fun `storageMetadata type-safe builder extension works`() {
        val storage = Firebase.storage
        val metadata: StorageMetadata = storageMetadata {
            contentLanguage = "en_us"
            contentType = "text/html"
            contentEncoding = "utf-8"
            cacheControl = "no-cache"
            contentDisposition = "attachment"
        }

        assertThat(metadata.getContentType()).isEqualTo("text/html")
        assertThat(metadata.getCacheControl()).isEqualTo("no-cache")
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
