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

package com.google.firebase.dataconnect.util

import com.google.firebase.dataconnect.DataConnectPathSegment
import com.google.firebase.dataconnect.emptyDataConnectPath
import com.google.firebase.dataconnect.testutil.DataConnectPath
import com.google.firebase.dataconnect.testutil.extract
import com.google.firebase.dataconnect.testutil.isListValue
import com.google.firebase.dataconnect.testutil.isStructValue
import com.google.firebase.dataconnect.testutil.listValueOrNull
import com.google.firebase.dataconnect.testutil.map
import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb.dataConnectPath as dataConnectPathArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb.fieldPathSegment as fieldPathSegmentArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb.listIndexPathSegment as listIndexPathSegmentArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.listValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.property.arbitrary.structKey
import com.google.firebase.dataconnect.testutil.property.arbitrary.twoValues
import com.google.firebase.dataconnect.testutil.property.arbitrary.value
import com.google.firebase.dataconnect.testutil.property.arbitrary.valueOfKind
import com.google.firebase.dataconnect.testutil.randomlyInsertValue
import com.google.firebase.dataconnect.testutil.registerDataConnectKotestPrinters
import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import com.google.firebase.dataconnect.testutil.structValueOrNull
import com.google.firebase.dataconnect.testutil.walk
import com.google.firebase.dataconnect.testutil.walkPaths
import com.google.firebase.dataconnect.testutil.walkValues
import com.google.firebase.dataconnect.toPathString
import com.google.firebase.dataconnect.util.ProtoGraft.withGraftedInValues
import com.google.firebase.dataconnect.util.ProtoUtil.toValueProto
import com.google.firebase.dataconnect.withAddedField
import com.google.firebase.dataconnect.withAddedListIndex
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.common.DelicateKotest
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.Exhaustive
import io.kotest.property.PropTestConfig
import io.kotest.property.PropertyContext
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.distinct
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.negativeInt
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.enum
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ProtoGraftUnitTest {

  @Before
  fun registerPrinters() {
    registerDataConnectKotestPrinters()
  }

  @Test
  fun `Struct withGraftedInValues() with empty valueByPath should return receiver`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct()) { structSample ->
      val struct: Struct = structSample.struct

      struct.withGraftedInValues(emptyMap()) shouldBeSameInstanceAs struct
    }
  }

  @Test
  fun `ListValue withGraftedInValues() with empty valueByPath should return receiver`() = runTest {
    checkAll(propTestConfig, Arb.proto.listValue()) { listValueSample ->
      val listValue: ListValue = listValueSample.listValue

      listValue.withGraftedInValues(emptyMap()) shouldBeSameInstanceAs listValue
    }
  }

  @Test
  fun `Struct withGraftedInValues() with the empty path should throw`() = runTest {
    val dataConnectPathArb = dataConnectPathArb()
    checkAll(propTestConfig, Arb.proto.struct(), Arb.proto.value(), Arb.int(0..3)) {
      structSample,
      valueToGraft,
      otherPathCount ->
      val struct: Struct = structSample.struct
      val valueByPath = buildMap {
        put(emptyDataConnectPath(), valueToGraft)
        repeat(otherPathCount) { put(dataConnectPathArb.bind(), valueToGraft) }
      }

      val exception =
        shouldThrow<IllegalArgumentException> { struct.withGraftedInValues(valueByPath) }

      assertSoftly {
        exception.message shouldContainWithNonAbuttingText "af5k4an5za"
        exception.message shouldContainWithNonAbuttingTextIgnoringCase "contains the empty path"
        exception.message shouldContainWithNonAbuttingTextIgnoringCase "empty path is not allowed"
      }
    }
  }

  @Test
  fun `ListValue withGraftedInValues() with the empty path should throw`() = runTest {
    val dataConnectPathArb = dataConnectPathArb()
    checkAll(propTestConfig, Arb.proto.listValue(), Arb.proto.value(), Arb.int(0..3)) {
      listValueSample,
      valueToGraft,
      otherPathCount ->
      val listValue: ListValue = listValueSample.listValue
      val valueByPath = buildMap {
        put(emptyDataConnectPath(), valueToGraft)
        repeat(otherPathCount) { put(dataConnectPathArb.bind(), valueToGraft) }
      }

      val exception =
        shouldThrow<IllegalArgumentException> { listValue.withGraftedInValues(valueByPath) }

      assertSoftly {
        exception.message shouldContainWithNonAbuttingText "rm45kyhtff"
        exception.message shouldContainWithNonAbuttingTextIgnoringCase "contains the empty path"
        exception.message shouldContainWithNonAbuttingTextIgnoringCase "empty path is not allowed"
      }
    }
  }

  @Test
  fun `Struct withGraftedInValues() with single-segment paths`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct(), Arb.list(Arb.proto.value(), 1..5)) {
      structSample,
      valuesToGraft ->
      val struct: Struct = structSample.struct
      val structKeyArb = Arb.proto.structKey().filterNot { struct.containsFields(it) }
      val valueByFieldName: Map<String, Value> = valuesToGraft.associateBy { structKeyArb.bind() }
      val valueByPath: Map<DataConnectPath, Value> =
        valueByFieldName.mapKeys { entry -> listOf(DataConnectPathSegment.Field(entry.key)) }

      val result = struct.withGraftedInValues(valueByPath)

      val expectedResult: Struct =
        struct.toBuilder().let { structBuilder ->
          valueByFieldName.entries.forEach { (fieldName, valueToGraft) ->
            structBuilder.putFields(fieldName, valueToGraft)
          }
          structBuilder.build()
        }
      result shouldBe expectedResult
    }
  }

  @Test
  fun `ListValue withGraftedInValues() with a single field path`() = runTest {
    checkAll(propTestConfig, Arb.proto.listValue(), fieldPathSegmentArb(), Arb.proto.value()) {
      listValueSample,
      fieldPathSegment,
      valueToGraft ->
      val listValue: ListValue = listValueSample.listValue
      val path = listOf<DataConnectPathSegment>(fieldPathSegment)
      val valueByPath = mapOf(path to valueToGraft)

      shouldThrow<ProtoGraft.InsertIntoNonStructException> {
        listValue.withGraftedInValues(valueByPath)
      }
    }
  }

  @Test
  fun `ListValue withGraftedInValues() with a single list index path`() = runTest {
    checkAll(propTestConfig, Arb.proto.listValue(), listIndexPathSegmentArb(), Arb.proto.value()) {
      listValueSample,
      listIndexPathSegment,
      valueToGraft ->
      val listValue: ListValue = listValueSample.listValue
      val path = listOf<DataConnectPathSegment>(listIndexPathSegment)
      val valueByPath = mapOf(path to valueToGraft)

      shouldThrow<ProtoGraft.LastPathSegmentNotFieldException> {
        listValue.withGraftedInValues(valueByPath)
      }
    }
  }

  @Test
  fun `Struct withGraftedInValues() with paths ending with list index should throw`() = runTest {
    checkAll(
      propTestConfig,
      Arb.proto.struct(),
      Arb.proto.value(),
      dataConnectPathArb(),
      Arb.int()
    ) { struct, valueToGraft, graftPathPrefix, graftPathLastSegmentIndex ->
      val graftPath = graftPathPrefix.withAddedListIndex(graftPathLastSegmentIndex)
      val valueByPath = mapOf(graftPath to valueToGraft)

      val exception =
        shouldThrow<ProtoGraft.LastPathSegmentNotFieldException> {
          struct.struct.withGraftedInValues(valueByPath)
        }

      assertSoftly {
        exception.message shouldContainWithNonAbuttingText "qxgass8cvx"
        exception.message shouldContainWithNonAbuttingText graftPath.toPathString()
        exception.message shouldContainWithNonAbuttingTextIgnoringCase
          "last segment is list index $graftPathLastSegmentIndex"
        exception.message shouldContainWithNonAbuttingTextIgnoringCase
          "last segment must be a field"
      }
    }
  }

  @Test
  fun `Struct withGraftedInValues() with paths ending with an existing key should throw`() =
    runTest {
      val nonEmptyStructArb = Arb.proto.struct().filterNot { it.struct.fieldsCount == 0 }
      checkAll(propTestConfig, nonEmptyStructArb, Arb.proto.value()) { struct, valueToGraft ->
        val existingPaths =
          struct.struct
            .walkPaths()
            .filter { it.lastOrNull() is DataConnectPathSegment.Field }
            .toList()
        val existingPath = Arb.of(existingPaths).bind()
        val valueByPath = mapOf(existingPath to valueToGraft)

        val exception =
          shouldThrow<ProtoGraft.KeyExistsException> {
            struct.struct.withGraftedInValues(valueByPath)
          }

        assertSoftly {
          exception.message shouldContainWithNonAbuttingText "ecgd5r2v4a"
          exception.message shouldContainWithNonAbuttingText existingPath.toPathString()
          exception.message shouldContainWithNonAbuttingText existingPath.dropLast(1).toPathString()
          val existingField = (existingPath.last() as DataConnectPathSegment.Field).field
          exception.message shouldContainWithNonAbuttingTextIgnoringCase
            "already has a field named $existingField"
          exception.message shouldContainWithNonAbuttingTextIgnoringCase
            "it is required to not already have that field"
        }
      }
    }

  @Test
  fun `Struct withGraftedInValues() with paths whose immediate parent is an existing struct`() =
    runTest {
      checkAll(propTestConfig, Arb.proto.struct(), Arb.list(Arb.proto.value(), 1..5)) {
        struct,
        valuesToGraft ->
        val destPaths = pathToNonExistentFieldInExistingStructSequence(struct.struct).iterator()
        val valueByPath = valuesToGraft.associateBy { destPaths.next() }

        val result = struct.struct.withGraftedInValues(valueByPath)

        val expectedResult =
          struct.struct.toExpectedStructWithValuesGraftedInToExistingStruct(valueByPath)
        result shouldBe expectedResult
      }
    }

  @Test
  fun `Struct withGraftedInValues() with paths whose immediate parent does not exist`() = runTest {
    val fieldPathSegmentArb = fieldPathSegmentArb(Arb.proto.structKey())
    val fieldPathSegmentsSizeArb = Arb.int(1..5)
    checkAll(propTestConfig, Arb.proto.struct(), Arb.list(Arb.proto.value(), 1..5)) {
      struct,
      valuesToGraft ->
      data class DestPathInfo(
        val prefix: DataConnectPath,
        val suffix: DataConnectPath,
        val fullPath: DataConnectPath
      )
      val destPaths =
        pathToNonExistentFieldInExistingStructSequence(struct.struct)
          .map {
            val suffixSize = fieldPathSegmentsSizeArb.bind()
            val suffix = List(suffixSize) { fieldPathSegmentArb.bind() }
            DestPathInfo(prefix = it, suffix = suffix, fullPath = it + suffix)
          }
          .take(valuesToGraft.size)
          .toList()
      val valueByPath =
        valuesToGraft.zip(destPaths).associate { (value, destPath) ->
          Pair(destPath.fullPath, value)
        }

      val result = struct.struct.withGraftedInValues(valueByPath)

      val prefixedValuesByPath =
        valuesToGraft.zip(destPaths).associate { (value, destPath) ->
          val suffix = destPath.suffix.map { it as DataConnectPathSegment.Field }
          Pair(destPath.prefix, value.withParents(suffix).toValueProto())
        }
      val expectedResult =
        struct.struct.toExpectedStructWithValuesGraftedInToExistingStruct(prefixedValuesByPath)
      result shouldBe expectedResult
    }
  }

  @Test
  fun `Struct withGraftedInValues() with paths of non-structs should throw`() = runTest {
    checkAll(
      propTestConfig,
      structWithAtLeast1ValueOfNotKindArb(Value.KindCase.STRUCT_VALUE),
      Arb.proto.value(),
      fieldPathSegmentArb()
    ) { struct, valueToGraft, fieldPathSegment ->
      val nonStructs = struct.walk().filterNot { it.value.isStructValue }.toList()
      val nonStruct = Arb.of(nonStructs).bind()
      val valueToGraftPath = nonStruct.path + listOf(fieldPathSegment)
      val valueByPath = mapOf(valueToGraftPath to valueToGraft)

      val exception =
        shouldThrow<ProtoGraft.InsertIntoNonStructException> {
          struct.withGraftedInValues(valueByPath)
        }

      assertSoftly {
        exception.message shouldContainWithNonAbuttingText "zcj277ka6a"
        exception.message shouldContainWithNonAbuttingText nonStruct.path.toPathString()
        exception.message shouldContainWithNonAbuttingText valueToGraftPath.toPathString()
        exception.message shouldContainWithNonAbuttingTextIgnoringCase
          "has kind ${nonStruct.value.kindCase}"
        exception.message shouldContainWithNonAbuttingTextIgnoringCase
          "expected to have kind ${Value.KindCase.STRUCT_VALUE}"
      }
    }
  }

  @Test
  fun `Struct withGraftedInValues() with paths with a field segment that is not a struct should throw`() =
    runTest {
      checkAll(
        propTestConfig,
        structWithAtLeast1ValueOfNotKindArb(Value.KindCase.STRUCT_VALUE),
        Arb.proto.value(),
        dataConnectPathArb(size = 0..5),
        Arb.twoValues(fieldPathSegmentArb())
      ) { struct, valueToGraft, pathSuffix, (fieldSegment1, fieldSegment2) ->
        val nonStructs = struct.walk().filterNot { it.value.isStructValue }.toList()
        val (nonStructPath, nonStructValue) = Arb.of(nonStructs).bind()
        val graftPath = buildList {
          addAll(nonStructPath)
          add(fieldSegment1)
          addAll(pathSuffix)
          add(fieldSegment2)
        }
        val valueByPath = mapOf(graftPath to valueToGraft)

        val exception =
          shouldThrow<ProtoGraft.PathFieldOfNonStructException> {
            struct.withGraftedInValues(valueByPath)
          }

        assertSoftly {
          exception.message shouldContainWithNonAbuttingText "s3mhtfj2mm"
          exception.message shouldContainWithNonAbuttingText graftPath.toPathString()
          exception.message shouldContainWithNonAbuttingTextIgnoringCase
            "whose segment ${nonStructPath.size} " +
              "(${nonStructPath.last().toExpectedDescriptionInExceptionMessages()})"
          exception.message shouldContainWithNonAbuttingTextIgnoringCase
            "has kind ${nonStructValue.kindCase}"
          exception.message shouldContainWithNonAbuttingTextIgnoringCase
            "expected to have kind ${Value.KindCase.STRUCT_VALUE}"
        }
      }
    }

  @Test
  fun `Struct withGraftedInValues() with paths with a list index segment that is not a list should throw`() =
    runTest {
      checkAll(
        propTestConfig,
        structWithAtLeast1ValueOfNotKindArb(Value.KindCase.LIST_VALUE),
        Arb.proto.value(),
        dataConnectPathArb(size = 0..5),
        listIndexPathSegmentArb(),
        fieldPathSegmentArb()
      ) { struct, valueToGraft, pathSuffix, listIndexSegment, fieldSegment ->
        val nonListValues = struct.walk().filterNot { it.value.isListValue }.toList()
        val (nonListValuePath, nonListValueValue) = Arb.of(nonListValues).bind()
        val graftPath = buildList {
          addAll(nonListValuePath)
          add(listIndexSegment)
          addAll(pathSuffix)
          add(fieldSegment)
        }
        val valueByPath = mapOf(graftPath to valueToGraft)

        val exception =
          shouldThrow<ProtoGraft.PathListIndexOfNonListException> {
            struct.withGraftedInValues(valueByPath)
          }

        assertSoftly {
          exception.message shouldContainWithNonAbuttingText "gr7mqk4jnn"
          exception.message shouldContainWithNonAbuttingText graftPath.toPathString()
          exception.message shouldContainWithNonAbuttingTextIgnoringCase
            "whose segment ${nonListValuePath.size} " +
              "(${nonListValuePath.last().toExpectedDescriptionInExceptionMessages()})"
          exception.message shouldContainWithNonAbuttingTextIgnoringCase
            "has kind ${nonListValueValue.kindCase}"
          exception.message shouldContainWithNonAbuttingTextIgnoringCase
            "expected to have kind ${Value.KindCase.LIST_VALUE}"
        }
      }
    }

  @Test
  fun `Struct withGraftedInValues() with paths with a negative list index segment should throw`() =
    verifyWithGraftedInStructsThrowsPathListIndexOutOfBoundsException<
      ProtoGraft.NegativePathListIndexException
    >(
      outOfRangeListIndexArbFactory = { Arb.negativeInt() }
    ) { exception, graftPath, listValuePath, listValue, outOfRangeListIndex ->
      exception.message shouldContainWithNonAbuttingText "rrk4t44n42"
      exception.message shouldContainWithNonAbuttingText graftPath.toPathString()
      exception.message shouldContainWithNonAbuttingTextIgnoringCase
        "whose segment ${listValuePath.size+1} (list index $outOfRangeListIndex) is negative"
      exception.message shouldContainWithNonAbuttingTextIgnoringCase
        "between 0 (inclusive) and ${listValue.valuesCount} (exclusive)"
    }

  @Test
  fun `Struct withGraftedInValues() with paths with a too-large list index segment should throw`() =
    verifyWithGraftedInStructsThrowsPathListIndexOutOfBoundsException<
      ProtoGraft.PathListIndexGreaterThanOrEqualToListSizeException
    >(
      outOfRangeListIndexArbFactory = { listValue -> Arb.int(min = listValue.valuesCount) }
    ) { exception, graftPath, listValuePath, listValue, outOfRangeListIndex ->
      exception.message shouldContainWithNonAbuttingText "pdfqm8kb54"
      exception.message shouldContainWithNonAbuttingText graftPath.toPathString()
      exception.message shouldContainWithNonAbuttingTextIgnoringCase
        "whose segment ${listValuePath.size+1} (list index $outOfRangeListIndex) " +
          "is greater than or equal to the size of the list"
      exception.message shouldContainWithNonAbuttingTextIgnoringCase
        "between 0 (inclusive) and ${listValue.valuesCount} (exclusive)"
    }

  private inline fun <
    reified E : ProtoGraft.PathListIndexOutOfBoundsException> verifyWithGraftedInStructsThrowsPathListIndexOutOfBoundsException(
    crossinline outOfRangeListIndexArbFactory: (ListValue) -> Arb<Int>,
    crossinline validateMessage:
      (
        exception: E,
        graftPath: DataConnectPath,
        listValuePath: DataConnectPath,
        listValue: ListValue,
        outOfRangeListIndex: Int,
      ) -> Unit,
  ) = runTest {
    val structWithAtLeast1ListValueArb =
      structWithAtLeast1ValueMatchingArb(Arb.proto.valueOfKind(Value.KindCase.LIST_VALUE)) {
        it.isListValue
      }

    checkAll(
      propTestConfig,
      structWithAtLeast1ListValueArb,
      Arb.proto.value(),
      dataConnectPathArb(size = 0..5),
      fieldPathSegmentArb()
    ) { struct, valueToGraft, pathSuffix, fieldSegment ->
      val listValues = struct.walk().filter { it.value.isListValue }.toList()
      val (listValuePath, listValueValue) = Arb.of(listValues).bind()
      val listValue = listValueValue.listValue
      val outOfRangeListIndex = outOfRangeListIndexArbFactory(listValue).bind()
      val graftPath = buildList {
        addAll(listValuePath)
        add(DataConnectPathSegment.ListIndex(outOfRangeListIndex))
        addAll(pathSuffix)
        add(fieldSegment)
      }
      val valueByPath = mapOf(graftPath to valueToGraft)

      val exception = shouldThrow<E> { struct.withGraftedInValues(valueByPath) }

      assertSoftly {
        validateMessage(exception, graftPath, listValuePath, listValue, outOfRangeListIndex)
      }
    }
  }

  @Test
  fun `Struct withGraftedInValues() with nested paths`() = runTest {
    val structArb = Arb.proto.struct()
    checkAll(propTestConfig, structArb, Arb.int(1..10)) { struct, subStructCount ->
      val valueByPath = mutableMapOf<DataConnectPath, Value>()
      val valuesToGraft = generateSequence { structArb.bind().toValueProto() }.take(subStructCount)
      val expectedResult = populateStructsByPath(valueByPath, struct.struct, valuesToGraft)

      val result = struct.struct.withGraftedInValues(valueByPath)

      result shouldBe expectedResult
    }
  }
}

