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
import com.google.firebase.dataconnect.testutil.property.arbitrary.next
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.random
import com.google.firebase.dataconnect.testutil.property.arbitrary.scalarValue
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
import io.kotest.property.RandomSource
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.distinct
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.set
import io.kotest.property.arbitrary.string
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

  @Test
  fun `structDiff,structFastEqual for keys added to root struct`() = runTest {
    val structKeyArb = Arb.proto.structKey()
    val valueArb = Arb.proto.value()
    checkAll(propTestConfig, Arb.proto.struct(), Arb.int(1..5)) { sample, unexpectedKeyCount ->
      val struct1 = sample.struct
      val struct2KeyArb = structKeyArb.filterNot(struct1::containsFields).distinct()
      val expectedDifferences = mutableListOf<DifferencePathPair<Difference.StructUnexpectedKey>>()
      val struct2 =
        struct1.toBuilder().let { struct2Builder ->
          repeat(unexpectedKeyCount) {
            val difference = Difference.StructUnexpectedKey(struct2KeyArb.bind(), valueArb.bind())
            check(!struct2Builder.containsFields(difference.key))
            struct2Builder.putFields(difference.key, difference.value)
            expectedDifferences.add(DifferencePathPair(emptyList(), difference))
          }
          struct2Builder.build()
        }

      structFastEqual(struct1, struct2) shouldBe false
      structFastEqual(struct2, struct1) shouldBe false

      structDiff(struct1, struct2).asClue { differences ->
        differences.toList() shouldContainExactlyInAnyOrder expectedDifferences
      }
      structDiff(struct2, struct1).asClue { differences ->
        differences.toList() shouldContainExactlyInAnyOrder
          expectedDifferences.map {
            val missingKeyDifference = it.difference.run { Difference.StructMissingKey(key, value) }
            DifferencePathPair(it.path, missingKeyDifference)
          }
      }
    }
  }

  @Test
  fun `structDiff,structFastEqual for keys added to nested struct`() = runTest {
    val structKeyArb = Arb.proto.structKey()
    val valueArb = Arb.proto.value()
    checkAll(propTestConfig, Arb.structWithAtLeast1SubStruct(structKeyArb), Arb.int(1..5)) {
      struct1,
      unexpectedKeyCount ->
      val structPaths = buildList {
        struct1.walk { path, value ->
          if (path.isNotEmpty() && value.kindCase == Value.KindCase.STRUCT_VALUE) {
            val struct = value.structValue
            add(path to structKeyArb.filterNot(struct::containsFields).distinct())
          }
        }
      }
      val expectedDifferences =
        List(unexpectedKeyCount) {
          val (path, keyArb) = structPaths.random(randomSource().random)
          val difference = Difference.StructUnexpectedKey(keyArb.bind(), valueArb.bind())
          DifferencePathPair(path, difference)
        }
      val struct2 =
        struct1.map { path, value ->
          val curDifferences = expectedDifferences.filter { it.path == path }.map { it.difference }
          if (curDifferences.isEmpty()) {
            value
          } else {
            value.structValue.toBuilder().let { structBuilder ->
              curDifferences.forEach { structBuilder.putFields(it.key, it.value) }
              structBuilder.build().toValueProto()
            }
          }
        }

      structFastEqual(struct1, struct2) shouldBe false
      structFastEqual(struct2, struct1) shouldBe false

      structDiff(struct1, struct2).asClue { differences ->
        differences.toList() shouldContainExactlyInAnyOrder expectedDifferences
      }
      structDiff(struct2, struct1).asClue { differences ->
        differences.toList() shouldContainExactlyInAnyOrder
          expectedDifferences.map {
            val missingKeyDifference = it.difference.run { Difference.StructMissingKey(key, value) }
            DifferencePathPair(it.path, missingKeyDifference)
          }
      }
    }
  }

  @Test
  fun `structDiff,structFastEqual for KindCase`() = runTest {
    val valueArb = Arb.proto.value()
    checkAll(propTestConfig, Arb.proto.struct(size = 1..5).map { it.struct }, Arb.int(1..5)) {
      struct1,
      changedKeyCount ->
      val struct1PathsToReplace = randomPathsToReplace(struct1, changedKeyCount)
      val expectedDifferences = mutableListOf<DifferencePathPair<Difference.KindCase>>()
      val struct2 =
        struct1.map { path, value ->
          if (!struct1PathsToReplace.contains(path)) {
            value
          } else {
            val newValue = valueArb.filterNot { it.kindCase == value.kindCase }.bind()
            expectedDifferences.add(DifferencePathPair(path, Difference.KindCase(value, newValue)))
            newValue
          }
        }

      structFastEqual(struct1, struct2) shouldBe false
      structFastEqual(struct2, struct1) shouldBe false

      structDiff(struct1, struct2).asClue { differences ->
        differences.toList() shouldContainExactlyInAnyOrder expectedDifferences
      }
      structDiff(struct2, struct1).asClue { differences ->
        val invertedExpectedDifferences =
          expectedDifferences.map {
            DifferencePathPair(
              it.path,
              Difference.KindCase(value1 = it.difference.value2, value2 = it.difference.value1)
            )
          }
        differences.toList() shouldContainExactlyInAnyOrder invertedExpectedDifferences
      }
    }
  }

  @Test
  fun `listValueDiff reports equal for same instance`() = runTest {
    checkAll(propTestConfig, Arb.proto.listValue()) { sample ->
      val differences = listValueDiff(sample.listValue, sample.listValue)
      differences.size shouldBe 0
    }
  }

  @Test
  fun `listValueDiff reports equal for equal, but distinct instance`() = runTest {
    checkAll(propTestConfig, Arb.proto.listValue()) { sample ->
      val differences = listValueDiff(sample.listValue, sample.listValue.deepCopy())
      differences.size shouldBe 0
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

    fun Arb.Companion.structWithAtLeast1SubStruct(key: Arb<String>): Arb<Struct> {
      val structArb = Arb.proto.struct(depth = 2..4, key = key).map { it.struct }
      return Arb.bind(structArb, structArb, Arb.long()) { struct1, struct2, randomSeed ->
        val rs = RandomSource.seeded(randomSeed)
        struct1.withRandomlyInsertedValues(
          listOf(struct2.toValueProto()),
          rs.random,
          { key.next(rs, edgeCaseProbability = rs.random.nextFloat()) },
        )
      }
    }

    fun PropertyContext.randomPathsToReplace(
      struct: Struct,
      maxNumPaths: Int
    ): List<List<PathComponent>> = randomSource().random.pathsToReplace(struct, maxNumPaths)

    fun Random.pathsToReplace(struct: Struct, maxNumPaths: Int): List<List<PathComponent>> {
      val candidatePaths = struct.allDescendantPaths().toMutableList()
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
  }
}
