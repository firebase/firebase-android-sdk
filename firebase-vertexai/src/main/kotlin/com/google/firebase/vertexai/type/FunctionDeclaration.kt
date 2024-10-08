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
 * A declared function that a model can be given access to in order to gain info or complete tasks.
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
 * @param name The name of the function call, this should be clear and descriptive for the model.
 * @param description A description of what the function does and its output.
 * @param parameters A list of parameters that the function accepts.
 * @param optionalParameters A list of parameters that can be omitted.
 * @see Schema
 */
public class FunctionDeclaration(
  internal val name: String,
  internal val description: String,
  internal val parameters: Map<String, Schema>,
  internal val optionalParameters: List<String> = emptyList(),
) {
  internal val schema: Schema =
    Schema.obj(properties = parameters, optionalProperties = optionalParameters, nullable = false)
}
