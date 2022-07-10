// Copyright 2022 Google LLC
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

package com.google.firebase.firestore.encoding

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * The generic serializer for data types that need special treatment during (de)serialization
 * process. The concrete implementation of this generic serializer class is restricted to four
 * firestore data types, respectively to be [DocumentReference], [Timestamp], [GeoPoint] and [Date]
 * (these are the firestore supported non-primitive data types).
 */
private sealed class FirestoreNativeDataTypeSerializer<T>() : KSerializer<T> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor(
            // get the serializer's subclass simple name without the package name
            "__${javaClass.simpleName}__"
        )

    override fun serialize(encoder: Encoder, value: T) {
        val nestedMapEncoder = encoder as FirestoreAbstractEncoder
        nestedMapEncoder.encodeFirestoreNativeDataType(value)
    }

    override fun deserialize(decoder: Decoder): T {
        val nestedMapDecoder = decoder as FirestoreAbstractDecoder
        val decodeValue = nestedMapDecoder.decodeFirestoreNativeDataType()
        return when (descriptor.serialName) {
            "__DateSerializer__" ->
                (decodeValue as Timestamp).let { it.toDate() }
                        as T // Date is saved as Timestamp in firestore
            else -> decodeValue as T
        }
    }

    object GeoPointSerializer : FirestoreNativeDataTypeSerializer<GeoPoint>()
    object TimestampSerializer : FirestoreNativeDataTypeSerializer<Timestamp>()

}