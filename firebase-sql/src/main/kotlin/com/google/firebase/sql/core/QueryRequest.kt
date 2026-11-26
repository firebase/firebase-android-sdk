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

package com.google.firebase.sql.core

/**
 * A PostgREST request that a builder describes, without the URL of the service.
 *
 * @property pathSegments The segments that the network client appends to the URL of the service,
 * for example `products`, or `rpc` and the function name. Each one is percent-encoded as a single
 * segment, so a table named `rpc/x` is not mistaken for the function `x`.
 * @property retryEnabled Whether a GET or HEAD request is sent again after a transient failure. See
 * [OperationsExecutor].
 */
internal data class QueryRequest(
  val httpMethod: String,
  val pathSegments: List<String>,
  val queryParams: List<Pair<String, String>>,
  val headers: Map<String, String>,
  val body: Any? = null,
  val retryEnabled: Boolean = true
)
