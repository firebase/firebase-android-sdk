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
import com.google.firebase.ai.type.RealtimeInputConfig.ActivityHandling
import com.google.firebase.ai.type.RealtimeInputConfig.TurnCoverage
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.serialization.encodeToString
import org.junit.Test

@OptIn(PublicPreviewAPI::class)
internal class RealtimeInputConfigTest {

  @Test
  fun `Basic RealtimeInputConfig`() {
    val config = realtimeInputConfig {
      activityHandling = ActivityHandling.NO_INTERRUPT
      turnCoverage = TurnCoverage.ONLY_ACTIVITY
      automaticActivityDetection = ActivityDetectionConfig.disabled()
    }

    val expectedJson =
      """
      {
          "activityHandling": "NO_INTERRUPTION",
          "turnCoverage": "TURN_INCLUDES_ONLY_ACTIVITY",
          "automaticActivityDetection": {
              "disabled": true
          }
      }
      """
        .trimIndent()

    JSON.encodeToString(config.toInternal()).shouldEqualJson(expectedJson)
  }

  @Test
  fun `ActivityDetectionConfig full config`() {
    val detection = activityDetectionConfig {
      startSensitivity = ActivityDetectionConfig.Sensitivity.HIGH
      endSensitivity = ActivityDetectionConfig.Sensitivity.LOW
      prefixPaddingMs = 100
      silenceDurationMs = 500
    }

    val expectedJson =
      """
      {
          "startOfSpeechSensitivity": "START_SENSITIVITY_HIGH",
          "endOfSpeechSensitivity": "END_SENSITIVITY_LOW",
          "prefixPaddingMs": 100,
          "silenceDurationMs": 500
      }
      """
        .trimIndent()

    JSON.encodeToString(detection.toInternal()).shouldEqualJson(expectedJson)
  }

  @Test
  fun `DSL correctly delegates to Builder`() {
    val config =
      RealtimeInputConfig.builder().setActivityHandling(ActivityHandling.INTERRUPT).build()

    val configDsl = realtimeInputConfig { activityHandling = ActivityHandling.INTERRUPT }

    config.activityHandling?.shouldBeEqual(configDsl.activityHandling as ActivityHandling)
  }

  @Test
  fun `LiveClientSetupMessage includes RealtimeInputConfig in serialization`() {
    val config = liveGenerationConfig {
      realtimeInputConfig = realtimeInputConfig { activityHandling = ActivityHandling.NO_INTERRUPT }
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
              "realtimeInputConfig": {
                  "activityHandling": "NO_INTERRUPTION"
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
              "activityStart": {}
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
              "activityEnd": {}
          }
      }
      """
        .trimIndent()

    JSON.encodeToString(setup.toInternal()).shouldEqualJson(expectedJson)
  }
}
