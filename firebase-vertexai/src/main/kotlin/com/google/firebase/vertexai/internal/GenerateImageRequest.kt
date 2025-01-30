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

package com.google.firebase.vertexai.internal

import com.google.firebase.vertexai.common.Request
import kotlinx.serialization.Serializable

@Serializable
internal data class GenerateImageRequest(
  val instances: List<ImagenPromptInstance>,
  val parameters: ImagenParameters,
) : Request {}

@Serializable internal data class ImagenPromptInstance(val prompt: String)

@Serializable
internal data class ImagenParameters(
  val sampleCount: Int = 1,
  val includeRaiReason: Boolean = true,
  val storageUri: String?,
  val negativePrompt: String?,
  val aspectRatio: String?,
  val safetySetting: String?,
  val personGeneration: String?,
  val addWatermark: Boolean?,
  val imageOutputOptions: ImageOutputOptions?,
)

@Serializable
internal data class ImageOutputOptions(val mimeType: String, val compressionQuality: Int?)
