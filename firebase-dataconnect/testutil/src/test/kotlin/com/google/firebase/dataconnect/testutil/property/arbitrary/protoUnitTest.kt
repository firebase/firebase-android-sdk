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

import com.google.firebase.dataconnect.DataConnectPathSegment
import com.google.firebase.dataconnect.testutil.DataConnectPathValuePair
import com.google.firebase.dataconnect.testutil.RandomSeedTestRule
import com.google.firebase.dataconnect.testutil.isListValue
import com.google.firebase.dataconnect.testutil.isRecursivelyEmpty
import com.google.firebase.dataconnect.testutil.isStructValue
import com.google.firebase.dataconnect.testutil.listValueOrNull
import com.google.firebase.dataconnect.testutil.map
import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.firebase.dataconnect.testutil.toPathString
import com.google.firebase.dataconnect.testutil.toValueProto
import com.google.firebase.dataconnect.testutil.walk
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.collections.shouldStartWith
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.maps.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.Exhaustive
import io.kotest.property.PropTestConfig
import io.kotest.property.RandomSource
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.intRange
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.negativeInt
import io.kotest.property.arbitrary.nonNegativeInt
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
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
  fun `scalarValue() should only produce scalar values`() = runTest {
    checkAll(propTestConfig, Arb.proto.scalarValue()) { value ->
      value.kindCase shouldBeIn scalarKindCases
    }
  }

  @Test
  fun `scalarValue() should produce all scalar value kind cases`() = runTest {
    val encounteredKindCases = mutableSetOf<Value.KindCase>()
    checkAll(propTestConfig, Arb.proto.scalarValue()) { value ->
      encounteredKindCases.add(value.kindCase)
    }
    encounteredKindCases shouldContainExactlyInAnyOrder scalarKindCases
  }

  @Test
  fun `scalarValue() should exclude the given kind case`() = runTest {
    checkAll(propTestConfig, Exhaustive.collection(scalarKindCases)) { kindCase ->
      val scalarValueArb = Arb.proto.scalarValue(exclude = kindCase)
      val encounteredKindCases =
        List(propTestConfig.iterations!!) { scalarValueArb.bind().kindCase }
      val expectedEncounteredKindCases = scalarKindCases.filterNot { it == kindCase }
      encounteredKindCases.distinct() shouldContainExactlyInAnyOrder expectedEncounteredKindCases
    }
  }

  @Test
  fun `listValue() should produce Values with kindCase LIST_VALUE`() =
    verifyArbGeneratesValuesWithKindCase(
      Arb.proto.listValue().map { it.toValueProto() },
      Value.KindCase.LIST_VALUE
    )

  @Test
  fun `listValue() Sample should specify the correct depth`() = runTest {
    checkAll(propTestConfig, Arb.proto.listValue()) { sample ->
      sample.depth shouldBe sample.listValue.maxDepth()
    }
  }

  @Test
  fun `listValue() Sample should specify the correct descendants`() = runTest {
    checkAll(propTestConfig, Arb.proto.listValue()) { sample ->
      val listValue: ListValue = sample.listValue
      val expectedDescendants = listValue.toValueProto().calculateExpectedDescendants()
      sample.descendants shouldContainExactlyInAnyOrder expectedDescendants
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
  fun `listValue() should generate nested structs and lists`() = runTest {
    var nestedListCount = 0
    var nestedStructCount = 0

    checkAll(propTestConfig, Arb.proto.listValue()) { sample ->
      if (sample.listValue.hasNestedKind(Value.KindCase.LIST_VALUE)) {
        nestedListCount++
      }
      if (sample.listValue.hasNestedKind(Value.KindCase.STRUCT_VALUE)) {
        nestedStructCount++
      }
    }

    assertSoftly {
      withClue("nestedListCount") { nestedListCount shouldBeGreaterThan 0 }
      withClue("nestedStructCount") { nestedStructCount shouldBeGreaterThan 0 }
    }
  }

  @Test
  fun `struct() Sample should specify the correct depth`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct()) { sample ->
      sample.depth shouldBe sample.struct.maxDepth()
    }
  }

  @Test
  fun `struct() Sample should specify the correct descendants`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct()) { sample ->
      val struct: Struct = sample.struct
      val expectedDescendants = struct.toValueProto().calculateExpectedDescendants()
      sample.descendants shouldContainExactlyInAnyOrder expectedDescendants
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

  @Test
  fun `struct() should generate nested structs and lists`() = runTest {
    var nestedListCount = 0
    var nestedStructCount = 0

    checkAll(propTestConfig, Arb.proto.struct()) { sample ->
      if (sample.struct.hasNestedKind(Value.KindCase.LIST_VALUE)) {
        nestedListCount++
      }
      if (sample.struct.hasNestedKind(Value.KindCase.STRUCT_VALUE)) {
        nestedStructCount++
      }
    }

    assertSoftly {
      withClue("nestedListCount") { nestedListCount shouldBeGreaterThan 0 }
      withClue("nestedStructCount") { nestedStructCount shouldBeGreaterThan 0 }
    }
  }

  @Test
  fun `valueOfKind() should generate same values when given same random seed`() {
    Value.KindCase.entries.forEach { kindCase ->
      verifyArbGeneratesSameValuesWithSameRandomSeed(Arb.proto.valueOfKind(kindCase))
    }
  }

  @Test
  fun `value() should generate same values when given same random seed`() =
    verifyArbGeneratesSameValuesWithSameRandomSeed(Arb.proto.value())

  @Test
  fun `nullValue() should generate same values when given same random seed`() =
    verifyArbGeneratesSameValuesWithSameRandomSeed(Arb.proto.nullValue())

  @Test
  fun `numberValue() should generate same values when given same random seed`() =
    verifyArbGeneratesSameValuesWithSameRandomSeed(Arb.proto.numberValue())

  @Test
  fun `boolValue() should generate same values when given same random seed`() =
    verifyArbGeneratesSameValuesWithSameRandomSeed(Arb.proto.boolValue())

  @Test
  fun `stringValue() should generate same values when given same random seed`() =
    verifyArbGeneratesSameValuesWithSameRandomSeed(Arb.proto.stringValue())

  @Test
  fun `kindNotSetValue() should generate same values when given same random seed`() =
    verifyArbGeneratesSameValuesWithSameRandomSeed(Arb.proto.kindNotSetValue())

  @Test
  fun `scalarValue() should generate same values when given same random seed`() =
    verifyArbGeneratesSameValuesWithSameRandomSeed(Arb.proto.scalarValue())

  @Test
  fun `listValue() should generate same values when given same random seed`() =
    verifyArbGeneratesSameValuesWithSameRandomSeed(Arb.proto.listValue())

  @Test
  fun `structKey() should generate same values when given same random seed`() =
    verifyArbGeneratesSameValuesWithSameRandomSeed(Arb.proto.structKey())

  @Test
  fun `struct() should generate same values when given same random seed`() =
    verifyArbGeneratesSameValuesWithSameRandomSeed(Arb.proto.struct())

  @Test
  fun `recursivelyEmptyListValue() should generate recursively empty ListValue values`() = runTest {
    checkAll(propTestConfig, Arb.proto.recursivelyEmptyListValue()) { sample ->
      sample.listValue.isRecursivelyEmpty() shouldBe true
    }
  }

  @Test
  fun `recursivelyEmptyListValue() should respect the given size`() = runTest {
    checkAll(propTestConfig, Arb.intRange(0..8).filterNot { it.isEmpty() }) { sizeRange ->
      val arb = Arb.proto.recursivelyEmptyListValue(size = sizeRange, depth = 2..5)
      val listValue = arb.bind().listValue
      listValue.walk(includeSelf = true).forEach { (path, value) ->
        withClue("path=$path") {
          val listSize = value.listValueOrNull.shouldNotBeNull().valuesCount
          if (listSize != 0) {
            listSize shouldBeInRange sizeRange
          }
        }
      }
    }
  }

  @Test
  fun `recursivelyEmptyListValue() should respect the given depth`() = runTest {
    checkAll(propTestConfig, Arb.intRange(1..8).filterNot { it.isEmpty() }) { depthRange ->
      val arb = Arb.proto.recursivelyEmptyListValue(size = 1..5, depth = depthRange)
      val listValue = arb.bind().listValue
      listValue.maxDepth() shouldBeInRange depthRange
    }
  }

  @Test
  fun `recursivelyEmptyListValue() should use the given Random`() =
    verifyArbGeneratesSameValuesWithSameRandomSeed(Arb.proto.recursivelyEmptyListValue())

  @Test
  fun `recursivelyEmptyListValue() should never generate edge cases from sample()`() = runTest {
    checkAll(propTestConfig, Arb.randomSource()) { rs ->
      val sample = Arb.proto.recursivelyEmptyListValue().sample(rs).value
      sample.edgeCases.shouldBeEmpty()
    }
  }

  @Test
  fun `recursivelyEmptyListValue() should always generate edge cases from edgecase()`() = runTest {
    checkAll(propTestConfig, Arb.randomSource()) { rs ->
      val sample = Arb.proto.recursivelyEmptyListValue().edgecase(rs)
      sample.shouldNotBeNull().edgeCases.shouldNotBeEmpty()
    }
  }

  @Test
  fun `structNestedInStructKeys uses the given nestingRange`() = runTest {
    checkAll(propTestConfig, Arb.intRange(1..5).filterNot { it.isEmpty() }) { nestingRange ->
      val arb = Arb.proto.structNestedInStructKeys(nestingRange = nestingRange)

      val sample = arb.bind()

      sample.childStructs.size shouldBeInRange nestingRange
    }
  }

  @Test
  fun `structNestedInStructKeys throws if nestingRange has invalid first`() = runTest {
    checkAll(propTestConfig, Arb.negativeInt(), Arb.nonNegativeInt()) { first, last ->
      shouldThrow<IllegalArgumentException> {
        Arb.proto.structNestedInStructKeys(nestingRange = first..last)
      }
    }
  }

  @Test
  fun `structNestedInStructKeys throws if nestingRange is empty`() = runTest {
    checkAll(propTestConfig, Arb.nonNegativeInt()) { first ->
      shouldThrow<IllegalArgumentException> {
        Arb.proto.structNestedInStructKeys(nestingRange = first until first)
      }
    }
  }

  @Test
  fun `structNestedInStructKeys uses the given Struct Arb`() = runTest {
    val structArb =
      Arb.proto.struct().map { sample ->
        val modifiedStruct =
          sample.struct.toBuilder().putFields("foo", "bar".toValueProto()).build()
        ProtoArb.StructInfo(modifiedStruct, sample.depth, sample.descendants)
      }
    checkAll(propTestConfig, Arb.proto.structNestedInStructKeys(structArb = structArb)) { sample ->
      assertSoftly {
        withClue("prunedStruct") {
          sample.prunedStruct.fieldsMap["foo"].shouldNotBeNull() shouldBe "bar".toValueProto()
        }
        sample.childStructs.entries.forEach { (path, childStruct) ->
          withClue("childStructs[${path.toPathString()}]") {
            childStruct.fieldsMap["foo"].shouldNotBeNull() shouldBe "bar".toValueProto()
          }
        }
      }
    }
  }

  @Test
  fun `structNestedInStructKeys uses the given Struct key Arb`() = runTest {
    checkAll(propTestConfig, Arb.proto.structKey()) { prefix ->
      val structKeyArb = Arb.proto.structKey().map { prefix + it }
      val arb = Arb.proto.structNestedInStructKeys(structKeyArb = structKeyArb)

      val sample = arb.bind()

      assertSoftly {
        sample.childStructs.keys.forEach { path ->
          withClue("path=${path.toPathString()}") {
            path
              .shouldNotBeEmpty()
              .last()
              .shouldBeInstanceOf<DataConnectPathSegment.Field>()
              .field
              .shouldStartWith(prefix)
          }
        }
      }
    }
  }

  @Test
  fun `structNestedInStructKeys childStructs should not be empty`() = runTest {
    checkAll(propTestConfig, Arb.intRange(1..5).filterNot { it.isEmpty() }) { nestingRange ->
      val arb = Arb.proto.structNestedInStructKeys(nestingRange = nestingRange)

      val sample = arb.bind()

      sample.childStructs.shouldNotBeEmpty()
    }
  }

  @Test
  fun `structNestedInStructKeys childStructs keys should all be nested`() = runTest {
    checkAll(propTestConfig, Arb.proto.structNestedInStructKeys(nestingRange = 2..5)) { sample ->
      val pathsSortedBySize = sample.childStructs.keys.sortedBy { it.size }
      val pathWindows = pathsSortedBySize.windowed(2)
      check(pathWindows.isNotEmpty())

      assertSoftly {
        withClue("pathsSortedBySize=${pathsSortedBySize.joinToString { it.toPathString() }}") {
          pathWindows.forEachIndexed { index, (parentPath, childPath) ->
            withClue("index=$index") { childPath shouldStartWith parentPath }
          }
        }
      }
    }
  }

  @Test
  fun `structNestedInStructKeys prunedStruct is the expected Struct`() = runTest {
    checkAll(propTestConfig, Arb.proto.structNestedInStructKeys()) { sample ->
      val expectedPrunedStruct =
        sample.aggregatedStruct.map { path, value ->
          if (path in sample.childStructs) null else value
        }

      sample.prunedStruct shouldBe expectedPrunedStruct
    }
  }

  @Test
  fun `structNestedInStructKeys aggregatedStruct is the expected Struct`() = runTest {
    checkAll(propTestConfig, Arb.proto.structNestedInStructKeys()) { sample ->
      data class InsertInfo(val field: String, val struct: Struct)
      val insertInfoByParent =
        sample.childStructs.entries
          .associate { (path, struct) ->
            val parentPath = path.dropLast(1)
            val field = path.last().shouldBeInstanceOf<DataConnectPathSegment.Field>().field
            parentPath to InsertInfo(field, struct)
          }
          .toMutableMap()
      var expectedAggregatedStruct = sample.prunedStruct
      while (insertInfoByParent.isNotEmpty()) {
        val insertInfoByParentSizeBefore = insertInfoByParent.size
        expectedAggregatedStruct =
          expectedAggregatedStruct.map { path, value ->
            val insertInfo = insertInfoByParent.remove(path)
            if (insertInfo === null) {
              value
            } else {
              value.structValue
                .toBuilder()
                .putFields(insertInfo.field, insertInfo.struct.toValueProto())
                .build()
                .toValueProto()
            }
          }
        withClue("insertInfoByParent=$insertInfoByParent") {
          insertInfoByParent.size shouldNotBe insertInfoByParentSizeBefore
        }
      }
      sample.aggregatedStruct shouldBe expectedAggregatedStruct
    }
  }

  private fun verifyArbGeneratesValuesWithKindCase(
    arb: Arb<Value>,
    expectedKindCase: Value.KindCase
  ) = runTest {
    checkAll(propTestConfig, arb) { value -> value.kindCase shouldBe expectedKindCase }
  }

  private fun verifyArbGeneratesSameValuesWithSameRandomSeed(arb: Arb<*>) = runTest {
    val edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.33)
    checkAll(propTestConfig.withIterations(10), Arb.long(), Arb.int(1..20)) { seed, count ->
      fun generateSamples() =
        arb.generate(RandomSource.seeded(seed), edgeConfig).map { it.value }.take(count).toList()
      val samples1 = generateSamples()
      val samples2 = generateSamples()
      samples1 shouldContainExactly samples2
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

    val scalarKindCases: List<Value.KindCase> =
      listOf(
        Value.KindCase.NULL_VALUE,
        Value.KindCase.BOOL_VALUE,
        Value.KindCase.NUMBER_VALUE,
        Value.KindCase.STRING_VALUE,
        Value.KindCase.KIND_NOT_SET,
      )

    fun ListValue.hasNestedKind(kindCase: Value.KindCase): Boolean {
      repeat(valuesCount) {
        val value = getValues(it)
        if (value.hasNestedKind(kindCase)) {
          return true
        }
      }
      return false
    }

    fun Struct.hasNestedKind(kindCase: Value.KindCase): Boolean {
      fieldsMap.values.forEach { value ->
        if (value.hasNestedKind(kindCase)) {
          return true
        }
      }
      return false
    }

    fun Value.hasNestedKind(kindCase: Value.KindCase): Boolean =
      when (this.kindCase) {
        kindCase -> true
        Value.KindCase.LIST_VALUE -> listValue.hasNestedKind(kindCase)
        Value.KindCase.STRUCT_VALUE -> structValue.hasNestedKind(kindCase)
        else -> false
      }

    fun Value.calculateExpectedDescendants(): List<DataConnectPathValuePair> = buildList {
      val queue: MutableList<DataConnectPathValuePair> = mutableListOf()
      queue.add(DataConnectPathValuePair(emptyList(), this@calculateExpectedDescendants))

      while (queue.isNotEmpty()) {
        val entry = queue.removeFirst()
        val (path, value) = entry
        if (path.isNotEmpty()) {
          add(entry)
        }

        if (value.isStructValue) {
          value.structValue.fieldsMap.entries.forEach { (key, childValue) ->
            val childPath = buildList {
              addAll(path)
              add(DataConnectPathSegment.Field(key))
            }
            queue.add(DataConnectPathValuePair(childPath, childValue))
          }
        } else if (value.isListValue) {
          value.listValue.valuesList.forEachIndexed { index, childValue ->
            val childPath = buildList {
              addAll(path)
              add(DataConnectPathSegment.ListIndex(index))
            }
            queue.add(DataConnectPathValuePair(childPath, childValue))
          }
        }
      }
    }
  }
}
