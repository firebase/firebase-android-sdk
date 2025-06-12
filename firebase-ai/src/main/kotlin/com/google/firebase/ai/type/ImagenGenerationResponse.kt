/*
 * Copyright 2025 Google LLC
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

package com.google.firebase.ai.type

import android.util.Base64
import com.google.firebase.ai.ImagenModel
import kotlinx.serialization.Serializable

/**
 * Represents a response from a call to [ImagenModel.generateImages]
 *
 * @param images contains the generated images
 * @param filteredReason if fewer images were generated than were requested, this field will contain
 * the reason they were filtered out.
 */
@PublicPreviewAPI
public class ImagenGenerationResponse<T>
internal constructor(public val images: List<T>, public val filteredReason: String?) {

  @Serializable
  internal data class Internal(val predictions: List<ImagenImageResponse>) {
    internal fun toPublicGCS() =
      ImagenGenerationResponse(
        images = predictions.filter { it.mimeType != null }.map { it.toPublicGCS() },
        null,
      )

    internal fun toPublicInline() =
      ImagenGenerationResponse(
        images = predictions.filter { it.mimeType != null }.map { it.toPublicInline() },
        null,
      )
  }

  @Serializable
  internal data class ImagenImageResponse(
    val bytesBase64Encoded: String? = null,
    val gcsUri: String? = null,
    val mimeType: String? = null,
    val raiFilteredReason: String? = null,
  ) {
    internal fun toPublicInline() =
      ImagenInlineImage(Base64.decode(bytesBase64Encoded!!, Base64.NO_WRAP), mimeType!!)

    internal fun toPublicGCS() = ImagenGCSImage(gcsUri!!, mimeType!!)
  }
}
