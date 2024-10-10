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

package com.google.firebase.vertexai.internal.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.google.firebase.vertexai.common.client.Schema
import com.google.firebase.vertexai.common.shared.FileData
import com.google.firebase.vertexai.common.shared.FunctionCall
import com.google.firebase.vertexai.common.shared.FunctionCallPart
import com.google.firebase.vertexai.common.shared.FunctionResponse
import com.google.firebase.vertexai.common.shared.FunctionResponsePart
import com.google.firebase.vertexai.common.shared.InlineData
import com.google.firebase.vertexai.type.BlockReason
import com.google.firebase.vertexai.type.Candidate
import com.google.firebase.vertexai.type.Citation
import com.google.firebase.vertexai.type.CitationMetadata
import com.google.firebase.vertexai.type.Content
import com.google.firebase.vertexai.type.CountTokensResponse
import com.google.firebase.vertexai.type.FileDataPart
import com.google.firebase.vertexai.type.FinishReason
import com.google.firebase.vertexai.type.FunctionCallingConfig
import com.google.firebase.vertexai.type.FunctionDeclaration
import com.google.firebase.vertexai.type.GenerateContentResponse
import com.google.firebase.vertexai.type.GenerationConfig
import com.google.firebase.vertexai.type.HarmBlockMethod
import com.google.firebase.vertexai.type.HarmBlockThreshold
import com.google.firebase.vertexai.type.HarmCategory
import com.google.firebase.vertexai.type.HarmProbability
import com.google.firebase.vertexai.type.HarmSeverity
import com.google.firebase.vertexai.type.ImagePart
import com.google.firebase.vertexai.type.InlineDataPart
import com.google.firebase.vertexai.type.Part
import com.google.firebase.vertexai.type.PromptFeedback
import com.google.firebase.vertexai.type.SafetyRating
import com.google.firebase.vertexai.type.SafetySetting
import com.google.firebase.vertexai.type.SerializationException
import com.google.firebase.vertexai.type.TextPart
import com.google.firebase.vertexai.type.Tool
import com.google.firebase.vertexai.type.ToolConfig
import com.google.firebase.vertexai.type.UsageMetadata
import com.google.firebase.vertexai.type.content
import java.io.ByteArrayOutputStream
import java.util.Calendar
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import org.json.JSONObject

private const val BASE_64_FLAGS = Base64.NO_WRAP

internal fun Content.toInternal() =
  com.google.firebase.vertexai.common.shared.Content(
    this.role ?: "user",
    this.parts.map { it.toInternal() }
  )

internal fun Part.toInternal(): com.google.firebase.vertexai.common.shared.Part {
  return when (this) {
    is TextPart -> com.google.firebase.vertexai.common.shared.TextPart(text)
    is ImagePart ->
      com.google.firebase.vertexai.common.shared.InlineDataPart(
        InlineData("image/jpeg", encodeBitmapToBase64Png(image))
      )
    is InlineDataPart ->
      com.google.firebase.vertexai.common.shared.InlineDataPart(
        InlineData(mimeType, Base64.encodeToString(inlineData, BASE_64_FLAGS))
      )
    is com.google.firebase.vertexai.type.FunctionCallPart ->
      FunctionCallPart(FunctionCall(name, args))
    is com.google.firebase.vertexai.type.FunctionResponsePart ->
      FunctionResponsePart(FunctionResponse(name, response))
    is FileDataPart ->
      com.google.firebase.vertexai.common.shared.FileDataPart(
        FileData(mimeType = mimeType, fileUri = uri)
      )
    else ->
      throw SerializationException(
        "The given subclass of Part (${javaClass.simpleName}) is not supported in the serialization yet."
      )
  }
}

internal fun SafetySetting.toInternal() =
  com.google.firebase.vertexai.common.shared.SafetySetting(
    harmCategory.toInternal(),
    threshold.toInternal(),
    method.toInternal()
  )

internal fun makeMissingCaseException(source: String, ordinal: Int): SerializationException {
  return SerializationException(
    """
    |Missing case for a $source: $ordinal
    |This error indicates that one of the `toInternal` conversions needs updating.
    |If you're a developer seeing this exception, please file an issue on our GitHub repo:
    |https://github.com/firebase/firebase-android-sdk
  """
      .trimMargin()
  )
}

