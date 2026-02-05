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
import com.google.firebase.dataconnect.DataConnectPathComparator
import com.google.firebase.dataconnect.DataConnectPathSegment
import com.google.firebase.dataconnect.emptyDataConnectPath
import com.google.firebase.dataconnect.testutil.isListValue
import com.google.firebase.dataconnect.testutil.isStructValue
import com.google.firebase.dataconnect.testutil.map
import com.google.firebase.dataconnect.testutil.property.arbitrary.ProtoArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.listValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.next
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.property.arbitrary.structKey
import com.google.firebase.dataconnect.testutil.registerDataConnectKotestPrinters
import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.firebase.dataconnect.testutil.walk
import com.google.firebase.dataconnect.testutil.withRandomlyInsertedStruct
import com.google.firebase.dataconnect.toPathString
import com.google.firebase.dataconnect.util.ProtoPrune.PrunedListValue
import com.google.firebase.dataconnect.util.ProtoPrune.PrunedStruct
import com.google.firebase.dataconnect.util.ProtoPrune.PrunedValue
import com.google.firebase.dataconnect.util.ProtoPrune.withPrunedDescendants
import com.google.firebase.dataconnect.util.ProtoUtil.toValueProto
import com.google.firebase.dataconnect.withAddedListIndex
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.print.print
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
import io.mockk.every
import io.mockk.mockk
import kotlin.collections.map
import kotlin.random.nextInt
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ProtoPruneUnitTest {

  @Before
  fun registerPrinters() {
    registerDataConnectKotestPrinters()
  }

  @Test
  fun `Struct withPrunedDescendants() on empty Struct returns null and never calls predicate`() {
    val predicate: WithPrunedDescendantsPredicate = mockk()

    val result = Struct.getDefaultInstance().withPrunedDescendants(predicate)

    assertSoftly {
      result.shouldBeNull()
      confirmVerified(predicate)
    }
  }

  @Test
  fun `ListValue withPrunedDescendants() on empty ListValue returns null and never calls predicate`() {
    val predicate: WithPrunedDescendantsPredicate = mockk()

    val result = ListValue.getDefaultInstance().withPrunedDescendants(predicate)

    assertSoftly {
      result.shouldBeNull()
      confirmVerified(predicate)
    }
  }

  @Test
  fun `Struct withPrunedDescendants() when no prunable values returns null and never calls predicate`() =
    runTest {
      checkAll(propTestConfig, nonPrunableStructArb()) { struct: Struct ->
        val predicate: WithPrunedDescendantsPredicate = mockk()

        val result = struct.withPrunedDescendants(predicate)

        assertSoftly {
          result.shouldBeNull()
          confirmVerified(predicate)
        }
      }
    }

  @Test
  fun `ListValue withPrunedDescendants() when no prunable values returns null and never calls predicate`() =
    runTest {
      checkAll(propTestConfig, nonPrunableListValueArb()) { listValue: ListValue ->
        val predicate: WithPrunedDescendantsPredicate = mockk()

        val result = listValue.withPrunedDescendants(predicate)

        assertSoftly {
          result.shouldBeNull()
          confirmVerified(predicate)
        }
      }
    }

  @Test
  fun `Struct withPrunedDescendants() returns null if nothing is pruned`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct()) { sample ->
      val struct: Struct = sample.struct
      val predicate: WithPrunedDescendantsPredicate = mockk()
      every { predicate(any(), any()) } returns false

      val result = struct.withPrunedDescendants(predicate)

      result.shouldBeNull()
    }
  }

  @Test
  fun `ListValue withPrunedDescendants() returns null if nothing is pruned`() = runTest {
    checkAll(propTestConfig, Arb.proto.listValue()) { sample ->
      val listValue: ListValue = sample.listValue
      val predicate: WithPrunedDescendantsPredicate = mockk()
      every { predicate(any(), any()) } returns false

      val result = listValue.withPrunedDescendants(predicate)

      result.shouldBeNull()
    }
  }

  @Test
  fun `Struct withPrunedDescendants() calls the predicate with all paths eligible for pruning`() =
    runTest {
      checkAll(propTestConfig.copy(seed = 123), Arb.proto.struct(), Arb.float(0.0f..1.0f)) {
        sample,
        pruneProbability ->
        val struct: Struct = sample.struct
        val capturedPaths = mutableListOf<DataConnectPath>()
        val predicate: WithPrunedDescendantsPredicate = mockk()
        every { predicate(capture(capturedPaths), any()) } answers
          {
            randomSource().random.nextFloat() < pruneProbability
          }

        struct.withPrunedDescendants(predicate)

        val expectedPredicateCallPaths = struct.pathsEligibleForPruning().toList()
        capturedPaths shouldContainExactlyInAnyOrder expectedPredicateCallPaths
      }
    }

  @Test
  fun `ListValue withPrunedDescendants() calls the predicate with all paths eligible for pruning`() =
    runTest {
      checkAll(propTestConfig, Arb.proto.listValue(), Arb.float(0.0f..1.0f)) {
        sample,
        pruneProbability ->
        val listValue: ListValue = sample.listValue
        val capturedPaths = mutableListOf<DataConnectPath>()
        val predicate: WithPrunedDescendantsPredicate = mockk()
        every { predicate(capture(capturedPaths), any()) } answers
          {
            randomSource().random.nextFloat() < pruneProbability
          }

        listValue.withPrunedDescendants(predicate)

        val expectedPredicateCallPaths = listValue.pathsEligibleForPruning().toList()
        capturedPaths shouldContainExactlyInAnyOrder expectedPredicateCallPaths
      }
    }

  @Test
  fun `Struct withPrunedDescendants() returns prunedValueByPath whose keys are paths for which the predicate returned true`() =
    runTest {
      checkAll(propTestConfig, PrunableStructArb()) { sample ->
        val struct: Struct = sample.struct
        val predicate = predicateReturningTrueForPaths(sample.pathsToPrune)

        val result = struct.withPrunedDescendants(predicate)

        val prunedValueByPath = result.shouldNotBeNull().prunedValueByPath
        prunedValueByPath.keys shouldContainExactlyInAnyOrder sample.pathsToPrune
      }
    }

  @Test
  fun `ListValue withPrunedDescendants() returns prunedValueByPath whose keys are paths for which the predicate returned true`() =
    runTest {
      checkAll(propTestConfig, PrunableListValueArb()) { sample ->
        val listValue: ListValue = sample.listValue
        val predicate = predicateReturningTrueForPaths(sample.pathsToPrune)

        val result = listValue.withPrunedDescendants(predicate)

        val prunedValueByPath = result.shouldNotBeNull().prunedValueByPath
        prunedValueByPath.keys shouldContainExactlyInAnyOrder sample.pathsToPrune
      }
    }

  @Test
  fun `Struct withPrunedDescendants() returns prunedValueByPath whose values are the pruned values at the paths for which the predicate returned true`() =
    runTest {
      checkAll(propTestConfig, PrunableStructArb()) { sample ->
        val struct: Struct = sample.struct
        val predicate = predicateReturningTrueForPaths(sample.pathsToPrune)

        val result = struct.withPrunedDescendants(predicate)

        val prunedValueByPath = result.shouldNotBeNull().prunedValueByPath
        val expectedPrunedValueByPath = sample.expectedPruneStructResult.prunedValueByPath
        prunedValueByPath shouldContainExactly expectedPrunedValueByPath
      }
    }

  @Test
  fun `ListValue withPrunedDescendants() returns prunedValueByPath whose values are the pruned values at the paths for which the predicate returned true`() =
    runTest {
      checkAll(propTestConfig, PrunableListValueArb()) { sample ->
        val listValue: ListValue = sample.listValue
        val predicate = predicateReturningTrueForPaths(sample.pathsToPrune)

        val result = listValue.withPrunedDescendants(predicate)

        val prunedValueByPath = result.shouldNotBeNull().prunedValueByPath
        val expectedPrunedValueByPath = sample.expectedPruneListValueResult.prunedValueByPath
        prunedValueByPath shouldContainExactly expectedPrunedValueByPath
      }
    }

  @Test
  fun `Struct withPrunedDescendants() returns prunedStruct that is the pruned receiver`() =
    runTest {
      checkAll(propTestConfig, PrunableStructArb()) { sample ->
        val struct: Struct = sample.struct
        val predicate = predicateReturningTrueForPaths(sample.pathsToPrune)

        val result = struct.withPrunedDescendants(predicate)

        val prunedStruct = result.shouldNotBeNull().prunedStruct
        val expectedPrunedStruct = sample.expectedPruneStructResult.prunedStruct
        prunedStruct shouldBe expectedPrunedStruct
      }
    }

  @Test
  fun `ListValue withPrunedDescendants() returns prunedStruct that is the pruned receiver`() =
    runTest {
      checkAll(propTestConfig, PrunableListValueArb()) { sample ->
        val listValue: ListValue = sample.listValue
        val predicate = predicateReturningTrueForPaths(sample.pathsToPrune)

        val result = listValue.withPrunedDescendants(predicate)

        val prunedListValue = result.shouldNotBeNull().prunedListValue
        val expectedPrunedListValue = sample.expectedPruneListValueResult.prunedListValue
        prunedListValue shouldBe expectedPrunedListValue
      }
    }
}

