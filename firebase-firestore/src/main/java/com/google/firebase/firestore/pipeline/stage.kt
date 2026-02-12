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

import com.google.common.annotations.Beta
import com.google.firebase.firestore.UserDataReader
import com.google.firebase.firestore.VectorValue
import com.google.firebase.firestore.model.Document
import com.google.firebase.firestore.model.DocumentKey.KEY_FIELD_NAME
import com.google.firebase.firestore.model.MutableDocument
import com.google.firebase.firestore.model.ResourcePath
import com.google.firebase.firestore.model.Values
import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.evaluation.EvaluationContext
import com.google.firebase.firestore.remote.RemoteSerializer
import com.google.firestore.v1.Pipeline
import com.google.firestore.v1.Value
import javax.annotation.Nonnull

@Beta
sealed class Stage<T : Stage<T>>(internal val name: String, internal val options: InternalOptions) {
  internal fun toProtoStage(userDataReader: UserDataReader): Pipeline.Stage {
    val builder = Pipeline.Stage.newBuilder()
    builder.setName(name)
    args(userDataReader).forEach(builder::addArgs)
    options.forEach(builder::putOptions)
    return builder.build()
  }

  internal abstract fun canonicalId(): String

  internal abstract fun args(userDataReader: UserDataReader): Sequence<Value>

  internal abstract fun self(options: InternalOptions): T

  protected fun withOption(key: String, value: Value): T = self(options.with(key, value))

  /**
   * Specify named [String] parameter
   *
   * @param key The name of parameter
   * @param value The [String] value of parameter
   * @return New stage with named parameter.
   */
  fun withOption(key: String, value: String): T = withOption(key, Values.encodeValue(value))

  /**
   * Specify named [Boolean] parameter
   *
   * @param key The name of parameter
   * @param value The [Boolean] value of parameter
   * @return New stage with named parameter.
   */
  fun withOption(key: String, value: Boolean): T = withOption(key, Values.encodeValue(value))

  /**
   * Specify named [Long] parameter
   *
   * @param key The name of parameter
   * @param value The [Long] value of parameter
   * @return New stage with named parameter.
   */
  fun withOption(key: String, value: Long): T = withOption(key, Values.encodeValue(value))

  /**
   * Specify named [Double] parameter
   *
   * @param key The name of parameter
   * @param value The [Double] value of parameter
   * @return New stage with named parameter.
   */
  fun withOption(key: String, value: Double): T = withOption(key, Values.encodeValue(value))

  /**
   * Specify named [Field] parameter
   *
   * @param key The name of parameter
   * @param value The [Field] value of parameter
   * @return New stage with named parameter.
   */
  fun withOption(key: String, value: Field): T = withOption(key, value.toProto())

  internal open fun evaluate(
    context: EvaluationContext,
    inputs: List<MutableDocument>
  ): List<MutableDocument> {
    throw NotImplementedError("Stage $name does not support offline evaluation")
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
@Beta
class RawStage
private constructor(
  name: String,
  private val arguments: List<GenericArg>,
  options: InternalOptions = InternalOptions.EMPTY
) : Stage<RawStage>(name, options) {
  companion object {
    /**
     * Specify name of stage
     *
     * @param name The unique name of the stage to add.
     * @return A new [RawStage] for the specified stage name.
     */
    @JvmStatic fun ofName(name: String) = RawStage(name, emptyList(), InternalOptions.EMPTY)
  }

  override fun self(options: InternalOptions) = RawStage(name, arguments, options)

  /**
   * Specify arguments to stage.
   *
   * @param arguments A list of ordered parameters to configure the stage's behavior.
   * @return [RawStage] with specified parameters.
   */
  fun withArguments(vararg arguments: Any): RawStage =
    RawStage(name, arguments.map(GenericArg::from), options)

  override fun canonicalId(): String {
    TODO("Not yet implemented")
  }

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
        else -> ExprArg(Expression.toExprOrConstant(arg))
      }
  }
  abstract fun toProto(userDataReader: UserDataReader): Value

  data class AggregateArg(val aggregate: AggregateFunction) : GenericArg() {
    override fun toProto(userDataReader: UserDataReader) = aggregate.toProto(userDataReader)
  }

  data class ExprArg(val expr: Expression) : GenericArg() {
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
  override fun canonicalId(): String {
    TODO("Not yet implemented")
  }

  override fun args(userDataReader: UserDataReader): Sequence<Value> = emptySequence()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is DatabaseSource) return false
    return options == other.options
  }

  override fun hashCode(): Int {
    return options.hashCode()
  }
}

