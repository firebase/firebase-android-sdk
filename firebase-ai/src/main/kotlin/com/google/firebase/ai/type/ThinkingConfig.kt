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

/** Configuration parameters for thinking features. */
public class ThinkingConfig
private constructor(
  internal val thinkingBudget: Int? = null,
  internal val includeThoughts: Boolean? = null
) {

  public class Builder() {
    @JvmField
    @set:JvmSynthetic // hide void setter from Java
    public var thinkingBudget: Int? = null

    @JvmField
    @set:JvmSynthetic // hide void setter from Java
    public var includeThoughts: Boolean? = null

    /**
     * Indicates the thinking budget in tokens. `0` is disabled. `-1` is dynamic. The default values
     * and allowed ranges are model dependent.
     */
    public fun setThinkingBudget(thinkingBudget: Int): Builder = apply {
      this.thinkingBudget = thinkingBudget
    }

    public fun setIncludeThoughts(includeThoughts: Boolean): Builder = apply {
      this.includeThoughts = includeThoughts
    }

    public fun build(): ThinkingConfig =
      ThinkingConfig(thinkingBudget = thinkingBudget, includeThoughts = includeThoughts)
  }

  internal fun toInternal() = Internal(thinkingBudget, includeThoughts)

  @Serializable
  internal data class Internal(
    @SerialName("thinking_budget") val thinkingBudget: Int? = null,
    val includeThoughts: Boolean? = null
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