@OptIn(ExperimentalKotest::class)
private val propTestConfig =
  PropTestConfig(iterations = 200, edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2))

/**
 * Creates and returns a new [Struct] that is the result of randomly inserting the [Value] objects
 * produced by the given [valuesToGraft] sequence into the receiver [Struct] at random points, and
 * puts entries into the given [valueByPath] map about the paths at which each [Struct] was grafted.
 *
 * This function is used to set up test scenarios where [Struct] objects are grafted into a base
 * [Struct] at various paths, some of which may require the creation of intermediate parent [Struct]
 * objects.
 *
 * @param valueByPath A mutable map that will be populated with the [DataConnectPath] to each
 * grafted [Value]. This map is used to verify the grafting operation in tests.
 * @param struct The base [Struct] onto which other [Value] objects will be grafted.
 * @param valuesToGraft A sequence of [Value] objects to be randomly grafted.
 * @return A new [Struct] instance that is the receiver [Struct] with all the [valuesToGraft]
 * randomly inserted at various paths.
 */
private fun PropertyContext.populateStructsByPath(
  valueByPath: MutableMap<DataConnectPath, Value>,
  struct: Struct,
  valuesToGraft: Sequence<Value>
): Struct {
  val structKeyArb = Arb.proto.structKey()
  val missingParentsPathArb = Arb.list(fieldPathSegmentArb(string = structKeyArb), 0..3)

  val structBuilder = struct.toBuilder()
  valuesToGraft.forEach { valueToGraft ->
    val missingParentsPath = missingParentsPathArb.bind()
    val valuesToGraftWithMissingParents =
      if (missingParentsPath.isEmpty()) {
        valueToGraft
      } else {
        valueToGraft.withParents(missingParentsPath).toValueProto()
      }
    val graftPath =
      structBuilder.randomlyInsertValue(
        valuesToGraftWithMissingParents,
        randomSource().random,
        generateKey = { structKeyArb.bind() },
      )
    valueByPath[graftPath + missingParentsPath] = valueToGraft
  }

  return structBuilder.build()
}

