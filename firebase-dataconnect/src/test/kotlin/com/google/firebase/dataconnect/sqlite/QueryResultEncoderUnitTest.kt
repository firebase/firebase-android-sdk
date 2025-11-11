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

import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWith1ByteUtf8Encoding
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWith2ByteUtf8Encoding
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWith3ByteUtf8Encoding
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWith4ByteUtf8Encoding
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWithEvenNumByteUtf8EncodingDistribution
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.stringValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.stringWithLoneSurrogates
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.property.arbitrary.structKey
import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.firebase.dataconnect.util.ProtoUtil.toValueProto
import com.google.protobuf.Struct
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.pair
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import org.junit.Test

class QueryResultEncoderUnitTest {

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
    checkAll(propTestConfig, Arb.string(), Arb.proto.stringValue()) { entityFieldName, entityId ->
      val struct = Struct.newBuilder().putFields(entityFieldName, entityId).build()
      struct.decodingEncodingShouldProduceIdenticalStruct(
        entities = listOf(struct),
        entityFieldName
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
    checkAll(propTestConfig, Arb.string(), stringForEncodeTestingArb()) { entityFieldName, entityId
      ->
      val struct = Struct.newBuilder().putFields(entityFieldName, entityId.toValueProto()).build()
      struct.decodingEncodingShouldProduceIdenticalStruct(
        entities = listOf(struct),
        entityFieldName
      )
    }
  }

  @Test
  fun `entity ID are long strings`() = runTest {
    checkAll(propTestConfig, Arb.string(), longStringForEncodeTestingArb()) {
      entityFieldName,
      entityId ->
      val struct = Struct.newBuilder().putFields(entityFieldName, entityId.toValueProto()).build()
      struct.decodingEncodingShouldProduceIdenticalStruct(
        entities = listOf(struct),
        entityFieldName
      )
    }
  }

  @Test
  fun `entire struct is a non-nested entity`() = runTest {
    val arb: Arb<Pair<String, Struct>> =
      Arb.pair(Arb.string(), Arb.proto.stringValue()).flatMap { (entityFieldName, entityId) ->
        val keyArb = Arb.proto.structKey().filterNot { it == entityFieldName }
        Arb.proto.struct(size = 1..100, depth = 1, key = keyArb).map { struct ->
          val newStruct = struct.struct.toBuilder().putFields(entityFieldName, entityId).build()
          Pair(entityFieldName, newStruct)
        }
      }
    checkAll(propTestConfig, arb) { (entityFieldName: String, struct: Struct) ->
      struct.decodingEncodingShouldProduceIdenticalStruct(
        entities = listOf(struct),
        entityFieldName
      )
    }
  }

  @Test
  fun `entire struct is a nested entity`() = runTest {
    val arb: Arb<Pair<String, Struct>> =
      Arb.pair(Arb.string(), Arb.proto.stringValue()).flatMap { (entityFieldName, entityId) ->
        val keyArb = Arb.proto.structKey().filterNot { it == entityFieldName }
        Arb.proto.struct(size = 2..3, depth = 2..4, key = keyArb).map { struct ->
          val newStruct = struct.struct.toBuilder().putFields(entityFieldName, entityId).build()
          Pair(entityFieldName, newStruct)
        }
      }
    checkAll(propTestConfig, arb) { (entityFieldName: String, struct: Struct) ->
      struct.decodingEncodingShouldProduceIdenticalStruct(
        entities = listOf(struct),
        entityFieldName
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

    fun Struct.decodingEncodingShouldProduceIdenticalStruct(
      entities: List<Struct> = emptyList(),
      entityFieldName: String? = null
    ) {
      val encodeResult = QueryResultEncoder.encode(this, entityFieldName)
      val decodeResult =
        QueryResultDecoder.decode(encodeResult.byteArray, encodeResult.entities, entityFieldName)

      assertSoftly {
        withClue("QueryResultDecoder.decode() return value") { decodeResult shouldBe this }
        withClue("entities returned from QueryResultEncoder.encode()") {
          encodeResult.entities.map { it.data } shouldContainExactlyInAnyOrder entities
        }
      }
    }

    fun String.calculateExpectedEncodingAsEntityId(): ByteArray {
      val byteBuffer = ByteBuffer.allocate(length * 2)
      forEach(byteBuffer::putChar)
      val digest = MessageDigest.getInstance("SHA-512")
      byteBuffer.flip()
      digest.update(byteBuffer)
      return digest.digest()
    }
  }
}
