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
import kotlinx.serialization.Serializable

/** Represents the type of content present in a response (e.g., text, image, audio). */
public class ResponseModality private constructor(public val ordinal: Int) {

  @Serializable(Internal.Serializer::class)
  internal enum class Internal {
    TEXT,
    IMAGE,
    AUDIO;

    internal object Serializer : KSerializer<Internal> by FirstOrdinalSerializer(Internal::class)

    internal fun toPublic() =
      when (this) {
        TEXT -> ResponseModality.TEXT
        IMAGE -> ResponseModality.IMAGE
        else -> ResponseModality.AUDIO
      }
  }

  internal fun toInternal() =
    when (this) {
      TEXT -> "TEXT"
      IMAGE -> "IMAGE"
      else -> "AUDIO"
    }
  public companion object {

    /** Represents a plain text response modality. */
    @JvmField public val TEXT: ResponseModality = ResponseModality(1)

    /** Represents an image response modality. */
    @JvmField public val IMAGE: ResponseModality = ResponseModality(2)

    /** Represents an audio response modality. */
    @JvmField public val AUDIO: ResponseModality = ResponseModality(4)
  }
}