@Beta
class CollectionSource
internal constructor(
  internal val path: ResourcePath,
  // We validate [firestore.databaseId] when adding to pipeline.
  internal val serializer: RemoteSerializer,
  options: InternalOptions
) : Stage<CollectionSource>("collection", options) {

  internal constructor(
    path: ResourcePath,
    serializer: RemoteSerializer,
    options: CollectionSourceOptions
  ) : this(path, serializer, options.options)

  override fun canonicalId(): String {
    return "${name}(${path.canonicalString()})"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is CollectionSource) return false
    if (path != other.path) return false
    if (serializer.databaseId() != other.serializer.databaseId()) return false
    if (options != other.options) return false
    return true
  }

  override fun hashCode(): Int {
    var result = path.hashCode()
    result = 31 * result + (serializer.databaseId().hashCode() ?: 0)
    result = 31 * result + options.hashCode()
    return result
  }

  override fun self(options: InternalOptions): CollectionSource =
    CollectionSource(path, serializer, options)
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(Value.newBuilder().setReferenceValue("/${path.canonicalString()}").build())

  override fun evaluate(
    context: EvaluationContext,
    inputs: List<MutableDocument>
  ): List<MutableDocument> {
    return inputs.filter { input -> input.isFoundDocument && input.key.collectionPath == path }
  }
}

@Beta
class CollectionSourceOptions internal constructor(options: InternalOptions) :
  AbstractOptions<CollectionSourceOptions>(options) {
  /** Creates a new, empty `CollectionSourceOptions` object. */
  constructor() : this(InternalOptions.EMPTY)

  /**
   * Specifies query hints for the collection source.
   *
   * @param hints The hints to apply to the collection source.
   * @return A new `CollectionSourceOptions` with the specified hints.
   */
  fun withHints(hints: CollectionHints) = adding(hints)

  override fun self(options: InternalOptions): CollectionSourceOptions {
    return CollectionSourceOptions(options)
  }
}

@Beta
class CollectionHints internal constructor(options: InternalOptions) :
  AbstractOptions<CollectionHints>(options) {
  /** Creates a new, empty `CollectionHints` object. */
  constructor() : this(InternalOptions.EMPTY)

  override fun self(options: InternalOptions): CollectionHints {
    return CollectionHints(options)
  }

  /**
   * Forces the query to use a specific index.
   *
   * @param value The name of the index to force.
   * @return A new `CollectionHints` with the specified forced index.
   */
  fun withForceIndex(value: String): CollectionHints {
    return with("force_index", value)
  }

  /**
   * Specifies fields to ignore in the index.
   *
   * @param values The names of the fields to ignore in the index.
   * @return A new `CollectionHints` with the specified ignored index fields.
   */
  fun withIgnoreIndexFields(vararg values: String): CollectionHints {
    return with("ignore_index_fields", *values)
  }
}

@Beta
class CollectionGroupSource
internal constructor(val collectionId: String, options: InternalOptions) :
  Stage<CollectionGroupSource>("collection_group", options) {

  internal constructor(
    collectionId: String,
    options: CollectionGroupOptions
  ) : this(collectionId, options.options)

  override fun canonicalId(): String {
    return "${name}(${collectionId})"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is CollectionGroupSource) return false
    if (collectionId != other.collectionId) return false
    if (options != other.options) return false
    return true
  }

  override fun hashCode(): Int {
    var result = collectionId.hashCode()
    result = 31 * result + options.hashCode()
    return result
  }

  override fun self(options: InternalOptions) = CollectionGroupSource(collectionId, options)
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(Value.newBuilder().setReferenceValue("").build(), encodeValue(collectionId))
  override fun evaluate(
    context: EvaluationContext,
    inputs: List<MutableDocument>
  ): List<MutableDocument> {
    return inputs.filter { input ->
      input.isFoundDocument && input.key.collectionGroup == collectionId
    }
  }
}

@Beta
class CollectionGroupOptions internal constructor(options: InternalOptions) :
  AbstractOptions<CollectionGroupOptions>(options) {
  /** Creates a new, empty `CollectionGroupOptions` object. */
  constructor() : this(InternalOptions.EMPTY)

  override fun self(options: InternalOptions): CollectionGroupOptions {
    return CollectionGroupOptions(options)
  }

  /**
   * Specifies query hints for the collection group source.
   *
   * @param hints The hints to apply to the collection group source.
   * @return A new `CollectionGroupOptions` with the specified hints.
   */
  fun withHints(hints: CollectionHints): CollectionGroupOptions = adding(hints)
}

