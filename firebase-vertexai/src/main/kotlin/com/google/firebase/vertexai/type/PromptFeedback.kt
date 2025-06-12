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

import com.google.firebase.vertexai.common.util.FirstOrdinalSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Feedback on the prompt provided in the request.
 *
 * @param blockReason The reason that content was blocked, if at all.
 * @param safetyRatings A list of relevant [SafetyRating].
 * @param blockReasonMessage A message describing the reason that content was blocked, if any.
 */
@Deprecated(
  """The Vertex AI in Firebase SDK (firebase-vertexai) has been replaced with the FirebaseAI SDK (firebase-ai) to accommodate the evolving set of supported features and services.
For migration details, see the migration guide: https://firebase.google.com/docs/vertex-ai/migrate-to-latest-sdk"""
)
public class PromptFeedback(
  public val blockReason: BlockReason?,
  public val safetyRatings: List<SafetyRating>,
  public val blockReasonMessage: String?
) {

  @Serializable
  internal data class Internal(
    val blockReason: BlockReason.Internal? = null,
    val safetyRatings: List<SafetyRating.Internal>? = null,
    val blockReasonMessage: String? = null,
  ) {

    internal fun toPublic(): PromptFeedback {
      val safetyRatings = safetyRatings?.mapNotNull { it.toPublic() }.orEmpty()
      return PromptFeedback(blockReason?.toPublic(), safetyRatings, blockReasonMessage)
    }
  }
}

/** Describes why content was blocked. */
@Deprecated(
  """The Vertex AI in Firebase SDK (firebase-vertexai) has been replaced with the FirebaseAI SDK (firebase-ai) to accommodate the evolving set of supported features and services.
For migration details, see the migration guide: https://firebase.google.com/docs/vertex-ai/migrate-to-latest-sdk"""
)
public class BlockReason private constructor(public val name: String, public val ordinal: Int) {

  @Serializable(Internal.Serializer::class)
  internal enum class Internal {
    UNKNOWN,
    @SerialName("BLOCKED_REASON_UNSPECIFIED") UNSPECIFIED,
    SAFETY,
    OTHER,
    BLOCKLIST,
    PROHIBITED_CONTENT;

    internal object Serializer : KSerializer<Internal> by FirstOrdinalSerializer(Internal::class)

    internal fun toPublic() =
      when (this) {
        SAFETY -> BlockReason.SAFETY
        OTHER -> BlockReason.OTHER
        BLOCKLIST -> BlockReason.BLOCKLIST
        PROHIBITED_CONTENT -> BlockReason.PROHIBITED_CONTENT
        else -> BlockReason.UNKNOWN
      }
  }
  public companion object {
    /** A new and not yet supported value. */
    @JvmField public val UNKNOWN: BlockReason = BlockReason("UNKNOWN", 0)

    /** Content was blocked for violating provided [SafetySetting]. */
    @JvmField public val SAFETY: BlockReason = BlockReason("SAFETY", 1)

    /** Content was blocked for another reason. */
    @JvmField public val OTHER: BlockReason = BlockReason("OTHER", 2)

    /** Content was blocked for another reason. */
    @JvmField public val BLOCKLIST: BlockReason = BlockReason("BLOCKLIST", 3)

    /** Candidates blocked due to the terms which are included from the terminology blocklist. */
    @JvmField public val PROHIBITED_CONTENT: BlockReason = BlockReason("PROHIBITED_CONTENT", 4)
  }
}
