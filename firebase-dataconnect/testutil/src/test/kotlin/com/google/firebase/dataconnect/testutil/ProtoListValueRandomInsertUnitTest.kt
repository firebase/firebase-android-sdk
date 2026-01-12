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

package com.google.firebase.dataconnect.testutil

import com.google.firebase.dataconnect.testutil.property.arbitrary.listValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.random
import com.google.firebase.dataconnect.testutil.property.arbitrary.value
import com.google.protobuf.ListValue
import com.google.protobuf.Value
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.PropertyContext
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ProtoListValueRandomInsertUnitTest {

  @Test
  fun `ListValue withRandomlyInsertedValue should insert the value`() = runTest {
    checkAll(
      propTestConfig,
      Arb.proto.listValue().map { it.listValue },
      Arb.proto.value(),
      Arb.random(),
    ) { listValue, value, random ->
      val result = listValue.withRandomlyInsertedValue(value, random)
      result.shouldBeListValueWithValueInserted(listValue, value)
    }
  }

  @Test
  fun `ListValue withRandomlyInsertedValue should use the given random`() = runTest {
    checkAll(propTestConfig, Arb.proto.listValue().map { it.listValue }, Arb.proto.value()) {
      listValue,
      value ->
      shouldUseGivenRandom { random -> listValue.withRandomlyInsertedValue(value, random) }
    }
  }

  @Test
  fun `ListValue Builder randomlyInsertValue should insert the value`() = runTest {
    checkAll(
      propTestConfig,
      Arb.proto.listValue().map { it.listValue },
      Arb.proto.value(),
      Arb.random()
    ) { listValue, value, random ->
      val listValueBuilder = listValue.toBuilder()

      listValueBuilder.randomlyInsertValue(value, random)

      listValueBuilder.build().shouldBeListValueWithValueInserted(listValue, value)
    }
  }

  @Test
  fun `ListValue Builder randomlyInsertValue should return the insertion path`() = runTest {
    checkAll(
      propTestConfig,
      Arb.proto.listValue().map { it.listValue },
      Arb.proto.value(),
      Arb.random()
    ) { listValue, value, random ->
      val listValueBuilder = listValue.toBuilder()

      val result = listValueBuilder.randomlyInsertValue(value, random)

      val expectedPath =
        listValueBuilder.build().shouldBeListValueWithValueInserted(listValue, value)
      result shouldBe expectedPath
    }
  }

  @Test
  fun `ListValue Builder randomlyInsertValue should use the given random`() = runTest {
    checkAll(propTestConfig, Arb.proto.listValue().map { it.listValue }, Arb.proto.value()) {
      listValue,
      value ->
      shouldUseGivenRandom { random -> listValue.toBuilder().randomlyInsertValue(value, random) }
    }
  }

  @Test
  fun `ListValue withRandomlyInsertedValues should insert the values`() = runTest {
    checkAll(
      propTestConfig,
      Arb.proto.listValue().map { it.listValue },
      Arb.int(0..3),
      Arb.random()
    ) { listValue, valueCount, random ->
      val values: List<Value> = List(valueCount) { Arb.proto.value().bind() }

      val result = listValue.withRandomlyInsertedValues(values, random)

      result.shouldBeListValueWithValuesInserted(listValue, values)
    }
  }

  @Test
  fun `ListValue withRandomlyInsertedValues should use the given random`() = runTest {
    checkAll(propTestConfig, Arb.proto.listValue().map { it.listValue }, Arb.int(0..3)) {
      listValue,
      valueCount ->
      val values: List<Value> = List(valueCount) { Arb.proto.value().bind() }
      shouldUseGivenRandom { random -> listValue.withRandomlyInsertedValues(values, random) }
    }
  }

  @Test
  fun `ListValue Builder randomlyInsertValues should insert the values`() = runTest {
    checkAll(
      propTestConfig,
      Arb.proto.listValue().map { it.listValue },
      Arb.int(0..3),
      Arb.random()
    ) { listValue, valueCount, random ->
      val values: List<Value> = List(valueCount) { Arb.proto.value().bind() }
      val listValueBuilder = listValue.toBuilder()

      listValueBuilder.randomlyInsertValues(values, random)

      listValueBuilder.build().shouldBeListValueWithValuesInserted(listValue, values)
    }
  }

  @Test
  fun `ListValue Builder randomlyInsertValues should return the insertion paths`() = runTest {
    checkAll(
      propTestConfig,
      Arb.proto.listValue().map { it.listValue },
      Arb.int(0..3),
      Arb.random()
    ) { listValue, valueCount, random ->
      val values: List<Value> = List(valueCount) { Arb.proto.value().bind() }
      val listValueBuilder = listValue.toBuilder()

      val result = listValueBuilder.randomlyInsertValues(values, random)

      val expectedPaths =
        listValueBuilder.build().shouldBeListValueWithValuesInserted(listValue, values)
      result shouldContainExactlyInAnyOrder expectedPaths
    }
  }

  @Test
  fun `ListValue Builder randomlyInsertValues should use the given random`() = runTest {
    checkAll(propTestConfig, Arb.proto.listValue().map { it.listValue }, Arb.int(0..3)) {
      listValue,
      valueCount ->
      val values: List<Value> = List(valueCount) { Arb.proto.value().bind() }
      shouldUseGivenRandom { random -> listValue.toBuilder().randomlyInsertValues(values, random) }
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

    private fun <T> PropertyContext.shouldUseGivenRandom(block: (Random) -> T) {
      val seed = randomSource().random.nextLong()
      val result1 = block(Random(seed))
      val result2 = block(Random(seed))
      withClue("random seed: $seed") { result1 shouldBe result2 }
    }

    fun ListValue.shouldBeListValueWithValueInserted(
      originalListValue: ListValue,
      insertedValue: Value
    ): DataConnectPath {
      val insertedValuePaths = findPathsWithValue(insertedValue, identityEqual)
      val insertedValuePath = insertedValuePaths.shouldHaveSize(1).single()
      val thisWithoutInsertedValue = map { path, value ->
        if (path == insertedValuePath) null else value
      }
      thisWithoutInsertedValue shouldBe originalListValue
      return insertedValuePath
    }

    fun ListValue.shouldBeListValueWithValuesInserted(
      originalListValue: ListValue,
      insertedValues: List<Value>
    ): List<DataConnectPath> {
      val insertedValuesPaths =
        insertedValues.map { insertedValue ->
          val insertedValuePaths = findPathsWithValue(insertedValue, identityEqual)
          insertedValuePaths.shouldHaveSize(1).single()
        }
      val thisWithoutInsertedValues = map { path, value ->
        if (insertedValuesPaths.contains(path)) null else value
      }
      thisWithoutInsertedValues shouldBe originalListValue
      return insertedValuesPaths
    }

    inline fun ListValue.findPathsWithValue(
      value: Value,
      crossinline isEqual: (Value, Value) -> Boolean
    ): Set<DataConnectPath> =
      walk().mapNotNull { (path, curValue) -> if (isEqual(value, curValue)) path else null }.toSet()

    val identityEqual: (Value, Value) -> Boolean = { value1, value2 -> value1 === value2 }
  }
}
