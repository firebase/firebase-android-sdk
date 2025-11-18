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

import com.google.firebase.dataconnect.sqlite.QueryResultEncoderTesting.EntityTestCase
import com.google.firebase.dataconnect.sqlite.QueryResultEncoderTesting.calculateExpectedEncodingAsEntityId
import com.google.firebase.dataconnect.sqlite.QueryResultEncoderTesting.decodingEncodingShouldProduceIdenticalStruct
import com.google.firebase.dataconnect.sqlite.QueryResultEncoderTesting.withRandomlyInsertedEntities
import com.google.firebase.dataconnect.testutil.buildByteArray
import com.google.firebase.dataconnect.testutil.property.arbitrary.maxDepth
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.stringValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.property.arbitrary.structKey
import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.firebase.dataconnect.util.ProtoUtil.buildStructProto
import com.google.firebase.dataconnect.util.ProtoUtil.toCompactString
import com.google.firebase.dataconnect.util.ProtoUtil.toValueProto
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.Exhaustive
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.set
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlin.random.nextInt
import kotlinx.coroutines.test.runTest
import org.junit.Test

class QueryResultEncoderUnitTest {

  @Test
  fun `bool values`() = runTest {
    data class BoolTestCase(val value: Boolean, val discriminator: Byte)
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
        put(sample.discriminator)
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

  @Test fun `long string values`() = verifyStringValues(StringEncodingTestCase.longStringsArb())

  private fun verifyStringValues(arb: Arb<StringEncodingTestCase>) = runTest {
    checkAll(propTestConfig, arb) { sample ->
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

  @Test fun `struct keys`() = verifyStringStructKeys(StringEncodingTestCase.arb())

  @Test fun `long struct keys`() = verifyStringStructKeys(StringEncodingTestCase.longStringsArb())

  private fun verifyStringStructKeys(arb: Arb<StringEncodingTestCase>) = runTest {
    checkAll(propTestConfig, arb) { sample ->
      val struct = Struct.newBuilder().putFields(sample.string, Value.getDefaultInstance()).build()

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
  fun `various structs round trip`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct()) { struct ->
      struct.struct.decodingEncodingShouldProduceIdenticalStruct()
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
    checkAll(
      @OptIn(ExperimentalKotest::class)
      propTestConfig.copy(iterations = 100, seed = 5085109927819487176),
      structArb
    ) { struct ->
      struct.struct.decodingEncodingShouldProduceIdenticalStruct()
    }
  }

  @Test
  fun `entire struct is an empty entity`() = runTest {
    checkAll(propTestConfig, Arb.string(), Arb.proto.stringValue()) { entityIdFieldName, entityId ->
      val struct = Struct.newBuilder().putFields(entityIdFieldName, entityId).build()
      struct.decodingEncodingShouldProduceIdenticalStruct(
        entities = listOf(struct),
        entityIdFieldName
      )
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
      @OptIn(ExperimentalKotest::class) propTestConfig.copy(iterations = 10),
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
  fun `entire struct is a non-nested entity`() = runTest {
    checkAll(propTestConfig, EntityTestCase.arb(structSize = 1..100, structDepth = 1)) { sample ->
      sample.struct.decodingEncodingShouldProduceIdenticalStruct(
        entities = listOf(sample.struct),
        sample.entityIdFieldName
      )
    }
  }

  @Test
  fun `entire struct is a nested entity`() = runTest {
    checkAll(propTestConfig, EntityTestCase.arb(structSize = 2..3, structDepth = 2..4)) { sample ->
      sample.struct.decodingEncodingShouldProduceIdenticalStruct(
        entities = listOf(sample.struct),
        sample.entityIdFieldName
      )
    }
  }

  @Test
  fun `struct has only entities at top level`() = runTest {
    checkAll(propTestConfig, Arb.string(), Arb.int(1..3)) { entityIdFieldName, entityCount ->
      val entityArb =
        EntityTestCase.arb(
          entityIdFieldName = Arb.constant(entityIdFieldName),
          structSize = 0..2,
          structDepth = 1..2,
        )
      val entities = List(entityCount) { entityArb.bind().struct }
      val structKeyArb = Arb.proto.structKey().filterNot { it == entityIdFieldName }
      val structKeys = Arb.set(structKeyArb, entities.size).bind()
      val struct =
        Struct.newBuilder()
          .apply {
            structKeys.zip(entities).forEach { (key, entity) ->
              putFields(key, entity.toValueProto())
            }
          }
          .build()

      struct.decodingEncodingShouldProduceIdenticalStruct(entities = entities, entityIdFieldName)
    }
  }

  @Test
  fun `struct has entities at mixed depths`() = runTest {
    checkAll(
      @OptIn(ExperimentalKotest::class) propTestConfig.copy(iterations = 100),
      Arb.string(),
      Arb.int(1..3),
    ) { entityIdFieldName, entityCount ->
      val entityArb =
        EntityTestCase.arb(
          entityIdFieldName = Arb.constant(entityIdFieldName),
          structSize = 0..2,
          structDepth = 1..2,
        )
      val entities = List(entityCount) { entityArb.bind().struct }
      val structKeyArb = Arb.proto.structKey().filterNot { it == entityIdFieldName }
      val structKeys = Arb.set(structKeyArb, entities.size).bind()
      val struct =
        Struct.newBuilder()
          .apply {
            structKeys.zip(entities).forEach { (key, entity) ->
              val depth = randomSource().random.nextInt(1..5)
              var subStruct = entity
              repeat(depth) {
                subStruct = Struct.newBuilder().putFields(key, subStruct.toValueProto()).build()
              }
              putFields(key, subStruct.toValueProto())
            }
          }
          .build()
      check(struct.maxDepth() > 1) {
        "struct.maxDepth()==${struct.maxDepth()}, struct=${struct.toCompactString()}"
      }

      struct.decodingEncodingShouldProduceIdenticalStruct(entities = entities, entityIdFieldName)
    }
  }

  @Test
  fun `struct has intermixed entities and values`() = runTest {
    checkAll(propTestConfig, Arb.string(), Arb.int(1..3)) { entityIdFieldName, entityCount ->
      val entityArb =
        EntityTestCase.arb(
          entityIdFieldName = Arb.constant(entityIdFieldName),
          structSize = 0..2,
          structDepth = 1..2,
        )
      val entities = List(entityCount) { entityArb.bind() }
      val entityStructs = entities.map { it.struct }
      val structKeyArb = Arb.proto.structKey().filterNot { it == entityIdFieldName }
      val originalStruct = Arb.proto.struct(key = structKeyArb).bind().struct
      val patchedStruct =
        originalStruct.withRandomlyInsertedEntities(
          entityStructs,
          randomSource(),
          { structKeyArb.bind() }
        )

      patchedStruct.decodingEncodingShouldProduceIdenticalStruct(
        entities = entityStructs,
        entityIdFieldName
      )
    }
  }

  @Test
  fun `struct has nested entities`() = runTest {
    val entity1 = buildStructProto {
      put("_id", "entity1")
      put("name", "annie")
      put("age", 42)
    }
    val entity2 = buildStructProto {
      put("_id", "entity2")
      put("name", "jackson")
      put("age", 24)
      put("sibling", entity1)
    }
    val entity2Pruned = entity2.toBuilder().removeFields("sibling").build()
    val rootEntity = buildStructProto {
      put("_id", "entity3")
      put("type", "results")
      put("person", entity2)
    }
    val rootEntityPruned = rootEntity.toBuilder().removeFields("person").build()

    rootEntity.decodingEncodingShouldProduceIdenticalStruct(
      entities = listOf(rootEntityPruned, entity1, entity2Pruned),
      entityIdFieldName = "_id",
    )
  }

  @Test
  fun `list has nested entities and scalar values`() = runTest {
    val entity1 = buildStructProto {
      put("_id", "entity1")
      put("name", "annie")
      put("age", 42)
    }
    val entity2 = buildStructProto {
      put("_id", "entity2")
      put("name", "jackson")
      put("age", 24)
      put("sibling", entity1)
    }
    val list =
      ListValue.newBuilder()
        .apply {
          addValues("foobar".toValueProto())
          addValues(entity2.toValueProto())
          addValues(42.toValueProto())
        }
        .build()
    val root = buildStructProto {
      put("foo", "bar")
      put("stuff", list)
    }

    val entities: List<Struct> =
      listOf(
        entity1,
        entity2.toBuilder().removeFields("sibling").build(),
      )

    root.decodingEncodingShouldProduceIdenticalStruct(
      entities = entities,
      entityIdFieldName = "_id",
    )
  }

  @Test
  fun `entity has list with nested entities and scalar values`() = runTest {
    val entity1 = buildStructProto {
      put("_id", "entity1")
      put("name", "annie")
      put("age", 42)
    }
    val entity2 = buildStructProto {
      put("_id", "entity2")
      put("name", "jackson")
      put("age", 24)
      put("sibling", entity1)
    }
    val list =
      ListValue.newBuilder()
        .apply {
          addValues("foobar".toValueProto())
          addValues(entity2.toValueProto())
          addValues(42.toValueProto())
        }
        .build()
    val root = buildStructProto {
      put("_id", "root")
      put("foo", "bar")
      put("stuff", list)
    }

    val entities: List<Struct> =
      listOf(
        entity1,
        entity2.toBuilder().removeFields("sibling").build(),
        root
          .toBuilder()
          .putFields(
            "stuff",
            root
              .getFieldsOrThrow("stuff")
              .listValue
              .toBuilder()
              .apply { setValues(1, Value.getDefaultInstance()) }
              .build()
              .toValueProto()
          )
          .build()
      )

    root.decodingEncodingShouldProduceIdenticalStruct(
      entities = entities,
      entityIdFieldName = "_id",
    )
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