internal class DocumentsSource
@JvmOverloads
internal constructor(
  val documents: Array<ResourcePath>,
  options: InternalOptions = InternalOptions.EMPTY
) : Stage<DocumentsSource>("documents", options) {
  private val docKeySet: HashSet<String> by lazy {
    documents.map { it.canonicalString() }.toHashSet()
  }

  override fun evaluate(
    context: EvaluationContext,
    inputs: List<MutableDocument>
  ): List<MutableDocument> {
    return inputs.filter { input ->
      input.isFoundDocument && docKeySet.contains(input.key.path.canonicalString())
    }
  }

  override fun canonicalId(): String {
    val sortedDocuments = documents.sorted()
    return "${name}(${sortedDocuments.joinToString(",")})"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is DocumentsSource) return false
    if (!documents.contentEquals(other.documents)) return false
    if (options != other.options) return false
    return true
  }

  override fun hashCode(): Int {
    var result = documents.contentHashCode()
    result = 31 * result + options.hashCode()
    return result
  }

  internal constructor(document: String) : this(arrayOf(ResourcePath.fromString(document)))
  override fun self(options: InternalOptions) = DocumentsSource(documents, options)
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    documents.asSequence().map(::encodeValue)
}

class SubcollectionSource
internal constructor(internal val path: String, options: InternalOptions = InternalOptions.EMPTY) :
  Stage<SubcollectionSource>("subcollection", options) {

  override fun self(options: InternalOptions) = SubcollectionSource(path, options)

  override fun canonicalId(): String = "${name}($path)"

  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(Values.encodeValue(path))
}

private fun associateWithoutDuplications(
  fields: Array<out Selectable>,
  userDataReader: UserDataReader
): Map<String, Value> {
  return fields.fold(HashMap<String, Value>()) { results, selectable ->
    if (results.contains(selectable.alias)) {
      throw IllegalArgumentException("Duplicate alias: '${selectable.alias}'")
    }

    results.set(selectable.alias, selectable.toProto(userDataReader))
    results
  }
}

internal class AddFieldsStage
internal constructor(
  private val fields: Array<out Selectable>,
  options: InternalOptions = InternalOptions.EMPTY
) : Stage<AddFieldsStage>("add_fields", options) {
  init {
    for (field in fields) {
      val alias = field.alias
      require(alias != Field.DOCUMENT_ID.alias, { "Alias ${Field.DOCUMENT_ID.alias} is reserved" })
      require(alias != Field.CREATE_TIME.alias, { "Alias ${Field.CREATE_TIME.alias} is reserved" })
      require(alias != Field.UPDATE_TIME.alias, { "Alias ${Field.UPDATE_TIME.alias} is reserved" })
    }
  }
  override fun self(options: InternalOptions) = AddFieldsStage(fields, options)
  override fun canonicalId(): String {
    TODO("Not yet implemented")
  }

  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(encodeValue(associateWithoutDuplications(fields, userDataReader)))

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is AddFieldsStage) return false
    if (!fields.contentEquals(other.fields)) return false
    if (options != other.options) return false
    return true
  }

  override fun hashCode(): Int {
    var result = fields.contentHashCode()
    result = 31 * result + options.hashCode()
    return result
  }
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
 * are defined using [AliasedAggregate] expressions, which are typically created by calling
 * [AggregateFunction.alias] on [AggregateFunction] instances. Each aggregation calculates a value
 * (e.g., sum, average, count) based on the documents within its group.
 */
