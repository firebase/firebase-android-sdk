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

  private inline fun ensureRemaining(
    byteCount: Int,
    eofErrorId: String,
    eofException: (message: String, cause: Throwable?) -> Throwable
  ) {
    while (byteBuffer.remaining() < byteCount) {
      if (!readSome()) {
        val remaining = byteBuffer.remaining()
        throw eofException(
          "end of input reached prematurely reading $byteCount bytes: " +
            "got $remaining bytes (${byteBuffer.get0xHexString()}), " +
            "${byteCount-remaining} fewer bytes than expected " +
            "[xg5y5fm2vk, eofErrorId=$eofErrorId]",
          null
        )
      }
    }
  }

  private fun readByte(eofErrorId: String): Byte {
    ensureRemaining(1, eofErrorId, ::ByteEOFException)
    return byteBuffer.get()
  }

  private fun readChar(eofErrorId: String): Char {
    ensureRemaining(2, eofErrorId, ::CharEOFException)
    return byteBuffer.getChar()
  }

  private fun readFixed32Int(eofErrorId: String): Int {
    ensureRemaining(4, eofErrorId, ::Fixed32IntEOFException)
    return byteBuffer.getInt()
  }

  private inline fun <T : Number> readVarint(
    typeName: String,
    maxSize: Int,
    isValidDecodedValue: (T) -> Boolean,
    invalidValueErrorId: String,
    decodeErrorId: String,
    eofErrorId: String,
    invalidValueException: (message: String) -> Exception,
    decodeException: (message: String, cause: Throwable?) -> Exception,
    eofException: (message: String, cause: Throwable?) -> Exception,
    read: (ByteBuffer) -> T
  ): T {
    ensureRemaining(1, eofErrorId, eofException)
    while (true) {
      val originalPosition = byteBuffer.position()

      val originalLimit = byteBuffer.limit()
      val readLimit = originalLimit.coerceAtMost(byteBuffer.position() + maxSize)
      byteBuffer.limit(readLimit)
      val readResult = runCatching { read(byteBuffer) }
      byteBuffer.limit(originalLimit)
      val decodedByteCount = byteBuffer.position() - originalPosition

      readResult.fold(
        onSuccess = { decodedValue ->
          if (isValidDecodedValue(decodedValue)) {
            return decodedValue
          }

          byteBuffer.position(originalPosition)
          throw invalidValueException(
            "invalid $typeName value decoded: $decodedValue " +
              "(decoded from $decodedByteCount bytes: " +
              "${byteBuffer.get0xHexString(length = decodedByteCount)}) " +
              "[pypnp79waw, invalidValueErrorId=$invalidValueErrorId]"
          )
        },
        onFailure = {
          byteBuffer.position(originalPosition)

          if (byteBuffer.remaining() >= maxSize) {
            throw decodeException(
              "$typeName decode failed of $decodedByteCount bytes: " +
                "${byteBuffer.get0xHexString(length=decodedByteCount)} " +
                "[ybydmsykkp, decodeErrorId=$decodeErrorId]",
              readResult.exceptionOrNull()
            )
          }

          if (!readSome()) {
            throw eofException(
              "end of input reached during decoding of $typeName value: " +
                "got ${byteBuffer.remaining()} bytes (${byteBuffer.get0xHexString()}), " +
                " but expected between 1 and $maxSize bytes [c439qmdmnk, eofErrorId=$eofErrorId]",
              readResult.exceptionOrNull()
            )
          }
        }
      )
    }
  }
  private fun readUInt32(
    invalidValueErrorId: String,
    decodeErrorId: String,
    eofErrorId: String,
  ): Int =
    readVarint(
      typeName = "uint32",
      maxSize = CodedIntegers.MAX_VARINT32_SIZE,
      isValidDecodedValue = { it >= 0 },
      invalidValueErrorId = invalidValueErrorId,
      decodeErrorId = decodeErrorId,
      eofErrorId = eofErrorId,
      invalidValueException = ::UInt32InvalidValueException,
      decodeException = ::UInt32DecodeException,
      eofException = ::UInt32EOFException,
      read = { byteBuffer -> byteBuffer.getUInt32() },
    )

  @Suppress("SameParameterValue")
  private fun readSInt32(
    invalidValueErrorId: String,
    decodeErrorId: String,
    eofErrorId: String,
  ): Int =
    readVarint(
      typeName = "sint32",
      maxSize = CodedIntegers.MAX_VARINT32_SIZE,
      isValidDecodedValue = { true },
      invalidValueErrorId = invalidValueErrorId,
      decodeErrorId = decodeErrorId,
      eofErrorId = eofErrorId,
      invalidValueException = ::SInt32InvalidValueException,
      decodeException = ::SInt32DecodeException,
      eofException = ::SInt32EOFException,
      read = { byteBuffer -> byteBuffer.getSInt32() },
    )

  @Suppress("SameParameterValue")
  private fun readUInt64(
    invalidValueErrorId: String,
    decodeErrorId: String,
    eofErrorId: String,
  ): Long =
    readVarint(
      typeName = "uint64",
      maxSize = CodedIntegers.MAX_VARINT64_SIZE,
      isValidDecodedValue = { it >= 0 },
      invalidValueErrorId = invalidValueErrorId,
      decodeErrorId = decodeErrorId,
      eofErrorId = eofErrorId,
      invalidValueException = ::UInt64InvalidValueException,
      decodeException = ::UInt64DecodeException,
      eofException = ::UInt64EOFException,
      read = { byteBuffer -> byteBuffer.getUInt64() },
    )

  @Suppress("SameParameterValue")
  private fun readSInt64(
    invalidValueErrorId: String,
    decodeErrorId: String,
    eofErrorId: String,
  ): Long =
    readVarint(
      typeName = "sint64",
      maxSize = CodedIntegers.MAX_VARINT64_SIZE,
      isValidDecodedValue = { true },
      invalidValueErrorId = invalidValueErrorId,
      decodeErrorId = decodeErrorId,
      eofErrorId = eofErrorId,
      invalidValueException = ::SInt64InvalidValueException,
      decodeException = ::SInt64DecodeException,
      eofException = ::SInt64EOFException,
      read = { byteBuffer -> byteBuffer.getSInt64() },
    )

  @Suppress("SameParameterValue")
  private fun readDouble(eofErrorId: String): Double {
    ensureRemaining(8, eofErrorId, ::DoubleEOFException)
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

  private fun readMagic() {
    ensureRemaining(4, eofErrorId = "MagicEOF", ::MagicEOFException)
    val magic = byteBuffer.getInt()

    if (magic != QueryResultCodec.QUERY_RESULT_MAGIC) {
      throw BadMagicException(
        "read magic value 0x" +
          magic.toUInt().toString(16).padStart(8, '0') +
          ", but expected 0x" +
          QueryResultCodec.QUERY_RESULT_MAGIC.toUInt().toString(16).padStart(8, '0') +
          " [jk832sz9hx]"
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

  private inline fun readValueType(
    valueTypeDescription: String,
    eofErrorId: String,
    unknownValueTypeIndicatorByteException: (message: String, cause: Throwable?) -> Exception
  ): ValueType {
    val byte = readByte(eofErrorId)
    val valueType = ValueType.fromSerializedByte(byte)
    return valueType
      ?: throw unknownValueTypeIndicatorByteException(
        "read unknown $valueTypeDescription value type indicator byte: $byte; expected one of " +
          ValueType.entries
            .sortedBy { it.serializedByte }
            .joinToString { "${it.serializedByte} (${it.displayName})" } +
          " [pmkb3sc2mn]",
        null
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

  private fun readStringValueType(): StringValueType {
    val valueType =
      readValueType(
        valueTypeDescription = "string",
        eofErrorId = "StringValueTypeIndicatorByteEOF",
        unknownValueTypeIndicatorByteException = ::UnknownStringValueTypeIndicatorByteException,
      )

    StringValueType.fromValueType(valueType)?.let {
      return it
    }

    throw NonStringValueTypeIndicatorByteException(
      "read non-string value type indicator byte: ${valueType.serializedByte} " +
        "(${valueType.displayName}); expected one of " +
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
    val byte = readByte(eofErrorId = "String1ByteEOF")
    val char = byte.decodeChar()
    return char.toString()
  }

  private fun readString2Byte(): String {
    val byte1 = readByte(eofErrorId = "String2ByteEOF1")
    val byte2 = readByte(eofErrorId = "String2ByteEOF2")
    val char1 = byte1.decodeChar()
    val char2 = byte2.decodeChar()
    charArray[0] = char1
    charArray[1] = char2
    return String(charArray, 0, 2)
  }

  private fun readString1Char(): String {
    val char = readChar(eofErrorId = "String1CharEOF")
    return char.toString()
  }

  private fun readString2Char(): String {
    charArray[0] = readChar(eofErrorId = "String2CharEOF1")
    charArray[1] = readChar(eofErrorId = "String2CharEOF2")
    return String(charArray, 0, 2)
  }

  private fun readStringUtf8(): String {
    val byteCount =
      readUInt32(
        invalidValueErrorId = "StringUtf8ByteCountInvalidValue",
        decodeErrorId = "StringUtf8ByteCountDecodeFailed",
        eofErrorId = "StringUtf8ByteCountEOF",
      )
    val charCount =
      readUInt32(
        invalidValueErrorId = "StringUtf8CharCountInvalidValue",
        decodeErrorId = "StringUtf8CharCountDecodeFailed",
        eofErrorId = "StringUtf8CharCountEOF",
      )

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
    val charCount =
      readUInt32(
        invalidValueErrorId = "StringUtf16CharCountInvalidValue",
        decodeErrorId = "StringUtf16CharCountDecodeFailed",
        eofErrorId = "StringUtf16CharCountEOF",
      )

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
    val size =
      readUInt32(
        invalidValueErrorId = "ListSizeInvalidValue",
        decodeErrorId = "ListSizeDecodeFailed",
        eofErrorId = "ListSizeEOF",
      )

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

  private fun readStructValueType(): StructValueType {
    val valueType =
      readValueType(
        valueTypeDescription = "struct",
        eofErrorId = "StructValueTypeIndicatorByteEOF",
        unknownValueTypeIndicatorByteException = ::UnknownStructValueTypeIndicatorByteException,
      )

    StructValueType.fromValueType(valueType)?.let {
      return it
    }

    throw NonStructValueTypeIndicatorByteException(
      "read non-struct value type indicator byte: ${valueType.serializedByte} " +
        "(${valueType.displayName}); expected one of " +
        StructValueType.entries
          .sortedBy { it.valueType.serializedByte }
          .joinToString { "${it.valueType.serializedByte} (${it.valueType.displayName})" } +
        " [w5k8zmq9nz]"
    )
  }

  private fun readStruct(): Struct {
    val keyCount =
      readUInt32(
        invalidValueErrorId = "StructKeyCountInvalidValue",
        decodeErrorId = "StructKeyCountDecodeFailed",
        eofErrorId = "StructKeyCountEOF",
      )

    val structBuilder = Struct.newBuilder()
    repeat(keyCount) {
      val key = readString()
      val value = readValue()
      structBuilder.putFields(key, value)
    }
    return structBuilder.build()
  }

  private enum class EntitySubStructValueType(val valueType: ValueType) {
    Entity(ValueType.Entity),
    Struct(ValueType.Struct),
    List(ValueType.List),
    Scalar(ValueType.KindNotSet);

    companion object {
      fun fromValueType(valueType: ValueType): EntitySubStructValueType? =
        entries.firstOrNull { it.valueType == valueType }
    }
  }

  private fun readEntitySubStructValueType(): EntitySubStructValueType {
    val valueType =
      readValueType(
        valueTypeDescription = "entity sub-struct",
        eofErrorId = "EntitySubStructValueTypeIndicatorByteEOF",
        unknownValueTypeIndicatorByteException =
          ::UnknownEntitySubStructValueTypeIndicatorByteException,
      )

    EntitySubStructValueType.fromValueType(valueType)?.let {
      return it
    }

    throw NonEntitySubStructValueTypeIndicatorByteException(
      "read non-entity-sub-struct value type indicator byte: ${valueType.serializedByte} " +
        "(${valueType.displayName}); expected one of " +
        EntitySubStructValueType.entries
          .sortedBy { it.valueType.serializedByte }
          .joinToString { "${it.valueType.serializedByte} (${it.valueType.displayName})" } +
        " [xsaekqs6pw]"
    )
  }

  private fun readEntity(): Struct {
    val encodedEntityIdSize =
      readUInt32(
        invalidValueErrorId = "EncodedEntityIdSizeInvalidValue",
        decodeErrorId = "EncodedEntityIdSizeDecodeFailed",
        eofErrorId = "EncodedEntityIdSizeEOF",
      )

    val encodedEntityId = readBytes(encodedEntityIdSize)
    val entity =
      entities.find { it.encodedId.contentEquals(encodedEntityId) }
        ?: throw EntityNotFoundException(
          "could not find entity with encoded id ${encodedEntityId.to0xHexString()} [p583k77y7r]"
        )
    return readEntitySubStruct(entity.data)
  }

  private fun readEntitySubStruct(entity: Struct): Struct {
    val structKeyCount =
      readUInt32(
        invalidValueErrorId = "EntitySubStructKeyCountInvalidValue",
        decodeErrorId = "EntitySubStructKeyCountDecodeFailed",
        eofErrorId = "EntitySubStructKeyCountEOF",
      )

    val structBuilder = Struct.newBuilder()
    repeat(structKeyCount) {
      val key = readString()
      val value = readEntityValue(key, entity)
      structBuilder.putFields(key, value)
    }
    return structBuilder.build()
  }

  private fun readEntitySubList(entity: ListValue): ListValue {
    val listSize =
      readUInt32(
        invalidValueErrorId = "EntitySubListSizeInvalidValue",
        decodeErrorId = "EntitySubListSizeDecodeFailed",
        eofErrorId = "EntitySubListSizeEOF",
      )

    val listValueBuilder = ListValue.newBuilder()
    repeat(listSize) { index ->
      val value = readEntityValue(index, entity)
      listValueBuilder.addValues(value)
    }
    return listValueBuilder.build()
  }

  private inline fun readEntityValue(getSubEntity: () -> Value): Value =
    when (readEntitySubStructValueType()) {
      EntitySubStructValueType.Entity -> readEntity().toValueProto()
      EntitySubStructValueType.Struct -> {
        val subEntity = getSubEntity().structValue
        readEntitySubStruct(subEntity).toValueProto()
      }
      EntitySubStructValueType.List -> {
        val subEntity = getSubEntity().listValue
        readEntitySubList(subEntity).toValueProto()
      }
      EntitySubStructValueType.Scalar -> getSubEntity()
    }

  private fun readEntityValue(key: String, entity: Struct): Value = readEntityValue {
    entity.getFieldsOrThrow(key)
  }

  private fun readEntityValue(index: Int, entity: ListValue): Value = readEntityValue {
    entity.getValues(index)
  }

  private fun readValue(): Value {
    val valueType =
      readValueType(
        valueTypeDescription = "value",
        eofErrorId = "ReadValueValueTypeIndicatorByteEOF",
        unknownValueTypeIndicatorByteException = ::UnknownValueTypeIndicatorByteException,
      )

    val valueBuilder = Value.newBuilder()

    when (valueType) {
      ValueType.Null -> valueBuilder.setNullValue(NullValue.NULL_VALUE)
      ValueType.Double -> valueBuilder.setNumberValue(readDouble(eofErrorId = "ReadDoubleValueEOF"))
      ValueType.PositiveZero -> valueBuilder.setNumberValue(0.0)
      ValueType.NegativeZero -> valueBuilder.setNumberValue(-0.0)
      ValueType.Fixed32Int ->
        valueBuilder.setNumberValue(
          readFixed32Int(eofErrorId = "ReadFixed32IntValueEOF").toDouble()
        )
      ValueType.UInt32 ->
        valueBuilder.setNumberValue(
          readUInt32(
              invalidValueErrorId = "ReadUInt32ValueInvalidValue",
              decodeErrorId = "ReadUInt32ValueDecodeError",
              eofErrorId = "ReadUInt32ValueEOF",
            )
            .toDouble()
        )
      ValueType.SInt32 ->
        valueBuilder.setNumberValue(
          readSInt32(
              invalidValueErrorId = "ReadSInt32ValueInvalidValue",
              decodeErrorId = "ReadSInt32ValueDecodeError",
              eofErrorId = "ReadSInt32ValueEOF",
            )
            .toDouble()
        )
      ValueType.UInt64 ->
        valueBuilder.setNumberValue(
          readUInt64(
              invalidValueErrorId = "ReadUInt64ValueInvalidValue",
              decodeErrorId = "ReadUInt64ValueDecodeError",
              eofErrorId = "ReadUInt64ValueEOF",
            )
            .toDouble()
        )
      ValueType.SInt64 ->
        valueBuilder.setNumberValue(
          readSInt64(
              invalidValueErrorId = "ReadSInt64ValueInvalidValue",
              decodeErrorId = "ReadSInt64ValueDecodeError",
              eofErrorId = "ReadSInt64ValueEOF",
            )
            .toDouble()
        )
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

  sealed class EOFException(message: String, cause: Throwable? = null) :
    DecodeException(message, cause)

  class BadMagicException(message: String, cause: Throwable? = null) :
    DecodeException(message, cause)

  class MagicEOFException(message: String, cause: Throwable? = null) : EOFException(message, cause)

  class UnknownValueTypeIndicatorByteException(message: String, cause: Throwable? = null) :
    DecodeException(message, cause)

  class UnknownStringValueTypeIndicatorByteException(message: String, cause: Throwable? = null) :
    DecodeException(message, cause)

  class NonStringValueTypeIndicatorByteException(message: String, cause: Throwable? = null) :
    DecodeException(message, cause)

  class UnknownStructValueTypeIndicatorByteException(message: String, cause: Throwable? = null) :
    DecodeException(message, cause)

  class NonStructValueTypeIndicatorByteException(message: String, cause: Throwable? = null) :
    DecodeException(message, cause)

  class UnknownEntitySubStructValueTypeIndicatorByteException(
    message: String,
    cause: Throwable? = null
  ) : DecodeException(message, cause)

  class NonEntitySubStructValueTypeIndicatorByteException(
    message: String,
    cause: Throwable? = null
  ) : DecodeException(message, cause)

  class ByteEOFException(message: String, cause: Throwable? = null) : EOFException(message, cause)

  class CharEOFException(message: String, cause: Throwable? = null) : EOFException(message, cause)

  class Fixed32IntEOFException(message: String, cause: Throwable? = null) :
    EOFException(message, cause)

  class DoubleEOFException(message: String, cause: Throwable? = null) :
    EOFException(message, cause)

  class ByteArrayEOFException(message: String, cause: Throwable? = null) :
    EOFException(message, cause)

  class UInt32EOFException(message: String, cause: Throwable? = null) :
    EOFException(message, cause)

  class UInt32InvalidValueException(message: String, cause: Throwable? = null) :
    DecodeException(message, cause)

  class UInt32DecodeException(message: String, cause: Throwable? = null) :
    DecodeException(message, cause)

  class UInt64EOFException(message: String, cause: Throwable? = null) :
    EOFException(message, cause)

  class UInt64InvalidValueException(message: String, cause: Throwable? = null) :
    DecodeException(message, cause)

  class UInt64DecodeException(message: String, cause: Throwable? = null) :
    DecodeException(message, cause)

  class SInt32EOFException(message: String, cause: Throwable? = null) :
    EOFException(message, cause)

  class SInt32InvalidValueException(message: String, cause: Throwable? = null) :
    DecodeException(message, cause)

  class SInt32DecodeException(message: String, cause: Throwable? = null) :
    DecodeException(message, cause)

  class SInt64EOFException(message: String, cause: Throwable? = null) :
    EOFException(message, cause)

  class SInt64InvalidValueException(message: String, cause: Throwable? = null) :
    DecodeException(message, cause)

  class SInt64DecodeException(message: String, cause: Throwable? = null) :
    DecodeException(message, cause)

  class Utf8EOFException(message: String, cause: Throwable? = null) : EOFException(message, cause)

  class Utf8IncorrectNumCharactersException(message: String, cause: Throwable? = null) :
    DecodeException(message, cause)

  class Utf16EOFException(message: String, cause: Throwable? = null) : EOFException(message, cause)

  class EntityNotFoundException(message: String, cause: Throwable? = null) :
    DecodeException(message, cause)

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
