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

import com.google.firebase.dataconnect.DataConnectPath
import com.google.firebase.dataconnect.DataConnectPathSegment
import com.google.firebase.dataconnect.emptyDataConnectPath
import com.google.firebase.dataconnect.testutil.isStructValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb.dataConnectPath as dataConnectPathArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb.fieldPathSegment as dataConnectFieldPathSegmentArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb.listIndexPathSegment as dataConnectListIndexPathSegmentArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb.pathSegment as dataConnectPathSegmentArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.ProtoArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.listValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.property.arbitrary.structKey
import com.google.firebase.dataconnect.testutil.walk
import com.google.firebase.dataconnect.toPathString
import com.google.firebase.dataconnect.util.ProtoPrune.withDescendantStructsPruned
import com.google.firebase.dataconnect.withAddedListIndex
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import io.kotest.assertions.shouldFail
import io.kotest.assertions.withClue
import io.kotest.common.DelicateKotest
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.PropertyContext
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.distinct
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.collections.map
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ProtoPruneUnitTest {

  @Test
  fun `Struct withDescendantStructsPruned() should return null if nothing is pruned`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct(), dataConnectPathArb()) { structSample, path ->
      val struct: Struct = structSample.struct
      val predicate = predicateThatUnconditionallyReturnsFalse()

      val result = struct.withDescendantStructsPruned(path, predicate)

      result.shouldBeNull()
    }
  }

  @Test
  fun `ListValue withDescendantStructsPruned() should return null if nothing is pruned`() =
    runTest {
      checkAll(propTestConfig, Arb.proto.listValue(), dataConnectPathArb()) { listValueSample, path
        ->
        val listValue: ListValue = listValueSample.listValue
        val predicate = predicateThatUnconditionallyReturnsFalse()

        val result = listValue.withDescendantStructsPruned(path, predicate)

        result.shouldBeNull()
      }
    }

  @Test
  fun `Struct withDescendantStructsPruned() should call the predicate with all candidates`() =
    runTest {
      checkAll(propTestConfig, Arb.proto.struct(), dataConnectPathArb()) { structSample, path ->
        val struct: Struct = structSample.struct
        val predicate: PrunePredicate = mockk()
        every { predicate(any(), any()) } returns false

        struct.withDescendantStructsPruned(path, predicate)

        val expectedInvocations = struct.calculateExpectedPruneInvocations(path)
        expectedInvocations.forEach { (expectedPath, expectedStruct) ->
          verify(exactly = 1) { predicate(eq(expectedPath), eq(expectedStruct)) }
        }
        confirmVerified(predicate)
      }
    }

  @Test
  fun `ListValue withDescendantStructsPruned() should call the predicate with all candidates`() =
    runTest {
      checkAll(propTestConfig, Arb.proto.listValue(), dataConnectPathArb()) { listValueSample, path
        ->
        val listValue: ListValue = listValueSample.listValue
        val predicate: PrunePredicate = mockk()
        every { predicate(any(), any()) } returns false

        listValue.withDescendantStructsPruned(path, predicate)

        val expectedInvocations = listValue.calculateExpectedPruneInvocations(path)
        expectedInvocations.forEach { (expectedPath, expectedStruct) ->
          verify(exactly = 1) { predicate(eq(expectedPath), eq(expectedStruct)) }
        }
        confirmVerified(predicate)
      }
    }

  @Test
  fun `Struct withDescendantStructsPruned() should not call the predicate with children of pruned structs`() =
    runTest {
      checkAll(propTestConfig, Arb.proto.struct(), dataConnectPathArb(), Arb.float(0.0f..1.0f)) {
        structSample,
        path,
        pruneProbability ->
        val struct: Struct = structSample.struct
        val predicateCalls: MutableList<PredicateCall> = mutableListOf()
        val predicate = predicateReturningTrueProbabilistically(pruneProbability, predicateCalls)

        struct.withDescendantStructsPruned(path, predicate)

        val prunedPaths = predicateCalls.filter { it.returnValue }.map { it.path }
        withClue("prunedPaths={${prunedPaths.joinToString { it.toPathString() }}}") {
          prunedPaths.shouldNotContainParentsOfAnyElement()
        }
      }
    }

  @Test
  fun `ListValue withDescendantStructsPruned() should not call the predicate with children of pruned structs`() =
    runTest {
      checkAll(
        propTestConfig,
        Arb.proto.listValue(),
        dataConnectPathArb(),
        Arb.float(0.0f..1.0f)
      ) { listValueSample, path, pruneProbability ->
        val listValue: ListValue = listValueSample.listValue
        val predicateCalls: MutableList<PredicateCall> = mutableListOf()
        val predicate = predicateReturningTrueProbabilistically(pruneProbability, predicateCalls)

        listValue.withDescendantStructsPruned(path, predicate)

        val prunedPaths = predicateCalls.filter { it.returnValue }.map { it.path }
        withClue("prunedPaths={${prunedPaths.joinToString { it.toPathString() }}}") {
          prunedPaths.shouldNotContainParentsOfAnyElement()
        }
      }
    }

  @Test
  fun `Struct withDescendantStructsPruned() should return the correct prunedStructsByPath`() =
    runTest {
      val structArb =
        structWithEligiblePruningPathsSampleArb().filter { it.eligiblePaths.isNotEmpty() }
      checkAll(propTestConfig, structArb, dataConnectPathArb(), Arb.float(0.0f..1.0f)) {
        structSample,
        path,
        pruneProbability ->
        val struct: Struct = structSample.structInfo.struct
        val forcedPrunePath = Arb.of(structSample.eligiblePaths).bind()
        val predicateCalls: MutableList<PredicateCall> = mutableListOf()
        val predicate =
          predicateReturningTrueProbabilistically(
            pruneProbability,
            predicateCalls,
            forcedReturnValues = mapOf(path + forcedPrunePath to true)
          )

        val result = struct.withDescendantStructsPruned(path, predicate)

        val expectedPrunedStructsByPath =
          predicateCalls.filter { it.returnValue }.associate { it.path to it.struct }
        result.shouldNotBeNull().prunedStructsByPath shouldBe expectedPrunedStructsByPath
      }
    }

  @Test
  fun `ListValue withDescendantStructsPruned() should return the correct prunedStructsByPath`() =
    runTest {
      val listValueArb =
        listValueWithEligiblePruningPathsSampleArb().filter { it.eligiblePaths.isNotEmpty() }
      checkAll(propTestConfig, listValueArb, dataConnectPathArb(), Arb.float(0.0f..1.0f)) {
        listValueSample,
        path,
        pruneProbability ->
        val listValue: ListValue = listValueSample.listValueInfo.listValue
        val forcedPrunePath = Arb.of(listValueSample.eligiblePaths).bind()
        val predicateCalls: MutableList<PredicateCall> = mutableListOf()
        val predicate =
          predicateReturningTrueProbabilistically(
            pruneProbability,
            predicateCalls,
            forcedReturnValues = mapOf(path + forcedPrunePath to true)
          )

        val result = listValue.withDescendantStructsPruned(path, predicate)

        val expectedPrunedStructsByPath =
          predicateCalls.filter { it.returnValue }.associate { it.path to it.struct }
        result.shouldNotBeNull().prunedStructsByPath shouldBe expectedPrunedStructsByPath
      }
    }

  @Test
  fun `Struct withDescendantStructsPruned() should return the correct prunedStruct`() = runTest {
    TODO()
  }

  @Test
  fun `ListValue withDescendantStructsPruned() should return the correct prunedStruct`() = runTest {
    TODO()
  }
}

