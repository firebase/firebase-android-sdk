package com.google.firebase.firestore.pipeline

import com.google.common.collect.ImmutableMap
import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firebase.firestore.model.Values.encodeVectorValue
import com.google.firestore.v1.Pipeline
import com.google.firestore.v1.Value

abstract class Stage
internal constructor(private val name: String, private val options: Map<String, Value>) {
  internal constructor(name: String) : this(name, emptyMap())
  internal fun toProtoStage(): Pipeline.Stage {
    val builder = Pipeline.Stage.newBuilder()
    builder.setName(name)
    args().forEach { arg -> builder.addArgs(arg) }
    builder.putAllOptions(options)
    return builder.build()
  }
  protected abstract fun args(): Sequence<Value>
}

class DatabaseSource : Stage("database") {
  override fun args(): Sequence<Value> = emptySequence()
}

class CollectionSource internal constructor(path: String) : Stage("collection") {
  private val path: String = if (path.startsWith("/")) path else "/" + path
  override fun args(): Sequence<Value> =
    sequenceOf(Value.newBuilder().setReferenceValue(path).build())
}

class CollectionGroupSource internal constructor(val collectionId: String) :
  Stage("collection_group") {
  override fun args(): Sequence<Value> =
    sequenceOf(Value.newBuilder().setReferenceValue("").build(), encodeValue(collectionId))
}

class DocumentsSource internal constructor(private val documents: Array<out String>) :
  Stage("documents") {
  override fun args(): Sequence<Value> = documents.asSequence().map(::encodeValue)
}

class AddFieldsStage internal constructor(private val fields: Array<out Selectable>) :
  Stage("add_fields") {
  override fun args(): Sequence<Value> =
    sequenceOf(encodeValue(fields.associate { it.alias to it.toProto() }))
}

class AggregateStage
internal constructor(
  private val accumulators: Map<String, Accumulator>,
  private val groups: Map<String, Expr>
) : Stage("aggregate") {
  internal constructor(accumulators: Map<String, Accumulator>) : this(accumulators, emptyMap())
  companion object {
    @JvmStatic
    fun withAccumulators(vararg accumulators: AccumulatorWithAlias): AggregateStage {
      if (accumulators.isEmpty()) {
        throw IllegalArgumentException(
          "Must specify at least one accumulator for aggregate() stage. There is a distinct() stage if only distinct group values are needed."
        )
      }
      return AggregateStage(accumulators.associate { it.alias to it.accumulator })
    }
  }

  fun withGroups(vararg selectable: Selectable) =
    AggregateStage(accumulators, selectable.associateBy(Selectable::alias))

  fun withGroups(vararg fields: String) =
    AggregateStage(accumulators, fields.associateWith(Field::of))

  override fun args(): Sequence<Value> =
    sequenceOf(
      encodeValue(accumulators.mapValues { entry -> entry.value.toProto() }),
      encodeValue(groups.mapValues { entry -> entry.value.toProto() })
    )
}

class WhereStage internal constructor(private val condition: BooleanExpr) : Stage("where") {
  override fun args(): Sequence<Value> = sequenceOf(condition.toProto())
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

  override fun args(): Sequence<Value> =
    sequenceOf(property.toProto(), encodeVectorValue(vector), distanceMeasure.proto)
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
  override fun args(): Sequence<Value> = sequenceOf(encodeValue(limit))
}

class OffsetStage internal constructor(private val offset: Long) : Stage("offset") {
  override fun args(): Sequence<Value> = sequenceOf(encodeValue(offset))
}

class SelectStage internal constructor(private val fields: Array<out Selectable>) :
  Stage("select") {
  override fun args(): Sequence<Value> =
    sequenceOf(encodeValue(fields.associate { it.alias to it.toProto() }))
}

class SortStage internal constructor(private val orders: Array<out Ordering>) : Stage("sort") {
  override fun args(): Sequence<Value> = orders.asSequence().map(Ordering::toProto)
}

class DistinctStage internal constructor(private val groups: Array<out Selectable>) :
  Stage("distinct") {
  override fun args(): Sequence<Value> =
    sequenceOf(encodeValue(groups.associate { it.alias to it.toProto() }))
}

class RemoveFieldsStage internal constructor(private val fields: Array<out Field>) :
  Stage("remove_fields") {
  override fun args(): Sequence<Value> = fields.asSequence().map(Field::toProto)
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
  override fun args(): Sequence<Value> = sequenceOf(field.toProto(), mode.proto)
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
  override fun args(): Sequence<Value> = sequenceOf(encodeValue(size), mode.proto)
}

class UnionStage internal constructor(private val other: com.google.firebase.firestore.Pipeline) :
  Stage("union") {
  override fun args(): Sequence<Value> =
    sequenceOf(Value.newBuilder().setPipelineValue(other.toPipelineProto()).build())
}

class UnnestStage internal constructor(private val selectable: Selectable) : Stage("unnest") {
  override fun args(): Sequence<Value> =
    sequenceOf(encodeValue(selectable.alias), selectable.toProto())
}