@Beta
class AggregateStage
private constructor(
  private val accumulators: Map<String, AggregateFunction>,
  private val groups: Map<String, Expression>,
  options: InternalOptions = InternalOptions.EMPTY
) : Stage<AggregateStage>("aggregate", options) {
  private constructor(accumulators: Map<String, AggregateFunction>) : this(accumulators, emptyMap())
  companion object {

    /**
     * Create [AggregateStage] with one or more accumulators.
     *
     * @param accumulator The first [AliasedAggregate] expression, wrapping an [AggregateFunction]
     * with an alias for the accumulated results.
     * @param additionalAccumulators The [AliasedAggregate] expressions, each wrapping an
     * [AggregateFunction] with an alias for the accumulated results.
     * @return [AggregateStage] with specified accumulators.
     */
    @JvmStatic
    fun withAccumulators(
      accumulator: AliasedAggregate,
      vararg additionalAccumulators: AliasedAggregate
    ): AggregateStage {
      val accumulators =
        additionalAccumulators.fold(mapOf(accumulator.alias to accumulator.expr)) { acc, next ->
          if (acc.containsKey(next.alias)) {
            throw IllegalArgumentException("Duplicate alias: '${next.alias}'")
          }
          acc.plus(next.alias to next.expr)
        }

      return AggregateStage(accumulators)
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
  fun withGroups(groupField: String, vararg additionalGroups: Any): AggregateStage =
    withGroups(Expression.field(groupField), *additionalGroups)

  /**
   * Add one or more groups to [AggregateStage]
   *
   * @param group The [Selectable] expression to consider when determining group value combinations.
   * @param additionalGroups The [Selectable] expressions to consider when determining group value
   * combinations or [String]s representing field names.
   * @return [AggregateStage] with specified groups.
   */
  fun withGroups(group: Selectable, vararg additionalGroups: Any): AggregateStage {
    val groups =
      additionalGroups.map(Selectable::toSelectable).fold(mapOf(group.alias to group.expr)) {
        acc,
        next ->
        if (acc.containsKey(next.alias)) {
          throw IllegalArgumentException("Duplicate alias: '${next.alias}'")
        }
        acc.plus(next.alias to next.expr)
      }

    return AggregateStage(accumulators, groups, options)
  }

  internal fun withOptions(options: AggregateOptions) =
    AggregateStage(accumulators, groups, options.options)

  override fun canonicalId(): String {
    TODO("Not yet implemented")
  }

  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(
      encodeValue(accumulators.mapValues { entry -> entry.value.toProto(userDataReader) }),
      encodeValue(groups.mapValues { entry -> entry.value.toProto(userDataReader) })
    )

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is AggregateStage) return false
    if (accumulators != other.accumulators) return false
    if (groups != other.groups) return false
    if (options != other.options) return false
    return true
  }

  override fun hashCode(): Int {
    var result = accumulators.hashCode()
    result = 31 * result + groups.hashCode()
    result = 31 * result + options.hashCode()
    return result
  }
}

@Beta
class AggregateHints internal constructor(options: InternalOptions) :
  AbstractOptions<AggregateHints>(options) {
  /** Creates a new, empty `AggregateHints` object. */
  constructor() : this(InternalOptions.EMPTY)

  override fun self(options: InternalOptions): AggregateHints {
    return AggregateHints(options)
  }

  fun withForceStreamableEnabled(): AggregateHints {
    return with("force_streamable", true)
  }
}

@Beta
class AggregateOptions internal constructor(options: InternalOptions) :
  AbstractOptions<AggregateOptions>(options) {
  /** Creates a new, empty `AggregateOptions` object. */
  constructor() : this(InternalOptions.EMPTY)

  override fun self(options: InternalOptions): AggregateOptions {
    return AggregateOptions(options)
  }

  /**
   * Specifies query hints for the aggregation.
   *
   * @param hints The hints to apply to the aggregation.
   * @return A new `AggregateOptions` with the specified hints.
   */
  fun withHints(hints: AggregateHints): AggregateOptions = adding(hints)
}

internal class WhereStage
internal constructor(
  internal val condition: Expression,
  options: InternalOptions = InternalOptions.EMPTY
) : Stage<WhereStage>("where", options) {
  override fun canonicalId(): String {
    return "${name}(${condition.canonicalId()})"
  }

  override fun self(options: InternalOptions) = WhereStage(condition, options)
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(condition.toProto(userDataReader))

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is WhereStage) return false
    if (condition != other.condition) return false
    if (options != other.options) return false
    return true
  }

  override fun hashCode(): Int {
    var result = condition.hashCode()
    result = 31 * result + options.hashCode()
    return result
  }

  override fun evaluate(
    context: EvaluationContext,
    inputs: List<MutableDocument>
  ): List<MutableDocument> {
    val conditionFunction = condition.evaluateFunction(context)
    return inputs.filter { input -> conditionFunction(input).value?.booleanValue ?: false }
  }
}

/**
 * Performs a vector similarity search, ordering the result set by most similar to least similar,
 * and returning the first N documents in the result set.
 */
