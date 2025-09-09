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

package com.google.firebase.firestore

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.Timestamp
import com.google.firebase.firestore.model.Document
import com.google.firebase.firestore.model.DocumentKey
import com.google.firebase.firestore.model.ResourcePath
import com.google.firebase.firestore.model.Values
import com.google.firebase.firestore.pipeline.AddFieldsStage
import com.google.firebase.firestore.pipeline.AggregateFunction
import com.google.firebase.firestore.pipeline.AggregateOptions
import com.google.firebase.firestore.pipeline.AggregateStage
import com.google.firebase.firestore.pipeline.AliasedAggregate
import com.google.firebase.firestore.pipeline.AliasedExpr
import com.google.firebase.firestore.pipeline.BooleanExpr
import com.google.firebase.firestore.pipeline.CollectionGroupOptions
import com.google.firebase.firestore.pipeline.CollectionGroupSource
import com.google.firebase.firestore.pipeline.CollectionSource
import com.google.firebase.firestore.pipeline.CollectionSourceOptions
import com.google.firebase.firestore.pipeline.DatabaseSource
import com.google.firebase.firestore.pipeline.DistinctStage
import com.google.firebase.firestore.pipeline.DocumentsSource
import com.google.firebase.firestore.pipeline.Expr
import com.google.firebase.firestore.pipeline.Expr.Companion.field
import com.google.firebase.firestore.pipeline.Field
import com.google.firebase.firestore.pipeline.FindNearestOptions
import com.google.firebase.firestore.pipeline.FindNearestStage
import com.google.firebase.firestore.pipeline.FunctionExpr
import com.google.firebase.firestore.pipeline.InternalOptions
import com.google.firebase.firestore.pipeline.LimitStage
import com.google.firebase.firestore.pipeline.OffsetStage
import com.google.firebase.firestore.pipeline.Ordering
import com.google.firebase.firestore.pipeline.PipelineOptions
import com.google.firebase.firestore.pipeline.RawStage
import com.google.firebase.firestore.pipeline.RemoveFieldsStage
import com.google.firebase.firestore.pipeline.ReplaceStage
import com.google.firebase.firestore.pipeline.SampleStage
import com.google.firebase.firestore.pipeline.SelectStage
import com.google.firebase.firestore.pipeline.Selectable
import com.google.firebase.firestore.pipeline.SortStage
import com.google.firebase.firestore.pipeline.Stage
import com.google.firebase.firestore.pipeline.UnionStage
import com.google.firebase.firestore.pipeline.UnnestOptions
import com.google.firebase.firestore.pipeline.UnnestStage
import com.google.firebase.firestore.pipeline.WhereStage
import com.google.firebase.firestore.remote.RemoteSerializer
import com.google.firebase.firestore.util.Logger
import com.google.firestore.v1.ExecutePipelineRequest
import com.google.firestore.v1.StructuredPipeline
import com.google.firestore.v1.Value

