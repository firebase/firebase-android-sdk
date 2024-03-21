/*
 * Copyright 2023 Google LLC
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

package com.google.firebase.vertex.internal.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.google.firebase.vertex.type.BlobPart
import com.google.firebase.vertex.type.BlockReason
import com.google.firebase.vertex.type.BlockThreshold
import com.google.firebase.vertex.type.Candidate
import com.google.firebase.vertex.type.CitationMetadata
import com.google.firebase.vertex.type.Content
import com.google.firebase.vertex.type.CountTokensResponse
import com.google.firebase.vertex.type.FinishReason
import com.google.firebase.vertex.type.GenerateContentResponse
import com.google.firebase.vertex.type.GenerationConfig
import com.google.firebase.vertex.type.HarmCategory
import com.google.firebase.vertex.type.HarmProbability
import com.google.firebase.vertex.type.ImagePart
import com.google.firebase.vertex.type.Part
import com.google.firebase.vertex.type.PromptFeedback
import com.google.firebase.vertex.type.RequestOptions
import com.google.firebase.vertex.type.SafetyRating
import com.google.firebase.vertex.type.SafetySetting
import com.google.firebase.vertex.type.SerializationException
import com.google.firebase.vertex.type.TextPart
import com.google.firebase.vertex.type.content
import java.io.ByteArrayOutputStream

private const val BASE_64_FLAGS = Base64.NO_WRAP

// TODO(rlazo): Add missing parameters
internal fun RequestOptions.toInternal() =
  com.google.ai.client.generativeai.common.RequestOptions(
    timeout,
    apiVersion,
    endpoint
  )

internal fun Content.toInternal() =
  com.google.ai.client.generativeai.common.shared.Content(
    this.role ?: "user",
    this.parts.map { it.toInternal() }
  )

internal fun Part.toInternal(): com.google.ai.client.generativeai.common.shared.Part {
  return when (this) {
    is TextPart -> com.google.ai.client.generativeai.common.shared.TextPart(text)
    is ImagePart ->
      com.google.ai.client.generativeai.common.shared.BlobPart(
        com.google.ai.client.generativeai.common.shared.Blob(
          "image/jpeg",
          encodeBitmapToBase64Png(image)
        )
      )
    is BlobPart ->
      com.google.ai.client.generativeai.common.shared.BlobPart(
        com.google.ai.client.generativeai.common.shared.Blob(
          mimeType,
          Base64.encodeToString(blob, BASE_64_FLAGS)
        )
      )
    else ->
      throw SerializationException(
        "The given subclass of Part (${javaClass.simpleName}) is not supported in the serialization yet."
      )
  }
}

internal fun SafetySetting.toInternal() =
  com.google.ai.client.generativeai.common.shared.SafetySetting(
    harmCategory.toInternal(),
    threshold.toInternal()
  )

internal fun GenerationConfig.toInternal() =
  com.google.ai.client.generativeai.common.client.GenerationConfig(
    temperature = temperature,
    topP = topP,
    topK = topK,
    candidateCount = candidateCount,
    maxOutputTokens = maxOutputTokens,
    stopSequences = stopSequences
  )

internal fun com.google.firebase.vertex.type.HarmCategory.toInternal() =
  when (this) {
    HarmCategory.HARASSMENT ->
      com.google.ai.client.generativeai.common.shared.HarmCategory.HARASSMENT
    HarmCategory.HATE_SPEECH ->
      com.google.ai.client.generativeai.common.shared.HarmCategory.HATE_SPEECH
    HarmCategory.SEXUALLY_EXPLICIT ->
      com.google.ai.client.generativeai.common.shared.HarmCategory.SEXUALLY_EXPLICIT
    HarmCategory.DANGEROUS_CONTENT ->
      com.google.ai.client.generativeai.common.shared.HarmCategory.DANGEROUS_CONTENT
    HarmCategory.UNKNOWN -> com.google.ai.client.generativeai.common.shared.HarmCategory.UNKNOWN
  }

internal fun BlockThreshold.toInternal() =
  when (this) {
    BlockThreshold.NONE ->
      com.google.ai.client.generativeai.common.shared.HarmBlockThreshold.BLOCK_NONE
    BlockThreshold.ONLY_HIGH ->
      com.google.ai.client.generativeai.common.shared.HarmBlockThreshold.BLOCK_ONLY_HIGH
    BlockThreshold.MEDIUM_AND_ABOVE ->
      com.google.ai.client.generativeai.common.shared.HarmBlockThreshold.BLOCK_MEDIUM_AND_ABOVE
    BlockThreshold.LOW_AND_ABOVE ->
      com.google.ai.client.generativeai.common.shared.HarmBlockThreshold.BLOCK_LOW_AND_ABOVE
    BlockThreshold.UNSPECIFIED ->
      com.google.ai.client.generativeai.common.shared.HarmBlockThreshold.UNSPECIFIED
  }

internal fun com.google.ai.client.generativeai.common.server.Candidate.toPublic(): Candidate {
  val safetyRatings = safetyRatings?.map { it.toPublic() }.orEmpty()
  val citations = citationMetadata?.citationSources?.map { it.toPublic() }.orEmpty()
  val finishReason = finishReason.toPublic()

  return Candidate(
    this.content?.toPublic() ?: content("model") {},
    safetyRatings,
    citations,
    finishReason
  )
}

internal fun com.google.ai.client.generativeai.common.shared.Content.toPublic(): Content =
  Content(role, parts.map { it.toPublic() })

internal fun com.google.ai.client.generativeai.common.shared.Part.toPublic(): Part {
  return when (this) {
    is com.google.ai.client.generativeai.common.shared.TextPart -> TextPart(text)
    is com.google.ai.client.generativeai.common.shared.BlobPart -> {
      val data = Base64.decode(inlineData.data, BASE_64_FLAGS)
      if (inlineData.mimeType.contains("image")) {
        ImagePart(decodeBitmapFromImage(data))
      } else {
        BlobPart(inlineData.mimeType, data)
      }
    }
  }
}

internal fun com.google.ai.client.generativeai.common.server.CitationSources.toPublic() =
  CitationMetadata(startIndex = startIndex, endIndex = endIndex, uri = uri, license = license)

internal fun com.google.ai.client.generativeai.common.server.SafetyRating.toPublic() =
  SafetyRating(category.toPublic(), probability.toPublic())

internal fun com.google.ai.client.generativeai.common.server.PromptFeedback.toPublic():
  PromptFeedback {
  val safetyRatings = safetyRatings?.map { it.toPublic() }.orEmpty()
  return com.google.firebase.vertex.type.PromptFeedback(
    blockReason?.toPublic(),
    safetyRatings,
  )
}

internal fun com.google.ai.client.generativeai.common.server.FinishReason?.toPublic() =
  when (this) {
    null -> null
    com.google.ai.client.generativeai.common.server.FinishReason.MAX_TOKENS ->
      FinishReason.MAX_TOKENS
    com.google.ai.client.generativeai.common.server.FinishReason.RECITATION ->
      FinishReason.RECITATION
    com.google.ai.client.generativeai.common.server.FinishReason.SAFETY -> FinishReason.SAFETY
    com.google.ai.client.generativeai.common.server.FinishReason.STOP -> FinishReason.STOP
    com.google.ai.client.generativeai.common.server.FinishReason.OTHER -> FinishReason.OTHER
    com.google.ai.client.generativeai.common.server.FinishReason.UNSPECIFIED ->
      FinishReason.UNSPECIFIED
    com.google.ai.client.generativeai.common.server.FinishReason.UNKNOWN -> FinishReason.UNKNOWN
  }

internal fun com.google.ai.client.generativeai.common.shared.HarmCategory.toPublic() =
  when (this) {
    com.google.ai.client.generativeai.common.shared.HarmCategory.HARASSMENT ->
      HarmCategory.HARASSMENT
    com.google.ai.client.generativeai.common.shared.HarmCategory.HATE_SPEECH ->
      HarmCategory.HATE_SPEECH
    com.google.ai.client.generativeai.common.shared.HarmCategory.SEXUALLY_EXPLICIT ->
      HarmCategory.SEXUALLY_EXPLICIT
    com.google.ai.client.generativeai.common.shared.HarmCategory.DANGEROUS_CONTENT ->
      HarmCategory.DANGEROUS_CONTENT
    com.google.ai.client.generativeai.common.shared.HarmCategory.UNKNOWN -> HarmCategory.UNKNOWN
  }

internal fun com.google.ai.client.generativeai.common.server.HarmProbability.toPublic() =
  when (this) {
    com.google.ai.client.generativeai.common.server.HarmProbability.HIGH -> HarmProbability.HIGH
    com.google.ai.client.generativeai.common.server.HarmProbability.MEDIUM -> HarmProbability.MEDIUM
    com.google.ai.client.generativeai.common.server.HarmProbability.LOW -> HarmProbability.LOW
    com.google.ai.client.generativeai.common.server.HarmProbability.NEGLIGIBLE ->
      HarmProbability.NEGLIGIBLE
    com.google.ai.client.generativeai.common.server.HarmProbability.UNSPECIFIED ->
      HarmProbability.UNSPECIFIED
    com.google.ai.client.generativeai.common.server.HarmProbability.UNKNOWN ->
      HarmProbability.UNKNOWN
  }

internal fun com.google.ai.client.generativeai.common.server.BlockReason.toPublic() =
  when (this) {
    com.google.ai.client.generativeai.common.server.BlockReason.UNSPECIFIED ->
      BlockReason.UNSPECIFIED
    com.google.ai.client.generativeai.common.server.BlockReason.SAFETY -> BlockReason.SAFETY
    com.google.ai.client.generativeai.common.server.BlockReason.OTHER -> BlockReason.OTHER
    com.google.ai.client.generativeai.common.server.BlockReason.UNKNOWN -> BlockReason.UNKNOWN
  }

internal fun com.google.ai.client.generativeai.common.GenerateContentResponse.toPublic():
  GenerateContentResponse {
  return GenerateContentResponse(
    candidates?.map { it.toPublic() }.orEmpty(),
    promptFeedback?.toPublic()
  )
}

internal fun com.google.ai.client.generativeai.common.CountTokensResponse.toPublic() =
  CountTokensResponse(totalTokens)

private fun encodeBitmapToBase64Png(input: Bitmap): String {
  ByteArrayOutputStream().let {
    input.compress(Bitmap.CompressFormat.JPEG, 80, it)
    return Base64.encodeToString(it.toByteArray(), BASE_64_FLAGS)
  }
}

private fun decodeBitmapFromImage(input: ByteArray) =
  BitmapFactory.decodeByteArray(input, 0, input.size)
