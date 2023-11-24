@file:OptIn(ExperimentalSerializationApi::class)

package com.google.firebase.dataconnect

import java.io.DataOutputStream
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.serializer

inline fun <reified T> calculateSha512(value: T) = calculateSha512(serializer(), value)

fun <T> calculateSha512(serializer: SerializationStrategy<T>, value: T): ByteArray =
  Sha512Encoder().apply { encodeSerializableValue(serializer, value) }.digest.digest()

private class Sha512Encoder : Encoder {

  val digest = newSha512MessageDigest()
  val out = DataOutputStream(DigestOutputStream(NullOutputStream, digest))

  override val serializersModule = EmptySerializersModule()

  override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
    return when (val kind = descriptor.kind) {
      is StructureKind.LIST -> {
        out.writeTag(Tag.ListBegin)
        Sha512ListEncoder(this, endTag = Tag.ListEnd)
      }
      is StructureKind.MAP -> {
        out.writeTag(Tag.MapBegin)
        Sha512MapEncoder(this, endTag = Tag.MapEnd)
      }
      is StructureKind.CLASS -> {
        out.writeTag(Tag.ClassBegin)
        Sha512MapEncoder(this, endTag = Tag.ClassEnd)
      }
      is StructureKind.OBJECT -> {
        out.writeTag(Tag.ObjectBegin)
        Sha512ObjectEncoder(this, endTag = Tag.ObjectEnd)
      }
      else -> throw SerializationException("beginStructure unexpected SerialDescriptor.kind: $kind")
    }
  }

  override fun encodeBoolean(value: Boolean) {
    out.writeTag(Tag.Boolean)
    out.writeBoolean(value)
  }

  override fun encodeByte(value: Byte) {
    out.writeTag(Tag.Byte)
    out.writeByte(value.toInt())
  }

  override fun encodeChar(value: Char) {
    out.writeTag(Tag.Char)
    out.writeChar(value.code)
  }

  override fun encodeDouble(value: Double) {
    out.writeTag(Tag.Double)
    out.writeDouble(value)
  }

  override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
    out.writeTag(Tag.Enum)
    out.writeInt(index)
  }

  override fun encodeFloat(value: Float) {
    out.writeTag(Tag.Float)
    out.writeFloat(value)
  }

  override fun encodeInline(descriptor: SerialDescriptor): Encoder {
    out.writeTag(Tag.Inline)
    return this
  }

  override fun encodeInt(value: Int) {
    out.writeTag(Tag.Int)
    out.writeInt(value)
  }

  override fun encodeLong(value: Long) {
    out.writeTag(Tag.Long)
    out.writeLong(value)
  }

  @ExperimentalSerializationApi
  override fun encodeNull() {
    out.writeTag(Tag.Null)
  }

  override fun encodeShort(value: Short) {
    out.writeTag(Tag.Short)
    out.writeShort(value.toInt())
  }

  override fun encodeString(value: String) {
    out.writeTag(Tag.String)
    out.writeUTF(value)
  }
}

