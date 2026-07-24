/*
 * Copyright 2025 Google LLC
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

package com.google.firebase.ai.type

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json

/**
 * A [GenerateContentResponse] augmented with class information.
 *
 * Use [getObject] to parse the response and extract the strongly typed object.
 */
public class GenerateObjectResponse<T : Any>
internal constructor(
  public val response: GenerateContentResponse,
  internal val schema: JsonSchema<T>?,
  internal var instances: MutableList<T?>?
) {

  internal constructor(
    response: GenerateContentResponse,
    schema: JsonSchema<T>
  ) : this(response, schema = schema, instances = null)

  internal constructor(
    response: GenerateContentResponse,
    instances: List<T>
  ) : this(
    response,
    schema = null,
    instances = (instances as? MutableList<T?>) ?: instances.toMutableList()
  )

  /**
   * Deserialize a candidate (default first) and convert it into the type associated with this
   * response.
   *
   * @param candidateIndex which candidate to deserialize
   * @throws RuntimeException if class is not @Serializable
   * @throws SerializationException if an error occurs during deserialization
   */
  @OptIn(InternalSerializationApi::class)
  public fun getObject(candidateIndex: Int = 0): T? {
    // 1. Fast Path / Cache Hit: Return immediately if already resolved or in memory
    instances?.getOrNull(candidateIndex)?.let {
      return it
    }

    // 2. Cache Miss (Cloud response on first access): Deserialize using schema
    if (schema == null) return null
    val candidate = response.candidates.getOrNull(candidateIndex) ?: return null
    val text =
      candidate.content.parts
        .filter { !it.isThought }
        .filterIsInstance<TextPart>()
        .joinToString(" ") { it.text }
    if (text.isEmpty()) return null

    val deserialized = Json.decodeFromString(schema.getSerializer(), text) as T?

    // 3. Save to instances list for future accesses (lazy loading) and return
    if (instances == null) {
      instances = MutableList(response.candidates.size) { null }
    }
    if (candidateIndex < (instances?.size ?: 0)) {
      instances?.set(candidateIndex, deserialized)
    }
    return deserialized
  }
}
