// Copyright 2024 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.firebase.dataconnect.serializers

import com.google.firebase.Timestamp
import java.text.DateFormat
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

public object TimestampSerializer : KSerializer<Timestamp> {
  private val threadLocalDateFormatter =
    object : ThreadLocal<SimpleDateFormat>() {
      override fun initialValue(): SimpleDateFormat {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        return dateFormat
      }
    }

  private val dateFormatter: DateFormat
    get() = threadLocalDateFormatter.get()!!

  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("Timestamp", PrimitiveKind.STRING)

  private fun timestampToString(timestamp: Timestamp): String {
    val serializedDate = dateFormatter.format(Date(timestamp.seconds * 1000))
    val secondStr = serializedDate.replace("\\.\\d*".toRegex(), "").replace("Z", "")
    val nanoStr = timestamp.nanoseconds.toString().padStart(9, '0').takeLast(9)
    return "$secondStr.${nanoStr}Z"
  }

  private fun timestampFromString(str: String): Timestamp {
    val parts = str.split(".")
    val position = ParsePosition(0)
    val date = dateFormatter.parse(str, position)
    val seconds = Timestamp(date!!).seconds
    val nanosecondsStr = parts[1].replace("Z", "")
    val nanoseconds = if (nanosecondsStr.length > 1) nanosecondsStr.toInt() else 0
    return Timestamp(seconds, nanoseconds)
  }

  override fun serialize(encoder: Encoder, value: Timestamp) {
    encoder.encodeString(timestampToString(value))
  }

  override fun deserialize(decoder: Decoder): Timestamp {
    val rfc3339String = decoder.decodeString()
    return timestampFromString(rfc3339String)
  }
}
