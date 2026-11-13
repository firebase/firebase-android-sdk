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
 * The HTTP method with which [FirebaseSql.rpc] calls a PostgreSQL function.
 *
 * The method decides where the arguments go and whether the function can change data:
 * - [POST], the default, sends the arguments as a JSON object in the request body. It is the only
 * method that can call a function that changes data.
 * - [GET] and [HEAD] send each argument as a query parameter, and PostgREST runs the function in a
 * read-only transaction, so a function that changes data fails with the PostgreSQL error `25006`.
 * The SDK retries them after a transient failure, like the other GET and HEAD requests; it never
 * retries a POST request.
 *
 * **Example: call a function that only reads, with GET**
 * ```kotlin
 * // GET /rpc/products_under?max_price=10
 * val cheap =
 *   sql
 *     .rpc("products_under", mapOf("max_price" to 10), method = RpcMethod.GET)
 *     .execute()
 *     .decodeList<Product>()
 * ```
 *
 * **Example: count the rows of a function without reading them, with HEAD**
 * ```kotlin
 * // HEAD /rpc/products_under?max_price=10 with the header Prefer: count=exact
 * val numberOfProducts =
 *   sql
 *     .rpc(
 *       "products_under",
 *       mapOf("max_price" to 10),
 *       method = RpcMethod.HEAD,
 *       count = Count.EXACT
 *     )
 *     .execute()
 *     .countOrNull()
 * ```
 */
public class RpcMethod private constructor(internal val rawValue: String) {
  /** Returns the HTTP method, for example `GET`. */
  override fun toString(): String = rawValue

  public companion object {
    /**
     * Sends the arguments as a JSON object in the request body. PostgREST runs a `VOLATILE`
     * function in a read-write transaction, and a `STABLE` or `IMMUTABLE` function in a read-only
     * one.
     *
     * PostgREST: `POST /rpc/<function>` with the body `{"<argument>": <value>}`
     */
    public val POST: RpcMethod = RpcMethod("POST")

    /**
     * Sends each argument as a query parameter, and runs the function in a read-only transaction.
     * Use it for a function that only reads data.
     *
     * PostgREST: `GET /rpc/<function>?<argument>=<value>`
     */
    public val GET: RpcMethod = RpcMethod("GET")

    /**
     * Like [GET], but the response has no body: use it with a count to get only the number of rows
     * that the function returns.
     *
     * PostgREST: `HEAD /rpc/<function>?<argument>=<value>`
     */
    public val HEAD: RpcMethod = RpcMethod("HEAD")
  }
}
