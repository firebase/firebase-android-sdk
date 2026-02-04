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

import com.google.firebase.dataconnect.testutil.DataConnectPath
import com.google.firebase.dataconnect.testutil.property.arbitrary.ProtoArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.next
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.property.arbitrary.structKey
import com.google.firebase.dataconnect.testutil.randomlyInsertStruct
import com.google.firebase.dataconnect.testutil.randomlyInsertValue
import com.google.firebase.dataconnect.testutil.registerDataConnectKotestPrinters
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import com.google.firebase.dataconnect.testutil.toPrintFriendlyMap
import com.google.firebase.dataconnect.testutil.walk
import com.google.firebase.dataconnect.toEntityPathProto
import com.google.firebase.dataconnect.toPathString
import com.google.firebase.dataconnect.util.ProtoUtil.toValueProto
import com.google.firebase.dataconnect.withAddedListIndex
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import google.firebase.dataconnect.proto.kotlinsdk.Entity as EntityProto
import google.firebase.dataconnect.proto.kotlinsdk.EntityList as EntityListProto
import google.firebase.dataconnect.proto.kotlinsdk.EntityOrEntityList as EntityOrEntityListProto
import google.firebase.dataconnect.proto.kotlinsdk.QueryResult as QueryResultProto
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.print.print
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.common.DelicateKotest
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldBeUnique
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.RandomSource
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.distinct
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.intRange
import io.kotest.property.arbitrary.negativeInt
import io.kotest.property.asSample
import io.kotest.property.checkAll
import kotlin.random.nextInt
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

