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
import com.google.firebase.dataconnect.serializers.AnyValueSerializer
import com.google.protobuf.ListValue
import com.google.protobuf.NullValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import com.google.protobuf.Value.KindCase
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

internal inline fun <reified T> decodeFromStruct(struct: Struct): T =
  decodeFromStruct(struct, serializer(), serializersModule = null)

internal fun <T> decodeFromStruct(
  struct: Struct,
  deserializer: DeserializationStrategy<T>,
  serializersModule: SerializersModule?
): T {
  val protoValue = Value.newBuilder().setStructValue(struct).build()
  return decodeFromValue(protoValue, deserializer, serializersModule)
}

internal inline fun <reified T> decodeFromValue(value: Value): T =
  decodeFromValue(value, serializer(), serializersModule = null)

internal fun <T> decodeFromValue(
  value: Value,
  deserializer: DeserializationStrategy<T>,
  serializersModule: SerializersModule?
): T {
  val decoder = ProtoValueDecoder(value, path = null, serializersModule ?: EmptySerializersModule())
  return decoder.decodeSerializableValue(deserializer)
}

private fun <T> Value.decode(path: String?, expectedKindCase: KindCase, block: (Value) -> T): T =
  if (kindCase != expectedKindCase) {
    throw SerializationException(
      (if (path === null) "" else "decoding \"$path\" failed: ") +
        "expected $expectedKindCase, but got $kindCase (${toAny()})"
    )
  } else {
    block(this)
  }

private fun Value.decodeBoolean(path: String?): Boolean =
  decode(path, KindCase.BOOL_VALUE) { it.boolValue }

private fun Value.decodeByte(path: String?): Byte =
  decode(path, KindCase.NUMBER_VALUE) { it.numberValue.toInt().toByte() }

private fun Value.decodeChar(path: String?): Char =
  decode(path, KindCase.NUMBER_VALUE) { it.numberValue.toInt().toChar() }

private fun Value.decodeDouble(path: String?): Double =
  decode(path, KindCase.NUMBER_VALUE) { it.numberValue }

private fun Value.decodeEnum(path: String?): Int =
  decode(path, KindCase.NUMBER_VALUE) { it.numberValue.toInt() }

private fun Value.decodeFloat(path: String?): Float =
  decode(path, KindCase.NUMBER_VALUE) { it.numberValue.toFloat() }

private fun Value.decodeString(path: String?): String =
  decode(path, KindCase.STRING_VALUE) { it.stringValue }

private fun Value.decodeStruct(path: String?): Struct =
  decode(path, KindCase.STRUCT_VALUE) { it.structValue }

private fun Value.decodeList(path: String?): ListValue =
  decode(path, KindCase.LIST_VALUE) { it.listValue }

private fun Value.decodeNull(path: String?): NullValue =
  decode(path, KindCase.NULL_VALUE) { it.nullValue }

private fun Value.decodeInt(path: String?): Int =
  decode(path, KindCase.NUMBER_VALUE) { it.numberValue.toInt() }

private fun Value.decodeLong(path: String?): Long =
  decode(path, KindCase.STRING_VALUE) { it.stringValue.toLong() }

private fun Value.decodeShort(path: String?): Short =
  decode(path, KindCase.NUMBER_VALUE) { it.numberValue.toInt().toShort() }

internal class ProtoValueDecoder(
  internal val valueProto: Value,
  private val path: String?,
  override val serializersModule: SerializersModule
) : Decoder {

  override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder =
    when (val kind = descriptor.kind) {
      is StructureKind.CLASS ->
        ProtoStructValueDecoder(valueProto.decodeStruct(path), path, serializersModule)
      is StructureKind.LIST ->
        ProtoListValueDecoder(valueProto.decodeList(path), path, serializersModule)
      is StructureKind.MAP ->
        ProtoMapValueDecoder(valueProto.decodeStruct(path), path, serializersModule)
      is StructureKind.OBJECT -> ProtoObjectValueDecoder(path, serializersModule)
      else -> throw IllegalArgumentException("unsupported SerialKind: ${kind::class.qualifiedName}")
    }

  override fun decodeBoolean() = valueProto.decodeBoolean(path)

  override fun decodeByte() = valueProto.decodeByte(path)

  override fun decodeChar() = valueProto.decodeChar(path)

  override fun decodeDouble() = valueProto.decodeDouble(path)

  override fun decodeEnum(enumDescriptor: SerialDescriptor) = valueProto.decodeEnum(path)

  override fun decodeFloat() = valueProto.decodeFloat(path)

  override fun decodeInline(descriptor: SerialDescriptor) =
    ProtoValueDecoder(valueProto, path, serializersModule)

  override fun decodeInt(): Int = valueProto.decodeInt(path)

  override fun decodeLong() = valueProto.decodeLong(path)

  override fun decodeShort() = valueProto.decodeShort(path)

  override fun decodeString() = valueProto.decodeString(path)

  override fun decodeNotNullMark() = !valueProto.hasNullValue()

  override fun decodeNull(): Nothing? {
    valueProto.decodeNull(path)
    return null
  }
}

