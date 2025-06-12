/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.vertexai.type

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.json.JSONObject

internal sealed interface Response

@Serializable
internal data class GRpcErrorResponse(val error: GRpcError) : Response {

  @Serializable
  internal data class GRpcError(
    val code: Int,
    val message: String,
    val details: List<GRpcErrorDetails>? = null
  ) {

    @Serializable
    internal data class GRpcErrorDetails(
      val reason: String? = null,
      val domain: String? = null,
      val metadata: Map<String, String>? = null
    )
  }
}

internal fun JSONObject.toInternal() = Json.decodeFromString<JsonObject>(toString())

internal fun JsonObject.toPublic() = JSONObject(toString())
