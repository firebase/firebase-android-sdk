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
import com.google.firebase.sql.Count
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.serializer

/**
 * Starts a request on one table or view: a read, an insert, an upsert, an update, or a delete.
 *
 * Get a [QueryBuilder] from [FirebaseSql.from][com.google.firebase.sql.FirebaseSql.from], then call
 * one of its five operations:
 * - [select] reads rows: `GET /<table>?select=<columns>`, or `HEAD` with `head = true`.
 * - [insert] adds rows: `POST /<table>`, with the rows as a JSON body.
 * - [upsert] adds rows, and updates or skips the rows that already exist: `POST /<table>` with
 * `Prefer: resolution=merge-duplicates` or `Prefer: resolution=ignore-duplicates`.
 * - [update] changes the rows that the filters match: `PATCH /<table>?<filters>`.
 * - [delete] removes the rows that the filters match: `DELETE /<table>?<filters>`.
 *
 * Each operation returns a [FilterBuilder]. Add filters such as [FilterBuilder.eq] to choose the
 * rows (several filters are combined with `AND`), then modifiers such as [TransformBuilder.order],
 * then call [TransformBuilder.execute]. Nothing is sent until `execute()`, which returns an
 * [OperationResult][com.google.firebase.sql.OperationResult], or throws when the request fails, for
 * example a [FirebaseSqlException][com.google.firebase.sql.FirebaseSqlException] when the server
 * rejects it.
 *
 * **Filter every update and delete.** Without a filter, [update] and [delete] apply to every row of
 * the table that the database lets the user change, for example through its row-level security
 * policies. To make the server reject a mutation that would change more rows than you expect, use
 * [TransformBuilder.maxAffected].
 *
 * **Request bodies.** [insert], [upsert], and [update] take the data in one of these forms:
 * - An object of a class annotated with `@Serializable`, for one row, or a `List` of them, for
 * several rows with [insert] or [upsert]. The extension functions `insert(data)`, `upsert(data)`,
 * and `update(data)` of this package encode it with the serializer of its type, which the compiler
 * finds, so that no class is looked up by reflection when the app runs. The members that take a
 * `SerializationStrategy` take the serializer as an argument instead. The keys are the serial names
 * of the properties, for example the names given with `@SerialName`. A property that keeps its
 * default value is left out, so the database fills that column, with its column default for a new
 * row. The other properties are sent, a `null` value as `null`. To send a property that keeps its
 * default value, write a `Map`, or annotate the property with `@EncodeDefault`.
 * - A `Map<String, Any?>` from column names to values, for one row, or a `List` of maps, for
 * several rows with [insert] or [upsert]. A kotlinx.serialization `JsonObject` is such a map, and a
 * `JsonArray` of objects is sent as it is.
 *
 * The values in maps can be `null`, strings, chars, booleans, numbers (including `BigDecimal`),
 * enums (sent as their name), `UUID`s, nested maps and lists, and `JsonElement`s. A `ByteArray`
 * value is sent as a PostgreSQL `bytea` in hex format, a `java.util.Date` as an ISO 8601 timestamp,
 * and a `java.sql.Date` or a `java.sql.Time` as its SQL literal. A number that is not finite, such
 * as `Double.NaN`, and an object of any other class make the operation throw an
 * `IllegalArgumentException`:
 * - Convert a `java.time` value to a `String`, for example with `toString()`. For a
 * `ZonedDateTime`, use `toOffsetDateTime().toString()`: `ZonedDateTime.toString()` ends with a zone
 * ID, such as `[Asia/Kolkata]`, that PostgreSQL rejects.
 * - Pass an object of a class annotated with `@Serializable` as the data itself, or encode it to a
 * `JsonElement` first.
 *
 * **Immutability.** Builders are immutable: each method returns a new builder, and leaves the
 * builder that it is called on unchanged. One [QueryBuilder] can therefore start several requests,
 * and a builder can be executed more than once and shared between threads.
 *
 * **Not stable for inheritance.** Later versions can add members to this interface, so implementing
 * it requires an opt-in to [InternalForInheritanceFirebaseApi]. Get a builder from
 * `FirebaseSql.from()` instead, and mock it in tests.
 *
 * **Example: the five operations**
 * ```kotlin
 * val products = sql.from("products")
 *
 * val cheap = products.select().lt("price", 2).execute().decodeList<Product>()
 * products.insert(Product(id = 1, name = "Apple", price = 1.5)).execute()
 * products.upsert(mapOf("id" to 1, "name" to "Apple", "price" to 1.25)).execute()
 * products.update(mapOf("price" to 1.0)).eq("id", 1).execute()
 * products.delete().eq("id", 1).execute()
 * ```
 */