private fun DataConnectPathSegment.toExpectedDescriptionInExceptionMessages(): String =
  when (this) {
    is DataConnectPathSegment.Field -> "field $field"
    is DataConnectPathSegment.ListIndex -> "list index $index"
  }

/**
 * Creates a [Struct] by grafting in [Value] objects into the receiver [Struct].
 *
 * This function is intended to construct the expected output [Struct] after a grafting operation.
 * It iterates through the receiver [Struct] and, for any [Struct] values encountered, it checks if
 * there are corresponding [Struct] objects to be grafted in at that path. If so, it grafts them
 * into the existing [Struct] at the specified path.
 *
 * The grafting process ensures that each [Value] in [valueByPath] is placed at its designated
 * [DataConnectPath] within the receiver [Struct]. It's crucial that the paths in [valueByPath]
 * point to non-existent fields within existing [Struct] values in the receiver, or directly to the
 * root if the path is empty.
 *
 * @param valueByPath A map where keys are [DataConnectPath] objects indicating where a [Value]
 * should be grafted, and values are the [Value] objects to be grafted.
 * @return A new [Struct] instance that is the receiver [Struct] with the provided [valueByPath]
 * grafted into it.
 */
private fun Struct.toExpectedStructWithValuesGraftedInToExistingStruct(
  valueByPath: Map<DataConnectPath, Value>
): Struct {
  val valuesRemaining = valueByPath.entries.toMutableList()
  val result = map { path, value ->
    if (!value.isStructValue) {
      value
    } else {
      val curValues = valuesRemaining.extract { _, entry -> path == entry.key.dropLast(1) }
      if (curValues.isEmpty()) {
        value
      } else {
        value.structValue.toBuilder().let { structBuilder ->
          curValues.forEach { (path, subValue) ->
            val field = (path.last() as DataConnectPathSegment.Field).field
            require(!structBuilder.containsFields(field))
            structBuilder.putFields(field, subValue)
          }
          structBuilder.build().toValueProto()
        }
      }
    }
  }
  check(valuesRemaining.isEmpty())
  return result
}

