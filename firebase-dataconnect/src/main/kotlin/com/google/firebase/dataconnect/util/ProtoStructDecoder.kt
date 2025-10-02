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
import com.google.firebase.dataconnect.util.ProtoDecoderUtil.decodeBoolean
import com.google.firebase.dataconnect.util.ProtoDecoderUtil.decodeByte
import com.google.firebase.dataconnect.util.ProtoDecoderUtil.decodeChar
import com.google.firebase.dataconnect.util.ProtoDecoderUtil.decodeDouble
import com.google.firebase.dataconnect.util.ProtoDecoderUtil.decodeEnum
import com.google.firebase.dataconnect.util.ProtoDecoderUtil.decodeFloat
import com.google.firebase.dataconnect.util.ProtoDecoderUtil.decodeInt
import com.google.firebase.dataconnect.util.ProtoDecoderUtil.decodeList
import com.google.firebase.dataconnect.util.ProtoDecoderUtil.decodeLong
import com.google.firebase.dataconnect.util.ProtoDecoderUtil.decodeNull
import com.google.firebase.dataconnect.util.ProtoDecoderUtil.decodeShort
import com.google.firebase.dataconnect.util.ProtoDecoderUtil.decodeString
import com.google.firebase.dataconnect.util.ProtoDecoderUtil.decodeStruct
import com.google.firebase.dataconnect.util.ProtoUtil.toAny
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
import kotlinx.serialization.modules.SerializersModule

/**
 * Holder for "global" functions related to [ProtoStructValueDecoder].
 *
 * Technically, these functions _could_ be defined as free functions; however, doing so creates a
 * ProtoStructDecoderKt, ProtoUtilKt, etc. Java class with public visibility, which pollutes the
 * public API. Using an "internal" object, instead, to gather together the top-level functions
 * avoids this public API pollution.
 */
private object ProtoDecoderUtil {
  fun <T> decode(value: Value, path: String?, expectedKindCase: KindCase, block: (Value) -> T): T =
    if (value.kindCase != expectedKindCase) {
      throw SerializationException(
        (if (path === null) "" else "decoding \"$path\" failed: ") +
          "expected $expectedKindCase, but got ${value.kindCase} (${value.toAny()})"
      )
    } else {
      block(value)
    }

  fun decodeBoolean(value: Value, path: String?): Boolean =
    decode(value, path, KindCase.BOOL_VALUE) { it.boolValue }

  fun decodeByte(value: Value, path: String?): Byte =
    decode(value, path, KindCase.NUMBER_VALUE) { it.numberValue.toInt().toByte() }

  fun decodeChar(value: Value, path: String?): Char =
    decode(value, path, KindCase.NUMBER_VALUE) { it.numberValue.toInt().toChar() }

  fun decodeDouble(value: Value, path: String?): Double =
    decode(value, path, KindCase.NUMBER_VALUE) { it.numberValue }

  fun decodeEnum(value: Value, path: String?): String =
    decode(value, path, KindCase.STRING_VALUE) { it.stringValue }

  fun decodeFloat(value: Value, path: String?): Float =
    decode(value, path, KindCase.NUMBER_VALUE) { it.numberValue.toFloat() }

  fun decodeString(value: Value, path: String?): String =
    decode(value, path, KindCase.STRING_VALUE) { it.stringValue }

  fun decodeStruct(value: Value, path: String?): Struct =
    decode(value, path, KindCase.STRUCT_VALUE) { it.structValue }

  fun decodeList(value: Value, path: String?): ListValue =
    decode(value, path, KindCase.LIST_VALUE) { it.listValue }

  fun decodeNull(value: Value, path: String?): NullValue =
    decode(value, path, KindCase.NULL_VALUE) { it.nullValue }

  fun decodeInt(value: Value, path: String?): Int =
    decode(value, path, KindCase.NUMBER_VALUE) { it.numberValue.toInt() }

  fun decodeLong(value: Value, path: String?): Long =
    decode(value, path, KindCase.STRING_VALUE) { it.stringValue.toLong() }

  fun decodeShort(value: Value, path: String?): Short =
    decode(value, path, KindCase.NUMBER_VALUE) { it.numberValue.toInt().toShort() }
}

