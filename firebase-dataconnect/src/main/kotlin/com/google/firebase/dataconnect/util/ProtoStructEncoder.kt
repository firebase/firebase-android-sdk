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

@file:OptIn(ExperimentalSerializationApi::class)

package com.google.firebase.dataconnect.util

import com.google.firebase.dataconnect.AnyValue
import com.google.firebase.dataconnect.DataConnectPath
import com.google.firebase.dataconnect.serializers.AnyValueSerializer
import com.google.firebase.dataconnect.toPathString
import com.google.firebase.dataconnect.util.ProtoUtil.nullProtoValue
import com.google.firebase.dataconnect.util.ProtoUtil.toValueProto
import com.google.firebase.dataconnect.withAddedField
import com.google.firebase.dataconnect.withAddedListIndex
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

internal class ProtoValueEncoder(
  private val path: DataConnectPath,
  override val serializersModule: SerializersModule,
  val onValue: (Value) -> Unit
) : Encoder {

  override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder =
    when (val kind = descriptor.kind) {
      is StructureKind.CLASS -> ProtoStructValueEncoder(path, serializersModule, onValue)
      is StructureKind.LIST -> ProtoListValueEncoder(path, serializersModule, onValue)
      is StructureKind.MAP -> ProtoMapValueEncoder(path, serializersModule, onValue)
      is StructureKind.OBJECT -> ProtoObjectValueEncoder
      else -> throw IllegalArgumentException("unsupported SerialKind: ${kind::class.qualifiedName}")
    }

  override fun encodeBoolean(value: Boolean) {
    onValue(value.toValueProto())
  }

  override fun encodeByte(value: Byte) {
    onValue(value.toValueProto())
  }

  override fun encodeChar(value: Char) {
    onValue(value.toValueProto())
  }

  override fun encodeDouble(value: Double) {
    onValue(value.toValueProto())
  }

  override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
    onValue(enumDescriptor.getElementName(index).toValueProto())
  }

  override fun encodeFloat(value: Float) {
    onValue(value.toValueProto())
  }

  override fun encodeInline(descriptor: SerialDescriptor) = this

  override fun encodeInt(value: Int) {
    onValue(value.toValueProto())
  }

  override fun encodeLong(value: Long) {
    onValue(value.toValueProto())
  }

  @ExperimentalSerializationApi
  override fun encodeNull() {
    onValue(nullProtoValue)
  }

  @ExperimentalSerializationApi
  override fun encodeNotNullMark() {
    encodeBoolean(true)
  }

  override fun encodeShort(value: Short) {
    onValue(value.toValueProto())
  }

  override fun encodeString(value: String) {
    onValue(value.toValueProto())
  }

  override fun <T : Any?> encodeSerializableValue(serializer: SerializationStrategy<T>, value: T) {
    when (serializer) {
      is AnyValueSerializer -> {
        val anyValue = value as AnyValue
        onValue(anyValue.protoValue)
      }
      else -> super.encodeSerializableValue(serializer, value)
    }
  }
}

private abstract class ProtoCompositeValueEncoder<K>(
  private val path: DataConnectPath,
  override val serializersModule: SerializersModule,
  private val onValue: (Value) -> Unit
) : CompositeEncoder {
  private val valueByKey = mutableMapOf<K, Value>()

  private fun putValue(descriptor: SerialDescriptor, index: Int, value: Value) {
    val key = keyOf(descriptor, index)
    valueByKey[key] = value
  }

  protected abstract fun keyOf(descriptor: SerialDescriptor, index: Int): K
  protected abstract fun elementPathForKey(key: K): DataConnectPath

  override fun encodeBooleanElement(descriptor: SerialDescriptor, index: Int, value: Boolean) {
    putValue(descriptor, index, value.toValueProto())
  }

  override fun encodeByteElement(descriptor: SerialDescriptor, index: Int, value: Byte) {
    putValue(descriptor, index, value.toValueProto())
  }

  override fun encodeCharElement(descriptor: SerialDescriptor, index: Int, value: Char) {
    putValue(descriptor, index, value.toValueProto())
  }

  override fun encodeDoubleElement(descriptor: SerialDescriptor, index: Int, value: Double) {
    putValue(descriptor, index, value.toValueProto())
  }

  override fun encodeFloatElement(descriptor: SerialDescriptor, index: Int, value: Float) {
    putValue(descriptor, index, value.toValueProto())
  }

  override fun encodeInlineElement(descriptor: SerialDescriptor, index: Int): Encoder {
    throw UnsupportedOperationException("inline is not implemented yet")
  }

  override fun encodeIntElement(descriptor: SerialDescriptor, index: Int, value: Int) {
    putValue(descriptor, index, value.toValueProto())
  }

  override fun encodeLongElement(descriptor: SerialDescriptor, index: Int, value: Long) {
    putValue(descriptor, index, value.toValueProto())
  }

  override fun encodeShortElement(descriptor: SerialDescriptor, index: Int, value: Short) {
    putValue(descriptor, index, value.toValueProto())
  }

  override fun encodeStringElement(descriptor: SerialDescriptor, index: Int, value: String) {
    putValue(descriptor, index, value.toValueProto())
  }

  @ExperimentalSerializationApi
  override fun <T : Any> encodeNullableSerializableElement(
    descriptor: SerialDescriptor,
    index: Int,
    serializer: SerializationStrategy<T>,
    value: T?
  ) {
    val key = keyOf(descriptor, index)
    val encoder =
      ProtoValueEncoder(elementPathForKey(key), serializersModule) { valueByKey[key] = it }
    encoder.encodeNullableSerializableValue(serializer, value)
  }

  override fun <T> encodeSerializableElement(
    descriptor: SerialDescriptor,
    index: Int,
    serializer: SerializationStrategy<T>,
    value: T
  ) {
    val key = keyOf(descriptor, index)
    val encoder =
      ProtoValueEncoder(elementPathForKey(key), serializersModule) { valueByKey[key] = it }
    encoder.encodeSerializableValue(serializer, value)
  }

  override fun endStructure(descriptor: SerialDescriptor) {
    onValue(Value.newBuilder().also { populate(descriptor, it, valueByKey) }.build())
  }

  protected abstract fun populate(
    descriptor: SerialDescriptor,
    valueBuilder: Value.Builder,
    valueByKey: Map<K, Value>
  )
}