/**
 * Searches the receiver [Struct] for all [Struct] values, recursively, including the receiver
 * [Struct] itself, and returns a map containing each discovered [Struct] with its key being the
 * [DataConnectPath] to the [Struct].
 */
private fun Struct.findNestedStructs(): Map<DataConnectPath, Struct> =
  walk(includeSelf = true)
    .filter { (_, value) -> value.isStructValue }
    .associate { (path, value) -> Pair(path, value.structValue) }

/**
 * Returns a [Sequence] that generates [DataConnectPath] objects whose values are distinct paths to
 * [Struct] keys that do not exist. Each path has at least one segment. The path of all segments
 * _except_ the last segment is the path of a [Struct] in the given [struct]. The final path segment
 * is a [DataConnectPathSegment.Field] that does _not_ exist.
 */
private fun PropertyContext.pathToNonExistentFieldInExistingStructSequence(
  struct: Struct
): Sequence<DataConnectPath> = sequence {
  val nestedStructByPath = struct.findNestedStructs()
  val destStructPathArb = Arb.of(nestedStructByPath.keys)

  val destKeyArbByPath = mutableMapOf<DataConnectPath, Arb<String>>()
  fun getDestKeyArbByPath(path: DataConnectPath): Arb<String> =
    destKeyArbByPath.getOrPut(path) {
      val subStruct = nestedStructByPath[path]!!
      @OptIn(DelicateKotest::class)
      Arb.proto.structKey().filterNot { subStruct.containsFields(it) }.distinct()
    }

  while (true) {
    val destStructPath = destStructPathArb.bind()
    val destKeyArb = getDestKeyArbByPath(destStructPath)
    val insertPathToExistingStruct = destStructPath.withAddedField(destKeyArb.bind())
    yield(insertPathToExistingStruct)
  }
}

