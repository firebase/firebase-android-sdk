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
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.FirebaseApp
import com.google.firebase.components.Component
import com.google.firebase.components.ComponentRegistrar
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FileDownloadTask
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ListResult
import com.google.firebase.storage.OnPausedListener
import com.google.firebase.storage.OnProgressListener
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.StorageTask
import com.google.firebase.storage.StorageTaskScheduler
import com.google.firebase.storage.StreamDownloadTask
import com.google.firebase.storage.UploadTask
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Returns the [FirebaseStorage] instance of the default [FirebaseApp]. */
@Deprecated(
  "Use `com.google.firebase.Firebase.storage` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-storage-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the <a href=\"https://firebase.google.com/docs/android/ktx-apis-to-main-modules\">FAQ about this initiative.</a>",
  ReplaceWith(
    expression = "com.google.firebase.Firebase.storage",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.storage.storage"]
  )
)
val Firebase.storage: FirebaseStorage
  get() = FirebaseStorage.getInstance()

/** Returns the [FirebaseStorage] instance for a custom storage bucket at [url]. */
@Deprecated(
  "Use `com.google.firebase.Firebase.storage(url)` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-storage-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the <a href=\"https://firebase.google.com/docs/android/ktx-apis-to-main-modules\">FAQ about this initiative.</a>",
  ReplaceWith(
    expression = "com.google.firebase.Firebase.storage(url)",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.storage.storage"]
  )
)
fun Firebase.storage(url: String): FirebaseStorage = FirebaseStorage.getInstance(url)

/** Returns the [FirebaseStorage] instance of a given [FirebaseApp]. */
@Deprecated(
  "Use `com.google.firebase.Firebase.storage(app)` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-storage-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the <a href=\"https://firebase.google.com/docs/android/ktx-apis-to-main-modules\">FAQ about this initiative.</a>",
  ReplaceWith(
    expression = "com.google.firebase.Firebase.storage(app)",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.storage.storage"]
  )
)
fun Firebase.storage(app: FirebaseApp): FirebaseStorage = FirebaseStorage.getInstance(app)

/** Returns the [FirebaseStorage] instance of a given [FirebaseApp] and storage bucket [url]. */
@Deprecated(
  "Use `com.google.firebase.Firebase.storage(app, url)` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-storage-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the <a href=\"https://firebase.google.com/docs/android/ktx-apis-to-main-modules\">FAQ about this initiative.</a>",
  ReplaceWith(
    expression = "com.google.firebase.Firebase.storage(app, url)",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.storage.storage"]
  )
)
fun Firebase.storage(app: FirebaseApp, url: String): FirebaseStorage =
  FirebaseStorage.getInstance(app, url)

/** Returns a [StorageMetadata] object initialized using the [init] function. */
@Deprecated(
  "Use `com.google.firebase.storage.storageMetadata(init)` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-storage-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the <a href=\"https://firebase.google.com/docs/android/ktx-apis-to-main-modules\">FAQ about this initiative.</a>",
  ReplaceWith(
    expression = "storageMetadata(init)",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.storage.storageMetadata"]
  )
)
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
@Deprecated(
  "Use `com.google.firebase.storage.UploadTask.TaskSnapshot.component1` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-storage-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the <a href=\"https://firebase.google.com/docs/android/ktx-apis-to-main-modules\">FAQ about this initiative.</a>",
  ReplaceWith(
    expression = "component1()",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.storage.component1"]
  )
)
operator fun UploadTask.TaskSnapshot.component1() = bytesTransferred

/**
 * Destructuring declaration for [UploadTask.TaskSnapshot] to provide totalByteCount.
 *
 * @return the totalByteCount of the [UploadTask.TaskSnapshot]
 */
@Deprecated(
  "Use `com.google.firebase.storage.UploadTask.TaskSnapshot.component2` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-storage-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the <a href=\"https://firebase.google.com/docs/android/ktx-apis-to-main-modules\">FAQ about this initiative.</a>",
  ReplaceWith(
    expression = "component2()",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.storage.component2"]
  )
)
operator fun UploadTask.TaskSnapshot.component2() = totalByteCount

