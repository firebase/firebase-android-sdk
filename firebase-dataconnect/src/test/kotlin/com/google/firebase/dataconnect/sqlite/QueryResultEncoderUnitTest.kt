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
import com.google.firebase.dataconnect.emptyDataConnectPath
import com.google.firebase.dataconnect.testutil.BuildByteArrayDSL
import com.google.firebase.dataconnect.testutil.beEqualTo
import com.google.firebase.dataconnect.testutil.buildByteArray
import com.google.firebase.dataconnect.testutil.property.arbitrary.ProtoArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.StringWithEncodingLengthArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.StringWithEncodingLengthArb.Mode.Utf8EncodingLongerThanOrEqualToUtf16
import com.google.firebase.dataconnect.testutil.property.arbitrary.StringWithEncodingLengthArb.Mode.Utf8EncodingShorterThanUtf16
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWith1ByteUtf8Encoding
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWith2ByteUtf8Encoding
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWith3ByteUtf8Encoding
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWith4ByteUtf8Encoding
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWithEvenNumByteUtf8EncodingDistribution
import com.google.firebase.dataconnect.testutil.property.arbitrary.listNoRepeat
import com.google.firebase.dataconnect.testutil.property.arbitrary.listValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.next
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.randomSource
import com.google.firebase.dataconnect.testutil.property.arbitrary.randomlyInsertStruct
import com.google.firebase.dataconnect.testutil.property.arbitrary.randomlyInsertStructs
import com.google.firebase.dataconnect.testutil.property.arbitrary.recursivelyEmptyListValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.stringValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.stringWithLoneSurrogates
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.property.arbitrary.structKey
import com.google.firebase.dataconnect.testutil.property.arbitrary.twoValues
import com.google.firebase.dataconnect.testutil.property.arbitrary.withIterations
import com.google.firebase.dataconnect.testutil.property.arbitrary.withIterationsIfNotNull
import com.google.firebase.dataconnect.testutil.property.arbitrary.withRandomlyInsertedValues
import com.google.firebase.dataconnect.testutil.randomlyInsertStruct
import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.firebase.dataconnect.testutil.structOf
import com.google.firebase.dataconnect.testutil.toValueProto
import com.google.firebase.dataconnect.util.ImmutableByteArray
import com.google.firebase.dataconnect.util.ProtoUtil.toCompactString
import com.google.firebase.dataconnect.util.StringUtil.to0xHexString
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import io.kotest.assertions.withClue
import io.kotest.common.DelicateKotest
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.Exhaustive
import io.kotest.property.PropTestConfig
import io.kotest.property.PropertyContext
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.char
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.distinct
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.pair
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.withEdgecases
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import org.junit.Test

class QueryResultEncoderUnitTest {

