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

import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ListPartitionUnitTest {

  @Test
  fun `randomPartitions Random generates random partitions`() = runTest {
    checkAll(propTestConfig, Arb.list(Arb.long(), 1..100)) { list ->
      val partitionCount = Arb.int(1..list.size).bind()

      val partitions = list.randomPartitions(partitionCount, randomSource().random)

      partitions.size shouldBe partitionCount
      partitions.flatten() shouldBe list
    }
  }

  @Test
  fun `randomPartitions Random uses the given Random`() = runTest {
    checkAll(propTestConfig, Arb.list(Arb.long(), 1..100)) { list ->
      val partitionCount = Arb.int(1..list.size).bind()
      val randomSeed = randomSource().random.nextLong()

      val partitions1 = list.randomPartitions(partitionCount, Random(randomSeed))
      val partitions2 = list.randomPartitions(partitionCount, Random(randomSeed))

      partitions1 shouldBe partitions2
    }
  }

  private companion object {
    @OptIn(ExperimentalKotest::class)
    val propTestConfig =
      PropTestConfig(
        iterations = 200,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.33),
        shrinkingMode = ShrinkingMode.Off,
      )
  }
}
