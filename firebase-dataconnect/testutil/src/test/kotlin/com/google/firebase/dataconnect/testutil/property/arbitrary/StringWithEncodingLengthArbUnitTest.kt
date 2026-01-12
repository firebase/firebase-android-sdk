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

import com.google.firebase.dataconnect.testutil.property.arbitrary.StringWithEncodingLengthArb.Mode.Utf8EncodingLongerThanUtf16
import com.google.firebase.dataconnect.testutil.property.arbitrary.StringWithEncodingLengthArb.Mode.Utf8EncodingShorterThanOrEqualToUtf16
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.nonNegativeInt
import io.kotest.property.arbitrary.of
import io.kotest.property.assume
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class StringWithEncodingLengthArbUnitTest {

  @Test
  fun `StringWithEncodingLengthArb should produce string lengths in the given range`() = runTest {
    var minLengthCount = 0
    var maxLengthCount = 0
    var midLengthCount = 0

    val modeArb = Arb.of(Utf8EncodingLongerThanUtf16, Utf8EncodingShorterThanOrEqualToUtf16)
    val lengthRangeArb =
      Arb.twoValues(Arb.nonNegativeInt(max = 100)).map { (bound1, bound2) ->
        if (bound1 < bound2) bound1..bound2 else bound2..bound1
      }

    checkAll(propTestConfig, modeArb, lengthRangeArb) { mode, lengthRange ->
      assume(lengthRange.last >= mode.minCharCount)
      val sample = StringWithEncodingLengthArb(mode, lengthRange).bind()

      sample.length shouldBeInRange lengthRange

      if (sample.length == lengthRange.first) {
        minLengthCount++
      } else if (sample.length == lengthRange.last) {
        maxLengthCount++
      } else {
        midLengthCount++
      }
    }

    assertSoftly {
      withClue("minLengthCount") { minLengthCount shouldBeGreaterThan 0 }
      withClue("maxLengthCount") { maxLengthCount shouldBeGreaterThan 0 }
      withClue("midLengthCount") { midLengthCount shouldBeGreaterThan 0 }
    }
  }

  @Test
  fun `StringWithEncodingLengthArb should produce strings with utf8 and utf16 encoding lengths respecting the given mode`() =
    runTest {
      var utf8EdgeCaseCount = 0
      var utf8NonEdgeCaseCount = 0
      var utf16EdgeCaseCount = 0
      var utf16NonEdgeCaseCount = 0

      val modeArb = Arb.of(Utf8EncodingLongerThanUtf16, Utf8EncodingShorterThanOrEqualToUtf16)
      val lengthRangeArb = Arb.nonNegativeInt(max = 100).map { it..it }

      checkAll(propTestConfig, modeArb, lengthRangeArb) { mode, lengthRange ->
        assume(lengthRange.last >= mode.minCharCount)
        val sample = StringWithEncodingLengthArb(mode, lengthRange).bind()

        val utf8ByteCount = sample.encodeToByteArray().size
        val utf16ByteCount = sample.length * 2

        when (mode) {
          Utf8EncodingLongerThanUtf16 -> {
            utf8ByteCount shouldBeGreaterThan utf16ByteCount
            if (utf8ByteCount == utf16ByteCount + 1) {
              utf16EdgeCaseCount++
            } else {
              utf16NonEdgeCaseCount++
            }
          }
          Utf8EncodingShorterThanOrEqualToUtf16 -> {
            utf8ByteCount shouldBeLessThanOrEqualTo utf16ByteCount
            if (utf8ByteCount == utf16ByteCount) {
              utf8EdgeCaseCount++
            } else {
              utf8NonEdgeCaseCount++
            }
          }
        }
      }

      assertSoftly {
        withClue("utf8EdgeCaseCount") { utf8EdgeCaseCount shouldBeGreaterThan 0 }
        withClue("utf8NonEdgeCaseCount") { utf8NonEdgeCaseCount shouldBeGreaterThan 0 }
        withClue("utf16EdgeCaseCount") { utf16EdgeCaseCount shouldBeGreaterThan 0 }
        withClue("utf16NonEdgeCaseCount") { utf16NonEdgeCaseCount shouldBeGreaterThan 0 }
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
  }
}
