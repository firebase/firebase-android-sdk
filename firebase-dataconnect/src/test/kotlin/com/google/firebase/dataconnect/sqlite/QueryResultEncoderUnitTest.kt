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

import com.google.firebase.dataconnect.testutil.property.arbitrary.boolValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWith1ByteUtf8Encoding
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWith2ByteUtf8Encoding
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWith3ByteUtf8Encoding
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWith4ByteUtf8Encoding
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWithEvenNumByteUtf8EncodingDistribution
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.kindNotSetValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.listValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.nullValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.numberValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.stringValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.stringWithLoneSurrogates
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.protobuf.Struct
import com.google.protobuf.Value
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
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
  fun `struct with all null values`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct(value = Arb.proto.nullValue())) { struct ->
      struct.struct.decodingEncodingShouldProduceIdenticalStruct()
    }
  }

  @Test
  fun `struct with all number values`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct(value = Arb.proto.numberValue())) { struct ->
      struct.struct.decodingEncodingShouldProduceIdenticalStruct()
    }
  }

  @Test
  fun `struct with all bool values`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct(value = Arb.proto.boolValue())) { struct ->
      struct.struct.decodingEncodingShouldProduceIdenticalStruct()
    }
  }

  @Test
  fun `struct with all string values`() =
    testStructWithStringValues(propTestConfig, stringForEncodeTestingArb())

  @Test
  fun `struct with long string values`() =
    testStructWithStringValues(
      @OptIn(ExperimentalKotest::class) propTestConfig.copy(iterations = 10),
      longStringForEncodeTestingArb()
    )

  private fun testStructWithStringValues(propTestConfig: PropTestConfig, stringArb: Arb<String>) =
    runTest {
      checkAll(propTestConfig, Arb.proto.struct(value = Arb.proto.stringValue(stringArb))) { struct
        ->
        struct.struct.decodingEncodingShouldProduceIdenticalStruct()
      }
    }

  @Test
  fun `struct with all kind_not_set values`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct(value = Arb.proto.kindNotSetValue())) { struct ->
      struct.struct.decodingEncodingShouldProduceIdenticalStruct()
    }
  }

  @Test
  fun `struct with all non-nested list values`() = runTest {
    val listValueArb: Arb<Value> = Arb.proto.listValue(depth = 1..1).map { it.toValueProto() }
    checkAll(propTestConfig, Arb.proto.struct(value = listValueArb)) { struct ->
      struct.struct.decodingEncodingShouldProduceIdenticalStruct()
    }
  }

  @Test
  fun `struct with all nested list values`() = runTest {
    val listValueArb: Arb<Value> =
      Arb.proto.listValue(length = 1..2, depth = 2..4).map { it.toValueProto() }
    checkAll(propTestConfig, Arb.proto.struct(depth = 1..1, value = listValueArb)) { struct ->
      struct.struct.decodingEncodingShouldProduceIdenticalStruct()
    }
  }

  @Test
  fun `struct with all non-nested struct values`() = runTest {
    val structValueArb: Arb<Value> = Arb.proto.struct(depth = 1..1).map { it.toValueProto() }
    checkAll(propTestConfig, Arb.proto.struct(value = structValueArb)) { struct ->
      struct.struct.decodingEncodingShouldProduceIdenticalStruct()
    }
  }

  @Test
  fun `struct with all nested struct values`() = runTest {
    val structValueArb: Arb<Value> =
      Arb.proto.struct(size = 1..2, depth = 2..4).map { it.toValueProto() }
    checkAll(propTestConfig, Arb.proto.struct(depth = 1..1, value = structValueArb)) { struct ->
      struct.struct.decodingEncodingShouldProduceIdenticalStruct()
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // companion object
  //////////////////////////////////////////////////////////////////////////////////////////////////

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

    fun Struct.decodingEncodingShouldProduceIdenticalStruct() {
      val encodeResult = QueryResultEncoder.encode(this)
      val decodeResult = QueryResultDecoder.decode(encodeResult.byteArray, encodeResult.entities)
      decodeResult shouldBe this
    }
  }
}
