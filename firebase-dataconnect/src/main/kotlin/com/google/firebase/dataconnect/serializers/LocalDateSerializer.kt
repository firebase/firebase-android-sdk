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
 *
 * @see JavaTimeLocalDateSerializer
 * @see KotlinxDatetimeLocalDateSerializer
 */
public object LocalDateSerializer : KSerializer<LocalDate> {

  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("com.google.firebase.dataconnect.LocalDate", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: LocalDate) {
    val serializedDate: String = serializeToString(value)
    encoder.encodeString(serializedDate)
  }

  override fun deserialize(decoder: Decoder): LocalDate {
    val decodedString = decoder.decodeString()
    return deserializeToLocalDate(decodedString)
  }

  private val decodeRegexPattern = Pattern.compile("^(-?\\d+)-(-?\\d+)-(-?\\d+)$")

  private fun deserializeToLocalDate(string: String): LocalDate {
    val matcher = decodeRegexPattern.matcher(string)
    require(matcher.matches()) {
      "date \"$string\" does not match regular expression: ${matcher.pattern()}"
    }

    fun Matcher.groupToIntIgnoringLeadingZeroes(index: Int): Int {
      val groupText =
        group(index)
          ?: throw IllegalStateException(
            "internal error: group(index) should not be null " +
              " (index=$index, string=$string, matcher=$this, error code hp48d53pbb)"
          )

      val isNegative = groupText.firstOrNull() == '-'

      val zeroPaddedString =
        if (isNegative) {
          groupText.substring(1)
        } else {
          groupText
        }

      val intAbsString = zeroPaddedString.trimStart('0')
      val intStringPrefix = if (isNegative) "-" else ""
      val intString = intStringPrefix + intAbsString
      if (intString.isEmpty()) {
        return 0
      }

      return intString.toInt()
    }

    val year = matcher.groupToIntIgnoringLeadingZeroes(1)
    val month = matcher.groupToIntIgnoringLeadingZeroes(2)
    val day = matcher.groupToIntIgnoringLeadingZeroes(3)

    return LocalDate(year = year, month = month, day = day)
  }

  private fun serializeToString(localDate: LocalDate): String {
    val yearStr = localDate.year.toZeroPaddedString(length = 4)
    val monthStr = localDate.month.toZeroPaddedString(length = 2)
    val dayStr = localDate.day.toZeroPaddedString(length = 2)
    return "$yearStr-$monthStr-$dayStr"
  }

  private fun Int.toZeroPaddedString(length: Int): String = buildString {
    append(this@toZeroPaddedString)

    val firstChar =
      firstOrNull()?.let {
        if (it == '-') {
          deleteCharAt(0)
          it
        } else {
          null
        }
      }

    while (this.length < length) {
      insert(0, '0')
    }

    if (firstChar != null) {
      insert(0, firstChar)
    }
  }
}