@SubclassOptInRequired(InternalForInheritanceFirebaseApi::class)
public interface QueryBuilder {
  /**
   * Starts a read of the rows of the table, with the given [columns].
   *
   * Add filters to choose the rows, and modifiers such as [TransformBuilder.order] or
   * [TransformBuilder.range]. The response is a JSON array with one object for each row, which
   * `decodeList<T>()` decodes. [TransformBuilder.single] and [TransformBuilder.maybeSingle] return
   * one object instead.
   *
   * [columns] uses the syntax of the PostgREST `select` query parameter, with the items separated
   * by commas:
   * - `*`: every column. This is the default.
   * - `id, name`: only these columns.
   * - `label:name`: the column `name`, returned under the key `label`.
   * - `price::text`: the column cast to another PostgreSQL type, here `text`, so that the price is
   * returned as a JSON string.
   * - `details->color` or `details->>color`: the value of the key `color` in the `jsonb` column
   * `details`, as JSON with `->`, or as text with `->>`. It is returned under the key `color`.
   * Chain the operators to go deeper, for example `details->size->>width`.
   * - `categories(id, name)`: the row of `categories` that the foreign key `category_id` links to,
   * embedded under the key `categories`. It is a JSON object, or `null` when the product has no
   * category. In the other direction, `products(name)` in a read of `categories` embeds the
   * products of each category as a JSON array. The parentheses take the same syntax, so embedded
   * tables can be nested.
   * - `categories!inner(name)`: like `categories(name)`, but only the products that have a linked
   * category that matches the filters on `categories` are returned.
   * - `category:categories(name)`: an embedded table under another key.
   *
   * Spaces, tabs, and line breaks around the items are allowed, so [columns] can be a multi-line
   * string. To filter on an embedded table, include it in [columns], and name its columns
   * `<table>.<column>`, for example `eq("categories.name", "Fruit")`. Such a filter only filters
   * the embedded rows: the other rows are still returned, with `null` or an empty array for the
   * embedded table, unless it is embedded with `!inner`.
   *
   * PostgREST: `GET /<table>?select=<columns>`, or `HEAD` with [head]. [count] adds `Prefer:
   * count=<algorithm>`.
   *
   * **Example: read every product**
   * ```kotlin
   * val products = sql.from("products").select().execute().decodeList<Product>()
   * ```
   *
   * **Example: rename and cast columns**
   * ```kotlin
   * @Serializable data class PriceLabel(val id: Long, val label: String, val price: String)
   *
   * val labels =
   *   sql
   *     .from("products")
   *     .select("id, label:name, price::text")
   *     .order("id")
   *     .execute()
   *     .decodeList<PriceLabel>()
   * ```
   * Response:
   * ```json
   * [{"id": 1, "label": "Apple", "price": "1.5"}, {"id": 2, "label": "Pear", "price": "2.0"}]
   * ```
   *
   * **Example: read a value inside a `jsonb` column**
   * ```kotlin
   * @Serializable data class ProductColor(val id: Long, val color: String? = null)
   *
   * val colors =
   *   sql.from("products").select("id, details->>color").execute().decodeList<ProductColor>()
   * ```
   * Response:
   * ```json
   * [{"id": 1, "color": "red"}, {"id": 2, "color": null}]
   * ```
   *
   * **Example: embed the category of each product**
   * ```kotlin
   * @Serializable data class ProductCategory(val name: String, val category: Category? = null)
   *
   * val products =
   *   sql
   *     .from("products")
   *     .select("name, category:categories(id, name)")
   *     .execute()
   *     .decodeList<ProductCategory>()
   * ```
   * Response:
   * ```json
   * [
   *   {"name": "Apple", "category": {"id": 1, "name": "Fruit"}},
   *   {"name": "Gift card", "category": null}
   * ]
   * ```
   *
   * **Example: embed the products of each category**
   * ```kotlin
   * @Serializable data class CategoryProducts(val name: String, val products: List<Product>)
   *
   * val categories =
   *   sql
   *     .from("categories")
   *     .select("name, products(id, name, price)")
   *     .execute()
   *     .decodeList<CategoryProducts>()
   * ```
   * Response:
   * ```json
   * [
   *   {"name": "Fruit", "products": [{"id": 1, "name": "Apple", "price": 1.5}]},
   *   {"name": "Toys", "products": []}
   * ]
   * ```
   *
   * **Example: keep only the products of one category**
   * ```kotlin
   * val fruit =
   *   sql
   *     .from("products")
   *     .select("id, name, price, categories!inner(name)")
   *     .eq("categories.name", "Fruit")
   *     .execute()
   *     .decodeList<Product>()
   * ```
   *
   * **Example: count the rows without reading them**
   * ```kotlin
   * val result =
   *   sql
   *     .from("products")
   *     .select(head = true, count = Count.EXACT)
   *     .gt("price", 10)
   *     .execute()
   * val expensive = result.countOrNull() // The data is empty.
   * ```
   *
   * @param columns The columns to return, in the syntax above. `*` (the default) returns every
   * column.
   * @param head `true` to send a HEAD request, which returns no rows: the `data` of the result is
   * empty. Use it with [count] to get only the number of rows. `false` (the default) returns the
   * rows.
   * @param count The algorithm to count the rows that the filters match: [Count.EXACT],
   * [Count.PLANNED], or [Count.ESTIMATED]. The number is in
   * [com.google.firebase.sql.OperationResult.countOrNull], and includes every matching row, not
   * only the rows that [TransformBuilder.limit] or [TransformBuilder.range] return. `null` (the
   * default) counts nothing.
   * @return A new builder, on which to add the filters and the modifiers of the read.
   */
  public fun select(
    columns: String = "*",
    head: Boolean = false,
    count: Count? = null
  ): FilterBuilder