class Pipeline
internal constructor(
  private val firestore: FirebaseFirestore,
  private val userDataReader: UserDataReader,
  private val stages: List<Stage<*>>
) {
  internal constructor(
    firestore: FirebaseFirestore,
    userDataReader: UserDataReader,
    stage: Stage<*>
  ) : this(firestore, userDataReader, listOf(stage))

  private fun append(stage: Stage<*>): Pipeline {
    return Pipeline(firestore, userDataReader, stages.plus(stage))
  }

  private fun toStructuredPipelineProto(options: InternalOptions?): StructuredPipeline {
    val builder = StructuredPipeline.newBuilder()
    builder.pipeline = toPipelineProto()
    options?.forEach(builder::putOptions)
    return builder.build()
  }

  internal fun toPipelineProto(): com.google.firestore.v1.Pipeline =
    com.google.firestore.v1.Pipeline.newBuilder()
      .addAllStages(stages.map { it.toProtoStage(userDataReader) })
      .build()

  private fun toExecutePipelineRequest(options: InternalOptions?): ExecutePipelineRequest {
    val database = firestore!!.databaseId
    val builder = ExecutePipelineRequest.newBuilder()
    builder.database = "projects/${database.projectId}/databases/${database.databaseId}"
    builder.structuredPipeline = toStructuredPipelineProto(options)
    return builder.build()
  }

  /**
   * Executes this pipeline and returns the results as a [Task] of [PipelineSnapshot].
   *
   * @return A [Task] that will be resolved with the results of the pipeline.
   */
  fun execute(): Task<PipelineSnapshot> = execute(null)

  /**
   * Executes this pipeline and returns the results as a [Task] of [PipelineSnapshot].
   *
   * @param options The [PipelineOptions] to use to instruct Firestore backend execution.
   * @return A [Task] that will be resolved with the results of the pipeline.
   */
  fun execute(options: PipelineOptions): Task<PipelineSnapshot> = execute(options.options)

  internal fun execute(options: InternalOptions?): Task<PipelineSnapshot> {
    val request = toExecutePipelineRequest(options)
    val observerTask = ObserverSnapshotTask()
    Logger.warn("Pipeline", "Executing pipeline: $request")
    firestore?.callClient { call -> call!!.executePipeline(request, observerTask) }
    return observerTask.task
  }

  private inner class ObserverSnapshotTask : PipelineResultObserver {
    private val userDataWriter =
      UserDataWriter(firestore, DocumentSnapshot.ServerTimestampBehavior.DEFAULT)
    private val taskCompletionSource = TaskCompletionSource<PipelineSnapshot>()
    private val results: MutableList<PipelineResult> = mutableListOf()
    override fun onDocument(
      key: DocumentKey?,
      data: Map<String, Value>,
      createTime: Timestamp?,
      updateTime: Timestamp?
    ) {
      results.add(
        PipelineResult(
          userDataWriter,
          if (key == null) null else DocumentReference(key, firestore),
          data,
          createTime,
          updateTime
        )
      )
    }

    override fun onComplete(executionTime: Timestamp) {
      taskCompletionSource.setResult(PipelineSnapshot(executionTime, results))
    }

    override fun onError(exception: FirebaseFirestoreException) {
      taskCompletionSource.setException(exception)
    }

    val task: Task<PipelineSnapshot>
      get() = taskCompletionSource.task
  }

  internal fun documentReference(key: DocumentKey): DocumentReference {
    return DocumentReference(key, firestore)
  }

  /**
   * Adds a raw stage to the pipeline by specifying the stage name as an argument. This does not
   * offer any type safety on the stage params and requires the caller to know the order (and
   * optionally names) of parameters accepted by the stage.
   *
   * This method provides a way to call stages that are supported by the Firestore backend but that
   * are not implemented in the SDK version being used.
   *
   * @param rawStage An [RawStage] object that specifies stage name and parameters.
   * @return A new [Pipeline] object with this stage appended to the stage list.
   */
  fun rawStage(rawStage: RawStage): Pipeline = append(rawStage)

  /**
   * Adds new fields to outputs from previous stages.
   *
   * This stage allows you to compute values on-the-fly based on existing data from previous stages
   * or constants. You can use this to create new fields or overwrite existing ones.
   *
   * The added fields are defined using [Selectable]s, which can be:
   *
   * - [Field]: References an existing document field.
   * - [AliasedExpr]: Represents the result of a expression with an assigned alias name using
   * [Expr.alias]
   *
   * Example:
   * ```
   * firestore.pipeline().collection("books")
   *   .addFields(
   *     field("rating").as("bookRating"), // Rename 'rating' to 'bookRating'
   *     add(5, field("quantity")).as("totalCost")  // Calculate 'totalCost'
   *   );
   * ```
   *
   * @param field The first field to add to the documents, specified as a [Selectable].
   * @param additionalFields The fields to add to the documents, specified as [Selectable]s.
   * @return A new [Pipeline] object with this stage appended to the stage list.
   */
  fun addFields(field: Selectable, vararg additionalFields: Selectable): Pipeline =
    append(AddFieldsStage(arrayOf(field, *additionalFields)))

  /**
   * Remove fields from outputs of previous stages.
   *
   * Example:
   * ```
   * firestore.pipeline().collection("books")
   *   .removeFields(
   *     field("rating"), field("cost")
   *   );
   * ```
   *
   * @param field The first [Field] to remove.
   * @param additionalFields Optional additional [Field]s to remove.
   * @return A new [Pipeline] object with this stage appended to the stage list.
   */
  fun removeFields(field: Field, vararg additionalFields: Field): Pipeline =
    append(RemoveFieldsStage(arrayOf(field, *additionalFields)))

  /**
   * Remove fields from outputs of previous stages.
   *
   * Example:
   * ```
   * firestore.pipeline().collection("books")
   *   .removeFields(
   *     "rating", "cost"
   *   );
   * ```
   *
   * @param field The first [String] name of field to remove.
   * @param additionalFields Optional additional [String] name of fields to remove.
   * @return A new [Pipeline] object with this stage appended to the stage list.
   */
  fun removeFields(field: String, vararg additionalFields: String): Pipeline =
    append(
      RemoveFieldsStage(arrayOf(field(field), *additionalFields.map(Expr::field).toTypedArray()))
    )

  /**
   * Selects or creates a set of fields from the outputs of previous stages.
   *
   * The selected fields are defined using [Selectable] expressions, which can be:
   *
   * - [String]: Name of an existing field
   * - [Field]: Reference to an existing field.
   * - [AliasedExpr]: Represents the result of a expression with an assigned alias name using
   * [Expr.alias]
   *
   * If no selections are provided, the output of this stage is empty. Use [Pipeline.addFields]
   * instead if only additions are desired.
   *
   * Example:
   * ```
   * firestore.pipeline().collection("books")
   *   .select(
   *     field("name"),
   *     field("address").toUppercase().as("upperAddress"),
   *   );
   * ```
   *
   * @param selection The first field to include in the output documents, specified as a
   * [Selectable] expression.
   * @param additionalSelections Optional additional fields to include in the output documents,
   * specified as [Selectable] expressions or string values representing field names.
   * @return A new [Pipeline] object with this stage appended to the stage list.
   */
  fun select(selection: Selectable, vararg additionalSelections: Any): Pipeline =
    append(SelectStage.of(selection, *additionalSelections))

  /**
   * Selects or creates a set of fields from the outputs of previous stages.
   *
   * The selected fields are defined using [Selectable] expressions, which can be:
   *
   * - [String]: Name of an existing field
   * - [Field]: Reference to an existing field.
   * - [AliasedExpr]: Represents the result of a expression with an assigned alias name using
   * [Expr.alias]
   *
   * If no selections are provided, the output of this stage is empty. Use [Pipeline.addFields]
   * instead if only additions are desired.
   *
   * Example:
   * ```
   * firestore.collection("books")
   *   .select("name", "address");
   *
   * // The above is a shorthand of this:
   * firestore.pipeline().collection("books")
   *    .select(field("name"), field("address"));
   * ```
   *
   * @param fieldName The first field to include in the output documents, specified as a string
   * value representing a field names.
   * @param additionalSelections Optional additional fields to include in the output documents,
   * specified as [Selectable] expressions or string values representing field names.
   * @return A new [Pipeline] object with this stage appended to the stage list.
   */
  fun select(fieldName: String, vararg additionalSelections: Any): Pipeline =
    append(SelectStage.of(fieldName, *additionalSelections))

  /**
   * Sorts the documents from previous stages based on one or more [Ordering] criteria.
   *
   * This stage allows you to order the results of your pipeline. You can specify multiple
   * [Ordering] instances to sort by multiple fields in ascending or descending order. If documents
   * have the same value for a field used for sorting, the next specified ordering will be used. If
   * all orderings result in equal comparison, the documents are considered equal and the order is
   * unspecified.
   *
   * Example:
   * ```
   * // Sort books by rating in descending order, and then by title in ascending order for books with the same rating
   * firestore.pipeline().collection("books")
   *     .sort(
   *         Ordering.of("rating").descending(),
   *         Ordering.of("title")  // Ascending order is the default
   *     );
   * ```
   *
   * @param order The first [Ordering] instance specifying the sorting criteria.
   * @param additionalOrders Optional additional [Ordering] instances specifying the sorting
   * criteria.
   * @return A new [Pipeline] object with this stage appended to the stage list.
   */
  fun sort(order: Ordering, vararg additionalOrders: Ordering): Pipeline =
    append(SortStage(arrayOf(order, *additionalOrders)))

  /**
   * Filters the documents from previous stages to only include those matching the specified
   * [BooleanExpr].
   *
   * This stage allows you to apply conditions to the data, similar to a "WHERE" clause in SQL.
   *
   * You can filter documents based on their field values, using implementations of [BooleanExpr],
   * typically including but not limited to:
   *
   * - field comparators: [Expr.eq], [Expr.lt] (less than), [Expr.gt] (greater than), etc.
   * - logical operators: [Expr.and], [Expr.or], [Expr.not], etc.
   * - advanced functions: [Expr.regexMatch], [Expr.arrayContains], etc.
   *
   * Example:
   * ```
   * firestore.pipeline().collection("books")
   *   .where(
   *     and(
   *         gt("rating", 4.0),   // Filter for ratings greater than 4.0
   *         field("genre").eq("Science Fiction") // Equivalent to eq("genre", "Science Fiction")
   *     )
   *   );
   * ```
   *
   * @param condition The [BooleanExpr] to apply.
   * @return A new [Pipeline] object with this stage appended to the stage list.
   */
  fun where(condition: BooleanExpr): Pipeline = append(WhereStage(condition))

  /**
   * Skips the first `offset` number of documents from the results of previous stages.
   *
   * This stage is useful for implementing pagination in your pipelines, allowing you to retrieve
   * results in chunks. It is typically used in conjunction with [limit] to control the size of each
   * page.
   *
   * Example:
   * ```
   * // Retrieve the second page of 20 results
   * firestore.pipeline().collection("books")
   *     .sort(field("published").descending())
   *     .offset(20)  // Skip the first 20 results
   *     .limit(20);   // Take the next 20 results
   * ```
   *
   * @param offset The number of documents to skip.
   * @return A new [Pipeline] object with this stage appended to the stage list.
   */
  fun offset(offset: Int): Pipeline = append(OffsetStage(offset))

  /**
   * Limits the maximum number of documents returned by previous stages to `limit`.
   *
   * This stage is particularly useful when you want to retrieve a controlled subset of data from a
   * potentially large result set. It's often used for:
   *
   * - **Pagination:** In combination with [offset] to retrieve specific pages of results.
   * - **Limiting Data Retrieval:** To prevent excessive data transfer and improve performance,
   * especially when dealing with large collections.
   *
   * Example:
   * ```
   * // Limit the results to the top 10 highest-rated books
   * firestore.pipeline().collection("books")
   *     .sort(field("rating").descending())
   *     .limit(10);
   * ```
   *
   * @param limit The maximum number of documents to return.
   * @return A new [Pipeline] object with this stage appended to the stage list.
   */
  fun limit(limit: Int): Pipeline = append(LimitStage(limit))

  /**
   * Returns a set of distinct values from the inputs to this stage.
   *
   * This stage runs through the results from previous stages to include only results with unique
   * combinations of [Expr] values [Field], [FunctionExpr], etc).
   *
   * The parameters to this stage are defined using [Selectable] expressions or strings:
   *
   * - [String]: Name of an existing field
   * - [Field]: References an existing document field.
   * - [AliasedExpr]: Represents the result of a function with an assigned alias name using
   * [Expr.alias]
   *
   * Example:
   * ```
   * // Get a list of unique author names in uppercase and genre combinations.
   * firestore.pipeline().collection("books")
   *     .distinct(toUppercase(field("author")).as("authorName"), field("genre"))
   *     .select("authorName");
   * ```
   *
   * @param group The [Selectable] expression to consider when determining distinct value
   * combinations.
   * @param additionalGroups The [Selectable] expressions to consider when determining distinct
   * value combinations or [String]s representing field names.
   * @return A new [Pipeline] object with this stage appended to the stage list.
   */
  fun distinct(group: Selectable, vararg additionalGroups: Any): Pipeline =
    append(
      DistinctStage(arrayOf(group, *additionalGroups.map(Selectable::toSelectable).toTypedArray()))
    )

  /**
   * Returns a set of distinct values from the inputs to this stage.
   *
   * This stage runs through the results from previous stages to include only results with unique
   * combinations of [Expr] values ([Field], [FunctionExpr], etc).
   *
   * The parameters to this stage are defined using [Selectable] expressions or strings:
   *
   * - [String]: Name of an existing field
   * - [Field]: References an existing document field.
   * - [AliasedExpr]: Represents the result of a function with an assigned alias name using
   * [Expr.alias]
   *
   * Example:
   * ```
   * // Get a list of unique genres.
   * firestore.pipeline().collection("books")
   *     .distinct("genre");
   * ```
   *
   * @param groupField The [String] representing field name.
   * @param additionalGroups The [Selectable] expressions to consider when determining distinct
   * value combinations or [String]s representing field names.
   * @return A new [Pipeline] object with this stage appended to the stage list.
   */
  fun distinct(groupField: String, vararg additionalGroups: Any): Pipeline =
    append(
      DistinctStage(
        arrayOf(field(groupField), *additionalGroups.map(Selectable::toSelectable).toTypedArray())
      )
    )

  /**
   * Performs aggregation operations on the documents from previous stages.
   *
   * This stage allows you to calculate aggregate values over a set of documents. You define the
   * aggregations to perform using [AliasedAggregate] expressions which are typically results of
   * calling [AggregateFunction.alias] on [AggregateFunction] instances.
   *
   * Example:
   * ```
   * // Calculate the average rating and the total number of books
   * firestore.pipeline().collection("books")
   *     .aggregate(
   *         field("rating").avg().as("averageRating"),
   *         countAll().as("totalBooks")
   *     );
   * ```
   *
   * @param accumulator The first [AliasedAggregate] expression, wrapping an [AggregateFunction]
   * with an alias for the accumulated results.
   * @param additionalAccumulators The [AliasedAggregate] expressions, each wrapping an
   * [AggregateFunction] with an alias for the accumulated results.
   * @return A new [Pipeline] object with this stage appended to the stage list.
   */
  fun aggregate(
    accumulator: AliasedAggregate,
    vararg additionalAccumulators: AliasedAggregate
  ): Pipeline = append(AggregateStage.withAccumulators(accumulator, *additionalAccumulators))

  /**
   * Performs optionally grouped aggregation operations on the documents from previous stages.
   *
   * This stage allows you to calculate aggregate values over a set of documents, optionally grouped
   * by one or more fields or functions. You can specify:
   *
   * - **Grouping Fields or Expressions:** One or more fields or functions to group the documents
   * by. For each distinct combination of values in these fields, a separate group is created. If no
   * grouping fields are provided, a single group containing all documents is used. Not specifying
   * groups is the same as putting the entire inputs into one group.
   *
   * - **AggregateFunctions:** One or more accumulation operations to perform within each group.
   * These are defined using [AliasedAggregate] expressions, which are typically created by calling
   * [AggregateFunction.alias] on [AggregateFunction] instances. Each aggregation calculates a value
   * (e.g., sum, average, count) based on the documents within its group.
   *
   * Example:
   * ```
   * // Calculate the average rating for each genre.
   * firestore.pipeline().collection("books")
   *   .aggregate(
   *     Aggregate
   *       .withAccumulators(avg("rating").as("avg_rating"))
   *       .withGroups("genre"));
   * ```
   *
   * @param aggregateStage An [AggregateStage] object that specifies the grouping fields (if any)
   * and the aggregation operations to perform.
   * @return A new [Pipeline] object with this stage appended to the stage list.
   */
  fun aggregate(aggregateStage: AggregateStage): Pipeline =
    aggregate(aggregateStage, AggregateOptions())

  /**
   * Performs optionally grouped aggregation operations on the documents from previous stages.
   *
   * This stage allows you to calculate aggregate values over a set of documents, optionally grouped
   * by one or more fields or functions. You can specify:
   *
   * - **Grouping Fields or Expressions:** One or more fields or functions to group the documents
   * by. For each distinct combination of values in these fields, a separate group is created. If no
   * grouping fields are provided, a single group containing all documents is used. Not specifying
   * groups is the same as putting the entire inputs into one group.
   *
   * - **AggregateFunctions:** One or more accumulation operations to perform within each group.
   * These are defined using [AliasedAggregate] expressions, which are typically created by calling
   * [AggregateFunction.alias] on [AggregateFunction] instances. Each aggregation calculates a value
   * (e.g., sum, average, count) based on the documents within its group.
   *
   * @param aggregateStage An [AggregateStage] object that specifies the grouping fields (if any)
   * and the aggregation operations to perform.
   * @param options The [AggregateOptions] to use when performing the aggregation.
   * @return A new [Pipeline] object with this stage appended to the stage list.
   */
  fun aggregate(aggregateStage: AggregateStage, options: AggregateOptions): Pipeline =
    append(aggregateStage.withOptions(options))

  /**
   * Performs a vector similarity search, ordering the result set by most similar to least similar,
   * and returning the first N documents in the result set.
   *
   * @param vectorField A [Field] that contains vector to search on.
   * @param vectorValue The [VectorValue] in array form that is used to measure the distance from
   * [vectorField] values in the documents.
   * @param distanceMeasure specifies what type of distance is calculated when performing the
   * search.
   * @return A new [Pipeline] object with this stage appended to the stage list.
   */
  fun findNearest(
    vectorField: Field,
    vectorValue: DoubleArray,
    distanceMeasure: FindNearestStage.DistanceMeasure,
  ): Pipeline = append(FindNearestStage.of(vectorField, vectorValue, distanceMeasure))

  /**
   * Performs a vector similarity search, ordering the result set by most similar to least similar,
   * and returning the first N documents in the result set.
   *
   * @param vectorField A [String] specifying the vector field to search on.
   * @param vectorValue The [VectorValue] in array form that is used to measure the distance from
   * [vectorField] values in the documents.
   * @param distanceMeasure specifies what type of distance is calculated when performing the
   * search.
   * @return A new [Pipeline] object with this stage appended to the stage list.
   */
  fun findNearest(
    vectorField: String,
    vectorValue: DoubleArray,
    distanceMeasure: FindNearestStage.DistanceMeasure,
  ): Pipeline = append(FindNearestStage.of(vectorField, vectorValue, distanceMeasure))

  /**
   * Performs a vector similarity search, ordering the result set by most similar to least similar,
   * and returning the first N documents in the result set.
   *
   * @param vectorField A [Field] that contains vector to search on.
   * @param vectorValue The [VectorValue] used to measure the distance from [vectorField] values in
   * the documents.
   * @param distanceMeasure specifies what type of distance is calculated. when performing the
   * search.
   * @return A new [Pipeline] object with this stage appended to the stage list.
   */
  fun findNearest(
    vectorField: Field,
    vectorValue: VectorValue,
    distanceMeasure: FindNearestStage.DistanceMeasure,
  ): Pipeline = append(FindNearestStage.of(vectorField, vectorValue, distanceMeasure))

  /**
   * Performs a vector similarity search, ordering the result set by most similar to least similar,
   * and returning the first N documents in the result set.
   *
   * @param vectorField A [String] specifying the vector field to search on.
   * @param vectorValue The [VectorValue] used to measure the distance from [vectorField] values in
   * the documents.
   * @param distanceMeasure specifies what type of distance is calculated when performing the
   * search.
   * @return A new [Pipeline] object with this stage appended to the stage list.
   */
  fun findNearest(
    vectorField: String,
    vectorValue: VectorValue,
    distanceMeasure: FindNearestStage.DistanceMeasure,
  ): Pipeline = append(FindNearestStage.of(vectorField, vectorValue, distanceMeasure))

  /**
   * Performs a vector similarity search, ordering the result set by most similar to least similar,
   * and returning the first N documents in the result set.
   *
   * @param vectorField A [Field] that contains vector to search on.
   * @param vectorValue The [Expr] that should evaluate to a [VectorValue] used to measure the
   * distance from [vectorField] values in the documents.
   * @param distanceMeasure specifies what type of distance is calculated when performing the
   * search.
   * @return A new [Pipeline] object with this stage appended to the stage list.
   */
  fun findNearest(
    vectorField: String,
    vectorValue: Expr,
    distanceMeasure: FindNearestStage.DistanceMeasure
  ): Pipeline = findNearest(vectorField, vectorValue, distanceMeasure, FindNearestOptions())

  /**
   * Performs a vector similarity search, ordering the result set by most similar to least similar,
   * and returning the first N documents in the result set.
   *
   * Example:
   * ```
   * // Find books with similar "topicVectors" to the given targetVector
   * firestore.pipeline().collection("books")
   *     .findNearest("topicVectors", targetVector, FindNearest.DistanceMeasure.COSINE,
   *        new FindNearestOptions()
   *          .withLimit(10)
   *          .withDistanceField("distance"));
   * ```
   *
   * @param vectorField A [Field] that contains vector to search on.
   * @param vectorValue The [Expr] that should evaluate to a [VectorValue] used to measure the
   * distance from [vectorField] values in the documents.
   * @param distanceMeasure specifies what type of distance is calculated when performing the
   * search.
   * @param options The [FindNearestOptions] to use when performing the search.
   * @return A new [Pipeline] object with this stage appended to the stage list.
   */
  fun findNearest(
    vectorField: String,
    vectorValue: Expr,
    distanceMeasure: FindNearestStage.DistanceMeasure,
    options: FindNearestOptions
  ): Pipeline = append(FindNearestStage.of(vectorField, vectorValue, distanceMeasure, options))

  /**
   * Fully overwrites all fields in a document with those coming from a nested map.
   *
   * This stage allows you to emit a map value as a document. Each key of the map becomes a field on
   * the document that contains the corresponding value.
   *
   * Example:
   * ```
   * // Input.
   * // {
   * //  "name": "John Doe Jr.",
   * //  "parents": {
   * //    "father": "John Doe Sr.",
   * //    "mother": "Jane Doe"
   * // }
   *
   * // Emit parents as document.
   * firestore.pipeline().collection("people").replaceWith("parents");
   *
   * // Output
   * // {
   * //  "father": "John Doe Sr.",
   * //  "mother": "Jane Doe"
   * // }
   * ```
   *
   * @param field The [String] specifying the field name containing the nested map.
   * @return A new [Pipeline] object with this stage appended to the stage list.
   */
  fun replaceWith(field: String): Pipeline = replaceWith(field(field))

  /**
   * Fully overwrites all fields in a document with those coming from a nested map.
   *
   * This stage allows you to emit a map value as a document. Each key of the map becomes a field on
   * the document that contains the corresponding value.
   *
   * Example:
   * ```
   * // Input.
   * // {
   * //  "name": "John Doe Jr.",
   * //  "parents": {
   * //    "father": "John Doe Sr.",
   * //    "mother": "Jane Doe"
   * // }
   *
   * // Emit parents as document.
   * firestore.pipeline().collection("people").replaceWith(field("parents"));
   *
   * // Output
   * // {
   * //  "father": "John Doe Sr.",
   * //  "mother": "Jane Doe"
   * // }
   * ```
   *
   * @param mapValue The [Expr] or [Field] containing the nested map.
   * @return A new [Pipeline] object with this stage appended to the stage list.
   */
  fun replaceWith(mapValue: Expr): Pipeline =
    append(ReplaceStage(mapValue, ReplaceStage.Mode.FULL_REPLACE))

  /**
   * Performs a pseudo-random sampling of the input documents.
   *
   * The [documents] parameter represents the target number of documents to produce and must be a
   * non-negative integer value. If the previous stage produces less than size documents, the entire
   * previous results are returned. If the previous stage produces more than size, this outputs a
   * sample of exactly size entries where any sample is equally likely.
   *
   * Example:
   * ```
   * // Sample 10 books, if available.
   * firestore.pipeline().collection("books")
   *     .sample(10);
   * ```
   *
   * @param documents The number of documents to emit.
   * @return A new [Pipeline] object with this stage appended to the stage list.
   */
  fun sample(documents: Int): Pipeline = append(SampleStage.withDocLimit(documents))

  /**
   * Performs a pseudo-random sampling of the input documents.
   *
   * Examples:
   * ```
   * // Sample 10 books, if available.
   * firestore.pipeline().collection("books")
   *     .sample(Sample.withDocLimit(10));
   *
   * // Sample 50% of books.
   * firestore.pipeline().collection("books")
   *     .sample(Sample.withPercentage(0.5));
   * ```
   *
   * @param sample An [SampleStage] object that specifies how sampling is performed.
   * @return A new [Pipeline] object with this stage appended to the stage list.
   */
  fun sample(sample: SampleStage): Pipeline = append(sample)

  /**
   * Performs union of all documents from two pipelines, including duplicates.
   *
   * This stage will pass through documents from previous stage, and also pass through documents
   * from previous stage of the `other` Pipeline given in parameter. The order of documents emitted
   * from this stage is undefined.
   *
   * Example:
   * ```
   * // Emit documents from books collection and magazines collection.
   * firestore.pipeline().collection("books")
   *     .union(firestore.pipeline().collection("magazines"));
   * ```
   *
   * @param other The other [Pipeline] that is part of union.
   * @return A new [Pipeline] object with this stage appended to the stage list.
   */
  fun union(other: Pipeline): Pipeline = append(UnionStage(other))

  /**
   * Takes a specified array from the input documents and outputs a document for each element with
   * the element stored in a field with name specified by the alias.
   *
   * For each document emitted by the prior stage, this stage will emit zero or more augmented
   * documents. The input array found in the previous stage document field specified by the
   * [arrayField] parameter, will for each element of the input array produce an augmented document.
   * The element of the input array will be stored in a field with name specified by [alias]
   * parameter on the augmented document.
   *
   * Example:
   * ```
   * // Input:
   * // { "title": "The Hitchhiker's Guide to the Galaxy", "tags": [ "comedy", "space", "adventure" ], ... }
   *
   * // Emit a book document for each tag of the book.
   * firestore.pipeline().collection("books")
   *     .unnest("tags", "tag");
   *
   * // Output:
   * // { "title": "The Hitchhiker's Guide to the Galaxy", "tag": "comedy", ... }
   * // { "title": "The Hitchhiker's Guide to the Galaxy", "tag": "space", ... }
   * // { "title": "The Hitchhiker's Guide to the Galaxy", "tag": "adventure", ... }
   * ```
   *
   * @param arrayField The name of the field containing the array.
   * @param alias The name of field to store emitted element of array.
   * @return A new [Pipeline] object with this stage appended to the stage list.
   */
  fun unnest(arrayField: String, alias: String): Pipeline = unnest(field(arrayField).alias(alias))

  /**
   * Takes a specified array from the input documents and outputs a document for each element with
   * the element stored in a field with name specified by the alias.
   *
   * For each document emitted by the prior stage, this stage will emit zero or more augmented
   * documents. The input array is found in parameter [arrayWithAlias], which can be an [Expr] with
   * an alias specified via [Expr.alias], or a [Field] that can also have alias specified. For each
   * element of the input array, an augmented document will be produced. The element of input array
   * will be stored in a field with name specified by the alias of the [arrayWithAlias] parameter.
   * If the [arrayWithAlias] is a [Field] with no alias, then the original array field will be
   * replaced with the individual element.
   *
   * @param arrayWithAlias The input array with field alias to store output element of array.
   * @return A new [Pipeline] object with this stage appended to the stage list.
   */
  fun unnest(arrayWithAlias: Selectable): Pipeline = unnest(arrayWithAlias, UnnestOptions())

  /**
   * Takes a specified array from the input documents and outputs a document for each element with
   * the element stored in a field with name specified by the alias.
   *
   * For each document emitted by the prior stage, this stage will emit zero or more augmented
   * documents. The input array is found in parameter [arrayWithAlias], which can be an [Expr] with
   * an alias specified via [Expr.alias], or a [Field] that can also have alias specified. For each
   * element of the input array, an augmented document will be produced. The element of input array
   * will be stored in a field with name specified by the alias of the [arrayWithAlias] parameter.
   * If the [arrayWithAlias] is a [Field] with no alias, then the original array field will be
   * replaced with the individual element.
   *
   * Example:
   * ```
   * // Input:
   * // { "title": "The Hitchhiker's Guide to the Galaxy", "tags": [ "comedy", "space", "adventure" ], ... }
   *
   * // Emit a book document for each tag of the book.
   * firestore.pipeline().collection("books")
   *     .unnest("tags", "tag", new UnnestOptions().withIndexField("tagIndex"));
   *
   * // Output:
   * // { "title": "The Hitchhiker's Guide to the Galaxy", "tagIndex": 0, "tag": "comedy", ... }
   * // { "title": "The Hitchhiker's Guide to the Galaxy", "tagIndex": 1, "tag": "space", ... }
   * // { "title": "The Hitchhiker's Guide to the Galaxy", "tagIndex": 2, "tag": "adventure", ... }
   * ```
   *
   * @param arrayWithAlias The input array with field alias to store output element of array.
   * @param options The [UnnestOptions] to use when performing the unnest.
   * @return A new [Pipeline] object with this stage appended to the stage list.
   */
  fun unnest(arrayWithAlias: Selectable, options: UnnestOptions): Pipeline =
    append(UnnestStage(arrayWithAlias, options.options))

  /**
   * Takes a specified array from the input documents and outputs a document for each element with
   * the element stored in a field with name specified by the alias.
   *
   * For each document emitted by the prior stage, this stage will emit zero or more augmented
   * documents. The input array specified in the [unnestStage] parameter will for each element of
   * the input array produce an augmented document. The element of the input array will be stored in
   * a field with a name specified by the [unnestStage] parameter.
   *
   * Optionally, an index field can also be added to emitted documents. See [UnnestStage] for
   * further information.
   *
   * @param unnestStage An [UnnestStage] object that specifies the search parameters.
   * @return A new [Pipeline] object with this stage appended to the stage list.
   */
  fun unnest(unnestStage: UnnestStage): Pipeline = append(unnestStage)
}

