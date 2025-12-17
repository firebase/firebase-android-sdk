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

import kotlin.reflect.KClass
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializerOrNull

public class GenerateObjectResponse<T : Any>
internal constructor(public val response: GenerateContentResponse, internal val clazz: KClass<T>) {

  @OptIn(InternalSerializationApi::class)
  public fun getObject(candidateIndex: Int = 0): T? {
    val candidate = response.candidates[candidateIndex]
    val deserializer =
      clazz.serializerOrNull()
        ?: throw RuntimeException("Object type ${clazz.qualifiedName} is not @Serializable")
    val text =
      candidate.content.parts
        .filter { !it.isThought }
        .filterIsInstance<TextPart>()
        .joinToString(" ") { it.text }
    if (text.isEmpty()) {
      return null
    }
    return Json.decodeFromString(deserializer, text)
  }
}
