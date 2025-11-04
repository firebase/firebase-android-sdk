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
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWith3ByteUtf8Encoding
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWith4ByteUtf8Encoding
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWithEvenNumByteUtf8EncodingDistribution
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.stringWithLoneSurrogates
import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.protobuf.ListValue
import com.google.protobuf.NullValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.RandomSource
import io.kotest.property.Sample
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.asSample
import io.kotest.property.checkAll
import kotlin.random.nextInt
import kotlinx.coroutines.test.runTest
import org.junit.Test

class QueryResultEncoderUnitTest {

  @Test
  fun `empty struct`() {
    Struct.getDefaultInstance().decodingEncodingShouldProduceIdenticalStruct()
  }

  @Test
  fun `struct with all null values`() = runTest {
    checkAll(propTestConfig, structArb(value = nullValueArb())) { struct ->
      struct.decodingEncodingShouldProduceIdenticalStruct()
    }
  }

  @Test
  fun `struct with all number values`() = runTest {
    checkAll(propTestConfig, structArb(value = numberValueArb())) { struct ->
      struct.decodingEncodingShouldProduceIdenticalStruct()
    }
  }

  @Test
  fun `struct with all bool values`() = runTest {
    checkAll(propTestConfig, structArb(value = boolValueArb())) { struct ->
      struct.decodingEncodingShouldProduceIdenticalStruct()
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
      checkAll(propTestConfig, structArb(value = stringValueArb(stringArb))) { struct ->
        struct.decodingEncodingShouldProduceIdenticalStruct()
      }
    }

  @Test
  fun `struct with all kind_not_set values`() = runTest {
    checkAll(propTestConfig, structArb(value = kindNotSetValueArb())) { struct ->
      struct.decodingEncodingShouldProduceIdenticalStruct()
    }
  }

  @Test
  fun `struct with all list values`() = runTest {
    checkAll(propTestConfig, structArb(value = listValueArb())) { struct ->
      struct.decodingEncodingShouldProduceIdenticalStruct()
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for helper functions
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun `nullValueArb() should produce Values with kindCase NULL_VALUE`() =
    verifyArbGeneratesValuesWithKindCase(nullValueArb(), Value.KindCase.NULL_VALUE)

  @Test
  fun `numberValueArb() should produce Values with kindCase NUMBER_VALUE`() =
    verifyArbGeneratesValuesWithKindCase(numberValueArb(), Value.KindCase.NUMBER_VALUE)

  @Test
  fun `boolValueArb() should produce Values with kindCase BOOL_VALUE`() =
    verifyArbGeneratesValuesWithKindCase(boolValueArb(), Value.KindCase.BOOL_VALUE)

  @Test
  fun `stringValueArb() should produce Values with kindCase STRING_VALUE`() =
    verifyArbGeneratesValuesWithKindCase(stringValueArb(), Value.KindCase.STRING_VALUE)

  @Test
  fun `kindNotSetValueArb() should produce Values with kindCase KIND_NOT_SET`() =
    verifyArbGeneratesValuesWithKindCase(kindNotSetValueArb(), Value.KindCase.KIND_NOT_SET)

  @Test
  fun `listValueArb() should produce Values with kindCase LIST_VALUE`() =
    verifyArbGeneratesValuesWithKindCase(listValueArb(), Value.KindCase.LIST_VALUE)

  private fun verifyArbGeneratesValuesWithKindCase(
    arb: Arb<Value>,
    expectedKindCase: Value.KindCase
  ) = runTest {
    checkAll(propTestConfig, arb) { value -> value.kindCase shouldBe expectedKindCase }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // StructArb class
  //////////////////////////////////////////////////////////////////////////////////////////////////

  private class StructArb(
    private val size: IntRange,
    private val keyArb: Arb<String>,
    private val valueArb: Arb<Value>,
  ) : Arb<Struct>() {

    init {
      require(size.first >= 0) {
        "size.first must be greater than or equal to zero, but got size=$size"
      }
      require(!size.isEmpty()) { "size.isEmpty() must be false, but got $size" }
    }

    private val sizeEdgeCases =
      listOf(size.first, size.first + 1, size.last, size.last - 1)
        .distinct()
        .filter { it in size }
        .sorted()

    override fun sample(rs: RandomSource): Sample<Struct> {
      val sizeEdgeCaseProbability = rs.random.nextFloat()
      val keyEdgeCaseProbability = rs.random.nextFloat()
      val valueEdgeCaseProbability = rs.random.nextFloat()
      val sample =
        sample(
          rs,
          sizeEdgeCaseProbability = sizeEdgeCaseProbability,
          keyEdgeCaseProbability = keyEdgeCaseProbability,
          valueEdgeCaseProbability = valueEdgeCaseProbability,
        )
      return sample.asSample()
    }

    private fun sample(
      rs: RandomSource,
      sizeEdgeCaseProbability: Float,
      keyEdgeCaseProbability: Float,
      valueEdgeCaseProbability: Float,
    ): Struct {
      val size = rs.nextSize(sizeEdgeCaseProbability)
      val structBuilder = Struct.newBuilder()
      repeat(size) {
        val key = rs.nextKey(keyEdgeCaseProbability)
        val value = rs.nextValue(valueEdgeCaseProbability)
        structBuilder.putFields(key, value)
      }
      return structBuilder.build()
    }

    override fun edgecase(rs: RandomSource): Struct {
      val edgeCases = rs.nextEdgeCases()
      val sizeEdgeCaseProbability = if (edgeCases.contains(EdgeCase.Size)) 1.0f else 0.0f
      val keyEdgeCaseProbability = if (edgeCases.contains(EdgeCase.Keys)) 1.0f else 0.0f
      val valueEdgeCaseProbability = if (edgeCases.contains(EdgeCase.Values)) 1.0f else 0.0f
      return sample(
        rs,
        sizeEdgeCaseProbability = sizeEdgeCaseProbability,
        keyEdgeCaseProbability = keyEdgeCaseProbability,
        valueEdgeCaseProbability = valueEdgeCaseProbability,
      )
    }

    private fun RandomSource.nextSize(edgeCaseProbability: Float): Int {
      require(edgeCaseProbability in 0.0f..1.0f) {
        "invalid edgeCaseProbability: $edgeCaseProbability"
      }
      return if (random.nextFloat() < edgeCaseProbability) {
        sizeEdgeCases.random(random)
      } else {
        size.random(random)
      }
    }

    private fun RandomSource.nextKey(edgeCaseProbability: Float): String {
      require(edgeCaseProbability in 0.0f..1.0f) {
        "invalid edgeCaseProbability: $edgeCaseProbability"
      }
      return if (random.nextFloat() < edgeCaseProbability) {
        keyArb.edgecase(this)!!
      } else {
        keyArb.sample(this).value
      }
    }

    private fun RandomSource.nextValue(edgeCaseProbability: Float): Value {
      require(edgeCaseProbability in 0.0f..1.0f) {
        "invalid edgeCaseProbability: $edgeCaseProbability"
      }
      return if (random.nextFloat() < edgeCaseProbability) {
        valueArb.edgecase(this)!!
      } else {
        valueArb.sample(this).value
      }
    }

    private enum class EdgeCase {
      Size,
      Keys,
      Values,
    }

    private companion object {
      fun RandomSource.nextEdgeCases(): List<EdgeCase> {
        val edgeCaseCount = random.nextInt(1..EdgeCase.entries.size)
        return EdgeCase.entries.shuffled(random).take(edgeCaseCount)
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // ListValueArb class
  //////////////////////////////////////////////////////////////////////////////////////////////////

  private class ListValueArb(
    private val length: IntRange,
    private val valueArb: Arb<Value>,
  ) : Arb<ListValue>() {

    init {
      require(length.first >= 0) {
        "length.first must be greater than or equal to zero, but got length=$length"
      }
      require(!length.isEmpty()) { "length.isEmpty() must be false, but got $length" }
    }

    private val lengthEdgeCases =
      listOf(length.first, length.first + 1, length.last, length.last - 1)
        .distinct()
        .filter { it in length }
        .sorted()

    override fun sample(rs: RandomSource): Sample<ListValue> {
      val lengthEdgeCaseProbability = rs.random.nextFloat()
      val valueEdgeCaseProbability = rs.random.nextFloat()
      val sample =
        sample(
          rs,
          lengthEdgeCaseProbability = lengthEdgeCaseProbability,
          valueEdgeCaseProbability = valueEdgeCaseProbability,
        )
      return sample.asSample()
    }

    private fun sample(
      rs: RandomSource,
      lengthEdgeCaseProbability: Float,
      valueEdgeCaseProbability: Float,
    ): ListValue {
      val length = rs.nextLength(lengthEdgeCaseProbability)
      val listValueBuilder = ListValue.newBuilder()
      repeat(length) {
        val value = rs.nextValue(valueEdgeCaseProbability)
        listValueBuilder.addValues(value)
      }
      return listValueBuilder.build()
    }

    override fun edgecase(rs: RandomSource): ListValue {
      val edgeCases = rs.nextEdgeCases()
      val lengthEdgeCaseProbability = if (edgeCases.contains(EdgeCase.Length)) 1.0f else 0.0f
      val valueEdgeCaseProbability = if (edgeCases.contains(EdgeCase.Values)) 1.0f else 0.0f
      return sample(
        rs,
        lengthEdgeCaseProbability = lengthEdgeCaseProbability,
        valueEdgeCaseProbability = valueEdgeCaseProbability,
      )
    }

    private fun RandomSource.nextLength(edgeCaseProbability: Float): Int {
      require(edgeCaseProbability in 0.0f..1.0f) {
        "invalid edgeCaseProbability: $edgeCaseProbability"
      }
      return if (random.nextFloat() < edgeCaseProbability) {
        lengthEdgeCases.random(random)
      } else {
        length.random(random)
      }
    }

    private fun RandomSource.nextValue(edgeCaseProbability: Float): Value {
      require(edgeCaseProbability in 0.0f..1.0f) {
        "invalid edgeCaseProbability: $edgeCaseProbability"
      }
      return if (random.nextFloat() < edgeCaseProbability) {
        valueArb.edgecase(this)!!
      } else {
        valueArb.sample(this).value
      }
    }

    private enum class EdgeCase {
      Length,
      Values,
    }

    private companion object {
      fun RandomSource.nextEdgeCases(): List<EdgeCase> {
        val edgeCaseCount = random.nextInt(1..EdgeCase.entries.size)
        return EdgeCase.entries.shuffled(random).take(edgeCaseCount)
      }
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
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.33)
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

    fun structKeyArb(): Arb<String> = Arb.string(1..10, Codepoint.alphanumeric())

    fun structArb(
      size: IntRange = 0..10,
      key: Arb<String> = structKeyArb(),
      value: Arb<Value>,
    ): Arb<Struct> = StructArb(size, key, value)

    fun nullValueArb(): Arb<Value> = arbitrary {
      Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build()
    }

    fun numberValueArb(
      number: Arb<Double> = Arb.double(),
    ): Arb<Value> = number.map { Value.newBuilder().setNumberValue(it).build() }

    fun boolValueArb(
      boolean: Arb<Boolean> = Arb.boolean(),
    ): Arb<Value> = boolean.map { Value.newBuilder().setBoolValue(it).build() }

    fun stringValueArb(
      string: Arb<String> = stringForEncodeTestingArb(),
    ): Arb<Value> = string.map { Value.newBuilder().setStringValue(it).build() }

    fun kindNotSetValueArb(): Arb<Value> = arbitrary { Value.newBuilder().build() }

    fun listValueArb(
      length: IntRange = 0..10,
      nullValueArb: Arb<Value> = nullValueArb(),
      numberValueArb: Arb<Value> = numberValueArb(),
      boolValueArb: Arb<Value> = boolValueArb(),
      stringValueArb: Arb<Value> = stringValueArb(),
      kindNotSetValueArb: Arb<Value> = kindNotSetValueArb(),
    ): Arb<Value> =
      ListValueArb(
          length = length,
          valueArb =
            Arb.choice(
              nullValueArb,
              numberValueArb,
              boolValueArb,
              stringValueArb,
              kindNotSetValueArb
            ),
        )
        .map { Value.newBuilder().setListValue(it).build() }
  }
}
