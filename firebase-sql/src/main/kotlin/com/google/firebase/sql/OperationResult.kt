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

package com.google.firebase.sql

import com.google.firebase.sql.core.defaultJson
import java.util.Objects
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.serializer

/**
 * The response of a successful request, which `execute()` returns.
 *
 * `execute()` throws an exception when a request fails, so a result always holds a successful
 * response, whose [status] is from `200` to `299`.
 *
 * Read the data with the decode functions, which decode JSON into Kotlin types and into classes
 * annotated with `@Serializable`:
 * - [decodeList] decodes rows.
 * - [decodeSingle] decodes one row: the JSON object that `single()` returns, or the only row of a
 * JSON array. [decodeSingleOrNull] does the same, or gives `null` when there is no row, for example
 * after `maybeSingle()`.
 * - [decodeAs] decodes any JSON value, for example the result of a function, and [decodeAsOrNull]
 * gives `null` instead of an error.
 * - To read rows without a class, decode them as JSON objects: `decodeList<JsonObject>()`.
 *
 * Each decode function takes the type as a type argument, for example `decodeList<Product>()`, or a
 * deserializer, for example `decodeList(Product.serializer())`. When the data cannot be decoded,
 * they throw the [SerializationException] of kotlinx.serialization, which is an
 * [IllegalArgumentException].
 *
 * [data] gives the body as text, for example after `csv()`. [countOrNull] gives the number of rows
 * that the server counted, and [rangeOrNull] the positions of the returned rows.
 *
 * **Example: read rows**
 * ```kotlin
 * val products = sql.from("products").select().lt("price", 2).execute().decodeList<Product>()
 * ```
 *
 * **Example: read a page and the total number of rows**
 * ```kotlin
 * val result = sql.from("products").select(count = Count.EXACT).order("id").range(0, 19).execute()
 * val products = result.decodeList<Product>() // At most 20 products.
 * val total = result.countOrNull() // Every product, not only the 20 of the page.
 * ```
 *
 * **Example: handle an error**
 * ```kotlin
 * try {
 *   val products = sql.from("products").select().execute().decodeList<Product>()
 * } catch (e: FirebaseSqlException) {
 *   Log.w("Products", "The server rejected the request: ${e.message}", e)
 * } catch (e: IOException) {
 *   Log.w("Products", "No response, for example offline: ${e.message}", e)
 * }
 * ```
 *
 * @property data The body of the response, as text: JSON by default, CSV after `csv()`, GeoJSON
 * after `geojson()`, or the plan after `explain()`. It is empty when the response has no body, for
 * example for a HEAD request, or for a mutation without `select()`. After `maybeSingle()`, it is
 * the JSON object of the row, or `null` when no row matches.
 * @property status The HTTP status code of the response, for example `200`, `201`, `204`, or `206`.
 * @property statusText The HTTP status text of the response, for example `OK`. It is empty for an
 * HTTP/2 response, which has no status text.
 */
