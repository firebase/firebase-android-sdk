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

package com.google.firebase.ai.type

import kotlinx.serialization.Serializable

public abstract class StringFormat private constructor(internal val value: String) {
  public class Custom(value: String) : StringFormat(value)
}

/**
 * Definition of a data type.
 *
 * These types can be objects, but also primitives and arrays. Represents a select subset of an
 * [OpenAPI 3.0 schema object](https://spec.openapis.org/oas/v3.0.3#schema).
 *
 * **Note:** While optional, including a `description` field in your `Schema` is strongly
 * encouraged. The more information the model has about what it's expected to generate, the better
 * the results.
 */
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
  public val title: String? = null,
  public val minItems: Int? = null,
  public val maxItems: Int? = null,
  public val minimum: Double? = null,
  public val maximum: Double? = null,
  public val anyOf: List<Schema>? = null,
) {

  public companion object {
    /**
     * Returns a [Schema] representing a boolean value.
     *
     * @param description An optional description of what the boolean should contain or represent.
     * @param nullable Indicates whether the value can be `null`. Defaults to `false`.
     */
    @JvmStatic
    @JvmOverloads
    public fun boolean(
      description: String? = null,
      nullable: Boolean = false,
      title: String? = null,
    ): Schema =
      Schema(description = description, nullable = nullable, type = "BOOLEAN", title = title)

    /**
     * Returns a [Schema] for a 32-bit signed integer number.
     *
     * **Important:** This [Schema] provides a hint to the model that it should generate a 32-bit
     * integer, but only guarantees that the value will be an integer. Therefore it's *possible*
     * that decoding it as an `Int` variable (or `int` in Java) could overflow.
     *
     * @param description An optional description of what the integer should contain or represent.
     * @param nullable Indicates whether the value can be `null`. Defaults to `false`.
     */
    @JvmStatic
    @JvmName("numInt")
    @JvmOverloads
    public fun integer(
      description: String? = null,
      nullable: Boolean = false,
      title: String? = null,
      minimum: Double? = null,
      maximum: Double? = null,
    ): Schema =
      Schema(
        description = description,
        format = "int32",
        nullable = nullable,
        type = "INTEGER",
        title = title,
        minimum = minimum,
        maximum = maximum,
      )

    /**
     * Returns a [Schema] for a 64-bit signed integer number.
     *
     * @param description An optional description of what the number should contain or represent.
     * @param nullable Indicates whether the value can be `null`. Defaults to `false`.
     */
    @JvmStatic
    @JvmName("numLong")
    @JvmOverloads
    public fun long(
      description: String? = null,
      nullable: Boolean = false,
      title: String? = null,
      minimum: Double? = null,
      maximum: Double? = null,
    ): Schema =
      Schema(
        description = description,
        nullable = nullable,
        type = "INTEGER",
        title = title,
        minimum = minimum,
        maximum = maximum,
      )

    /**
     * Returns a [Schema] for a double-precision floating-point number.
     *
     * @param description An optional description of what the number should contain or represent.
     * @param nullable Indicates whether the value can be `null`. Defaults to `false`.
     */
    @JvmStatic
    @JvmName("numDouble")
    @JvmOverloads
    public fun double(
      description: String? = null,
      nullable: Boolean = false,
      title: String? = null,
      minimum: Double? = null,
      maximum: Double? = null,
    ): Schema =
      Schema(
        description = description,
        nullable = nullable,
        type = "NUMBER",
        title = title,
        minimum = minimum,
        maximum = maximum,
      )

    /**
     * Returns a [Schema] for a single-precision floating-point number.
     *
     * **Important:** This [Schema] provides a hint to the model that it should generate a
     * single-precision floating-point number, but only guarantees that the value will be a number.
     * Therefore it's *possible* that decoding it as a `Float` variable (or `float` in Java) could
     * overflow.
     *
     * @param description An optional description of what the number should contain or represent.
     * @param nullable Indicates whether the value can be `null`. Defaults to `false`.
     */
    @JvmStatic
    @JvmName("numFloat")
    @JvmOverloads
    public fun float(
      description: String? = null,
      nullable: Boolean = false,
      title: String? = null,
      minimum: Double? = null,
      maximum: Double? = null,
    ): Schema =
      Schema(
        description = description,
        nullable = nullable,
        type = "NUMBER",
        format = "float",
        title = title,
        minimum = minimum,
        maximum = maximum,
      )

    /**
     * Returns a [Schema] for a string.
     *
     * @param description An optional description of what the string should contain or represent.
     * @param nullable Indicates whether the value can be `null`. Defaults to `false`.
     * @param format An optional pattern that values need to adhere to.
     */
    @JvmStatic
    @JvmName("str")
    @JvmOverloads
    public fun string(
      description: String? = null,
      nullable: Boolean = false,
      format: StringFormat? = null,
      title: String? = null,
    ): Schema =
      Schema(
        description = description,
        format = format?.value,
        nullable = nullable,
        type = "STRING",
        title = title,
      )

    /**
     * Returns a [Schema] for a complex data type.
     *
     * This schema instructs the model to produce data of type object, which has keys of type
     * `String` and values of type [Schema].
     *
     * **Example:** A `city` could be represented with the following object `Schema`.
     *
     * ```
     * Schema.obj(mapOf(
     *   "name"  to Schema.string(),
     *   "population" to Schema.integer()
     * ))
     * ```
     *
     * @param properties The map of the object's property names to their [Schema]s.
     * @param optionalProperties The list of optional properties. They must correspond to the keys
     * provided in the `properties` map. By default it's empty, signaling the model that all
     * properties are to be included.
     * @param description An optional description of what the object represents.
     * @param nullable Indicates whether the value can be `null`. Defaults to `false`.
     */
    @JvmStatic
    @JvmOverloads
    public fun obj(
      properties: Map<String, Schema>,
      optionalProperties: List<String> = emptyList(),
      description: String? = null,
      nullable: Boolean = false,
      title: String? = null,
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
        title = title,
      )
    }

    /**
     * Returns a [Schema] for an array.
     *
     * @param items The [Schema] of the elements stored in the array.
     * @param description An optional description of what the array represents.
     * @param nullable Indicates whether the value can be `null`. Defaults to `false`.
     */
    @JvmStatic
    @JvmOverloads
    public fun array(
      items: Schema,
      description: String? = null,
      nullable: Boolean = false,
      title: String? = null,
      minItems: Int? = null,
      maxItems: Int? = null,
    ): Schema =
      Schema(
        description = description,
        nullable = nullable,
        items = items,
        type = "ARRAY",
        title = title,
        minItems = minItems,
        maxItems = maxItems,
      )

    /**
     * Returns a [Schema] for an enumeration.
     *
     * For example, the cardinal directions can be represented as:
     * ```
     * Schema.enumeration(listOf("north", "east", "south", "west"), "Cardinal directions")
     * ```
     *
     * @param values The list of valid values for this enumeration
     * @param description The description of what the parameter should contain or represent
     * @param nullable Indicates whether the value can be `null`. Defaults to `false`.
     */
    @JvmStatic
    @JvmOverloads
    public fun enumeration(
      values: List<String>,
      description: String? = null,
      nullable: Boolean = false,
      title: String? = null,
    ): Schema =
      Schema(
        description = description,
        format = "enum",
        nullable = nullable,
        enum = values,
        type = "STRING",
        title = title,
      )

    /**
     * Returns a [Schema] representing a value that must conform to *any* (one of) the provided
     * sub-schema.
     *
     * Example: A field that can hold either a simple userID or a more detailed user object.
     *
     * Schema.anyOf( listOf( Schema.integer(description = "User ID"), Schema.obj(mapOf(
     *
     * ```
     *     "userID" to Schema.integer(description = "User ID"),
     *     "username" to Schema.string(description = "Username")
     * ```
     *
     * )) )
     *
     * @param schemas The list of valid schemas which could be here
     */
    @JvmStatic
    public fun anyOf(schemas: List<Schema>): Schema = Schema(type = "ANYOF", anyOf = schemas)
  }

  internal fun toInternal(): Internal {
    val cleanedType =
      if (type == "ANYOF") {
        null
      } else {
        type
      }
    return Internal(
      cleanedType,
      description,
      format,
      nullable,
      enum,
      properties?.mapValues { it.value.toInternal() },
      required,
      items?.toInternal(),
      title,
      minItems,
      maxItems,
      minimum,
      maximum,
      anyOf?.map { it.toInternal() },
    )
  }

  @Serializable
  internal data class Internal(
    val type: String? = null,
    val description: String? = null,
    val format: String? = null,
    val nullable: Boolean? = false,
    val enum: List<String>? = null,
    val properties: Map<String, Internal>? = null,
    val required: List<String>? = null,
    val items: Internal? = null,
    val title: String? = null,
    val minItems: Int? = null,
    val maxItems: Int? = null,
    val minimum: Double? = null,
    val maximum: Double? = null,
    val anyOf: List<Internal>? = null,
  )
}