/**
 * Wraps the receiver [Value] in parent [Struct] objects to place it at the given [path].
 *
 * For example, if this [Value] is `{ "key": "value" }` and the path is `["a", "b"]`, the returned
 * struct will be `{ "a": { "b": { "key": "value" } } }`.
 *
 * @param path The path where the receiver [Value] will be in the returned [Struct]; must not be
 * empty.
 * @return A new [Struct] with the receiver [Value] at the specified path
 */
private fun Value.withParents(path: List<DataConnectPathSegment.Field>): Struct {
  require(path.isNotEmpty())
  var structBuilder = Struct.newBuilder()
  structBuilder.putFields(path.last().field, this)
  path.dropLast(1).reversed().forEach {
    val parentStructBuilder = Struct.newBuilder()
    parentStructBuilder.putFields(it.field, structBuilder.build().toValueProto())
    structBuilder = parentStructBuilder
  }
  return structBuilder.build()
}

/**
 * Returns an [Arb] that produces [Struct] instances guaranteed to contain at least one [Value]
 * whose [Value.kindCase] is _not_ the specified [kind].
 */
private fun structWithAtLeast1ValueOfNotKindArb(kind: Value.KindCase): Arb<Struct> {
  val valueOfNotKindArb = Arb.proto.value(exclude = kind)
  return structWithAtLeast1ValueMatchingArb(valueOfNotKindArb) { it.kindCase != kind }
}