  /**
   * Starts an insert of one row, or of several rows in one request.
   *
   * Pass one object to insert one row, or a list of objects to insert several rows in a single
   * statement: if one row fails, for example because of a duplicate key, no row is inserted. See
   * [QueryBuilder] for the types that [data] accepts.
   *
   * A column that the object does not contain gets its default value from the table, for example
   * `now()` for `created_at`. In a list whose objects have different keys, the SDK sends the union
   * of the keys in the `columns` query parameter: a column that some objects contain and others do
   * not is inserted as `NULL` in the rows that do not contain it, or gets its default value there
   * when [defaultToNull] is `false`.
   *
   * A `@Serializable` object leaves out the properties that keep their Kotlin default value, so the
   * database fills those columns with their column defaults: for example, `Product(id = 2, name =
   * "Pear", price = 2.0)` sends only `id`, `name`, and `price`. The other properties are sent, a
   * `null` value as `null`. In a list, an object that keeps a default value lacks that key, so the
   * column gets `NULL` in its row unless [defaultToNull] is `false`.
   *
   * By default, the response has no body. To get the inserted rows, with the values that the
   * database filled in, call [TransformBuilder.select] after [insert].
   *
   * PostgREST: `POST /<table>` with [data] as a JSON body, and `columns=<keys>` for a list.
   *
   * **Example: insert one row**
   * ```kotlin
   * sql
   *   .from("products")
   *   .insert(mapOf("id" to 1, "name" to "Apple", "price" to 1.5, "category_id" to 1))
   *   .execute()
   * ```
   *
   * **Example: insert a row and read it back**
   * ```kotlin
   * val pear =
   *   sql
   *     .from("products")
   *     .insert(Product(id = 2, name = "Pear", price = 2.0))
   *     .select()
   *     .single()
   *     .execute()
   *     .decodeSingle<Product>()
   * ```
   * Response:
   * ```json
   * {
   *   "id": 2, "name": "Pear", "price": 2.0, "category_id": null, "tags": [], "details": null,
   *   "deleted_at": null, "created_at": "2026-10-13T18:49:24.123456+00:00"
   * }
   * ```
   *
   * **Example: insert rows that have different keys**
   * ```kotlin
   * sql
   *   .from("products")
   *   .insert(
   *     listOf(
   *       mapOf("id" to 3, "name" to "Kiwi", "price" to 0.5, "tags" to listOf("green")),
   *       mapOf("id" to 4, "name" to "Lime", "price" to 0.4),
   *     ),
   *     defaultToNull = false,
   *   )
   *   .execute()
   * // Sends columns=id,name,price,tags. The lime gets the default tags, '{}'. With the default
   * // defaultToNull = true, it would get NULL, which the NOT NULL constraint of tags rejects.
   * ```
   *
   * **Example: count the inserted rows**
   * ```kotlin
   * val result =
   *   sql
   *     .from("products")
   *     .insert(listOf(Product(5, "Plum", 0.8), Product(6, "Fig", 1.2)), count = Count.EXACT)
   *     .execute()
   * val inserted = result.countOrNull() // 2. The data is empty.
   * ```
   *
   * @param data The row to insert, as a `Map` from column names to values. See [QueryBuilder] for
   * the values that it can hold.
   * @param count The algorithm to count the inserted rows. The number is in
   * [com.google.firebase.sql.OperationResult.countOrNull]. `null` (the default) counts nothing.
   * @param defaultToNull What a list inserts in a row whose object lacks a column that other
   * objects contain: `NULL` when `true` (the default), or the column default when `false`, which
   * sends `Prefer: missing=default`. It has no effect on a single object, whose missing columns
   * always get their default value.
   * @return A new builder. Call [TransformBuilder.select] on it to get the inserted rows, or
   * [TransformBuilder.execute] to send the insert.
   * @throws IllegalArgumentException If [data] contains a value that the SDK cannot encode to JSON,
   * such as `Double.NaN` or an object of an unsupported class. See [QueryBuilder].
   */
  public fun insert(
    data: Map<String, Any?>,
    count: Count? = null,
    defaultToNull: Boolean = true
  ): FilterBuilder

