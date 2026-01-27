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
import com.google.firebase.dataconnect.testutil.isListValue
import com.google.firebase.dataconnect.testutil.isStructValue
import com.google.firebase.dataconnect.testutil.map
import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb.dataConnectPath as dataConnectPathArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.ProtoArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.listValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.next
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.property.arbitrary.structKey
import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.firebase.dataconnect.testutil.walk
import com.google.firebase.dataconnect.testutil.withRandomlyInsertedStruct
import com.google.firebase.dataconnect.util.ProtoPrune.withDescendantStructsPruned
import com.google.firebase.dataconnect.util.ProtoUtil.toValueProto
import com.google.firebase.dataconnect.withAddedListIndex
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import io.kotest.assertions.assertSoftly
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.asSample
import io.kotest.property.checkAll
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlin.random.nextInt
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ProtoPruneUnitTest {

  @Test
  fun `Struct withDescendantStructsPruned() on empty Struct returns null and never calls predicate`() =
    runTest {
      checkAll(propTestConfig, dataConnectPathArb()) { basePath ->
        val predicate: PrunePredicate = mockk()

        val result = Struct.getDefaultInstance().withDescendantStructsPruned(basePath, predicate)

        assertSoftly {
          result.shouldBeNull()
          confirmVerified(predicate)
        }
      }
    }

  @Test
  fun `ListValue withDescendantStructsPruned() on empty ListValue returns null and never calls predicate`() =
    runTest {
      checkAll(propTestConfig, dataConnectPathArb()) { basePath ->
        val predicate: PrunePredicate = mockk()

        val result = ListValue.getDefaultInstance().withDescendantStructsPruned(basePath, predicate)

        assertSoftly {
          result.shouldBeNull()
          confirmVerified(predicate)
        }
      }
    }

  @Test
  fun `Struct withDescendantStructsPruned() when no prunable structs returns null and never calls predicate`() =
    runTest {
      checkAll(propTestConfig, dataConnectPathArb(), nonPrunableStructArb()) {
        basePath,
        struct: Struct ->
        val predicate: PrunePredicate = mockk()

        val result = struct.withDescendantStructsPruned(basePath, predicate)

        assertSoftly {
          result.shouldBeNull()
          confirmVerified(predicate)
        }
      }
    }

  @Test
  fun `ListValue withDescendantStructsPruned() when no prunable structs returns null and never calls predicate`() =
    runTest {
      checkAll(propTestConfig, dataConnectPathArb(), nonPrunableListValueArb()) {
        basePath,
        listValue: ListValue ->
        val predicate: PrunePredicate = mockk()

        val result = listValue.withDescendantStructsPruned(basePath, predicate)

        assertSoftly {
          result.shouldBeNull()
          confirmVerified(predicate)
        }
      }
    }

  @Test
  fun `Struct withDescendantStructsPruned() returns null if nothing is pruned`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct(), dataConnectPathArb()) { structSample, path ->
      val struct: Struct = structSample.struct

      val result = struct.withDescendantStructsPruned(path) { false }

      result.shouldBeNull()
    }
  }

  @Test
  fun `ListValue withDescendantStructsPruned() returns null if nothing is pruned`() = runTest {
    checkAll(propTestConfig, Arb.proto.listValue(), dataConnectPathArb()) { listValueSample, path ->
      val listValue: ListValue = listValueSample.listValue

      val result = listValue.withDescendantStructsPruned(path) { false }

      result.shouldBeNull()
    }
  }

  @Test
  fun `Struct withDescendantStructsPruned() calls the predicate with all paths eligible for pruning`() =
    runTest {
      checkAll(propTestConfig, Arb.proto.struct(), dataConnectPathArb(), Arb.float(0.0f..1.0f)) {
        structSample,
        path,
        trueReturnProbability ->
        val struct: Struct = structSample.struct
        val predicateCallPaths = mutableListOf<DataConnectPath>()
        val predicate: PrunePredicate = {
          predicateCallPaths.add(it)
          randomSource().random.nextFloat() < trueReturnProbability
        }

        struct.withDescendantStructsPruned(path, predicate)

        val expectedPredicateCallPaths = struct.pathsEligibleForPruning(path).toList()
        predicateCallPaths shouldContainExactlyInAnyOrder expectedPredicateCallPaths
      }
    }

  @Test
  fun `ListValue withDescendantStructsPruned() calls the predicate with all paths eligible for pruning`() =
    runTest {
      checkAll(
        propTestConfig,
        Arb.proto.listValue(),
        dataConnectPathArb(),
        Arb.float(0.0f..1.0f)
      ) { listValueSample, path, trueReturnProbability ->
        val listValue: ListValue = listValueSample.listValue
        val predicateCallPaths = mutableListOf<DataConnectPath>()
        val predicate: PrunePredicate = {
          predicateCallPaths.add(it)
          randomSource().random.nextFloat() < trueReturnProbability
        }

        listValue.withDescendantStructsPruned(path, predicate)

        val expectedPredicateCallPaths = listValue.pathsEligibleForPruning(path).toList()
        predicateCallPaths shouldContainExactlyInAnyOrder expectedPredicateCallPaths
      }
    }

  @Test
  fun `Struct withDescendantStructsPruned() returns prunedStructByPath whose keys are paths for which the predicate returned true`() =
    runTest {
      checkAll(propTestConfig, dataConnectPathArb()) { basePath ->
        val sample = PrunableStructArb(basePath).bind()
        val struct: Struct = sample.struct

        val result = struct.withDescendantStructsPruned(basePath) { it in sample.pathsToPrune }

        val prunedStructByPath = result.shouldNotBeNull().prunedStructByPath
        prunedStructByPath.keys shouldContainExactlyInAnyOrder sample.pathsToPrune
      }
    }

  @Test
  fun `ListValue withDescendantStructsPruned() returns prunedStructByPath whose keys are paths for which the predicate returned true`() =
    runTest {
      checkAll(propTestConfig, dataConnectPathArb()) { basePath ->
        val sample = PrunableListValueArb(basePath).bind()
        val listValue: ListValue = sample.listValue

        val result = listValue.withDescendantStructsPruned(basePath) { it in sample.pathsToPrune }

        val prunedStructByPath = result.shouldNotBeNull().prunedStructByPath
        prunedStructByPath.keys shouldContainExactlyInAnyOrder sample.pathsToPrune
      }
    }

  @Test
  fun `Struct withDescendantStructsPruned() returns prunedStructByPath whose values are the pruned Structs at the paths for which the predicate returned true`() =
    runTest {
      checkAll(propTestConfig, dataConnectPathArb()) { basePath ->
        val sample = PrunableStructArb(basePath).bind()
        val struct: Struct = sample.struct

        val result = struct.withDescendantStructsPruned(basePath) { it in sample.pathsToPrune }

        val prunedStructByPath = result.shouldNotBeNull().prunedStructByPath
        val expectedPrunedStructByPath = sample.expectedPruneStructResult.prunedStructByPath
        prunedStructByPath shouldContainExactly expectedPrunedStructByPath
      }
    }

  @Test
  fun `ListValue withDescendantStructsPruned() returns prunedStructByPath whose values are the pruned Structs at the paths for which the predicate returned true`() =
    runTest {
      checkAll(propTestConfig, dataConnectPathArb()) { basePath ->
        val sample = PrunableListValueArb(basePath).bind()
        val listValue: ListValue = sample.listValue

        val result = listValue.withDescendantStructsPruned(basePath) { it in sample.pathsToPrune }

        val prunedStructByPath = result.shouldNotBeNull().prunedStructByPath
        val expectedPrunedStructByPath = sample.expectedPruneListValueResult.prunedStructByPath
        prunedStructByPath shouldContainExactly expectedPrunedStructByPath
      }
    }

  @Test
  fun `Struct withDescendantStructsPruned() returns prunedStruct that is the pruned receiver`() =
    runTest {
      checkAll(propTestConfig, dataConnectPathArb()) { basePath ->
        val sample = PrunableStructArb(basePath).bind()
        val struct: Struct = sample.struct

        val result = struct.withDescendantStructsPruned(basePath) { it in sample.pathsToPrune }

        val prunedStruct = result.shouldNotBeNull().prunedStruct
        val expectedPrunedStruct = sample.expectedPruneStructResult.prunedStruct
        prunedStruct shouldBe expectedPrunedStruct
      }
    }

  @Test
  fun `ListValue withDescendantStructsPruned() returns prunedStruct that is the pruned receiver`() =
    runTest {
      checkAll(propTestConfig, dataConnectPathArb()) { basePath ->
        val sample = PrunableListValueArb(basePath).bind()
        val listValue: ListValue = sample.listValue

        val result = listValue.withDescendantStructsPruned(basePath) { it in sample.pathsToPrune }

        val prunedListValue = result.shouldNotBeNull().prunedListValue
        val expectedPrunedListValue = sample.expectedPruneListValueResult.prunedListValue
        prunedListValue shouldBe expectedPrunedListValue
      }
    }
}

