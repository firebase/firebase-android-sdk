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

sealed class StringFormat(val value: String) {
  class Custom(format: String) : StringFormat(format)
}

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
 * int/float or keywords like "enum" for the string type
 * @property enum: contains the enum values for a string enum
 * @property type: contains the type info and parser
 * @property properties: if type is OBJECT, then this contains the description of the fields of the
 * object by name
 * @property required: if type is OBJECT, then this contains the list of required keys
 * @property items: if the type is ARRAY, then this contains a description of the objects in the
 * array
 */
class Schema
internal constructor(
  val type: String,
  val description: String? = null,
  val format: String? = null,
  val nullable: Boolean? = null,
  val enum: List<String>? = null,
  val properties: Map<String, Schema>? = null,
  val required: List<String>? = null,
  val items: Schema? = null,
) {

  companion object {

    /** Registers a schema for a boolean */
    @JvmStatic
    fun boolean(name: String? = null, description: String? = null, nullable: Boolean = false) =
      Schema(
        description = description,
        nullable = nullable,
        type = "BOOLEAN",
      )

    /** Registers a schema for a 32 bit integer number */
    @JvmStatic
    @JvmName("numInt")
    fun integer(description: String? = null, nullable: Boolean = false) =
      Schema(
        description = description,
        format = "int32",
        nullable = nullable,
        type = "INTEGER",
      )

    /** Registers a schema for a 64 bit integer number */
    @JvmStatic
    @JvmName("numLong")
    fun long(description: String? = null, nullable: Boolean = false) =
      Schema(
        description = description,
        nullable = nullable,
        type = "INTEGER",
      )

    /** Registers a schema for a floating point number */
    @JvmStatic
    @JvmName("numDouble")
    fun double(description: String? = null, nullable: Boolean = false) =
      Schema(description = description, nullable = nullable, type = "NUMBER", format = "double")

    /** Registers a schema for a floating point number */
    @JvmStatic
    @JvmName("numFloat")
    fun float(description: String? = null, nullable: Boolean = false) =
      Schema(description = description, nullable = nullable, type = "NUMBER", format = "float")

    /** Registers a schema for a string */
    @JvmStatic
    @JvmName("str")
    fun string(
      name: String? = null,
      description: String? = null,
      nullable: Boolean = false,
      format: StringFormat? = null
    ) =
      Schema(
        description = description,
        format = format?.value,
        nullable = nullable,
        type = "STRING"
      )

    /**
     * Registers a schema for a complex object. In a function it will be returned as a [JSONObject]
     */
    @JvmStatic
    fun obj(
      properties: Map<String, Schema>,
      description: String? = null,
      optionalProperties: List<String> = emptyList(),
      nullable: Boolean = false
    ) =
      Schema(
        description = description,
        nullable = nullable,
        properties = properties,
        required = properties.keys.minus(optionalProperties.toSet()).toList(),
        type = "OBJECT",
      )

    /**
     * Registers a schema for an array.
     *
     * @param items can be used to specify the type of the array
     */
    @JvmStatic
    fun array(items: Schema, description: String? = null, nullable: Boolean = false) =
      Schema(
        description = description,
        nullable = nullable,
        items = items,
        type = "ARRAY",
      )

    /** Registers a schema for an enum */
    @JvmStatic
    @JvmName("enumeration")
    fun enumeration(values: List<String>, description: String? = null, nullable: Boolean = false) =
      Schema(
        description = description,
        format = "enum",
        nullable = nullable,
        enum = values,
        type = "STRING",
      )
  }
}
