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

import com.google.firebase.ai.common.JSON
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.serialization.encodeToString
import org.junit.Test

@OptIn(PublicPreviewAPI::class)
internal class LiveRealtimeInputConfigTest {

  @Test
  fun `Basic LiveRealtimeInputConfig`() {
    val config = liveRealtimeInputConfig {
      activityHandling = LiveRealtimeInputConfig.ActivityHandling.NO_INTERRUPT
      turnCoverage = LiveRealtimeInputConfig.TurnCoverage.ONLY_ACTIVITY
      automaticActivityDetection = liveActivityDetection { disabled = true }
    }

    val expectedJson =
      """
      {
          "activity_handling": "NO_INTERRUPTION",
          "turn_coverage": "TURN_INCLUDES_ONLY_ACTIVITY",
          "automatic_activity_detection": {
              "disabled": true
          }
      }
      """
        .trimIndent()

    JSON.encodeToString(config.toInternal()).shouldEqualJson(expectedJson)
  }

  @Test
  fun `LiveActivityDetection full config`() {
    val detection = liveActivityDetection {
      startSensitivity = LiveActivityDetection.Sensitivity.HIGH
      endSensitivity = LiveActivityDetection.Sensitivity.LOW
      prefixPaddingMS = 100
      silenceDurationMS = 500
      disabled = false
    }

    val expectedJson =
      """
      {
          "start_of_speech_sensitivity": "START_SENSITIVITY_HIGH",
          "end_of_speech_sensitivity": "END_SENSITIVITY_LOW",
          "prefix_padding_ms": 100,
          "silence_duration_ms": 500,
          "disabled": false
      }
      """
        .trimIndent()

    JSON.encodeToString(detection.toInternal()).shouldEqualJson(expectedJson)
  }

  @Test
  fun `DSL correctly delegates to Builder`() {
    val config =
      LiveRealtimeInputConfig.builder()
        .setActivityHandling(LiveRealtimeInputConfig.ActivityHandling.INTERRUPT)
        .build()

    val configDsl = liveRealtimeInputConfig {
      activityHandling = LiveRealtimeInputConfig.ActivityHandling.INTERRUPT
    }

    config.activityHandling?.shouldBeEqual(
      configDsl.activityHandling as LiveRealtimeInputConfig.ActivityHandling
    )
  }

  @Test
  fun `LiveClientSetupMessage includes LiveRealtimeInputConfig in serialization`() {
    val config = liveGenerationConfig {
      realtimeInputConfig = liveRealtimeInputConfig {
        activityHandling = LiveRealtimeInputConfig.ActivityHandling.NO_INTERRUPT
      }
    }
    val message =
      LiveClientSetupMessage(
        model = "my-model",
        generationConfig = null,
        tools = null,
        systemInstruction = null,
        inputAudioTranscription = null,
        outputAudioTranscription = null,
        sessionResumption = null,
        contextWindowCompression = null,
        realtimeInputConfig = config.realtimeInputConfig?.toInternal()
      )

    val expectedJson =
      """
      {
          "setup": {
              "model": "my-model",
              "realtime_input_config": {
                  "activity_handling": "NO_INTERRUPTION"
              }
          }
      }
      """
        .trimIndent()

    JSON.encodeToString(message.toInternal()).shouldEqualJson(expectedJson)
  }

  @Test
  fun `BidiGenerateContentRealtimeInputSetup with activityStart serializes correctly`() {
    val setup = LiveSession.BidiGenerateContentRealtimeInputSetup(activityStart = true)

    val expectedJson =
      """
      {
          "realtimeInput": {
              "activity_start": {}
          }
      }
      """
        .trimIndent()

    JSON.encodeToString(setup.toInternal()).shouldEqualJson(expectedJson)
  }

  @Test
  fun `BidiGenerateContentRealtimeInputSetup with activityEnd serializes correctly`() {
    val setup = LiveSession.BidiGenerateContentRealtimeInputSetup(activityEnd = true)

    val expectedJson =
      """
      {
          "realtimeInput": {
              "activity_end": {}
          }
      }
      """
        .trimIndent()

    JSON.encodeToString(setup.toInternal()).shouldEqualJson(expectedJson)
  }
}
