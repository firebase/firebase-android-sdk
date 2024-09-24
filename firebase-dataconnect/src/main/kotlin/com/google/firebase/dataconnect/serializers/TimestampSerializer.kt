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

import com.google.firebase.Timestamp
import java.text.DateFormat
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

// Googlers see go/firemat:timestamps for specifications.

/**
 * An implementation of [KSerializer] for serializing and deserializing [Timestamp] objects in the
 * wire format expected by the Firebase Data Connect backend.
 */
public object TimestampSerializer : KSerializer<Timestamp> {

  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("Timestamp", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: Timestamp) {
    val rfc3339String = TimestampSerializerImpl.timestampToString(value)
    encoder.encodeString(rfc3339String)
  }

  override fun deserialize(decoder: Decoder): Timestamp {
    val rfc3339String = decoder.decodeString()
    return TimestampSerializerImpl.timestampFromString(rfc3339String)
  }
}

internal object TimestampSerializerImpl {

  private val threadLocalDateFormatter =
    object : ThreadLocal<SimpleDateFormat>() {
      override fun initialValue(): SimpleDateFormat {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        return dateFormat
      }
    }

  private val dateFormatter: DateFormat
    get() = threadLocalDateFormatter.get()!!

  // TODO: Replace this implementation with Instant.parse() once minSdkVersion is bumped to at
  //  least 26 (Build.VERSION_CODES.O).
  fun timestampFromString(str: String): Timestamp {
    val strUppercase = str.uppercase()

    // If the timestamp string is 1985-04-12T23:20:50.123456789-07:00, the time-secfrac part
    // (.123456789) is optional. And time-offset part can either be Z or +xx:xx or -xx:xx.
    val regex =
      Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{0,9})?(Z|[+-]\\d{2}:\\d{2})$")

    require(strUppercase.matches(regex)) {
      "Value does not conform to the RFC3339 specification with up to 9 digits of time-secfrac precision (str=$str)."
    }

    val position = ParsePosition(0)
    val seconds = run {
      val date = dateFormatter.parse(strUppercase, position)
      requireNotNull(date)
      require(position.index == 19) {
        "position.index=${position.index}, but expected 19 (str=$str)"
      }
      Timestamp(date).seconds
    }

    // For time-secfrac part, when running against different databases, this precision might change,
    // and server will truncate it to 0/3/6 digits precision without throwing an error.
    var nanoseconds = 0
    // Parse the nanoseconds.
    if (strUppercase[position.index] == '.') {
      val nanoStrStart = ++position.index
      // We don't check for boundary since the string has pass the regex test.
      while (strUppercase[position.index].isDigit()) {
        position.index++
      }
      val nanosecondsStr = strUppercase.substring(nanoStrStart, position.index)
      nanoseconds = nanosecondsStr.padEnd(9, '0').toInt()
    }

    if (strUppercase[position.index] == 'Z') {
      return Timestamp(seconds, nanoseconds)
    }

    // Parse the +xx:xx or -xx:xx time-offset part.
    val addTimeDiffer = strUppercase[position.index] == '+'
    val hours = strUppercase.substring(position.index + 1, position.index + 3).toInt()
    val minutes = strUppercase.substring(position.index + 4, position.index + 6).toInt()
    val timeZoneDiffer = hours * 3600 + minutes * 60
    return Timestamp(seconds + if (addTimeDiffer) -timeZoneDiffer else timeZoneDiffer, nanoseconds)
  }

  /**
   * The expected serialized timestamp format is RFC3339: `yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS'Z'`, it
   * can be constructed by two parts. First, we use `dateFormatter` to serialize seconds. Then, we
   * pad nanoseconds into a 9 digits string.
   */
  fun timestampToString(timestamp: Timestamp): String {
    val serializedSecond = dateFormatter.format(Date(timestamp.seconds * 1000))
    val serializedNano = timestamp.nanoseconds.toString().padStart(9, '0')
    return "$serializedSecond.${serializedNano}Z"
  }
}
