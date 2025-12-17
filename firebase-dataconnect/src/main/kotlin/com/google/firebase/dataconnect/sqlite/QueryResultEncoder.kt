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

import com.google.firebase.dataconnect.DataConnectPath
import com.google.firebase.dataconnect.MutableDataConnectPath
import com.google.firebase.dataconnect.sqlite.CodedIntegersExts.putSInt32
import com.google.firebase.dataconnect.sqlite.CodedIntegersExts.putSInt64
import com.google.firebase.dataconnect.sqlite.CodedIntegersExts.putUInt32
import com.google.firebase.dataconnect.sqlite.CodedIntegersExts.putUInt64
import com.google.firebase.dataconnect.sqlite.QueryResultCodec.Entity
import com.google.firebase.dataconnect.toPathString
import com.google.firebase.dataconnect.util.ProtoUtil.toValueProto
import com.google.firebase.dataconnect.withAddedField
import com.google.firebase.dataconnect.withAddedListIndex
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
    writer.writeFixed32Int(QueryResultCodec.QUERY_RESULT_MAGIC)
    writeStruct(queryResult, path = mutableListOf())
  }

  fun flush() {
    writer.flush()
  }

  private fun writeBoolean(value: Boolean) {
    val byte = if (value) QueryResultCodec.VALUE_BOOL_TRUE else QueryResultCodec.VALUE_BOOL_FALSE
    writer.writeByte(byte)
  }

  private fun writeDouble(value: Double) {
    when (val encoding = DoubleEncoding.calculateOptimalSpaceEfficientEncodingFor(value)) {
      is DoubleEncoding.Double -> {
        writer.writeByte(QueryResultCodec.VALUE_NUMBER_DOUBLE)
        writer.writeDouble(value)
      }
      DoubleEncoding.PositiveZero -> writer.writeByte(QueryResultCodec.VALUE_NUMBER_POSITIVE_ZERO)
      DoubleEncoding.NegativeZero -> writer.writeByte(QueryResultCodec.VALUE_NUMBER_NEGATIVE_ZERO)
      is DoubleEncoding.Fixed32Int -> {
        writer.writeByte(QueryResultCodec.VALUE_NUMBER_FIXED32)
        writer.writeFixed32Int(encoding.value)
      }
      is DoubleEncoding.UInt32 -> {
        writer.writeByte(QueryResultCodec.VALUE_NUMBER_UINT32)
        writer.writeUInt32(encoding.value, encoding.size)
      }
      is DoubleEncoding.SInt32 -> {
        writer.writeByte(QueryResultCodec.VALUE_NUMBER_SINT32)
        writer.writeSInt32(encoding.value, encoding.size)
      }
      is DoubleEncoding.UInt64 -> {
        writer.writeByte(QueryResultCodec.VALUE_NUMBER_UINT64)
        writer.writeUInt64(encoding.value, encoding.size)
      }
      is DoubleEncoding.SInt64 -> {
        writer.writeByte(QueryResultCodec.VALUE_NUMBER_SINT64)
        writer.writeSInt64(encoding.value, encoding.size)
      }
    }
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

  private fun writeList(
    listValue: ListValue,
    path: MutableDataConnectPath,
  ) {
    writer.writeByte(QueryResultCodec.VALUE_LIST)
    writer.writeUInt32(listValue.valuesCount)
    listValue.valuesList.forEachIndexed { listIndex, childValue ->
      path.withAddedListIndex(listIndex) { writeValue(childValue, path) }
    }
  }

  private fun Value.isEntity(): Boolean =
    kindCase == Value.KindCase.STRUCT_VALUE && structValue.isEntity()

  private fun Struct.isEntity(): Boolean =
    entityFieldName !== null &&
      containsFields(entityFieldName) &&
      getFieldsOrThrow(entityFieldName).kindCase == Value.KindCase.STRING_VALUE

  private fun Value.getEntityId(): String? =
    if (kindCase == Value.KindCase.STRUCT_VALUE) {
      structValue.getEntityId()
    } else {
      null
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

  private enum class EntitySubListLeafContents {
    Entities,
    NonEntities,
  }

  private fun ListValue.calculateEntitySubListLeafContents(
    path: DataConnectPath
  ): EntitySubListLeafContents? {
    val queue = ArrayDeque<Pair<DataConnectPath, ListValue>>()
    queue.add(path to this)
    return calculateEntitySubListLeafContentsRecursively(queue)
  }

  private fun calculateEntitySubListLeafContentsRecursively(
    queue: ArrayDeque<Pair<DataConnectPath, ListValue>>
  ): EntitySubListLeafContents? {
    var leafContents: Pair<DataConnectPath, EntitySubListLeafContents>? = null

    while (queue.isNotEmpty()) {
      val (path, listValue) = queue.removeFirst()

      repeat(listValue.valuesCount) { listIndex ->
        val value = listValue.getValues(listIndex)
        if (value.kindCase == Value.KindCase.LIST_VALUE) {
          queue.add(path.withAddedListIndex(listIndex) to value.listValue)
        } else {
          val valueContents =
            if (value.isEntity()) {
              EntitySubListLeafContents.Entities
            } else {
              EntitySubListLeafContents.NonEntities
            }
          if (leafContents === null) {
            leafContents = path.withAddedListIndex(listIndex) to valueContents
          } else if (leafContents.second != valueContents) {
            val curPath = path.withAddedListIndex(listIndex)
            val (entityPath, nonEntityPath) =
              when (valueContents) {
                EntitySubListLeafContents.Entities -> Pair(curPath, leafContents.first)
                EntitySubListLeafContents.NonEntities -> Pair(leafContents.first, curPath)
              }
            throw IntermixedEntityAndNonEntityListInEntityException(
              "Found entity at ${entityPath.toPathString()} " +
                "and non-entity at ${nonEntityPath.toPathString()}, " +
                "both of which are leaf values of a list in an entity, " +
                "but leaf values of lists in entities must be all be entities " +
                "or must all be non-entities, but not both [df9tkx7jk4]"
            )
          }
        }
      }
    }

    return leafContents?.second
  }

  class IntermixedEntityAndNonEntityListInEntityException(message: String) : Exception(message)

  private fun writeStruct(
    struct: Struct,
    path: MutableDataConnectPath,
  ) {
    val entityId = struct.getEntityId()
    if (entityId !== null) {
      writer.writeByte(QueryResultCodec.VALUE_ENTITY)
      writeEntity(entityId, struct, path)
      return
    }

    val map = struct.fieldsMap
    writer.writeByte(QueryResultCodec.VALUE_STRUCT)
    writer.writeUInt32(map.size)
    map.entries.forEach { (key, value) ->
      writeString(key)
      path.withAddedField(key) { writeValue(value, path) }
    }
  }

  private fun writeValue(
    value: Value,
    path: MutableDataConnectPath,
  ) {
    when (value.kindCase) {
      Value.KindCase.NULL_VALUE -> writer.writeByte(QueryResultCodec.VALUE_NULL)
      Value.KindCase.NUMBER_VALUE -> writeDouble(value.numberValue)
      Value.KindCase.BOOL_VALUE -> writeBoolean(value.boolValue)
      Value.KindCase.STRING_VALUE -> writeString(value.stringValue)
      Value.KindCase.STRUCT_VALUE -> writeStruct(value.structValue, path)
      Value.KindCase.LIST_VALUE -> writeList(value.listValue, path)
      Value.KindCase.KIND_NOT_SET -> writer.writeByte(QueryResultCodec.VALUE_KIND_NOT_SET)
    }
  }

  private fun writeEntity(
    entityId: String,
    entity: Struct,
    path: MutableDataConnectPath,
  ) {
    val encodedEntityId = sha512DigestCalculator.calculate(entityId)
    writer.writeUInt32(encodedEntityId.size)
    writer.write(ByteBuffer.wrap(encodedEntityId))
    val struct = writeEntitySubStruct(entity, path)
    entities.add(Entity(entityId, encodedEntityId, struct))
  }

  private fun writeEntitySubStruct(
    struct: Struct,
    path: MutableDataConnectPath,
  ): Struct {
    writer.writeUInt32(struct.fieldsCount)
    val structBuilder = Struct.newBuilder()
    struct.fieldsMap.entries.forEach { (key, value) ->
      writeString(key)
      val entityValue: Value? =
        path.withAddedField(key) {
          writeEntityValue(
            value,
            path,
            entitySubListLeafContentsAffinity = EntitySubListLeafContents.NonEntities
          )
        }
      if (entityValue !== null) {
        structBuilder.putFields(key, entityValue)
      }
    }
    return structBuilder.build()
  }

  private fun writeEntitySubListOfNonEntities(
    listValue: ListValue,
    path: MutableDataConnectPath,
  ): ListValue {
    writer.writeByte(QueryResultCodec.VALUE_LIST)
    writer.writeUInt32(listValue.valuesCount)

    val listValueBuilder = ListValue.newBuilder()
    listValue.valuesList.forEachIndexed { index, value ->
      path.withAddedListIndex(index) {
        val nonEntityValue =
          writeEntityValue(
            value,
            path,
            entitySubListLeafContentsAffinity = EntitySubListLeafContents.NonEntities
          )
        checkNotNull(nonEntityValue) {
          "internal error gj26tyh2bj: " +
            "list at ${path.toPathString()} is an entity with id=${value.getEntityId()}, " +
            "but expected it to not be an entity"
        }
        listValueBuilder.addValues(nonEntityValue)
      }
    }

    return listValueBuilder.build()
  }

  private fun writeEntitySubListOfEntities(
    listValue: ListValue,
    path: MutableDataConnectPath,
  ) {
    writer.writeByte(QueryResultCodec.VALUE_LIST_OF_ENTITIES)
    writer.writeUInt32(listValue.valuesCount)

    listValue.valuesList.forEachIndexed { index, value ->
      path.withAddedListIndex(index) {
        if (value.kindCase == Value.KindCase.LIST_VALUE) {
          writeEntitySubListOfEntities(value.listValue, path)
        } else {
          val entityValue =
            writeEntityValue(
              value,
              path,
              entitySubListLeafContentsAffinity = EntitySubListLeafContents.Entities
            )
          check(entityValue === null) {
            "internal error p3ae56pn93: " +
              "value at ${path.toPathString()} is a non-entity of type " +
              "${entityValue!!.kindCase}, but expected it to be an entity"
          }
        }
      }
    }
  }

  private fun writeEntityValue(
    value: Value,
    path: MutableDataConnectPath,
    entitySubListLeafContentsAffinity: EntitySubListLeafContents,
  ): Value? =
    when (value.kindCase) {
      Value.KindCase.STRUCT_VALUE -> {
        val subStructEntityId = value.structValue.getEntityId()
        if (subStructEntityId !== null) {
          writer.writeByte(QueryResultCodec.VALUE_ENTITY)
          writeEntity(subStructEntityId, value.structValue, path)
          null
        } else {
          writer.writeByte(QueryResultCodec.VALUE_STRUCT)
          writeEntitySubStruct(value.structValue, path).toValueProto()
        }
      }
      Value.KindCase.LIST_VALUE -> {
        val calculatedEntitySubListLeafContents =
          value.listValue.calculateEntitySubListLeafContents(path)
        val effectiveEntitySubListLeafContents =
          calculatedEntitySubListLeafContents ?: entitySubListLeafContentsAffinity
        when (effectiveEntitySubListLeafContents) {
          EntitySubListLeafContents.Entities -> {
            writeEntitySubListOfEntities(value.listValue, path)
            null
          }
          EntitySubListLeafContents.NonEntities -> {
            writeEntitySubListOfNonEntities(value.listValue, path).toValueProto()
          }
        }
      }
      else -> {
        writer.writeByte(QueryResultCodec.VALUE_KIND_NOT_SET)
        value
      }
    }

  private sealed interface DoubleEncoding {
    data class Double(val value: kotlin.Double) : DoubleEncoding
    object PositiveZero : DoubleEncoding
    object NegativeZero : DoubleEncoding

    data class UInt32(val value: Int, val size: Int) : DoubleEncoding
    data class SInt32(val value: Int, val size: Int) : DoubleEncoding
    data class Fixed32Int(val value: Int) : DoubleEncoding

    data class UInt64(val value: Long, val size: Int) : DoubleEncoding
    data class SInt64(val value: Long, val size: Int) : DoubleEncoding

    companion object {
      fun calculateOptimalSpaceEfficientEncodingFor(value: kotlin.Double): DoubleEncoding {
        if (!value.isFinite()) {
          return Double(value)
        }

        if (value == 0.0) {
          value.toBits().also { bits ->
            return when (bits) {
              0L -> PositiveZero
              Long.MIN_VALUE -> NegativeZero
              else ->
                throw IllegalStateException("unexpected bits for ${value}: $bits [myedq2mzzg]")
            }
          }
        }

        value.toInt().also { intValue ->
          if (intValue.toDouble() == value) {
            return if (intValue < 0) {
              val sint32Size = CodedIntegers.computeSInt32Size(intValue)
              if (sint32Size < 4) {
                SInt32(intValue, sint32Size)
              } else {
                Fixed32Int(intValue)
              }
            } else {
              val uint32Size = CodedIntegers.computeUInt32Size(intValue)
              if (uint32Size < 4) {
                UInt32(intValue, uint32Size)
              } else {
                Fixed32Int(intValue)
              }
            }
          }
        }

        value.toLong().also { longValue ->
          if (longValue.toDouble() == value) {
            return if (longValue < 0) {
              val sint64Size = CodedIntegers.computeSInt64Size(longValue)
              if (sint64Size < 8) {
                SInt64(longValue, sint64Size)
              } else {
                Double(value)
              }
            } else {
              val uint64Size = CodedIntegers.computeUInt64Size(longValue)
              if (uint64Size < 8) {
                UInt64(longValue, uint64Size)
              } else {
                Double(value)
              }
            }
          }
        }

        return Double(value)
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

  fun writeUInt32(value: Int, size: Int? = null) {
    ensureRemaining(size ?: CodedIntegers.computeUInt32Size(value))
    byteBuffer.putUInt32(value)
  }

  fun writeSInt32(value: Int, size: Int? = null) {
    ensureRemaining(size ?: CodedIntegers.computeSInt32Size(value))
    byteBuffer.putSInt32(value)
  }

  fun writeFixed32Int(value: Int) {
    ensureRemaining(4)
    byteBuffer.putInt(value)
  }

  fun writeUInt64(value: Long, size: Int? = null) {
    ensureRemaining(size ?: CodedIntegers.computeUInt64Size(value))
    byteBuffer.putUInt64(value)
  }

  fun writeSInt64(value: Long, size: Int? = null) {
    ensureRemaining(size ?: CodedIntegers.computeSInt64Size(value))
    byteBuffer.putSInt64(value)
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
    writeUInt32(expectedByteCount)
    writeUInt32(string.length)

    if (writeUtf8Fast(string, expectedByteCount)) {
      return
    }

    utf8CharsetEncoder.reset()
    val charBuffer = CharBuffer.wrap(string)

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

  private fun writeUtf8Fast(string: String, expectedByteCount: Int): Boolean {
    if (expectedByteCount > byteBuffer.capacity()) {
      return false
    }

    if (expectedByteCount > byteBuffer.remaining()) {
      flushOnce()
      if (expectedByteCount > byteBuffer.remaining()) {
        return false
      }
    }

    val byteBufferArrayOffset = byteBuffer.arrayOffset()
    val offset = byteBufferArrayOffset + byteBuffer.position()
    val newOffset = Utf8.encode(string, byteBuffer.array(), offset, byteBuffer.limit())

    val byteWriteCount = newOffset - offset
    check(byteWriteCount == expectedByteCount) {
      "internal error s469aqrktv: byteWriteCount=$byteWriteCount " +
        "should be equal to expectedByteCount=$expectedByteCount, but they differ by " +
        "${(expectedByteCount-byteWriteCount).absoluteValue}"
    }

    byteBuffer.position(newOffset - byteBufferArrayOffset)
    return true
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
      "internal error hr3gyzbfh7: minRemaining=$minRemaining must be less than or equal to " +
        "byteBuffer.capacity()=${byteBuffer.capacity()}"
    }

    while (byteBuffer.remaining() < minRemaining) {
      val byteWriteCount = flushOnce()
      if (byteWriteCount < 1) {
        throw NoBytesWrittenException(
          "internal error wkh35rfq9e: byteWriteCount=$byteWriteCount " +
            "despite ${byteBuffer.position()} bytes being available for writing" +
            "(expected byteWriteCount to be greater than or equal to 1)"
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
