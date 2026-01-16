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
import com.google.firebase.dataconnect.testutil.isStructValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb.dataConnectPath as dataConnectPathArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.listValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.firebase.dataconnect.testutil.walk
import com.google.firebase.dataconnect.toPathString
import com.google.firebase.dataconnect.util.ProtoPrune.withDescendantStructsPruned
import com.google.firebase.dataconnect.withAddedListIndex
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.Exhaustive
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.pair
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.boolean
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ProtoPruneUnitTest {

  @Test
  fun `Struct withDescendantStructsPruned() should return null if nothing is pruned`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct(), dataConnectPathArb()) { structSample, path ->
      val struct: Struct = structSample.struct
      val predicate = mockPredicateThatUnconditionallyReturns(false)

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
        val predicate = mockPredicateThatUnconditionallyReturns(false)

        val result = listValue.withDescendantStructsPruned(path, predicate)

        result.shouldBeNull()
      }
    }

  @Test
  fun `Struct withDescendantStructsPruned() should call the predicate with all candidate pruning paths`() =
    runTest {
      checkAll(propTestConfig, Arb.proto.struct(), dataConnectPathArb()) { structSample, path ->
        val struct: Struct = structSample.struct
        val predicate = mockPredicateThatUnconditionallyReturns(false)

        struct.withDescendantStructsPruned(path, predicate)

        val expectedPaths = struct.calculateExpectedPruneInvocations(path).map { it.path }
        val actualPaths = mutableListOf<DataConnectPath>()
        verify(atLeast = 0) { predicate.invoke(capture(actualPaths), any()) }
        actualPaths shouldContainExactlyInAnyOrder expectedPaths
      }
    }

  @Test
  fun `ListValue withDescendantStructsPruned() should call the predicate with all candidate pruning paths`() =
    runTest {
      checkAll(propTestConfig, Arb.proto.listValue(), dataConnectPathArb()) { listValueSample, path
        ->
        val listValue: ListValue = listValueSample.listValue
        val predicate = mockPredicateThatUnconditionallyReturns(false)

        listValue.withDescendantStructsPruned(path, predicate)

        val expectedPaths = listValue.calculateExpectedPruneInvocations(path).map { it.path }
        val actualPaths = mutableListOf<DataConnectPath>()
        verify(atLeast = 0) { predicate.invoke(capture(actualPaths), any()) }
        actualPaths shouldContainExactlyInAnyOrder expectedPaths
      }
    }

  @Test
  fun `Struct withDescendantStructsPruned() should call the predicate with corresponding path and struct`() =
    runTest {
      checkAll(propTestConfig, Arb.proto.struct(), dataConnectPathArb()) { structSample, path ->
        val struct: Struct = structSample.struct
        val predicate = mockPredicateThatUnconditionallyReturns(false)

        struct.withDescendantStructsPruned(path, predicate)

        val expectedInvocations =
          struct.calculateExpectedPruneInvocations(path).associate { it.path to it.struct }
        val actualPaths = mutableListOf<DataConnectPath>()
        val actualStructs = mutableListOf<Struct>()
        verify(atLeast = 0) { predicate.invoke(capture(actualPaths), capture(actualStructs)) }
        assertSoftly {
          actualPaths.zip(actualStructs).forEach { (path, actualStruct) ->
            withClue("path=${path.toPathString()}") {
              actualStruct shouldBe expectedInvocations[path]
            }
          }
        }
      }
    }

  @Test
  fun `ListValue withDescendantStructsPruned() should call the predicate with corresponding path and struct`() =
    runTest {
      checkAll(propTestConfig, Arb.proto.listValue(), dataConnectPathArb()) { listValueSample, path
        ->
        val listValue: ListValue = listValueSample.listValue
        val predicate = mockPredicateThatUnconditionallyReturns(false)

        listValue.withDescendantStructsPruned(path, predicate)

        val expectedInvocations =
          listValue.calculateExpectedPruneInvocations(path).associate { it.path to it.struct }
        val actualPaths = mutableListOf<DataConnectPath>()
        val actualStructs = mutableListOf<Struct>()
        verify(atLeast = 0) { predicate.invoke(capture(actualPaths), capture(actualStructs)) }
        assertSoftly {
          actualPaths.zip(actualStructs).forEach { (path, actualStruct) ->
            withClue("path=${path.toPathString()}") {
              actualStruct shouldBe expectedInvocations[path]
            }
          }
        }
      }
    }
}

@OptIn(ExperimentalKotest::class)
private val propTestConfig =
  PropTestConfig(iterations = 200, edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2))

private typealias PrunePredicate = (path: DataConnectPath, struct: Struct) -> Boolean

private fun mockPredicateThatUnconditionallyReturns(returnValue: Boolean): PrunePredicate {
  val predicate: PrunePredicate = mockk(relaxed = true)
  every { predicate(any(), any()) } returns returnValue
  return predicate
}

private data class DataConnectPathStructPair(val path: DataConnectPath, val struct: Struct)

private fun Struct.calculateExpectedPruneInvocations(
  basePath: DataConnectPath
): List<DataConnectPathStructPair> =
  walk()
    .filter {
      it.path.isNotEmpty() &&
        it.value.isStructValue &&
        it.path.last() is DataConnectPathSegment.Field &&
        it.path.dropLast(1).lastOrNull().let { penultimateSegment ->
          penultimateSegment === null || penultimateSegment is DataConnectPathSegment.Field
        }
    }
    .map { DataConnectPathStructPair(basePath + it.path, it.value.structValue) }
    .toList()

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

/** Unit tests for private helper functions defined in this file. */
class ProtoPruneTestingUnitTest {

  @Test
  fun `mockPredicateThatUnconditionallyReturns() returns a function that always returns the given value`() =
    runTest {
      val pathStructPairArb = Arb.pair(dataConnectPathArb(), Arb.proto.struct())
      checkAll(propTestConfig, Exhaustive.boolean()) { returnValue ->
        val predicate = mockPredicateThatUnconditionallyReturns(returnValue)

        repeat(20) { invocationIndex ->
          val (path, struct) = pathStructPairArb.bind()
          withClue("invocationIndex=$invocationIndex") {
            predicate(path, struct.struct) shouldBe returnValue
          }
        }
      }
    }
}
