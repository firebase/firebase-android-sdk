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
import android.util.Base64

/**
 * Represents an Imagen Image that is contained inline
 *
 * @param data Contains the raw bytes of the image
 * @param mimeType Contains the MIME type of the image (for example, `"image/png"`)
 */
public class ImagenInlineImage
internal constructor(public val data: ByteArray, public val mimeType: String) {

  /**
   * Returns the image as an Android OS native [Bitmap] so that it can be saved or sent to the UI.
   */
  public fun asBitmap(): Bitmap {
    val data = Base64.decode(data, Base64.NO_WRAP)
    return BitmapFactory.decodeByteArray(data, 0, data.size)
  }
}
