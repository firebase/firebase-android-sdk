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
import com.google.firebase.dataconnect.testutil.isStructValue
import com.google.firebase.dataconnect.testutil.map
import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb.dataConnectPath as dataConnectPathArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.property.arbitrary.structKey
import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import com.google.firebase.dataconnect.testutil.walk
import com.google.firebase.dataconnect.testutil.walkPaths
import com.google.firebase.dataconnect.toPathString
import com.google.firebase.dataconnect.util.ProtoGraft.withGraftedInStructs
import com.google.firebase.dataconnect.util.ProtoUtil.toValueProto
import com.google.firebase.dataconnect.withAddedField
import com.google.firebase.dataconnect.withAddedListIndex
import com.google.protobuf.Struct
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.DelicateKotest
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.distinct
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ProtoGraftUnitTest {

  @Test
  fun `withGraftedInStructs() with structsByPath empty`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct()) { struct ->
      struct.struct.withGraftedInStructs(emptyMap()) shouldBeSameInstanceAs struct.struct
    }
  }

  @Test
  fun `withGraftedInStructs() with structsByPath containing only the empty path`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct(), Arb.proto.struct()) { struct, structToGraft ->
      val structsByPath = mapOf(emptyDataConnectPath() to structToGraft.struct)

      struct.struct.withGraftedInStructs(structsByPath) shouldBeSameInstanceAs structToGraft.struct
    }
  }

  @Test
  fun `withGraftedInStructs() with structsByPath containing paths with size 1`() = runTest {
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
  fun `withGraftedInStructs() with structsByPath containing paths ending with list index should throw`() =
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
  fun `withGraftedInStructs() with structsByPath containing paths ending with existing key should throw`() =
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
  fun `withGraftedInStructs() when structsByPath contains parent paths of existing structs`() =
    runTest {
      val structKeyArb = Arb.proto.structKey()
      val structArb = Arb.proto.struct()
      checkAll(propTestConfig, structArb, Arb.list(structArb, 1..5)) { struct, structsToGraft ->
        val structNestedStructsByPath: Map<DataConnectPath, Struct> =
          struct.struct
            .walk(includeSelf = true)
            .filter { (_, value) -> value.isStructValue }
            .associate { (path, value) -> Pair(path, value.structValue) }
        val destStructPathArb = Arb.of(structNestedStructsByPath.keys)
        val destKeyArbByPath = mutableMapOf<DataConnectPath, Arb<String>>()
        val structsByPath =
          structsToGraft
            .map { it.struct }
            .associateBy {
              val destStructPath = destStructPathArb.bind()
              val destKeyArb =
                destKeyArbByPath.getOrPut(destStructPath) {
                  val subStruct = structNestedStructsByPath[destStructPath]!!
                  @OptIn(DelicateKotest::class)
                  structKeyArb.filterNot { subStruct.containsFields(it) }.distinct()
                }
              val destKey = destKeyArb.bind()
              destStructPath.withAddedField(destKey)
            }

        val result = struct.struct.withGraftedInStructs(structsByPath)

        val expectedResult =
          struct.struct.toExpectedStructWithStructsGraftedInToExistingStruct(structsByPath)
        result shouldBe expectedResult
      }
    }
}

@OptIn(ExperimentalKotest::class)
private val propTestConfig =
  PropTestConfig(iterations = 200, edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2))

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
            check(!structBuilder.containsFields(field))
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
