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
import com.google.firebase.ai.ondevice.interop.FirebaseAIOnDeviceNotAvailableException
import com.google.firebase.ai.ondevice.interop.GenerateContentRequest as OnDeviceGenerateContentRequest
import com.google.firebase.ai.ondevice.interop.GenerativeModel as OnDeviceGenerativeModel
import com.google.firebase.ai.ondevice.interop.ImagePart as OnDeviceImagePart
import com.google.firebase.ai.ondevice.interop.TextPart as OnDeviceTextPart
import com.google.firebase.ai.type.Candidate
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.CountTokensResponse
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.GenerateObjectResponse
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.JsonSchema
import com.google.firebase.ai.type.TextPart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Implementation of [GenerativeModelProvider] that delegates to an on-device generative model.
 *
 * This provider handles the conversion between Firebase AI types and the underlying on-device model
 * types, as well as error handling and availability checks.
 *
 * @property onDeviceModel The underlying on-device model to use for generation.
 * @property onDeviceConfig Configuration options for the on-device model.
 */
internal class OnDeviceGenerativeModelProvider(
  private val onDeviceModel: OnDeviceGenerativeModel,
  private val onDeviceConfig: OnDeviceConfig
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
      GenerateContentResponse(
        response.candidates.map { Candidate.fromInterop(it) },
        InferenceSource.ON_DEVICE,
        null,
        null
      )
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
      if (!onDeviceModel.isAvailable()) {
        throw FirebaseAIException.from(
          FirebaseAIOnDeviceNotAvailableException("On-device model is not available")
        )
      }

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
    if (!onDeviceModel.isAvailable()) {
      throw FirebaseAIException.from(
        FirebaseAIOnDeviceNotAvailableException("On-device model is not available")
      )
    }

    val request = buildOnDeviceGenerateContentRequest(prompt)

    emitAll(
      onDeviceModel
        .generateContentStream(request)
        .catch { throw FirebaseAIException.from(it) }
        .map {
          GenerateContentResponse(
            it.candidates.map { candidate -> Candidate.fromInterop(candidate) },
            InferenceSource.ON_DEVICE,
            null,
            null
          )
        }
    )
  }

  /**
   * Generates a structured object based on the given prompt and schema.
   *
   * Note: This is currently not supported for on-device models.
   *
   * @param jsonSchema The schema defining the structure of the output.
   * @param prompt The list of content parts to use as the prompt.
   * @return The generated object response.
   * @throws FirebaseAIException Always throws as this feature is not supported.
   */
  override suspend fun <T : Any> generateObject(
    jsonSchema: JsonSchema<T>,
    prompt: List<Content>
  ): GenerateObjectResponse<T> {
    throw FirebaseAIException.from(
      IllegalArgumentException("On-device mode is not supported for `generateObject`")
    )
  }

  /**
   * Warms up the on-device model to reduce latency for the first request.
   *
   * @throws FirebaseAIException If the on-device model is unavailable or if warmup fails.
   */
  override suspend fun warmup() {
    withFirebaseAIExceptionHandling {
      ensureOnDeviceModelAvailable()
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

  private suspend fun ensureOnDeviceModelAvailable() {
    if (!onDeviceModel.isAvailable()) {
      throw FirebaseAIException.from(
        FirebaseAIOnDeviceNotAvailableException("On-device model is not available")
      )
    }
  }

  private fun buildOnDeviceGenerateContentRequest(
    prompt: List<Content>
  ): OnDeviceGenerateContentRequest {
    if (prompt.isEmpty()) {
      throw FirebaseAIException.from(IllegalArgumentException("Prompt is empty"))
    }
    val parts =
      if (prompt.size == 1) {
        prompt.first().parts
      } else {
        Log.w(TAG, "On-device model does not support multiple prompts, concatenating them instead")
        prompt.flatMap { it.parts }
      }
    val textParts =
      parts.filterIsInstance<TextPart>().also {
        if (it.size > 1)
          Log.w(
            TAG,
            "On-device model does not support multiple text parts, concatenating them instead"
          )
      }
    if (textParts.isEmpty()) {
      throw FirebaseAIException.from(
        IllegalArgumentException("On-device model requires text as part of the prompt")
      )
    }
    val text = textParts.joinToString("") { it.text }
    val image =
      parts
        .filterIsInstance<ImagePart>()
        .also {
          if (it.size > 1)
            Log.w(
              TAG,
              "On-device model does not support multiple image parts, using only the first one"
            )
        }
        .firstOrNull()
    return OnDeviceGenerateContentRequest(
      text = OnDeviceTextPart(text),
      image = image?.let { OnDeviceImagePart(it.image) },
      temperature = onDeviceConfig.temperature,
      topK = onDeviceConfig.topK,
      seed = onDeviceConfig.seed,
      candidateCount = onDeviceConfig.candidateCount,
      maxOutputTokens = onDeviceConfig.maxOutputTokens
    )
  }

  private companion object {
    private val TAG = OnDeviceGenerativeModelProvider::class.java.simpleName
  }
}
