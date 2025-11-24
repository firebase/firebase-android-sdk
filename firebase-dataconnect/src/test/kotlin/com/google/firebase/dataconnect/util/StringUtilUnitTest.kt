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

package com.google.firebase.dataconnect.util

import com.google.firebase.dataconnect.testutil.property.arbitrary.ByteBufferArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.OffsetLengthOutOfRangeArb
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import com.google.firebase.dataconnect.util.StringUtil.ellipsizeMiddle
import com.google.firebase.dataconnect.util.StringUtil.get0xHexString
import com.google.firebase.dataconnect.util.StringUtil.to0xHexString
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.char
import io.kotest.property.arbitrary.charArray
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.negativeInt
import io.kotest.property.checkAll
import kotlin.random.nextInt
import kotlinx.coroutines.test.runTest
import org.junit.Test

class StringUtilUnitTest {

  @Test
  fun `Int to0xHexString() should return the correct string`() = runTest {
    checkAll(propTestConfig, Arb.int()) { int ->
      val expected = "0x" + int.toUInt().toString(16).uppercase().padStart(8, '0')
      int.to0xHexString() shouldBe expected
    }
  }

  @Test
  fun `ByteArray to0xHexString() should return the correct string`() = runTest {
    checkAll(propTestConfig, Arb.byteArray(Arb.int(0..100), Arb.byte())) { byteArray ->
      val expected = "0x" + byteArray.toExpectedHexString()
      byteArray.to0xHexString() shouldBe expected
    }
  }

  @Test
  fun `ByteArray to0xHexString(include0xPrefix=true)`() = runTest {
    checkAll(propTestConfig, Arb.byteArray(Arb.int(0..100), Arb.byte())) { byteArray ->
      val expected = "0x" + byteArray.toExpectedHexString()
      byteArray.to0xHexString(include0xPrefix = true) shouldBe expected
    }
  }

  @Test
  fun `ByteArray to0xHexString(include0xPrefix=false)`() = runTest {
    checkAll(propTestConfig, Arb.byteArray(Arb.int(0..100), Arb.byte())) { byteArray ->
      val expected = byteArray.toExpectedHexString()
      byteArray.to0xHexString(include0xPrefix = false) shouldBe expected
    }
  }

  @Test
  fun `ByteArray to0xHexString(offset)`() = runTest {
    checkAll(propTestConfig, Arb.byteArray(Arb.int(0..100), Arb.byte())) { byteArray ->
      val offset = randomSource().random.nextInt(0..byteArray.size)
      val expected = "0x" + byteArray.sliceArray(offset until byteArray.size).toExpectedHexString()
      withClue("offset=$offset") { byteArray.to0xHexString(offset = offset) shouldBe expected }
    }
  }

  @Test
  fun `ByteArray to0xHexString(length)`() = runTest {
    checkAll(propTestConfig, Arb.byteArray(Arb.int(0..100), Arb.byte())) { byteArray ->
      val length = randomSource().random.nextInt(0..byteArray.size)
      val expected = "0x" + byteArray.sliceArray(0 until length).toExpectedHexString()
      withClue("length=$length") { byteArray.to0xHexString(length = length) shouldBe expected }
    }
  }

  @Test
  fun `ByteArray to0xHexString(offset, length)`() = runTest {
    checkAll(propTestConfig, Arb.byteArray(Arb.int(0..100), Arb.byte())) { byteArray ->
      val indices = List(2) { randomSource().random.nextInt(0..byteArray.size) }.sorted()
      val offset = indices[0]
      val length = indices[1] - offset
      val expected =
        "0x" + byteArray.sliceArray(offset until (offset + length)).toExpectedHexString()
      withClue("offset=$offset, length=$length") {
        byteArray.to0xHexString(offset = offset, length = length) shouldBe expected
      }
    }
  }

