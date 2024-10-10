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

package com.google.firebase.vertexai.common.client

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class GenerationConfig(
  val temperature: Float?,
  @SerialName("top_p") val topP: Float?,
  @SerialName("top_k") val topK: Int?,
  @SerialName("candidate_count") val candidateCount: Int?,
  @SerialName("max_output_tokens") val maxOutputTokens: Int?,
  @SerialName("stop_sequences") val stopSequences: List<String>?,
  @SerialName("response_mime_type") val responseMimeType: String? = null,
  @SerialName("presence_penalty") val presencePenalty: Float? = null,
  @SerialName("frequency_penalty") val frequencyPenalty: Float? = null,
  @SerialName("response_schema") val responseSchema: Schema? = null,
)

@Serializable
internal data class Tool(
  val functionDeclarations: List<FunctionDeclaration>? = null,
  // This is a json object because it is not possible to make a data class with no parameters.
  val codeExecution: JsonObject? = null,
)

@Serializable
internal data class ToolConfig(
  @SerialName("function_calling_config") val functionCallingConfig: FunctionCallingConfig?
)

@Serializable
internal data class FunctionCallingConfig(
  val mode: Mode,
  @SerialName("allowed_function_names") val allowedFunctionNames: List<String>? = null
) {
  @Serializable
  enum class Mode {
    @SerialName("MODE_UNSPECIFIED") UNSPECIFIED,
    AUTO,
    ANY,
    NONE
  }
}

@Serializable
internal data class FunctionDeclaration(
  val name: String,
  val description: String,
  val parameters: Schema
)

@Serializable
internal data class Schema(
  val type: String,
  val description: String? = null,
  val format: String? = null,
  val nullable: Boolean? = false,
  val enum: List<String>? = null,
  val properties: Map<String, Schema>? = null,
  val required: List<String>? = null,
  val items: Schema? = null,
)