private class ProtoListValueEncoder(
  private val path: DataConnectPath,
  serializersModule: SerializersModule,
  onValue: (Value) -> Unit
) : ProtoCompositeValueEncoder<Int>(path, serializersModule, onValue) {

  override fun keyOf(descriptor: SerialDescriptor, index: Int) = index

  override fun elementPathForKey(key: Int): DataConnectPath = path.withAddedListIndex(key)

  override fun populate(
    descriptor: SerialDescriptor,
    valueBuilder: Value.Builder,
    valueByKey: Map<Int, Value>
  ) {
    valueBuilder.setListValue(
      ListValue.newBuilder().also { listValueBuilder ->
        for (i in 0 until valueByKey.size) {
          listValueBuilder.addValues(
            valueByKey[i]
              ?: throw SerializationException(
                "${path.toPathString()}: list value missing at index $i" +
                  " (have ${valueByKey.size} indexes:" +
                  " ${valueByKey.keys.sorted().joinToString()})"
              )
          )
        }
      }
    )
  }
}

private class ProtoStructValueEncoder(
  private val path: DataConnectPath,
  serializersModule: SerializersModule,
  onValue: (Value) -> Unit
) : ProtoCompositeValueEncoder<String>(path, serializersModule, onValue) {

  override fun keyOf(descriptor: SerialDescriptor, index: Int) = descriptor.getElementName(index)

  override fun elementPathForKey(key: String): DataConnectPath = path.withAddedField(key)

  override fun populate(
    descriptor: SerialDescriptor,
    valueBuilder: Value.Builder,
    valueByKey: Map<String, Value>
  ) {
    valueBuilder.setStructValue(
      Struct.newBuilder().also { structBuilder ->
        valueByKey.forEach { (key, value) ->
          if (value.hasNullValue()) {
            structBuilder.putFields(key, nullProtoValue)
          } else {
            structBuilder.putFields(key, value)
          }
        }
      }
    )
  }
}

