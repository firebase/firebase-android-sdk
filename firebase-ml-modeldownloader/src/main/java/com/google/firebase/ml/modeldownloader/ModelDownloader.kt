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
@Deprecated(
  "Firebase ML is deprecated and will be shut down on June 15, 2027. To host custom models, you must migrate to another solution. You can use Cloud Storage for Firebase as an alternative for hosting custom models. For more info, see https://firebase.google.com/docs/ml/migrate-to-cloud-storage"
)
val Firebase.modelDownloader: FirebaseModelDownloader
  get() = FirebaseModelDownloader.getInstance()

/** Returns the [FirebaseModelDownloader] instance of a given [FirebaseApp]. */
@Deprecated(
  "Firebase ML is deprecated and will be shut down on June 15, 2027. To host custom models, you must migrate to another solution. You can use Cloud Storage for Firebase as an alternative for hosting custom models. For more info, see https://firebase.google.com/docs/ml/migrate-to-cloud-storage"
)
fun Firebase.modelDownloader(app: FirebaseApp) = FirebaseModelDownloader.getInstance(app)

/** Returns a [CustomModelDownloadConditions] initialized using the [init] function. */
@Deprecated(
  "Firebase ML is deprecated and will be shut down on June 15, 2027. To host custom models, you must migrate to another solution. You can use Cloud Storage for Firebase as an alternative for hosting custom models. For more info, see https://firebase.google.com/docs/ml/migrate-to-cloud-storage"
)
fun customModelDownloadConditions(
  init: CustomModelDownloadConditions.Builder.() -> Unit
): CustomModelDownloadConditions {
  val builder = CustomModelDownloadConditions.Builder()
  builder.init()
  return builder.build()
}

@Deprecated(
  "Firebase ML is deprecated and will be shut down on June 15, 2027. To host custom models, you must migrate to another solution. You can use Cloud Storage for Firebase as an alternative for hosting custom models. For more info, see https://firebase.google.com/docs/ml/migrate-to-cloud-storage"
)
operator fun CustomModel.component1(): File? = file

@Deprecated(
  "Firebase ML is deprecated and will be shut down on June 15, 2027. To host custom models, you must migrate to another solution. You can use Cloud Storage for Firebase as an alternative for hosting custom models. For more info, see https://firebase.google.com/docs/ml/migrate-to-cloud-storage"
)
operator fun CustomModel.component2() = size

@Deprecated(
  "Firebase ML is deprecated and will be shut down on June 15, 2027. To host custom models, you must migrate to another solution. You can use Cloud Storage for Firebase as an alternative for hosting custom models. For more info, see https://firebase.google.com/docs/ml/migrate-to-cloud-storage"
)
operator fun CustomModel.component3() = downloadId

@Deprecated(
  "Firebase ML is deprecated and will be shut down on June 15, 2027. To host custom models, you must migrate to another solution. You can use Cloud Storage for Firebase as an alternative for hosting custom models. For more info, see https://firebase.google.com/docs/ml/migrate-to-cloud-storage"
)
operator fun CustomModel.component4() = modelHash

@Deprecated(
  "Firebase ML is deprecated and will be shut down on June 15, 2027. To host custom models, you must migrate to another solution. You can use Cloud Storage for Firebase as an alternative for hosting custom models. For more info, see https://firebase.google.com/docs/ml/migrate-to-cloud-storage"
)
operator fun CustomModel.component5() = name

/** @suppress */
class FirebaseMlModelDownloaderKtxRegistrar : ComponentRegistrar {
  override fun getComponents(): List<Component<*>> = listOf()
}
