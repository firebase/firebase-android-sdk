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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import java.nio.ByteBuffer
import kotlinx.coroutines.test.runTest
import org.junit.Test

class OffsetLengthOutOfRangeArbUnitTest {

  @Test
  fun `OffsetLengthOutOfRangeArbUnitTest samples should cause IOOBE`() = runTest {
    val byteBuffer = ByteBuffer.allocate(1024)
    checkAll(propTestConfig, Arb.byteArray(Arb.int(0..20), Arb.byte())) { byteArray ->
      val arb = OffsetLengthOutOfRangeArb(byteArray.size)

      val (offset, length) = arb.bind()

      assertSoftly {
        withClue("offset") { offset shouldBeGreaterThanOrEqualTo 0 }
        withClue("length") { length shouldBeGreaterThanOrEqualTo 0 }
        shouldThrow<IndexOutOfBoundsException> { byteBuffer.put(byteArray, offset, length) }
      }
    }
  }

  @Test
  fun `OffsetLengthOutOfRangeArbUnitTest samples should have positive offset and length`() =
    runTest {
      checkAll(propTestConfig, Arb.int(0..(Int.MAX_VALUE / 3))) { arraySize ->
        val arb = OffsetLengthOutOfRangeArb(arraySize)
        val (offset, length) = arb.bind()
        assertSoftly {
          withClue("offset") { offset shouldBeGreaterThanOrEqualTo 0 }
          withClue("length") { length shouldBeGreaterThanOrEqualTo 0 }
        }
      }
    }

  @Test
  fun `OffsetLengthOutOfRangeArbUnitTest samples should have various cases`() = runTest {
    var offsetValidCount = 0
    var lengthValidCount = 0
    var offsetAndLengthInvalidCount = 0

    checkAll(propTestConfig, Arb.int(0..(Int.MAX_VALUE / 3))) { arraySize ->
      val arb = OffsetLengthOutOfRangeArb(arraySize)

      val (offset, length) = arb.bind()

      if (offset in 0..arraySize) {
        offsetValidCount++
      } else if (length in 0..arraySize) {
        lengthValidCount++
      } else {
        offsetAndLengthInvalidCount++
      }
    }

    assertSoftly {
      withClue("offsetValidCount") { offsetValidCount shouldBeGreaterThan 0 }
      withClue("lengthValidCount") { lengthValidCount shouldBeGreaterThan 0 }
      withClue("offsetAndLengthInvalidCount") { offsetAndLengthInvalidCount shouldBeGreaterThan 0 }
    }
  }

  private companion object {

    @OptIn(ExperimentalKotest::class)
    val propTestConfig =
      PropTestConfig(
        iterations = 1000,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2),
      )
  }
}
