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
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.property.arbitrary.structKey
import com.google.firebase.dataconnect.testutil.property.arbitrary.twoValues
import com.google.firebase.dataconnect.testutil.property.arbitrary.value
import com.google.firebase.dataconnect.testutil.property.arbitrary.valueOfKind
import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import com.google.firebase.dataconnect.testutil.structValueOrNull
import com.google.firebase.dataconnect.testutil.walk
import com.google.firebase.dataconnect.testutil.walkPaths
import com.google.firebase.dataconnect.testutil.walkValues
import com.google.firebase.dataconnect.toPathString
import com.google.firebase.dataconnect.util.ProtoGraft.withGraftedInStructs
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
import org.junit.Test

class ProtoGraftUnitTest {

  @Test
  fun `withGraftedInStructs() with empty structsByPath should return receiver Struct`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct()) { struct ->
      struct.struct.withGraftedInStructs(emptyMap()) shouldBeSameInstanceAs struct.struct
    }
  }

  @Test
  fun `withGraftedInStructs() with only the empty path`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct(), Arb.proto.struct()) { struct, structToGraft ->
      val structsByPath = mapOf(emptyDataConnectPath() to structToGraft.struct)

      struct.struct.withGraftedInStructs(structsByPath) shouldBeSameInstanceAs structToGraft.struct
    }
  }

  @Test
  fun `withGraftedInStructs() with single-segment paths`() = runTest {
    val structArb = Arb.proto.struct()
    checkAll(propTestConfig, structArb, Arb.list(structArb, 1..5)) { struct, structsToGraft ->
      val structKeyArb = Arb.proto.structKey().filterNot { struct.struct.containsFields(it) }
      val structsByFieldName: Map<String, Struct> =
        structsToGraft.associate { structKeyArb.bind() to it.struct }
      val structsByPath: Map<DataConnectPath, Struct> =
        structsByFieldName.mapKeys { entry -> listOf(DataConnectPathSegment.Field(entry.key)) }

      val result = struct.struct.withGraftedInStructs(structsByPath)

      val expectedResult: Struct =
        struct.struct.toBuilder().let { structBuilder ->
          structsByFieldName.entries.forEach { (fieldName, structToGraft) ->
            structBuilder.putFields(fieldName, structToGraft.toValueProto())
          }
          structBuilder.build()
        }
      result shouldBe expectedResult
    }
  }

  @Test
  fun `withGraftedInStructs() with paths ending with list index should throw`() = runTest {
    val structArb = Arb.proto.struct()
    checkAll(propTestConfig, structArb, structArb, dataConnectPathArb(), Arb.int()) {
      struct,
      structToGraft,
      graftPathPrefix,
      graftPathLastSegmentIndex ->
      val graftPath = graftPathPrefix.withAddedListIndex(graftPathLastSegmentIndex)
      val structsByPath = mapOf(graftPath to structToGraft.struct)

      val exception =
        shouldThrow<ProtoGraft.LastPathSegmentNotFieldException> {
          struct.struct.withGraftedInStructs(structsByPath)
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
  fun `withGraftedInStructs() with paths ending with an existing key should throw`() = runTest {
    val structArb = Arb.proto.struct()
    val nonEmptyStructArb = structArb.filterNot { it.struct.fieldsCount == 0 }
    checkAll(propTestConfig, nonEmptyStructArb, structArb) { struct, structToGraft ->
      val existingPaths =
        struct.struct
          .walkPaths()
          .filter { it.lastOrNull() is DataConnectPathSegment.Field }
          .toList()
      val existingPath = Arb.of(existingPaths).bind()
      val structsByPath = mapOf(existingPath to structToGraft.struct)

      val exception =
        shouldThrow<ProtoGraft.KeyExistsException> {
          struct.struct.withGraftedInStructs(structsByPath)
        }

      assertSoftly {
        exception.message shouldContainWithNonAbuttingText "ecgd5r2v4a"
        exception.message shouldContainWithNonAbuttingText existingPath.toPathString()
        exception.message shouldContainWithNonAbuttingText existingPath.dropLast(1).toPathString()
        val existingField = (existingPath.last() as DataConnectPathSegment.Field).field
        exception.message shouldContainWithNonAbuttingTextIgnoringCase
          "already has a field named $existingField"
        exception.message shouldContainWithNonAbuttingTextIgnoringCase
          "it is required to not already have that key"
      }
    }
  }

  @Test
  fun `withGraftedInStructs() with paths whose immediate parent is an existing struct`() = runTest {
    val structArb = Arb.proto.struct()
    checkAll(propTestConfig, structArb, Arb.list(structArb, 1..5)) { struct, structsToGraft ->
      val destPaths = pathToNonExistentFieldInExistingStructSequence(struct.struct).iterator()
      val structsByPath = structsToGraft.map { it.struct }.associateBy { destPaths.next() }

      val result = struct.struct.withGraftedInStructs(structsByPath)

      val expectedResult =
        struct.struct.toExpectedStructWithStructsGraftedInToExistingStruct(structsByPath)
      result shouldBe expectedResult
    }
  }

  @Test
  fun `withGraftedInStructs() with paths whose immediate parent does not exist`() = runTest {
    val structArb = Arb.proto.struct()
    val fieldPathSegmentArb = fieldPathSegmentArb(Arb.proto.structKey())
    val fieldPathSegmentsSizeArb = Arb.int(1..5)
    checkAll(propTestConfig, structArb, Arb.list(structArb, 1..5)) { struct, structsToGraft ->
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
          .take(structsToGraft.size)
          .toList()
      val structsByPath =
        structsToGraft.zip(destPaths).associate { (struct, destPath) ->
          Pair(destPath.fullPath, struct.struct)
        }

      val result = struct.struct.withGraftedInStructs(structsByPath)

      val prefixedStructsByPath =
        structsToGraft.zip(destPaths).associate { (struct, destPath) ->
          val suffix = destPath.suffix.map { it as DataConnectPathSegment.Field }
          Pair(destPath.prefix, struct.struct.withParents(suffix))
        }
      val expectedResult =
        struct.struct.toExpectedStructWithStructsGraftedInToExistingStruct(prefixedStructsByPath)
      result shouldBe expectedResult
    }
  }

  @Test
  fun `withGraftedInStructs() with paths of non-structs should throw`() = runTest {
    checkAll(
      propTestConfig,
      structWithAtLeast1ValueOfNotKindArb(Value.KindCase.STRUCT_VALUE),
      Arb.proto.struct(),
      fieldPathSegmentArb()
    ) { struct, structToGraft, fieldPathSegment ->
      val nonStructs = struct.walk().filterNot { it.value.isStructValue }.toList()
      val nonStruct = Arb.of(nonStructs).bind()
      val structToGraftPath = nonStruct.path + listOf(fieldPathSegment)
      val structsByPath = mapOf(structToGraftPath to structToGraft.struct)

      val exception =
        shouldThrow<ProtoGraft.InsertIntoNonStructException> {
          struct.withGraftedInStructs(structsByPath)
        }

      assertSoftly {
        exception.message shouldContainWithNonAbuttingText "zcj277ka6a"
        exception.message shouldContainWithNonAbuttingText nonStruct.path.toPathString()
        exception.message shouldContainWithNonAbuttingText structToGraftPath.toPathString()
        exception.message shouldContainWithNonAbuttingTextIgnoringCase
          "has kind case ${nonStruct.value.kindCase}"
        exception.message shouldContainWithNonAbuttingTextIgnoringCase
          "required to be ${Value.KindCase.STRUCT_VALUE}"
      }
    }
  }

  @Test
  fun `withGraftedInStructs() with paths with a field segment that is not a struct should throw`() =
    runTest {
      checkAll(
        propTestConfig,
        structWithAtLeast1ValueOfNotKindArb(Value.KindCase.STRUCT_VALUE),
        Arb.proto.struct(),
        dataConnectPathArb(size = 0..5),
        Arb.twoValues(fieldPathSegmentArb())
      ) { struct, structToGraft, pathSuffix, (fieldSegment1, fieldSegment2) ->
        val nonStructs = struct.walk().filterNot { it.value.isStructValue }.toList()
        val (nonStructPath, nonStructValue) = Arb.of(nonStructs).bind()
        val graftPath = buildList {
          addAll(nonStructPath)
          add(fieldSegment1)
          addAll(pathSuffix)
          add(fieldSegment2)
        }
        val structsByPath = mapOf(graftPath to structToGraft.struct)

        val exception =
          shouldThrow<ProtoGraft.PathFieldOfNonStructException> {
            struct.withGraftedInStructs(structsByPath)
          }

        assertSoftly {
          exception.message shouldContainWithNonAbuttingText "s3mhtfj2mm"
          exception.message shouldContainWithNonAbuttingText graftPath.toPathString()
          exception.message shouldContainWithNonAbuttingTextIgnoringCase
            "whose segment ${nonStructPath.size} " +
              "(${nonStructPath.last().toExpectedDescriptionInExceptionMessages()})"
          exception.message shouldContainWithNonAbuttingTextIgnoringCase
            "has kind case ${nonStructValue.kindCase}"
          exception.message shouldContainWithNonAbuttingTextIgnoringCase
            "required to be ${Value.KindCase.STRUCT_VALUE}"
        }
      }
    }

  @Test
  fun `withGraftedInStructs() with paths with a list index segment that is not a list should throw`() =
    runTest {
      checkAll(
        propTestConfig,
        structWithAtLeast1ValueOfNotKindArb(Value.KindCase.LIST_VALUE),
        Arb.proto.struct(),
        dataConnectPathArb(size = 0..5),
        listIndexPathSegmentArb(),
        fieldPathSegmentArb()
      ) { struct, structToGraft, pathSuffix, listIndexSegment, fieldSegment ->
        val nonListValues = struct.walk().filterNot { it.value.isListValue }.toList()
        val (nonListValuePath, nonListValueValue) = Arb.of(nonListValues).bind()
        val graftPath = buildList {
          addAll(nonListValuePath)
          add(listIndexSegment)
          addAll(pathSuffix)
          add(fieldSegment)
        }
        val structsByPath = mapOf(graftPath to structToGraft.struct)

        val exception =
          shouldThrow<ProtoGraft.PathListIndexOfNonListException> {
            struct.withGraftedInStructs(structsByPath)
          }

        assertSoftly {
          exception.message shouldContainWithNonAbuttingText "gr7mqk4jnn"
          exception.message shouldContainWithNonAbuttingText graftPath.toPathString()
          exception.message shouldContainWithNonAbuttingTextIgnoringCase
            "whose segment ${nonListValuePath.size} " +
              "(${nonListValuePath.last().toExpectedDescriptionInExceptionMessages()})"
          exception.message shouldContainWithNonAbuttingTextIgnoringCase
            "has kind case ${nonListValueValue.kindCase}"
          exception.message shouldContainWithNonAbuttingTextIgnoringCase
            "required to be ${Value.KindCase.LIST_VALUE}"
        }
      }
    }

  @Test
  fun `withGraftedInStructs() with paths with a negative list index segment should throw`() =
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
  fun `withGraftedInStructs() with paths with a too-large list index segment should throw`() =
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
      Arb.proto.struct(),
      dataConnectPathArb(size = 0..5),
      fieldPathSegmentArb()
    ) { struct, structToGraft, pathSuffix, fieldSegment ->
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
      val structsByPath = mapOf(graftPath to structToGraft.struct)

      val exception = shouldThrow<E> { struct.withGraftedInStructs(structsByPath) }

      assertSoftly {
        validateMessage(exception, graftPath, listValuePath, listValue, outOfRangeListIndex)
      }
    }
  }
}

