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

import android.util.Log
import com.google.firebase.ai.InferenceSource
import com.google.firebase.ai.OnDeviceConfig
import com.google.firebase.ai.ondevice.interop.FirebaseAIOnDeviceInvalidRequestException
import com.google.firebase.ai.ondevice.interop.FirebaseAIOnDeviceNotAvailableException
import com.google.firebase.ai.ondevice.interop.FunctionCallPart as OnDeviceFunctionCallPart
import com.google.firebase.ai.ondevice.interop.FunctionDeclaration as OnDeviceFunctionDeclaration
import com.google.firebase.ai.ondevice.interop.FunctionResponsePart as OnDeviceFunctionResponsePart
import com.google.firebase.ai.ondevice.interop.FunctionSchema as OnDeviceFunctionSchema
import com.google.firebase.ai.ondevice.interop.GenerateContentRequest as OnDeviceGenerateContentRequest
import com.google.firebase.ai.ondevice.interop.GenerativeModel as OnDeviceGenerativeModel
import com.google.firebase.ai.ondevice.interop.ImagePart as OnDeviceImagePart
import com.google.firebase.ai.ondevice.interop.TextPart as OnDeviceTextPart
import com.google.firebase.ai.type.AutoFunctionDeclaration
import com.google.firebase.ai.type.Candidate
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.CountTokensResponse
import com.google.firebase.ai.type.FinishReason
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.GenerateObjectResponse
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.JsonSchema
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.TextPart
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Implementation of [GenerativeModelProvider] that delegates to an on-device generative model.
 *
 * This provider handles the conversion between Firebase AI types and the underlying on-device model
 * types, as well as error handling and availability checks.
 *
 * @property onDeviceModel The underlying on-device model to use for generation.
 * @property onDeviceConfig Configuration options for the on-device model.
 * @property systemInstruction Optional instructions that direct the model to behave a certain way.
 * @property tools Optional list of tools provided to the model.
 */