/** Start of a Firestore Pipeline */
class PipelineSource internal constructor(private val firestore: FirebaseFirestore) {

  /**
   * Convert the given Query into an equivalent Pipeline.
   *
   * @param query A Query to be converted into a Pipeline.
   * @return A new [Pipeline] object that is equivalent to [query]
   * @throws [IllegalArgumentException] Thrown if the [query] provided targets a different project
   * or database than the pipeline.
   */
  fun createFrom(query: Query): Pipeline {
    if (query.firestore.databaseId != firestore.databaseId) {
      throw IllegalArgumentException("Provided query is from a different Firestore instance.")
    }
    return query.query.toPipeline(firestore, firestore.userDataReader)
  }

  /**
   * Convert the given Aggregate Query into an equivalent Pipeline.
   *
   * @param aggregateQuery An Aggregate Query to be converted into a Pipeline.
   * @return A new [Pipeline] object that is equivalent to [aggregateQuery]
   * @throws [IllegalArgumentException] Thrown if the [aggregateQuery] provided targets a different
   * project or database than the pipeline.
   */
  fun createFrom(aggregateQuery: AggregateQuery): Pipeline {
    val aggregateFields = aggregateQuery.aggregateFields
    return createFrom(aggregateQuery.query)
      .aggregate(
        aggregateFields.first().toPipeline(),
        *aggregateFields.drop(1).map(AggregateField::toPipeline).toTypedArray<AliasedAggregate>()
      )
  }

