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

import com.google.firebase.dataconnect.DataConnectPathSegment
import com.google.firebase.dataconnect.testutil.DataConnectPath
import com.google.firebase.dataconnect.testutil.DataConnectPathValuePair
import com.google.firebase.dataconnect.testutil.toValueProto
import com.google.firebase.dataconnect.testutil.withAddedPathSegment
import com.google.protobuf.ListValue
import com.google.protobuf.NullValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import io.kotest.property.Arb
import io.kotest.property.Exhaustive
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.asSample
import io.kotest.property.exhaustive.constant
import io.kotest.property.exhaustive.of
import kotlin.random.nextInt

object ProtoArb {

  data class StructInfo(
    val struct: Struct,
    val depth: Int,
    val descendants: List<DataConnectPathValuePair>,
  ) {
    fun toValueProto(): Value = struct.toValueProto()
  }

  data class ListValueInfo(
    val listValue: ListValue,
    val depth: Int,
    val descendants: List<DataConnectPathValuePair>,
  ) {
    fun toValueProto(): Value = listValue.toValueProto()
  }
}

val Arb.Companion.proto: ProtoArb
  get() = ProtoArb

object ProtoExhaustive

val Exhaustive.Companion.proto: ProtoExhaustive
  get() = ProtoExhaustive

fun ProtoArb.valueOfKind(kindCase: Value.KindCase): Arb<Value> =
  when (kindCase) {
    Value.KindCase.KIND_NOT_SET -> kindNotSetValue()
    Value.KindCase.NULL_VALUE -> nullValue()
    Value.KindCase.NUMBER_VALUE -> numberValue()
    Value.KindCase.STRING_VALUE -> stringValue()
    Value.KindCase.BOOL_VALUE -> boolValue()
    Value.KindCase.STRUCT_VALUE -> struct().map { it.toValueProto() }
    Value.KindCase.LIST_VALUE -> listValue().map { it.toValueProto() }
  }

fun ProtoArb.value(
  exclude: Value.KindCase? = null,
  structKey: Arb<String> = structKey()
): Arb<Value> {
  val arbs = buildList {
    if (exclude != Value.KindCase.KIND_NOT_SET) {
      add(kindNotSetValue())
    }
    if (exclude != Value.KindCase.NULL_VALUE) {
      add(nullValue())
    }
    if (exclude != Value.KindCase.NUMBER_VALUE) {
      add(numberValue())
    }
    if (exclude != Value.KindCase.STRING_VALUE) {
      add(stringValue())
    }
    if (exclude != Value.KindCase.BOOL_VALUE) {
      add(boolValue())
    }
    if (exclude != Value.KindCase.STRUCT_VALUE) {
      add(struct(key = structKey).map { it.toValueProto() })
    }
    if (exclude != Value.KindCase.LIST_VALUE) {
      add(listValue(structKey = structKey).map { it.toValueProto() })
    }
  }
  return Arb.choice(arbs)
}

fun ProtoExhaustive.nullValue(): Exhaustive<Value> =
  Exhaustive.constant(Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build())

fun ProtoArb.nullValue(): Arb<Value> = arbitrary {
  Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build()
}

fun ProtoArb.numberValue(
  number: Arb<Double> = Arb.double(),
  filter: ((Double) -> Boolean)? = null,
): Arb<Value> = number.filter { filter === null || filter(it) }.map { it.toValueProto() }

fun ProtoArb.boolValue(
  boolean: Arb<Boolean> = Arb.boolean(),
): Arb<Value> = boolean.map { it.toValueProto() }

fun ProtoExhaustive.boolValue(): Exhaustive<Value> =
  Exhaustive.of(true.toValueProto(), false.toValueProto())

fun ProtoArb.stringValue(
  string: Arb<String> = Arb.dataConnect.string(),
  filter: ((String) -> Boolean)? = null,
): Arb<Value> = string.filter { filter === null || filter(it) }.map { it.toValueProto() }

fun ProtoArb.kindNotSetValue(): Arb<Value> = arbitrary { Value.newBuilder().build() }

fun ProtoExhaustive.kindNotSetValue(): Exhaustive<Value> =
  Exhaustive.constant(Value.newBuilder().build())

