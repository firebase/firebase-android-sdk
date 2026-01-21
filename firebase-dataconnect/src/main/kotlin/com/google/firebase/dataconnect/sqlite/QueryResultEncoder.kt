/*
 * Copyright 2026 Google LLC
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
import com.google.firebase.dataconnect.DataConnectPathSegment
import com.google.firebase.dataconnect.MutableDataConnectPath
import com.google.firebase.dataconnect.sqlite.CodedIntegersExts.putSInt32
import com.google.firebase.dataconnect.sqlite.CodedIntegersExts.putSInt64
import com.google.firebase.dataconnect.sqlite.CodedIntegersExts.putUInt32
import com.google.firebase.dataconnect.sqlite.CodedIntegersExts.putUInt64
import com.google.firebase.dataconnect.toPathString
import com.google.firebase.dataconnect.util.ProtoPrune.withDescendantStructsPruned
import com.google.firebase.dataconnect.util.ProtoUtil.toCompactString
import com.google.firebase.dataconnect.util.ProtoUtil.toValueProto
import com.google.firebase.dataconnect.util.StringUtil.to0xHexString
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
import java.util.Objects
import kotlin.math.absoluteValue

/**
 * This class is NOT thread safe. The behavior of an instance of this class when used concurrently
 * from multiple threads without external synchronization is undefined.
 */
