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

import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import io.kotest.assertions.shouldFail
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.Exhaustive
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.next
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TestUtilsUnitTest {

  @Test
  fun `shouldNotContainLoneSurrogates with strings that do NOT contain lone surrogates`() =
    runTest {
      checkAll(propTestConfig, Arb.dataConnect.string()) { it.shouldNotContainLoneSurrogates() }
    }

  @Test
  fun `shouldNotContainLoneSurrogates with strings that DO contain lone surrogates`() = runTest {
    val arb = Exhaustive.of("A\uD800B", "A\uDFFF B", "\uD800", "\uDC00", "\uDC00\uD800")
    checkAll(propTestConfig, arb, Arb.int(1..5)) { string, repetitions ->
      shouldFail { string.repeat(repetitions).shouldNotContainLoneSurrogates() }
    }
  }

  private companion object {

    @OptIn(ExperimentalKotest::class)
    val propTestConfig =
      PropTestConfig(
        iterations = 1000,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.33)
      )
  }
}