@OptIn(ExperimentalKotest::class)
private val propTestConfig =
  PropTestConfig(iterations = 200, edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2))

/**
 * Returns whether the receiver path is eligible for pruning where its value is the given [Value].
 *
 * This function assumes that the root element is a [Struct] (namely, not a [ListValue]).
 *
 * A path is eligible for pruning if all of the following hold:
 * 1. The last path segment is a field (not a list index).
 * 2. The value is either a [Struct] or a [ListValue]; if it is a [ListValue] then it is either
 * empty or contains all [Struct] values.
 */
private fun DataConnectPath.isEligibleForPruning(value: Value): Boolean {
  if (isEmpty()) {
    return false
  }

  return when (value.kindCase) {
    Value.KindCase.STRUCT_VALUE -> last() is DataConnectPathSegment.Field
    Value.KindCase.LIST_VALUE ->
      last() is DataConnectPathSegment.Field &&
        (value.listValue.valuesCount == 0 || value.listValue.valuesList.all { it.isStructValue })
    else -> false
  }
}

/**
 * Creates and returns a [Sequence] that generates the paths of the receiver [Struct] that are
 * eligible for pruning, according to [isEligibleForPruning].
 */
private fun Struct.pathsEligibleForPruning(): Sequence<DataConnectPath> =
  walk().filter { it.path.isEligibleForPruning(it.value) }.map { it.path }