private class ProtoStructValueDecoder(
  private val struct: Struct,
  private val path: String?,
  override val serializersModule: SerializersModule
) : CompositeDecoder {

  override fun endStructure(descriptor: SerialDescriptor) {}

  @Volatile private lateinit var elementIndexes: Iterator<Int>

  private fun getOrInitializeElementIndexes(descriptor: SerialDescriptor): Iterator<Int> {
    if (!::elementIndexes.isInitialized) {
      val names =
        buildSet<String> {
          addAll(struct.fieldsMap.keys)
          addAll(descriptor.elementNames)
        }
      elementIndexes = names.map(descriptor::getElementIndex).sorted().iterator()
    }

    return elementIndexes
  }

  override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
    val indexes = getOrInitializeElementIndexes(descriptor)
    return if (indexes.hasNext()) indexes.next() else CompositeDecoder.DECODE_DONE
  }

  override fun decodeBooleanElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(descriptor, index, Value::decodeBoolean)

  override fun decodeByteElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(descriptor, index, Value::decodeByte)

  override fun decodeCharElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(descriptor, index, Value::decodeChar)

  override fun decodeDoubleElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(descriptor, index, Value::decodeDouble)

  override fun decodeFloatElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(descriptor, index, Value::decodeFloat)

  override fun decodeInlineElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(descriptor, index) { ProtoValueDecoder(this, it, serializersModule) }

  override fun decodeIntElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(descriptor, index, Value::decodeInt)

  override fun decodeLongElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(descriptor, index, Value::decodeLong)

  override fun decodeShortElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(descriptor, index, Value::decodeShort)

  override fun decodeStringElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(descriptor, index, Value::decodeString)

  private fun <T> decodeValueElement(
    descriptor: SerialDescriptor,
    index: Int,
    block: Value.(String?) -> T
  ): T {
    val elementName = descriptor.getElementName(index)
    val elementPath = elementPathForName(elementName)
    val elementKind = descriptor.getElementDescriptor(index).kind

    val valueProto =
      struct.fieldsMap[elementName]
        ?: throw SerializationException("element \"$elementPath\" missing (expected $elementKind)")

    return block(valueProto, elementPath)
  }

  override fun <T> decodeSerializableElement(
    descriptor: SerialDescriptor,
    index: Int,
    deserializer: DeserializationStrategy<T>,
    previousValue: T?
  ): T {
    if (previousValue !== null) {
      return previousValue
    }

    val elementName = descriptor.getElementName(index)
    val elementPath = elementPathForName(elementName)
    val elementKind = descriptor.getElementDescriptor(index).kind

    val valueProto =
      struct.fieldsMap[elementName]
        ?: if (elementKind is StructureKind.OBJECT) Value.getDefaultInstance()
        else throw SerializationException("element \"$elementPath\" missing; expected $elementKind")

    return when (deserializer) {
      is AnyValueSerializer -> {
        @Suppress("UNCHECKED_CAST")
        AnyValue(valueProto) as T
      }
      else -> {
        val protoValueDecoder = ProtoValueDecoder(valueProto, elementPath, serializersModule)
        deserializer.deserialize(protoValueDecoder)
      }
    }
  }

  override fun <T : Any> decodeNullableSerializableElement(
    descriptor: SerialDescriptor,
    index: Int,
    deserializer: DeserializationStrategy<T?>,
    previousValue: T?
  ): T? {
    val elementName = descriptor.getElementName(index)
    return if (previousValue !== null) {
      previousValue
    } else if (!struct.containsFields(elementName)) {
      null
    } else if (struct.getFieldsOrThrow(elementName).hasNullValue()) {
      null
    } else {
      decodeSerializableElement(descriptor, index, deserializer, previousValue = null)
    }
  }

  private fun elementPathForName(elementName: String) =
    if (path === null) elementName else "${path}.${elementName}"
}

