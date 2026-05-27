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
import com.google.firebase.dataconnect.testutil.property.arbitrary.ProtoArb.DurationSample
import com.google.firebase.dataconnect.testutil.property.arbitrary.next
import com.google.firebase.dataconnect.testutil.toValueProto
import com.google.firebase.dataconnect.testutil.withAddedPathSegment
import com.google.protobuf.Duration
import com.google.protobuf.ListValue
import com.google.protobuf.NullValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import io.kotest.assertions.print.print
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
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.asSample
import io.kotest.property.exhaustive.constant
import io.kotest.property.exhaustive.of
import kotlin.random.nextInt

object ProtoArb {

  class StructInfo(
    val struct: Struct,
    val depth: Int,
    val descendants: List<DataConnectPathValuePair>,
  ) {
    fun toValueProto(): Value = struct.toValueProto()

    override fun toString() = "StructInfo(struct=${struct.print().value}, depth=$depth)"
  }

  class ListValueInfo(
    val listValue: ListValue,
    val depth: Int,
    val descendants: List<DataConnectPathValuePair>,
  ) {
    fun toValueProto(): Value = listValue.toValueProto()

    override fun toString() = "ListValueInfo(listValue=${listValue.print().value}, depth=$depth)"
  }

  data class DurationSample(
    val duration: Duration,
    val edgeCases: Set<EdgeCase>,
    val secondsEdgeCaseProbability: Float,
    val nanosEdgeCaseProbability: Float,
  ) {
    override fun toString(): String =
      "DurationSample(" +
        "duration=${duration.print().value}, " +
        "edgeCases=${edgeCases.print().value}, " +
        "secondsEdgeCaseProbability=${secondsEdgeCaseProbability.print().value}, " +
        "nanosEdgeCaseProbability=${nanosEdgeCaseProbability.print().value})"

    enum class EdgeCase {
      Seconds,
      Nanos,
    }
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

fun ProtoArb.value(exclude: Value.KindCase): Arb<Value> {
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
      add(struct().map { it.toValueProto() })
    }
    if (exclude != Value.KindCase.LIST_VALUE) {
      add(listValue().map { it.toValueProto() })
    }
  }
  return Arb.choice(arbs)
}

