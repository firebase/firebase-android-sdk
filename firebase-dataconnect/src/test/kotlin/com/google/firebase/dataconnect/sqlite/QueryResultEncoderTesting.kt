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

import com.google.firebase.dataconnect.sqlite.QueryResultEncoderTesting.charArbWithCodeGreaterThan255
import com.google.firebase.dataconnect.testutil.BuildByteArrayDSL
import com.google.firebase.dataconnect.testutil.beEqualTo
import com.google.firebase.dataconnect.testutil.property.arbitrary.StringWithEncodingLengthArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.StringWithEncodingLengthArb.Mode.Utf8EncodingLongerThanUtf16
import com.google.firebase.dataconnect.testutil.property.arbitrary.StringWithEncodingLengthArb.Mode.Utf8EncodingShorterThanOrEqualToUtf16
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWith1ByteUtf8Encoding
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWith2ByteUtf8Encoding
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWith3ByteUtf8Encoding
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWith4ByteUtf8Encoding
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWithEvenNumByteUtf8EncodingDistribution
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.stringWithLoneSurrogates
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.property.arbitrary.structKey
import com.google.firebase.dataconnect.testutil.property.arbitrary.twoValues
import com.google.firebase.dataconnect.util.ProtoUtil.toCompactString
import com.google.firebase.dataconnect.util.ProtoUtil.toValueProto
import com.google.firebase.dataconnect.util.StringUtil.to0xHexString
import com.google.protobuf.Struct
import com.google.protobuf.Value
import io.kotest.assertions.withClue
import io.kotest.common.DelicateKotest
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.should
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.char
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.distinct
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.pair
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.withEdgecases
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object QueryResultEncoderTesting {

  data class EntityTestCase(
    val entityIdFieldName: String,
    val entityId: String,
    val struct: Struct
  ) {

    companion object {

      fun arb(
        entityIdFieldName: Arb<String> = StringEncodingTestCase.arb().map { it.string },
        @OptIn(DelicateKotest::class)
        entityId: Arb<String> = StringEncodingTestCase.arb().map { it.string }.distinct(),
        structKey: Arb<String> = Arb.proto.structKey(),
        structSize: IntRange,
        structDepth: IntRange,
      ): Arb<EntityTestCase> =
        Arb.pair(entityIdFieldName, entityId).flatMap { (entityIdFieldName, entityId) ->
          val keyArb = structKey.filterNot { it == entityIdFieldName }
          Arb.proto.struct(size = structSize, depth = structDepth, key = keyArb).map { struct ->
            val newStruct =
              struct.struct
                .toBuilder()
                .putFields(entityIdFieldName, entityId.toValueProto())
                .build()
            EntityTestCase(
              entityIdFieldName = entityIdFieldName,
              entityId = entityId,
              struct = newStruct,
            )
          }
        }

      fun arb(
        entityIdFieldName: Arb<String> = StringEncodingTestCase.arb().map { it.string },
        @OptIn(DelicateKotest::class)
        entityId: Arb<String> = StringEncodingTestCase.arb().map { it.string }.distinct(),
        structKey: Arb<String> = Arb.proto.structKey(),
        structSize: IntRange,
        structDepth: Int,
      ): Arb<EntityTestCase> =
        arb(
          entityIdFieldName = entityIdFieldName,
          entityId = entityId,
          structKey = structKey,
          structSize = structSize,
          structDepth = structDepth..structDepth,
        )
    }
  }

  fun charArbWithCodeGreaterThan255(): Arb<Char> {
    val charRange = 256.toChar()..Char.MAX_VALUE
    val charEdgeCases: List<Char> =
      listOf(
          charRange.first,
          charRange.last,
          Char.MIN_VALUE,
          Char.MAX_VALUE,
          Char.MIN_HIGH_SURROGATE,
          Char.MAX_HIGH_SURROGATE,
          Char.MIN_LOW_SURROGATE,
          Char.MAX_LOW_SURROGATE,
        )
        .flatMap { listOf(it, it + 1, it - 1) }
    return Arb.char(charRange).withEdgecases(charEdgeCases.distinct().filter { it in charRange })
  }

  fun Struct.decodingEncodingShouldProduceIdenticalStruct(
    entities: List<Struct> = emptyList(),
    entityIdFieldName: String? = null
  ) {
    val encodeResult = QueryResultEncoder.encode(this, entityIdFieldName)

    withClue("QueryResultEncoder.encode() entities returned") {
      class StructWrapper(val struct: Struct) {
        override fun equals(other: Any?) = other is StructWrapper && other.struct == struct
        override fun hashCode() = struct.hashCode()
        override fun toString() = struct.toCompactString()
      }

      val actualEntities = encodeResult.entities.map { it.data }.map(::StructWrapper)
      val expectedEntities = entities.map(::StructWrapper)
      actualEntities shouldContainExactlyInAnyOrder expectedEntities
    }

    val decodeResult = QueryResultDecoder.decode(encodeResult.byteArray, encodeResult.entities)

    withClue("QueryResultDecoder.decode() return value") {
      decodeResult should beEqualTo(this, structPrinter = { it.toCompactString() })
    }
  }

  fun String.calculateExpectedEncodingAsEntityId(): ByteArray {
    val byteBuffer = ByteBuffer.allocate(length * 2)
    forEach(byteBuffer::putChar)
    val digest = MessageDigest.getInstance("SHA-512")
    byteBuffer.flip()
    digest.update(byteBuffer)
    return digest.digest()
  }

  fun Struct.forEachValue(block: (Value) -> Unit) {
    val values: MutableList<Value> = mutableListOf(this.toValueProto())
    while (values.isNotEmpty()) {
      val value = values.removeFirst()
      block(value)

      if (value.kindCase == Value.KindCase.LIST_VALUE) {
        value.listValue.valuesList.forEach(values::add)
      } else if (value.kindCase == Value.KindCase.STRUCT_VALUE) {
        value.structValue.fieldsMap.entries.forEach { values.add(it.value) }
      }
    }
  }

  fun Struct.keysRecursive(): List<String> {
    val keys = mutableListOf<String>()
    forEachValue { value ->
      if (value.kindCase == Value.KindCase.STRUCT_VALUE) {
        keys.addAll(value.structValue.fieldsMap.keys)
      }
    }
    return keys
  }

  fun Struct.structSizesRecursive(): List<Int> {
    val structSizes = mutableListOf<Int>()
    forEachValue { value ->
      if (value.kindCase == Value.KindCase.STRUCT_VALUE) {
        structSizes.add(value.structValue.fieldsCount)
      }
    }
    return structSizes
  }

  fun Value.subStructs(): List<Value> {
    val subStructs = mutableListOf<Value>()
    val values = mutableListOf(this)
    while (values.isNotEmpty()) {
      val value = values.removeFirst()
      if (value.kindCase == Value.KindCase.STRUCT_VALUE) {
        subStructs.add(value)
        value.structValue.fieldsMap.values.forEach(values::add)
      } else if (value.kindCase == Value.KindCase.LIST_VALUE) {
        value.listValue.valuesList.forEach(values::add)
      }
    }
    return subStructs
  }

  fun Struct.withRandomlyInsertedEntities(
    entities: List<Struct>,
    rs: RandomSource,
    generateKey: () -> String
  ): Struct {
    val valueWrapper = toValueProto()
    val subStructs = valueWrapper.subStructs()
    val subStructByEntityIndex = List(entities.size) { subStructs.random(rs.random) }
    val insertedEntityIndices = mutableSetOf<Int>()

    fun patch(value: Value): Value {
      return if (value.kindCase == Value.KindCase.STRUCT_VALUE) {
        val entityIndices =
          subStructByEntityIndex.indices.filter { subStructByEntityIndex[it] === value }
        entityIndices.forEach {
          check(!insertedEntityIndices.contains(it)) {
            "internal error jstfxyrdsg: entity index $it visited multiple times"
          }
        }

        val builder = value.structValue.toBuilder()

        builder.fieldsMap.entries.toList().forEach { (key, value) ->
          builder.putFields(key, patch(value))
        }

        entityIndices.forEach { entityIndex ->
          insertedEntityIndices.add(entityIndex)
          val entity = entities[entityIndex]
          val key = generateSequence(generateKey).filterNot { builder.containsFields(it) }.first()
          builder.putFields(key, entity.toValueProto())
        }

        builder.build().toValueProto()
      } else if (value.kindCase == Value.KindCase.LIST_VALUE) {
        val builder = value.listValue.toBuilder()
        repeat(builder.valuesCount) { builder.setValues(it, patch(builder.getValues(it))) }
        builder.build().toValueProto()
      } else {
        value
      }
    }

    val patchedStruct = patch(valueWrapper).structValue

    val entityIndicesSorted = entities.indices.sorted()
    val insertedEntityIndicesSorted = insertedEntityIndices.sorted()
    check(entityIndicesSorted == insertedEntityIndicesSorted) {
      "internal error esv76bzer6: not all entities were inserted: " +
        "entityIndices=$entityIndicesSorted, insertedEntityIndices=$insertedEntityIndicesSorted"
    }

    return patchedStruct
  }
}

