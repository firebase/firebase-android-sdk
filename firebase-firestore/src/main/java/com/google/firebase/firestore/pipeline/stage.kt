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

import com.google.firebase.firestore.UserDataReader
import com.google.firebase.firestore.VectorValue
import com.google.firebase.firestore.model.Values
import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firebase.firestore.pipeline.Field.Companion.of
import com.google.firestore.v1.Pipeline
import com.google.firestore.v1.Value

abstract class Stage<T : Stage<T>>
internal constructor(protected val name: String, internal val options: InternalOptions) {
  internal fun toProtoStage(userDataReader: UserDataReader): Pipeline.Stage {
    val builder = Pipeline.Stage.newBuilder()
    builder.setName(name)
    args(userDataReader).forEach(builder::addArgs)
    options.forEach(builder::putOptions)
    return builder.build()
  }
  internal abstract fun args(userDataReader: UserDataReader): Sequence<Value>

  internal abstract fun self(options: InternalOptions): T

  protected fun with(key: String, value: Value): T = self(options.with(key, value))

  fun with(key: String, value: String): T = with(key, Values.encodeValue(value))

  fun with(key: String, value: Boolean): T = with(key, Values.encodeValue(value))

  fun with(key: String, value: Long): T = with(key, Values.encodeValue(value))

  fun with(key: String, value: Double): T = with(key, Values.encodeValue(value))

  fun with(key: String, value: Field): T = with(key, value.toProto())
}

class GenericStage
internal constructor(
  name: String,
  private val arguments: List<GenericArg>,
  options: InternalOptions = InternalOptions.EMPTY
) : Stage<GenericStage>(name, options) {
  companion object {
    @JvmStatic fun ofName(name: String) = GenericStage(name, emptyList(), InternalOptions.EMPTY)
  }

  override fun self(options: InternalOptions) = GenericStage(name, arguments, options)

  fun withArguments(vararg arguments: Any): GenericStage =
    GenericStage(name, arguments.map(GenericArg::from), options)

  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    arguments.asSequence().map { it.toProto(userDataReader) }
}

internal sealed class GenericArg {
  companion object {
    fun from(arg: Any?): GenericArg =
      when (arg) {
        is AggregateFunction -> AggregateArg(arg)
        is Ordering -> OrderingArg(arg)
        is Map<*, *> ->
          MapArg(arg.asIterable().associate { (key, value) -> key as String to from(value) })
        is List<*> -> ListArg(arg.map(::from))
        else -> ExprArg(Expr.toExprOrConstant(arg))
      }
  }
  abstract fun toProto(userDataReader: UserDataReader): Value

