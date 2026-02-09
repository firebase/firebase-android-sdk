/*
 * Copyright 2026 Google LLC
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

package com.google.firebase.ai.generativemodel

import com.google.firebase.ai.common.APIController
import com.google.firebase.ai.common.CountTokensRequest
import com.google.firebase.ai.common.GenerateContentRequest
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.CountTokensResponse
import com.google.firebase.ai.type.FinishReason
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.GenerateObjectResponse
import com.google.firebase.ai.type.GenerationConfig
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.GenerativeBackendEnum
import com.google.firebase.ai.type.InvalidStateException
import com.google.firebase.ai.type.JsonSchema
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.SafetySetting
import com.google.firebase.ai.type.SerializationException
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.ToolConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

internal class CloudGenerativeModelProvider(
  private val modelName: String,
  private val generationConfig: GenerationConfig? = null,
  private val safetySettings: List<SafetySetting>? = null,
  private val tools: List<Tool>? = null,
  private val toolConfig: ToolConfig? = null,
  private val systemInstruction: Content? = null,
  private val generativeBackend: GenerativeBackend = GenerativeBackend.googleAI(),
  private val controller: APIController,
) : GenerativeModelProvider {

  override suspend fun generateContent(prompt: List<Content>): GenerateContentResponse =
    try {
      controller.generateContent(buildGenerateContentRequest(prompt)).toPublic().validate()
    } catch (e: Throwable) {
      throw FirebaseAIException.from(e)
    }

  override suspend fun countTokens(prompt: List<Content>): CountTokensResponse =
    try {
      controller.countTokens(buildCountTokensRequest(prompt)).toPublic()
    } catch (e: Throwable) {
      throw FirebaseAIException.from(e)
    }

  override fun generateContentStream(prompt: List<Content>): Flow<GenerateContentResponse> =
    controller
      .generateContentStream(buildGenerateContentRequest(prompt))
      .map { it.toPublic().validate() }
      .catch { throw FirebaseAIException.from(it) }

  override suspend fun <T : Any> generateObject(
    jsonSchema: JsonSchema<T>,
    prompt: List<Content>
  ): GenerateObjectResponse<T> =
    try {
      val config =
        (generationConfig?.toBuilder() ?: GenerationConfig.builder())
          .setResponseSchemaJson(jsonSchema)
          .setResponseMimeType("application/json")
          .build()
      val request = buildGenerateContentRequest(prompt, config)
      GenerateObjectResponse(controller.generateContent(request).toPublic().validate(), jsonSchema)
    } catch (e: Throwable) {
      throw FirebaseAIException.from(e)
    }

  private fun buildGenerateContentRequest(
    prompt: List<Content>,
    overrideConfig: GenerationConfig? = null
  ) =
    GenerateContentRequest(
      modelName,
      prompt.map { it.toInternal() },
      safetySettings
        ?.also { safetySettingList ->
          if (
            generativeBackend.backend == GenerativeBackendEnum.GOOGLE_AI &&
              safetySettingList.any { it.method != null }
          ) {
            throw InvalidStateException(
              "HarmBlockMethod is unsupported by the Google Developer API"
            )
          }
        }
        ?.map { it.toInternal() },
      (overrideConfig ?: generationConfig)?.toInternal(),
      tools?.map { it.toInternal() },
      toolConfig?.toInternal(),
      systemInstruction?.copy(role = "system")?.toInternal(),
    )

  private fun buildCountTokensRequest(prompt: List<Content>) =
    when (generativeBackend.backend) {
      GenerativeBackendEnum.GOOGLE_AI ->
        CountTokensRequest.forGoogleAI(buildGenerateContentRequest(prompt))
      GenerativeBackendEnum.VERTEX_AI ->
        CountTokensRequest.forVertexAI(buildGenerateContentRequest(prompt))
    }

  private fun GenerateContentResponse.validate() = apply {
    if (candidates.isEmpty() && promptFeedback == null) {
      throw SerializationException("Error deserializing response, found no valid fields")
    }
    promptFeedback?.blockReason?.let { throw PromptBlockedException(this) }
    candidates
      .mapNotNull { it.finishReason }
      .firstOrNull { it != FinishReason.STOP }
      ?.let { throw ResponseStoppedException(this) }
  }
}
