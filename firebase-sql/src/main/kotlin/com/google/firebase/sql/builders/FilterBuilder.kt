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
import com.google.firebase.sql.FilterOperator
import com.google.firebase.sql.Order
import com.google.firebase.sql.TextSearchType

/**
 * Adds filters to a request: the conditions that a row must match to be read, updated, or deleted.
 *
 * Each filter becomes a query parameter of the PostgREST request, which the server turns into a
 * condition of the SQL `WHERE` clause. Filters apply to [QueryBuilder.select], to
 * [QueryBuilder.update] and [QueryBuilder.delete], which change only the rows that match, and to
 * [FirebaseSql.rpc][com.google.firebase.sql.FirebaseSql.rpc], where they filter the rows that the
 * function returns.
 *
 * **Combining filters:** Chained filters are combined with AND, including several filters on the
 * same column. For OR, use [or]. For an operator without a dedicated method, use [filter], and to
 * negate a condition, use [filterNot]. An `update()` or a `delete()` without filters changes every
 * row of the table; [TransformBuilder.maxAffected] makes the server reject a change of more rows
 * than expected.
 *
 * **Sending the request:** Nothing is sent until [TransformBuilder.execute], a suspend function, is
 * called.
 *
 * **Filters and modifiers:** Write the filters first, then the modifiers, such as `order()` and
 * `limit()`, so that the code reads in the order that the database runs it: it filters the rows,
 * then sorts them, then returns the requested range. A filter can still follow `order()`,
 * `limit()`, `range()`, `setHeader()`, and the other modifiers that return a [FilterBuilder], and
 * it filters the rows the same way. The modifiers that change the type of the response, `single()`,
 * `maybeSingle()`, `csv()`, `geojson()`, and `explain()`, return a [TransformBuilder], which has no
 * filter methods, so add the filters before them. Since the other modifiers keep the
 * [FilterBuilder] type, a query can be built step by step in one variable.
 *
 * **Immutability:** Builders are immutable. Every method returns a new builder and leaves the
 * builder that it is called on unchanged, so always use the returned builder. A builder can be the
 * base of several requests, be executed more than once, and be shared between threads.
 *
 * **Not stable for inheritance:** Later versions can add members to this interface, so implementing
 * it requires an opt-in to [InternalForInheritanceFirebaseApi]. Get a builder from an operation of
 * [QueryBuilder] or from [FirebaseSql.rpc][com.google.firebase.sql.FirebaseSql.rpc] instead, and
 * mock it in tests.
 *
 * **`NULL` values:** In SQL, a comparison with `NULL` is never true, so most filters leave out the
 * rows where the column is `NULL`. To match them, use `exact(column, null)`, [isDistinct], or a
 * condition such as `category_id.is.null` in [or].
 *
 * **Columns:** A filter names a column of the table, a column of an embedded table as
 * `table.column`, or a field of a `jsonb` column as a JSON path, such as `details->>color`. A
 * filter on `table.column` needs the table in `select()`, for example `select("name,
 * categories(name)")`, and it filters the rows of the embedded table only: every top-level row is
 * still returned, with `null` (or an empty list) for the embedded table when none of its rows
 * match. To also leave out the top-level rows without a match, add `!inner` after the table name,
 * for example `select("name, categories!inner(name)")`.
 *
 * A column name must not start with `$`. The server ignores the query parameters whose names start
 * with `$`, so the request would run without the filter, and an `update()` or a `delete()` could
 * change every row. For such a name, and for a `referencedTable` of [or] or [and] that starts with
 * `$`, [TransformBuilder.execute] throws an [IllegalArgumentException] instead of sending the
 * request.
 *
 * **Values:** Do not quote or URL-encode values, except in the conditions of [or] and [and]: the
 * SDK URL-encodes every query parameter, and PostgREST reads everything after the operator as the
 * value. A `String` is sent as is, even when it contains commas, parentheses, or quotes. Any other
 * value is sent as its JSON text, without the quotes of a JSON string:
 * - A number, a `Boolean`, or a `Char`, for example `42`, `9.99`, or `true`.
 * - An enum, as its name. When the database stores other values, for example the `@SerialName` of
 * each constant, pass the value as a `String`.
 * - A `UUID`, as its canonical form, for example `123e4567-e89b-12d3-a456-426614174000`.
 * - A `java.util.Date`, as an ISO 8601 timestamp in UTC, for example `2026-01-31T12:00:00.000Z`.
 * - A `Map`, a `List`, or a `JsonElement`, as JSON, for example `{"color":"red"}` to compare with a
 * `jsonb` column.
 * - `null`, where a method accepts it, as the text `null`, which PostgREST reads as SQL `NULL` only
 * with the `is` operator.
 *
 * The methods that take a list, such as [isIn] and [contains], also quote each element as needed. A
 * value that cannot be encoded, such as `Double.NaN` or an instance of your own class, makes the
 * filter method throw an [IllegalArgumentException]. Pass a `java.time` value as a `String`, for
 * example `instant.toString()`, and encode an object of a `@Serializable` class with
 * `Json.encodeToJsonElement()` first.
 *
 * **Example: read the rows that match**
 * ```kotlin
 * // PostgREST: select=*&category_id=eq.1&price=lt.2&order=price.asc
 * val cheapFruit =
 *   sql
 *     .from("products")
 *     .select()
 *     .eq("category_id", 1)
 *     .lt("price", 2)
 *     .order("price")
 *     .execute()
 *     .decodeList<Product>()
 * ```
 *
 * **Example: update the rows that match**
 * ```kotlin
 * val updated =
 *   sql
 *     .from("products")
 *     .update(mapOf("price" to 1.99))
 *     .eq("id", 42)
 *     .select("id, name, price")
 *     .execute()
 *     .decodeList<Product>()
 * ```
 *
 * Response:
 * ```json
 * [{ "id": 42, "name": "Apple", "price": 1.99 }]
 * ```
 *
 * **Example: delete the rows that match**
 * ```kotlin
 * // Keeps the products whose deleted_at is NULL: NULL < '2026-01-01' is not true.
 * val result =
 *   sql.from("products").delete().lt("deleted_at", "2026-01-01T00:00:00Z").execute()
 * ```
 *
 * **Example: filter on a column of an embedded table**
 * ```kotlin
 * @Serializable
 * data class ProductWithCategory(val name: String, val categories: Category? = null)
 *
 * // Without !inner, the products of other categories would also come back, with null categories.
 * val fruit =
 *   sql
 *     .from("products")
 *     .select("name, categories!inner(id, name)")
 *     .eq("categories.name", "Fruit")
 *     .execute()
 *     .decodeList<ProductWithCategory>()
 * ```
 *
 * Response:
 * ```json
 * [{ "name": "Apple", "categories": { "id": 1, "name": "Fruit" } }]
 * ```
 *
 * **Example: build a query step by step**
 * ```kotlin
 * suspend fun search(maxPrice: Double?, categoryId: Long?, cheapFirst: Boolean): List<Product> {
 *   var query = sql.from("products").select()
 *   if (maxPrice != null) query = query.lte("price", maxPrice)
 *   if (categoryId != null) query = query.eq("category_id", categoryId)
 *   if (cheapFirst) query = query.order("price")
 *   return query.limit(20).execute().decodeList<Product>()
 * }
 * ```
 */
