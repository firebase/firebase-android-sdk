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

import com.google.firebase.annotations.InternalForInheritanceFirebaseApi
import com.google.firebase.sql.ExplainFormat
import com.google.firebase.sql.FirebaseSqlException
import com.google.firebase.sql.OperationResult
import com.google.firebase.sql.Order
import com.google.firebase.sql.ReservedHeaderException

/**
 * The modifiers of a request, and [execute], which sends it.
 *
 * [FilterBuilder] extends this interface. Write a chain in the order that the database runs it: the
 * operation, the filters, then the modifiers, and [execute] last:
 * ```kotlin
 * val products =
 *   sql
 *     .from("products") // Returns a QueryBuilder.
 *     .select("id, name, price") // The operation returns a FilterBuilder.
 *     .gte("price", 1) // The filters.
 *     .order("price") // The modifiers.
 *     .limit(20)
 *     .execute() // Sends the request.
 *     .decodeList<Product>()
 * ```
 *
 * The modifiers set:
 * - The order and the range of the rows: [order], [limit], and [range].
 * - The columns of the rows, and their `null` values: [select] and [stripNulls].
 * - The type of the response, instead of a JSON array of rows: one object with [single] and
 * [maybeSingle], CSV with [csv], GeoJSON with [geojson], or the plan of the query with [explain].
 * - How a mutation runs: [dryRun] and [maxAffected].
 * - How the request is sent: [setHeader] and [noRetry].
 *
 * **Filters after modifiers:** On a [FilterBuilder], every modifier returns a [FilterBuilder], so
 * filters can still follow it, except the ones that change the type of the response: [single],
 * [maybeSingle], [csv], [geojson], and [explain] return a [TransformBuilder], which has no filter
 * methods. A filter after a modifier sends the same request as before it: the database filters the
 * rows before it sorts them and returns the requested range. Write the filters first anyway, so
 * that the code reads in that order.
 *
 * Builders are immutable: each method returns a new builder, and leaves the builder that it is
 * called on unchanged. Always use the returned builder. A builder can be the base of several
 * requests, be executed more than once, and be shared between threads:
 * ```kotlin
 * val products = sql.from("products").select()
 * val cheap = products.lt("price", 5) // `products` still selects every product.
 * products.limit(10) // Has no effect: the returned builder is dropped.
 * val firstTen = products.order("id").limit(10) // Uses the returned builder.
 * ```
 *
 * [execute] is a suspend function, so call it from a coroutine. It returns an [OperationResult], or
 * throws when the request fails: a [FirebaseSqlException] when the server rejects it, or an
 * `IOException` when no response arrives. Each call sends a new request, and cancelling the
 * coroutine cancels the request.
 *
 * **Not stable for inheritance:** Later versions can add members to this interface, so implementing
 * it requires an opt-in to [InternalForInheritanceFirebaseApi]. Get a builder from the other
 * builders instead, and mock it in tests.
 */
@SubclassOptInRequired(InternalForInheritanceFirebaseApi::class)
public interface TransformBuilder {
  /**
   * Makes an insert, an upsert, an update, or a delete return the rows that it changed, with the
   * given [columns].
   *
   * PostgREST then returns the inserted, updated, or deleted rows as a JSON array, or as one object
   * with [single] or [maybeSingle]. [columns] uses the syntax of [QueryBuilder.select], so it can
   * rename columns and embed linked tables. On a read, it replaces the columns of
   * [QueryBuilder.select]. On a function call, it chooses the columns of the rows that the function
   * returns.
   *
   * PostgREST: `select=<columns>`, plus `Prefer: return=representation` for an insert, an upsert,
   * an update, a delete, or a function call with POST.
   *
   * **Example: get the values that the database filled in**
   * ```kotlin
   * @Serializable
   * data class NewProduct(val id: Long, @SerialName("created_at") val createdAt: String)
   *
   * val created =
   *   sql
   *     .from("products")
   *     .insert(mapOf("id" to 11, "name" to "Cherry", "price" to 4.0))
   *     .select("id, created_at")
   *     .single()
   *     .execute()
   *     .decodeSingle<NewProduct>()
   * ```
   * Response:
   * ```json
   * {"id": 11, "created_at": "2026-10-13T18:49:24.123456+00:00"}
   * ```
   *
   * **Example: update rows and return them with their category**
   * ```kotlin
   * val moved =
   *   sql
   *     .from("products")
   *     .update(mapOf("category_id" to 2))
   *     .eq("id", 11)
   *     .select("name, categories(name)")
   *     .execute()
   * ```
   * Response:
   * ```json
   * [{"name": "Cherry", "categories": {"name": "Berries"}}]
   * ```
   *
   * @param columns The columns to return, in the syntax of [QueryBuilder.select]. `*` (the default)
   * returns every column.
   * @return A new builder that returns the rows, on which filters can still be added.
   */
  public fun select(columns: String = "*"): FilterBuilder

