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

/**
 * Defines a function that the model can use as a tool. Including a function references to enable
 * automatic function calling.
 *
 * When generating responses, the model might need external information or require the application
 * to perform an action. `AutoFunctionDeclaration` provides the necessary information for the model
 * to create a [FunctionCallPart], which instructs the client to execute the corresponding function.
 * The client then sends the result back to the model as a [FunctionResponsePart].
 *
 * For example
 *
 * ```
 * val getExchangeRate = AutoFunctionDeclaration.create(
 *    name = "getExchangeRate",
 *    description = "Get the exchange rate for currencies between countries.",
 *    inputSchema = CurrencyRequest.schema,
 *    outputSchema = CurrencyResponse.schema,
 * ) {
 *    // make an api request to convert currencies and return the result
 * }
 * ```
 * @see JsonSchema
 */
public class AutoFunctionDeclaration<I : Any, O : Any>
internal constructor(
  public val name: String,
  public val description: String,
  public val inputSchema: JsonSchema<I>,
  public val outputSchema: JsonSchema<O>?,
  public val functionReference: (suspend (I) -> O)?
) {
  public companion object {

    /**
     * Create a strongly typed function declaration with an associated function reference.
     *
     * @param functionName the name of the function (to the model)
     * @param description the description of the function
     * @param inputSchema the object the model must provide to you as input
     * @param outputSchema the type that will be return to the model when the function is executed
     * @param functionReference the function that will be executed when requested by the model.
     */
    public fun <I : Any, O : Any> create(
      functionName: String,
      description: String,
      inputSchema: JsonSchema<I>,
      outputSchema: JsonSchema<O>,
      functionReference: (suspend (I) -> O)? = null
    ): AutoFunctionDeclaration<I, O> {
      return AutoFunctionDeclaration<I, O>(
        functionName,
        description,
        inputSchema,
        outputSchema,
        functionReference
      )
    }

    /**
     * Create a strongly typed function declaration with an associated function reference. This
     * version allows an arbitrary JsonObject as output rather than a strict schema.
     *
     * @param functionName the name of the function (to the model)
     * @param description the description of the function
     * @param inputSchema the object the model must provide to you as input
     * @param functionReference the function that will be executed when requested by the model.
     */
    public fun <I : Any> create(
      functionName: String,
      description: String,
      inputSchema: JsonSchema<I>,
      functionReference: (suspend (I) -> FunctionResponsePart)? = null
    ): AutoFunctionDeclaration<I, FunctionResponsePart> {
      return AutoFunctionDeclaration<I, FunctionResponsePart>(
        functionName,
        description,
        inputSchema,
        null,
        functionReference
      )
    }
  }

  internal fun toInternal(): FunctionDeclaration.Internal {
    return FunctionDeclaration.Internal(
      name,
      description,
      null,
      inputSchema.toInternalJson(),
      outputSchema?.toInternalJson()
    )
  }
}
