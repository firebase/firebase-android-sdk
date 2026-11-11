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
import com.google.firebase.ai.ondevice.interop.DownloadStatusInterop
import com.google.firebase.ai.ondevice.interop.FinishReason
import com.google.firebase.ai.ondevice.interop.FirebaseAIOnDeviceException
import com.google.firebase.ai.ondevice.interop.FirebaseAIOnDeviceInvalidRequestException
import com.google.firebase.ai.ondevice.interop.GenerateContentResponse
import com.google.firebase.ai.ondevice.interop.GenerationConfig
import com.google.firebase.ai.ondevice.interop.ModelConfig
import com.google.firebase.ai.ondevice.interop.OnDeviceModelStatusInterop
import com.google.mlkit.genai.common.FunctionArgValue
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.ModelPreference
import com.google.mlkit.genai.prompt.ModelReleaseStage
import com.google.mlkit.genai.prompt.Part
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.content
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.genai.prompt.generationConfig
import com.google.mlkit.genai.prompt.modelConfig
import com.google.mlkit.genai.schema.tools.FunctionType

// ====================================
// `Part` converter extension functions
// ====================================
internal fun com.google.firebase.ai.ondevice.interop.Part.toMlKit(): Part =
  when (this) {
    is com.google.firebase.ai.ondevice.interop.TextPart -> toMlKit()
    is com.google.firebase.ai.ondevice.interop.ImagePart -> toMlKit()
    is com.google.firebase.ai.ondevice.interop.FunctionCallPart -> toMlKit()
    is com.google.firebase.ai.ondevice.interop.FunctionResponsePart -> toMlKit()
    else ->
      throw FirebaseAIOnDeviceInvalidRequestException(
        IllegalArgumentException("Unsupported part type: ${this::class.java.simpleName}")
      )
  }

internal fun com.google.firebase.ai.ondevice.interop.TextPart.toMlKit(): TextPart = TextPart(text)

internal fun com.google.firebase.ai.ondevice.interop.TextPart.toMlKitSystemInstruction():
  SystemInstruction = SystemInstruction(text)

internal fun com.google.firebase.ai.ondevice.interop.ImagePart.toMlKit(): ImagePart =
  ImagePart(bitmap)

internal fun com.google.firebase.ai.ondevice.interop.FunctionCallPart.toMlKit():
  com.google.mlkit.genai.prompt.FunctionCallPart =
  com.google.mlkit.genai.prompt.FunctionCallPart(
    com.google.mlkit.genai.common.FunctionCall(
      name = name,
      arguments = args.mapValues { toFunctionArgValue(it.value) },
      id = id
    )
  )

internal fun com.google.firebase.ai.ondevice.interop.FunctionResponsePart.toMlKit():
  com.google.mlkit.genai.prompt.FunctionResponsePart =
  com.google.mlkit.genai.prompt.FunctionResponsePart(id = id, name = name, response = response)

private fun toFunctionArgValue(value: Any?): FunctionArgValue =
  when (value) {
    is Boolean -> FunctionArgValue.BoolValue(value)
    is Int -> FunctionArgValue.IntValue(value)
    is Long -> FunctionArgValue.LongValue(value)
    is Double -> FunctionArgValue.DoubleValue(value)
    is Float -> FunctionArgValue.DoubleValue(value.toDouble())
    is String -> FunctionArgValue.StringValue(value)
    is List<*> -> FunctionArgValue.ListValue(value.map { toFunctionArgValue(it) })
    is Map<*, *> ->
      FunctionArgValue.MapValue(
        value.entries.associate { it.key.toString() to toFunctionArgValue(it.value) }
      )
    else -> FunctionArgValue.StringValue(value?.toString() ?: "")
  }

// ============================================
// `CountTokens*` converter extension functions
// ============================================
internal fun com.google.mlkit.genai.prompt.CountTokensResponse.toInterop(): CountTokensResponse =
  CountTokensResponse(totalTokens)

// =========================================
// `Candidate` converter extension functions
// =========================================
internal fun com.google.mlkit.genai.prompt.Candidate.toInterop(): Candidate =
  Candidate(
    text = text,
    finishReason = finishReason?.toInterop() ?: FinishReason.OTHER,
    functionCalls = functionCalls?.map { it.toInterop() } ?: emptyList()
  )

internal fun com.google.mlkit.genai.common.FunctionCall.toInterop():
  com.google.firebase.ai.ondevice.interop.FunctionCallPart =
  com.google.firebase.ai.ondevice.interop.FunctionCallPart(
    name = name,
    args = arguments.mapValues { it.value.toAny() },
    id = id
  )

internal fun com.google.mlkit.genai.prompt.FunctionCallPart.toInterop():
  com.google.firebase.ai.ondevice.interop.FunctionCallPart = functionCall.toInterop()

internal fun com.google.mlkit.genai.prompt.FunctionResponsePart.toInterop():
  com.google.firebase.ai.ondevice.interop.FunctionResponsePart =
  com.google.firebase.ai.ondevice.interop.FunctionResponsePart(
    name = name,
    response = response,
    id = id
  )

