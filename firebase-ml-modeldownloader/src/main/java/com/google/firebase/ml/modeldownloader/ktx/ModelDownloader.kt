// Copyright 2021 Google LLC
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

package com.google.firebase.ml.modeldownloader.ktx

import com.google.firebase.FirebaseApp
import com.google.firebase.components.Component
import com.google.firebase.components.ComponentRegistrar
import com.google.firebase.ktx.Firebase
import com.google.firebase.ml.modeldownloader.CustomModel
import com.google.firebase.ml.modeldownloader.CustomModelDownloadConditions
import com.google.firebase.ml.modeldownloader.FirebaseModelDownloader
import java.io.File

/** Returns the [FirebaseModelDownloader] instance of the default [FirebaseApp]. */
@Deprecated(
  "com.google.firebase.ml.modeldownloaderktx.Firebase.modelDownloader has been deprecated. Use `com.google.firebase.ml.modeldownloaderFirebase.modelDownloader` instead.",
  ReplaceWith(
    expression = "Firebase.modelDownloader",
    imports =
      ["com.google.firebase.Firebase", "com.google.firebase.ml.modeldownloadermodelDownloader"]
  )
)
val Firebase.modelDownloader: FirebaseModelDownloader
  get() = FirebaseModelDownloader.getInstance()

/** Returns the [FirebaseModelDownloader] instance of a given [FirebaseApp]. */
@Deprecated(
  "com.google.firebase.ml.modeldownloaderktx.Firebase.modelDownloader(app) has been deprecated. Use `com.google.firebase.ml.modeldownloaderFirebase.modelDownloader(app)` instead.",
  ReplaceWith(
    expression = "Firebase.modelDownloader(app)",
    imports =
      ["com.google.firebase.Firebase", "com.google.firebase.ml.modeldownloadermodelDownloader"]
  )
)
fun Firebase.modelDownloader(app: FirebaseApp) = FirebaseModelDownloader.getInstance(app)

/** Returns a [CustomModelDownloadConditions] initialized using the [init] function. */
@Deprecated(
  "com.google.firebase.ml.modeldownloaderktx.customModelDownloadConditions( has been deprecated. Use `com.google.firebase.ml.modeldownloadercustomModelDownloadConditions(` instead.",
  ReplaceWith(
    expression = "customModelDownloadConditions(",
    imports =
      [
        "com.google.firebase.Firebase",
        "com.google.firebase.ml.modeldownloadercustomModelDownloadConditions"
      ]
  )
)
fun customModelDownloadConditions(
  init: CustomModelDownloadConditions.Builder.() -> Unit
): CustomModelDownloadConditions {
  val builder = CustomModelDownloadConditions.Builder()
  builder.init()
  return builder.build()
}

operator fun CustomModel.component1(): File? = file

operator fun CustomModel.component2() = size

operator fun CustomModel.component3() = downloadId

operator fun CustomModel.component4() = modelHash

operator fun CustomModel.component5() = name

/** @suppress */
@Deprecated(
  "com.google.firebase.ml.modeldownloaderktx.FirebaseMlModelDownloaderKtxRegistrar has been deprecated. Use `com.google.firebase.ml.modeldownloaderFirebaseMlModelDownloaderKtxRegistrar` instead.",
  ReplaceWith(
    expression = "FirebaseMlModelDownloaderKtxRegistrar",
    imports =
      [
        "com.google.firebase.Firebase",
        "com.google.firebase.ml.modeldownloaderFirebaseMlModelDownloaderKtxRegistrar"
      ]
  )
)
class FirebaseMlModelDownloaderKtxRegistrar : ComponentRegistrar {
  override fun getComponents(): List<Component<*>> = listOf()
}
