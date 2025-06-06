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

/** Represents the aspect ratio that the generated image should conform to. */
@PublicPreviewAPI
@Deprecated(
  """The Vertex AI in Firebase SDK (firebase-vertexai) has been replaced with the FirebaseAI SDK (firebase-ai) to accommodate the evolving set of supported features and services.
For migration details, see the migration guide: https://firebase.google.com/docs/vertex-ai/migrate-to-latest-sdk"""
)
public class ImagenAspectRatio private constructor(internal val internalVal: String) {
  public companion object {
    /** A square image, useful for icons, profile pictures, etc. */
    @JvmField public val SQUARE_1x1: ImagenAspectRatio = ImagenAspectRatio("1:1")
    /** A portrait image in 3:4, the aspect ratio of older TVs. */
    @JvmField public val PORTRAIT_3x4: ImagenAspectRatio = ImagenAspectRatio("3:4")
    /** A landscape image in 4:3, the aspect ratio of older TVs. */
    @JvmField public val LANDSCAPE_4x3: ImagenAspectRatio = ImagenAspectRatio("4:3")
    /** A portrait image in 9:16, the aspect ratio of modern monitors and phone screens. */
    @JvmField public val PORTRAIT_9x16: ImagenAspectRatio = ImagenAspectRatio("9:16")
    /** A landscape image in 16:9, the aspect ratio of modern monitors and phone screens. */
    @JvmField public val LANDSCAPE_16x9: ImagenAspectRatio = ImagenAspectRatio("16:9")
  }
}