internal fun FunctionArgValue.toAny(): Any? =
  when (this) {
    is FunctionArgValue.BoolValue -> value
    is FunctionArgValue.IntValue -> value
    is FunctionArgValue.LongValue -> value
    is FunctionArgValue.DoubleValue -> value
    is FunctionArgValue.StringValue -> value
    is FunctionArgValue.ListValue -> values.map { it.toAny() }
    is FunctionArgValue.MapValue -> map.mapValues { it.value.toAny() }
  }

private fun Int.toInterop(): FinishReason =
  when (this) {
    com.google.mlkit.genai.prompt.Candidate.FinishReason.STOP -> FinishReason.STOP
    com.google.mlkit.genai.prompt.Candidate.FinishReason.MAX_TOKENS -> FinishReason.MAX_TOKENS
    else -> FinishReason.OTHER
  }

// ================================================
// `Tool` converter extension functions
// ================================================
internal fun com.google.firebase.ai.ondevice.interop.FunctionDeclaration.toMlKit():
  com.google.mlkit.genai.schema.tools.FunctionDeclaration =
  com.google.mlkit.genai.schema.tools.FunctionDeclaration(
    name = name,
    description = description,
    parameters = parameters?.toMlKit()
  )

internal fun com.google.firebase.ai.ondevice.interop.FunctionSchema.toMlKit():
  com.google.mlkit.genai.schema.tools.FunctionSchema =
  com.google.mlkit.genai.schema.tools.FunctionSchema(
    type =
      when (type.uppercase()) {
        "STRING" -> FunctionType.STRING
        "NUMBER" -> FunctionType.NUMBER
        "INTEGER" -> FunctionType.INTEGER
        "BOOLEAN" -> FunctionType.BOOLEAN
        "ARRAY" -> FunctionType.ARRAY
        "OBJECT" -> FunctionType.OBJECT
        "NULL" -> FunctionType.NULL
        else -> FunctionType.UNSPECIFIED
      },
    description = description,
    properties = properties.mapValues { it.value.toMlKit() }.ifEmpty { null },
    required = required.ifEmpty { null },
    enum = enum,
    nullable = nullable,
    items = items?.toMlKit()
  )

// ================================================
// `GenerateContent*` converter extension functions
// ================================================
internal fun com.google.firebase.ai.ondevice.interop.GenerateContentRequest.toMlKit():
  GenerateContentRequest {
  try {
    require(parts.isNotEmpty()) { "parts must not be empty" }
    return generateContentRequest(
      content { this@toMlKit.parts.forEach { addPart(it.toMlKit()) } }
    ) {
      temperature = this@toMlKit.temperature
      maxOutputTokens = this@toMlKit.maxOutputTokens
      candidateCount = this@toMlKit.candidateCount
      topK = this@toMlKit.topK
      seed = this@toMlKit.seed
      systemInstruction = this@toMlKit.systemInstruction?.toMlKitSystemInstruction()
      enableThinking = this@toMlKit.enableThinking ?: false
      if (this@toMlKit.tools.isNotEmpty()) {
        setTools(this@toMlKit.tools.map { it.toMlKit() })
      }
    }
  } catch (e: IllegalArgumentException) {
    throw FirebaseAIOnDeviceInvalidRequestException(e)
  }
}

internal fun com.google.mlkit.genai.prompt.GenerateContentResponse.toInterop(
  modelName: String
): GenerateContentResponse =
  GenerateContentResponse(
    candidates = candidates.map { it.toInterop() },
    modelVersion = modelName,
    thoughtProcess = thoughtProcess.map { it.toInterop() }
  )

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

// ========================================================================
// `DownloadStatus` and `OnDeviceModelStatus` converter extension functions
// ========================================================================
internal fun com.google.mlkit.genai.common.DownloadStatus.toInterop(): DownloadStatusInterop =
  when (this) {
    is com.google.mlkit.genai.common.DownloadStatus.DownloadStarted ->
      DownloadStatusInterop.DownloadStarted(bytesToDownload)
    is com.google.mlkit.genai.common.DownloadStatus.DownloadProgress ->
      DownloadStatusInterop.DownloadInProgress(totalBytesDownloaded)
    is com.google.mlkit.genai.common.DownloadStatus.DownloadCompleted ->
      DownloadStatusInterop.DownloadCompleted()
    is com.google.mlkit.genai.common.DownloadStatus.DownloadFailed ->
      DownloadStatusInterop.DownloadFailed(FirebaseAIOnDeviceException.from(e))
  }

internal fun Int.toInteropStatus(): OnDeviceModelStatusInterop =
  when (this) {
    com.google.mlkit.genai.common.FeatureStatus.UNAVAILABLE ->
      OnDeviceModelStatusInterop.UNAVAILABLE
    com.google.mlkit.genai.common.FeatureStatus.DOWNLOADABLE ->
      OnDeviceModelStatusInterop.DOWNLOADABLE
    com.google.mlkit.genai.common.FeatureStatus.DOWNLOADING ->
      OnDeviceModelStatusInterop.DOWNLOADING
    com.google.mlkit.genai.common.FeatureStatus.AVAILABLE -> OnDeviceModelStatusInterop.AVAILABLE
    else -> OnDeviceModelStatusInterop.UNAVAILABLE
  }
