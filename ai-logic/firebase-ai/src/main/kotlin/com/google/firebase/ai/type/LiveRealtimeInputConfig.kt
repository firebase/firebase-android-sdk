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
 * Configures model input behavior when generating content in the Live API via realtime supported
 * methods.
 *
 * @property automaticActivityDetection Configures automatic activity detection on the model. When
 * not set, automatic activity detection is enabled by default. If set, the user must send activity
 * signals.
 * @property activityHandling Defines how the model treats user input activity.
 * @property turnCoverage Defines which input is included in the user's turn, relative to the
 * starting and ending of the activity.
 */
@PublicPreviewAPI
public class LiveRealtimeInputConfig
private constructor(
  internal val automaticActivityDetection: LiveActivityDetection?,
  internal val activityHandling: ActivityHandling?,
  internal val turnCoverage: TurnCoverage?
) {

  /** How a model handles user input activity. */
  public class ActivityHandling private constructor(internal val value: String) {
    public companion object {
      /**
       * When the user sends input marking the start of activity, the model's current response will
       * be cut-off immediately.
       */
      @JvmField
      public val INTERRUPT: ActivityHandling = ActivityHandling("START_OF_ACTIVITY_INTERRUPTS")

      /**
       * When the user sends input marking the start of activity, the model will process it, but
       * won't cut-off its current response.
       */
      @JvmField public val NO_INTERRUPT: ActivityHandling = ActivityHandling("NO_INTERRUPTION")
    }
  }

  /** How the model considers which input is included in the user's turn. */
  public class TurnCoverage private constructor(internal val value: String) {
    public companion object {
      /**
       * The model will exclude inactivity (e.g, silence on the audio stream) from the user's input.
       */
      @JvmField public val ONLY_ACTIVITY: TurnCoverage = TurnCoverage("TURN_INCLUDES_ONLY_ACTIVITY")

      /**
       * The model will include all input (including inactivity) since the last turn as the user's
       * input.
       */
      @JvmField public val ALL_INPUT: TurnCoverage = TurnCoverage("TURN_INCLUDES_ALL_INPUT")
    }
  }

  /** Builder for creating a [LiveRealtimeInputConfig]. */
  public class Builder {
    @JvmField public var automaticActivityDetection: LiveActivityDetection? = null
    @JvmField public var activityHandling: ActivityHandling? = null
    @JvmField public var turnCoverage: TurnCoverage? = null

    public fun setAutomaticActivityDetection(config: LiveActivityDetection): Builder = apply {
      this.automaticActivityDetection = config
    }

    public fun setActivityHandling(handling: ActivityHandling): Builder = apply {
      this.activityHandling = handling
    }

    public fun setTurnCoverage(coverage: TurnCoverage): Builder = apply {
      this.turnCoverage = coverage
    }

    /** Create a new [LiveRealtimeInputConfig] with the attached arguments. */
    public fun build(): LiveRealtimeInputConfig =
      LiveRealtimeInputConfig(automaticActivityDetection, activityHandling, turnCoverage)
  }

  internal fun toInternal(): Internal =
    Internal(
      automaticActivityDetection = automaticActivityDetection?.toInternal(),
      activityHandling = activityHandling?.value,
      turnCoverage = turnCoverage?.value
    )

  @Serializable
  internal data class Internal(
    @SerialName("automatic_activity_detection")
    val automaticActivityDetection: LiveActivityDetection.Internal? = null,
    @SerialName("activity_handling") val activityHandling: String? = null,
    @SerialName("turn_coverage") val turnCoverage: String? = null
  )

  public companion object {
    /** Creates a new [Builder]. */
    @JvmStatic public fun builder(): Builder = Builder()
  }
}

/** Helper method to construct a [LiveRealtimeInputConfig] in a DSL-like manner. */
@OptIn(PublicPreviewAPI::class)
public fun liveRealtimeInputConfig(
  init: LiveRealtimeInputConfig.Builder.() -> Unit
): LiveRealtimeInputConfig {
  val builder = LiveRealtimeInputConfig.builder()
  builder.init()
  return builder.build()
}