  /**
   * Starts an insert of several rows in a single statement, one `Map` from column names to values
   * for each row. See the `insert` that takes one `Map` for the details.
   *
   * @param data The rows to insert. See [QueryBuilder] for the values that the maps can hold.
   * @param count The algorithm to count the inserted rows. `null` (the default) counts nothing. See
   * the `insert` that takes a `Map`.
   * @param defaultToNull What a row inserts for a column that it lacks and that other rows contain:
   * `NULL` when `true` (the default), or the column default when `false`. See the `insert` that
   * takes a `Map`.
   * @return A new builder. Call [TransformBuilder.select] on it to get the inserted rows, or
   * [TransformBuilder.execute] to send the insert.
   * @throws IllegalArgumentException If [data] contains a value that the SDK cannot encode to JSON,
   * such as `Double.NaN` or an object of an unsupported class. See [QueryBuilder].
   */
  public fun insert(
    data: List<Map<String, Any?>>,
    count: Count? = null,
    defaultToNull: Boolean = true
  ): FilterBuilder

  /**
   * Starts an insert of [data], encoded to JSON with [serializer]: one row for an object, or
   * several rows in a single statement for a list. See the `insert` that takes a `Map` for the
   * details.
   *
   * The [insert] extension function finds the serializer when the app is compiled, so
   * `insert(product)` sends the same request as `insert(product, Product.serializer())`.
   *
   * @param data The row or the rows to insert, for example an object of a class annotated with
   * `@Serializable`, or a `List` of them.
   * @param serializer Encodes [data], for example `Product.serializer()`, or
   * `ListSerializer(Product.serializer())` for a list.
   * @param count The algorithm to count the inserted rows. `null` (the default) counts nothing. See
   * the `insert` that takes a `Map`.
   * @param defaultToNull What a row inserts for a column that it lacks and that other rows contain:
   * `NULL` when `true` (the default), or the column default when `false`. See the `insert` that
   * takes a `Map`.
   * @return A new builder. Call [TransformBuilder.select] on it to get the inserted rows, or
   * [TransformBuilder.execute] to send the insert.
   * @throws IllegalArgumentException If [serializer] cannot encode [data] to JSON, for example a
   * `Double` that is not finite, or does not encode it as an object or a list of objects, for
   * example for a `String`.
   */
  public fun <T> insert(
    data: T,
    serializer: SerializationStrategy<T>,
    count: Count? = null,
    defaultToNull: Boolean = true
  ): FilterBuilder