@OptIn(ExperimentalKotest::class)
private val propTestConfig =
  PropTestConfig(iterations = 200, edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2))

private fun DataConnectPathSegment.toExpectedDescriptionInExceptionMessages(): String =
  when (this) {
    is DataConnectPathSegment.Field -> "field $field"
    is DataConnectPathSegment.ListIndex -> "list index $index"
  }

/**
 * Creates a [Struct] by grafting in other [Struct] objects into the receiver [Struct].
 *
 * This function is intended to construct the expected output [Struct] after a grafting operation.
 * It iterates through the receiver [Struct] and, for any [Struct] values encountered, it checks if
 * there are corresponding [Struct] objects to be grafted in at that path. If so, it grafts them
 * into the existing [Struct] at the specified path.
 *
 * The grafting process ensures that each [Struct] in [structsByPath] is placed at its designated
 * [DataConnectPath] within the receiver [Struct]. It's crucial that the paths in [structsByPath]
 * point to non-existent fields within existing [Struct] values in the receiver, or directly to the
 * root if the path is empty.
 *
 * @param structsByPath A map where keys are [DataConnectPath] objects indicating where a [Struct]
 * should be grafted, and values are the [Struct] objects to be grafted.
 * @return A new [Struct] instance that is the receiver [Struct] with the provided [structsByPath]
 * grafted into it.
 */