@SubclassOptInRequired(InternalForInheritanceFirebaseApi::class)
public interface FilterBuilder : TransformBuilder {

  /**
   * Keeps only the rows where [column] is equal to [value].
   *
   * To find the rows where [column] is `NULL`, use `exact(column, null)`: in SQL, `NULL = NULL` is
   * not true, so `eq` never matches `NULL`. A `String` is compared as written, and other values are
   * sent as described in [FilterBuilder]. To compare a whole `jsonb` value, pass a `Map`, a `List`,
   * or a `JsonElement`.
   *
   * PostgREST: `column=eq.value`
   *
   * **Example: find a product by ID**
   * ```kotlin
   * val product =
   *   sql.from("products").select().eq("id", 42).single().execute().decodeSingle<Product>()
   * ```
   *
   * **Example: combine filters**
   * ```kotlin
   * // Filters are combined with AND.
   * val cheapFruit =
   *   sql
   *     .from("products")
   *     .select()
   *     .eq("category_id", 1)
   *     .lt("price", 2)
   *     .execute()
   *     .decodeList<Product>()
   * ```
   *
   * **Example: compare a field of a `jsonb` column**
   * ```kotlin
   * // details->>color is the color field of details, as text.
   * val red =
   *   sql
   *     .from("products")
   *     .select()
   *     .eq("details->>color", "red")
   *     .execute()
   *     .decodeList<Product>()
   * ```
   *
   * @param column The column to compare. For a column of an embedded table, use `table.column`.
   * @param value The value to compare with, for example a `String`, a number, a `Boolean`, an enum,
   * or a `UUID`.
   * @return A new builder with the filter added.
   * @throws IllegalArgumentException If [value] cannot be encoded, for example `Double.NaN`, or an
   * instance of your own class.
   * @see exact
   * @see match
   */
  public fun eq(column: String, value: Any): FilterBuilder

  /**
   * Keeps only the rows where [column] is not equal to [value].
   *
   * The rows where [column] is `NULL` are left out: in SQL, `NULL <> value` is not true. To keep
   * them, use [isDistinct]. Values are sent as described in [FilterBuilder].
   *
   * PostgREST: `column=neq.value`
   *
   * **Example: products outside a category**
   * ```kotlin
   * // Also leaves out the products without a category.
   * val notFruit =
   *   sql.from("products").select().neq("category_id", 1).execute().decodeList<Product>()
   * ```
   *
   * @param column The column to compare. For a column of an embedded table, use `table.column`.
   * @param value The value to compare with, for example a `String`, a number, a `Boolean`, or an
   * enum.
   * @return A new builder with the filter added.
   * @throws IllegalArgumentException If [value] cannot be encoded, for example `Double.NaN`, or an
   * instance of your own class.
   * @see isDistinct
   */
  public fun neq(column: String, value: Any): FilterBuilder

  /**
   * Keeps only the rows where [column] is greater than [value].
   *
   * The comparison follows the type of [column]: numbers compare by value, text by the sort order
   * of its collation, and dates and timestamps by time. Pass a time as an ISO 8601 `String`, such
   * as `"2026-01-01T00:00:00Z"`, or as a `java.util.Date`. The rows where [column] is `NULL` are
   * left out.
   *
   * PostgREST: `column=gt.value`
   *
   * **Example: products that cost more than 100**
   * ```kotlin
   * val expensive =
   *   sql.from("products").select().gt("price", 100).execute().decodeList<Product>()
   * ```
   *
   * **Example: products created in the last 24 hours**
   * ```kotlin
   * val since = Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000L)
   * val recent =
   *   sql.from("products").select().gt("created_at", since).execute().decodeList<Product>()
   * ```
   *
   * @param column The column to compare. For a column of an embedded table, use `table.column`.
   * @param value The value to compare with, for example a number, a `String`, or a `Date`.
   * @return A new builder with the filter added.
   * @throws IllegalArgumentException If [value] cannot be encoded, for example `Double.NaN`, or an
   * instance of your own class.
   */
  public fun gt(column: String, value: Any): FilterBuilder

  /**
   * Keeps only the rows where [column] is greater than or equal to [value].
   *
   * The comparison follows the type of [column], as for [gt]. The rows where [column] is `NULL` are
   * left out.
   *
   * PostgREST: `column=gte.value`
   *
   * **Example: products that cost from 10 to 20**
   * ```kotlin
   * val midRange =
   *   sql
   *     .from("products")
   *     .select()
   *     .gte("price", 10)
   *     .lte("price", 20)
   *     .execute()
   *     .decodeList<Product>()
   * ```
   *
   * @param column The column to compare. For a column of an embedded table, use `table.column`.
   * @param value The value to compare with, for example a number, a `String`, or a `Date`.
   * @return A new builder with the filter added.
   * @throws IllegalArgumentException If [value] cannot be encoded, for example `Double.NaN`, or an
   * instance of your own class.
   */
  public fun gte(column: String, value: Any): FilterBuilder

  /**
   * Keeps only the rows where [column] is less than [value].
   *
   * The comparison follows the type of [column], as for [gt]. The rows where [column] is `NULL` are
   * left out.
   *
   * PostgREST: `column=lt.value`
   *
   * **Example: products that cost less than 5**
   * ```kotlin
   * val cheap = sql.from("products").select().lt("price", 5).execute().decodeList<Product>()
   * ```
   *
   * **Example: products created in 2025 or earlier**
   * ```kotlin
   * val older =
   *   sql
   *     .from("products")
   *     .select()
   *     .lt("created_at", "2026-01-01T00:00:00Z")
   *     .execute()
   *     .decodeList<Product>()
   * ```
   *
   * @param column The column to compare. For a column of an embedded table, use `table.column`.
   * @param value The value to compare with, for example a number, a `String`, or a `Date`.
   * @return A new builder with the filter added.
   * @throws IllegalArgumentException If [value] cannot be encoded, for example `Double.NaN`, or an
   * instance of your own class.
   */
  public fun lt(column: String, value: Any): FilterBuilder

  /**
   * Keeps only the rows where [column] is less than or equal to [value].
   *
   * The comparison follows the type of [column], as for [gt]. The rows where [column] is `NULL` are
   * left out.
   *
   * PostgREST: `column=lte.value`
   *
   * **Example: products that cost at most 9.99**
   * ```kotlin
   * val affordable =
   *   sql.from("products").select().lte("price", 9.99).execute().decodeList<Product>()
   * ```
   *
   * @param column The column to compare. For a column of an embedded table, use `table.column`.
   * @param value The value to compare with, for example a number, a `String`, or a `Date`.
   * @return A new builder with the filter added.
   * @throws IllegalArgumentException If [value] cannot be encoded, for example `Double.NaN`, or an
   * instance of your own class.
   */
  public fun lte(column: String, value: Any): FilterBuilder

