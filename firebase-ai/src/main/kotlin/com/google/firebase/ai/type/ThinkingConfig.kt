package com.google.firebase.ai.type

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

public class ThinkingConfig(
  public val includeThoughts: Boolean? = null,
  public val thinkingBudget: Int? = null
) {

  internal fun toInternal() = Internal(includeThoughts, thinkingBudget)

  @Serializable
  internal data class Internal(
    @SerialName("include_thoughts") val includeThoughts: Boolean?,
    @SerialName("thinking_budget") val thinkingBudget: Int?
  )
}