/**
 * Creates and returns a [Sequence] that generates the paths of the receiver [ListValue] that are
 * eligible for pruning, according to [isEligibleForPruning].
 */
private fun ListValue.pathsEligibleForPruning(): Sequence<DataConnectPath> = sequence {
  valuesList.forEachIndexed { listIndex, listElement ->
    val listElementBasePath = emptyDataConnectPath().withAddedListIndex(listIndex)

    val listElementPathsEligibleForPruning =
      if (listElement.isStructValue) {
        listElement.structValue.pathsEligibleForPruning()
      } else if (listElement.isListValue) {
        listElement.listValue.pathsEligibleForPruning()
      } else {
        emptySequence()
      }

    yieldAll(listElementPathsEligibleForPruning.map { listElementBasePath + it })
  }
}

/**
 * An [Arb] that generates a [Struct] objects that have at least one path that is eligible for
 * pruning, according to [isEligibleForPruning].
 */
private class PrunableStructArb(
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

    val pathsToPrune: Set<DataConnectPath> = run {
      val eligiblePaths = struct.pathsEligibleForPruning().toList()
      check(eligiblePaths.isNotEmpty())
      val pruneCountArb = Arb.int(1..eligiblePaths.size)
      val pruneCount = pruneCountArb.next(rs, pruneCountEdgeCaseProbability)
      eligiblePaths.shuffled(rs.random).take(pruneCount).toSet()
    }

    val prunedValueByPath = mutableMapOf<DataConnectPath, PrunedValue>()
    val prunedStruct =
      struct.map { path, value ->
        if (path !in pathsToPrune) {
          value
        } else {
          prunedValueByPath[path] = value.toPrunedValue()
          null
        }
      }
    check(prunedValueByPath.keys == pathsToPrune)

    return Sample(
      struct = struct,
      pathsToPrune = pathsToPrune,
      expectedPruneStructResult =
        ProtoPrune.PruneStructResult(
          prunedStruct = prunedStruct,
          prunedValueByPath = prunedValueByPath.toMap(),
        )
    )
  }
}

