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

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.UserDataReader
import com.google.firebase.firestore.VectorValue
import com.google.firebase.firestore.model.MutableDocument
import com.google.firebase.firestore.model.ResourcePath
import com.google.firebase.firestore.model.Values
import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firebase.firestore.pipeline.Expr.Companion.constant
import com.google.firebase.firestore.pipeline.Expr.Companion.field
import com.google.firebase.firestore.util.Preconditions
import com.google.firestore.v1.Pipeline
import com.google.firestore.v1.Value
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter

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

  /**
   * Specify named [String] parameter
   *
   * @param key The name of parameter
   * @param value The [String] value of parameter
   * @return New stage with named parameter.
   */
  fun with(key: String, value: String): T = with(key, Values.encodeValue(value))

  /**
   * Specify named [Boolean] parameter
   *
   * @param key The name of parameter
   * @param value The [Boolean] value of parameter
   * @return New stage with named parameter.
   */
  fun with(key: String, value: Boolean): T = with(key, Values.encodeValue(value))

  /**
   * Specify named [Long] parameter
   *
   * @param key The name of parameter
   * @param value The [Long] value of parameter
   * @return New stage with named parameter.
   */
  fun with(key: String, value: Long): T = with(key, Values.encodeValue(value))

  /**
   * Specify named [Double] parameter
   *
   * @param key The name of parameter
   * @param value The [Double] value of parameter
   * @return New stage with named parameter.
   */
  fun with(key: String, value: Double): T = with(key, Values.encodeValue(value))

  /**
   * Specify named [Field] parameter
   *
   * @param key The name of parameter
   * @param value The [Field] value of parameter
   * @return New stage with named parameter.
   */
  fun with(key: String, value: Field): T = with(key, value.toProto())

  internal open fun evaluate(
    context: EvaluationContext,
    inputs: Flow<MutableDocument>
  ): Flow<MutableDocument> {
    throw NotImplementedError("Stage does not support offline evaluation")
  }
}

/**
 * Adds a stage to the pipeline by specifying the stage name as an argument. This does not offer any
 * type safety on the stage params and requires the caller to know the order (and optionally names)
 * of parameters accepted by the stage.
 *
 * This class provides a way to call stages that are supported by the Firestore backend but that are
 * not implemented in the SDK version being used.
 */
