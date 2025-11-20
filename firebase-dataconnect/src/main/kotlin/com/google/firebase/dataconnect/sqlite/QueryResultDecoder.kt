/*
 * Copyright 2025 Google LLC
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

package com.google.firebase.dataconnect.sqlite

import com.google.firebase.dataconnect.sqlite.CodedIntegersExts.getSInt32
import com.google.firebase.dataconnect.sqlite.CodedIntegersExts.getSInt64
import com.google.firebase.dataconnect.sqlite.CodedIntegersExts.getUInt32
import com.google.firebase.dataconnect.sqlite.CodedIntegersExts.getUInt64
import com.google.firebase.dataconnect.sqlite.QueryResultCodec.Entity
import com.google.firebase.dataconnect.util.ProtoUtil.toValueProto
import com.google.firebase.dataconnect.util.StringUtil.get0xHexString
import com.google.firebase.dataconnect.util.StringUtil.to0xHexString
import com.google.protobuf.ListValue
import com.google.protobuf.NullValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.CharBuffer
import java.nio.channels.Channels
import java.nio.channels.ReadableByteChannel
import java.nio.charset.CodingErrorAction

/**
 * This class is NOT thread safe. The behavior of an instance of this class when used concurrently
 * from multiple threads without external synchronization is undefined.
 */
