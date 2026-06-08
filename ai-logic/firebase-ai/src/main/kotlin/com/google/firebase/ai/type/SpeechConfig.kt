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
 * Configures a participating speaker within a multi-speaker setup.
 *
 * When generating multi-speaker conversational audio, each speaker must be configured with a unique
 * name and a specific voice. Find the list of
 * [supported voices](https://cloud.google.com/text-to-speech/docs/chirp3-hd).
 *
 * @property speaker The unique name/identifier of the speaker (e.g., `"Alice"`).
 * @property voice The specific [Voice] assigned to this speaker.
 */
@PublicPreviewAPI
@Serializable
public data class SpeakerVoiceConfig(
  public val speaker: String,
  public val voice: Voice,
) {
  internal fun toInternal() =
    Internal(
      speaker = speaker,
      voiceConfig = voice.toInternal().let { VoiceConfigInternal(it) },
    )

  @Serializable
  internal data class Internal(
    val speaker: String,
    @SerialName("voice_config") val voiceConfig: VoiceConfigInternal,
  )
}

/**
 * Configuration for a multi-speaker audio generation setup.
 *
 * Enables the model to generate audio containing multiple distinct speakers, alternating voices
 * dynamically based on speaker labels in the prompt.
 *
 * **Note:** Multi-speaker configurations are not supported by the Live API (e.g.,
 * [LiveGenerationConfig]), and will be silently ignored by the backend.
 *
 * @property speakerVoiceConfigs A list of voice configurations for the participating speakers.
 *
 * Currently, the backend requires exactly **two** speaker voice configurations.
 */
@PublicPreviewAPI
@Serializable
public data class MultiSpeakerVoiceConfig(
  public val speakerVoiceConfigs: List<SpeakerVoiceConfig>,
) {
  internal fun toInternal() =
    Internal(speakerVoiceConfigs = speakerVoiceConfigs.map { it.toInternal() })

  @Serializable
  internal data class Internal(
    @SerialName("speaker_voice_configs") val speakerVoiceConfigs: List<SpeakerVoiceConfig.Internal>,
  )
}

/**
 * Speech configuration class for controlling the model's speech and audio generation behaviors.
 *
 * This allows you to configure the voice properties (single-speaker OR multi-speaker setup) and
 * language preferences when requesting the model to generate spoken responses.
 *
 * @property voice The single-speaker [Voice] configuration.
 * @property multiSpeakerVoiceConfig The multi-speaker configuration. Note that this configuration
 * is not supported by the Live API (e.g., [LiveGenerationConfig]) and will be silently ignored.
 * @property languageCode The optional IETF BCP-47 language code (e.g., `"en-US"`, `"es-ES"`) used
 * to guide the model's speech synthesis and recognition.
 */
@PublicPreviewAPI
public class SpeechConfig
private constructor(
  public val voice: Voice? = null,
  public val multiSpeakerVoiceConfig: MultiSpeakerVoiceConfig? = null,
  public val languageCode: String? = null,
) {

  /**
   * Constructs a [SpeechConfig] for a single-speaker setup.
   *
   * @param voice The specific [Voice] to use for speech generation.
   * @param languageCode An optional IETF BCP-47 language code to guide speech generation.
   */
  @JvmOverloads
  public constructor(
    voice: Voice,
    languageCode: String? = null,
  ) : this(voice = voice, multiSpeakerVoiceConfig = null, languageCode = languageCode)

  /**
   * Constructs a [SpeechConfig] for a multi-speaker setup.
   *
   * Note: Multi-speaker configurations are not supported by the Live API (e.g.,
   * [LiveGenerationConfig]), and will be silently ignored by the backend.
   *
   * @param multiSpeakerVoiceConfig The configuration detailing multiple speakers and their
   * corresponding voices.
   * @param languageCode An optional IETF BCP-47 language code to guide speech generation.
   */
  @JvmOverloads
  public constructor(
    multiSpeakerVoiceConfig: MultiSpeakerVoiceConfig,
    languageCode: String? = null,
  ) : this(
    voice = null,
    multiSpeakerVoiceConfig = multiSpeakerVoiceConfig,
    languageCode = languageCode
  )

  @Serializable
  internal data class Internal(
    @SerialName("voice_config") val voiceConfig: VoiceConfigInternal? = null,
    @SerialName("multi_speaker_voice_config")
    val multiSpeakerVoiceConfig: MultiSpeakerVoiceConfig.Internal? = null,
    @SerialName("language_code") val languageCode: String? = null,
  )

  internal fun toInternal(): Internal {
    return Internal(
      voiceConfig = voice?.toInternal()?.let { VoiceConfigInternal(it) },
      multiSpeakerVoiceConfig = multiSpeakerVoiceConfig?.toInternal(),
      languageCode = languageCode,
    )
  }
}
