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

package com.google.firebase.dataconnect.testutil.property.arbitrary

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.ranges.shouldBeIn
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.pair
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlin.code
import kotlinx.coroutines.test.runTest
import org.junit.Test

@Suppress("ClassName")
class stringUnitTest {

  @Test
  fun `codepointWith1ByteUtf8Encoding() should produce codepoints whose utf8 encoding is 1 byte`() =
    runTest {
      checkAll(propTestConfig, Arb.string(1..200, Arb.codepointWith1ByteUtf8Encoding())) { string ->
        string.toByteArray() shouldHaveSize string.length
      }
    }

  @Test
  fun `codepointWith1ByteUtf8Encoding() should produce values in expected range`() = runTest {
    checkAll(propTestConfig, Arb.codepointWith1ByteUtf8Encoding()) { codepoint ->
      codepoint.value shouldBeInRange 0..0x7f
    }
  }

  @Test
  fun `codepointWith1ByteUtf8Encoding() should produce expected edge cases`() = runTest {
    val edgeCaseCodepoints = setOf(0, 1, 0x7f)
    checkAll(propTestConfigEdgeCasesOnly, Arb.codepointWith1ByteUtf8Encoding()) { codepoint ->
      codepoint.value shouldBeIn edgeCaseCodepoints
    }
  }

  @Test
  fun `codepointWith2ByteUtf8Encoding() should produce codepoints whose utf8 encoding is 2 bytes`() =
    runTest {
      checkAll(propTestConfig, Arb.string(1..200, Arb.codepointWith2ByteUtf8Encoding())) { string ->
        string.toByteArray() shouldHaveSize (string.length * 2)
      }
    }

  @Test
  fun `codepointWith2ByteUtf8Encoding() should produce values in expected range`() = runTest {
    checkAll(propTestConfig, Arb.codepointWith2ByteUtf8Encoding()) { codepoint ->
      codepoint.value shouldBeInRange 0x80..0x7ff
    }
  }

  @Test
  fun `codepointWith2ByteUtf8Encoding() should produce expected edge cases`() = runTest {
    val edgeCaseCodepoints = setOf(0x80, 0x7ff)
    checkAll(propTestConfigEdgeCasesOnly, Arb.codepointWith2ByteUtf8Encoding()) { codepoint ->
      codepoint.value shouldBeIn edgeCaseCodepoints
    }
  }

  @Test
  fun `codepointWith3ByteUtf8Encoding() should produce codepoints whose utf8 encoding is 3 bytes`() =
    runTest {
      checkAll(propTestConfig, Arb.string(1..200, Arb.codepointWith3ByteUtf8Encoding())) { string ->
        string.toByteArray() shouldHaveSize (string.length * 3)
      }
    }

  @Test
  fun `codepointWith3ByteUtf8Encoding() should produce values in expected range`() = runTest {
    checkAll(propTestConfig, Arb.codepointWith3ByteUtf8Encoding()) { codepoint ->
      codepoint.value shouldBeInRange 0x800..0xffff
    }
  }

  @Test
  fun `codepointWith3ByteUtf8Encoding() should not produce surrogates`() = runTest {
    checkAll(propTestConfig, Arb.string(1, Arb.codepointWith3ByteUtf8Encoding())) { string ->
      string.forEachIndexed { index, char ->
        withClue("index=$index char=0x${char.code.toString(16)}") {
          char.isSurrogate() shouldBe false
        }
      }
    }
  }

  @Test
  fun `codepointWith3ByteUtf8Encoding() should produce expected edge cases`() = runTest {
    val edgeCaseCodepoints = setOf(0x800, 0xd7ff, 0xe000, 0xffff)
    checkAll(propTestConfigEdgeCasesOnly, Arb.codepointWith3ByteUtf8Encoding()) { codepoint ->
      codepoint.value shouldBeIn edgeCaseCodepoints
    }
  }

  @Test
  fun `codepointWith4ByteUtf8Encoding() should produce codepoints whose utf8 encoding is 4 bytes`() =
    runTest {
      checkAll(propTestConfig, Arb.string(1..200, Arb.codepointWith4ByteUtf8Encoding())) { string ->
        // The expected length is `string.length * 2` rather than the "obvious" `string.length * 4`
        // because all Unicode codepoints requiring 4 bytes for their UTF-8 encoding require 2
        // UTF-16
        // code units (a surrogate pair).
        string.toByteArray() shouldHaveSize (string.length * 2)
      }
    }

  @Test
  fun `codepointWith4ByteUtf8Encoding() should produce values in expected range`() = runTest {
    checkAll(propTestConfig, Arb.codepointWith4ByteUtf8Encoding()) { codepoint ->
      codepoint.value shouldBeInRange 0x10000..0x10FFFF
    }
  }

  @Test
  fun `codepointWith4ByteUtf8Encoding() should always produce surrogates`() = runTest {
    checkAll(propTestConfig, Arb.string(1, Arb.codepointWith4ByteUtf8Encoding())) { string ->
      string.forEachIndexed { index, char ->
        withClue("index=$index char=0x${char.code.toString(16)}") {
          char.isSurrogate() shouldBe true
        }
      }
    }
  }

  @Test
  fun `codepointWith4ByteUtf8Encoding() should produce expected edge cases`() = runTest {
    val edgeCaseCodepoints = setOf(0x10000, 0x10FFFF)
    checkAll(propTestConfigEdgeCasesOnly, Arb.codepointWith4ByteUtf8Encoding()) { codepoint ->
      codepoint.value shouldBeIn edgeCaseCodepoints
    }
  }