@PublicPreviewAPI
internal class OnDeviceGenerativeModelProvider(
  private val onDeviceModel: OnDeviceGenerativeModel,
  private val onDeviceConfig: OnDeviceConfig,
  internal val systemInstruction: Content? = null,
  internal val tools: List<Tool> = emptyList()
) : GenerativeModelProvider {

  /**
   * Generates content based on the given prompt.
   *
   * @param prompt The list of content parts to use as the prompt.
   * @return The generated response.
   * @throws FirebaseAIException If the on-device model is unavailable or if generation fails.
   */
  override suspend fun generateContent(prompt: List<Content>): GenerateContentResponse =
    withFirebaseAIExceptionHandling {
      ensureOnDeviceModelAvailable()

      val request = buildOnDeviceGenerateContentRequest(prompt)

      val response = onDeviceModel.generateContent(request)
      toPublicResponse(response)
    }

  /**
   * Counts the number of tokens in the given prompt.
   *
   * @param prompt The list of content parts to count tokens for.
   * @return The count of tokens.
   * @throws FirebaseAIException If the on-device model is unavailable or if counting tokens fails.
   */
  override suspend fun countTokens(prompt: List<Content>): CountTokensResponse =
    withFirebaseAIExceptionHandling {
      ensureOnDeviceModelAvailable(checkThinkingMode = false, checkFunctionCalling = false)

      val request = buildOnDeviceGenerateContentRequest(prompt)

      val response = onDeviceModel.countTokens(request)
      CountTokensResponse(response.totalTokens)
    }

  /**
   * Generates a stream of content based on the given prompt.
   *
   * @param prompt The list of content parts to use as the prompt.
   * @return A flow of generated responses.
   */
  override fun generateContentStream(prompt: List<Content>): Flow<GenerateContentResponse> = flow {
    val request =
      try {
        ensureOnDeviceModelAvailable()
        buildOnDeviceGenerateContentRequest(prompt)
      } catch (e: Throwable) {
        throw FirebaseAIException.from(e)
      }

    emitAll(
      onDeviceModel
        .generateContentStream(request)
        .catch { throw FirebaseAIException.from(it) }
        .map { toPublicResponse(it) }
    )
  }

  /**
   * Generates a structured object based on the given prompt and schema.
   *
   * @param jsonSchema The schema defining the structure of the output.
   * @param prompt The list of content parts to use as the prompt.
   * @return The generated object response.
   * @throws FirebaseAIException If the on-device model is unavailable or if generation fails.
   */
  override suspend fun <T : Any> generateObject(
    jsonSchema: JsonSchema<T>,
    prompt: List<Content>
  ): GenerateObjectResponse<T> = withFirebaseAIExceptionHandling {
    ensureOnDeviceModelAvailable(checkFunctionCalling = false)

    val request = buildOnDeviceGenerateContentRequest(prompt)
    val interopResponse = onDeviceModel.generateObject(request, jsonSchema.clazz)
    val candidates =
      interopResponse.instances.map {
        Candidate(
          content = content { text("") },
          safetyRatings = emptyList(),
          citationMetadata = null,
          finishReason = FinishReason.STOP,
          finishMessage = null,
          groundingMetadata = null,
          urlContextMetadata = null
        )
      }
    @Suppress("UNCHECKED_CAST")
    GenerateObjectResponse(
      GenerateContentResponse(candidates, InferenceSource.ON_DEVICE, null, null, "ondevice"),
      instances = ArrayList(interopResponse.instances as List<T?>)
    )
  }

  /**
   * Warms up the on-device model to reduce latency for the first request.
   *
   * @throws FirebaseAIException If the on-device model is unavailable or if warmup fails.
   */
  override suspend fun warmUp() {
    withFirebaseAIExceptionHandling {
      ensureOnDeviceModelAvailable(checkThinkingMode = false, checkFunctionCalling = false)
      onDeviceModel.warmup()
    }
  }

  private suspend fun <T> withFirebaseAIExceptionHandling(block: suspend () -> T): T {
    try {
      return block()
    } catch (e: Throwable) {
      throw FirebaseAIException.from(e)
    }
  }

  private suspend fun ensureOnDeviceModelAvailable(
    checkThinkingMode: Boolean = true,
    checkFunctionCalling: Boolean = true
  ) {
    if (!onDeviceModel.isAvailable()) {
      throw FirebaseAIException.from(
        FirebaseAIOnDeviceNotAvailableException("On-device model is not available")
      )
    }
    if (
      checkThinkingMode &&
        onDeviceConfig.enableThinking == true &&
        !onDeviceModel.isThinkingModeAvailable()
    ) {
      throw FirebaseAIException.from(
        FirebaseAIOnDeviceNotAvailableException(
          "Thinking mode is not available on the on-device model"
        )
      )
    }
    val effectiveTools = onDeviceConfig.tools ?: tools
    if (checkFunctionCalling && effectiveTools.isNotEmpty()) {
      val hasNonFunctionTools =
        effectiveTools.any {
          it.googleSearch != null ||
            it.codeExecution != null ||
            it.urlContext != null ||
            it.googleMaps != null
        }
      if (hasNonFunctionTools) {
        throw FirebaseAIException.from(
          FirebaseAIOnDeviceNotAvailableException(
            "Tools other than function calling are not supported on the on-device model"
          )
        )
      }
      val hasFunctionDeclarations =
        effectiveTools.any {
          !it.functionDeclarations.isNullOrEmpty() || !it.autoFunctionDeclarations.isNullOrEmpty()
        }
      if (hasFunctionDeclarations && !onDeviceModel.isFunctionCallingAvailable()) {
        throw FirebaseAIException.from(
          FirebaseAIOnDeviceNotAvailableException(
            "Function calling is not available on the on-device model"
          )
        )
      }
    }
  }

  private fun toPublicResponse(
    response: com.google.firebase.ai.ondevice.interop.GenerateContentResponse
  ): GenerateContentResponse {
    val candidates =
      if (response.candidates.isNotEmpty()) {
        response.candidates.map { candidate ->
          Candidate.fromInterop(candidate, response.thoughtProcess)
        }
      } else if (response.thoughtProcess.isNotEmpty()) {
        listOf(Candidate.fromInteropThought(response.thoughtProcess))
      } else {
        emptyList()
      }
    return GenerateContentResponse(
      candidates,
      InferenceSource.ON_DEVICE,
      null,
      null,
      response.modelVersion
    )
  }

  private fun buildOnDeviceGenerateContentRequest(
    prompt: List<Content>
  ): OnDeviceGenerateContentRequest {
    if (prompt.isEmpty()) {
      throw FirebaseAIException.from(
        FirebaseAIOnDeviceInvalidRequestException(IllegalArgumentException("Prompt is empty"))
      )
    }
    val parts =
      if (prompt.size == 1) {
        prompt.first().parts
      } else {
        Log.w(TAG, "On-device model does not support multiple prompts, concatenating them instead")
        prompt.flatMap { it.parts }
      }
    if (parts.isEmpty()) {
      throw FirebaseAIException.from(
        FirebaseAIOnDeviceInvalidRequestException(IllegalArgumentException("Prompt is empty"))
      )
    }
    val onDeviceParts =
      parts.map { part ->
        when (part) {
          is TextPart -> OnDeviceTextPart(part.text)
          is ImagePart -> OnDeviceImagePart(part.image)
          is FunctionCallPart ->
            OnDeviceFunctionCallPart(
              name = part.name,
              args = part.args.mapValues { it.value.toAny() },
              id = part.id
            )
          is FunctionResponsePart ->
            OnDeviceFunctionResponsePart(
              name = part.name,
              response = part.response.entries.associate { it.key to it.value.toAny() },
              id = part.id
            )
          else ->
            throw FirebaseAIException.from(
              FirebaseAIOnDeviceInvalidRequestException(
                IllegalArgumentException(
                  "On-device model does not support part type: ${part::class.java.simpleName}"
                )
              )
            )
        }
      }
    val onDeviceSystemInstruction =
      systemInstruction?.let { instruction ->
        val systemTextParts = instruction.parts.filterIsInstance<TextPart>()
        if (systemTextParts.size < instruction.parts.size) {
          val unsupportedSystemParts = instruction.parts.filter { it !is TextPart }
          throw FirebaseAIException.from(
            FirebaseAIOnDeviceInvalidRequestException(
              IllegalArgumentException(
                "On-device model does not support non-text parts in systemInstruction: ${unsupportedSystemParts.map { it::class.java.simpleName }}"
              )
            )
          )
        }
        if (systemTextParts.size > 1) {
          Log.w(
            TAG,
            "On-device model does not support multiple text parts in systemInstruction, concatenating them instead"
          )
        }
        if (systemTextParts.isEmpty()) null
        else OnDeviceTextPart(systemTextParts.joinToString("\n") { it.text })
      }
    val effectiveTools = onDeviceConfig.tools ?: tools
    val onDeviceTools =
      if (effectiveTools.isNotEmpty()) {
        effectiveTools
          .flatMap { tool ->
            val normal = tool.functionDeclarations?.map { it.toInterop() } ?: emptyList()
            val auto = tool.autoFunctionDeclarations?.map { it.toInterop() } ?: emptyList()
            normal + auto
          }
          .ifEmpty { null }
      } else {
        null
      }
    return OnDeviceGenerateContentRequest(
      parts = onDeviceParts,
      temperature = onDeviceConfig.temperature,
      topK = onDeviceConfig.topK,
      seed = onDeviceConfig.seed,
      candidateCount = onDeviceConfig.candidateCount,
      maxOutputTokens = onDeviceConfig.maxOutputTokens,
      systemInstruction = onDeviceSystemInstruction,
      enableThinking = onDeviceConfig.enableThinking,
      tools = onDeviceTools
    )
  }

  private companion object {
    private val TAG = OnDeviceGenerativeModelProvider::class.java.simpleName
  }
}

