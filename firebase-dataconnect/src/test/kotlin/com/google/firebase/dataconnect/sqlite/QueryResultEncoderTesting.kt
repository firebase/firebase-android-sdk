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

package com.google.firebase.dataconnect.sqlite

import com.google.firebase.dataconnect.DataConnectPath
import com.google.firebase.dataconnect.testutil.beEqualTo
import com.google.firebase.dataconnect.testutil.structFastEqual
import com.google.firebase.dataconnect.util.ProtoUtil.toCompactString
import com.google.protobuf.Struct
import io.kotest.assertions.withClue
import io.kotest.common.DelicateKotest
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.should
import io.kotest.property.Arb
import io.kotest.property.PropertyContext
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.char
import io.kotest.property.arbitrary.distinct
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.withEdgecases
import java.nio.ByteBuffer
import java.security.MessageDigest

object QueryResultEncoderTesting {

  data class EntityIdSample(val string: String)

  fun entityIdArb(): Arb<EntityIdSample> =
    Arb.string(10..10, Codepoint.alphanumeric()).map(::EntityIdSample)

  fun distinctEntityIdArb(): Arb<EntityIdSample> =
    @OptIn(DelicateKotest::class) entityIdArb().distinct()

  fun charArbWithCodeGreaterThan255(): Arb<Char> {
    val charRange = 256.toChar()..Char.MAX_VALUE
    val charEdgeCases: List<Char> =
      listOf(
          charRange.first,
          charRange.last,
          Char.MIN_VALUE,
          Char.MAX_VALUE,
          Char.MIN_HIGH_SURROGATE,
          Char.MAX_HIGH_SURROGATE,
          Char.MIN_LOW_SURROGATE,
          Char.MAX_LOW_SURROGATE,
        )
        .flatMap { listOf(it, it + 1, it - 1) }
    return Arb.char(charRange).withEdgecases(charEdgeCases.distinct().filter { it in charRange })
  }

  fun Struct.decodingEncodingShouldProduceIdenticalStruct(
    expectedEntities: List<Struct> = emptyList(),
    entityIdByPath: Map<DataConnectPath, String>? = null,
  ) {
    val encodeResult = QueryResultEncoder.encode(this, entityIdByPath)

    withClue("QueryResultEncoder.encode() entities returned") {
      class StructWrapper(val struct: Struct) {
        override fun equals(other: Any?) =
          other is StructWrapper && structFastEqual(struct, other.struct)
        override fun hashCode() = struct.hashCode()
        override fun toString() = struct.toCompactString()
      }

      val actualEntitiesWrapped = encodeResult.entities.map { it.data }.map(::StructWrapper)
      val expectedEntitiesWrapped = expectedEntities.map(::StructWrapper)
      actualEntitiesWrapped shouldContainExactlyInAnyOrder expectedEntitiesWrapped
    }

    val decodeEntities =
      encodeResult.entities.map {
        QueryResultDecoder.Entity(
          encodedId = it.encodedId,
          data = it.data,
        )
      }
    val decodeResult = QueryResultDecoder.decode(encodeResult.byteArray, decodeEntities)

    withClue("QueryResultDecoder.decode() return value") {
      decodeResult should beEqualTo(this, structPrinter = { it.toCompactString() })
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

  interface BuildEntityIdByPathContext {
    fun putWithRandomUniqueEntityId(path: DataConnectPath)
  }

  fun PropertyContext.buildEntityIdByPathMap(
    block: BuildEntityIdByPathContext.() -> Unit
  ): Map<DataConnectPath, String> {
    val distinctEntityIdArb = distinctEntityIdArb()
    val entityIdByPath = mutableMapOf<DataConnectPath, String>()
    val context =
      object : BuildEntityIdByPathContext {
        override fun putWithRandomUniqueEntityId(path: DataConnectPath) {
          entityIdByPath[path] = distinctEntityIdArb.bind().string
        }
      }
    block(context)
    return entityIdByPath.toMap()
  }
}