  @Test
  fun `ByteArray to0xHexString(offset=negative)`() = runTest {
    checkAll(propTestConfig, Arb.byteArray(Arb.int(0..100), Arb.byte()), Arb.negativeInt()) {
      byteArray,
      negativeOffset ->
      val exception =
        shouldThrow<IllegalArgumentException> { byteArray.to0xHexString(offset = negativeOffset) }
      assertSoftly {
        exception.message shouldContainWithNonAbuttingTextIgnoringCase
          "invalid offset: $negativeOffset"
        exception.message shouldContainWithNonAbuttingTextIgnoringCase
          "must be greater than or equal to zero"
        exception.message shouldContainWithNonAbuttingText "size=${byteArray.size}"
      }
    }
  }

  @Test
  fun `ByteArray to0xHexString(length=negative)`() = runTest {
    checkAll(propTestConfig, Arb.byteArray(Arb.int(0..100), Arb.byte()), Arb.negativeInt()) {
      byteArray,
      negativeLength ->
      val exception =
        shouldThrow<IllegalArgumentException> { byteArray.to0xHexString(length = negativeLength) }
      assertSoftly {
        exception.message shouldContainWithNonAbuttingTextIgnoringCase
          "invalid length: $negativeLength"
        exception.message shouldContainWithNonAbuttingTextIgnoringCase
          "must be greater than or equal to zero"
        exception.message shouldContainWithNonAbuttingText "offset=0"
        exception.message shouldContainWithNonAbuttingText "size=${byteArray.size}"
      }
    }
  }

  @Test
  fun `ByteArray to0xHexString(offset+length out of range)`() = runTest {
    checkAll(propTestConfig, Arb.byteArray(Arb.int(0..100), Arb.byte())) { byteArray ->
      val offsetLengthOutOfRangeArb = OffsetLengthOutOfRangeArb(byteArray.size)
      val (offset, length) = offsetLengthOutOfRangeArb.bind()

      withClue("offset=$offset, length=$length, byteArray.size=${byteArray.size}") {
        val exception =
          shouldThrow<IllegalArgumentException> {
            byteArray.to0xHexString(offset = offset, length = length)
          }
        assertSoftly {
          exception.message shouldContainWithNonAbuttingText "offset=$offset"
          exception.message shouldContainWithNonAbuttingText "length=$length"
          exception.message shouldContainWithNonAbuttingText "offset + length: ${offset+length}"
          exception.message shouldContainWithNonAbuttingText "size=${byteArray.size}"
        }
      }
    }
  }

  @Test
  fun `ByteBuffer get0xHexString() should return the correct string`() = runTest {
    checkAll(propTestConfig, ByteBufferArb(remaining = Arb.int(0..100))) { byteBufferInfo ->
      val expected = "0x" + byteBufferInfo.bytesCopy().toExpectedHexString()
      byteBufferInfo.byteBuffer.get0xHexString() shouldBe expected
    }
  }

  @Test
  fun `ByteBuffer get0xHexString(include0xPrefix=true)`() = runTest {
    checkAll(propTestConfig, ByteBufferArb(remaining = Arb.int(0..100))) { byteBufferInfo ->
      val expected = "0x" + byteBufferInfo.bytesCopy().toExpectedHexString()
      byteBufferInfo.byteBuffer.get0xHexString(include0xPrefix = true) shouldBe expected
    }
  }

  @Test
  fun `ByteBuffer get0xHexString(include0xPrefix=false)`() = runTest {
    checkAll(propTestConfig, ByteBufferArb(remaining = Arb.int(0..100))) { byteBufferInfo ->
      val expected = byteBufferInfo.bytesCopy().toExpectedHexString()
      byteBufferInfo.byteBuffer.get0xHexString(include0xPrefix = false) shouldBe expected
    }
  }

  @Test
  fun `ByteBuffer get0xHexString(offset)`() = runTest {
    checkAll(propTestConfig, ByteBufferArb(remaining = Arb.int(0..100))) { byteBufferInfo ->
      val offset = randomSource().random.nextInt(0..byteBufferInfo.remaining)
      val expected =
        "0x" +
          byteBufferInfo
            .bytesSliceArray(offset until byteBufferInfo.bytesSize)
            .toExpectedHexString()
      withClue("offset=$offset") {
        byteBufferInfo.byteBuffer.get0xHexString(offset = offset) shouldBe expected
      }
    }
  }

