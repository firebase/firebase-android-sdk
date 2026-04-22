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

package com.google.firebase.ai.type

import kotlinx.serialization.Serializable

/**
 * Configures the sliding window context compression mechanism.
 *
 * The context window will be truncated by keeping only a suffix of it.
 *
 * @property targetTokens The session reduction target, i.e., how many tokens we should keep.
 */
@PublicPreviewAPI
public class SlidingWindow(public val targetTokens: Int? = null) {
  internal fun toInternal() = Internal(targetTokens)

  @Serializable
  internal data class Internal(
    val targetTokens: Int? = null
  )
}

/**
 * Enables context window compression to manage the model's context window.
 *
 * This mechanism prevents the context from exceeding a given length.
 *
 * @property triggerTokens The number of tokens (before running a turn) that triggers the context window compression.
 * @property slidingWindow The sliding window compression mechanism.
 */
@PublicPreviewAPI
public class ContextWindowCompressionConfig(
  public val triggerTokens: Int? = null,
  public val slidingWindow: SlidingWindow? = null
) {
  internal fun toInternal() = Internal(triggerTokens, slidingWindow?.toInternal())

  @Serializable
  internal data class Internal(
    val triggerTokens: Int? = null,
    val slidingWindow: SlidingWindow.Internal? = null
  )
}
