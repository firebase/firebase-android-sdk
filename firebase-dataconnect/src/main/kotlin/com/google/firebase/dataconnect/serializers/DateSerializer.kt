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

import java.text.DateFormat
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

  private val threadLocalDateFormatter =
    object : ThreadLocal<SimpleDateFormat>() {
      override fun initialValue() = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }

  private val dateFormatter: DateFormat
    get() = threadLocalDateFormatter.get()!!

  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("Date", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: Date) {
    val serializedDate = dateFormatter.format(value)
    encoder.encodeString(serializedDate)
  }

  override fun deserialize(decoder: Decoder): Date {
    val serializedDate = decoder.decodeString()
    val position = ParsePosition(0)
    val date = dateFormatter.parse(serializedDate, position)
    requireNotNull(date)
    require(position.index == serializedDate.length)
    return date
  }
}
