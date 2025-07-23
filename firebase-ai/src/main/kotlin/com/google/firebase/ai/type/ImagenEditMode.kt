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

/** Represents the edit mode for Imagen */
public class ImagenEditMode private constructor(internal val value: String) {

  public companion object {
    /** Inserts a new element into an image */
    @JvmField
    public val INPAINT_INSERTION: ImagenEditMode = ImagenEditMode("EDIT_MODE_INPAINT_INSERTION")
    /** Removes an element from an image */
    @JvmField
    public val INPAINT_REMOVAL: ImagenEditMode = ImagenEditMode("EDIT_MODE_INPAINT_REMOVAL")
    /** Extend the borders of an image outwards */
    @JvmField public val OUTPAINT: ImagenEditMode = ImagenEditMode("EDIT_MODE_OUTPAINT")
  }
}