  /**
   * Keeps only the rows where [column] matches the SQL `LIKE` [pattern], case-sensitively.
   *
   * [pattern] must match the whole value. In it, `%` matches any sequence of characters, including
   * none, and `_` matches exactly one character, so put `%` at both ends to find a substring.
   * PostgREST also reads every `*` as `%`, so `like` cannot match a literal `*`. To match a literal
   * `%` or `_`, put a backslash in front of it, for example `"100\\%"` in Kotlin. [column] must be
   * a text column, and the rows where it is `NULL` are left out. To ignore case, use [ilike].
   *
   * PostgREST: `column=like.pattern`
   *
   * **Example: names that contain a word**
   * ```kotlin
   * // Matches "Green Apple", but not "green apple".
   * val apples =
   *   sql.from("products").select().like("name", "%Apple%").execute().decodeList<Product>()
   * ```
   *
   * **Example: names that start with a word**
   * ```kotlin
   * // "Apple*" is the same as "Apple%".
   * val apples =
   *   sql.from("products").select().like("name", "Apple*").execute().decodeList<Product>()
   * ```
   *
   * @param column The text column to match. For a column of an embedded table, use `table.column`.
   * @param pattern The `LIKE` pattern, sent as is.
   * @return A new builder with the filter added.
   * @see likeAll
   * @see likeAny
   */
  public fun like(column: String, pattern: String): FilterBuilder

  /**
   * Keeps only the rows where [column] matches every pattern in [patterns], case-sensitively.
   *
   * Each pattern uses the syntax of [like], with `%`, `_`, and `*`. The patterns are sent as a
   * PostgreSQL array literal, and the SDK double-quotes a pattern when needed, so any pattern can
   * be passed as is. An empty list keeps every row. Otherwise, the rows where [column] is `NULL`
   * are left out.
   *
   * PostgREST: `column=like(all).{pattern1,pattern2}`
   *
   * **Example: names that contain two words**
   * ```kotlin
   * val organicApples =
   *   sql
   *     .from("products")
   *     .select()
   *     .likeAll("name", listOf("%Organic%", "%Apple%"))
   *     .execute()
   *     .decodeList<Product>()
   * ```
   *
   * @param column The text column to match. For a column of an embedded table, use `table.column`.
   * @param patterns The `LIKE` patterns, which must all match.
   * @return A new builder with the filter added.
   */
  public fun likeAll(column: String, patterns: List<String>): FilterBuilder

  /**
   * Keeps only the rows where [column] matches at least one pattern in [patterns],
   * case-sensitively.
   *
   * Each pattern uses the syntax of [like], with `%`, `_`, and `*`. The patterns are sent as a
   * PostgreSQL array literal, and the SDK double-quotes a pattern when needed, so any pattern can
   * be passed as is. An empty list keeps no rows, and the rows where [column] is `NULL` are left
   * out.
   *
   * PostgREST: `column=like(any).{pattern1,pattern2}`
   *
   * **Example: names that start with one of two words**
   * ```kotlin
   * val products =
   *   sql
   *     .from("products")
   *     .select()
   *     .likeAny("name", listOf("Apple%", "Pear%"))
   *     .execute()
   *     .decodeList<Product>()
   * ```
   *
   * @param column The text column to match. For a column of an embedded table, use `table.column`.
   * @param patterns The `LIKE` patterns, at least one of which must match.
   * @return A new builder with the filter added.
   */
  public fun likeAny(column: String, patterns: List<String>): FilterBuilder

  /**
   * Keeps only the rows where [column] matches the SQL `ILIKE` [pattern], ignoring case.
   *
   * [pattern] uses the syntax of [like]: it must match the whole value, `%` and `*` match any
   * sequence of characters, and `_` matches exactly one character. [column] must be a text column,
   * and the rows where it is `NULL` are left out.
   *
   * PostgREST: `column=ilike.pattern`
   *
   * **Example: names that contain a word, in any case**
   * ```kotlin
   * // Matches "Green Apple" and "APPLE PIE".
   * val apples =
   *   sql.from("products").select().ilike("name", "%apple%").execute().decodeList<Product>()
   * ```
   *
   * @param column The text column to match. For a column of an embedded table, use `table.column`.
   * @param pattern The `ILIKE` pattern, sent as is.
   * @return A new builder with the filter added.
   * @see ilikeAll
   * @see ilikeAny
   */
  public fun ilike(column: String, pattern: String): FilterBuilder

  /**
   * Keeps only the rows where [column] matches every pattern in [patterns], ignoring case.
   *
   * Each pattern uses the syntax of [like], with `%`, `_`, and `*`. The patterns are sent as a
   * PostgreSQL array literal, and the SDK double-quotes a pattern when needed, so any pattern can
   * be passed as is. An empty list keeps every row. Otherwise, the rows where [column] is `NULL`
   * are left out.
   *
   * PostgREST: `column=ilike(all).{pattern1,pattern2}`
   *
   * **Example: names that contain two words, in any case**
   * ```kotlin
   * val organicApples =
   *   sql
   *     .from("products")
   *     .select()
   *     .ilikeAll("name", listOf("%organic%", "%apple%"))
   *     .execute()
   *     .decodeList<Product>()
   * ```
   *
   * @param column The text column to match. For a column of an embedded table, use `table.column`.
   * @param patterns The `ILIKE` patterns, which must all match.
   * @return A new builder with the filter added.
   */
  public fun ilikeAll(column: String, patterns: List<String>): FilterBuilder

  /**
   * Keeps only the rows where [column] matches at least one pattern in [patterns], ignoring case.
   *
   * Each pattern uses the syntax of [like], with `%`, `_`, and `*`. The patterns are sent as a
   * PostgreSQL array literal, and the SDK double-quotes a pattern when needed, so any pattern can
   * be passed as is. An empty list keeps no rows, and the rows where [column] is `NULL` are left
   * out.
   *
   * PostgREST: `column=ilike(any).{pattern1,pattern2}`
   *
   * **Example: names that start with one of two words, in any case**
   * ```kotlin
   * val products =
   *   sql
   *     .from("products")
   *     .select()
   *     .ilikeAny("name", listOf("apple%", "pear%"))
   *     .execute()
   *     .decodeList<Product>()
   * ```
   *
   * @param column The text column to match. For a column of an embedded table, use `table.column`.
   * @param patterns The `ILIKE` patterns, at least one of which must match.
   * @return A new builder with the filter added.
   */
  public fun ilikeAny(column: String, patterns: List<String>): FilterBuilder

  /**
   * Keeps only the rows where [column] matches the POSIX regular expression [pattern],
   * case-sensitively.
   *
   * It uses the PostgreSQL `~` operator. The expression can match any part of the value; anchor it
   * with `^` and `$` to match the whole value. [column] must be a text column, and the rows where
   * it is `NULL` are left out. An invalid expression makes the request fail.
   *
   * PostgREST: `column=match.pattern`
   *
   * **Example: names that are one capitalized word**
   * ```kotlin
   * val products =
   *   sql
   *     .from("products")
   *     .select()
   *     .regexMatch("name", "^[A-Z][a-z]+$")
   *     .execute()
   *     .decodeList<Product>()
   * ```
   *
   * @param column The text column to match. For a column of an embedded table, use `table.column`.
   * @param pattern The regular expression, sent as is. In a Kotlin string, write `\` as `\\`, for
   * example `"^\\d+$"`.
   * @return A new builder with the filter added.
   * @see regexIMatch
   */
  public fun regexMatch(column: String, pattern: String): FilterBuilder

