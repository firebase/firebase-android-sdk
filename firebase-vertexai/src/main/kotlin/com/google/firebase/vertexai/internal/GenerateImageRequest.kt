package com.google.firebase.vertexai.internal

import com.google.firebase.vertexai.common.Request
import kotlinx.serialization.Serializable

@Serializable
internal data class GenerateImageRequest(
  val instances: List<ImagenPromptInstance>,
  val parameters: ImagenParameters,
) : Request {}

@Serializable internal data class ImagenPromptInstance(val prompt: String)

@Serializable
internal data class ImagenParameters(
  val sampleCount: Int = 1,
  val includeRaiReason: Boolean = true,
  val storageUri: String?,
  val negativePrompt: String?,
  val aspectRatio: String?,
  val safetySetting: String?,
  val personGeneration: String?,
  val addWatermark: Boolean?,
  val imageOutputOptions: ImageOutputOptions?,
)

@Serializable
internal data class ImageOutputOptions(val mimeType: String, val compressionQuality: Int?)