  /**
   * Sorts the rows by [column], in the direction of [order].
   *
   * Each call adds a sort key after the keys of the earlier calls, so the first call gives the main
   * order. Without a call to `order()`, the rows come in no particular order, so always order the
   * rows that you page through with [range] or [limit]. `NULL` values come last in ascending order
   * and first in descending order, unless [nullsFirst] says otherwise.
   *
   * [column] can be a value inside a `json` or `jsonb` column, for example `details->>color`. With
   * [referencedTable], the call sorts the rows of an embedded table inside each row instead.
   *
   * PostgREST: `order=<column>.<asc|desc>[.nullsfirst|.nullslast]`, with the keys of all the calls
   * joined by commas, for example `order=price.desc,name.asc`. With [referencedTable], the keys go
   * in `<referencedTable>.order`.
   *
   * **Example: sort by several columns**
   * ```kotlin
   * // order=price.desc,name.asc
   * val products =
   *   sql
   *     .from("products")
   *     .select()
   *     .order("price", Order.DESCENDING)
   *     .order("name")
   *     .execute()
   *     .decodeList<Product>()
   * ```
   *
   * **Example: put the `NULL` values first**
   * ```kotlin
   * // order=deleted_at.asc.nullsfirst: the products that are not deleted come first.
   * val products =
   *   sql.from("products").select().order("deleted_at", nullsFirst = true).execute()
   * ```
   *
   * **Example: sort the rows of an embedded table**
   * ```kotlin
   * // order=name.asc&products.order=price.desc
   * val categories =
   *   sql
   *     .from("categories")
   *     .select("name, products(name, price)")
   *     .order("name")
   *     .order("price", Order.DESCENDING, referencedTable = "products")
   *     .execute()
   * ```
   * Response:
   * ```json
   * [
   *   {
   *     "name": "Fruit",
   *     "products": [{"name": "Mango", "price": 2.5}, {"name": "Apple", "price": 1.5}]
   *   }
   * ]
   * ```
   *
   * @param column The column to sort by, or a path inside a JSON column, such as `details->>color`.
   * With [referencedTable], a column of the embedded table.
   * @param order [Order.ASCENDING] (the default) or [Order.DESCENDING].
   * @param nullsFirst `true` to put the `NULL` values first, `false` to put them last, or `null`
   * (the default) for the order of PostgreSQL: last in ascending order, first in descending order.
   * @param referencedTable The embedded table whose rows to sort, as named in the `select` columns,
   * or its alias. `null` (the default) sorts the top-level rows.
   * @return A new builder with the sort key added.
   */
  public fun order(
    column: String,
    order: Order = Order.ASCENDING,
    nullsFirst: Boolean? = null,
    referencedTable: String? = null
  ): TransformBuilder

