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

import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWith1ByteUtf8Encoding
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWith2ByteUtf8Encoding
import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.protobuf.NullValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.list
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
    checkAll(propTestConfig, structWithNullValuesArb()) { struct ->
      struct.decodingEncodingShouldProduceIdenticalStruct()
    }
  }

  @Test
  fun `struct with all number values`() = runTest {
    checkAll(propTestConfig, structWithNumberValuesArb()) { struct ->
      struct.decodingEncodingShouldProduceIdenticalStruct()
    }
  }

  @Test
  fun `struct with all bool values`() = runTest {
    checkAll(propTestConfig, structWithBoolValuesArb()) { struct ->
      struct.decodingEncodingShouldProduceIdenticalStruct()
    }
  }

  @Test
  fun `struct with all string values`() = runTest {
    checkAll(propTestConfig, structWithStringValuesArb()) { struct ->
      struct.decodingEncodingShouldProduceIdenticalStruct()
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for helper functions
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun `structWithNullValuesArb() should produce structs with only number values`() =
    verifyArbProducesStructsWithKindCase(structWithNullValuesArb(), Value.KindCase.NULL_VALUE)

  @Test
  fun `structWithNullValuesArb() should produce non-empty structs`() =
    verifyArbProducesNonEmptyStructs(structWithNullValuesArb())

  @Test
  fun `structWithNumberValuesArb() should produce structs with only number values`() =
    verifyArbProducesStructsWithKindCase(structWithNumberValuesArb(), Value.KindCase.NUMBER_VALUE)

  @Test
  fun `structWithNumberValuesArb() should produce non-empty structs`() =
    verifyArbProducesNonEmptyStructs(structWithNumberValuesArb())

  @Test
  fun `structWithBoolValuesArb() should produce structs with only number values`() =
    verifyArbProducesStructsWithKindCase(structWithBoolValuesArb(), Value.KindCase.BOOL_VALUE)

  @Test
  fun `structWithBoolValuesArb() should produce non-empty structs`() =
    verifyArbProducesNonEmptyStructs(structWithBoolValuesArb())

  @Test
  fun `structWithStringValuesArb() should produce structs with only number values`() =
    verifyArbProducesStructsWithKindCase(structWithStringValuesArb(), Value.KindCase.STRING_VALUE)

  @Test
  fun `structWithStringValuesArb() should produce non-empty structs`() =
    verifyArbProducesNonEmptyStructs(structWithStringValuesArb())

  private fun verifyArbProducesStructsWithKindCase(
    arb: Arb<Struct>,
    expectedKindCase: Value.KindCase
  ) = runTest {
    checkAll(propTestConfig, arb) { struct ->
      struct.fieldsMap.values.forEach { it.kindCase shouldBe expectedKindCase }
    }
  }

  fun verifyArbProducesNonEmptyStructs(arb: Arb<Struct>) = runTest {
    val occurrenceCountBySize = mutableMapOf<Int, Int>()
    checkAll(propTestConfig, arb) { struct ->
      val oldCount = occurrenceCountBySize.getOrDefault(struct.fieldsCount, 0)
      occurrenceCountBySize[struct.fieldsCount] = oldCount + 1
    }
    withClue(
      "occurrenceCountBySize=${occurrenceCountBySize.toSortedMap(Comparator.comparing { it } )}"
    ) {
      val occurrenceCounts = occurrenceCountBySize.keys.sorted() // sorted for better fail messages
      occurrenceCounts shouldNotContain 0
      occurrenceCounts shouldContain 1
      occurrenceCounts shouldContain 2
    }
  }

  private companion object {

    @OptIn(ExperimentalKotest::class)
    val propTestConfig =
      PropTestConfig(
        iterations = 1000,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.33)
      )

    fun stringForEncodeTestingArb(): Arb<String> =
      Arb.choice(
        Arb.constant(""),
        Arb.string(1..20, Arb.codepointWith1ByteUtf8Encoding()),
        Arb.string(1..20, Arb.codepointWith2ByteUtf8Encoding()),
        //        Arb.string(1..20, Arb.codepointWith3ByteUtf8Encoding()),
        //        Arb.string(1..20, Arb.codepointWith4ByteUtf8Encoding()),
        //        Arb.string(1..20, Arb.codepointWithEvenNumByteUtf8EncodingDistribution()),
        //        // TODO: add support for lone surrogates
        //        // Arb.int(1..20).flatMap { Arb.stringWithLoneSurrogates(it) }.map { it.string },
        //        Arb.dataConnect.string(0..20),
        )

    fun Struct.decodingEncodingShouldProduceIdenticalStruct() {
      val encodeResult = QueryResultEncoder.encode(this)
      val decodeResult = QueryResultDecoder.decode(encodeResult.byteArray, encodeResult.entities)
      decodeResult shouldBe this
    }

    fun structWithNullValuesArb(
      keys: Arb<List<String>> = Arb.list(Arb.string(1..10, Codepoint.alphanumeric()), 1..10)
    ): Arb<Struct> =
      keys.map { keys ->
        val builder = Struct.newBuilder()
        keys.forEach { key ->
          builder.putFields(key, Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build())
        }
        builder.build()
      }

    fun structWithNumberValuesArb(
      map: Arb<Map<String, Double>> =
        Arb.map(Arb.string(1..10, Codepoint.alphanumeric()), Arb.double(), 1, 10)
    ): Arb<Struct> =
      map.map { map ->
        val builder = Struct.newBuilder()
        map.entries.forEach { (key, value) ->
          builder.putFields(key, Value.newBuilder().setNumberValue(value).build())
        }
        builder.build()
      }

    fun structWithBoolValuesArb(
      map: Arb<Map<String, Boolean>> =
        Arb.map(Arb.string(1..10, Codepoint.alphanumeric()), Arb.boolean(), 1, 10)
    ): Arb<Struct> =
      map.map { map ->
        val builder = Struct.newBuilder()
        map.entries.forEach { (key, value) ->
          builder.putFields(key, Value.newBuilder().setBoolValue(value).build())
        }
        builder.build()
      }

    fun structWithStringValuesArb(
      map: Arb<Map<String, String>> =
        Arb.map(Arb.string(1..10, Codepoint.alphanumeric()), stringForEncodeTestingArb(), 1, 10)
    ): Arb<Struct> =
      map.map { map ->
        val builder = Struct.newBuilder()
        map.entries.forEach { (key, value) ->
          builder.putFields(key, Value.newBuilder().setStringValue(value).build())
        }
        builder.build()
      }
  }
}
