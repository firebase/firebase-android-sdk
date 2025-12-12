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
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.Companion.decode
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.DoubleEOFException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.EntityNotFoundException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.Fixed32IntEOFException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.MagicEOFException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.SInt32DecodeException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.SInt32EOFException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.SInt64DecodeException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.SInt64EOFException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.String1ByteEOFException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.String1CharEOFException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.String2ByteEOFException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.String2CharEOFException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.UInt32DecodeException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.UInt32EOFException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.UInt64DecodeException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.UInt64EOFException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.UInt64InvalidValueException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.UnexpectedValueTypeIndicatorByteException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.UnknownValueTypeIndicatorByteException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.Utf16EOFException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.Utf8EOFException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.Utf8IncorrectNumCharactersException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.ValueTypeIndicatorEOFException
import com.google.firebase.dataconnect.testutil.BuildByteArrayDSL
import com.google.firebase.dataconnect.testutil.buildByteArray
import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import com.google.firebase.dataconnect.util.ProtoUtil.toValueProto
import com.google.firebase.dataconnect.util.StringUtil.ellipsizeMiddle
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
import kotlin.random.nextInt
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
      val sampleByteArray = sample.byteArrayCopy()
      val byteArray = buildByteArray { put(sampleByteArray) }
      assertDecodeThrowsEOFException<MagicEOFException>(
        byteArray,
        errorUid = "xg5y5fm2vk",
        callerErrorId = "MagicEOF",
        whileText = "reading 4 bytes",
        gotBytes = sampleByteArray,
        expectedBytesText = null,
        fewerBytesThanExpected = 4 - sampleByteArray.size,
      )
    }
  }

  @Test
  fun `root struct value type indicator byte unknown should throw`() = runTest {
    checkAll(propTestConfig, UnknownValueTypeIndicatorByte.arb()) { unknownValueTypeIndicator ->
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        put(unknownValueTypeIndicator.byte)
      }
      assertDecodeThrowsUnknownValueTypeIndicatorByteException(
        byteArray,
        callerErrorId = "RootStructValueTypeIndicatorByteUnknown",
        unknownValueTypeIndicator = unknownValueTypeIndicator.byte,
      )
    }
  }

  @Test
  fun `root struct value type indicator byte unexpected should throw`() = runTest {
    val arb = Exhaustive.collection(ValueTypeIndicatorByte.all - ValueTypeIndicatorByte.rootStructs)
    checkAll(propTestConfig, arb) { nonRootStructValueTypeIndicatorByte ->
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        put(nonRootStructValueTypeIndicatorByte.byte)
      }
      assertDecodeThrowsUnexpectedValueTypeIndicatorByteException(
        byteArray,
        callerErrorId = "RootStructValueTypeIndicatorByteUnexpected",
        unexpectedValueTypeIndicator = nonRootStructValueTypeIndicatorByte.byte,
      )
    }
  }

  @Test
  fun `root struct value type indicator byte truncated should throw`() {
    val byteArray = buildByteArray { putInt(QueryResultCodec.QUERY_RESULT_MAGIC) }
    assertDecodeThrowsValueTypeIndicatorEOFException(
      byteArray,
      callerErrorId = "RootStructValueTypeIndicatorByteEOF",
    )
  }

  private fun makeByteArrayEndingWithStructKeyCount(structKeyCount: ByteArray): ByteArray =
    buildByteArray {
      putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
      put(QueryResultCodec.VALUE_STRUCT)
      put(structKeyCount)
    }

  @Test
  fun `struct key count invalid should throw`() = runTest {
    checkAll(propTestConfig, MalformedVarintByteArrayArb(5..10)) { invalidStructKeyCount ->
      val invalidStructKeyCountByteArray = invalidStructKeyCount.byteArrayCopy()
      val byteArray = makeByteArrayEndingWithStructKeyCount(invalidStructKeyCountByteArray)
      assertDecodeThrowsUInt32DecodeException(
        byteArray,
        callerErrorId = "StructKeyCountDecodeFailed",
        uint32ByteArray = invalidStructKeyCountByteArray,
      )
    }
  }

  @Test
  fun `struct key count truncated should throw`() = runTest {
    checkAll(propTestConfig, Arb.truncatedVarint32ByteArray()) { truncatedStructKeyCount ->
      val truncatedStructKeyCountByteArray = truncatedStructKeyCount.byteArrayCopy()
      val byteArray = makeByteArrayEndingWithStructKeyCount(truncatedStructKeyCountByteArray)
      assertDecodeThrowsUInt32EOFException(
        byteArray,
        callerErrorId = "StructKeyCountEOF",
        uint32ByteArray = truncatedStructKeyCountByteArray,
      )
    }
  }

  @Test
  fun `string 1 byte value truncated`() {
    val byteArray = makeByteArrayEndingWithValue { put(QueryResultCodec.VALUE_STRING_1BYTE) }
    assertDecodeThrowsEOFException<String1ByteEOFException>(
      byteArray,
      errorUid = "xg5y5fm2vk",
      callerErrorId = "String1ByteEOF",
      whileText = "reading 1 bytes",
      gotBytes = byteArrayOf(),
      expectedBytesText = null,
      fewerBytesThanExpected = 1,
    )
  }

  @Test
  fun `string 2 byte value truncated`() = runTest {
    checkAll(propTestConfig, Arb.byteArray(Arb.int(0..1), Arb.byte())) { truncated2ByteString ->
      val byteArray = makeByteArrayEndingWithValue {
        put(QueryResultCodec.VALUE_STRING_2BYTE)
        put(truncated2ByteString)
      }
      assertDecodeThrowsEOFException<String2ByteEOFException>(
        byteArray,
        errorUid = "xg5y5fm2vk",
        callerErrorId = "String2ByteEOF",
        whileText = "reading 2 bytes",
        gotBytes = truncated2ByteString,
        expectedBytesText = null,
        fewerBytesThanExpected = 2 - truncated2ByteString.size,
      )
    }
  }

  @Test
  fun `string 1 char value truncated`() = runTest {
    checkAll(propTestConfig, Arb.byteArray(Arb.int(0..1), Arb.byte())) { truncated1CharString ->
      val byteArray = makeByteArrayEndingWithValue {
        put(QueryResultCodec.VALUE_STRING_1CHAR)
        put(truncated1CharString)
      }
      assertDecodeThrowsEOFException<String1CharEOFException>(
        byteArray,
        errorUid = "xg5y5fm2vk",
        callerErrorId = "String1CharEOF",
        whileText = "reading 2 bytes",
        gotBytes = truncated1CharString,
        expectedBytesText = null,
        fewerBytesThanExpected = 2 - truncated1CharString.size,
      )
    }
  }

  @Test
  fun `string 2 char value truncated`() = runTest {
    checkAll(propTestConfig, Arb.byteArray(Arb.int(0..3), Arb.byte())) { truncated2CharString ->
      val byteArray = makeByteArrayEndingWithValue {
        put(QueryResultCodec.VALUE_STRING_2CHAR)
        put(truncated2CharString)
      }
      assertDecodeThrowsEOFException<String2CharEOFException>(
        byteArray,
        errorUid = "xg5y5fm2vk",
        callerErrorId = "String2CharEOF",
        whileText = "reading 4 bytes",
        gotBytes = truncated2CharString,
        expectedBytesText = null,
        fewerBytesThanExpected = 4 - truncated2CharString.size,
      )
    }
  }

  @Test
  fun `utf8 byte count invalid should throw`() = runTest {
    checkAll(propTestConfig, MalformedVarintByteArrayArb(5..10)) { invalidUtf8ByteCount ->
      val invalidUtf8ByteCountByteArray = invalidUtf8ByteCount.byteArrayCopy()
      val byteArray = makeByteArrayEndingWithValue {
        put(QueryResultCodec.VALUE_STRING_UTF8)
        put(invalidUtf8ByteCountByteArray)
      }
      assertDecodeThrowsUInt32DecodeException(
        byteArray,
        callerErrorId = "StringUtf8ByteCountDecodeFailed",
        uint32ByteArray = invalidUtf8ByteCountByteArray,
      )
    }
  }

  @Test
  fun `utf8 byte count truncated should throw`() = runTest {
    checkAll(propTestConfig, Arb.truncatedVarint32ByteArray()) { truncatedUtf8ByteCount ->
      val truncatedUtf8ByteCountByteArray = truncatedUtf8ByteCount.byteArrayCopy()
      val byteArray = makeByteArrayEndingWithValue {
        put(QueryResultCodec.VALUE_STRING_UTF8)
        put(truncatedUtf8ByteCountByteArray)
      }
      assertDecodeThrowsUInt32EOFException(
        byteArray,
        callerErrorId = "StringUtf8ByteCountEOF",
        uint32ByteArray = truncatedUtf8ByteCountByteArray,
      )
    }
  }

  @Test
  fun `utf8 char count invalid should throw`() = runTest {
    checkAll(propTestConfig, MalformedVarintByteArrayArb(5..10)) { invalidUtf8CharCount ->
      val invalidUtf8CharCountByteArray = invalidUtf8CharCount.byteArrayCopy()
      val byteArray = makeByteArrayEndingWithValue {
        put(QueryResultCodec.VALUE_STRING_UTF8)
        putUInt32(randomSource().random.nextInt(0..Int.MAX_VALUE)) // utf8ByteCount
        put(invalidUtf8CharCountByteArray)
      }
      assertDecodeThrowsUInt32DecodeException(
        byteArray,
        callerErrorId = "StringUtf8CharCountDecodeFailed",
        uint32ByteArray = invalidUtf8CharCountByteArray,
      )
    }
  }

  @Test
  fun `utf8 char count truncated should throw`() = runTest {
    checkAll(propTestConfig, Arb.truncatedVarint32ByteArray()) { truncatedUtf8CharCount ->
      val truncatedUtf8CharCountByteArray = truncatedUtf8CharCount.byteArrayCopy()
      val byteArray = makeByteArrayEndingWithValue {
        put(QueryResultCodec.VALUE_STRING_UTF8)
        putUInt32(randomSource().random.nextInt(0..Int.MAX_VALUE)) // utf8ByteCount
        put(truncatedUtf8CharCountByteArray)
      }
      assertDecodeThrowsUInt32EOFException(
        byteArray,
        callerErrorId = "StringUtf8CharCountEOF",
        uint32ByteArray = truncatedUtf8CharCountByteArray,
      )
    }
  }

  @Test
  fun `utf16 char count invalid should throw`() = runTest {
    checkAll(propTestConfig, MalformedVarintByteArrayArb(5..10)) { invalidUtf16CharCount ->
      val invalidUtf16CharCountByteArray = invalidUtf16CharCount.byteArrayCopy()
      val byteArray = makeByteArrayEndingWithValue {
        put(QueryResultCodec.VALUE_STRING_UTF16)
        put(invalidUtf16CharCountByteArray)
      }
      assertDecodeThrowsUInt32DecodeException(
        byteArray,
        callerErrorId = "StringUtf16CharCountDecodeFailed",
        uint32ByteArray = invalidUtf16CharCountByteArray,
      )
    }
  }

  @Test
  fun `utf16 char count truncated should throw`() = runTest {
    checkAll(propTestConfig, Arb.truncatedVarint32ByteArray()) { truncatedUtf16CharCount ->
      val truncatedUtf16CharCountByteArray = truncatedUtf16CharCount.byteArrayCopy()
      val byteArray = makeByteArrayEndingWithValue {
        put(QueryResultCodec.VALUE_STRING_UTF16)
        put(truncatedUtf16CharCountByteArray)
      }
      assertDecodeThrowsUInt32EOFException(
        byteArray,
        callerErrorId = "StringUtf16CharCountEOF",
        uint32ByteArray = truncatedUtf16CharCountByteArray,
      )
    }
  }

  private fun makeByteArrayEndingWithStringValueTypeIndicator(
    stringValueTypeIndicator: Byte
  ): ByteArray =
    makeByteArrayEndingWithStringValueTypeIndicator(byteArrayOf(stringValueTypeIndicator))

  private fun makeByteArrayEndingWithStringValueTypeIndicator(
    stringValueTypeIndicator: ByteArray
  ): ByteArray = buildByteArray {
    putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
    put(QueryResultCodec.VALUE_STRUCT)
    putUInt32(1) // struct key count
    put(stringValueTypeIndicator)
  }

  @Test
  fun `string value type indicator byte unknown should throw`() = runTest {
    checkAll(propTestConfig, UnknownValueTypeIndicatorByte.arb()) { unknownValueTypeIndicator ->
      val byteArray =
        makeByteArrayEndingWithStringValueTypeIndicator(unknownValueTypeIndicator.byte)
      assertDecodeThrowsUnknownValueTypeIndicatorByteException(
        byteArray,
        callerErrorId = "StringValueTypeIndicatorByteUnknown",
        unknownValueTypeIndicator = unknownValueTypeIndicator.byte,
      )
    }
  }

  @Test
  fun `string value type indicator byte unexpected should throw`() = runTest {
    val arb = Exhaustive.collection(ValueTypeIndicatorByte.all - ValueTypeIndicatorByte.strings)
    checkAll(propTestConfig, arb) { nonStringValueTypeIndicatorByte ->
      val byteArray =
        makeByteArrayEndingWithStringValueTypeIndicator(nonStringValueTypeIndicatorByte.byte)
      assertDecodeThrowsUnexpectedValueTypeIndicatorByteException(
        byteArray,
        callerErrorId = "StringValueTypeIndicatorByteUnexpected",
        unexpectedValueTypeIndicator = nonStringValueTypeIndicatorByte.byte,
      )
    }
  }

  @Test
  fun `string value type indicator byte truncated should throw`() {
    val byteArray = makeByteArrayEndingWithStringValueTypeIndicator(byteArrayOf())
    assertDecodeThrowsValueTypeIndicatorEOFException(
      byteArray,
      callerErrorId = "StringValueTypeIndicatorByteEOF",
    )
  }

  @Test
  fun `read value value type indicator byte unknown should throw`() = runTest {
    checkAll(propTestConfig, UnknownValueTypeIndicatorByte.arb()) { unknownValueTypeIndicator ->
      val byteArray = makeByteArrayEndingWithValue { put(unknownValueTypeIndicator.byte) }
      assertDecodeThrowsUnknownValueTypeIndicatorByteException(
        byteArray,
        callerErrorId = "ReadValueValueTypeIndicatorByteUnknown",
        unknownValueTypeIndicator = unknownValueTypeIndicator.byte,
      )
    }
  }

  @Test
  fun `read value value type indicator byte truncated should throw`() {
    val byteArray = makeByteArrayEndingWithValue {}
    assertDecodeThrowsValueTypeIndicatorEOFException(
      byteArray,
      callerErrorId = "ReadValueValueTypeIndicatorByteEOF",
    )
  }

  private fun makeByteArrayEndingWithEncodedEntityIdSize(
    encodedEntityIdSize: ByteArray
  ): ByteArray = buildByteArray {
    putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
    put(QueryResultCodec.VALUE_ENTITY)
    put(encodedEntityIdSize)
  }

  @Test
  fun `encoded entity id size invalid should throw`() = runTest {
    checkAll(propTestConfig, MalformedVarintByteArrayArb(5..10)) { invalidEncodedEntityIdSize ->
      val invalidEncodedEntityIdSizeByteArray = invalidEncodedEntityIdSize.byteArrayCopy()
      val byteArray =
        makeByteArrayEndingWithEncodedEntityIdSize(invalidEncodedEntityIdSizeByteArray)
      assertDecodeThrowsUInt32DecodeException(
        byteArray,
        callerErrorId = "EncodedEntityIdSizeDecodeFailed",
        uint32ByteArray = invalidEncodedEntityIdSizeByteArray,
      )
    }
  }

  @Test
  fun `encoded entity id size truncated should throw`() = runTest {
    checkAll(propTestConfig, Arb.truncatedVarint32ByteArray()) { truncatedEncodedEntityIdSize ->
      val truncatedEncodedEntityIdSizeByteArray = truncatedEncodedEntityIdSize.byteArrayCopy()
      val byteArray =
        makeByteArrayEndingWithEncodedEntityIdSize(truncatedEncodedEntityIdSizeByteArray)
      assertDecodeThrowsUInt32EOFException(
        byteArray,
        callerErrorId = "EncodedEntityIdSizeEOF",
        uint32ByteArray = truncatedEncodedEntityIdSizeByteArray,
      )
    }
  }

  @Test
  fun `encoded entity id truncated should throw`() = runTest {
    class EncodedEntityIdTruncatedTestCase(
      byteArray: ByteArray,
      val byteCountInflation: Int,
    ) {
      private val _byteArray = byteArray.copyOf()
      val inflatedByteCount = byteArray.size + byteCountInflation

      fun byteArrayCopy(): ByteArray = _byteArray.copyOf()

      override fun toString() =
        "${this::class.simpleName}(" +
          "${_byteArray.to0xHexString().ellipsizeMiddle(maxLength = 20)} " +
          "(${_byteArray.size} bytes), " +
          "byteCountInflation=$byteCountInflation, inflatedByteCount=$inflatedByteCount)"
    }
    val arb =
      Arb.bind(
        Arb.byteArray(Arb.int(0..16384), Arb.byte()),
        Arb.int(1..8192),
        ::EncodedEntityIdTruncatedTestCase
      )

    checkAll(@OptIn(ExperimentalKotest::class) propTestConfig.copy(iterations = 50), arb) { testCase
      ->
      val testCaseByteArray = testCase.byteArrayCopy()
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        put(QueryResultCodec.VALUE_ENTITY)
        putUInt32(testCase.inflatedByteCount)
        put(testCaseByteArray)
      }
      assertDecodeThrowsEOFException<ByteArrayEOFException>(
        byteArray,
        errorUid = "dnx886qwmk",
        callerErrorId = null,
        whileText = "reading byte array of length ${testCase.inflatedByteCount}",
        gotBytes = testCaseByteArray,
        expectedBytesText = null,
        fewerBytesThanExpected = testCase.byteCountInflation,
      )
    }
  }

  @Test
  fun `encoded entity id not found should throw`() = runTest {
    val arb = Arb.byteArray(Arb.int(0..50), Arb.byte()).map(::ByteArraySample)
    checkAll(propTestConfig, arb) { encodedEntityId ->
      val encodedEntityIdByteArray = encodedEntityId.byteArrayCopy()
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        put(QueryResultCodec.VALUE_ENTITY)
        putUInt32(encodedEntityIdByteArray.size)
        put(encodedEntityIdByteArray)
      }
      assertDecodeThrows<EntityNotFoundException>(byteArray) {
        messageShouldContainWithNonAbuttingText("p583k77y7r")
        messageShouldContainWithNonAbuttingText(encodedEntityIdByteArray.to0xHexString())
        messageShouldContainWithNonAbuttingTextIgnoringCase("could not find entity")
      }
    }
  }

  @Test
  fun `encoded entity id very long should be decodable`() = runTest {
    class VeryLongEncodedEntityIdTestCase(
      val veryLongEncodedEntityId: ByteArray,
      val randomValue: Int,
    ) {
      override fun toString(): String =
        "${this::class.simpleName}(" +
          veryLongEncodedEntityId.to0xHexString().ellipsizeMiddle(maxLength = 20) +
          " (${veryLongEncodedEntityId.size} bytes), " +
          "randomValue=$randomValue)"
    }
    val arb =
      Arb.bind(
        Arb.byteArray(Arb.int(2000..90000), Arb.byte()),
        Arb.int(),
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
  fun `utf8 insufficient bytes should throw`() = runTest {
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
      val byteArray = makeByteArrayEndingWithValue {
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
  fun `utf8 insufficient chars should throw`() = runTest {
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
      val byteArray = makeByteArrayEndingWithValue {
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
  fun `utf16 insufficient chars should throw`() = runTest {
    class InsufficientUtf16CharsTestCase(
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
      Arb.bind(Arb.string(0..20), Arb.positiveInt(max = 10), ::InsufficientUtf16CharsTestCase)

    checkAll(propTestConfig, arb) { testCase ->
      val byteArray = makeByteArrayEndingWithValue {
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
  fun `list of entities size invalid should throw`() = runTest {
    checkAll(propTestConfig, MalformedVarintByteArrayArb(5..10)) { invalidListSize ->
      val invalidListSizeByteArray = invalidListSize.byteArrayCopy()
      val byteArray = makeByteArrayEndingWithValue {
        put(QueryResultCodec.VALUE_LIST_OF_ENTITIES)
        put(invalidListSizeByteArray)
      }
      assertDecodeThrowsUInt32DecodeException(
        byteArray,
        callerErrorId = "ListOfEntitiesSizeDecodeFailed",
        uint32ByteArray = invalidListSizeByteArray,
      )
    }
  }

  @Test
  fun `list of entities size truncated should throw`() = runTest {
    checkAll(propTestConfig, Arb.truncatedVarint32ByteArray()) { truncatedListSize ->
      val truncatedListSizeByteArray = truncatedListSize.byteArrayCopy()
      val byteArray = makeByteArrayEndingWithValue {
        put(QueryResultCodec.VALUE_LIST_OF_ENTITIES)
        put(truncatedListSizeByteArray)
      }
      assertDecodeThrowsUInt32EOFException(
        byteArray,
        callerErrorId = "ListOfEntitiesSizeEOF",
        uint32ByteArray = truncatedListSizeByteArray,
      )
    }
  }

  @Test
  fun `list of non-entities size invalid should throw`() = runTest {
    checkAll(propTestConfig, MalformedVarintByteArrayArb(5..10)) { invalidListSize ->
      val invalidListSizeByteArray = invalidListSize.byteArrayCopy()
      val byteArray = makeByteArrayEndingWithValue {
        put(QueryResultCodec.VALUE_LIST)
        put(invalidListSizeByteArray)
      }
      assertDecodeThrowsUInt32DecodeException(
        byteArray,
        callerErrorId = "ListOfNonEntitiesSizeDecodeFailed",
        uint32ByteArray = invalidListSizeByteArray,
      )
    }
  }

  @Test
  fun `list of non-entities size truncated should throw`() = runTest {
    checkAll(propTestConfig, Arb.truncatedVarint32ByteArray()) { truncatedListSize ->
      val truncatedListSizeByteArray = truncatedListSize.byteArrayCopy()
      val byteArray = makeByteArrayEndingWithValue {
        put(QueryResultCodec.VALUE_LIST)
        put(truncatedListSizeByteArray)
      }
      assertDecodeThrowsUInt32EOFException(
        byteArray,
        callerErrorId = "ListOfNonEntitiesSizeEOF",
        uint32ByteArray = truncatedListSizeByteArray,
      )
    }
  }

  @Test
  fun `double value truncated should throw`() = runTest {
    checkAll(propTestConfig, Arb.byteArray(Arb.int(0..7), Arb.byte())) { truncatedDoubleValue ->
      val byteArray = makeByteArrayEndingWithValue {
        put(QueryResultCodec.VALUE_NUMBER_DOUBLE)
        put(truncatedDoubleValue)
      }
      assertDecodeThrowsEOFException<DoubleEOFException>(
        byteArray,
        errorUid = "xg5y5fm2vk",
        callerErrorId = "ReadDoubleValueEOF",
        whileText = "reading 8 bytes",
        gotBytes = truncatedDoubleValue,
        expectedBytesText = null,
        fewerBytesThanExpected = 8 - truncatedDoubleValue.size,
      )
    }
  }

  @Test
  fun `fixed32 value truncated should throw`() = runTest {
    checkAll(propTestConfig, Arb.byteArray(Arb.int(0..3), Arb.byte())) { truncatedFixed32Value ->
      val byteArray = makeByteArrayEndingWithValue {
        put(QueryResultCodec.VALUE_NUMBER_FIXED32)
        put(truncatedFixed32Value)
      }
      assertDecodeThrowsEOFException<Fixed32IntEOFException>(
        byteArray,
        errorUid = "xg5y5fm2vk",
        callerErrorId = "ReadFixed32IntValueEOF",
        whileText = "reading 4 bytes",
        gotBytes = truncatedFixed32Value,
        expectedBytesText = null,
        fewerBytesThanExpected = 4 - truncatedFixed32Value.size,
      )
    }
  }

  @Test
  fun `uint32 value invalid should throw`() = runTest {
    checkAll(propTestConfig, MalformedVarintByteArrayArb(5..10)) { invalidUInt32Value ->
      val invalidUInt32ValueByteArray = invalidUInt32Value.byteArrayCopy()
      val byteArray = makeByteArrayEndingWithValue {
        put(QueryResultCodec.VALUE_NUMBER_UINT32)
        put(invalidUInt32ValueByteArray)
      }
      assertDecodeThrowsUInt32DecodeException(
        byteArray,
        callerErrorId = "ReadUInt32ValueDecodeError",
        uint32ByteArray = invalidUInt32ValueByteArray,
      )
    }
  }

  @Test
  fun `uint32 value truncated should throw`() = runTest {
    checkAll(propTestConfig, Arb.truncatedVarint32ByteArray()) { truncatedUInt32Value ->
      val truncatedUInt32ValueByteArray = truncatedUInt32Value.byteArrayCopy()
      val byteArray = makeByteArrayEndingWithValue {
        put(QueryResultCodec.VALUE_NUMBER_UINT32)
        put(truncatedUInt32ValueByteArray)
      }
      assertDecodeThrowsUInt32EOFException(
        byteArray,
        callerErrorId = "ReadUInt32ValueEOF",
        uint32ByteArray = truncatedUInt32ValueByteArray,
      )
    }
  }

  @Test
  fun `uint64 value invalid with byte 10 LSB clear should throw`() = runTest {
    // Make sure that byte 10 has its least significant bit cleared, because if it is, instead, set,
    // then a UInt64InvalidValueException is thrown instead.
    val arb =
      MalformedVarintByteArrayArb(
        10..20,
        transformer = { it[9] = it[9].withLeastSignificantBitCleared() }
      )

    checkAll(propTestConfig, arb) { invalidUInt64Value ->
      val byteArray = makeByteArrayEndingWithValue {
        put(QueryResultCodec.VALUE_NUMBER_UINT64)
        put(invalidUInt64Value.byteArrayCopy())
      }
      assertDecodeThrows<UInt64DecodeException>(byteArray) {
        messageShouldContainWithNonAbuttingText("ybydmsykkp")
        messageShouldContainWithNonAbuttingText("decodeErrorId=ReadUInt64ValueDecodeError")
        messageShouldContainWithNonAbuttingTextIgnoringCase("uint64 decode failed of 10 bytes")
        messageShouldContainWithNonAbuttingTextIgnoringCase(
          invalidUInt64Value.byteArraySliceArray(0..9).to0xHexString()
        )
      }
    }
  }

  @Test
  fun `uint64 value invalid with byte 10 LSB set should throw`() = runTest {
    // Make sure that byte 10 has its least significant bit set, because if it is, instead, clear,
    // then a UInt64DecodeException is thrown instead.
    val arb =
      MalformedVarintByteArrayArb(
        10..20,
        transformer = { it[9] = it[9].withLeastSignificantBitSet() }
      )

    checkAll(propTestConfig, arb) { invalidUInt64Value ->
      val invalidUInt64ValueByteArray = invalidUInt64Value.byteArrayCopy()
      val byteArray = makeByteArrayEndingWithValue {
        put(QueryResultCodec.VALUE_NUMBER_UINT64)
        put(invalidUInt64ValueByteArray)
      }
      assertDecodeThrows<UInt64InvalidValueException>(byteArray) {
        messageShouldContainWithNonAbuttingText("pypnp79waw")
        messageShouldContainRegexMatchIgnoringCase("invalid uint64 value decoded: -[0-9]+\\W")
        messageShouldContainWithNonAbuttingTextIgnoringCase(
          "decoded from 10 bytes: " + invalidUInt64ValueByteArray.to0xHexString(length = 10)
        )
      }
    }
  }

  @Test
  fun `uint64 value truncated should throw`() = runTest {
    checkAll(propTestConfig, Arb.truncatedVarint64ByteArray()) { truncatedUInt64Value ->
      val truncatedUInt64ValueByteArray = truncatedUInt64Value.byteArrayCopy()
      val byteArray = makeByteArrayEndingWithValue {
        put(QueryResultCodec.VALUE_NUMBER_UINT64)
        put(truncatedUInt64ValueByteArray)
      }
      assertDecodeThrowsEOFException<UInt64EOFException>(
        byteArray,
        errorUid = "c439qmdmnk",
        callerErrorId = "ReadUInt64ValueEOF",
        whileText = "decoding uint64 value",
        gotBytes = truncatedUInt64ValueByteArray,
        expectedBytesText = "between 1 and 10",
        fewerBytesThanExpected = null,
      )
    }
  }

  @Test
  fun `sint32 value invalid should throw`() = runTest {
    checkAll(propTestConfig, MalformedVarintByteArrayArb(5..10)) { invalidSInt32Value ->
      val byteArray = makeByteArrayEndingWithValue {
        put(QueryResultCodec.VALUE_NUMBER_SINT32)
        put(invalidSInt32Value.byteArrayCopy())
      }
      assertDecodeThrows<SInt32DecodeException>(byteArray) {
        messageShouldContainWithNonAbuttingText("ybydmsykkp")
        messageShouldContainWithNonAbuttingText("decodeErrorId=ReadSInt32ValueDecodeError")
        messageShouldContainWithNonAbuttingTextIgnoringCase("sint32 decode failed of 5 bytes")
        messageShouldContainWithNonAbuttingTextIgnoringCase(
          invalidSInt32Value.byteArraySliceArray(0..4).to0xHexString()
        )
      }
    }
  }

  @Test
  fun `sint32 value truncated should throw`() = runTest {
    checkAll(propTestConfig, Arb.truncatedVarint32ByteArray()) { truncatedSInt32Value ->
      val truncatedSInt32ValueByteArray = truncatedSInt32Value.byteArrayCopy()
      val byteArray = makeByteArrayEndingWithValue {
        put(QueryResultCodec.VALUE_NUMBER_SINT32)
        put(truncatedSInt32ValueByteArray)
      }
      assertDecodeThrowsEOFException<SInt32EOFException>(
        byteArray,
        errorUid = "c439qmdmnk",
        callerErrorId = "ReadSInt32ValueEOF",
        whileText = "decoding sint32 value",
        gotBytes = truncatedSInt32ValueByteArray,
        expectedBytesText = "between 1 and 5",
        fewerBytesThanExpected = null,
      )
    }
  }

  @Test
  fun `sint64 value invalid with byte 10 LSB clear should throw`() = runTest {
    // Make sure that byte 10 has its least significant bit cleared, because if it is, instead, set,
    // then a value SInt64 value will be decoded.
    val arb =
      MalformedVarintByteArrayArb(
        10..20,
        transformer = { it[9] = it[9].withLeastSignificantBitCleared() }
      )

    checkAll(propTestConfig, arb) { invalidSInt64Value ->
      val byteArray = makeByteArrayEndingWithValue {
        put(QueryResultCodec.VALUE_NUMBER_SINT64)
        put(invalidSInt64Value.byteArrayCopy())
      }
      assertDecodeThrows<SInt64DecodeException>(byteArray) {
        messageShouldContainWithNonAbuttingText("ybydmsykkp")
        messageShouldContainWithNonAbuttingText("decodeErrorId=ReadSInt64ValueDecodeError")
        messageShouldContainWithNonAbuttingTextIgnoringCase("sint64 decode failed of 10 bytes")
        messageShouldContainWithNonAbuttingTextIgnoringCase(
          invalidSInt64Value.byteArraySliceArray(0..9).to0xHexString()
        )
      }
    }
  }

  @Test
  fun `sint64 value truncated should throw`() = runTest {
    checkAll(propTestConfig, Arb.truncatedVarint64ByteArray()) { truncatedSInt64Value ->
      val truncatedSInt64ValueByteArray = truncatedSInt64Value.byteArrayCopy()
      val byteArray = makeByteArrayEndingWithValue {
        put(QueryResultCodec.VALUE_NUMBER_SINT64)
        put(truncatedSInt64ValueByteArray)
      }
      assertDecodeThrowsEOFException<SInt64EOFException>(
        byteArray,
        errorUid = "c439qmdmnk",
        callerErrorId = "ReadSInt64ValueEOF",
        whileText = "decoding sint64 value",
        gotBytes = truncatedSInt64ValueByteArray,
        expectedBytesText = "between 1 and 10",
        fewerBytesThanExpected = null,
      )
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
    callerErrorId: String?,
    whileText: String,
    gotBytes: ByteArray,
    expectedBytesText: String?,
    fewerBytesThanExpected: Int?,
  ) =
    assertDecodeThrows<E>(byteArray) {
      messageShouldContainWithNonAbuttingText(errorUid)
      if (callerErrorId !== null) {
        messageShouldContainWithNonAbuttingText("eofErrorId=$callerErrorId")
      }
      messageShouldContainWithNonAbuttingTextIgnoringCase(
        "end of input reached prematurely while $whileText"
      )
      val gotBytesHexString =
        gotBytes.to0xHexString(include0xPrefix = false).ellipsizeMiddle(maxLength = 20)
      messageShouldContainWithNonAbuttingTextIgnoringCase(
        "got ${gotBytes.size} bytes (0x$gotBytesHexString)"
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

  private fun assertDecodeThrowsUInt32EOFException(
    byteArray: ByteArray,
    callerErrorId: String,
    uint32ByteArray: ByteArray,
  ) =
    assertDecodeThrowsEOFException<UInt32EOFException>(
      byteArray,
      errorUid = "c439qmdmnk",
      callerErrorId = callerErrorId,
      whileText = "decoding uint32 value",
      gotBytes = uint32ByteArray,
      expectedBytesText = "between 1 and 5",
      fewerBytesThanExpected = null,
    )

  private fun assertDecodeThrowsUInt32DecodeException(
    byteArray: ByteArray,
    callerErrorId: String,
    uint32ByteArray: ByteArray,
  ) =
    assertDecodeThrows<UInt32DecodeException>(byteArray) {
      messageShouldContainWithNonAbuttingText("ybydmsykkp")
      messageShouldContainWithNonAbuttingText("decodeErrorId=$callerErrorId")
      messageShouldContainWithNonAbuttingTextIgnoringCase("uint32 decode failed of 5 bytes")
      messageShouldContainWithNonAbuttingTextIgnoringCase(
        uint32ByteArray.sliceArray(0..4).to0xHexString()
      )
    }

  private fun assertDecodeThrowsUnknownValueTypeIndicatorByteException(
    byteArray: ByteArray,
    callerErrorId: String,
    unknownValueTypeIndicator: Byte,
  ) =
    assertDecodeThrows<UnknownValueTypeIndicatorByteException>(byteArray) {
      messageShouldContainWithNonAbuttingText("y6ppbg7ary")
      messageShouldContainWithNonAbuttingText("unknownErrorId=$callerErrorId")
      messageShouldContainWithNonAbuttingTextIgnoringCase(
        "read unknown value type indicator byte: $unknownValueTypeIndicator"
      )
    }

  private fun assertDecodeThrowsUnexpectedValueTypeIndicatorByteException(
    byteArray: ByteArray,
    callerErrorId: String,
    unexpectedValueTypeIndicator: Byte,
  ) =
    assertDecodeThrows<UnexpectedValueTypeIndicatorByteException>(byteArray) {
      messageShouldContainWithNonAbuttingText("hxtgz4ffem")
      messageShouldContainWithNonAbuttingText("unexpectedErrorId=$callerErrorId")
      messageShouldContainWithNonAbuttingTextIgnoringCase(
        "read unexpected value type indicator byte: $unexpectedValueTypeIndicator"
      )
    }

  private fun assertDecodeThrowsValueTypeIndicatorEOFException(
    byteArray: ByteArray,
    callerErrorId: String,
  ) =
    assertDecodeThrowsEOFException<ValueTypeIndicatorEOFException>(
      byteArray,
      errorUid = "xg5y5fm2vk",
      callerErrorId = callerErrorId,
      whileText = "reading 1 bytes",
      gotBytes = ByteArray(0),
      expectedBytesText = null,
      fewerBytesThanExpected = 1,
    )

  private fun makeByteArrayEndingWithValue(block: BuildByteArrayDSL.() -> Unit): ByteArray =
    buildByteArray {
      putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
      put(QueryResultCodec.VALUE_STRUCT)
      putUInt32(1) // struct key count
      put(QueryResultCodec.VALUE_STRING_EMPTY)
      block(this)
    }

  private class ByteArraySample(byteArray: ByteArray) {
    private val _byteArray = byteArray.copyOf()
    val byteArraySize: Int = byteArray.size

    fun byteArrayCopy(): ByteArray = _byteArray.copyOf()

    override fun toString(): String =
      "byte array: ${_byteArray.to0xHexString()} ($byteArraySize bytes))"
  }

  private data class UnknownValueTypeIndicatorByte(val byte: Byte) {
    companion object {
      fun arb(): Arb<UnknownValueTypeIndicatorByte> = Arb.of(all).withEdgecases(edgeCases)

      val all: Set<UnknownValueTypeIndicatorByte> = run {
        val validValueTypeIndicatorBytes = ValueTypeIndicatorByte.all.map { it.byte }
        (Byte.MIN_VALUE..Byte.MAX_VALUE)
          .map { it.toByte() }
          .filterNot { validValueTypeIndicatorBytes.contains(it) }
          .map(::UnknownValueTypeIndicatorByte)
          .toSet()
      }

      val edgeCases: List<UnknownValueTypeIndicatorByte> =
        buildSet {
            add(Byte.MIN_VALUE)
            add(Byte.MAX_VALUE)
            add(0)
            add(-1)
            add(1)
            ValueTypeIndicatorByte.all.forEach { valueTypeIndicatorByte ->
              repeat(3) { offset ->
                add((valueTypeIndicatorByte.byte + offset).toByte())
                add((valueTypeIndicatorByte.byte - offset).toByte())
              }
            }
            ValueTypeIndicatorByte.all.forEach { remove(it.byte) }
          }
          .map(::UnknownValueTypeIndicatorByte)
    }
  }

  private data class ValueTypeIndicatorByte(val byte: Byte, val name: String) {
    companion object {
      val NULL = ValueTypeIndicatorByte(QueryResultCodec.VALUE_NULL, "VALUE_NULL")
      val KIND_NOT_SET =
        ValueTypeIndicatorByte(QueryResultCodec.VALUE_KIND_NOT_SET, "VALUE_KIND_NOT_SET")
      val ENTITY = ValueTypeIndicatorByte(QueryResultCodec.VALUE_ENTITY, "VALUE_ENTITY")
      val NUMBER_DOUBLE =
        ValueTypeIndicatorByte(QueryResultCodec.VALUE_NUMBER_DOUBLE, "VALUE_NUMBER_DOUBLE")
      val NUMBER_POSITIVE_ZERO =
        ValueTypeIndicatorByte(
          QueryResultCodec.VALUE_NUMBER_POSITIVE_ZERO,
          "VALUE_NUMBER_POSITIVE_ZERO"
        )
      val NUMBER_NEGATIVE_ZERO =
        ValueTypeIndicatorByte(
          QueryResultCodec.VALUE_NUMBER_NEGATIVE_ZERO,
          "VALUE_NUMBER_NEGATIVE_ZERO"
        )
      val NUMBER_FIXED32 =
        ValueTypeIndicatorByte(QueryResultCodec.VALUE_NUMBER_FIXED32, "VALUE_NUMBER_FIXED32")
      val NUMBER_UINT32 =
        ValueTypeIndicatorByte(QueryResultCodec.VALUE_NUMBER_UINT32, "VALUE_NUMBER_UINT32")
      val NUMBER_SINT32 =
        ValueTypeIndicatorByte(QueryResultCodec.VALUE_NUMBER_SINT32, "VALUE_NUMBER_SINT32")
      val NUMBER_UINT64 =
        ValueTypeIndicatorByte(QueryResultCodec.VALUE_NUMBER_UINT64, "VALUE_NUMBER_UINT64")
      val NUMBER_SINT64 =
        ValueTypeIndicatorByte(QueryResultCodec.VALUE_NUMBER_SINT64, "VALUE_NUMBER_SINT64")
      val BOOL_TRUE = ValueTypeIndicatorByte(QueryResultCodec.VALUE_BOOL_TRUE, "VALUE_BOOL_TRUE")
      val BOOL_FALSE = ValueTypeIndicatorByte(QueryResultCodec.VALUE_BOOL_FALSE, "VALUE_BOOL_FALSE")
      val STRUCT = ValueTypeIndicatorByte(QueryResultCodec.VALUE_STRUCT, "VALUE_STRUCT")
      val LIST = ValueTypeIndicatorByte(QueryResultCodec.VALUE_LIST, "VALUE_LIST")
      val LIST_OF_ENTITIES =
        ValueTypeIndicatorByte(QueryResultCodec.VALUE_LIST_OF_ENTITIES, "VALUE_LIST_OF_ENTITIES")
      val STRING_EMPTY =
        ValueTypeIndicatorByte(QueryResultCodec.VALUE_STRING_EMPTY, "VALUE_STRING_EMPTY")
      val STRING_1BYTE =
        ValueTypeIndicatorByte(QueryResultCodec.VALUE_STRING_1BYTE, "VALUE_STRING_1BYTE")
      val STRING_2BYTE =
        ValueTypeIndicatorByte(QueryResultCodec.VALUE_STRING_2BYTE, "VALUE_STRING_2BYTE")
      val STRING_1CHAR =
        ValueTypeIndicatorByte(QueryResultCodec.VALUE_STRING_1CHAR, "VALUE_STRING_1CHAR")
      val STRING_2CHAR =
        ValueTypeIndicatorByte(QueryResultCodec.VALUE_STRING_2CHAR, "VALUE_STRING_2CHAR")
      val STRING_UTF8 =
        ValueTypeIndicatorByte(QueryResultCodec.VALUE_STRING_UTF8, "VALUE_STRING_UTF8")
      val STRING_UTF16 =
        ValueTypeIndicatorByte(QueryResultCodec.VALUE_STRING_UTF16, "VALUE_STRING_UTF16")

      val all: Set<ValueTypeIndicatorByte> =
        setOf(
          NULL,
          KIND_NOT_SET,
          ENTITY,
          NUMBER_DOUBLE,
          NUMBER_POSITIVE_ZERO,
          NUMBER_NEGATIVE_ZERO,
          NUMBER_FIXED32,
          NUMBER_UINT32,
          NUMBER_SINT32,
          NUMBER_UINT64,
          NUMBER_SINT64,
          BOOL_TRUE,
          BOOL_FALSE,
          STRUCT,
          LIST,
          LIST_OF_ENTITIES,
          STRING_EMPTY,
          STRING_1BYTE,
          STRING_2BYTE,
          STRING_1CHAR,
          STRING_2CHAR,
          STRING_UTF8,
          STRING_UTF16,
        )

      val strings: Set<ValueTypeIndicatorByte> =
        setOf(
          STRING_EMPTY,
          STRING_1BYTE,
          STRING_2BYTE,
          STRING_1CHAR,
          STRING_2CHAR,
          STRING_UTF8,
          STRING_UTF16,
        )

      val rootStructs: Set<ValueTypeIndicatorByte> = setOf(STRUCT, ENTITY)
    }
  }

  private companion object {

    @OptIn(ExperimentalKotest::class)
    val propTestConfig =
      PropTestConfig(
        iterations = 1000,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.33)
      )

    fun Byte.withLeastSignificantBitSet(): Byte = (toInt() or 1).toByte()

    fun Byte.withLeastSignificantBitCleared(): Byte = (toInt() and 1.inv()).toByte()

    fun Arb.Companion.varintContinuationByte(byte: Arb<Byte> = byte()): Arb<Byte> =
      byte.map { it.toVarintContinuationByte() }

    fun Arb.Companion.varintContinuationByteArray(
      lengthRange: IntRange,
      byte: Arb<Byte> = byte()
    ): Arb<ByteArraySample> =
      byteArray(Arb.int(lengthRange), varintContinuationByte(byte)).map(::ByteArraySample)

    fun Arb.Companion.truncatedVarint32ByteArray(byte: Arb<Byte> = byte()): Arb<ByteArraySample> =
      varintContinuationByteArray(0..4, byte)

    fun Arb.Companion.truncatedVarint64ByteArray(byte: Arb<Byte> = byte()): Arb<ByteArraySample> =
      varintContinuationByteArray(0..9, byte)
  }
}
