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

private fun Boolean.toProtoValue(): Value = Value.newBuilder().setBoolValue(this).build()

private fun Byte.toProtoValue(): Value = toInt().toProtoValue()

private fun Char.toProtoValue(): Value = code.toProtoValue()

private fun Double.toProtoValue(): Value = Value.newBuilder().setNumberValue(this).build()

private fun Float.toProtoValue(): Value = toDouble().toProtoValue()

private fun Int.toProtoValue(): Value = toDouble().toProtoValue()

private fun Long.toProtoValue(): Value = toString().toProtoValue()

private fun Short.toProtoValue(): Value = toInt().toProtoValue()

private fun String.toProtoValue(): Value = Value.newBuilder().setStringValue(this).build()

private val nullProtoValue: Value
  get() {
    return Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build()
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
    onValue(value.toProtoValue())
  }

  override fun encodeByte(value: Byte) {
    onValue(value.toProtoValue())
  }

  override fun encodeChar(value: Char) {
    onValue(value.toProtoValue())
  }

  override fun encodeDouble(value: Double) {
    onValue(value.toProtoValue())
  }

  override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
    onValue(index.toProtoValue())
  }

  override fun encodeFloat(value: Float) {
    onValue(value.toProtoValue())
  }

  override fun encodeInline(descriptor: SerialDescriptor) = this

  override fun encodeInt(value: Int) {
    onValue(value.toProtoValue())
  }

  override fun encodeLong(value: Long) {
    onValue(value.toProtoValue())
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
    onValue(value.toProtoValue())
  }

  override fun encodeString(value: String) {
    onValue(value.toProtoValue())
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
    putValue(descriptor, index, value.toProtoValue())
  }

  override fun encodeByteElement(descriptor: SerialDescriptor, index: Int, value: Byte) {
    putValue(descriptor, index, value.toProtoValue())
  }

  override fun encodeCharElement(descriptor: SerialDescriptor, index: Int, value: Char) {
    putValue(descriptor, index, value.toProtoValue())
  }

  override fun encodeDoubleElement(descriptor: SerialDescriptor, index: Int, value: Double) {
    putValue(descriptor, index, value.toProtoValue())
  }

  override fun encodeFloatElement(descriptor: SerialDescriptor, index: Int, value: Float) {
    putValue(descriptor, index, value.toProtoValue())
  }

  override fun encodeInlineElement(descriptor: SerialDescriptor, index: Int): Encoder {
    throw UnsupportedOperationException("inline is not implemented yet")
  }

  override fun encodeIntElement(descriptor: SerialDescriptor, index: Int, value: Int) {
    putValue(descriptor, index, value.toProtoValue())
  }

  override fun encodeLongElement(descriptor: SerialDescriptor, index: Int, value: Long) {
    putValue(descriptor, index, value.toProtoValue())
  }

  override fun encodeShortElement(descriptor: SerialDescriptor, index: Int, value: Short) {
    putValue(descriptor, index, value.toProtoValue())
  }

  override fun encodeStringElement(descriptor: SerialDescriptor, index: Int, value: String) {
    putValue(descriptor, index, value.toProtoValue())
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
