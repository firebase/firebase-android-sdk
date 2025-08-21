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
 * Contains a set of tools (like function declarations) that the model has access to. These tools
 * can be used to gather information or complete tasks.
 */
public class Tool
internal constructor(
  internal val functionDeclarations: List<FunctionDeclaration>?,
  internal val googleSearch: GoogleSearch?
) {
  internal fun toInternal() =
    Internal(
      functionDeclarations?.map { it.toInternal() } ?: emptyList(),
      googleSearch = this.googleSearch?.toInternal()
    )
  @Serializable
  internal data class Internal(
    val functionDeclarations: List<FunctionDeclaration.Internal>? = null,
    val googleSearch: GoogleSearch.Internal? = null,
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
      return Tool(functionDeclarations, null)
    }

    /**
     * Creates a [Tool] instance that allows the model to use Grounding with Google Search.
     *
     * Grounding with Google Search can be used to allow the model to connect to Google Search to
     * access and incorporate up-to-date information from the web into it's responses.
     *
     * When using this feature, you are required to comply with the "Grounding with Google Search"
     * usage requirements for your chosen API provider:
     * [Gemini Developer API](https://ai.google.dev/gemini-api/terms#grounding-with-google-search)
     * or Vertex AI Gemini API (see [Service Terms](https://cloud.google.com/terms/service-terms)
     * section within the Service Specific Terms).
     *
     * @param googleSearch An empty [GoogleSearch] object. The presence of this object in the list
     * of tools enables the model to use Google Search.
     * @return A [Tool] configured for Google Search.
     */
    @JvmStatic
    public fun googleSearch(googleSearch: GoogleSearch = GoogleSearch()): Tool {
      return Tool(null, googleSearch)
    }
  }
}
