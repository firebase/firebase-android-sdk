@file:OptIn(ExperimentalSerializationApi::class)

package com.google.firebase.dataconnect

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
import kotlinx.serialization.serializer

inline fun <reified T> decodeFromStruct(struct: Struct): T = decodeFromStruct(serializer(), struct)

fun <T> decodeFromStruct(deserializer: DeserializationStrategy<T>, struct: Struct): T {
  val protoValue = Value.newBuilder().setStructValue(struct).build()
  return ProtoValueDecoder(protoValue, path = null).decodeSerializableValue(deserializer)
}

private fun Value.toAny(): Any? =
  when (kindCase) {
    KindCase.BOOL_VALUE -> boolValue
    KindCase.NUMBER_VALUE -> numberValue
    KindCase.STRING_VALUE -> stringValue
    KindCase.LIST_VALUE -> listValue.valuesList
    KindCase.STRUCT_VALUE -> structValue.fieldsMap
    KindCase.NULL_VALUE -> null
    else -> "ERROR: unsupported kindCase: $kindCase"
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

private class ProtoValueDecoder(private val valueProto: Value, private val path: String?) :
  Decoder {

  override val serializersModule = EmptySerializersModule()

  override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder =
    when (val kind = descriptor.kind) {
      is StructureKind.CLASS -> ProtoStructValueDecoder(valueProto.decodeStruct(path), path)
      is StructureKind.LIST -> ProtoListValueDecoder(valueProto.decodeList(path), path)
      is StructureKind.OBJECT -> ProtoObjectValueDecoder
      else -> throw IllegalArgumentException("unsupported SerialKind: ${kind::class.qualifiedName}")
    }

  override fun decodeBoolean() = valueProto.decodeBoolean(path)

  override fun decodeByte() = valueProto.decodeByte(path)

  override fun decodeChar() = valueProto.decodeChar(path)

  override fun decodeDouble() = valueProto.decodeDouble(path)

  override fun decodeEnum(enumDescriptor: SerialDescriptor) = valueProto.decodeEnum(path)

  override fun decodeFloat() = valueProto.decodeFloat(path)

  override fun decodeInline(descriptor: SerialDescriptor) = ProtoValueDecoder(valueProto, path)

  override fun decodeInt(): Int = valueProto.decodeInt(path)

  override fun decodeLong() = valueProto.decodeLong(path)

  override fun decodeShort() = valueProto.decodeShort(path)

  override fun decodeString() = valueProto.decodeString(path)

  @ExperimentalSerializationApi
  override fun decodeNotNullMark(): Boolean {
    return !valueProto.hasNullValue()
  }

  @ExperimentalSerializationApi
  override fun decodeNull(): Nothing? {
    valueProto.decodeNull(path)
    return null
  }
}

private class ProtoStructValueDecoder(private val struct: Struct, private val path: String?) :
  CompositeDecoder {
  override val serializersModule = EmptySerializersModule()

  override fun endStructure(descriptor: SerialDescriptor) {}

  private lateinit var elementIndexes: Iterator<Int>

  private fun computeElementIndexSet(descriptor: SerialDescriptor) =
    buildSet<String> {
        addAll(struct.fieldsMap.keys)
        addAll(descriptor.elementNames)
      }
      .map(descriptor::getElementIndex)

  override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
    if (!::elementIndexes.isInitialized) {
      elementIndexes = computeElementIndexSet(descriptor).sorted().iterator()
    }
    return if (elementIndexes.hasNext()) elementIndexes.next() else CompositeDecoder.DECODE_DONE
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
    decodeValueElement(descriptor, index, ::ProtoValueDecoder)

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

    return deserializer.deserialize(ProtoValueDecoder(valueProto, elementPath))
  }

  @ExperimentalSerializationApi
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

private class ProtoListValueDecoder(private val list: ListValue, private val path: String?) :
  CompositeDecoder {
  override val serializersModule = EmptySerializersModule()

  override fun endStructure(descriptor: SerialDescriptor) {}

  private val elementIndexes: Iterator<Int> by lazy {
    list.valuesList.mapIndexed { index, _ -> index }.iterator()
  }

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
    decodeValueElement(index, ::ProtoValueDecoder)

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
    if (previousValue !== null) previousValue
    else
      deserializer.deserialize(
        ProtoValueDecoder(list.valuesList[index], elementPathForIndex(index))
      )

  @ExperimentalSerializationApi
  override fun <T : Any> decodeNullableSerializableElement(
    descriptor: SerialDescriptor,
    index: Int,
    deserializer: DeserializationStrategy<T?>,
    previousValue: T?
  ): T? {
    return if (previousValue !== null) {
      previousValue
    } else if (list.valuesList[index].hasNullValue()) {
      null
    } else {
      decodeSerializableElement(descriptor, index, deserializer, previousValue = null)
    }
  }

  private fun elementPathForIndex(index: Int) = if (path === null) "[$index]" else "${path}[$index]"
}

private object ProtoObjectValueDecoder : CompositeDecoder {
  override val serializersModule = EmptySerializersModule()

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

  @ExperimentalSerializationApi
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
}
