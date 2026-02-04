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
import com.google.firebase.dataconnect.emptyDataConnectPath
import com.google.firebase.dataconnect.testutil.DataConnectPath
import com.google.firebase.dataconnect.testutil.DataConnectPathValuePair
import com.google.firebase.dataconnect.testutil.isListValue
import com.google.firebase.dataconnect.testutil.isStructValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.property.arbitrary.structKey
import com.google.firebase.dataconnect.testutil.registerDataConnectKotestPrinters
import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.firebase.dataconnect.testutil.structOf
import com.google.firebase.dataconnect.testutil.toPrintable
import com.google.firebase.dataconnect.testutil.walk
import com.google.firebase.dataconnect.toEntityPathProto
import com.google.firebase.dataconnect.withAddedField
import com.google.firebase.dataconnect.withAddedListIndex
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import google.firebase.dataconnect.proto.kotlinsdk.Entity as EntityProto
import google.firebase.dataconnect.proto.kotlinsdk.EntityList as EntityListProto
import google.firebase.dataconnect.proto.kotlinsdk.EntityOrEntityList as EntityOrEntityListProto
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.common.DelicateKotest
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldBeUnique
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.distinct
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.orNull
import io.kotest.property.checkAll
import io.mockk.every
import io.mockk.mockk
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

      result.proto.struct shouldBe sample.queryResultProto.struct
    }
  }

  @Test
  fun `dehydrateQueryResult() returns the correct proto entities`() = runTest {
    checkAll(propTestConfig, QueryResultArb(entityCountRange = 1..10)) { sample ->
      val queryResult: Struct = sample.hydratedStruct
      val getEntityIdForPath = sample::getEntityIdForPath

      val result = dehydrateQueryResult(queryResult, getEntityIdForPath)

      result.proto.entitiesList shouldContainExactlyInAnyOrder sample.queryResultProto.entitiesList
    }
  }

  @Test
  fun `dehydrateQueryResult() handles entity lists correctly`() = runTest {
    val structKeyArb = Arb.proto.structKey()
    checkAll(propTestConfig, Arb.list(Arb.proto.struct(key = structKeyArb), 1..5)) { entities ->
      val entityIdArb = Arb.proto.structKey(length = 4).distinct()
      val entityIds = List(entities.size) { entityIdArb.bind() }
      val structKey = structKeyArb.bind()
      val listValue =
        ListValue.newBuilder().addAllValues(entities.map { it.toValueProto() }).build()
      val queryResult = structOf(structKey, listValue)
      val entityIdByPath =
        entityIds
          .mapIndexed { index, entityId ->
            val path = emptyDataConnectPath().withAddedField(structKey).withAddedListIndex(index)
            Pair(path, entityId)
          }
          .toMap()

      val result = dehydrateQueryResult(queryResult, entityIdByPath::get)

      withClue("result.proto.struct") { result.proto.struct shouldBe Struct.getDefaultInstance() }
      withClue("result.proto.struct") {
        val entityListProto =
          EntityListProto.newBuilder()
            .addAllEntities(
              entityIds.zip(entities.map { it.struct.fieldsMap.keys }).map { (entityId, fields) ->
                EntityProto.newBuilder().setEntityId(entityId).addAllFields(fields).build()
              }
            )
            .build()
        val entityOrEntityListProto =
          EntityOrEntityListProto.newBuilder()
            .setPath(emptyDataConnectPath().withAddedField(structKey).toEntityPathProto())
            .setEntityList(entityListProto)
            .build()
        result.proto.entitiesList shouldContainExactly listOf(entityOrEntityListProto)
      }
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

/**
 * Asserts that a [DehydratedQueryResult] is empty, meaning its [DehydratedQueryResult.proto.struct]
 * is the same instance as [expectedStruct], and both [DehydratedQueryResult.proto.entitiesList] and
 * [DehydratedQueryResult.entities] are empty.
 *
 * This function is used to verify scenarios where no entities should be extracted or modifications
 * made to the original struct during the dehydration process.
 *
 * @receiver The [DehydratedQueryResult] instance to check.
 * @param expectedStruct The [Struct] that the [DehydratedQueryResult.proto.struct] is expected to
 * be the same instance as.
 */
private fun DehydratedQueryResult.shouldHaveEmptyEntitiesAndStruct(expectedStruct: Struct) {
  assertSoftly {
    withClue("proto.struct") { proto.struct shouldBeSameInstanceAs expectedStruct }
    withClue("proto.entitiesList") { proto.entitiesList.shouldBeEmpty() }
    withClue("entities") { entities.shouldBeEmpty() }
  }
}

/**
 * Walks the receiver [Struct] and returns all paths that are eligible for entity extraction.
 *
 * A path is "eligible" for entity extraction if its value is a [Struct] and it satisfies one of the
 * following conditions:
 * 1. It is a field of another [Struct].
 * 2. It is an element of a [ListValue] where all elements of the [ListValue] are [Struct] values.
 */
private fun Struct.eligibleEntityStructPaths(): Sequence<DataConnectPath> {
  val entityListPaths = buildSet {
    walk().forEach { (path, value) ->
      if (value.isListValue) {
        val containsOnlyStructs = value.listValue.valuesList.all { it.isStructValue }
        if (containsOnlyStructs) {
          add(path)
        }
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

  return walk(includeSelf = false).filter(::filterEntityPaths).map { it.path }
}
