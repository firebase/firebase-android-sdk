/*
 * Copyright 2025 Google LLC
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

package com.google.firebase.ai.type

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Gemini 2.5 series models and newer utilize a thinking process before generating a response. This
 * allows them to reason through complex problems and plan a more coherent and accurate answer. See
 * the [thinking documentation](https://firebase.google.com/docs/ai-logic/thinking) for more
 * details.
 */
public class ThinkingConfig
private constructor(
  internal val thinkingBudget: Int? = null,
  internal val includeThoughts: Boolean? = null,
  internal val thinkingLevel: ThinkingLevel? = null
) {

  public class Builder() {
    @JvmField
    @set:JvmSynthetic // hide void setter from Java
    public var thinkingBudget: Int? = null

    @JvmField
    @set:JvmSynthetic // hide void setter from Java
    public var includeThoughts: Boolean? = null

    @JvmField
    @set:JvmSynthetic // hide void setter from Java
    public var thinkingLevel: ThinkingLevel? = null

    /**
     * Indicates the thinking budget in tokens.
     *
     * Use `0` for disabled, and `-1` for dynamic. The range of
     * [supported thinking budget values](https://firebase.google.com/docs/ai-logic/thinking#supported-thinking-budget-values)
     * depends on the model.
     */
    public fun setThinkingBudget(thinkingBudget: Int): Builder = apply {
      this.thinkingBudget = thinkingBudget
    }

    /** Indicates the thinking budget based in Levels. */
    public fun setThinkingLevel(thinkingLevel: ThinkingLevel): Builder = apply {
      this.thinkingLevel = thinkingLevel
    }

    /**
     * Indicates whether to request the model to include the thoughts parts in the response.
     *
     * Keep in mind that once enabled, you should check for the `isThought` property when processing
     * a `Part` instance to correctly handle both thoughts and the actual response.
     *
     * The default value is `false`.
     */
    public fun setIncludeThoughts(includeThoughts: Boolean): Builder = apply {
      this.includeThoughts = includeThoughts
    }

    public fun build(): ThinkingConfig {
      if (thinkingBudget != null && thinkingLevel != null)
        throw IllegalArgumentException(
          "`thinkingBudget` already set. Cannot set both `thinkingBudget` and `thinkingLevel`"
        )
      return ThinkingConfig(
        thinkingBudget = thinkingBudget,
        includeThoughts = includeThoughts,
        thinkingLevel = thinkingLevel
      )
    }
  }

  internal fun toInternal() = Internal(thinkingBudget, includeThoughts, thinkingLevel?.toInternal())

  @Serializable
  internal data class Internal(
    @SerialName("thinking_budget") val thinkingBudget: Int? = null,
    val includeThoughts: Boolean? = null,
    @SerialName("thinking_level") val thinkingLevel: ThinkingLevel.Internal? = null,
  )
}

/**
 * Helper method to construct a [ThinkingConfig] in a DSL-like manner.
 *
 * Example Usage:
 * ```
 * thinkingConfig {
 *   thinkingBudget = 0 // disable thinking
 * }
 * ```
 */
public fun thinkingConfig(init: ThinkingConfig.Builder.() -> Unit): ThinkingConfig {
  val builder = ThinkingConfig.Builder()
  builder.init()
  return builder.build()
}