  /**
   * Set the pipeline's source to the collection specified by the given path.
   *
   * @param path A path to a collection that will be the source of this pipeline.
   * @return A new [Pipeline] object with documents from target collection.
   */
  fun collection(path: String): Pipeline = collection(firestore.collection(path))

  /**
   * Set the pipeline's source to the collection specified by the given [CollectionReference].
   *
   * @param ref A [CollectionReference] for a collection that will be the source of this pipeline.
   * @return A new [Pipeline] object with documents from target collection.
   * @throws [IllegalArgumentException] Thrown if the [ref] provided targets a different project or
   * database than the pipeline.
   */
  fun collection(ref: CollectionReference): Pipeline = collection(ref, CollectionSourceOptions())

  /**
   * Set the pipeline's source to the collection specified by the given [CollectionReference].
   *
   * @param ref A [CollectionReference] for a collection that will be the source of this pipeline.
   * @param options [CollectionSourceOptions] for the collection.
   * @return A new [Pipeline] object with documents from target collection.
   * @throws [IllegalArgumentException] Thrown if the [ref] provided targets a different project or
   * database than the pipeline.
   */
  fun collection(ref: CollectionReference, options: CollectionSourceOptions): Pipeline {
    if (
      ref.firestore.databaseId != firestore.databaseId ||
        ref.firestore.app?.options?.projectId != firestore.app?.options?.projectId
    ) {
      throw IllegalArgumentException(
        "Invalid CollectionReference. The Firestore instance of the CollectionReference must match the Firestore instance of the PipelineSource."
      )
    }

    return Pipeline(
      firestore,
      firestore.userDataReader,
      CollectionSource(
        ResourcePath.fromString(ref.path),
        RemoteSerializer(firestore.databaseId),
        options
      )
    )
  }

