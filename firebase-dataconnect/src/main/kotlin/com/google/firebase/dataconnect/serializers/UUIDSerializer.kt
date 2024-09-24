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

import java.util.UUID
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * An implementation of [KSerializer] for serializing and deserializing [UUID] objects in the wire
 * format expected by the Firebase Data Connect backend.
 */
public object UUIDSerializer : KSerializer<UUID> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: UUID) {
    val uuidString = UUIDSerializerImpl.serialize(value)
    encoder.encodeString(uuidString)
  }

  override fun deserialize(decoder: Decoder): UUID {
    val decodedString = decoder.decodeString()
    return UUIDSerializerImpl.deserialize(decodedString)
  }
}

internal object UUIDSerializerImpl {
  internal fun serialize(value: UUID): String {
    // Remove dashes from the UUID since the server will remove them anyways (see cl/629562890).
    return value.toString().replace("-", "")
  }

  internal fun deserialize(decodedString: String): UUID {
    require(decodedString.length == 32) {
      "invalid UUID string: $decodedString (length=${decodedString.length}, expected=32)"
    }

    // Insert dashes into the UUID string since the server will remove them (see cl/629562890).
    val decodedStringWithDashes = buildString {
      append(decodedString, 0, 8)
      append("-")
      append(decodedString, 8, 12)
      append("-")
      append(decodedString, 12, 16)
      append("-")
      append(decodedString, 16, 20)
      append("-")
      append(decodedString, 20, decodedString.length)
    }
    check(decodedStringWithDashes.length == 36) {
      "internal error: decodedStringWithDashes.length==${decodedStringWithDashes.length}, " +
        "but expected 36 (decodedStringWithDashes=\"${decodedStringWithDashes}\")"
    }

    return UUID.fromString(decodedStringWithDashes)
  }
}