internal class QueryResultArb(
  entityCountRange: IntRange,
  private val structKeyArb: Arb<String> = Arb.proto.structKey(length = 4),
  private val structArb: Arb<ProtoArb.StructInfo> = Arb.proto.struct(key = structKeyArb),
) : Arb<QueryResultArb.Sample>() {

  data class Sample(
    val hydratedStruct: Struct,
    val entityByPath: Map<DataConnectPath, DehydratedQueryResult.Entity>,
    val entityListPaths: Set<DataConnectPath>,
    val queryResultProto: QueryResultProto,
    val edgeCases: Set<EdgeCase>,
  ) {
    fun getEntityIdForPath(path: DataConnectPath): String? = entityByPath[path]?.entityId

    enum class EdgeCase {
      EntityCount,
      DehydratedStruct,
      EntityStruct,
      EntityList,
      EntityListSize,
    }

    override fun toString(): String =
      "QueryResultArb.Sample(" +
        "hydratedStruct=${hydratedStruct.print().value}, " +
        "entityByPath.size=${entityByPath.size}, " +
        "entityByPath=${entityByPath.toPrintFriendlyMap().print().value}, " +
        "entityListPaths.size=${entityListPaths.size}, " +
        "entityListPaths=${entityListPaths.map{it.toPathString()}.print().value}, " +
        "queryResultProto=${queryResultProto.print().value}, " +
        "edgeCases.size=${edgeCases.size}, " +
        "edgeCases=${edgeCases.map { it.name }.sorted().print().value})"
  }

  override fun sample(rs: RandomSource) = generate(rs, edgeCases = emptySet()).asSample()

  override fun edgecase(rs: RandomSource): Sample {
    val edgeCases =
      Sample.EdgeCase.entries.let { allEdgeCases ->
        val edgeCaseCount = rs.random.nextInt(1..allEdgeCases.size)
        allEdgeCases.shuffled(rs.random).take(edgeCaseCount).toSet()
      }
    return generate(rs, edgeCases = edgeCases)
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

  private fun generate(rs: RandomSource, edgeCases: Set<Sample.EdgeCase>): Sample {
    fun Set<Sample.EdgeCase>.probability(edgeCase: Sample.EdgeCase): Float =
      if (edgeCase in this) 1.0f else rs.random.nextFloat()

    val entityCountEdgeCaseProbability = edgeCases.probability(Sample.EdgeCase.EntityCount)
    val dehydratedStructEdgeCaseProbability =
      edgeCases.probability(Sample.EdgeCase.DehydratedStruct)
    val entityStructEdgeCaseProbability = edgeCases.probability(Sample.EdgeCase.EntityStruct)
    val entityListProbability = edgeCases.probability(Sample.EdgeCase.EntityList)
    val entityListSizeEdgeCaseProbability = edgeCases.probability(Sample.EdgeCase.EntityListSize)

    val dehydratedStruct = structArb.next(rs, dehydratedStructEdgeCaseProbability).struct

    val entities = run {
      val entityCount = entityCountArb.next(rs, entityCountEdgeCaseProbability)
      check(entityCount >= 0)
      val entityIdArb = structKeyArb.distinct()
      List(entityCount) {
        val entityStruct = structArb.next(rs, entityStructEdgeCaseProbability).struct
        val entityId = entityIdArb.sample(rs).value
        DehydratedQueryResult.Entity(entityId, entityStruct)
      }
    }

    val entityByPath: Map<DataConnectPath, DehydratedQueryResult.Entity>
    val entityListPaths: Set<DataConnectPath>
    val queryResultProto: QueryResultProto
    val hydratedStruct: Struct =
      dehydratedStruct.toBuilder().let { hydratedStructBuilder ->
        val entityByPathBuilder = mutableMapOf<DataConnectPath, DehydratedQueryResult.Entity>()
        val entityListPathsBuilder = mutableSetOf<DataConnectPath>()
        val queryResultProtoBuilder = QueryResultProto.newBuilder()
        queryResultProtoBuilder.setStruct(dehydratedStruct)

        var entityIndex = 0
        while (entityIndex < entities.size) {
          val isEntityList = rs.random.nextFloat() < entityListProbability
          val entityOrEntityListProto: EntityOrEntityListProto =
            if (!isEntityList) {
              val entity = entities[entityIndex++]
              val entityPath =
                hydratedStructBuilder.randomlyInsertStruct(
                  entity.struct,
                  rs.random,
                  generateKey = { structKeyArb.sample(rs).value }
                )

              entityByPathBuilder[entityPath] = entity

              val entityProto =
                EntityProto.newBuilder().let { entityProtoBuilder ->
                  entityProtoBuilder.setEntityId(entity.entityId)
                  entityProtoBuilder.addAllFields(entity.struct.fieldsMap.keys)
                  entityProtoBuilder.build()
                }
              EntityOrEntityListProto.newBuilder()
                .setEntity(entityProto)
                .setPath(entityPath.toEntityPathProto())
                .build()
            } else {
              val entitiesRemaining = entities.size - entityIndex
              val entityListSize =
                Arb.int(1..entitiesRemaining).next(rs, entityListSizeEdgeCaseProbability)
              val entitiesInEntityList = entities.subList(entityIndex, entityIndex + entityListSize)
              entityIndex += entityListSize

              val listValue =
                ListValue.newBuilder()
                  .addAllValues(entitiesInEntityList.map { it.struct.toValueProto() })
                  .build()
              val entityListPath =
                hydratedStructBuilder.randomlyInsertValue(
                  listValue.toValueProto(),
                  rs.random,
                  generateKey = { structKeyArb.sample(rs).value }
                )
              entityListPathsBuilder.add(entityListPath)
              entitiesInEntityList.mapIndexed { index, entity ->
                entityByPathBuilder[entityListPath.withAddedListIndex(index)] = entity
              }

              val entityListProto =
                EntityListProto.newBuilder()
                  .addAllEntities(
                    entitiesInEntityList.map {
                      EntityProto.newBuilder()
                        .setEntityId(it.entityId)
                        .addAllFields(it.struct.fieldsMap.keys)
                        .build()
                    }
                  )
                  .build()

              EntityOrEntityListProto.newBuilder()
                .setEntityList(entityListProto)
                .setPath(entityListPath.toEntityPathProto())
                .build()
            }

          queryResultProtoBuilder.addEntities(entityOrEntityListProto)
        }

        entityByPath = entityByPathBuilder.toMap()
        entityListPaths = entityListPathsBuilder.toSet()
        queryResultProto = queryResultProtoBuilder.build()
        hydratedStructBuilder.build()
      }

    return Sample(
      hydratedStruct = hydratedStruct,
      entityByPath = entityByPath,
      entityListPaths = entityListPaths,
      queryResultProto = queryResultProto,
      edgeCases = edgeCases,
    )
  }
}

class QueryResultArbUnitTest {

  @Before
  fun registerPrinters() {
    registerDataConnectKotestPrinters()
  }

  @Test
  fun `QueryResultArb should respect the given entityCountRange`() = runTest {
    checkAll(propTestConfig, Arb.intRange(0..5).filterNot { it.isEmpty() }) { entityCountRange ->
      val arb = QueryResultArb(entityCountRange = entityCountRange)

      val sample = arb.bind()

      sample.entityByPath.size shouldBeInRange entityCountRange
    }
  }

  @Test
  fun `QueryResultArb should throw on empty entityCountRange`() = runTest {
    checkAll(propTestConfig, Arb.int()) { rangeFirst ->
      val emptyRange = rangeFirst until rangeFirst

      val exception =
        shouldThrow<IllegalArgumentException> { QueryResultArb(entityCountRange = emptyRange) }

      assertSoftly {
        exception.message shouldContainWithNonAbuttingText emptyRange.toString()
        exception.message shouldContainWithNonAbuttingTextIgnoringCase "empty"
      }
    }
  }

  @Test
  fun `QueryResultArb should throw on negative entityCountRange first`() = runTest {
    val intRangeWithNegativeFirstArb =
      Arb.bind(Arb.negativeInt(), Arb.int()) { negativeInt, int ->
        if (negativeInt < int) negativeInt..int else int..negativeInt
      }
    checkAll(propTestConfig, intRangeWithNegativeFirstArb) { entityCountRangeWithNegativeFirst ->
      val exception =
        shouldThrow<IllegalArgumentException> {
          QueryResultArb(entityCountRange = entityCountRangeWithNegativeFirst)
        }

      assertSoftly {
        exception.message shouldContainWithNonAbuttingText
          entityCountRangeWithNegativeFirst.toString()
        exception.message shouldContainWithNonAbuttingText
          entityCountRangeWithNegativeFirst.first.toString()
        exception.message shouldContainWithNonAbuttingText "first"
      }
    }
  }

  @Test
  fun `QueryResultArb sample() should generate samples with empty edge case set`() = runTest {
    checkAll(propTestConfig, Arb.intRange(0..5).filterNot { it.isEmpty() }) { entityCountRange ->
      val arb = QueryResultArb(entityCountRange = entityCountRange)

      val sample = arb.sample(randomSource()).value

      sample.edgeCases.shouldBeEmpty()
    }
  }

  @Test
  fun `QueryResultArb edgecase() should generate samples with non-empty edge case set`() = runTest {
    checkAll(propTestConfig, Arb.intRange(0..5).filterNot { it.isEmpty() }) { entityCountRange ->
      val arb = QueryResultArb(entityCountRange = entityCountRange)

      val sample = arb.edgecase(randomSource())

      sample.edgeCases.shouldNotBeEmpty()
    }
  }

  @Test
  fun `QueryResultArb should generate unique entity IDs`() = runTest {
    checkAll(propTestConfig, Arb.intRange(0..5).filterNot { it.isEmpty() }) { entityCountRange ->
      val arb = QueryResultArb(entityCountRange = entityCountRange)

      val sample = arb.bind()

      sample.entityByPath.values.map { it.entityId }.shouldBeUnique()
    }
  }

  @Test
  fun `QueryResultArb should generate entity lists sometimes`() = runTest {
    var entityListCount = 0

    checkAll(propTestConfig, Arb.intRange(0..5).filterNot { it.isEmpty() }) { entityCountRange ->
      val arb = QueryResultArb(entityCountRange = entityCountRange)

      val sample = arb.bind()

      entityListCount += sample.entityListPaths.size
    }

    entityListCount shouldBeGreaterThan 0
  }

  @Test
  fun `QueryResultArb should generate nested entities sometimes`() = runTest {
    var nestedEntityCount = 0

    checkAll(propTestConfig, Arb.intRange(0..5).filterNot { it.isEmpty() }) { entityCountRange ->
      val arb = QueryResultArb(entityCountRange = entityCountRange)

      val sample = arb.bind()

      sample.entityByPath.keys.forEach { path ->
        val ancestorEntityCount = sample.entityByPath.keys.filterAncestorOf(path).count()
        nestedEntityCount += ancestorEntityCount
      }
    }

    nestedEntityCount shouldBeGreaterThan 0
  }

  @Test
  fun `QueryResultArb should generate correct entityListPaths`() = runTest {
    checkAll(propTestConfig, Arb.intRange(0..5).filterNot { it.isEmpty() }) { entityCountRange ->
      val arb = QueryResultArb(entityCountRange = entityCountRange)

      val sample = arb.bind()

      assertSoftly {
        sample.entityListPaths.forEach { entityListPath ->
          withClue("entityListPath=${entityListPath.toPathString()}") {
            sample.shouldBeEntityList(entityListPath)
          }
        }
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

private fun DataConnectPath.ancestors(): Sequence<DataConnectPath> = sequence {
  var path = this@ancestors
  while (path.isNotEmpty()) {
    path = path.dropLast(1)
    yield(path)
  }
}

private fun Iterable<DataConnectPath>.filterAncestorOf(
  path: DataConnectPath
): List<DataConnectPath> {
  val ancestors = path.ancestors().toSet()
  return filter { it in ancestors }
}

private fun QueryResultArb.Sample.shouldBeEntityList(path: DataConnectPath) {
  val entityListValue = hydratedStruct.walk().filter { it.path == path }.single().value
  entityListValue.kindCase shouldBe Value.KindCase.LIST_VALUE

  val entityList = entityListValue.listValue
  repeat(entityList.valuesCount) { entityListIndex ->
    withClue("entityListIndex=$entityListIndex") {
      val entityListElement = entityList.getValues(entityListIndex)
      entityListElement.kindCase shouldBe Value.KindCase.STRUCT_VALUE
      val entityListElementPath = path.withAddedListIndex(entityListIndex)
      entityByPath shouldContainKey entityListElementPath
    }
  }
}
