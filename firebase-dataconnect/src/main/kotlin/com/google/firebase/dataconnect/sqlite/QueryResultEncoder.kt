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

import com.google.firebase.dataconnect.sqlite.CodedIntegersExts.putUInt32
import com.google.firebase.dataconnect.sqlite.QueryResultCodec.Entity
import com.google.firebase.dataconnect.util.ProtoUtil.toValueProto
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.CharBuffer
import java.nio.channels.Channels
import java.nio.channels.WritableByteChannel
import java.nio.charset.CoderResult
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import kotlin.math.absoluteValue

/**
 * This class is NOT thread safe. The behavior of an instance of this class when used concurrently
 * from multiple threads without external synchronization is undefined.
 */
internal class QueryResultEncoder(
  channel: WritableByteChannel,
  private val entityFieldName: String? = null
) {

  val entities: MutableList<Entity> = mutableListOf()

  private val writer = QueryResultChannelWriter(channel)

  private val sha512DigestCalculator = Sha512DigestCalculator()

  class EncodeResult(val byteArray: ByteArray, val entities: List<Entity>)

  fun encode(queryResult: Struct) {
    writer.writeInt(QueryResultCodec.QUERY_RESULT_HEADER)
    writeStruct(queryResult)
  }

  fun flush() {
    writer.flush()
  }

  private fun writeString(string: String) {
    if (string.isEmpty()) {
      writer.writeByte(QueryResultCodec.VALUE_STRING_EMPTY)
      return
    } else if (string.length == 1) {
      val char = string[0]
      if (char.code < 256) {
        writer.writeByte(QueryResultCodec.VALUE_STRING_1BYTE)
        writer.writeByte(char.code.toByte())
      } else {
        writer.writeByte(QueryResultCodec.VALUE_STRING_1CHAR)
        writer.writeChar(char)
      }
      return
    } else if (string.length == 2) {
      val char1 = string[0]
      val char2 = string[1]
      if (char1.code < 256 && char2.code < 256) {
        writer.writeByte(QueryResultCodec.VALUE_STRING_2BYTE)
        writer.writeByte(char1.code.toByte())
        writer.writeByte(char2.code.toByte())
      } else {
        writer.writeByte(QueryResultCodec.VALUE_STRING_2CHAR)
        writer.writeChar(char1)
        writer.writeChar(char2)
      }
      return
    }

    val utf8ByteCount: Int? = Utf8.encodedLength(string)
    val utf16ByteCount = string.length * 2

    if (utf8ByteCount !== null && utf8ByteCount <= utf16ByteCount) {
      writer.writeByte(QueryResultCodec.VALUE_STRING_UTF8)
      writer.writeStringUtf8(string, utf8ByteCount)
    } else {
      writer.writeByte(QueryResultCodec.VALUE_STRING_UTF16)
      writer.writeStringCustomUtf16(string, utf16ByteCount)
    }
  }

  private fun writeList(listValue: ListValue) {
    writer.writeByte(QueryResultCodec.VALUE_LIST)
    writer.writeUInt32(listValue.valuesCount)
    repeat(listValue.valuesCount) { writeValue(listValue.getValues(it)) }
  }

  private fun Struct.getEntityId(): String? =
    if (entityFieldName === null || !containsFields(entityFieldName)) {
      null
    } else {
      val entityIdValue = getFieldsOrThrow(entityFieldName)
      if (entityIdValue?.kindCase != Value.KindCase.STRING_VALUE) {
        null
      } else {
        entityIdValue.stringValue
      }
    }

  private fun writeStruct(struct: Struct) {
    val entityId = struct.getEntityId()
    if (entityId !== null) {
      writer.writeByte(QueryResultCodec.VALUE_ENTITY)
      writeEntity(entityId, struct)
      return
    }

    val map = struct.fieldsMap
    writer.writeByte(QueryResultCodec.VALUE_STRUCT)
    writer.writeUInt32(map.size)
    map.entries.forEach { (key, value) ->
      writeString(key)
      writeValue(value)
    }
  }

  private fun writeValue(value: Value) {
    when (value.kindCase) {
      Value.KindCase.NULL_VALUE -> writer.writeByte(QueryResultCodec.VALUE_NULL)
      Value.KindCase.NUMBER_VALUE -> {
        writer.writeByte(QueryResultCodec.VALUE_NUMBER)
        writer.writeDouble(value.numberValue)
      }
      Value.KindCase.BOOL_VALUE ->
        writer.writeByte(
          if (value.boolValue) QueryResultCodec.VALUE_BOOL_TRUE
          else QueryResultCodec.VALUE_BOOL_FALSE
        )
      Value.KindCase.STRING_VALUE -> writeString(value.stringValue)
      Value.KindCase.STRUCT_VALUE -> writeStruct(value.structValue)
      Value.KindCase.LIST_VALUE -> writeList(value.listValue)
      Value.KindCase.KIND_NOT_SET -> writer.writeByte(QueryResultCodec.VALUE_KIND_NOT_SET)
    }
  }

  private fun writeEntity(entityId: String, entity: Struct) {
    val encodedEntityId = sha512DigestCalculator.calculate(entityId)
    writer.writeUInt32(encodedEntityId.size)
    writer.write(ByteBuffer.wrap(encodedEntityId))
    val struct = writeEntitySubStruct(entity)
    entities.add(Entity(entityId, encodedEntityId, struct))
  }

  private fun writeEntitySubStruct(struct: Struct): Struct {
    writer.writeUInt32(struct.fieldsCount)
    val structBuilder = Struct.newBuilder()
    struct.fieldsMap.entries.forEach { (key, value) ->
      writeString(key)
      val entityValue = writeEntityValue(value)
      if (entityValue !== null) {
        structBuilder.putFields(key, entityValue)
      }
    }
    return structBuilder.build()
  }

  private fun writeEntitySubList(listValue: ListValue): ListValue {
    writer.writeUInt32(listValue.valuesCount)
    val listValueBuilder = ListValue.newBuilder()
    listValue.valuesList.forEach { value ->
      val entityValue = writeEntityValue(value)
      listValueBuilder.addValues(entityValue ?: Value.getDefaultInstance())
    }
    return listValueBuilder.build()
  }

  private fun writeEntityValue(value: Value): Value? {
    return when (value.kindCase) {
      Value.KindCase.STRUCT_VALUE -> {
        val subStructEntityId = value.structValue.getEntityId()
        if (subStructEntityId !== null) {
          writer.writeByte(QueryResultCodec.VALUE_ENTITY)
          writeEntity(subStructEntityId, value.structValue)
          null
        } else {
          writer.writeByte(QueryResultCodec.VALUE_STRUCT)
          writeEntitySubStruct(value.structValue).toValueProto()
        }
      }
      Value.KindCase.LIST_VALUE -> {
        writer.writeByte(QueryResultCodec.VALUE_LIST)
        writeEntitySubList(value.listValue).toValueProto()
      }
      else -> {
        writer.writeByte(QueryResultCodec.VALUE_KIND_NOT_SET)
        value
      }
    }
  }

  companion object {

    fun encode(queryResult: Struct, entityFieldName: String? = null): EncodeResult =
      ByteArrayOutputStream().use { byteArrayOutputStream ->
        val entities =
          Channels.newChannel(byteArrayOutputStream).use { writableByteChannel ->
            val encoder = QueryResultEncoder(writableByteChannel, entityFieldName)
            encoder.encode(queryResult)
            encoder.flush()
            encoder.entities
          }
        EncodeResult(byteArrayOutputStream.toByteArray(), entities)
      }
  }
}