@Beta
class FindNearestStage
internal constructor(
  private val property: Expression,
  private val vector: Expression,
  private val distanceMeasure: DistanceMeasure,
  options: InternalOptions = InternalOptions.EMPTY
) : Stage<FindNearestStage>("find_nearest", options) {

  private constructor(
    property: Expression,
    vector: Expression,
    distanceMeasure: DistanceMeasure,
    options: FindNearestOptions
  ) : this(property, vector, distanceMeasure, options.options)

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
    internal fun of(
      vectorField: Field,
      vectorValue: VectorValue,
      distanceMeasure: DistanceMeasure,
      options: FindNearestOptions = FindNearestOptions()
    ) = FindNearestStage(vectorField, constant(vectorValue), distanceMeasure, options)

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
    internal fun of(
      vectorField: Field,
      vectorValue: DoubleArray,
      distanceMeasure: DistanceMeasure,
      options: FindNearestOptions = FindNearestOptions()
    ) = FindNearestStage(vectorField, Expression.vector(vectorValue), distanceMeasure, options)

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
    internal fun of(
      vectorField: String,
      vectorValue: VectorValue,
      distanceMeasure: DistanceMeasure,
      options: FindNearestOptions = FindNearestOptions()
    ) = FindNearestStage(field(vectorField), constant(vectorValue), distanceMeasure, options)

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
    internal fun of(
      vectorField: String,
      vectorValue: DoubleArray,
      distanceMeasure: DistanceMeasure,
      options: FindNearestOptions = FindNearestOptions()
    ) =
      FindNearestStage(field(vectorField), Expression.vector(vectorValue), distanceMeasure, options)

    internal fun of(
      vectorField: String,
      vectorValue: Expression,
      distanceMeasure: DistanceMeasure,
      options: FindNearestOptions = FindNearestOptions()
    ) = FindNearestStage(field(vectorField), vectorValue, distanceMeasure, options)
  }

  class DistanceMeasure private constructor(internal val proto: Value) {
    private constructor(protoString: String) : this(encodeValue(protoString))

    companion object {
      /** The Euclidean distance measure. */
      @JvmField val EUCLIDEAN = DistanceMeasure("euclidean")

      /** The Cosine distance measure. */
      @JvmField val COSINE = DistanceMeasure("cosine")

      /** The Dot Product distance measure. */
      @JvmField val DOT_PRODUCT = DistanceMeasure("dot_product")
    }
  }

  override fun self(options: InternalOptions) =
    FindNearestStage(property, vector, distanceMeasure, options)

  override fun canonicalId(): String {
    TODO("Not yet implemented")
  }

  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(
      property.toProto(userDataReader),
      vector.toProto(userDataReader),
      distanceMeasure.proto
    )

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is FindNearestStage) return false
    if (property != other.property) return false
    if (vector != other.vector) return false
    if (distanceMeasure != other.distanceMeasure) return false
    if (options != other.options) return false
    return true
  }

  override fun hashCode(): Int {
    var result = property.hashCode()
    result = 31 * result + vector.hashCode()
    result = 31 * result + distanceMeasure.hashCode()
    result = 31 * result + options.hashCode()
    return result
  }
}

@Beta
class FindNearestOptions private constructor(options: InternalOptions) :
  AbstractOptions<FindNearestOptions>(options) {
  /** Creates a new, empty `FindNearestOptions` object. */
  constructor() : this(InternalOptions.EMPTY)

  override fun self(options: InternalOptions): FindNearestOptions {
    return FindNearestOptions(options)
  }

  /**
   * Specifies the upper bound of documents to return.
   *
   * @param limit must be a positive integer.
   * @return A new `FindNearestOptions` with the specified limit.
   */
  fun withLimit(limit: Long): FindNearestOptions {
    return with("limit", limit)
  }

  /**
   * Add a field containing the distance to the result.
   *
   * @param distanceField The [Field] that will be added to the result.
   * @return A new `FindNearestOptions` with the specified distance field.
   */
  fun withDistanceField(distanceField: Field): FindNearestOptions {
    return with("distance_field", distanceField)
  }

  /**
   * Add a field containing the distance to the result.
   *
   * @param distanceField The name of the field that will be added to the result.
   * @return A new `FindNearestOptions` with the specified distance field.
   */
  fun withDistanceField(distanceField: String?): FindNearestOptions? {
    return withDistanceField(field(distanceField!!))
  }
}

