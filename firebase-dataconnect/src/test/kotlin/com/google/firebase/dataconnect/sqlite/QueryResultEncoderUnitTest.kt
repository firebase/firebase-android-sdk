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

import com.google.firebase.dataconnect.testutil.RandomSeedTestRule
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWith1ByteUtf8Encoding
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWith2ByteUtf8Encoding
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWith3ByteUtf8Encoding
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWith4ByteUtf8Encoding
import com.google.firebase.dataconnect.testutil.property.arbitrary.codepointWithEvenNumByteUtf8EncodingDistribution
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.stringWithLoneSurrogates
import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.firebase.dataconnect.util.ProtoUtil.toCompactString
import com.google.protobuf.ListValue
import com.google.protobuf.NullValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.RandomSource
import io.kotest.property.Sample
import io.kotest.property.ShrinkingMode
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
import org.junit.Rule
import org.junit.Test

class QueryResultEncoderUnitTest {

  @get:Rule val randomSeedTestRule = RandomSeedTestRule()

  private val rs: RandomSource by randomSeedTestRule.rs

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
  fun `struct with all non-nested list values`() = runTest {
    val listValueArb: Arb<Value> = listValueArb(depth = 1..1).map { it.toValueProto() }
    checkAll(propTestConfig, structArb(value = listValueArb)) { struct ->
      struct.decodingEncodingShouldProduceIdenticalStruct()
    }
  }

  @Test
  fun `struct with all nested list values`() = runTest {
    val listValueArb: Arb<Value> =
      listValueArb(length = 1..2, depth = 2..4).map { it.toValueProto() }
    checkAll(propTestConfig, structArb(size = 1..2, value = listValueArb)) { struct ->
      struct.decodingEncodingShouldProduceIdenticalStruct()
    }
  }

  @Test
  fun `struct with all non-nested struct values`() = runTest {
    val listValueArb: Arb<Value> = structArb(depth = 1..1).map { it.toValueProto() }
    checkAll(propTestConfig, structArb(value = listValueArb)) { struct ->
      struct.decodingEncodingShouldProduceIdenticalStruct()
    }
  }

