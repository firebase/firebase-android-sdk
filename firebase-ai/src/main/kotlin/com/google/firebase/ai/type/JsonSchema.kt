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

import kotlin.reflect.KClass
import kotlinx.serialization.json.JsonObject

/**
 * Definition of a data type.
 *
 * These types can be objects, but also primitives and arrays. Represents a select subset of an
 * [JsonSchema object](https://json-schema.org/specification).
 *
 * **Note:** While optional, including a `description` field in your `JsonSchema` is strongly
 * encouraged. The more information the model has about what it's expected to generate, the better
 * the results.
 */
public class JsonSchema<T : Any>
internal constructor(
  public val type: String,
  public val clazz: KClass<T>,
  public val description: String? = null,
  public val format: String? = null,
  public val pattern: String? = null,
  public val nullable: Boolean? = null,
  public val enum: List<String>? = null,
  public val properties: Map<String, JsonSchema<*>>? = null,
  public val required: List<String>? = null,
  public val items: JsonSchema<*>? = null,
  public val title: String? = null,
  public val minItems: Int? = null,
  public val maxItems: Int? = null,
  public val minimum: Double? = null,
  public val maximum: Double? = null,
  public val anyOf: List<JsonSchema<*>>? = null,
) {

  public companion object {
    /**
     * Returns a [JsonSchema] representing a boolean value.
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
    ): JsonSchema<Boolean> =
      JsonSchema<Boolean>(
        description = description,
        nullable = nullable,
        type = "BOOLEAN",
        title = title,
        clazz = Boolean::class
      )

    /**
     * Returns a [JsonSchema] for a 32-bit signed integer number.
     *
     * **Important:** This [JsonSchema] provides a hint to the model that it should generate a
     * 32-bit integer, but only guarantees that the value will be an integer. Therefore it's
     * *possible* that decoding it as an `Int` variable (or `int` in Java) could overflow.
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
    ): JsonSchema<Integer> =
      JsonSchema(
        description = description,
        format = "int32",
        nullable = nullable,
        type = "INTEGER",
        title = title,
        minimum = minimum,
        maximum = maximum,
        clazz = Integer::class
      )

    /**
     * Returns a [JsonSchema] for a 64-bit signed integer number.
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
    ): JsonSchema<Long> =
      JsonSchema(
        description = description,
        nullable = nullable,
        type = "INTEGER",
        title = title,
        minimum = minimum,
        maximum = maximum,
        clazz = Long::class
      )

    /**
     * Returns a [JsonSchema] for a double-precision floating-point number.
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
    ): JsonSchema<Double> =
      JsonSchema(
        description = description,
        nullable = nullable,
        type = "NUMBER",
        title = title,
        minimum = minimum,
        maximum = maximum,
        clazz = Double::class
      )

    /**
     * Returns a [JsonSchema] for a single-precision floating-point number.
     *
     * **Important:** This [JsonSchema] provides a hint to the model that it should generate a
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
    ): JsonSchema<Float> =
      JsonSchema(
        description = description,
        nullable = nullable,
        type = "NUMBER",
        format = "float",
        title = title,
        minimum = minimum,
        maximum = maximum,
        clazz = Float::class
      )

    /**
     * Returns a [JsonSchema] for a string.
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
    ): JsonSchema<String> =
      JsonSchema(
        description = description,
        format = format?.value,
        nullable = nullable,
        type = "STRING",
        title = title,
        clazz = String::class
      )

    /**
     * Returns a [JsonSchema] for a complex data type.
     *
     * This schema instructs the model to produce data of type object, which has keys of type
     * `String` and values of type [JsonSchema].
     *
     * **Example:** A `city` could be represented with the following object `JsonSchema`.
     *
     * ```
     * JsonSchema.obj(mapOf(
     *   "name"  to JsonSchema.string(),
     *   "population" to JsonSchema.integer()
     * ))
     * ```
     *
     * @param properties The map of the object's property names to their [JsonSchema]s.
     * @param optionalProperties The list of optional properties. They must correspond to the keys
     * provided in the `properties` map. By default it's empty, signaling the model that all
     * properties are to be included.
     * @param description An optional description of what the object represents.
     * @param nullable Indicates whether the value can be `null`. Defaults to `false`.
     */
    @JvmStatic
    @JvmOverloads
    public fun obj(
      properties: Map<String, JsonSchema<*>>,
      optionalProperties: List<String> = emptyList(),
      description: String? = null,
      nullable: Boolean = false,
      title: String? = null,
    ): JsonSchema<JsonObject> {
      if (!properties.keys.containsAll(optionalProperties)) {
        throw IllegalArgumentException(
          "All optional properties must be present in properties. Missing: ${optionalProperties.minus(properties.keys)}"
        )
      }
      return JsonSchema(
        description = description,
        nullable = nullable,
        properties = properties,
        required = properties.keys.minus(optionalProperties.toSet()).toList(),
        type = "OBJECT",
        title = title,
        clazz = JsonObject::class
      )
    }

    /**
     * Returns a [JsonSchema] for a complex data type.
     *
     * This schema instructs the model to produce data of type object, which has keys of type
     * `String` and values of type [JsonSchema].
     *
     * **Example:** A `city` could be represented with the following object `JsonSchema`.
     *
     * ```
     * JsonSchema.obj(mapOf(
     *     "name"  to JsonSchema.string(),
     *     "population" to JsonSchema.integer()
     *   ),
     *   City::class
     * )
     * ```
     *
     * @param properties The map of the object's property names to their [JsonSchema]s.
     * @param clazz the real class that this schema represents
     * @param optionalProperties The list of optional properties. They must correspond to the keys
     * provided in the `properties` map. By default it's empty, signaling the model that all
     * properties are to be included.
     * @param description An optional description of what the object represents.
     * @param nullable Indicates whether the value can be `null`. Defaults to `false`.
     */
    @JvmStatic
    @JvmOverloads
    public fun <T : Any> obj(
      properties: Map<String, JsonSchema<*>>,
      clazz: KClass<T>,
      optionalProperties: List<String> = emptyList(),
      description: String? = null,
      nullable: Boolean = false,
      title: String? = null,
    ): JsonSchema<T> {
      if (!properties.keys.containsAll(optionalProperties)) {
        throw IllegalArgumentException(
          "All optional properties must be present in properties. Missing: ${optionalProperties.minus(properties.keys)}"
        )
      }
      return JsonSchema(
        description = description,
        nullable = nullable,
        properties = properties,
        required = properties.keys.minus(optionalProperties.toSet()).toList(),
        type = "OBJECT",
        title = title,
        clazz = clazz
      )
    }

    /**
     * Returns a [JsonSchema] for an array.
     *
     * @param items The [JsonSchema] of the elements stored in the array.
     * @param description An optional description of what the array represents.
     * @param nullable Indicates whether the value can be `null`. Defaults to `false`.
     */
    @JvmStatic
    @JvmOverloads
    public fun array(
      items: JsonSchema<*>,
      description: String? = null,
      nullable: Boolean = false,
      title: String? = null,
      minItems: Int? = null,
      maxItems: Int? = null,
    ): JsonSchema<List<*>> =
      JsonSchema(
        description = description,
        nullable = nullable,
        items = items,
        type = "ARRAY",
        title = title,
        minItems = minItems,
        maxItems = maxItems,
        clazz = List::class
      )

    /**
     * Returns a [JsonSchema] for an enumeration.
     *
     * For example, the cardinal directions can be represented as:
     * ```
     * JsonSchema.enumeration(listOf("north", "east", "south", "west"), "Cardinal directions")
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
    ): JsonSchema<String> =
      JsonSchema(
        description = description,
        format = "enum",
        nullable = nullable,
        enum = values,
        type = "STRING",
        title = title,
        clazz = String::class
      )

    /**
     * Returns a [JsonSchema] for an enumeration.
     *
     * For example, the cardinal directions can be represented as:
     * ```
     * JsonSchema.enumeration(
     *   listOf("north", "east", "south", "west"),
     *   Direction::class,
     *   "Cardinal directions"
     * )
     * ```
     *
     * @param values The list of valid values for this enumeration
     * @param clazz the real class that this schema represents
     * @param description The description of what the parameter should contain or represent
     * @param nullable Indicates whether the value can be `null`. Defaults to `false`.
     */
    @JvmStatic
    @JvmOverloads
    public fun <T : Any> enumeration(
      values: List<String>,
      clazz: KClass<T>,
      description: String? = null,
      nullable: Boolean = false,
      title: String? = null,
    ): JsonSchema<T> =
      JsonSchema(
        description = description,
        format = "enum",
        nullable = nullable,
        enum = values,
        type = "STRING",
        title = title,
        clazz = clazz
      )

    /**
     * Returns a [JsonSchema] representing a value that must conform to *any* (one of) the provided
     * sub-schema.
     *
     * Example: A field that can hold either a simple userID or a more detailed user object.
     *
     * ```
     * JsonSchema.anyOf( listOf( JsonSchema.integer(description = "User ID"), JsonSchema.obj( mapOf(
     *     "userID" to JsonSchema.integer(description = "User ID"),
     *     "username" to JsonSchema.string(description = "Username")
     * )))
     * ```
     *
     * @param schemas The list of valid schemas which could be here
     */
    @JvmStatic
    public fun anyOf(schemas: List<JsonSchema<*>>): JsonSchema<String> =
      JsonSchema(type = "ANYOF", anyOf = schemas, clazz = String::class)
  }

  internal fun toInternalJson(): Schema.InternalJson {
    val outType =
      if (type == "ANYOF" || (type == "STRING" && format == "enum")) {
        null
      } else {
        type.lowercase()
      }

    val (outMinimum, outMaximum) =
      if (outType == "integer" && format == "int32") {
        (minimum ?: Integer.MIN_VALUE.toDouble()) to (maximum ?: Integer.MAX_VALUE.toDouble())
      } else {
        minimum to maximum
      }

    val outFormat =
      if (
        (outType == "integer" && format == "int32") ||
          (outType == "number" && format == "float") ||
          format == "enum"
      ) {
        null
      } else {
        format
      }

    if (nullable == true) {
      return Schema.InternalJsonNullable(
        outType?.let { listOf(it, "null") },
        description,
        outFormat,
        pattern,
        enum?.let {
          buildList {
            addAll(it)
            add("null")
          }
        },
        properties?.mapValues { it.value.toInternalJson() },
        required,
        items?.toInternalJson(),
        title,
        minItems,
        maxItems,
        outMinimum,
        outMaximum,
        anyOf?.map { it.toInternalJson() },
      )
    }
    return Schema.InternalJsonNonNull(
      outType,
      description,
      outFormat,
      pattern,
      enum,
      properties?.mapValues { it.value.toInternalJson() },
      required,
      items?.toInternalJson(),
      title,
      minItems,
      maxItems,
      outMinimum,
      outMaximum,
      anyOf?.map { it.toInternalJson() },
    )
  }
}
