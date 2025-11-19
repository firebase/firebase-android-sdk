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
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.EntityNotFoundException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.NegativeEntityIdSizeException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.NegativeListSizeException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.NegativeStringByteCountException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.NegativeStringCharCountException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.NegativeStructKeyCountException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.UnknownStringValueTypeIndicatorByteException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.UnknownStructValueTypeIndicatorByteException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.UnknownValueTypeIndicatorByteException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.Utf16EOFException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.Utf8EOFException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.Utf8IncorrectNumCharactersException
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
import io.kotest.property.arbitrary.negativeInt
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.pair
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
  fun `decode() should throw BadMagicException`() = runTest {
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
  fun `decode() should throw UnknownStructValueTypeIndicatorByteException`() = runTest {
    data class NonStructDiscriminator(val value: Byte)
    val arb =
      Exhaustive.collection(valueDiscriminatorBytes - structDiscriminatorBytes)
        .map(::NonStructDiscriminator)

    checkAll(propTestConfig, arb) { nonStructDiscriminator ->
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        put(nonStructDiscriminator.value)
      }
      assertDecodeThrows<UnknownStructValueTypeIndicatorByteException>(byteArray) {
        messageShouldContainWithNonAbuttingText("s8b9jqegdy")
        messageShouldContainWithNonAbuttingText(nonStructDiscriminator.value.toString())
        messageShouldContainWithNonAbuttingText("non-struct kind case byte")
      }
    }
  }
  @Test
  fun `decode() should throw NegativeStructKeyCountException`() = runTest {
    data class NegativeStructKeyCount(val value: Int)
    val arb = Arb.negativeInt().map(::NegativeStructKeyCount)

    checkAll(propTestConfig, arb) { negativeStructKeyCount ->
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        put(QueryResultCodec.VALUE_STRUCT)
        putUInt32(negativeStructKeyCount.value)
      }
      assertDecodeThrows<NegativeStructKeyCountException>(byteArray) {
        messageShouldContainWithNonAbuttingText("y9253xj96g")
        messageShouldContainWithNonAbuttingText(negativeStructKeyCount.value.toString())
        messageShouldContainWithNonAbuttingTextIgnoringCase("struct key count")
        messageShouldContainWithNonAbuttingTextIgnoringCase("greater than or equal to zero")
      }
    }
  }

  @Test
  fun `decode() should throw NegativeStringByteCountException for utf8`() = runTest {
    data class NegativeStringByteCountTestCase(
      val structKeyCount: Int,
      val negativeStringByteCount: Int
    )
    val arb = Arb.bind(Arb.positiveInt(), Arb.negativeInt(), ::NegativeStringByteCountTestCase)

    checkAll(propTestConfig, arb) { testCase ->
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        put(QueryResultCodec.VALUE_STRUCT)
        putUInt32(testCase.structKeyCount)
        put(QueryResultCodec.VALUE_STRING_UTF8)
        putUInt32(testCase.negativeStringByteCount)
      }
      assertDecodeThrows<NegativeStringByteCountException>(byteArray) {
        messageShouldContainWithNonAbuttingText("a9kma55y7m")
        messageShouldContainWithNonAbuttingText(testCase.negativeStringByteCount.toString())
        messageShouldContainWithNonAbuttingTextIgnoringCase("string byte count")
        messageShouldContainWithNonAbuttingTextIgnoringCase("greater than or equal to zero")
      }
    }
  }

  @Test
  fun `decode() should throw NegativeStringCharCountException for utf8`() = runTest {
    data class NegativeStringCharCountTestCase(
      val structKeyCount: Int,
      val stringByteCount: Int,
      val negativeStringCharCount: Int
    )
    val arb =
      Arb.bind(
        Arb.positiveInt(),
        Arb.positiveInt(),
        Arb.negativeInt(),
        ::NegativeStringCharCountTestCase
      )

    checkAll(propTestConfig, arb) { testCase ->
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        put(QueryResultCodec.VALUE_STRUCT)
        putUInt32(testCase.structKeyCount)
        put(QueryResultCodec.VALUE_STRING_UTF8)
        putUInt32(testCase.stringByteCount)
        putUInt32(testCase.negativeStringCharCount)
      }
      assertDecodeThrows<NegativeStringCharCountException>(byteArray) {
        messageShouldContainWithNonAbuttingText("gwybfam237")
        messageShouldContainWithNonAbuttingText(testCase.negativeStringCharCount.toString())
        messageShouldContainWithNonAbuttingTextIgnoringCase("string char count")
        messageShouldContainWithNonAbuttingTextIgnoringCase("greater than or equal to zero")
      }
    }
  }

  @Test
  fun `decode() should throw NegativeStringCharCountException for utf16`() = runTest {
    data class NegativeStringCharCountTestCase(
      val structKeyCount: Int,
      val negativeStringCharCount: Int
    )
    val arb = Arb.bind(Arb.positiveInt(), Arb.negativeInt(), ::NegativeStringCharCountTestCase)
    checkAll(propTestConfig, arb) { testCase ->
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        put(QueryResultCodec.VALUE_STRUCT)
        putUInt32(testCase.structKeyCount)
        put(QueryResultCodec.VALUE_STRING_UTF16)
        putUInt32(testCase.negativeStringCharCount)
      }
      assertDecodeThrows<NegativeStringCharCountException>(byteArray) {
        messageShouldContainWithNonAbuttingText("gwybfam237")
        messageShouldContainWithNonAbuttingText(testCase.negativeStringCharCount.toString())
        messageShouldContainWithNonAbuttingTextIgnoringCase("string char count")
        messageShouldContainWithNonAbuttingTextIgnoringCase("greater than or equal to zero")
      }
    }
  }

  @Test
  fun `decode() should throw UnknownStringValueTypeIndicatorByteException`() = runTest {
    data class NonStringDiscriminator(val value: Byte)
    data class StructKeyCount(val value: Int)
    val arb =
      Exhaustive.collection(valueDiscriminatorBytes - stringDiscriminatorBytes)
        .map(::NonStringDiscriminator)
    val structKeyCountArb = Arb.positiveInt().map(::StructKeyCount)

    checkAll(propTestConfig, arb, structKeyCountArb) { nonStringDiscriminator, structKeyCount ->
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        put(QueryResultCodec.VALUE_STRUCT)
        putUInt32(structKeyCount.value)
        put(nonStringDiscriminator.value)
      }
      assertDecodeThrows<UnknownStringValueTypeIndicatorByteException>(byteArray) {
        messageShouldContainWithNonAbuttingText("hfvxx849cv")
        messageShouldContainWithNonAbuttingText(nonStringDiscriminator.value.toString())
        messageShouldContainWithNonAbuttingTextIgnoringCase("non-string discriminator byte")
      }
    }
  }

  @Test
  fun `decode() should throw UnknownValueTypeIndicatorByteException`() = runTest {
    data class InvalidDiscriminator(val value: Byte)
    data class StructKeyCount(val value: Int)
    val arb = Arb.invalidDiscriminatorByte().map(::InvalidDiscriminator)
    val structKeyCountArb = Arb.positiveInt().map(::StructKeyCount)

    checkAll(propTestConfig, arb, structKeyCountArb) { invalidDiscriminator, structKeyCount ->
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        put(QueryResultCodec.VALUE_STRUCT)
        putUInt32(structKeyCount.value)
        put(QueryResultCodec.VALUE_STRING_EMPTY)
        put(invalidDiscriminator.value)
      }
      assertDecodeThrows<UnknownValueTypeIndicatorByteException>(byteArray) {
        messageShouldContainWithNonAbuttingText("pmkb3sc2mn")
        messageShouldContainWithNonAbuttingText(invalidDiscriminator.value.toString())
        messageShouldContainWithNonAbuttingTextIgnoringCase("unknown discriminator byte")
      }
    }
  }

  @Test
  fun `decode() should throw NegativeEntityIdSizeException`() = runTest {
    data class NegativeEntityIdSize(val value: Int)
    val arb = Arb.negativeInt().map(::NegativeEntityIdSize)

    checkAll(propTestConfig, arb) { negativeEntityIdSize ->
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        put(QueryResultCodec.VALUE_ENTITY)
        putUInt32(negativeEntityIdSize.value)
      }
      assertDecodeThrows<NegativeEntityIdSizeException>(byteArray) {
        messageShouldContainWithNonAbuttingText("agvqmbgknh")
        messageShouldContainWithNonAbuttingText(negativeEntityIdSize.value.toString())
        messageShouldContainWithNonAbuttingTextIgnoringCase("entity id size")
        messageShouldContainWithNonAbuttingTextIgnoringCase("greater than or equal to zero")
      }
    }
  }

  @Test
  fun `decode() should throw EntityNotFoundException`() = runTest {
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
  fun `decode() should be able to decode very long entity IDs`() = runTest {
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
  fun `decode() should throw Utf8EOFException with 'insufficient bytes' message for utf8`() =
    runTest {
      class InsufficientUtf8BytesTestCase(
        val string: String,
        val structKeyCount: Int,
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
            "structKeyCount=$structKeyCount, " +
            "byteCountInflation=$byteCountInflation, charCountInflation=$charCountInflation, " +
            "inflatedCharCount=${inflatedCharCount}, inflatedByteCount=${inflatedByteCount})"
      }
      val arb =
        Arb.bind(
          Arb.string(0..20),
          Arb.positiveInt(),
          Arb.positiveInt(100),
          Arb.positiveInt(100),
          ::InsufficientUtf8BytesTestCase,
        )

      checkAll(propTestConfig, arb) { testCase ->
        val byteArray = buildByteArray {
          putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
          put(QueryResultCodec.VALUE_STRUCT)
          putUInt32(testCase.structKeyCount)
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
  fun `decode() should throw Utf8IncorrectNumCharactersException with 'insufficient chars' message for utf8`() =
    runTest {
      class IncorrectUtf8NumCharactersTestCase(
        val string: String,
        val structKeyCount: Int,
        val charCountInflation: Int,
      ) {
        val inflatedCharCount = string.length + charCountInflation
        val stringUtf8Bytes = string.encodeToByteArray()
        override fun toString(): String =
          "${this::class.simpleName}(" +
            "string=$string (charCount=${string.length}), " +
            "stringUtf8Bytes=${stringUtf8Bytes.to0xHexString()} " +
            "(byteCount=${stringUtf8Bytes.size}), " +
            "structKeyCount=$structKeyCount, " +
            "charCountInflation=$charCountInflation, inflatedCharCount=${inflatedCharCount})"
      }
      val arb =
        Arb.pair(Arb.string(0..20), Arb.positiveInt()).flatMap { (string, structKeyCount) ->
          val charCountInflationValues = (-10..10).filter { it != 0 && it >= -(string.length) }
          Arb.of(charCountInflationValues).map { charCountInflation ->
            IncorrectUtf8NumCharactersTestCase(string, structKeyCount, charCountInflation)
          }
        }

      checkAll(propTestConfig, arb) { testCase ->
        val byteArray = buildByteArray {
          putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
          put(QueryResultCodec.VALUE_STRUCT)
          putUInt32(testCase.structKeyCount)
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
  fun `decode() should throw Utf16EOFException with 'insufficient chars' message for utf16`() =
    runTest {
      class InsufficientUtf16BytesTestCase(
        val string: String,
        val structKeyCount: Int,
        val charCountInflation: Int,
      ) {
        val inflatedCharCount = string.length + charCountInflation
        override fun toString(): String =
          "${this::class.simpleName}(" +
            "string=$string " +
            "(charCount=${string.length}, utf16ByteCount=${string.length*2}), " +
            "structKeyCount=$structKeyCount, " +
            "charCountInflation=$charCountInflation, inflatedCharCount=${inflatedCharCount})"
      }
      val arb =
        Arb.bind(
          Arb.string(0..20),
          Arb.positiveInt(),
          Arb.positiveInt(max = 10),
          ::InsufficientUtf16BytesTestCase
        )

      checkAll(propTestConfig, arb) { testCase ->
        val byteArray = buildByteArray {
          putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
          put(QueryResultCodec.VALUE_STRUCT)
          putUInt32(testCase.structKeyCount)
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
  fun `decode() should throw NegativeListSizeException`() = runTest {
    data class NegativeListSizeTestCase(val negativeListSize: Int, val structKeyCount: Int)
    val arb = Arb.bind(Arb.negativeInt(), Arb.positiveInt(), ::NegativeListSizeTestCase)

    checkAll(propTestConfig, arb) { testCase ->
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        put(QueryResultCodec.VALUE_STRUCT)
        putUInt32(testCase.structKeyCount)
        put(QueryResultCodec.VALUE_STRING_EMPTY)
        put(QueryResultCodec.VALUE_LIST)
        putUInt32(testCase.negativeListSize)
      }
      assertDecodeThrows<NegativeListSizeException>(byteArray) {
        messageShouldContainWithNonAbuttingText("yfvpf9pwt8")
        messageShouldContainWithNonAbuttingTextIgnoringCase(
          "read list size ${testCase.negativeListSize}"
        )
        messageShouldContainWithNonAbuttingTextIgnoringCase("greater than or equal to zero")
      }
    }
  }

  @Test
  fun `decode() should throw ByteArrayEOFException`() = runTest {
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

  private companion object {

    @OptIn(ExperimentalKotest::class)
    val propTestConfig =
      PropTestConfig(
        iterations = 1000,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.33)
      )

    val valueDiscriminatorBytes: Set<Byte> =
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

    val invalidValueDiscriminatorBytes: List<Byte> =
      (Byte.MIN_VALUE..Byte.MAX_VALUE)
        .map { it.toByte() }
        .filterNot { valueDiscriminatorBytes.contains(it) }

    val stringDiscriminatorBytes: Set<Byte> =
      setOf(
        QueryResultCodec.VALUE_STRING_EMPTY,
        QueryResultCodec.VALUE_STRING_1BYTE,
        QueryResultCodec.VALUE_STRING_2BYTE,
        QueryResultCodec.VALUE_STRING_1CHAR,
        QueryResultCodec.VALUE_STRING_2CHAR,
        QueryResultCodec.VALUE_STRING_UTF8,
        QueryResultCodec.VALUE_STRING_UTF16,
      )

    val structDiscriminatorBytes: Set<Byte> =
      setOf(QueryResultCodec.VALUE_STRUCT, QueryResultCodec.VALUE_ENTITY)

    val invalidValueDiscriminatorByteEdgeCases: List<Byte> =
      buildSet {
          add(Byte.MIN_VALUE)
          add(Byte.MAX_VALUE)
          add(0)
          add(-1)
          add(1)
          valueDiscriminatorBytes.forEach { discriminator ->
            repeat(3) { offset ->
              add((discriminator + offset).toByte())
              add((discriminator - offset).toByte())
            }
          }
          removeAll(valueDiscriminatorBytes)
        }
        .distinct()

    /**
     * Creates and returns an [Arb] that generates [Byte] values that are not one of the "value
     * discriminator" bytes (the VALUE_XXX constants) defined in [QueryResultCodec].
     */
    fun Arb.Companion.invalidDiscriminatorByte(): Arb<Byte> =
      of(invalidValueDiscriminatorBytes).withEdgecases(invalidValueDiscriminatorByteEdgeCases)
  }
}
