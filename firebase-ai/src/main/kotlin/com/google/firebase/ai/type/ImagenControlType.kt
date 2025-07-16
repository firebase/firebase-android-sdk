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
    /** Canny Control uses edge detection to ensure the new image follow the same outlines */
    public val CANNY: ImagenControlType = ImagenControlType("CONTROL_TYPE_CANNY")
    /**
     * Scribble Control uses enhanced edge detection to ensure the new image follow similar outlines
     */
    public val SCRIBBLE: ImagenControlType = ImagenControlType("CONTROL_TYPE_SCRIBBLE")
    /** Face mesh control is used to ensure that the new image has the same facial expressions */
    public val FACE_MESH: ImagenControlType = ImagenControlType("CONTROL_TYPE_FACE_MESH")
    /**
     * Color superpixels are a control mechanism used to ensure that the new image looks similar in
     * shape and color to the original
     */
    public val COLOR_SUPERPIXEL: ImagenControlType =
      ImagenControlType("CONTROL_TYPE_COLOR_SUPERPIXEL")
  }
}
