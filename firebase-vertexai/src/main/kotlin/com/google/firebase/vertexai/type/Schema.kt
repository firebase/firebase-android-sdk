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

/** Represents a schema */
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
    /** Returns a schema for a boolean */
    @JvmStatic
    fun boolean(description: String? = null, nullable: Boolean = false) =
      Schema(
        description = description,
        nullable = nullable,
        type = "BOOLEAN",
      )

    /**
     * Returns a schema for a 32-bit integer number
     *
     * @param description: The description of what the parameter should contain or represent
     * @param nullable: Whether null is a valid value for this schema
     */
    @JvmStatic
    @JvmName("numInt")
    fun integer(description: String? = null, nullable: Boolean = false) =
      Schema(
        description = description,
        format = "int32",
        nullable = nullable,
        type = "INTEGER",
      )

    /**
     * Returns a schema for a 64-bit integer number
     *
     * @param description: The description of what the parameter should contain or represent
     * @param nullable: Whether null is a valid value for this schema
     */
    @JvmStatic
    @JvmName("numLong")
    fun long(description: String? = null, nullable: Boolean = false) =
      Schema(
        description = description,
        nullable = nullable,
        type = "INTEGER",
      )

    /**
     * Returns a schema for a floating point number
     *
     * @param description: The description of what the parameter should contain or represent
     * @param nullable: Whether null is a valid value for this schema
     */
    @JvmStatic
    @JvmName("numDouble")
    fun double(description: String? = null, nullable: Boolean = false) =
      Schema(description = description, nullable = nullable, type = "NUMBER", format = "double")

    /**
     * Returns a schema for a floating point number
     *
     * @param description: The description of what the parameter should contain or represent
     * @param nullable: Whether null is a valid value for this schema
     */
    @JvmStatic
    @JvmName("numFloat")
    fun float(description: String? = null, nullable: Boolean = false) =
      Schema(description = description, nullable = nullable, type = "NUMBER", format = "float")

    /**
     * Returns a schema for a string
     *
     * @param description: The description of what the parameter should contain or represent
     * @param nullable: Whether null is a valid value for this schema
     * @param format: The pattern that values need to adhere to
     */
    @JvmStatic
    @JvmName("str")
    fun string(
      description: String? = null,
      nullable: Boolean = false,
      format: StringFormat? = null
    ) =
      Schema(description = description, format = format?.value, nullable = nullable, type = "STRING")

    /**
     * Returns a schema for a complex object. In a function, it will be returned as a [JSONObject].
     *
     * @param properties: The map of the object's fields to their schema
     * @param description: The description of what the parameter should contain or represent
     * @param nullable: Whether null is a valid value for this schema
     */
    @JvmStatic
    fun obj(
      properties: Map<String, Schema>,
      description: String? = null,
      nullable: Boolean = false,
      optionalProperties: List<String> = emptyList(),
    ) =
      Schema(
        description = description,
        nullable = nullable,
        properties = properties,
        required = properties.keys.minus(optionalProperties.toSet()).toList(),
        type = "OBJECT",
      )

    /**
     * Returns a schema for an array.
     *
     * @param items: The schema of the elements of this array
     * @param description: The description of what the parameter should contain or represent
     * @param nullable: Whether null is a valid value for this schema
     */
    @JvmStatic
    fun array(items: Schema, description: String? = null, nullable: Boolean = false) =
      Schema(
        description = description,
        nullable = nullable,
        items = items,
        type = "ARRAY",
      )

    /**
     * Returns a schema for an enumeration
     *
     * @param values: The list of valid values for this enumeration
     * @param description: The description of what the parameter should contain or represent
     * @param nullable: Whether null is a valid value for this schema
     */
    @JvmStatic
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
