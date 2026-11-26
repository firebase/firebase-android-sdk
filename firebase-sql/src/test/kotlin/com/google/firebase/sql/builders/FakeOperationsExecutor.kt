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

import com.google.firebase.sql.core.NetworkResponse
import com.google.firebase.sql.core.OperationsExecutor
import com.google.firebase.sql.core.QueryRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl

/** An executor that records the requests and returns [response] without any network I/O. */
internal class FakeOperationsExecutor(
  var response: NetworkResponse = NetworkResponse(200, "OK", "[]")
) : OperationsExecutor("public", "https://fake.url".toHttpUrl()) {
  val requests = mutableListOf<QueryRequest>()
  val lastRequest: QueryRequest?
    get() = requests.lastOrNull()

  override suspend fun execute(request: QueryRequest): NetworkResponse {
    requests.add(request)
    return response
  }
}

/**
 * Returns the request that a builder describes, without sending it. Every builder of the SDK is an
 * [OperationBuilderImpl], whose `toRequest()` is internal.
 */
internal fun TransformBuilder.toRequest(): QueryRequest = (this as OperationBuilderImpl).toRequest()

/** Parses [text] into a [JsonElement], to compare request bodies with readable JSON. */
internal fun json(text: String): JsonElement = Json.parseToJsonElement(text)