internal class LimitStage
internal constructor(val limit: Int, options: InternalOptions = InternalOptions.EMPTY) :
  Stage<LimitStage>("limit", options) {
  override fun canonicalId(): String {
    return "${name}(${limit})"
  }

  override fun self(options: InternalOptions) = LimitStage(limit, options)
  override fun evaluate(
    context: EvaluationContext,
    inputs: List<MutableDocument>
  ): List<MutableDocument> =
    when {
      limit > 0 -> inputs.take(limit)
      limit < 0 -> inputs.takeLast(limit)
      else -> listOf()
    }
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(encodeValue(limit))

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is LimitStage) return false
    if (limit != other.limit) return false
    if (options != other.options) return false
    return true
  }

  override fun hashCode(): Int {
    var result = limit
    result = 31 * result + options.hashCode()
    return result
  }
}

internal class OffsetStage
internal constructor(private val offset: Int, options: InternalOptions = InternalOptions.EMPTY) :
  Stage<OffsetStage>("offset", options) {
  override fun self(options: InternalOptions) = OffsetStage(offset, options)
  override fun canonicalId(): String {
    TODO("Not yet implemented")
  }

  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(encodeValue(offset))

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is OffsetStage) return false
    if (offset != other.offset) return false
    if (options != other.options) return false
    return true
  }

  override fun hashCode(): Int {
    var result = offset
    result = 31 * result + options.hashCode()
    return result
  }
}

internal class SelectStage
private constructor(internal val fields: Array<out Selectable>, options: InternalOptions) :
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
  override fun canonicalId(): String {
    TODO("Not yet implemented")
  }

  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(encodeValue(associateWithoutDuplications(fields, userDataReader)))

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SelectStage) return false
    if (!fields.contentEquals(other.fields)) return false
    if (options != other.options) return false
    return true
  }

  override fun hashCode(): Int {
    var result = fields.contentHashCode()
    result = 31 * result + options.hashCode()
    return result
  }
}

private fun comparatorFromOrderings(
  context: EvaluationContext,
  orderings: Array<out Ordering>
): Comparator<Document> =
  java.util.Comparator { d1, d2 ->
    for (ordering in orderings) {
      val expr = ordering.expr
      // Evaluate expression for both documents using expr->Evaluate
      // (assuming this method exists) Pass const references to documents.
      val leftValue = expr.evaluateFunction(context)(d1 as MutableDocument)
      val rightValue = expr.evaluateFunction(context)(d2 as MutableDocument)

      // Compare results, using MinValue for error
      val comparison =
        Values.compare(
          if (leftValue.isError || leftValue.isUnset) Values.NULL_VALUE else leftValue.value!!,
          if (rightValue.isError || rightValue.isUnset) Values.NULL_VALUE else rightValue.value!!,
        )

      if (comparison != 0) {
        return@Comparator if (ordering.dir == Ordering.Direction.ASCENDING) {
          comparison
        } else {
          -comparison
        }
      }
    }
    return@Comparator 0
  }

internal class SortStage
internal constructor(
  val orders: Array<out Ordering>,
  options: InternalOptions = InternalOptions.EMPTY
) : Stage<SortStage>("sort", options) {
  override fun canonicalId(): String {
    return "${name}(${orders.joinToString(",") { it.canonicalId() }})"
  }

  companion object {
    internal val BY_DOCUMENT_ID = SortStage(arrayOf(Field.DOCUMENT_ID.ascending()))
  }

  override fun self(options: InternalOptions) = SortStage(orders, options)
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    orders.asSequence().map { it.toProto(userDataReader) }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SortStage) return false
    if (!orders.contentEquals(other.orders)) return false
    if (options != other.options) return false
    return true
  }

  override fun hashCode(): Int {
    var result = orders.contentHashCode()
    result = 31 * result + options.hashCode()
    return result
  }

  override fun evaluate(
    context: EvaluationContext,
    inputs: List<MutableDocument>
  ): List<MutableDocument> {
    return inputs.sortedWith(comparator(context))
  }

  internal fun comparator(context: EvaluationContext): Comparator<Document> =
    comparatorFromOrderings(context, orders)

  internal fun withStableOrdering(): SortStage {
    val position = orders.indexOfFirst { (it.expr as? Field)?.alias == KEY_FIELD_NAME }
    return if (position < 0) {
      // Append the DocumentId to orders to make ordering stable.
      SortStage(orders.asList().plus(Field.DOCUMENT_ID.ascending()).toTypedArray(), options)
    } else {
      this
    }
  }
}

