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

/**
 * Represents the format an image should be returned in.
 * @param mimeType A string (like "image/jpeg") specifying the encoding mimetype of the image.
 * @param compressionQuality an int (1-100) representing how the quality of the image, a lower
 * number meaning the image is permitted to be lower quality to reduce size. This parameter is not
 * relevant for every mimetype
 */
public class ImagenImageFormat
private constructor(public val mimeType: String, public val compressionQuality: Int?) {

  public companion object {
    /**
     * An [ImagenImageFormat] representing a JPEG image.
     * @param compressionQuality an int (1-100) representing how the quality of the image, a lower
     * number meaning the image is permitted to be lower quality to reduce size.
     */
    public fun jpeg(compressionQuality: Int? = null): ImagenImageFormat {
      return ImagenImageFormat("image/jpeg", compressionQuality)
    }

    /** An [ImagenImageFormat] representing a PNG image */
    public fun png(): ImagenImageFormat {
      return ImagenImageFormat("image/png", null)
    }
  }
}
