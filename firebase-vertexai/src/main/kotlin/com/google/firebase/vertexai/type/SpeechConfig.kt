/*
 * Copyright 2025 Google LLC
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

package com.google.firebase.vertexai.type

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Speech configuration class for setting up the voice of the server's response. */
@PublicPreviewAPI
@Deprecated(
  """The Vertex AI in Firebase SDK (firebase-vertexai) has been replaced with the FirebaseAI SDK (firebase-ai) to accommodate the evolving set of supported features and services.
For migration details, see the migration guide: https://firebase.google.com/docs/vertex-ai/migrate-to-latest-sdk"""
)
public class SpeechConfig(
  /** The voice to be used for the server's speech response. */
  public val voice: Voices
) {

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
