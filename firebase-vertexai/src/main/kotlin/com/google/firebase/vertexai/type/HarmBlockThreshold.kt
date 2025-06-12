/*
 * Copyright 2023 Google LLC
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

package com.google.firebase.vertexai.type

import com.google.firebase.vertexai.common.makeMissingCaseException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Represents the threshold for a [HarmCategory] to be allowed by [SafetySetting]. */
@Deprecated(
  """The Vertex AI in Firebase SDK (firebase-vertexai) has been replaced with the FirebaseAI SDK (firebase-ai) to accommodate the evolving set of supported features and services.
For migration details, see the migration guide: https://firebase.google.com/docs/vertex-ai/migrate-to-latest-sdk"""
)
public class HarmBlockThreshold private constructor(public val ordinal: Int) {

  internal fun toInternal() =
    when (this) {
      OFF -> Internal.OFF
      NONE -> Internal.BLOCK_NONE
      ONLY_HIGH -> Internal.BLOCK_ONLY_HIGH
      MEDIUM_AND_ABOVE -> Internal.BLOCK_MEDIUM_AND_ABOVE
      LOW_AND_ABOVE -> Internal.BLOCK_LOW_AND_ABOVE
      else -> throw makeMissingCaseException("HarmBlockThreshold", ordinal)
    }

  @Serializable
  internal enum class Internal {
    @SerialName("HARM_BLOCK_THRESHOLD_UNSPECIFIED") UNSPECIFIED,
    BLOCK_LOW_AND_ABOVE,
    BLOCK_MEDIUM_AND_ABOVE,
    BLOCK_ONLY_HIGH,
    BLOCK_NONE,
    OFF
  }

  public companion object {
    /** Content with negligible harm is allowed. */
    @JvmField public val LOW_AND_ABOVE: HarmBlockThreshold = HarmBlockThreshold(0)

    /** Content with negligible to low harm is allowed. */
    @JvmField public val MEDIUM_AND_ABOVE: HarmBlockThreshold = HarmBlockThreshold(1)

    /** Content with negligible to medium harm is allowed. */
    @JvmField public val ONLY_HIGH: HarmBlockThreshold = HarmBlockThreshold(2)

    /** All content is allowed regardless of harm. */
    @JvmField public val NONE: HarmBlockThreshold = HarmBlockThreshold(3)

    /**
     * All content is allowed regardless of harm.
     *
     * The same as [NONE], but metadata when the corresponding [HarmCategory] occurs will **NOT** be
     * present in the response.
     */
    @JvmField public val OFF: HarmBlockThreshold = HarmBlockThreshold(4)
  }
}
