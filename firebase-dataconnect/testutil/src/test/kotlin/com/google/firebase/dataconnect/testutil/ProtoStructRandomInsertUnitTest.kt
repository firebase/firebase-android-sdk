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

import com.google.firebase.dataconnect.DataConnectPathSegment
import com.google.firebase.dataconnect.testutil.Difference.StructUnexpectedKey
import com.google.firebase.dataconnect.testutil.property.arbitrary.RememberArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.random
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.property.arbitrary.structKey
import com.google.firebase.dataconnect.testutil.property.arbitrary.value
import com.google.protobuf.Struct
import com.google.protobuf.Value
import io.kotest.assertions.asClue
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.PropertyContext
import io.kotest.property.RandomSource
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ProtoStructRandomInsertUnitTest {

  @Test
  fun `Struct withRandomlyInsertedValue should insert the value`() = runTest {
    checkAll(
      propTestConfig,
      Arb.proto.struct().map { it.struct },
      Arb.proto.value(),
      Arb.random()
    ) { struct, value, random ->
      val result = struct.withRandomlyInsertedValue(value, random, keyGenerator())
      result.shouldBeStructWithValueInserted(struct, value)
    }
  }

  @Test
  fun `Struct withRandomlyInsertedValue should use the given key generator`() = runTest {
    checkAll(
      propTestConfig,
      Arb.proto.struct().map { it.struct },
      Arb.proto.value(),
      Arb.random()
    ) { struct, value, random ->
      val structKeyArb = RememberArb(Arb.proto.structKey())

      val result = struct.withRandomlyInsertedValue(value, random, { structKeyArb.bind() })

      val insertPath = result.shouldBeStructWithValueInserted(struct, value)
      insertPath.shouldHaveFinalSegmentWithFieldIn(structKeyArb.generatedValues)
    }
  }

  @Test
  fun `Struct withRandomlyInsertedValue should use the given random`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct().map { it.struct }, Arb.proto.value(), Arb.long()) {
      struct,
      value,
      randomSeed ->
      shouldUseGivenRandom { random ->
        struct.withRandomlyInsertedValue(value, random, keyGenerator(randomSeed))
      }
    }
  }

  @Test
  fun `Struct Builder randomlyInsertValue should insert the value`() = runTest {
    checkAll(
      propTestConfig,
      Arb.proto.struct().map { it.struct },
      Arb.proto.value(),
      Arb.random()
    ) { struct, value, random ->
      val structBuilder = struct.toBuilder()

      structBuilder.randomlyInsertValue(value, random, keyGenerator())

      structBuilder.build().shouldBeStructWithValueInserted(struct, value)
    }
  }

  @Test
  fun `Struct Builder randomlyInsertValue should return the insertion path`() = runTest {
    checkAll(
      propTestConfig,
      Arb.proto.struct().map { it.struct },
      Arb.proto.value(),
      Arb.random()
    ) { struct, value, random ->
      val structBuilder = struct.toBuilder()

      val result = structBuilder.randomlyInsertValue(value, random, keyGenerator())

      val expectedPath = structBuilder.build().shouldBeStructWithValueInserted(struct, value)
      result shouldBe expectedPath
    }
  }

  @Test
  fun `Struct Builder randomlyInsertValue should use the given key generator`() = runTest {
    checkAll(
      propTestConfig,
      Arb.proto.struct().map { it.struct },
      Arb.proto.value(),
      Arb.random()
    ) { struct, value, random ->
      val structKeyArb = RememberArb(Arb.proto.structKey())
      val structBuilder = struct.toBuilder()

      val path = structBuilder.randomlyInsertValue(value, random, { structKeyArb.bind() })

      path.shouldHaveFinalSegmentWithFieldIn(structKeyArb.generatedValues)
    }
  }

  @Test
  fun `Struct Builder randomlyInsertValue should use the given random`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct().map { it.struct }, Arb.proto.value(), Arb.long()) {
      struct,
      value,
      randomSeed ->
      shouldUseGivenRandom { random ->
        struct.toBuilder().randomlyInsertValue(value, random, keyGenerator(randomSeed))
      }
    }
  }

  @Test
  fun `Struct withRandomlyInsertedValues should insert the values`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct().map { it.struct }, Arb.int(0..3), Arb.random()) {
      struct,
      valueCount,
      random ->
      val values: List<Value> = List(valueCount) { Arb.proto.value().bind() }

      val result = struct.withRandomlyInsertedValues(values, random, keyGenerator())

      result.shouldBeStructWithValuesInserted(struct, values)
    }
  }

  @Test
  fun `Struct withRandomlyInsertedValues should use the given key generator`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct().map { it.struct }, Arb.int(0..3), Arb.random()) {
      struct,
      valueCount,
      random ->
      val values: List<Value> = List(valueCount) { Arb.proto.value().bind() }
      val structKeyArb = RememberArb(Arb.proto.structKey())

      val result = struct.withRandomlyInsertedValues(values, random, { structKeyArb.bind() })

      val insertPaths = result.shouldBeStructWithValuesInserted(struct, values)
      insertPaths.forEachIndexed { index, insertPath ->
        withClue("insertPaths[$index]=$insertPath") {
          insertPath.shouldHaveFinalSegmentWithFieldIn(structKeyArb.generatedValues)
        }
      }
    }
  }

  @Test
  fun `Struct withRandomlyInsertedValues should use the given random`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct().map { it.struct }, Arb.int(0..3), Arb.long()) {
      struct,
      valueCount,
      randomSeed ->
      val values: List<Value> = List(valueCount) { Arb.proto.value().bind() }
      shouldUseGivenRandom { random ->
        struct.withRandomlyInsertedValues(values, random, keyGenerator(randomSeed))
      }
    }
  }

  @Test
  fun `Struct Builder randomlyInsertValues should insert the values`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct().map { it.struct }, Arb.int(0..3), Arb.random()) {
      struct,
      valueCount,
      random ->
      val values: List<Value> = List(valueCount) { Arb.proto.value().bind() }
      val structBuilder = struct.toBuilder()

      structBuilder.randomlyInsertValues(values, random, keyGenerator())

      structBuilder.build().shouldBeStructWithValuesInserted(struct, values)
    }
  }

  @Test
  fun `Struct Builder randomlyInsertValues should return the insertion paths`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct().map { it.struct }, Arb.int(0..3), Arb.random()) {
      struct,
      valueCount,
      random ->
      val values: List<Value> = List(valueCount) { Arb.proto.value().bind() }
      val structBuilder = struct.toBuilder()

      val result = structBuilder.randomlyInsertValues(values, random, keyGenerator())

      val expectedPaths = structBuilder.build().shouldBeStructWithValuesInserted(struct, values)
      result shouldContainExactlyInAnyOrder expectedPaths
    }
  }

  @Test
  fun `Struct Builder randomlyInsertValues should use the given key generator`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct().map { it.struct }, Arb.int(0..3), Arb.random()) {
      struct,
      valueCount,
      random ->
      val values: List<Value> = List(valueCount) { Arb.proto.value().bind() }
      val structKeyArb = RememberArb(Arb.proto.structKey())
      val structBuilder = struct.toBuilder()

      structBuilder.randomlyInsertValues(values, random, { structKeyArb.bind() })

      val insertPaths = structBuilder.build().shouldBeStructWithValuesInserted(struct, values)
      insertPaths.forEachIndexed { index, insertPath ->
        withClue("insertPaths[$index]=$insertPath") {
          insertPath.shouldHaveFinalSegmentWithFieldIn(structKeyArb.generatedValues)
        }
      }
    }
  }

  @Test
  fun `Struct Builder randomlyInsertValues should use the given random`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct().map { it.struct }, Arb.int(0..3), Arb.long()) {
      struct,
      valueCount,
      randomSeed ->
      val values: List<Value> = List(valueCount) { Arb.proto.value().bind() }
      shouldUseGivenRandom { random ->
        struct.toBuilder().randomlyInsertValues(values, random, keyGenerator(randomSeed))
      }
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

    fun PropertyContext.keyGenerator(): (() -> String) {
      val arb = Arb.proto.structKey()
      return { arb.bind() }
    }

    fun PropertyContext.keyGenerator(seed: Long): (() -> String) {
      val arb = Arb.proto.structKey()
      val rs = RandomSource.seeded(seed)
      return { arb.generate(rs, config.edgeConfig).first().value }
    }

    fun Struct.shouldBeStructWithValueInserted(
      originalStruct: Struct,
      insertedValue: Value
    ): DataConnectPath {
      val differences = structDiff(originalStruct, this).toList()
      withClue("differences=$differences") { differences shouldHaveSize 1 }
      differences.single().asClue { (path, difference) ->
        difference.shouldBeInstanceOf<StructUnexpectedKey>()
        difference.value shouldBeSameInstanceAs insertedValue
        return path.withAddedField(difference.key)
      }
    }

    fun Struct.shouldBeStructWithValuesInserted(
      originalStruct: Struct,
      insertedValues: List<Value>
    ): List<DataConnectPath> {
      val differences = structDiff(originalStruct, this).toList()
      withClue("differences.size=${differences.size}, differences=$differences") {
        differences shouldHaveSize insertedValues.size

        val unexpectedKeyDifferences =
          differences
            .mapIndexed { index, (path, difference) ->
              val unexpectedKeyDifference =
                withClue("differenceIndex=$index") {
                  difference.shouldBeInstanceOf<StructUnexpectedKey>()
                }
              DifferencePathPair(path, unexpectedKeyDifference)
            }
            .toMutableList()

        val insertPaths: MutableList<DataConnectPath> = mutableListOf()
        insertedValues.forEach { insertedValue ->
          val unexpectedKeyDifferencesIndex =
            unexpectedKeyDifferences.indexOfFirst { it.difference.value === insertedValue }
          withClue(
            "insertedValue=$insertedValue, unexpectedKeyDifferences=$unexpectedKeyDifferences"
          ) {
            unexpectedKeyDifferencesIndex shouldBeGreaterThanOrEqualTo 0
          }
          val (path, difference) = unexpectedKeyDifferences.removeAt(unexpectedKeyDifferencesIndex)
          insertPaths.add(path.withAddedField(difference.key))
        }

        return insertPaths.toList()
      }
    }

    fun DataConnectPath.shouldHaveFinalSegmentWithFieldIn(expectedFields: Collection<String>) {
      withClue("path=${this.toPathString()}") {
        val finalSegment = lastOrNull().shouldBeInstanceOf<DataConnectPathSegment.Field>()
        withClue("finalSegment=$finalSegment") { expectedFields shouldContain finalSegment.field }
      }
    }

    private fun <T> PropertyContext.shouldUseGivenRandom(block: (Random) -> T) {
      val seed = randomSource().random.nextLong()
      val result1 = block(Random(seed))
      val result2 = block(Random(seed))
      withClue("random seed: $seed") { result1 shouldBe result2 }
    }
  }
}
