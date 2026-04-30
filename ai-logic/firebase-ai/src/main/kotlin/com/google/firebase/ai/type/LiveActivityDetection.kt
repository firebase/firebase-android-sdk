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
 * Configures the model's automatic detection of user activity.
 *
 * @property startSensitivity Determines how likely the start of speech is detected.
 * @property endSensitivity Determines how likely the end of speech is detected.
 * @property prefixPaddingMS How long detected speech should be present before start-of-speech is
 * committed. The lower this value, the more sensitive the start-of-speech detection is and the
 * shorter the speech that can be recognized. However, this also increases the probability of false
 * positives.
 * @property silenceDurationMS How long silence (or non-speech) should be present before
 * end-of-speech is committed. The larger this value, the longer speech gaps can be without
 * interrupting the user's activity, but this will increase the model's latency.
 * @property disabled Disables automatic activity detection. When automatic activity detection is
 * enabled, the model will interpret detected voices and text as the start of activity. When
 * automatic activity detection is disabled, the user must send activity signals manually.
 */
@PublicPreviewAPI
public class LiveActivityDetection
private constructor(
  internal val startSensitivity: Sensitivity?,
  internal val endSensitivity: Sensitivity?,
  internal val prefixPaddingMS: Int?,
  internal val silenceDurationMS: Int?,
  internal val disabled: Boolean?
) {

  /** How sensitive the model interprets speech activity. */
  public enum class Sensitivity {
    /**
     * The model will detect speech less often. In other words, a higher volume of speech is
     * required for the model to consider the user is speaking.
     */
    LOW,
    /**
     * The model will detect speech more often. In other words, a lower volume of speech is required
     * for the model to consider the user is speaking.
     */
    HIGH
  }

  /** Builder for creating a [LiveActivityDetection]. */
  public class Builder {
    @JvmField public var startSensitivity: Sensitivity? = null
    @JvmField public var endSensitivity: Sensitivity? = null
    @JvmField public var prefixPaddingMS: Int? = null
    @JvmField public var silenceDurationMS: Int? = null
    @JvmField public var disabled: Boolean? = null

    public fun setStartSensitivity(sensitivity: Sensitivity): Builder = apply {
      this.startSensitivity = sensitivity
    }

    public fun setEndSensitivity(sensitivity: Sensitivity): Builder = apply {
      this.endSensitivity = sensitivity
    }

    public fun setPrefixPaddingMS(paddingMs: Int): Builder = apply {
      this.prefixPaddingMS = paddingMs
    }

    public fun setSilenceDurationMS(durationMs: Int): Builder = apply {
      this.silenceDurationMS = durationMs
    }

    public fun setDisabled(disabled: Boolean): Builder = apply { this.disabled = disabled }

    /** Create a new [LiveActivityDetection] with the attached arguments. */
    public fun build(): LiveActivityDetection =
      LiveActivityDetection(
        startSensitivity,
        endSensitivity,
        prefixPaddingMS,
        silenceDurationMS,
        disabled
      )
  }

  internal fun toInternal(): Internal =
    Internal(
      startSensitivity =
        when (startSensitivity) {
          Sensitivity.LOW -> Internal.StartSensitivity.LOW
          Sensitivity.HIGH -> Internal.StartSensitivity.HIGH
          null -> null
        },
      endSensitivity =
        when (endSensitivity) {
          Sensitivity.LOW -> Internal.EndSensitivity.LOW
          Sensitivity.HIGH -> Internal.EndSensitivity.HIGH
          null -> null
        },
      prefixPaddingMs = prefixPaddingMS,
      silenceDurationMs = silenceDurationMS,
      disabled = disabled
    )

  @Serializable
  internal data class Internal(
    @SerialName("start_of_speech_sensitivity") val startSensitivity: StartSensitivity? = null,
    @SerialName("end_of_speech_sensitivity") val endSensitivity: EndSensitivity? = null,
    @SerialName("prefix_padding_ms") val prefixPaddingMs: Int? = null,
    @SerialName("silence_duration_ms") val silenceDurationMs: Int? = null,
    @SerialName("disabled") val disabled: Boolean? = null
  ) {
    @Serializable
    internal enum class StartSensitivity {
      @SerialName("START_SENSITIVITY_UNSPECIFIED") UNSPECIFIED,
      @SerialName("START_SENSITIVITY_HIGH") HIGH,
      @SerialName("START_SENSITIVITY_LOW") LOW
    }

    @Serializable
    internal enum class EndSensitivity {
      @SerialName("END_SENSITIVITY_UNSPECIFIED") UNSPECIFIED,
      @SerialName("END_SENSITIVITY_HIGH") HIGH,
      @SerialName("END_SENSITIVITY_LOW") LOW
    }
  }

  public companion object {
    /** Creates a new [Builder]. */
    @JvmStatic public fun builder(): Builder = Builder()
  }
}

/** Helper method to construct a [LiveActivityDetection] in a DSL-like manner. */
@OptIn(PublicPreviewAPI::class)
public fun liveActivityDetection(
  init: LiveActivityDetection.Builder.() -> Unit
): LiveActivityDetection {
  val builder = LiveActivityDetection.builder()
  builder.init()
  return builder.build()
}