/**
 * An [Arb] that generates a [ListValue] objects that have at least one path that is eligible for
 * pruning, according to [isEligibleForPruning].
 */
private class PrunableListValueArb : Arb<PrunableListValueArb.Sample>() {

  data class Sample(
    val listValue: ListValue,
    val pathsToPrune: Set<DataConnectPath>,
    val expectedPruneListValueResult: ProtoPrune.PruneListValueResult,
  ) {
    override fun toString(): String {
      val pathsToPrunePrintFriendly =
        pathsToPrune.sortedWith(DataConnectPathComparator).map { it.toPathString() }
      return "PrunableListValueArb.Sample(" +
        "listValue=${listValue.print().value}, " +
        "pathsToPrune.size=${pathsToPrune.size}, " +
        "pathsToPrune=${pathsToPrunePrintFriendly.print().value}, " +
        "expectedPruneListValueResult=$expectedPruneListValueResult)"
    }
  }

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

    val pathsToPrune: Set<DataConnectPath> = run {
      val eligiblePaths = listValue.pathsEligibleForPruning().toList()
      check(eligiblePaths.isNotEmpty())
      val pruneCountArb = Arb.int(1..eligiblePaths.size)
      val pruneCount = pruneCountArb.next(rs, pruneCountEdgeCaseProbability)
      eligiblePaths.shuffled(rs.random).take(pruneCount).toSet()
    }

    val prunedValueByPath = mutableMapOf<DataConnectPath, PrunedValue>()
    val prunedListValue =
      listValue.map { path, value ->
        if (path !in pathsToPrune) {
          value
        } else {
          prunedValueByPath[path] = value.toPrunedValue()
          null
        }
      }
    check(prunedValueByPath.keys == pathsToPrune)

    return Sample(
      listValue = listValue,
      pathsToPrune = pathsToPrune,
      expectedPruneListValueResult =
        ProtoPrune.PruneListValueResult(
          prunedListValue = prunedListValue,
          prunedValueByPath = prunedValueByPath.toMap(),
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
 * Converts the receiver [Value] to either a [PrunedStruct] (if the value is a [Struct]) or a
 * [PrunedListValue] (if the value is a [ListValue] with all elements being [Struct] values).
 */
private fun Value.toPrunedValue(): PrunedValue =
  when (kindCase) {
    Value.KindCase.STRUCT_VALUE -> PrunedStruct(structValue)
    Value.KindCase.LIST_VALUE ->
      PrunedListValue(
        listValue.valuesList.mapIndexed { index, value ->
          check(value.isStructValue) {
            "ListValue at index $index must have kind=${Value.KindCase.STRUCT_VALUE}, " +
              "but got ${value.kindCase} [ttdw38b53y]"
          }
          value.structValue
        }
      )
    else -> throw IllegalArgumentException("unsupported Value.KindCase: $kindCase [p7x8bexnc6]")
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

/**
 * Creates and returns a function that can be specified as the `predicate` argument to
 * [ProtoPrune.withPrunedDescendants] that returns `true` if, and only if, it is invoked with a
 * [DataConnectPath] that is contained in the given [paths].
 */
private fun predicateReturningTrueForPaths(
  paths: Collection<DataConnectPath>
): WithPrunedDescendantsPredicate {
  val predicate: WithPrunedDescendantsPredicate = mockk()
  every { predicate(any(), any()) } answers { firstArg<DataConnectPath>() in paths }
  return predicate
}
