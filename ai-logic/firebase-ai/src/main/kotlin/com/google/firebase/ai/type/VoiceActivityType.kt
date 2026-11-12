/*
 * Copyright 2026 Google LLC
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

import android.util.Log

/**
 * Represents the type of server-detected voice activity signal.
 *
 * @see LiveServerVoiceActivity
 */
@PublicPreviewAPI
public class VoiceActivityType private constructor(internal val value: String) {
  override fun equals(other: Any?): Boolean = (other as? VoiceActivityType)?.value == value

  override fun hashCode(): Int = value.hashCode()

  override fun toString(): String = "VoiceActivityType($value)"

  public companion object {
    /** Unknown or unspecified voice activity signal. */
    @JvmField public val UNKNOWN: VoiceActivityType = VoiceActivityType("TYPE_UNSPECIFIED")

    /** Start of user speech signal. */
    @JvmField public val ACTIVITY_START: VoiceActivityType = VoiceActivityType("ACTIVITY_START")

    /** End of user speech signal. */
    @JvmField public val ACTIVITY_END: VoiceActivityType = VoiceActivityType("ACTIVITY_END")

    internal fun fromValue(value: String?): VoiceActivityType =
      when (value) {
        null,
        UNKNOWN.value -> UNKNOWN
        ACTIVITY_START.value -> ACTIVITY_START
        ACTIVITY_END.value -> ACTIVITY_END
        else -> {
          Log.w("VoiceActivityType", "Unknown VoiceActivityType value: $value")
          UNKNOWN
        }
      }
  }
}
