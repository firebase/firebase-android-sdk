package com.google.firebase.vertexai.type

import kotlinx.serialization.Serializable

internal class BidiGenerateContentClientMessage(
  val model: String,
  val generationConfig: LiveGenerationConfig.Internal?,
  val tools: List<Tool.Internal>?,
  val toolConfig: ToolConfig.Internal?,
  val systemInstruction: Content.Internal?
) {

  @Serializable
  internal class Internal(val setup: BidiGenerateContentSetup) {
    @Serializable
    internal data class BidiGenerateContentSetup(
      val model: String,
      val generationConfig: LiveGenerationConfig.Internal?,
      val tools: List<Tool.Internal>?,
      val toolConfig: ToolConfig.Internal?,
      val systemInstruction: Content.Internal?
    )
  }

  fun toInternal() =
    Internal(
      Internal.BidiGenerateContentSetup(
        model,
        generationConfig,
        tools,
        toolConfig,
        systemInstruction
      )
    )
}
