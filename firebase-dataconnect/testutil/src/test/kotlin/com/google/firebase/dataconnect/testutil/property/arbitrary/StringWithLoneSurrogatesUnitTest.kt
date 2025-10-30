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
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.positiveInt
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class StringWithLoneSurrogatesUnitTest {

  @Test
  fun `stringWithLoneSurrogates() should produce strings with at least 1 lone surrogate`() =
    runTest {
      checkAll(propTestConfig, Arb.stringWithLoneSurrogates(1..20)) { sample ->
        sample.loneSurrogateCount shouldBeGreaterThan 0
      }
    }

  @Test
  fun `stringWithLoneSurrogates() should produce strings with the indicated number of lone surrogates`() =
    runTest {
      checkAll(propTestConfig, Arb.stringWithLoneSurrogates(1..20)) { sample ->
        sample.loneSurrogateCount shouldBe sample.string.countLoneSurrogates()
      }
    }

  @Test
  fun `stringWithLoneSurrogates() should produce strings whose length is in the given range`() =
    runTest {
      checkAll(propTestConfig, Arb.positiveInt(100).distinctPair()) { (bound1, bound2) ->
        val lengthRange = if (bound1 < bound2) bound1..bound2 else bound2..bound1
        val sample = Arb.stringWithLoneSurrogates(lengthRange).bind()
        assertSoftly {
          sample.string.length shouldBeGreaterThanOrEqual lengthRange.first
          sample.string.length shouldBeLessThanOrEqual lengthRange.last
        }
      }
    }

  @Test
  fun `stringWithLoneSurrogates() should produce strings with the entire range of lone surrogate counts`() =
    runTest {
      checkAll(propTestConfig, Arb.int(1..50)) { stringLength ->
        val arb = Arb.stringWithLoneSurrogates(stringLength..stringLength)
        val samples = List(1000) { arb.bind() }
        val loneSurrogateCounts =
          samples.groupBy { it.loneSurrogateCount }.mapValues { it.value.size }
        withClue("loneSurrogateCounts=${loneSurrogateCounts.toSortedMap(compareBy { it })}") {
          loneSurrogateCounts.keys shouldContainExactlyInAnyOrder (1..stringLength).toList()
        }
      }
    }

  private companion object {

    @OptIn(ExperimentalKotest::class)
    val propTestConfig =
      PropTestConfig(
        iterations = 1000,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2),
        shrinkingMode = ShrinkingMode.Off,
      )

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
