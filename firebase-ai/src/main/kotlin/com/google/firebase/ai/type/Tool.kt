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
@OptIn(PublicPreviewAPI::class)
internal constructor(
  internal val functionDeclarations: List<FunctionDeclaration>?,
  internal val autoFunctionDeclarations: List<AutoFunctionDeclaration<*, *>>?,
  internal val googleSearch: GoogleSearch?,
  internal val codeExecution: JsonObject?,
  @property:PublicPreviewAPI internal val urlContext: UrlContext?,
) {

  @OptIn(PublicPreviewAPI::class)
  internal fun toInternal() =
    Internal(
      buildList {
        functionDeclarations?.let { addAll(it.map { it.toInternal() }) }
        autoFunctionDeclarations?.let { addAll(it.map { it.toInternal() }) }
      },
      googleSearch = this.googleSearch?.toInternal(),
      codeExecution = this.codeExecution,
      urlContext = this.urlContext?.toInternal()
    )

  @OptIn(PublicPreviewAPI::class)
  @Serializable
  internal data class Internal(
    val functionDeclarations: List<FunctionDeclaration.Internal>? = null,
    val googleSearch: GoogleSearch.Internal? = null,
    // This is a json object because it is not possible to make a data class with no parameters.
    val codeExecution: JsonObject? = null,
    val urlContext: UrlContext.Internal? = null,
  )
  public companion object {

    @OptIn(PublicPreviewAPI::class)
    private val codeExecutionInstance by lazy {
      Tool(null, null, null, JsonObject(emptyMap()), null)
    }

    /**
     * Creates a [Tool] instance that provides the model with access to the [functionDeclarations].
     *
     * @param functionDeclarations The list of functions that this tool allows the model access to.
     */
    @JvmStatic
    public fun functionDeclarations(
      functionDeclarations: List<FunctionDeclaration>,
    ): Tool {
      @OptIn(PublicPreviewAPI::class) return Tool(functionDeclarations, null, null, null, null)
    }

    /**
     * Creates a [Tool] instance that provides the model with access to the [functionDeclarations].
     *
     * @param functionDeclarations The list of functions that this tool allows the model access to.
     * @param autoFunctionDeclarations The list of functions that this tool has access to which
     * should be executed automatically
     */
    @JvmStatic
    internal fun functionDeclarations(
      functionDeclarations: List<FunctionDeclaration>? = null,
      autoFunctionDeclarations: List<AutoFunctionDeclaration<*, *>>?
    ): Tool {
      @OptIn(PublicPreviewAPI::class)
      return Tool(functionDeclarations, autoFunctionDeclarations, null, null, null)
    }

    /** Creates a [Tool] instance that allows the model to use code execution. */
    @JvmStatic
    public fun codeExecution(): Tool {
      return codeExecutionInstance
    }

    /**
     * Creates a [Tool] instance that allows you to provide additional context to the models in the
     * form of public web URLs. By including URLs in your request, the Gemini model will access the
     * content from those pages to inform and enhance its response.
     *
     * @param urlContext Specifies the URL context configuration.
     * @return A [Tool] configured for URL context.
     */
    @PublicPreviewAPI
    @JvmStatic
    public fun urlContext(urlContext: UrlContext = UrlContext()): Tool {
      return Tool(null, null, null, null, urlContext)
    }

    /**
     * Creates a [Tool] instance that allows the model to use grounding with Google Search.
     *
     * Grounding with Google Search can be used to allow the model to connect to Google Search to
     * access and incorporate up-to-date information from the web into it's responses.
     *
     * When using this feature, you are required to comply with the "grounding with Google Search"
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
      @OptIn(PublicPreviewAPI::class) return Tool(null, null, googleSearch, null, null)
    }
  }
}
