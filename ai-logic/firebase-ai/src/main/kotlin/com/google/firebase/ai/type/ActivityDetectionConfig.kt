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

/** Configures the model's automatic detection of user activity. */
@PublicPreviewAPI
public class ActivityDetectionConfig
private constructor(
  internal val startSensitivity: Sensitivity?,
  internal val endSensitivity: Sensitivity?,
  internal val prefixPaddingMs: Int?,
  internal val silenceDurationMs: Int?,
  internal val disabled: Boolean?
) {

  /** How sensitive the model interprets speech activity. */
  public class Sensitivity private constructor(internal val value: String) {
    public companion object {
      /**
       * The model will detect speech less often. In other words, a higher volume of speech is
       * required for the model to consider the user is speaking.
       */
      @JvmField public val LOW: Sensitivity = Sensitivity("LOW")

      /**
       * The model will detect speech more often. In other words, a lower volume of speech is
       * required for the model to consider the user is speaking.
       */
      @JvmField public val HIGH: Sensitivity = Sensitivity("HIGH")
    }
  }

  /** Builder for creating an [ActivityDetectionConfig]. */
  public class Builder {
    /** Determines how likely the start of speech is detected. */
    @JvmField public var startSensitivity: Sensitivity? = null

    /** Determines how likely the end of speech is detected. */
    @JvmField public var endSensitivity: Sensitivity? = null

    /**
     * How long (in milliseconds) detected speech should be present before start-of-speech is
     * committed.
     *
     * The lower this value, the more sensitive the start-of-speech detection is and the shorter the
     * speech that can be recognized. However, this also increases the probability of false
     * positives.
     */
    @JvmField public var prefixPaddingMs: Int? = null

    /**
     * How long (in milliseconds) silence (or non-speech) should be present before end-of-speech is
     * committed.
     *
     * The larger this value, the longer speech gaps can be without interrupting the user's
     * activity, but this will increase the model's latency.
     */
    @JvmField public var silenceDurationMs: Int? = null

    /** Sets [startSensitivity]. */
    public fun setStartSensitivity(sensitivity: Sensitivity): Builder = apply {
      startSensitivity = sensitivity
    }

    /** Sets [endSensitivity]. */
    public fun setEndSensitivity(sensitivity: Sensitivity): Builder = apply {
      endSensitivity = sensitivity
    }

    /** Sets [prefixPaddingMs] in milliseconds. */
    public fun setPrefixPaddingMs(paddingMs: Int): Builder = apply { prefixPaddingMs = paddingMs }

    /** Sets [silenceDurationMs] in milliseconds. */
    public fun setSilenceDurationMs(durationMs: Int): Builder = apply {
      silenceDurationMs = durationMs
    }

    /** Creates a new [ActivityDetectionConfig] with the configured options. */
    public fun build(): ActivityDetectionConfig =
      ActivityDetectionConfig(
        startSensitivity,
        endSensitivity,
        prefixPaddingMs,
        silenceDurationMs,
        null
      )
  }

  internal fun toInternal(): Internal =
    Internal(
      startSensitivity = startSensitivity?.let { "START_SENSITIVITY_${it.value}" },
      endSensitivity = endSensitivity?.let { "END_SENSITIVITY_${it.value}" },
      prefixPaddingMs = prefixPaddingMs,
      silenceDurationMs = silenceDurationMs,
      disabled = disabled
    )

  @Serializable
  internal data class Internal(
    @SerialName("start_of_speech_sensitivity") val startSensitivity: String? = null,
    @SerialName("end_of_speech_sensitivity") val endSensitivity: String? = null,
    @SerialName("prefix_padding_ms") val prefixPaddingMs: Int? = null,
    @SerialName("silence_duration_ms") val silenceDurationMs: Int? = null,
    val disabled: Boolean? = null
  )

  public companion object {
    /** Creates a new [Builder]. */
    @JvmStatic public fun builder(): Builder = Builder()

    /**
     * Disables automatic activity detection.
     *
     * When automatic activity detection is disabled, the user must send activity signals manually
     * using [LiveSession.sendStartActivityRealtime] and [LiveSession.sendStopActivityRealtime].
     */
    @JvmStatic
    public fun disabled(): ActivityDetectionConfig =
      ActivityDetectionConfig(
        startSensitivity = null,
        endSensitivity = null,
        prefixPaddingMs = null,
        silenceDurationMs = null,
        disabled = true
      )
  }
}

/** Helper method to construct an [ActivityDetectionConfig] in a DSL-like manner. */
@OptIn(PublicPreviewAPI::class)
public fun activityDetectionConfig(
  init: ActivityDetectionConfig.Builder.() -> Unit
): ActivityDetectionConfig {
  val builder = ActivityDetectionConfig.builder()
  builder.init()
  return builder.build()
}
