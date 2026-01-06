/*
 * Copyright 2025 Google LLC
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

package com.google.firebase.dataconnect.sqlite

import com.google.firebase.dataconnect.DataConnectPath
import com.google.firebase.dataconnect.DataConnectPathSegment
import com.google.firebase.dataconnect.emptyDataConnectPath
import com.google.firebase.dataconnect.sqlite.QueryResultEncoderTesting.buildEntityIdByPathMap
import com.google.firebase.dataconnect.sqlite.QueryResultEncoderTesting.calculateExpectedEncodingAsEntityId
import com.google.firebase.dataconnect.sqlite.QueryResultEncoderTesting.decodingEncodingShouldProduceIdenticalStruct
import com.google.firebase.dataconnect.sqlite.QueryResultEncoderTesting.entityIdArb
import com.google.firebase.dataconnect.testutil.buildByteArray
import com.google.firebase.dataconnect.testutil.isListValue
import com.google.firebase.dataconnect.testutil.isRecursivelyEmpty
import com.google.firebase.dataconnect.testutil.isStructValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.listValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.recursivelyEmptyListValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.property.arbitrary.structKey
import com.google.firebase.dataconnect.testutil.property.arbitrary.value
import com.google.firebase.dataconnect.testutil.property.arbitrary.withIterations
import com.google.firebase.dataconnect.testutil.property.arbitrary.withIterationsIfNotNull
import com.google.firebase.dataconnect.testutil.randomlyInsertStruct
import com.google.firebase.dataconnect.testutil.randomlyInsertStructs
import com.google.firebase.dataconnect.testutil.randomlyInsertValue
import com.google.firebase.dataconnect.testutil.randomlyInsertValues
import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import com.google.firebase.dataconnect.testutil.toListValue
import com.google.firebase.dataconnect.testutil.toValueProto
import com.google.firebase.dataconnect.testutil.walk
import com.google.firebase.dataconnect.testutil.withAddedListIndex
import com.google.firebase.dataconnect.testutil.withAddedPathSegment
import com.google.firebase.dataconnect.testutil.withRandomlyInsertedValue
import com.google.firebase.dataconnect.testutil.withRandomlyInsertedValues
import com.google.protobuf.Struct
import com.google.protobuf.Value
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.Exhaustive
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.negativeInt
import io.kotest.property.assume
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.test.runTest
import org.junit.Test

class QueryResultEncoderUnitTest {

  @Test
  fun `bool values`() = runTest {
    data class BoolTestCase(val value: Boolean, val valueTypeIndicator: Byte)
    val arb =
      Exhaustive.of(
        BoolTestCase(true, QueryResultCodec.VALUE_BOOL_TRUE),
        BoolTestCase(false, QueryResultCodec.VALUE_BOOL_FALSE),
      )
    checkAll(propTestConfig, arb) { sample ->
      val struct = Struct.newBuilder().putFields("", sample.value.toValueProto()).build()

      val encodeResult = QueryResultEncoder.encode(struct)

      val expectedEncodedBytes = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        put(QueryResultCodec.VALUE_STRUCT)
        putUInt32(1) // struct size
        put(QueryResultCodec.VALUE_STRING_EMPTY)
        put(sample.valueTypeIndicator)
      }

      encodeResult.byteArray shouldBe expectedEncodedBytes
      QueryResultDecoder.decode(encodeResult.byteArray, emptyList()) shouldBe struct
    }
  }

  @Test
  fun `number values`() = runTest {
    checkAll(propTestConfig, DoubleEncodingTestCase.arb()) { sample ->
      val struct = Struct.newBuilder().putFields("", sample.value.toValueProto()).build()

      val encodeResult = QueryResultEncoder.encode(struct)

      val expectedEncodedBytes = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
        put(QueryResultCodec.VALUE_STRUCT)
        putUInt32(1) // struct size
        put(QueryResultCodec.VALUE_STRING_EMPTY)
        sample.encode(this)
      }

      encodeResult.byteArray shouldBe expectedEncodedBytes
      QueryResultDecoder.decode(encodeResult.byteArray, emptyList()) shouldBe struct
    }
  }

  @Test fun `string values`() = verifyStringValues(StringEncodingTestCase.arb())

  @Test
  fun `long string values`() =
    verifyStringValues(StringEncodingTestCase.longStringsArb(), iterations = 50)

  private fun verifyStringValues(arb: Arb<StringEncodingTestCase>, iterations: Int? = null) =
    runTest {
      checkAll(propTestConfig.withIterationsIfNotNull(iterations), arb) { sample ->
        val struct = Struct.newBuilder().putFields("", sample.string.toValueProto()).build()

        val encodeResult = QueryResultEncoder.encode(struct)

        val expectedEncodedBytes = buildByteArray {
          putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
          put(QueryResultCodec.VALUE_STRUCT)
          putUInt32(1) // struct size
          put(QueryResultCodec.VALUE_STRING_EMPTY)
          sample.encode(this)
        }

        encodeResult.byteArray shouldBe expectedEncodedBytes
        QueryResultDecoder.decode(encodeResult.byteArray, emptyList()) shouldBe struct
      }
    }

  @Test
  fun `struct values`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct()) { struct ->
      struct.struct.decodingEncodingShouldProduceIdenticalStruct()
    }
  }

  @Test
  fun `list values`() = runTest {
    val structKeyArb = Arb.proto.structKey()
    val structArb = Arb.proto.struct(size = 1..2, depth = 1..2)
    val listValueArb =
      Arb.choice(
        Arb.proto.listValue(size = 0..5, depth = 1..2).map { it.listValue },
        Arb.proto.recursivelyEmptyListValue().map { it.listValue },
      )
    checkAll(propTestConfig, structArb, Arb.list(listValueArb, 1..5)) { struct, listValueSamples ->
      val listValues = listValueSamples.map { it.toValueProto() }
      val structWithListValues =
        struct.struct.withRandomlyInsertedValues(
          listValues,
          randomSource().random,
          { structKeyArb.bind() }
        )
      structWithListValues.decodingEncodingShouldProduceIdenticalStruct()
    }
  }

  @Test fun `struct keys`() = verifyStringStructKeys(StringEncodingTestCase.arb())

  @Test
  fun `long struct keys`() =
    verifyStringStructKeys(StringEncodingTestCase.longStringsArb(), iterations = 50)

  private fun verifyStringStructKeys(arb: Arb<StringEncodingTestCase>, iterations: Int? = null) =
    runTest {
      checkAll(propTestConfig.withIterationsIfNotNull(iterations), arb) { sample ->
        val struct =
          Struct.newBuilder().putFields(sample.string, Value.getDefaultInstance()).build()

        val encodeResult = QueryResultEncoder.encode(struct)

        val expectedEncodedBytes = buildByteArray {
          putInt(QueryResultCodec.QUERY_RESULT_MAGIC)
          put(QueryResultCodec.VALUE_STRUCT)
          putUInt32(1) // struct size
          sample.encode(this)
          put(QueryResultCodec.VALUE_KIND_NOT_SET)
        }

        encodeResult.byteArray shouldBe expectedEncodedBytes
        QueryResultDecoder.decode(encodeResult.byteArray, emptyList()) shouldBe struct
      }
    }

  @Test
  fun `string encodings round trip`() = runTest {
    val structArb =
      Arb.proto.struct(
        size = 1..10,
        key = StringEncodingTestCase.arb().map { it.string },
        scalarValue = StringEncodingTestCase.arb().map { it.string.toValueProto() },
      )
    checkAll(propTestConfig, structArb) { struct ->
      struct.struct.decodingEncodingShouldProduceIdenticalStruct()
    }
  }

  @Test
  fun `long string encodings round trip`() = runTest {
    val structArb =
      Arb.proto.struct(
        size = 1,
        key = StringEncodingTestCase.longStringsArb().map { it.string },
        scalarValue = StringEncodingTestCase.longStringsArb().map { it.string.toValueProto() },
      )
    checkAll(propTestConfig.withIterations(50), structArb) { struct ->
      struct.struct.decodingEncodingShouldProduceIdenticalStruct()
    }
  }

  @Test
  fun `entity IDs are encoded using SHA-512`() = runTest {
    checkAll(propTestConfig, StringEncodingTestCase.arb()) { entityId ->
      val entityIdByPath = mapOf(emptyDataConnectPath() to entityId.string)

      val encodeResult = QueryResultEncoder.encode(Struct.getDefaultInstance(), entityIdByPath)

      encodeResult.entities shouldHaveSize 1
      val encodedEntityId = encodeResult.entities[0].encodedId
      encodedEntityId shouldBe entityId.string.calculateExpectedEncodingAsEntityId()
    }
  }

  @Test
  fun `entity ID contains code points with 1, 2, 3, and 4 byte UTF-8 encodings`() = runTest {
    checkAll(propTestConfig, StringEncodingTestCase.arb(), Arb.proto.struct()) { entityId, struct ->
      struct.struct.decodingEncodingShouldProduceIdenticalStruct(
        entities = listOf(struct.struct),
        entityIdByPath = mapOf(emptyDataConnectPath() to entityId.string),
      )
    }
  }

  @Test
  fun `entity ID is a long strings`() = runTest {
    checkAll(
      propTestConfig.withIterations(50),
      StringEncodingTestCase.longStringsArb().map { it.string },
      Arb.proto.struct(),
    ) { entityId, struct ->
      struct.struct.decodingEncodingShouldProduceIdenticalStruct(
        entities = listOf(struct.struct),
        entityIdByPath = mapOf(emptyDataConnectPath() to entityId),
      )
    }
  }

  @Test
  fun `entity is the entire struct`() = runTest {
    checkAll(propTestConfig, entityIdArb(), Arb.proto.struct()) { entityId, struct ->
      struct.struct.decodingEncodingShouldProduceIdenticalStruct(
        entities = listOf(struct.struct),
        entityIdByPath = mapOf(emptyDataConnectPath() to entityId.string),
      )
    }
  }

  @Test
  fun `entity contains nested entities in struct keys`() = runTest {
    val structKeyArb = Arb.proto.structKey()
    val structArb = Arb.proto.struct()
    val subEntityCountArb = Arb.int(1..10)
    checkAll(propTestConfig, Arb.int(1..3)) { rootEntityCount ->
      val subEntityCounts = List(rootEntityCount) { subEntityCountArb.bind() }
      val totalEntityCount = rootEntityCount + subEntityCounts.sum()
      val entities = List(totalEntityCount) { structArb.bind().struct }
      data class RootEntityInfo(val struct: Struct, val subEntityPaths: List<DataConnectPath>)
      val rootEntities = buildList {
        val entityIterator = entities.iterator()
        repeat(rootEntityCount) { rootEntityIndex ->
          val rootEntityBuilder = entityIterator.next().toBuilder()
          val subEntityCount = subEntityCounts[rootEntityIndex]
          val subEntityPaths = mutableListOf<DataConnectPath>()
          repeat(subEntityCount) {
            val subEntityPath =
              rootEntityBuilder.randomlyInsertStruct(
                entityIterator.next(),
                randomSource().random,
                { structKeyArb.bind() },
              )
            subEntityPaths.add(subEntityPath)
          }
          add(RootEntityInfo(rootEntityBuilder.build(), subEntityPaths.toList()))
        }
      }
      val rootStructBuilder = structArb.bind().struct.toBuilder()
      val rootEntityPaths =
        rootStructBuilder.randomlyInsertStructs(
          rootEntities.map { it.struct },
          randomSource().random,
          { structKeyArb.bind() },
        )
      val rootStruct = rootStructBuilder.build()
      val entityPaths = buildList {
        rootEntityPaths.forEachIndexed { index, rootEntityPath ->
          add(rootEntityPath)
          rootEntities[index].subEntityPaths.forEach { subEntityPath ->
            add(rootEntityPath + subEntityPath)
          }
        }
      }
      val entityIdByPath = buildEntityIdByPathMap {
        entityPaths.forEach { entityPath -> putWithRandomUniqueEntityId(entityPath) }
      }

      rootStruct.decodingEncodingShouldProduceIdenticalStruct(entities, entityIdByPath)
    }
  }

  @Test
  fun `entity contains lists of lists of entities`() = runTest {
    val structKeyArb = Arb.proto.structKey()
    val structArb = Arb.proto.struct()
    val listValueArb =
      Arb.proto
        .listValue(
          depth = 2..4,
          structSize = IntRange.EMPTY,
          scalarValue = structArb.map { it.toValueProto() }
        )
        .filterNot { it.listValue.isRecursivelyEmpty() }

    checkAll(propTestConfig, structArb, listValueArb) { struct, listValueSample ->
      val entityPathValuePairs = listValueSample.descendants.filterNot { it.value.isListValue }
      val entities = entityPathValuePairs.map { it.value.structValue }
      val rootStructBuilder = struct.struct.toBuilder()
      val listValuePath =
        rootStructBuilder.randomlyInsertValue(
          listValueSample.listValue.toValueProto(),
          randomSource().random,
          generateKey = { structKeyArb.bind() }
        )
      val rootStruct = rootStructBuilder.build()
      val entityIdByPath = buildEntityIdByPathMap {
        entityPathValuePairs
          .map { it.path }
          .forEach { entitySubPath -> putWithRandomUniqueEntityId(listValuePath + entitySubPath) }
      }

      rootStruct.decodingEncodingShouldProduceIdenticalStruct(entities, entityIdByPath)
    }
  }

  @Test
  fun `entity contains lists of lists of entities with empty lists interspersed`() = runTest {
    val structKeyArb = Arb.proto.structKey()
    val structArb = Arb.proto.struct()

    checkAll(propTestConfig, structArb, Arb.int(2..10), Arb.proto.recursivelyEmptyListValue()) {
      struct,
      subEntityCount,
      recursivelyEmptyListValue ->
      val subEntities = List(subEntityCount) { structArb.bind().struct }
      val listValueBuilder = recursivelyEmptyListValue.listValue.toBuilder()
      val entitySubPaths =
        listValueBuilder.randomlyInsertValues(
          subEntities.map { it.toValueProto() },
          randomSource().random,
        )
      val listValue = listValueBuilder.build()
      val rootStructBuilder = struct.struct.toBuilder()
      val listValuePath =
        rootStructBuilder.randomlyInsertValue(
          listValue.toValueProto(),
          randomSource().random,
          generateKey = { structKeyArb.bind() }
        )
      val rootStruct = rootStructBuilder.build()
      val entityIdByPath = buildEntityIdByPathMap {
        putWithRandomUniqueEntityId(emptyDataConnectPath())
        entitySubPaths.forEach { entitySubPath ->
          putWithRandomUniqueEntityId(listValuePath + entitySubPath)
        }
      }
      val entities = buildList {
        add(struct.struct)
        addAll(subEntities)
      }

      rootStruct.decodingEncodingShouldProduceIdenticalStruct(entities, entityIdByPath)
    }
  }

  @Test
  fun `entity contains lists of lists of non-entities with empty lists interspersed`() = runTest {
    val structKeyArb = Arb.proto.structKey()
    val structArb = Arb.proto.struct()
    val entityIdArb = entityIdArb()

    checkAll(propTestConfig, structArb, Arb.int(2..10), Arb.proto.recursivelyEmptyListValue()) {
      struct,
      nonEntityCount,
      recursivelyEmptyListValue ->
      val nonEntities = List(nonEntityCount) { structArb.bind().struct }
      val listValueBuilder = recursivelyEmptyListValue.listValue.toBuilder()
      listValueBuilder.randomlyInsertValues(
        nonEntities.map { it.toValueProto() },
        randomSource().random,
      )
      val listValue = listValueBuilder.build()
      val rootStruct =
        struct.struct.withRandomlyInsertedValue(
          listValue.toValueProto(),
          randomSource().random,
          generateKey = { structKeyArb.bind() }
        )

      rootStruct.decodingEncodingShouldProduceIdenticalStruct(
        entities = listOf(rootStruct),
        entityIdByPath = mapOf(emptyDataConnectPath() to entityIdArb.bind().string),
      )
    }
  }

  @Test
  fun `entity contains lists of lists of entities and non-entities should throw`() = runTest {
    val valueExceptRecursivelyEmptyListsArb =
      Arb.proto.value().filterNot { it.isListValue && it.listValue.isRecursivelyEmpty() }
    val structKeyArb = Arb.proto.structKey()
    checkAll(
      propTestConfig,
      Arb.proto.struct(),
      Arb.proto.recursivelyEmptyListValue(),
      Arb.list(Arb.proto.struct(), 1..10),
      Arb.list(valueExceptRecursivelyEmptyListsArb, 1..10),
    ) { rootStructBase, recursivelyEmptyListValue, entities, nonEntities ->
      val listValueBuilder = recursivelyEmptyListValue.listValue.toBuilder()
      val insertPaths =
        listValueBuilder.randomlyInsertValues(
          entities.map { it.toValueProto() } + nonEntities,
          randomSource().random,
        )
      val listValue = listValueBuilder.build()
      val rootStructBuilder = rootStructBase.struct.toBuilder()
      val listValuePath =
        rootStructBuilder.randomlyInsertValue(
          listValue.toValueProto(),
          randomSource().random,
          generateKey = { structKeyArb.bind() }
        )
      val rootStruct = rootStructBuilder.build()
      val entityIdByPath = buildEntityIdByPathMap {
        putWithRandomUniqueEntityId(emptyDataConnectPath())
        val entityPaths = insertPaths.take(entities.size)
        entityPaths.forEach { entityPath ->
          putWithRandomUniqueEntityId(listValuePath + entityPath)
        }
      }

      TODO()
//      val exception =
//        shouldThrow<IntermixedEntityAndNonEntityListInEntityException> {
//          QueryResultEncoder.encode(rootStruct, entityIdByPath)
//        }
//
//      assertSoftly {
//        exception.message shouldContainWithNonAbuttingText "df9tkx7jk4"
//        exception.message shouldContainWithNonAbuttingTextIgnoringCase
//          "must be all be entities or must all be non-entities"
//      }
    }
  }

  @Test
  fun `list containing only entities`() = runTest {
    val structKeyArb = Arb.proto.structKey()
    val structArb = Arb.proto.struct()

    checkAll(propTestConfig, structArb, Arb.int(1..10)) { struct, entityCount ->
      val entities = List(entityCount) { structArb.bind().struct }
      val listValue = entities.map { it.toValueProto() }.toListValue()
      val rootStructBuilder = struct.struct.toBuilder()
      val listValuePath =
        rootStructBuilder.randomlyInsertValue(
          listValue.toValueProto(),
          randomSource().random,
          { structKeyArb.bind() },
        )
      val rootStruct = rootStructBuilder.build()
      val entityIdByPath = buildEntityIdByPathMap {
        repeat(entityCount) { entityIndex ->
          putWithRandomUniqueEntityId(listValuePath.withAddedListIndex(entityIndex))
        }
      }

      rootStruct.decodingEncodingShouldProduceIdenticalStruct(entities, entityIdByPath)
    }
  }

  @Test
  fun `list containing mixed entities and non-entities`() = runTest {
    val structKeyArb = Arb.proto.structKey()
    val structArb = Arb.proto.struct()

    checkAll(propTestConfig, structArb, Arb.int(1..10), Arb.int(1..10)) {
      struct,
      entityCount,
      nonEntityCount ->
      val listElements = List(entityCount + nonEntityCount) { structArb.bind().struct }
      val entityIndices = listElements.indices.shuffled(randomSource().random).take(entityCount)
      val entities = listElements.filterIndexed { index, _ -> entityIndices.contains(index) }
      val listValue = listElements.map { it.toValueProto() }.toListValue()
      val rootStructBuilder = struct.struct.toBuilder()
      val listValuePath =
        rootStructBuilder.randomlyInsertValue(
          listValue.toValueProto(),
          randomSource().random,
          { structKeyArb.bind() },
        )
      val rootStruct = rootStructBuilder.build()
      val entityIdByPath = buildEntityIdByPathMap {
        entityIndices.forEach { entityIndex ->
          putWithRandomUniqueEntityId(listValuePath.withAddedListIndex(entityIndex))
        }
      }

      rootStruct.decodingEncodingShouldProduceIdenticalStruct(entities, entityIdByPath)
    }
  }

  @Test
  fun `entity paths of non-struct values are ignored`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct()) { struct ->
      val nonStructPaths =
        struct.struct.walk().filterNot { it.value.isStructValue }.map { it.path }.toList()
      assume(nonStructPaths.isNotEmpty())
      val entityIdByPath = buildEntityIdByPathMap {
        nonStructPaths.forEach { nonStructPath -> putWithRandomUniqueEntityId(nonStructPath) }
      }

      struct.struct.decodingEncodingShouldProduceIdenticalStruct(emptyList(), entityIdByPath)
    }
  }

  @Test
  fun `entity paths of non-existent values are ignored`() = runTest {
    val structKeyArb = Arb.proto.structKey()

    checkAll(propTestConfig, Arb.proto.struct()) { struct ->
      val nonExistentPaths = buildList {
        struct.struct.walk().forEach { (path, value) ->
          val nonExistentSubPathsArb =
            if (value.isStructValue) {
              structKeyArb
                .filterNot { value.structValue.containsFields(it) }
                .map(DataConnectPathSegment::Field)
            } else if (value.isListValue) {
              Arb.choice(Arb.negativeInt(), Arb.int(min = value.listValue.valuesCount))
                .map(DataConnectPathSegment::ListIndex)
            } else {
              null
            }
          if (nonExistentSubPathsArb !== null) {
            repeat(3) {
              val nonExistentPath = path.withAddedPathSegment(nonExistentSubPathsArb.bind())
              add(nonExistentPath)
            }
          }
        }
      }
      assume(nonExistentPaths.isNotEmpty())
      val entityIdByPath = buildEntityIdByPathMap {
        nonExistentPaths.forEach { nonExistentPath -> putWithRandomUniqueEntityId(nonExistentPath) }
      }

      struct.struct.decodingEncodingShouldProduceIdenticalStruct(emptyList(), entityIdByPath)
    }
  }

  private companion object {

    @OptIn(ExperimentalKotest::class)
    val propTestConfig =
      PropTestConfig(
        iterations = 1000,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.33),
        shrinkingMode = ShrinkingMode.Off,
      )
  }
}
