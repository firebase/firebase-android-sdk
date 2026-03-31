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

/**
 * Contains a set of tools (like function declarations) that the server template model has access
 * to.
 */
public class TemplateTool
@OptIn(PublicPreviewAPI::class)
internal constructor(
  internal val functionDeclarations: List<TemplateFunctionDeclaration>?,
) {

  public val templateAutoFunctionDeclarations: List<TemplateAutoFunctionDeclaration<*, *>>
    get() =
      functionDeclarations?.filterIsInstance<TemplateAutoFunctionDeclaration<*, *>>() ?: emptyList()

  @OptIn(PublicPreviewAPI::class)
  internal fun toInternal() =
    Tool.Internal(functionDeclarations = functionDeclarations?.map { it.toInternal() })

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
    ): TemplateTool {
      return TemplateTool(functionDeclarations)
    }
  }
}

/** A function declaration for a template tool. */
public open class TemplateFunctionDeclaration(
  public val name: String,
  public val description: String,
  public val parameters: JsonSchema<out Any>? = null
) {
  internal fun toInternal(): FunctionDeclaration.Internal {
    return FunctionDeclaration.Internal(
      name,
      description,
      parametersJsonSchema = parameters?.toInternalJson()
    )
  }
}

/** A function declaration for a template tool that can be called by the model automatically. */
public class TemplateAutoFunctionDeclaration<I : Any, O : Any>(
  name: String,
  description: String,
  inputSchema: JsonSchema<I>,
  outputSchema: JsonSchema<O>? = null,
  functionReference: (suspend (I) -> O),
) :
  AutoFunctionDeclaration<I, O>(
    name,
    description,
    inputSchema = inputSchema,
    outputSchema = outputSchema,
    functionReference = functionReference
  )

/** Config for template tools to use with server prompts. */
public class TemplateToolConfig {
  internal fun toInternal(): ToolConfig.Internal? {
    return null // Empty config payload as defined in flutter API
  }
}