  /**
   * Keeps only the rows where [column] matches the POSIX regular expression [pattern], ignoring
   * case.
   *
   * It uses the PostgreSQL `~*` operator. As with [regexMatch], the expression can match any part
   * of the value unless it is anchored with `^` and `$`, [column] must be a text column, and the
   * rows where it is `NULL` are left out. An invalid expression makes the request fail.
   *
   * PostgREST: `column=imatch.pattern`
   *
   * **Example: names that start with `apple` or `pear`, in any case**
   * ```kotlin
   * val products =
   *   sql
   *     .from("products")
   *     .select()
   *     .regexIMatch("name", "^(apple|pear)")
   *     .execute()
   *     .decodeList<Product>()
   * ```
   *
   * @param column The text column to match. For a column of an embedded table, use `table.column`.
   * @param pattern The regular expression, sent as is. In a Kotlin string, write `\` as `\\`.
   * @return A new builder with the filter added.
   * @see regexMatch
   */
  public fun regexIMatch(column: String, pattern: String): FilterBuilder

  /**
   * Keeps only the rows where each column in [query] is equal to its value.
   *
   * It adds one [eq] filter for each entry, in the iteration order of [query], so the values are
   * sent as for [eq], and the rows where one of the columns is `NULL` are left out. An empty map
   * adds no filter, so an `update()` or a `delete()` with only an empty [match] changes every row.
   *
   * PostgREST: `column1=eq.value1&column2=eq.value2`
   *
   * **Example: find products by two columns**
   * ```kotlin
   * val apples =
   *   sql
   *     .from("products")
   *     .select()
   *     .match(mapOf("category_id" to 1, "name" to "Apple"))
   *     .execute()
   *     .decodeList<Product>()
   * ```
   *
   * **Example: update the rows that match**
   * ```kotlin
   * sql
   *   .from("products")
   *   .update(mapOf("price" to 0.99))
   *   .match(mapOf("category_id" to 1, "name" to "Apple"))
   *   .execute()
   * ```
   *
   * @param query The columns, each mapped to the value that it must be equal to. For a column of an
   * embedded table, use `table.column` as the key.
   * @return A new builder with one filter added for each entry of [query].
   * @throws IllegalArgumentException If a value of [query] cannot be encoded, for example
   * `Double.NaN`, or an instance of your own class.
   */
  public fun match(query: Map<String, Any>): FilterBuilder

  /**
   * Keeps only the rows where [column] is `NULL`, `true`, or `false`, with the SQL `IS` operator.
   *
   * `exact(column, null)` works with a column of any type, and is the way to find `NULL` values,
   * which [eq] never matches. `exact(column, true)` and `exact(column, false)` need a `boolean`
   * column, and leave out the rows where it is `NULL`. To keep the rows where [column] is not
   * `NULL`, use `filterNot(column, FilterOperator.IS, null)`.
   *
   * PostgREST: `column=is.null`, `column=is.true`, or `column=is.false`
   *
   * **Example: products that are not deleted**
   * ```kotlin
   * val products =
   *   sql.from("products").select().exact("deleted_at", null).execute().decodeList<Product>()
   * ```
   *
   * **Example: products without a category**
   * ```kotlin
   * val uncategorized =
   *   sql.from("products").select().exact("category_id", null).execute().decodeList<Product>()
   * ```
   *
   * @param column The column to check. For a column of an embedded table, use `table.column`.
   * @param value `null` to keep the rows where [column] is `NULL`, or `true` or `false` to keep the
   * rows where a `boolean` column has that value.
   * @return A new builder with the filter added.
   */
  public fun exact(column: String, value: Boolean?): FilterBuilder

  /**
   * Keeps only the rows where [column] is distinct from [value], counting `NULL` as a value.
   *
   * It uses SQL `IS DISTINCT FROM`: unlike [neq], it keeps the rows where [column] is `NULL`.
   * Values are sent as described in [FilterBuilder].
   *
   * [value] cannot be `null`, because PostgREST would read it as the text `null`, not as SQL
   * `NULL`. To keep the rows where [column] is not `NULL`, use `filterNot(column,
   * FilterOperator.IS, null)`; to keep the rows where it is `NULL`, use `exact(column, null)`.
   *
   * PostgREST: `column=isdistinct.value`
   *
   * **Example: products outside a category, including the ones without a category**
   * ```kotlin
   * val notFruit =
   *   sql
   *     .from("products")
   *     .select()
   *     .isDistinct("category_id", 1)
   *     .execute()
   *     .decodeList<Product>()
   * ```
   *
   * @param column The column to compare. For a column of an embedded table, use `table.column`.
   * @param value The value to compare with.
   * @return A new builder with the filter added.
   * @throws IllegalArgumentException If [value] cannot be encoded, for example `Double.NaN`, or an
   * instance of your own class.
   * @see neq
   */
  public fun isDistinct(column: String, value: Any): FilterBuilder

  /**
   * Keeps only the rows where [column] is equal to one of [values].
   *
   * Each value is converted as for [eq], and the values are sent as a PostgREST list. A value that
   * contains a comma, a parenthesis, a double quote, or a backslash, that is empty, or that starts
   * or ends with whitespace is double-quoted, so any `String` can be passed as is. An empty list
   * keeps no rows, and the rows where [column] is `NULL` are left out.
   *
   * PostgREST: `column=in.(value1,value2)`
   *
   * **Example: find products by ID**
   * ```kotlin
   * val products =
   *   sql
   *     .from("products")
   *     .select()
   *     .isIn("id", listOf(1, 2, 3))
   *     .execute()
   *     .decodeList<Product>()
   * ```
   *
   * **Example: values that contain a comma**
   * ```kotlin
   * // PostgREST: name=in.("Salt, coarse",Pepper)
   * val products =
   *   sql
   *     .from("products")
   *     .select()
   *     .isIn("name", listOf("Salt, coarse", "Pepper"))
   *     .execute()
   *     .decodeList<Product>()
   * ```
   *
   * **Example: delete several rows**
   * ```kotlin
   * sql.from("products").delete().isIn("id", listOf(4, 5, 6)).execute()
   * ```
   *
   * @param column The column to compare. For a column of an embedded table, use `table.column`.
   * @param values The values to look for.
   * @return A new builder with the filter added.
   * @throws IllegalArgumentException If an element of [values] cannot be encoded, for example
   * `Double.NaN`, or an instance of your own class.
   * @see notIn
   */
  public fun isIn(column: String, values: List<Any>): FilterBuilder

  /**
   * Keeps only the rows where [column] is equal to none of [values].
   *
   * The values are sent as for [isIn], so any `String` can be passed as is. An empty list keeps
   * every row. Otherwise, the rows where [column] is `NULL` are left out: in SQL, `NULL NOT IN
   * (...)` is not true.
   *
   * PostgREST: `column=not.in.(value1,value2)`
   *
   * **Example: products outside two categories**
   * ```kotlin
   * // Also leaves out the products without a category.
   * val others =
   *   sql
   *     .from("products")
   *     .select()
   *     .notIn("category_id", listOf(1, 2))
   *     .execute()
   *     .decodeList<Product>()
   * ```
   *
   * @param column The column to compare. For a column of an embedded table, use `table.column`.
   * @param values The values to leave out.
   * @return A new builder with the filter added.
   * @throws IllegalArgumentException If an element of [values] cannot be encoded, for example
   * `Double.NaN`, or an instance of your own class.
   * @see isIn
   */
  public fun notIn(column: String, values: List<Any>): FilterBuilder