internal class DistinctStage
internal constructor(
  private val groups: Array<out Selectable>,
  options: InternalOptions = InternalOptions.EMPTY
) : Stage<DistinctStage>("distinct", options) {
  override fun self(options: InternalOptions) = DistinctStage(groups, options)
  override fun canonicalId(): String {
    TODO("Not yet implemented")
  }

  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(encodeValue(associateWithoutDuplications(groups, userDataReader)))

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is DistinctStage) return false
    if (!groups.contentEquals(other.groups)) return false
    if (options != other.options) return false
    return true
  }

  override fun hashCode(): Int {
    var result = groups.contentHashCode()
    result = 31 * result + options.hashCode()
    return result
  }
}

internal class RemoveFieldsStage
internal constructor(
  private val fields: Array<out Field>,
  options: InternalOptions = InternalOptions.EMPTY
) : Stage<RemoveFieldsStage>("remove_fields", options) {
  init {
    for (field in fields) {
      val alias = field.alias
      require(alias != Field.DOCUMENT_ID.alias, { "Alias ${Field.DOCUMENT_ID.alias} is required" })
      require(alias != Field.CREATE_TIME.alias, { "Alias ${Field.CREATE_TIME.alias} is required" })
      require(alias != Field.UPDATE_TIME.alias, { "Alias ${Field.UPDATE_TIME.alias} is required" })
    }
  }
  override fun self(options: InternalOptions) = RemoveFieldsStage(fields, options)
  override fun canonicalId(): String {
    TODO("Not yet implemented")
  }

  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    fields.asSequence().map(Field::toProto)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is RemoveFieldsStage) return false
    if (!fields.contentEquals(other.fields)) return false
    if (options != other.options) return false
    return true
  }

  override fun hashCode(): Int {
    var result = fields.contentHashCode()
    result = 31 * result + options.hashCode()
    return result
  }
}

internal class ReplaceStage
internal constructor(
  private val mapValue: Expression,
  private val mode: Mode,
  options: InternalOptions = InternalOptions.EMPTY
) : Stage<ReplaceStage>("replace_with", options) {
  class Mode private constructor(internal val proto: Value) {
    private constructor(protoString: String) : this(encodeValue(protoString))
    companion object {
      val FULL_REPLACE = Mode("full_replace")
      val MERGE_PREFER_NEXT = Mode("merge_prefer_nest")
      val MERGE_PREFER_PARENT = Mode("merge_prefer_parent")
    }
  }
  override fun self(options: InternalOptions) = ReplaceStage(mapValue, mode, options)
  override fun canonicalId(): String {
    TODO("Not yet implemented")
  }

  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(mapValue.toProto(userDataReader), mode.proto)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ReplaceStage) return false
    if (mapValue != other.mapValue) return false
    if (mode != other.mode) return false
    if (options != other.options) return false
    return true
  }

  override fun hashCode(): Int {
    var result = mapValue.hashCode()
    result = 31 * result + mode.hashCode()
    result = 31 * result + options.hashCode()
    return result
  }
}

/**
 * Performs a pseudo-random sampling of the input documents.
 *
 * The documents produced from this stage are non-deterministic, running the same query over the
 * same dataset multiple times will produce different results. There are two different ways to
 * dictate how the sample is calculated either by specifying a target output size, or by specifying
 * a target percentage of the input size.
 */
@Beta
class SampleStage
private constructor(
  private val size: Number,
  private val mode: Mode,
  options: InternalOptions = InternalOptions.EMPTY
) : Stage<SampleStage>("sample", options) {
  override fun self(options: InternalOptions) = SampleStage(size, mode, options)
  override fun canonicalId(): String {
    TODO("Not yet implemented")
  }

  class Mode private constructor(internal val proto: Value) {
    private constructor(protoString: String) : this(encodeValue(protoString))
    companion object {
      /** Sample by a fixed number of documents. */
      val DOCUMENTS = Mode("documents")
      /** Sample by a percentage of documents. */
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
     * Creates [SampleStage] with the specified number of results returned.
     *
     * The [results] parameter represents the number of results to produce and must be a
     * non-negative integer value. If the previous stage produces less than the specified number,
     * the entire previous results are returned. If the previous stage produces more than the
     * specified number, this stage samples the specified number of documents from the previous
     * stage, with equal probability for each result.
     *
     * @param results The number of documents to emit.
     * @return [SampleStage] with specified [documents].
     */
    @JvmStatic fun withDocLimit(results: Int) = SampleStage(results, Mode.DOCUMENTS)
  }
  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(encodeValue(size), mode.proto)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SampleStage) return false
    if (size != other.size) return false
    if (mode != other.mode) return false
    if (options != other.options) return false
    return true
  }

  override fun hashCode(): Int {
    var result = size.hashCode()
    result = 31 * result + mode.hashCode()
    result = 31 * result + options.hashCode()
    return result
  }
}

