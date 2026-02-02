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

@file:OptIn(DelicateKotest::class)

package com.google.firebase.dataconnect.sqlite

import com.google.firebase.dataconnect.DataConnectPathSegment
import com.google.firebase.dataconnect.testutil.DataConnectPath
import com.google.firebase.dataconnect.testutil.DataConnectPathValuePair
import com.google.firebase.dataconnect.testutil.isListValue
import com.google.firebase.dataconnect.testutil.isStructValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.next
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.property.arbitrary.structKey
import com.google.firebase.dataconnect.testutil.randomlyInsertStruct
import com.google.firebase.dataconnect.testutil.registerDataConnectKotestPrinters
import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.firebase.dataconnect.testutil.toPrintFriendlyMap
import com.google.firebase.dataconnect.testutil.toPrintable
import com.google.firebase.dataconnect.testutil.walk
import com.google.protobuf.Struct
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.print.print
import io.kotest.assertions.withClue
import io.kotest.common.DelicateKotest
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldBeUnique
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.RandomSource
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.distinct
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.orNull
import io.kotest.property.asSample
import io.kotest.property.checkAll
import io.mockk.every
import io.mockk.mockk
import kotlin.random.nextInt
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class QueryResultDehydratorUnitTest {

  @Before
  fun registerPrinters() {
    registerDataConnectKotestPrinters()
  }

  @Test
  fun `dehydrateQueryResult() with default getEntityIdForPath returns the receiver`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct()) { structSample ->
      val struct: Struct = structSample.struct

      val result = dehydrateQueryResult(struct)

      result.shouldHaveEmptyEntitiesAndStruct(struct)
    }
  }

  @Test
  fun `dehydrateQueryResult() with null getEntityIdForPath returns the receiver`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct()) { structSample ->
      val struct: Struct = structSample.struct

      val result = dehydrateQueryResult(struct, null)

      result.shouldHaveEmptyEntitiesAndStruct(struct)
    }
  }

  @Test
  fun `dehydrateQueryResult() with getEntityIdForPath returning null returns the receiver`() =
    runTest {
      checkAll(propTestConfig, Arb.proto.struct()) { structSample ->
        val struct: Struct = structSample.struct
        val getEntityIdForPath: GetEntityIdForPathFunction = mockk()
        every { getEntityIdForPath(any()) } returns null

        val result = dehydrateQueryResult(struct, getEntityIdForPath)

        result.shouldHaveEmptyEntitiesAndStruct(struct)
      }
    }

  @Test
  fun `dehydrateQueryResult() calls getEntityIdForPath at most once for each path`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct(), Arb.double(0.0..1.0)) {
      structSample,
      nullProbability ->
      val entityIdArb = Arb.proto.structKey().distinct().orNull(nullProbability)
      val struct: Struct = structSample.struct
      val getEntityIdForPath: GetEntityIdForPathFunction = mockk()
      val capturedPaths = mutableListOf<DataConnectPath>()
      every { getEntityIdForPath(capture(capturedPaths)) } answers { entityIdArb.bind() }

      dehydrateQueryResult(struct, getEntityIdForPath)

      capturedPaths.shouldBeUnique()
    }
  }

  @Test
  fun `dehydrateQueryResult() calls getEntityIdForPath for each eligible entity`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct(), Arb.double(0.0..1.0)) {
      structSample,
      nullProbability ->
      val entityIdArb = Arb.proto.structKey().distinct().orNull(nullProbability)
      val struct: Struct = structSample.struct
      val getEntityIdForPath: GetEntityIdForPathFunction = mockk()
      val capturedPaths = mutableListOf<DataConnectPath>()
      every { getEntityIdForPath(capture(capturedPaths)) } answers { entityIdArb.bind() }

      dehydrateQueryResult(struct, getEntityIdForPath)

      val expectedCapturedPaths =
        struct.eligibleEntityStructPaths().map { it.toPrintable() }.toSet()
      val actualCapturedPaths = capturedPaths.map { it.toPrintable() }.toSet()
      actualCapturedPaths shouldContainExactlyInAnyOrder expectedCapturedPaths
    }
  }

  @Test
  fun `dehydrateQueryResult() returns the correct entities`() = runTest {
    checkAll(propTestConfig, QueryResultArb(entityCountRange = 1..10)) { sample ->
      val queryResult: Struct = sample.hydratedStruct
      val getEntityIdForPath = sample::getEntityIdForPath

      val result = dehydrateQueryResult(queryResult, getEntityIdForPath)

      result.entities shouldContainExactlyInAnyOrder sample.entityByPath.values
    }
  }

  @Test
  fun `dehydrateQueryResult() returns the correct proto struct`() = runTest {
    checkAll(propTestConfig, QueryResultArb(entityCountRange = 1..10)) { sample ->
      val queryResult: Struct = sample.hydratedStruct
      val getEntityIdForPath = sample::getEntityIdForPath

      val result = dehydrateQueryResult(queryResult, getEntityIdForPath)

      result.proto.struct shouldBe sample.dehydratedStruct
    }
  }

  @Test
  fun `dehydrateQueryResult() returns the correct proto entities`() = runTest {
    checkAll(propTestConfig, QueryResultArb(entityCountRange = 1..10)) { sample ->
      val queryResult: Struct = sample.hydratedStruct
      val getEntityIdForPath = sample::getEntityIdForPath

      val result = dehydrateQueryResult(queryResult, getEntityIdForPath)

      TODO("validate result.proto.entitiesList")
    }
  }

  @Test
  fun `dehydrateQueryResult() handles entity lists correctly`() = runTest {
    checkAll(propTestConfig, QueryResultArb(entityCountRange = 1..10)) { sample ->
      val queryResult: Struct = sample.hydratedStruct
      val getEntityIdForPath = sample::getEntityIdForPath

      val result = dehydrateQueryResult(queryResult, getEntityIdForPath)

      TODO("validate result.proto.entitiesList prunes lists of entities")
    }
  }
}

