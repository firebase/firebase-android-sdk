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

package com.google.firebase.dataconnect.testutil.property.arbitrary

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.intRange
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlin.random.nextInt
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ByteBufferArbUnitTest {

  @Test
  fun `should use the given byte arb`() = runTest {
    checkAll(propTestConfig, ByteArbInfo.arb()) { byteArbInfo ->
      val arb = ByteBufferArb(byte = byteArbInfo.arb)

      val sample = arb.bind()

      val byteArray = ByteArray(sample.byteBuffer.remaining())
      sample.byteBuffer.get(byteArray)
      byteArray.elementsShouldBeInRange(byteArbInfo.min, byteArbInfo.max)
    }
  }

  @Test
  fun `should use the given remaining arb`() = runTest {
    checkAll(propTestConfig, IntArbInfo.arb(Arb.int(0..100))) { intArbInfo ->
      val arb = ByteBufferArb(remaining = intArbInfo.arb)

      val sample = arb.bind()

      sample.byteBuffer.remaining() shouldBeInRange intArbInfo.range
    }
  }

  @Test
  fun `should use the given headerSize arb`() = runTest {
    checkAll(propTestConfig, IntArbInfo.arb(Arb.int(0..100))) { intArbInfo ->
      val arb = ByteBufferArb(headerSize = intArbInfo.arb)

      val sample = arb.bind()

      sample.byteBuffer.position() shouldBeInRange intArbInfo.range
    }
  }

  @Test
  fun `should use the given trailerSize arb`() = runTest {
    checkAll(propTestConfig, IntArbInfo.arb(Arb.int(0..100))) { intArbInfo ->
      val arb = ByteBufferArb(trailerSize = intArbInfo.arb)

      val sample = arb.bind()

      val trailerSize = sample.byteBuffer.run { capacity() - limit() }
      trailerSize shouldBeInRange intArbInfo.range
    }
  }

  @Test
  fun `should use the given bufferType arb`() = runTest {
    checkAll(propTestConfig, Arb.enum<ByteBufferArb.BufferType>()) { bufferType ->
      val arb = ByteBufferArb(bufferType = Arb.constant(bufferType))

      val sample = arb.bind()

      sample.bufferType shouldBe bufferType
    }
  }

  @Test
  fun `should use the given arbs respectively`() = runTest {
    data class RespectiveArbsTestCase(
      val byte: ByteArbInfo,
      val remaining: IntArbInfo,
      val headerSize: IntArbInfo,
      val trailerSize: IntArbInfo,
    )
    val arb =
      Arb.bind(ByteArbInfo.arb(), Arb.threeValues(IntArbInfo.arb(Arb.int(0..100)))) {
        byteArbInfo,
        (remainingArbInfo, headerSizeArbInfo, trailerSizeArbInfo) ->
        RespectiveArbsTestCase(byteArbInfo, remainingArbInfo, headerSizeArbInfo, trailerSizeArbInfo)
      }

    checkAll(propTestConfig, arb) { testCase ->
      val arb =
        ByteBufferArb(
          byte = testCase.byte.arb,
          remaining = testCase.remaining.arb,
          headerSize = testCase.headerSize.arb,
          trailerSize = testCase.trailerSize.arb,
        )

      val sample = arb.bind()

      val remaining = sample.byteBuffer.remaining()
      val headerSize = sample.byteBuffer.position()
      val trailerSize = sample.byteBuffer.run { capacity() - limit() }
      val bytes = ByteArray(sample.byteBuffer.remaining())
      sample.byteBuffer.get(bytes)
      assertSoftly {
        withClue("byte") { bytes.elementsShouldBeInRange(testCase.byte.min, testCase.byte.max) }
        withClue("remaining") { remaining shouldBeInRange testCase.remaining.range }
        withClue("headerSize") { headerSize shouldBeInRange testCase.headerSize.range }
        withClue("trailerSize") { trailerSize shouldBeInRange testCase.trailerSize.range }
      }
    }
  }

  @Test
  fun `should generate both direct and indirect buffers`() = runTest {
    var directCount = 0
    var indirectCount = 0

    checkAll(propTestConfig, ByteBufferArb()) { sample ->
      if (sample.byteBuffer.isDirect) {
        directCount++
      } else {
        indirectCount++
      }
    }

    assertSoftly {
      withClue("directCount") { directCount shouldBeGreaterThan 0 }
      withClue("indirectCount") { indirectCount shouldBeGreaterThan 0 }
    }
  }

  @Test
  fun `sample bytesSize should be the length of the array returned from bytesCopy()`() = runTest {
    checkAll(propTestConfig, ByteBufferArb()) { sample ->
      val bytesCopy = sample.bytesCopy()
      sample.bytesSize shouldBe bytesCopy.size
    }
  }

  @Test
  fun `sample bytesCopy should match the contents of the ByteBuffer`() = runTest {
    checkAll(propTestConfig, ByteBufferArb()) { sample ->
      val bytesCopy = sample.bytesCopy()
      val bytesRead = ByteArray(sample.byteBuffer.remaining())
      sample.byteBuffer.get(bytesRead)
      bytesRead shouldBe bytesCopy
    }
  }

  @Test
  fun `sample bytesCopy should not affect bytes in the ByteBuffer`() = runTest {
    checkAll(propTestConfig, ByteBufferArb()) { sample ->
      val bytesCopy = sample.bytesCopy()
      sample.bytesCopy().shuffle(randomSource().random)
      val bytesRead = ByteArray(sample.byteBuffer.remaining())
      sample.byteBuffer.get(bytesRead)
      bytesRead shouldBe bytesCopy
    }
  }

  @Test
  fun `sample bytesCopy should not reflect bytes in the ByteBuffer`() = runTest {
    checkAll(propTestConfig, ByteBufferArb()) { sample ->
      val bytesCopy = sample.bytesCopy()
      sample.byteBuffer.clear()
      while (sample.byteBuffer.hasRemaining()) {
        sample.byteBuffer.put(randomSource().random.nextInt().toByte())
      }
      sample.bytesCopy() shouldBe bytesCopy
    }
  }

  @Test
  fun `sample bytesCopy should always return a new object`() = runTest {
    checkAll(propTestConfig, ByteBufferArb()) { sample ->
      val bytesCopy1 = sample.bytesCopy()
      val bytesCopy2 = sample.bytesCopy()
      bytesCopy1 shouldNotBeSameInstanceAs bytesCopy2
    }
  }

  @Test
  fun `sample bytesSliceArray should return correct slice`() = runTest {
    checkAll(propTestConfig, ByteBufferArb(remaining = Arb.int(1..100))) { sample ->
      val bytes = sample.bytesCopy()
      val sliceRange = Arb.intRange(0 until bytes.size).bind()

      val slice = sample.bytesSliceArray(sliceRange)

      slice shouldBe bytes.sliceArray(sliceRange)
    }
  }

  @Test
  fun `sample remaining should match that of the ByteBuffer`() = runTest {
    checkAll(propTestConfig, ByteBufferArb()) { sample ->
      sample.remaining shouldBe sample.byteBuffer.remaining()
    }
  }

  @Test
  fun `sample remaining should not change`() = runTest {
    checkAll(propTestConfig, ByteBufferArb()) { sample ->
      val originalRemaining = sample.remaining
      while (sample.byteBuffer.hasRemaining()) {
        sample.byteBuffer.get()
        sample.remaining shouldBe originalRemaining
      }
      sample.byteBuffer.clear()
      sample.remaining shouldBe originalRemaining
    }
  }

  @Test
  fun `sample headerSize should match that of the ByteBuffer`() = runTest {
    checkAll(propTestConfig, ByteBufferArb()) { sample ->
      sample.headerSize shouldBe sample.byteBuffer.position()
    }
  }

  @Test
  fun `sample headerSize should not change`() = runTest {
    checkAll(propTestConfig, ByteBufferArb()) { sample ->
      val originalHeaderSize = sample.headerSize
      while (sample.byteBuffer.hasRemaining()) {
        sample.byteBuffer.get()
        sample.headerSize shouldBe originalHeaderSize
      }
      sample.byteBuffer.clear()
      sample.headerSize shouldBe originalHeaderSize
    }
  }

  @Test
  fun `sample trailerSize should match that of the ByteBuffer`() = runTest {
    checkAll(propTestConfig, ByteBufferArb()) { sample ->
      sample.trailerSize shouldBe sample.byteBuffer.capacity() - sample.byteBuffer.limit()
    }
  }

  @Test
  fun `sample trailerSize should not change`() = runTest {
    checkAll(propTestConfig, ByteBufferArb()) { sample ->
      val originalTrailerSize = sample.trailerSize
      while (sample.byteBuffer.hasRemaining()) {
        sample.byteBuffer.get()
        sample.trailerSize shouldBe originalTrailerSize
      }
      sample.byteBuffer.clear()
      sample.trailerSize shouldBe originalTrailerSize
    }
  }

  @Test
  fun `sample bufferType should match that of the ByteBuffer`() = runTest {
    checkAll(propTestConfig, ByteBufferArb()) { sample ->
      when (sample.bufferType) {
        ByteBufferArb.BufferType.Direct -> sample.byteBuffer.isDirect shouldBe true
        ByteBufferArb.BufferType.Indirect -> sample.byteBuffer.isDirect shouldBe false
      }
    }
  }

  @Test
  fun `sample arrayOffset should match that of the ByteBuffer`() = runTest {
    checkAll(propTestConfig, ByteBufferArb()) { sample ->
      val expectedArrayOffset =
        if (sample.byteBuffer.isDirect) -1 else sample.byteBuffer.arrayOffset()
      sample.arrayOffset shouldBe expectedArrayOffset
    }
  }

  @Test
  fun `sample capacity should match that of the ByteBuffer`() = runTest {
    checkAll(propTestConfig, ByteBufferArb()) { sample ->
      sample.capacity shouldBe sample.byteBuffer.capacity()
    }
  }

  @Test
  fun `sample position should match that of the ByteBuffer`() = runTest {
    checkAll(propTestConfig, ByteBufferArb()) { sample ->
      sample.position shouldBe sample.byteBuffer.position()
    }
  }

  @Test
  fun `sample position should not change`() = runTest {
    checkAll(propTestConfig, ByteBufferArb()) { sample ->
      val originalPosition = sample.position
      while (sample.byteBuffer.hasRemaining()) {
        sample.byteBuffer.get()
        sample.position shouldBe originalPosition
      }
      sample.byteBuffer.clear()
      sample.position shouldBe originalPosition
    }
  }

  @Test
  fun `sample limit should match that of the ByteBuffer`() = runTest {
    checkAll(propTestConfig, ByteBufferArb()) { sample ->
      sample.limit shouldBe sample.byteBuffer.limit()
    }
  }

  @Test
  fun `sample limit should not change`() = runTest {
    checkAll(propTestConfig, ByteBufferArb()) { sample ->
      val originalLimit = sample.limit
      repeat(10) {
        val newLimit = randomSource().random.nextInt(0..sample.byteBuffer.capacity())
        sample.byteBuffer.limit(newLimit)
        sample.limit shouldBe originalLimit
      }
      sample.byteBuffer.clear()
      sample.limit shouldBe originalLimit
    }
  }

  class ByteArbInfo(val arb: Arb<Byte>, val min: Byte, val max: Byte) {
    override fun toString() = "Arb.byte($min..$max)"

    companion object {
      fun arb(byte: Arb<Byte> = Arb.byte()): Arb<ByteArbInfo> =
        Arb.twoValues(byte).map { (b1, b2) ->
          val (min, max) = if (b1 < b2) Pair(b1, b2) else Pair(b2, b1)
          ByteArbInfo(Arb.byte(min = min, max = max), min = min, max = max)
        }
    }
  }

  class IntArbInfo(val arb: Arb<Int>, val min: Int, val max: Int) {
    val range = min..max
    override fun toString() = "Arb.int($min..$max)"

    companion object {
      fun arb(int: Arb<Int> = Arb.int()): Arb<IntArbInfo> =
        Arb.twoValues(int).map { (i1, i2) ->
          val (min, max) = if (i1 < i2) Pair(i1, i2) else Pair(i2, i1)
          IntArbInfo(Arb.int(min = min, max = max), min = min, max = max)
        }
    }
  }

  private companion object {

    @OptIn(ExperimentalKotest::class)
    val propTestConfig =
      PropTestConfig(
        iterations = 1000,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.33),
        shrinkingMode = ShrinkingMode.Off,
      )

    fun ByteArray.elementsShouldBeInRange(min: Byte, max: Byte) {
      forEachIndexed { index, byte ->
        withClue("index=$index") {
          assertSoftly {
            byte shouldBeGreaterThanOrEqualTo min
            byte shouldBeLessThanOrEqualTo max
          }
        }
      }
    }
  }
}
