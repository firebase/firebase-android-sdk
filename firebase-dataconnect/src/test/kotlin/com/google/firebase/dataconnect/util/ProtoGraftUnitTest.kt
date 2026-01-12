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
import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb.dataConnectPath as dataConnectPathArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.property.arbitrary.structKey
import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import com.google.firebase.dataconnect.util.ProtoGraft.withGraftedInStructs
import com.google.firebase.dataconnect.util.ProtoUtil.toValueProto
import com.google.firebase.dataconnect.withAddedListIndex
import com.google.protobuf.Struct
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ProtoGraftUnitTest {

  @Test
  fun `withGraftedInStructs() when structsByPath is empty should return input struct`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct()) { struct ->
      struct.struct.withGraftedInStructs(emptyMap()) shouldBeSameInstanceAs struct.struct
    }
  }

  @Test
  fun `withGraftedInStructs() when structsByPath contains 1 path, the empty path`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct(), Arb.proto.struct()) { struct, structToGraft ->
      val structsByPath = mapOf(emptyDataConnectPath() to structToGraft.struct)

      struct.struct.withGraftedInStructs(structsByPath) shouldBeSameInstanceAs structToGraft.struct
    }
  }

  @Test
  fun `withGraftedInStructs() when structsByPath contains only paths into the root`() = runTest {
    val structArb = Arb.proto.struct()
    checkAll(propTestConfig, structArb, Arb.list(structArb, 1..5)) { struct, structsToGraft ->
      val structKeyArb = Arb.proto.structKey().filterNot { struct.struct.containsFields(it) }
      val structsByPath =
        buildMap<DataConnectPath, Struct> {
          structsToGraft.forEach {
            val path = listOf(DataConnectPathSegment.Field(structKeyArb.bind()))
            put(path, it.struct)
          }
        }

      val result = struct.struct.withGraftedInStructs(structsByPath)

      val expectedResult: Struct =
        struct.struct.toBuilder().let { structBuilder ->
          structsByPath.entries.forEach { (path, structToGraft) ->
            val pathSegment = path.single() as DataConnectPathSegment.Field
            structBuilder.putFields(pathSegment.field, structToGraft.toValueProto())
          }
          structBuilder.build()
        }
      result shouldBe expectedResult
    }
  }

  @Test
  fun `withGraftedInStructs() when structsByPath contains paths ending with an index should throw`() =
    runTest {
      val structArb = Arb.proto.struct()
      checkAll(propTestConfig, structArb, structArb, dataConnectPathArb(), Arb.int()) {
        struct,
        structToGraft,
        graftPathPrefix,
        graftPathLastSegmentIndex ->
        val graftPath = graftPathPrefix.withAddedListIndex(graftPathLastSegmentIndex)
        val structsByPath = mapOf(graftPath to structToGraft.struct)

        val exception =
          shouldThrow<ProtoGraft.LastInsertPathSegmentNotFieldException> {
            struct.struct.withGraftedInStructs(structsByPath)
          }

        assertSoftly {
          exception.message shouldContainWithNonAbuttingText "qxgass8cvx"
          exception.message shouldContainWithNonAbuttingTextIgnoringCase
            "last path segment is list index $graftPathLastSegmentIndex"
          exception.message shouldContainWithNonAbuttingTextIgnoringCase
            "must have a field as the last path segment"
        }
      }
    }

  private companion object {

    @OptIn(ExperimentalKotest::class)
    val propTestConfig =
      PropTestConfig(
        iterations = 200,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2)
      )
  }
}
