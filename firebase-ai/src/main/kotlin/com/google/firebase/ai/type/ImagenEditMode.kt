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

/** Represents the edit mode for this imagen editing config */
public class ImagenEditMode private constructor(internal val value: String) {

  public companion object {
    /**
     * Inpainting insertion is an edit mode where you mask off an area of the image, and use the
     * prompt to add new elements to the image.
     */
    public val INPAINT_INSERTION: ImagenEditMode = ImagenEditMode("EDIT_MODE_INPAINT_INSERTION")
    /**
     * Inpainting removal is an edit mode where you mask off an area of the image, and use the
     * prompt to remove elements from the image.
     */
    public val INPAINT_REMOVAL: ImagenEditMode = ImagenEditMode("EDIT_MODE_INPAINT_REMOVAL")
    /**
     * Outpainting is an edit mode where your mask is larger than the image, and expands the
     * boundaries of the image by continuing the background. The prompt can guide this process.
     */
    public val OUTPAINT: ImagenEditMode = ImagenEditMode("EDIT_MODE_OUTPAINT")
  }
}
