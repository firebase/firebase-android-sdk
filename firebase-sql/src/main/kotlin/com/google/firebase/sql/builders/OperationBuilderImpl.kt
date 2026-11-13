/*
 * Copyright 2026 Google LLC
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

package com.google.firebase.sql.builders

import com.google.firebase.sql.Count
import com.google.firebase.sql.ExplainFormat
import com.google.firebase.sql.FilterOperator
import com.google.firebase.sql.FirebaseSqlException
import com.google.firebase.sql.FirebaseSqlExceptionCategory
import com.google.firebase.sql.OperationResult
import com.google.firebase.sql.Order
import com.google.firebase.sql.RpcMethod
import com.google.firebase.sql.TextSearchType
import com.google.firebase.sql.core.CLOSED_MESSAGE
import com.google.firebase.sql.core.JsonValueEncoder
import com.google.firebase.sql.core.OperationsExecutor
import com.google.firebase.sql.core.QueryRequest
import com.google.firebase.sql.core.defaultJson
import com.google.firebase.sql.core.requireNotReservedHeader
import java.util.TreeMap
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * The implementation of [QueryBuilder], [FilterBuilder], and [TransformBuilder].
 *
 * A builder is immutable: every method returns a new builder with the change, and leaves the
 * builder that it is called on unchanged. A builder can therefore be the base of several requests,
 * be executed more than once, and be shared between threads.
 *
 * [toRequest] turns the [State] into the PostgREST request: the HTTP method, the path, the query
 * parameters (`select`, `columns`, `on_conflict`, the filters, `limit`, `offset`, and `order`), the
 * `Accept` and `Prefer` headers, the headers of `setHeader()`, and the body. [execute] sends that
 * request with the [OperationsExecutor].
 */
