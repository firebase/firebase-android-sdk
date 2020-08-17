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

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.app
import com.google.firebase.ktx.initialize
import com.google.firebase.platforminfo.UserAgentPublisher
import com.google.firebase.storage.FileDownloadTask
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.KtxTestUtil
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.StreamDownloadTask
import com.google.firebase.storage.UploadTask
import java.io.ByteArrayInputStream
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.`when`
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

    @Test
    fun `ListResult destructuring declarations work`() {
        val mockListResult = KtxTestUtil.listResult(listOf<StorageReference>(), listOf<StorageReference>(), null)

        val (items, prefixes, pageToken) = mockListResult
        assertThat(items).isSameInstanceAs(mockListResult.items)
        assertThat(prefixes).isSameInstanceAs(mockListResult.prefixes)
        assertThat(pageToken).isSameInstanceAs(mockListResult.pageToken)
    }

    @Test
    fun `UploadTask#TaskSnapshot destructuring declarations work`() {
        val mockTaskSnapshot = Mockito.mock(UploadTask.TaskSnapshot::class.java)
        `when`(mockTaskSnapshot.bytesTransferred).thenReturn(50)
        `when`(mockTaskSnapshot.totalByteCount).thenReturn(100)
        `when`(mockTaskSnapshot.metadata).thenReturn(storageMetadata {
            contentType = "image/png"
            contentEncoding = "utf-8"
        })
        `when`(mockTaskSnapshot.uploadSessionUri).thenReturn(Uri.parse("https://test.com"))

        val (bytesTransferred, totalByteCount, metadata, sessionUri) = mockTaskSnapshot

        assertThat(bytesTransferred).isSameInstanceAs(mockTaskSnapshot.bytesTransferred)
        assertThat(totalByteCount).isSameInstanceAs(mockTaskSnapshot.totalByteCount)
        assertThat(metadata).isSameInstanceAs(mockTaskSnapshot.metadata)
        assertThat(sessionUri).isSameInstanceAs(mockTaskSnapshot.uploadSessionUri)
    }

    @Test
    fun `StreamDownloadTask#TaskSnapshot destructuring declarations work`() {
        val mockTaskSnapshot = Mockito.mock(StreamDownloadTask.TaskSnapshot::class.java)
        `when`(mockTaskSnapshot.bytesTransferred).thenReturn(50)
        `when`(mockTaskSnapshot.totalByteCount).thenReturn(100)
        `when`(mockTaskSnapshot.stream).thenReturn(ByteArrayInputStream("test".toByteArray()))

        val (bytesTransferred, totalByteCount, stream) = mockTaskSnapshot

        assertThat(bytesTransferred).isSameInstanceAs(mockTaskSnapshot.bytesTransferred)
        assertThat(totalByteCount).isSameInstanceAs(mockTaskSnapshot.totalByteCount)
        assertThat(stream).isSameInstanceAs(mockTaskSnapshot.stream)
    }

    @Test
    fun `FileDownloadTask#TaskSnapshot destructuring declarations work`() {
        val mockTaskSnapshot = Mockito.mock(FileDownloadTask.TaskSnapshot::class.java)
        `when`(mockTaskSnapshot.bytesTransferred).thenReturn(50)
        `when`(mockTaskSnapshot.totalByteCount).thenReturn(100)

        val (bytesTransferred, totalByteCount) = mockTaskSnapshot

        assertThat(bytesTransferred).isSameInstanceAs(mockTaskSnapshot.bytesTransferred)
        assertThat(totalByteCount).isSameInstanceAs(mockTaskSnapshot.totalByteCount)
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
