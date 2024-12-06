/*
 * Copyright 2024 Google LLC
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

package com.google.firebase.vertexai.common

import com.google.firebase.vertexai.common.client.GenerationConfig
import com.google.firebase.vertexai.common.client.Tool
import com.google.firebase.vertexai.common.client.ToolConfig
import com.google.firebase.vertexai.common.shared.Content
import com.google.firebase.vertexai.common.shared.SafetySetting
import com.google.firebase.vertexai.common.util.fullModelName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal sealed interface Request

@Serializable
internal data class GenerateContentRequest(
  val model: String? = null,
  val contents: List<Content>,
  @SerialName("safety_settings") val safetySettings: List<SafetySetting>? = null,
  @SerialName("generation_config") val generationConfig: GenerationConfig? = null,
  val tools: List<Tool>? = null,
  @SerialName("tool_config") var toolConfig: ToolConfig? = null,
  @SerialName("system_instruction") val systemInstruction: Content? = null,
) : Request

@Serializable
internal data class CountTokensRequest(
  val generateContentRequest: GenerateContentRequest? = null,
  val model: String? = null,
  val contents: List<Content>? = null,
  val tools: List<Tool>? = null,
  @SerialName("system_instruction") val systemInstruction: Content? = null,
) : Request {
  companion object {
    fun forGenAI(generateContentRequest: GenerateContentRequest) =
      CountTokensRequest(
        generateContentRequest =
          generateContentRequest.model?.let {
            generateContentRequest.copy(model = fullModelName(it))
          }
            ?: generateContentRequest
      )

    fun forVertexAI(generateContentRequest: GenerateContentRequest) =
      CountTokensRequest(
        model = generateContentRequest.model?.let { fullModelName(it) },
        contents = generateContentRequest.contents,
        tools = generateContentRequest.tools,
        systemInstruction = generateContentRequest.systemInstruction,
      )
  }
}
