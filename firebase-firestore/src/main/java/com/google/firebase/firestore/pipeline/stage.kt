// Copyright 2025 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.firestore.pipeline

import com.google.common.collect.ImmutableMap
import com.google.firebase.firestore.UserDataReader
import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firebase.firestore.model.Values.encodeVectorValue
import com.google.firestore.v1.Pipeline
import com.google.firestore.v1.Value

abstract class Stage
internal constructor(private val name: String, private val options: Map<String, Value>) {
  internal constructor(name: String) : this(name, emptyMap())
  internal fun toProtoStage(userDataReader: UserDataReader): Pipeline.Stage {
    val builder = Pipeline.Stage.newBuilder()
    builder.setName(name)
    args(userDataReader).forEach { arg -> builder.addArgs(arg) }
    builder.putAllOptions(options)
    return builder.build()
  }
  protected abstract fun args(userDataReader: UserDataReader): Sequence<Value>
}

class GenericStage internal constructor(name: String, private val params: List<GenericArg>) :
  Stage(name) {
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    params.asSequence().map { it.toProto(userDataReader) }
}

internal sealed class GenericArg {
  companion object {
    fun from(arg: Any?): GenericArg =
      when (arg) {
        is AggregateExpr -> AggregateArg(arg)
        is Ordering -> OrderingArg(arg)
        is Map<*, *> -> MapArg(arg.asIterable().associate { it.key as String to from(it.value) })
        is List<*> -> ListArg(arg.map(::from))
        else -> ExprArg(Expr.toExprOrConstant(arg))
      }
  }
  abstract fun toProto(userDataReader: UserDataReader): Value

  data class AggregateArg(val aggregate: AggregateExpr) : GenericArg() {
    override fun toProto(userDataReader: UserDataReader) = aggregate.toProto(userDataReader)
  }

  data class ExprArg(val expr: Expr) : GenericArg() {
    override fun toProto(userDataReader: UserDataReader) = expr.toProto(userDataReader)
  }

  data class OrderingArg(val ordering: Ordering) : GenericArg() {
    override fun toProto(userDataReader: UserDataReader) = ordering.toProto(userDataReader)
  }

  data class MapArg(val args: Map<String, GenericArg>) : GenericArg() {
    override fun toProto(userDataReader: UserDataReader) =
      encodeValue(args.mapValues { it.value.toProto(userDataReader) })
  }

  data class ListArg(val args: List<GenericArg>) : GenericArg() {
    override fun toProto(userDataReader: UserDataReader) =
      encodeValue(args.map { it.toProto(userDataReader) })
  }
}

class DatabaseSource : Stage("database") {
  override fun args(userDataReader: UserDataReader): Sequence<Value> = emptySequence()
}

class CollectionSource internal constructor(path: String) : Stage("collection") {
  private val path: String = if (path.startsWith("/")) path else "/" + path
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(Value.newBuilder().setReferenceValue(path).build())
}

class CollectionGroupSource internal constructor(val collectionId: String) :
  Stage("collection_group") {
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(Value.newBuilder().setReferenceValue("").build(), encodeValue(collectionId))
}

class DocumentsSource internal constructor(private val documents: Array<out String>) :
  Stage("documents") {
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    documents.asSequence().map(::encodeValue)
}

class AddFieldsStage internal constructor(private val fields: Array<out Selectable>) :
  Stage("add_fields") {
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(encodeValue(fields.associate { it.getAlias() to it.toProto(userDataReader) }))
}

class AggregateStage
internal constructor(
  private val accumulators: Map<String, AggregateExpr>,
  private val groups: Map<String, Expr>
) : Stage("aggregate") {
  private constructor(accumulators: Map<String, AggregateExpr>) : this(accumulators, emptyMap())
  companion object {
    @JvmStatic
    fun withAccumulators(vararg accumulators: AggregateWithAlias): AggregateStage {
      if (accumulators.isEmpty()) {
        throw IllegalArgumentException(
          "Must specify at least one accumulator for aggregate() stage. There is a distinct() stage if only distinct group values are needed."
        )
      }
      return AggregateStage(accumulators.associate { it.alias to it.expr })
    }
  }

  fun withGroups(vararg groups: Selectable) =
    AggregateStage(accumulators, groups.associateBy(Selectable::getAlias))

  fun withGroups(vararg fields: String) =
    AggregateStage(accumulators, fields.associateWith(Field::of))

  fun withGroups(vararg selectable: Any) =
    AggregateStage(
      accumulators,
      selectable.map(Selectable::toSelectable).associateBy(Selectable::getAlias)
    )

  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(
      encodeValue(accumulators.mapValues { entry -> entry.value.toProto(userDataReader) }),
      encodeValue(groups.mapValues { entry -> entry.value.toProto(userDataReader) })
    )
}