sealed class DoubleEncodingTestCase(val value: Double) {

  abstract fun encode(dsl: BuildByteArrayDSL)

  data object PositiveZero : DoubleEncodingTestCase(0.0) {
    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_NUMBER_POSITIVE_ZERO)
    }
  }

  data object NegativeZero : DoubleEncodingTestCase(-0.0) {
    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_NUMBER_NEGATIVE_ZERO)
    }
  }

  class DoubleEncoded(value: Double, val description: String) : DoubleEncodingTestCase(value) {
    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_NUMBER_DOUBLE)
      dsl.putDouble(value)
    }
    override fun toString() = "DoubleEncoded($value, description=$description)"
  }

  data class UInt32Encoded(val intValue: Int, val byteCount: Int) :
    DoubleEncodingTestCase(intValue.toDouble()) {
    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_NUMBER_UINT32)
      val actualByteCount = dsl.putUInt32(intValue)
      check(actualByteCount == byteCount) {
        "actualByteCount=$actualByteCount, byteCount=$byteCount, " +
          "but they should be equal [qmgvc7th7p]"
      }
    }
    override fun toString() = "UInt32Encoded($intValue, byteCount=$byteCount)"
  }

  data class SInt32Encoded(val intValue: Int, val byteCount: Int) :
    DoubleEncodingTestCase(intValue.toDouble()) {
    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_NUMBER_SINT32)
      val actualByteCount = dsl.putSInt32(intValue)
      check(actualByteCount == byteCount) {
        "actualByteCount=$actualByteCount, byteCount=$byteCount, " +
          "but they should be equal [m74dahr5s5]"
      }
    }
    override fun toString() = "SInt32Encoded($intValue, byteCount=$byteCount)"
  }

  data class Fixed32IntEncoded(val intValue: Int) : DoubleEncodingTestCase(intValue.toDouble()) {
    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_NUMBER_FIXED32)
      dsl.putInt(intValue)
    }
    override fun toString() = "Fixed32IntEncoded($intValue)"
  }

  data class UInt64Encoded(val longValue: Long, val byteCount: Int) :
    DoubleEncodingTestCase(longValue.toDouble()) {
    init {
      require(longValue.toDouble().toLong() == longValue) {
        "longValue=$longValue, which does not losslessly round-trip to and from a double: " +
          "$longValue.toDouble()=${longValue.toDouble()}, " +
          "$longValue.toDouble().toLong()=${longValue.toDouble().toLong()} [b7xe5d3mez]"
      }
    }
    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_NUMBER_UINT64)
      val actualByteCount = dsl.putUInt64(longValue)
      check(actualByteCount == byteCount) {
        "actualByteCount=$actualByteCount, byteCount=$byteCount, " +
          "but they should be equal [j7pnjj29fb]"
      }
    }
    override fun toString() = "UInt64Encoded($longValue, byteCount=$byteCount)"
  }

  data class SInt64Encoded(val longValue: Long, val byteCount: Int) :
    DoubleEncodingTestCase(longValue.toDouble()) {
    init {
      require(longValue.toDouble().toLong() == longValue) {
        "longValue=$longValue, which does not losslessly round-trip to and from a double: " +
          "$longValue.toDouble()=${longValue.toDouble()}, " +
          "$longValue.toDouble().toLong()=${longValue.toDouble().toLong()} [zvppmvqt53]"
      }
    }
    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_NUMBER_SINT64)
      val actualByteCount = dsl.putSInt64(longValue)
      check(actualByteCount == byteCount) {
        "actualByteCount=$actualByteCount, byteCount=$byteCount, " +
          "but they should be equal [mhsh585nq2]"
      }
    }
    override fun toString() = "SInt64Encoded($longValue, byteCount=$byteCount)"
  }

  companion object {

    fun arb(): Arb<DoubleEncodingTestCase> =
      Arb.choice(
        Arb.of(
          PositiveZero,
          NegativeZero,
          DoubleEncoded(Double.NaN, "NaN"),
          DoubleEncoded(Double.POSITIVE_INFINITY, "POSITIVE_INFINITY"),
          DoubleEncoded(Double.NEGATIVE_INFINITY, "NEGATIVE_INFINITY"),
        ),
        Arb.int(1..127).map { UInt32Encoded(it, byteCount = 1) },
        Arb.int(128..16_383).map { UInt32Encoded(it, byteCount = 2) },
        Arb.int(16_384..2_097_151).map { UInt32Encoded(it, byteCount = 3) },
        Arb.int(2_097_152..Int.MAX_VALUE).map { Fixed32IntEncoded(it) },
        Arb.long(Int.MAX_VALUE.toLong() + 1..34_359_738_367).map {
          UInt64Encoded(it, byteCount = 5)
        },
        Arb.long(34_359_738_368..4_398_046_511_103).map { UInt64Encoded(it, byteCount = 6) },
        Arb.long(4_398_046_511_104..562_949_953_421_311).map { UInt64Encoded(it, byteCount = 7) },
        Arb.long(562_949_953_421_312..Long.MAX_VALUE).map {
          DoubleEncoded(it.toDouble(), "long value $it")
        },
        Arb.int(-64..-1).map { SInt32Encoded(it, byteCount = 1) },
        Arb.int(-8192..-65).map { SInt32Encoded(it, byteCount = 2) },
        Arb.int(-1_048_576..-8193).map { SInt32Encoded(it, byteCount = 3) },
        Arb.int(Int.MIN_VALUE..-1_048_577).map { Fixed32IntEncoded(it) },
        Arb.long(-17_179_869_184 until Int.MIN_VALUE.toLong()).map {
          SInt64Encoded(it, byteCount = 5)
        },
        Arb.long(-2_199_023_255_552..-17_179_869_185).map { SInt64Encoded(it, byteCount = 6) },
        Arb.long(-281_474_976_710_656..-2_199_023_255_553).map { SInt64Encoded(it, byteCount = 7) },
        Arb.long(Long.MIN_VALUE..-281_474_976_710_657).map {
          DoubleEncoded(it.toDouble(), "negative long value $it")
        },
        Arb.double()
          .filterNot { it.toLong().toDouble() == it || it.isNaN() || it.isInfinite() || it == 0.0 }
          .map { DoubleEncoded(it, "double typical case") }
      )
  }
}

