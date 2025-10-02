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
package com.google.firebase.dataconnect.testutil

import com.google.firebase.dataconnect.util.ProtoUtil.nullProtoValue
import com.google.firebase.dataconnect.util.ProtoUtil.toListOfAny
import com.google.firebase.dataconnect.util.ProtoUtil.toMap
import com.google.firebase.dataconnect.util.ProtoUtil.toValueProto
import com.google.firebase.dataconnect.util.ProtoValueDecoder
import com.google.firebase.dataconnect.util.ProtoValueEncoder
import com.google.protobuf.Value
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

// Adapted from JsonContentPolymorphicSerializer
// https://github.com/Kotlin/kotlinx.serialization/blob/8c84a5b4dd/formats/json/commonMain/src/kotlinx/serialization/json/JsonContentPolymorphicSerializer.kt#L67
object DataConnectAnySerializer : KSerializer<Any?> {

  @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
  override val descriptor =
    buildSerialDescriptor("DataConnectAnySerializer", PolymorphicKind.SEALED)

  @Suppress("UNCHECKED_CAST")
  override fun serialize(encoder: Encoder, value: Any?) {
    require(encoder is ProtoValueEncoder) {
      "DataConnectAnySerializer only supports ProtoValueEncoder" +
        ", but got ${encoder::class.qualifiedName}"
    }
    val protoValue =
      when (value) {
        null -> nullProtoValue
        is String -> value.toValueProto()
        is Double -> value.toValueProto()
        is Boolean -> value.toValueProto()
        is List<*> -> value.toValueProto()
        is Map<*, *> -> (value as Map<String, Any?>).toValueProto()
        else ->
          throw IllegalArgumentException(
            "unsupported type: ${value::class.qualifiedName} (error code: av5kpmwb8h)"
          )
      }
    encoder.onValue(protoValue)
  }

  override fun deserialize(decoder: Decoder): Any? {
    require(decoder is ProtoValueDecoder) {
      "DataConnectAnySerializer only supports ProtoValueDecoder" +
        ", but got ${decoder::class.qualifiedName}"
    }
    return when (val kindCase = decoder.valueProto.kindCase) {
      Value.KindCase.NULL_VALUE -> null
      Value.KindCase.STRING_VALUE -> decoder.valueProto.stringValue
      Value.KindCase.NUMBER_VALUE -> decoder.valueProto.numberValue
      Value.KindCase.BOOL_VALUE -> decoder.valueProto.boolValue
      Value.KindCase.LIST_VALUE -> decoder.valueProto.listValue.toListOfAny()
      Value.KindCase.STRUCT_VALUE -> decoder.valueProto.structValue.toMap()
      else ->
        throw IllegalArgumentException("unsupported KindCase: $kindCase (error code: 3bde44vczt)")
    }
  }
}