  /**
   * Set the pipeline's source to the collection group with the given id.
   *
   * @param collectionId The id of a collection group that will be the source of this pipeline.
   * @return A new [Pipeline] object with documents from target collection group.
   */
  fun collectionGroup(collectionId: String): Pipeline =
    collectionGroup(collectionId, CollectionGroupOptions())

  /**
   * Set the pipeline's source to the collection group with the given id.
   *
   * @param collectionId The id of a collection group that will be the source of this pipeline.
   * @param options [CollectionGroupOptions] for the collection group.
   * @return A new [Pipeline] object with documents from target collection group.
   */
  fun collectionGroup(collectionId: String, options: CollectionGroupOptions): Pipeline =
    Pipeline(firestore, firestore.userDataReader, CollectionGroupSource(collectionId, options))

  /**
   * Set the pipeline's source to be all documents in this database.
   *
   * @return A new [Pipeline] object with all documents in this database.
   */
  fun database(): Pipeline = Pipeline(firestore, firestore.userDataReader, DatabaseSource())

  /**
   * Set the pipeline's source to the documents specified by the given paths.
   *
   * @param documents Paths specifying the individual documents that will be the source of this
   * pipeline.
   * @return A new [Pipeline] object with [documents].
   */
  fun documents(vararg documents: String): Pipeline {
    // Validate document path by converting to DocumentReference
    return documents(*documents.map(firestore::document).toTypedArray())
  }