@OptIn(ExperimentalKotest::class)
private val propTestConfig =
  PropTestConfig(iterations = 200, edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2))

private typealias PrunePredicate = (path: DataConnectPath) -> Boolean

/**
 * Returns whether the receiver path is eligible for pruning where its value is the given [Value].
 *
 * This function assumes that the root element is a [Struct] (namely, not a [ListValue]).
 *
 * A path is eligible for pruning if its value is a [Struct] and it is not a direct element of a
 * [ListValue] and not the root [Struct] itself.
 */
private fun DataConnectPath.isEligibleForPruning(value: Value): Boolean =
  isNotEmpty() && value.isStructValue && last() is DataConnectPathSegment.Field

/**
 * Creates and returns a [Sequence] that generates the paths of the receiver [Struct] that are
 * eligible for pruning, according to [isEligibleForPruning].
 */
private fun Struct.pathsEligibleForPruning(
  basePath: DataConnectPath = emptyDataConnectPath()
): Sequence<DataConnectPath> =
  walk().filter { it.path.isEligibleForPruning(it.value) }.map { basePath + it.path }

/**
 * Creates and returns a [Sequence] that generates the paths of the receiver [ListValue] that are
 * eligible for pruning, according to [isEligibleForPruning].
 */
private fun ListValue.pathsEligibleForPruning(
  basePath: DataConnectPath = emptyDataConnectPath()
): Sequence<DataConnectPath> = sequence {
  valuesList.forEachIndexed { listIndex, listElement ->
    val listElementBasePath = basePath.withAddedListIndex(listIndex)
    if (listElement.isStructValue) {
      yieldAll(listElement.structValue.pathsEligibleForPruning(listElementBasePath))
    } else if (listElement.isListValue) {
      yieldAll(listElement.listValue.pathsEligibleForPruning(listElementBasePath))
    }
  }
}

