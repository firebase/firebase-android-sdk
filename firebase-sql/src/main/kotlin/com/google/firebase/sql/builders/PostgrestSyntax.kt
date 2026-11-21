/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.sql.builders

import com.google.firebase.sql.core.JsonValueEncoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Formats values for the query string of a request, following how PostgREST parses each part of it.
 */
internal object PostgrestSyntax {

  private val PLAIN_IDENTIFIER = Regex("^[A-Za-z0-9_$]+$")

  /**
   * Formats the value of a filter. PostgREST reads everything after the operator as the value, so a
   * string is sent as is. Other values are sent as their JSON text, without the quotes of a JSON
   * string: for example `42`, `true`, `null`, `WINTER`, or `{"a":1}`.
   */
  fun filterValue(value: Any?): String = if (value is String) value else text(encode(value))

  /**
   * Returns the query parameter name of a filter on [column]. PostgREST reads the parameters
   * `select`, `columns`, and `on_conflict`, and the parameters that end in `order`, `limit`,
   * `offset`, `and`, or `or`, as something other than a filter, so a column with one of these names
   * is double-quoted, for example `"order"` or `cities."limit"`.
   */
  fun filterKey(column: String): String {
    val name = column.substringAfterLast('.')
    if (name !in RESERVED_PARAMETERS) return column
    return column.dropLast(name.length) + "\"$name\""
  }

  /**
   * Formats [values] as a PostgREST list for the `in` operator, for example `(1,"Doe, John")`.
   *
   * PostgREST splits the list on commas and ends it at `)`, so a value that contains a comma, a
   * parenthesis, a double quote, or a backslash is double-quoted, and `"` and `\` are escaped. An
   * empty value and a value that starts or ends with whitespace are quoted too.
   */
  fun list(values: List<Any?>): String =
    values.joinToString(",", prefix = "(", postfix = ")") { listElement(filterValue(it)) }

  /**
   * Formats [values] as a PostgreSQL array literal, for example `{1,"a b"}`, for the array
   * operators (`cs`, `cd`, `ov`) and the quantified operators (`like(any)` and the others).
   *
   * PostgreSQL parses the literal, so an element that is empty, that contains a brace, a comma, a
   * double quote, a backslash, or whitespace, or that is the word `NULL` in any case is
   * double-quoted, and `"` and `\` are escaped. A `null` element is sent as `NULL`, a nested list
   * as a nested array, and an object as its quoted JSON text.
   */
  fun arrayLiteral(values: List<Any?>): String = arrayLiteral(encode(values) as JsonArray)

  /** Formats [array] as a PostgreSQL array literal. See [arrayLiteral]. */
  fun arrayLiteral(array: JsonArray): String =
    array.joinToString(",", prefix = "{", postfix = "}") { arrayElement(it) }

  /**
   * Formats the names of the `columns` query parameter. A name that is not made only of letters,
   * digits, `_`, and `$` is double-quoted, and `"` and `\` are escaped.
   */
  fun columns(names: Collection<String>): String =
    names.joinToString(",") { if (PLAIN_IDENTIFIER.matches(it)) it else quote(it) }

  /**
   * Cleans the columns of the `select` query parameter. PostgREST only skips spaces and tabs
   * between the items of `select`, so other whitespace outside of double-quoted names, such as line
   * breaks, is replaced with spaces. Leading and trailing whitespace is removed.
   */
  fun cleanSelect(columns: String): String {
    val cleaned = StringBuilder(columns.length)
    var quoted = false
    var escaped = false
    for (c in columns) {
      when {
        escaped -> escaped = false
        quoted && c == '\\' -> escaped = true
        c == '"' -> quoted = !quoted
      }
      val keep = quoted || c == '"' || !c.isWhitespace() || c == ' ' || c == '\t'
      cleaned.append(if (keep) c else ' ')
    }
    return cleaned.toString().trim()
  }

  /**
   * Formats the arguments of a function that is called with GET or HEAD. PostgREST reads each
   * argument from the query parameter with its name, and reads the whole value as the argument.
   *
   * A `null` argument is omitted, so that the function uses the default value of the parameter. A
   * list is sent as a PostgreSQL array literal, and an object as its JSON text.
   *
   * @throws IllegalArgumentException if [arguments] is not encoded as a JSON object, that is if it
   * is not a `Map` or a class annotated with `@Serializable`.
   */
  fun functionArguments(arguments: Any): List<Pair<String, String>> {
    val encoded = encode(arguments)
    require(encoded is JsonObject) {
      "The arguments of a function called with GET or HEAD must be a Map or a @Serializable " +
        "class, but was ${if (encoded is JsonArray) "a list" else "a single value"}."
    }
    return encoded.mapNotNull { (name, value) ->
      when (value) {
        is JsonNull -> null
        is JsonArray -> name to arrayLiteral(value)
        else -> name to text(value)
      }
    }
  }

  private fun encode(value: Any?): JsonElement = JsonValueEncoder.encode(value)

  /** The text of [element], without the quotes of a JSON string. */
  private fun text(element: JsonElement): String =
    if (element is JsonPrimitive) element.content else element.toString()

  private fun listElement(value: String): String {
    val needsQuotes =
      value.isEmpty() ||
        value.any { it in ",()\"\\" } ||
        value.first().isWhitespace() ||
        value.last().isWhitespace()
    return if (needsQuotes) quote(value) else value
  }

  private fun arrayElement(element: JsonElement): String =
    when (element) {
      is JsonNull -> "NULL"
      is JsonArray -> arrayLiteral(element)
      is JsonObject -> quote(element.toString())
      is JsonPrimitive -> {
        val value = element.content
        val needsQuotes =
          value.isEmpty() ||
            value.equals("NULL", ignoreCase = true) ||
            value.any { it in "{},\"\\" || it.isWhitespace() }
        if (needsQuotes) quote(value) else value
      }
    }

  private fun quote(value: String): String = buildString {
    append('"')
    for (c in value) {
      if (c == '"' || c == '\\') append('\\')
      append(c)
    }
    append('"')
  }

  /** The names that PostgREST reads as parameters, not as filters, as the last part of a name. */
  private val RESERVED_PARAMETERS =
    setOf("select", "columns", "on_conflict", "order", "limit", "offset", "and", "or")
}