  /**
   * Set the pipeline's source to the documents specified by the given DocumentReferences.
   *
   * @param documents DocumentReferences specifying the individual documents that will be the source
   * of this pipeline.
   * @return Pipeline with [documents].
   * @throws [IllegalArgumentException] Thrown if the [documents] provided targets a different
   * project or database than the pipeline.
   */
  fun documents(vararg documents: DocumentReference): Pipeline {
    val databaseId = firestore.databaseId
    for (document in documents) {
      if (document.firestore.databaseId != databaseId) {
        throw IllegalArgumentException(
          "Provided document reference is from a different Firestore instance."
        )
      }
    }
    return Pipeline(
      firestore,
      firestore.userDataReader,
      DocumentsSource(documents.map { ResourcePath.fromString(it.path) }.toTypedArray())
    )
  }
}

/**
 * A `PipelineSnapshot` contains the results of a pipeline execution. It can be iterated to retrieve
 * the individual `PipelineResult` objects.
 */
class PipelineSnapshot
internal constructor(executionTime: Timestamp, results: List<PipelineResult>) :
  Iterable<PipelineResult> {

  /** The time at which the pipeline producing this result is executed. */
  val executionTime: Timestamp = executionTime

  /** List of all the results */
  val results: List<PipelineResult> = results

  override fun iterator() = results.iterator()

  override fun toString() = "PipelineSnapshot{executionTime=$executionTime, results=$results}"
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as PipelineSnapshot
    if (results != other.results) return false
    return true
  }
  override fun hashCode(): Int {
    return results.hashCode()
  }
}

