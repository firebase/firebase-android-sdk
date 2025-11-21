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

import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.BadMagicException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.ByteArrayEOFException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.ByteEOFException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.Companion.decode
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.EntityNotFoundException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.MagicEOFException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.UInt32DecodeException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.UInt32EOFException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.UInt32InvalidValueException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.UInt64DecodeException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.UInt64InvalidValueException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.UnexpectedValueTypeIndicatorByteException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.UnknownValueTypeIndicatorByteException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.Utf16EOFException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.Utf8EOFException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.Utf8IncorrectNumCharactersException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoderUnitTest.ByteArraySample
import com.google.firebase.dataconnect.testutil.buildByteArray
import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import com.google.firebase.dataconnect.util.ProtoUtil.toValueProto
import com.google.firebase.dataconnect.util.StringUtil.to0xHexString
import com.google.protobuf.Struct
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.string.shouldMatch
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.Exhaustive
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.positiveInt
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.withEdgecases
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import io.kotest.property.exhaustive.map
import kotlinx.coroutines.test.runTest
import org.junit.Test

class QueryResultDecoderUnitTest {

  @Test
  fun `magic value incorrect should throw`() = runTest {
    class MagicValue(val value: Int) {
      val hexValue = value.toUInt().toString(16)
      override fun toString(): String = "$value (hexValue=0x$hexValue)"
    }
    class BadMagicTestCase(val good: MagicValue, val bad: MagicValue) {
      override fun toString(): String = "${this::class.simpleName}(good=$good, bad=$bad)"
    }
    val goodMagic = MagicValue(QueryResultCodec.QUERY_RESULT_MAGIC)
    val arb =
      Arb.int()
        .filterNot { it == goodMagic.value }
        .map { BadMagicTestCase(good = goodMagic, bad = MagicValue(it)) }

    checkAll(propTestConfig, arb) { testCase ->
      val byteArray = buildByteArray { putInt(testCase.bad.value) }
      assertDecodeThrows<BadMagicException>(byteArray) {
        messageShouldContainWithNonAbuttingText("jk832sz9hx")
        messageShouldContainRegexMatchIgnoringCase("read magic value 0x0*${testCase.bad.hexValue}")
        messageShouldContainRegexMatchIgnoringCase("expected 0x0*${testCase.good.hexValue}")
      }
    }
  }

  @Test
  fun `magic value truncated should throw`() = runTest {
    val arb = Arb.byteArray(Arb.int(0..3), Arb.byte()).map(::ByteArraySample)
    checkAll(propTestConfig, arb) { sample ->
      val byteArray = buildByteArray { put(sample.byteArray) }
      assertDecodeThrowsEOFException<MagicEOFException>(
        byteArray,
        errorUid = "xg5y5fm2vk",
        callerErrorId = "MagicEOF",
        whileText = "reading 4 bytes",
        gotBytes = sample.byteArray,
        expectedBytesText = null,
        fewerBytesThanExpected = 4 - sample.arraySize,
      )
    }
  }

