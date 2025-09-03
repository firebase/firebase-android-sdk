/*
 * Copyright 2023 Google LLC
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

import com.google.firebase.ai.common.makeMissingCaseException
import com.google.firebase.ai.common.util.FirstOrdinalSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Category for a given harm rating. */
public class HarmCategory private constructor(public val ordinal: Int) {
  internal fun toInternal() =
    when (this) {
      HARASSMENT -> Internal.HARASSMENT
      HATE_SPEECH -> Internal.HATE_SPEECH
      SEXUALLY_EXPLICIT -> Internal.SEXUALLY_EXPLICIT
      DANGEROUS_CONTENT -> Internal.DANGEROUS_CONTENT
      CIVIC_INTEGRITY -> Internal.CIVIC_INTEGRITY
      IMAGE_HATE -> Internal.IMAGE_HATE
      IMAGE_DANGEROUS_CONTENT -> Internal.IMAGE_DANGEROUS_CONTENT
      IMAGE_HARASSMENT -> Internal.IMAGE_HARASSMENT
      IMAGE_SEXUALLY_EXPLICIT -> Internal.IMAGE_SEXUALLY_EXPLICIT
      UNKNOWN -> Internal.UNKNOWN
      else -> throw makeMissingCaseException("HarmCategory", ordinal)
    }
  @Serializable(Internal.Serializer::class)
  internal enum class Internal {
    UNKNOWN,
    @SerialName("HARM_CATEGORY_HARASSMENT") HARASSMENT,
    @SerialName("HARM_CATEGORY_HATE_SPEECH") HATE_SPEECH,
    @SerialName("HARM_CATEGORY_SEXUALLY_EXPLICIT") SEXUALLY_EXPLICIT,
    @SerialName("HARM_CATEGORY_DANGEROUS_CONTENT") DANGEROUS_CONTENT,
    @SerialName("HARM_CATEGORY_CIVIC_INTEGRITY") CIVIC_INTEGRITY,
    @SerialName("HARM_CATEGORY_IMAGE_HATE") IMAGE_HATE,
    @SerialName("HARM_CATEGORY_IMAGE_DANGEROUS_CONTENT") IMAGE_DANGEROUS_CONTENT,
    @SerialName("HARM_CATEGORY_IMAGE_HARASSMENT") IMAGE_HARASSMENT,
    @SerialName("HARM_CATEGORY_IMAGE_SEXUALLY_EXPLICIT") IMAGE_SEXUALLY_EXPLICIT;

    internal object Serializer : KSerializer<Internal> by FirstOrdinalSerializer(Internal::class)

    internal fun toPublic() =
      when (this) {
        HARASSMENT -> HarmCategory.HARASSMENT
        HATE_SPEECH -> HarmCategory.HATE_SPEECH
        SEXUALLY_EXPLICIT -> HarmCategory.SEXUALLY_EXPLICIT
        DANGEROUS_CONTENT -> HarmCategory.DANGEROUS_CONTENT
        CIVIC_INTEGRITY -> HarmCategory.CIVIC_INTEGRITY
        IMAGE_HATE -> HarmCategory.IMAGE_HATE
        IMAGE_DANGEROUS_CONTENT -> HarmCategory.IMAGE_DANGEROUS_CONTENT
        IMAGE_HARASSMENT -> HarmCategory.IMAGE_HARASSMENT
        IMAGE_SEXUALLY_EXPLICIT -> HarmCategory.IMAGE_SEXUALLY_EXPLICIT
        else -> HarmCategory.UNKNOWN
      }
  }
  public companion object {
    /** A new and not yet supported value. */
    @JvmField public val UNKNOWN: HarmCategory = HarmCategory(0)

    /** Harassment content. */
    @JvmField public val HARASSMENT: HarmCategory = HarmCategory(1)

    /** Hate speech and content. */
    @JvmField public val HATE_SPEECH: HarmCategory = HarmCategory(2)

    /** Sexually explicit content. */
    @JvmField public val SEXUALLY_EXPLICIT: HarmCategory = HarmCategory(3)

    /** Dangerous content. */
    @JvmField public val DANGEROUS_CONTENT: HarmCategory = HarmCategory(4)

    /** Content that may be used to harm civic integrity. */
    @JvmField public val CIVIC_INTEGRITY: HarmCategory = HarmCategory(5)

    /** Content that may be used to harm civic integrity. */
    @JvmField public val IMAGE_HATE: HarmCategory = HarmCategory(6)

    /** Content that may be used to harm civic integrity. */
    @JvmField public val IMAGE_DANGEROUS_CONTENT: HarmCategory = HarmCategory(7)

    /** Content that may be used to harm civic integrity. */
    @JvmField public val IMAGE_HARASSMENT: HarmCategory = HarmCategory(8)

    /** Content that may be used to harm civic integrity. */
    @JvmField public val IMAGE_SEXUALLY_EXPLICIT: HarmCategory = HarmCategory(9)
  }
}
