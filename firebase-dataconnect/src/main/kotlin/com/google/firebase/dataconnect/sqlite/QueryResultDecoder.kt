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
import com.google.firebase.dataconnect.util.StringUtil.ellipsizeMiddle
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

    val rootStructValueType =
      readValueType(
        eofErrorId = "RootStructValueTypeIndicatorByteEOF",
        unknownErrorId = "RootStructValueTypeIndicatorByteUnknown",
        unexpectedErrorId = "RootStructValueTypeIndicatorByteUnexpected",
        map = RootStructValueType.instanceByValueType,
      )

    return when (rootStructValueType) {
      RootStructValueType.Struct -> readStruct()
      RootStructValueType.Entity -> readEntity()
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
          "end of input reached prematurely while reading $byteCount bytes: " +
            "got $remaining bytes (${byteBuffer.get0xHexString()}), " +
            "${byteCount-remaining} fewer bytes than expected " +
            "[xg5y5fm2vk, eofErrorId=$eofErrorId]",
          null
        )
      }
    }
  }

  private interface VarintValueVerifier<T : Number> {
    val errorId: String
    fun isValid(decodedValue: T): Boolean
    fun exception(message: String): Exception
  }

  private inline fun <T : Number> readVarint(
    typeName: String,
    maxSize: Int,
    decodeErrorId: String,
    eofErrorId: String,
    valueVerifier: VarintValueVerifier<T>?,
    decodeException: (message: String, cause: Throwable?) -> Exception,
    eofException: (message: String, cause: Throwable?) -> Exception,
    read: (ByteBuffer) -> T
  ): T {
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
          if (valueVerifier === null || valueVerifier.isValid(decodedValue)) {
            return decodedValue
          }

          byteBuffer.position(originalPosition)
          throw valueVerifier.exception(
            "invalid $typeName value decoded: $decodedValue " +
              "(decoded from $decodedByteCount bytes: " +
              "${byteBuffer.get0xHexString(length = decodedByteCount)}) " +
              "[pypnp79waw, invalidValueErrorId=${valueVerifier.errorId}]"
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
              "end of input reached prematurely while decoding $typeName value: " +
                "got ${byteBuffer.remaining()} bytes (${byteBuffer.get0xHexString()}), " +
                "but expected between 1 and $maxSize bytes [c439qmdmnk, eofErrorId=$eofErrorId]",
              readResult.exceptionOrNull()
            )
          }
        }
      )
    }
  }

  private class UInt32ValueVerifier(override val errorId: String) : VarintValueVerifier<Int> {
    override fun isValid(decodedValue: Int) = decodedValue >= 0
    override fun exception(message: String) = UInt32InvalidValueException(message)
  }

  private fun readUInt32(
    valueVerifier: UInt32ValueVerifier,
    decodeErrorId: String,
    eofErrorId: String,
  ): Int =
    readVarint(
      typeName = "uint32",
      maxSize = CodedIntegers.MAX_VARINT32_SIZE,
      decodeErrorId = decodeErrorId,
      eofErrorId = eofErrorId,
      valueVerifier = valueVerifier,
      decodeException = ::UInt32DecodeException,
      eofException = ::UInt32EOFException,
      read = { byteBuffer -> byteBuffer.getUInt32() },
    )

  @Suppress("SameParameterValue")
  private fun readSInt32(decodeErrorId: String, eofErrorId: String): Int =
    readVarint(
      typeName = "sint32",
      maxSize = CodedIntegers.MAX_VARINT32_SIZE,
      decodeErrorId = decodeErrorId,
      eofErrorId = eofErrorId,
      valueVerifier = null,
      decodeException = ::SInt32DecodeException,
      eofException = ::SInt32EOFException,
      read = { byteBuffer -> byteBuffer.getSInt32() },
    )

  private class UInt64ValueVerifier(override val errorId: String) : VarintValueVerifier<Long> {
    override fun isValid(decodedValue: Long) = decodedValue >= 0
    override fun exception(message: String) = UInt64InvalidValueException(message)
  }

  @Suppress("SameParameterValue")
  private fun readUInt64(
    valueVerifier: UInt64ValueVerifier,
    decodeErrorId: String,
    eofErrorId: String,
  ): Long =
    readVarint(
      typeName = "uint64",
      maxSize = CodedIntegers.MAX_VARINT64_SIZE,
      decodeErrorId = decodeErrorId,
      eofErrorId = eofErrorId,
      valueVerifier = valueVerifier,
      decodeException = ::UInt64DecodeException,
      eofException = ::UInt64EOFException,
      read = { byteBuffer -> byteBuffer.getUInt64() },
    )

  @Suppress("SameParameterValue")
  private fun readSInt64(decodeErrorId: String, eofErrorId: String): Long =
    readVarint(
      typeName = "sint64",
      maxSize = CodedIntegers.MAX_VARINT64_SIZE,
      decodeErrorId = decodeErrorId,
      eofErrorId = eofErrorId,
      valueVerifier = null,
      decodeException = ::SInt64DecodeException,
      eofException = ::SInt64EOFException,
      read = { byteBuffer -> byteBuffer.getSInt64() },
    )

  private fun readBytes(byteCount: Int): ByteArray {
    val byteArray = ByteArray(byteCount)
    var byteArrayOffset = 0
    while (byteArrayOffset < byteCount) {
      val wantByteCount = byteCount - byteArrayOffset

      if (byteBuffer.remaining() == 0 && !readSome()) {
        val byteArrayHexString =
          byteArray
            .to0xHexString(length = byteArrayOffset, include0xPrefix = false)
            .ellipsizeMiddle(maxLength = 20)
        throw ByteArrayEOFException(
          "end of input reached prematurely while reading byte array of length $byteCount: " +
            "got $byteArrayOffset bytes (0x$byteArrayHexString), " +
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
    KindNotSet(QueryResultCodec.VALUE_KIND_NOT_SET, "kindNotSet"),
    ListOfEntities(QueryResultCodec.VALUE_LIST_OF_ENTITIES, "listOfEntities"),
    ListOfNonEntities(QueryResultCodec.VALUE_LIST_OF_NON_ENTITIES, "listOfNonEntities"),
    Struct(QueryResultCodec.VALUE_STRUCT, "struct"),
    Entity(QueryResultCodec.VALUE_ENTITY, "entity"),
    StringEmpty(QueryResultCodec.VALUE_STRING_EMPTY, "emptyString"),
    String1Byte(QueryResultCodec.VALUE_STRING_1BYTE, "oneByteString"),
    String2Byte(QueryResultCodec.VALUE_STRING_2BYTE, "twoByteString"),
    String1Char(QueryResultCodec.VALUE_STRING_1CHAR, "oneCharString"),
    String2Char(QueryResultCodec.VALUE_STRING_2CHAR, "twoCharString"),
    StringUtf8(QueryResultCodec.VALUE_STRING_UTF8, "utf8"),
    StringUtf16(QueryResultCodec.VALUE_STRING_UTF16, "utf16");

    companion object {
      val identityMap = buildMap { ValueType.entries.forEach { put(it, it) } }

      fun fromSerializedByte(serializedByte: Byte): ValueType? =
        entries.firstOrNull { it.serializedByte == serializedByte }
    }
  }

  private fun <T : Any> readValueType(
    eofErrorId: String,
    unknownErrorId: String,
    unexpectedErrorId: String,
    map: Map<ValueType, T>,
  ): T {
    ensureRemaining(1, eofErrorId, ::ValueTypeIndicatorEOFException)
    val byte = byteBuffer.get()

    val valueType = ValueType.fromSerializedByte(byte)
    if (valueType === null) {
      throw UnknownValueTypeIndicatorByteException(
        "read unknown value type indicator byte: $byte; expected one of " +
          ValueType.entries
            .sortedBy { it.serializedByte }
            .joinToString { "${it.serializedByte} (${it.displayName})" } +
          " [y6ppbg7ary, unknownErrorId=$unknownErrorId]",
        null
      )
    }

    val mappedType = map[valueType]
    if (mappedType === null) {
      throw UnexpectedValueTypeIndicatorByteException(
        "read unexpected value type indicator byte: $byte (${valueType.displayName}); " +
          "expected one of " +
          map.keys
            .sortedBy { it.serializedByte }
            .joinToString { "${it.serializedByte} (${it.displayName})" } +
          " [hxtgz4ffem, unexpectedErrorId=$unexpectedErrorId]",
        null
      )
    }

    return mappedType
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
      val instanceByValueType: Map<ValueType, StringValueType> = buildMap {
        StringValueType.entries.forEach { stringValueType ->
          check(stringValueType.valueType !in this) {
            "internal error fv988yxmbr: duplicate value type: ${stringValueType.valueType}"
          }
          put(stringValueType.valueType, stringValueType)
        }
      }

      fun fromValueType(valueType: ValueType): StringValueType? =
        entries.firstOrNull { it.valueType == valueType }
    }
  }

  private fun readStringValueType(): StringValueType =
    readValueType(
      eofErrorId = "StringValueTypeIndicatorByteEOF",
      unknownErrorId = "StringValueTypeIndicatorByteUnknown",
      unexpectedErrorId = "StringValueTypeIndicatorByteUnexpected",
      map = StringValueType.instanceByValueType,
    )

  private fun Byte.decodeChar(): Char {
    val codepoint = toUByte().toInt()
    val charCount = Character.toChars(codepoint, charArray, 0)
    check(charCount == 1) { "charCount=$charCount, but expected 1 (codepoint=$codepoint)" }
    return charArray[0]
  }

  private fun readString1Byte(): String {
    ensureRemaining(1, eofErrorId = "String1ByteEOF", ::String1ByteEOFException)
    val byte = byteBuffer.get()
    val char = byte.decodeChar()
    return char.toString()
  }

  private fun readString2Byte(): String {
    ensureRemaining(2, eofErrorId = "String2ByteEOF", ::String2ByteEOFException)
    val byte1 = byteBuffer.get()
    val byte2 = byteBuffer.get()
    val char1 = byte1.decodeChar()
    val char2 = byte2.decodeChar()
    charArray[0] = char1
    charArray[1] = char2
    return String(charArray, 0, 2)
  }

  private fun readString1Char(): String {
    ensureRemaining(2, eofErrorId = "String1CharEOF", ::String1CharEOFException)
    val char = byteBuffer.getChar()
    return char.toString()
  }

  private fun readString2Char(): String {
    ensureRemaining(4, eofErrorId = "String2CharEOF", ::String2CharEOFException)
    charArray[0] = byteBuffer.getChar()
    charArray[1] = byteBuffer.getChar()
    return String(charArray, 0, 2)
  }

  private fun readStringUtf8(): String {
    val byteCount =
      readUInt32(
        valueVerifier = stringUtf8ByteCountUInt32ValueVerifier,
        decodeErrorId = "StringUtf8ByteCountDecodeFailed",
        eofErrorId = "StringUtf8ByteCountEOF",
      )
    val charCount =
      readUInt32(
        valueVerifier = stringUtf8CharCountUInt32ValueVerifier,
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
          "end of input reached prematurely while reading $charCount characters " +
            "($byteCount bytes) of a UTF-8 encoded string: " +
            "got ${charBuffer.position()} characters, " +
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
        valueVerifier = stringUtf16CharCountUInt32ValueVerifier,
        decodeErrorId = "StringUtf16CharCountDecodeFailed",
        eofErrorId = "StringUtf16CharCountEOF",
      )

    val charBuffer = CharBuffer.allocate(charCount)

    while (charBuffer.remaining() > 0) {
      if (byteBuffer.remaining() < 2 && !readSome()) {
        val totalBytesRead = byteBuffer.remaining() + (charBuffer.position() * 2)
        val expectedTotalBytesRead = charCount * 2
        throw Utf16EOFException(
          "end of input reached prematurely while reading $charCount characters " +
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

  private fun readListOfEntities(): ListValue {
    val size =
      readUInt32(
        valueVerifier = listOfEntitiesSizeUInt32ValueVerifier,
        decodeErrorId = "ListOfEntitiesSizeDecodeFailed",
        eofErrorId = "ListOfEntitiesSizeEOF",
      )

    val listValueBuilder = ListValue.newBuilder()
    repeat(size) {
      val entity = readEntity()
      listValueBuilder.addValues(entity.toValueProto())
    }
    return listValueBuilder.build()
  }

  private fun readListOfNonEntities(): ListValue {
    val size =
      readUInt32(
        valueVerifier = listOfNonEntitiesSizeUInt32ValueVerifier,
        decodeErrorId = "ListOfNonEntitiesSizeDecodeFailed",
        eofErrorId = "ListOfNonEntitiesSizeEOF",
      )

    val listValueBuilder = ListValue.newBuilder()
    repeat(size) {
      val value = readValue()
      listValueBuilder.addValues(value)
    }
    return listValueBuilder.build()
  }

  private enum class RootStructValueType(val valueType: ValueType) {
    Struct(ValueType.Struct),
    Entity(ValueType.Entity);

    companion object {
      val instanceByValueType: Map<ValueType, RootStructValueType> = buildMap {
        RootStructValueType.entries.forEach { structValueType ->
          check(structValueType.valueType !in this) {
            "internal error j6rhkqr37n: duplicate value type: ${structValueType.valueType}"
          }
          put(structValueType.valueType, structValueType)
        }
      }

      fun fromValueType(valueType: ValueType): RootStructValueType? =
        entries.firstOrNull { it.valueType == valueType }
    }
  }

  private fun readStruct(): Struct {
    val keyCount =
      readUInt32(
        valueVerifier = structKeyCountUInt32ValueVerifier,
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
    ListOfEntities(ValueType.ListOfEntities),
    ListOfNonEntities(ValueType.ListOfNonEntities),
    Scalar(ValueType.KindNotSet);

    companion object {
      val instanceByValueType: Map<ValueType, EntitySubStructValueType> = buildMap {
        EntitySubStructValueType.entries.forEach { entitySubStructValueType ->
          check(entitySubStructValueType.valueType !in this) {
            "internal error f2a6c8nqby: duplicate value type: ${entitySubStructValueType.valueType}"
          }
          put(entitySubStructValueType.valueType, entitySubStructValueType)
        }
      }

      fun fromValueType(valueType: ValueType): EntitySubStructValueType? =
        entries.firstOrNull { it.valueType == valueType }
    }
  }

  private fun readEntity(): Struct {
    val encodedEntityIdSize =
      readUInt32(
        valueVerifier = encodedEntityIdSizeUInt32ValueVerifier,
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
        valueVerifier = entitySubStructKeyCountUInt32ValueVerifier,
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

  private fun readEntitySubListOfNonEntities(entity: ListValue): ListValue {
    val listSize =
      readUInt32(
        valueVerifier = entitySubListOfNonEntitiesSizeUInt32ValueVerifier,
        decodeErrorId = "EntitySubListOfNonEntitiesSizeDecodeFailed",
        eofErrorId = "EntitySubListOfNonEntitiesSizeEOF",
      )

    val listValueBuilder = ListValue.newBuilder()
    repeat(listSize) { index ->
      val value = readEntityValue(index, entity)
      listValueBuilder.addValues(value)
    }
    return listValueBuilder.build()
  }

  private fun readEntitySubListOfEntities(): ListValue {
    val listSize =
      readUInt32(
        valueVerifier = entitySubListOfEntitiesSizeUInt32ValueVerifier,
        decodeErrorId = "EntitySubListOfEntitiesSizeDecodeFailed",
        eofErrorId = "EntitySubListOfEntitiesSizeEOF",
      )

    val listValueBuilder = ListValue.newBuilder()
    repeat(listSize) {
      val value = readEntity()
      listValueBuilder.addValues(value.toValueProto())
    }
    return listValueBuilder.build()
  }

  private inline fun readEntityValue(getSubEntity: () -> Value): Value {
    val entitySubStructValueType =
      readValueType(
        eofErrorId = "EntitySubStructValueTypeIndicatorByteEOF",
        unknownErrorId = "EntitySubStructValueTypeIndicatorByteUnknown",
        unexpectedErrorId = "EntitySubStructValueTypeIndicatorByteUnexpected",
        map = EntitySubStructValueType.instanceByValueType,
      )

    return when (entitySubStructValueType) {
      EntitySubStructValueType.Entity -> readEntity().toValueProto()
      EntitySubStructValueType.Struct -> {
        val subEntity = getSubEntity().structValue
        readEntitySubStruct(subEntity).toValueProto()
      }
      EntitySubStructValueType.ListOfEntities -> {
        readEntitySubListOfEntities().toValueProto()
      }
      EntitySubStructValueType.ListOfNonEntities -> {
        val subEntity = getSubEntity().listValue
        readEntitySubListOfNonEntities(subEntity).toValueProto()
      }
      EntitySubStructValueType.Scalar -> getSubEntity()
    }
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
        eofErrorId = "ReadValueValueTypeIndicatorByteEOF",
        unknownErrorId = "ReadValueValueTypeIndicatorByteUnknown",
        unexpectedErrorId = "ReadValueValueTypeIndicatorByteUnexpected",
        map = ValueType.identityMap,
      )

    val valueBuilder = Value.newBuilder()

    when (valueType) {
      ValueType.Null -> valueBuilder.setNullValue(NullValue.NULL_VALUE)
      ValueType.Double -> {
        ensureRemaining(8, eofErrorId = "ReadDoubleValueEOF", ::DoubleEOFException)
        valueBuilder.setNumberValue(byteBuffer.getDouble())
      }
      ValueType.PositiveZero -> valueBuilder.setNumberValue(0.0)
      ValueType.NegativeZero -> valueBuilder.setNumberValue(-0.0)
      ValueType.Fixed32Int -> {
        ensureRemaining(4, eofErrorId = "ReadFixed32IntValueEOF", ::Fixed32IntEOFException)
        valueBuilder.setNumberValue(byteBuffer.getInt().toDouble())
      }
      ValueType.UInt32 ->
        valueBuilder.setNumberValue(
          readUInt32(
              valueVerifier = readUInt32ValueVerifier,
              decodeErrorId = "ReadUInt32ValueDecodeError",
              eofErrorId = "ReadUInt32ValueEOF",
            )
            .toDouble()
        )
      ValueType.SInt32 ->
        valueBuilder.setNumberValue(
          readSInt32(
              decodeErrorId = "ReadSInt32ValueDecodeError",
              eofErrorId = "ReadSInt32ValueEOF",
            )
            .toDouble()
        )
      ValueType.UInt64 ->
        valueBuilder.setNumberValue(
          readUInt64(
              valueVerifier = readUInt64ValueVerifier,
              decodeErrorId = "ReadUInt64ValueDecodeError",
              eofErrorId = "ReadUInt64ValueEOF",
            )
            .toDouble()
        )
      ValueType.SInt64 ->
        valueBuilder.setNumberValue(
          readSInt64(
              decodeErrorId = "ReadSInt64ValueDecodeError",
              eofErrorId = "ReadSInt64ValueEOF",
            )
            .toDouble()
        )
      ValueType.BoolTrue -> valueBuilder.setBoolValue(true)
      ValueType.BoolFalse -> valueBuilder.setBoolValue(false)
      ValueType.ListOfEntities -> valueBuilder.setListValue(readListOfEntities())
      ValueType.ListOfNonEntities -> valueBuilder.setListValue(readListOfNonEntities())
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

  class ValueTypeIndicatorEOFException(message: String, cause: Throwable? = null) :
    EOFException(message, cause)

  class UnknownValueTypeIndicatorByteException(message: String, cause: Throwable? = null) :
    DecodeException(message, cause)

  class UnexpectedValueTypeIndicatorByteException(message: String, cause: Throwable? = null) :
    DecodeException(message, cause)

  class NonEntitySubStructValueTypeIndicatorByteException(
    message: String,
    cause: Throwable? = null
  ) : DecodeException(message, cause)

  class String1ByteEOFException(message: String, cause: Throwable? = null) :
    EOFException(message, cause)

  class String2ByteEOFException(message: String, cause: Throwable? = null) :
    EOFException(message, cause)

  class String1CharEOFException(message: String, cause: Throwable? = null) :
    EOFException(message, cause)

  class String2CharEOFException(message: String, cause: Throwable? = null) :
    EOFException(message, cause)

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

  class SInt32DecodeException(message: String, cause: Throwable? = null) :
    DecodeException(message, cause)

  class SInt64EOFException(message: String, cause: Throwable? = null) :
    EOFException(message, cause)

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

    private val stringUtf8ByteCountUInt32ValueVerifier =
      UInt32ValueVerifier("StringUtf8ByteCountInvalidValue")

    private val stringUtf8CharCountUInt32ValueVerifier =
      UInt32ValueVerifier("StringUtf8CharCountInvalidValue")

    private val stringUtf16CharCountUInt32ValueVerifier =
      UInt32ValueVerifier("StringUtf16CharCountInvalidValue")

    private val listOfEntitiesSizeUInt32ValueVerifier =
      UInt32ValueVerifier("ListOfEntitiesSizeInvalidValue")

    private val listOfNonEntitiesSizeUInt32ValueVerifier =
      UInt32ValueVerifier("ListOfNonEntitiesSizeInvalidValue")

    private val structKeyCountUInt32ValueVerifier =
      UInt32ValueVerifier("StructKeyCountInvalidValue")

    private val encodedEntityIdSizeUInt32ValueVerifier =
      UInt32ValueVerifier("EncodedEntityIdSizeInvalidValue")

    private val entitySubStructKeyCountUInt32ValueVerifier =
      UInt32ValueVerifier("EntitySubStructKeyCountInvalidValue")

    private val entitySubListOfNonEntitiesSizeUInt32ValueVerifier =
      UInt32ValueVerifier("EntitySubListOfNonEntitiesSizeInvalidValue")

    private val entitySubListOfEntitiesSizeUInt32ValueVerifier =
      UInt32ValueVerifier("EntitySubListOfEntitiesSizeInvalidValue")

    private val readUInt32ValueVerifier = UInt32ValueVerifier("ReadUInt32ValueInvalidValue")

    private val readUInt64ValueVerifier = UInt64ValueVerifier("ReadUInt64ValueInvalidValue")
  }
}