private class QueryResultChannelWriter(private val channel: WritableByteChannel) {

  private val utf8CharsetEncoder =
    Charsets.UTF_8.newEncoder()
      .onUnmappableCharacter(CodingErrorAction.REPORT)
      .onMalformedInput(CodingErrorAction.REPORT)

  private val byteBuffer = ByteBuffer.allocate(2048).order(ByteOrder.BIG_ENDIAN)

  fun flush() {
    byteBuffer.flip()
    while (byteBuffer.remaining() > 0) {
      channel.write(byteBuffer)
    }
    byteBuffer.clear()
  }

  fun writeUInt32(value: Int) {
    require(value >= 0) {
      "value=$value, but it must be greater than or equal to zero [fqn5fex58z]"
    }
    val size = CodedIntegers.computeUInt32Size(value)
    ensureRemaining(size)
    byteBuffer.putUInt32(value)
  }

  fun writeInt(value: Int) {
    ensureRemaining(4)
    byteBuffer.putInt(value)
  }

  fun writeByte(value: Byte) {
    ensureRemaining(1)
    byteBuffer.put(value)
  }

  fun writeBytes(value1: Byte, value2: Byte) {
    ensureRemaining(2)
    byteBuffer.put(value1)
    byteBuffer.put(value2)
  }

  fun writeBytes(value1: Byte, value2: Byte, value3: Byte) {
    ensureRemaining(3)
    byteBuffer.put(value1)
    byteBuffer.put(value2)
    byteBuffer.put(value3)
  }

  fun writeChar(value: Char) {
    ensureRemaining(2)
    byteBuffer.putChar(value)
  }

