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
    val config = liveGenerationConfig { speechConfig = SpeechConfig(voice = Voice("Charon")) }

    val expectedJson =
      """
      {
        "speech_config": {
          "voiceConfig": {
            "prebuiltVoiceConfig": {
              "voiceName": "Charon"
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
      speechConfig = SpeechConfig(voice = Voice("Charon"), languageCode = "en-US")
    }

    val expectedJson =
      """
      {
        "speech_config": {
          "languageCode": "en-US",
          "voiceConfig": {
            "prebuiltVoiceConfig": {
              "voiceName": "Charon"
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
    // Note: Multi-speaker configurations are not supported by the Live API, and will
    // be silently ignored by the backend. However, we still support serialization
    // consistency across both standard and live generation configurations.
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
          "multiSpeakerVoiceConfig": {
            "speakerVoiceConfigs": [
              {
                "speaker": "Speaker1",
                "voiceConfig": {
                  "prebuiltVoiceConfig": {
                    "voiceName": "Puck"
                  }
                }
              },
              {
                "speaker": "Speaker2",
                "voiceConfig": {
                  "prebuiltVoiceConfig": {
                    "voiceName": "Charon"
                  }
                }
              }
            ]
          },
          "languageCode": "en-US"
        }
      }
    """
        .trimIndent()

    JSON.encodeToString(config.toInternal()).shouldEqualJson(expectedJson)
  }
}
