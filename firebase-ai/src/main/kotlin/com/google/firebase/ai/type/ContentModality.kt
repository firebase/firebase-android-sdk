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

import com.google.firebase.ai.common.util.FirstOrdinalSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Content part modality. */
public class ContentModality private constructor(public val ordinal: Int) {

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
        TEXT -> ContentModality.TEXT
        IMAGE -> ContentModality.IMAGE
        VIDEO -> ContentModality.VIDEO
        AUDIO -> ContentModality.AUDIO
        DOCUMENT -> ContentModality.DOCUMENT
        else -> ContentModality.UNSPECIFIED
      }
  }

  internal fun toInternal() =
    when (this) {
      TEXT -> "TEXT"
      IMAGE -> "IMAGE"
      VIDEO -> "VIDEO"
      AUDIO -> "AUDIO"
      DOCUMENT -> "DOCUMENT"
      else -> "UNSPECIFIED"
    }
  public companion object {
    /** Unspecified modality. */
    @JvmField public val UNSPECIFIED: ContentModality = ContentModality(0)

    /** Plain text. */
    @JvmField public val TEXT: ContentModality = ContentModality(1)

    /** Image. */
    @JvmField public val IMAGE: ContentModality = ContentModality(2)

    /** Video. */
    @JvmField public val VIDEO: ContentModality = ContentModality(3)

    /** Audio. */
    @JvmField public val AUDIO: ContentModality = ContentModality(4)

    /** Document, for example, PDF. */
    @JvmField public val DOCUMENT: ContentModality = ContentModality(5)
  }
}
