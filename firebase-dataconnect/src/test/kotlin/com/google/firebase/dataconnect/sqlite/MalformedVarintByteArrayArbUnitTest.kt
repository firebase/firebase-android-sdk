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

import com.google.firebase.dataconnect.testutil.property.arbitrary.twoValues
import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MalformedVarintByteArrayArbUnitTest {

  @Test
  fun `should generate arrays with size in the given range`() = runTest {
    checkAll(propTestConfig, Arb.sizeRange()) { sizeRange ->
      val arb = MalformedVarintByteArrayArb(sizeRange)

      val sample = arb.bind()

      sample.byteArrayCopy().size shouldBeInRange sizeRange
    }
  }

  @Test
  fun `should generate arrays with the initial bytes all continuation bytes`() = runTest {
    checkAll(propTestConfig, Arb.sizeRange()) { sizeRange ->
      val arb = MalformedVarintByteArrayArb(sizeRange)

      val sample = arb.bind()

      val initialBytesRange = 0 until sizeRange.first
      sample.byteArrayCopy().sliceArray(initialBytesRange).forEachIndexed { index, byte ->
        withClue("index=$index") { byte.isContinuationByte() shouldBe true }
      }
    }
  }

  @Test
  fun `should generate arrays with the subsequent bytes some continuation bytes`() = runTest {
    var continuationByteCount = 0
    var nonContinuationByteCount = 0

    checkAll(propTestConfig, Arb.sizeRange()) { sizeRange ->
      val arb = MalformedVarintByteArrayArb(sizeRange)

      val sample = arb.bind()

      val subsequentBytesRange = sizeRange.first until sample.byteArraySize
      sample.byteArrayCopy().sliceArray(subsequentBytesRange).forEach { byte ->
        if (byte.isContinuationByte()) {
          continuationByteCount++
        } else {
          nonContinuationByteCount++
        }
      }
    }

    assertSoftly {
      withClue("continuationByteCount") { continuationByteCount shouldBeGreaterThan 0 }
      withClue("nonContinuationByteCount") { nonContinuationByteCount shouldBeGreaterThan 0 }
    }
  }

  @Test
  fun `should return edgeCase=null from sample()`() = runTest {
    checkAll(propTestConfig, Arb.sizeRange()) { sizeRange ->
      val arb = MalformedVarintByteArrayArb(sizeRange)

      val sample = arb.sample(randomSource()).value

      sample.asClue { it.edgeCase.shouldBeNull() }
    }
  }

  @Test
  fun `should return edgeCase=Size from edgecase()`() = runTest {
    var sizeEdgeCaseCount = 0

    checkAll(propTestConfig, Arb.sizeRange()) { sizeRange ->
      val arb = MalformedVarintByteArrayArb(sizeRange)

      val sample = arb.edgecase(randomSource())

      if (sample.edgeCase == MalformedVarintByteArrayArb.EdgeCase.Size) {
        sizeEdgeCaseCount++
        sample.asClue { listOf(sizeRange.first, sizeRange.last) shouldContain it.byteArraySize }
      }
    }

    withClue("sizeEdgeCaseCount") { sizeEdgeCaseCount shouldBeGreaterThan 0 }
  }

  @Test
  fun `should return edgeCase=Byte from edgecase()`() = runTest {
    var byteEdgeCaseCount = 0

    checkAll(propTestConfig, Arb.sizeRange()) { sizeRange ->
      val arb = MalformedVarintByteArrayArb(sizeRange)

      val sample = arb.edgecase(randomSource())

      if (sample.edgeCase == MalformedVarintByteArrayArb.EdgeCase.Byte) {
        byteEdgeCaseCount++
        sample.asClue {
          it.byteArrayCopy().forEachIndexed { index, byte ->
            val expectedValues = buildSet {
              addAll(continuationByteEdgeCases)
              if (index > sizeRange.first) {
                addAll(byteEdgeCases)
              }
            }
            withClue("index=$index, byte=$byte") { expectedValues shouldContain byte }
          }
        }
      }
    }

    withClue("byteEdgeCaseCount") { byteEdgeCaseCount shouldBeGreaterThan 0 }
  }

  @Test
  fun `should return edgeCase=AllContinuationBytes from edgecase()`() = runTest {
    var allContinuationBytesEdgeCaseCount = 0

    checkAll(propTestConfig, Arb.sizeRange()) { sizeRange ->
      val arb = MalformedVarintByteArrayArb(sizeRange)

      val sample = arb.edgecase(randomSource())

      if (sample.edgeCase == MalformedVarintByteArrayArb.EdgeCase.AllContinuationBytes) {
        allContinuationBytesEdgeCaseCount++
        sample.asClue {
          it.byteArrayCopy().forEachIndexed { index, byte ->
            withClue("index=$index, byte=$byte") { byte.isContinuationByte() shouldBe true }
          }
        }
      }
    }

    withClue("allContinuationBytesEdgeCaseCount") {
      allContinuationBytesEdgeCaseCount shouldBeGreaterThan 0
    }
  }

  @Test
  fun `should return edgeCase=SizeAndByte from edgecase()`() = runTest {
    var sizeAndByteEdgeCaseCount = 0

    checkAll(propTestConfig, Arb.sizeRange()) { sizeRange ->
      val arb = MalformedVarintByteArrayArb(sizeRange)

      val sample = arb.edgecase(randomSource())

      if (sample.edgeCase == MalformedVarintByteArrayArb.EdgeCase.SizeAndByte) {
        sizeAndByteEdgeCaseCount++
        sample.asClue {
          listOf(sizeRange.first, sizeRange.last) shouldContain it.byteArraySize
          it.byteArrayCopy().forEachIndexed { index, byte ->
            val expectedValues = buildSet {
              addAll(continuationByteEdgeCases)
              if (index > sizeRange.first) {
                addAll(byteEdgeCases)
              }
            }
            withClue("index=$index, byte=$byte") { expectedValues shouldContain byte }
          }
        }
      }
    }

    withClue("sizeAndByteEdgeCaseCount") { sizeAndByteEdgeCaseCount shouldBeGreaterThan 0 }
  }

  @Test
  fun `should apply the given transformer`() = runTest {
    val arb =
      MalformedVarintByteArrayArb(
        5..100,
        transformer = { byteArray ->
          byteArray.indices.forEach { index -> byteArray[index] = index.toByte() }
        }
      )

    checkAll(propTestConfig, arb) { sample ->
      val expectedByteArray = ByteArray(sample.byteArraySize)
      expectedByteArray.indices.forEach { expectedByteArray[it] = it.toByte() }

      sample.byteArrayCopy() shouldBe expectedByteArray
    }
  }

  @Test
  fun `should ignore changes made by the transformer after the fact`() = runTest {
    val byteArrays = mutableListOf<ByteArray>()
    val arb = MalformedVarintByteArrayArb(5..100, transformer = byteArrays::add)
    checkAll(propTestConfig, arb) { sample ->
      val sampleByteArray1 = sample.byteArrayCopy()

      check(byteArrays.isNotEmpty())
      byteArrays.forEach { byteArray -> byteArray.shuffle(randomSource().random) }
      byteArrays.clear()

      sample.byteArrayCopy() shouldBe sampleByteArray1
    }
  }

  private companion object {

    @OptIn(ExperimentalKotest::class)
    val propTestConfig =
      PropTestConfig(
        iterations = 1000,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.33)
      )

    fun Arb.Companion.sizeRange(): Arb<IntRange> =
      twoValues(int(0..10)).map { (i1, i2) -> if (i1 < i2) i1..i2 else i2..i1 }

    fun Byte.isContinuationByte(): Boolean = (toInt() and 0x80) == 0x80

    val byteEdgeCases: Set<Byte> = buildSet {
      val rs = RandomSource.default()
      val arb = Arb.byte()
      repeat(200) { add(arb.edgecase(rs)!!) }
    }

    val continuationByteEdgeCases = byteEdgeCases.map { it.toVarintContinuationByte() }.toSet()
  }
}