  /**
   * Keeps only the rows where the array in [column] contains every element of [values].
   *
   * Use it with an array column, such as `text[]`. The order of the elements does not matter. The
   * values are sent as a PostgreSQL array literal, and the SDK double-quotes an element when
   * needed, so any `String` can be passed as is. An empty list keeps every row where [column] is
   * not `NULL`.
   *
   * `contains` always sends an array literal. For a `jsonb` column or a range column, use [filter]
   * with [FilterOperator.CS] instead, as in the second example.
   *
   * PostgREST: `column=cs.{value1,value2}`
   *
   * **Example: products that have both tags**
   * ```kotlin
   * val products =
   *   sql
   *     .from("products")
   *     .select()
   *     .contains("tags", listOf("organic", "vegan"))
   *     .execute()
   *     .decodeList<Product>()
   * ```
   *
   * **Example: a `jsonb` column that contains an object**
   * ```kotlin
   * // PostgREST: details=cs.{"color":"red"}
   * val red =
   *   sql
   *     .from("products")
   *     .select()
   *     .filter("details", FilterOperator.CS, mapOf("color" to "red"))
   *     .execute()
   *     .decodeList<Product>()
   * ```
   *
   * @param column The array column. For a column of an embedded table, use `table.column`.
   * @param values The elements that the array must contain.
   * @return A new builder with the filter added.
   * @throws IllegalArgumentException If an element of [values] cannot be encoded, for example
   * `Double.NaN`, or an instance of your own class.
   */
  public fun contains(column: String, values: List<Any>): FilterBuilder

  /**
   * Keeps only the rows where every element of the array in [column] is in [values].
   *
   * Use it with an array column, such as `text[]`. The values are sent as for [contains]. A row
   * whose array is empty always matches, and an empty list keeps only those rows. For a range
   * column, use `filter(column, FilterOperator.CD, range)` to keep the rows whose range is inside
   * `range`, as in the second example.
   *
   * PostgREST: `column=cd.{value1,value2}`
   *
   * **Example: products whose tags are all in a list**
   * ```kotlin
   * val products =
   *   sql
   *     .from("products")
   *     .select()
   *     .contained("tags", listOf("organic", "vegan", "local"))
   *     .execute()
   *     .decodeList<Product>()
   * ```
   *
   * **Example: reservations within a day**
   * ```kotlin
   * @Serializable data class Reservation(val id: Long, val room: String)
   *
   * val reservations =
   *   sql
   *     .from("reservations")
   *     .select("id, room")
   *     .filter("during", FilterOperator.CD, "[2026-01-01 00:00,2026-01-02 00:00)")
   *     .execute()
   *     .decodeList<Reservation>()
   * ```
   *
   * @param column The array column. For a column of an embedded table, use `table.column`.
   * @param values The elements that the array can contain.
   * @return A new builder with the filter added.
   * @throws IllegalArgumentException If an element of [values] cannot be encoded, for example
   * `Double.NaN`, or an instance of your own class.
   */
  public fun contained(column: String, values: List<Any>): FilterBuilder

  /**
   * Keeps only the rows where the array in [column] has at least one element in common with
   * [values].
   *
   * Use it with an array column, such as `text[]`. The values are sent as for [contains]. An empty
   * list keeps no rows. For a range column, use `filter(column, FilterOperator.OV, range)` to keep
   * the rows whose range overlaps `range`, as in the second example.
   *
   * PostgREST: `column=ov.{value1,value2}`
   *
   * **Example: products with at least one of two tags**
   * ```kotlin
   * val deals =
   *   sql
   *     .from("products")
   *     .select()
   *     .overlaps("tags", listOf("sale", "clearance"))
   *     .execute()
   *     .decodeList<Product>()
   * ```
   *
   * **Example: reservations that overlap a time slot**
   * ```kotlin
   * @Serializable data class Reservation(val id: Long, val room: String)
   *
   * val overlapping =
   *   sql
   *     .from("reservations")
   *     .select("id, room")
   *     .filter("during", FilterOperator.OV, "[2026-01-01 10:00,2026-01-01 12:00)")
   *     .execute()
   *     .decodeList<Reservation>()
   * ```
   *
   * @param column The array column. For a column of an embedded table, use `table.column`.
   * @param values The elements to look for.
   * @return A new builder with the filter added.
   * @throws IllegalArgumentException If an element of [values] cannot be encoded, for example
   * `Double.NaN`, or an instance of your own class.
   */
  public fun overlaps(column: String, values: List<Any>): FilterBuilder

  /**
   * Keeps only the rows where the range in [column] is strictly left of [range], that is, where
   * every value in it is less than every value in [range].
   *
   * It uses the PostgreSQL `<<` operator, with a range column such as `tsrange`, `daterange`, or
   * `int4range`. The rows where [column] is `NULL` are left out.
   *
   * PostgREST: `column=sl.range`
   *
   * **Example: reservations that end by the start of a time slot**
   * ```kotlin
   * @Serializable data class Reservation(val id: Long, val room: String)
   *
   * // Keeps [2026-01-01 10:00,2026-01-01 12:00), but not [2026-01-01 11:00,2026-01-01 12:30).
   * val earlier =
   *   sql
   *     .from("reservations")
   *     .select("id, room")
   *     .rangeLt("during", "[2026-01-01 12:00,2026-01-01 13:00)")
   *     .execute()
   *     .decodeList<Reservation>()
   * ```
   *
   * @param column The range column. For a column of an embedded table, use `table.column`.
   * @param range A range literal of the type of [column], sent as is: `[` or `(` for an inclusive
   * or exclusive lower bound, the two bounds separated by a comma, then `]` or `)` for an inclusive
   * or exclusive upper bound, for example `[2026-01-01 10:00,2026-01-01 12:00)`. Leave out a bound
   * to make the range unbounded on that side, for example `[2026-01-01 12:00,)`.
   * @return A new builder with the filter added.
   */
  public fun rangeLt(column: String, range: String): FilterBuilder

  /**
   * Keeps only the rows where the range in [column] is strictly right of [range], that is, where
   * every value in it is greater than every value in [range].
   *
   * It uses the PostgreSQL `>>` operator, with a range column such as `tsrange`, `daterange`, or
   * `int4range`. The rows where [column] is `NULL` are left out.
   *
   * PostgREST: `column=sr.range`
   *
   * **Example: reservations after a time slot**
   * ```kotlin
   * @Serializable data class Reservation(val id: Long, val room: String)
   *
   * // Keeps [2026-01-01 13:00,2026-01-01 14:00), but not [2026-01-01 12:30,2026-01-01 14:00).
   * val later =
   *   sql
   *     .from("reservations")
   *     .select("id, room")
   *     .rangeGt("during", "[2026-01-01 12:00,2026-01-01 13:00)")
   *     .execute()
   *     .decodeList<Reservation>()
   * ```
   *
   * @param column The range column. For a column of an embedded table, use `table.column`.
   * @param range A range literal of the type of [column], sent as is: `[` or `(` for an inclusive
   * or exclusive lower bound, the two bounds separated by a comma, then `]` or `)` for an inclusive
   * or exclusive upper bound, for example `[2026-01-01 10:00,2026-01-01 12:00)`. Leave out a bound
   * to make the range unbounded on that side, for example `[2026-01-01 12:00,)`.
   * @return A new builder with the filter added.
   */
  public fun rangeGt(column: String, range: String): FilterBuilder

