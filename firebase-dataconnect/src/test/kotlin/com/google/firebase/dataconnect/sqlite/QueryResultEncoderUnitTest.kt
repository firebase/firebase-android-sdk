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

import com.google.firebase.dataconnect.testutil.buildByteArray
import com.google.firebase.dataconnect.testutil.property.arbitrary.StringWithEncodingLengthArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.StringWithEncodingLengthArb.Mode.Utf8EncodingLongerThanUtf16
import com.google.firebase.dataconnect.testutil.property.arbitrary.StringWithEncodingLengthArb.Mode.Utf8EncodingShorterThanOrEqualToUtf16
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWith1ByteUtf8Encoding
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWith2ByteUtf8Encoding
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWith3ByteUtf8Encoding
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWith4ByteUtf8Encoding
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWithEvenNumByteUtf8EncodingDistribution
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.maxDepth
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.stringValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.stringWithLoneSurrogates
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.property.arbitrary.structKey
import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.firebase.dataconnect.util.ProtoUtil.buildStructProto
import com.google.firebase.dataconnect.util.ProtoUtil.toCompactString
import com.google.firebase.dataconnect.util.ProtoUtil.toValueProto
import com.google.firebase.dataconnect.util.StringUtil.to0xHexString
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.common.DelicateKotest
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.RandomSource
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.distinct
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.pair
import io.kotest.property.arbitrary.set
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.random.nextInt
import kotlinx.coroutines.test.runTest
import org.junit.Test

class QueryResultEncoderUnitTest {

  @Test
  fun `strings are encoded with the empty string marker when the length is 0`() {
    val struct = Struct.newBuilder().putFields("", true.toValueProto()).build()

    val encodedBytes = QueryResultEncoder.encode(struct).byteArray

    val expectedEncodedBytes = buildByteArray {
      putInt(QueryResultCodec.QUERY_RESULT_HEADER)
      put(QueryResultCodec.VALUE_STRUCT)
      putInt(1) // struct size
      put(QueryResultCodec.VALUE_STRING_EMPTY)
      put(QueryResultCodec.VALUE_BOOL_TRUE)
    }
    encodedBytes shouldBe expectedEncodedBytes
  }

  @Test
  fun `strings are encoded in utf8 when shorter than or equal to utf16`() = runTest {
    val stringArb = StringWithEncodingLengthArb(Utf8EncodingShorterThanOrEqualToUtf16, 1..100)
    checkAll(propTestConfig, stringArb) { string ->
      val struct = Struct.newBuilder().putFields(string, true.toValueProto()).build()

      val encodedBytes = QueryResultEncoder.encode(struct).byteArray

      val stringUtf8Encoded = string.encodeToByteArray()
      encodedBytes.to0xHexString() shouldContain
        stringUtf8Encoded.to0xHexString(include0xPrefix = false)
    }
  }

  @Test
  fun `strings are encoded in utf16 when longer than utf8`() = runTest {
    val stringArb = StringWithEncodingLengthArb(Utf8EncodingLongerThanUtf16, 1..100)
    checkAll(propTestConfig, stringArb) { string ->
      val struct = Struct.newBuilder().putFields(string, true.toValueProto()).build()

      val encodedBytes = QueryResultEncoder.encode(struct).byteArray

      val stringUtf16Encoded = string.toByteArray(Charsets.UTF_16BE)
      encodedBytes.to0xHexString() shouldContain
        stringUtf16Encoded.to0xHexString(include0xPrefix = false)
    }
  }

