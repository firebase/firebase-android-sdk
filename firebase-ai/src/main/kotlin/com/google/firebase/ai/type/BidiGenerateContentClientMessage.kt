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

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@PublicPreviewAPI
internal class BidiGenerateContentClientMessage(
  val model: String,
  val generationConfig: LiveGenerationConfig.Internal?,
  val tools: List<Tool.Internal>?,
  val systemInstruction: Content.Internal?
) {

  @Serializable
  internal class Internal(val setup: BidiGenerateContentSetup) {
    @Serializable
    internal data class BidiGenerateContentSetup(
      val model: String,
      val generationConfig: LiveGenerationConfig.Internal?,
      val tools: List<Tool.Internal>?,
      val systemInstruction: Content.Internal?
    )
  }

  fun toInternal() =
    Internal(Internal.BidiGenerateContentSetup(model, generationConfig, tools, systemInstruction))
}
