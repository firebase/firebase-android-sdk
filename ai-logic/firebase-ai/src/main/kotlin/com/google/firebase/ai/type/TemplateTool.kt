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
  internal val googleMaps: GoogleMaps? = null,
) {

  @OptIn(PublicPreviewAPI::class)
  internal fun toInternal() =
    Internal(
      buildList {
        functionDeclarations?.let { addAll(it.map { it.toInternal() }) }
        autoFunctionDeclarations?.let { addAll(it.map { it.toInternal() }) }
      },
      googleMaps?.toInternal(),
    )

  @Serializable
  internal data class Internal(
    val templateFunctions: List<TemplateFunctionDeclaration.Internal>? = null,
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
      return TemplateTool(functionDeclarations, autoFunctionDeclarations)
    }

    /**
     * Creates a [Tool] instance that allows the model to use grounding with Google Maps.
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
     * @return A [Tool] configured for Google Maps.
     */
    @JvmStatic
    @JvmOverloads
    public fun googleMaps(googleMaps: GoogleMaps = GoogleMaps()): TemplateTool {
      return TemplateTool(null, null, googleMaps)
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
