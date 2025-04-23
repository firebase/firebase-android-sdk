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

/**
 * Represents an Imagen-generated image that is contained in Google Cloud Storage.
 *
 * @param gcsUri Contains the `gs://` URI for the image.
 * @param mimeType Contains the MIME type of the image (for example, `"image/png"`).
 */
@PublicPreviewAPI
internal class ImagenGCSImage
internal constructor(public val gcsUri: String, public val mimeType: String) {}
