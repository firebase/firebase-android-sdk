/*
 * Copyright 2024 Google LLC
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

@file:Suppress("UnusedReceiverParameter")

package com.google.firebase.dataconnect.testutil.property.arbitrary

import com.google.firebase.dataconnect.testutil.toValueProto
import com.google.protobuf.ListValue
import com.google.protobuf.NullValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.Sample
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.withEdgecases
import io.kotest.property.asSample
import kotlin.random.nextInt

object Proto {

  data class StructInfo(
    val struct: Struct,
    val depth: Int,
    val descendantValues: List<Value>,
  ) {
    fun toValueProto(): Value = struct.toValueProto()
  }

  data class ListValueInfo(
    val listValue: ListValue,
    val depth: Int,
    val descendantValues: List<Value>,
  ) {
    fun toValueProto(): Value = listValue.toValueProto()
  }
}

val Arb.Companion.proto: Proto
  get() = Proto

//////////////////////////////////////////////////////////////////////////////////////////////////
// Arb.Companion.proto extension functions
//////////////////////////////////////////////////////////////////////////////////////////////////

fun Proto.valueOfKind(kindCase: Value.KindCase): Arb<Value> =
  when (kindCase) {
    Value.KindCase.KIND_NOT_SET -> kindNotSetValue()
    Value.KindCase.NULL_VALUE -> nullValue()
    Value.KindCase.NUMBER_VALUE -> numberValue()
    Value.KindCase.STRING_VALUE -> stringValue()
    Value.KindCase.BOOL_VALUE -> boolValue()
    Value.KindCase.STRUCT_VALUE -> struct().map { it.toValueProto() }
    Value.KindCase.LIST_VALUE -> listValue().map { it.toValueProto() }
  }

fun Proto.value(): Arb<Value> =
  Arb.choice(
    kindNotSetValue(),
    nullValue(),
    numberValue(),
    stringValue(),
    boolValue(),
    struct().map { it.toValueProto() },
    listValue().map { it.toValueProto() },
  )

fun Proto.nullValue(): Arb<Value> = arbitrary {
  Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build()
}

fun Proto.numberValue(
  number: Arb<Double> = Arb.double(),
): Arb<Value> = number.map { Value.newBuilder().setNumberValue(it).build() }

fun Proto.boolValue(
  boolean: Arb<Boolean> = Arb.boolean(),
): Arb<Value> = boolean.map { Value.newBuilder().setBoolValue(it).build() }

fun Proto.stringValue(
  string: Arb<String> = Arb.dataConnect.string(),
): Arb<Value> = string.map { Value.newBuilder().setStringValue(it).build() }

fun Proto.kindNotSetValue(): Arb<Value> = arbitrary { Value.newBuilder().build() }

fun Proto.scalarValue(exclude: Value.KindCase? = null): Arb<Value> {
  val arbs = buildList {
    if (exclude != Value.KindCase.NULL_VALUE) {
      add(nullValue())
    }
    if (exclude != Value.KindCase.NUMBER_VALUE) {
      add(numberValue())
    }
    if (exclude != Value.KindCase.BOOL_VALUE) {
      add(boolValue())
    }
    if (exclude != Value.KindCase.STRING_VALUE) {
      add(stringValue())
    }
    if (exclude != Value.KindCase.KIND_NOT_SET) {
      add(kindNotSetValue())
    }
  }

  return Arb.choice(arbs)
}

fun Proto.listValue(
  length: IntRange = 0..10,
  depth: IntRange = 1..3,
  scalarValue: Arb<Value> = scalarValue(),
): Arb<Proto.ListValueInfo> =
  ListValueArb(
    length = length,
    depth = depth,
    scalarValueArb = scalarValue,
  )

fun Proto.structKey(): Arb<String> = Arb.string(1..10, Codepoint.alphanumeric())

fun Proto.struct(
  size: IntRange = 0..5,
  depth: IntRange = 1..3,
  key: Arb<String> = structKey(),
  scalarValue: Arb<Value> = scalarValue(),
): Arb<Proto.StructInfo> =
  StructArb(
    size = size,
    depth = depth,
    keyArb = key,
    scalarValueArb = scalarValue,
  )

fun Proto.struct(
  size: Int,
  depth: IntRange = 1..3,
  key: Arb<String> = structKey(),
  scalarValue: Arb<Value> = scalarValue(),
): Arb<Proto.StructInfo> =
  StructArb(
    size = size..size,
    depth = depth,
    keyArb = key,
    scalarValueArb = scalarValue,
  )

fun Proto.struct(
  size: IntRange = 0..5,
  depth: Int,
  key: Arb<String> = structKey(),
  scalarValue: Arb<Value> = scalarValue(),
): Arb<Proto.StructInfo> =
  StructArb(
    size = size,
    depth = depth..depth,
    keyArb = key,
    scalarValueArb = scalarValue,
  )

fun Proto.struct(
  size: Int,
  depth: Int,
  key: Arb<String> = structKey(),
  scalarValue: Arb<Value> = scalarValue(),
): Arb<Proto.StructInfo> =
  StructArb(
    size = size..size,
    depth = depth..depth,
    keyArb = key,
    scalarValueArb = scalarValue,
  )

//////////////////////////////////////////////////////////////////////////////////////////////////
// StructArb class
//////////////////////////////////////////////////////////////////////////////////////////////////

private class StructArb(
  size: IntRange,
  depth: IntRange,
  private val keyArb: Arb<String>,
  private val scalarValueArb: Arb<Value>,
  listValueArb: ListValueArb? = null
) : Arb<Proto.StructInfo>() {

  init {
    require(size.first >= 0) {
      "size.first must be greater than or equal to zero, but got size=$size"
    }
    require(!size.isEmpty()) { "size.isEmpty() must be false, but got $size" }
    require(depth.first > 0) { "depth.first must be greater than zero, but got depth=$depth" }
    require(!depth.isEmpty()) { "depth.isEmpty() must be false, but got $depth" }
    require(depth.last == 1 || size.last > 0) {
      "depth.last==${depth.last} and size.last=${size.last}, but this is an impossible " +
        "combination because the struct size must be at least 1 in order to produce a depth " +
        "greater than 1"
    }
  }

  private val listValueArb =
    listValueArb
      ?: ListValueArb(
        length = size,
        depth = depth,
        scalarValueArb = scalarValueArb,
        structArb = this,
      )

  private val sizeArb: Arb<Int> = run {
    val edgeCases = listOf(size.first, size.first + 1, size.last, size.last - 1)
    Arb.int(size).withEdgecases(edgeCases.distinct().filter { it in size })
  }

  private val nonZeroSizeArb: Arb<Int> = run {
    val first = size.first.coerceAtLeast(1)
    val last = size.last.coerceAtLeast(1)
    val edgeCases = listOf(first, first + 1, last, last - 1)
    val nonZeroSizeRange = first..last
    Arb.int(nonZeroSizeRange).withEdgecases(edgeCases.distinct().filter { it in nonZeroSizeRange })
  }

  private val depthArb: Arb<Int> = run {
    val edgeCases = listOf(depth.first, depth.last).distinct()
    Arb.int(depth).withEdgecases(edgeCases)
  }

  override fun sample(rs: RandomSource): Sample<Proto.StructInfo> {
    val sizeEdgeCaseProbability = rs.random.nextFloat()
    val keyEdgeCaseProbability = rs.random.nextFloat()
    val valueEdgeCaseProbability = rs.random.nextFloat()
    val sample =
      sample(
        rs,
        depth = depthArb.next(rs, edgeCaseProbability = rs.random.nextFloat()),
        sizeEdgeCaseProbability = sizeEdgeCaseProbability,
        keyEdgeCaseProbability = keyEdgeCaseProbability,
        valueEdgeCaseProbability = valueEdgeCaseProbability,
        nestedProbability = rs.random.nextFloat(),
      )
    return sample.asSample()
  }

  fun sample(
    rs: RandomSource,
    depth: Int,
    sizeEdgeCaseProbability: Float,
    keyEdgeCaseProbability: Float,
    valueEdgeCaseProbability: Float,
    nestedProbability: Float,
  ): Proto.StructInfo {
    require(depth > 0) { "invalid depth: $depth (must be greater than zero)" }

    val size = run {
      val arb = if (depth > 1) nonZeroSizeArb else sizeArb
      arb.next(rs, sizeEdgeCaseProbability)
    }
    val forcedDepthIndex = if (size == 0 || depth <= 1) -1 else rs.random.nextInt(size)

    fun RandomSource.nextNestedValue(depth: Int) =
      rs.nextNestedValue(
        depth = depth,
        sizeEdgeCaseProbability = sizeEdgeCaseProbability,
        keyEdgeCaseProbability = keyEdgeCaseProbability,
        valueEdgeCaseProbability = valueEdgeCaseProbability,
        nestedProbability = nestedProbability,
      )

    val descendantValues = mutableListOf<Value>()
    fun NextNestedValueResult.extractValue(): Value {
      descendantValues.addAll(this.descendantValues)
      return value
    }

    val structBuilder = Struct.newBuilder()
    while (structBuilder.fieldsCount < size) {
      val index = structBuilder.fieldsCount
      val key = keyArb.next(rs, keyEdgeCaseProbability)
      if (structBuilder.containsFields(key)) {
        continue
      }

      val value =
        if (depth > 1 && index == forcedDepthIndex) {
          rs.nextNestedValue(depth - 1).extractValue()
        } else if (depth > 1 && rs.random.nextFloat() < nestedProbability) {
          rs.nextNestedValue(rs.random.nextInt(1 until depth)).extractValue()
        } else {
          scalarValueArb.next(rs, valueEdgeCaseProbability)
        }

      descendantValues.add(value)
      structBuilder.putFields(key, value)
    }

    return Proto.StructInfo(structBuilder.build(), depth, descendantValues.toList())
  }

  override fun edgecase(rs: RandomSource): Proto.StructInfo {
    val edgeCases = rs.nextEdgeCases()
    val sizeEdgeCaseProbability = if (edgeCases.contains(EdgeCase.Size)) 1.0f else 0.0f
    val depthEdgeCaseProbability = if (edgeCases.contains(EdgeCase.Depth)) 1.0f else 0.0f
    val keyEdgeCaseProbability = if (edgeCases.contains(EdgeCase.Keys)) 1.0f else 0.0f
    val valueEdgeCaseProbability = if (edgeCases.contains(EdgeCase.Values)) 1.0f else 0.0f
    val nestedProbability = if (edgeCases.contains(EdgeCase.OnlyNested)) 1.0f else 0.0f
    return sample(
      rs,
      depth = depthArb.next(rs, depthEdgeCaseProbability),
      sizeEdgeCaseProbability = sizeEdgeCaseProbability,
      keyEdgeCaseProbability = keyEdgeCaseProbability,
      valueEdgeCaseProbability = valueEdgeCaseProbability,
      nestedProbability = nestedProbability,
    )
  }

  private class NextNestedValueResult(
    val value: Value,
    val descendantValues: List<Value>,
  )

  private fun RandomSource.nextNestedValue(
    depth: Int,
    sizeEdgeCaseProbability: Float,
    keyEdgeCaseProbability: Float,
    valueEdgeCaseProbability: Float,
    nestedProbability: Float,
  ): NextNestedValueResult =
    if (random.nextBoolean()) {
      val sample =
        sample(
          this,
          depth = depth,
          sizeEdgeCaseProbability = sizeEdgeCaseProbability,
          keyEdgeCaseProbability = keyEdgeCaseProbability,
          valueEdgeCaseProbability = valueEdgeCaseProbability,
          nestedProbability = nestedProbability,
        )
      NextNestedValueResult(sample.struct.toValueProto(), sample.descendantValues)
    } else {
      val sample =
        listValueArb.sample(
          this,
          depth = depth,
          lengthEdgeCaseProbability = sizeEdgeCaseProbability,
          valueEdgeCaseProbability = valueEdgeCaseProbability,
          nestedProbability = nestedProbability,
        )
      NextNestedValueResult(sample.listValue.toValueProto(), sample.descendantValues)
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
  length: IntRange,
  depth: IntRange,
  private val scalarValueArb: Arb<Value>,
  structArb: StructArb? = null,
) : Arb<Proto.ListValueInfo>() {

  init {
    require(length.first >= 0) {
      "length.first must be greater than or equal to zero, but got length=$length"
    }
    require(!length.isEmpty()) { "length.isEmpty() must be false, but got $length" }
    require(depth.first > 0) { "depth.first must be greater than zero, but got depth=$depth" }
    require(!depth.isEmpty()) { "depth.isEmpty() must be false, but got $depth" }
    require(depth.last == 1 || length.last > 0) {
      "depth.last==${depth.last} and length.last=${length.last}, but this is an impossible " +
        "combination because the list length must be at least 1 in order to produce a depth " +
        "greater than 1"
    }
  }

  private val structArb =
    structArb
      ?: StructArb(
        size = length,
        depth = depth,
        keyArb = Arb.proto.structKey(),
        scalarValueArb = scalarValueArb,
        listValueArb = this,
      )

  private val lengthArb: Arb<Int> = run {
    val edgeCases = listOf(length.first, length.first + 1, length.last, length.last - 1)
    Arb.int(length).withEdgecases(edgeCases.distinct().filter { it in length })
  }

  private val nonZeroLengthArb: Arb<Int> = run {
    val first = length.first.coerceAtLeast(1)
    val last = length.last.coerceAtLeast(1)
    val edgeCases = listOf(first, first + 1, last, last - 1)
    val nonZeroLengthRange = first..last
    Arb.int(nonZeroLengthRange)
      .withEdgecases(edgeCases.distinct().filter { it in nonZeroLengthRange })
  }

  private val depthArb: Arb<Int> = run {
    val edgeCases = listOf(depth.first, depth.last).distinct()
    Arb.int(depth).withEdgecases(edgeCases)
  }

  override fun sample(rs: RandomSource): Sample<Proto.ListValueInfo> {
    val sample =
      sample(
        rs,
        depth = depthArb.next(rs, edgeCaseProbability = rs.random.nextFloat()),
        lengthEdgeCaseProbability = rs.random.nextFloat(),
        valueEdgeCaseProbability = rs.random.nextFloat(),
        nestedProbability = rs.random.nextFloat(),
      )
    return sample.asSample()
  }

  fun sample(
    rs: RandomSource,
    depth: Int,
    lengthEdgeCaseProbability: Float,
    valueEdgeCaseProbability: Float,
    nestedProbability: Float,
  ): Proto.ListValueInfo {
    require(depth > 0) { "invalid depth: $depth (must be greater than zero)" }

    val length = run {
      val arb = if (depth > 1) nonZeroLengthArb else lengthArb
      arb.next(rs, lengthEdgeCaseProbability)
    }
    val forcedDepthIndex = if (length == 0 || depth <= 1) -1 else rs.random.nextInt(length)

    val values = mutableListOf<Value>()

    fun RandomSource.nextNestedValue(depth: Int) =
      nextNestedValue(
        depth = depth,
        lengthEdgeCaseProbability = lengthEdgeCaseProbability,
        valueEdgeCaseProbability = valueEdgeCaseProbability,
        nestedProbability = nestedProbability,
      )

    val descendantValues = mutableListOf<Value>()
    fun NextNestedValueResult.extractValue(): Value {
      descendantValues.addAll(this.descendantValues)
      return value
    }

    repeat(length) { index ->
      val value =
        if (depth > 1 && index == forcedDepthIndex) {
          rs.nextNestedValue(depth - 1).extractValue()
        } else if (depth > 1 && rs.random.nextFloat() < nestedProbability) {
          rs.nextNestedValue(rs.random.nextInt(1 until depth)).extractValue()
        } else {
          scalarValueArb.next(rs, valueEdgeCaseProbability)
        }

      descendantValues.add(value)
      values.add(value)
    }

    val listValue = ListValue.newBuilder().addAllValues(values).build()
    return Proto.ListValueInfo(listValue, depth, descendantValues.toList())
  }

  override fun edgecase(rs: RandomSource): Proto.ListValueInfo {
    val edgeCases = rs.nextEdgeCases()
    val lengthEdgeCaseProbability = if (edgeCases.contains(EdgeCase.Length)) 1.0f else 0.0f
    val depthEdgeCaseProbability = if (edgeCases.contains(EdgeCase.Depth)) 1.0f else 0.0f
    val valueEdgeCaseProbability = if (edgeCases.contains(EdgeCase.Values)) 1.0f else 0.0f
    val nestedProbability = if (edgeCases.contains(EdgeCase.OnlyNested)) 1.0f else 0.0f
    return sample(
      rs,
      depth = depthArb.next(rs, depthEdgeCaseProbability),
      lengthEdgeCaseProbability = lengthEdgeCaseProbability,
      valueEdgeCaseProbability = valueEdgeCaseProbability,
      nestedProbability = nestedProbability,
    )
  }

  private class NextNestedValueResult(
    val value: Value,
    val descendantValues: List<Value>,
  )

  private fun RandomSource.nextNestedValue(
    depth: Int,
    lengthEdgeCaseProbability: Float,
    valueEdgeCaseProbability: Float,
    nestedProbability: Float,
  ): NextNestedValueResult =
    if (random.nextBoolean()) {
      val sample =
        sample(
          this,
          depth = depth,
          lengthEdgeCaseProbability = lengthEdgeCaseProbability,
          valueEdgeCaseProbability = valueEdgeCaseProbability,
          nestedProbability = nestedProbability,
        )
      NextNestedValueResult(sample.listValue.toValueProto(), sample.descendantValues)
    } else {
      val sample =
        structArb.sample(
          this,
          depth = depth,
          sizeEdgeCaseProbability = lengthEdgeCaseProbability,
          keyEdgeCaseProbability = 0.33f,
          valueEdgeCaseProbability = valueEdgeCaseProbability,
          nestedProbability = nestedProbability,
        )
      NextNestedValueResult(sample.struct.toValueProto(), sample.descendantValues)
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