  data class AggregateArg(val aggregate: AggregateFunction) : GenericArg() {
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

internal class DatabaseSource
@JvmOverloads
internal constructor(options: InternalOptions = InternalOptions.EMPTY) :
  Stage<DatabaseSource>("database", options) {
  override fun self(options: InternalOptions) = DatabaseSource(options)
  override fun args(userDataReader: UserDataReader): Sequence<Value> = emptySequence()
}

internal class CollectionSource
@JvmOverloads
internal constructor(val path: String, options: InternalOptions = InternalOptions.EMPTY) :
  Stage<CollectionSource>("collection", options) {
  override fun self(options: InternalOptions): CollectionSource = CollectionSource(path, options)
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(
      Value.newBuilder().setReferenceValue(if (path.startsWith("/")) path else "/" + path).build()
    )
}

internal class CollectionGroupSource
@JvmOverloads
internal constructor(val collectionId: String, options: InternalOptions = InternalOptions.EMPTY) :
  Stage<CollectionGroupSource>("collection_group", options) {
  override fun self(options: InternalOptions) = CollectionGroupSource(collectionId, options)
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(Value.newBuilder().setReferenceValue("").build(), encodeValue(collectionId))
}

internal class DocumentsSource
@JvmOverloads
internal constructor(
  private val documents: Array<out String>,
  options: InternalOptions = InternalOptions.EMPTY
) : Stage<DocumentsSource>("documents", options) {
  internal constructor(document: String) : this(arrayOf(document))
  override fun self(options: InternalOptions) = DocumentsSource(documents, options)
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    documents.asSequence().map { if (it.startsWith("/")) it else "/" + it }.map(::encodeValue)
}

internal class AddFieldsStage
internal constructor(
  private val fields: Array<out Selectable>,
  options: InternalOptions = InternalOptions.EMPTY
) : Stage<AddFieldsStage>("add_fields", options) {
  override fun self(options: InternalOptions) = AddFieldsStage(fields, options)
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(encodeValue(fields.associate { it.getAlias() to it.toProto(userDataReader) }))
}

/**
 * Performs optionally grouped aggregation operations on the documents from previous stages.
 *
 * This stage allows you to calculate aggregate values over a set of documents, optionally grouped
 * by one or more fields or functions. You can specify:
 *
 * - **Grouping Fields or Expressions:** One or more fields or functions to group the documents by.
 * For each distinct combination of values in these fields, a separate group is created. If no
 * grouping fields are provided, a single group containing all documents is used. Not specifying
 * groups is the same as putting the entire inputs into one group.
 *
 * - **AggregateFunctions:** One or more accumulation operations to perform within each group. These
 * are defined using [AggregateWithAlias] expressions, which are typically created by calling
 * [AggregateFunction.alias] on [AggregateFunction] instances. Each aggregation calculates a value
 * (e.g., sum, average, count) based on the documents within its group.
 */
class AggregateStage
internal constructor(
  private val accumulators: Map<String, AggregateFunction>,
  private val groups: Map<String, Expr>,
  options: InternalOptions = InternalOptions.EMPTY
) : Stage<AggregateStage>("aggregate", options) {
  private constructor(accumulators: Map<String, AggregateFunction>) : this(accumulators, emptyMap())
  companion object {

    /**
     * Create [AggregateStage] with one or more accumulators.
     *
     * @param accumulator The first [AggregateWithAlias] expression, wrapping an {@link
     * AggregateFunction} with an alias for the accumulated results.
     * @param additionalAccumulators The [AggregateWithAlias] expressions, each wrapping an
     * [AggregateFunction] with an alias for the accumulated results.
     * @return Aggregate Stage with specified accumulators.
     */
    @JvmStatic
    fun withAccumulators(
      accumulator: AggregateWithAlias,
      vararg additionalAccumulators: AggregateWithAlias
    ): AggregateStage {
      return AggregateStage(
        mapOf(accumulator.alias to accumulator.expr)
          .plus(additionalAccumulators.associate { it.alias to it.expr })
      )
    }
  }

  override fun self(options: InternalOptions) = AggregateStage(accumulators, groups, options)

  /**
   * Add one or more groups to [AggregateStage]
   *
   * @param groupField The [String] representing field name.
   * @param additionalGroups The [Selectable] expressions to consider when determining group value
   * combinations or [String]s representing field names.
   * @return Aggregate Stage with specified groups.
   */
  fun withGroups(groupField: String, vararg additionalGroups: Any) =
    withGroups(Field.of(groupField), additionalGroups)

  /**
   * Add one or more groups to [AggregateStage]
   *
   * @param groupField The [Selectable] expression to consider when determining group value
   * combinations.
   * @param additionalGroups The [Selectable] expressions to consider when determining group value
   * combinations or [String]s representing field names.
   * @return Aggregate Stage with specified groups.
   */
  fun withGroups(group: Selectable, vararg additionalGroups: Any) =
    AggregateStage(
      accumulators,
      mapOf(group.getAlias() to group.getExpr())
        .plus(additionalGroups.map(Selectable::toSelectable).associateBy(Selectable::getAlias))
    )

  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(
      encodeValue(accumulators.mapValues { entry -> entry.value.toProto(userDataReader) }),
      encodeValue(groups.mapValues { entry -> entry.value.toProto(userDataReader) })
    )
}

internal class WhereStage
internal constructor(
  private val condition: BooleanExpr,
  options: InternalOptions = InternalOptions.EMPTY
) : Stage<WhereStage>("where", options) {
  override fun self(options: InternalOptions) = WhereStage(condition, options)
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(condition.toProto(userDataReader))
}

/**
 * Performs a vector similarity search, ordering the result set by most similar to least similar,
 * and returning the first N documents in the result set.
 */
class FindNearestStage
internal constructor(
  private val property: Expr,
  private val vector: Expr,
  private val distanceMeasure: DistanceMeasure,
  options: InternalOptions = InternalOptions.EMPTY
) : Stage<FindNearestStage>("find_nearest", options) {

  companion object {

    /**
     * Create [FindNearestStage].
     *
     * @param vectorField A [Field] that contains vector to search on.
     * @param vectorValue The [VectorValue] used to measure the distance from [vectorField] values
     * in the documents.
     * @param distanceMeasure specifies what type of distance is calculated. when performing the
     * search.
     * @return [FindNearestStage] with specified parameters.
     */
    @JvmStatic
    fun of(vectorField: Field, vectorValue: VectorValue, distanceMeasure: DistanceMeasure) =
      FindNearestStage(vectorField, Constant.of(vectorValue), distanceMeasure)

    /**
     * Create [FindNearestStage].
     *
     * @param vectorField A [Field] that contains vector to search on.
     * @param vectorValue The [VectorValue] in array form that is used to measure the distance from
     * [vectorField] values in the documents.
     * @param distanceMeasure specifies what type of distance is calculated when performing the
     * search.
     * @return [FindNearestStage] with specified parameters.
     */
    @JvmStatic
    fun of(vectorField: Field, vectorValue: DoubleArray, distanceMeasure: DistanceMeasure) =
      FindNearestStage(vectorField, Constant.vector(vectorValue), distanceMeasure)

    /**
     * Create [FindNearestStage].
     *
     * @param vectorField A [String] specifying the vector field to search on.
     * @param vectorValue The [VectorValue] used to measure the distance from [vectorField] values
     * in the documents.
     * @param distanceMeasure specifies what type of distance is calculated when performing the
     * search.
     * @return [FindNearestStage] with specified parameters.
     */
    @JvmStatic
    fun of(vectorField: String, vectorValue: VectorValue, distanceMeasure: DistanceMeasure) =
      FindNearestStage(Constant.of(vectorField), Constant.of(vectorValue), distanceMeasure)

    /**
     * Create [FindNearestStage].
     *
     * @param vectorField A [String] specifying the vector field to search on.
     * @param vectorValue The [VectorValue] in array form that is used to measure the distance from
     * [vectorField] values in the documents.
     * @param distanceMeasure specifies what type of distance is calculated when performing the
     * search.
     * @return [FindNearestStage] with specified parameters.
     */
    @JvmStatic
    fun of(vectorField: String, vectorValue: DoubleArray, distanceMeasure: DistanceMeasure) =
      FindNearestStage(Constant.of(vectorField), Constant.vector(vectorValue), distanceMeasure)
  }

  class DistanceMeasure private constructor(internal val proto: Value) {
    private constructor(protoString: String) : this(encodeValue(protoString))

    companion object {
      @JvmField val EUCLIDEAN = DistanceMeasure("euclidean")

      @JvmField val COSINE = DistanceMeasure("cosine")

      @JvmField val DOT_PRODUCT = DistanceMeasure("dot_product")
    }
  }

  override fun self(options: InternalOptions) =
    FindNearestStage(property, vector, distanceMeasure, options)

  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(
      property.toProto(userDataReader),
      vector.toProto(userDataReader),
      distanceMeasure.proto
    )

  /**
   * Specifies the upper bound of documents to return.
   *
   * @param limit must be a positive integer.
   * @return [FindNearestStage] with specified [limit].
   */
  fun withLimit(limit: Long): FindNearestStage = with("limit", limit)

  /**
   * Add a field containing the distance to the result.
   *
   * @param distanceField The [Field] that will be added to the result.
   * @return [FindNearestStage] with specified [distanceField].
   */
  fun withDistanceField(distanceField: Field): FindNearestStage =
    with("distance_field", distanceField)

  /**
   * Add a field containing the distance to the result.
   *
   * @param distanceField The name of the field that will be added to the result.
   * @return [FindNearestStage] with specified [distanceField].
   */
  fun withDistanceField(distanceField: String): FindNearestStage =
    withDistanceField(of(distanceField))
}

internal class LimitStage
internal constructor(private val limit: Int, options: InternalOptions = InternalOptions.EMPTY) :
  Stage<LimitStage>("limit", options) {
  override fun self(options: InternalOptions) = LimitStage(limit, options)
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(encodeValue(limit))
}

internal class OffsetStage
internal constructor(private val offset: Int, options: InternalOptions = InternalOptions.EMPTY) :
  Stage<OffsetStage>("offset", options) {
  override fun self(options: InternalOptions) = OffsetStage(offset, options)
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(encodeValue(offset))
}

internal class SelectStage
internal constructor(
  private val fields: Array<out Selectable>,
  options: InternalOptions = InternalOptions.EMPTY
) : Stage<SelectStage>("select", options) {
  override fun self(options: InternalOptions) = SelectStage(fields, options)
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(encodeValue(fields.associate { it.getAlias() to it.toProto(userDataReader) }))
}

internal class SortStage
internal constructor(
  private val orders: Array<out Ordering>,
  options: InternalOptions = InternalOptions.EMPTY
) : Stage<SortStage>("sort", options) {
  override fun self(options: InternalOptions) = SortStage(orders, options)
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    orders.asSequence().map { it.toProto(userDataReader) }
}

internal class DistinctStage
internal constructor(
  private val groups: Array<out Selectable>,
  options: InternalOptions = InternalOptions.EMPTY
) : Stage<DistinctStage>("distinct", options) {
  override fun self(options: InternalOptions) = DistinctStage(groups, options)
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(encodeValue(groups.associate { it.getAlias() to it.toProto(userDataReader) }))
}

internal class RemoveFieldsStage
internal constructor(
  private val fields: Array<out Field>,
  options: InternalOptions = InternalOptions.EMPTY
) : Stage<RemoveFieldsStage>("remove_fields", options) {
  override fun self(options: InternalOptions) = RemoveFieldsStage(fields, options)
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    fields.asSequence().map(Field::toProto)
}

internal class ReplaceStage
internal constructor(
  private val field: Selectable,
  private val mode: Mode,
  options: InternalOptions = InternalOptions.EMPTY
) : Stage<ReplaceStage>("replace", options) {
  class Mode private constructor(internal val proto: Value) {
    private constructor(protoString: String) : this(encodeValue(protoString))
    companion object {
      val FULL_REPLACE = Mode("full_replace")
      val MERGE_PREFER_NEXT = Mode("merge_prefer_nest")
      val MERGE_PREFER_PARENT = Mode("merge_prefer_parent")
    }
  }
  override fun self(options: InternalOptions) = ReplaceStage(field, mode, options)
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(field.toProto(userDataReader), mode.proto)
}

class SampleStage
private constructor(
  private val size: Number,
  private val mode: Mode,
  options: InternalOptions = InternalOptions.EMPTY
) : Stage<SampleStage>("sample", options) {
  override fun self(options: InternalOptions) = SampleStage(size, mode, options)
  class Mode private constructor(internal val proto: Value) {
    private constructor(protoString: String) : this(encodeValue(protoString))
    companion object {
      val DOCUMENTS = Mode("documents")
      val PERCENT = Mode("percent")
    }
  }
  companion object {
    @JvmStatic fun withPercentage(percentage: Double) = SampleStage(percentage, Mode.PERCENT)

    @JvmStatic fun withDocLimit(documents: Int) = SampleStage(documents, Mode.DOCUMENTS)
  }
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(encodeValue(size), mode.proto)
}

internal class UnionStage
internal constructor(
  private val other: com.google.firebase.firestore.Pipeline,
  options: InternalOptions = InternalOptions.EMPTY
) : Stage<UnionStage>("union", options) {
  override fun self(options: InternalOptions) = UnionStage(other, options)
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(Value.newBuilder().setPipelineValue(other.toPipelineProto()).build())
}

class UnnestStage
internal constructor(
  private val selectable: Selectable,
  options: InternalOptions = InternalOptions.EMPTY
) : Stage<UnnestStage>("unnest", options) {
  companion object {
    @JvmStatic fun withField(selectable: Selectable) = UnnestStage(selectable)
    @JvmStatic
    fun withField(field: String, alias: String): UnnestStage =
      UnnestStage(Field.of(field).alias(alias))
  }
  override fun self(options: InternalOptions) = UnnestStage(selectable, options)
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(encodeValue(selectable.getAlias()), selectable.toProto(userDataReader))

  fun withIndexField(indexField: String): UnnestStage = with("index_field", indexField)
}
