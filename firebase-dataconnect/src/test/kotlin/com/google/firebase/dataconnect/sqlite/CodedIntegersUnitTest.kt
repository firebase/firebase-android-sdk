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

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.negativeInt
import io.kotest.property.arbitrary.negativeLong
import io.kotest.property.arbitrary.withEdgecases
import io.kotest.property.checkAll
import java.nio.ByteBuffer
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CodedIntegersUnitTest {

  @Test
  fun `computeUInt32Size() returns the correct value`() = runTest {
    checkAll(propTestConfig, uint32Arb()) { value ->
      CodedIntegers.computeUInt32Size(value) shouldBe value.calculateUInt32Size()
    }
  }

  @Test
  fun `putUInt32() round trips with getUInt32()`() = runTest {
    val byteBuffer = ByteBuffer.allocate(CodedIntegers.MAX_VARINT32_SIZE)
    checkAll(propTestConfig, uint32Arb()) { value ->
      byteBuffer.clear()
      CodedIntegers.putUInt32(value, byteBuffer)
      byteBuffer.flip()
      CodedIntegers.getUInt32(byteBuffer) shouldBe value
    }
  }

  @Test
  fun `putUInt32() puts the number of bytes calculated by computeUInt32Size()`() = runTest {
    val byteBuffer = ByteBuffer.allocate(CodedIntegers.MAX_VARINT32_SIZE)
    checkAll(propTestConfig, uint32Arb()) { value ->
      byteBuffer.clear()
      CodedIntegers.putUInt32(value, byteBuffer)
      byteBuffer.position() shouldBe CodedIntegers.computeUInt32Size(value)
    }
  }

  @Test
  fun `computeUInt64Size() returns the correct value`() = runTest {
    checkAll(propTestConfig, uint64Arb()) { value ->
      val byteBuffer = ByteBuffer.allocate(CodedIntegers.MAX_VARINT64_SIZE)
      assertSoftly {
        byteBuffer.clear()
        CodedIntegers.putUInt64(value, byteBuffer)
        CodedIntegers.computeUInt64Size(value) shouldBe value.calculateUInt64Size()
      }
    }
  }

  @Test
  fun `putUInt64() round trips with getUInt64()`() = runTest {
    val byteBuffer = ByteBuffer.allocate(CodedIntegers.MAX_VARINT64_SIZE)
    checkAll(propTestConfig, uint64Arb()) { value ->
      byteBuffer.clear()
      CodedIntegers.putUInt64(value, byteBuffer)
      byteBuffer.flip()
      CodedIntegers.getUInt64(byteBuffer) shouldBe value
    }
  }

  @Test
  fun `putUInt64() puts the number of bytes calculated by computeUInt64Size()`() = runTest {
    val byteBuffer = ByteBuffer.allocate(CodedIntegers.MAX_VARINT64_SIZE)
    checkAll(propTestConfig, uint64Arb()) { value ->
      byteBuffer.clear()
      CodedIntegers.putUInt64(value, byteBuffer)
      byteBuffer.position() shouldBe CodedIntegers.computeUInt64Size(value)
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Unit tests for test helper methods
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun `uint32Arb() should produce correct samples`() = runTest {
    val counts = IntArray(uint32MaxValueByByteCount.size)
    var negativeCount = 0
    val uint32Arb = uint32Arb()

    checkAll(propTestConfig, Arb.constant(null)) {
      val sample = uint32Arb.sample(randomSource()).value
      if (sample < 0) {
        negativeCount++
      } else if (sample == 0) {
        counts[0]++
      } else {
        val index = uint32MaxValueByByteCount.indexOfLast { sample > it }
        counts[index]++
      }
    }

    assertSoftly {
      withClue("negativeCount") { negativeCount shouldBeGreaterThan 0 }
      counts.forEachIndexed { index, count ->
        withClue("counts[$index]") { count shouldBeGreaterThan 0 }
      }
    }
  }

  @Test
  fun `uint32Arb() should produce correct edge cases`() = runTest {
    val counts = IntArray(uint32EdgeCases.size)
    val uint32Arb = uint32Arb()

    checkAll(propTestConfig, Arb.constant(null)) {
      val sample = uint32Arb.edgecase(randomSource())
      val index = uint32EdgeCases.indexOf(sample)
      counts[index]++
    }

    assertSoftly {
      counts.forEachIndexed { index, count ->
        withClue("counts[$index],uint32EdgeCases[$index]=${uint32EdgeCases[index]}") {
          count shouldBeGreaterThan 0
        }
      }
    }
  }

  @Test
  fun `uint64Arb() should produce correct samples`() = runTest {
    val counts = IntArray(uint64MaxValueByByteCount.size)
    var negativeCount = 0
    val uint64Arb = uint64Arb()

    checkAll(propTestConfig, Arb.constant(null)) {
      val sample = uint64Arb.sample(randomSource()).value
      if (sample < 0) {
        negativeCount++
      } else if (sample == 0L) {
        counts[0]++
      } else {
        val index = uint64MaxValueByByteCount.indexOfLast { sample > it }
        counts[index]++
      }
    }

    assertSoftly {
      withClue("negativeCount") { negativeCount shouldBeGreaterThan 0 }
      counts.forEachIndexed { index, count ->
        withClue("counts[$index]") { count shouldBeGreaterThan 0 }
      }
    }
  }

  @Test
  fun `uint64Arb() should produce correct edge cases`() = runTest {
    val counts = IntArray(uint64EdgeCases.size)
    val uint64Arb = uint64Arb()

    checkAll(propTestConfig, Arb.constant(null)) {
      val sample = uint64Arb.edgecase(randomSource())
      val index = uint64EdgeCases.indexOf(sample)
      counts[index]++
    }

    assertSoftly {
      counts.forEachIndexed { index, count ->
        withClue("counts[$index],uint64EdgeCases[$index]=${uint64EdgeCases[index]}") {
          count shouldBeGreaterThan 0
        }
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // companion object
  //////////////////////////////////////////////////////////////////////////////////////////////////

  private companion object {

    @OptIn(ExperimentalKotest::class)
    val propTestConfig =
      PropTestConfig(
        iterations = 1000,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.25),
        shrinkingMode = ShrinkingMode.Off,
      )

    val uint32MaxValueByByteCount: List<Int> = listOf(0, 127, 16_383, 2_097_151, 268_435_455)

    val uint32EdgeCases: List<Int> =
      uint32MaxValueByByteCount
        .flatMap { listOf(it, -it) }
        .flatMap { listOf(it, it + 1, it + 2, it - 1) }
        .toMutableList()
        .apply {
          add(1)
          add(-1)
          add(2)
          add(-2)
        }
        .distinct()

    fun uint32Arb(): Arb<Int> {
      val positiveRanges =
        uint32MaxValueByByteCount.windowed(2, partialWindows = true).map { window ->
          when (window.size) {
            1 -> (window[0] + 1)..Int.MAX_VALUE
            2 -> (window[0] + 1)..window[1]
            else ->
              throw IllegalStateException("window.size=${window.size} window=$window [rv27chgadc]")
          }
        }

      val arbs =
        positiveRanges.map { Arb.int(it) }.toMutableList().apply { add(Arb.negativeInt()) }.toList()

      return Arb.choice(arbs).withEdgecases(uint32EdgeCases)
    }

    fun Int.calculateUInt32Size(): Int {
      if (this < 0) {
        return 5
      } else if (this == 0) {
        return 1
      }
      uint32MaxValueByByteCount.forEachIndexed { index, cutoff ->
        if (this <= cutoff) {
          return index
        }
      }
      return 5
    }

    val uint64MaxValueByByteCount: List<Long> =
      listOf(
        0,
        127,
        16_383,
        2_097_151,
        268_435_455,
        34_359_738_367,
        4_398_046_511_103,
        562_949_953_421_311,
        72_057_594_037_927_935,
      )

    val uint64EdgeCases: List<Long> =
      uint64MaxValueByByteCount
        .flatMap { listOf(it, -it) }
        .flatMap { listOf(it, it + 1, it + 2, it - 1) }
        .toMutableList()
        .apply {
          add(1)
          add(-1)
          add(2)
          add(-2)
        }
        .distinct()

    fun uint64Arb(): Arb<Long> {
      val positiveRanges =
        uint64MaxValueByByteCount.windowed(2, partialWindows = true).map { window ->
          when (window.size) {
            1 -> (window[0] + 1)..Long.MAX_VALUE
            2 -> (window[0] + 1)..window[1]
            else ->
              throw IllegalStateException("window.size=${window.size} window=$window [f4t2gk6tbb]")
          }
        }

      val arbs =
        positiveRanges
          .map { Arb.long(it) }
          .toMutableList()
          .apply { add(Arb.negativeLong()) }
          .toList()

      return Arb.choice(arbs).withEdgecases(uint64EdgeCases)
    }

    fun Long.calculateUInt64Size(): Int {
      if (this < 0) {
        return 10
      } else if (this == 0L) {
        return 1
      }
      uint64MaxValueByByteCount.forEachIndexed { index, cutoff ->
        if (this <= cutoff) {
          return index
        }
      }
      return 9
    }
  }
}