  @Test
  fun `bool values`() = runTest {
    data class BoolTestCase(val value: Boolean, val valueTypeIndicator: Byte)
    val arb =
      Exhaustive.of(
        BoolTestCase(true, QueryResultCodec.VALUE_BOOL_TRUE),
        BoolTestCase(false, QueryResultCodec.VALUE_BOOL_FALSE),
      )
    checkAll(propTestConfig, arb) { sample ->
      val struct = structOf("", sample.value)

      val encodeResult = QueryResultEncoder.encode(struct)

      val expectedEncodedBytes = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        putUInt32(1) // struct size
        put(QueryResultCodec.VALUE_STRING_EMPTY) // struct key
        put(sample.valueTypeIndicator)
      }

      encodeResult.byteArray shouldBe expectedEncodedBytes
      QueryResultDecoder.decode(encodeResult.byteArray) shouldBe struct
    }
  }

  @Test
  fun `number values`() = runTest {
    checkAll(propTestConfig, DoubleEncodingTestCase.arb()) { sample ->
      val struct = structOf("", sample.value)

      val encodeResult = QueryResultEncoder.encode(struct)

      val expectedEncodedBytes = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        putUInt32(1) // struct size
        put(QueryResultCodec.VALUE_STRING_EMPTY) // struct key
        sample.encode(this)
      }

      encodeResult.byteArray shouldBe expectedEncodedBytes
      QueryResultDecoder.decode(encodeResult.byteArray) shouldBe struct
    }
  }

  @Test fun `string values`() = verifyStringValues(StringEncodingTestCase.arb())

  @Test
  fun `long string values`() =
    verifyStringValues(StringEncodingTestCase.longStringsArb(), iterations = 50)

  private fun verifyStringValues(
    arb: Arb<StringEncodingTestCase>,
    iterations: Int? = null,
  ) = runTest {
    checkAll(propTestConfig.withIterationsIfNotNull(iterations), arb) { sample ->
      val struct = structOf("", sample.string)

      val encodeResult = QueryResultEncoder.encode(struct)

      val expectedEncodedBytes = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        putUInt32(1) // struct size
        put(QueryResultCodec.VALUE_STRING_EMPTY)
        sample.encode(this)
      }

      encodeResult.byteArray shouldBe expectedEncodedBytes
      QueryResultDecoder.decode(encodeResult.byteArray) shouldBe struct
    }
  }

  @Test
  fun `null values`() = runTest {
    val struct = structOf("", null)

    val encodeResult = QueryResultEncoder.encode(struct)

    val expectedEncodedBytes = buildByteArray {
      putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
      putUInt32(1) // struct size
      put(QueryResultCodec.VALUE_STRING_EMPTY) // struct key
      put(QueryResultCodec.VALUE_NULL)
    }

    encodeResult.byteArray shouldBe expectedEncodedBytes
    QueryResultDecoder.decode(encodeResult.byteArray) shouldBe struct
  }

  @Test
  fun `kind not set values`() = runTest {
    val struct = structOf("", Value.getDefaultInstance())

    val encodeResult = QueryResultEncoder.encode(struct)

    val expectedEncodedBytes = buildByteArray {
      putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
      putUInt32(1) // struct size
      put(QueryResultCodec.VALUE_STRING_EMPTY) // struct key
      put(QueryResultCodec.VALUE_KIND_NOT_SET)
    }

    encodeResult.byteArray shouldBe expectedEncodedBytes
    QueryResultDecoder.decode(encodeResult.byteArray) shouldBe struct
  }

  @Test
  fun `struct values`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct()) { struct ->
      struct.struct.decodingEncodingShouldProduceIdenticalStruct()
    }
  }

  @Test
  fun `list values`() = runTest {
    val listValueArb: Arb<ListValue> =
      Arb.choice(
        Arb.proto.listValue().map { it.listValue },
        Arb.proto.recursivelyEmptyListValue().map { it.listValue },
      )
    checkAll(propTestConfig, Arb.proto.struct(), Arb.list(listValueArb, 1..3)) {
      struct,
      listValueSamples ->
      val listValues = listValueSamples.map { it.toValueProto() }
      val structWithListValues = struct.struct.withRandomlyInsertedValues(listValues)
      structWithListValues.decodingEncodingShouldProduceIdenticalStruct()
    }
  }

  @Test fun `struct keys`() = verifyStringStructKeys(StringEncodingTestCase.arb())

  @Test
  fun `long struct keys`() =
    verifyStringStructKeys(StringEncodingTestCase.longStringsArb(), iterations = 50)

  private fun verifyStringStructKeys(
    arb: Arb<StringEncodingTestCase>,
    iterations: Int? = null,
  ) = runTest {
    checkAll(propTestConfig.withIterationsIfNotNull(iterations), arb) { sample ->
      val struct = structOf(sample.string, null)

      val encodeResult = QueryResultEncoder.encode(struct)

      val expectedEncodedBytes = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        putUInt32(1) // struct size
        sample.encode(this) // struct key
        put(QueryResultCodec.VALUE_NULL)
      }

      encodeResult.byteArray shouldBe expectedEncodedBytes
      QueryResultDecoder.decode(encodeResult.byteArray) shouldBe struct
    }
  }

  @Test
  fun `string encodings round trip`() = runTest {
    val stringArb = StringEncodingTestCase.arb()
    val structArb =
      Arb.proto.struct(
        size = 1..5,
        key = stringArb.map { it.string },
        scalarValue = Arb.proto.stringValue(stringArb.map { it.string }),
      )
    checkAll(propTestConfig, structArb) { struct ->
      struct.struct.decodingEncodingShouldProduceIdenticalStruct()
    }
  }

  @Test
  fun `long string encodings round trip`() = runTest {
    val longStringArb = StringEncodingTestCase.longStringsArb()
    val structArb =
      Arb.proto.struct(
        size = 1,
        key = longStringArb.map { it.string },
        scalarValue = Arb.proto.stringValue(longStringArb.map { it.string }),
      )
    checkAll(propTestConfig.withIterations(50), structArb) { struct ->
      struct.struct.decodingEncodingShouldProduceIdenticalStruct()
    }
  }

  /*

    @Test
    fun `entity IDs are encoded using SHA-512`() = runTest {
      checkAll(propTestConfig, StringEncodingTestCase.arb()) { entityId ->
        val entityIdByPath = mapOf(emptyDataConnectPath() to entityId.string)

        val encodeResult = QueryResultEncoder.encode(Struct.getDefaultInstance(), entityIdByPath)

        encodeResult.entities shouldHaveSize 1
        val encodedEntityId = encodeResult.entities[0].encodedId
        encodedEntityId shouldBe entityId.string.calculateUtf16BigEndianSha512Digest()
      }
    }

    @Test
    fun `entity ID contains code points with 1, 2, 3, and 4 byte UTF-8 encodings`() = runTest {
      checkAll(propTestConfig, StringEncodingTestCase.arb(), Arb.proto.struct()) { entityId, struct ->
        struct.struct.decodingEncodingShouldProduceIdenticalStruct(
          expectedEntities = listOf(struct.struct),
          entityIdByPath = mapOf(emptyDataConnectPath() to entityId.string),
        )
      }
    }

    @Test
    fun `entity ID is a long string`() = runTest {
      checkAll(
        propTestConfig.withIterations(50),
        StringEncodingTestCase.longStringsArb().map { it.string },
        Arb.proto.struct(),
      ) { entityId, struct ->
        struct.struct.decodingEncodingShouldProduceIdenticalStruct(
          expectedEntities = listOf(struct.struct),
          entityIdByPath = mapOf(emptyDataConnectPath() to entityId),
        )
      }
    }
  */

  @Test
  fun `entities, not nested`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct(), Arb.int(1..3)) { structSample, entityCount ->
      val entities = run {
        val entityArb = entityArb()
        List(entityCount) { entityArb.bind() }
      }
      val entityInfoByPath: Map<DataConnectPath, QueryResultEncoder.Entity>
      val rootStruct =
        structSample.struct.toBuilder().let { structBuilder ->
          val insertPaths = structBuilder.randomlyInsertStructs(entities.map { it.struct })
          entityInfoByPath = insertPaths.zip(entities).toMap()
          structBuilder.build()
        }

      rootStruct.decodingEncodingShouldProduceIdenticalStruct(entityInfoByPath)
    }
  }

  @Test
  fun `entities, nested in struct keys`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct(), nestedEntityArb(nestingLevel = 1..3)) {
      structSample,
      nestedEntitySample ->
      val entityInfoByPath: Map<DataConnectPath, QueryResultEncoder.Entity>
      val rootStruct =
        structSample.struct.toBuilder().let { structBuilder ->
          val rootEntityPath =
            structBuilder.randomlyInsertStruct(nestedEntitySample.rootEntity.struct)

          entityInfoByPath = buildMap {
            put(rootEntityPath, nestedEntitySample.rootEntity)
            nestedEntitySample.nestedEntityByPath.entries.forEach {
              put(rootEntityPath + it.key, it.value)
            }
          }

          structBuilder.build()
        }

      rootStruct.decodingEncodingShouldProduceIdenticalStruct(entityInfoByPath)
    }
  }
}