sealed class StringEncodingTestCase(val string: String) {

  abstract fun encode(dsl: BuildByteArrayDSL)

  data object EmptyString : StringEncodingTestCase("") {
    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_STRING_EMPTY)
    }
  }

  class OneByte(val char: Char) : StringEncodingTestCase(char.toString()) {
    init {
      require((char.code.toByte().toInt() and 0xFF) == char.code)
    }
    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_STRING_1BYTE)
      dsl.put(char.code.toByte())
    }
    override fun toString() = "OneByte(char.code=${char.code})"
  }

  class TwoBytes(val char1: Char, val char2: Char) :
    StringEncodingTestCase(char1.toString() + char2.toString()) {
    init {
      require((char1.code.toByte().toInt() and 0xFF) == char1.code)
      require((char2.code.toByte().toInt() and 0xFF) == char2.code)
    }
    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_STRING_2BYTE)
      dsl.put(char1.code.toByte())
      dsl.put(char2.code.toByte())
    }
    override fun toString() = "TwoBytes(char1.code=${char1.code}, char2.code=${char2.code})"
  }

  class OneChar(val char: Char, val description: String) : StringEncodingTestCase(char.toString()) {
    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_STRING_1CHAR)
      dsl.putChar(char)
    }
    override fun toString() = "OneChar(char.code=${char.code}, description=$description)"
  }

  class TwoChars(val char1: Char, val char2: Char, val description: String) :
    StringEncodingTestCase(char1.toString() + char2.toString()) {
    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_STRING_2CHAR)
      dsl.putChar(char1)
      dsl.putChar(char2)
    }
    override fun toString() =
      "TwoChars(" + "char1.code=${char1.code}, char2.code=${char2.code}, description=$description)"
  }

  class Utf8Encoding(string: String, val description: String) : StringEncodingTestCase(string) {

    private val utf8EncodingBytes = string.encodeToByteArray()

    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_STRING_UTF8)
      dsl.putUInt32(utf8EncodingBytes.size)
      dsl.putUInt32(string.length)
      dsl.put(utf8EncodingBytes)
    }
    override fun toString() =
      "Utf8Encoding(description=$description, " +
        "string=$string, utf8EncodingBytes=${utf8EncodingBytes.to0xHexString()})"
  }

  class Utf16Encoding(string: String, val description: String) : StringEncodingTestCase(string) {

    private val utf16EncodingBytes = string.toByteArray(StandardCharsets.UTF_16BE)

    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_STRING_UTF16)
      dsl.putUInt32(string.length)
      dsl.put(utf16EncodingBytes)
    }

    override fun toString() =
      "Utf16Encoding(description=$description, string=$string, " +
        "utf16EncodingBytes=${utf16EncodingBytes.to0xHexString()})"
  }

  class Utf16WithLoneSurrogatesEncoding(string: String, val loneSurrogateCount: Int) :
    StringEncodingTestCase(string) {

    private val utf16EncodingBytes =
      ByteBuffer.allocate(string.length * 2).let { byteBuffer ->
        val charBuffer = byteBuffer.asCharBuffer()
        charBuffer.put(string)
        byteBuffer.array()
      }

    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_STRING_UTF16)
      dsl.putUInt32(string.length)
      dsl.put(utf16EncodingBytes)
    }
    override fun toString() =
      "Utf16WithLoneSurrogatesEncoding(" +
        "loneSurrogateCount=$loneSurrogateCount, string=$string, " +
        "utf16EncodingBytes=${utf16EncodingBytes.to0xHexString()})"
  }

  companion object {

    fun arb(): Arb<StringEncodingTestCase> =
      Arb.choice(
        Arb.constant(EmptyString),
        Arb.int(0..255).map { OneByte(it.toChar()) },
        charArbWithCodeGreaterThan255().map { OneChar(it, "not a lone surrogate") },
        Arb.twoValues(Arb.int(0..255)).map { (codepoint1, codepoint2) ->
          TwoBytes(codepoint1.toChar(), codepoint2.toChar())
        },
        Arb.twoValues(charArbWithCodeGreaterThan255()).map { (char1, char2) ->
          TwoChars(char1, char2, "no lone surrogates")
        },
        // The minimum length is 3, as strings shorter than 3 characters are handled above.
        StringWithEncodingLengthArb(Utf8EncodingShorterThanOrEqualToUtf16, 3..100).map {
          Utf8Encoding(it, "utf-8 encoding shorter than or equal to utf-16 encoding")
        },
        StringWithEncodingLengthArb(Utf8EncodingLongerThanUtf16, 3..100).map {
          Utf16Encoding(it, "utf-16 encoding shorter than utf-8")
        },
        Arb.stringWithLoneSurrogates(3..100).map {
          Utf16WithLoneSurrogatesEncoding(it.string, it.loneSurrogateCount)
        },
        Arb.stringWithLoneSurrogates(1..1).map { OneChar(it.string.single(), "lone surrogate") },
        Arb.stringWithLoneSurrogates(2..2).map {
          TwoChars(it.string[0], it.string[1], "${it.loneSurrogateCount} lone surrogates")
        },
        Arb.string(3..20, Arb.codepointWith1ByteUtf8Encoding()).map {
          Utf8Encoding(it, "string with all chars having 1-byte utf-8 encoding")
        },
        Arb.string(3..20, Arb.codepointWith2ByteUtf8Encoding()).map {
          Utf8Encoding(it, "string with all chars having 2-byte utf-8 encoding")
        },
        Arb.string(3..20, Arb.codepointWith3ByteUtf8Encoding()).map {
          Utf16Encoding(it, "string with all chars having 3-byte utf-8 encoding")
        },
        Arb.string(3..20, Arb.codepointWith4ByteUtf8Encoding()).map {
          Utf8Encoding(it, "string with all chars having 4-byte utf-8 encoding")
        },
        Arb.string(3..20, Arb.codepointWithEvenNumByteUtf8EncodingDistribution()).map {
          if (it.encodeToByteArray().size <= it.toByteArray(StandardCharsets.UTF_16BE).size) {
            Utf8Encoding(it, "string with chars having various byte length utf-8 encoding")
          } else {
            Utf16Encoding(it, "string with chars having various byte length utf-8 encoding")
          }
        },
      )

    private val longStringLengthRange = 2048..99999

    fun longStringsArb(): Arb<StringEncodingTestCase> =
      Arb.choice(
        StringWithEncodingLengthArb(Utf8EncodingShorterThanOrEqualToUtf16, longStringLengthRange)
          .map { Utf8Encoding(it, "utf-8 encoding shorter than or equal to utf-16 encoding") },
        StringWithEncodingLengthArb(Utf8EncodingLongerThanUtf16, longStringLengthRange).map {
          Utf16Encoding(it, "utf-16 encoding shorter than utf-8")
        },
        Arb.stringWithLoneSurrogates(longStringLengthRange).map {
          Utf16WithLoneSurrogatesEncoding(it.string, it.loneSurrogateCount)
        },
        Arb.string(longStringLengthRange, Arb.codepointWith1ByteUtf8Encoding()).map {
          Utf8Encoding(it, "string with all chars having 1-byte utf-8 encoding")
        },
        Arb.string(longStringLengthRange, Arb.codepointWith2ByteUtf8Encoding()).map {
          Utf8Encoding(it, "string with all chars having 2-byte utf-8 encoding")
        },
        Arb.string(longStringLengthRange, Arb.codepointWith3ByteUtf8Encoding()).map {
          Utf16Encoding(it, "string with all chars having 3-byte utf-8 encoding")
        },
        Arb.string(longStringLengthRange, Arb.codepointWith4ByteUtf8Encoding()).map {
          Utf8Encoding(it, "string with all chars having 4-byte utf-8 encoding")
        },
        Arb.string(longStringLengthRange, Arb.codepointWithEvenNumByteUtf8EncodingDistribution())
          .map {
            if (it.encodeToByteArray().size <= it.toByteArray(StandardCharsets.UTF_16BE).size) {
              Utf8Encoding(it, "string with chars having various byte length utf-8 encoding")
            } else {
              Utf16Encoding(it, "string with chars having various byte length utf-8 encoding")
            }
          },
      )
  }
}