  @Test
  fun `root struct value type indicator byte unknown should throw`() = runTest {
    checkAll(propTestConfig, Arb.unknownValueTypeIndicatorByte()) { unknownValueTypeIndicator ->
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        put(unknownValueTypeIndicator.byte)
      }
      assertDecodeThrows<UnknownValueTypeIndicatorByteException>(byteArray) {
        messageShouldContainWithNonAbuttingText("y6ppbg7ary")
        messageShouldContainWithNonAbuttingText(
          "unknownErrorId=RootStructValueTypeIndicatorByteUnknown"
        )
        messageShouldContainWithNonAbuttingTextIgnoringCase(
          "read unknown value type indicator byte: ${unknownValueTypeIndicator.byte}"
        )
      }
    }
  }

  @Test
  fun `root struct value type indicator byte unexpected should throw`() = runTest {
    val arb = Exhaustive.collection(valueTypeIndicatorBytes - structValueTypeIndicatorBytes)
    checkAll(propTestConfig, arb) { unexpectedRootStructValueTypeIndicatorByte ->
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        put(unexpectedRootStructValueTypeIndicatorByte)
      }
      assertDecodeThrows<UnexpectedValueTypeIndicatorByteException>(byteArray) {
        messageShouldContainWithNonAbuttingText("hxtgz4ffem")
        messageShouldContainWithNonAbuttingText(
          "unexpectedErrorId=RootStructValueTypeIndicatorByteUnexpected"
        )
        messageShouldContainWithNonAbuttingTextIgnoringCase(
          "read unexpected value type indicator byte: $unexpectedRootStructValueTypeIndicatorByte"
        )
      }
    }
  }

  @Test
  fun `root struct value type indicator byte truncated should throw`() {
    val byteArray = buildByteArray { putInt(QueryResultCodec.QUERY_RESULT_MAGIC) }
    assertDecodeThrowsEOFException<ByteEOFException>(
      byteArray,
      errorUid = "xg5y5fm2vk",
      callerErrorId = "RootStructValueTypeIndicatorByteEOF",
      whileText = "reading 1 bytes",
      gotBytes = ByteArray(0),
      expectedBytesText = null,
      fewerBytesThanExpected = 1,
    )
  }

  @Test
  fun `struct key count invalid should throw`() = runTest {
    checkAll(propTestConfig, MalformedVarintByteArrayArb(5..10)) { testCase ->
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        put(QueryResultCodec.VALUE_STRUCT)
        put(testCase.byteArray)
      }
      assertDecodeThrows<UInt32DecodeException>(byteArray) {
        messageShouldContainWithNonAbuttingText("ybydmsykkp")
        messageShouldContainWithNonAbuttingText("decodeErrorId=StructKeyCountDecodeFailed")
        messageShouldContainWithNonAbuttingTextIgnoringCase("uint32 decode failed of 5 bytes")
        messageShouldContainWithNonAbuttingTextIgnoringCase(
          testCase.byteArray.sliceArray(0..4).to0xHexString()
        )
      }
    }
  }

  @Test
  fun `struct key count truncated should throw`() = runTest {
    checkAll(propTestConfig, Arb.truncatedUInt32ByteArray()) { sample ->
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        put(QueryResultCodec.VALUE_STRUCT)
        put(sample.byteArray)
      }
      assertDecodeThrowsEOFException<UInt32EOFException>(
        byteArray,
        errorUid = "c439qmdmnk",
        callerErrorId = "StructKeyCountEOF",
        whileText = "decoding uint32 value",
        gotBytes = sample.byteArray,
        expectedBytesText = "between 1 and 5",
        fewerBytesThanExpected = null,
      )
    }
  }

  @Test
  fun `should throw UInt32InvalidValueException for utf8 invalid byte count`() = runTest {
    checkAll(propTestConfig, MalformedVarintByteArrayArb(5..10)) { testCase ->
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        put(QueryResultCodec.VALUE_STRUCT)
        putUInt32(1) // struct key count
        put(QueryResultCodec.VALUE_STRING_UTF8)
        put(testCase.byteArray)
      }
      assertDecodeThrows<UInt32InvalidValueException>(byteArray) {
        messageShouldContainWithNonAbuttingText("a9kma55y7m")
        messageShouldContainWithNonAbuttingTextIgnoringCase(
          testCase.byteArray.sliceArray(0..4).to0xHexString()
        )
        messageShouldContainWithNonAbuttingTextIgnoringCase("string byte count")
        messageShouldContainWithNonAbuttingTextIgnoringCase("greater than or equal to zero")
      }
    }
  }

  @Test
  fun `should throw UInt32InvalidValueException for utf8 invalid char count`() = runTest {
    data class InvalidStringCharCountTestCase(
      val stringByteCount: Int,
      val invalidStringCharCount: MalformedVarintByteArrayArb.Sample,
    )
    val arb =
      Arb.bind(
        Arb.positiveInt(),
        MalformedVarintByteArrayArb(5..10),
        ::InvalidStringCharCountTestCase
      )

    checkAll(propTestConfig, arb) { testCase ->
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        put(QueryResultCodec.VALUE_STRUCT)
        putUInt32(1) // struct key count
        put(QueryResultCodec.VALUE_STRING_UTF8)
        putUInt32(testCase.stringByteCount)
        put(testCase.invalidStringCharCount.byteArray)
      }
      assertDecodeThrows<UInt32InvalidValueException>(byteArray) {
        messageShouldContainWithNonAbuttingText("gwybfam237")
        messageShouldContainWithNonAbuttingTextIgnoringCase(
          testCase.invalidStringCharCount.byteArray.sliceArray(0..4).to0xHexString()
        )
        messageShouldContainWithNonAbuttingTextIgnoringCase("string char count")
        messageShouldContainWithNonAbuttingTextIgnoringCase("greater than or equal to zero")
      }
    }
  }

  @Test
  fun `should throw UInt32InvalidValueException for utf16 invalid char count`() = runTest {
    checkAll(propTestConfig, MalformedVarintByteArrayArb(5..10)) { testCase ->
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        put(QueryResultCodec.VALUE_STRUCT)
        putUInt32(1) // struct key count
        put(QueryResultCodec.VALUE_STRING_UTF16)
        put(testCase.byteArray)
      }
      assertDecodeThrows<UInt32InvalidValueException>(byteArray) {
        messageShouldContainWithNonAbuttingText("gwybfam237")
        messageShouldContainWithNonAbuttingTextIgnoringCase(
          testCase.byteArray.sliceArray(0..4).to0xHexString()
        )
        messageShouldContainWithNonAbuttingTextIgnoringCase("string char count")
        messageShouldContainWithNonAbuttingTextIgnoringCase("greater than or equal to zero")
      }
    }
  }

  @Test
  fun `should throw UnknownStringValueTypeIndicatorByteException`() = runTest {
    data class NonStringValueTypeIndicator(val value: Byte)
    val arb =
      Exhaustive.collection(valueTypeIndicatorBytes - stringValueTypeIndicatorBytes)
        .map(::NonStringValueTypeIndicator)

    checkAll(propTestConfig, arb) { nonStringValueTypeIndicator ->
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        put(QueryResultCodec.VALUE_STRUCT)
        putUInt32(1) // struct key count
        put(nonStringValueTypeIndicator.value)
      }
      assertDecodeThrows<IllegalArgumentException>(byteArray) {
        messageShouldContainWithNonAbuttingText("hfvxx849cv")
        messageShouldContainWithNonAbuttingText(nonStringValueTypeIndicator.value.toString())
        messageShouldContainWithNonAbuttingTextIgnoringCase("non-string value type indicator byte")
      }
    }
  }

  @Test
  fun `should throw UnknownValueTypeIndicatorByteException`() = runTest {
    checkAll(propTestConfig, Arb.unknownValueTypeIndicatorByte()) { unknownValueTypeIndicatorByte ->
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        put(QueryResultCodec.VALUE_STRUCT)
        putUInt32(1) // struct key count
        put(QueryResultCodec.VALUE_STRING_EMPTY)
        put(unknownValueTypeIndicatorByte.byte)
      }
      assertDecodeThrows<UnknownValueTypeIndicatorByteException>(byteArray) {
        messageShouldContainWithNonAbuttingText("pmkb3sc2mn")
        messageShouldContainWithNonAbuttingText(unknownValueTypeIndicatorByte.byte.toString())
        messageShouldContainWithNonAbuttingTextIgnoringCase("unknown value type indicator byte")
      }
    }
  }

  @Test
  fun `should throw UInt32InvalidValueException for invalid entity ID size`() = runTest {
    checkAll(propTestConfig, MalformedVarintByteArrayArb(5..10)) { testCase ->
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        put(QueryResultCodec.VALUE_ENTITY)
        put(testCase.byteArray)
      }
      assertDecodeThrows<UInt32InvalidValueException>(byteArray) {
        messageShouldContainWithNonAbuttingText("agvqmbgknh")
        messageShouldContainWithNonAbuttingTextIgnoringCase(
          testCase.byteArray.sliceArray(0..4).to0xHexString()
        )
        messageShouldContainWithNonAbuttingTextIgnoringCase("entity id size")
        messageShouldContainWithNonAbuttingTextIgnoringCase("greater than or equal to zero")
      }
    }
  }

  @Test
  fun `should throw EntityNotFoundException`() = runTest {
    class EncodedEntityId(val value: ByteArray) {
      override fun toString() = "${this::class.simpleName}(${value.to0xHexString()})"
    }
    val arb = Arb.byteArray(Arb.int(0..50), Arb.byte()).map(::EncodedEntityId)

    checkAll(propTestConfig, arb) { encodedEntityId ->
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        put(QueryResultCodec.VALUE_ENTITY)
        putUInt32(encodedEntityId.value.size)
        put(encodedEntityId.value)
      }
      assertDecodeThrows<EntityNotFoundException>(byteArray) {
        messageShouldContainWithNonAbuttingText("p583k77y7r")
        messageShouldContainWithNonAbuttingText(encodedEntityId.value.to0xHexString())
        messageShouldContainWithNonAbuttingTextIgnoringCase("could not find entity")
      }
    }
  }

  @Test
  fun `should be able to decode very long entity IDs`() = runTest {
    class VeryLongEncodedEntityIdTestCase(
      val veryLongEncodedEntityId: ByteArray,
      val randomValue: Double
    ) {
      override fun toString(): String =
        "${this::class.simpleName}(" +
          veryLongEncodedEntityId.to0xHexString().substring(0..20) +
          "..., " +
          "length=${veryLongEncodedEntityId.size}, " +
          "randomValue=$randomValue)"
    }
    val arb =
      Arb.bind(
        Arb.byteArray(Arb.int(2000..90000), Arb.byte()),
        Arb.double(),
        ::VeryLongEncodedEntityIdTestCase
      )

    checkAll(@OptIn(ExperimentalKotest::class) propTestConfig.copy(iterations = 10), arb) { testCase
      ->
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        put(QueryResultCodec.VALUE_ENTITY)
        putUInt32(testCase.veryLongEncodedEntityId.size)
        put(testCase.veryLongEncodedEntityId)
        putUInt32(1) // entity key count
        put(QueryResultCodec.VALUE_STRING_EMPTY)
        put(QueryResultCodec.VALUE_KIND_NOT_SET)
      }
      val entity =
        QueryResultCodec.Entity(
          id = "",
          encodedId = testCase.veryLongEncodedEntityId,
          data = Struct.newBuilder().putFields("", testCase.randomValue.toValueProto()).build()
        )

      val decodeResult = decode(byteArray, listOf(entity))

      decodeResult shouldBe entity.data
    }
  }

  @Test
  fun `should throw Utf8EOFException with 'insufficient bytes' message for utf8`() = runTest {
    class InsufficientUtf8BytesTestCase(
      val string: String,
      val byteCountInflation: Int,
      val charCountInflation: Int,
    ) {
      val inflatedCharCount = string.length + charCountInflation
      val stringUtf8Bytes = string.encodeToByteArray()
      val inflatedByteCount = stringUtf8Bytes.size + byteCountInflation
      override fun toString(): String =
        "${this::class.simpleName}(" +
          "string=$string (charCount=${string.length}), " +
          "stringUtf8Bytes=${stringUtf8Bytes.to0xHexString()} " +
          "(byteCount=${stringUtf8Bytes.size}), " +
          "byteCountInflation=$byteCountInflation, charCountInflation=$charCountInflation, " +
          "inflatedCharCount=${inflatedCharCount}, inflatedByteCount=${inflatedByteCount})"
    }
    val arb =
      Arb.bind(
        Arb.string(0..20),
        Arb.positiveInt(100),
        Arb.positiveInt(100),
        ::InsufficientUtf8BytesTestCase,
      )

    checkAll(propTestConfig, arb) { testCase ->
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        put(QueryResultCodec.VALUE_STRUCT)
        putUInt32(1) // struct key count
        put(QueryResultCodec.VALUE_STRING_UTF8)
        putUInt32(testCase.inflatedByteCount)
        putUInt32(testCase.inflatedCharCount)
        put(testCase.stringUtf8Bytes)
      }
      assertDecodeThrows<Utf8EOFException>(byteArray) {
        messageShouldContainWithNonAbuttingText("akn3x7p8rm")
        messageShouldContainWithNonAbuttingTextIgnoringCase("end of input reached prematurely")
        messageShouldContainWithNonAbuttingTextIgnoringCase(
          "reading ${testCase.inflatedCharCount} characters " +
            "(${testCase.inflatedByteCount} bytes) of a UTF-8 encoded string"
        )
        messageShouldContainWithNonAbuttingTextIgnoringCase(
          "got ${testCase.string.length} characters, " +
            "${testCase.charCountInflation} fewer characters"
        )
        messageShouldContainWithNonAbuttingTextIgnoringCase(
          "${testCase.stringUtf8Bytes.size} bytes, ${testCase.byteCountInflation} fewer bytes"
        )
      }
    }
  }

  @Test
  fun `should throw Utf8IncorrectNumCharactersException with 'insufficient chars' message for utf8`() =
    runTest {
      class IncorrectUtf8NumCharactersTestCase(
        val string: String,
        val charCountInflation: Int,
      ) {
        val inflatedCharCount = string.length + charCountInflation
        val stringUtf8Bytes = string.encodeToByteArray()
        override fun toString(): String =
          "${this::class.simpleName}(" +
            "string=$string (charCount=${string.length}), " +
            "stringUtf8Bytes=${stringUtf8Bytes.to0xHexString()} " +
            "(byteCount=${stringUtf8Bytes.size}), " +
            "charCountInflation=$charCountInflation, inflatedCharCount=${inflatedCharCount})"
      }
      val arb =
        Arb.string(0..20).flatMap { string ->
          val charCountInflationValues = (-10..10).filter { it != 0 && it >= -(string.length) }
          Arb.of(charCountInflationValues).map { charCountInflation ->
            IncorrectUtf8NumCharactersTestCase(string, charCountInflation)
          }
        }

      checkAll(propTestConfig, arb) { testCase ->
        val byteArray = buildByteArray {
          putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
          put(QueryResultCodec.VALUE_STRUCT)
          putUInt32(1) // struct key count
          put(QueryResultCodec.VALUE_STRING_UTF8)
          putUInt32(testCase.stringUtf8Bytes.size)
          putUInt32(testCase.inflatedCharCount)
          put(testCase.stringUtf8Bytes)
        }
        assertDecodeThrows<Utf8IncorrectNumCharactersException>(byteArray) {
          messageShouldContainWithNonAbuttingText("chq89pn4j6")
          messageShouldContainWithNonAbuttingTextIgnoringCase(
            "expected to read ${testCase.inflatedCharCount} characters"
          )
          messageShouldContainWithNonAbuttingTextIgnoringCase(
            "${testCase.stringUtf8Bytes.size} bytes"
          )
          messageShouldContainWithNonAbuttingTextIgnoringCase("got the expected number of bytes")
          if (testCase.charCountInflation > 0) {
            messageShouldContainWithNonAbuttingTextIgnoringCase(
              "got ${testCase.string.length} characters, " +
                "${testCase.charCountInflation} fewer characters"
            )
          } else {
            messageShouldContainWithNonAbuttingTextIgnoringCase(
              "got ${testCase.string.length} characters, " +
                "${-testCase.charCountInflation} more characters"
            )
          }
        }
      }
    }

  @Test
  fun `should throw Utf16EOFException with 'insufficient chars' message for utf16`() = runTest {
    class InsufficientUtf16BytesTestCase(
      val string: String,
      val charCountInflation: Int,
    ) {
      val inflatedCharCount = string.length + charCountInflation
      override fun toString(): String =
        "${this::class.simpleName}(" +
          "string=$string " +
          "(charCount=${string.length}, utf16ByteCount=${string.length*2}), " +
          "charCountInflation=$charCountInflation, inflatedCharCount=${inflatedCharCount})"
    }
    val arb =
      Arb.bind(Arb.string(0..20), Arb.positiveInt(max = 10), ::InsufficientUtf16BytesTestCase)

    checkAll(propTestConfig, arb) { testCase ->
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        put(QueryResultCodec.VALUE_STRUCT)
        putUInt32(1) // struct key count
        put(QueryResultCodec.VALUE_STRING_UTF16)
        putUInt32(testCase.inflatedCharCount)
        testCase.string.forEach(::putChar)
      }
      assertDecodeThrows<Utf16EOFException>(byteArray) {
        messageShouldContainWithNonAbuttingText("e399qdvzdz")
        messageShouldContainWithNonAbuttingTextIgnoringCase("end of input reached prematurely")
        messageShouldContainWithNonAbuttingTextIgnoringCase(
          "reading ${testCase.inflatedCharCount} characters " +
            "(${testCase.inflatedCharCount * 2} bytes) of a UTF-16 encoded string"
        )
        messageShouldContainWithNonAbuttingTextIgnoringCase(
          "got ${testCase.string.length} characters, " +
            "${testCase.charCountInflation} fewer characters"
        )
        messageShouldContainWithNonAbuttingTextIgnoringCase(
          "${testCase.string.length*2} bytes, ${testCase.charCountInflation*2} fewer bytes"
        )
      }
    }
  }

  @Test
  fun `should throw UInt32InvalidValueException for invalid list size`() = runTest {
    checkAll(propTestConfig, MalformedVarintByteArrayArb(5..10)) { testCase ->
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        put(QueryResultCodec.VALUE_STRUCT)
        putUInt32(1) // struct key count
        put(QueryResultCodec.VALUE_STRING_EMPTY)
        put(QueryResultCodec.VALUE_LIST)
        put(testCase.byteArray)
      }
      assertDecodeThrows<UInt32InvalidValueException>(byteArray) {
        messageShouldContainWithNonAbuttingText("yfvpf9pwt8")
        messageShouldContainWithNonAbuttingTextIgnoringCase(
          testCase.byteArray.sliceArray(0..4).to0xHexString()
        )
        messageShouldContainWithNonAbuttingTextIgnoringCase("greater than or equal to zero")
      }
    }
  }

  @Test
  fun `should throw ByteArrayEOFException`() = runTest {
    class ByteArrayEOFTestCase(val byteArray: ByteArray, val byteCountInflation: Int) {
      val inflatedByteCount = byteArray.size + byteCountInflation
      override fun toString() =
        "${this::class.simpleName}(" +
          "byteArray=${byteArray.to0xHexString()}, " +
          "byteCountInflation=$byteCountInflation, inflatedByteCount=$inflatedByteCount)"
    }
    val arb =
      Arb.bind(
        Arb.byteArray(Arb.int(0..16384), Arb.byte()),
        Arb.positiveInt(32768),
        ::ByteArrayEOFTestCase
      )

    checkAll(propTestConfig, arb) { testCase ->
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        put(QueryResultCodec.VALUE_ENTITY)
        putUInt32(testCase.inflatedByteCount)
        put(testCase.byteArray)
      }
      assertDecodeThrows<ByteArrayEOFException>(byteArray) {
        messageShouldContainWithNonAbuttingText("dnx886qwmk")
        messageShouldContainWithNonAbuttingTextIgnoringCase("end of input reached prematurely")
        messageShouldContainWithNonAbuttingTextIgnoringCase(
          "reading byte array of length ${testCase.inflatedByteCount}"
        )
        messageShouldContainWithNonAbuttingTextIgnoringCase(
          "got ${testCase.byteArray.size} bytes, ${testCase.byteCountInflation} fewer bytes"
        )
      }
    }
  }

  private class AssertDecodeThrowsDSL {

    val messageSubstringsWithNonAbuttingText = mutableListOf<String>()
    val messageSubstringsWithNonAbuttingTextIgnoringCase = mutableListOf<String>()
    val messageSubstringRegexMatchesIgnoringCase = mutableListOf<String>()

    fun messageShouldContainWithNonAbuttingText(text: String) {
      messageSubstringsWithNonAbuttingText.add(text)
    }

    fun messageShouldContainWithNonAbuttingTextIgnoringCase(text: String) {
      messageSubstringsWithNonAbuttingTextIgnoringCase.add(text)
    }

    fun messageShouldContainRegexMatchIgnoringCase(text: String) {
      messageSubstringRegexMatchesIgnoringCase.add(text)
    }
  }

  private inline fun <reified E : Throwable> assertDecodeThrows(
    byteArray: ByteArray,
    config: AssertDecodeThrowsDSL.() -> Unit,
  ) {
    val dsl = AssertDecodeThrowsDSL().apply(config)

    val exception = shouldThrow<E> { decode(byteArray, emptyList()) }

    assertSoftly {
      dsl.messageSubstringsWithNonAbuttingText.forEach {
        exception.message shouldContainWithNonAbuttingText it
      }
      dsl.messageSubstringsWithNonAbuttingTextIgnoringCase.forEach {
        exception.message shouldContainWithNonAbuttingTextIgnoringCase it
      }
      dsl.messageSubstringRegexMatchesIgnoringCase.forEach {
        exception.message shouldMatch Regex(".*${it}.*", RegexOption.IGNORE_CASE)
      }
    }
  }

  private inline fun <reified E : QueryResultDecoder.EOFException> assertDecodeThrowsEOFException(
    byteArray: ByteArray,
    errorUid: String,
    callerErrorId: String,
    whileText: String,
    gotBytes: ByteArray,
    expectedBytesText: String?,
    fewerBytesThanExpected: Int?,
  ) =
    assertDecodeThrows<E>(byteArray) {
      messageShouldContainWithNonAbuttingText(errorUid)
      messageShouldContainWithNonAbuttingText("eofErrorId=$callerErrorId")
      messageShouldContainWithNonAbuttingTextIgnoringCase(
        "end of input reached prematurely while $whileText"
      )
      messageShouldContainWithNonAbuttingTextIgnoringCase(
        "got ${gotBytes.size} bytes (${gotBytes.to0xHexString()}"
      )
      if (expectedBytesText !== null) {
        messageShouldContainWithNonAbuttingTextIgnoringCase("expected $expectedBytesText bytes")
      }
      if (fewerBytesThanExpected !== null) {
        messageShouldContainWithNonAbuttingTextIgnoringCase(
          "$fewerBytesThanExpected fewer bytes than expected"
        )
      }
    }

  @Test
  fun `should throw UInt32DecodeException when decoding invalid varints`() = runTest {
    checkAll(propTestConfig, MalformedVarintByteArrayArb(5..20)) { testCase ->
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        put(QueryResultCodec.VALUE_STRUCT)
        putUInt32(1) // struct key count
        put(QueryResultCodec.VALUE_STRING_EMPTY)
        put(QueryResultCodec.VALUE_NUMBER_UINT32)
        put(testCase.byteArray)
      }
      assertDecodeThrows<UInt32DecodeException>(byteArray) {
        messageShouldContainWithNonAbuttingText("ybydmsykkp")
        messageShouldContainWithNonAbuttingTextIgnoringCase(
          "uint32 decode failed of 5 bytes: " + testCase.byteArray.to0xHexString(length = 5)
        )
      }
    }
  }

  @Test
  fun `should throw UInt64DecodeException when decoding invalid varints`() = runTest {
    // Make sure that byte 10 has its least significant bit cleared, because if it is, instead, set,
    // then a UInt64InvalidValueException is thrown instead.
    val arb =
      MalformedVarintByteArrayArb(10..20).map { sample ->
        sample.copy(
          byteArray =
            sample.byteArray.copyOf().also { it[9] = it[9].withLeastSignificantBitCleared() }
        )
      }

    checkAll(propTestConfig, arb) { testCase ->
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        put(QueryResultCodec.VALUE_STRUCT)
        putUInt32(1) // struct key count
        put(QueryResultCodec.VALUE_STRING_EMPTY)
        put(QueryResultCodec.VALUE_NUMBER_UINT64)
        put(testCase.byteArray)
      }
      assertDecodeThrows<UInt64DecodeException>(byteArray) {
        messageShouldContainWithNonAbuttingText("ybydmsykkp")
        messageShouldContainWithNonAbuttingTextIgnoringCase(
          "uint64 decode failed of 10 bytes: " + testCase.byteArray.to0xHexString(length = 10)
        )
      }
    }
  }

  @Test
  fun `should throw UInt64InvalidValueException when decoding invalid varints`() = runTest {
    // Make sure that byte 10 has its least significant bit set, because if it is, instead, clear,
    // then a UInt64DecodeException is thrown instead.
    val arb =
      MalformedVarintByteArrayArb(10..20).map { sample ->
        sample.copy(
          byteArray = sample.byteArray.copyOf().also { it[9] = it[9].withLeastSignificantBitSet() }
        )
      }

    checkAll(propTestConfig, arb) { testCase ->
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        put(QueryResultCodec.VALUE_STRUCT)
        putUInt32(1) // struct key count
        put(QueryResultCodec.VALUE_STRING_EMPTY)
        put(QueryResultCodec.VALUE_NUMBER_UINT64)
        put(testCase.byteArray)
      }
      assertDecodeThrows<UInt64InvalidValueException>(byteArray) {
        messageShouldContainWithNonAbuttingText("pypnp79waw")
        messageShouldContainRegexMatchIgnoringCase("invalid uint64 value decoded: -[0-9]+\\W")
        messageShouldContainWithNonAbuttingTextIgnoringCase(
          "decoded from 10 bytes: " + testCase.byteArray.to0xHexString(length = 10)
        )
      }
    }
  }

  private class ByteArraySample(private val _byteArray: ByteArray) {
    val arraySize: Int = _byteArray.size
    val byteArray: ByteArray
      get() = _byteArray.copyOf()

    fun to0xHexString(): String = _byteArray.to0xHexString()

    override fun toString(): String =
      "byte array: ${_byteArray.to0xHexString()} (${_byteArray.size} bytes))"
  }

  private data class UnknownValueTypeIndicatorByte(val byte: Byte)

  private companion object {

    @OptIn(ExperimentalKotest::class)
    val propTestConfig =
      PropTestConfig(
        iterations = 1000,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.33)
      )

    val valueTypeIndicatorBytes: Set<Byte> =
      setOf(
        QueryResultCodec.VALUE_NULL,
        QueryResultCodec.VALUE_KIND_NOT_SET,
        QueryResultCodec.VALUE_ENTITY,
        QueryResultCodec.VALUE_NUMBER_DOUBLE,
        QueryResultCodec.VALUE_NUMBER_POSITIVE_ZERO,
        QueryResultCodec.VALUE_NUMBER_NEGATIVE_ZERO,
        QueryResultCodec.VALUE_NUMBER_FIXED32,
        QueryResultCodec.VALUE_NUMBER_UINT32,
        QueryResultCodec.VALUE_NUMBER_SINT32,
        QueryResultCodec.VALUE_NUMBER_UINT64,
        QueryResultCodec.VALUE_NUMBER_SINT64,
        QueryResultCodec.VALUE_BOOL_TRUE,
        QueryResultCodec.VALUE_BOOL_FALSE,
        QueryResultCodec.VALUE_STRUCT,
        QueryResultCodec.VALUE_LIST,
        QueryResultCodec.VALUE_STRING_EMPTY,
        QueryResultCodec.VALUE_STRING_1BYTE,
        QueryResultCodec.VALUE_STRING_2BYTE,
        QueryResultCodec.VALUE_STRING_1CHAR,
        QueryResultCodec.VALUE_STRING_2CHAR,
        QueryResultCodec.VALUE_STRING_UTF8,
        QueryResultCodec.VALUE_STRING_UTF16,
      )

    val invalidValueTypeIndicatorBytes: List<UnknownValueTypeIndicatorByte> =
      (Byte.MIN_VALUE..Byte.MAX_VALUE)
        .map { it.toByte() }
        .filterNot { valueTypeIndicatorBytes.contains(it) }
        .map(::UnknownValueTypeIndicatorByte)

    val stringValueTypeIndicatorBytes: Set<Byte> =
      setOf(
        QueryResultCodec.VALUE_STRING_EMPTY,
        QueryResultCodec.VALUE_STRING_1BYTE,
        QueryResultCodec.VALUE_STRING_2BYTE,
        QueryResultCodec.VALUE_STRING_1CHAR,
        QueryResultCodec.VALUE_STRING_2CHAR,
        QueryResultCodec.VALUE_STRING_UTF8,
        QueryResultCodec.VALUE_STRING_UTF16,
      )

    val structValueTypeIndicatorBytes: Set<Byte> =
      setOf(QueryResultCodec.VALUE_STRUCT, QueryResultCodec.VALUE_ENTITY)

    val invalidValueTypeIndicatorByteEdgeCases: List<UnknownValueTypeIndicatorByte> =
      buildSet {
          add(Byte.MIN_VALUE)
          add(Byte.MAX_VALUE)
          add(0)
          add(-1)
          add(1)
          valueTypeIndicatorBytes.forEach { valueTypeIndicatorByte ->
            repeat(3) { offset ->
              add((valueTypeIndicatorByte + offset).toByte())
              add((valueTypeIndicatorByte - offset).toByte())
            }
          }
          removeAll(valueTypeIndicatorBytes)
        }
        .distinct()
        .map(::UnknownValueTypeIndicatorByte)

    fun Byte.withLeastSignificantBitSet(): Byte = (toInt() or 1).toByte()

    fun Byte.withLeastSignificantBitCleared(): Byte = (toInt() and 1.inv()).toByte()

    /**
     * Creates and returns an [Arb] that generates [Byte] values that are not one of the "value type
     * indicator" bytes (the VALUE_XXX constants) defined in [QueryResultCodec].
     */
    fun Arb.Companion.unknownValueTypeIndicatorByte(): Arb<UnknownValueTypeIndicatorByte> =
      of(invalidValueTypeIndicatorBytes).withEdgecases(invalidValueTypeIndicatorByteEdgeCases)

    fun Arb.Companion.varintContinuationByte(byte: Arb<Byte> = byte()): Arb<Byte> =
      byte.map { it.toVarintContinuationByte() }

    fun Arb.Companion.varintContinuationByteArray(
      lengthRange: IntRange,
      byte: Arb<Byte> = byte()
    ): Arb<ByteArraySample> =
      byteArray(Arb.int(lengthRange), varintContinuationByte(byte)).map(::ByteArraySample)

    fun Arb.Companion.truncatedUInt32ByteArray(byte: Arb<Byte> = byte()): Arb<ByteArraySample> =
      varintContinuationByteArray(0..4, byte)
  }
}