@OptIn(ExperimentalKotest::class)
private val propTestConfig =
  PropTestConfig(iterations = 200, edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2))

private typealias PrunePredicate = (path: DataConnectPath, struct: Struct) -> Boolean

/** Returns a [PrunePredicate] that unconditionally returns `false`. */
private fun predicateThatUnconditionallyReturnsFalse(): PrunePredicate = { _, _ -> false }

private data class PredicateCall(
  val path: DataConnectPath,
  val struct: Struct,
  val returnValue: Boolean,
)

/**
 * Returns a [PrunePredicate] that returns `true` with a given probability.
 *
 * This is used for property-based testing to simulate a predicate that sometimes prunes. All calls
 * to the predicate are recorded in the [calls] list.
 *
 * The [forcedReturnValues] can be used to override the return value for a given path.
 */
private fun PropertyContext.predicateReturningTrueProbabilistically(
  trueProbability: Float,
  calls: MutableList<PredicateCall>,
  forcedReturnValues: Map<DataConnectPath, Boolean> = emptyMap()
): PrunePredicate = { path, struct ->
  val returnValue =
    forcedReturnValues[path] ?: (randomSource().random.nextFloat() < trueProbability)
  calls.add(PredicateCall(path, struct, returnValue))
  returnValue
}

private data class DataConnectPathStructPair(val path: DataConnectPath, val struct: Struct)

