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

package com.google.firebase.dataconnect.util

import com.google.firebase.dataconnect.util.ListUtil.isPrefixOf
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.PropertyContext
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.random.nextInt

class ListUtilUnitTest {

  @Test
  fun `List isPrefixOf() returns true when the receiver is an empty list`() = runTest {
    checkAll(propTestConfig, Arb.list(Arb.int(), 0..10)) { list ->
      emptyList<Int>().isPrefixOf(list) shouldBe true
    }
  }

  @Test
  fun `List isPrefixOf() returns true when the receiver list is a non-empty prefix`() = runTest {
    checkAll(propTestConfig, Arb.list(Arb.int(), 1..10)) { list ->
      val prefixLength = randomSource().random.nextInt(1..list.size)
      val prefix = list.take(prefixLength)
      withClue("prefixLength=$prefixLength") {
        prefix.isPrefixOf(list) shouldBe true
      }
    }
  }

  @Test
  fun `List isPrefixOf() returns false when the receiver list has a greater size`() = runTest {
    checkAll(propTestConfig, Arb.list(Arb.int(), 0..10), Arb.list(Arb.int(), 1..10)) { list, extras ->
      val prefix = list + extras
      prefix.isPrefixOf(list) shouldBe false
    }
  }

  @Test
  fun `List isPrefixOf() returns false when the receiver list is not a prefix`() = runTest {
    checkAll(propTestConfig, Arb.list(Arb.int(), 1..10)) { list ->
      val prefixLength = randomSource().random.nextInt(1..list.size)
      val prefixDifferingIndexCount = randomSource().random.nextInt(1..prefixLength)
      val differingIndices = (0 until prefixLength).shuffled(randomSource().random).take(prefixDifferingIndexCount)
      val prefix = list.take(prefixLength).mapIndexed { index, element ->
        if (index !in differingIndices) {
          element
        } else {
          randomIntNotEqualTo(element)
        }
      }

      withClue("prefix=$prefix, prefixLength=$prefixLength, " +
          "prefixDifferingIndexCount=$prefixDifferingIndexCount, " +
          "differingIndices=$differingIndices, ") {
        prefix.isPrefixOf(list) shouldBe false
      }
    }
  }

  private companion object {

    @OptIn(ExperimentalKotest::class)
    val propTestConfig =
      PropTestConfig(
        iterations = 1000,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2)
      )

    fun PropertyContext.randomIntNotEqualTo(value: Int): Int {
      val random = randomSource().random
      while (true) {
        val newValue = random.nextInt()
        if (newValue != value) {
          return newValue
        }
      }
    }
  }
}