class PipelineResult
internal constructor(
  private val userDataWriter: UserDataWriter,
  ref: DocumentReference?,
  private val fields: Map<String, Value>,
  createTime: Timestamp?,
  updateTime: Timestamp?,
) {

  internal constructor(
    document: Document,
    serverTimestampBehavior: DocumentSnapshot.ServerTimestampBehavior,
    firestore: FirebaseFirestore
  ) : this(
    UserDataWriter(firestore, serverTimestampBehavior),
    DocumentReference(document.key, firestore),
    document.data.fieldsMap,
    document.createTime?.timestamp,
    document.version.timestamp
  )

  /** The time the document was created. Null if this result is not a document. */
  val createTime: Timestamp? = createTime

  /**
   * The time the document was last updated (at the time the snapshot was generated). Null if this
   * result is not a document.
   */
  val updateTime: Timestamp? = updateTime

  /**
   * The reference to the document, if the query returns the `__name__` field for a document. The
   * name field will be returned by default if querying a document.
   *
   * The `__name__` field will not be returned if the query projects away this field. For example:
   * ```
   *   // this query does not select the `__name__` field as part of the select stage,
   *   // so the __name__ field will not be in the output docs from this stage
   *   db.pipeline().collection("books").select("title", "desc")
   * ```
   *
   * The `__name__` field will not be returned from queries with aggregate or distinct stages.
   *
   * @return [DocumentReference] Reference to the document, if applicable.
   */
  val ref: DocumentReference? = ref

  /**
   * Returns the ID of the document represented by this result. Returns null if this result is not
   * corresponding to a Firestore document.
   *
   * @return ID of document, if applicable.
   */
  fun getId(): String? = ref?.id

  /**
   * Retrieves all fields in the result as an object map.
   *
   * @return Map of field names to objects.
   */
  fun getData(): Map<String, Any?> = userDataWriter.convertObject(fields)

  private fun extractNestedValue(fieldPath: FieldPath): Value? {
    val segments = fieldPath.internalPath.iterator()
    if (!segments.hasNext()) {
      return Values.encodeValue(fields)
    }
    val firstSegment = segments.next()
    if (!fields.containsKey(firstSegment)) {
      return null
    }
    var value: Value? = fields[firstSegment]
    for (segment in segments) {
      if (value == null || !value.hasMapValue()) {
        return null
      }
      value = value.mapValue.getFieldsOrDefault(segment, null)
    }
    return value
  }

  /**
   * Retrieves the field specified by [field].
   *
   * @param field The field path (e.g. "foo" or "foo.bar") to a specific field.
   * @return The data at the specified field location or null if no such field exists.
   */
  fun get(field: String): Any? = get(FieldPath.fromDotSeparatedPath(field))

  /**
   * Retrieves the field specified by [fieldPath].
   *
   * @param fieldPath The field path to a specific field.
   * @return The data at the specified field location or null if no such field exists.
   */
  fun get(fieldPath: FieldPath): Any? = userDataWriter.convertValue(extractNestedValue(fieldPath))

  override fun toString() = "PipelineResult{ref=$ref, updateTime=$updateTime}, data=${getData()}"
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as PipelineResult
    if (ref != other.ref) return false
    if (fields != other.fields) return false
    return true
  }

  override fun hashCode(): Int {
    var result = ref?.hashCode() ?: 0
    result = 31 * result + fields.hashCode()
    return result
  }
}

internal interface PipelineResultObserver {
  fun onDocument(
    key: DocumentKey?,
    data: Map<String, Value>,
    createTime: Timestamp?,
    updateTime: Timestamp?
  )
  fun onComplete(executionTime: Timestamp)
  fun onError(exception: FirebaseFirestoreException)
}
