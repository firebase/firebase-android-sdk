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
@file:OptIn(ExperimentalSerializationApi::class)

package com.google.firebase.ai.common

import com.google.firebase.ai.common.util.fullModelName
import com.google.firebase.ai.common.util.trimmedModelName
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerationConfig
import com.google.firebase.ai.type.ImagenEditingConfig
import com.google.firebase.ai.type.ImagenImageFormat
import com.google.firebase.ai.type.ImagenInlineImage
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.SafetySetting
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.ToolConfig
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal interface Request

@Serializable
internal data class GenerateContentRequest(
  val model: String? = null,
  val contents: List<Content.Internal>,
  @SerialName("safety_settings") val safetySettings: List<SafetySetting.Internal>? = null,
  @SerialName("generation_config") val generationConfig: GenerationConfig.Internal? = null,
  val tools: List<Tool.Internal>? = null,
  @SerialName("tool_config") var toolConfig: ToolConfig.Internal? = null,
  @SerialName("system_instruction") val systemInstruction: Content.Internal? = null,
) : Request

@Serializable
internal data class CountTokensRequest(
  val generateContentRequest: GenerateContentRequest? = null,
  val model: String? = null,
  val contents: List<Content.Internal>? = null,
  val tools: List<Tool.Internal>? = null,
  @SerialName("system_instruction") val systemInstruction: Content.Internal? = null,
  val generationConfig: GenerationConfig.Internal? = null
) : Request {
  companion object {

    fun forGoogleAI(generateContentRequest: GenerateContentRequest) =
      CountTokensRequest(
        generateContentRequest =
          generateContentRequest.model?.let {
            generateContentRequest.copy(model = fullModelName(trimmedModelName(it)))
          }
            ?: generateContentRequest
      )

    fun forVertexAI(generateContentRequest: GenerateContentRequest) =
      CountTokensRequest(
        model = generateContentRequest.model?.let { fullModelName(it) },
        contents = generateContentRequest.contents,
        tools = generateContentRequest.tools,
        systemInstruction = generateContentRequest.systemInstruction,
        generationConfig = generateContentRequest.generationConfig,
      )
  }
}

@Serializable
@PublicPreviewAPI
internal data class GenerateImageRequest(
  val instances: List<ImagenPrompt>,
  val parameters: ImagenParameters,
) : Request {
  @Serializable
  internal data class ImagenPrompt(
    val prompt: String? = null,
    val image: ImagenInlineImage.Internal? = null,
    val referenceImages: List<ReferenceImage>? = null
  )

  @OptIn(PublicPreviewAPI::class)
  @Serializable
  internal data class ImagenParameters(
    val sampleCount: Int,
    val includeRaiReason: Boolean,
    val storageUri: String?,
    val negativePrompt: String?,
    val aspectRatio: String?,
    val safetySetting: String?,
    val personGeneration: String?,
    val addWatermark: Boolean?,
    val imageOutputOptions: ImagenImageFormat.Internal?,
    val editMode: String?,
    val editConfig: ImagenEditingConfig.Internal?,
  )

  @Serializable
  internal enum class ReferenceType {
    @SerialName("REFERENCE_TYPE_UNSPECIFIED") UNSPECIFIED,
    @SerialName("REFERENCE_TYPE_RAW") RAW,
    @SerialName("REFERENCE_TYPE_MASK") MASK,
    @SerialName("REFERENCE_TYPE_CONTROL") CONTROL,
    @SerialName("REFERENCE_TYPE_STYLE") STYLE,
    @SerialName("REFERENCE_TYPE_SUBJECT") SUBJECT,
    @SerialName("REFERENCE_TYPE_MASKED_SUBJECT") MASKED_SUBJECT,
    @SerialName("REFERENCE_TYPE_PRODUCT") PRODUCT
  }

  @Serializable
  internal enum class MaskMode {
    @SerialName("MASK_MODE_DEFAULT") DEFAULT,
    @SerialName("MASK_MODE_USER_PROVIDED") USER_PROVIDED,
    @SerialName("MASK_MODE_BACKGROUND") BACKGROUND,
    @SerialName("MASK_MODE_FOREGROUND") FOREGROUND,
    @SerialName("MASK_MODE_SEMANTIC") SEMANTIC
  }

  @Serializable internal data class MaskImageConfig(val maskMode: MaskMode, val dilation: Double?)

  @Serializable
  internal data class ReferenceImage(
    val referenceType: ReferenceType,
    val referenceId: Int,
    val referenceImage: ImagenInlineImage.Internal,
    val maskImageConfig: MaskImageConfig?
  )
}
