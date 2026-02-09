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

internal class OnDeviceModelProvider(
  private val onDeviceModel: OnDeviceGenerativeModel,
  private val onDeviceConfig: OnDeviceConfig
) : GenerativeModelProvider {

  override suspend fun generateContent(prompt: List<Content>): GenerateContentResponse {
    if (!onDeviceModel.isAvailable()) {
      throw FirebaseAIException.from(
        FirebaseAIOnDeviceNotAvailableException("On-device model is not available")
      )
    }

    val request = buildOnDeviceGenerateContentRequest(prompt)

    return try {
      val response = onDeviceModel.generateContent(request)
      GenerateContentResponse(
        response.candidates.map { Candidate.fromInterop(it) },
        InferenceSource.ON_DEVICE,
        null,
        null
      )
    } catch (e: Throwable) {
      throw FirebaseAIException.from(e)
    }
  }

  override suspend fun countTokens(prompt: List<Content>): CountTokensResponse {
    if (!onDeviceModel.isAvailable()) {
      throw FirebaseAIException.from(
        FirebaseAIOnDeviceNotAvailableException("On-device model is not available")
      )
    }

    val request = buildOnDeviceGenerateContentRequest(prompt)

    return try {
      val response = onDeviceModel.countTokens(request)
      CountTokensResponse(response.totalTokens)
    } catch (e: Throwable) {
      throw FirebaseAIException.from(e)
    }
  }

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

  override suspend fun <T : Any> generateObject(
    jsonSchema: JsonSchema<T>,
    prompt: List<Content>
  ): GenerateObjectResponse<T> {
    throw FirebaseAIException.from(
      IllegalArgumentException("On-device mode is not supported for `generateObject`")
    )
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
    private val TAG = OnDeviceModelProvider::class.java.simpleName
  }
}