  /**
   * Keeps only the rows where the range in [column] does not extend to the left of [range], that
   * is, where its lower bound is greater than or equal to the lower bound of [range].
   *
   * It uses the PostgreSQL `&>` operator, with a range column such as `tsrange`, `daterange`, or
   * `int4range`. The upper bound of [range] does not matter. The rows where [column] is `NULL` are
   * left out.
   *
   * PostgREST: `column=nxl.range`
   *
   * **Example: reservations that start at 12:00 or later**
   * ```kotlin
   * @Serializable data class Reservation(val id: Long, val room: String)
   *
   * // Keeps [2026-01-01 12:00,2026-01-01 18:00), but not [2026-01-01 11:00,2026-01-01 13:00).
   * val afternoon =
   *   sql
   *     .from("reservations")
   *     .select("id, room")
   *     .rangeGte("during", "[2026-01-01 12:00,2026-01-01 13:00)")
   *     .execute()
   *     .decodeList<Reservation>()
   * ```
   *
   * @param column The range column. For a column of an embedded table, use `table.column`.
   * @param range A range literal of the type of [column], sent as is: `[` or `(` for an inclusive
   * or exclusive lower bound, the two bounds separated by a comma, then `]` or `)` for an inclusive
   * or exclusive upper bound, for example `[2026-01-01 10:00,2026-01-01 12:00)`. Leave out a bound
   * to make the range unbounded on that side, for example `[2026-01-01 12:00,)`.
   * @return A new builder with the filter added.
   */
  public fun rangeGte(column: String, range: String): FilterBuilder

  /**
   * Keeps only the rows where the range in [column] does not extend to the right of [range], that
   * is, where its upper bound is less than or equal to the upper bound of [range].
   *
   * It uses the PostgreSQL `&<` operator, with a range column such as `tsrange`, `daterange`, or
   * `int4range`. The lower bound of [range] does not matter. The rows where [column] is `NULL` are
   * left out.
   *
   * PostgREST: `column=nxr.range`
   *
   * **Example: reservations that end by 13:00**
   * ```kotlin
   * @Serializable data class Reservation(val id: Long, val room: String)
   *
   * // Keeps [2026-01-01 08:00,2026-01-01 13:00), but not [2026-01-01 12:00,2026-01-01 14:00).
   * val morning =
   *   sql
   *     .from("reservations")
   *     .select("id, room")
   *     .rangeLte("during", "[2026-01-01 12:00,2026-01-01 13:00)")
   *     .execute()
   *     .decodeList<Reservation>()
   * ```
   *
   * @param column The range column. For a column of an embedded table, use `table.column`.
   * @param range A range literal of the type of [column], sent as is: `[` or `(` for an inclusive
   * or exclusive lower bound, the two bounds separated by a comma, then `]` or `)` for an inclusive
   * or exclusive upper bound, for example `[2026-01-01 10:00,2026-01-01 12:00)`. Leave out a bound
   * to make the range unbounded on that side, for example `[2026-01-01 12:00,)`.
   * @return A new builder with the filter added.
   */
  public fun rangeLte(column: String, range: String): FilterBuilder

  /**
   * Keeps only the rows where the range in [column] is adjacent to [range]: the two ranges do not
   * overlap, and there is no gap between them.
   *
   * It uses the PostgreSQL `-|-` operator, with a range column such as `tsrange`, `daterange`, or
   * `int4range`. For example, `[2026-01-01 10:00,2026-01-01 12:00)` is adjacent to `[2026-01-01
   * 12:00,2026-01-01 13:00)`. The rows where [column] is `NULL` are left out.
   *
   * PostgREST: `column=adj.range`
   *
   * **Example: reservations right next to a time slot**
   * ```kotlin
   * @Serializable data class Reservation(val id: Long, val room: String)
   *
   * // Keeps [2026-01-01 10:00,2026-01-01 12:00) and [2026-01-01 13:00,2026-01-01 14:00).
   * val neighbors =
   *   sql
   *     .from("reservations")
   *     .select("id, room")
   *     .adjacent("during", "[2026-01-01 12:00,2026-01-01 13:00)")
   *     .execute()
   *     .decodeList<Reservation>()
   * ```
   *
   * @param column The range column. For a column of an embedded table, use `table.column`.
   * @param range A range literal of the type of [column], sent as is: `[` or `(` for an inclusive
   * or exclusive lower bound, the two bounds separated by a comma, then `]` or `)` for an inclusive
   * or exclusive upper bound, for example `[2026-01-01 10:00,2026-01-01 12:00)`. Leave out a bound
   * to make the range unbounded on that side, for example `[2026-01-01 12:00,)`.
   * @return A new builder with the filter added.
   */
  public fun adjacent(column: String, range: String): FilterBuilder

  /**
   * Keeps only the rows where [column] matches the full-text search [query].
   *
   * PostgreSQL turns [query] into a `tsquery` with the function that [textSearchType] selects, and
   * keeps the rows where `column @@ tsquery` is true:
   * - [TextSearchType.NONE]: `to_tsquery`. [query] must use the `tsquery` syntax: words joined by
   * `&` (AND), `|` (OR), or `<->` (followed by), `!` in front of a word for NOT, parentheses for
   * grouping, and `:*` after a word for a prefix match. Two words without an operator between them
   * make the request fail.
   * - [TextSearchType.PLAINTO]: `plainto_tsquery`. Every word of [query] must match, in any order;
   * operators and punctuation are ignored.
   * - [TextSearchType.PHRASETO]: `phraseto_tsquery`. The words of [query] must appear next to each
   * other, in this order.
   * - [TextSearchType.WEBSEARCH]: `websearch_to_tsquery`, with the syntax of web search engines:
   * every word must match, text in double quotes must match as a phrase, `or` between two words
   * means OR, and `-` in front of a word excludes it. The syntax of [query] never makes the request
   * fail.
   *
   * [config] names the text search configuration that splits and normalizes the words of [query].
   * For example, `english` matches `backups` with `backup`, and ignores words such as `the`. Use
   * the configuration that built the `tsvector` column, such as `english` for the `fts` column of
   * `articles`. On a text column, such as `body`, PostgreSQL converts the column with `to_tsvector`
   * and the default configuration of the database, whatever [config] is.
   *
   * PostgREST: `column=fts.query` for [TextSearchType.NONE], with `plfts`, `phfts`, or `wfts` in
   * place of `fts` for the other types, and with `(config)` after the operator when [config] is
   * set, for example `body=wfts(english).backup -cloud`
   *
   * **Example: `tsquery` syntax**
   * ```kotlin
   * @Serializable data class Article(val id: Long, val title: String)
   *
   * // Articles that mention backups, but not the cloud.
   * val articles =
   *   sql
   *     .from("articles")
   *     .select("id, title")
   *     .textSearch("fts", "backup & !cloud", TextSearchType.NONE, config = "english")
   *     .execute()
   *     .decodeList<Article>()
   * ```
   *
   * **Example: plain text**
   * ```kotlin
   * @Serializable data class Article(val id: Long, val title: String)
   *
   * // Articles that contain both words, in any form and order, such as "backup is fast".
   * val articles =
   *   sql
   *     .from("articles")
   *     .select("id, title")
   *     .textSearch("fts", "fast backups", TextSearchType.PLAINTO, config = "english")
   *     .execute()
   *     .decodeList<Article>()
   * ```
   *
   * **Example: web search syntax**
   * ```kotlin
   * @Serializable data class Article(val id: Long, val title: String)
   *
   * // The phrase "point in time", the word "recovery", and not the word "cloud".
   * val query = "\"point in time\" recovery -cloud"
   * val articles =
   *   sql
   *     .from("articles")
   *     .select("id, title")
   *     .textSearch("fts", query, TextSearchType.WEBSEARCH, config = "english")
   *     .execute()
   *     .decodeList<Article>()
   * ```
   *
   * **Example: a text column**
   * ```kotlin
   * @Serializable data class Article(val id: Long, val title: String)
   *
   * val articles =
   *   sql
   *     .from("articles")
   *     .select("id, title")
   *     .textSearch("body", "backup", TextSearchType.PLAINTO)
   *     .execute()
   *     .decodeList<Article>()
   * ```
   *
   * @param column A `tsvector` column, such as `fts`, or a text column, such as `body`. For a
   * column of an embedded table, use `table.column`.
   * @param query The text to search for, in the syntax of [textSearchType], sent as is.
   * @param textSearchType How to read [query], as described above.
   * @param config The text search configuration, a name such as `"english"` or `"simple"`, or
   * `null` (the default) for the default configuration of the database.
   * @return A new builder with the filter added.
   * @throws IllegalArgumentException If [config] is not a name made of letters, digits, and
   * underscores that does not start with a digit, such as `"english"`.
   */
  public fun textSearch(
    column: String,
    query: String,
    textSearchType: TextSearchType,
    config: String? = null
  ): FilterBuilder