  /**
   * Returns at most [count] rows.
   *
   * A later call to [limit] or [range] for the same [referencedTable] replaces the limit. Use it
   * with [order], so that the same rows come back each time. With [referencedTable], it limits the
   * rows of an embedded table inside each row instead.
   *
   * On an update or a delete, PostgREST would apply a top-level limit only to the rows that
   * [select] returns, and still change every row that the filters match, so [limit] throws an
   * [IllegalStateException] there. To limit the rows that a mutation can change, use [maxAffected].
   * A limit with [referencedTable] is allowed.
   *
   * PostgREST: `limit=<count>`, or `<referencedTable>.limit=<count>`.
   *
   * **Example: the five cheapest products**
   * ```kotlin
   * val cheapest =
   *   sql.from("products").select().order("price").limit(5).execute().decodeList<Product>()
   * ```
   *
   * **Example: at most three products in each category**
   * ```kotlin
   * val categories =
   *   sql
   *     .from("categories")
   *     .select("name, products(name)")
   *     .limit(3, referencedTable = "products")
   *     .execute()
   * ```
   *
   * @param count The maximum number of rows, `0` or more.
   * @param referencedTable The embedded table whose rows to limit, as named in the `select`
   * columns, or its alias. `null` (the default) limits the top-level rows.
   * @return A new builder with the limit set.
   * @throws IllegalArgumentException If [count] is negative.
   * @throws IllegalStateException If the request is an update or a delete, and [referencedTable] is
   * `null`.
   */
  public fun limit(count: Long, referencedTable: String? = null): TransformBuilder

  /**
   * Returns the rows from position [from] to position [to], both included, counting from 0.
   *
   * It sets the offset to [from] and the limit to `to - from + 1`, and replaces the [limit] set
   * earlier for the same [referencedTable]; a later [limit] replaces that part of the range. Use it
   * with [order], so that each page holds the same rows each time. To get the total number of rows
   * with a page, pass a `count` to [QueryBuilder.select].
   *
   * On an update or a delete, PostgREST would apply a top-level range only to the rows that
   * [select] returns, and still change every row that the filters match, so [range] throws an
   * [IllegalStateException] there. To limit the rows that a mutation can change, use [maxAffected].
   * A range with [referencedTable] is allowed.
   *
   * PostgREST: `limit=<to - from + 1>&offset=<from>`, or `<referencedTable>.limit` and
   * `<referencedTable>.offset`.
   *
   * **Example: page through the products**
   * ```kotlin
   * val pageSize = 20L
   *
   * suspend fun page(index: Long): List<Product> =
   *   sql
   *     .from("products")
   *     .select()
   *     .order("id")
   *     .range(index * pageSize, (index + 1) * pageSize - 1)
   *     .execute()
   *     .decodeList<Product>()
   *
   * val first = page(0) // Rows 0 to 19.
   * val second = page(1) // Rows 20 to 39.
   * ```
   *
   * **Example: get a page and the total number of rows**
   * ```kotlin
   * val result =
   *   sql
   *     .from("products")
   *     .select(count = Count.EXACT)
   *     .order("id")
   *     .range(0, 19)
   *     .execute()
   * val products = result.decodeList<Product>() // At most 20 products.
   * val total = result.countOrNull() // Every product, not only the 20 of the page.
   * ```
   *
   * @param from The position of the first row, from 0.
   * @param to The position of the last row, which is included. It must not be less than [from].
   * @param referencedTable The embedded table whose rows to return, as named in the `select`
   * columns, or its alias. `null` (the default) applies the range to the top-level rows.
   * @return A new builder with the offset and the limit set.
   * @throws IllegalArgumentException If [from] is negative, if [to] is less than [from], or for
   * `range(0, Long.MAX_VALUE)`, whose number of rows does not fit in a `Long`.
   * @throws IllegalStateException If the request is an update or a delete, and [referencedTable] is
   * `null`.
   */
  public fun range(from: Long, to: Long, referencedTable: String? = null): TransformBuilder

