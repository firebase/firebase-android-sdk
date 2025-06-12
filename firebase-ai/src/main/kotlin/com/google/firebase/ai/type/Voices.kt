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

package com.google.firebase.ai.type

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Various voices supported by the server */
@Deprecated("Use the Voice class instead.", ReplaceWith("Voice"))
@PublicPreviewAPI
public class Voices private constructor(public val ordinal: Int) {

  @Serializable internal data class Internal(@SerialName("voice_name") val voiceName: String)

  @Serializable
  internal enum class InternalEnum {
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
        else -> Voices.PUCK
      }
  }

  internal fun toInternal(): Internal {
    return when (this) {
      CHARON -> Internal(InternalEnum.CHARON.name)
      AOEDE -> Internal(InternalEnum.AOEDE.name)
      FENRIR -> Internal(InternalEnum.FENRIR.name)
      KORE -> Internal(InternalEnum.KORE.name)
      else -> Internal(InternalEnum.PUCK.name)
    }
  }

  public companion object {
    /**
     * Unspecified voice.
     *
     * Will use the default voice of the model.
     */
    @JvmField public val UNSPECIFIED: Voices = Voices(0)

    /** Represents the Charon voice. */
    @JvmField public val CHARON: Voices = Voices(1)

    /** Represents the Aoede voice. */
    @JvmField public val AOEDE: Voices = Voices(2)

    /** Represents the Fenrir voice. */
    @JvmField public val FENRIR: Voices = Voices(3)

    /** Represents the Kore voice. */
    @JvmField public val KORE: Voices = Voices(4)

    /** Represents the Puck voice. */
    @JvmField public val PUCK: Voices = Voices(5)
  }
}