internal class ProtoValueDecoder(
  internal val valueProto: Value,
  private val path: String?,
  override val serializersModule: SerializersModule
) : Decoder {

  override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder =
    when (val kind = descriptor.kind) {
      is StructureKind.CLASS ->
        ProtoStructValueDecoder(decodeStruct(valueProto, path), path, serializersModule)
      is StructureKind.LIST ->
        ProtoListValueDecoder(decodeList(valueProto, path), path, serializersModule)
      is StructureKind.MAP ->
        ProtoMapValueDecoder(decodeStruct(valueProto, path), path, serializersModule)
      is StructureKind.OBJECT -> ProtoObjectValueDecoder(path, serializersModule)
      else -> throw IllegalArgumentException("unsupported SerialKind: ${kind::class.qualifiedName}")
    }

  override fun decodeBoolean() = decodeBoolean(valueProto, path)

  override fun decodeByte() = decodeByte(valueProto, path)

  override fun decodeChar() = decodeChar(valueProto, path)

  override fun decodeDouble() = decodeDouble(valueProto, path)

  override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
    val enumValueName = decodeEnum(valueProto, path)
    return enumDescriptor.getElementIndex(enumValueName)
  }

  override fun decodeFloat() = decodeFloat(valueProto, path)

  override fun decodeInline(descriptor: SerialDescriptor) =
    ProtoValueDecoder(valueProto, path, serializersModule)

  override fun decodeInt(): Int = decodeInt(valueProto, path)

  override fun decodeLong() = decodeLong(valueProto, path)

  override fun decodeShort() = decodeShort(valueProto, path)

  override fun decodeString() = decodeString(valueProto, path)

  override fun decodeNotNullMark() = !valueProto.hasNullValue()

  override fun decodeNull(): Nothing? {
    decodeNull(valueProto, path)
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
      elementIndexes =
        names
          .map(descriptor::getElementIndex)
          .filter { it != CompositeDecoder.UNKNOWN_NAME } // ignore unknown keys
          .sorted()
          .iterator()
    }

    return elementIndexes
  }

  override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
    val indexes = getOrInitializeElementIndexes(descriptor)
    return if (indexes.hasNext()) indexes.next() else CompositeDecoder.DECODE_DONE
  }

  override fun decodeBooleanElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(descriptor, index, ProtoDecoderUtil::decodeBoolean)

  override fun decodeByteElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(descriptor, index, ProtoDecoderUtil::decodeByte)

  override fun decodeCharElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(descriptor, index, ProtoDecoderUtil::decodeChar)

  override fun decodeDoubleElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(descriptor, index, ProtoDecoderUtil::decodeDouble)

  override fun decodeFloatElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(descriptor, index, ProtoDecoderUtil::decodeFloat)

  override fun decodeInlineElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(descriptor, index) { valueProto, elementPath ->
      ProtoValueDecoder(valueProto, elementPath, serializersModule)
    }

  override fun decodeIntElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(descriptor, index, ProtoDecoderUtil::decodeInt)

  override fun decodeLongElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(descriptor, index, ProtoDecoderUtil::decodeLong)

  override fun decodeShortElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(descriptor, index, ProtoDecoderUtil::decodeShort)

  override fun decodeStringElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(descriptor, index, ProtoDecoderUtil::decodeString)

  private fun <T> decodeValueElement(
    descriptor: SerialDescriptor,
    index: Int,
    block: (Value, String?) -> T
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
    decodeValueElement(index, ProtoDecoderUtil::decodeBoolean)

  override fun decodeByteElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(index, ProtoDecoderUtil::decodeByte)

  override fun decodeCharElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(index, ProtoDecoderUtil::decodeChar)

  override fun decodeDoubleElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(index, ProtoDecoderUtil::decodeDouble)

  override fun decodeFloatElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(index, ProtoDecoderUtil::decodeFloat)

  override fun decodeInlineElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(index) { protoValue, elementPath ->
      ProtoValueDecoder(protoValue, elementPath, serializersModule)
    }

  override fun decodeIntElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(index, ProtoDecoderUtil::decodeInt)

  override fun decodeLongElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(index, ProtoDecoderUtil::decodeLong)

  override fun decodeShortElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(index, ProtoDecoderUtil::decodeShort)

  override fun decodeStringElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(index, ProtoDecoderUtil::decodeString)

  private inline fun <T> decodeValueElement(index: Int, block: (Value, String?) -> T): T =
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
    decodeValueElement(index, ProtoDecoderUtil::decodeBoolean)

  override fun decodeByteElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(index, ProtoDecoderUtil::decodeByte)

  override fun decodeCharElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(index, ProtoDecoderUtil::decodeChar)

  override fun decodeDoubleElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(index, ProtoDecoderUtil::decodeDouble)

  override fun decodeFloatElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(index, ProtoDecoderUtil::decodeFloat)

  override fun decodeInlineElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(index) { valueProto, elementPath ->
      ProtoValueDecoder(valueProto, elementPath, serializersModule)
    }

  override fun decodeIntElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(index, ProtoDecoderUtil::decodeInt)

  override fun decodeLongElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(index, ProtoDecoderUtil::decodeLong)

  override fun decodeShortElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(index, ProtoDecoderUtil::decodeShort)

  override fun decodeStringElement(descriptor: SerialDescriptor, index: Int) =
    if (index % 2 == 0) {
      structEntryByElementIndex(index).key
    } else {
      decodeValueElement(index, ProtoDecoderUtil::decodeString)
    }

  private inline fun <T> decodeValueElement(index: Int, block: (Value, String?) -> T): T {
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