/**
 * Calculates the expected invocations of a [PrunePredicate] that unconditionally returns `false`
 * for a [Struct].
 *
 * This function walks the [Struct] and returns a list of all the descendant [Struct]s that are
 * eligible for pruning, along with their corresponding [DataConnectPath].
 *
 * A [Struct] is considered eligible for pruning if it is not a direct element of a [ListValue] and
 * not the root [Struct] itself.
 *
 * @param basePath The base path to prepend to the paths of the descendant [Struct]s.
 */
private fun Struct.calculateExpectedPruneInvocations(
  basePath: DataConnectPath
): List<DataConnectPathStructPair> =
  walk()
    .filter { it.path.isEligibleForPruning(it.value) }
    .map { DataConnectPathStructPair(basePath + it.path, it.value.structValue) }
    .toList()

/**
 * Returns whether the receiver path is eligible for pruning where its value is the given [Value].
 *
 * This function assumes that the root element is a [Struct] (namely, not a [ListValue]).
 *
 * A path is eligible for pruning if its value is a [Struct] and if it is not a direct element of a
 * [ListValue] and not the root [Struct] itself.
 */
private fun DataConnectPath.isEligibleForPruning(value: Value): Boolean =
  isNotEmpty() &&
    value.isStructValue &&
    last() is DataConnectPathSegment.Field &&
    dropLast(1).lastOrNull().let { penultimateSegment ->
      penultimateSegment === null || penultimateSegment is DataConnectPathSegment.Field
    }

/**
 * Calculates the expected invocations of a [PrunePredicate] that unconditionally returns `false`
 * for a [ListValue].
 *
 * This function walks the [ListValue] and returns a list of all the descendant [Struct]s that are
 * eligible for pruning, along with their corresponding [DataConnectPath].
 *
 * A [Struct] is considered eligible for pruning if it is not a direct element of a [ListValue].
 *
 * @param basePath The base path to prepend to the paths of the descendant [Struct]s.
 */
private fun ListValue.calculateExpectedPruneInvocations(
  basePath: DataConnectPath
): List<DataConnectPathStructPair> = buildList {
  valuesList.forEachIndexed { listIndex, listElement ->
    if (listElement.isStructValue) {
      addAll(
        listElement.structValue.calculateExpectedPruneInvocations(
          basePath.withAddedListIndex(listIndex)
        )
      )
    }
  }
}

/**
 * Asserts that for every [DataConnectPath] in this list, none of its parent paths are also present
 * in the list.
 *
 * For example, if the list contains the path `a.b.c`, this function will fail if the list also
 * contains `a.b` or `a`.
 */