class GenericStage
private constructor(
  name: String,
  private val arguments: List<GenericArg>,
  options: InternalOptions = InternalOptions.EMPTY
) : Stage<GenericStage>(name, options) {
  companion object {
    /**
     * Specify name of stage
     *
     * @param name The unique name of the stage to add.
     * @return [GenericStage] with specified parameters.
     */
    @JvmStatic fun ofName(name: String) = GenericStage(name, emptyList(), InternalOptions.EMPTY)
  }

  override fun self(options: InternalOptions) = GenericStage(name, arguments, options)

  /**
   * Specify arguments to stage.
   *
   * @param arguments A list of ordered parameters to configure the stage's behavior.
   * @return [GenericStage] with specified parameters.
   */
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

class CollectionSource
internal constructor(
  private val path: String,
  // We validate [firestore.databaseId] when adding to pipeline.
  internal val firestore: FirebaseFirestore?,
  options: InternalOptions
) : Stage<CollectionSource>("collection", options) {
  override fun self(options: InternalOptions): CollectionSource =
    CollectionSource(path, firestore, options)
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(
      Value.newBuilder().setReferenceValue(if (path.startsWith("/")) path else "/" + path).build()
    )
  companion object {
    /**
     * Set the pipeline's source to the collection specified by the given path.
     *
     * @param path A path to a collection that will be the source of this pipeline.
     * @return Pipeline with documents from target collection.
     */
    @JvmStatic
    fun of(path: String): CollectionSource {
      // Validate path by converting to ResourcePath
      val resourcePath = ResourcePath.fromString(path)
      return CollectionSource(resourcePath.canonicalString(), null, InternalOptions.EMPTY)
    }

    /**
     * Set the pipeline's source to the collection specified by the given CollectionReference.
     *
     * @param ref A CollectionReference for a collection that will be the source of this pipeline.
     * @return Pipeline with documents from target collection.
     */
    @JvmStatic
    fun of(ref: CollectionReference): CollectionSource {
      return CollectionSource(ref.path, ref.firestore, InternalOptions.EMPTY)
    }
  }

  fun withForceIndex(value: String) = with("force_index", value)

  override fun evaluate(
    context: EvaluationContext,
    inputs: Flow<MutableDocument>
  ): Flow<MutableDocument> {
    return inputs.filter { input ->
      input.isFoundDocument && input.key.collectionPath.canonicalString() == path
    }
  }
}

class CollectionGroupSource
private constructor(private val collectionId: String, options: InternalOptions) :
  Stage<CollectionGroupSource>("collection_group", options) {
  override fun self(options: InternalOptions) = CollectionGroupSource(collectionId, options)
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(Value.newBuilder().setReferenceValue("").build(), encodeValue(collectionId))

  companion object {

    /**
     * Set the pipeline's source to the collection group with the given id.
     *
     * @param collectionId The id of a collection group that will be the source of this pipeline.
     */
    @JvmStatic
    fun of(collectionId: String): CollectionGroupSource {
      Preconditions.checkNotNull(collectionId, "Provided collection ID must not be null.")
      require(!collectionId.contains("/")) {
        "Invalid collectionId '$collectionId'. Collection IDs must not contain '/'."
      }
      return CollectionGroupSource(collectionId, InternalOptions.EMPTY)
    }
  }

  fun withForceIndex(value: String) = with("force_index", value)
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
     * @return [AggregateStage] with specified accumulators.
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
   * @return [AggregateStage] with specified groups.
   */
  fun withGroups(groupField: String, vararg additionalGroups: Any) =
    withGroups(Expr.field(groupField), additionalGroups)

  /**
   * Add one or more groups to [AggregateStage]
   *
   * @param groupField The [Selectable] expression to consider when determining group value
   * combinations.
   * @param additionalGroups The [Selectable] expressions to consider when determining group value
   * combinations or [String]s representing field names.
   * @return [AggregateStage] with specified groups.
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

  override fun evaluate(
    context: EvaluationContext,
    inputs: Flow<MutableDocument>
  ): Flow<MutableDocument> {
    val conditionFunction = condition.evaluateContext(context)
    return inputs.filter { input -> conditionFunction(input).value?.booleanValue ?: false }
  }
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
      FindNearestStage(vectorField, constant(vectorValue), distanceMeasure)

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
      FindNearestStage(vectorField, Expr.vector(vectorValue), distanceMeasure)

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
      FindNearestStage(constant(vectorField), constant(vectorValue), distanceMeasure)

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
      FindNearestStage(constant(vectorField), Expr.vector(vectorValue), distanceMeasure)
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
    withDistanceField(field(distanceField))
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
private constructor(private val fields: Array<out Selectable>, options: InternalOptions) :
  Stage<SelectStage>("select", options) {
  companion object {
    @JvmStatic
    fun of(selection: Selectable, vararg additionalSelections: Any): SelectStage =
      SelectStage(
        arrayOf(selection, *additionalSelections.map(Selectable::toSelectable).toTypedArray()),
        InternalOptions.EMPTY
      )

    @JvmStatic
    fun of(fieldName: String, vararg additionalSelections: Any): SelectStage =
      of(field(fieldName), *additionalSelections)
  }
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
  private val mapValue: Expr,
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
  override fun self(options: InternalOptions) = ReplaceStage(mapValue, mode, options)
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(mapValue.toProto(userDataReader), mode.proto)
}

/**
 * Performs a pseudo-random sampling of the input documents.
 *
 * The documents produced from this stage are non-deterministic, running the same query over the
 * same dataset multiple times will produce different results. There are two different ways to
 * dictate how the sample is calculated either by specifying a target output size, or by specifying
 * a target percentage of the input size.
 */
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
    /**
     * Creates [SampleStage] with size limited to percentage of prior stages results.
     *
     * The [percentage] parameter is the target percentage (between 0.0 & 1.0) of the number of
     * input documents to produce. Each input document is independently selected against the given
     * percentage. As a result the output size will be approximately documents * [percentage].
     *
     * @param percentage The percentage of the prior stages documents to emit.
     * @return [SampleStage] with specified [percentage].
     */
    @JvmStatic fun withPercentage(percentage: Double) = SampleStage(percentage, Mode.PERCENT)

    /**
     * Creates [SampleStage] with size limited to number of documents.
     *
     * The [documents] parameter represents the target number of documents to produce and must be a
     * non-negative integer value. If the previous stage produces less than size documents, the
     * entire previous results are returned. If the previous stage produces more than size, this
     * outputs a sample of exactly size entries where any sample is equally likely.
     *
     * @param documents The number of documents to emit.
     * @return [SampleStage] with specified [documents].
     */
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

/**
 * Takes a specified array from the input documents and outputs a document for each element with the
 * element stored in a field with name specified by the alias.
 */
class UnnestStage
internal constructor(
  private val selectable: Selectable,
  options: InternalOptions = InternalOptions.EMPTY
) : Stage<UnnestStage>("unnest", options) {
  companion object {

    /**
     * Creates [UnnestStage] with input array and alias specified.
     *
     * For each document emitted by the prior stage, this stage will emit zero or more augmented
     * documents. The input array is found in parameter [arrayWithAlias], which can be an [Expr]
     * with an alias specified via [Expr.alias], or a [Field] that can also have alias specified.
     * For each element of the input array, an augmented document will be produced. The element of
     * input array will be stored in a field with name specified by the alias of the
     * [arrayWithAlias] parameter. If the [arrayWithAlias] is a [Field] with no alias, then the
     * original array field will be replaced with the individual element.
     *
     * @param arrayWithAlias The input array with field alias to store output element of array.
     * @return [SampleStage] with input array and alias specified.
     */
    @JvmStatic fun withField(arrayWithAlias: Selectable) = UnnestStage(arrayWithAlias)

    /**
     * Creates [UnnestStage] with input array and alias specified.
     *
     * For each document emitted by the prior stage, this stage will emit zero or more augmented
     * documents. The input array found in the previous stage document field specified by the
     * [arrayField] parameter, will for each element of the input array produce an augmented
     * document. The element of the input array will be stored in a field with name specified by
     * [alias] parameter on the augmented document.
     *
     * @return [SampleStage] with input array and alias specified.
     */
    @JvmStatic
    fun withField(arrayField: String, alias: String): UnnestStage =
      UnnestStage(Expr.field(arrayField).alias(alias))
  }
  override fun self(options: InternalOptions) = UnnestStage(selectable, options)
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(encodeValue(selectable.getAlias()), selectable.toProto(userDataReader))

  /**
   * Adds index field to emitted documents
   *
   * A field with name specified in [indexField] will be added to emitted document. The index is a
   * numeric value that corresponds to array index of the element from input array.
   *
   * @param indexField The field name of index field.
   * @return [SampleStage] that includes specified index field.
   */
  fun withIndexField(indexField: String): UnnestStage = with("index_field", indexField)
}
