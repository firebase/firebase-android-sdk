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

@file:OptIn(DelicateKotest::class)

package com.google.firebase.dataconnect.testutil

import com.google.firebase.dataconnect.DataConnectPathSegment as PathComponent
import com.google.firebase.dataconnect.testutil.property.arbitrary.distinctPair
import com.google.firebase.dataconnect.testutil.property.arbitrary.listValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.numberValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.scalarValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.stringValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.property.arbitrary.structKey
import com.google.firebase.dataconnect.testutil.property.arbitrary.value
import com.google.firebase.dataconnect.testutil.property.arbitrary.valueOfKind
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import io.kotest.assertions.asClue
import io.kotest.common.DelicateKotest
import io.kotest.common.ExperimentalKotest
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.PropertyContext
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.assume
import io.kotest.property.checkAll
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ProtoTestUtilsUnitTest {

  @Test
  fun `allDescendants Value`() = runTest {
    val arb =
      Arb.choice(
        Arb.proto.listValue().map { it.toValueProto() },
        Arb.proto.struct().map { it.toValueProto() },
        Arb.proto.scalarValue(),
      )
    checkAll(propTestConfig, arb) { value ->
      val expectedAllDescendantsReturnValue =
        when (value.kindCase) {
          Value.KindCase.STRUCT_VALUE -> value.structValue.allDescendants()
          Value.KindCase.LIST_VALUE -> value.listValue.allDescendants()
          else -> emptyList()
        }

      value.allDescendants() shouldContainExactlyInAnyOrder expectedAllDescendantsReturnValue
    }
  }

  @Test
  fun `allDescendants ListValue of scalar values`() = runTest {
    checkAll(propTestConfig, Arb.list(Arb.proto.scalarValue(), 0..20)) { values ->
      val listValue: ListValue = values.toListValue()
      listValue.allDescendants() shouldContainExactlyInAnyOrder values
    }
  }

  @Test
  fun `allDescendants ListValue of composite values`() = runTest {
    checkAll(propTestConfig, Arb.proto.listValue()) { sample ->
      sample.listValue.allDescendants() shouldContainExactlyInAnyOrder sample.descendantValues
    }
  }

  @Test
  fun `allDescendants Struct of scalar values`() = runTest {
    checkAll(propTestConfig, Arb.list(Arb.proto.scalarValue(), 0..20)) { values ->
      val struct: Struct = structWithValues(values)
      struct.allDescendants() shouldContainExactlyInAnyOrder values
    }
  }

  @Test
  fun `allDescendants Struct of composite values`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct()) { sample ->
      sample.struct.allDescendants() shouldContainExactlyInAnyOrder sample.descendantValues
    }
  }

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
      val originalValues = sample.struct.allDescendants()
      val deepCopyValues = sample.struct.deepCopy().allDescendants()
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
      val originalValues = sample.listValue.allDescendants()
      val deepCopyValues = sample.listValue.deepCopy().allDescendants()
      deepCopiedValuesShouldBeDistinctInstances(originalValues, deepCopyValues)
    }
  }

  @Test
  fun `deepCopy Value should produce an equal instance`() = runTest {
    checkAll(propTestConfig, Arb.proto.value()) { value -> value.deepCopy() shouldBe value }
  }

  @Test
  fun `deepCopy Value should return a different instance than the receiver`() = runTest {
    checkAll(propTestConfig, Arb.proto.value()) { value ->
      value.deepCopy() shouldNotBeSameInstanceAs value
    }
  }

  @Test
  fun `deepCopy Value should deep clone recursively`() = runTest {
    checkAll(propTestConfig, Arb.proto.value()) { value ->
      val originalValues = value.allDescendants()
      val deepCopyValues = value.deepCopy().allDescendants()
      deepCopiedValuesShouldBeDistinctInstances(originalValues, deepCopyValues)
    }
  }

  @Test
  fun `structDiff,structFastEqual for same instance`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct()) { sample ->
      val struct = sample.struct
      structFastEqual(struct, struct) shouldBe true
      structDiff(struct, struct).size shouldBe 0
    }
  }

  @Test
  fun `structDiff,structFastEqual for distinct, but equal instance`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct()) { sample ->
      val struct1 = sample.struct
      val struct2 = struct1.deepCopy()
      structFastEqual(struct1, struct2) shouldBe true
      structDiff(struct1, struct2).size shouldBe 0
    }
  }

  private suspend fun verifyStructDiffReturnsDifferences(
    structArb: Arb<Struct>,
    prepare:
      suspend PropertyContext.(
        struct1: Struct, keyCount: Int, expectedDifferences: MutableList<DifferencePathPair<*>>
      ) -> Struct,
  ) {
    checkAll(propTestConfig, structArb, Arb.int(1..5)) { struct1, keyCount ->
      val mutableDifferences = mutableListOf<DifferencePathPair<*>>()
      val struct2 = prepare(struct1, keyCount, mutableDifferences)
      val expectedDifferences = mutableDifferences.toList()

      structFastEqual(struct1, struct2) shouldBe false
      structFastEqual(struct2, struct1) shouldBe false

      structDiff(struct1, struct2).asClue { differences ->
        differences.toList() shouldContainExactlyInAnyOrder expectedDifferences
      }
      structDiff(struct2, struct1).asClue { differences ->
        differences.toList() shouldContainExactlyInAnyOrder
          expectedDifferences.withInvertedDifferences()
      }
    }
  }

  @Test
  fun `structDiff,structFastEqual for keys added to structs`() = runTest {
    val valueArb = Arb.proto.value()
    val structKeyArb = Arb.proto.structKey()
    val structArb = Arb.proto.struct(key = structKeyArb).map { it.struct }

    verifyStructDiffReturnsDifferences(structArb) { struct1, keyCount, expectedDifferences ->
      val structPaths = buildSet {
        add(emptyList())
        struct1.allDescendantPaths().forEach { path ->
          if (path.lastOrNull() is PathComponent.Field) {
            add(path.dropLast(1))
          }
        }
      }
      val valuesToAddByPath: Map<List<PathComponent>, MutableList<Value>> = buildMap {
        repeat(keyCount) {
          val path = structPaths.random(randomSource().random)
          getOrPut(path) { mutableListOf() }.add(valueArb.bind())
        }
      }
      check(valuesToAddByPath.values.flatten().size == keyCount)
      struct1.map(includeSelf = true) { path, value ->
        val valuesToAdd = valuesToAddByPath[path]
        if (valuesToAdd === null) {
          value
        } else {
          value.structValue.toBuilder().let { structBuilder ->
            val myStructKeyArb = structKeyArb.filterNot(structBuilder::containsFields)
            valuesToAdd.forEach { valueToAdd ->
              val keyToAdd = myStructKeyArb.bind()
              expectedDifferences.add(
                DifferencePathPair(
                  path,
                  Difference.StructUnexpectedKey(keyToAdd, valueToAdd),
                )
              )
              structBuilder.putFields(keyToAdd, valueToAdd)
            }
            structBuilder.build().toValueProto()
          }
        }
      }
    }
  }

  @Test
  fun `structDiff,structFastEqual for keys removed`() = runTest {
    val structArb = Arb.proto.struct(size = 1..5).map { it.struct }
    verifyStructDiffReturnsDifferences(structArb) { struct1, keyCount, expectedDifferences ->
      val replaceResult =
        replaceRandomValues(
          struct1,
          keyCount,
          filter = { path, _ -> path.lastOrNull() is PathComponent.Field },
          replacementValue = { _, _ -> null },
        )
      assume(replaceResult.replacements.isNotEmpty())

      val differences =
        replaceResult.replacements.map { replacement ->
          DifferencePathPair(
            replacement.path.dropLast(1),
            Difference.StructMissingKey(
              (replacement.path.last() as PathComponent.Field).field,
              replacement.oldValue
            )
          )
        }

      expectedDifferences.addAll(differences)
      replaceResult.newItem
    }
  }

  @Test
  fun `structDiff,structFastEqual for KindCase`() = runTest {
    val valueArb = Arb.proto.value()
    val structKeyArb = Arb.proto.structKey()
    val structArb = Arb.proto.struct(size = 1..5, key = structKeyArb).map { it.struct }

    verifyStructDiffReturnsDifferences(structArb) { struct1, keyCount, expectedDifferences ->
      val replaceResult =
        replaceRandomValues(
          struct1,
          keyCount,
          replacementValue = { _, oldValue ->
            valueArb.filterNot { it.kindCase == oldValue.kindCase }.bind()
          }
        )

      val differences =
        replaceResult.replacements.map { replacement ->
          DifferencePathPair(
            replacement.path,
            Difference.KindCase(replacement.oldValue, replacement.newValue)
          )
        }

      expectedDifferences.addAll(differences)
      replaceResult.newItem
    }
  }

  @Test
  fun `structDiff,structFastEqual for BoolValue, NumberValue, StringValue`() = runTest {
    val structArb = Arb.proto.struct(size = 1..5).map { it.struct }

    verifyStructDiffReturnsDifferences(structArb) { struct1, keyCount, expectedDifferences ->
      val replaceResult =
        replaceRandomValues(
          struct1,
          keyCount,
          filter = { _, value -> value.isBoolNumberOrString() },
          replacementValue = { _, oldValue -> unequalValueOfSameKind(oldValue) },
        )
      assume(replaceResult.replacements.isNotEmpty())
      expectedDifferences.addAllUnequalValueOfSameKindDifferences(replaceResult.replacements)
      replaceResult.newItem
    }
  }

  @Test
  fun `listValueDiff,listValueFastEqual for same instance`() = runTest {
    checkAll(propTestConfig, Arb.proto.listValue()) { sample ->
      listValueFastEqual(sample.listValue, sample.listValue) shouldBe true
      listValueDiff(sample.listValue, sample.listValue).size shouldBe 0
    }
  }

  @Test
  fun `listValueDiff,listValueFastEqual for distinct, but equal instance`() = runTest {
    checkAll(propTestConfig, Arb.proto.listValue()) { sample ->
      val listValue1 = sample.listValue
      val listValue2 = listValue1.deepCopy()
      listValueFastEqual(listValue1, listValue2) shouldBe true
      listValueDiff(listValue1, listValue2).size shouldBe 0
    }
  }

  private suspend fun verifyListValueDiffReturnsDifferences(
    listValueArb: Arb<ListValue>,
    prepare:
      suspend PropertyContext.(
        listValue1: ListValue,
        itemCount: Int,
        expectedDifferences: MutableList<DifferencePathPair<*>>,
      ) -> ListValue,
  ) {
    checkAll(propTestConfig, listValueArb, Arb.int(1..5)) { listValue1, itemCount ->
      val mutableDifferences = mutableListOf<DifferencePathPair<*>>()
      val listValue2 = prepare(listValue1, itemCount, mutableDifferences)
      val expectedDifferences = mutableDifferences.toList()

      listValueFastEqual(listValue1, listValue2) shouldBe false
      listValueFastEqual(listValue2, listValue1) shouldBe false

      listValueDiff(listValue1, listValue2).asClue { differences ->
        differences.toList() shouldContainExactlyInAnyOrder expectedDifferences
      }
      listValueDiff(listValue2, listValue1).asClue { differences ->
        differences.toList() shouldContainExactlyInAnyOrder
          expectedDifferences.withInvertedDifferences()
      }
    }
  }

  @Test
  fun `listValueDiff,listValueFastEqual for KindCase`() = runTest {
    val valueArb = Arb.proto.value()
    val listValueArb = Arb.proto.listValue(length = 1..5).map { it.listValue }

    verifyListValueDiffReturnsDifferences(listValueArb) { listValue1, itemCount, expectedDifferences
      ->
      val replaceResult =
        replaceRandomValues(
          listValue1,
          itemCount,
          replacementValue = { _, oldValue ->
            valueArb.filterNot { it.kindCase == oldValue.kindCase }.bind()
          }
        )

      val differences =
        replaceResult.replacements.map { replacement ->
          DifferencePathPair(
            replacement.path,
            Difference.KindCase(replacement.oldValue, replacement.newValue)
          )
        }

      expectedDifferences.addAll(differences)
      replaceResult.newItem
    }
  }

  @Test
  fun `listValueDiff,listValueFastEqual for values added to the end of a list`() = runTest {
    val valueArb = Arb.proto.value()
    val listValueArb = Arb.proto.listValue(length = 1..5).map { it.listValue }

    verifyListValueDiffReturnsDifferences(listValueArb) { listValue1, itemCount, expectedDifferences
      ->
      val listPaths = buildSet {
        add(emptyList())
        listValue1.allDescendantPaths().forEach { path ->
          if (path.lastOrNull() is PathComponent.ListIndex) {
            add(path.dropLast(1))
          }
        }
      }
      val valuesToAddByPath: Map<List<PathComponent>, MutableList<Value>> = buildMap {
        repeat(itemCount) {
          val path = listPaths.random(randomSource().random)
          getOrPut(path) { mutableListOf() }.add(valueArb.bind())
        }
      }
      check(valuesToAddByPath.values.flatten().size == itemCount)
      listValue1.map(includeSelf = true) { path, value ->
        val valuesToAdd = valuesToAddByPath[path]
        if (valuesToAdd === null) {
          value
        } else {
          value.listValue.toBuilder().let { listValueBuilder ->
            valuesToAdd.forEach { valueToAdd ->
              expectedDifferences.add(
                DifferencePathPair(
                  path,
                  Difference.ListUnexpectedElement(listValueBuilder.valuesCount, valueToAdd),
                )
              )
              listValueBuilder.addValues(valueToAdd)
            }
            listValueBuilder.build().toValueProto()
          }
        }
      }
    }
  }

  @Test
  fun `listValueDiff,listValueFastEqual for values removed from the end of the root list`() =
    runTest {
      val listValueArb = Arb.proto.listValue(length = 1..5).map { it.listValue }

      verifyListValueDiffReturnsDifferences(listValueArb) {
        listValue1,
        itemCount,
        expectedDifferences ->
        listValue1.toBuilder().let { listValueBuilder ->
          var i = itemCount
          while (listValueBuilder.valuesCount > 0 && i > 0) {
            i--
            val removeIndex = listValueBuilder.valuesCount - 1
            val oldValue = listValueBuilder.getValues(removeIndex)
            listValueBuilder.removeValues(removeIndex)
            expectedDifferences.add(
              DifferencePathPair(
                path = emptyList(),
                difference = Difference.ListMissingElement(removeIndex, oldValue),
              )
            )
          }
          listValueBuilder.build()
        }
      }
    }

  @Test
  fun `listValueDiff,listValueFastEqual for BoolValue, NumberValue, StringValue`() = runTest {
    val listValueArb = Arb.proto.listValue(length = 1..5).map { it.listValue }

    verifyListValueDiffReturnsDifferences(listValueArb) { listValue1, itemCount, expectedDifferences
      ->
      val replaceResult =
        replaceRandomValues(
          listValue1,
          itemCount,
          filter = { _, value -> value.isBoolNumberOrString() },
          replacementValue = { _, oldValue -> unequalValueOfSameKind(oldValue) },
        )
      assume(replaceResult.replacements.isNotEmpty())
      expectedDifferences.addAllUnequalValueOfSameKindDifferences(replaceResult.replacements)
      replaceResult.newItem
    }
  }

  @Test
  fun `valueDiff,valueFastEqual for same instance`() = runTest {
    checkAll(propTestConfig, Arb.proto.value()) { value ->
      valueFastEqual(value, value) shouldBe true
      valueDiff(value, value).size shouldBe 0
    }
  }

  @Test
  fun `valueDiff,valueFastEqual for distinct, but equal instance`() = runTest {
    checkAll(propTestConfig, Arb.proto.value()) { value ->
      val valueCopy = value.deepCopy()
      valueFastEqual(value, valueCopy) shouldBe true
      valueDiff(value, valueCopy).size shouldBe 0
    }
  }

  @Test
  fun `valueDiff,valueFastEqual for boolean equal values`() = runTest {
    checkAll(propTestConfig, Arb.boolean()) { boolean ->
      val value1 = boolean.toValueProto()
      val value2 = boolean.toValueProto()

      valueFastEqual(value1, value2) shouldBe true
      valueDiff(value1, value2).size shouldBe 0
    }
  }

  @Test
  fun `valueDiff,valueFastEqual for boolean unequal values`() = runTest {
    checkAll(propTestConfig, Arb.boolean()) { boolean ->
      val value1 = boolean.toValueProto()
      val value2 = (!boolean).toValueProto()

      valueFastEqual(value1, value2) shouldBe false
      valueDiff(value1, value2).asClue { differences ->
        differences
          .toList()
          .shouldContainExactlyInAnyOrder(
            DifferencePathPair(
              path = emptyList(),
              difference = Difference.BoolValue(boolean, !boolean)
            )
          )
      }
    }
  }

  @Test
  fun `valueDiff,valueFastEqual for string equal values`() = runTest {
    checkAll(propTestConfig, Arb.string()) { string ->
      val value1 = string.toValueProto()
      val value2 = string.toValueProto()

      valueFastEqual(value1, value2) shouldBe true
      valueDiff(value1, value2).size shouldBe 0
    }
  }

  @Test
  fun `valueDiff,valueFastEqual for string unequal values`() = runTest {
    checkAll(propTestConfig, Arb.string().distinctPair()) { (string1, string2) ->
      val value1 = string1.toValueProto()
      val value2 = string2.toValueProto()

      valueFastEqual(value1, value2) shouldBe false
      valueDiff(value1, value2).asClue { differences ->
        differences
          .toList()
          .shouldContainExactlyInAnyOrder(
            DifferencePathPair(
              path = emptyList(),
              difference = Difference.StringValue(string1, string2)
            )
          )
      }
    }
  }

  @Test
  fun `valueDiff,valueFastEqual for number equal values`() = runTest {
    checkAll(propTestConfig, Arb.double()) { double ->
      val value1 = double.toValueProto()
      val value2 = double.toValueProto()

      valueFastEqual(value1, value2) shouldBe true
      valueDiff(value1, value2).size shouldBe 0
    }
  }

  @Test
  fun `valueDiff,valueFastEqual for number unequal values`() = runTest {
    val arb = Arb.double().distinctPair(isEqual = ::numberValuesEqual)
    checkAll(propTestConfig, arb) { (double1, double2) ->
      val value1 = double1.toValueProto()
      val value2 = double2.toValueProto()

      valueFastEqual(value1, value2) shouldBe false
      valueDiff(value1, value2).asClue { differences ->
        differences
          .toList()
          .shouldContainExactlyInAnyOrder(
            DifferencePathPair(
              path = emptyList(),
              difference = Difference.NumberValue(double1, double2)
            )
          )
      }
    }
  }

  @Test
  fun `valueDiff,valueFastEqual for different kind cases`() = runTest {
    checkAll(propTestConfig, Arb.enum<Value.KindCase>().distinctPair()) { (kind1, kind2) ->
      val value1 = Arb.proto.valueOfKind(kind1).bind()
      val value2 = Arb.proto.valueOfKind(kind2).bind()

      valueFastEqual(value1, value2) shouldBe false
      valueDiff(value1, value2).asClue { differences ->
        differences
          .toList()
          .shouldContainExactlyInAnyOrder(
            DifferencePathPair(path = emptyList(), difference = Difference.KindCase(value1, value2))
          )
      }
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

    fun PropertyContext.randomPathsToReplace(
      value: Value,
      maxNumPaths: Int,
      filter: ((path: List<PathComponent>, value: Value) -> Boolean)? = null,
    ): List<List<PathComponent>> = randomSource().random.pathsToReplace(value, maxNumPaths, filter)

    fun Random.pathsToReplace(
      value: Value,
      maxNumPaths: Int,
      filter: ((path: List<PathComponent>, value: Value) -> Boolean)? = null,
    ): List<List<PathComponent>> {
      val candidatePaths = value.allDescendantPaths(filter).toMutableList()
      return buildList {
        while (size < maxNumPaths && candidatePaths.isNotEmpty()) {
          val path = candidatePaths.random(this@pathsToReplace)
          add(path)
          candidatePaths.removeAll { candidatePath ->
            candidatePath.isPrefixOf(path) || path.isPrefixOf(candidatePath)
          }
        }
      }
    }

    fun List<*>.isPrefixOf(otherList: List<*>): Boolean =
      if (size > otherList.size) {
        false
      } else {
        otherList.subList(0, size) == this
      }

    data class ReplaceRandomValuesResult<T, V : Value?>(
      val newItem: T,
      val replacements: List<Replacement<V>>,
    ) {
      data class Replacement<V : Value?>(
        val path: List<PathComponent>,
        val oldValue: Value,
        val newValue: V,
      )
    }

    fun <V : Value?> PropertyContext.replaceRandomValues(
      struct: Struct,
      maxNumPaths: Int,
      filter: ((path: List<PathComponent>, value: Value) -> Boolean)? = null,
      replacementValue: (path: List<PathComponent>, oldValue: Value) -> V,
    ): ReplaceRandomValuesResult<Struct, V> {
      val pathsToReplace = randomPathsToReplace(struct.toValueProto(), maxNumPaths, filter)

      val replacements = mutableListOf<ReplaceRandomValuesResult.Replacement<V>>()
      val newStruct =
        struct.map { path, value ->
          if (pathsToReplace.contains(path)) {
            val newValue = replacementValue(path, value)
            replacements.add(ReplaceRandomValuesResult.Replacement(path, value, newValue))
            newValue
          } else {
            value
          }
        }

      return ReplaceRandomValuesResult(newStruct, replacements.toList())
    }

    fun <V : Value?> PropertyContext.replaceRandomValues(
      listValue: ListValue,
      maxNumPaths: Int,
      filter: ((path: List<PathComponent>, value: Value) -> Boolean)? = null,
      replacementValue: (path: List<PathComponent>, oldValue: Value) -> V,
    ): ReplaceRandomValuesResult<ListValue, V> {
      val pathsToReplace = randomPathsToReplace(listValue.toValueProto(), maxNumPaths, filter)

      val replacements = mutableListOf<ReplaceRandomValuesResult.Replacement<V>>()
      val newListValue =
        listValue.map { path, value ->
          if (pathsToReplace.contains(path)) {
            val newValue = replacementValue(path, value)
            replacements.add(ReplaceRandomValuesResult.Replacement(path, value, newValue))
            newValue
          } else {
            value
          }
        }

      return ReplaceRandomValuesResult(newListValue, replacements.toList())
    }

    fun List<DifferencePathPair<*>>.withInvertedDifferences(): List<DifferencePathPair<*>> = map {
      it.withInvertedDifference()
    }

    fun DifferencePathPair<*>.withInvertedDifference(): DifferencePathPair<*> =
      DifferencePathPair(path, difference.inverse())

    fun Difference.inverse(): Difference =
      when (this) {
        is Difference.BoolValue -> Difference.BoolValue(value1 = value2, value2 = value1)
        is Difference.KindCase -> Difference.KindCase(value1 = value2, value2 = value1)
        is Difference.ListMissingElement ->
          Difference.ListUnexpectedElement(index = index, value = value)
        is Difference.ListUnexpectedElement ->
          Difference.ListMissingElement(index = index, value = value)
        is Difference.NumberValue -> Difference.NumberValue(value1 = value2, value2 = value1)
        is Difference.StringValue -> Difference.StringValue(value1 = value2, value2 = value1)
        is Difference.StructMissingKey -> Difference.StructUnexpectedKey(key = key, value = value)
        is Difference.StructUnexpectedKey -> Difference.StructMissingKey(key = key, value = value)
      }

    fun Value.isBoolNumberOrString(): Boolean =
      when (kindCase) {
        Value.KindCase.NUMBER_VALUE,
        Value.KindCase.STRING_VALUE,
        Value.KindCase.BOOL_VALUE -> true
        else -> false
      }

    fun PropertyContext.unequalValueOfSameKind(value: Value): Value =
      when (val kindCase = value.kindCase) {
        Value.KindCase.BOOL_VALUE -> value.toBuilder().setBoolValue(!value.boolValue).build()
        Value.KindCase.NUMBER_VALUE ->
          Arb.proto.numberValue(filter = { !numberValuesEqual(it, value.numberValue) }).bind()
        Value.KindCase.STRING_VALUE ->
          Arb.proto.stringValue(filter = { it != value.stringValue }).bind()
        else ->
          throw IllegalStateException(
            "should never get here: kindCase=$kindCase value=$value [vqrnqxwcds]"
          )
      }

    fun MutableList<DifferencePathPair<*>>.addAllUnequalValueOfSameKindDifferences(
      replacements: List<ReplaceRandomValuesResult.Replacement<Value>>
    ) {
      replacements.forEach { replacement ->
        val difference =
          when (val kindCase = replacement.oldValue.kindCase) {
            Value.KindCase.NUMBER_VALUE ->
              Difference.NumberValue(
                replacement.oldValue.numberValue,
                replacement.newValue.numberValue
              )
            Value.KindCase.STRING_VALUE ->
              Difference.StringValue(
                replacement.oldValue.stringValue,
                replacement.newValue.stringValue
              )
            Value.KindCase.BOOL_VALUE ->
              Difference.BoolValue(replacement.oldValue.boolValue, replacement.newValue.boolValue)
            else ->
              throw IllegalStateException(
                "should never get here: kindCase=$kindCase replacement=$replacement [xgdwtkgtg2]"
              )
          }

        add(DifferencePathPair(replacement.path, difference))
      }
    }
  }
}