public class OperationResult
internal constructor(
  public val data: String,
  public val status: Int,
  public val statusText: String,
  private val count: Long? = null,
  private val range: LongRange? = null
) {

  /**
   * Returns the total number of rows that the server counted, or `null` when the request did not
   * ask for a count.
   *
   * Pass a [Count] to `select()`, to a mutation, or to `rpc()` to get a count. For a read, it
   * counts the rows that match the filters on all the pages, not only the rows of [data]. The
   * server sends it in the `Content-Range` header.
   *
   * **Example: count the rows without reading them**
   * ```kotlin
   * val total =
   *   sql.from("products").select(head = true, count = Count.EXACT).execute().countOrNull()
   * ```
   *
   * @return The number of rows, or `null`.
   */
  public fun countOrNull(): Long? = count

  /**
   * Returns the positions of the returned rows, counting from 0, or `null` when the response has no
   * rows or does not give their positions.
   *
   * PostgREST sends the positions in the `Content-Range` header, for example `20-39/250` for the
   * second page of 20 rows out of 250, which gives `20..39`.
   *
   * **Example: find out where a page ends**
   * ```kotlin
   * val result = sql.from("products").select().order("id").range(20, 39).execute()
   * val positions = result.rangeOrNull() // 20..39, or fewer rows on the last page.
   * ```
   *
   * @return The positions of the first and the last returned rows, or `null`.
   */
  public fun rangeOrNull(): LongRange? = range

  /**
   * Decodes the JSON array of the data into a list of [T].
   *
   * Use it for the rows of `select()`, of a mutation followed by `select()`, or of a function that
   * returns rows. [T] is usually a class annotated with `@Serializable` whose properties match the
   * selected columns; use `@SerialName` for a column whose name is not the name of the property,
   * for example `category_id`. JSON keys that [T] does not declare are ignored, and a property of
   * [T] that is missing from the JSON must have a default value.
   *
   * **Example: decode rows**
   * ```kotlin
   * val products =
   *   sql
   *     .from("products")
   *     .select("id, name, price")
   *     .lt("price", 10)
   *     .order("price", Order.ASCENDING)
   *     .execute()
   *     .decodeList<Product>()
   * ```
   *
   * Response:
   * ```json
   * [{"id": 2, "name": "Banana", "price": 0.25}, {"id": 1, "name": "Apple", "price": 0.5}]
   * ```
   *
   * **Example: decode rows with the row that they reference**
   * ```kotlin
   * @Serializable data class ProductName(val name: String, val categories: Category? = null)
   *
   * // Embeds the row of categories that the foreign key category_id references.
   * val names =
   *   sql
   *     .from("products")
   *     .select("name, categories(id, name)")
   *     .execute()
   *     .decodeList<ProductName>()
   * ```
   *
   * Response:
   * ```json
   * [{"name": "Apple", "categories": {"id": 1, "name": "Fruit"}}]
   * ```
   *
   * **Example: decode the rows that a mutation changed**
   * ```kotlin
   * val discounted =
   *   sql
   *     .from("products")
   *     .update(mapOf("price" to 0.5))
   *     .eq("category_id", 1)
   *     .select("id, name, price")
   *     .execute()
   *     .decodeList<Product>()
   * ```
   *
   * @return The decoded rows, in the order of the response.
   * @throws IllegalStateException When the response has no body, for example for a HEAD request or
   * for a mutation without `select()`.
   * @throws SerializationException When the data is not a JSON array of [T], for example the object
   * of `single()`. kotlinx.serialization also throws an [IllegalArgumentException], of which
   * [SerializationException] is a subclass, for some values that do not fit [T].
   */
  public inline fun <reified T> decodeList(): List<T> = decodeList(serializer<T>())

  /**
   * Decodes the JSON array of the data into a list, with [deserializer] for each element.
   *
   * `decodeList<T>()` calls it with the serializer of [T]. Call it directly when the type of the
   * rows is not known at compile time, for example in a generic class, or to decode the rows with a
   * serializer other than the one of their class.
   *
   * **Example: decode rows in a generic class**
   * ```kotlin
   * class Table<T>(private val name: String, private val deserializer: KSerializer<T>) {
   *   suspend fun all(): List<T> = sql.from(name).select().execute().decodeList(deserializer)
   * }
   *
   * val products = Table("products", Product.serializer()).all()
   * ```
   *
   * @param deserializer The deserializer of one row, for example `Product.serializer()`.
   * @return The decoded rows, in the order of the response.
   * @throws IllegalStateException When the response has no body, for example for a HEAD request or
   * for a mutation without `select()`.
   * @throws SerializationException When the data is not a JSON array, for example the object of
   * `single()`, or when [deserializer] cannot decode one of its elements.
   */
  public fun <T> decodeList(deserializer: DeserializationStrategy<T>): List<T> {
    check(data.isNotBlank()) { noDataMessage("decodeList()") }
    return defaultJson.decodeFromString(JsonArray.serializer(), data).map {
      defaultJson.decodeFromJsonElement(deserializer, it)
    }
  }

  /**
   * Decodes the one row of the data into a [T].
   *
   * The server sends the row in one of two shapes, and both work:
   * - a JSON object, after `single()` or `maybeSingle()`;
   * - a JSON array with one element, without them, for example after `select().eq("id", 42)`.
   *
   * With `single()`, the server checks that exactly one row matches. Without it, this function
   * checks it: an empty array, or an array of several rows, throws instead of giving the first row.
   * Any other JSON value, such as the number that a function returns, is decoded as it is. To
   * decode a whole array, such as the `int[]` that a function returns, use [decodeList] or
   * [decodeAs].
   *
   * JSON keys that [T] does not declare are ignored, and a property of [T] that is missing from the
   * JSON must have a default value. When no row may match, use [decodeSingleOrNull], which gives
   * `null` instead of an error.
   *
   * **Example: read one row by its ID**
   * ```kotlin
   * val product =
   *   sql.from("products").select().eq("id", 42).single().execute().decodeSingle<Product>()
   * ```
   *
   * Response, whose `details`, `deleted_at`, and `created_at` keys `Product` ignores:
   * ```json
   * {
   *   "id": 42,
   *   "name": "Kiwi",
   *   "price": 0.5,
   *   "category_id": 1,
   *   "tags": ["fruit"],
   *   "details": null,
   *   "deleted_at": null,
   *   "created_at": "2026-01-31T12:00:00+00:00"
   * }
   * ```
   *
   * **Example: read one row without single()**
   * ```kotlin
   * val product = sql.from("products").select().eq("id", 42).execute().decodeSingle<Product>()
   * ```
   *
   * **Example: insert a row and read it back**
   * ```kotlin
   * val kiwi =
   *   sql
   *     .from("products")
   *     .insert(Product(id = 42, name = "Kiwi", price = 0.5, categoryId = 1))
   *     .select()
   *     .single()
   *     .execute()
   *     .decodeSingle<Product>()
   * ```
   *
   * @return The decoded row.
   * @throws NoSuchElementException When the data has no row: the JSON `null`, or an empty array.
   * @throws IllegalArgumentException When the data has several rows.
   * @throws IllegalStateException When the response has no body, for example for a mutation without
   * `select()`.
   * @throws SerializationException When the row cannot be decoded into a [T].
   */
  public inline fun <reified T> decodeSingle(): T = decodeSingle(serializer<T>())

  /**
   * Decodes the one row of the data with [deserializer]. It accepts the same shapes as the
   * `decodeSingle` without a parameter: a JSON object, or a JSON array with one element.
   *
   * `decodeSingle<T>()` calls it with the serializer of [T]. Call it directly when the type is not
   * known at compile time, or to decode the row with a serializer other than the one of its class.
   *
   * **Example: read one row with the serializer of its class**
   * ```kotlin
   * val product =
   *   sql
   *     .from("products")
   *     .select()
   *     .eq("id", 42)
   *     .single()
   *     .execute()
   *     .decodeSingle(Product.serializer())
   * ```
   *
   * @param deserializer The deserializer of the row, for example `Product.serializer()`.
   * @return The decoded row.
   * @throws NoSuchElementException When the data has no row: the JSON `null`, or an empty array.
   * @throws IllegalArgumentException When the data has several rows.
   * @throws IllegalStateException When the response has no body, for example for a mutation without
   * `select()`.
   * @throws SerializationException When [deserializer] cannot decode the row.
   */
  public fun <T> decodeSingle(deserializer: DeserializationStrategy<T>): T {
    val row =
      oneRowOrNull("decodeSingle()")
        ?: throw NoSuchElementException(
          "decodeSingle() found no row. Use decodeSingleOrNull() when no row may match."
        )
    return defaultJson.decodeFromJsonElement(deserializer, row)
  }

  /**
   * Decodes the one row of the data into a [T], or returns `null` when there is no row.
   *
   * It accepts the same shapes as [decodeSingle]: a JSON object, or a JSON array with one element.
   * It returns `null` for the JSON `null`, which `maybeSingle()` gives when no row matches, and for
   * an empty array. It throws for an array of several rows, for empty data, which means that the
   * request asked for no rows, such as a mutation without `select()`, and when the row cannot be
   * decoded, so that a mistake in [T] does not look like a missing row.
   *
   * **Example: read a row that may not exist**
   * ```kotlin
   * val product =
   *   sql
   *     .from("products")
   *     .select()
   *     .eq("id", 42)
   *     .maybeSingle()
   *     .execute()
   *     .decodeSingleOrNull<Product>() // null when no product has the ID 42.
   * ```
   *
   * @return The decoded row, or `null` when there is no row.
   * @throws IllegalArgumentException When the data has several rows.
   * @throws IllegalStateException When the response has no body, for example for a mutation without
   * `select()`.
   * @throws SerializationException When the row cannot be decoded into a [T].
   */
  public inline fun <reified T> decodeSingleOrNull(): T? = decodeSingleOrNull(serializer<T>())

  /**
   * Decodes the one row of the data with [deserializer], or returns `null` when there is no row:
   * the JSON `null`, or an empty array.
   *
   * `decodeSingleOrNull<T>()` calls it with the serializer of [T], and follows the same rules.
   *
   * **Example: read a row that may not exist, with the serializer of its class**
   * ```kotlin
   * val product =
   *   sql
   *     .from("products")
   *     .select()
   *     .eq("id", 42)
   *     .maybeSingle()
   *     .execute()
   *     .decodeSingleOrNull(Product.serializer()) // null when no product has the ID 42.
   * ```
   *
   * @param deserializer The deserializer of the row, for example `Product.serializer()`.
   * @return The decoded row, or `null` when there is no row.
   * @throws IllegalArgumentException When the data has several rows.
   * @throws IllegalStateException When the response has no body, for example for a mutation without
   * `select()`.
   * @throws SerializationException When [deserializer] cannot decode the row.
   */
  public fun <T> decodeSingleOrNull(deserializer: DeserializationStrategy<T>): T? {
    val row = oneRowOrNull("decodeSingleOrNull()")
    if (row == null || row is JsonNull) return null
    return defaultJson.decodeFromJsonElement(deserializer, row)
  }

  /**
   * Returns the one row of the data: the element of an array with one element, or the JSON value
   * itself when it is not an array. Returns `null` for the JSON `null` and for an empty array.
   *
   * @throws IllegalStateException When the response has no body.
   * @throws IllegalArgumentException When the data has several rows.
   * @throws SerializationException When the data is not valid JSON.
   */
  private fun oneRowOrNull(function: String): JsonElement? {
    check(data.isNotBlank()) { noDataMessage(function) }
    return when (val element = defaultJson.parseToJsonElement(data)) {
      is JsonNull -> null
      is JsonArray ->
        when (element.size) {
          0 -> null
          1 -> element[0]
          else ->
            throw IllegalArgumentException(
              "$function expects one row, but the data has ${element.size} rows. Add a filter, " +
                "or call single() so that the server checks that one row matches."
            )
        }
      else -> element
    }
  }

  /**
   * Decodes the JSON of the data into [T], for any type that kotlinx.serialization decodes: a
   * number, a `String`, a `Boolean`, a `List`, a `Map`, a `JsonElement`, or a class annotated with
   * `@Serializable`.
   *
   * Use it for the result of a function that returns one value, which PostgREST answers with that
   * JSON value, for example `5`. A JSON string decodes into a `String` without its quotes.
   *
   * **Example: the result of a function**
   * ```kotlin
   * val sum = sql.rpc("add_numbers", mapOf("a" to 2, "b" to 3)).execute().decodeAs<Int>()
   * ```
   *
   * Response:
   * ```json
   * 5
   * ```
   *
   * **Example: a row as a map**
   * ```kotlin
   * val row =
   *   sql
   *     .from("products")
   *     .select("id, details")
   *     .eq("id", 42)
   *     .single()
   *     .execute()
   *     .decodeAs<Map<String, JsonElement>>()
   * val details = row["details"] // A JsonObject, or JsonNull when the column is NULL.
   * ```
   *
   * @return The decoded value.
   * @throws SerializationException When the data cannot be decoded into a [T], for example when the
   * response has no body. kotlinx.serialization also throws an [IllegalArgumentException], of which
   * [SerializationException] is a subclass, for some values that do not fit [T].
   */
  public inline fun <reified T> decodeAs(): T = decodeAs(serializer<T>())

  /**
   * Decodes any JSON value of the data with [deserializer], for example the result of a function.
   *
   * `decodeAs<T>()` calls it with the serializer of [T]. Call it directly when the type is not
   * known at compile time, or to decode the value with a serializer that you build.
   *
   * **Example: the IDs that a function returns**
   * ```kotlin
   * val ids = sql.rpc("product_ids").execute().decodeAs(ListSerializer(Long.serializer()))
   * ```
   *
   * Response:
   * ```json
   * [1, 2, 42]
   * ```
   *
   * @param deserializer The deserializer of the value, for example `Long.serializer()`.
   * @return The decoded value.
   * @throws SerializationException When [deserializer] cannot decode the data, for example when the
   * response has no body.
   */
  public fun <T> decodeAs(deserializer: DeserializationStrategy<T>): T =
    defaultJson.decodeFromString(deserializer, data)

  /**
   * Decodes the JSON of the data into [T] like [decodeAs], or returns `null` when it cannot be
   * decoded, for example when the data is empty or does not fit [T]. It does not throw.
   *
   * It hides the reason: use [decodeAs] to get the decoding error.
   *
   * **Example: a value that may be missing**
   * ```kotlin
   * val sum = sql.rpc("add_numbers", mapOf("a" to 2, "b" to 3)).execute().decodeAsOrNull<Int>()
   * ```
   *
   * @return The decoded value, or `null`.
   */
  public inline fun <reified T> decodeAsOrNull(): T? = decodeAsOrNull(serializer<T>())

  /**
   * Decodes any JSON value of the data with [deserializer] like [decodeAs], or returns `null` when
   * it cannot be decoded. It does not throw.
   *
   * **Example: a list of IDs that may be missing**
   * ```kotlin
   * val ids = sql.rpc("product_ids").execute().decodeAsOrNull(ListSerializer(Long.serializer()))
   * ```
   *
   * @param deserializer The deserializer of the value, for example `Long.serializer()`.
   * @return The decoded value, or `null`.
   */
  public fun <T> decodeAsOrNull(deserializer: DeserializationStrategy<T>): T? =
    try {
      defaultJson.decodeFromString(deserializer, data)
    } catch (e: Exception) {
      null
    }

  /**
   * Compares this result with another object.
   *
   * @param other The object to compare with this result.
   * @return `true` if, and only if, [other] is an [OperationResult] whose [data], [status],
   * [statusText], count, and range are equal to the ones of this result.
   */
  override fun equals(other: Any?): Boolean =
    other is OperationResult &&
      other.data == data &&
      other.status == status &&
      other.statusText == statusText &&
      other.count == count &&
      other.range == range

  /** Returns a hash code of all the values of this result, consistent with [equals]. */
  override fun hashCode(): Int = Objects.hash(data, status, statusText, count, range)

  /**
   * Returns a text with the [status], the [statusText], the count, the range, and the [data], for
   * debugging. It includes all the [data], so do not log it when the data is large or holds
   * personal information. Its format can change.
   */
  override fun toString(): String =
    "OperationResult(status=$status, statusText=$statusText, count=$count, range=$range, " +
      "data=$data)"

  /** The message of the error for data that is empty, because the response has no body. */
  private fun noDataMessage(function: String) =
    "$function found no data: the response has no body. To get the rows of a mutation, call " +
      "select()."
}
