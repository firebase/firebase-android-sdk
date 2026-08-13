/*
 * Copyright 2021 Google LLC
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

package com.google.firebase.ml.modeldownloader

import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.components.Component
import com.google.firebase.components.ComponentRegistrar
import java.io.File

internal const val DEPRECATION_MESSAGE =
  "Firebase ML is deprecated and will be shut down on June 15, 2027. To " +
    "host custom models, you must migrate to another solution. For more " +
    "information about migration options, see the notification banner in the " +
    "[Firebase ML documentation](https://firebase.google.com/docs/ml)."

/** Returns the [FirebaseModelDownloader] instance of the default [FirebaseApp]. */
@Deprecated(DEPRECATION_MESSAGE)
val Firebase.modelDownloader: FirebaseModelDownloader
  get() = FirebaseModelDownloader.getInstance()

/** Returns the [FirebaseModelDownloader] instance of a given [FirebaseApp]. */
@Deprecated(DEPRECATION_MESSAGE)
fun Firebase.modelDownloader(app: FirebaseApp) = FirebaseModelDownloader.getInstance(app)

/** Returns a [CustomModelDownloadConditions] initialized using the [init] function. */
@Deprecated(DEPRECATION_MESSAGE)
fun customModelDownloadConditions(
  init: CustomModelDownloadConditions.Builder.() -> Unit
): CustomModelDownloadConditions {
  val builder = CustomModelDownloadConditions.Builder()
  builder.init()
  return builder.build()
}

@Deprecated(DEPRECATION_MESSAGE) operator fun CustomModel.component1(): File? = file

@Deprecated(DEPRECATION_MESSAGE) operator fun CustomModel.component2() = size

@Deprecated(DEPRECATION_MESSAGE) operator fun CustomModel.component3() = downloadId

@Deprecated(DEPRECATION_MESSAGE) operator fun CustomModel.component4() = modelHash

@Deprecated(DEPRECATION_MESSAGE) operator fun CustomModel.component5() = name

/** @suppress */
class FirebaseMlModelDownloaderKtxRegistrar : ComponentRegistrar {
  override fun getComponents(): List<Component<*>> = listOf()
}
