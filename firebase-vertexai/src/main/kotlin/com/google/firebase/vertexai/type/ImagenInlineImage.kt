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

package com.google.firebase.vertexai.type

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Represents an Imagen-generated image that is returned as inline data.
 *
 * @property data The raw image bytes in JPEG or PNG format, as specified by [mimeType].
 * @property mimeType The IANA standard MIME type of the image data; either `"image/png"` or
 * `"image/jpeg"`; to request a different format, see [ImagenGenerationConfig.imageFormat].
 */
@PublicPreviewAPI
@Deprecated(
  """The Vertex AI in Firebase SDK (firebase-vertexai) has been replaced with the FirebaseAI SDK (firebase-ai) to accommodate the evolving set of supported features and services.
For migration details, see the migration guide: https://firebase.google.com/docs/vertex-ai/migrate-to-latest-sdk"""
)
public class ImagenInlineImage
internal constructor(public val data: ByteArray, public val mimeType: String) {

  /**
   * Returns the image as an Android OS native [Bitmap] so that it can be saved or sent to the UI.
   */
  public fun asBitmap(): Bitmap {
    return BitmapFactory.decodeByteArray(data, 0, data.size)
  }
}
