@file:OptIn(ExperimentalSerializationApi::class)

package com.google.firebase.dataconnect

import com.google.protobuf.ListValue
import com.google.protobuf.NullValue
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

inline fun <reified T> encodeToStruct(value: T): Struct = encodeToStruct(serializer(), value)

fun <T> encodeToStruct(serializer: SerializationStrategy<T>, value: T): Struct {
  val values = ProtoValueEncoder().apply { encodeSerializableValue(serializer, value) }.values
  if (values.isEmpty()) {
    return Struct.getDefaultInstance()
  }
  require(values.size == 1) {
    "encoding produced ${values.size} Value objects, " + "but expected at most 1"
  }
  val value = values.first()
  require(value.hasStructValue()) {
    "encoding produced ${value.kindCase}, but expected ${KindCase.STRUCT_VALUE}"
  }
  return value.structValue
}

private class ProtoValueEncoder : Encoder {

  val values = mutableListOf<Value>()

  override val serializersModule = EmptySerializersModule()

  override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder =
    when (val kind = descriptor.kind) {
      is StructureKind.MAP,
      is StructureKind.CLASS -> ProtoStructValueEncoder(values)
      is StructureKind.LIST -> ProtoListValueEncoder(values)
      is StructureKind.OBJECT -> ProtoObjectValueEncoder
      else -> throw IllegalArgumentException("unsupported SerialKind: ${kind::class.qualifiedName}")
    }

  override fun encodeBoolean(value: Boolean) {
    values.add(Value.newBuilder().setBoolValue(value).build())
  }

  override fun encodeByte(value: Byte) {
    TODO("Not yet implemented")
  }

  override fun encodeChar(value: Char) {
    TODO("Not yet implemented")
  }

  override fun encodeDouble(value: Double) {
    values.add(Value.newBuilder().setNumberValue(value).build())
  }

  override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
    TODO("Not yet implemented")
  }

  override fun encodeFloat(value: Float) {
    TODO("Not yet implemented")
  }

  override fun encodeInline(descriptor: SerialDescriptor) = this

  override fun encodeInt(value: Int) {
    encodeDouble(value.toDouble())
  }

  override fun encodeLong(value: Long) {
    TODO("Not yet implemented")
  }

  @ExperimentalSerializationApi
  override fun encodeNull() {
    values.add(Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build())
  }

  override fun encodeShort(value: Short) {
    TODO("Not yet implemented")
  }

  override fun encodeString(value: String) {
    values.add(Value.newBuilder().setStringValue(value).build())
  }
}

private abstract class ProtoCompositeValueEncoder<K>(private val dest: MutableList<Value>) :
  CompositeEncoder {
  override val serializersModule = EmptySerializersModule()

  private val valueEncoder = ProtoValueEncoder()
  private val keys = mutableListOf<K>()

  protected abstract fun keyOf(descriptor: SerialDescriptor, index: Int): K

  override fun encodeBooleanElement(descriptor: SerialDescriptor, index: Int, value: Boolean) {
    keys.add(keyOf(descriptor, index))
    valueEncoder.encodeBoolean(value)
  }

  override fun encodeByteElement(descriptor: SerialDescriptor, index: Int, value: Byte) {
    keys.add(keyOf(descriptor, index))
    valueEncoder.encodeByte(value)
  }

  override fun encodeCharElement(descriptor: SerialDescriptor, index: Int, value: Char) {
    keys.add(keyOf(descriptor, index))
    valueEncoder.encodeChar(value)
  }

  override fun encodeDoubleElement(descriptor: SerialDescriptor, index: Int, value: Double) {
    keys.add(keyOf(descriptor, index))
    valueEncoder.encodeDouble(value)
  }

  override fun encodeFloatElement(descriptor: SerialDescriptor, index: Int, value: Float) {
    keys.add(keyOf(descriptor, index))
    valueEncoder.encodeFloat(value)
  }

  override fun encodeInlineElement(descriptor: SerialDescriptor, index: Int): Encoder {
    keys.add(keyOf(descriptor, index))
    return valueEncoder.encodeInline(descriptor)
  }

  override fun encodeIntElement(descriptor: SerialDescriptor, index: Int, value: Int) {
    keys.add(keyOf(descriptor, index))
    valueEncoder.encodeInt(value)
  }

  override fun encodeLongElement(descriptor: SerialDescriptor, index: Int, value: Long) {
    keys.add(keyOf(descriptor, index))
    valueEncoder.encodeLong(value)
  }

  @ExperimentalSerializationApi
  override fun <T : Any> encodeNullableSerializableElement(
    descriptor: SerialDescriptor,
    index: Int,
    serializer: SerializationStrategy<T>,
    value: T?
  ) {
    keys.add(keyOf(descriptor, index))
    valueEncoder.encodeNullableSerializableValue(serializer, value)
  }

  override fun <T> encodeSerializableElement(
    descriptor: SerialDescriptor,
    index: Int,
    serializer: SerializationStrategy<T>,
    value: T
  ) {
    keys.add(keyOf(descriptor, index))
    valueEncoder.encodeSerializableValue(serializer, value)
  }

  override fun encodeShortElement(descriptor: SerialDescriptor, index: Int, value: Short) {
    keys.add(keyOf(descriptor, index))
    valueEncoder.encodeShort(value)
  }

  override fun encodeStringElement(descriptor: SerialDescriptor, index: Int, value: String) {
    keys.add(keyOf(descriptor, index))
    valueEncoder.encodeString(value)
  }

  override fun endStructure(descriptor: SerialDescriptor) {
    require(valueEncoder.values.size == keys.size) {
      "internal error: " +
        "valueEncoder.values.size != keys.size " +
        "(${valueEncoder.values.size} != ${keys.size})"
    }

    val valueByKey =
      buildMap<K, Value> {
        this@ProtoCompositeValueEncoder.keys.forEachIndexed { valueEncoderIndex, destKey ->
          put(destKey, valueEncoder.values[valueEncoderIndex])
        }
      }

    dest.add(Value.newBuilder().also { populate(it, valueByKey) }.build())
  }

  protected abstract fun populate(valueBuilder: Value.Builder, valueByKey: Map<K, Value>)
}

private class ProtoListValueEncoder(dest: MutableList<Value>) :
  ProtoCompositeValueEncoder<Int>(dest) {
  override fun keyOf(descriptor: SerialDescriptor, index: Int) = index

  override fun populate(valueBuilder: Value.Builder, valueByKey: Map<Int, Value>) {
    valueBuilder.setListValue(
      ListValue.newBuilder().also { listValueBuilder ->
        for (i in 0 until valueByKey.size) {
          listValueBuilder.addValues(
            valueByKey[i] ?: throw SerializationException("list value missing at index $i")
          )
        }
      }
    )
  }
}

private class ProtoStructValueEncoder(dest: MutableList<Value>) :
  ProtoCompositeValueEncoder<String>(dest) {
  override fun keyOf(descriptor: SerialDescriptor, index: Int) = descriptor.getElementName(index)

  override fun populate(valueBuilder: Value.Builder, valueByKey: Map<String, Value>) {
    valueBuilder.setStructValue(
      Struct.newBuilder().also { structBuilder ->
        valueByKey.forEach { (key, value) ->
          if (value.hasNullValue()) {
            structBuilder.removeFields(key)
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