private fun Struct.toExpectedStructWithStructsGraftedInToExistingStruct(
  structsByPath: Map<DataConnectPath, Struct>
): Struct {
  val structsRemaining = structsByPath.entries.toMutableList()
  val result = map { path, value ->
    if (!value.isStructValue) {
      value
    } else {
      val curStructs = structsRemaining.extract { _, entry -> path == entry.key.dropLast(1) }
      if (curStructs.isEmpty()) {
        value
      } else {
        value.structValue.toBuilder().let { structBuilder ->
          curStructs.forEach { (path, subStruct) ->
            val field = (path.last() as DataConnectPathSegment.Field).field
            require(!structBuilder.containsFields(field))
            structBuilder.putFields(field, subStruct.toValueProto())
          }
          structBuilder.build().toValueProto()
        }
      }
    }
  }
  check(structsRemaining.isEmpty())
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
 * Wraps the receiver [Struct] in parent [Struct] objects to place it at the given [path].
 *
 * For example, if this [Struct] is `{ "key": "value" }` and the path is `["a", "b"]`, the returned
 * struct will be `{ "a": { "b": { "key": "value" } } }`.
 *
 * @param path The path where the receiver [Struct] will be in the returned [Struct].
 * @return A new [Struct] with the receiver [Struct] at the specified path, or this same [Struct]
 * instance if the path is empty.
 */
private fun Struct.withParents(path: List<DataConnectPathSegment.Field>): Struct {
  if (path.isEmpty()) {
    return this
  }
  var structBuilder = Struct.newBuilder()
  structBuilder.putFields(path.last().field, toValueProto())
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

  @Test
  fun `withParents() when path is empty should return the receiver struct`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct()) { struct ->
      struct.struct.withParents(emptyList()) shouldBeSameInstanceAs struct.struct
    }
  }

  @Test
  fun `withParents() when path is non-empty should return a struct with the expected paths`() =
    runTest {
      val stringArb = Arb.proto.structKey()
      checkAll(
        propTestConfig,
        Arb.proto.struct(key = stringArb),
        Arb.list(fieldPathSegmentArb(stringArb), 0..5)
      ) { struct, path ->
        val result = struct.struct.withParents(path)

        val actualPaths = result.walkPaths(includeSelf = false).toList()
        val expectedPaths = buildList {
          struct.struct.walkPaths(includeSelf = false).map { path + it }.forEach { add(it) }
          (1..path.size).forEach { add(path.subList(0, it)) }
        }
        actualPaths shouldContainExactlyInAnyOrder expectedPaths
      }
    }

  @Test
  fun `withParents() when path is non-empty should contain the receiver at the given path`() =
    runTest {
      val stringArb = Arb.proto.structKey()
      checkAll(
        propTestConfig,
        Arb.proto.struct(key = stringArb),
        Arb.list(fieldPathSegmentArb(stringArb), 0..5)
      ) { struct, path ->
        val result = struct.struct.withParents(path)

        val value = result.walk(includeSelf = true).filter { it.path == path }.single().value
        value.structValue shouldBe struct.struct
      }
    }

  @Test
  fun `toExpectedStructWithStructsGraftedInToExistingStruct() when structsByPath is empty should return the same instance`() =
    runTest {
      checkAll(propTestConfig, Arb.proto.struct()) { struct ->
        val result = struct.struct.toExpectedStructWithStructsGraftedInToExistingStruct(emptyMap())

        result shouldBe struct.struct
      }
    }

  @Test
  fun `toExpectedStructWithStructsGraftedInToExistingStruct() when structsByPath contains paths whose immediate parent is an existing struct should graft correctly`() =
    runTest {
      val structArb = Arb.proto.struct()
      checkAll(propTestConfig, structArb, Arb.list(structArb, 1..5)) { struct, structsToGraft ->
        val destPaths = pathToNonExistentFieldInExistingStructSequence(struct.struct).iterator()
        val structsByPath = structsToGraft.map { it.struct }.associateBy { destPaths.next() }

        val result =
          struct.struct.toExpectedStructWithStructsGraftedInToExistingStruct(structsByPath)

        result.walk(includeSelf = true).forEach { (path, value) ->
          val expectedStruct = structsByPath[path]
          if (expectedStruct !== null) {
            withClue("path=${path.toPathString()}") {
              value.structValueOrNull shouldBe expectedStruct
            }
          }
        }
        val prunedResult =
          result.map { path, value -> if (structsByPath.containsKey(path)) null else value }
        withClue("prunedResult") { prunedResult shouldBe struct.struct }
      }
    }

  @Test
  fun `toExpectedStructWithStructsGraftedInToExistingStruct() when structsByPath contains an existing key should throw`() =
    runTest {
      val structArb = Arb.proto.struct()
      val nonEmptyStructArb = structArb.filterNot { it.struct.fieldsCount == 0 }
      checkAll(propTestConfig, nonEmptyStructArb, structArb) { struct, structToGraft ->
        val existingPaths =
          struct.struct
            .walkPaths()
            .filter { it.lastOrNull() is DataConnectPathSegment.Field }
            .toList()
        val existingPath = Arb.of(existingPaths).bind()
        val structsByPath = mapOf(existingPath to structToGraft.struct)

        shouldThrow<IllegalArgumentException> {
          struct.struct.toExpectedStructWithStructsGraftedInToExistingStruct(structsByPath)
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