/**
 * Returns an [Arb] that produces [Struct] instances guaranteed to contain at least one [Value] for
 * which the given [predicate] returns `true`.
 */
private fun structWithAtLeast1ValueMatchingArb(
  valueArb: Arb<Value>,
  predicate: (Value) -> Boolean
): Arb<Struct> {
  val structKeyArb = Arb.proto.structKey()
  val structArb = Arb.proto.struct(key = structKeyArb)
  return arbitrary { rs ->
    val struct = structArb.bind().struct
    if (struct.walkValues().any(predicate)) {
      struct
    } else {
      val valueMatchingPredicate = valueArb.bind()
      require(predicate(valueMatchingPredicate)) {
        "internal error vwbbb2wnbw: valueArb generated a value that does not satisfy " +
          "the given predicate, but all values generated by valueArb are required " +
          "to satisfy the given predicate; generated value: $valueMatchingPredicate"
      }
      val insertionPoint =
        struct
          .walk(includeSelf = true)
          .filter { it.value.isStructValue || it.value.isListValue }
          .toList()
          .random(rs.random)
      val insertionKey =
        insertionPoint.value.structValueOrNull?.let { insertionStruct ->
          structKeyArb.filterNot { insertionStruct.containsFields(it) }.bind()
        }
      val insertionIndex =
        insertionPoint.value.listValueOrNull?.let { insertionListValue ->
          Arb.int(0..insertionListValue.valuesCount).bind()
        }
      struct.map { path, value ->
        if (path != insertionPoint.path) {
          value
        } else if (value.isStructValue) {
          value.structValue
            .toBuilder()
            .putFields(insertionKey!!, valueMatchingPredicate)
            .build()
            .toValueProto()
        } else {
          check(value.isListValue) {
            "internal error yj9vteeqma: value.kindCase=${value.kindCase}, " +
              "but expected ${Value.KindCase.LIST_VALUE} (value=$value)"
          }
          value.listValue
            .toBuilder()
            .addValues(insertionIndex!!, valueMatchingPredicate)
            .build()
            .toValueProto()
        }
      }
    }
  }
}

