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

import org.json.JSONObject

/**
 * Representation of a function that a model can invoke.
 *
 * @see defineFunction
 */
class FunctionDeclaration(
  val name: String,
  val description: String,
  val parameters: List<Schema>,
  val requiredParameters: List<String>,
)

/**
 * Represents a parameter for a declared function
 *
 * ```
 * val currencyFrom = Schema.str("currencyFrom", "The currency to convert from.")
 * ```
 *
 * @property name: The name of the parameter
 * @property description: The description of what the parameter should contain or represent
 * @property format: format information for the parameter, this can include bitlength in the case of
 *   int/float or keywords like "enum" for the string type
 * @property enum: contains the enum values for a string enum
 * @property type: contains the type info and parser
 * @property properties: if type is OBJECT, then this contains the description of the fields of the
 *   object by name
 * @property required: if type is OBJECT, then this contains the list of required keys
 * @property items: if the type is ARRAY, then this contains a description of the objects in the
 *   array
 */
class Schema(
  val name: String,
  val description: String,
  val format: String? = null,
  val nullable: Boolean? = null,
  val enum: List<String>? = null,
  val properties: Map<String, Schema>? = null,
  val required: List<String>? = null,
  val items: Schema? = null,
  val type: FunctionType,
) {

  companion object {
    /** Registers a schema for a 32 bit integer number */
    @JvmStatic
    @JvmName("numInt")
    fun int(name: String, description: String) =
      Schema(
        name = name,
        description = description,
        format = "int32",
        type = FunctionType.INTEGER,
        nullable = false,
      )

    /** Registers a schema for a 64 bit integer number */
    @JvmStatic
    @JvmName("numLong")
    fun long(name: String, description: String) =
      Schema(
        name = name,
        description = description,
        type = FunctionType.LONG,
        nullable = false,
      )

    /** Registers a schema for a string */
    @JvmStatic
    fun str(name: String, description: String) =
      Schema(
        name = name,
        description = description,
        type = FunctionType.STRING,
        nullable = false,
      )

    /** Registers a schema for a boolean */
    @JvmStatic
    fun bool(name: String, description: String) =
      Schema(
        name = name,
        description = description,
        type = FunctionType.BOOLEAN,
        nullable = false,
      )

    /** Registers a schema for a floating point number */
    @JvmStatic
    @JvmName("numDouble")
    fun double(name: String, description: String) =
      Schema(
        name = name,
        description = description,
        type = FunctionType.NUMBER,
        nullable = false,
      )

    /**
     * Registers a schema for a complex object. In a function it will be returned as a [JSONObject]
     */
    @JvmStatic
    fun obj(name: String, description: String, vararg contents: Schema) =
      Schema(
        name = name,
        description = description,
        type = FunctionType.OBJECT,
        required = contents.map { it.name },
        properties = contents.associateBy { it.name }.toMap(),
        nullable = false,
      )

    /**
     * Registers a schema for an array.
     *
     * @param items can be used to specify the type of the array
     */
    @JvmStatic
    fun arr(name: String, description: String, items: Schema? = null) =
      Schema(
        name = name,
        description = description,
        type = FunctionType.ARRAY,
        items = items,
        nullable = false,
      )

    /** Registers a schema for an enum */
    @JvmStatic
    @JvmName("enumeration")
    fun enum(name: String, description: String, values: List<String>) =
      Schema(
        name = name,
        description = description,
        format = "enum",
        enum = values,
        type = FunctionType.STRING,
        nullable = false,
      )
  }
}

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
  parameters: List<Schema> = emptyList(),
  requiredParameters: List<String> = emptyList(),
) = FunctionDeclaration(name, description, parameters, requiredParameters)
