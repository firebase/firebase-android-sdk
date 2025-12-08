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
import com.google.firebase.dataconnect.testutil.RandomInsertMode
import com.google.firebase.dataconnect.testutil.buildByteArray
import com.google.firebase.dataconnect.testutil.map
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.property.arbitrary.structKey
import com.google.firebase.dataconnect.testutil.property.arbitrary.withIterations
import com.google.firebase.dataconnect.testutil.property.arbitrary.withIterationsIfNotNull
import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.firebase.dataconnect.testutil.withRandomlyInsertedStruct
import com.google.firebase.dataconnect.util.ProtoUtil.toValueProto
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
  fun `single entity`() = runTest {
    checkAll(propTestConfig, EntityTestCase.arb()) { sample ->
      sample.struct.decodingEncodingShouldProduceIdenticalStruct(
        entities = listOf(sample.struct),
        sample.entityIdFieldName
      )
    }
  }

  @Test
  fun `entities nested in struct values`() = runTest {
    checkAll(propTestConfig, Arb.proto.structKey(), Arb.int(1..10)) { entityIdFieldName, entityCount
      ->
      val entityArb = EntityTestCase.arb(entityIdFieldName = Arb.constant(entityIdFieldName))
      val entities = List(entityCount) { entityArb.bind().struct }
      val rootStruct = run {
        val nonEntityIdFieldNameArb = Arb.proto.structKey().filterNot { it == entityIdFieldName }
        entities.fold(Arb.proto.struct(key = nonEntityIdFieldNameArb).bind().struct) {
          rootStruct,
          entity ->
          rootStruct.withRandomlyInsertedStruct(
            entity,
            randomSource().random,
            RandomInsertMode.Struct,
            { nonEntityIdFieldNameArb.bind() }
          )
        }
      }

      rootStruct.decodingEncodingShouldProduceIdenticalStruct(entities, entityIdFieldName)
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
