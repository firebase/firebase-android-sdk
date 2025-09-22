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

package com.google.firebase.dataconnect.testutil

import com.google.firebase.dataconnect.testutil.property.arbitrary.listContainingNull
import com.google.firebase.dataconnect.testutil.property.arbitrary.sampleFromArb
import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.property.Arb
import io.kotest.property.Exhaustive
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ListContainingNullUnitTest {

  @Test
  fun `listContainingNull generates values with lengths in the given range`() = runTest {
    checkAll(NUM_ITERATIONS, listLengthsArb) { lengthRange ->
      val arb = Arb.listContainingNull(valuesGen, lengthRange)
      val sample = sampleFromArb(arb, edgeCaseProbability = 0.5)
      sample.value.asClue {
        assertSoftly {
          it.size shouldBeGreaterThanOrEqual lengthRange.first
          it.size shouldBeLessThanOrEqual lengthRange.last
        }
      }
    }
  }

  @Test
  fun `listContainingNull generates values from the given arb`() = runTest {
    checkAll(NUM_ITERATIONS, listLengthsArb) { lengthRange ->
      val arb = Arb.listContainingNull(valuesGen, lengthRange)
      val sample = sampleFromArb(arb, edgeCaseProbability = 0.5)
      sample.value.asClue {
        assertSoftly {
          it.forEachIndexed { index, value ->
            if (value !== null) {
              withClue("index=$index") { value shouldBeIn valuesGen.values }
            }
          }
        }
      }
    }
  }

  @Test
  fun `listContainingNull generates lists that always contain null`() = runTest {
    checkAll(NUM_ITERATIONS, listLengthsArb) { lengthRange ->
      val arb = Arb.listContainingNull(valuesGen, lengthRange)
      val sample = sampleFromArb(arb, edgeCaseProbability = 0.5)
      sample.value.asClue { it shouldContain null }
    }
  }

  private companion object {

    const val NUM_ITERATIONS = 1000

    val valuesGen = Exhaustive.of("foo", "bar", "baz")

    val listLengthsArb: Arb<IntRange> =
      Arb.bind(Arb.int(1..20), Arb.int(1..20)) { lengthLowerBound, lengthExtent ->
        lengthLowerBound until (lengthLowerBound + lengthExtent)
      }
  }
}
