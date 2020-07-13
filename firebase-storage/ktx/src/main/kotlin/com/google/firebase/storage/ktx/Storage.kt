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
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata

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

internal const val LIBRARY_NAME: String = "fire-stg-ktx"

/** @suppress */
@Keep
class FirebaseStorageKtxRegistrar : ComponentRegistrar {
    override fun getComponents(): List<Component<*>> =
            listOf(LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME))
}
