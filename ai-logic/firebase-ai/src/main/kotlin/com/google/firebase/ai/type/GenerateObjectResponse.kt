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

internal sealed interface ObjectSource<T : Any> {
  data class FromSchema<T : Any>(val schema: JsonSchema<T>) : ObjectSource<T>

  data class FromInstance<T : Any>(val instances: List<T>) : ObjectSource<T>
}

/**
 * A [GenerateContentResponse] augmented with class information.
 *
 * Use [getObject] to parse the response and extract the strongly typed object.
 */
public class GenerateObjectResponse<T : Any>
internal constructor(
  public val response: GenerateContentResponse,
  internal val source: ObjectSource<T>
) {

  internal constructor(
    response: GenerateContentResponse,
    schema: JsonSchema<T>
  ) : this(response, ObjectSource.FromSchema(schema))

  /**
   * Deserialize a candidate (default first) and convert it into the type associated with this
   * response.
   *
   * @param candidateIndex which candidate to deserialize
   * @throws RuntimeException if class is not @Serializable
   * @throws SerializationException if an error occurs during deserialization
   */
  @OptIn(InternalSerializationApi::class)
  public fun getObject(candidateIndex: Int = 0): T? =
    when (source) {
      is ObjectSource.FromInstance -> source.instances.getOrNull(candidateIndex)
      is ObjectSource.FromSchema -> {
        val candidate = response.candidates.getOrNull(candidateIndex)
        if (candidate == null) {
          null
        } else {
          val deserializer = source.schema.getSerializer()
          val text =
            candidate.content.parts
              .filter { !it.isThought }
              .filterIsInstance<TextPart>()
              .joinToString(" ") { it.text }
          if (text.isEmpty()) {
            null
          } else {
            Json.decodeFromString(deserializer, text) as T?
          }
        }
      }
    }
}