private class ProtoMapValueEncoder(
  private val path: DataConnectPath,
  override val serializersModule: SerializersModule,
  private val onValue: (Value) -> Unit
) : CompositeEncoder {

  private val keyByIndex = mutableMapOf<Int, String>()
  private val valueByIndex = mutableMapOf<Int, Value>()

  override fun encodeBooleanElement(descriptor: SerialDescriptor, index: Int, value: Boolean) {
    require(index % 2 != 0) { "invalid index: $index (must be odd)" }
    valueByIndex[index] = value.toValueProto()
  }

  override fun encodeByteElement(descriptor: SerialDescriptor, index: Int, value: Byte) {
    require(index % 2 != 0) { "invalid index: $index (must be odd)" }
    valueByIndex[index] = value.toValueProto()
  }

  override fun encodeCharElement(descriptor: SerialDescriptor, index: Int, value: Char) {
    require(index % 2 != 0) { "invalid index: $index (must be odd)" }
    valueByIndex[index] = value.toValueProto()
  }

  override fun encodeDoubleElement(descriptor: SerialDescriptor, index: Int, value: Double) {
    require(index % 2 != 0) { "invalid index: $index (must be odd)" }
    valueByIndex[index] = value.toValueProto()
  }

  override fun encodeFloatElement(descriptor: SerialDescriptor, index: Int, value: Float) {
    require(index % 2 != 0) { "invalid index: $index (must be odd)" }
    valueByIndex[index] = value.toValueProto()
  }

  override fun encodeInlineElement(descriptor: SerialDescriptor, index: Int): Encoder {
    throw UnsupportedOperationException("inline is not implemented yet")
  }

  override fun encodeIntElement(descriptor: SerialDescriptor, index: Int, value: Int) {
    require(index % 2 != 0) { "invalid index: $index (must be odd)" }
    valueByIndex[index] = value.toValueProto()
  }

  override fun encodeLongElement(descriptor: SerialDescriptor, index: Int, value: Long) {
    require(index % 2 != 0) { "invalid index: $index (must be odd)" }
    valueByIndex[index] = value.toValueProto()
  }

  @ExperimentalSerializationApi
  override fun <T : Any> encodeNullableSerializableElement(
    descriptor: SerialDescriptor,
    index: Int,
    serializer: SerializationStrategy<T>,
    value: T?
  ) {
    if (index % 2 == 0) {
      require(value is String) {
        "even indexes must be a String, but got index=$index value=$value"
      }
      keyByIndex[index] = value
      return
    }

    val protoValue =
      if (value === null) {
        null
      } else {
        val key = keyByIndex[index - 1] ?: "$index"
        val valuePath = path.withAddedField(key)
        var encodedValue: Value? = null
        val encoder = ProtoValueEncoder(valuePath, serializersModule) { encodedValue = it }
        encoder.encodeNullableSerializableValue(serializer, value)
        requireNotNull(encodedValue) { "ProtoValueEncoder should have produced a value" }
        encodedValue
      }
    valueByIndex[index] = protoValue ?: nullProtoValue
  }

  override fun <T> encodeSerializableElement(
    descriptor: SerialDescriptor,
    index: Int,
    serializer: SerializationStrategy<T>,
    value: T
  ) {
    if (index % 2 == 0) {
      require(value is String) {
        "even indexes must be a String, but got index=$index value=$value"
      }
      keyByIndex[index] = value
    } else {
      val key = keyByIndex[index - 1] ?: "$index"
      val valuePath = path.withAddedField(key)
      val encoder = ProtoValueEncoder(valuePath, serializersModule) { valueByIndex[index] = it }
      encoder.encodeSerializableValue(serializer, value)
    }
  }

  override fun encodeShortElement(descriptor: SerialDescriptor, index: Int, value: Short) {
    require(index % 2 != 0) { "invalid index: $index (must be odd)" }
    valueByIndex[index] = value.toValueProto()
  }

  override fun encodeStringElement(descriptor: SerialDescriptor, index: Int, value: String) {
    if (index % 2 != 0) {
      valueByIndex[index] = value.toValueProto()
    } else {
      keyByIndex[index] = value
    }
  }

  override fun endStructure(descriptor: SerialDescriptor) {
    var i = 0
    val structBuilder = Struct.newBuilder()
    while (keyByIndex.containsKey(i)) {
      val key = keyByIndex[i++]
      val value = valueByIndex[i++]
      structBuilder.putFields(key, value)
    }
    onValue(structBuilder.build().toValueProto())
  }
}

private object ProtoObjectValueEncoder : CompositeEncoder {
  override val serializersModule = EmptySerializersModule()

  override fun encodeBooleanElement(descriptor: SerialDescriptor, index: Int, value: Boolean) =
    notSupported()

  override fun encodeByteElement(descriptor: SerialDescriptor, index: Int, value: Byte) =
    notSupported()

  override fun encodeCharElement(descriptor: SerialDescriptor, index: Int, value: Char) =
    notSupported()

  override fun encodeDoubleElement(descriptor: SerialDescriptor, index: Int, value: Double) =
    notSupported()

  override fun encodeFloatElement(descriptor: SerialDescriptor, index: Int, value: Float) =
    notSupported()

  override fun encodeInlineElement(descriptor: SerialDescriptor, index: Int) = notSupported()

  override fun encodeIntElement(descriptor: SerialDescriptor, index: Int, value: Int) =
    notSupported()

  override fun encodeLongElement(descriptor: SerialDescriptor, index: Int, value: Long) =
    notSupported()

  @ExperimentalSerializationApi
  override fun <T : Any> encodeNullableSerializableElement(
    descriptor: SerialDescriptor,
    index: Int,
    serializer: SerializationStrategy<T>,
    value: T?
  ) = notSupported()

  override fun <T> encodeSerializableElement(
    descriptor: SerialDescriptor,
    index: Int,
    serializer: SerializationStrategy<T>,
    value: T
  ) = notSupported()

  override fun encodeShortElement(descriptor: SerialDescriptor, index: Int, value: Short) =
    notSupported()

  override fun encodeStringElement(descriptor: SerialDescriptor, index: Int, value: String) =
    notSupported()

  private fun notSupported(): Nothing =
    throw UnsupportedOperationException(
      "The only valid method call on ProtoObjectValueEncoder is endStructure()"
    )

  override fun endStructure(descriptor: SerialDescriptor) {}
}