  /**
   * Starts an insert that updates, or skips, the rows that already exist.
   *
   * A row already exists when an existing row has the same values in the [onConflict] columns, or
   * in the primary key when [onConflict] is `null`. These columns must be the primary key or have a
   * unique constraint. Include them in each object, so that the server can find the existing row.
   * - When [ignoreDuplicates] is `false` (the default), the existing row is updated: the columns
   * that the object contains get its values, and the other columns keep theirs.
   * - When [ignoreDuplicates] is `true`, the existing row is left as it is, and is not part of the
   * returned rows.
   *
   * The other rows are inserted like with [insert], in the same statement. See [QueryBuilder] for
   * the types that [data] accepts.
   *
   * In a list whose objects have different keys, every column of the union of the keys is written:
   * a key that is missing from an object writes `NULL`, or the column default when [defaultToNull]
   * is `false`, also over the value of an existing row.
   *
   * A `@Serializable` object leaves out the properties that keep their Kotlin default value, and
   * sends the others, a `null` value as `null`. For example, `Product(id = 1, name = "Apple", price
   * = 1.5)` sends only `id`, `name`, and `price`: a new row gets the column defaults in the other
   * columns, and an existing row keeps its values there. So a property that you set back to its
   * default value, such as `categoryId = null`, is not written: use a `Map`, or annotate the
   * property with `@EncodeDefault`, to write it.
   *
   * PostgREST: `POST /<table>` with `Prefer: resolution=merge-duplicates` or `Prefer:
   * resolution=ignore-duplicates`, plus `on_conflict=<onConflict>` when [onConflict] is set, and
   * `columns=<keys>` for a list.
   *
   * **Example: insert or update a product**
   * ```kotlin
   * // Inserts product 1, or sets the name and the price of the existing product 1. Its other
   * // columns, such as its tags, keep their values.
   * sql
   *   .from("products")
   *   .upsert(mapOf("id" to 1, "name" to "Apple", "price" to 1.25))
   *   .execute()
   * ```
   *
   * **Example: insert only the rows that do not exist**
   * ```kotlin
   * val inserted =
   *   sql
   *     .from("products")
   *     .upsert(
   *       listOf(
   *         mapOf("id" to 1, "name" to "Apple", "price" to 1.5),
   *         mapOf("id" to 9, "name" to "Mango", "price" to 2.5),
   *       ),
   *       ignoreDuplicates = true,
   *     )
   *     .select("id, name, price")
   *     .execute()
   *     .decodeList<Product>()
   * ```
   * Response, when product 1 exists (it is left unchanged):
   * ```json
   * [{"id": 9, "name": "Mango", "price": 2.5}]
   * ```
   *
   * **Example: find the existing rows by a unique column**
   * ```kotlin
   * // Assumes a unique constraint on categories.name. Adds the category "Fruit", unless a
   * // category with that name exists.
   * sql
   *   .from("categories")
   *   .upsert(Category(id = 7, name = "Fruit"), onConflict = "name", ignoreDuplicates = true)
   *   .execute()
   * ```
   *
   * @param data The row to insert or update, as a `Map` from column names to values. See
   * [QueryBuilder] for the values that it can hold.
   * @param onConflict The columns that identify an existing row, separated by commas, for example
   * `"name"` or `"order_id,product_id"`. They must be the primary key or have a unique constraint.
   * `null` (the default) uses the primary key. It must not be blank.
   * @param ignoreDuplicates `false` (the default) to update the existing rows (`ON CONFLICT ... DO
   * UPDATE`), or `true` to leave them as they are (`ON CONFLICT ... DO NOTHING`).
   * @param count The algorithm to count the rows that the upsert inserts or updates. The number is
   * in [com.google.firebase.sql.OperationResult.countOrNull]. `null` (the default) counts nothing.
   * @param defaultToNull What a list writes in a row whose object lacks a column that other objects
   * contain: `NULL` when `true` (the default), or the column default when `false`. See [insert].
   * @return A new builder. Call [TransformBuilder.select] on it to get the inserted and updated
   * rows, or [TransformBuilder.execute] to send the upsert.
   * @throws IllegalArgumentException If [data] contains a value that the SDK cannot encode to JSON,
   * such as `Double.NaN` or an object of an unsupported class. See [QueryBuilder]. Also if
   * [onConflict] is blank.
   */
  public fun upsert(
    data: Map<String, Any?>,
    onConflict: String? = null,
    ignoreDuplicates: Boolean = false,
    count: Count? = null,
    defaultToNull: Boolean = true
  ): FilterBuilder