@OptIn(ExperimentalKotest::class)
private val propTestConfig =
  PropTestConfig(
    iterations = 1000,
    edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.33),
    shrinkingMode = ShrinkingMode.Off,
  )

/** A test case for encoding a [Double] value into a byte array. */
private sealed class DoubleEncodingTestCase(val value: Double) {

  /** Encodes the [value] into the given [dsl]. */
  abstract fun encode(dsl: BuildByteArrayDSL)

  /** A test case for encoding positive zero, +0.0 */
  data object PositiveZero : DoubleEncodingTestCase(0.0) {
    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_NUMBER_POSITIVE_ZERO)
    }
  }

  /** A test case for encoding negative zero, -0.0 */
  data object NegativeZero : DoubleEncodingTestCase(-0.0) {
    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_NUMBER_NEGATIVE_ZERO)
    }
  }

  /**
   * A test case for encoding a [Double] as a 64-bit floating point number.
   *
   * @property description A description of the value, for debugging purposes.
   */
  class DoubleEncoded(value: Double, val description: String) : DoubleEncodingTestCase(value) {
    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_NUMBER_DOUBLE)
      dsl.putDouble(value)
    }
    override fun toString() = "DoubleEncoded($value, description=$description)"
  }

  /**
   * A test case for encoding a [Double] that can be losslessly represented as a 32-bit unsigned
   * integer using variable-length encoding.
   *
   * @property intValue The integer value to be encoded.
   * @property byteCount The number of bytes that the variable-length encoding is expected to use.
   */
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

  /**
   * A test case for encoding a [Double] that can be losslessly represented as a 32-bit signed
   * integer using ZigZag variable-length encoding.
   *
   * @property intValue The integer value to be encoded.
   * @property byteCount The number of bytes that the variable-length encoding is expected to use.
   */
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

  /**
   * A test case for encoding a [Double] that can be losslessly represented as a 32-bit integer
   * using fixed-length encoding.
   *
   * @property intValue The integer value to be encoded.
   */
  data class Fixed32IntEncoded(val intValue: Int) : DoubleEncodingTestCase(intValue.toDouble()) {
    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_NUMBER_FIXED32)
      dsl.putInt(intValue)
    }
    override fun toString() = "Fixed32IntEncoded($intValue)"
  }

  /**
   * A test case for encoding a [Double] that can be losslessly represented as a 64-bit unsigned
   * integer using variable-length encoding.
   *
   * @property longValue The integer value to be encoded.
   * @property byteCount The number of bytes that the variable-length encoding is expected to use.
   */
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

  /**
   * A test case for encoding a [Double] that can be losslessly represented as a 64-bit signed
   * integer using ZigZag variable-length encoding.
   *
   * @property longValue The integer value to be encoded.
   * @property byteCount The number of bytes that the variable-length encoding is expected to use.
   */
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

    /** Returns an [Arb] that generates [DoubleEncodingTestCase] instances. */
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

