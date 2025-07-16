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

import kotlinx.serialization.Serializable

/**
 * Contains the editing settings which are not specific to a reference image
 * @param editMode holds the editing mode if the request is for inpainting or outpainting
 * @param editSteps the number of intermediate steps to include in the editing process
 */
@PublicPreviewAPI
public class ImagenEditingConfig(
  internal val editMode: ImagenEditMode? = null,
  internal val editSteps: Int? = null,
) {
  internal fun toInternal(): Internal {
    return Internal(baseSteps = editSteps)
  }

  @Serializable
  internal data class Internal(
    val baseSteps: Int?,
  )
}
