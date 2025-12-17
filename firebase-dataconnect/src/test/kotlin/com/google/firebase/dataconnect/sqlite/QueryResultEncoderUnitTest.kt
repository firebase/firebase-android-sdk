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

import com.google.firebase.dataconnect.sqlite.QueryResultEncoder.IntermixedEntityAndNonEntityListInEntityException
import com.google.firebase.dataconnect.sqlite.QueryResultEncoderTesting.EntityTestCase
import com.google.firebase.dataconnect.sqlite.QueryResultEncoderTesting.calculateExpectedEncodingAsEntityId
import com.google.firebase.dataconnect.sqlite.QueryResultEncoderTesting.decodingEncodingShouldProduceIdenticalStruct
import com.google.firebase.dataconnect.testutil.MutableProtoValuePath
import com.google.firebase.dataconnect.testutil.ProtoValuePath
import com.google.firebase.dataconnect.testutil.buildByteArray
import com.google.firebase.dataconnect.testutil.isStructValue
import com.google.firebase.dataconnect.testutil.map
import com.google.firebase.dataconnect.testutil.property.arbitrary.listValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.recursivelyEmptyListValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.property.arbitrary.structKey
import com.google.firebase.dataconnect.testutil.property.arbitrary.twoValues
import com.google.firebase.dataconnect.testutil.property.arbitrary.value
import com.google.firebase.dataconnect.testutil.property.arbitrary.withIterations
import com.google.firebase.dataconnect.testutil.property.arbitrary.withIterationsIfNotNull
import com.google.firebase.dataconnect.testutil.randomlyInsertStruct
import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import com.google.firebase.dataconnect.testutil.toListValue
import com.google.firebase.dataconnect.testutil.toValueProto
import com.google.firebase.dataconnect.testutil.walk
import com.google.firebase.dataconnect.testutil.withAppendedListIndex
import com.google.firebase.dataconnect.testutil.withRandomlyInsertedStructs
import com.google.firebase.dataconnect.testutil.withRandomlyInsertedValue
import com.google.firebase.dataconnect.testutil.withRandomlyInsertedValues
import com.google.protobuf.ListValue
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
import io.kotest.property.PropertyContext
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlin.random.nextInt
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
      val struct = Struct.newBuilder().putFields("entityId", entityId.string.toValueProto()).build()

      val encodeResult = QueryResultEncoder.encode(struct, "entityId")

      encodeResult.entities shouldHaveSize 1
      val encodedEntityId = encodeResult.entities[0].encodedId
      encodedEntityId shouldBe entityId.string.calculateExpectedEncodingAsEntityId()
    }
  }

  @Test
  fun `entity ID contains code points with 1, 2, 3, and 4 byte UTF-8 encodings`() = runTest {
    checkAll(propTestConfig, Arb.string(), StringEncodingTestCase.arb()) {
      entityIdFieldName,
      entityId ->
      val struct =
        Struct.newBuilder().putFields(entityIdFieldName, entityId.string.toValueProto()).build()
      struct.decodingEncodingShouldProduceIdenticalStruct(
        entities = listOf(struct),
        entityIdFieldName
      )
    }
  }

  @Test
  fun `entity ID are long strings`() = runTest {
    checkAll(
      propTestConfig.withIterations(50),
      Arb.string(),
      StringEncodingTestCase.longStringsArb().map { it.string },
    ) { entityIdFieldName, entityId ->
      val struct = Struct.newBuilder().putFields(entityIdFieldName, entityId.toValueProto()).build()
      struct.decodingEncodingShouldProduceIdenticalStruct(
        entities = listOf(struct),
        entityIdFieldName
      )
    }
  }

  @Test
  fun `entity is the entire struct`() = runTest {
    checkAll(propTestConfig, EntityTestCase.arb()) { sample ->
      sample.struct.decodingEncodingShouldProduceIdenticalStruct(
        entities = listOf(sample.struct),
        sample.entityIdFieldName
      )
    }
  }

  @Test
  fun `entity contains nested entities in struct keys`() = runTest {
    val rootEntityCountArb = Arb.int(1..3)
    val subEntityCountArb = Arb.int(1..10)
    checkAll(propTestConfig, Arb.proto.structKey()) { entityIdFieldName ->
      val nonEntityIdFieldNameArb = Arb.proto.structKey().filterNot { it == entityIdFieldName }
      val generateKey: () -> String = { nonEntityIdFieldNameArb.bind() }
      val rootEntityCount = rootEntityCountArb.bind()
      val subEntityCounts = List(rootEntityCount) { subEntityCountArb.bind() }
      val totalEntityCount = rootEntityCount + subEntityCounts.sum()
      val entities = generateEntities(totalEntityCount, entityIdFieldName).map { it.struct }
      val rootEntities = buildList {
        val entityIterator = entities.iterator()
        repeat(rootEntityCount) { rootEntityIndex ->
          val rootEntityBuilder = entityIterator.next().toBuilder()
          val subEntityCount = subEntityCounts[rootEntityIndex]
          repeat(subEntityCount) {
            rootEntityBuilder.randomlyInsertStruct(
              entityIterator.next(),
              randomSource().random,
              generateKey
            )
          }
          add(rootEntityBuilder.build())
        }
      }
      val rootStruct =
        Arb.proto
          .struct(key = nonEntityIdFieldNameArb)
          .bind()
          .struct
          .withRandomlyInsertedStructs(
            rootEntities,
            randomSource().random,
            generateKey,
          )

      rootStruct.decodingEncodingShouldProduceIdenticalStruct(entities, entityIdFieldName)
    }
  }

  @Test
  fun `entity contains lists of lists of entities`() = runTest {
    checkAll(propTestConfig, Arb.proto.structKey(), Arb.int(2..4)) { entityIdFieldName, depth ->
      val entities = mutableListOf<Struct>()
      val entityGenerator =
        generateEntities(entityIdFieldName).map { it.struct }.onEach { entities.add(it) }.iterator()
      val listValue = generateListValueOfEntities(depth = depth, entityGenerator).listValue
      val nonEntityIdFieldNameArb = Arb.proto.structKey().filterNot { it == entityIdFieldName }
      val rootStruct =
        entityGenerator
          .next()
          .withRandomlyInsertedValue(
            listValue.toValueProto(),
            randomSource().random,
            generateKey = { nonEntityIdFieldNameArb.bind() }
          )

      rootStruct.decodingEncodingShouldProduceIdenticalStruct(entities, entityIdFieldName)
    }
  }

  @Test
  fun `entity contains lists of lists of entities with empty lists interspersed`() = runTest {
    checkAll(
      propTestConfig,
      Arb.proto.structKey(),
      Arb.int(2..10),
      Arb.proto.recursivelyEmptyListValue()
    ) { entityIdFieldName, entityCount, recursivelyEmptyListValue ->
      val entities = generateEntities(entityCount, entityIdFieldName).map { it.struct }
      val listValue =
        recursivelyEmptyListValue.listValue.withRandomlyInsertedValues(
          entities.drop(1).map { it.toValueProto() },
          randomSource().random,
        )
      val nonEntityIdFieldNameArb = Arb.proto.structKey().filterNot { it == entityIdFieldName }
      val rootStruct =
        entities[0].withRandomlyInsertedValue(
          listValue.toValueProto(),
          randomSource().random,
          generateKey = { nonEntityIdFieldNameArb.bind() }
        )

      rootStruct.decodingEncodingShouldProduceIdenticalStruct(entities, entityIdFieldName)
    }
  }

  @Test
  fun `entity contains lists of lists of entities and non-entities should throw`() = runTest {
    checkAll(
      propTestConfig,
      Arb.proto.structKey(),
      Arb.int(2..10),
      Arb.int(2..10),
      Arb.proto.recursivelyEmptyListValue()
    ) { entityIdFieldName, entityCount, nonEntityCount, recursivelyEmptyListValue ->
      val nonEntityIdFieldNameArb = Arb.proto.structKey().filterNot { it == entityIdFieldName }
      val nonEntityArb = Arb.proto.value(structKey = nonEntityIdFieldNameArb)
      val nonEntities = List(nonEntityCount) { nonEntityArb.bind() }
      val entities = generateEntities(entityCount, entityIdFieldName).map { it.struct }
      val listValue =
        recursivelyEmptyListValue.listValue.withRandomlyInsertedValues(
          nonEntities + entities.drop(1).map { it.toValueProto() },
          randomSource().random,
        )
      val rootStruct =
        entities[0].withRandomlyInsertedValue(
          listValue.toValueProto(),
          randomSource().random,
          generateKey = { nonEntityIdFieldNameArb.bind() }
        )

      val exception =
        shouldThrow<IntermixedEntityAndNonEntityListInEntityException> {
          QueryResultEncoder.encode(rootStruct, entityIdFieldName)
        }

      assertSoftly {
        exception.message shouldContainWithNonAbuttingText "df9tkx7jk4"
        exception.message shouldContainWithNonAbuttingTextIgnoringCase
          "must be all be entities or must all be non-entities"
      }
    }
  }

  @Test
  fun `entity contains lists of lists of non-entities with empty lists interspersed`() = runTest {
    checkAll(
      propTestConfig,
      Arb.proto.structKey(),
      Arb.int(2..10),
      Arb.proto.recursivelyEmptyListValue()
    ) { entityIdFieldName, nonEntityCount, recursivelyEmptyListValue ->
      val nonEntityIdFieldNameArb = Arb.proto.structKey().filterNot { it == entityIdFieldName }
      val nonEntityArb = Arb.proto.value(structKey = nonEntityIdFieldNameArb)
      val nonEntities = List(nonEntityCount) { nonEntityArb.bind() }
      val listValue =
        recursivelyEmptyListValue.listValue.withRandomlyInsertedValues(
          nonEntities,
          randomSource().random,
        )
      val rootStruct =
        generateEntity(entityIdFieldName)
          .struct
          .withRandomlyInsertedValue(
            listValue.toValueProto(),
            randomSource().random,
            generateKey = { nonEntityIdFieldNameArb.bind() }
          )

      rootStruct.decodingEncodingShouldProduceIdenticalStruct(listOf(rootStruct), entityIdFieldName)
    }
  }

  @Test
  fun `list containing only entities`() = runTest {
    checkAll(propTestConfig, Arb.proto.structKey(), Arb.int(1..10)) { entityIdFieldName, entityCount
      ->
      val entities = generateEntities(entityCount, entityIdFieldName).map { it.struct }

      val rootStruct = run {
        val nonEntityIdFieldNameArb = Arb.proto.structKey().filterNot { it == entityIdFieldName }
        val struct = Arb.proto.struct(key = nonEntityIdFieldNameArb).bind().struct
        struct.withRandomlyInsertedValue(
          entities.map { it.toValueProto() }.toListValue().toValueProto(),
          randomSource().random,
          { nonEntityIdFieldNameArb.bind() },
        )
      }

      rootStruct.decodingEncodingShouldProduceIdenticalStruct(entities, entityIdFieldName)
    }
  }

  @Test
  fun `list containing mixed entities and non-entities`() = runTest {
    checkAll(propTestConfig, Arb.proto.structKey(), Arb.twoValues(Arb.int(1..4))) {
      entityIdFieldName,
      (entityCount, nonEntityCount) ->
      val entities = generateEntities(entityCount, entityIdFieldName).map { it.struct }
      val rootStruct = run {
        val nonEntities = generateNonEntities(nonEntityCount, entityIdFieldName)
        val valueList =
          (entities.map { it.toValueProto() } + nonEntities).shuffled(randomSource().random)
        val nonEntityIdFieldNameArb = Arb.proto.structKey().filterNot { it == entityIdFieldName }
        val struct = Arb.proto.struct(key = nonEntityIdFieldNameArb).bind().struct
        struct.withRandomlyInsertedValue(
          valueList.toValueProto(),
          randomSource().random,
          { nonEntityIdFieldNameArb.bind() },
        )
      }

      rootStruct.decodingEncodingShouldProduceIdenticalStruct(entities, entityIdFieldName)
    }
  }

  @Test
  fun `entity with non-string ID field is treated as a non-entity`() = runTest {
    checkAll(propTestConfig, Arb.proto.structKey()) { entityIdFieldName ->
      val nonEntityIdStructKeyArb = Arb.proto.structKey().filterNot { it == entityIdFieldName }
      val nonStringValueArb =
        Arb.proto.value(exclude = Value.KindCase.STRING_VALUE, structKey = nonEntityIdStructKeyArb)
      val struct = Arb.proto.struct(key = nonEntityIdStructKeyArb).bind().struct
      val structPaths =
        struct.walk(includeSelf = true).filter { it.value.isStructValue }.map { it.path }.toList()
      val structPathsWithNonStringEntityIdValue =
        structPaths
          .shuffled(randomSource().random)
          .take(randomSource().random.nextInt(1..structPaths.size))
          .toSet()
      val structWithNonStringEntityIdValues =
        struct.map { path, value ->
          if (!structPathsWithNonStringEntityIdValue.contains(path)) {
            value
          } else {
            value.structValue
              .toBuilder()
              .putFields(entityIdFieldName, nonStringValueArb.bind())
              .build()
              .toValueProto()
          }
        }

      structWithNonStringEntityIdValues.decodingEncodingShouldProduceIdenticalStruct(
        emptyList(),
        entityIdFieldName
      )
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

    /** Generates an [EntityTestCase] object using the given entity ID field name. */
    fun PropertyContext.generateEntity(entityIdFieldName: String): EntityTestCase =
      generateEntities(1, entityIdFieldName).single()

    /**
     * Generates the given number of [EntityTestCase] objects using the given entity ID field name.
     */
    fun PropertyContext.generateEntities(
      count: Int,
      entityIdFieldName: String
    ): List<EntityTestCase> = generateEntities(entityIdFieldName).take(count).toList()

    /** Generates [EntityTestCase] objects using the given entity ID field name. */
    fun PropertyContext.generateEntities(entityIdFieldName: String): Sequence<EntityTestCase> {
      val entityValueArb = EntityTestCase.arb(entityIdFieldName = Arb.constant(entityIdFieldName))
      return generateSequence { entityValueArb.bind() }
    }

    /**
     * Generates the given number of [Value] objects that would not be considered to be entities,
     * nor contain values that would be considered to be entities with the given entity ID field
     * name.
     */
    fun PropertyContext.generateNonEntities(count: Int, entityIdFieldName: String): List<Value> =
      generateNonEntities(entityIdFieldName).take(count).toList()

    /**
     * Generates [Value] objects that would not be considered to be entities, nor contain values
     * that would be considered to be entities with the given entity ID field name.
     */
    fun PropertyContext.generateNonEntities(entityIdFieldName: String): Sequence<Value> {
      val nonEntityIdStructKeyArb = Arb.proto.structKey().filterNot { it == entityIdFieldName }
      val nonEntityValueArb = Arb.proto.value(structKey = nonEntityIdStructKeyArb)
      return generateSequence { nonEntityValueArb.bind() }
    }

    data class GenerateListValueOfEntitiesResult(
      val listValue: ListValue,
      val generatedListValuePaths: List<ProtoValuePath>,
    )

    fun PropertyContext.generateListValueOfEntities(
      depth: Int,
      entityGenerator: Iterator<Struct>
    ): GenerateListValueOfEntitiesResult {
      val generatedListValuePaths: MutableList<ProtoValuePath> = mutableListOf()
      val listValue =
        generateListValueOfEntities(
          depth = depth,
          entityGenerator = entityGenerator,
          path = mutableListOf(),
          generatedListValuePaths = generatedListValuePaths,
        )
      return GenerateListValueOfEntitiesResult(listValue, generatedListValuePaths.toList())
    }

    fun PropertyContext.generateListValueOfEntities(
      depth: Int,
      entityGenerator: Iterator<Struct>,
      path: MutableProtoValuePath,
      generatedListValuePaths: MutableList<ProtoValuePath>,
    ): ListValue {
      require(depth > 0) { "invalid depth: $depth [gwt2a6bbsz]" }
      val size = randomSource().random.nextInt(1..3)
      val valuesList =
        if (depth == 1) {
          generatedListValuePaths.add(path.toList())
          List(size) { entityGenerator.next().toValueProto() }
        } else {
          val fullDepthIndex = randomSource().random.nextInt(size)
          List(size) { listIndex ->
            val childDepth =
              if (listIndex == fullDepthIndex) {
                depth - 1
              } else {
                randomSource().random.nextInt(1 until depth)
              }
            path.withAppendedListIndex(listIndex) {
              generateListValueOfEntities(
                  childDepth,
                  entityGenerator,
                  path,
                  generatedListValuePaths
                )
                .toValueProto()
            }
          }
        }
      return valuesList.toListValue()
    }
  }
}
