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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Specifies the execution behavior of a [FunctionDeclaration] in the Live API.
 *
 * Note: All tools used with Live Thinking models must use [NON_BLOCKING].
 */
public class FunctionBehavior
private constructor(
  public val ordinal: Int,
  public val name: String,
) {
  internal fun toInternal(): Internal =
    when (this) {
      UNSPECIFIED -> Internal.UNSPECIFIED
      BLOCKING -> Internal.BLOCKING
      NON_BLOCKING -> Internal.NON_BLOCKING
      else -> throw makeMissingCaseException("FunctionBehavior", ordinal)
    }

  override fun toString(): String = name

  override fun equals(other: Any?): Boolean =
    other is FunctionBehavior && other.ordinal == ordinal

  override fun hashCode(): Int = ordinal

  @Serializable
  internal enum class Internal {
    @SerialName("UNSPECIFIED") UNSPECIFIED,
    @SerialName("BLOCKING") BLOCKING,
    @SerialName("NON_BLOCKING") NON_BLOCKING,
  }

  public companion object {
    /** The function behavior is unspecified. */
    @JvmField public val UNSPECIFIED: FunctionBehavior = FunctionBehavior(0, "UNSPECIFIED")

    /**
     * Blocking execution. The model waits for the function response before continuing generation.
     */
    @JvmField public val BLOCKING: FunctionBehavior = FunctionBehavior(1, "BLOCKING")

    /**
     * Non-blocking (asynchronous) execution. The model does not pause generation while waiting for
     * the function response and can continue speaking or reasoning. Required for all Live Thinking
     * tools.
     */
    @JvmField public val NON_BLOCKING: FunctionBehavior = FunctionBehavior(2, "NON_BLOCKING")

    @JvmStatic
    public fun fromString(value: String): FunctionBehavior =
      when (value.uppercase()) {
        "NON_BLOCKING" -> NON_BLOCKING
        "BLOCKING" -> BLOCKING
        else -> UNSPECIFIED
      }
  }
}

/** Typealias for [FunctionBehavior] for convenience. */
public typealias Behavior = FunctionBehavior

/**
 * Specifies how the model should schedule and handle the result of a [FunctionBehavior.NON_BLOCKING]
 * tool call in a [FunctionResponsePart].
 */
public class FunctionResponseScheduling
private constructor(
  public val ordinal: Int,
  public val name: String,
) {
  internal fun toInternal(): Internal =
    when (this) {
      UNSPECIFIED -> Internal.UNSPECIFIED
      INTERRUPT -> Internal.INTERRUPT
      WHEN_IDLE -> Internal.WHEN_IDLE
      SILENT -> Internal.SILENT
      else -> throw makeMissingCaseException("FunctionResponseScheduling", ordinal)
    }

  override fun toString(): String = name

  override fun equals(other: Any?): Boolean =
    other is FunctionResponseScheduling && other.ordinal == ordinal

  override fun hashCode(): Int = ordinal

  @Serializable
  internal enum class Internal {
    @SerialName("SCHEDULING_UNSPECIFIED") UNSPECIFIED,
    @SerialName("INTERRUPT") INTERRUPT,
    @SerialName("WHEN_IDLE") WHEN_IDLE,
    @SerialName("SILENT") SILENT,
  }

  public companion object {
    /** Scheduling is unspecified. */
    @JvmField
    public val UNSPECIFIED: FunctionResponseScheduling =
      FunctionResponseScheduling(0, "UNSPECIFIED")

    /** Interrupt the model's current ongoing generation immediately to incorporate the result. */
    @JvmField
    public val INTERRUPT: FunctionResponseScheduling = FunctionResponseScheduling(1, "INTERRUPT")

    /** Wait until the model finishes its current turn (`IDLE`) before presenting the result. */
    @JvmField
    public val WHEN_IDLE: FunctionResponseScheduling = FunctionResponseScheduling(2, "WHEN_IDLE")

    /** Ingest the tool response into context silently without triggering a new spoken response. */
    @JvmField
    public val SILENT: FunctionResponseScheduling = FunctionResponseScheduling(3, "SILENT")
  }
}
