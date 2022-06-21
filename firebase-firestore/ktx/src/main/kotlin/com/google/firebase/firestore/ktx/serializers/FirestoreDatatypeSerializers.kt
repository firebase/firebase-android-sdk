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

package com.google.firebase.firestore.ktx.serializers

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ktx.serialization.FirestoreMapEncoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual

private sealed class FirestoreNativeDataTypeSerializer<T>() : KSerializer<T> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("__${javaClass.simpleName}__") // get the serializer's subclass simple name without the package name

    override fun serialize(encoder: Encoder, value: T) {
        val nestedMapEncoder = encoder as FirestoreMapEncoder
        println("=".repeat(50))
        println(descriptor.kind)
        println(descriptor.serialName)
        println(descriptor.toString())

        nestedMapEncoder.encodeFirestoreNativeDataType(value)
    }

    override fun deserialize(decoder: Decoder): T {
        TODO("Not yet implemented")
    }

    object DocumentIdSerializer : FirestoreNativeDataTypeSerializer<DocumentReference>()
    object TimestampSerializer : FirestoreNativeDataTypeSerializer<Timestamp>()
    object GeoPointSerializer : FirestoreNativeDataTypeSerializer<GeoPoint>()
}

val FirestoreSerializersModule = SerializersModule {
    contextual(FirestoreNativeDataTypeSerializer.GeoPointSerializer)
    contextual(FirestoreNativeDataTypeSerializer.DocumentIdSerializer)
    contextual(FirestoreNativeDataTypeSerializer.TimestampSerializer)
}
