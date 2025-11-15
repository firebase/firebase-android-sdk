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

import com.google.firebase.dataconnect.sqlite.CodedIntegersExts.getUInt32
import com.google.firebase.dataconnect.sqlite.QueryResultCodec.Entity
import com.google.firebase.dataconnect.util.ProtoUtil.toValueProto
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
    readHeader()
    return when (readStructType()) {
      StructType.Struct -> readStruct()
      StructType.Entity -> readEntity()
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

  private fun readInt(): Int {
    ensureRemaining(4)
    return byteBuffer.getInt()
  }

  private fun readUInt32(): Int {
    ensureRemaining(1)
    while (true) {
      try {
        return byteBuffer.getUInt32()
      } catch (_: CodedIntegers.MalformedVarintException) {
        if (!readSome()) {
          throw UInt32EOFException(
            "end of input reached prematurely reading uint32: " +
              "got ${byteBuffer.remaining()} bytes," +
              "but need between 1 and ${CodedIntegers.MAX_VARINT32_SIZE} bytes [f9bkxazr3r]"
          )
        }
      }
    }
  }

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

  private fun readString(): String = readString(readStringType())

  private fun readString(stringType: StringType): String =
    when (stringType) {
      StringType.Empty -> ""
      StringType.OneByte -> readString1Byte()
      StringType.TwoByte -> readString2Byte()
      StringType.OneChar -> readString1Char()
      StringType.TwoChar -> readString2Char()
      StringType.Utf8 -> readStringUtf8()
      StringType.Utf16 -> readStringCustomUtf16()
    }

  private fun readHeader(): Int =
    readInt().also {
      if (it != QueryResultCodec.QUERY_RESULT_HEADER) {
        throw BadHeaderException(
          "read header 0x" +
            it.toUInt().toString(16).padStart(8, '0') +
            ", but expected 0x" +
            QueryResultCodec.QUERY_RESULT_HEADER.toUInt().toString(16).padStart(8, '0') +
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

  private enum class ValueKindCase(val serializedByte: Byte, val displayName: String) {
    Null(QueryResultCodec.VALUE_NULL, "null"),
    Number(QueryResultCodec.VALUE_NUMBER, "number"),
    BoolTrue(QueryResultCodec.VALUE_BOOL_TRUE, "true"),
    BoolFalse(QueryResultCodec.VALUE_BOOL_FALSE, "false"),
    KindNotSet(QueryResultCodec.VALUE_KIND_NOT_SET, "kindnotset"),
    List(QueryResultCodec.VALUE_LIST, "list"),
    Struct(QueryResultCodec.VALUE_STRUCT, "struct"),
    Entity(QueryResultCodec.VALUE_ENTITY, "entity"),
    StringEmpty(QueryResultCodec.VALUE_STRING_EMPTY, "emptystring"),
    String1Byte(QueryResultCodec.VALUE_STRING_1BYTE, "1bytestring"),
    String2Byte(QueryResultCodec.VALUE_STRING_2BYTE, "2bytestring"),
    String1Char(QueryResultCodec.VALUE_STRING_1CHAR, "1charstring"),
    String2Char(QueryResultCodec.VALUE_STRING_2CHAR, "2charstring"),
    StringUtf8(QueryResultCodec.VALUE_STRING_UTF8, "utf8"),
    StringUtf16(QueryResultCodec.VALUE_STRING_UTF16, "utf16");

    companion object {
      fun fromSerializedByte(serializedByte: Byte): ValueKindCase? =
        entries.firstOrNull { it.serializedByte == serializedByte }
    }
  }

  private fun readKindCase(): ValueKindCase {
    val byte = readByte()
    val kindCase = ValueKindCase.fromSerializedByte(byte)
    if (kindCase === null) {
      throw UnknownKindCaseByteException(
        "read unknown kind case byte $byte, but expected one of " +
          ValueKindCase.entries
            .sortedBy { it.serializedByte }
            .joinToString { "${it.serializedByte} (${it.displayName})" } +
          " [pmkb3sc2mn]"
      )
    }
    return kindCase
  }

  private enum class StringType(val valueKindCase: ValueKindCase) {
    Empty(ValueKindCase.StringEmpty),
    OneByte(ValueKindCase.String1Byte),
    TwoByte(ValueKindCase.String2Byte),
    OneChar(ValueKindCase.String1Char),
    TwoChar(ValueKindCase.String2Char),
    Utf8(ValueKindCase.StringUtf8),
    Utf16(ValueKindCase.StringUtf16);

    companion object {
      fun fromValueKindCase(valueKindCase: ValueKindCase): StringType? =
        entries.firstOrNull { it.valueKindCase == valueKindCase }
    }
  }

  private fun readStringType(): StringType =
    readKindCase().let { valueKindCase ->
      val stringType = StringType.fromValueKindCase(valueKindCase)
      if (stringType === null) {
        throw UnknownStringTypeException(
          "read non-string kind case byte ${valueKindCase.serializedByte} " +
            "(${valueKindCase.displayName}), but expected one of " +
            StringType.entries
              .sortedBy { it.valueKindCase.serializedByte }
              .joinToString {
                "${it.valueKindCase.serializedByte} (${it.valueKindCase.displayName})"
              } +
            " [hfvxx849cv]"
        )
      }
      stringType
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
      throw Utf8TooFewCharactersException(
        "expected to read $charCount characters ($byteCount bytes) of a UTF-8 encoded string, " +
          "but only got ${charBuffer.position()} characters, " +
          "${charBuffer.remaining()} fewer characters than expected [dhvzxrcrqe]"
      )
    }

    charBuffer.clear()
    return charBuffer.toString()
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

  private enum class StructType(val valueKindCase: ValueKindCase) {
    Struct(ValueKindCase.Struct),
    Entity(ValueKindCase.Entity);

    companion object {
      fun fromValueKindCase(valueKindCase: ValueKindCase): StructType? =
        entries.firstOrNull { it.valueKindCase == valueKindCase }
    }
  }

  private fun readStructType(): StructType =
    readKindCase().let { valueKindCase ->
      val structType = StructType.fromValueKindCase(valueKindCase)
      if (structType === null) {
        throw UnknownStructTypeException(
          "read non-struct kind case byte ${valueKindCase.serializedByte} " +
            "(${valueKindCase.displayName}), but expected one of " +
            StructType.entries
              .sortedBy { it.valueKindCase.serializedByte }
              .joinToString {
                "${it.valueKindCase.serializedByte} (${it.valueKindCase.displayName})"
              } +
            " [s8b9jqegdy]"
        )
      }
      structType
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

  private enum class EntitySubStructType(val valueKindCase: ValueKindCase) {
    Entity(ValueKindCase.Entity),
    Struct(ValueKindCase.Struct),
    List(ValueKindCase.List),
    Scalar(ValueKindCase.KindNotSet);

    companion object {
      fun fromValueKindCase(valueKindCase: ValueKindCase): EntitySubStructType? =
        entries.firstOrNull { it.valueKindCase == valueKindCase }
    }
  }

  private fun readEntitySubStructType(): EntitySubStructType =
    readKindCase().let { valueKindCase ->
      val entitySubStructType = EntitySubStructType.fromValueKindCase(valueKindCase)
      if (entitySubStructType === null) {
        throw UnknownEntitySubStructTypeException(
          "read non-entity-sub-struct kind case byte ${valueKindCase.serializedByte} " +
            "(${valueKindCase.displayName}), but expected one of " +
            EntitySubStructType.entries
              .sortedBy { it.valueKindCase.serializedByte }
              .joinToString {
                "${it.valueKindCase.serializedByte} (${it.valueKindCase.displayName})"
              } +
            " [w26af67653]"
        )
      }
      entitySubStructType
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
    when (readKindCase()) {
      ValueKindCase.Null -> valueBuilder.setNullValue(NullValue.NULL_VALUE)
      ValueKindCase.Number -> valueBuilder.setNumberValue(readDouble())
      ValueKindCase.BoolTrue -> valueBuilder.setBoolValue(true)
      ValueKindCase.BoolFalse -> valueBuilder.setBoolValue(false)
      ValueKindCase.List -> valueBuilder.setListValue(readList())
      ValueKindCase.Struct -> valueBuilder.setStructValue(readStruct())
      ValueKindCase.KindNotSet -> {}
      ValueKindCase.Entity -> valueBuilder.setStructValue(readEntity())
      ValueKindCase.StringEmpty -> valueBuilder.setStringValue("")
      ValueKindCase.String1Byte -> valueBuilder.setStringValue(readString1Byte())
      ValueKindCase.String2Byte -> valueBuilder.setStringValue(readString2Byte())
      ValueKindCase.String1Char -> valueBuilder.setStringValue(readString1Char())
      ValueKindCase.String2Char -> valueBuilder.setStringValue(readString2Char())
      ValueKindCase.StringUtf8 -> valueBuilder.setStringValue(readStringUtf8())
      ValueKindCase.StringUtf16 -> valueBuilder.setStringValue(readStringCustomUtf16())
    }
    return valueBuilder.build()
  }

  sealed class DecodeException(message: String) : Exception(message)

  class BadHeaderException(message: String) : DecodeException(message)

  class NegativeStructKeyCountException(message: String) : DecodeException(message)

  class NegativeStringByteCountException(message: String) : DecodeException(message)

  class NegativeStringCharCountException(message: String) : DecodeException(message)

  class NegativeListSizeException(message: String) : DecodeException(message)

  class UnknownKindCaseByteException(message: String) : DecodeException(message)

  class UnknownStringTypeException(message: String) : DecodeException(message)

  class UnknownStructTypeException(message: String) : DecodeException(message)

  class UnknownEntitySubStructTypeException(message: String) : DecodeException(message)

  class ByteArrayEOFException(message: String) : DecodeException(message)

  class UInt32EOFException(message: String) : DecodeException(message)

  class Utf8EOFException(message: String) : DecodeException(message)

  class Utf8TooFewCharactersException(message: String) : DecodeException(message)

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
