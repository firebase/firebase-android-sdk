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
import kotlinx.serialization.encodeToString
import org.junit.Test

@OptIn(PublicPreviewAPI::class)
internal class LiveGenerationConfigTest {

  @Test
  fun `LiveGenerationConfig with SpeechConfig voice only`() {
    val config = liveGenerationConfig { speechConfig = SpeechConfig(voice = Voice("Aoede")) }

    val expectedJson =
      """
      {
        "speech_config": {
          "voice_config": {
            "prebuilt_voice_config": {
              "voice_name": "Aoede"
            }
          }
        }
      }
    """
        .trimIndent()

    JSON.encodeToString(config.toInternal()).shouldEqualJson(expectedJson)
  }

  @Test
  fun `LiveGenerationConfig with SpeechConfig voice and languageCode`() {
    val config = liveGenerationConfig {
      speechConfig = SpeechConfig(voice = Voice("Aoede"), languageCode = "en-US")
    }

    val expectedJson =
      """
      {
        "speech_config": {
          "language_code": "en-US",
          "voice_config": {
            "prebuilt_voice_config": {
              "voice_name": "Aoede"
            }
          }
        }
      }
    """
        .trimIndent()

    JSON.encodeToString(config.toInternal()).shouldEqualJson(expectedJson)
  }

  @Test
  fun `LiveGenerationConfig with SpeechConfig multiSpeaker and languageCode`() {
    val config = liveGenerationConfig {
      speechConfig =
        SpeechConfig(
          multiSpeakerVoiceConfig =
            MultiSpeakerVoiceConfig(
              listOf(
                SpeakerVoiceConfig("Speaker1", Voice("Puck")),
                SpeakerVoiceConfig("Speaker2", Voice("Charon"))
              )
            ),
          languageCode = "en-US"
        )
    }

    val expectedJson =
      """
      {
        "speech_config": {
          "multi_speaker_voice_config": {
            "speaker_voice_configs": [
              {
                "speaker": "Speaker1",
                "voice_config": {
                  "prebuilt_voice_config": {
                    "voice_name": "Puck"
                  }
                }
              },
              {
                "speaker": "Speaker2",
                "voice_config": {
                  "prebuilt_voice_config": {
                    "voice_name": "Charon"
                  }
                }
              }
            ]
          },
          "language_code": "en-US"
        }
      }
    """
        .trimIndent()

    JSON.encodeToString(config.toInternal()).shouldEqualJson(expectedJson)
  }
}