/** A test case for encoding a [String] value into a byte array. */
private sealed class StringEncodingTestCase(val string: String) {

  /** Encodes the [string] into the given [dsl]. */
  abstract fun encode(dsl: BuildByteArrayDSL)

  /** A test case for encoding an empty string. */
  data object EmptyString : StringEncodingTestCase("") {
    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_STRING_EMPTY)
    }

    fun arb(): Arb<EmptyString> = Arb.constant(EmptyString)
  }

  /**
   * A test case for encoding a 1-character string where the character can be represented as a
   * single byte.
   *
   * @property char The character to be encoded.
   */
  class OneByte(val char: Char) : StringEncodingTestCase(char.toString()) {
    init {
      require((char.code.toByte().toInt() and 0xFF) == char.code)
    }
    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_STRING_1BYTE)
      dsl.put(char.code.toByte())
    }
    override fun toString() = "OneByte(char.code=${char.code})"

    companion object {
      fun arb(): Arb<OneByte> = Arb.int(0..255).map { OneByte(it.toChar()) }
    }
  }

  /**
   * A test case for encoding a 2-character string where each character can be represented as a
   * single byte.
   *
   * @property char1 The first character to be encoded.
   * @property char2 The second character to be encoded.
   */
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

    companion object {
      fun arb(): Arb<TwoBytes> =
        Arb.twoValues(OneByte.arb()).map { (byte1, byte2) -> TwoBytes(byte1.char, byte2.char) }
    }
  }

  /**
   * A test case for encoding a 1-character string where the character cannot be represented as a
   * single byte.
   *
   * @property char The character to be encoded.
   */
  class OneChar(val char: Char) : StringEncodingTestCase(char.toString()) {
    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_STRING_1CHAR)
      dsl.putChar(char)
    }
    override fun toString() = "OneChar(char.code=${char.code})"

    companion object {
      fun arb(): Arb<OneChar> = charArbWithCodeGreaterThan255().map(::OneChar)
    }
  }

  /**
   * A test case for encoding a 2-character string where at least one character cannot be
   * represented as a single byte.
   *
   * @property char1 The first character to be encoded.
   * @property char2 The second character to be encoded.
   */
  class TwoChars(val char1: Char, val char2: Char) :
    StringEncodingTestCase(char1.toString() + char2.toString()) {
    override fun encode(dsl: BuildByteArrayDSL) {
      dsl.put(QueryResultCodec.VALUE_STRING_2CHAR)
      dsl.putChar(char1)
      dsl.putChar(char2)
    }
    override fun toString() = "TwoChars(" + "char1.code=${char1.code}, char2.code=${char2.code})"

    companion object {
      fun arb(): Arb<TwoChars> {
        val oneByteArb = OneByte.arb().map { it.char }
        val oneCharArb = OneChar.arb().map { it.char }
        val twoCharsArb =
          Arb.choice(
            Arb.pair(oneByteArb, oneCharArb),
            Arb.pair(oneCharArb, oneByteArb),
            Arb.pair(oneCharArb, oneCharArb),
          )
        return twoCharsArb.map { (char1, char2) -> TwoChars(char1, char2) }
      }
    }
  }

  /**
   * A test case for encoding a [String] using UTF-8 encoding.
   *
   * @property description A description of the string, for debugging purposes.
   */
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

  /**
   * A test case for encoding a [String] using UTF-16BE encoding.
   *
   * @property description A description of the string, for debugging purposes.
   */
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

  /**
   * A test case for encoding a [String] that contains lone surrogates using UTF-16BE encoding.
   *
   * @property loneSurrogateCount The number of lone surrogates in the string.
   */
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

    /** Returns an [Arb] that generates [StringEncodingTestCase] instances. */
    fun arb(lengthRange: IntRange = 0..20): Arb<StringEncodingTestCase> {
      require(!lengthRange.isEmpty()) { "lengthRange must not be empty: $lengthRange" }
      val arbs = buildList {
        if (0 in lengthRange) {
          add(EmptyString.arb())
        }
        if (1 in lengthRange) {
          add(OneByte.arb())
          add(OneChar.arb())
        }
        if (2 in lengthRange) {
          add(TwoBytes.arb())
          add(TwoChars.arb())
        }

        val restRange =
          if (lengthRange.last < 3) {
            IntRange.EMPTY
          } else if (lengthRange.first >= 3) {
            lengthRange
          } else {
            check(lengthRange.first < 3)
            check(lengthRange.last >= 3)
            3..lengthRange.last
          }

        if (!restRange.isEmpty()) {
          add(
            StringWithEncodingLengthArb(Utf8EncodingShorterThanUtf16, restRange).map {
              Utf8Encoding(it, "utf-8 encoding shorter than utf-16 encoding")
            }
          )
          add(
            StringWithEncodingLengthArb(Utf8EncodingLongerThanOrEqualToUtf16, restRange).map {
              Utf16Encoding(it, "utf-8 encoding longer than or equal to utf-16 encoding")
            }
          )
          add(
            Arb.stringWithLoneSurrogates(restRange).map {
              Utf16WithLoneSurrogatesEncoding(it.string, it.loneSurrogateCount)
            }
          )
          add(
            Arb.string(restRange, Arb.codepointWith1ByteUtf8Encoding()).map {
              Utf8Encoding(it, "string with all chars having 1-byte utf-8 encoding")
            }
          )
          add(
            Arb.string(restRange, Arb.codepointWith2ByteUtf8Encoding()).map {
              Utf16Encoding(it, "string with all chars having 2-byte utf-8 encoding")
            }
          )
          add(
            Arb.string(restRange, Arb.codepointWith3ByteUtf8Encoding()).map {
              Utf16Encoding(it, "string with all chars having 3-byte utf-8 encoding")
            }
          )
          add(
            Arb.string(restRange, Arb.codepointWith4ByteUtf8Encoding()).map {
              Utf16Encoding(it, "string with all chars having 4-byte utf-8 encoding")
            }
          )
          add(
            Arb.string(restRange, Arb.codepointWithEvenNumByteUtf8EncodingDistribution()).map {
              if (it.encodeToByteArray().size < it.toByteArray(StandardCharsets.UTF_16BE).size)
                Utf8Encoding(it, "string with chars having various byte length utf-8 encoding")
              else Utf16Encoding(it, "string with chars having various byte length utf-16 encoding")
            }
          )
        }
      }

      return Arb.choice(arbs)
    }

    private val longStringLengthRange = 2048..32768

    /**
     * Returns an [Arb] that generates [StringEncodingTestCase] instances with long strings (2048 to
     * 32768 characters).
     */
    fun longStringsArb(): Arb<StringEncodingTestCase> = arb(longStringLengthRange)
  }
}

