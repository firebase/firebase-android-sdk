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

/**
 * Representation of a function that a model can invoke.
 *
 * @see defineFunction
 */
class FunctionDeclaration(
  val name: String,
  val description: String,
  val parameters: Map<String, Schema>,
  val requiredParameters: List<String>,
)

/**
 * A declared function, including implementation, that a model can be given access to in order to
 * gain info or complete tasks.
 *
 * ```
 * val getExchangeRate = defineFunction(
 *    name = "getExchangeRate",
 *    description = "Get the exchange rate for currencies between countries.",
 *    parameters = listOf(
 *      Schema.str("currencyFrom", "The currency to convert from."),
 *      Schema.str("currencyTo", "The currency to convert to.")
 *    ),
 *    requiredParameters = listOf("currencyFrom", "currencyTo")
 * )
 * ```
 *
 * @param name The name of the function call, this should be clear and descriptive for the model.
 * @param description A description of what the function does and its output.
 * @param parameters A list of parameters that the function accepts.
 * @param requiredParameters A list of parameters that the function requires to run.
 * @see Schema
 */
fun defineFunction(
  name: String,
  description: String,
  parameters: Map<String, Schema> = emptyMap(),
  requiredParameters: List<String> = emptyList(),
) = FunctionDeclaration(name, description, parameters, requiredParameters)