private class ProtoListValueDecoder(
  private val list: ListValue,
  private val path: String?,
  override val serializersModule: SerializersModule
) : CompositeDecoder {

  override fun endStructure(descriptor: SerialDescriptor) {}

  private val elementIndexes: IntIterator = list.valuesList.indices.iterator()

  override fun decodeElementIndex(descriptor: SerialDescriptor) =
    if (elementIndexes.hasNext()) elementIndexes.next() else CompositeDecoder.DECODE_DONE

  override fun decodeBooleanElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(index, Value::decodeBoolean)

  override fun decodeByteElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(index, Value::decodeByte)

  override fun decodeCharElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(index, Value::decodeChar)

  override fun decodeDoubleElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(index, Value::decodeDouble)

  override fun decodeFloatElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(index, Value::decodeFloat)

  override fun decodeInlineElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(index) { ProtoValueDecoder(this, it, serializersModule) }

  override fun decodeIntElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(index, Value::decodeInt)

  override fun decodeLongElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(index, Value::decodeLong)

  override fun decodeShortElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(index, Value::decodeShort)

  override fun decodeStringElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(index, Value::decodeString)

  private inline fun <T> decodeValueElement(index: Int, block: Value.(String?) -> T): T =
    block(list.valuesList[index], elementPathForIndex(index))

  override fun <T> decodeSerializableElement(
    descriptor: SerialDescriptor,
    index: Int,
    deserializer: DeserializationStrategy<T>,
    previousValue: T?
  ): T =
    if (previousValue !== null) {
      previousValue
    } else if (deserializer is AnyValueSerializer) {
      @Suppress("UNCHECKED_CAST")
      AnyValue(list.valuesList[index]) as T
    } else {
      deserializer.deserialize(
        ProtoValueDecoder(list.valuesList[index], elementPathForIndex(index), serializersModule)
      )
    }

  override fun <T : Any> decodeNullableSerializableElement(
    descriptor: SerialDescriptor,
    index: Int,
    deserializer: DeserializationStrategy<T?>,
    previousValue: T?
  ): T? =
    if (previousValue !== null) {
      previousValue
    } else if (list.valuesList[index].hasNullValue()) {
      null
    } else {
      decodeSerializableElement(descriptor, index, deserializer, previousValue = null)
    }

  private fun elementPathForIndex(index: Int) = if (path === null) "[$index]" else "${path}[$index]"

  override fun toString() = "ProtoListValueDecoder{path=$path, size=${list.valuesList.size}"
}

private class ProtoMapValueDecoder(
  private val struct: Struct,
  private val path: String?,
  override val serializersModule: SerializersModule
) : CompositeDecoder {

  override fun decodeSequentially() = true

  override fun decodeCollectionSize(descriptor: SerialDescriptor) = struct.fieldsCount

  override fun endStructure(descriptor: SerialDescriptor) {}

  private val structEntries: List<Map.Entry<String, Value>> = struct.fieldsMap.entries.toList()
  private val elementIndexes: IntIterator = (0 until structEntries.size * 2).iterator()

  private fun structEntryByElementIndex(index: Int): Map.Entry<String, Value> =
    structEntries[index / 2]

  override fun decodeElementIndex(descriptor: SerialDescriptor) =
    if (elementIndexes.hasNext()) elementIndexes.next() else CompositeDecoder.DECODE_DONE

  override fun decodeBooleanElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(index, Value::decodeBoolean)

  override fun decodeByteElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(index, Value::decodeByte)

  override fun decodeCharElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(index, Value::decodeChar)

  override fun decodeDoubleElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(index, Value::decodeDouble)

  override fun decodeFloatElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(index, Value::decodeFloat)

  override fun decodeInlineElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(index) { ProtoValueDecoder(this, it, serializersModule) }

  override fun decodeIntElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(index, Value::decodeInt)

  override fun decodeLongElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(index, Value::decodeLong)

  override fun decodeShortElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(index, Value::decodeShort)

  override fun decodeStringElement(descriptor: SerialDescriptor, index: Int) =
    if (index % 2 == 0) {
      structEntryByElementIndex(index).key
    } else {
      decodeValueElement(index, Value::decodeString)
    }

  private inline fun <T> decodeValueElement(index: Int, block: Value.(String?) -> T): T {
    require(index % 2 != 0) { "invalid value index: $index" }
    val value = structEntryByElementIndex(index).value
    val elementPath = elementPathForIndex(index)
    return block(value, elementPath)
  }

  override fun <T> decodeSerializableElement(
    descriptor: SerialDescriptor,
    index: Int,
    deserializer: DeserializationStrategy<T>,
    previousValue: T?
  ): T =
    if (previousValue !== null) {
      previousValue
    } else {
      decodeSerializableElement(index, deserializer)
    }

  override fun <T : Any> decodeNullableSerializableElement(
    descriptor: SerialDescriptor,
    index: Int,
    deserializer: DeserializationStrategy<T?>,
    previousValue: T?
  ): T? {
    if (previousValue !== null) {
      return previousValue
    }

    if (index % 2 != 0) {
      val structEntry = structEntryByElementIndex(index)
      if (structEntry.value.hasNullValue()) {
        return null
      }
    }

    return decodeSerializableElement(index, deserializer)
  }

  private fun <T> decodeSerializableElement(
    index: Int,
    deserializer: DeserializationStrategy<T>
  ): T {
    val structEntry = structEntryByElementIndex(index)
    val elementPath = elementPathForIndex(index)

    val elementDecoder =
      if (index % 2 == 0) {
        MapKeyDecoder(structEntry.key, elementPath, serializersModule)
      } else {
        ProtoValueDecoder(structEntry.value, elementPath, serializersModule)
      }

    return deserializer.deserialize(elementDecoder)
  }

  private fun elementPathForIndex(index: Int): String {
    val structEntry = structEntryByElementIndex(index)
    val key = structEntry.key
    return if (index % 2 == 0) {
      if (path === null) "[$key]" else "${path}[$key]"
    } else {
      if (path === null) "[$key].value" else "${path}[$key].value"
    }
  }

  override fun toString() = "ProtoMapValueDecoder{path=$path, size=${struct.fieldsCount}"
}