@OptIn(ExperimentalKotest::class)
private val propTestConfig =
  PropTestConfig(
    iterations = 200,
    edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.33),
    shrinkingMode = ShrinkingMode.Off,
  )

private fun DehydratedQueryResult.shouldHaveEmptyEntitiesAndStruct(expectedStruct: Struct) {
  assertSoftly {
    withClue("proto.struct") { proto.struct shouldBeSameInstanceAs expectedStruct }
    withClue("proto.entitiesList") { proto.entitiesList.shouldBeEmpty() }
    withClue("entities") { entities.shouldBeEmpty() }
  }
}

private fun Struct.eligibleEntityStructPaths(): Sequence<DataConnectPath> {
  val entityListPaths = mutableSetOf<DataConnectPath>()

  fun updateEntityListPaths(pair: DataConnectPathValuePair) {
    val (path, value) = pair
    if (value.isListValue) {
      val containsOnlyStructs = value.listValue.valuesList.all { it.isStructValue }
      if (containsOnlyStructs) {
        entityListPaths.add(path)
      }
    }
  }

  fun filterEntityPaths(pair: DataConnectPathValuePair): Boolean {
    val (path, value) = pair
    return when (path.last()) {
      is DataConnectPathSegment.Field -> value.isStructValue
      is DataConnectPathSegment.ListIndex -> path.dropLast(1) in entityListPaths
    }
  }

  return walk(includeSelf = false).onEach(::updateEntityListPaths).filter(::filterEntityPaths).map {
    it.path
  }
}

private class QueryResultArb(entityCountRange: IntRange) : Arb<QueryResultArb.Sample>() {

  data class Sample(
    val hydratedStruct: Struct,
    val dehydratedStruct: Struct,
    val entityByPath: Map<DataConnectPath, DehydratedQueryResult.Entity>,
  ) {
    fun getEntityIdForPath(path: DataConnectPath): String? = entityByPath[path]?.entityId

    override fun toString(): String =
      "QueryResultArb.Sample(" +
        "hydratedStruct=${hydratedStruct.print().value}, " +
        "dehydratedStruct=${dehydratedStruct.print().value}, " +
        "entityByPath.size=${entityByPath.size}, " +
        "entityByPath=${entityByPath.toPrintFriendlyMap().print().value})"
  }

  override fun sample(rs: RandomSource) =
    generate(
        rs,
        entityCountEdgeCaseProbability = rs.random.nextFloat(),
        dehydratedStructEdgeCaseProbability = rs.random.nextFloat(),
        entityStructEdgeCaseProbability = rs.random.nextFloat(),
      )
      .asSample()

  override fun edgecase(rs: RandomSource): Sample {
    val edgeCases =
      EdgeCase.entries.let {
        val count = rs.random.nextInt(1..it.size)
        it.shuffled(rs.random).take(count).toSet()
      }

    return generate(
      rs,
      entityCountEdgeCaseProbability = if (EdgeCase.EntityCount in edgeCases) 1.0f else 0.0f,
      dehydratedStructEdgeCaseProbability =
        if (EdgeCase.DehydratedStruct in edgeCases) 1.0f else 0.0f,
      entityStructEdgeCaseProbability = if (EdgeCase.EntityStruct in edgeCases) 1.0f else 0.0f,
    )
  }

  private enum class EdgeCase {
    EntityCount,
    DehydratedStruct,
    EntityStruct,
  }

  private val entityCountArb =
    Arb.int(
      entityCountRange.also {
        require(!it.isEmpty()) { "entityCountRange is empty: $it" }
        require(it.first >= 0) {
          "entityCountRange.first is ${it.first}, but is must be greater than or equal to zero " +
            "(entityCountRange=$it)"
        }
      }
    )

  private val structKeyArb = Arb.proto.structKey()
  private val structArb = Arb.proto.struct(key = structKeyArb)

  private fun generate(
    rs: RandomSource,
    entityCountEdgeCaseProbability: Float,
    dehydratedStructEdgeCaseProbability: Float,
    entityStructEdgeCaseProbability: Float,
  ): Sample {
    val entityCount = entityCountArb.next(rs, entityCountEdgeCaseProbability)
    check(entityCount >= 0)

    val dehydratedStruct = structArb.next(rs, dehydratedStructEdgeCaseProbability).struct

    val entityByPath: Map<DataConnectPath, DehydratedQueryResult.Entity>
    val hydratedStruct =
      dehydratedStruct.toBuilder().let { hydratedStructBuilder ->
        val entityIdArb = structKeyArb.distinct()
        val entityByPathBuilder = mutableMapOf<DataConnectPath, DehydratedQueryResult.Entity>()

        repeat(entityCount) {
          val entityStruct = structArb.next(rs, entityStructEdgeCaseProbability).struct
          val entityId = entityIdArb.sample(rs).value
          val entityPath =
            hydratedStructBuilder.randomlyInsertStruct(
              entityStruct,
              rs.random,
              generateKey = { structKeyArb.sample(rs).value }
            )
          entityByPathBuilder[entityPath] = DehydratedQueryResult.Entity(entityId, entityStruct)
        }

        entityByPath = entityByPathBuilder.toMap()
        hydratedStructBuilder.build()
      }

    return Sample(
      hydratedStruct = hydratedStruct,
      dehydratedStruct = dehydratedStruct,
      entityByPath = entityByPath,
    )
  }
}
