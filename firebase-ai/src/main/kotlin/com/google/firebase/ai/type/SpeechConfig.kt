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
 * Various voices supported by the server. In the documentation, find the list of
 * [all supported voices](https://cloud.google.com/text-to-speech/docs/chirp3-hd).
 */
@PublicPreviewAPI
@Serializable
public data class SpeakerVoiceConfig(
  public val speaker: String,
  public val voiceConfig: Voice,
) {
  internal fun toInternal() =
    Internal(
      speaker = speaker,
      voiceConfig = voiceConfig.toInternal().let { Voice.Internal.VoiceConfigInternal(it) },
    )

  @Serializable
  internal data class Internal(
    val speaker: String,
    @SerialName("voice_config") val voiceConfig: Voice.Internal.VoiceConfigInternal,
  )
}

/** The configuration for the multi-speaker setup. */
@PublicPreviewAPI
@Serializable
public data class MultiSpeakerVoiceConfig(
  public val speakerVoiceConfigs: List<SpeakerVoiceConfig>,
) {
  internal fun toInternal() = Internal(speakerVoiceConfigs = speakerVoiceConfigs.map { it.toInternal() })

  @Serializable
  internal data class Internal(
    @SerialName("speaker_voice_configs") val speakerVoiceConfigs: List<SpeakerVoiceConfig.Internal>,
  )
}

/** Speech configuration class for setting up the voice of the server's response. */
@PublicPreviewAPI
public class SpeechConfig
private constructor(
  public val voice: Voice? = null,
  public val multiSpeakerVoiceConfig: MultiSpeakerVoiceConfig? = null,
  public val languageCode: String? = null,
) {

  public constructor(
    voice: Voice,
    languageCode: String? = null,
  ) : this(voice = voice, multiSpeakerVoiceConfig = null, languageCode = languageCode)

  public constructor(
    multiSpeakerVoiceConfig: MultiSpeakerVoiceConfig,
    languageCode: String? = null,
  ) : this(voice = null, multiSpeakerVoiceConfig = multiSpeakerVoiceConfig, languageCode = languageCode)

  @Serializable
  internal data class Internal(
    @SerialName("voice_config") val voiceConfig: Voice.Internal.VoiceConfigInternal? = null,
    @SerialName("multi_speaker_voice_config")
    val multiSpeakerVoiceConfig: MultiSpeakerVoiceConfig.Internal? = null,
    @SerialName("language_code") val languageCode: String? = null,
  )

  internal fun toInternal(): Internal {
    return Internal(
      voiceConfig = voice?.toInternal()?.let { Voice.Internal.VoiceConfigInternal(it) },
      multiSpeakerVoiceConfig = multiSpeakerVoiceConfig?.toInternal(),
      languageCode = languageCode,
    )
  }
}