private fun List<DataConnectPath>.shouldNotContainParentsOfAnyElement() {
  forEachIndexed { index, path ->
    val parentPath = path.toMutableList()
    while (parentPath.isNotEmpty()) {
      parentPath.removeLast()
      withClue("index=$index, path=${path.toPathString()}") {
        this shouldNotContain parentPath.toList()
      }
    }
  }
}

private data class StructWithEligiblePruningPathsSample(
  val structInfo: ProtoArb.StructInfo,
  val eligiblePaths: List<DataConnectPath>,
)

/**
 * Returns an [Arb] that generates [Struct] objects along with the list of paths therein that are
 * eligible for pruning.
 *
 * Each sample contains a randomly generated [Struct] and a list of all the paths therein that are
 * eligible for pruning.
 */
private fun structWithEligiblePruningPathsSampleArb(): Arb<StructWithEligiblePruningPathsSample> =
  Arb.proto.struct().map { structSample ->
    val eligiblePaths =
      structSample.struct.calculateExpectedPruneInvocations(emptyDataConnectPath()).map { it.path }
    StructWithEligiblePruningPathsSample(structSample, eligiblePaths)
  }

private data class ListValueWithEligiblePruningPathsSample(
  val listValueInfo: ProtoArb.ListValueInfo,
  val eligiblePaths: List<DataConnectPath>,
)

/**
 * Returns an [Arb] that generates [ListValue] objects along with the list of paths therein that are
 * eligible for pruning.
 *
 * Each sample contains a randomly generated [ListValue] and a list of all the paths therein that
 * are eligible for pruning.
 */
private fun listValueWithEligiblePruningPathsSampleArb():
  Arb<ListValueWithEligiblePruningPathsSample> =
  Arb.proto.listValue().map { listValueSample ->
    val eligiblePaths =
      listValueSample.listValue.calculateExpectedPruneInvocations(emptyDataConnectPath()).map {
        it.path
      }
    ListValueWithEligiblePruningPathsSample(listValueSample, eligiblePaths)
  }

/** Unit tests for private helper functions defined in this file. */
class ProtoPruneTestingUnitTest {

  @Test
  fun `shouldNotContainParentsOfAnyElement() should fail if contains parent`() = runTest {
    checkAll(propTestConfig, dataConnectPathArb(), dataConnectPathArb(size = 1..5)) { path1, path2
      ->
      val paths = listOf(path1, path1 + path2)

      shouldFail { paths.shouldNotContainParentsOfAnyElement() }
    }
  }

  @Test
  @OptIn(DelicateKotest::class)
  fun `shouldNotContainParentsOfAnyElement() should pass if no overlapping paths`() = runTest {
    checkAll(propTestConfig, Arb.int(0..5)) { pathCount ->
      val dataConnectPathArb =
        dataConnectPathArb(
          size = 1..5,
          field = dataConnectFieldPathSegmentArb(Arb.proto.structKey().distinct()),
          listIndex = dataConnectListIndexPathSegmentArb(Arb.int().distinct()),
        )
      val paths = List(pathCount) { dataConnectPathArb.bind() }

      paths.shouldNotContainParentsOfAnyElement()
    }
  }

  @Test
  fun `shouldNotContainParentsOfAnyElement() should pass if paths share same parent`() = runTest {
    val dataConnectPathArb = dataConnectPathArb()
    checkAll(propTestConfig, dataConnectPathArb, Arb.int(2..5)) { basePath, siblingCount ->
      @OptIn(DelicateKotest::class)
      val siblingPathArb =
        Arb.bind(dataConnectPathSegmentArb().distinct(), dataConnectPathArb) { segment, pathSuffix
          ->
          basePath + listOf(segment) + pathSuffix
        }
      val paths = List(siblingCount) { siblingPathArb.bind() }

      paths.shouldNotContainParentsOfAnyElement()
    }
  }
}