/**
 * Destructuring declaration for [UploadTask.TaskSnapshot] to provide its metadata.
 *
 * @return the metadata of the [UploadTask.TaskSnapshot]
 */
@Deprecated(
  "Use `com.google.firebase.storage.UploadTask.TaskSnapshot.component3` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-storage-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the <a href=\"https://firebase.google.com/docs/android/ktx-apis-to-main-modules\">FAQ about this initiative.</a>",
  ReplaceWith(
    expression = "component3()",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.storage.component3"]
  )
)
operator fun UploadTask.TaskSnapshot.component3() = metadata

/**
 * Destructuring declaration for [UploadTask.TaskSnapshot] to provide its uploadSessionUri.
 *
 * @return the uploadSessionUri of the [UploadTask.TaskSnapshot]
 */
@Deprecated(
  "Use `com.google.firebase.storage.UploadTask.TaskSnapshot.component4` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-storage-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the <a href=\"https://firebase.google.com/docs/android/ktx-apis-to-main-modules\">FAQ about this initiative.</a>",
  ReplaceWith(
    expression = "component4()",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.storage.component4"]
  )
)
operator fun UploadTask.TaskSnapshot.component4() = uploadSessionUri

/**
 * Destructuring declaration for [StreamDownloadTask.TaskSnapshot] to provide bytesTransferred.
 *
 * @return the bytesTransferred of the [StreamDownloadTask.TaskSnapshot]
 */
@Deprecated(
  "Use `com.google.firebase.storage.StreamDownloadTask.TaskSnapshot.component1` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-storage-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the <a href=\"https://firebase.google.com/docs/android/ktx-apis-to-main-modules\">FAQ about this initiative.</a>",
  ReplaceWith(
    expression = "component1()",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.storage.component1"]
  )
)
operator fun StreamDownloadTask.TaskSnapshot.component1() = bytesTransferred

/**
 * Destructuring declaration for [StreamDownloadTask.TaskSnapshot] to provide totalByteCount.
 *
 * @return the totalByteCount of the [StreamDownloadTask.TaskSnapshot]
 */
@Deprecated(
  "Use `com.google.firebase.storage.StreamDownloadTask.TaskSnapshot.component2` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-storage-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the <a href=\"https://firebase.google.com/docs/android/ktx-apis-to-main-modules\">FAQ about this initiative.</a>",
  ReplaceWith(
    expression = "component2()",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.storage.component2"]
  )
)
operator fun StreamDownloadTask.TaskSnapshot.component2() = totalByteCount

/**
 * Destructuring declaration for [StreamDownloadTask.TaskSnapshot] to provide its stream.
 *
 * @return the stream of the [StreamDownloadTask.TaskSnapshot]
 */
@Deprecated(
  "Use `com.google.firebase.storage.StreamDownloadTask.TaskSnapshot.component3` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-storage-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the <a href=\"https://firebase.google.com/docs/android/ktx-apis-to-main-modules\">FAQ about this initiative.</a>",
  ReplaceWith(
    expression = "component3()",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.storage.component3"]
  )
)
operator fun StreamDownloadTask.TaskSnapshot.component3() = stream

/**
 * Destructuring declaration for [FileDownloadTask.TaskSnapshot] to provide bytesTransferred.
 *
 * @return the bytesTransferred of the [FileDownloadTask.TaskSnapshot]
 */
@Deprecated(
  "Use `com.google.firebase.storage.FileDownloadTask.TaskSnapshot.component1` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-storage-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the <a href=\"https://firebase.google.com/docs/android/ktx-apis-to-main-modules\">FAQ about this initiative.</a>",
  ReplaceWith(
    expression = "component1()",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.storage.component1"]
  )
)
operator fun FileDownloadTask.TaskSnapshot.component1() = bytesTransferred

/**
 * Destructuring declaration for [FileDownloadTask.TaskSnapshot] to provide totalByteCount.
 *
 * @return the totalByteCount of the [FileDownloadTask.TaskSnapshot]
 */
@Deprecated(
  "Use `com.google.firebase.storage.FileDownloadTask.TaskSnapshot.component2` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-storage-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the <a href=\"https://firebase.google.com/docs/android/ktx-apis-to-main-modules\">FAQ about this initiative.</a>",
  ReplaceWith(
    expression = "component2()",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.storage.component2"]
  )
)
operator fun FileDownloadTask.TaskSnapshot.component2() = totalByteCount

