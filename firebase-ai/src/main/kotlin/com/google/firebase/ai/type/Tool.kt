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

package com.google.firebase.ai.type

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Contains a set of function declarations that the model has access to. These can be used to gather
 * information, or complete tasks
 *
 * @param functionDeclarations The set of functions that this tool allows the model access to
 */
public class Tool
internal constructor(internal val functionDeclarations: List<FunctionDeclaration>?) {
  internal fun toInternal() = Internal(functionDeclarations?.map { it.toInternal() } ?: emptyList())
  @Serializable
  internal data class Internal(
    val functionDeclarations: List<FunctionDeclaration.Internal>? = null,
    // This is a json object because it is not possible to make a data class with no parameters.
    val codeExecution: JsonObject? = null,
  )
  public companion object {

    /**
     * Creates a [Tool] instance that provides the model with access to the [functionDeclarations].
     *
     * @param functionDeclarations The list of functions that this tool allows the model access to.
     */
    @JvmStatic
    public fun functionDeclarations(functionDeclarations: List<FunctionDeclaration>): Tool {
      return Tool(functionDeclarations)
    }
  }
}