internal class UnionStage
internal constructor(
  private val other: com.google.firebase.firestore.Pipeline,
  options: InternalOptions = InternalOptions.EMPTY
) : Stage<UnionStage>("union", options) {
  override fun self(options: InternalOptions) = UnionStage(other, options)
  override fun canonicalId(): String {
    TODO("Not yet implemented")
  }

  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(Value.newBuilder().setPipelineValue(other.toPipelineProto(userDataReader)).build())

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is UnionStage) return false
    if (this.other != other.other) return false
    if (options != other.options) return false
    return true
  }

  override fun hashCode(): Int {
    var result = other.hashCode()
    result = 31 * result + options.hashCode()
    return result
  }
}

/**
 * Takes a specified array from the input documents and outputs a document for each element with the
 * element stored in a field with name specified by the alias.
 */
@Beta
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
     * documents. The input array is found in parameter [arrayWithAlias], which can be an
     * [Expression] with an alias specified via [Expression.alias], or a [Field] that can also have
     * alias specified. For each element of the input array, an augmented document will be produced.
     * The element of input array will be stored in a field with name specified by the alias of the
     * [arrayWithAlias] parameter. If the [arrayWithAlias] is a [Field] with no alias, then the
     * original array field will be replaced with the individual element.
     *
     * @param arrayWithAlias The input array with field alias to store output element of array.
     * @return [UnnestStage] with input array and alias specified.
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
     * @return [UnnestStage] with input array and alias specified.
     */
    @JvmStatic
    fun withField(arrayField: String, alias: String): UnnestStage =
      UnnestStage(Expression.Companion.field(arrayField).alias(alias))
  }
  override fun self(options: InternalOptions) = UnnestStage(selectable, options)
  override fun canonicalId(): String {
    TODO("Not yet implemented")
  }

  override fun args(userDataReader: UserDataReader): Sequence<Value> =
    sequenceOf(selectable.toProto(userDataReader), field(selectable.alias).toProto())

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is UnnestStage) return false
    if (selectable != other.selectable) return false
    if (options != other.options) return false
    return true
  }

  override fun hashCode(): Int {
    var result = selectable.hashCode()
    result = 31 * result + options.hashCode()
    return result
  }

  /**
   * Adds an index field to the output documents.
   *
   * A field with the name specified in [indexField] will be added to each output document. The
   * value of this field is a numeric value that corresponds to the array index of the element from
   * the input array.
   *
   * @param indexField The name of the index field.
   * @return A new `UnnestStage` that includes the specified index field.
   */
  fun withIndexField(indexField: String): UnnestStage = withOption("index_field", indexField)
}

@Beta
class UnnestOptions private constructor(options: InternalOptions) :
  AbstractOptions<UnnestOptions>(options) {
  /** Creates a new, empty `UnnestOptions` object. */
  constructor() : this(InternalOptions.EMPTY)

  /**
   * Adds index field to emitted documents
   *
   * A field with name specified in [indexField] will be added to emitted document. The index is a
   * numeric value that corresponds to array index of the element from input array.
   *
   * @param indexField The field name of index field.
   * @return A new `UnnestOptions` that includes the specified index field.
   */
  fun withIndexField(@Nonnull indexField: String): UnnestOptions {
    return with("index_field", Value.newBuilder().setFieldReferenceValue(indexField).build())
  }

  override fun self(options: InternalOptions): UnnestOptions {
    return UnnestOptions(options)
  }
}

internal class DefineStage
internal constructor(
  private val aliasedExpressions: Array<out AliasedExpression>,
  options: InternalOptions = InternalOptions.EMPTY
) : Stage<DefineStage>("let", options) {

  override fun self(options: InternalOptions) = DefineStage(aliasedExpressions, options)

  override fun canonicalId(): String {
    TODO("Not yet implemented")
  }

  override fun args(userDataReader: UserDataReader): Sequence<Value> {
    return sequenceOf(encodeValue(associateWithoutDuplications(aliasedExpressions, userDataReader)))
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is DefineStage) return false
    if (!aliasedExpressions.contentEquals(other.aliasedExpressions)) return false
    if (options != other.options) return false
    return true
  }

  override fun hashCode(): Int {
    var result = aliasedExpressions.contentHashCode()
    result = 31 * result + options.hashCode()
    return result
  }
}