private fun JsonElement.toAny(): Any? =
  when (this) {
    is JsonNull -> null
    is JsonPrimitive -> {
      if (isString) content else booleanOrNull ?: intOrNull ?: longOrNull ?: doubleOrNull ?: content
    }
    is JsonArray -> map { it.toAny() }
    is JsonObject -> entries.associate { it.key to it.value.toAny() }
  }

private fun Schema.toInterop(): OnDeviceFunctionSchema =
  OnDeviceFunctionSchema(
    type = type,
    description = description,
    properties = properties?.mapValues { it.value.toInterop() },
    required = required,
    enum = enum,
    nullable = nullable,
    items = items?.toInterop()
  )

private fun FunctionDeclaration.toInterop(): OnDeviceFunctionDeclaration =
  OnDeviceFunctionDeclaration(
    name = name,
    description = description,
    parameters = schema.toInterop()
  )

private fun JsonSchema<*>.toInterop(): OnDeviceFunctionSchema =
  OnDeviceFunctionSchema(
    type = type,
    description = description,
    properties = properties?.mapValues { it.value.toInterop() },
    required = required,
    enum = enum,
    nullable = nullable,
    items = items?.toInterop()
  )

private fun AutoFunctionDeclaration<*, *>.toInterop(): OnDeviceFunctionDeclaration =
  OnDeviceFunctionDeclaration(
    name = name,
    description = description,
    parameters = inputSchema.toInterop()
  )
