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
@file:SuppressLint("NewApi")

package com.google.firebase.dataconnect.serializers

import android.annotation.SuppressLint
import com.google.firebase.Timestamp
import com.google.firebase.dataconnect.toJavaLocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * An implementation of [KSerializer] for serializing and deserializing [java.time.Instant] objects in the
 * wire format expected by the Firebase Data Connect backend.
 *
 * Be sure to _only_ call this method if [java.time.Instant] is available. See the documentation
 * for [toJavaLocalDate] for details.
 *
 * @see TimestampSerializer
 * @see KotlinxDatetimeInstantSerializer
 */
public object JavaTimeInstantSerializer : KSerializer<java.time.Instant> {

  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("java.time.Instant", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: java.time.Instant) {
    TimestampSerializer.serialize(encoder, Timestamp(value))
  }

  override fun deserialize(decoder: Decoder): java.time.Instant {
    return TimestampSerializer.deserialize(decoder).toInstant()
  }
}
