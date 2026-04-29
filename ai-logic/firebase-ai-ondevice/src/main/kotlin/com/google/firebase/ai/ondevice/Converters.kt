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

package com.google.firebase.ai.ondevice

import com.google.firebase.ai.ondevice.interop.Candidate
import com.google.firebase.ai.ondevice.interop.CountTokensResponse
import com.google.firebase.ai.ondevice.interop.FinishReason
import com.google.firebase.ai.ondevice.interop.FirebaseAIOnDeviceInvalidRequestException
import com.google.firebase.ai.ondevice.interop.GenerateContentResponse
import com.google.firebase.ai.ondevice.interop.GenerationConfig
import com.google.firebase.ai.ondevice.interop.ModelConfig
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.ModelPreference
import com.google.mlkit.genai.prompt.ModelReleaseStage
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generationConfig
import com.google.mlkit.genai.prompt.modelConfig

// ====================================
// `Part` converter extension functions
// ====================================
internal fun com.google.firebase.ai.ondevice.interop.TextPart.toMlKit(): TextPart = TextPart(text)

internal fun com.google.firebase.ai.ondevice.interop.ImagePart.toMlKit(): ImagePart =
  ImagePart(bitmap)

// ============================================
// `CountTokens*` converter extension functions
// ============================================
internal fun com.google.mlkit.genai.prompt.CountTokensResponse.toInterop(): CountTokensResponse =
  CountTokensResponse(totalTokens)

// =========================================
// `Candidate` converter extension functions
// =========================================
internal fun com.google.mlkit.genai.prompt.Candidate.toInterop(): Candidate =
  Candidate(text, finishReason?.toInterop() ?: FinishReason.OTHER)

private fun Int.toInterop(): FinishReason =
  when (this) {
    com.google.mlkit.genai.prompt.Candidate.FinishReason.STOP -> FinishReason.STOP
    com.google.mlkit.genai.prompt.Candidate.FinishReason.MAX_TOKENS -> FinishReason.MAX_TOKENS
    else -> FinishReason.OTHER
  }

// ================================================
// `GenerateContent*` converter extension functions
// ================================================
internal fun com.google.firebase.ai.ondevice.interop.GenerateContentRequest.toMlKit():
  GenerateContentRequest {
  try {
    return generateContentRequest(text, image) {
      temperature = this@toMlKit.temperature
      maxOutputTokens = this@toMlKit.maxOutputTokens
      candidateCount = this@toMlKit.candidateCount
      topK = this@toMlKit.topK
      seed = this@toMlKit.seed
    }
  } catch (e: IllegalArgumentException) {
    throw FirebaseAIOnDeviceInvalidRequestException(e)
  }
}

internal fun com.google.mlkit.genai.prompt.GenerateContentResponse.toInterop():
  GenerateContentResponse = GenerateContentResponse(candidates.map { it.toInterop() })

// ================================================
// `GenerationConfig` converter extension functions
// ================================================
internal fun GenerationConfig.toMlKit(): com.google.mlkit.genai.prompt.GenerationConfig =
  generationConfig {
    this@toMlKit.modelConfig?.let { modelConfig = it.toMlKit() }
  }

// ===========================================
// `ModelConfig` converter extension functions
// ===========================================
internal fun ModelConfig.toMlKit(): com.google.mlkit.genai.prompt.ModelConfig = modelConfig {
  releaseStage = this@toMlKit.releaseStage.toMlKit()
  preference = this@toMlKit.preference.toMlKit()
}

private fun com.google.firebase.ai.ondevice.interop.ModelReleaseStage.toMlKit(): Int =
  when (this) {
    com.google.firebase.ai.ondevice.interop.ModelReleaseStage.PREVIEW -> ModelReleaseStage.PREVIEW
    com.google.firebase.ai.ondevice.interop.ModelReleaseStage.STABLE -> ModelReleaseStage.STABLE
  }

private fun com.google.firebase.ai.ondevice.interop.ModelPreference.toMlKit(): Int =
  when (this) {
    com.google.firebase.ai.ondevice.interop.ModelPreference.FULL -> ModelPreference.FULL
    com.google.firebase.ai.ondevice.interop.ModelPreference.FAST -> ModelPreference.FAST
  }

private fun generateContentRequest(
  text: com.google.firebase.ai.ondevice.interop.TextPart,
  image: com.google.firebase.ai.ondevice.interop.ImagePart? = null,
  init: GenerateContentRequest.Builder.() -> Unit
): GenerateContentRequest {
  val builder =
    if (image == null) {
      GenerateContentRequest.builder(text.toMlKit())
    } else {
      GenerateContentRequest.builder(text = text.toMlKit(), image = image.toMlKit())
    }
  builder.init()
  return builder.build()
}
