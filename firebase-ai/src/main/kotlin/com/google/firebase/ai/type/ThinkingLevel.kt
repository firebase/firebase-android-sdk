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

/** Specifies the quality of the thinking response. */
public class ThinkingLevel private constructor(public val ordinal: Int) {
  internal fun toInternal() =
    when (this) {
      LOW -> Internal.LOW
      HIGH -> Internal.HIGH
      else -> throw makeMissingCaseException("ThinkingLevel", ordinal)
    }

  @Serializable
  internal enum class Internal {
    @SerialName("THINKING_LEVEL_UNSPECIFIED") UNSPECIFIED,
    LOW,
    HIGH,
  }
  public companion object {
    /** A lower quality thinking response, which provides low latency. */
    @JvmField public val LOW: ThinkingLevel = ThinkingLevel(0)

    /** A higher quality thinking response, which may increase latency. */
    @JvmField public val HIGH: ThinkingLevel = ThinkingLevel(1)
  }
}