  /**
   * Keeps only the rows that match at least one of the conditions in [expr].
   *
   * [expr] is a comma-separated list of conditions in PostgREST syntax, which the SDK sends as is,
   * inside parentheses:
   * - A condition is `column.operator.value`, with an operator of [filter], for example
   * `price.lt.10`, `category_id.is.null`, `id.in.(1,2,3)`, or `tags.cs.{sale,new}`.
   * - `not.` in front of the operator negates a condition, for example `category_id.not.eq.1`.
   * - `and(...)` and `or(...)` group conditions, and can be nested, for example
   * `category_id.eq.1,and(price.gte.10,price.lte.20)`.
   * - A value that contains a comma, a parenthesis, or a double quote must be in double quotes,
   * with a backslash in front of each `"` or `\` inside, for example `name.eq."Salt, coarse"`.
   *
   * Each call adds one condition, combined with the other filters with AND. With [referencedTable],
   * the conditions filter the rows of that embedded table, which must be in `select()`, instead of
   * the top-level rows: every top-level row is still returned, with `null` (or an empty list) for
   * the embedded table when none of its rows match, unless the table has `!inner` in `select()`.
   *
   * PostgREST: `or=(expr)`, or `referencedTable.or=(expr)` with [referencedTable]
   *
   * **Example: one condition or another**
   * ```kotlin
   * // price < 1 OR price > 100
   * val outliers =
   *   sql
   *     .from("products")
   *     .select()
   *     .or("price.lt.1,price.gt.100")
   *     .execute()
   *     .decodeList<Product>()
   * ```
   *
   * **Example: combine with other filters**
   * ```kotlin
   * // category_id = 1 AND (price < 1 OR the tags contain "sale")
   * val deals =
   *   sql
   *     .from("products")
   *     .select()
   *     .eq("category_id", 1)
   *     .or("price.lt.1,tags.cs.{sale}")
   *     .execute()
   *     .decodeList<Product>()
   * ```
   *
   * **Example: nested conditions and a quoted value**
   * ```kotlin
   * // name = 'Salt, coarse' OR (category_id = 2 AND price <= 5)
   * val products =
   *   sql
   *     .from("products")
   *     .select()
   *     .or("""name.eq."Salt, coarse",and(category_id.eq.2,price.lte.5)""")
   *     .execute()
   *     .decodeList<Product>()
   * ```
   *
   * **Example: filter the rows of an embedded table**
   * ```kotlin
   * @Serializable
   * data class ProductWithCategory(val name: String, val categories: Category? = null)
   *
   * val products =
   *   sql
   *     .from("products")
   *     .select("name, categories(id, name)")
   *     .or("name.eq.Fruit,name.eq.Vegetables", referencedTable = "categories")
   *     .execute()
   *     .decodeList<ProductWithCategory>()
   * ```
   *
   * Response:
   * ```json
   * [
   *   { "name": "Apple", "categories": { "id": 1, "name": "Fruit" } },
   *   { "name": "Salt", "categories": null }
   * ]
   * ```
   *
   * @param expr The conditions, separated by commas, without the outer parentheses.
   * @param referencedTable The embedded table whose rows the conditions filter, or `null` (the
   * default) for the top-level rows.
   * @return A new builder with the condition added.
   */
  public fun or(expr: String, referencedTable: String? = null): FilterBuilder

  /**
   * Keeps only the rows that match all the conditions in [expr].
   *
   * Chained filters are already combined with AND, so `and` is mostly useful to require several
   * groups of [or] conditions, or to filter the rows of an embedded table with [referencedTable].
   * [expr] uses the syntax of [or], and the SDK sends it as is, inside parentheses. With
   * [referencedTable], the conditions filter the rows of that embedded table, as described in [or].
   *
   * PostgREST: `and=(expr)`, or `referencedTable.and=(expr)` with [referencedTable]
   *
   * **Example: two groups of OR conditions**
   * ```kotlin
   * // (price < 1 OR price > 100) AND (category_id IS NULL OR category_id = 1)
   * val products =
   *   sql
   *     .from("products")
   *     .select()
   *     .and("or(price.lt.1,price.gt.100),or(category_id.is.null,category_id.eq.1)")
   *     .execute()
   *     .decodeList<Product>()
   * ```
   *
   * **Example: filter the rows of an embedded table**
   * ```kotlin
   * @Serializable
   * data class ProductWithCategory(val name: String, val categories: Category? = null)
   *
   * // categories is null for the products whose category does not match both conditions.
   * val products =
   *   sql
   *     .from("products")
   *     .select("name, categories(id, name)")
   *     .and("id.gte.10,name.like.F*", referencedTable = "categories")
   *     .execute()
   *     .decodeList<ProductWithCategory>()
   * ```
   *
   * @param expr The conditions, separated by commas, without the outer parentheses.
   * @param referencedTable The embedded table whose rows the conditions filter, or `null` (the
   * default) for the top-level rows.
   * @return A new builder with the condition added.
   */
  public fun and(expr: String, referencedTable: String? = null): FilterBuilder

