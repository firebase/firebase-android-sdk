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

private fun <T> Value.decode(path: String?, expectedKindCase: KindCase, block: (Value) -> T): T =
  if (kindCase != expectedKindCase) {
    throw SerializationException(
      (if (path === null) "" else "decoding \"$path\" failed: ") +
        "expected $expectedKindCase, but got $kindCase"
    )
  } else {
    block(this)
  }

private fun Value.decodeBoolean(path: String?): Boolean =
  decode(path, KindCase.BOOL_VALUE) { it.boolValue }

private fun Value.decodeDouble(path: String?): Double =
  decode(path, KindCase.NUMBER_VALUE) { it.numberValue }

private fun Value.decodeString(path: String?): String =
  decode(path, KindCase.STRING_VALUE) { it.stringValue }

private fun Value.decodeStruct(path: String?): Struct =
  decode(path, KindCase.STRUCT_VALUE) { it.structValue }

private fun Value.decodeList(path: String?): ListValue =
  decode(path, KindCase.LIST_VALUE) { it.listValue }

private fun Value.decodeNull(path: String?): NullValue =
  decode(path, KindCase.NULL_VALUE) { it.nullValue }

private fun Value.decodeInt(path: String?): Int = decodeDouble(path).toInt()

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

  override fun decodeByte(): Byte {
    TODO("Not yet implemented")
  }

  override fun decodeChar(): Char {
    TODO("Not yet implemented")
  }

  override fun decodeDouble() = valueProto.decodeDouble(path)

  override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
    TODO("Not yet implemented")
  }

  override fun decodeFloat(): Float {
    TODO("Not yet implemented")
  }

  override fun decodeInline(descriptor: SerialDescriptor): Decoder {
    TODO("Not yet implemented")
  }

  override fun decodeInt(): Int = valueProto.decodeInt(path)

  override fun decodeLong(): Long {
    TODO("Not yet implemented")
  }

  @ExperimentalSerializationApi
  override fun decodeNotNullMark(): Boolean {
    return !valueProto.hasNullValue()
  }

  @ExperimentalSerializationApi
  override fun decodeNull(): Nothing? {
    valueProto.decodeNull(path)
    return null
  }

  override fun decodeShort(): Short {
    TODO("Not yet implemented")
  }

  override fun decodeString() = valueProto.decodeString(path)
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

  override fun decodeByteElement(descriptor: SerialDescriptor, index: Int): Byte {
    TODO("Not yet implemented")
  }

  override fun decodeCharElement(descriptor: SerialDescriptor, index: Int): Char {
    TODO("Not yet implemented")
  }

  override fun decodeDoubleElement(descriptor: SerialDescriptor, index: Int): Double =
    decodeValueElement(descriptor, index, Value::decodeDouble)

  override fun decodeFloatElement(descriptor: SerialDescriptor, index: Int): Float {
    TODO("Not yet implemented")
  }

  override fun decodeInlineElement(descriptor: SerialDescriptor, index: Int): Decoder {
    TODO("Not yet implemented")
  }

  override fun decodeIntElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(descriptor, index, Value::decodeInt)

  override fun decodeLongElement(descriptor: SerialDescriptor, index: Int): Long {
    TODO("Not yet implemented")
  }

  override fun decodeShortElement(descriptor: SerialDescriptor, index: Int): Short {
    TODO("Not yet implemented")
  }

  override fun decodeStringElement(descriptor: SerialDescriptor, index: Int): String =
    decodeValueElement(descriptor, index, Value::decodeString)

  private fun <T> decodeValueElement(
    descriptor: SerialDescriptor,
    index: Int,
    block: Value.(String?) -> T
  ): T {
    val elementName = descriptor.getElementName(index)
    val value =
      struct.fieldsMap[elementName]
        ?: throw SerializationException(
          "element \"$elementName\" missing (expected ${descriptor.getElementDescriptor(index).kind})"
        )
    return block(value, elementPathForName(elementName))
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
    val valueProto = struct.fieldsMap[elementName]
    if (valueProto === null) {
      throw SerializationException(
        "element \"$elementName\" missing; expected ${descriptor.getElementDescriptor(index).kind}"
      )
    }

    return deserializer.deserialize(ProtoValueDecoder(valueProto, elementPathForName(elementName)))
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

  override fun decodeByteElement(descriptor: SerialDescriptor, index: Int): Byte {
    TODO("Not yet implemented")
  }

  override fun decodeCharElement(descriptor: SerialDescriptor, index: Int): Char {
    TODO("Not yet implemented")
  }

  override fun decodeDoubleElement(descriptor: SerialDescriptor, index: Int): Double =
    decodeValueElement(index, Value::decodeDouble)

  override fun decodeFloatElement(descriptor: SerialDescriptor, index: Int): Float {
    TODO("Not yet implemented")
  }

  override fun decodeInlineElement(descriptor: SerialDescriptor, index: Int): Decoder {
    TODO("Not yet implemented")
  }

  override fun decodeIntElement(descriptor: SerialDescriptor, index: Int) =
    decodeValueElement(index, Value::decodeInt)

  override fun decodeLongElement(descriptor: SerialDescriptor, index: Int): Long {
    TODO("Not yet implemented")
  }

  override fun decodeShortElement(descriptor: SerialDescriptor, index: Int): Short {
    TODO("Not yet implemented")
  }

  override fun decodeStringElement(descriptor: SerialDescriptor, index: Int): String =
    decodeValueElement(index, Value::decodeString)

  private inline fun <T> decodeValueElement(index: Int, block: Value.(String?) -> T): T =
    block(list.valuesList[index], elementPathForIndex(index))

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

  @ExperimentalSerializationApi
  override fun <T : Any> decodeNullableSerializableElement(
    descriptor: SerialDescriptor,
    index: Int,
    deserializer: DeserializationStrategy<T?>,
    previousValue: T?
  ) = notSupported()

  override fun <T> decodeSerializableElement(
    descriptor: SerialDescriptor,
    index: Int,
    deserializer: DeserializationStrategy<T>,
    previousValue: T?
  ) = notSupported()

  override fun decodeShortElement(descriptor: SerialDescriptor, index: Int) = notSupported()

  override fun decodeStringElement(descriptor: SerialDescriptor, index: Int) = notSupported()

  private fun notSupported(): Nothing =
    throw UnsupportedOperationException(
      "The only valid method calls on ProtoObjectValueDecoder are " +
        "decodeElementIndex() and endStructure()"
    )

  override fun decodeElementIndex(descriptor: SerialDescriptor) = CompositeDecoder.DECODE_DONE

  override fun endStructure(descriptor: SerialDescriptor) {}
}