  /**
   * Starts an upsert of several rows in a single statement, one `Map` from column names to values
   * for each row. See the `upsert` that takes one `Map` for the details.
   *
   * @param data The rows to insert or update. See [QueryBuilder] for the values that the maps can
   * hold.
   * @param onConflict The columns that identify an existing row, separated by commas. `null` (the
   * default) uses the primary key. See the `upsert` that takes a `Map`.
   * @param ignoreDuplicates `false` (the default) to update the existing rows, or `true` to leave
   * them as they are.
   * @param count The algorithm to count the rows that the upsert inserts or updates. `null` (the
   * default) counts nothing.
   * @param defaultToNull What a row writes for a column that it lacks and that other rows contain:
   * `NULL` when `true` (the default), or the column default when `false`.
   * @return A new builder. Call [TransformBuilder.select] on it to get the inserted and updated
   * rows, or [TransformBuilder.execute] to send the upsert.
   * @throws IllegalArgumentException If [data] contains a value that the SDK cannot encode to JSON,
   * such as `Double.NaN` or an object of an unsupported class. See [QueryBuilder]. Also if
   * [onConflict] is blank.
   */
  public fun upsert(
    data: List<Map<String, Any?>>,
    onConflict: String? = null,
    ignoreDuplicates: Boolean = false,
    count: Count? = null,
    defaultToNull: Boolean = true
  ): FilterBuilder

  /**
   * Starts an upsert of [data], encoded to JSON with [serializer]: one row for an object, or
   * several rows in a single statement for a list. See the `upsert` that takes a `Map` for the
   * details.
   *
   * The [upsert] extension function finds the serializer when the app is compiled, so
   * `upsert(product)` sends the same request as `upsert(product, Product.serializer())`.
   *
   * @param data The row or the rows to insert or update, for example an object of a class annotated
   * with `@Serializable`, or a `List` of them.
   * @param serializer Encodes [data], for example `Product.serializer()`, or
   * `ListSerializer(Product.serializer())` for a list.
   * @param onConflict The columns that identify an existing row, separated by commas. `null` (the
   * default) uses the primary key. See the `upsert` that takes a `Map`.
   * @param ignoreDuplicates `false` (the default) to update the existing rows, or `true` to leave
   * them as they are.
   * @param count The algorithm to count the rows that the upsert inserts or updates. `null` (the
   * default) counts nothing.
   * @param defaultToNull What a row writes for a column that it lacks and that other rows contain:
   * `NULL` when `true` (the default), or the column default when `false`.
   * @return A new builder. Call [TransformBuilder.select] on it to get the inserted and updated
   * rows, or [TransformBuilder.execute] to send the upsert.
   * @throws IllegalArgumentException If [serializer] cannot encode [data] to JSON, for example a
   * `Double` that is not finite, or does not encode it as an object or a list of objects, for
   * example for a `String`.
   */
  public fun <T> upsert(
    data: T,
    serializer: SerializationStrategy<T>,
    onConflict: String? = null,
    ignoreDuplicates: Boolean = false,
    count: Count? = null,
    defaultToNull: Boolean = true
  ): FilterBuilder

