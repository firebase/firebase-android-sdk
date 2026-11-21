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

package com.google.firebase.sql.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Encodes request bodies, filter values, and function arguments to JSON.
 *
 * PostgREST reads request bodies as JSON, so every value needs a JSON representation. A value
 * without one is rejected with an [IllegalArgumentException] before the request is sent.
 */
internal object JsonValueEncoder {

  /**
   * Encodes [value] to a [JsonElement]:
   * - `null` becomes [JsonNull], and a [JsonElement] is kept as it is.
   * - Strings and chars become JSON strings. Booleans and numbers become JSON literals.
   * - Maps become JSON objects, in iteration order. The keys are the `toString()` of the map keys.
   * - A [ByteArray] becomes the JSON string of the hex format of PostgreSQL `bytea`, for example
   * `"\\x01ff"`, so a `bytea` column stores the bytes. A `ByteArray` property of a `@Serializable`
   * class uses its serializer instead, which gives a JSON array of numbers.
   * - Other collections and arrays, including other primitive arrays, become JSON arrays.
   * - An enum becomes the JSON string of its name.
   * - A [UUID] becomes the JSON string of its canonical form.
   * - A [Date] becomes an ISO-8601 timestamp string that PostgreSQL accepts as `timestamptz`, in
   * the proleptic Gregorian calendar that PostgreSQL uses. `java.sql.Date` and `java.sql.Time`
   * become their SQL literals, for example `2026-10-08` and `10:20:06`.
   *
   * Any other value is rejected, including `java.time` values, which apps convert to strings, and
   * objects of classes annotated with `@Serializable`, which the builders encode with the
   * serializer of their type. The set of types is closed so that no class is recognized by its
   * name, which would break with minification and core library desugaring.
   *
   * @throws IllegalArgumentException if [value] is or contains a number that is not finite, or a
   * value that has no JSON representation.
   */
  fun encode(value: Any?): JsonElement =
    when (value) {
      null -> JsonNull
      is JsonElement -> value
      is String -> JsonPrimitive(value)
      is Char -> JsonPrimitive(value.toString())
      is Boolean -> JsonPrimitive(value)
      is Number -> encodeNumber(value)
      is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to encode(v) })
      is Iterable<*> -> JsonArray(value.map { encode(it) })
      is Array<*> -> JsonArray(value.map { encode(it) })
      is ByteArray -> JsonPrimitive(byteaHex(value))
      is ShortArray -> JsonArray(value.map { JsonPrimitive(it) })
      is IntArray -> JsonArray(value.map { JsonPrimitive(it) })
      is LongArray -> JsonArray(value.map { JsonPrimitive(it) })
      is FloatArray -> JsonArray(value.map { encodeNumber(it) })
      is DoubleArray -> JsonArray(value.map { encodeNumber(it) })
      is BooleanArray -> JsonArray(value.map { JsonPrimitive(it) })
      is CharArray -> JsonArray(value.map { JsonPrimitive(it.toString()) })
      is Enum<*> -> JsonPrimitive(value.name)
      is UUID -> JsonPrimitive(value.toString())
      is Date -> encodeDate(value)
      else -> throw unsupported(value)
    }

  /**
   * Returns [bytes] in the hex format of PostgreSQL `bytea`: `\x` followed by two hexadecimal
   * digits for each byte.
   */
  private fun byteaHex(bytes: ByteArray): String =
    bytes.joinToString(separator = "", prefix = "\\x") { "%02x".format(it.toInt() and 0xff) }

  private fun encodeDate(value: Date): JsonPrimitive {
    // java.sql.Date and java.sql.Time hold a local date and a local time, and their toString() is
    // the SQL literal. As an instant in UTC, a date would move to the previous or the next day.
    if (value is java.sql.Date || value is java.sql.Time) {
      return JsonPrimitive(value.toString())
    }
    return JsonPrimitive(utcTimestampFormat.get()!!.format(value))
  }

  /**
   * The format of an instant in UTC, one for each thread, because a [SimpleDateFormat] is not
   * thread-safe, and is slow to create.
   */
  private val utcTimestampFormat =
    object : ThreadLocal<SimpleDateFormat>() {
      override fun initialValue(): SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
          // By default, SimpleDateFormat switches to the Julian calendar before 1582-10-15, which
          // would move older dates by several days. PostgreSQL uses the Gregorian calendar for
          // all dates.
          calendar =
            GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.ROOT).apply {
              gregorianChange = Date(Long.MIN_VALUE)
            }
        }
    }

  private fun encodeNumber(value: Number): JsonPrimitive {
    val finite =
      when (value) {
        is Double -> value.isFinite()
        is Float -> value.isFinite()
        else -> true
      }
    require(finite) { "JSON has no representation for the number $value." }
    return JsonPrimitive(value)
  }

  private fun unsupported(value: Any) =
    IllegalArgumentException(
      // A local or an anonymous class has no qualified name.
      "Cannot encode a value of type ${value::class.qualifiedName ?: value::class} to JSON. Use a " +
        "String, a number, a Boolean, an enum, a UUID, a Date, a Map, a List, or a JsonElement, " +
        "and convert other values first, for example a java.time value to a String. To send an " +
        "object of a class annotated with @Serializable, pass it as the data of insert(), " +
        "upsert(), or update(), or as the params of rpc(), or encode it with " +
        "Json.encodeToJsonElement() first."
    )
}
