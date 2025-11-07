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

import com.google.firebase.dataconnect.testutil.RandomSeedTestRule
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.RandomSource
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@Suppress("ClassName")
class protoUnitTest {

  @get:Rule val randomSeedTestRule = RandomSeedTestRule()

  private val rs: RandomSource by randomSeedTestRule.rs

  @Test
  fun `nullValue() should produce Values with kindCase NULL_VALUE`() =
    verifyArbGeneratesValuesWithKindCase(Arb.proto.nullValue(), Value.KindCase.NULL_VALUE)

  @Test
  fun `numberValue() should produce Values with kindCase NUMBER_VALUE`() =
    verifyArbGeneratesValuesWithKindCase(Arb.proto.numberValue(), Value.KindCase.NUMBER_VALUE)

  @Test
  fun `boolValue() should produce Values with kindCase BOOL_VALUE`() =
    verifyArbGeneratesValuesWithKindCase(Arb.proto.boolValue(), Value.KindCase.BOOL_VALUE)

  @Test
  fun `stringValue() should produce Values with kindCase STRING_VALUE`() =
    verifyArbGeneratesValuesWithKindCase(Arb.proto.stringValue(), Value.KindCase.STRING_VALUE)

  @Test
  fun `kindNotSetValue() should produce Values with kindCase KIND_NOT_SET`() =
    verifyArbGeneratesValuesWithKindCase(Arb.proto.kindNotSetValue(), Value.KindCase.KIND_NOT_SET)

  @Test
  fun `listValue() should produce Values with kindCase LIST_VALUE`() =
    verifyArbGeneratesValuesWithKindCase(
      Arb.proto.listValue().map { it.toValueProto() },
      Value.KindCase.LIST_VALUE
    )

  @Test
  fun `listValue() should specify the correct depth`() = runTest {
    checkAll(propTestConfig, Arb.proto.listValue()) { sample ->
      sample.depth shouldBe sample.listValue.maxDepth()
    }
  }

  @Test
  fun `listValue() should generate depths up to 3 for normal samples`() = runTest {
    val arb = Arb.proto.listValue()
    val depths = mutableSetOf<Int>()
    repeat(propTestConfig.iterations!!) { depths.add(arb.sample(rs).value.depth) }
    withClue("depths=${depths.sorted()}") { depths.shouldContainExactlyInAnyOrder(1, 2, 3) }
  }

  @Test
  fun `listValue() should generate depths up to 3 for edge cases`() = runTest {
    val arb = Arb.proto.listValue()
    val depths = mutableSetOf<Int>()
    repeat(propTestConfig.iterations!!) { depths.add(arb.edgecase(rs)!!.depth) }
    withClue("depths=${depths.sorted()}") { depths.shouldContainExactlyInAnyOrder(1, 2, 3) }
  }

  @Test
  fun `struct() should specify the correct depth`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct()) { sample ->
      sample.depth shouldBe sample.struct.maxDepth()
    }
  }

  @Test
  fun `struct() should generate depths up to 3 for normal samples`() = runTest {
    val arb = Arb.proto.struct()
    val depths = mutableSetOf<Int>()
    repeat(propTestConfig.iterations!!) { depths.add(arb.sample(rs).value.depth) }
    withClue("depths=${depths.sorted()}") { depths.shouldContainExactlyInAnyOrder(1, 2, 3) }
  }

  @Test
  fun `struct() should generate depths up to 3 for edge cases`() = runTest {
    val arb = Arb.proto.struct()
    val depths = mutableSetOf<Int>()
    repeat(propTestConfig.iterations!!) { depths.add(arb.edgecase(rs)!!.depth) }
    withClue("depths=${depths.sorted()}") { depths.shouldContainExactlyInAnyOrder(1, 2, 3) }
  }

  private fun verifyArbGeneratesValuesWithKindCase(
    arb: Arb<Value>,
    expectedKindCase: Value.KindCase
  ) = runTest {
    checkAll(propTestConfig, arb) { value -> value.kindCase shouldBe expectedKindCase }
  }

  private companion object {

    @OptIn(ExperimentalKotest::class)
    val propTestConfig =
      PropTestConfig(
        iterations = 1000,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.33),
        shrinkingMode = ShrinkingMode.Off,
      )

    fun ListValue.maxDepth(): Int {
      var maxDepth = 1
      repeat(valuesCount) {
        val curMaxDepth = getValues(it).maxDepth()
        if (curMaxDepth > maxDepth) {
          maxDepth = curMaxDepth
        }
      }
      return maxDepth
    }

    fun Struct.maxDepth(): Int {
      var maxDepth = 1
      fieldsMap.values.forEach { value ->
        val curMaxDepth = value.maxDepth()
        if (curMaxDepth > maxDepth) {
          maxDepth = curMaxDepth
        }
      }
      return maxDepth
    }

    fun Value.maxDepth(): Int =
      when (kindCase) {
        Value.KindCase.STRUCT_VALUE -> 1 + structValue.maxDepth()
        Value.KindCase.LIST_VALUE -> 1 + listValue.maxDepth()
        else -> 1
      }
  }
}