  /**
   * Starts an update of the rows that the filters match.
   *
   * Add at least one filter, such as [FilterBuilder.eq]: without a filter, the update changes every
   * row of the table that the database lets the user change, for example through its row-level
   * security policies.
   *
   * [data] holds the columns to change and their new values; the other columns keep their values. A
   * `@Serializable` object sets the properties that do not keep their default value, a `null` value
   * as `NULL`, so a property that you set back to its default value is not written: use a `Map` to
   * choose the columns exactly. See [QueryBuilder] for the types that [data] accepts.
   *
   * By default, the response has no body. To get the updated rows, call [TransformBuilder.select]
   * after the filters. To make the server reject an update that would change more rows than you
   * expect, use [TransformBuilder.maxAffected].
   *
   * PostgREST: `PATCH /<table>?<filters>` with [data] as a JSON body.
   *
   * **Example: change one row**
   * ```kotlin
   * sql.from("products").update(mapOf("price" to 1.75)).eq("id", 1).execute()
   * ```
   *
   * **Example: change several rows and return them**
   * ```kotlin
   * val discounted =
   *   sql
   *     .from("products")
   *     .update(mapOf("price" to 0.99))
   *     .eq("category_id", 1)
   *     .gt("price", 0.99)
   *     .select("id, name, price")
   *     .execute()
   *     .decodeList<Product>()
   * ```
   * Response:
   * ```json
   * [{"id": 1, "name": "Apple", "price": 0.99}, {"id": 2, "name": "Pear", "price": 0.99}]
   * ```
   *
   * **Example: mark rows as deleted and count them**
   * ```kotlin
   * val result =
   *   sql
   *     .from("products")
   *     .update(mapOf("deleted_at" to Date()), count = Count.EXACT) // java.util.Date
   *     .eq("category_id", 3)
   *     .exact("deleted_at", null)
   *     .execute()
   * val archived = result.countOrNull()
   * ```
   *
   * **Example: refuse to change more than one row**
   * ```kotlin
   * // Fails with the code PGRST124, and changes nothing, if several products are named "Apple".
   * sql
   *   .from("products")
   *   .update(mapOf("name" to "Green apple"))
   *   .eq("name", "Apple")
   *   .maxAffected(1)
   *   .execute()
   * ```
   *
   * @param data The columns to change and their new values, as a `Map` from column names to values,
   * for example a `JsonObject`. See [QueryBuilder] for the values that it can hold.
   * @param count The algorithm to count the updated rows. The number is in
   * [com.google.firebase.sql.OperationResult.countOrNull]. `null` (the default) counts nothing.
   * @return A new builder, on which to add the filters.
   * @throws IllegalArgumentException If [data] contains a value that the SDK cannot encode to JSON,
   * such as `Double.NaN` or an object of an unsupported class. See [QueryBuilder].
   */
  public fun update(
    data: Map<String, Any?>,
    count: Count? = null,
  ): FilterBuilder

  /**
   * Starts an update with the columns of [data], encoded to a JSON object with [serializer]. See
   * the `update` that takes a `Map` for the details.
   *
   * The [update] extension function finds the serializer when the app is compiled, so
   * `update(product)` sends the same request as `update(product, Product.serializer())`. Every
   * property of the class is set, including the `null` ones, so use a `Map` to change only some
   * columns.
   *
   * @param data The columns to change and their new values, for example an object of a class
   * annotated with `@Serializable`.
   * @param serializer Encodes [data], for example `Product.serializer()`.
   * @param count The algorithm to count the updated rows. `null` (the default) counts nothing.
   * @return A new builder, on which to add the filters.
   * @throws IllegalArgumentException If [serializer] cannot encode [data] to JSON, for example a
   * `Double` that is not finite, or does not encode it as an object, for example for a list.
   */
  public fun <T> update(
    data: T,
    serializer: SerializationStrategy<T>,
    count: Count? = null,
  ): FilterBuilder

  /**
   * Starts a delete of the rows that the filters match.
   *
   * Add at least one filter, such as [FilterBuilder.eq]: without a filter, the delete removes every
   * row of the table that the database lets the user delete, for example through its row-level
   * security policies.
   *
   * By default, the response has no body. To get the deleted rows, call [TransformBuilder.select]
   * after the filters. To make the server reject a delete that would remove more rows than you
   * expect, use [TransformBuilder.maxAffected].
   *
   * PostgREST: `DELETE /<table>?<filters>`
   *
   * **Example: delete one row**
   * ```kotlin
   * sql.from("products").delete().eq("id", 1).execute()
   * ```
   *
   * **Example: delete rows and return them**
   * ```kotlin
   * val removed =
   *   sql
   *     .from("products")
   *     .delete()
   *     .lt("deleted_at", "2026-01-01T00:00:00Z")
   *     .select("id, name, price")
   *     .execute()
   *     .decodeList<Product>()
   * ```
   * Response:
   * ```json
   * [{"id": 7, "name": "Old pear", "price": 2.0}]
   * ```
   *
   * **Example: count the deleted rows**
   * ```kotlin
   * val result =
   *   sql
   *     .from("products")
   *     .delete(count = Count.EXACT)
   *     .isIn("id", listOf(5, 6))
   *     .execute()
   * val deleted = result.countOrNull()
   * ```
   *
   * @param count The algorithm to count the deleted rows. The number is in
   * [com.google.firebase.sql.OperationResult.countOrNull]. `null` (the default) counts nothing.
   * @return A new builder, on which to add the filters.
   */
  public fun delete(count: Count? = null): FilterBuilder
}