private fun Struct.decodingEncodingShouldProduceIdenticalStruct(
  entityByPath: Map<DataConnectPath, QueryResultEncoder.Entity>? = null,
) {
  val encodedBytes =
    withClue("QueryResultEncoder.encode()") {
      val entityIdByPath = entityByPath?.mapValues { it.value.id }
      val getEntityIdForPath = entityIdByPath?.let { it::get }
      val encodeResult = QueryResultEncoder.encode(this, getEntityIdForPath)

      encodeResult.entityByPath shouldContainExactly (entityByPath ?: emptyMap())

      encodeResult.byteArray
    }

  withClue("QueryResultDecoder.decode()") {
    val entityByEncodedId = entityByPath?.map { it.value.run { encodedId to struct } }?.toMap()
    val getEntityByEncodedId = entityByEncodedId?.let { it::get }
    val decodedStruct = QueryResultDecoder.decode(encodedBytes, getEntityByEncodedId)
    decodedStruct should beEqualTo(this, structPrinter = { it.toCompactString() })
  }
}

private interface BuildEntityIdByPathContext {
  fun putWithRandomUniqueEntityId(path: DataConnectPath): String
}

/**
 * Builds a map that associates [DataConnectPath] instances with unique, randomly generated entity
 * IDs.
 *
 * This helper function is used within tests to construct the `entityIdByPath` map required by
 * [QueryResultEncoder.encode]. It ensures that each path specified in the [block] is mapped to a
 * distinct entity ID string.
 *
 * Example usage:
 * ```
 * val entityIdByPath = buildEntityIdByPathMap {
 *   putWithRandomUniqueEntityId(emptyDataConnectPath())
 *   putWithRandomUniqueEntityId(someOtherPath)
 * }
 * ```
 *
 * @return A [Map] where each [DataConnectPath] provided in the [block] is associated with a unique
 * [String] entity ID.
 */