fun ProtoArb.scalarValue(exclude: Value.KindCase? = null): Arb<Value> {
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

fun ProtoArb.recursivelyEmptyListValue(
  size: IntRange = 0..5,
  depth: IntRange = 1..3
): Arb<RecursivelyEmptyListValueArb.Sample> =
  RecursivelyEmptyListValueArb(size = size, depth = depth)

class RecursivelyEmptyListValueArb(size: IntRange, depth: IntRange) :
  Arb<RecursivelyEmptyListValueArb.Sample>() {

  init {
    require(!size.isEmpty()) { "size must not be an empty range, but got: $size" }
    require(size.first >= 0) {
      "size.first must be greater than or equal to zero, but got ${size.first} (size=$size)"
    }
    require(size.last >= 0) {
      "size.last must be greater than or equal to zero, but got ${size.last} (size=$size)"
    }
    require(!depth.isEmpty()) { "depth must not be an empty range, but got: $depth" }
    require(depth.first > 0) {
      "depth.first must be greater than zero, but got ${depth.first} (depth=$depth)"
    }
    require(depth.last > 0) {
      "depth.last must be greater than zero, but got ${depth.last} (depth=$depth)"
    }
  }

  private val sizeArb: Arb<Int> = Arb.int(size)
  private val depthArb: Arb<Int> = Arb.int(depth)

  data class Sample(val listValue: ListValue, val edgeCases: Set<EdgeCase>) {
    enum class EdgeCase {
      Size,
      Depth,
      MaxDepth,
    }
  }

  override fun sample(rs: RandomSource) =
    sample(
        rs,
        sizeEdgeCaseProbability = rs.random.nextFloat(),
        depthEdgeCaseProbability = rs.random.nextFloat(),
        maxDepthProbability = rs.random.nextFloat(),
      )
      .let { listValue -> Sample(listValue, emptySet()).asSample() }

  fun sample(
    rs: RandomSource,
    sizeEdgeCaseProbability: Float,
    depthEdgeCaseProbability: Float,
    maxDepthProbability: Float
  ): ListValue =
    sample(
      rs,
      depth = depthArb.next(rs, depthEdgeCaseProbability),
      sizeEdgeCaseProbability = sizeEdgeCaseProbability,
      maxDepthProbability = maxDepthProbability
    )

  fun sample(
    rs: RandomSource,
    depth: Int,
    sizeEdgeCaseProbability: Float,
    maxDepthProbability: Float
  ): ListValue {
    require(depth > 0) { "invalid depth: $depth (must be greater than zero)" }
    val size = sizeArb.next(rs, sizeEdgeCaseProbability)
    check(size >= 0)

    if (size == 0 || depth == 1) {
      return ListValue.getDefaultInstance()
    }

    val maxDepthIndex = rs.random.nextInt(size)
    val listValueBuilder = ListValue.newBuilder()

    repeat(size) { index ->
      val childDepth =
        if (index == maxDepthIndex || rs.random.nextFloat() <= maxDepthProbability) {
          depth - 1
        } else {
          rs.random.nextInt(1 until depth)
        }

      val childListValue: ListValue =
        sample(
          rs,
          depth = childDepth,
          sizeEdgeCaseProbability = sizeEdgeCaseProbability,
          maxDepthProbability = maxDepthProbability,
        )

      listValueBuilder.addValues(childListValue.toValueProto())
    }

    return listValueBuilder.build()
  }

  override fun edgecase(rs: RandomSource): Sample {
    val edgeCases: Set<Sample.EdgeCase> = run {
      val allEdgeCases = Sample.EdgeCase.entries
      val edgeCaseCount = rs.random.nextInt(1..allEdgeCases.size)
      allEdgeCases.shuffled(rs.random).take(edgeCaseCount).toSet()
    }
    val listValue =
      sample(
        rs,
        sizeEdgeCaseProbability = if (edgeCases.contains(Sample.EdgeCase.Size)) 1.0f else 0.0f,
        depthEdgeCaseProbability = if (edgeCases.contains(Sample.EdgeCase.Depth)) 1.0f else 0.0f,
        maxDepthProbability = if (edgeCases.contains(Sample.EdgeCase.MaxDepth)) 1.0f else 0.0f,
      )
    return Sample(listValue, edgeCases)
  }
}

fun ProtoArb.listValue(
  size: IntRange = 0..10,
  depth: IntRange = 1..3,
  structKey: Arb<String> = structKey(),
  scalarValue: Arb<Value> = scalarValue(),
  structSize: IntRange = 0..10,
): Arb<ProtoArb.ListValueInfo> =
  ListValueArb(
    sizeRange = size,
    depthRange = depth,
    structKeyArb = structKey,
    scalarValueArb = scalarValue,
    structSizeRange = structSize,
  )

fun ProtoArb.structKey(): Arb<String> = Arb.string(1..10, Codepoint.alphanumeric())

fun ProtoArb.struct(
  size: IntRange = 0..5,
  depth: IntRange = 1..3,
  key: Arb<String> = structKey(),
  scalarValue: Arb<Value> = scalarValue(),
  listSize: IntRange = 0..10,
): Arb<ProtoArb.StructInfo> =
  StructArb(
    sizeRange = size,
    depthRange = depth,
    structKeyArb = key,
    scalarValueArb = scalarValue,
    listSizeRange = listSize,
  )

fun ProtoArb.struct(
  size: Int,
  depth: IntRange = 1..3,
  key: Arb<String> = structKey(),
  scalarValue: Arb<Value> = scalarValue(),
  listSize: IntRange = 0..10,
): Arb<ProtoArb.StructInfo> =
  StructArb(
    sizeRange = size..size,
    depthRange = depth,
    structKeyArb = key,
    scalarValueArb = scalarValue,
    listSizeRange = listSize,
  )

fun ProtoArb.struct(
  size: IntRange = 0..5,
  depth: Int,
  key: Arb<String> = structKey(),
  scalarValue: Arb<Value> = scalarValue(),
  listSize: IntRange = 0..10,
): Arb<ProtoArb.StructInfo> =
  StructArb(
    sizeRange = size,
    depthRange = depth..depth,
    structKeyArb = key,
    scalarValueArb = scalarValue,
    listSizeRange = listSize,
  )

fun ProtoArb.struct(
  size: Int,
  depth: Int,
  key: Arb<String> = structKey(),
  scalarValue: Arb<Value> = scalarValue(),
  listSize: IntRange = 0..10,
): Arb<ProtoArb.StructInfo> =
  StructArb(
    sizeRange = size..size,
    depthRange = depth..depth,
    structKeyArb = key,
    scalarValueArb = scalarValue,
    listSizeRange = listSize,
  )

//////////////////////////////////////////////////////////////////////////////////////////////////
// CompositeValueArb class
//////////////////////////////////////////////////////////////////////////////////////////////////

private typealias GenerateCompositeValueFunc<V> =
  (
    rs: RandomSource,
    path: DataConnectPath,
    depth: Int,
    descendants: MutableList<DataConnectPathValuePair>,
    structSizeEdgeCaseProbability: Float,
    listSizeEdgeCaseProbability: Float,
    structKeyEdgeCaseProbability: Float,
    scalarValueEdgeCaseProbability: Float,
    nestedProbability: Float,
  ) -> V

private abstract class CompositeValueArb<V, I>(
  depthRange: IntRange,
  structSizeRange: IntRange,
  listSizeRange: IntRange,
  structKeyArb: Arb<String>,
  scalarValueArb: Arb<Value>,
) : Arb<I>() {
  private val depthArb = Arb.int(depthRange)

  private val generator =
    StructOrListValueGenerator(
      scalarValue = scalarValueArb,
      structKey = structKeyArb,
      structSize = structSizeRange,
      listSize = listSizeRange,
    )

  private val generateCompositeValueFunc = getGenerateValueFunc(generator)

  override fun sample(rs: RandomSource) =
    generate(
        rs,
        depthEdgeCaseProbability = rs.random.nextFloat(),
        structSizeEdgeCaseProbability = rs.random.nextFloat(),
        listSizeEdgeCaseProbability = rs.random.nextFloat(),
        structKeyEdgeCaseProbability = rs.random.nextFloat(),
        scalarValueEdgeCaseProbability = rs.random.nextFloat(),
        nestedProbability = rs.random.nextFloat(),
      )
      .asSample()

  override fun edgecase(rs: RandomSource): I {
    val edgeCases: Set<EdgeCase> = run {
      val edgeCaseCount = rs.random.nextInt(1..EdgeCase.entries.size)
      EdgeCase.entries.shuffled(rs.random).take(edgeCaseCount).toSet()
    }

    val depthEdgeCaseProbability = if (edgeCases.contains(EdgeCase.Depth)) 1.0f else 0.0f
    val structSizeEdgeCaseProbability = if (edgeCases.contains(EdgeCase.StructSize)) 1.0f else 0.0f
    val listSizeEdgeCaseProbability = if (edgeCases.contains(EdgeCase.ListSize)) 1.0f else 0.0f
    val structKeyEdgeCaseProbability = if (edgeCases.contains(EdgeCase.StructKey)) 1.0f else 0.0f
    val scalarValueEdgeCaseProbability = if (edgeCases.contains(EdgeCase.Value)) 1.0f else 0.0f
    val nestedProbability = if (edgeCases.contains(EdgeCase.OnlyNested)) 1.0f else 0.0f

    return generate(
      rs,
      depthEdgeCaseProbability = depthEdgeCaseProbability,
      structSizeEdgeCaseProbability = structSizeEdgeCaseProbability,
      listSizeEdgeCaseProbability = listSizeEdgeCaseProbability,
      structKeyEdgeCaseProbability = structKeyEdgeCaseProbability,
      scalarValueEdgeCaseProbability = scalarValueEdgeCaseProbability,
      nestedProbability = nestedProbability,
    )
  }

  private fun generate(
    rs: RandomSource,
    depthEdgeCaseProbability: Float,
    structSizeEdgeCaseProbability: Float,
    listSizeEdgeCaseProbability: Float,
    structKeyEdgeCaseProbability: Float,
    scalarValueEdgeCaseProbability: Float,
    nestedProbability: Float,
  ): I {
    val depth = depthArb.next(rs, depthEdgeCaseProbability)
    val descendants: MutableList<DataConnectPathValuePair> = mutableListOf()
    val generatedValue =
      generateCompositeValueFunc(
        rs,
        emptyList(),
        depth,
        descendants,
        structSizeEdgeCaseProbability,
        listSizeEdgeCaseProbability,
        structKeyEdgeCaseProbability,
        scalarValueEdgeCaseProbability,
        nestedProbability,
      )
    return sampleFromValue(generatedValue, depth, descendants.toList())
  }

  protected abstract fun getGenerateValueFunc(
    generator: StructOrListValueGenerator
  ): GenerateCompositeValueFunc<V>

  protected abstract fun sampleFromValue(
    value: V,
    depth: Int,
    descendants: List<DataConnectPathValuePair>
  ): I

  private enum class EdgeCase {
    Depth,
    StructSize,
    ListSize,
    StructKey,
    Value,
    OnlyNested,
  }
}

//////////////////////////////////////////////////////////////////////////////////////////////////
// StructArb class
//////////////////////////////////////////////////////////////////////////////////////////////////

private class StructArb(
  sizeRange: IntRange,
  depthRange: IntRange,
  structKeyArb: Arb<String>,
  scalarValueArb: Arb<Value>,
  listSizeRange: IntRange,
) :
  CompositeValueArb<Struct, ProtoArb.StructInfo>(
    depthRange = depthRange,
    structSizeRange = sizeRange,
    listSizeRange = listSizeRange,
    structKeyArb = structKeyArb,
    scalarValueArb = scalarValueArb,
  ) {

  override fun getGenerateValueFunc(generator: StructOrListValueGenerator) =
    generator::generateStruct

  override fun sampleFromValue(
    value: Struct,
    depth: Int,
    descendants: List<DataConnectPathValuePair>
  ) = ProtoArb.StructInfo(value, depth, descendants)
}

//////////////////////////////////////////////////////////////////////////////////////////////////
// ListValueArb class
//////////////////////////////////////////////////////////////////////////////////////////////////

private class ListValueArb(
  sizeRange: IntRange,
  depthRange: IntRange,
  structKeyArb: Arb<String>,
  scalarValueArb: Arb<Value>,
  structSizeRange: IntRange,
) :
  CompositeValueArb<ListValue, ProtoArb.ListValueInfo>(
    depthRange = depthRange,
    structSizeRange = structSizeRange,
    listSizeRange = sizeRange,
    structKeyArb = structKeyArb,
    scalarValueArb = scalarValueArb,
  ) {

  override fun getGenerateValueFunc(generator: StructOrListValueGenerator) =
    generator::generateListValue

  override fun sampleFromValue(
    value: ListValue,
    depth: Int,
    descendants: List<DataConnectPathValuePair>
  ) = ProtoArb.ListValueInfo(value, depth, descendants)
}

//////////////////////////////////////////////////////////////////////////////////////////////////
// Other helper functions
//////////////////////////////////////////////////////////////////////////////////////////////////

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

private class StructOrListValueGenerator(
  private val scalarValue: Arb<Value>,
  private val structKey: Arb<String>,
  private val structSize: IntRange,
  private val listSize: IntRange,
) {
  fun generateStruct(
    rs: RandomSource,
    path: DataConnectPath,
    depth: Int,
    descendants: MutableList<DataConnectPathValuePair>,
    structSizeEdgeCaseProbability: Float,
    listSizeEdgeCaseProbability: Float,
    structKeyEdgeCaseProbability: Float,
    scalarValueEdgeCaseProbability: Float,
    nestedProbability: Float,
  ): Struct {
    require(depth > 0) { "invalid depth: $depth (must be greater than zero)" }

    val structBuilder = Struct.newBuilder()

    val childPathSegmentGenerator =
      generateSequence { structKey.next(rs, structKeyEdgeCaseProbability) }
        .filterNot { structBuilder.containsFields(it) }
        .map(DataConnectPathSegment::Field)
        .iterator()

    generateCompositeValue(
      rs = rs,
      path = path,
      depth = depth,
      sizeRange = structSize,
      sizeEdgeCaseProbability = structSizeEdgeCaseProbability,
      descendants = descendants,
      structSizeEdgeCaseProbability = structSizeEdgeCaseProbability,
      listSizeEdgeCaseProbability = listSizeEdgeCaseProbability,
      structKeyEdgeCaseProbability = structKeyEdgeCaseProbability,
      scalarValueEdgeCaseProbability = scalarValueEdgeCaseProbability,
      nestedProbability = nestedProbability,
      childPathSegmentForIndex = { childPathSegmentGenerator.next() },
      onChildGenerated = { childPathSegment, value ->
        structBuilder.putFields(childPathSegment.field, value)
      },
    )

    return structBuilder.build()
  }

  fun generateListValue(
    rs: RandomSource,
    path: DataConnectPath,
    depth: Int,
    descendants: MutableList<DataConnectPathValuePair>,
    structSizeEdgeCaseProbability: Float,
    listSizeEdgeCaseProbability: Float,
    structKeyEdgeCaseProbability: Float,
    scalarValueEdgeCaseProbability: Float,
    nestedProbability: Float,
  ): ListValue {
    require(depth > 0) { "invalid depth: $depth (must be greater than zero)" }

    val listValueBuilder = ListValue.newBuilder()

    generateCompositeValue(
      rs = rs,
      path = path,
      depth = depth,
      sizeRange = listSize,
      sizeEdgeCaseProbability = listSizeEdgeCaseProbability,
      descendants = descendants,
      structSizeEdgeCaseProbability = structSizeEdgeCaseProbability,
      listSizeEdgeCaseProbability = listSizeEdgeCaseProbability,
      structKeyEdgeCaseProbability = structKeyEdgeCaseProbability,
      scalarValueEdgeCaseProbability = scalarValueEdgeCaseProbability,
      nestedProbability = nestedProbability,
      childPathSegmentForIndex = DataConnectPathSegment::ListIndex,
      onChildGenerated = { _, value -> listValueBuilder.addValues(value) },
    )

    return listValueBuilder.build()
  }

  private inline fun <P : DataConnectPathSegment> generateCompositeValue(
    rs: RandomSource,
    path: DataConnectPath,
    depth: Int,
    sizeRange: IntRange,
    sizeEdgeCaseProbability: Float,
    descendants: MutableList<DataConnectPathValuePair>,
    structSizeEdgeCaseProbability: Float,
    listSizeEdgeCaseProbability: Float,
    structKeyEdgeCaseProbability: Float,
    scalarValueEdgeCaseProbability: Float,
    nestedProbability: Float,
    childPathSegmentForIndex: (index: Int) -> P,
    onChildGenerated: (pathSegment: P, value: Value) -> Unit,
  ) {
    val size =
      calculateSize(
        rs,
        depth = depth,
        sizeRange = sizeRange,
        edgeCaseProbability = sizeEdgeCaseProbability,
      )
    val maxDepthIndex = calculateMaxDepthIndex(rs, depth = depth, size = size)

    repeat(size) { valueIndex ->
      val childPathSegment = childPathSegmentForIndex(valueIndex)
      val childPath = path.withAddedPathSegment(childPathSegment)

      val childDepth =
        calculateChildDepth(
          rs,
          parentDepth = depth,
          index = valueIndex,
          maxDepthIndex = maxDepthIndex,
          nestedProbability = nestedProbability,
        )
      check(childDepth < depth) // avoid infinite recursion

      val value =
        generateValue(
          rs,
          path = childPath,
          depth = childDepth,
          descendants = descendants,
          structSizeEdgeCaseProbability = structSizeEdgeCaseProbability,
          listSizeEdgeCaseProbability = listSizeEdgeCaseProbability,
          structKeyEdgeCaseProbability = structKeyEdgeCaseProbability,
          scalarValueEdgeCaseProbability = scalarValueEdgeCaseProbability,
          nestedProbability = nestedProbability,
        )

      descendants.add(DataConnectPathValuePair(childPath, value))
      onChildGenerated(childPathSegment, value)
    }
  }

  fun generateValue(
    rs: RandomSource,
    path: DataConnectPath,
    depth: Int,
    descendants: MutableList<DataConnectPathValuePair>,
    structSizeEdgeCaseProbability: Float,
    listSizeEdgeCaseProbability: Float,
    structKeyEdgeCaseProbability: Float,
    scalarValueEdgeCaseProbability: Float,
    nestedProbability: Float,
  ): Value {
    if (depth == 0) {
      return scalarValue.next(rs, scalarValueEdgeCaseProbability)
    }

    val childCanBeStruct = structSize.isNonEmptyExcluding(0)
    val childCanBeList = listSize.isNonEmptyExcluding(0)
    val childIsStruct =
      if (childCanBeStruct && childCanBeList) {
        rs.random.nextBoolean()
      } else if (childCanBeStruct) {
        true
      } else if (childCanBeList) {
        false
      } else {
        throw IllegalStateException(
          "internal error epqkjj6z5w: neither childCanBeList nor childCanBeStruct is true, " +
            "but at least one of them must be true (listSize=$listSize, structSize=$structSize)"
        )
      }

    return if (childIsStruct) {
      generateStruct(
          rs,
          path = path,
          depth = depth,
          descendants = descendants,
          structSizeEdgeCaseProbability = structSizeEdgeCaseProbability,
          listSizeEdgeCaseProbability = listSizeEdgeCaseProbability,
          structKeyEdgeCaseProbability = structKeyEdgeCaseProbability,
          scalarValueEdgeCaseProbability = scalarValueEdgeCaseProbability,
          nestedProbability = nestedProbability,
        )
        .toValueProto()
    } else {
      generateListValue(
          rs,
          path = path,
          depth = depth,
          descendants = descendants,
          structSizeEdgeCaseProbability = structSizeEdgeCaseProbability,
          listSizeEdgeCaseProbability = listSizeEdgeCaseProbability,
          structKeyEdgeCaseProbability = structKeyEdgeCaseProbability,
          scalarValueEdgeCaseProbability = scalarValueEdgeCaseProbability,
          nestedProbability = nestedProbability,
        )
        .toValueProto()
    }
  }

  companion object {
    private fun calculateChildDepth(
      rs: RandomSource,
      parentDepth: Int,
      index: Int,
      maxDepthIndex: Int,
      nestedProbability: Float,
    ): Int {
      require(parentDepth > 0) { "invalid parentDepth: $parentDepth (must be greater than zero)" }
      return if (parentDepth == 1) {
        0
      } else if (index == maxDepthIndex) {
        parentDepth - 1
      } else if (rs.random.nextFloat() < nestedProbability) {
        rs.random.nextInt(1 until parentDepth)
      } else {
        0
      }
    }

    private fun calculateSize(
      rs: RandomSource,
      depth: Int,
      sizeRange: IntRange,
      edgeCaseProbability: Float
    ): Int {
      require(depth > 0) { "invalid depth: $depth (must be greater than zero)" }
      require(depth == 1 || sizeRange.isNonEmptyExcluding(0)) {
        "depth==$depth and sizeRange=$sizeRange, which is an illegal combination " +
          "because when depth is greater than 1 then sizeRange must contain " +
          "at least one non-zero value"
      }

      if (rs.random.nextFloat() < edgeCaseProbability) {
        return if (rs.random.nextBoolean()) {
          if (depth == 1 || sizeRange.first != 0) {
            sizeRange.first
          } else {
            1
          }
        } else {
          sizeRange.last
        }
      }

      while (true) {
        val size = rs.random.nextInt(sizeRange)
        if (depth == 1 || size != 0) {
          return size
        }
      }
    }
  }

  private fun calculateMaxDepthIndex(rs: RandomSource, depth: Int, size: Int): Int =
    if (size == 0 || depth == 1) -1 else rs.random.nextInt(size)
}

private fun IntRange.isNonEmptyExcluding(value: Int): Boolean =
  if (isEmpty()) {
    false
  } else if (!contains(value)) {
    true
  } else {
    val size = (last - first) + 1
    size > 1
  }