  @Test
  fun `ByteBuffer get0xHexString(length)`() = runTest {
    checkAll(propTestConfig, ByteBufferArb(remaining = Arb.int(0..100))) { byteBufferInfo ->
      val length = randomSource().random.nextInt(0..byteBufferInfo.remaining)
      val expected = "0x" + byteBufferInfo.bytesSliceArray(0 until length).toExpectedHexString()
      withClue("length=$length") {
        byteBufferInfo.byteBuffer.get0xHexString(length = length) shouldBe expected
      }
    }
  }

  @Test
  fun `ByteBuffer get0xHexString(offset, length)`() = runTest {
    checkAll(propTestConfig, ByteBufferArb(remaining = Arb.int(0..100))) { byteBufferInfo ->
      val indices = List(2) { randomSource().random.nextInt(0..byteBufferInfo.remaining) }.sorted()
      val offset = indices[0]
      val length = indices[1] - offset
      val expected =
        "0x" + byteBufferInfo.bytesSliceArray(offset until (offset + length)).toExpectedHexString()
      withClue("offset=$offset, length=$length") {
        byteBufferInfo.byteBuffer.get0xHexString(offset = offset, length = length) shouldBe expected
      }
    }
  }

  @Test
  fun `ByteBuffer get0xHexString(offset=negative)`() = runTest {
    checkAll(propTestConfig, ByteBufferArb(remaining = Arb.int(0..100)), Arb.negativeInt()) {
      byteBufferInfo,
      negativeOffset ->
      val exception =
        shouldThrow<IllegalArgumentException> {
          byteBufferInfo.byteBuffer.get0xHexString(offset = negativeOffset)
        }
      assertSoftly {
        exception.message shouldContainWithNonAbuttingTextIgnoringCase
          "invalid offset: $negativeOffset"
        exception.message shouldContainWithNonAbuttingTextIgnoringCase
          "must be greater than or equal to zero"
        exception.message shouldContainWithNonAbuttingText "remaining=${byteBufferInfo.remaining}"
      }
    }
  }

  @Test
  fun `ByteBuffer get0xHexString(length=negative)`() = runTest {
    checkAll(propTestConfig, ByteBufferArb(remaining = Arb.int(0..100)), Arb.negativeInt()) {
      byteBufferInfo,
      negativeLength ->
      val exception =
        shouldThrow<IllegalArgumentException> {
          byteBufferInfo.byteBuffer.get0xHexString(length = negativeLength)
        }
      assertSoftly {
        exception.message shouldContainWithNonAbuttingTextIgnoringCase
          "invalid length: $negativeLength"
        exception.message shouldContainWithNonAbuttingTextIgnoringCase
          "must be greater than or equal to zero"
        exception.message shouldContainWithNonAbuttingText "offset=0"
        exception.message shouldContainWithNonAbuttingText "remaining=${byteBufferInfo.remaining}"
      }
    }
  }

  @Test
  fun `ByteBuffer get0xHexString(offset+length out of range)`() = runTest {
    checkAll(propTestConfig, ByteBufferArb(remaining = Arb.int(0..100))) { byteBufferInfo ->
      val offsetLengthOutOfRangeArb = OffsetLengthOutOfRangeArb(byteBufferInfo.remaining)
      val (offset, length) = offsetLengthOutOfRangeArb.bind()

      withClue("offset=$offset, length=$length, byteArray.size=${byteBufferInfo.remaining}") {
        val exception =
          shouldThrow<IllegalArgumentException> {
            byteBufferInfo.byteBuffer.get0xHexString(offset = offset, length = length)
          }
        assertSoftly {
          exception.message shouldContainWithNonAbuttingText "offset=$offset"
          exception.message shouldContainWithNonAbuttingText "length=$length"
          exception.message shouldContainWithNonAbuttingText "offset + length: ${offset+length}"
          exception.message shouldContainWithNonAbuttingText "remaining=${byteBufferInfo.remaining}"
        }
      }
    }
  }

