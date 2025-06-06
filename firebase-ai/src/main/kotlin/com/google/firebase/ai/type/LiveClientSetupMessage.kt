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

/**
 * First message in a live session.
 *
 * Contains configuration that will be used for the duration of the session.
 */
@OptIn(ExperimentalSerializationApi::class)
@PublicPreviewAPI
internal class LiveClientSetupMessage(
  val model: String,
  // Some config options are supported in generateContent but not in bidi and vise versa; so bidi
  // needs its own config class
  val generationConfig: LiveGenerationConfig.Internal?,
  val tools: List<Tool.Internal>?,
  val systemInstruction: Content.Internal?
) {
  @Serializable
  internal class Internal(val setup: LiveClientSetup) {
    @Serializable
    internal data class LiveClientSetup(
      val model: String,
      val generationConfig: LiveGenerationConfig.Internal?,
      val tools: List<Tool.Internal>?,
      val systemInstruction: Content.Internal?
    )
  }

  fun toInternal() =
    Internal(Internal.LiveClientSetup(model, generationConfig, tools, systemInstruction))
}
