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

/** Modality for bidirectional streaming. */
@PublicPreviewAPI
public class ResponseModality private constructor(public val ordinal: Int) {

  @Serializable(Internal.Serializer::class)
  internal enum class Internal {
    @SerialName("MODALITY_UNSPECIFIED") UNSPECIFIED,
    TEXT,
    IMAGE,
    AUDIO;

    internal object Serializer : KSerializer<Internal> by FirstOrdinalSerializer(Internal::class)

    internal fun toPublic() =
      when (this) {
        TEXT -> ResponseModality.TEXT
        IMAGE -> ResponseModality.IMAGE
        AUDIO -> ResponseModality.AUDIO
        else -> ResponseModality.UNSPECIFIED
      }
  }

  internal fun toInternal() =
    when (this) {
      TEXT -> "TEXT"
      IMAGE -> "IMAGE"
      AUDIO -> "AUDIO"
      else -> "UNSPECIFIED"
    }
  public companion object {
    /** Unspecified modality. */
    @JvmField public val UNSPECIFIED: ResponseModality = ResponseModality(0)

    /** Plain text. */
    @JvmField public val TEXT: ResponseModality = ResponseModality(1)

    /** Image. */
    @JvmField public val IMAGE: ResponseModality = ResponseModality(2)

    /** Audio. */
    @JvmField public val AUDIO: ResponseModality = ResponseModality(4)
  }
}
