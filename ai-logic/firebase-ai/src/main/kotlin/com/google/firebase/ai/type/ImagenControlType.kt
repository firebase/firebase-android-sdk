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

/** Represents a control type for controlled Imagen generation/editing */
public class ImagenControlType internal constructor(internal val value: String) {
  public companion object {

    /**
     * Use edge detection to ensure the new image follows the same outlines as the reference image.
     */
    @JvmField public val CANNY: ImagenControlType = ImagenControlType("CONTROL_TYPE_CANNY")

    /**
     * Use enhanced edge detection to ensure the new image follows the same outlines as the
     * reference image.
     */
    @JvmField public val SCRIBBLE: ImagenControlType = ImagenControlType("CONTROL_TYPE_SCRIBBLE")

    /**
     * Use face mesh control to ensure that the new image has the same facial expressions as the
     * reference image.
     */
    @JvmField public val FACE_MESH: ImagenControlType = ImagenControlType("CONTROL_TYPE_FACE_MESH")

    /**
     * Use color superpixels to ensure that the new image is similar in shape and color to the
     * reference image.
     */
    @JvmField
    public val COLOR_SUPERPIXEL: ImagenControlType =
      ImagenControlType("CONTROL_TYPE_COLOR_SUPERPIXEL")
  }
}
