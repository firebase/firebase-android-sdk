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

import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.TimeZone
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * An implementation of [KSerializer] for serializing and deserializing [Date] objects in the wire
 * format expected by the Firebase Data Connect backend.
 */
public object DateSerializer : KSerializer<Date> {

  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("Date", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: Date) {
    val calendar = GregorianCalendar(TimeZone.getTimeZone("UTC"))
    calendar.time = value

    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH) + 1
    val day = calendar.get(Calendar.DAY_OF_MONTH)

    val serializedDate =
      "$year".padStart(4, '0') + '-' + "$month".padStart(2, '0') + '-' + "$day".padStart(2, '0')
    encoder.encodeString(serializedDate)
  }

  override fun deserialize(decoder: Decoder): Date {
    val serializedDate = decoder.decodeString()

    val matcher = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})$").matcher(serializedDate)
    require(matcher.matches()) { "date does not match regular expression: ${matcher.pattern()}" }

    fun Matcher.groupToIntIgnoringLeadingZeroes(index: Int): Int {
      val groupText = group(index)!!.trimStart('0')
      return if (groupText.isEmpty()) 0 else groupText.toInt()
    }

    val year = matcher.groupToIntIgnoringLeadingZeroes(1)
    val month = matcher.groupToIntIgnoringLeadingZeroes(2)
    val day = matcher.groupToIntIgnoringLeadingZeroes(3)

    return GregorianCalendar(TimeZone.getTimeZone("UTC"))
      .apply {
        set(year, month - 1, day, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
      }
      .time
  }
}