private fun PropertyContext.buildEntityIdByPathMap(
  block: BuildEntityIdByPathContext.() -> Unit
): Map<DataConnectPath, String> {
  @OptIn(DelicateKotest::class) val distinctEntityIdArb = EntityIdSample.arb().distinct()
  val entityIdByPath = mutableMapOf<DataConnectPath, String>()
  val context =
    object : BuildEntityIdByPathContext {
      override fun putWithRandomUniqueEntityId(path: DataConnectPath): String {
        val entityId = distinctEntityIdArb.bind().string
        entityIdByPath[path] = entityId
        return entityId
      }
    }
  block(context)
  return entityIdByPath.toMap()
}

/**
 * Calculates and returns the expected byte array encoding for a string being used as an entity ID.
 *
 * This function mimics the expected behavior of the [QueryResultEncoder] when it processes an
 * entity ID. It takes each character of the string, encodes it into a 2-byte representation
 * (similar to UTF-16BE), and then computes a SHA-512 hash of the resulting byte sequence.
 */
private fun String.calculateUtf16BigEndianSha512Digest(): ImmutableByteArray {
  val byteBuffer = ByteBuffer.allocate(length * 2)
  forEach(byteBuffer::putChar)
  val digest = MessageDigest.getInstance("SHA-512")
  byteBuffer.flip()
  digest.update(byteBuffer)
  return ImmutableByteArray.adopt(digest.digest())
}

