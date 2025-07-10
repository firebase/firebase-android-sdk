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

import com.google.firebase.ai.common.GenerateImageRequest
import kotlinx.serialization.Serializable

@PublicPreviewAPI
public abstract class ImagenReferenceImage
internal constructor(
  internal val maskConfig: ImagenMaskConfig? = null,
  internal val subjectConfig: ImagenSubjectConfig? = null,
  internal val styleConfig: ImagenStyleConfig? = null,
  internal val controlConfig: ImagenControlConfig? = null,
  public val image: ImagenInlineImage? = null,
  public val referenceId: Int? = null,
) {

  internal fun toInternal(optionalReferenceId: Int): Internal {
    val referenceType =
      when (this) {
        is ImagenRawImage -> GenerateImageRequest.ReferenceType.RAW
        is ImagenMaskReference -> GenerateImageRequest.ReferenceType.MASK
        is ImagenSubjectReference -> GenerateImageRequest.ReferenceType.SUBJECT
        is ImagenStyleReference -> GenerateImageRequest.ReferenceType.STYLE
        is ImagenControlReference -> GenerateImageRequest.ReferenceType.CONTROL
        else -> {
          throw IllegalStateException(
            "${this.javaClass.simpleName} is not a known subtype of ImagenReferenceImage"
          )
        }
      }
    return Internal(
      referenceType = referenceType,
      referenceImage = image?.toInternal(),
      referenceId = referenceId ?: optionalReferenceId,
      subjectImageConfig = subjectConfig?.toInternal(),
      maskImageConfig = maskConfig?.toInternal(),
      styleImageConfig = styleConfig?.toInternal(),
    )
  }

  @Serializable
  internal data class Internal(
    val referenceType: GenerateImageRequest.ReferenceType,
    val referenceImage: ImagenInlineImage.Internal?,
    val referenceId: Int,
    val subjectImageConfig: ImagenSubjectConfig.Internal?,
    val maskImageConfig: ImagenMaskConfig.Internal?,
    val styleImageConfig: ImagenStyleConfig.Internal?,
  )
}