fun ProtoArb.value(
  recursiveExcludes: Set<Value.KindCase> = emptySet(),
  structKey: Arb<String> = structKey()
): Arb<Value> {
  val scalarArbs = buildList {
    if (Value.KindCase.KIND_NOT_SET !in recursiveExcludes) {
      add(kindNotSetValue())
    }
    if (Value.KindCase.NULL_VALUE !in recursiveExcludes) {
      add(nullValue())
    }
    if (Value.KindCase.NUMBER_VALUE !in recursiveExcludes) {
      add(numberValue())
    }
    if (Value.KindCase.STRING_VALUE !in recursiveExcludes) {
      add(stringValue())
    }
    if (Value.KindCase.BOOL_VALUE !in recursiveExcludes) {
      add(boolValue())
    }
  }

  val scalarValueArb = Arb.choice(scalarArbs)

  val compositeArbs = buildList {
    if (Value.KindCase.STRUCT_VALUE !in recursiveExcludes) {
      add(struct(key = structKey, scalarValue = scalarValueArb).map { it.toValueProto() })
    }
    if (Value.KindCase.LIST_VALUE !in recursiveExcludes) {
      add(listValue(structKey = structKey, scalarValue = scalarValueArb).map { it.toValueProto() })
    }
  }

  return Arb.choice(scalarArbs + compositeArbs)
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

fun ProtoArb.structKey(lengthRange: IntRange = 1..10): Arb<String> =
  Arb.string(lengthRange, Codepoint.alphanumeric())

fun ProtoArb.structKey(length: Int): Arb<String> = structKey(length..length)

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

fun ProtoArb.duration(
  min: Duration? = null,
  max: Duration? = null,
): Arb<DurationSample> = DurationArb(min, max)

//////////////////////////////////////////////////////////////////////////////////////////////////
// CompositeValueArb class
//////////////////////////////////////////////////////////////////////////////////////////////////

private typealias GenerateCompositeValueFunc<V> =
  (
    rs: RandomSource,
    path: DataConnectPath,
    depth: Int,
    descendants: MutableList<DataConnectPathValuePair>,
    probabilities: GenerateCompositeValueProbabilities,
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
        GenerateCompositeValueProbabilities(
          rs.random.nextFloat(),
          rs.random.nextFloat(),
          rs.random.nextFloat(),
          rs.random.nextFloat(),
          rs.random.nextFloat(),
        ),
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
      GenerateCompositeValueProbabilities(
        structSizeEdgeCase = structSizeEdgeCaseProbability,
        listSizeEdgeCase = listSizeEdgeCaseProbability,
        structKeyEdgeCase = structKeyEdgeCaseProbability,
        scalarValueEdgeCase = scalarValueEdgeCaseProbability,
        nested = nestedProbability,
      ),
    )
  }

  private fun generate(
    rs: RandomSource,
    depthEdgeCaseProbability: Float,
    probabilities: GenerateCompositeValueProbabilities,
  ): I {
    val depth = depthArb.next(rs, depthEdgeCaseProbability)
    val descendants: MutableList<DataConnectPathValuePair> = mutableListOf()
    val generatedValue =
      generateCompositeValueFunc(
        rs,
        emptyList(),
        depth,
        descendants,
        probabilities,
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

private data class GenerateCompositeValueProbabilities(
  val structSizeEdgeCase: Float,
  val listSizeEdgeCase: Float,
  val structKeyEdgeCase: Float,
  val scalarValueEdgeCase: Float,
  val nested: Float,
)

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
    probabilities: GenerateCompositeValueProbabilities,
  ): Struct {
    require(depth > 0) { "invalid depth: $depth (must be greater than zero)" }

    val structBuilder = Struct.newBuilder()

    val childPathSegmentGenerator =
      generateSequence { structKey.next(rs, probabilities.structKeyEdgeCase) }
        .filterNot { structBuilder.containsFields(it) }
        .map(DataConnectPathSegment::Field)
        .iterator()

    generateCompositeValue(
      rs = rs,
      path = path,
      depth = depth,
      sizeRange = structSize,
      sizeEdgeCaseProbability = probabilities.structSizeEdgeCase,
      descendants = descendants,
      probabilities = probabilities,
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
    probabilities: GenerateCompositeValueProbabilities,
  ): ListValue {
    require(depth > 0) { "invalid depth: $depth (must be greater than zero)" }

    val listValueBuilder = ListValue.newBuilder()

    generateCompositeValue(
      rs = rs,
      path = path,
      depth = depth,
      sizeRange = listSize,
      sizeEdgeCaseProbability = probabilities.listSizeEdgeCase,
      descendants = descendants,
      probabilities = probabilities,
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
    probabilities: GenerateCompositeValueProbabilities,
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

    val maxDepthIndex = if (size == 0 || depth == 1) -1 else rs.random.nextInt(size)

    repeat(size) { valueIndex ->
      val childPathSegment = childPathSegmentForIndex(valueIndex)
      val childPath = path.withAddedPathSegment(childPathSegment)

      val childDepth =
        calculateChildDepth(
          rs,
          parentDepth = depth,
          index = valueIndex,
          maxDepthIndex = maxDepthIndex,
          nestedProbability = probabilities.nested,
        )
      check(childDepth < depth) // avoid infinite recursion

      val value =
        generateValue(
          rs,
          path = childPath,
          depth = childDepth,
          descendants = descendants,
          probabilities = probabilities,
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
    probabilities: GenerateCompositeValueProbabilities,
  ): Value {
    if (depth == 0) {
      return scalarValue.next(rs, probabilities.scalarValueEdgeCase)
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
          probabilities = probabilities,
        )
        .toValueProto()
    } else {
      generateListValue(
          rs,
          path = path,
          depth = depth,
          descendants = descendants,
          probabilities = probabilities,
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

    private fun IntRange.isNonEmptyExcluding(value: Int): Boolean =
      if (isEmpty()) {
        false
      } else if (!contains(value)) {
        true
      } else {
        val size = (last - first) + 1
        size > 1
      }
  }
}

private class DurationArb(private val min: Duration?, private val max: Duration?) :
  Arb<DurationSample>() {

  init {
    if (min !== null && max !== null) {
      val minSeconds = min.seconds
      val maxSeconds = max.seconds
      require(minSeconds <= maxSeconds) {
        "min.seconds must be less than or equal to max.seconds, " +
          "but min.seconds is $minSeconds and max.seconds is $maxSeconds, " +
          "making min.seconds greater than max.seconds by ${maxSeconds - minSeconds} [r6ys67cpdz]"
      }
      if (minSeconds == maxSeconds) {
        val minNanos = min.nanos
        val maxNanos = max.nanos
        require(minNanos <= maxNanos) {
          "since min.seconds is equal to max.seconds ($maxSeconds), " +
            "min.nanos must be less than or equal to max.nanos, " +
            "but min.nanos is $minNanos and max.nanos is $maxNanos, " +
            "makign min.nanos greater than max.nanos by ${maxNanos - minNanos} [nks29mtww5]"
        }
      }
    }
  }

  override fun sample(rs: RandomSource) =
    generate(
        rs,
        edgeCases = emptySet(),
        secondsEdgeCaseProbability = rs.random.nextFloat(),
        nanosEdgeCaseProbability = rs.random.nextFloat(),
      )
      .asSample()

  override fun edgecase(rs: RandomSource): DurationSample {
    val edgeCases = run {
      val allEdgeCases = DurationSample.EdgeCase.entries.sorted()
      val edgeCaseCount = rs.random.nextInt(1..allEdgeCases.size)
      allEdgeCases.shuffled(rs.random).take(edgeCaseCount).toSet()
    }

    val secondsEdgeCaseProbability =
      if (DurationSample.EdgeCase.Seconds in edgeCases) 1.0f else 0.0f
    val nanosEdgeCaseProbability = if (DurationSample.EdgeCase.Nanos in edgeCases) 1.0f else 0.0f

    return generate(
      rs,
      edgeCases = edgeCases,
      secondsEdgeCaseProbability = secondsEdgeCaseProbability,
      nanosEdgeCaseProbability = nanosEdgeCaseProbability,
    )
  }

  private fun generate(
    rs: RandomSource,
    edgeCases: Set<DurationSample.EdgeCase>,
    secondsEdgeCaseProbability: Float,
    nanosEdgeCaseProbability: Float,
  ): DurationSample {
    val seconds = secondsArb.next(rs, secondsEdgeCaseProbability)
    val nanos = nanosArbForSeconds(seconds).next(rs, nanosEdgeCaseProbability)
    val duration = Duration.newBuilder().setSeconds(seconds).setNanos(nanos).build()

    return DurationSample(
      duration = duration,
      edgeCases = edgeCases,
      secondsEdgeCaseProbability = secondsEdgeCaseProbability,
      nanosEdgeCaseProbability = nanosEdgeCaseProbability,
    )
  }

  private val secondsArb = run {
    val minSeconds = min?.seconds ?: Long.MIN_VALUE
    val maxSeconds = max?.seconds ?: Long.MAX_VALUE
    Arb.long(minSeconds..maxSeconds)
  }

  private val nanosArb = Arb.intWithEvenNumDigitsDistribution(nanosRange)

  private val minNanosArb =
    if (min === null) {
      nanosArb
    } else if (max !== null && min.seconds == max.seconds) {
      Arb.intWithEvenNumDigitsDistribution(min.nanos..max.nanos)
    } else {
      Arb.intWithEvenNumDigitsDistribution(min.nanos..nanosRange.last)
    }

  private val maxNanosArb =
    if (max === null) {
      nanosArb
    } else if (min !== null && min.seconds == max.seconds) {
      minNanosArb
    } else {
      Arb.intWithEvenNumDigitsDistribution(0..max.nanos)
    }

  private fun nanosArbForSeconds(seconds: Long): Arb<Int> =
    if (min !== null && seconds == min.seconds) {
      minNanosArb
    } else if (max !== null && seconds == max.seconds) {
      maxNanosArb
    } else {
      nanosArb
    }

  private companion object {
    private val nanosRange = 0..999_999_999
  }
}