/**
 * An [Arb] that generates a [Struct] objects that have at least one path that is eligible for
 * pruning, according to [isEligibleForPruning].
 *
 * @param basePath The base [DataConnectPath] to prepend to all generated paths.
 */
private class PrunableStructArb(
  private val basePath: DataConnectPath = emptyDataConnectPath(),
  private val structKeyArb: Arb<String> = Arb.proto.structKey(length = 4),
  private val structArb: Arb<ProtoArb.StructInfo> = Arb.proto.struct(key = structKeyArb),
) : Arb<PrunableStructArb.Sample>() {

  data class Sample(
    val struct: Struct,
    val pathsToPrune: Set<DataConnectPath>,
    val expectedPruneStructResult: ProtoPrune.PruneStructResult,
  )

  override fun sample(rs: RandomSource) =
    generateSample(
        rs,
        structEdgeCaseProbability = rs.random.nextFloat(),
        pruneCountEdgeCaseProbability = rs.random.nextFloat(),
      )
      .asSample()

  override fun edgecase(rs: RandomSource): Sample {
    val edgeCaseCount = rs.random.nextInt(1..EdgeCase.entries.size)
    val edgeCases = EdgeCase.entries.shuffled(rs.random).take(edgeCaseCount).toSet()
    return generateSample(
      rs,
      structEdgeCaseProbability = if (EdgeCase.Struct in edgeCases) 1.0f else 0.0f,
      pruneCountEdgeCaseProbability = if (EdgeCase.PruneCount in edgeCases) 1.0f else 0.0f,
    )
  }

  private enum class EdgeCase {
    Struct,
    PruneCount,
  }

  private fun generateSample(
    rs: RandomSource,
    structEdgeCaseProbability: Float,
    pruneCountEdgeCaseProbability: Float,
  ): Sample {
    val (struct1, struct2) = List(2) { structArb.next(rs, structEdgeCaseProbability).struct }
    val struct = run {
      val generateKey = { structKeyArb.sample(rs).value }
      struct1.withRandomlyInsertedStruct(struct2, rs.random, generateKey)
    }

    val pathsToPrune = run {
      val eligiblePaths = struct.pathsEligibleForPruning().toList()
      check(eligiblePaths.isNotEmpty())
      val pruneCountArb = Arb.int(1..eligiblePaths.size)
      val pruneCount = pruneCountArb.next(rs, pruneCountEdgeCaseProbability)
      eligiblePaths.shuffled(rs.random).take(pruneCount).toSet()
    }

    val prunedStructByPath = mutableMapOf<DataConnectPath, Struct>()
    val prunedStruct =
      struct.map { path, value ->
        if (path !in pathsToPrune) {
          value
        } else {
          prunedStructByPath[path] = value.structValue
          null
        }
      }
    check(prunedStructByPath.keys == pathsToPrune)

    return Sample(
      struct = struct,
      pathsToPrune = pathsToPrune.map { basePath + it }.toSet(),
      expectedPruneStructResult =
        ProtoPrune.PruneStructResult(
          prunedStruct = prunedStruct,
          prunedStructByPath = prunedStructByPath.mapKeys { basePath + it.key },
        )
    )
  }
}