/** Unit tests for private helper functions defined in this file. */
class ProtoGraftTestingUnitTest {

  @Before
  fun registerPrinters() {
    registerDataConnectKotestPrinters()
  }

  @Test
  fun `withParents() when path is empty should throw`() = runTest {
    checkAll(propTestConfig, Arb.proto.value()) { value ->
      shouldThrow<IllegalArgumentException> { value.withParents(emptyList()) }
    }
  }

  @Test
  fun `withParents() should return a struct with the expected paths`() = runTest {
    checkAll(propTestConfig, Arb.proto.value(), Arb.list(fieldPathSegmentArb(), 1..5)) { value, path
      ->
      val result = value.withParents(path)

      val actualPaths = result.walkPaths(includeSelf = false).toList()
      val expectedPaths = buildList {
        value.walkPaths(includeSelf = false).map { path + it }.forEach { add(it) }
        (1..path.size).forEach { add(path.subList(0, it)) }
      }
      actualPaths shouldContainExactlyInAnyOrder expectedPaths
    }
  }

  @Test
  fun `withParents() should contain the receiver at the given path`() = runTest {
    checkAll(propTestConfig, Arb.proto.value(), Arb.list(fieldPathSegmentArb(), 1..5)) { value, path
      ->
      val result = value.withParents(path)

      val valueAtPath = result.walk().filter { it.path == path }.single().value
      valueAtPath shouldBe value
    }
  }