  /**
   * Returns the rows at the positions of [range], both ends included, counting from 0.
   *
   * Same as `range(range.first, range.last, referencedTable)`.
   *
   * **Example: the second page of 20 products**
   * ```kotlin
   * val page = sql.from("products").select().order("id").range(20L..39L).execute()
   * ```
   *
   * @param range The positions of the first and the last row, for example `0L..19L`.
   * @param referencedTable The embedded table whose rows to return, as named in the `select`
   * columns, or its alias. `null` (the default) applies the range to the top-level rows.
   * @return A new builder with the offset and the limit set.
   * @throws IllegalArgumentException If [range] is empty, starts below 0, or is
   * `0L..Long.MAX_VALUE`, whose number of rows does not fit in a `Long`.
   * @throws IllegalStateException If the request is an update or a delete, and [referencedTable] is
   * `null`.
   */
  public fun range(range: LongRange, referencedTable: String? = null): TransformBuilder

  /**
   * Returns the only row of the result as a JSON object instead of an array.
   *
   * The request fails unless the result has exactly one row: PostgREST then answers with a `406`
   * error with the code `PGRST116`, which [execute] throws as a [FirebaseSqlException] whose
   * `details` give the number of rows, for example `The result contains 0 rows`. When an insert, an
   * upsert, an update, or a delete affects more than one row, PostgREST also rolls it back. To get
   * `null` instead of an error when there is no row, use [maybeSingle].
   *
   * Decode the response with `decodeSingle<T>()`.
   *
   * PostgREST: `Accept: application/vnd.pgrst.object+json`
   *
   * **Example: read a product by its ID**
   * ```kotlin
   * val product =
   *   sql
   *     .from("products")
   *     .select("id, name, price")
   *     .eq("id", 1)
   *     .single()
   *     .execute()
   *     .decodeSingle<Product>()
   * ```
   * Response:
   * ```json
   * {"id": 1, "name": "Apple", "price": 1.5}
   * ```
   *
   * **Example: handle a missing row**
   * ```kotlin
   * try {
   *   val result = sql.from("products").select().eq("id", 99).single().execute()
   *   println(result.decodeSingle<Product>())
   * } catch (e: FirebaseSqlException) {
   *   if (e.code == "PGRST116") println("No product 99") else throw e
   * }
   * ```
   *
   * @return A new builder that returns one object. It has no filter methods, so add the filters
   * first.
   */
  public fun single(): TransformBuilder

  /**
   * Returns the only row of the result as a JSON object, or `null` when there is no row.
   *
   * When there is no row, the data of the result is the JSON text `null`. When the result has more
   * than one row, the request fails like with [single], with a `406` error with the code
   * `PGRST116`:
   * - On a read, the SDK asks for a JSON array and keeps its only element. For more than one row,
   * [execute] throws a [FirebaseSqlException] with the status `406`, the code `PGRST116`, and the
   * number of rows in its `details`. The server sends every row that matches, so filter on a unique
   * column.
   * - On an insert, an upsert, an update, a delete, or a function call with POST, the SDK asks
   * PostgREST for one object, like [single]: PostgREST rolls back a mutation that affects more than
   * one row, and the SDK turns the error for no row into the data `null`.
   *
   * Decode the response with `decodeSingleOrNull<T>()`, which returns `null` for the data `null`,
   * and also when the data cannot be decoded into `T`.
   *
   * PostgREST: `Accept: application/json` on a read, and `Accept:
   * application/vnd.pgrst.object+json` otherwise.
   *
   * **Example: read a product that may not exist**
   * ```kotlin
   * val product =
   *   sql
   *     .from("products")
   *     .select()
   *     .eq("id", 42)
   *     .maybeSingle()
   *     .execute()
   *     .decodeSingleOrNull<Product>()
   * if (product == null) println("No product 42")
   * ```
   * Response, when no row matches:
   * ```json
   * null
   * ```
   *
   * **Example: update a row that may not exist**
   * ```kotlin
   * val renamed =
   *   sql
   *     .from("products")
   *     .update(mapOf("name" to "Golden apple"))
   *     .eq("id", 42)
   *     .select()
   *     .maybeSingle()
   *     .execute()
   *     .decodeSingleOrNull<Product>()
   * ```
   *
   * @return A new builder that returns one object, or `null`. It has no filter methods, so add the
   * filters first.
   */
  public fun maybeSingle(): TransformBuilder

