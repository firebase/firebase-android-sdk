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

@file:OptIn(ExperimentalKotest::class)

package com.google.firebase.dataconnect.testutil.property.arbitrary

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll
import kotlin.ranges.rangeTo
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ArbsUnitTest {

  @Test
  fun `maxAgeArb() produces valid values`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.maxAge()) { maxAge ->
      assertSoftly {
        withClue("seconds") { maxAge.seconds shouldBeGreaterThanOrEqual 0 }
        withClue("nanos") { maxAge.nanos shouldBeInRange 0..999_999_999 }
      }
    }
  }

  @Test
  fun `maxAgeArb() respects the given min`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.maxAge()) { min ->
      val arb = Arb.dataConnect.maxAge(min = min)

      val maxAge = arb.bind()

      assertSoftly {
        withClue("seconds") { maxAge.seconds shouldBeGreaterThanOrEqual min.seconds }
        withClue("nanos") {
          val minNanos = if (maxAge.seconds == min.seconds) min.nanos else 0
          maxAge.nanos shouldBeInRange minNanos..999_999_999
        }
      }
    }
  }
}

private val propTestConfig = PropTestConfig(iterations = 1000)
