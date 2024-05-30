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

import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import com.google.protobuf.Value.KindCase
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.serializer

internal inline fun <reified T> encodeToStruct(value: T): Struct =
  encodeToStruct(serializer(), value)

internal fun <T> encodeToStruct(serializer: SerializationStrategy<T>, value: T): Struct {
  val values = mutableListOf<Value>()
  ProtoValueEncoder(path = null, onValue = values::add).encodeSerializableValue(serializer, value)
  if (values.isEmpty()) {
    return Struct.getDefaultInstance()
  }
  require(values.size == 1) {
    "encoding produced ${values.size} Value objects, but expected either 0 or 1"
  }
  val valueProto = values.single()
  require(valueProto.hasStructValue()) {
    "encoding produced ${valueProto.kindCase}, but expected ${KindCase.STRUCT_VALUE}"
  }
  return valueProto.structValue
}

private class ProtoValueEncoder(private val path: String?, private val onValue: (Value) -> Unit) :
  Encoder {

  override val serializersModule = EmptySerializersModule()

  override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder =
    when (val kind = descriptor.kind) {
      is StructureKind.MAP,
      is StructureKind.CLASS -> ProtoStructValueEncoder(path, onValue)
      is StructureKind.LIST -> ProtoListValueEncoder(path, onValue)
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
    onValue(index.toValueProto())
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
}

private abstract class ProtoCompositeValueEncoder<K>(
  private val path: String?,
  private val onValue: (Value) -> Unit
) : CompositeEncoder {
  override val serializersModule = EmptySerializersModule()

  private val valueByKey = mutableMapOf<K, Value>()

  private fun putValue(descriptor: SerialDescriptor, index: Int, value: Value) {
    val key = keyOf(descriptor, index)
    valueByKey[key] = value
  }

  protected abstract fun keyOf(descriptor: SerialDescriptor, index: Int): K
  protected abstract fun formattedKeyForElementPath(key: K): String

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
    val encoder = ProtoValueEncoder(elementPathForKey(key)) { valueByKey[key] = it }
    encoder.encodeNullableSerializableValue(serializer, value)
  }

  override fun <T> encodeSerializableElement(
    descriptor: SerialDescriptor,
    index: Int,
    serializer: SerializationStrategy<T>,
    value: T
  ) {
    val key = keyOf(descriptor, index)
    val encoder = ProtoValueEncoder(elementPathForKey(key)) { valueByKey[key] = it }
    encoder.encodeSerializableValue(serializer, value)
  }

  override fun endStructure(descriptor: SerialDescriptor) {
    onValue(Value.newBuilder().also { populate(descriptor, it, valueByKey) }.build())
  }

  private fun elementPathForKey(key: K): String =
    formattedKeyForElementPath(key).let { if (path === null) it else "$path$it" }

  protected abstract fun populate(
    descriptor: SerialDescriptor,
    valueBuilder: Value.Builder,
    valueByKey: Map<K, Value>
  )
}

private class ProtoListValueEncoder(private val path: String?, onValue: (Value) -> Unit) :
  ProtoCompositeValueEncoder<Int>(path, onValue) {

  override fun keyOf(descriptor: SerialDescriptor, index: Int) = index

  override fun formattedKeyForElementPath(key: Int) = "[$key]"

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
                "$path: list value missing at index $i" +
                  " (have ${valueByKey.size} indexes:" +
                  " ${valueByKey.keys.sorted().joinToString()})"
              )
          )
        }
      }
    )
  }
}

private class ProtoStructValueEncoder(path: String?, onValue: (Value) -> Unit) :
  ProtoCompositeValueEncoder<String>(path, onValue) {

  override fun keyOf(descriptor: SerialDescriptor, index: Int) = descriptor.getElementName(index)

  override fun formattedKeyForElementPath(key: String) = ".$key"

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