  fun writeDouble(value: Double) {
    ensureRemaining(8)
    byteBuffer.putDouble(value)
  }

  fun write(bytes: ByteBuffer) {
    while (bytes.remaining() > 0) {
      if (byteBuffer.remaining() > bytes.remaining()) {
        byteBuffer.put(bytes)
        break
      } else if (byteBuffer.position() == 0) {
        channel.write(byteBuffer)
      } else {
        val limitBefore = bytes.limit()
        bytes.limit(bytes.position() + byteBuffer.remaining())
        byteBuffer.put(bytes)
        bytes.limit(limitBefore)
        flushOnce()
      }
    }
  }

  fun writeStringUtf8(string: String, expectedByteCount: Int) {
    utf8CharsetEncoder.reset()
    val charBuffer = CharBuffer.wrap(string)

    writeUInt32(expectedByteCount)
    writeUInt32(string.length)

    var byteWriteCount = 0
    while (true) {
      val byteBufferPositionBefore = byteBuffer.position()

      val coderResult1 =
        if (charBuffer.hasRemaining()) {
          utf8CharsetEncoder.encode(charBuffer, byteBuffer, true)
        } else {
          CoderResult.UNDERFLOW
        }

      val coderResult2 =
        if (coderResult1.isUnderflow) {
          utf8CharsetEncoder.flush(byteBuffer)
        } else {
          coderResult1
        }

      val byteBufferPositionAfter = byteBuffer.position()
      byteWriteCount += byteBufferPositionAfter - byteBufferPositionBefore

      if (coderResult2.isUnderflow) {
        break
      }

      if (!coderResult2.isOverflow) {
        coderResult2.throwException()
      }

      flushOnce()
    }

    check(byteWriteCount == expectedByteCount) {
      "internal error rvmdh67npk: byteWriteCount=$byteWriteCount " +
        "should be equal to expectedByteCount=$expectedByteCount, but they differ by " +
        "${(expectedByteCount-byteWriteCount).absoluteValue}"
    }
  }

  fun writeStringCustomUtf16(string: String, expectedByteCount: Int) {
    writeUInt32(string.length)

    var byteWriteCount = 0
    var stringOffset = 0
    while (stringOffset < string.length) {
      val charBuffer = byteBuffer.asCharBuffer()
      val putLength = charBuffer.remaining().coerceAtMost(string.length - stringOffset)
      charBuffer.put(string, stringOffset, stringOffset + putLength)

      byteBuffer.position(byteBuffer.position() + (putLength * 2))
      flushOnce()

      byteWriteCount += putLength * 2
      stringOffset += putLength
    }

    check(byteWriteCount == expectedByteCount) {
      "internal error agdf5qbwwp: byteWriteCount=$byteWriteCount " +
        "should be equal to expectedByteCount=$expectedByteCount, but they differ by " +
        "${(expectedByteCount - byteWriteCount).absoluteValue}"
    }
  }

  class NoBytesWrittenException(message: String) : Exception(message)

  private fun flushOnce(): Int {
    byteBuffer.flip()
    val byteWriteCount = channel.write(byteBuffer)
    byteBuffer.compact()
    return byteWriteCount
  }

  private fun ensureRemaining(minRemaining: Int) {
    require(minRemaining <= byteBuffer.capacity()) {
      "minRemaining=$minRemaining must be less than or equal to " +
        "byteBuffer.capacity()=${byteBuffer.capacity()}"
    }

    while (byteBuffer.remaining() < minRemaining) {
      val byteWriteCount = flushOnce()
      if (byteWriteCount < 1) {
        throw NoBytesWrittenException(
          "no bytes were written despite ${byteBuffer.position()} byte available for writing"
        )
      }
    }
  }
}

private class Sha512DigestCalculator {

  private val digest = MessageDigest.getInstance("SHA-512")
  private val buffer = ByteArray(1024)

  fun calculate(string: String): ByteArray {
    digest.reset()

    var bufferIndex = 0
    string.forEach { char ->
      buffer[bufferIndex++] = char.toByteUshr(8)
      buffer[bufferIndex++] = char.toByteUshr(0)
      if (bufferIndex + 2 >= buffer.size) {
        digest.update(buffer, 0, bufferIndex)
        bufferIndex = 0
      }
    }

    if (bufferIndex > 0) {
      digest.update(buffer, 0, bufferIndex)
    }

    return digest.digest()
  }

  private companion object {

    @Suppress("SpellCheckingInspection")
    fun Char.toByteUshr(shiftAmount: Int): Byte = ((code ushr shiftAmount) and 0xFF).toByte()
  }
}
