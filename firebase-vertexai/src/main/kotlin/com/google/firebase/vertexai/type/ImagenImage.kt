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
 * Represents an abstract Imagen Image that is either contained inline or in Google Cloud Storage
 *
 * @param data Contains the raw bytes of the image, mutually exclusive with [gcsUri]
 * @param gcsUri Contains the gs:// uri for the image , mutually exclusive with [data]
 * @param mimeType Contains the mime type of the image eg. "image/png"
 */
public class ImagenImage(
  public val data: ByteArray?,
  public val gcsUri: String?,
  public val mimeType: String,
) : ImagenImageRepresentible {

  override fun asImagenImage(): ImagenImage {
    return this
  }
}