  @Test
  fun `strings are encoded in utf16 when they contain lone surrogates`() = runTest {
    val stringArb = Arb.stringWithLoneSurrogates(1..100).map { it.string }
    checkAll(propTestConfig, stringArb) { string ->
      val struct = Struct.newBuilder().putFields(string, true.toValueProto()).build()

      val encodedBytes = QueryResultEncoder.encode(struct).byteArray

      val stringUtf16Encoded = buildByteArray { string.forEach(::putChar) }
      encodedBytes.to0xHexString() shouldContain
        stringUtf16Encoded.to0xHexString(include0xPrefix = false)
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
        key = stringForEncodeTestingArb(),
        scalarValue = stringForEncodeTestingArb().map { it.toValueProto() },
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
        key = longStringForEncodeTestingArb(),
        scalarValue = longStringForEncodeTestingArb().map { it.toValueProto() },
      )
    checkAll(@OptIn(ExperimentalKotest::class) propTestConfig.copy(iterations = 10), structArb) {
      struct ->
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
    checkAll(propTestConfig, stringForEncodeTestingArb()) { entityId ->
      val struct = Struct.newBuilder().putFields("entityId", entityId.toValueProto()).build()

      val encodeResult = QueryResultEncoder.encode(struct, "entityId")

      encodeResult.entities shouldHaveSize 1
      val encodedEntityId = encodeResult.entities[0].encodedId
      encodedEntityId shouldBe entityId.calculateExpectedEncodingAsEntityId()
    }
  }

  @Test
  fun `entity ID contains code points with 1, 2, 3, and 4 byte UTF-8 encodings`() = runTest {
    checkAll(propTestConfig, Arb.string(), stringForEncodeTestingArb()) {
      entityIdFieldName,
      entityId ->
      val struct = Struct.newBuilder().putFields(entityIdFieldName, entityId.toValueProto()).build()
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
      longStringForEncodeTestingArb()
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
    checkAll(propTestConfig, entityArb(structSize = 1..100, structDepth = 1)) { sample ->
      sample.struct.decodingEncodingShouldProduceIdenticalStruct(
        entities = listOf(sample.struct),
        sample.entityIdFieldName
      )
    }
  }

  @Test
  fun `entire struct is a nested entity`() = runTest {
    checkAll(propTestConfig, entityArb(structSize = 2..3, structDepth = 2..4)) { sample ->
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
        entityArb(
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
        entityArb(
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
        entityArb(
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

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Unit tests for unit testing classes and functions defined in this file
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun `entityArb() should specify the correct entityIdFieldName in its samples`() = runTest {
    checkAll(propTestConfig, Arb.string()) { entityIdFieldName ->
      val entityArb =
        entityArb(
          entityIdFieldName = Arb.constant(entityIdFieldName),
          structSize = 0..10,
          structDepth = 1..1,
        )

      val sample = entityArb.bind()

      sample.entityIdFieldName shouldBe entityIdFieldName
    }
  }

  @Test
  fun `entityArb() should specify the correct entityId in its samples`() = runTest {
    checkAll(propTestConfig, Arb.string()) { entityId ->
      val entityArb =
        entityArb(
          entityId = Arb.constant(entityId),
          structSize = 0..10,
          structDepth = 1..1,
        )

      val sample = entityArb.bind()

      sample.entityId shouldBe entityId
    }
  }

  @Test
  fun `entityArb() should use set the entityId using the entityIdFieldName in the struct`() =
    runTest {
      checkAll(propTestConfig, Arb.string(), Arb.string()) { entityId, entityIdFieldName ->
        val entityArb =
          entityArb(
            entityIdFieldName = Arb.constant(entityIdFieldName),
            entityId = Arb.constant(entityId),
            structSize = 0..10,
            structDepth = 1..1,
          )

        val sample = entityArb.bind()

        sample.asClue {
          withClue("containsFields") { it.struct.containsFields(entityIdFieldName).shouldBeTrue() }
          val entityIdValue: Value = it.struct.getFieldsOrThrow(entityIdFieldName)
          withClue("kindCase") { entityIdValue.kindCase shouldBe Value.KindCase.STRING_VALUE }
          withClue("stringValue") { entityIdValue.stringValue shouldBe entityId }
        }
      }
    }

  @Test
  fun `entityArb() should use the given structKey`() = runTest {
    checkAll(propTestConfig, Arb.string()) { entityIdFieldName ->
      val generatedKeys = mutableSetOf(entityIdFieldName)
      val structKeyArb = Arb.proto.structKey().map { it.also(generatedKeys::add) }
      val entityArb =
        entityArb(
          entityIdFieldName = Arb.constant(entityIdFieldName),
          structKey = structKeyArb,
          structSize = 1..3,
          structDepth = 1..3,
        )

      val sample = entityArb.bind()

      sample.struct.keysRecursive().distinct() shouldContainExactlyInAnyOrder generatedKeys
    }
  }

  @Test
  fun `entityArb() should use the given structSize`() = runTest {
    checkAll(propTestConfig, Arb.int(0..20), Arb.int(0..20)) { bound1, bound2 ->
      val entityArbStructSize = if (bound1 <= bound2) bound1..bound2 else bound2..bound1
      val entityArb = entityArb(structSize = entityArbStructSize, structDepth = 1)

      val sample = entityArb.bind()

      // Add 1 to the end of the struct size range to account for the entityId field itself.
      val structSizeRange = entityArbStructSize.first..(entityArbStructSize.last + 1)
      assertSoftly {
        sample.struct.structSizesRecursive().forEachIndexed { index, structSize ->
          withClue("index=$index") { structSize shouldBeInRange structSizeRange }
        }
      }
    }
  }

  @Test
  fun `entityArb() should use the given structDepth as IntRange`() = runTest {
    checkAll(propTestConfig, Arb.int(1..5), Arb.int(1..5)) { bound1, bound2 ->
      val entityArbStructDepth = if (bound1 <= bound2) bound1..bound2 else bound2..bound1
      val entityArb = entityArb(structSize = 1..2, structDepth = entityArbStructDepth)

      val sample = entityArb.bind()

      sample.struct.maxDepth() shouldBeInRange entityArbStructDepth
    }
  }

  @Test
  fun `entityArb() should use the given structDepth as Int`() = runTest {
    checkAll(propTestConfig, Arb.int(1..5)) { entityArbStructDepth ->
      val entityArb = entityArb(structSize = 1..2, structDepth = entityArbStructDepth)

      val sample = entityArb.bind()

      sample.struct.maxDepth() shouldBe entityArbStructDepth
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Class-level classes and functions
  //////////////////////////////////////////////////////////////////////////////////////////////////

  data class EntityArbSample(
    val entityIdFieldName: String,
    val entityId: String,
    val struct: Struct
  )

  private companion object {

    @OptIn(ExperimentalKotest::class)
    val propTestConfig =
      PropTestConfig(
        iterations = 1000,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.33),
        shrinkingMode = ShrinkingMode.Off,
      )

    fun stringForEncodeTestingArb(): Arb<String> =
      Arb.choice(
        Arb.constant(""),
        Arb.string(1..20, Arb.codepointWith1ByteUtf8Encoding()),
        Arb.string(1..20, Arb.codepointWith2ByteUtf8Encoding()),
        Arb.string(1..20, Arb.codepointWith3ByteUtf8Encoding()),
        Arb.string(1..20, Arb.codepointWith4ByteUtf8Encoding()),
        Arb.string(1..20, Arb.codepointWithEvenNumByteUtf8EncodingDistribution()),
        Arb.stringWithLoneSurrogates(1..20).map { it.string },
        Arb.dataConnect.string(0..20),
      )

    fun longStringForEncodeTestingArb(): Arb<String> =
      Arb.choice(
        Arb.string(2048..99999, Arb.codepointWith1ByteUtf8Encoding()),
        Arb.string(2048..99999, Arb.codepointWith2ByteUtf8Encoding()),
        Arb.string(2048..99999, Arb.codepointWith3ByteUtf8Encoding()),
        Arb.string(2048..99999, Arb.codepointWith4ByteUtf8Encoding()),
        Arb.string(2048..99999, Arb.codepointWithEvenNumByteUtf8EncodingDistribution()),
        Arb.stringWithLoneSurrogates(2048..99999).map { it.string },
        Arb.dataConnect.string(2048..99999),
      )

    fun entityArb(
      entityIdFieldName: Arb<String> = stringForEncodeTestingArb(),
      @OptIn(DelicateKotest::class) entityId: Arb<String> = stringForEncodeTestingArb().distinct(),
      structKey: Arb<String> = Arb.proto.structKey(),
      structSize: IntRange,
      structDepth: IntRange,
    ): Arb<EntityArbSample> =
      Arb.pair(entityIdFieldName, entityId).flatMap { (entityIdFieldName, entityId) ->
        val keyArb = structKey.filterNot { it == entityIdFieldName }
        Arb.proto.struct(size = structSize, depth = structDepth, key = keyArb).map { struct ->
          val newStruct =
            struct.struct.toBuilder().putFields(entityIdFieldName, entityId.toValueProto()).build()
          EntityArbSample(
            entityIdFieldName = entityIdFieldName,
            entityId = entityId,
            struct = newStruct,
          )
        }
      }

    fun entityArb(
      entityIdFieldName: Arb<String> = stringForEncodeTestingArb(),
      entityId: Arb<String> = stringForEncodeTestingArb(),
      structKey: Arb<String> = Arb.proto.structKey(),
      structSize: IntRange,
      structDepth: Int,
    ): Arb<EntityArbSample> =
      entityArb(
        entityIdFieldName = entityIdFieldName,
        entityId = entityId,
        structKey = structKey,
        structSize = structSize,
        structDepth = structDepth..structDepth,
      )

    fun Struct.decodingEncodingShouldProduceIdenticalStruct(
      entities: List<Struct> = emptyList(),
      entityIdFieldName: String? = null
    ) {
      val encodeResult = QueryResultEncoder.encode(this, entityIdFieldName)
      withClue("entities returned from QueryResultEncoder.encode()") {
        encodeResult.entities.map { it.data } shouldContainExactlyInAnyOrder entities
      }

      val decodeResult = QueryResultDecoder.decode(encodeResult.byteArray, encodeResult.entities)
      withClue("QueryResultDecoder.decode() return value") { decodeResult shouldBe this }
    }

    fun String.calculateExpectedEncodingAsEntityId(): ByteArray {
      val byteBuffer = ByteBuffer.allocate(length * 2)
      forEach(byteBuffer::putChar)
      val digest = MessageDigest.getInstance("SHA-512")
      byteBuffer.flip()
      digest.update(byteBuffer)
      return digest.digest()
    }

    fun Struct.forEachValue(block: (Value) -> Unit) {
      val values: MutableList<Value> = mutableListOf(this.toValueProto())
      while (values.isNotEmpty()) {
        val value = values.removeFirst()
        block(value)

        if (value.kindCase == Value.KindCase.LIST_VALUE) {
          value.listValue.valuesList.forEach(values::add)
        } else if (value.kindCase == Value.KindCase.STRUCT_VALUE) {
          value.structValue.fieldsMap.entries.forEach { values.add(it.value) }
        }
      }
    }

    fun Struct.keysRecursive(): List<String> {
      val keys = mutableListOf<String>()
      forEachValue { value ->
        if (value.kindCase == Value.KindCase.STRUCT_VALUE) {
          keys.addAll(value.structValue.fieldsMap.keys)
        }
      }
      return keys
    }

    fun Struct.structSizesRecursive(): List<Int> {
      val structSizes = mutableListOf<Int>()
      forEachValue { value ->
        if (value.kindCase == Value.KindCase.STRUCT_VALUE) {
          structSizes.add(value.structValue.fieldsCount)
        }
      }
      return structSizes
    }

    fun Value.subStructs(): List<Value> {
      val subStructs = mutableListOf<Value>()
      val values = mutableListOf(this)
      while (values.isNotEmpty()) {
        val value = values.removeFirst()
        if (value.kindCase == Value.KindCase.STRUCT_VALUE) {
          subStructs.add(value)
          value.structValue.fieldsMap.values.forEach(values::add)
        } else if (value.kindCase == Value.KindCase.LIST_VALUE) {
          value.listValue.valuesList.forEach(values::add)
        }
      }
      return subStructs
    }

    fun Struct.withRandomlyInsertedEntities(
      entities: List<Struct>,
      rs: RandomSource,
      nextKey: () -> String
    ): Struct {
      val valueWrapper = toValueProto()
      val subStructs = valueWrapper.subStructs()
      val subStructByEntityIndex = List(entities.size) { subStructs.random(rs.random) }
      val insertedEntityIndices = mutableSetOf<Int>()

      fun patch(value: Value): Value {
        return if (value.kindCase == Value.KindCase.STRUCT_VALUE) {
          val entityIndices =
            subStructByEntityIndex.indices.filter { subStructByEntityIndex[it] === value }
          entityIndices.forEach {
            check(!insertedEntityIndices.contains(it)) {
              "internal error jstfxyrdsg: entity index $it visited multiple times"
            }
          }

          val builder = value.structValue.toBuilder()

          builder.fieldsMap.entries.toList().forEach { (key, value) ->
            builder.putFields(key, patch(value))
          }

          entityIndices.forEach { entityIndex ->
            insertedEntityIndices.add(entityIndex)
            val entity = entities[entityIndex]
            var key = nextKey()
            while (builder.containsFields(key)) {
              key = nextKey()
            }
            builder.putFields(key, entity.toValueProto())
          }

          builder.build().toValueProto()
        } else if (value.kindCase == Value.KindCase.LIST_VALUE) {
          val builder = ListValue.newBuilder()
          value.listValue.valuesList.forEach { builder.addValues(patch(it)) }
          builder.build().toValueProto()
        } else {
          value
        }
      }

      val patchedStruct = patch(valueWrapper).structValue

      val entityIndicesSorted = entities.indices.sorted()
      val insertedEntityIndicesSorted = insertedEntityIndices.sorted()
      check(entityIndicesSorted == insertedEntityIndicesSorted) {
        "internal error esv76bzer6: not all entities were inserted: " +
          "entityIndices=$entityIndicesSorted, insertedEntityIndices=$insertedEntityIndicesSorted"
      }

      return patchedStruct
    }
  }
}
