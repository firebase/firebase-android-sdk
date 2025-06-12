/*
 * Copyright 2024 Google LLC
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

/** Represents the severity of a [HarmCategory] being applicable in a [SafetyRating]. */
@Deprecated(
  """The Vertex AI in Firebase SDK (firebase-vertexai) has been replaced with the FirebaseAI SDK (firebase-ai) to accommodate the evolving set of supported features and services.
For migration details, see the migration guide: https://firebase.google.com/docs/vertex-ai/migrate-to-latest-sdk"""
)
public class HarmSeverity private constructor(public val ordinal: Int) {
  @Serializable(Internal.Serializer::class)
  internal enum class Internal {
    UNKNOWN,
    @SerialName("HARM_SEVERITY_UNSPECIFIED") UNSPECIFIED,
    @SerialName("HARM_SEVERITY_NEGLIGIBLE") NEGLIGIBLE,
    @SerialName("HARM_SEVERITY_LOW") LOW,
    @SerialName("HARM_SEVERITY_MEDIUM") MEDIUM,
    @SerialName("HARM_SEVERITY_HIGH") HIGH;

    internal object Serializer : KSerializer<Internal> by FirstOrdinalSerializer(Internal::class)

    internal fun toPublic() =
      when (this) {
        HIGH -> HarmSeverity.HIGH
        MEDIUM -> HarmSeverity.MEDIUM
        LOW -> HarmSeverity.LOW
        NEGLIGIBLE -> HarmSeverity.NEGLIGIBLE
        else -> HarmSeverity.UNKNOWN
      }
  }
  public companion object {
    /** A new and not yet supported value. */
    @JvmField public val UNKNOWN: HarmSeverity = HarmSeverity(0)

    /** Severity for harm is negligible. */
    @JvmField public val NEGLIGIBLE: HarmSeverity = HarmSeverity(1)

    /** Low level of harm severity. */
    @JvmField public val LOW: HarmSeverity = HarmSeverity(2)

    /** Medium level of harm severity. */
    @JvmField public val MEDIUM: HarmSeverity = HarmSeverity(3)

    /** High level of harm severity. */
    @JvmField public val HIGH: HarmSeverity = HarmSeverity(4)
  }
}
