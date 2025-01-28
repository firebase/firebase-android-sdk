package com.google.firebase.vertexai.internal

import kotlinx.serialization.Serializable

@Serializable
internal data class GenerateImageResponse(val predictions: List<ImagenImageResponse>) {}

@Serializable
internal data class ImagenImageResponse(
  val bytesBase64Encoded: String? = null,
  val gcsUri: String? = null,
  val mimeType: String? = null,
  val raiFilteredReason: String? = null,
)