internal fun GenerationConfig.toInternal() =
  com.google.firebase.vertexai.common.client.GenerationConfig(
    temperature = temperature,
    topP = topP,
    topK = topK,
    candidateCount = candidateCount,
    maxOutputTokens = maxOutputTokens,
    stopSequences = stopSequences,
    frequencyPenalty = frequencyPenalty,
    presencePenalty = presencePenalty,
    responseMimeType = responseMimeType,
    responseSchema = responseSchema?.toInternal()
  )

internal fun HarmCategory.toInternal() =
  when (this) {
    HarmCategory.HARASSMENT -> com.google.firebase.vertexai.common.shared.HarmCategory.HARASSMENT
    HarmCategory.HATE_SPEECH -> com.google.firebase.vertexai.common.shared.HarmCategory.HATE_SPEECH
    HarmCategory.SEXUALLY_EXPLICIT ->
      com.google.firebase.vertexai.common.shared.HarmCategory.SEXUALLY_EXPLICIT
    HarmCategory.DANGEROUS_CONTENT ->
      com.google.firebase.vertexai.common.shared.HarmCategory.DANGEROUS_CONTENT
    HarmCategory.UNKNOWN -> com.google.firebase.vertexai.common.shared.HarmCategory.UNKNOWN
    else -> throw makeMissingCaseException("HarmCategory", ordinal)
  }

internal fun HarmBlockMethod.toInternal() =
  when (this) {
    HarmBlockMethod.SEVERITY -> com.google.firebase.vertexai.common.shared.HarmBlockMethod.SEVERITY
    HarmBlockMethod.PROBABILITY ->
      com.google.firebase.vertexai.common.shared.HarmBlockMethod.PROBABILITY
    else -> throw makeMissingCaseException("HarmBlockMethod", ordinal)
  }

internal fun ToolConfig.toInternal() =
  com.google.firebase.vertexai.common.client.ToolConfig(
    if (functionCallingConfig == null) null
    else
      com.google.firebase.vertexai.common.client.FunctionCallingConfig(
        when (functionCallingConfig.mode) {
          FunctionCallingConfig.Mode.ANY ->
            com.google.firebase.vertexai.common.client.FunctionCallingConfig.Mode.ANY
          FunctionCallingConfig.Mode.AUTO ->
            com.google.firebase.vertexai.common.client.FunctionCallingConfig.Mode.AUTO
          FunctionCallingConfig.Mode.NONE ->
            com.google.firebase.vertexai.common.client.FunctionCallingConfig.Mode.NONE
        },
        functionCallingConfig.allowedFunctionNames
      )
  )

internal fun HarmBlockThreshold.toInternal() =
  when (this) {
    HarmBlockThreshold.NONE ->
      com.google.firebase.vertexai.common.shared.HarmBlockThreshold.BLOCK_NONE
    HarmBlockThreshold.ONLY_HIGH ->
      com.google.firebase.vertexai.common.shared.HarmBlockThreshold.BLOCK_ONLY_HIGH
    HarmBlockThreshold.MEDIUM_AND_ABOVE ->
      com.google.firebase.vertexai.common.shared.HarmBlockThreshold.BLOCK_MEDIUM_AND_ABOVE
    HarmBlockThreshold.LOW_AND_ABOVE ->
      com.google.firebase.vertexai.common.shared.HarmBlockThreshold.BLOCK_LOW_AND_ABOVE
    else -> throw makeMissingCaseException("HarmBlockThreshold", ordinal)
  }

internal fun Tool.toInternal() =
  com.google.firebase.vertexai.common.client.Tool(
    functionDeclarations?.map { it.toInternal() } ?: emptyList()
  )

internal fun FunctionDeclaration.toInternal() =
  com.google.firebase.vertexai.common.client.FunctionDeclaration(name, "", schema.toInternal())

