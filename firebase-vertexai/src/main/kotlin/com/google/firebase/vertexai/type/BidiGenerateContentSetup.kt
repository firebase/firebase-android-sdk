package com.google.firebase.vertexai.type

import kotlinx.serialization.Serializable

@Serializable
internal data class BidiGenerateContentSetup(
  val model: String,
  val generationConfig: LiveGenerationConfig.Internal?,
  val tools: List<Tool.Internal>?,
  val systemInstruction: Content.Internal?
)

@Serializable
internal data class BidiGenerateContentClientMessage(val setup: BidiGenerateContentSetup)