  @Test
  fun `struct with all nested struct values`() = runTest {
    val listValueArb: Arb<Value> = structArb(size = 1..2, depth = 2..4).map { it.toValueProto() }
    checkAll(propTestConfig, structArb(value = listValueArb)) { struct ->
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
    verifyArbGeneratesValuesWithKindCase(
      listValueArb().map { it.toValueProto() },
      Value.KindCase.LIST_VALUE
    )

  @Test
  fun `listValueArb() should specify the correct depth`() = runTest {
    checkAll(propTestConfig, listValueArb()) { sample ->
      sample.depth shouldBe sample.listValue.maxDepth()
    }
  }

  @Test
  fun `listValueArb() should generate depths up to 3 for normal samples`() = runTest {
    val arb = listValueArb()
    val depths = mutableSetOf<Int>()
    repeat(propTestConfig.iterations!!) { depths.add(arb.sample(rs).value.depth) }
    withClue("depths=${depths.sorted()}") { depths.shouldContainExactlyInAnyOrder(1, 2, 3) }
  }

  @Test
  fun `listValueArb() should generate depths up to 3 for edge cases`() = runTest {
    val arb = listValueArb()
    val depths = mutableSetOf<Int>()
    repeat(propTestConfig.iterations!!) { depths.add(arb.edgecase(rs)!!.depth) }
    withClue("depths=${depths.sorted()}") { depths.shouldContainExactlyInAnyOrder(1, 2, 3) }
  }

  @Test
  fun `structArb() should specify the correct depth`() = runTest {
    checkAll(propTestConfig, structArb()) { sample ->
      sample.depth shouldBe sample.struct.maxDepth()
    }
  }

  @Test
  fun `structArb() should generate depths up to 3 for normal samples`() = runTest {
    val arb = structArb()
    val depths = mutableSetOf<Int>()
    repeat(propTestConfig.iterations!!) { depths.add(arb.sample(rs).value.depth) }
    withClue("depths=${depths.sorted()}") { depths.shouldContainExactlyInAnyOrder(1, 2, 3) }
  }

  @Test
  fun `structArb() should generate depths up to 3 for edge cases`() = runTest {
    val arb = structArb()
    val depths = mutableSetOf<Int>()
    repeat(propTestConfig.iterations!!) { depths.add(arb.edgecase(rs)!!.depth) }
    withClue("depths=${depths.sorted()}") { depths.shouldContainExactlyInAnyOrder(1, 2, 3) }
  }

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
    private val depth: IntRange,
    private val keyArb: Arb<String>,
    private val valueArb: Arb<Value>,
  ) : Arb<StructArb.StructInfo>() {

    init {
      require(size.first >= 0) {
        "size.first must be greater than or equal to zero, but got size=$size"
      }
      require(!size.isEmpty()) { "size.isEmpty() must be false, but got $size" }
      require(depth.first > 0) { "depth.first must be greater than zero, but got depth=$depth" }
      require(!depth.isEmpty()) { "depth.isEmpty() must be false, but got $depth" }
    }

    class StructInfo(
      val struct: Struct,
      val depth: Int,
    ) {
      fun toValueProto(): Value = Value.newBuilder().setStructValue(struct).build()

      override fun toString(): String = struct.toCompactString()
    }

    private val sizeEdgeCases =
      listOf(size.first, size.first + 1, size.last, size.last - 1)
        .distinct()
        .filter { it in size }
        .sorted()

    private val depthEdgeCases = listOf(depth.first, depth.last)

    override fun sample(rs: RandomSource): Sample<StructInfo> {
      val sizeEdgeCaseProbability = rs.random.nextFloat()
      val keyEdgeCaseProbability = rs.random.nextFloat()
      val valueEdgeCaseProbability = rs.random.nextFloat()
      val sample =
        sample(
          rs,
          depth = rs.nextDepth(edgeCaseProbability = rs.random.nextFloat()),
          sizeEdgeCaseProbability = sizeEdgeCaseProbability,
          keyEdgeCaseProbability = keyEdgeCaseProbability,
          valueEdgeCaseProbability = valueEdgeCaseProbability,
          nestedProbability = rs.random.nextFloat(),
        )
      return sample.asSample()
    }

    private fun sample(
      rs: RandomSource,
      depth: Int,
      sizeEdgeCaseProbability: Float,
      keyEdgeCaseProbability: Float,
      valueEdgeCaseProbability: Float,
      nestedProbability: Float,
    ): StructInfo {
      require(depth > 0) { "invalid depth: $depth (must be greater than zero)" }
      val size = rs.nextSize(sizeEdgeCaseProbability)

      fun RandomSource.nextNestedStruct(): Value {
        val struct =
          sample(
            this,
            depth = depth - 1,
            sizeEdgeCaseProbability = sizeEdgeCaseProbability,
            keyEdgeCaseProbability = keyEdgeCaseProbability,
            valueEdgeCaseProbability = valueEdgeCaseProbability,
            nestedProbability = nestedProbability,
          )
        return Value.newBuilder().setStructValue(struct.struct).build()
      }

      val structBuilder = Struct.newBuilder()
      var hasNestedStruct = false
      while (structBuilder.fieldsCount < size) {
        val key = rs.nextKey(keyEdgeCaseProbability)
        if (structBuilder.containsFields(key)) {
          continue
        }
        val value =
          if (depth > 1 && rs.random.nextFloat() < nestedProbability) {
            hasNestedStruct = true
            rs.nextNestedStruct()
          } else {
            rs.nextValue(valueEdgeCaseProbability)
          }
        structBuilder.putFields(key, value)
      }

      if (depth > 1 && !hasNestedStruct) {
        val keyToReplace = structBuilder.fieldsMap.keys.randomOrNull(rs.random)
        val key = keyToReplace ?: rs.nextKey(keyEdgeCaseProbability)
        structBuilder.putFields(key, rs.nextNestedStruct())
      }

      return StructInfo(structBuilder.build(), depth)
    }

    override fun edgecase(rs: RandomSource): StructInfo {
      val edgeCases = rs.nextEdgeCases()
      val sizeEdgeCaseProbability = if (edgeCases.contains(EdgeCase.Size)) 1.0f else 0.0f
      val depthEdgeCaseProbability = if (edgeCases.contains(EdgeCase.Depth)) 1.0f else 0.0f
      val keyEdgeCaseProbability = if (edgeCases.contains(EdgeCase.Keys)) 1.0f else 0.0f
      val valueEdgeCaseProbability = if (edgeCases.contains(EdgeCase.Values)) 1.0f else 0.0f
      val nestedProbability = if (edgeCases.contains(EdgeCase.OnlyNested)) 1.0f else 0.0f
      return sample(
        rs,
        depth = rs.nextDepth(depthEdgeCaseProbability),
        sizeEdgeCaseProbability = sizeEdgeCaseProbability,
        keyEdgeCaseProbability = keyEdgeCaseProbability,
        valueEdgeCaseProbability = valueEdgeCaseProbability,
        nestedProbability = nestedProbability,
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

    private fun RandomSource.nextDepth(edgeCaseProbability: Float): Int {
      require(edgeCaseProbability in 0.0f..1.0f) {
        "invalid edgeCaseProbability: $edgeCaseProbability"
      }
      return if (random.nextFloat() < edgeCaseProbability) {
        depthEdgeCases.random(random)
      } else {
        depth.random(random)
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
      Depth,
      Keys,
      Values,
      OnlyNested,
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
    private val depth: IntRange,
    private val valueArb: Arb<Value>,
  ) : Arb<ListValueArb.ListValueInfo>() {

    class ListValueInfo(
      val listValue: ListValue,
      val depth: Int,
    ) {
      fun toValueProto(): Value = Value.newBuilder().setListValue(listValue).build()

      override fun toString(): String = listValue.toCompactString()
    }

    init {
      require(length.first >= 0) {
        "length.first must be greater than or equal to zero, but got length=$length"
      }
      require(!length.isEmpty()) { "length.isEmpty() must be false, but got $length" }
      require(depth.first > 0) { "depth.first must be greater than zero, but got depth=$depth" }
      require(!depth.isEmpty()) { "depth.isEmpty() must be false, but got $depth" }
    }

    private val lengthEdgeCases =
      listOf(length.first, length.first + 1, length.last, length.last - 1)
        .distinct()
        .filter { it in length }
        .sorted()

    private val depthEdgeCases = listOf(depth.first, depth.last)

    override fun sample(rs: RandomSource): Sample<ListValueInfo> {
      val sample =
        sample(
          rs,
          depth = rs.nextDepth(edgeCaseProbability = rs.random.nextFloat()),
          lengthEdgeCaseProbability = rs.random.nextFloat(),
          valueEdgeCaseProbability = rs.random.nextFloat(),
          nestedProbability = rs.random.nextFloat(),
        )
      return sample.asSample()
    }

    private fun sample(
      rs: RandomSource,
      depth: Int,
      lengthEdgeCaseProbability: Float,
      valueEdgeCaseProbability: Float,
      nestedProbability: Float,
    ): ListValueInfo {
      require(depth > 0) { "invalid depth: $depth (must be greater than zero)" }
      val length = rs.nextLength(lengthEdgeCaseProbability)
      val values = mutableListOf<Value>()

      fun RandomSource.nextNestedListValue(): Value {
        val listValue =
          sample(
            this,
            depth = depth - 1,
            lengthEdgeCaseProbability = lengthEdgeCaseProbability,
            valueEdgeCaseProbability = valueEdgeCaseProbability,
            nestedProbability = nestedProbability,
          )
        return Value.newBuilder().setListValue(listValue.listValue).build()
      }

      var hasNestedListValue = false
      repeat(length) {
        val value =
          if (depth > 1 && rs.random.nextFloat() < nestedProbability) {
            hasNestedListValue = true
            rs.nextNestedListValue()
          } else {
            rs.nextValue(valueEdgeCaseProbability)
          }
        values.add(value)
      }

      if (depth > 1 && !hasNestedListValue) {
        values.removeFirstOrNull()
        values.add(rs.nextNestedListValue())
        values.shuffle(rs.random)
      }

      val listValue = ListValue.newBuilder().addAllValues(values).build()
      return ListValueInfo(listValue, depth)
    }

    override fun edgecase(rs: RandomSource): ListValueInfo {
      val edgeCases = rs.nextEdgeCases()
      val lengthEdgeCaseProbability = if (edgeCases.contains(EdgeCase.Length)) 1.0f else 0.0f
      val depthEdgeCaseProbability = if (edgeCases.contains(EdgeCase.Depth)) 1.0f else 0.0f
      val valueEdgeCaseProbability = if (edgeCases.contains(EdgeCase.Values)) 1.0f else 0.0f
      val nestedProbability = if (edgeCases.contains(EdgeCase.OnlyNested)) 1.0f else 0.0f
      return sample(
        rs,
        depth = rs.nextDepth(depthEdgeCaseProbability),
        lengthEdgeCaseProbability = lengthEdgeCaseProbability,
        valueEdgeCaseProbability = valueEdgeCaseProbability,
        nestedProbability = nestedProbability,
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

    private fun RandomSource.nextDepth(edgeCaseProbability: Float): Int {
      require(edgeCaseProbability in 0.0f..1.0f) {
        "invalid edgeCaseProbability: $edgeCaseProbability"
      }
      return if (random.nextFloat() < edgeCaseProbability) {
        depthEdgeCases.random(random)
      } else {
        depth.random(random)
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
      Depth,
      Values,
      OnlyNested,
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

    fun structKeyArb(): Arb<String> = Arb.string(1..10, Codepoint.alphanumeric())

    fun structArb(
      size: IntRange = 0..5,
      key: Arb<String> = structKeyArb(),
      value: Arb<Value>,
    ): Arb<Struct> =
      StructArb(
          size = size,
          depth = 1..1,
          keyArb = key,
          valueArb = value,
        )
        .map { it.struct }

    fun structArb(
      size: IntRange = 0..5,
      depth: IntRange = 1..3,
      key: Arb<String> = structKeyArb(),
      nullValue: Arb<Value> = nullValueArb(),
      numberValue: Arb<Value> = numberValueArb(),
      boolValue: Arb<Value> = boolValueArb(),
      stringValue: Arb<Value> = stringValueArb(),
      kindNotSetValue: Arb<Value> = kindNotSetValueArb(),
    ): Arb<StructArb.StructInfo> =
      StructArb(
        size = size,
        depth = depth,
        keyArb = key,
        valueArb =
          Arb.choice(
            nullValue,
            numberValue,
            boolValue,
            stringValue,
            kindNotSetValue,
          )
      )

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
      depth: IntRange = 1..3,
      nullValue: Arb<Value> = nullValueArb(),
      numberValue: Arb<Value> = numberValueArb(),
      boolValue: Arb<Value> = boolValueArb(),
      stringValue: Arb<Value> = stringValueArb(),
      kindNotSetValue: Arb<Value> = kindNotSetValueArb(),
    ): Arb<ListValueArb.ListValueInfo> =
      ListValueArb(
        length = length,
        depth = depth,
        valueArb =
          Arb.choice(
            nullValue,
            numberValue,
            boolValue,
            stringValue,
            kindNotSetValue,
          ),
      )

    fun ListValue.maxDepth(): Int {
      var maxDepth = 1
      repeat(valuesCount) {
        val curMaxDepth = getValues(it).maxDepth()
        if (curMaxDepth > maxDepth) {
          maxDepth = curMaxDepth
        }
      }
      return maxDepth
    }

    fun Struct.maxDepth(): Int {
      var maxDepth = 1
      fieldsMap.values.forEach { value ->
        val curMaxDepth = value.maxDepth()
        if (curMaxDepth > maxDepth) {
          maxDepth = curMaxDepth
        }
      }
      return maxDepth
    }

    fun Value.maxDepth(): Int =
      when (kindCase) {
        Value.KindCase.STRUCT_VALUE -> 1 + structValue.maxDepth()
        Value.KindCase.LIST_VALUE -> 1 + listValue.maxDepth()
        else -> 1
      }
  }
}
