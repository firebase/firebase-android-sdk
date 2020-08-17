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

import androidx.annotation.Keep
import com.google.firebase.FirebaseApp
import com.google.firebase.components.Component
import com.google.firebase.components.ComponentRegistrar
import com.google.firebase.ktx.Firebase
import com.google.firebase.platforminfo.LibraryVersionComponent
import com.google.firebase.storage.FileDownloadTask
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ListResult
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.StreamDownloadTask
import com.google.firebase.storage.UploadTask

/** Returns the [FirebaseStorage] instance of the default [FirebaseApp]. */
val Firebase.storage: FirebaseStorage
    get() = FirebaseStorage.getInstance()

/** Returns the [FirebaseStorage] instance for a custom storage bucket at [url]. */
fun Firebase.storage(url: String): FirebaseStorage = FirebaseStorage.getInstance(url)

/** Returns the [FirebaseStorage] instance of a given [FirebaseApp]. */
fun Firebase.storage(app: FirebaseApp): FirebaseStorage = FirebaseStorage.getInstance(app)

/** Returns the [FirebaseStorage] instance of a given [FirebaseApp] and storage bucket [url]. */
fun Firebase.storage(app: FirebaseApp, url: String): FirebaseStorage =
        FirebaseStorage.getInstance(app, url)

/** Returns a [StorageMetadata] object initialized using the [init] function. */
fun storageMetadata(init: StorageMetadata.Builder.() -> Unit): StorageMetadata {
    val builder = StorageMetadata.Builder()
    builder.init()
    return builder.build()
}

/**
 * Destructuring declaration for [UploadTask.TaskSnapshot] to provide bytesTransferred.
 *
 * @return the bytesTransferred of the [UploadTask.TaskSnapshot]
 */
operator fun UploadTask.TaskSnapshot.component1() = bytesTransferred

/**
 * Destructuring declaration for [UploadTask.TaskSnapshot] to provide totalByteCount.
 *
 * @return the totalByteCount of the [UploadTask.TaskSnapshot]
 */
operator fun UploadTask.TaskSnapshot.component2() = totalByteCount

/**
 * Destructuring declaration for [UploadTask.TaskSnapshot] to provide its metadata.
 *
 * @return the metadata of the [UploadTask.TaskSnapshot]
 */
operator fun UploadTask.TaskSnapshot.component3() = metadata

/**
 * Destructuring declaration for [UploadTask.TaskSnapshot] to provide its uploadSessionUri.
 *
 * @return the uploadSessionUri of the [UploadTask.TaskSnapshot]
 */
operator fun UploadTask.TaskSnapshot.component4() = uploadSessionUri

/**
 * Destructuring declaration for [StreamDownloadTask.TaskSnapshot] to provide bytesTransferred.
 *
 * @return the bytesTransferred of the [StreamDownloadTask.TaskSnapshot]
 */
operator fun StreamDownloadTask.TaskSnapshot.component1() = bytesTransferred

/**
 * Destructuring declaration for [StreamDownloadTask.TaskSnapshot] to provide totalByteCount.
 *
 * @return the totalByteCount of the [StreamDownloadTask.TaskSnapshot]
 */
operator fun StreamDownloadTask.TaskSnapshot.component2() = totalByteCount

/**
 * Destructuring declaration for [StreamDownloadTask.TaskSnapshot] to provide its stream.
 *
 * @return the stream of the [StreamDownloadTask.TaskSnapshot]
 */
operator fun StreamDownloadTask.TaskSnapshot.component3() = stream

/**
 * Destructuring declaration for [FileDownloadTask.TaskSnapshot] to provide bytesTransferred.
 *
 * @return the bytesTransferred of the [FileDownloadTask.TaskSnapshot]
 */
operator fun FileDownloadTask.TaskSnapshot.component1() = bytesTransferred

/**
 * Destructuring declaration for [FileDownloadTask.TaskSnapshot] to provide totalByteCount.
 *
 * @return the totalByteCount of the [FileDownloadTask.TaskSnapshot]
 */
operator fun FileDownloadTask.TaskSnapshot.component2() = totalByteCount

/**
 * Destructuring declaration for [ListResult] to provide its items.
 *
 * @return the items of the [ListResult]
 */
operator fun ListResult.component1(): List<StorageReference> = items

/**
 * Destructuring declaration for [ListResult] to provide its prefixes.
 *
 * @return the prefixes of the [ListResult]
 */
operator fun ListResult.component2(): List<StorageReference> = prefixes

/**
 * Destructuring declaration for [ListResult] to provide its pageToken.
 *
 * @return the pageToken of the [ListResult]
 */
operator fun ListResult.component3(): String? = pageToken

internal const val LIBRARY_NAME: String = "fire-stg-ktx"

/** @suppress */
@Keep
class FirebaseStorageKtxRegistrar : ComponentRegistrar {
    override fun getComponents(): List<Component<*>> =
            listOf(LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME))
}