internal fun com.google.firebase.vertexai.type.Schema.toInternal(): Schema =
  Schema(
    type,
    description,
    format,
    nullable,
    enum,
    properties?.mapValues { it.value.toInternal() },
    required,
    items?.toInternal(),
  )

internal fun JSONObject.toInternal() = Json.decodeFromString<JsonObject>(toString())

internal fun com.google.firebase.vertexai.common.server.Candidate.toPublic(): Candidate {
  val safetyRatings = safetyRatings?.map { it.toPublic() }.orEmpty()
  val citations = citationMetadata?.toPublic()
  val finishReason = finishReason.toPublic()

  return Candidate(
    this.content?.toPublic() ?: content("model") {},
    safetyRatings,
    citations,
    finishReason
  )
}

internal fun com.google.firebase.vertexai.common.UsageMetadata.toPublic(): UsageMetadata =
  UsageMetadata(promptTokenCount ?: 0, candidatesTokenCount ?: 0, totalTokenCount ?: 0)

internal fun com.google.firebase.vertexai.common.shared.Content.toPublic(): Content =
  Content(role, parts.map { it.toPublic() })

internal fun com.google.firebase.vertexai.common.shared.Part.toPublic(): Part {
  return when (this) {
    is com.google.firebase.vertexai.common.shared.TextPart -> TextPart(text)
    is com.google.firebase.vertexai.common.shared.InlineDataPart -> {
      val data = Base64.decode(inlineData.data, BASE_64_FLAGS)
      if (inlineData.mimeType.contains("image")) {
        ImagePart(decodeBitmapFromImage(data))
      } else {
        InlineDataPart(data, inlineData.mimeType)
      }
    }
    is FunctionCallPart ->
      com.google.firebase.vertexai.type.FunctionCallPart(
        functionCall.name,
        functionCall.args.orEmpty().mapValues { it.value ?: JsonNull }
      )
    is FunctionResponsePart ->
      com.google.firebase.vertexai.type.FunctionResponsePart(
        functionResponse.name,
        functionResponse.response,
      )
    is com.google.firebase.vertexai.common.shared.FileDataPart ->
      FileDataPart(fileData.mimeType, fileData.fileUri)
    else ->
      throw SerializationException(
        "Unsupported part type \"${javaClass.simpleName}\" provided. This model may not be supported by this SDK."
      )
  }
}

internal fun com.google.firebase.vertexai.common.server.CitationSources.toPublic(): Citation {
  val publicationDateAsCalendar =
    publicationDate?.let {
      val calendar = Calendar.getInstance()
      // Internal `Date.year` uses 0 to represent not specified. We use 1 as default.
      val year = if (it.year == null || it.year < 1) 1 else it.year
      // Internal `Date.month` uses 0 to represent not specified, or is 1-12 as months. The month as
      // expected by [Calendar] is 0-based, so we subtract 1 or use 0 as default.
      val month = if (it.month == null || it.month < 1) 0 else it.month - 1
      // Internal `Date.day` uses 0 to represent not specified. We use 1 as default.
      val day = if (it.day == null || it.day < 1) 1 else it.day
      calendar.set(year, month, day)
      calendar
    }
  return Citation(
    title = title,
    startIndex = startIndex,
    endIndex = endIndex,
    uri = uri,
    license = license,
    publicationDate = publicationDateAsCalendar
  )
}

internal fun com.google.firebase.vertexai.common.server.CitationMetadata.toPublic() =
  CitationMetadata(citationSources.map { it.toPublic() })

internal fun com.google.firebase.vertexai.common.server.SafetyRating.toPublic() =
  SafetyRating(
    category = category.toPublic(),
    probability = probability.toPublic(),
    probabilityScore = probabilityScore ?: 0f,
    blocked = blocked,
    severity = severity?.toPublic(),
    severityScore = severityScore
  )

internal fun com.google.firebase.vertexai.common.server.PromptFeedback.toPublic(): PromptFeedback {
  val safetyRatings = safetyRatings?.map { it.toPublic() }.orEmpty()
  return com.google.firebase.vertexai.type.PromptFeedback(
    blockReason?.toPublic(),
    safetyRatings,
    blockReasonMessage
  )
}