/**
 * Starts an insert of [data]: an object of a class annotated with `@Serializable` for one row, or a
 * `List` of them for several rows in a single statement.
 *
 * It calls [QueryBuilder.insert] with the serializer of the type [T], which the compiler finds, so
 * that no class is looked up by reflection when the app runs. See the `insert` that takes a `Map`
 * for the details.
 *
 * **Example: insert a row**
 * ```kotlin
 * sql.from("products").insert(Product(id = 7, name = "Melon", price = 3.0)).execute()
 * ```
 *
 * @param data The row or the rows to insert.
 * @param count The algorithm to count the inserted rows. `null` (the default) counts nothing.
 * @param defaultToNull What a row inserts for a column that it lacks and that other rows contain:
 * `NULL` when `true` (the default), or the column default when `false`.
 * @return A new builder. Call [TransformBuilder.select] on it to get the inserted rows, or
 * [TransformBuilder.execute] to send the insert.
 * @throws IllegalArgumentException If [data] cannot be encoded to JSON, for example a `Double` that
 * is not finite, or is not an object or a list of objects, for example a `String`.
 */
public inline fun <reified T> QueryBuilder.insert(
  data: T,
  count: Count? = null,
  defaultToNull: Boolean = true
): FilterBuilder = insert(data, serializer<T>(), count, defaultToNull)

/**
 * Starts an upsert of [data]: an object of a class annotated with `@Serializable` for one row, or a
 * `List` of them for several rows in a single statement.
 *
 * It calls [QueryBuilder.upsert] with the serializer of the type [T], which the compiler finds, so
 * that no class is looked up by reflection when the app runs. See the `upsert` that takes a `Map`
 * for the details.
 *
 * **Example: insert or update a category**
 * ```kotlin
 * sql.from("categories").upsert(Category(id = 7, name = "Fruit")).execute()
 * ```
 *
 * @param data The row or the rows to insert or update.
 * @param onConflict The columns that identify an existing row, separated by commas. `null` (the
 * default) uses the primary key. See [QueryBuilder.upsert].
 * @param ignoreDuplicates `false` (the default) to update the existing rows, or `true` to leave
 * them as they are.
 * @param count The algorithm to count the rows that the upsert inserts or updates. `null` (the
 * default) counts nothing.
 * @param defaultToNull What a row writes for a column that it lacks and that other rows contain:
 * `NULL` when `true` (the default), or the column default when `false`.
 * @return A new builder. Call [TransformBuilder.select] on it to get the inserted and updated rows,
 * or [TransformBuilder.execute] to send the upsert.
 * @throws IllegalArgumentException If [data] cannot be encoded to JSON, for example a `Double` that
 * is not finite, or is not an object or a list of objects, for example a `String`.
 */
public inline fun <reified T> QueryBuilder.upsert(
  data: T,
  onConflict: String? = null,
  ignoreDuplicates: Boolean = false,
  count: Count? = null,
  defaultToNull: Boolean = true
): FilterBuilder = upsert(data, serializer<T>(), onConflict, ignoreDuplicates, count, defaultToNull)

/**
 * Starts an update with the columns of [data], an object of a class annotated with `@Serializable`.
 *
 * It calls [QueryBuilder.update] with the serializer of the type [T], which the compiler finds, so
 * that no class is looked up by reflection when the app runs. The properties that keep their
 * default value are not set, so use a `Map` to choose the columns exactly. See the `update` that
 * takes a `Map` for the details.
 *
 * **Example: change every column of a row**
 * ```kotlin
 * sql.from("categories").update(Category(id = 7, name = "Fruit")).eq("id", 7).execute()
 * ```
 *
 * @param data The columns to change and their new values.
 * @param count The algorithm to count the updated rows. `null` (the default) counts nothing.
 * @return A new builder, on which to add the filters.
 * @throws IllegalArgumentException If [data] cannot be encoded to JSON, for example a `Double` that
 * is not finite, or is not an object, for example a list.
 */
public inline fun <reified T> QueryBuilder.update(
  data: T,
  count: Count? = null,
): FilterBuilder = update(data, serializer<T>(), count)