internal class QueryResultDecoder(
  private val channel: ReadableByteChannel,
  private val entities: List<Entity>,
) {

  private val charsetDecoder =
    Charsets.UTF_8.newDecoder()
      .onUnmappableCharacter(CodingErrorAction.REPORT)
      .onMalformedInput(CodingErrorAction.REPORT)

  private val byteBuffer: ByteBuffer =
    ByteBuffer.allocate(2048).apply {
      order(ByteOrder.BIG_ENDIAN)
      flip()
    }

  private val charArray = CharArray(2)

  fun decode(): Struct {
    readMagic()
    return when (readStructValueType()) {
      StructValueType.Struct -> readStruct()
      StructValueType.Entity -> readEntity()
    }
  }

  private fun readSome(): Boolean {
    byteBuffer.compact()
    val readCount = channel.read(byteBuffer)
    byteBuffer.flip()
    return readCount > 0
  }

  private fun ensureRemaining(byteCount: Int) {
    while (byteBuffer.remaining() < byteCount) {
      if (!readSome()) {
        throw EOFException(
          "end of input reached prematurely reading $byteCount bytes: " +
            "got ${byteBuffer.remaining()} bytes, " +
            "${byteCount-byteBuffer.remaining()} fewer bytes than expected [xg5y5fm2vk]"
        )
      }
    }
  }

  private fun readByte(): Byte {
    ensureRemaining(1)
    return byteBuffer.get()
  }

  private fun readChar(): Char {
    ensureRemaining(2)
    return byteBuffer.getChar()
  }

  private fun readFixed32Int(): Int {
    ensureRemaining(4)
    return byteBuffer.getInt()
  }

  private inline fun <T : Number> readVarint(
    typeName: String,
    maxSize: Int,
    isValidDecodedValue: (T) -> Boolean,
    decodeException: (message: String, cause: Throwable?) -> Exception,
    read: (ByteBuffer) -> T
  ): T {
    ensureRemaining(1)
    while (true) {
      val originalPosition = byteBuffer.position()

      val readResult = runCatching { read(byteBuffer) }

      readResult.fold(
        onSuccess = { decodedValue ->
          if (isValidDecodedValue(decodedValue)) {
            return decodedValue
          }

          val newPosition = byteBuffer.position()
          byteBuffer.position(originalPosition)
          val decodedByteCount = newPosition - originalPosition
          throw decodeException(
            "invalid $typeName value decoded: $decodedValue " +
              "(decoded from $decodedByteCount bytes: " +
              "${byteBuffer.get0xHexString(length = decodedByteCount)}) [fpt2q953k9]",
            null
          )
        },
        onFailure = {
          byteBuffer.position(originalPosition)

          if (byteBuffer.remaining() >= maxSize) {
            throw decodeException(
              "$typeName decode failed of $maxSize bytes: " +
                "${byteBuffer.get0xHexString(length=maxSize)} [ybydmsykkp]",
              readResult.exceptionOrNull()
            )
          }

          if (!readSome()) {
            throw decodeException(
              "end of input reached during decoding of $typeName value: " +
                "got ${byteBuffer.remaining()} bytes (${byteBuffer.get0xHexString()}), " +
                " but expected between 1 and $maxSize bytes [c439qmdmnk]",
              readResult.exceptionOrNull()
            )
          }
        }
      )
    }
  }

  private fun readUInt32(): Int =
    readVarint(
      typeName = "uint32",
      maxSize = CodedIntegers.MAX_VARINT32_SIZE,
      isValidDecodedValue = { it >= 0 },
      decodeException = ::UInt32DecodeException,
      read = { byteBuffer -> byteBuffer.getUInt32() },
    )

  private fun readSInt32(): Int =
    readVarint(
      typeName = "sint32",
      maxSize = CodedIntegers.MAX_VARINT32_SIZE,
      isValidDecodedValue = { true },
      decodeException = ::SInt32DecodeException,
      read = { byteBuffer -> byteBuffer.getSInt32() },
    )

  private fun readUInt64(): Long =
    readVarint(
      typeName = "uint64",
      maxSize = CodedIntegers.MAX_VARINT64_SIZE,
      isValidDecodedValue = { it >= 0 },
      decodeException = ::UInt64DecodeException,
      read = { byteBuffer -> byteBuffer.getUInt64() },
    )

  private fun readSInt64(): Long =
    readVarint(
      typeName = "sint64",
      maxSize = CodedIntegers.MAX_VARINT64_SIZE,
      isValidDecodedValue = { true },
      decodeException = ::SInt64DecodeException,
      read = { byteBuffer -> byteBuffer.getSInt64() },
    )

  private fun readDouble(): Double {
    ensureRemaining(8)
    return byteBuffer.getDouble()
  }

  private fun readBytes(byteCount: Int): ByteArray {
    val byteArray = ByteArray(byteCount)
    var byteArrayOffset = 0
    while (byteArrayOffset < byteCount) {
      val wantByteCount = byteCount - byteArrayOffset

      if (byteBuffer.remaining() == 0 && !readSome()) {
        throw ByteArrayEOFException(
          "end of input reached prematurely reading byte array of length $byteCount: " +
            "got ${byteArrayOffset + byteBuffer.remaining()} bytes, " +
            "${wantByteCount - byteBuffer.remaining()} fewer bytes than expected [dnx886qwmk]"
        )
      }

      val getByteCount = wantByteCount.coerceAtMost(byteBuffer.remaining())
      byteBuffer.get(byteArray, byteArrayOffset, getByteCount)
      byteArrayOffset += getByteCount
    }

    return byteArray
  }

  private fun readString(): String = readString(readStringValueType())

  private fun readString(stringType: StringValueType): String =
    when (stringType) {
      StringValueType.Empty -> ""
      StringValueType.OneByte -> readString1Byte()
      StringValueType.TwoByte -> readString2Byte()
      StringValueType.OneChar -> readString1Char()
      StringValueType.TwoChar -> readString2Char()
      StringValueType.Utf8 -> readStringUtf8()
      StringValueType.Utf16 -> readStringCustomUtf16()
    }

  private fun readMagic(): Int =
    readFixed32Int().also {
      if (it != QueryResultCodec.QUERY_RESULT_MAGIC) {
        throw BadMagicException(
          "read magic value 0x" +
            it.toUInt().toString(16).padStart(8, '0') +
            ", but expected 0x" +
            QueryResultCodec.QUERY_RESULT_MAGIC.toUInt().toString(16).padStart(8, '0') +
            " [jk832sz9hx]"
        )
      }
    }

  private fun readStructKeyCount(): Int =
    readUInt32().also {
      if (it < 0) {
        throw NegativeStructKeyCountException(
          "read struct key count $it, but expected " +
            "a number greater than or equal to zero [y9253xj96g]"
        )
      }
    }

  private fun readStringByteCount(): Int =
    readUInt32().also {
      if (it < 0) {
        throw NegativeStringByteCountException(
          "read string byte count $it, but expected " +
            "a number greater than or equal to zero [a9kma55y7m]"
        )
      }
    }

  private fun readStringCharCount(): Int =
    readUInt32().also {
      if (it < 0) {
        throw NegativeStringCharCountException(
          "read string char count $it, but expected " +
            "a number greater than or equal to zero [gwybfam237]"
        )
      }
    }

  private fun readListSize(): Int =
    readUInt32().also {
      if (it < 0) {
        throw NegativeListSizeException(
          "read list size $it, but expected a number greater than or equal to zero [yfvpf9pwt8]"
        )
      }
    }

  private fun readEntityIdSize(): Int =
    readUInt32().also {
      if (it < 0) {
        throw NegativeEntityIdSizeException(
          "read entity id size $it, " +
            "but expected a number greater than or equal to zero [agvqmbgknh]"
        )
      }
    }

  private enum class ValueType(val serializedByte: Byte, val displayName: String) {
    Null(QueryResultCodec.VALUE_NULL, "null"),
    Double(QueryResultCodec.VALUE_NUMBER_DOUBLE, "double"),
    PositiveZero(QueryResultCodec.VALUE_NUMBER_POSITIVE_ZERO, "+0.0"),
    NegativeZero(QueryResultCodec.VALUE_NUMBER_NEGATIVE_ZERO, "+0.0"),
    Fixed32Int(QueryResultCodec.VALUE_NUMBER_FIXED32, "fixed32Int"),
    UInt32(QueryResultCodec.VALUE_NUMBER_UINT32, "uint32"),
    SInt32(QueryResultCodec.VALUE_NUMBER_SINT32, "sint32"),
    UInt64(QueryResultCodec.VALUE_NUMBER_UINT64, "uint64"),
    SInt64(QueryResultCodec.VALUE_NUMBER_SINT64, "sint64"),
    BoolTrue(QueryResultCodec.VALUE_BOOL_TRUE, "true"),
    BoolFalse(QueryResultCodec.VALUE_BOOL_FALSE, "false"),
    KindNotSet(QueryResultCodec.VALUE_KIND_NOT_SET, "kindnotset"),
    List(QueryResultCodec.VALUE_LIST, "list"),
    Struct(QueryResultCodec.VALUE_STRUCT, "struct"),
    Entity(QueryResultCodec.VALUE_ENTITY, "entity"),
    StringEmpty(QueryResultCodec.VALUE_STRING_EMPTY, "emptystring"),
    String1Byte(QueryResultCodec.VALUE_STRING_1BYTE, "onebytestring"),
    String2Byte(QueryResultCodec.VALUE_STRING_2BYTE, "twobytestring"),
    String1Char(QueryResultCodec.VALUE_STRING_1CHAR, "onecharstring"),
    String2Char(QueryResultCodec.VALUE_STRING_2CHAR, "twocharstring"),
    StringUtf8(QueryResultCodec.VALUE_STRING_UTF8, "utf8"),
    StringUtf16(QueryResultCodec.VALUE_STRING_UTF16, "utf16");

    companion object {
      fun fromSerializedByte(serializedByte: Byte): ValueType? =
        entries.firstOrNull { it.serializedByte == serializedByte }
    }
  }

  private fun readValueType(): ValueType {
    val byte = readByte()
    val valueType = ValueType.fromSerializedByte(byte)
    return valueType
      ?: throw UnknownValueTypeIndicatorByteException(
        "read unknown value type indicator byte $byte, but expected one of " +
          ValueType.entries
            .sortedBy { it.serializedByte }
            .joinToString { "${it.serializedByte} (${it.displayName})" } +
          " [pmkb3sc2mn]"
      )
  }

  private enum class StringValueType(val valueType: ValueType) {
    Empty(ValueType.StringEmpty),
    OneByte(ValueType.String1Byte),
    TwoByte(ValueType.String2Byte),
    OneChar(ValueType.String1Char),
    TwoChar(ValueType.String2Char),
    Utf8(ValueType.StringUtf8),
    Utf16(ValueType.StringUtf16);

    companion object {
      fun fromValueType(valueType: ValueType): StringValueType? =
        entries.firstOrNull { it.valueType == valueType }
    }
  }

  private fun readStringValueType(): StringValueType =
    readValueType().let { valueType ->
      val stringValueType = StringValueType.fromValueType(valueType)
      return stringValueType
        ?: throw UnknownStringValueTypeIndicatorByteException(
          "read non-string value type indicator byte ${valueType.serializedByte} " +
            "(${valueType.displayName}), but expected one of " +
            StringValueType.entries
              .sortedBy { it.valueType.serializedByte }
              .joinToString { "${it.valueType.serializedByte} (${it.valueType.displayName})" } +
            " [hfvxx849cv]"
        )
    }

  private fun Byte.decodeChar(): Char {
    val codepoint = toUByte().toInt()
    val charCount = Character.toChars(codepoint, charArray, 0)
    check(charCount == 1) { "charCount=$charCount, but expected 1 (codepoint=$codepoint)" }
    return charArray[0]
  }

  private fun readString1Byte(): String {
    val byte = readByte()
    val char = byte.decodeChar()
    return char.toString()
  }

  private fun readString2Byte(): String {
    val byte1 = readByte()
    val byte2 = readByte()
    val char1 = byte1.decodeChar()
    val char2 = byte2.decodeChar()
    charArray[0] = char1
    charArray[1] = char2
    return String(charArray, 0, 2)
  }

  private fun readString1Char(): String {
    val char = readChar()
    return char.toString()
  }

  private fun readString2Char(): String {
    charArray[0] = readChar()
    charArray[1] = readChar()
    return String(charArray, 0, 2)
  }

  private fun readStringUtf8(): String {
    val byteCount = readStringByteCount()
    val charCount = readStringCharCount()

    val fastStringRead = readUtf8Fast(byteCount = byteCount, charCount = charCount)
    if (fastStringRead !== null) {
      return fastStringRead
    }

    charsetDecoder.reset()
    val charBuffer = CharBuffer.allocate(charCount)

    var bytesRemaining = byteCount
    while (byteBuffer.remaining() < bytesRemaining) {
      val view = byteBuffer.slice()
      view.limit(view.limit().coerceAtMost(bytesRemaining))
      val decodeResult = charsetDecoder.decode(view, charBuffer, false)
      byteBuffer.position(byteBuffer.position() + view.position())
      bytesRemaining -= view.position()

      if (!decodeResult.isUnderflow) {
        decodeResult.throwException()
      }

      if (byteBuffer.remaining() < bytesRemaining && !readSome()) {
        val totalBytesRead = byteBuffer.remaining() + byteCount - bytesRemaining
        throw Utf8EOFException(
          "end of input reached prematurely reading $charCount characters ($byteCount bytes) " +
            "of a UTF-8 encoded string: got ${charBuffer.position()} characters, " +
            "${charBuffer.remaining()} fewer characters than expected " +
            "($totalBytesRead bytes, $bytesRemaining fewer bytes than expected) [akn3x7p8rm]"
        )
      }
    }

    val view = byteBuffer.slice()
    view.limit(bytesRemaining)
    val finalDecodeResult = charsetDecoder.decode(view, charBuffer, true)
    byteBuffer.position(byteBuffer.position() + view.position())
    if (!finalDecodeResult.isUnderflow) {
      finalDecodeResult.throwException()
    }

    val flushResult = charsetDecoder.flush(charBuffer)
    if (!flushResult.isUnderflow) {
      flushResult.throwException()
    }
    if (charBuffer.hasRemaining()) {
      throw Utf8IncorrectNumCharactersException(
        "expected to read $charCount characters ($byteCount bytes) of a UTF-8 encoded string, " +
          "but only got ${charBuffer.position()} characters, " +
          "${charBuffer.remaining()} fewer characters than expected [dhvzxrcrqe]"
      )
    }

    charBuffer.clear()
    return charBuffer.toString()
  }

  private fun readUtf8Fast(byteCount: Int, charCount: Int): String? {
    if (byteCount > byteBuffer.capacity()) {
      return null
    }

    if (byteBuffer.remaining() < byteCount) {
      readSome()
      if (byteBuffer.remaining() < byteCount) {
        return null
      }
    }

    val byteBufferPosition = byteBuffer.position()
    val decodedString = Utf8.decodeUtf8(byteBuffer, byteBufferPosition, byteCount)
    byteBuffer.position(byteBufferPosition + byteCount)

    if (decodedString.length != charCount) {
      val differenceString =
        if (decodedString.length > charCount) {
          "${decodedString.length - charCount} more"
        } else {
          "${charCount - decodedString.length} fewer"
        }
      throw Utf8IncorrectNumCharactersException(
        "expected to read $charCount characters ($byteCount bytes) of a UTF-8 encoded string; " +
          "got the expected number of bytes, but got ${decodedString.length} characters, " +
          "$differenceString characters than expected [chq89pn4j6]"
      )
    }

    return decodedString
  }

  private fun readStringCustomUtf16(): String {
    val charCount = readStringCharCount()
    val charBuffer = CharBuffer.allocate(charCount)

    while (charBuffer.remaining() > 0) {
      if (byteBuffer.remaining() < 2 && !readSome()) {
        val totalBytesRead = byteBuffer.remaining() + (charBuffer.position() * 2)
        val expectedTotalBytesRead = charCount * 2
        throw Utf16EOFException(
          "end of input reached prematurely reading $charCount characters " +
            "($expectedTotalBytesRead bytes) of a UTF-16 encoded string: " +
            "got ${charBuffer.position()} characters, " +
            "${charBuffer.remaining()} fewer characters than expected " +
            "($totalBytesRead bytes, ${expectedTotalBytesRead-totalBytesRead} " +
            "fewer bytes than expected) [e399qdvzdz]"
        )
      }

      val charBufferView = byteBuffer.asCharBuffer()
      if (charBufferView.remaining() > charBuffer.remaining()) {
        charBufferView.limit(charBuffer.remaining())
      }

      charBuffer.put(charBufferView)
      byteBuffer.position(byteBuffer.position() + (charBufferView.position() * 2))
    }

    charBuffer.clear()
    return charBuffer.toString()
  }

  private fun readList(): ListValue {
    val size = readListSize()
    val listValueBuilder = ListValue.newBuilder()
    repeat(size) {
      val value = readValue()
      listValueBuilder.addValues(value)
    }
    return listValueBuilder.build()
  }

  private enum class StructValueType(val valueType: ValueType) {
    Struct(ValueType.Struct),
    Entity(ValueType.Entity);

    companion object {
      fun fromValueType(valueType: ValueType): StructValueType? =
        entries.firstOrNull { it.valueType == valueType }
    }
  }

  private fun readStructValueType(): StructValueType =
    readValueType().let { valueType ->
      val structValueType = StructValueType.fromValueType(valueType)
      return structValueType
        ?: throw UnknownStructValueTypeIndicatorByteException(
          "read non-struct value type indicator byte ${valueType.serializedByte} " +
            "(${valueType.displayName}), but expected one of " +
            StructValueType.entries
              .sortedBy { it.valueType.serializedByte }
              .joinToString { "${it.valueType.serializedByte} (${it.valueType.displayName})" } +
            " [s8b9jqegdy]"
        )
    }

  private fun readStruct(): Struct {
    val keyCount = readStructKeyCount()
    val structBuilder = Struct.newBuilder()
    repeat(keyCount) {
      val key = readString()
      val value = readValue()
      structBuilder.putFields(key, value)
    }
    return structBuilder.build()
  }

  private enum class EntitySubStructType(val valueType: ValueType) {
    Entity(ValueType.Entity),
    Struct(ValueType.Struct),
    List(ValueType.List),
    Scalar(ValueType.KindNotSet);

    companion object {
      fun fromValueType(valueType: ValueType): EntitySubStructType? =
        entries.firstOrNull { it.valueType == valueType }
    }
  }

  private fun readEntitySubStructType(): EntitySubStructType =
    readValueType().let { valueType ->
      val entitySubStructType = EntitySubStructType.fromValueType(valueType)
      return entitySubStructType
        ?: throw UnknownEntitySubStructTypeException(
          "read non-entity-sub-struct value type indicator byte ${valueType.serializedByte} " +
            "(${valueType.displayName}), but expected one of " +
            EntitySubStructType.entries
              .sortedBy { it.valueType.serializedByte }
              .joinToString { "${it.valueType.serializedByte} (${it.valueType.displayName})" } +
            " [w26af67653]"
        )
    }

  private fun readEntity(): Struct {
    val size = readEntityIdSize()
    val encodedEntityId = readBytes(size)
    val entity =
      entities.find { it.encodedId.contentEquals(encodedEntityId) }
        ?: throw EntityNotFoundException(
          "could not find entity with encoded id ${encodedEntityId.to0xHexString()} [p583k77y7r]"
        )
    return readEntitySubStruct(entity.data)
  }

  private fun readEntitySubStruct(entity: Struct): Struct {
    val structKeyCount = readStructKeyCount()
    val structBuilder = Struct.newBuilder()
    repeat(structKeyCount) {
      val key = readString()
      val value = readEntityValue(key, entity)
      structBuilder.putFields(key, value)
    }
    return structBuilder.build()
  }

  private fun readEntitySubList(entity: ListValue): ListValue {
    val listSize = readListSize()
    val listValueBuilder = ListValue.newBuilder()
    repeat(listSize) { index ->
      val value = readEntityValue(index, entity)
      listValueBuilder.addValues(value)
    }
    return listValueBuilder.build()
  }

  private inline fun readEntityValue(getSubEntity: () -> Value): Value =
    when (readEntitySubStructType()) {
      EntitySubStructType.Entity -> readEntity().toValueProto()
      EntitySubStructType.Struct -> {
        val subEntity = getSubEntity().structValue
        readEntitySubStruct(subEntity).toValueProto()
      }
      EntitySubStructType.List -> {
        val subEntity = getSubEntity().listValue
        readEntitySubList(subEntity).toValueProto()
      }
      EntitySubStructType.Scalar -> getSubEntity()
    }

  private fun readEntityValue(key: String, entity: Struct): Value = readEntityValue {
    entity.getFieldsOrThrow(key)
  }

  private fun readEntityValue(index: Int, entity: ListValue): Value = readEntityValue {
    entity.getValues(index)
  }

  private fun readValue(): Value {
    val valueBuilder = Value.newBuilder()
    when (readValueType()) {
      ValueType.Null -> valueBuilder.setNullValue(NullValue.NULL_VALUE)
      ValueType.Double -> valueBuilder.setNumberValue(readDouble())
      ValueType.PositiveZero -> valueBuilder.setNumberValue(0.0)
      ValueType.NegativeZero -> valueBuilder.setNumberValue(-0.0)
      ValueType.Fixed32Int -> valueBuilder.setNumberValue(readFixed32Int().toDouble())
      ValueType.UInt32 -> valueBuilder.setNumberValue(readUInt32().toDouble())
      ValueType.SInt32 -> valueBuilder.setNumberValue(readSInt32().toDouble())
      ValueType.UInt64 -> valueBuilder.setNumberValue(readUInt64().toDouble())
      ValueType.SInt64 -> valueBuilder.setNumberValue(readSInt64().toDouble())
      ValueType.BoolTrue -> valueBuilder.setBoolValue(true)
      ValueType.BoolFalse -> valueBuilder.setBoolValue(false)
      ValueType.List -> valueBuilder.setListValue(readList())
      ValueType.Struct -> valueBuilder.setStructValue(readStruct())
      ValueType.KindNotSet -> {}
      ValueType.Entity -> valueBuilder.setStructValue(readEntity())
      ValueType.StringEmpty -> valueBuilder.setStringValue("")
      ValueType.String1Byte -> valueBuilder.setStringValue(readString1Byte())
      ValueType.String2Byte -> valueBuilder.setStringValue(readString2Byte())
      ValueType.String1Char -> valueBuilder.setStringValue(readString1Char())
      ValueType.String2Char -> valueBuilder.setStringValue(readString2Char())
      ValueType.StringUtf8 -> valueBuilder.setStringValue(readStringUtf8())
      ValueType.StringUtf16 -> valueBuilder.setStringValue(readStringCustomUtf16())
    }
    return valueBuilder.build()
  }

  sealed class DecodeException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

  class BadMagicException(message: String) : DecodeException(message)

  class NegativeStructKeyCountException(message: String) : DecodeException(message)

  class NegativeStringByteCountException(message: String) : DecodeException(message)

  class NegativeStringCharCountException(message: String) : DecodeException(message)

  class NegativeListSizeException(message: String) : DecodeException(message)

  class UnknownValueTypeIndicatorByteException(message: String) : DecodeException(message)

  class UnknownStringValueTypeIndicatorByteException(message: String) : DecodeException(message)

  class UnknownStructValueTypeIndicatorByteException(message: String) : DecodeException(message)

  class UnknownEntitySubStructTypeException(message: String) : DecodeException(message)

  class ByteArrayEOFException(message: String) : DecodeException(message)

  class UInt32DecodeException(message: String, cause: Throwable? = null) :
    DecodeException(message, cause)

  class SInt32DecodeException(message: String, cause: Throwable? = null) :
    DecodeException(message, cause)

  class UInt64DecodeException(message: String, cause: Throwable? = null) :
    DecodeException(message, cause)

  class SInt64DecodeException(message: String, cause: Throwable? = null) :
    DecodeException(message, cause)

  class Utf8EOFException(message: String) : DecodeException(message)

  class Utf8IncorrectNumCharactersException(message: String) : DecodeException(message)

  class Utf16EOFException(message: String) : DecodeException(message)

  class NegativeEntityIdSizeException(message: String) : DecodeException(message)

  class EntityNotFoundException(message: String) : DecodeException(message)

  companion object {

    fun decode(byteArray: ByteArray, entities: List<Entity>): Struct =
      ByteArrayInputStream(byteArray).use { byteArrayInputStream ->
        Channels.newChannel(byteArrayInputStream).use { channel ->
          val decoder = QueryResultDecoder(channel, entities)
          decoder.decode()
        }
      }
  }
}
