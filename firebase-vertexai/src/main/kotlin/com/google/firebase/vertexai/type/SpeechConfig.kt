package com.google.firebase.vertexai.type

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Speech configuration class for setting up the voice of the server's response. */
public class SpeechConfig(public val voice: Voices) {

  @Serializable
  internal data class Internal(@SerialName("voice_config") val voiceConfig: VoiceConfigInternal) {
    @Serializable
    internal data class VoiceConfigInternal(
      @SerialName("prebuilt_voice_config") val prebuiltVoiceConfig: Voices.Internal,
    )
  }

  internal fun toInternal(): Internal {
    return Internal(Internal.VoiceConfigInternal(prebuiltVoiceConfig = voice.toInternal()))
  }
}
