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

package com.google.firebase.ai.type

import com.google.firebase.ai.common.makeMissingCaseException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Specifies how the block method computes the score that will be compared against the
 * [HarmBlockThreshold] in [SafetySetting].
 */
public class HarmBlockMethod private constructor(public val ordinal: Int) {
  internal fun toInternal() =
    when (this) {
      SEVERITY -> Internal.SEVERITY
      PROBABILITY -> Internal.PROBABILITY
      else -> throw makeMissingCaseException("HarmBlockMethod", ordinal)
    }

  @Serializable
  internal enum class Internal {
    @SerialName("HARM_BLOCK_METHOD_UNSPECIFIED") UNSPECIFIED,
    SEVERITY,
    PROBABILITY,
  }
  public companion object {
    /**
     * The harm block method uses both probability and severity scores. See [HarmSeverity] and
     * [HarmProbability].
     */
    @JvmField public val SEVERITY: HarmBlockMethod = HarmBlockMethod(0)

    /** The harm block method uses the probability score. See [HarmProbability]. */
    @JvmField public val PROBABILITY: HarmBlockMethod = HarmBlockMethod(1)
  }
}
