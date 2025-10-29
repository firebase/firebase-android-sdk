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

package com.google.firebase.dataconnect.sqlite2

import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.firebase.dataconnect.util.ProtoUtil.buildStructProto
import com.google.protobuf.Struct
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class QueryResultEncoderUnitTest {

  @Test
  fun `empty struct`() {
    Struct.getDefaultInstance().decodingEncodingShouldProduceIdenticalStruct()
  }

  @Test
  fun `struct with all number values`() = runTest {
    checkAll(propTestConfig, structWithNumberValuesArb()) { struct ->
      struct.decodingEncodingShouldProduceIdenticalStruct()
    }
  }

  private companion object {

    @OptIn(ExperimentalKotest::class)
    val propTestConfig =
      PropTestConfig(
        iterations = 1000,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.33)
      )

    fun Struct.decodingEncodingShouldProduceIdenticalStruct() {
      val encodeResult = QueryResultEncoder.encode(this)
      val decodeResult = QueryResultDecoder.decode(encodeResult.byteArray, encodeResult.entities)
      decodeResult shouldBe this
    }

    fun structWithNumberValuesArb(
      map: Arb<Map<String, Double>> =
        Arb.map(Arb.string(1..10, Codepoint.alphanumeric()), Arb.double(), 1, 10)
    ): Arb<Struct> =
      map.map { map -> buildStructProto { map.entries.forEach { put(it.key, it.value) } } }
  }
}