  @Test
  fun `toExpectedStructWithValuesGraftedInToExistingStruct() when valueByPath is empty should return the same instance`() =
    runTest {
      checkAll(propTestConfig, Arb.proto.struct()) { struct ->
        val result = struct.struct.toExpectedStructWithValuesGraftedInToExistingStruct(emptyMap())

        result shouldBe struct.struct
      }
    }

  @Test
  fun `toExpectedStructWithValuesGraftedInToExistingStruct() when valueByPath contains paths whose immediate parent is an existing struct should graft correctly`() =
    runTest {
      checkAll(propTestConfig, Arb.proto.struct(), Arb.list(Arb.proto.value(), 1..5)) {
        struct,
        valuesToGraft ->
        val destPaths = pathToNonExistentFieldInExistingStructSequence(struct.struct).iterator()
        val valueByPath = valuesToGraft.associateBy { destPaths.next() }

        val result = struct.struct.toExpectedStructWithValuesGraftedInToExistingStruct(valueByPath)

        result.walk().forEach { (path, value) ->
          valueByPath[path]?.let { expectedValue ->
            withClue("path=${path.toPathString()}") { value shouldBe expectedValue }
          }
        }
        val prunedResult =
          result.map { path, value -> if (valueByPath.containsKey(path)) null else value }
        withClue("prunedResult") { prunedResult shouldBe struct.struct }
      }
    }

  @Test
  fun `toExpectedStructWithValuesGraftedInToExistingStruct() when valueByPath contains an existing key should throw`() =
    runTest {
      val nonEmptyStructArb = Arb.proto.struct().filterNot { it.struct.fieldsCount == 0 }
      checkAll(propTestConfig, nonEmptyStructArb, Arb.proto.value()) { struct, valueToGraft ->
        val existingPaths =
          struct.struct
            .walkPaths()
            .filter { it.lastOrNull() is DataConnectPathSegment.Field }
            .toList()
        val existingPath = Arb.of(existingPaths).bind()
        val valueByPath = mapOf(existingPath to valueToGraft)

        shouldThrow<IllegalArgumentException> {
          struct.struct.toExpectedStructWithValuesGraftedInToExistingStruct(valueByPath)
        }
      }
    }

  @Test
  fun `structWithAtLeast1ValueOfNotKindArb should generate structs with at least 1 scalar value`() =
    runTest {
      checkAll(propTestConfig, Exhaustive.enum<Value.KindCase>()) { kindCase ->
        val arb = structWithAtLeast1ValueOfNotKindArb(kindCase)

        val struct = arb.bind()

        val notKindCaseCount = struct.walk().filterNot { it.value.kindCase == kindCase }.count()
        notKindCaseCount shouldBeGreaterThan 0
      }
    }
}
