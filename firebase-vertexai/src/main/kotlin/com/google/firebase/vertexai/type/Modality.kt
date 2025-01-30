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

import com.google.firebase.vertexai.common.util.FirstOrdinalSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

public class Modality private constructor(public val ordinal: Int) {

  @Serializable(Internal.Serializer::class)
  internal enum class Internal {
    @SerialName("MODALITY_UNSPECIFIED") UNSPECIFIED,
    TEXT,
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT;

    internal object Serializer : KSerializer<Internal> by FirstOrdinalSerializer(Internal::class)

    internal fun toPublic() =
      when (this) {
        TEXT -> Modality.TEXT
        IMAGE -> Modality.IMAGE
        VIDEO -> Modality.VIDEO
        AUDIO -> Modality.AUDIO
        DOCUMENT -> Modality.DOCUMENT
        else -> Modality.UNSPECIFIED
      }
  }

  public companion object {
    @JvmField public val UNSPECIFIED: Modality = Modality(0)

    /** Plain text. */
    @JvmField public val TEXT: Modality = Modality(1)

    /** Image. */
    @JvmField public val IMAGE: Modality = Modality(2)

    /** Video. */
    @JvmField public val VIDEO: Modality = Modality(3)

    /** Audio. */
    @JvmField public val AUDIO: Modality = Modality(4)

    /** Document, e.g. PDF. */
    @JvmField public val DOCUMENT: Modality = Modality(5)
  }
}
