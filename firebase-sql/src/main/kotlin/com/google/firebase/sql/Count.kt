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

/**
 * The algorithm that the server uses to count rows, for the `count` parameter of `select()`,
 * `insert()`, `upsert()`, `update()`, `delete()`, and [FirebaseSql.rpc].
 *
 * The SDK sends it in the `Prefer` request header, for example `Prefer: count=exact`. The server
 * returns the total in the `Content-Range` response header, and the SDK exposes it as
 * [OperationResult.countOrNull][com.google.firebase.sql.OperationResult.countOrNull]:
 * - For a read, it is the number of rows that match the filters, on all the pages, not only the
 * rows of the response.
 * - For an insert, an upsert, an update, or a delete, it is the number of rows that the request
 * changed.
 * - It is `null` when the response has no total, for example when the request asks for no count.
 *
 * With a count, a page whose offset is greater than the total fails with the status `416` and the
 * code `PGRST103`. Without a count, the same page has no rows.
 *
 * PostgREST: `Prefer: count=exact`, and the response header `Content-Range: 0-19/1234`
 *
 * **Example: count rows without reading them**
 * ```kotlin
 * // HEAD /products?select=*&category_id=eq.1 with the header Prefer: count=exact
 * val result =
 *   sql
 *     .from("products")
 *     .select(head = true, count = Count.EXACT)
 *     .eq("category_id", 1)
 *     .execute()
 * val numberOfProducts = result.countOrNull()
 * ```
 *
 * **Example: read a page, and the total for all the pages**
 * ```kotlin
 * val pageSize = 20L
 * val page = 2L // The first page is 0.
 * // GET /products?select=*&limit=20&offset=40&order=id.asc with the header Prefer: count=exact
 * val result =
 *   sql
 *     .from("products")
 *     .select(count = Count.EXACT)
 *     .order("id")
 *     .range(page * pageSize, (page + 1) * pageSize - 1)
 *     .execute()
 * val products = result.decodeList<Product>() // At most 20 products.
 * val total = result.countOrNull() ?: 0L // The products of all the pages.
 * val pageCount = (total + pageSize - 1) / pageSize
 * ```
 *
 * Response header: `Content-Range: 40-59/1234`
 *
 * **Example: count the rows that an update changes**
 * ```kotlin
 * val result =
 *   sql
 *     .from("products")
 *     .update(mapOf("price" to 0.5), count = Count.EXACT)
 *     .eq("category_id", 1)
 *     .execute()
 * val updatedRows = result.countOrNull()
 * ```
 */
public class Count private constructor(internal val rawValue: String) {
  /** Returns the value that the SDK sends to PostgREST, for example `exact`. */
  override fun toString(): String = rawValue

  public companion object {
    /**
     * Counts the rows exactly, with `COUNT(*)`. It is accurate, but can be slow on large tables.
     *
     * PostgREST: `Prefer: count=exact`
     */
    public val EXACT: Count = Count("exact")

    /**
     * Uses the row estimate of the PostgreSQL query planner. It is fast, but only as accurate as
     * the statistics of the table, so it can be higher or lower than the real count. Because the
     * total is an estimate, a page whose offset is greater than the estimate fails with the status
     * `416` and the code `PGRST103`, even when the table has rows at that offset.
     *
     * PostgREST: `Prefer: count=planned`
     */
    public val PLANNED: Count = Count("planned")

    /**
     * Counts the rows exactly up to a threshold, the maximum number of rows that the server returns
     * for each request, and uses the estimate of the query planner above it: exact for small
     * results, and fast for large ones. Above the threshold, the total is an estimate, like with
     * [PLANNED].
     *
     * PostgREST: `Prefer: count=estimated`
     */
    public val ESTIMATED: Count = Count("estimated")
  }
}