private class ProtoObjectValueDecoder(
  val path: String?,
  override val serializersModule: SerializersModule
) : CompositeDecoder {

  override fun decodeBooleanElement(descriptor: SerialDescriptor, index: Int) = notSupported()

  override fun decodeByteElement(descriptor: SerialDescriptor, index: Int) = notSupported()

  override fun decodeCharElement(descriptor: SerialDescriptor, index: Int) = notSupported()

  override fun decodeDoubleElement(descriptor: SerialDescriptor, index: Int) = notSupported()

  override fun decodeFloatElement(descriptor: SerialDescriptor, index: Int) = notSupported()

  override fun decodeInlineElement(descriptor: SerialDescriptor, index: Int) = notSupported()

  override fun decodeIntElement(descriptor: SerialDescriptor, index: Int) = notSupported()

  override fun decodeLongElement(descriptor: SerialDescriptor, index: Int) = notSupported()

  override fun decodeShortElement(descriptor: SerialDescriptor, index: Int) = notSupported()

  override fun decodeStringElement(descriptor: SerialDescriptor, index: Int) = notSupported()

  override fun <T> decodeSerializableElement(
    descriptor: SerialDescriptor,
    index: Int,
    deserializer: DeserializationStrategy<T>,
    previousValue: T?
  ) = notSupported()

  override fun <T : Any> decodeNullableSerializableElement(
    descriptor: SerialDescriptor,
    index: Int,
    deserializer: DeserializationStrategy<T?>,
    previousValue: T?
  ) = notSupported()

  private fun notSupported(): Nothing =
    throw UnsupportedOperationException(
      "The only valid method calls on ProtoObjectValueDecoder are " +
        "decodeElementIndex() and endStructure()"
    )

  override fun decodeElementIndex(descriptor: SerialDescriptor) = CompositeDecoder.DECODE_DONE

  override fun endStructure(descriptor: SerialDescriptor) {}

  override fun toString() = "ProtoObjectValueDecoder{path=$path}"
}

private class MapKeyDecoder(
  val key: String,
  val path: String,
  override val serializersModule: SerializersModule
) : Decoder {

  override fun decodeString() = key

  override fun beginStructure(descriptor: SerialDescriptor) = notSupported()

  override fun decodeBoolean() = notSupported()

  override fun decodeByte() = notSupported()

  override fun decodeChar() = notSupported()

  override fun decodeDouble() = notSupported()

  override fun decodeEnum(enumDescriptor: SerialDescriptor) = notSupported()

  override fun decodeFloat() = notSupported()

  override fun decodeInline(descriptor: SerialDescriptor) = notSupported()

  override fun decodeInt() = notSupported()

  override fun decodeLong() = notSupported()

  override fun decodeNotNullMark() = notSupported()

  override fun decodeNull() = notSupported()

  override fun decodeShort() = notSupported()

  private fun notSupported(): Nothing =
    throw UnsupportedOperationException(
      "The only valid method call on MapKeyDecoder is decodeString()"
    )

  override fun toString() = "MapKeyDecoder{path=$path}"
}