  /**
   * Returns the rows as CSV text instead of JSON.
   *
   * The first line holds the column names, and each other line holds one row. A value is put in
   * double quotes when it is empty, or contains a comma, a parenthesis, a double quote, a
   * backslash, or whitespace, and a double quote or a backslash in it is doubled. A `NULL` value is
   * written as nothing. When there is no row, the text has no header line either. Read the text
   * from [OperationResult.data].
   *
   * PostgREST: `Accept: text/csv`
   *
   * **Example: export the products as CSV**
   * ```kotlin
   * val csvText = sql.from("products").select("id, name").order("id").csv().execute().data
   * ```
   * Response:
   * ```text
   * id,name
   * 1,Apple
   * 8,"Gift card"
   * ```
   *
   * @return A new builder that returns CSV. It has no filter methods, so add the filters first.
   * @throws IllegalStateException If [stripNulls] was called earlier in the chain, because nulls
   * are stripped only from JSON.
   */
  public fun csv(): TransformBuilder

  /**
   * Returns the rows as a GeoJSON `FeatureCollection` instead of a JSON array.
   *
   * The database needs the PostGIS extension, and the selected columns need a `geometry` column,
   * which becomes the geometry of each feature; the other columns become its properties. Read the
   * text from [OperationResult.data]. [stripNulls] has no effect on this format.
   *
   * PostgREST: `Accept: application/geo+json`
   *
   * **Example: read the locations as GeoJSON**
   * ```kotlin
   * // Assumes: alter table products add column location geometry(Point, 4326);
   * val featureCollection =
   *   sql.from("products").select("id, name, location").geojson().execute().data
   * ```
   * Response:
   * ```json
   * {
   *   "type": "FeatureCollection",
   *   "features": [
   *     {
   *       "type": "Feature",
   *       "geometry": {"type": "Point", "coordinates": [2.35, 48.86]},
   *       "properties": {"id": 1, "name": "Apple"}
   *     }
   *   ]
   * }
   * ```
   *
   * @return A new builder that returns GeoJSON. It has no filter methods, so add the filters first.
   */
  public fun geojson(): TransformBuilder

  /**
   * Runs the request, then rolls back its transaction, so that the database keeps no change.
   *
   * Use it to test an insert, an upsert, an update, or a delete: the response is the same as for a
   * real request, for example the rows that [select] returns, but nothing is saved. Sequences that
   * the request advances, for example to generate IDs, are not rolled back.
   *
   * The server rolls back only when its `db-tx-end` setting allows it, and then confirms the
   * rollback in the `Preference-Applied` header of its response. Firebase SQL commits by default:
   * to allow the rollback, set `pgrst.db_tx_end` to `commit-allow-override` in the
   * `postgrest.pre_config()` function of the database, as
   * [In-database configuration](https://docs.postgrest.org/en/v12/references/configuration.html#in-database-configuration)
   * describes. Without the confirmation, the changes were committed: [execute] then throws a
   * [FirebaseSqlException] of the `CLIENT` category that says so, with the HTTP status of the
   * response. The SDK skips this check with [explain], because PostgREST sends no
   * `Preference-Applied` header with a plan.
   *
   * PostgREST: `Prefer: tx=rollback`
   *
   * **Example: preview an update**
   * ```kotlin
   * val preview =
   *   sql
   *     .from("products")
   *     .update(mapOf("price" to 0.5))
   *     .eq("category_id", 1)
   *     .select("id, name, price")
   *     .dryRun()
   *     .execute()
   *     .decodeList<Product>()
   * // The rows as they would be after the update. The database is unchanged.
   * ```
   *
   * @return A new builder that rolls back its transaction.
   */
  public fun dryRun(): TransformBuilder

