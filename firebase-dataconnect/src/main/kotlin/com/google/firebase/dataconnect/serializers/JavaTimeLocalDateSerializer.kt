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

import com.google.firebase.dataconnect.toDataConnectLocalDate
import com.google.firebase.dataconnect.toJavaLocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * An implementation of [KSerializer] for serializing and deserializing [java.time.LocalDate]
 * objects in the wire format expected by the Firebase Data Connect backend.
 *
 * Be sure to _only_ call this method if [java.time.LocalDate] is available. See the documentation
 * for [toJavaLocalDate] for details.
 *
 * @see LocalDateSerializer
 * @see KotlinxDatetimeLocalDateSerializer
 */
public object JavaTimeLocalDateSerializer : KSerializer<java.time.LocalDate> {

  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("java.time.LocalDate", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: java.time.LocalDate) {
    LocalDateSerializer.serialize(encoder, value.toDataConnectLocalDate())
  }

  override fun deserialize(decoder: Decoder): java.time.LocalDate {
    return LocalDateSerializer.deserialize(decoder).toJavaLocalDate()
  }
}