internal fun com.google.firebase.vertexai.common.server.FinishReason?.toPublic() =
  when (this) {
    null -> null
    com.google.firebase.vertexai.common.server.FinishReason.MAX_TOKENS -> FinishReason.MAX_TOKENS
    com.google.firebase.vertexai.common.server.FinishReason.RECITATION -> FinishReason.RECITATION
    com.google.firebase.vertexai.common.server.FinishReason.SAFETY -> FinishReason.SAFETY
    com.google.firebase.vertexai.common.server.FinishReason.STOP -> FinishReason.STOP
    com.google.firebase.vertexai.common.server.FinishReason.OTHER -> FinishReason.OTHER
    else -> FinishReason.UNKNOWN
  }

internal fun com.google.firebase.vertexai.common.shared.HarmCategory.toPublic() =
  when (this) {
    com.google.firebase.vertexai.common.shared.HarmCategory.HARASSMENT -> HarmCategory.HARASSMENT
    com.google.firebase.vertexai.common.shared.HarmCategory.HATE_SPEECH -> HarmCategory.HATE_SPEECH
    com.google.firebase.vertexai.common.shared.HarmCategory.SEXUALLY_EXPLICIT ->
      HarmCategory.SEXUALLY_EXPLICIT
    com.google.firebase.vertexai.common.shared.HarmCategory.DANGEROUS_CONTENT ->
      HarmCategory.DANGEROUS_CONTENT
    else -> HarmCategory.UNKNOWN
  }

internal fun com.google.firebase.vertexai.common.server.HarmProbability.toPublic() =
  when (this) {
    com.google.firebase.vertexai.common.server.HarmProbability.HIGH -> HarmProbability.HIGH
    com.google.firebase.vertexai.common.server.HarmProbability.MEDIUM -> HarmProbability.MEDIUM
    com.google.firebase.vertexai.common.server.HarmProbability.LOW -> HarmProbability.LOW
    com.google.firebase.vertexai.common.server.HarmProbability.NEGLIGIBLE ->
      HarmProbability.NEGLIGIBLE
    else -> HarmProbability.UNKNOWN
  }

internal fun com.google.firebase.vertexai.common.server.HarmSeverity.toPublic() =
  when (this) {
    com.google.firebase.vertexai.common.server.HarmSeverity.HIGH -> HarmSeverity.HIGH
    com.google.firebase.vertexai.common.server.HarmSeverity.MEDIUM -> HarmSeverity.MEDIUM
    com.google.firebase.vertexai.common.server.HarmSeverity.LOW -> HarmSeverity.LOW
    com.google.firebase.vertexai.common.server.HarmSeverity.NEGLIGIBLE -> HarmSeverity.NEGLIGIBLE
    else -> HarmSeverity.UNKNOWN
  }

internal fun com.google.firebase.vertexai.common.server.BlockReason.toPublic() =
  when (this) {
    com.google.firebase.vertexai.common.server.BlockReason.SAFETY -> BlockReason.SAFETY
    com.google.firebase.vertexai.common.server.BlockReason.OTHER -> BlockReason.OTHER
    else -> BlockReason.UNKNOWN
  }

internal fun com.google.firebase.vertexai.common.GenerateContentResponse.toPublic():
  GenerateContentResponse {
  return GenerateContentResponse(
    candidates?.map { it.toPublic() }.orEmpty(),
    promptFeedback?.toPublic(),
    usageMetadata?.toPublic()
  )
}

internal fun com.google.firebase.vertexai.common.CountTokensResponse.toPublic() =
  CountTokensResponse(totalTokens, totalBillableCharacters ?: 0)

internal fun JsonObject.toPublic() = JSONObject(toString())

private fun encodeBitmapToBase64Png(input: Bitmap): String {
  ByteArrayOutputStream().let {
    input.compress(Bitmap.CompressFormat.JPEG, 80, it)
    return Base64.encodeToString(it.toByteArray(), BASE_64_FLAGS)
  }
}

private fun decodeBitmapFromImage(input: ByteArray) =
  BitmapFactory.decodeByteArray(input, 0, input.size)
