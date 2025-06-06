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

package com.google.firebase.vertexai.type

import kotlinx.serialization.Serializable

/**
 * Defines a function that the model can use as a tool.
 *
 * When generating responses, the model might need external information or require the application
 * to perform an action. `FunctionDeclaration` provides the necessary information for the model to
 * create a [FunctionCallPart], which instructs the client to execute the corresponding function.
 * The client then sends the result back to the model as a [FunctionResponsePart].
 *
 * For example
 *
 * ```
 * val getExchangeRate = FunctionDeclaration(
 *    name = "getExchangeRate",
 *    description = "Get the exchange rate for currencies between countries.",
 *    parameters = mapOf(
 *      "currencyFrom" to Schema.str("The currency to convert from."),
 *      "currencyTo" to Schema.str("The currency to convert to.")
 *    )
 * )
 * ```
 *
 * See the
 * [Use the Gemini API for function calling](https://firebase.google.com/docs/vertex-ai/function-calling?platform=android)
 * guide for more information on function calling.
 *
 * @param name The name of the function.
 * @param description The description of what the function does and its output. To improve the
 * effectiveness of the model, the description should be clear and detailed.
 * @param parameters The map of parameters names to their [Schema] the function accepts as
 * arguments.
 * @param optionalParameters The list of parameter names that the model can omit when invoking this
 * function.
 * @see Schema
 */
@Deprecated(
  """The Vertex AI in Firebase SDK (firebase-vertexai) has been replaced with the FirebaseAI SDK (firebase-ai) to accommodate the evolving set of supported features and services.
For migration details, see the migration guide: https://firebase.google.com/docs/vertex-ai/migrate-to-latest-sdk"""
)
public class FunctionDeclaration(
  internal val name: String,
  internal val description: String,
  internal val parameters: Map<String, Schema>,
  internal val optionalParameters: List<String> = emptyList(),
) {
  internal val schema: Schema =
    Schema.obj(properties = parameters, optionalProperties = optionalParameters, nullable = false)

  internal fun toInternal() = Internal(name, "", schema.toInternal())

  @Serializable
  internal data class Internal(
    val name: String,
    val description: String,
    val parameters: Schema.Internal
  )
}