  data class EllipsizeMiddleTestCase(val string: String, val maxLength: Int) {
    override fun toString() =
      "${this::class.simpleName}(maxLength=$maxLength, " +
        "string.length=${string.length}, string=$string)"

    companion object {
      fun stringLengthLessThanOrEqualToMaxLengthArb(
        maxLength: Arb<Int>
      ): Arb<EllipsizeMiddleTestCase> = arb(maxLength) { 0..it }

      fun stringLengthGreaterThanMaxLengthArb(maxLength: Arb<Int>): Arb<EllipsizeMiddleTestCase> =
        arb(maxLength) { (it + 1)..((it + 1) * 3) }

      private inline fun arb(
        maxLength: Arb<Int>,
        crossinline toRange: (maxLength: Int) -> IntRange
      ): Arb<EllipsizeMiddleTestCase> =
        maxLength.flatMap { maxLength ->
          Arb.charArray(Arb.int(toRange(maxLength)), Arb.char()).map {
            EllipsizeMiddleTestCase(it.concatToString(), maxLength)
          }
        }
    }
  }

  @Test
  fun `String ellipsizeMiddle() should return unmodified string if string length is less than or equal to maxLength`() =
    runTest {
      val arb = EllipsizeMiddleTestCase.stringLengthLessThanOrEqualToMaxLengthArb(Arb.int(5..50))
      checkAll(propTestConfig, arb) { testCase ->
        testCase.string.ellipsizeMiddle(testCase.maxLength) shouldBe testCase.string
      }
    }

  @Test
  fun `String ellipsizeMiddle() should return the receiving object if string length is less than or equal to maxLength`() =
    runTest {
      val arb = EllipsizeMiddleTestCase.stringLengthLessThanOrEqualToMaxLengthArb(Arb.int(5..50))
      checkAll(propTestConfig, arb) { testCase ->
        testCase.string.ellipsizeMiddle(testCase.maxLength) shouldBeSameInstanceAs testCase.string
      }
    }

  @Test
  fun `String ellipsizeMiddle() should return a string with length maxLength`() = runTest {
    val arb = EllipsizeMiddleTestCase.stringLengthGreaterThanMaxLengthArb(Arb.int(5..50))
    checkAll(propTestConfig, arb) { testCase ->
      testCase.string.ellipsizeMiddle(testCase.maxLength).length shouldBe testCase.maxLength
    }
  }

  @Test
  fun `String ellipsizeMiddle() should return an ellipsized string`() = runTest {
    data class SubstringLength(val prefixLength: Int, val suffixLength: Int)

    val arb = EllipsizeMiddleTestCase.stringLengthGreaterThanMaxLengthArb(Arb.int(5..50))
    checkAll(propTestConfig, arb) { (string, maxLength) ->
      val ellipsizedString = string.ellipsizeMiddle(maxLength)

      val validLengths = sequence {
        val wantChars = maxLength - 3
        val wantCharsHalf = wantChars / 2
        if (wantChars % 2 == 0) {
          yield(SubstringLength(wantCharsHalf, wantCharsHalf))
        } else {
          yield(SubstringLength(wantCharsHalf, wantCharsHalf + 1))
          yield(SubstringLength(wantCharsHalf + 1, wantCharsHalf))
        }
      }
      val acceptableEllipsizedStrings =
        validLengths
          .map { (prefixLength, suffixLength) ->
            string.take(prefixLength) + "..." + string.takeLast(suffixLength)
          }
          .toList()
      acceptableEllipsizedStrings shouldContain ellipsizedString
    }
  }

  private companion object {

    @OptIn(ExperimentalKotest::class)
    val propTestConfig =
      PropTestConfig(
        iterations = 1000,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2)
      )

    fun ByteArray.toExpectedHexString(): String = buildString {
      this@toExpectedHexString.forEach {
        append(it.toUByte().toString(16).uppercase().padStart(2, '0'))
      }
    }
  }
}