private class Sha512ListEncoder(val parentEncoder: Sha512Encoder, val endTag: Tag) :
  CompositeEncoder {

  override val serializersModule = EmptySerializersModule()

  private val elements = mutableMapOf<Int, Sha512Encoder>()

  override fun endStructure(descriptor: SerialDescriptor) {
    parentEncoder.out.run {
      val digestBytes = ByteArray(parentEncoder.digest.digestLength)
      var expectedNextIndex = 0
      elements.entries
        .sortedBy { (index, _) -> index }
        .forEach { (index, encoder) ->
          if (index != expectedNextIndex) {
            throw SerializationException("got index $index, but expected $expectedNextIndex")
          }
          expectedNextIndex++
          writeInt(index)
          encoder.digest.digest(digestBytes, 0, digestBytes.size)
          write(digestBytes)
        }
      writeTag(endTag)
    }
  }

  override fun encodeBooleanElement(descriptor: SerialDescriptor, index: Int, value: Boolean) {
    elements[index] = Sha512Encoder().apply { encodeBoolean(value) }
  }

  override fun encodeByteElement(descriptor: SerialDescriptor, index: Int, value: Byte) {
    elements[index] = Sha512Encoder().apply { encodeByte(value) }
  }

  override fun encodeCharElement(descriptor: SerialDescriptor, index: Int, value: Char) {
    elements[index] = Sha512Encoder().apply { encodeChar(value) }
  }

  override fun encodeDoubleElement(descriptor: SerialDescriptor, index: Int, value: Double) {
    elements[index] = Sha512Encoder().apply { encodeDouble(value) }
  }

  override fun encodeFloatElement(descriptor: SerialDescriptor, index: Int, value: Float) {
    elements[index] = Sha512Encoder().apply { encodeFloat(value) }
  }

  override fun encodeInlineElement(descriptor: SerialDescriptor, index: Int): Encoder {
    return Sha512Encoder().also { elements[index] = it }
  }

  override fun encodeIntElement(descriptor: SerialDescriptor, index: Int, value: Int) {
    elements[index] = Sha512Encoder().apply { encodeInt(value) }
  }

  override fun encodeLongElement(descriptor: SerialDescriptor, index: Int, value: Long) {
    elements[index] = Sha512Encoder().apply { encodeLong(value) }
  }

  override fun encodeShortElement(descriptor: SerialDescriptor, index: Int, value: Short) {
    elements[index] = Sha512Encoder().apply { encodeShort(value) }
  }

  override fun encodeStringElement(descriptor: SerialDescriptor, index: Int, value: String) {
    elements[index] = Sha512Encoder().apply { encodeString(value) }
  }

  override fun <T> encodeSerializableElement(
    descriptor: SerialDescriptor,
    index: Int,
    serializer: SerializationStrategy<T>,
    value: T
  ) {
    elements[index] = Sha512Encoder().apply { encodeSerializableValue(serializer, value) }
  }

  @ExperimentalSerializationApi
  override fun <T : Any> encodeNullableSerializableElement(
    descriptor: SerialDescriptor,
    index: Int,
    serializer: SerializationStrategy<T>,
    value: T?
  ) {
    elements[index] = Sha512Encoder().apply { encodeNullableSerializableValue(serializer, value) }
  }
}

