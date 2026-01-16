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
import com.google.firebase.dataconnect.testutil.walk
import com.google.firebase.dataconnect.util.ProtoPrune.withDescendantStructsPruned
import com.google.firebase.dataconnect.withAddedListIndex
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll
import io.mockk.confirmVerified
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
      val predicate = predicateThatUnconditionallyReturns(false)

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
        val predicate = predicateThatUnconditionallyReturns(false)

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
}

@OptIn(ExperimentalKotest::class)
private val propTestConfig =
  PropTestConfig(iterations = 200, edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2))

private typealias PrunePredicate = (path: DataConnectPath, struct: Struct) -> Boolean

private fun predicateThatUnconditionallyReturns(returnValue: Boolean): PrunePredicate = { _, _ ->
  returnValue
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
