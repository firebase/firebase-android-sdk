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

public abstract class StringFormat private constructor(internal val value: String) {
  public class Custom(value: String) : StringFormat(value)
}

/** Represents a schema */
public class Schema
internal constructor(
  public val type: String,
  public val description: String? = null,
  public val format: String? = null,
  public val nullable: Boolean? = null,
  public val enum: List<String>? = null,
  public val properties: Map<String, Schema>? = null,
  public val required: List<String>? = null,
  public val items: Schema? = null,
) {

  public companion object {
    /** Returns a schema for a boolean */
    @JvmStatic
    @JvmOverloads
    public fun boolean(description: String? = null, nullable: Boolean = false): Schema =
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
    @JvmOverloads
    public fun integer(description: String? = null, nullable: Boolean = false): Schema =
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
    @JvmOverloads
    public fun long(description: String? = null, nullable: Boolean = false): Schema =
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
    @JvmOverloads
    public fun double(description: String? = null, nullable: Boolean = false): Schema =
      Schema(description = description, nullable = nullable, type = "NUMBER", format = "double")

    /**
     * Returns a schema for a floating point number
     *
     * @param description: The description of what the parameter should contain or represent
     * @param nullable: Whether null is a valid value for this schema
     */
    @JvmStatic
    @JvmName("numFloat")
    @JvmOverloads
    public fun float(description: String? = null, nullable: Boolean = false): Schema =
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
    @JvmOverloads
    public fun string(
      description: String? = null,
      nullable: Boolean = false,
      format: StringFormat? = null
    ): Schema =
      Schema(
        description = description,
        format = format?.value,
        nullable = nullable,
        type = "STRING"
      )

    /**
     * Returns a schema for a complex object. In a function, it will be returned as a [JSONObject].
     *
     * @param properties: The map of the object's fields to their schema
     * @param description: The description of what the parameter should contain or represent
     * @param nullable: Whether null is a valid value for this schema
     */
    @JvmStatic
    @JvmOverloads
    public fun obj(
      properties: Map<String, Schema>,
      optionalProperties: List<String> = emptyList(),
      description: String? = null,
      nullable: Boolean = false,
    ): Schema {
      if (!properties.keys.containsAll(optionalProperties)) {
        throw IllegalArgumentException(
          "All optional properties must be present in properties. Missing: ${optionalProperties.minus(properties.keys)}"
        )
      }
      return Schema(
        description = description,
        nullable = nullable,
        properties = properties,
        required = properties.keys.minus(optionalProperties.toSet()).toList(),
        type = "OBJECT",
      )
    }

    /**
     * Returns a schema for an array.
     *
     * @param items: The schema of the elements of this array
     * @param description: The description of what the parameter should contain or represent
     * @param nullable: Whether null is a valid value for this schema
     */
    @JvmStatic
    @JvmOverloads
    public fun array(
      items: Schema,
      description: String? = null,
      nullable: Boolean = false
    ): Schema =
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
    @JvmOverloads
    public fun enumeration(
      values: List<String>,
      description: String? = null,
      nullable: Boolean = false
    ): Schema =
      Schema(
        description = description,
        format = "enum",
        nullable = nullable,
        enum = values,
        type = "STRING",
      )
  }
}