/**
 * Destructuring declaration for [ListResult] to provide its items.
 *
 * @return the items of the [ListResult]
 */
@Deprecated(
  "Use `com.google.firebase.storage.ListResult.component1` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-storage-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the <a href=\"https://firebase.google.com/docs/android/ktx-apis-to-main-modules\">FAQ about this initiative.</a>",
  ReplaceWith(
    expression = "component1()",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.storage.component1"]
  )
)
operator fun ListResult.component1(): List<StorageReference> = items

/**
 * Destructuring declaration for [ListResult] to provide its prefixes.
 *
 * @return the prefixes of the [ListResult]
 */
@Deprecated(
  "Use `com.google.firebase.storage.ListResult.component2` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-storage-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the <a href=\"https://firebase.google.com/docs/android/ktx-apis-to-main-modules\">FAQ about this initiative.</a>",
  ReplaceWith(
    expression = "component2()",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.storage.component2"]
  )
)
operator fun ListResult.component2(): List<StorageReference> = prefixes

/**
 * Destructuring declaration for [ListResult] to provide its pageToken.
 *
 * @return the pageToken of the [ListResult]
 */
@Deprecated(
  "Use `com.google.firebase.storage.ListResult.component3` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-storage-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the <a href=\"https://firebase.google.com/docs/android/ktx-apis-to-main-modules\">FAQ about this initiative.</a>",
  ReplaceWith(
    expression = "component3()",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.storage.component3"]
  )
)
operator fun ListResult.component3(): String? = pageToken

/**
 * Starts listening to this task's progress and emits its values via a [Flow].
 *
 * - When the returned flow starts being collected, it attaches the following listeners:
 * [OnProgressListener], [OnPausedListener], [OnCompleteListener].
 * - When the flow completes the listeners will be removed.
 */
@Deprecated(
  "Use `com.google.firebase.storage.StorageTask.taskState` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-storage-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the <a href=\"https://firebase.google.com/docs/android/ktx-apis-to-main-modules\">FAQ about this initiative.</a>",
  ReplaceWith(
    expression = "StorageTask<T>.taskState",
    imports =
      [
        "com.google.firebase.Firebase",
        "com.google.firebase.storage.ListResult.StorageTask.taskState"
      ]
  )
)
val <T : StorageTask<T>.SnapshotBase> StorageTask<T>.taskState: Flow<TaskState<T>>
  get() = callbackFlow {
    val progressListener =
      OnProgressListener<T> { snapshot ->
        StorageTaskScheduler.getInstance().scheduleCallback {
          trySendBlocking(TaskState.InProgress(snapshot))
        }
      }
    val pauseListener =
      OnPausedListener<T> { snapshot ->
        StorageTaskScheduler.getInstance().scheduleCallback {
          trySendBlocking(TaskState.Paused(snapshot))
        }
      }

    // Only used to close or cancel the Flows, doesn't send any values
    val completionListener =
      OnCompleteListener<T> { task ->
        if (task.isSuccessful) {
          close()
        } else {
          val exception = task.exception
          cancel("Error getting the TaskState", exception)
        }
      }

    addOnProgressListener(progressListener)
    addOnPausedListener(pauseListener)
    addOnCompleteListener(completionListener)

    awaitClose {
      removeOnProgressListener(progressListener)
      removeOnPausedListener(pauseListener)
      removeOnCompleteListener(completionListener)
    }
  }

/** @suppress */
@Deprecated(
  "Use `com.google.firebase.storage.FirebaseStorageKtxRegistrar` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-storage-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the <a href=\"https://firebase.google.com/docs/android/ktx-apis-to-main-modules\">FAQ about this initiative.</a>",
  ReplaceWith(
    expression = "FirebaseStorageKtxRegistrar",
    imports =
      ["com.google.firebase.Firebase", "com.google.firebase.storage.FirebaseStorageKtxRegistrar"]
  )
)
@Keep
class FirebaseStorageKtxRegistrar : ComponentRegistrar {
  override fun getComponents(): List<Component<*>> = listOf()
}