/**
 * An [Arb] that generates a [ListValue] objects that have at least one path that is eligible for
 * pruning, according to [isEligibleForPruning].
 *
 * @param basePath The base [DataConnectPath] to prepend to all generated paths.
 */
private class PrunableListValueArb(val basePath: DataConnectPath = emptyDataConnectPath()) :
  Arb<PrunableListValueArb.Sample>() {

  data class Sample(
    val listValue: ListValue,
    val pathsToPrune: Set<DataConnectPath>,
    val expectedPruneListValueResult: ProtoPrune.PruneListValueResult,
  )

  private val structKeyArb = Arb.proto.structKey(length = 4)
  private val structArb = Arb.proto.struct(key = structKeyArb)
  private val prunableStructArb =
    PrunableStructArb(structKeyArb = structKeyArb, structArb = structArb)
  private val listValueArb = Arb.proto.listValue(structKey = structKeyArb)

  override fun sample(rs: RandomSource) =
    generateSample(
        rs,
        listValueEdgeCaseProbability = rs.random.nextFloat(),
        prunableStructEdgeCaseProbability = rs.random.nextFloat(),
        pruneCountEdgeCaseProbability = rs.random.nextFloat(),
      )
      .asSample()

  override fun edgecase(rs: RandomSource): Sample {
    val edgeCaseCount = rs.random.nextInt(1..EdgeCase.entries.size)
    val edgeCases = EdgeCase.entries.shuffled(rs.random).take(edgeCaseCount).toSet()
    return generateSample(
      rs,
      listValueEdgeCaseProbability = if (EdgeCase.ListValue in edgeCases) 1.0f else 0.0f,
      prunableStructEdgeCaseProbability = if (EdgeCase.PrunableStruct in edgeCases) 1.0f else 0.0f,
      pruneCountEdgeCaseProbability = if (EdgeCase.PruneCount in edgeCases) 1.0f else 0.0f,
    )
  }

  private enum class EdgeCase {
    ListValue,
    PrunableStruct,
    PruneCount,
  }

  private fun generateSample(
    rs: RandomSource,
    listValueEdgeCaseProbability: Float,
    prunableStructEdgeCaseProbability: Float,
    pruneCountEdgeCaseProbability: Float,
  ): Sample {
    val listValue =
      generateListValueWithAtLeastOnePrunableValue(
        rs,
        listValueEdgeCaseProbability = listValueEdgeCaseProbability,
        prunableStructEdgeCaseProbability = prunableStructEdgeCaseProbability,
      )

    val pathsToPrune = run {
      val eligiblePaths = listValue.pathsEligibleForPruning().toList()
      check(eligiblePaths.isNotEmpty())
      val pruneCountArb = Arb.int(1..eligiblePaths.size)
      val pruneCount = pruneCountArb.next(rs, pruneCountEdgeCaseProbability)
      eligiblePaths.shuffled(rs.random).take(pruneCount).toSet()
    }

    val prunedStructByPath = mutableMapOf<DataConnectPath, Struct>()
    val prunedListValue =
      listValue.map { path, value ->
        if (path !in pathsToPrune) {
          value
        } else {
          prunedStructByPath[path] = value.structValue
          null
        }
      }
    check(prunedStructByPath.keys == pathsToPrune)

    return Sample(
      listValue = listValue,
      pathsToPrune = pathsToPrune.map { basePath + it }.toSet(),
      expectedPruneListValueResult =
        ProtoPrune.PruneListValueResult(
          prunedListValue = prunedListValue,
          prunedStructByPath = prunedStructByPath.mapKeys { basePath + it.key },
        )
    )
  }

  private fun generateListValueWithAtLeastOnePrunableValue(
    rs: RandomSource,
    listValueEdgeCaseProbability: Float,
    prunableStructEdgeCaseProbability: Float,
  ): ListValue {
    val listValue = listValueArb.next(rs, listValueEdgeCaseProbability).listValue
    val listIndexPaths =
      listValue
        .walk()
        .filter { it.path.last() is DataConnectPathSegment.ListIndex }
        .map { it.path }
        .toList()
    val prunableValue =
      prunableStructArb.next(rs, prunableStructEdgeCaseProbability).struct.toValueProto()

    return if (listIndexPaths.isEmpty()) {
      listValue.toBuilder().addValues(prunableValue).build()
    } else {
      val replacementPath = listIndexPaths.random(rs.random)
      listValue.map { path, value -> if (path == replacementPath) prunableValue else value }
    }
  }
}

/**
 * Returns a new [Arb] that generates [Struct] instances where no descendant [Struct] objects are
 * eligible for pruning according to [isEligibleForPruning].
 */
private fun nonPrunableStructArb(): Arb<Struct> =
  Arb.proto.struct().map { sample ->
    sample.struct.map { path, value -> if (path.isEligibleForPruning(value)) null else value }
  }

/**
 * Returns a new [Arb] that generates [ListValue] instances where no descendant [Struct] objects are
 * eligible for pruning according to [isEligibleForPruning].
 */
private fun nonPrunableListValueArb(): Arb<ListValue> =
  Arb.proto.listValue().map { sample ->
    sample.listValue.map { path, value -> if (path.isEligibleForPruning(value)) null else value }
  }
