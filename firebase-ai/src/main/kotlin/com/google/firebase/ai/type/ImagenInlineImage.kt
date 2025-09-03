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

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlinx.serialization.Serializable

/**
 * Represents an Imagen-generated image that is returned as inline data.
 *
 * @property data The raw image bytes in JPEG or PNG format, as specified by [mimeType].
 * @property mimeType The IANA standard MIME type of the image data; either `"image/png"` or
 * `"image/jpeg"`; to request a different format, see [ImagenGenerationConfig.imageFormat].
 * @property safetyAttributes a set of safety attributes with their associated score.
 */
@PublicPreviewAPI
public class ImagenInlineImage
internal constructor(
  public val data: ByteArray,
  public val mimeType: String,
  public val safetyAttributes: Map<String, Double>) {

  /**
   * Returns the image as an Android OS native [Bitmap] so that it can be saved or sent to the UI.
   */
  public fun asBitmap(): Bitmap {
    return BitmapFactory.decodeByteArray(data, 0, data.size)
  }

  @Serializable internal data class Internal(val bytesBase64Encoded: String)

  internal fun toInternal(): Internal {
    val base64 = Base64.encodeToString(data, Base64.NO_WRAP)
    return Internal(base64)
  }
}

@PublicPreviewAPI
public fun Bitmap.toImagenInlineImage(): ImagenInlineImage {
  val byteArrayOutputStream = ByteArrayOutputStream()
  this.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
  val byteArray = byteArrayOutputStream.toByteArray()
  return ImagenInlineImage(data = byteArray, mimeType = "image/jpeg", safetyAttributes = emptyMap())
}