internal class QueryResultEncoder(
  channel: WritableByteChannel,
  private val entityIdByPath: Map<DataConnectPath, String>? = null,
) {

  val entities: MutableList<Entity> = mutableListOf()

  private val writer = QueryResultChannelWriter(channel)

  private val sha512DigestCalculator = Sha512DigestCalculator()

  class EncodeResult(val byteArray: ByteArray, val entities: List<Entity>)

  fun encode(queryResult: Struct) {
    writer.writeFixed32Int(QueryResultCodec.QUERY_RESULT_MAGIC)
    writeStructOrEntity(queryResult, path = mutableListOf())
  }

  fun flush() {
    writer.flush()
  }

  private data class PrunedEntity(
    val struct: Struct,
    val entityId: String,
    val path: DataConnectPath,
  )

  private fun DataConnectPath.getEntityId(): String? = entityIdByPath?.get(this)

  private fun DataConnectPath.isEntity(): Boolean =
    entityIdByPath !== null && entityIdByPath.containsKey(this)

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

  private fun writeValue(
    value: Value,
    path: MutableDataConnectPath,
  ) {
    when (value.kindCase) {
      Value.KindCase.NULL_VALUE -> writer.writeByte(QueryResultCodec.VALUE_NULL)
      Value.KindCase.NUMBER_VALUE -> writeDouble(value.numberValue)
      Value.KindCase.BOOL_VALUE -> writeBoolean(value.boolValue)
      Value.KindCase.STRING_VALUE -> writeString(value.stringValue)
      Value.KindCase.STRUCT_VALUE -> writeStructOrEntity(value.structValue, path)
      Value.KindCase.LIST_VALUE -> writeList(value.listValue, path)
      Value.KindCase.KIND_NOT_SET -> writer.writeByte(QueryResultCodec.VALUE_KIND_NOT_SET)
    }
  }

  private fun writeStructOrEntity(
    struct: Struct,
    path: MutableDataConnectPath,
  ) {
    val entityId = path.getEntityId()
    if (entityId !== null) {
      writer.writeByte(QueryResultCodec.VALUE_ENTITY)
      writeEntity(entityId, struct, path)
    } else {
      writer.writeByte(QueryResultCodec.VALUE_STRUCT)
      writer.writeUInt32(struct.fieldsCount)
      struct.fieldsMap.entries.forEach { (key, value) ->
        writeString(key)
        path.withAddedField(key) { writeValue(value, path) }
      }
    }
  }

  private fun writeList(
    listValue: ListValue,
    path: MutableDataConnectPath,
    typeByte: Byte = QueryResultCodec.VALUE_LIST,
  ) {
    writer.writeByte(typeByte)
    writer.writeUInt32(listValue.valuesCount)
    repeat(listValue.valuesCount) { listIndex ->
      path.withAddedListIndex(listIndex) { writeValue(listValue.getValues(listIndex), path) }
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
    val struct = writeEntityStruct(entity, path)
    entities.add(Entity(entityId, encodedEntityId, struct))
  }

  private fun writeEntityStruct(
    struct: Struct,
    path: MutableDataConnectPath,
  ): Struct {
    writer.writeUInt32(struct.fieldsCount)

    var structBuilder: Struct.Builder? = null
    struct.fieldsMap.entries.forEach { (key, value) ->
      writeString(key)
      val entityValue = path.withAddedField(key) { writeEntityValue(value, path) }

      if (entityValue !== value) {
        structBuilder = structBuilder ?: struct.toBuilder()
        if (entityValue === null) {
          structBuilder.removeFields(key)
        } else {
          structBuilder.putFields(key, entityValue)
        }
      }
    }

    return structBuilder?.build() ?: struct
  }

  private fun writeEntityValue(
    value: Value,
    path: MutableDataConnectPath,
  ): Value? =
    when (value.kindCase) {
      Value.KindCase.STRUCT_VALUE -> writeEntityStructValue(value, path)
      Value.KindCase.LIST_VALUE -> writeEntityListValue(value, path)
      else -> {
        writer.writeByte(QueryResultCodec.VALUE_FROM_ENTITY)
        value
      }
    }

  private fun writeEntityStructValue(
    value: Value,
    path: MutableDataConnectPath,
  ): Value? {
    path.getEntityId()?.let { entityId ->
      writer.writeByte(QueryResultCodec.VALUE_ENTITY)
      writeEntity(entityId, value.structValue, path)
      return null
    }

    val struct = value.structValue
    val protoPruneResult =
      if (entityIdByPath == null) {
        null
      } else {
        struct.withDescendantStructsPruned(path) { subStructPath, _ -> subStructPath.isEntity() }
      }

    if (protoPruneResult == null) {
      writer.writeByte(QueryResultCodec.VALUE_FROM_ENTITY)
      return value
    }

    writer.writeByte(QueryResultCodec.VALUE_STRUCT)

    val (prunedStruct, prunedProtoEntities) = protoPruneResult
    val prunedEntities =
      prunedProtoEntities.map { (path, struct) ->
        val entityId =
          path.getEntityId()
            ?: throw IllegalStateException(
              "internal error 2j8v9bczkg: entityId not found for path=${path.toPathString()}"
            )
        PrunedEntity(struct, entityId, path)
      }
    writer.writeUInt32(prunedEntities.size)

    prunedEntities.forEach { (entity, entityId, entityPath) ->
      val entityRelativePath = entityPath.drop(path.size)
      check(entityRelativePath.isNotEmpty()) {
        "internal error gw5zjrbcn3: entityRelativePath.isEmpty(), " +
          "but expected it to be non-empty " +
          "(path=${path.toPathString()}, entityPath=${entityPath.toPathString()})"
      }
      writePath(entityRelativePath)
      writeEntity(entityId, entity, entityPath.toMutableList())
    }

    return prunedStruct.toValueProto()
  }

  private fun writeEntityListValue(
    value: Value,
    path: MutableDataConnectPath,
  ): Value? {
    val listValue = value.listValue

    return when (listValue.classifyLeafContents(path)) {
      ListValueLeafContentsClassification.RecursivelyEmpty,
      ListValueLeafContentsClassification.Scalars -> {
        writer.writeByte(QueryResultCodec.VALUE_FROM_ENTITY)
        value
      }
      ListValueLeafContentsClassification.Entities,
      ListValueLeafContentsClassification.MixedEntitiesAndNonEntities -> {
        writeList(listValue, path, typeByte = QueryResultCodec.VALUE_LIST_OF_ENTITIES)
        null
      }
      ListValueLeafContentsClassification.NonEntities -> {
        val protoPruneResult =
          if (entityIdByPath == null) {
            null
          } else {
            listValue.withDescendantStructsPruned(path) { subStructPath, _ ->
              subStructPath.isEntity()
            }
          }

        if (protoPruneResult === null) {
          writer.writeByte(QueryResultCodec.VALUE_FROM_ENTITY)
          value
        } else {
          writer.writeByte(QueryResultCodec.VALUE_LIST)

          val (prunedListValue, prunedProtoEntities) = protoPruneResult
          val prunedEntities =
            prunedProtoEntities.map { (path, struct) ->
              val entityId =
                path.getEntityId()
                  ?: throw IllegalStateException(
                    "internal error 2j8v9bczkg: entityId not found for path=${path.toPathString()}"
                  )
              PrunedEntity(struct, entityId, path)
            }
          writer.writeUInt32(prunedEntities.size)

          prunedEntities.forEach { (entity, entityId, entityPath) ->
            val entityRelativePath = entityPath.drop(path.size)
            check(entityRelativePath.isNotEmpty()) {
              "internal error kkkt628af8: entityRelativePath.isEmpty(), " +
                "but expected it to be non-empty " +
                "(path=${path.toPathString()}, entityPath=${entityPath.toPathString()})"
            }
            writePath(entityRelativePath)
            writeEntity(entityId, entity, entityPath.toMutableList())
          }

          prunedListValue.toValueProto()
        }
      }
    }
  }

  private fun writePath(path: DataConnectPath) {
    writer.writeUInt32(path.size)
    path.forEach { pathSegment ->
      when (pathSegment) {
        is DataConnectPathSegment.Field -> {
          writer.writeByte(QueryResultCodec.VALUE_PATH_SEGMENT_FIELD)
          writeString(pathSegment.field)
        }
        is DataConnectPathSegment.ListIndex -> {
          writer.writeByte(QueryResultCodec.VALUE_PATH_SEGMENT_LIST_INDEX)
          writer.writeUInt32(pathSegment.index)
        }
      }
    }
  }
  private enum class ListValueLeafContentsClassification {
    Entities,
    Scalars,
    NonEntities,
    MixedEntitiesAndNonEntities,
    RecursivelyEmpty,
  }

  private enum class ListValueLeafContentsClassificationInternal {
    Entities,
    Scalars,
    NonEntities,
  }

  private fun ListValue.classifyLeafContents(
    path: MutableDataConnectPath
  ): ListValueLeafContentsClassification {
    var leafValueClassification: ListValueLeafContentsClassificationInternal? = null

    repeat(valuesCount) { listIndex ->
      val listElement = getValues(listIndex)

      val listElementClassification =
        when (listElement.kindCase) {
          Value.KindCase.STRUCT_VALUE ->
            path.withAddedListIndex(listIndex) {
              if (path.isEntity()) {
                ListValueLeafContentsClassification.Entities
              } else {
                ListValueLeafContentsClassification.NonEntities
              }
            }
          Value.KindCase.LIST_VALUE ->
            path.withAddedListIndex(listIndex) { listElement.listValue.classifyLeafContents(path) }
          else -> ListValueLeafContentsClassification.Scalars
        }

      when (listElementClassification) {
        ListValueLeafContentsClassification.RecursivelyEmpty -> {}
        ListValueLeafContentsClassification.MixedEntitiesAndNonEntities ->
          return ListValueLeafContentsClassification.MixedEntitiesAndNonEntities
        ListValueLeafContentsClassification.Scalars ->
          when (leafValueClassification) {
            null -> {
              leafValueClassification = ListValueLeafContentsClassificationInternal.Scalars
            }
            ListValueLeafContentsClassificationInternal.Scalars,
            ListValueLeafContentsClassificationInternal.NonEntities -> {}
            ListValueLeafContentsClassificationInternal.Entities ->
              return ListValueLeafContentsClassification.MixedEntitiesAndNonEntities
          }
        ListValueLeafContentsClassification.NonEntities ->
          when (leafValueClassification) {
            null,
            ListValueLeafContentsClassificationInternal.Scalars -> {
              leafValueClassification = ListValueLeafContentsClassificationInternal.NonEntities
            }
            ListValueLeafContentsClassificationInternal.NonEntities -> {}
            ListValueLeafContentsClassificationInternal.Entities ->
              return ListValueLeafContentsClassification.MixedEntitiesAndNonEntities
          }
        ListValueLeafContentsClassification.Entities ->
          when (leafValueClassification) {
            null -> {
              leafValueClassification = ListValueLeafContentsClassificationInternal.Entities
            }
            ListValueLeafContentsClassificationInternal.Entities -> {}
            ListValueLeafContentsClassificationInternal.NonEntities,
            ListValueLeafContentsClassificationInternal.Scalars ->
              return ListValueLeafContentsClassification.MixedEntitiesAndNonEntities
          }
      }
    }

    return when (leafValueClassification) {
      null -> ListValueLeafContentsClassification.RecursivelyEmpty
      ListValueLeafContentsClassificationInternal.Scalars ->
        ListValueLeafContentsClassification.Scalars
      ListValueLeafContentsClassificationInternal.Entities ->
        ListValueLeafContentsClassification.Entities
      ListValueLeafContentsClassificationInternal.NonEntities ->
        ListValueLeafContentsClassification.NonEntities
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

  class Entity(
    val id: String,
    val encodedId: ByteArray,
    val data: Struct,
  ) {

    override fun hashCode(): Int =
      Objects.hash(Entity::class.java, id, encodedId.contentHashCode(), data)

    override fun equals(other: Any?): Boolean =
      other is Entity &&
        other.id == id &&
        other.encodedId.contentEquals(encodedId) &&
        other.data == data

    override fun toString(): String =
      "Entity(id=$id, encodedId=${encodedId.to0xHexString()}, data=${data.toCompactString()})"
  }

  companion object {

    fun encode(
      queryResult: Struct,
      entityIdByPath: Map<DataConnectPath, String>? = null
    ): EncodeResult =
      ByteArrayOutputStream().use { byteArrayOutputStream ->
        val entities =
          Channels.newChannel(byteArrayOutputStream).use { writableByteChannel ->
            val encoder = QueryResultEncoder(writableByteChannel, entityIdByPath)
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
