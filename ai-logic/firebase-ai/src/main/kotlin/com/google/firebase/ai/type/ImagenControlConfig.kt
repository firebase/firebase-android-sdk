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

internal class ImagenControlConfig(
  internal val controlType: ImagenControlType,
  internal val enableComputation: Boolean? = null,
  internal val superpixelRegionSize: Int? = null,
  internal val superpixelRuler: Int? = null
) {

  fun toInternal(): Internal {
    return Internal(
      controlType = controlType.value,
      enableControlImageComputation = enableComputation,
      superpixelRegionSize = superpixelRegionSize,
      superpixelRuler = superpixelRuler
    )
  }

  @Serializable
  internal class Internal(
    val controlType: String?,
    val enableControlImageComputation: Boolean?,
    val superpixelRegionSize: Int?,
    val superpixelRuler: Int?
  )
}