class WhereStage internal constructor(private val condition: BooleanExpr) : Stage("where") {
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(condition.toProto(userDataReader))
}

class FindNearestStage
internal constructor(
  private val property: Expr,
  private val vector: DoubleArray,
  private val distanceMeasure: DistanceMeasure,
  private val options: FindNearestOptions
) : Stage("find_nearest", options.toProto()) {

  class DistanceMeasure private constructor(internal val proto: Value) {
    private constructor(protoString: String) : this(encodeValue(protoString))
    companion object {
      val EUCLIDEAN = DistanceMeasure("euclidean")
      val COSINE = DistanceMeasure("cosine")
      val DOT_PRODUCT = DistanceMeasure("dot_product")
    }
  }

  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(property.toProto(userDataReader), encodeVectorValue(vector), distanceMeasure.proto)
}

class FindNearestOptions
internal constructor(private val limit: Long?, private val distanceField: Field?) {
  fun toProto(): Map<String, Value> {
    val builder = ImmutableMap.builder<String, Value>()
    if (limit != null) {
      builder.put("limit", encodeValue(limit))
    }
    if (distanceField != null) {
      builder.put("distance_field", distanceField.toProto())
    }
    return builder.build()
  }
}

class LimitStage internal constructor(private val limit: Long) : Stage("limit") {
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(encodeValue(limit))
}

class OffsetStage internal constructor(private val offset: Long) : Stage("offset") {
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(encodeValue(offset))
}

class SelectStage internal constructor(private val fields: Array<out Selectable>) :
  Stage("select") {
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(encodeValue(fields.associate { it.getAlias() to it.toProto(userDataReader) }))
}

class SortStage internal constructor(private val orders: Array<out Ordering>) : Stage("sort") {
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    orders.asSequence().map { it.toProto(userDataReader) }
}

class DistinctStage internal constructor(private val groups: Array<out Selectable>) :
  Stage("distinct") {
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(encodeValue(groups.associate { it.getAlias() to it.toProto(userDataReader) }))
}

class RemoveFieldsStage internal constructor(private val fields: Array<out Field>) :
  Stage("remove_fields") {
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    fields.asSequence().map(Field::toProto)
}

class ReplaceStage internal constructor(private val field: Selectable, private val mode: Mode) :
  Stage("replace") {
  class Mode private constructor(internal val proto: Value) {
    private constructor(protoString: String) : this(encodeValue(protoString))
    companion object {
      val FULL_REPLACE = Mode("full_replace")
      val MERGE_PREFER_NEXT = Mode("merge_prefer_nest")
      val MERGE_PREFER_PARENT = Mode("merge_prefer_parent")
    }
  }
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(field.toProto(userDataReader), mode.proto)
}

class SampleStage internal constructor(private val size: Number, private val mode: Mode) :
  Stage("sample") {
  class Mode private constructor(internal val proto: Value) {
    private constructor(protoString: String) : this(encodeValue(protoString))
    companion object {
      val DOCUMENTS = Mode("documents")
      val PERCENT = Mode("percent")
    }
  }
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(encodeValue(size), mode.proto)
}

class UnionStage internal constructor(private val other: com.google.firebase.firestore.Pipeline) :
  Stage("union") {
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(Value.newBuilder().setPipelineValue(other.toPipelineProto()).build())
}

class UnnestStage internal constructor(private val selectable: Selectable) : Stage("unnest") {
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(encodeValue(selectable.getAlias()), selectable.toProto(userDataReader))
}