  /**
   * Returns the PostgreSQL execution plan of the request instead of its result.
   *
   * PostgREST returns plans only when its `db-plan-enabled` setting is `true`; otherwise it answers
   * with a `406` error with the code `PGRST107`. Firebase SQL turns it off by default: to turn it
   * on, set `pgrst.db_plan_enabled` to `true` in the `postgrest.pre_config()` function of the
   * database, like `pgrst.db_tx_end` for [dryRun]. Plans show details of the database, so PostgREST
   * recommends them only for test environments.
   *
   * The plan describes the query that serves the response format chosen by the other modifiers, for
   * example [single] or [csv]. With [explain], the SDK returns the plan as it is: it does not
   * reduce it for [maybeSingle], and does not check the rollback of [dryRun].
   *
   * PostgREST: `Accept: application/vnd.pgrst.plan+<format>; for="<media type>";
   * options=<options>`, with the options joined by `|`, for example `options=analyze|verbose`.
   *
   * **Example: check how a filter runs**
   * ```kotlin
   * val result =
   *   sql
   *     .from("products")
   *     .select()
   *     .eq("category_id", 1)
   *     .explain(analyze = true)
   *     .execute()
   * println(result.data) // The plan, as text.
   * ```
   *
   * @param analyze Whether to run the query, and show its real run times and row counts. The query
   * really runs, so an insert, an upsert, an update, or a delete is committed, unless [dryRun] is
   * used too and the server applies it, which the SDK cannot check with a plan.
   * @param verbose Whether to show more details, such as the output columns of each step.
   * @param settings Whether to show the configuration parameters that affect the plan and differ
   * from their defaults.
   * @param buffers Whether to show the buffer usage.
   * @param wal Whether to show the write-ahead log records that the query generates. PostgreSQL
   * requires [analyze] with it.
   * @param format The format of the plan: [ExplainFormat.TEXT], the default, or
   * [ExplainFormat.JSON].
   * @return A new builder that returns the plan. It has no filter methods, so add the filters
   * first.
   */
  public fun explain(
    analyze: Boolean = false,
    verbose: Boolean = false,
    settings: Boolean = false,
    buffers: Boolean = false,
    wal: Boolean = false,
    format: ExplainFormat = ExplainFormat.TEXT
  ): TransformBuilder

  /**
   * Turns off the automatic retries of this request.
   *
   * By default, the SDK retries a GET or HEAD request, that is a read or a function call with `get`
   * or `head`, when it fails with a network error, or when the server answers `502` or `503`, which
   * are usually transient: for example, the server answers `503` while it loads its schema cache.
   * It retries up to 3 times within the same call to [execute], whose result is the result of the
   * last attempt. Before each retry, it waits about 1, 2, then 4 seconds, up to 25% more or less at
   * random, so that the apps that failed at the same time do not all retry at the same time. The
   * first attempt has no `X-Retry-Count` header; each retry sends its number in it, from 1. Only
   * GET and HEAD requests are retried; other requests can change data, so they are not retried. An
   * attempt that times out is not retried either, and neither are the other errors, such as `429`
   * or `504`.
   *
   * With [noRetry], the result of the first attempt is the result of [execute].
   *
   * [noRetry] does not change one other resend: when the server answers `401` because it rejects a
   * Firebase Authentication or App Check token of the request, the SDK refreshes the tokens and
   * sends the request once more, whatever its method. Sending it again is safe, because the server
   * rejects the token before it runs the request.
   *
   * **Example: fail fast**
   * ```kotlin
   * val result = sql.from("products").select().noRetry().execute()
   * ```
   *
   * @return A new builder without automatic retries.
   */
  public fun noRetry(): TransformBuilder

  /**
   * Leaves the keys whose value is `null` out of the JSON response.
   *
   * It makes the response smaller. When you decode it, give each nullable property a default value,
   * for example `= null`, because its key can be missing. It works with the default JSON array,
   * with [single], and with [maybeSingle], and has no effect with [geojson].
   *
   * PostgREST: `Accept: application/vnd.pgrst.array+json;nulls=stripped`, or `Accept:
   * application/vnd.pgrst.object+json;nulls=stripped` for one object.
   *
   * **Example: read the products without their `null` values**
   * ```kotlin
   * val products =
   *   sql
   *     .from("products")
   *     .select("id, name, price, category_id")
   *     .stripNulls()
   *     .execute()
   *     .decodeList<Product>() // categoryId has a default value, so its key can be missing.
   * ```
   * Response:
   * ```json
   * [
   *   {"id": 1, "name": "Apple", "price": 1.5, "category_id": 1},
   *   {"id": 8, "name": "Gift card", "price": 10.0}
   * ]
   * ```
   *
   * @return A new builder that strips the `null` values.
   * @throws IllegalStateException If the response format is CSV, that is if [csv] is the last
   * format modifier called, because nulls are stripped only from JSON.
   */
  public fun stripNulls(): TransformBuilder

