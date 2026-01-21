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
import com.google.firebase.ai.ondevice.interop.FirebaseAiOnDeviceInvalidRequestException
import com.google.firebase.ai.ondevice.interop.GenerateContentResponse
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart

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
  val interop = this
  try {
    return generateContentRequest(text) {
      temperature = interop.temperature
      maxOutputTokens = interop.maxOutputTokens
      candidateCount = interop.candidateCount
      topK = interop.topK
      seed = interop.seed
    }
  } catch (e: IllegalArgumentException) {
    throw FirebaseAiOnDeviceInvalidRequestException(e)
  }
}

internal fun com.google.mlkit.genai.prompt.GenerateContentResponse.toInterop():
  GenerateContentResponse = GenerateContentResponse(candidates.map { it.toInterop() })

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