/**
 * Creates and returns an [Arb] that generates characters with code points greater than 255.
 *
 * This is useful for testing string encoding scenarios where characters cannot be represented as a
 * single byte. It includes various boundary and surrogate characters as edge cases.
 */
private fun charArbWithCodeGreaterThan255(): Arb<Char> {
  val charRange1 = 256.toChar() until Char.MIN_SURROGATE
  val charRange2 = Char.MIN_SURROGATE..Char.MAX_SURROGATE
  val charRange3 = (Char.MAX_SURROGATE + 1)..Char.MAX_VALUE
  val charRanges = listOf(charRange1, charRange2, charRange3)

  val edgeCases =
    charRanges
      .flatMap { listOf(it.first, it.last) }
      .flatMap { listOf(it, it + 1, it - 1) }
      .distinct()
      .filter { char -> charRanges.any { char in it } }
      .sorted()

  return Arb.choice(charRanges.map { Arb.char(it) }).withEdgecases(edgeCases)
}

private data class EntityIdSample(val string: String) {

  val expectedEncoding: ImmutableByteArray = string.calculateUtf16BigEndianSha512Digest()

  operator fun component2(): ImmutableByteArray = expectedEncoding

  companion object {
    fun arb(): Arb<EntityIdSample> =
      Arb.string(5..5, Codepoint.alphanumeric()).map(::EntityIdSample)
  }
}

private fun entityArb(
  entityIdArb: Arb<EntityIdSample> = @OptIn(DelicateKotest::class) EntityIdSample.arb().distinct(),
  structArb: Arb<ProtoArb.StructInfo> = Arb.proto.struct(),
): Arb<QueryResultEncoder.Entity> =
  Arb.bind(entityIdArb, structArb) { entityIdSample, structSample ->
    QueryResultEncoder.Entity(
      entityIdSample.string,
      entityIdSample.expectedEncoding,
      structSample.struct
    )
  }

private data class NestedEntitySample(
  val struct: Struct,
  val rootEntity: QueryResultEncoder.Entity,
  val nestedEntityByPath: Map<DataConnectPath, QueryResultEncoder.Entity>,
)

private fun nestedEntityArb(
  entityArb: Arb<QueryResultEncoder.Entity> = entityArb(),
  nestingLevel: IntRange,
): Arb<NestedEntitySample> {
  require(nestingLevel.first > 0)
  require(!nestingLevel.isEmpty())

  val structKeyArb = Arb.proto.structKey()
  val entitiesArb = Arb.listNoRepeat(entityArb, nestingLevel)

  return Arb.bind(entityArb, entitiesArb, Arb.randomSource()) { rootEntity, otherEntities, rs ->
    val entitiesRemaining = otherEntities.toMutableList().apply { add(0, rootEntity) }
    val insertPaths = mutableListOf<DataConnectPath>()
    var struct = entitiesRemaining.removeLast().struct
    while (entitiesRemaining.isNotEmpty()) {
      val parentEntity = entitiesRemaining.removeLast()
      struct =
        parentEntity.struct.toBuilder().let { parentEntityStructBuilder ->
          val insertPath =
            parentEntityStructBuilder.randomlyInsertStruct(
              struct,
              rs.random,
              generateKey = { structKeyArb.next(rs, edgeCaseProbability = rs.random.nextFloat()) },
            )
          insertPaths.add(insertPath)
          parentEntityStructBuilder.build()
        }
    }

    val nestedEntityByPath = buildMap {
      val pathSoFar = emptyDataConnectPath().toMutableList()
      insertPaths.zip(otherEntities).forEach { (relativePath, entity) ->
        pathSoFar.addAll(relativePath)
        put(pathSoFar.toList(), entity)
      }
    }

    NestedEntitySample(struct, rootEntity, nestedEntityByPath)
  }
}