  @Test
  fun `codepointWithEvenNumByteUtf8EncodingDistribution() should produce all utf8 encoded byte lengths`() =
    runTest {
      val byteCounts = mutableSetOf<Int>()
      checkAll(
        propTestConfig,
        Arb.string(1, Arb.codepointWithEvenNumByteUtf8EncodingDistribution())
      ) { string ->
        byteCounts.add(string.toByteArray().size)
      }
      byteCounts.shouldContainExactlyInAnyOrder(1, 2, 3, 4)
    }

  @Test
  fun `codepointWithEvenNumByteUtf8EncodingDistribution() should produce roughly even utf8 encoded byte lengths`() =
    runTest {
      val occurrenceCountByByteCount = mutableMapOf<Int, Int>()
      checkAll(
        propTestConfig,
        Arb.string(1, Arb.codepointWithEvenNumByteUtf8EncodingDistribution())
      ) { string ->
        val byteCount = string.toByteArray().size
        occurrenceCountByByteCount[byteCount] =
          occurrenceCountByByteCount.getOrDefault(byteCount, 0) + 1
      }
      assertSoftly {
        occurrenceCountByByteCount.entries.forEach { (byteCount, occurrenceCount) ->
          withClue("byteCount=$byteCount") { occurrenceCount shouldBeGreaterThan 100 }
        }
      }
    }

  @Test
  fun `stringWithEvenNumByteUtf8EncodingDistribution() should produce all utf8 encoded byte lengths`() =
    runTest {
      val byteCounts = mutableSetOf<Int>()
      checkAll(propTestConfig, Arb.stringWithEvenNumByteUtf8EncodingDistribution(0..100)) { string
        ->
        byteCounts.addAll(string.codepointUtf8EncodingByteCounts())
      }
      byteCounts.shouldContainExactlyInAnyOrder(1, 2, 3, 4)
    }

  @Test
  fun `stringWithEvenNumByteUtf8EncodingDistribution() should produce roughly even utf8 encoded byte lengths`() =
    runTest {
      val occurrenceCountByByteCount = mutableMapOf<Int, Int>()
      checkAll(propTestConfig, Arb.stringWithEvenNumByteUtf8EncodingDistribution(1..1)) { string ->
        val byteCount = string.toByteArray().size
        occurrenceCountByByteCount[byteCount] =
          occurrenceCountByByteCount.getOrDefault(byteCount, 0) + 1
      }
      assertSoftly {
        occurrenceCountByByteCount.entries.forEach { (byteCount, occurrenceCount) ->
          withClue("byteCount=$byteCount") { occurrenceCount shouldBeGreaterThan 100 }
        }
      }
    }

  @Test
  fun `stringWithLoneSurrogates() should produce string with at least 1 lone surrogate`() =
    runTest {
      checkAll(propTestConfig, Arb.stringWithLoneSurrogates(1..20)) { stringInfo ->
        stringInfo.string.countLoneSurrogates() shouldBeGreaterThan 0
      }
    }

  @Test
  fun `stringWithLoneSurrogates() should produce strings with the specified number of lone surrogates`() =
    runTest {
      checkAll(propTestConfig, Arb.stringWithLoneSurrogates(1..50)) { stringInfo ->
        stringInfo.loneSurrogateCount shouldBe stringInfo.string.countLoneSurrogates()
      }
    }

  @Test
  fun `stringWithLoneSurrogates() should produce strings whose length is within the given range`() =
    runTest {
      val arb =
        stringLengthRangeArb().flatMap { lengthRange: IntRange ->
          Arb.pair(Arb.constant(lengthRange), Arb.stringWithLoneSurrogates(lengthRange))
        }
      checkAll(propTestConfig, arb) { (lengthRange, sample) ->
        sample.string.length shouldBeIn lengthRange
      }
    }

  @Test
  fun `stringWithLoneSurrogates() should produce strings with the entire range of lone surrogate counts`() =
    runTest {
      val arb =
        stringLengthRangeArb().flatMap { lengthRange: IntRange ->
          Arb.pair(
            Arb.constant(lengthRange),
            Arb.list(
              Arb.stringWithLoneSurrogates(lengthRange).map { it.loneSurrogateCount },
              1000..1000
            )
          )
        }
      checkAll(propTestConfig, arb) { (lengthRange, loneSurrogateCounts) ->
        loneSurrogateCounts.toSet() shouldContainExactlyInAnyOrder (1..lengthRange.last).toList()
      }
    }

  private companion object {

    @OptIn(ExperimentalKotest::class)
    val propTestConfig =
      PropTestConfig(
        iterations = 1000,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2)
      )

    @OptIn(ExperimentalKotest::class)
    val propTestConfigEdgeCasesOnly =
      PropTestConfig(
        iterations = 1000,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 1.0)
      )

    fun stringLengthRangeArb(): Arb<IntRange> =
      Arb.twoValues(Arb.int(1..20)).map { (bound1, bound2) ->
        if (bound1 < bound2) bound1..bound2 else bound2..bound1
      }

    fun String.codepointUtf8EncodingByteCounts(): Set<Int> = buildSet {
      var i = 0
      while (i < length) {
        val char = get(i++)
        add(
          if (char.isSurrogate()) {
            i++
            4
          } else if (char.code < 0x80) {
            1
          } else if (char.code < 0x800) {
            2
          } else {
            3
          }
        )
      }
    }

    fun String.countLoneSurrogates(): Int {
      var loneSurrogateCount = 0
      var i = 0
      while (i < length) {
        val char: Char = get(i++)
        if (!char.isSurrogate()) {
          continue
        }
        if (char.isLowSurrogate()) {
          loneSurrogateCount++
        } else if (i == length) {
          loneSurrogateCount++
        } else if (get(i).isLowSurrogate()) {
          i++
        } else {
          loneSurrogateCount++
        }
      }

      return loneSurrogateCount
    }
  }
}