  /**
   * Makes an update, a delete, or a function call fail when it affects more than [count] rows.
   *
   * When more rows are affected, PostgREST answers with a `400` error with the code `PGRST124`,
   * whose `details` give the number of rows, and rolls back the transaction, so that no row
   * changes. For a function call, the affected rows are the rows that the function returns, so the
   * function must return `SETOF` a type or a `TABLE`; otherwise PostgREST answers with the code
   * `PGRST128`.
   *
   * It also sends `handling=strict`, so PostgREST rejects the request with the code `PGRST122` if
   * its `Prefer` header, including a `Prefer` in `SqlServiceSettings.headers`, has a preference
   * that PostgREST does not know.
   *
   * PostgREST ignores the maximum for the other requests, so [maxAffected] throws an
   * [IllegalStateException] on a read, an insert, or an upsert, instead of letting them change more
   * rows than expected.
   *
   * PostgREST: `Prefer: handling=strict,max-affected=<count>`
   *
   * **Example: refuse to delete more rows than expected**
   * ```kotlin
   * try {
   *   sql.from("products").delete().eq("category_id", 3).maxAffected(10).execute()
   * } catch (e: FirebaseSqlException) {
   *   if (e.code != "PGRST124") throw e
   *   // Nothing was deleted. The details are, for example, "The query affects 14 rows".
   *   println(e.details)
   * }
   * ```
   *
   * @param count The maximum number of rows that the request can affect, `0` or more.
   * @return A new builder with the maximum set.
   * @throws IllegalArgumentException If [count] is negative.
   * @throws IllegalStateException If the request is not an update, a delete, or a function call.
   */
  public fun maxAffected(count: Long): TransformBuilder

  /**
   * Sets an HTTP header of this request.
   *
   * Header names are case-insensitive. The value replaces the header with the same name that the
   * SDK computes, such as `Accept` or `Content-Type`, or that `SqlServiceSettings.headers` sets,
   * and a later call with the same name replaces it again. A `Prefer` header is merged instead: its
   * preferences are sent first, followed by the preferences that the SDK computes and then those of
   * a `Prefer` in `SqlServiceSettings.headers`, and PostgREST uses the first value of each
   * preference, so yours win.
   *
   * A name or a value that HTTP does not allow, for example a value with a line break or a name
   * with a space, makes [execute] throw an `IllegalArgumentException`.
   *
   * **Example: get the timestamps in another time zone**
   * ```kotlin
   * val products =
   *   sql
   *     .from("products")
   *     .select("id, created_at")
   *     .setHeader("Prefer", "timezone=America/New_York")
   *     .execute()
   * ```
   *
   * @param name The name of the header. It must not be one of the headers that the SDK reserves;
   * see below.
   * @param value The value of the header.
   * @return A new builder with the header set.
   * @throws ReservedHeaderException If [name] is, in any case, `Authorization`, a header that the
   * SDK sets itself, or a header that Firebase reserves: the tokens (`x-firebase-auth-token` and
   * `X-Firebase-AppCheck`), the schema (`Accept-Profile` and `Content-Profile`), the versions and
   * the app ID (`x-goog-api-client`, `x-client-version`, and `x-firebase-gmpid`), `X-Retry-Count`,
   * the routing headers (`x-firebase-sqlconnect-affinity` and `x-goog-request-params`), and the
   * heartbeat headers (`x-firebase-client` and `x-firebase-client-log-type`). To choose the schema,
   * use `SqlServiceSettings.schemaName`. It is an `IllegalArgumentException`.
   */
  public fun setHeader(name: String, value: String): TransformBuilder

