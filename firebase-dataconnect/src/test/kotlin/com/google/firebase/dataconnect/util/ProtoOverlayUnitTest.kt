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
import com.google.firebase.dataconnect.testutil.property.arbitrary.next
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.random
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
import com.google.firebase.dataconnect.util.ProtoGraft.withGraftedInStructs
import com.google.firebase.dataconnect.util.ProtoOverlay.overlay
import com.google.firebase.dataconnect.util.ProtoPrune.withPrunedDescendants
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
import io.kotest.property.RandomSource
import io.kotest.property.Sample
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.distinct
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.negativeInt
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.shuffle
import io.kotest.property.asSample
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.enum
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ProtoOverlayUnitTest {

  @Before
  fun registerPrinters() {
    registerDataConnectKotestPrinters()
  }

  @Test
  fun `overlay() returns default Struct when given empty iterable`() {
    overlay(emptyList()) shouldBe Struct.getDefaultInstance()
  }

  @Test
  fun `overlay() returns the non-empty Struct when all other Structs are empty`() = runTest {
    checkAll(propTestConfig, nonEmptyStructArb(), Arb.int(1..5)) { struct, emptyStructCount ->
      val structList = buildList {
        add(struct)
        repeat(emptyStructCount) { add(Struct.getDefaultInstance()) }
      }
      val shuffledStructList = Arb.shuffle(structList).bind()

      overlay(shuffledStructList) shouldBeSameInstanceAs struct
    }
  }

  @Test
  fun `overlay() returns the fullest Struct when all other Structs are subsets`() = runTest {
    checkAll(propTestConfig.copy(seed=123), nonEmptyStructArb(), Arb.int(1..5)) { struct, subsetStructCount ->
      val prunedStructArb = PrunedStructArb(struct)
      val structList = buildList {
        add(struct)
        repeat(subsetStructCount) { add(prunedStructArb.bind()) }
      }
      val shuffledStructList = Arb.shuffle(structList).bind()

      overlay(shuffledStructList) shouldBe struct
    }
  }

}

@OptIn(ExperimentalKotest::class)
private val propTestConfig =
  PropTestConfig(iterations = 200, edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2))

private class PrunedStructArb(private val struct: Struct) : Arb<Struct>() {

  init {
    require(struct.fieldsCount > 0) {
      "struct.fieldsCount=${struct.fieldsCount}, but fieldsCount must be strictly greater than zero"
    }
  }

  override fun sample(rs: RandomSource) = generate(
    rs,
    prunedStructEdgeCaseProbability = rs.random.nextFloat(),
  ).asSample()

  override fun edgecase(rs: RandomSource) = generate(rs, prunedStructEdgeCaseProbability = 1.0f)

  private fun generate(
    rs: RandomSource,
    prunedStructEdgeCaseProbability: Float,
  ): Struct {
    val prunedStructs = mutableListOf<Struct>()

    var curStruct = struct
    while (curStruct.fieldsCount > 0) {
      val prunePath = curStruct.walkPaths().filter { it.lastOrNull() is DataConnectPathSegment.Field }.toList().random(rs.random)
      var pathPruned = false
      val curStructPruned = curStruct.map { path, value ->
        if (path == prunePath) {
          pathPruned = true
          null
        } else {
          value
        }
      }
      check(pathPruned)
      prunedStructs.add(curStructPruned)
      curStruct = curStructPruned
    }

    val prunedStructIndex = Arb.int(prunedStructs.indices).next(rs, prunedStructEdgeCaseProbability)
    return prunedStructs[prunedStructIndex]
  }

}

private fun nonEmptyStructArb(): Arb<Struct> = Arb.proto.struct().filterNot { it.struct.fieldsCount == 0 }.map{it.struct}