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

/** Various voices supported by the server */
public class Voices private constructor(public val ordinal: Int) {

  @Serializable internal data class Internal(@SerialName("voice_name") val voiceName: String)

  @Serializable
  internal enum class InternalEnum {
    @SerialName("VOICES_UNSPECIFIED") UNSPECIFIED,
    CHARON,
    AOEDE,
    FENRIR,
    KORE,
    PUCK;
    internal fun toPublic() =
      when (this) {
        CHARON -> Voices.CHARON
        AOEDE -> Voices.AOEDE
        FENRIR -> Voices.FENRIR
        KORE -> Voices.KORE
        PUCK -> Voices.PUCK
        else -> Voices.UNSPECIFIED
      }
  }

  internal fun toInternal(): Internal {
    return when (this) {
      CHARON -> Internal(InternalEnum.CHARON.name)
      AOEDE -> Internal(InternalEnum.AOEDE.name)
      FENRIR -> Internal(InternalEnum.FENRIR.name)
      KORE -> Internal(InternalEnum.KORE.name)
      PUCK -> Internal(InternalEnum.PUCK.name)
      else -> Internal(InternalEnum.UNSPECIFIED.name)
    }
  }

  public companion object {
    /** Unspecified modality. */
    @JvmField public val UNSPECIFIED: Voices = Voices(0)

    @JvmField public val CHARON: Voices = Voices(1)

    @JvmField public val AOEDE: Voices = Voices(2)

    @JvmField public val FENRIR: Voices = Voices(3)

    @JvmField public val KORE: Voices = Voices(4)

    @JvmField public val PUCK: Voices = Voices(5)
  }
}
