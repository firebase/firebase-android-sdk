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

/** Returns the [FirebaseModelDownloader] instance of the default [FirebaseApp]. */
val Firebase.modelDownloader: FirebaseModelDownloader
  get() = FirebaseModelDownloader.getInstance()

/** Returns the [FirebaseModelDownloader] instance of a given [FirebaseApp]. */
fun Firebase.modelDownloader(app: FirebaseApp) = FirebaseModelDownloader.getInstance(app)

/** Returns a [CustomModelDownloadConditions] initialized using the [init] function. */
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
class FirebaseMlModelDownloaderKtxRegistrar : ComponentRegistrar {
  override fun getComponents(): List<Component<*>> = listOf()
}