  /**
   * Keeps only the rows where [column] matches [value] with the PostgREST [operator].
   *
   * Use it for an operator that has no dedicated method, such as [FilterOperator.OV] on a range
   * column or [FilterOperator.CS] on a `jsonb` column, or for an operator that is chosen at run
   * time. Each constant is the PostgREST operator with the same name in lowercase:
   * - `EQ`, `NEQ`, `GT`, `GTE`, `LT`, and `LTE`: SQL `=`, `<>`, `>`, `>=`, `<`, and `<=`.
   * - `LIKE` and `ILIKE`: SQL `LIKE` and `ILIKE`. `MATCH` and `IMATCH`: the regular expression
   * operators `~` and `~*`.
   * - `IN`: equal to one of a list. `IS`: SQL `IS`, with `null`, `true`, `false`, or `unknown`.
   * `ISDISTINCT`: SQL `IS DISTINCT FROM`.
   * - `CS`, `CD`, and `OV`: `@>` (contains), `<@` (is contained by), and `&&` (overlaps), for
   * arrays and ranges, and `CS` and `CD` for `jsonb` too.
   * - `SL`, `SR`, `NXL`, `NXR`, and `ADJ`: the range operators `<<`, `>>`, `&>`, `&<`, and `-|-`.
   * - `FTS`, `PLFTS`, `PHFTS`, and `WFTS`: full-text search. For a text search configuration, use
   * [textSearch].
   * - `EQ_ANY`, `GT_ANY`, `GTE_ANY`, `LT_ANY`, `LTE_ANY`, `LIKE_ANY`, `ILIKE_ANY`, `MATCH_ANY`, and
   * `IMATCH_ANY`, and the same with `_ALL`: the operator followed by `(any)` or `(all)`, for
   * example `eq(any)`. [value] is a list: `ANY` keeps a row when the comparison is true for at
   * least one element, and `ALL` when it is true for every element. An empty list keeps every row
   * with `_ALL` and no rows with `_ANY`. Otherwise, the rows where [column] is `NULL` are left out.
   *
   * In an `update()` or a `delete()`, an `_ALL` operator with an empty list changes every row of
   * the table. [TransformBuilder.maxAffected] makes the server reject a change of more rows than
   * expected.
   *
   * To negate the condition, use [filterNot].
   *
   * [value] is formatted for [operator]:
   * - With [FilterOperator.IN], a `List` is sent as a PostgREST list, as [isIn] does, for example
   * `(1,2,3)`.
   * - With [FilterOperator.CS], [FilterOperator.CD], [FilterOperator.OV], or an operator that ends
   * with `_ANY` or `_ALL`, a `List` is sent as a PostgreSQL array literal, as [contains] does, for
   * example `{a,b}`.
   * - A `String` is sent as is, so it must use the syntax that PostgREST expects for [operator],
   * for example `"(1,2)"` for `IN`, `"{a,b}"` for `CS` on an array, or a range literal for `OV`.
   * - `null` is sent as the text `null`, which PostgREST reads as SQL `NULL` only with
   * [FilterOperator.IS], for example `filter("deleted_at", FilterOperator.IS, null)`.
   * - Any other value is sent as described in [FilterBuilder], for example a `Map` as JSON.
   *
   * PostgREST: `column=operator.value`
   *
   * **Example: reservations that overlap a time slot**
   * ```kotlin
   * @Serializable data class Reservation(val id: Long, val room: String)
   *
   * val overlapping =
   *   sql
   *     .from("reservations")
   *     .select("id, room")
   *     .filter("during", FilterOperator.OV, "[2026-01-01 10:00,2026-01-01 12:00)")
   *     .execute()
   *     .decodeList<Reservation>()
   * ```
   *
   * **Example: a `jsonb` column that contains an object**
   * ```kotlin
   * // PostgREST: details=cs.{"color":"red"}
   * val red =
   *   sql
   *     .from("products")
   *     .select()
   *     .filter("details", FilterOperator.CS, mapOf("color" to "red"))
   *     .execute()
   *     .decodeList<Product>()
   * ```
   *
   * **Example: products cheaper than every price of a list**
   * ```kotlin
   * // PostgREST: price=lt(all).{2.5,3.0}
   * val cheapest =
   *   sql
   *     .from("products")
   *     .select()
   *     .filter("price", FilterOperator.LT_ALL, listOf(2.5, 3.0))
   *     .execute()
   *     .decodeList<Product>()
   * ```
   *
   * **Example: choose the operator at run time**
   * ```kotlin
   * val includeTwo = true
   * val operator = if (includeTwo) FilterOperator.LTE else FilterOperator.LT
   * val products =
   *   sql.from("products").select().filter("price", operator, 2).execute().decodeList<Product>()
   * ```
   *
   * @param column The column to filter. For a column of an embedded table, use `table.column`.
   * @param operator The PostgREST operator.
   * @param value The value, formatted for [operator] as described above.
   * @return A new builder with the filter added.
   * @throws IllegalArgumentException If [value], or an element of it, cannot be encoded, for
   * example `Double.NaN`, or an instance of your own class.
   */
  public fun filter(column: String, operator: FilterOperator, value: Any?): FilterBuilder

  /**
   * Keeps only the rows that do not match the condition that [operator] and [value] make on
   * [column].
   *
   * [operator] and [value] work as in [filter], and the operator is sent after `not.`. In SQL, the
   * negation of a comparison with `NULL` is still not true, so the rows where [column] is `NULL`
   * are left out, except with [FilterOperator.IS]: for example, `filterNot(column,
   * FilterOperator.IS, true)` keeps them.
   *
   * An empty list keeps every row with [FilterOperator.IN] or an `_ANY` operator, even the rows
   * where [column] is `NULL`, and no rows with an `_ALL` operator. So in an `update()` or a
   * `delete()`, an empty list can change every row of the table. [TransformBuilder.maxAffected]
   * makes the server reject a change of more rows than expected.
   *
   * PostgREST: `column=not.operator.value`
   *
   * **Example: products that have a category**
   * ```kotlin
   * // PostgREST: category_id=not.is.null
   * val categorized =
   *   sql
   *     .from("products")
   *     .select()
   *     .filterNot("category_id", FilterOperator.IS, null)
   *     .execute()
   *     .decodeList<Product>()
   * ```
   *
   * **Example: products without a tag**
   * ```kotlin
   * // PostgREST: tags=not.cs.{sale}
   * val fullPrice =
   *   sql
   *     .from("products")
   *     .select()
   *     .filterNot("tags", FilterOperator.CS, listOf("sale"))
   *     .execute()
   *     .decodeList<Product>()
   * ```
   *
   * **Example: names that do not contain a word**
   * ```kotlin
   * // PostgREST: name=not.ilike.%apple%
   * val others =
   *   sql
   *     .from("products")
   *     .select()
   *     .filterNot("name", FilterOperator.ILIKE, "%apple%")
   *     .execute()
   *     .decodeList<Product>()
   * ```
   *
   * @param column The column to filter. For a column of an embedded table, use `table.column`.
   * @param operator The PostgREST operator to negate.
   * @param value The value, formatted for [operator] as in [filter].
   * @return A new builder with the filter added.
   * @throws IllegalArgumentException If [value], or an element of it, cannot be encoded, for
   * example `Double.NaN`, or an instance of your own class.
   */
  public fun filterNot(column: String, operator: FilterOperator, value: Any?): FilterBuilder

  // The modifiers below return a FilterBuilder here, so that filters can still follow them. Their
  // documentation is in TransformBuilder. The modifiers that change the type of the response,
  // single(), maybeSingle(), csv(), geojson(), and explain(), keep returning a TransformBuilder,
  // which has no filter methods.

  override fun order(
    column: String,
    order: Order,
    nullsFirst: Boolean?,
    referencedTable: String?
  ): FilterBuilder

  override fun limit(count: Long, referencedTable: String?): FilterBuilder

  override fun range(from: Long, to: Long, referencedTable: String?): FilterBuilder

  override fun range(range: LongRange, referencedTable: String?): FilterBuilder

  override fun stripNulls(): FilterBuilder

  override fun dryRun(): FilterBuilder

  override fun maxAffected(count: Long): FilterBuilder

  override fun setHeader(name: String, value: String): FilterBuilder

  override fun noRetry(): FilterBuilder
}
