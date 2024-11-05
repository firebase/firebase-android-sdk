/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.dataconnect.serializers

import com.google.firebase.dataconnect.LocalDate
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * An implementation of [KSerializer] for serializing and deserializing [LocalDate] objects in the
 * wire format expected by the Firebase Data Connect backend.
 */
public object LocalDateSerializer : KSerializer<LocalDate> {

  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("com.google.firebase.dataconnect.LocalDate", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: LocalDate) {
    value.run {
      require(year >= 0) { "invalid value: $value (year must be non-negative)" }
      require(month >= 0) { "invalid value: $value (month must be non-negative)" }
      require(day >= 0) { "invalid value: $value (day must be non-negative)" }
    }
    val serializedDate =
      "${value.year}".padStart(4, '0') +
        '-' +
        "${value.month}".padStart(2, '0') +
        '-' +
        "${value.day}".padStart(2, '0')
    encoder.encodeString(serializedDate)
  }

  override fun deserialize(decoder: Decoder): LocalDate {
    val decodedString = decoder.decodeString()
    val matcher = Pattern.compile("^(\\d+)-(\\d+)-(\\d+)$").matcher(decodedString)
    require(matcher.matches()) {
      "date \"$decodedString\" does not match regular expression: ${matcher.pattern()}"
    }

    fun Matcher.groupToIntIgnoringLeadingZeroes(index: Int): Int {
      val groupText = group(index)!!.trimStart('0')
      return if (groupText.isEmpty()) 0 else groupText.toInt()
    }

    val year = matcher.groupToIntIgnoringLeadingZeroes(1)
    val month = matcher.groupToIntIgnoringLeadingZeroes(2)
    val day = matcher.groupToIntIgnoringLeadingZeroes(3)

    return LocalDate(year = year, month = month, day = day)
  }
}
