/*
 * Copyright 2026 Google LLC
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
import kotlinx.serialization.Serializable

/**
 * Contains a set of tools (like function declarations) that the server template model has access
 * to.
 */
public class TemplateTool
@OptIn(PublicPreviewAPI::class, InternalSerializationApi::class)
internal constructor(
  internal val functionDeclarations: List<TemplateFunctionDeclaration>?,
  internal val autoFunctionDeclarations: List<TemplateAutoFunctionDeclaration<*, *>>? = null,
  internal val urlContext: UrlContext?,
  internal val googleSearch: GoogleSearch?,
  internal val googleMaps: GoogleMaps?,
) {

  @OptIn(PublicPreviewAPI::class)
  internal fun toInternal() =
    Internal(
      buildList {
        functionDeclarations?.let { addAll(it.map { it.toInternal() }) }
        autoFunctionDeclarations?.let { addAll(it.map { it.toInternal() }) }
      },
      urlContext?.toInternal(),
      googleSearch?.toInternal(),
      googleMaps?.toInternal(),
    )

  @Serializable
  internal data class Internal(
    val templateFunctions: List<TemplateFunctionDeclaration.Internal>? = null,
    val urlContext: UrlContext.Internal? = null,
    val googleSearch: GoogleSearch.Internal? = null,
    val googleMaps: GoogleMaps.Internal? = null,
  )

  public companion object {

    /**
     * Creates a [TemplateTool] instance that provides the model with access to the
     * [functionDeclarations].
     *
     * @param functionDeclarations The list of functions that this tool allows the model access to.
     */
    @JvmStatic
    public fun functionDeclarations(
      functionDeclarations: List<TemplateFunctionDeclaration>,
      autoFunctionDeclarations: List<TemplateAutoFunctionDeclaration<*, *>>? = null,
    ): TemplateTool {
      return TemplateTool(functionDeclarations, autoFunctionDeclarations, null, null, null)
    }

    /**
     * Creates a [TemplateTool] instance that allows you to provide additional context to the models
     * in the form of public web URLs. By including URLs in your request, the Gemini model will
     * access the content from those pages to inform and enhance its response.
     *
     * @param urlContext Specifies the URL context configuration.
     * @return A [TemplateTool] configured for URL context.
     */
    @JvmStatic
    @JvmOverloads
    public fun urlContext(urlContext: UrlContext = UrlContext()): TemplateTool {
      return TemplateTool(null, null, urlContext, null, null)
    }

    /**
     * Creates a [TemplateTool] instance that allows the model to use grounding with Google Search.
     *
     * Grounding with Google Search can be used to allow the model to connect to Google Search to
     * access and incorporate up-to-date information from the web into its responses.
     *
     * When using this feature, you are required to comply with the "grounding with Google Search"
     * usage requirements for your chosen API provider:
     * [Gemini Developer API](https://ai.google.dev/gemini-api/terms#grounding-with-google-search)
     * or Vertex AI Gemini API (see [Service Terms](https://cloud.google.com/terms/service-terms)
     * section within the Service Specific Terms).
     *
     * @param googleSearch An empty [GoogleSearch] object. The presence of this object in the list
     * of tools enables the model to use Google Search.
     * @return A [TemplateTool] configured for Google Search.
     */
    @JvmStatic
    @JvmOverloads
    public fun googleSearch(googleSearch: GoogleSearch = GoogleSearch()): TemplateTool {
      return TemplateTool(null, null, null, googleSearch, null)
    }

    /**
     * Creates a [TemplateTool] instance that allows the model to use grounding with Google Maps.
     *
     * Grounding with Google Maps can be used to allow the model to connect to Google Maps to
     * incorporate location-based information into its responses.
     *
     * When using this feature, you are required to comply with the "Grounding with Google Maps"
     * usage requirements for your chosen API provider:
     * [Gemini Developer API](https://ai.google.dev/gemini-api/terms#grounding-with-google-maps) or
     * Vertex AI Gemini API (see [Service Terms](https://cloud.google.com/terms/service-terms)
     * section within the Service Specific Terms).
     *
     * @return A [TemplateTool] configured for Google Maps.
     */
    @JvmStatic
    @JvmOverloads
    public fun googleMaps(googleMaps: GoogleMaps = GoogleMaps()): TemplateTool {
      return TemplateTool(null, null, null, null, googleMaps)
    }
  }
}

/** A function declaration for a template tool. */
public open class TemplateFunctionDeclaration(
  public val name: String,
  public val inputSchema: JsonSchema<out Any>? = null,
  public val outputSchema: JsonSchema<out Any>? = null
) {

  @Serializable
  internal data class Internal(
    val name: String,
    val inputSchema: Schema.InternalJson? = null,
    val outputSchema: Schema.InternalJson? = null,
  )

  internal fun toInternal(): Internal {
    return Internal(
      name,
      inputSchema = inputSchema?.toInternalJson(),
      outputSchema = outputSchema?.toInternalJson()
    )
  }
}

/** A function declaration for a template tool that can be called by the model automatically. */
public class TemplateAutoFunctionDeclaration<I : Any, O : Any>
internal constructor(
  public val name: String,
  public val inputSchema: JsonSchema<I>,
  public val outputSchema: JsonSchema<O>? = null,
  public val functionReference: (suspend (I) -> O)?,
) {

  internal fun toInternal(): TemplateFunctionDeclaration.Internal {
    return TemplateFunctionDeclaration.Internal(
      name,
      inputSchema.toInternalJson(),
      outputSchema = outputSchema?.toInternalJson()
    )
  }

  public companion object {
    /**
     * Creates a strongly typed function declaration with an associated function reference.
     *
     * @param functionName the name of the function (to the model)
     * @param inputSchema the object the model must provide to you as input
     * @param outputSchema the type that will be return to the model when the function is executed
     * @param functionReference the function that will be executed when requested by the model.
     */
    public fun <I : Any, O : Any> create(
      functionName: String,
      inputSchema: JsonSchema<I>,
      outputSchema: JsonSchema<O>,
      functionReference: (suspend (I) -> O)? = null
    ): TemplateAutoFunctionDeclaration<I, O> {
      return TemplateAutoFunctionDeclaration<I, O>(
        functionName,
        inputSchema,
        outputSchema,
        functionReference
      )
    }
  }
}

/** Config for template tools to use with server prompts. */
public class TemplateToolConfig public constructor(private val retrievalConfig: RetrievalConfig?) {

  public constructor() : this(null)
  internal fun toInternal(): ToolConfig.Internal {
    return ToolConfig.Internal(
      functionCallingConfig = null,
      retrievalConfig = retrievalConfig?.toInternal()
    )
  }
}
