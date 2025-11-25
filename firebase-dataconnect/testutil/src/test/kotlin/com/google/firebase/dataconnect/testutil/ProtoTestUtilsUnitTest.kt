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

import com.google.firebase.dataconnect.testutil.property.arbitrary.listValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.scalarValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.protobuf.Value
import io.kotest.common.ExperimentalKotest
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ProtoTestUtilsUnitTest {

  @Test
  fun `deepCopy Struct should produce an equal instance`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct()) { sample ->
      sample.struct.deepCopy() shouldBe sample.struct
    }
  }

  @Test
  fun `deepCopy Struct should return a different instance than the receiver`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct()) { sample ->
      sample.struct.deepCopy() shouldNotBeSameInstanceAs sample.struct
    }
  }

  @Test
  fun `deepCopy Struct should deep clone recursively`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct()) { sample ->
      val originalValues = sample.struct.valuesRecursive()
      val deepCopyValues = sample.struct.deepCopy().valuesRecursive()
      deepCopiedValuesShouldBeDistinctInstances(originalValues, deepCopyValues)
    }
  }

  @Test
  fun `deepCopy ListValue should produce an equal instance`() = runTest {
    checkAll(propTestConfig, Arb.proto.listValue()) { sample ->
      sample.listValue.deepCopy() shouldBe sample.listValue
    }
  }

  @Test
  fun `deepCopy ListValue should return a different instance than the receiver`() = runTest {
    checkAll(propTestConfig, Arb.proto.listValue()) { sample ->
      sample.listValue.deepCopy() shouldNotBeSameInstanceAs sample.listValue
    }
  }

  @Test
  fun `deepCopy ListValue should deep clone recursively`() = runTest {
    checkAll(propTestConfig, Arb.proto.listValue()) { sample ->
      val originalValues = sample.listValue.valuesRecursive()
      val deepCopyValues = sample.listValue.deepCopy().valuesRecursive()
      deepCopiedValuesShouldBeDistinctInstances(originalValues, deepCopyValues)
    }
  }

  @Test
  fun `deepCopy Value should produce an equal instance`() = runTest {
    checkAll(propTestConfig, Arb.scalarAndCompositeValue()) { value ->
      value.deepCopy() shouldBe value
    }
  }

  @Test
  fun `deepCopy Value should return a different instance than the receiver`() = runTest {
    checkAll(propTestConfig, Arb.scalarAndCompositeValue()) { value ->
      value.deepCopy() shouldNotBeSameInstanceAs value
    }
  }

  @Test
  fun `deepCopy Value should deep clone recursively`() = runTest {
    checkAll(propTestConfig, Arb.scalarAndCompositeValue()) { value ->
      val originalValues = value.valuesRecursive()
      val deepCopyValues = value.deepCopy().valuesRecursive()
      deepCopiedValuesShouldBeDistinctInstances(originalValues, deepCopyValues)
    }
  }

  @Test
  fun `structDiff reports equal for same instance`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct(), Arb.differenceAccumulator()) {
      sample,
      differenceAccumulator ->
      structDiff(sample.struct, sample.struct, differenceAccumulator) shouldBe true
      differenceAccumulator?.size?.shouldBe(0)
    }
  }

  @Test
  fun `structDiff reports equal for equal, but distinct instance`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct(), Arb.differenceAccumulator()) {
      sample,
      differenceAccumulator ->
      structDiff(sample.struct, sample.struct.deepCopy(), differenceAccumulator) shouldBe true
      differenceAccumulator?.size?.shouldBe(0)
    }
  }

  @Test
  fun `listValueDiff reports equal for same instance`() = runTest {
    checkAll(propTestConfig, Arb.proto.listValue(), Arb.differenceAccumulator()) {
      sample,
      differenceAccumulator ->
      listValueDiff(sample.listValue, sample.listValue, differenceAccumulator) shouldBe true
      differenceAccumulator?.size?.shouldBe(0)
    }
  }

  @Test
  fun `listValueDiff reports equal for equal, but distinct instance`() = runTest {
    checkAll(propTestConfig, Arb.proto.listValue(), Arb.differenceAccumulator()) {
      sample,
      differenceAccumulator ->
      listValueDiff(sample.listValue, sample.listValue.deepCopy(), differenceAccumulator) shouldBe
        true
      differenceAccumulator?.size?.shouldBe(0)
    }
  }

  @Test
  fun `valueDiff reports equal for same instance`() = runTest {
    checkAll(propTestConfig, Arb.scalarAndCompositeValue(), Arb.differenceAccumulator()) {
      value,
      differenceAccumulator ->
      valueDiff(value, value, differenceAccumulator) shouldBe true
      differenceAccumulator?.size?.shouldBe(0)
    }
  }

  @Test
  fun `valueDiff reports equal for equal, but distinct instance`() = runTest {
    checkAll(propTestConfig, Arb.scalarAndCompositeValue(), Arb.differenceAccumulator()) {
      value,
      differenceAccumulator ->
      valueDiff(value, value.deepCopy(), differenceAccumulator) shouldBe true
      differenceAccumulator?.size?.shouldBe(0)
    }
  }

  private fun deepCopiedValuesShouldBeDistinctInstances(
    originalValues: List<Value>,
    deepCopiedValues: List<Value>
  ) {
    deepCopiedValues shouldContainExactlyInAnyOrder originalValues
    deepCopiedValues.forAll { deepCopiedValue ->
      originalValues.forAll { originalValue ->
        deepCopiedValue shouldNotBeSameInstanceAs originalValue
      }
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

    fun Arb.Companion.scalarAndCompositeValue(): Arb<Value> =
      Arb.choice(
        Arb.proto.scalarValue(),
        Arb.proto.struct().map { it.toValueProto() },
        Arb.proto.listValue().map { it.toValueProto() },
      )

    fun Arb.Companion.differenceAccumulator(): Arb<DifferenceAccumulator?> =
      arbitrary { DifferenceAccumulator() }.orNull(nullProbability = 0.33)
  }
}