private class Sha512MapEncoder(val parentEncoder: Sha512Encoder, val endTag: Tag) :
  CompositeEncoder {

  override val serializersModule = EmptySerializersModule()

  private val elements = mutableMapOf<String, Sha512Encoder>()

  override fun endStructure(descriptor: SerialDescriptor) {
    parentEncoder.out.run {
      val digestBytes = ByteArray(parentEncoder.digest.digestLength)
      elements.entries
        .sortedBy { (key, _) -> key }
        .forEach { (key, encoder) ->
          writeUTF(key)
          encoder.digest.digest(digestBytes, 0, digestBytes.size)
          write(digestBytes)
        }
      writeTag(endTag)
    }
  }

  override fun encodeBooleanElement(descriptor: SerialDescriptor, index: Int, value: Boolean) {
    elements[descriptor.getElementName(index)] = Sha512Encoder().apply { encodeBoolean(value) }
  }

  override fun encodeByteElement(descriptor: SerialDescriptor, index: Int, value: Byte) {
    elements[descriptor.getElementName(index)] = Sha512Encoder().apply { encodeByte(value) }
  }

  override fun encodeCharElement(descriptor: SerialDescriptor, index: Int, value: Char) {
    elements[descriptor.getElementName(index)] = Sha512Encoder().apply { encodeChar(value) }
  }

  override fun encodeDoubleElement(descriptor: SerialDescriptor, index: Int, value: Double) {
    elements[descriptor.getElementName(index)] = Sha512Encoder().apply { encodeDouble(value) }
  }

  override fun encodeFloatElement(descriptor: SerialDescriptor, index: Int, value: Float) {
    elements[descriptor.getElementName(index)] = Sha512Encoder().apply { encodeFloat(value) }
  }

  override fun encodeInlineElement(descriptor: SerialDescriptor, index: Int): Encoder {
    return Sha512Encoder().also { elements[descriptor.getElementName(index)] = it }
  }

  override fun encodeIntElement(descriptor: SerialDescriptor, index: Int, value: Int) {
    elements[descriptor.getElementName(index)] = Sha512Encoder().apply { encodeInt(value) }
  }

  override fun encodeLongElement(descriptor: SerialDescriptor, index: Int, value: Long) {
    elements[descriptor.getElementName(index)] = Sha512Encoder().apply { encodeLong(value) }
  }

  override fun encodeShortElement(descriptor: SerialDescriptor, index: Int, value: Short) {
    elements[descriptor.getElementName(index)] = Sha512Encoder().apply { encodeShort(value) }
  }

  override fun encodeStringElement(descriptor: SerialDescriptor, index: Int, value: String) {
    elements[descriptor.getElementName(index)] = Sha512Encoder().apply { encodeString(value) }
  }

  override fun <T> encodeSerializableElement(
    descriptor: SerialDescriptor,
    index: Int,
    serializer: SerializationStrategy<T>,
    value: T
  ) {
    elements[descriptor.getElementName(index)] =
      Sha512Encoder().apply { encodeSerializableValue(serializer, value) }
  }

  @ExperimentalSerializationApi
  override fun <T : Any> encodeNullableSerializableElement(
    descriptor: SerialDescriptor,
    index: Int,
    serializer: SerializationStrategy<T>,
    value: T?
  ) {
    elements[descriptor.getElementName(index)] =
      Sha512Encoder().apply { encodeNullableSerializableValue(serializer, value) }
  }
}

private class Sha512ObjectEncoder(val parentEncoder: Sha512Encoder, val endTag: Tag) :
  CompositeEncoder {

  override val serializersModule = EmptySerializersModule()

  override fun endStructure(descriptor: SerialDescriptor) {
    parentEncoder.out.writeTag(endTag)
  }

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

  override fun encodeInlineElement(descriptor: SerialDescriptor, index: Int): Encoder =
    notSupported()

  override fun encodeIntElement(descriptor: SerialDescriptor, index: Int, value: Int) =
    notSupported()

  override fun encodeLongElement(descriptor: SerialDescriptor, index: Int, value: Long) =
    notSupported()

  override fun encodeShortElement(descriptor: SerialDescriptor, index: Int, value: Short) =
    notSupported()

  override fun encodeStringElement(descriptor: SerialDescriptor, index: Int, value: String) =
    notSupported()

  override fun <T> encodeSerializableElement(
    descriptor: SerialDescriptor,
    index: Int,
    serializer: SerializationStrategy<T>,
    value: T
  ) = notSupported()

  @ExperimentalSerializationApi
  override fun <T : Any> encodeNullableSerializableElement(
    descriptor: SerialDescriptor,
    index: Int,
    serializer: SerializationStrategy<T>,
    value: T?
  ) = notSupported()

  private fun notSupported(): Nothing =
    throw UnsupportedOperationException(
      "The only valid method call on Sha512ObjectEncoder is endStructure()"
    )
}

private enum class Tag {
  ListBegin,
  ListEnd,
  MapBegin,
  MapEnd,
  ClassBegin,
  ClassEnd,
  ObjectBegin,
  ObjectEnd,
  Boolean,
  Byte,
  Char,
  Double,
  Enum,
  Float,
  Inline,
  Int,
  Long,
  Null,
  Short,
  String
}

private fun DataOutputStream.writeTag(tag: Tag) = writeInt(tag.ordinal)

private object NullOutputStream : OutputStream() {
  override fun write(b: Int) {}
  override fun write(b: ByteArray?) {}
  override fun write(b: ByteArray?, off: Int, len: Int) {}
}

private fun newSha512MessageDigest(): MessageDigest = MessageDigest.getInstance("SHA-512")