  /**
   * Sends the request, suspends until its response arrives, and returns it, or throws its error.
   *
   * It returns an [OperationResult] when the server answers with a successful HTTP status. Its
   * `data` is the response body: a JSON array of rows by default, one JSON object with [single] or
   * [maybeSingle], or the text of [csv], [geojson], or [explain]. It is empty when the response has
   * no body, for example after `select(head = true)`, or after a mutation without [select]. Its
   * `countOrNull()` is the number of rows when the request asks for a count. Decode the data with
   * `decodeList<T>()`, `decodeSingle<T>()`, `decodeSingleOrNull<T>()`, or `decodeAs<T>()`.
   *
   * It throws when the request fails:
   * - The server answers with an HTTP error status: a [FirebaseSqlException] with the HTTP
   * `status`, and the `code`, `message`, `details`, and `hint` of the server, for example the code
   * `23505` for a duplicate key. Its `category` tells whether PostgREST or the Firebase SQL
   * platform in front of it rejected the request.
   * - The SDK detects an error in a successful response: a [FirebaseSqlException] for more than one
   * row with [maybeSingle], or for a [dryRun] whose rollback the server did not confirm.
   * - No HTTP response arrives: the `IOException` of the network, for example an
   * `UnknownHostException` when the device is offline, or an `InterruptedIOException` when an
   * attempt times out.
   * - The Firebase Authentication token of the signed-in user cannot be retrieved: the error of
   * Firebase Authentication, and the request is not sent.
   *
   * **Timeouts and retries.** Each attempt of the request can take at most 100 seconds, longer than
   * the 90 seconds after which the server cancels a request. An attempt that takes longer is
   * cancelled and is not retried, and [execute] throws an `InterruptedIOException`. A GET or HEAD
   * request that fails because of the network, or because of a transient server error, is retried
   * within the same call; see [noRetry].
   *
   * **Cancellation.** Cancelling the coroutine that calls [execute] cancels the HTTP call and any
   * wait between attempts, and [execute] throws a `CancellationException`. To give up sooner, wrap
   * the call in `withTimeout` or `withTimeoutOrNull`: the limit covers the whole call, retries
   * included. The server may still apply an insert, an upsert, an update, or a delete that was
   * cancelled or that timed out, so read the data again to check it, and repeat the mutation only
   * if needed.
   *
   * Each call sends a new request, so a builder can be executed more than once.
   *
   * **Example: handle the errors**
   * ```kotlin
   * try {
   *   val products =
   *     sql.from("products").select().eq("category_id", 1).execute().decodeList<Product>()
   *   println("${products.size} products")
   * } catch (e: FirebaseSqlException) {
   *   // The server answered with an HTTP error status.
   *   println("HTTP ${e.status} ${e.code}: ${e.message} ${e.details}")
   * } catch (e: IOException) {
   *   // No response, for example because of a network error or a timeout.
   *   println("No response from the server: ${e.message}")
   * }
   * ```
   *
   * **Example: give up after 5 seconds, retries included**
   * ```kotlin
   * val result = withTimeoutOrNull(5_000) { sql.from("products").select().execute() }
   * if (result == null) println("No result within 5 seconds")
   * ```
   *
   * @return The response of the request.
   * @throws FirebaseSqlException When the server rejects the request, with its HTTP `status`, or
   * when the SDK detects an error in a successful response.
   * @throws java.io.IOException When no HTTP response arrives, for example because the network is
   * unreachable; an `InterruptedIOException` when an attempt times out.
   * @throws IllegalStateException If the `FirebaseSql` instance that created this builder was
   * terminated before the call, or while the request runs.
   * @throws IllegalArgumentException If the request cannot be built: a query parameter name that
   * starts with `$`, such as a filter on a column whose name starts with `$`, which the server
   * would ignore, or a header name or value that HTTP does not allow.
   * @throws kotlin.coroutines.cancellation.CancellationException If the coroutine is cancelled.
   */
  public suspend fun execute(): OperationResult
}
