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

package com.google.firebase.dataconnect.sqlite

import com.google.firebase.dataconnect.sqlite.QueryResultEncoderTesting.EntityTestCase
import com.google.firebase.dataconnect.sqlite.QueryResultEncoderTesting.keysRecursive
import com.google.firebase.dataconnect.sqlite.QueryResultEncoderTesting.structSizesRecursive
import com.google.firebase.dataconnect.testutil.property.arbitrary.maxDepth
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.structKey
import com.google.protobuf.Value
import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class QueryResultEncoderTestingUnitTest {

  @Test
  fun `entityArb() should specify the correct entityIdFieldName in its samples`() = runTest {
    checkAll(propTestConfig, Arb.string()) { entityIdFieldName ->
      val entityArb =
        EntityTestCase.arb(
          entityIdFieldName = Arb.constant(entityIdFieldName),
          structSize = 0..10,
          structDepth = 1..1,
        )

      val sample = entityArb.bind()

      sample.entityIdFieldName shouldBe entityIdFieldName
    }
  }

  @Test
  fun `entityArb() should specify the correct entityId in its samples`() = runTest {
    checkAll(propTestConfig, Arb.string()) { entityId ->
      val entityArb =
        EntityTestCase.arb(
          entityId = Arb.constant(entityId),
          structSize = 0..10,
          structDepth = 1..1,
        )

      val sample = entityArb.bind()

      sample.entityId shouldBe entityId
    }
  }

  @Test
  fun `entityArb() should use set the entityId using the entityIdFieldName in the struct`() =
    runTest {
      checkAll(propTestConfig, Arb.string(), Arb.string()) { entityId, entityIdFieldName ->
        val entityArb =
          EntityTestCase.arb(
            entityIdFieldName = Arb.constant(entityIdFieldName),
            entityId = Arb.constant(entityId),
            structSize = 0..10,
            structDepth = 1..1,
          )

        val sample = entityArb.bind()

        sample.asClue {
          withClue("containsFields") { it.struct.containsFields(entityIdFieldName).shouldBeTrue() }
          val entityIdValue: Value = it.struct.getFieldsOrThrow(entityIdFieldName)
          withClue("kindCase") { entityIdValue.kindCase shouldBe Value.KindCase.STRING_VALUE }
          withClue("stringValue") { entityIdValue.stringValue shouldBe entityId }
        }
      }
    }

  @Test
  fun `entityArb() should use the given structKey`() = runTest {
    checkAll(propTestConfig, Arb.string()) { entityIdFieldName ->
      val generatedKeys = mutableSetOf(entityIdFieldName)
      val structKeyArb = Arb.proto.structKey().map { it.also(generatedKeys::add) }
      val entityArb =
        EntityTestCase.arb(
          entityIdFieldName = Arb.constant(entityIdFieldName),
          structKey = structKeyArb,
          structSize = 1..3,
          structDepth = 1..3,
        )

      val sample = entityArb.bind()

      sample.struct.keysRecursive().distinct() shouldContainExactlyInAnyOrder generatedKeys
    }
  }

  @Test
  fun `entityArb() should use the given structSize`() = runTest {
    checkAll(propTestConfig, Arb.int(0..20), Arb.int(0..20)) { bound1, bound2 ->
      val entityArbStructSize = if (bound1 <= bound2) bound1..bound2 else bound2..bound1
      val entityArb = EntityTestCase.arb(structSize = entityArbStructSize, structDepth = 1)

      val sample = entityArb.bind()

      // Add 1 to the end of the struct size range to account for the entityId field itself.
      val structSizeRange = entityArbStructSize.first..(entityArbStructSize.last + 1)
      assertSoftly {
        sample.struct.structSizesRecursive().forEachIndexed { index, structSize ->
          withClue("index=$index") { structSize shouldBeInRange structSizeRange }
        }
      }
    }
  }

  @Test
  fun `entityArb() should use the given structDepth as IntRange`() = runTest {
    checkAll(propTestConfig, Arb.int(1..5), Arb.int(1..5)) { bound1, bound2 ->
      val entityArbStructDepth = if (bound1 <= bound2) bound1..bound2 else bound2..bound1
      val entityArb = EntityTestCase.arb(structSize = 1..2, structDepth = entityArbStructDepth)

      val sample = entityArb.bind()

      sample.struct.maxDepth() shouldBeInRange entityArbStructDepth
    }
  }

  @Test
  fun `entityArb() should use the given structDepth as Int`() = runTest {
    checkAll(propTestConfig, Arb.int(1..5)) { entityArbStructDepth ->
      val entityArb = EntityTestCase.arb(structSize = 1..2, structDepth = entityArbStructDepth)

      val sample = entityArb.bind()

      sample.struct.maxDepth() shouldBe entityArbStructDepth
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