internal class OperationBuilderImpl
private constructor(private val executor: OperationsExecutor, private val state: State) :
  QueryBuilder, FilterBuilder, TransformBuilder {

  /**
   * Creates a builder for the table or view named [tableName].
   *
   * @throws IllegalArgumentException If [tableName] is empty, `.`, or `..`.
   */
  constructor(
    tableName: String,
    executor: OperationsExecutor
  ) : this(executor, tableState(tableName))

  /** The media type of the response. The last call to a format modifier wins. */
  private enum class ResponseFormat {
    DEFAULT,
    SINGLE,
    MAYBE_SINGLE,
    CSV,
    GEOJSON
  }

  /**
   * Everything that the calls add to the request. A value is never changed once it is in a state,
   * so the builders can share the values.
   */
  private data class State(
    /** The table, or `rpc` and the function, each sent as one path segment. */
    val pathSegments: List<String>,
    val httpMethod: String = "GET",
    val body: Any? = null,
    val selectColumns: String? = null,
    val columns: String? = null,
    val onConflict: String? = null,
    val filters: List<Pair<String, String>> = emptyList(),
    // A null key means the top-level table, any other key an embedded resource.
    val limits: Map<String?, Long> = emptyMap(),
    val offsets: Map<String?, Long> = emptyMap(),
    val orders: List<Pair<String?, String>> = emptyList(),
    val responseFormat: ResponseFormat = ResponseFormat.DEFAULT,
    val stripNulls: Boolean = false,
    val explainFormat: String? = null,
    val explainOptions: List<String> = emptyList(),
    val returnRepresentation: Boolean = false,
    val countMode: Count? = null,
    val missingDefault: Boolean = false,
    val resolution: String? = null,
    val txRollback: Boolean = false,
    val maxAffected: Long? = null,
    val retryEnabled: Boolean = true,
    /** `true` for a call of a PostgreSQL function, made with `rpc()`. */
    val isFunctionCall: Boolean = false,
    /** A case-insensitive map, which is copied for each new header. */
    val customHeaders: Map<String, String> = emptyMap()
  ) {
    /** `true` for a GET or HEAD request, which changes nothing. */
    val isRead: Boolean
      get() = httpMethod == "GET" || httpMethod == "HEAD"

    /** `true` for an update or a delete, whose rows a top-level limit does not limit. */
    val isUpdateOrDelete: Boolean
      get() = httpMethod == "PATCH" || httpMethod == "DELETE"
  }

  /** Returns a new builder with the state that [change] returns. */
  private inline fun next(change: State.() -> State): OperationBuilderImpl =
    OperationBuilderImpl(executor, state.change())

  // --- QueryBuilder ---

  override fun select(columns: String, head: Boolean, count: Count?): FilterBuilder {
    val selectColumns = PostgrestSyntax.cleanSelect(columns)
    return next {
      copy(
        selectColumns = selectColumns,
        httpMethod = if (head) "HEAD" else "GET",
        countMode = count
      )
    }
  }

  override fun insert(
    data: Map<String, Any?>,
    count: Count?,
    defaultToNull: Boolean
  ): FilterBuilder = insertBody(JsonValueEncoder.encode(data), count, defaultToNull)

  override fun insert(
    data: List<Map<String, Any?>>,
    count: Count?,
    defaultToNull: Boolean
  ): FilterBuilder = insertBody(JsonValueEncoder.encode(data), count, defaultToNull)

  override fun <T> insert(
    data: T,
    serializer: SerializationStrategy<T>,
    count: Count?,
    defaultToNull: Boolean
  ): FilterBuilder =
    insertBody(defaultJson.encodeToJsonElement(serializer, data), count, defaultToNull)

  private fun insertBody(body: Any, count: Count?, defaultToNull: Boolean): FilterBuilder {
    requireRows(body)
    return next { inserting(body, count, defaultToNull) }
  }

  override fun upsert(
    data: Map<String, Any?>,
    onConflict: String?,
    ignoreDuplicates: Boolean,
    count: Count?,
    defaultToNull: Boolean
  ): FilterBuilder =
    upsertBody(JsonValueEncoder.encode(data), onConflict, ignoreDuplicates, count, defaultToNull)

  override fun upsert(
    data: List<Map<String, Any?>>,
    onConflict: String?,
    ignoreDuplicates: Boolean,
    count: Count?,
    defaultToNull: Boolean
  ): FilterBuilder =
    upsertBody(JsonValueEncoder.encode(data), onConflict, ignoreDuplicates, count, defaultToNull)

  override fun <T> upsert(
    data: T,
    serializer: SerializationStrategy<T>,
    onConflict: String?,
    ignoreDuplicates: Boolean,
    count: Count?,
    defaultToNull: Boolean
  ): FilterBuilder =
    upsertBody(
      defaultJson.encodeToJsonElement(serializer, data),
      onConflict,
      ignoreDuplicates,
      count,
      defaultToNull
    )

  private fun upsertBody(
    body: Any,
    onConflict: String?,
    ignoreDuplicates: Boolean,
    count: Count?,
    defaultToNull: Boolean
  ): FilterBuilder {
    requireRows(body)
    require(onConflict == null || onConflict.isNotBlank()) {
      "onConflict must not be blank; pass null to resolve the conflicts on the primary key."
    }
    val resolution = if (ignoreDuplicates) "ignore-duplicates" else "merge-duplicates"
    return next {
      // Without `on_conflict`, PostgREST resolves the conflicts on the primary key.
      inserting(body, count, defaultToNull).copy(onConflict = onConflict, resolution = resolution)
    }
  }

  override fun update(data: Map<String, Any?>, count: Count?): FilterBuilder =
    updateBody(JsonValueEncoder.encode(data), count)

  override fun <T> update(
    data: T,
    serializer: SerializationStrategy<T>,
    count: Count?
  ): FilterBuilder = updateBody(defaultJson.encodeToJsonElement(serializer, data), count)

  private fun updateBody(body: Any, count: Count?): FilterBuilder {
    // PostgREST sets the columns of the object in the matched rows, and rejects other values.
    require(body is JsonObject) {
      "The data of an update must be an object, like a Map or a @Serializable class, but is " +
        "${if (body is JsonArray) "a list" else "a single value"}."
    }
    return next { copy(httpMethod = "PATCH", body = body, countMode = count) }
  }

  override fun delete(count: Count?): FilterBuilder = next {
    copy(httpMethod = "DELETE", body = null, countMode = count)
  }

  /** The state of an INSERT of [body], which is also the start of an UPSERT. */
  private fun State.inserting(body: Any?, count: Count?, defaultToNull: Boolean): State =
    copy(
      httpMethod = "POST",
      body = body,
      // PostgREST rejects a list of objects with different keys unless `columns` lists them.
      columns = columnsOf(body),
      countMode = count,
      missingDefault = !defaultToNull
    )

  // --- FilterBuilder ---

  override fun eq(column: String, value: Any) = append(column, "eq", value)
  override fun neq(column: String, value: Any) = append(column, "neq", value)
  override fun gt(column: String, value: Any) = append(column, "gt", value)
  override fun gte(column: String, value: Any) = append(column, "gte", value)
  override fun lt(column: String, value: Any) = append(column, "lt", value)
  override fun lte(column: String, value: Any) = append(column, "lte", value)

  override fun like(column: String, pattern: String) = append(column, "like.$pattern")
  override fun ilike(column: String, pattern: String) = append(column, "ilike.$pattern")
  override fun likeAll(column: String, patterns: List<String>) =
    append(column, "like(all).${PostgrestSyntax.arrayLiteral(patterns)}")
  override fun likeAny(column: String, patterns: List<String>) =
    append(column, "like(any).${PostgrestSyntax.arrayLiteral(patterns)}")
  override fun ilikeAll(column: String, patterns: List<String>) =
    append(column, "ilike(all).${PostgrestSyntax.arrayLiteral(patterns)}")
  override fun ilikeAny(column: String, patterns: List<String>) =
    append(column, "ilike(any).${PostgrestSyntax.arrayLiteral(patterns)}")

  override fun match(query: Map<String, Any>): FilterBuilder {
    val expressions =
      query.map { (column, value) ->
        PostgrestSyntax.filterKey(column) to "eq.${PostgrestSyntax.filterValue(value)}"
      }
    return next { copy(filters = filters + expressions) }
  }

  override fun regexMatch(column: String, pattern: String) = append(column, "match.$pattern")
  override fun regexIMatch(column: String, pattern: String) = append(column, "imatch.$pattern")

  override fun exact(column: String, value: Boolean?): FilterBuilder =
    append(column, "is.${value ?: "null"}")

  override fun isDistinct(column: String, value: Any) = append(column, "isdistinct", value)

  override fun isIn(column: String, values: List<Any>) = append(column, "in", values)
  override fun notIn(column: String, values: List<Any>) = append(column, "not.in", values)
  override fun contains(column: String, values: List<Any>) =
    append(column, "cs.${PostgrestSyntax.arrayLiteral(values)}")
  override fun contained(column: String, values: List<Any>) =
    append(column, "cd.${PostgrestSyntax.arrayLiteral(values)}")
  override fun overlaps(column: String, values: List<Any>) =
    append(column, "ov.${PostgrestSyntax.arrayLiteral(values)}")

  override fun rangeLt(column: String, range: String) = append(column, "sl.$range")
  override fun rangeGt(column: String, range: String) = append(column, "sr.$range")
  override fun rangeGte(column: String, range: String) = append(column, "nxl.$range")
  override fun rangeLte(column: String, range: String) = append(column, "nxr.$range")
  override fun adjacent(column: String, range: String) = append(column, "adj.$range")

  override fun textSearch(
    column: String,
    query: String,
    textSearchType: TextSearchType,
    config: String?
  ): FilterBuilder = append(column, "${textSearchType.rawValue}${configSuffix(config)}.$query")

  override fun or(expr: String, referencedTable: String?) =
    appendParameter(if (referencedTable != null) "$referencedTable.or" else "or", "($expr)")
  override fun and(expr: String, referencedTable: String?) =
    appendParameter(if (referencedTable != null) "$referencedTable.and" else "and", "($expr)")

  override fun filter(column: String, operator: FilterOperator, value: Any?) =
    append(column, operator.rawValue, value)
  override fun filterNot(column: String, operator: FilterOperator, value: Any?) =
    append(column, "not.${operator.rawValue}", value)

  private fun configSuffix(config: String?): String {
    if (config == null) return ""
    // PostgREST reads the configuration as an identifier, so `pg_catalog.english` fails to parse.
    require(TEXT_SEARCH_CONFIG.matches(config)) {
      "The text search configuration must be a name made of letters, digits, and underscores, " +
        "for example \"english\", but is '$config'."
    }
    return "($config)"
  }

  private fun append(column: String, operator: String, value: Any?): FilterBuilder {
    val baseOperator = operator.removePrefix("not.")
    val negated = operator != baseOperator
    if (baseOperator == "in" && value is Iterable<*>) {
      val values = value.toList()
      // PostgREST reads `in.("")` as an empty list, which matches no row, so the only value is
      // compared with `eq` instead.
      if (values.size == 1 && PostgrestSyntax.filterValue(values.single()).isEmpty()) {
        return append(column, if (negated) "not.eq." else "eq.")
      }
      return append(column, "$operator.${PostgrestSyntax.list(values)}")
    }
    val formatted =
      if (value is Iterable<*> && baseOperator.takesArrayLiteral()) {
        PostgrestSyntax.arrayLiteral(value.toList())
      } else {
        PostgrestSyntax.filterValue(value)
      }
    return append(column, "$operator.$formatted")
  }

  /** Whether PostgREST reads the value of this operator as a PostgreSQL array literal. */
  private fun String.takesArrayLiteral(): Boolean =
    this in ARRAY_OPERATORS || endsWith("(any)") || endsWith("(all)")

  /** Adds a filter on [column], which is quoted when PostgREST would read it as a parameter. */
  private fun append(column: String, expression: String): FilterBuilder =
    appendParameter(PostgrestSyntax.filterKey(column), expression)

  private fun appendParameter(name: String, expression: String): FilterBuilder = next {
    copy(filters = filters + (name to expression))
  }

  // --- TransformBuilder ---

  override fun select(columns: String): FilterBuilder {
    val selectColumns = PostgrestSyntax.cleanSelect(columns)
    return next {
      // Selecting the columns of a mutation returns the mutated rows.
      copy(selectColumns = selectColumns, returnRepresentation = returnRepresentation || !isRead)
    }
  }

  override fun order(
    column: String,
    order: Order,
    nullsFirst: Boolean?,
    referencedTable: String?
  ): FilterBuilder {
    val direction =
      when (order) {
        Order.ASCENDING -> "asc"
        Order.DESCENDING -> "desc"
      }
    // Without a position, PostgreSQL puts the nulls last in ascending order and first in
    // descending order, which a default index can return without sorting.
    val nulls =
      when (nullsFirst) {
        null -> ""
        true -> ".nullsfirst"
        false -> ".nullslast"
      }
    return addOrder(referencedTable, "$column.$direction$nulls")
  }

  private fun addOrder(referencedTable: String?, term: String): FilterBuilder = next {
    copy(orders = orders + (referencedTable to term))
  }

  override fun limit(count: Long, referencedTable: String?): FilterBuilder {
    require(count >= 0) { "limit() needs a count of 0 or more, but the count is $count." }
    checkLimitOfMutation("limit()", referencedTable)
    return next { copy(limits = limits + (referencedTable to count)) }
  }

  override fun range(from: Long, to: Long, referencedTable: String?): FilterBuilder {
    require(from >= 0 && to >= from) {
      "range() needs 0 <= from <= to, but from is $from and to is $to."
    }
    // The limit, to - from + 1, must fit in a Long.
    require(to - from < Long.MAX_VALUE) {
      "range() returns at most Long.MAX_VALUE rows, but from is $from and to is $to."
    }
    checkLimitOfMutation("range()", referencedTable)
    val limit = to - from + 1
    return next {
      copy(
        offsets = offsets + (referencedTable to from),
        limits = limits + (referencedTable to limit)
      )
    }
  }

  override fun range(range: LongRange, referencedTable: String?): FilterBuilder =
    range(range.first, range.last, referencedTable)

  /**
   * Rejects a top-level limit on an update or a delete: PostgREST would apply it only to the rows
   * that the response returns, and still change every row that the filters match.
   */
  private fun checkLimitOfMutation(method: String, referencedTable: String?) {
    check(referencedTable != null || !state.isUpdateOrDelete) {
      "$method does not limit the rows that an update() or a delete() changes: every row that the " +
        "filters match would still change. Use maxAffected() to limit them, or pass a " +
        "referencedTable to limit the embedded rows that select() returns."
    }
  }

  override fun single(): TransformBuilder = next { copy(responseFormat = ResponseFormat.SINGLE) }

  override fun maybeSingle(): TransformBuilder = next {
    copy(responseFormat = ResponseFormat.MAYBE_SINGLE)
  }

  override fun csv(): TransformBuilder {
    check(!state.stripNulls) { STRIP_NULLS_WITH_CSV }
    return next { copy(responseFormat = ResponseFormat.CSV) }
  }

  override fun geojson(): TransformBuilder = next { copy(responseFormat = ResponseFormat.GEOJSON) }

  override fun stripNulls(): FilterBuilder {
    check(state.responseFormat != ResponseFormat.CSV) { STRIP_NULLS_WITH_CSV }
    return next { copy(stripNulls = true) }
  }

  override fun dryRun(): FilterBuilder = next { copy(txRollback = true) }

  override fun explain(
    analyze: Boolean,
    verbose: Boolean,
    settings: Boolean,
    buffers: Boolean,
    wal: Boolean,
    format: ExplainFormat
  ): TransformBuilder {
    val options = buildList {
      if (analyze) add("analyze")
      if (verbose) add("verbose")
      if (settings) add("settings")
      if (buffers) add("buffers")
      if (wal) add("wal")
    }
    return next { copy(explainFormat = format.rawValue, explainOptions = options) }
  }

  override fun maxAffected(count: Long): FilterBuilder {
    require(count >= 0) { "maxAffected() needs a count of 0 or more, but the count is $count." }
    check(state.isUpdateOrDelete || state.isFunctionCall) {
      "maxAffected() works only with update(), delete(), and rpc(): PostgREST ignores it for " +
        "other requests, so it would not limit the rows that they change."
    }
    return next { copy(maxAffected = count) }
  }

  override fun noRetry(): FilterBuilder = next { copy(retryEnabled = false) }

  override fun setHeader(name: String, value: String): FilterBuilder {
    requireNotReservedHeader(name, "setHeader()")
    val headers = TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER)
    headers.putAll(state.customHeaders)
    headers[name] = value
    return next { copy(customHeaders = headers) }
  }

  /** Returns the PostgREST request that this builder describes. */
  fun toRequest(): QueryRequest {
    val headers = TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER)
    acceptHeader()?.let { headers["Accept"] = it }
    val preferences = preferences()
    if (preferences.isNotEmpty()) {
      headers["Prefer"] = preferences.joinToString(",")
    }
    for ((name, value) in state.customHeaders) {
      val computed = headers[name]
      // PostgREST uses the first value of each preference, so the custom preferences go first.
      headers[name] =
        if (computed != null && name.equals("Prefer", ignoreCase = true)) "$value,$computed"
        else value
    }

    return QueryRequest(
      httpMethod = state.httpMethod,
      pathSegments = state.pathSegments,
      queryParams = buildQueryParams(),
      headers = headers,
      body = state.body,
      retryEnabled = state.retryEnabled
    )
  }

  override suspend fun execute(): OperationResult {
    check(!executor.isClosed) { CLOSED_MESSAGE }
    val response = executor.execute(toRequest())
    val maybeSingle =
      state.responseFormat == ResponseFormat.MAYBE_SINGLE && state.explainFormat == null

    response.error?.let { error ->
      // A mutation or a POST function call with maybeSingle() asks PostgREST for one object, so a
      // request without rows gets the error PGRST116, which means no row here.
      if (maybeSingle && error is FirebaseSqlException && error.isNoRowsError()) {
        return OperationResult(data = "null", status = 200, statusText = "OK")
      }
      throw error
    }

    // With explain(), the response contains the plan instead of the rows, and PostgREST omits
    // the Preference-Applied header.
    if (state.txRollback && state.explainFormat == null) {
      val applied =
        response.preferenceApplied?.split(',')?.any {
          it.trim().equals("tx=rollback", ignoreCase = true)
        } == true
      if (!applied) {
        throw FirebaseSqlException(
          message =
            "The server did not apply Prefer: tx=rollback, so the changes were committed. " +
              "To allow it, set pgrst.db_tx_end to 'commit-allow-override' in " +
              "postgrest.pre_config() of the database.",
          status = response.status,
          // The response succeeded; the SDK detected the problem.
          category = FirebaseSqlExceptionCategory.CLIENT
        )
      }
    }

    val data = response.body ?: ""
    // A read with maybeSingle() asks for a JSON array, which is reduced to at most one row here.
    val rows =
      if (maybeSingle && state.isRead) {
        runCatching { Json.parseToJsonElement(data) }.getOrNull() as? JsonArray
      } else {
        null
      }
    if (rows != null && rows.size > 1) {
      throw FirebaseSqlException(
        message = "Cannot coerce the result to a single JSON object",
        category = FirebaseSqlExceptionCategory.POSTGREST,
        code = NOT_SINGULAR_CODE,
        status = NOT_ACCEPTABLE,
        details = "The result contains ${rows.size} rows"
      )
    }
    return OperationResult(
      data = if (rows != null) rows.firstOrNull()?.toString() ?: "null" else data,
      status = response.status,
      statusText = response.statusText,
      count = response.count,
      range = response.range
    )
  }

  /** Whether this is the `PGRST116` error of a request that asked for one object and got no row. */
  private fun FirebaseSqlException.isNoRowsError(): Boolean =
    code == NOT_SINGULAR_CODE && NO_ROWS.containsMatchIn(details.orEmpty())

  /** The `Accept` header, or `null` to accept the default response of PostgREST. */
  private fun acceptHeader(): String? {
    val mediaType = responseMediaType()
    val format = state.explainFormat ?: return mediaType
    val options =
      if (state.explainOptions.isEmpty()) ""
      else "; options=${state.explainOptions.joinToString("|")}"
    return "application/vnd.pgrst.plan+$format; for=\"${mediaType ?: "application/json"}\"$options"
  }

  private fun responseMediaType(): String? {
    // PostgREST only strips nulls for its own array and object media types.
    val arrayMediaType = if (state.stripNulls) "$ARRAY_MEDIA_TYPE;nulls=stripped" else null
    val objectMediaType =
      if (state.stripNulls) "$OBJECT_MEDIA_TYPE;nulls=stripped" else OBJECT_MEDIA_TYPE
    return when (state.responseFormat) {
      ResponseFormat.DEFAULT -> arrayMediaType
      ResponseFormat.SINGLE -> objectMediaType
      ResponseFormat.MAYBE_SINGLE ->
        if (state.isRead) arrayMediaType ?: "application/json" else objectMediaType
      ResponseFormat.CSV -> "text/csv"
      ResponseFormat.GEOJSON -> "application/geo+json"
    }
  }

  private fun preferences(): List<String> = buildList {
    if (state.returnRepresentation) add("return=representation")
    state.countMode?.let { add("count=${it.rawValue}") }
    if (state.missingDefault) add("missing=default")
    state.resolution?.let { add("resolution=$it") }
    if (state.txRollback) add("tx=rollback")
    state.maxAffected?.let {
      add("handling=strict")
      add("max-affected=$it")
    }
  }

  private fun buildQueryParams(): List<Pair<String, String>> {
    val params = mutableListOf<Pair<String, String>>()
    state.selectColumns?.let { params.add("select" to it) }
    state.columns?.let { params.add("columns" to it) }
    state.onConflict?.let { params.add("on_conflict" to it) }
    params.addAll(state.filters)

    state.limits.forEach { (ref, limit) ->
      params.add((if (ref != null) "$ref.limit" else "limit") to limit.toString())
    }
    state.offsets.forEach { (ref, offset) ->
      params.add((if (ref != null) "$ref.offset" else "offset") to offset.toString())
    }
    // PostgREST reads only the first `order` parameter of a table, so the terms are joined.
    state.orders.groupBy({ it.first }, { it.second }).forEach { (ref, orders) ->
      params.add((if (ref != null) "$ref.order" else "order") to orders.joinToString(","))
    }
    // The server treats the query parameters whose names start with `$` as system parameters and
    // drops them. A filter on such a column would be lost without an error, and an update() or a
    // delete() could then change every row.
    params
      .firstOrNull { (name, _) -> name.startsWith("\$") }
      ?.let { (name, _) ->
        throw IllegalArgumentException(
          "The query parameter '$name' starts with '\$', which the server ignores, so the request " +
            "would run without it."
        )
      }
    return params
  }

  companion object {
    /**
     * Returns a builder that calls the PostgreSQL function named [functionName], which PostgREST
     * exposes at `/rpc/<functionName>`.
     *
     * With POST, the default, the arguments are sent as a JSON object in the request body. With GET
     * or HEAD, each argument is sent as a query parameter with its name, before the filters, and
     * PostgREST runs the function in a read-only transaction. See
     * [com.google.firebase.sql.FirebaseSql.rpc] for the parameters.
     *
     * @param params `null`, a `Map`, or a `JsonElement` of the arguments. An object of a class
     * annotated with `@Serializable` is encoded to a `JsonElement` with its serializer first.
     * @throws IllegalArgumentException When [params] is not an object, or when [functionName] is
     * empty, `.`, or `..`.
     */
    fun rpc(
      functionName: String,
      params: Any? = null,
      method: RpcMethod = RpcMethod.POST,
      count: Count? = null,
      executor: OperationsExecutor
    ): OperationBuilderImpl {
      requireOnePathSegment(functionName, "function")
      val pathSegments = listOf("rpc", functionName)
      val state =
        if (method != RpcMethod.POST) {
          State(
            pathSegments = pathSegments,
            httpMethod = method.rawValue,
            filters = params?.let { PostgrestSyntax.functionArguments(it) }.orEmpty(),
            countMode = count,
            isFunctionCall = true
          )
        } else {
          // A call without arguments sends an empty JSON object.
          val body = JsonValueEncoder.encode(params ?: emptyMap<String, Any?>())
          // PostgREST calls the function with the keys of an object as arguments. It rejects a list
          // of objects with different keys, calls the function with only the first object of any
          // other list, and fails on other values.
          require(body is JsonObject) {
            "The arguments of a function must be an object, like a Map or a @Serializable class, " +
              "but are ${if (body is JsonArray) "a list" else "a single value"}."
          }
          State(
            pathSegments = pathSegments,
            httpMethod = "POST",
            body = body,
            countMode = count,
            isFunctionCall = true
          )
        }
      return OperationBuilderImpl(executor, state)
    }

    /** Returns the state of a request on the table or view named [tableName]. */
    private fun tableState(tableName: String): State {
      requireOnePathSegment(tableName, "table")
      return State(pathSegments = listOf(tableName))
    }

    /**
     * Checks that [name] stays one segment of the URL path. OkHttp drops a `.` segment and removes
     * the segment before a `..` one, and an empty name changes the path too.
     */
    private fun requireOnePathSegment(name: String, kind: String) {
      require(name.isNotEmpty() && name != "." && name != "..") {
        "The $kind name must not be empty, \".\", or \"..\", but is '$name'."
      }
    }

    private const val ARRAY_MEDIA_TYPE = "application/vnd.pgrst.array+json"
    private const val OBJECT_MEDIA_TYPE = "application/vnd.pgrst.object+json"
    private const val NOT_SINGULAR_CODE = "PGRST116"
    private const val NOT_ACCEPTABLE = 406

    /** The operators whose value PostgREST reads as a PostgreSQL array literal. */
    private val ARRAY_OPERATORS = setOf("cs", "cd", "ov")

    /** A text search configuration, which PostgREST reads as an identifier. */
    private val TEXT_SEARCH_CONFIG = Regex("[A-Za-z_][A-Za-z0-9_]*")

    /** PostgREST strips nulls only from JSON, so `stripNulls()` would have no effect on CSV. */
    private const val STRIP_NULLS_WITH_CSV = "stripNulls() cannot be used with csv()"

    /** Matches the details of the `PGRST116` error for zero rows, but not for 10 rows. */
    private val NO_ROWS = Regex("(^|\\D)0 rows")

    /**
     * Checks that the data of an insert or an upsert is rows: an object, or a list of objects. For
     * example, a `String` is encoded as a JSON string, which PostgREST rejects.
     */
    private fun requireRows(body: Any) {
      require(body is JsonObject || body is JsonArray && body.all { it is JsonObject }) {
        "The data of an insert or an upsert must be an object or a list of objects, like a Map or " +
          "a @Serializable class, but is " +
          "${if (body is JsonArray) "a list of other values" else "a single value"}."
      }
    }

    /**
     * The `columns` query parameter for a list of objects: the union of their keys, in the order in
     * which they first appear. A key that is missing from an object is inserted as `NULL`, or as
     * the column default with `missing=default`.
     */
    private fun columnsOf(body: Any?): String? {
      val rows = body as? JsonArray ?: return null
      val names = LinkedHashSet<String>()
      rows.forEach { row -> if (row is JsonObject) names.addAll(row.keys) }
      return if (names.isEmpty()) null else PostgrestSyntax.columns(names)
    }
  }
}
