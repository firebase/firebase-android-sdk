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

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * Represents the interaction status of the model during a Live API session, particularly when
 * using models with extended thinking or non-blocking tools.
 */
public class InteractionStatus
private constructor(
  public val ordinal: Int,
  public val name: String,
) {
  internal fun toInternal(): Internal =
    when (this) {
      UNSPECIFIED -> Internal.UNSPECIFIED
      IDLE -> Internal.IDLE
      IN_PROGRESS -> Internal.IN_PROGRESS
      else -> throw makeMissingCaseException("InteractionStatus", ordinal)
    }

  override fun toString(): String = name

  override fun equals(other: Any?): Boolean =
    other is InteractionStatus && other.ordinal == ordinal

  override fun hashCode(): Int = ordinal

  @OptIn(ExperimentalSerializationApi::class)
  @Serializable
  internal enum class Internal {
    @SerialName("INTERACTION_STATUS_UNSPECIFIED")
    @JsonNames("UNSPECIFIED", "INTERACTION_STATUS_UNSPECIFIED")
    UNSPECIFIED,
    @SerialName("IDLE") @JsonNames("IDLE", "idle") IDLE,
    @SerialName("IN_PROGRESS") @JsonNames("IN_PROGRESS", "in_progress") IN_PROGRESS;

    internal fun toPublic(): InteractionStatus =
      when (this) {
        UNSPECIFIED -> InteractionStatus.UNSPECIFIED
        IDLE -> InteractionStatus.IDLE
        IN_PROGRESS -> InteractionStatus.IN_PROGRESS
      }
  }

  public companion object {
    /** The interaction status is unspecified. */
    @JvmField public val UNSPECIFIED: InteractionStatus = InteractionStatus(0, "UNSPECIFIED")

    /**
     * The model has finished all background reasoning and tool execution for the current
     * interaction and is idle.
     */
    @JvmField public val IDLE: InteractionStatus = InteractionStatus(1, "IDLE")

    /**
     * The model is still actively working on the user's request (e.g. performing extended thinking
     * or awaiting non-blocking tool execution), even if an intermediate verbal turn has completed
     * (`turnComplete == true`).
     */
    @JvmField public val IN_PROGRESS: InteractionStatus = InteractionStatus(2, "IN_PROGRESS")

    @JvmStatic
    public fun fromString(value: String): InteractionStatus =
      when (value.uppercase()) {
        "IDLE" -> IDLE
        "IN_PROGRESS" -> IN_PROGRESS
        else -> UNSPECIFIED
      }
  }
}
