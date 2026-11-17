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

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Tests how request bodies and filter values are encoded. PostgREST reads request bodies as JSON,
 * so every value needs a JSON representation, and a value without one is rejected before the
 * request is sent.
 */
class JsonValueEncoderTest {

  private fun encode(value: Any?): String = JsonValueEncoder.encode(value).toString()

  @Test
  fun nullIsEncodedAsJsonNull() {
    assertThat(JsonValueEncoder.encode(null)).isEqualTo(JsonNull)
  }

  @Test
  fun jsonElementsAreKeptAsTheyAre() {
    val element = buildJsonObject {
      put("id", 1)
      put("name", "Apple")
    }

    assertThat(JsonValueEncoder.encode(element)).isSameInstanceAs(element)
  }

  @Test
  fun stringsAndCharsAreEncodedAsJsonStrings() {
    assertThat(encode("Doe, \"John\"\n")).isEqualTo("\"Doe, \\\"John\\\"\\n\"")
    assertThat(encode('K')).isEqualTo("\"K\"")
  }

  @Test
  fun booleansAndNumbersAreEncodedAsJsonLiterals() {
    assertThat(encode(true)).isEqualTo("true")
    assertThat(encode(42)).isEqualTo("42")
    assertThat(encode(42L)).isEqualTo("42")
    assertThat(encode(7.toShort())).isEqualTo("7")
    assertThat(encode(7.toByte())).isEqualTo("7")
    assertThat(encode(9.5)).isEqualTo("9.5")
    assertThat(encode(0.25f)).isEqualTo("0.25")
    assertThat(encode(BigDecimal("12345678901234567890.10"))).isEqualTo("12345678901234567890.10")
    assertThat(encode(BigInteger("123456789012345678901234567890")))
      .isEqualTo("123456789012345678901234567890")
  }

  @Test
  fun nonFiniteNumbersAreRejected() {
    // JSON has no representation for NaN or infinity.
    val values =
      listOf(
        Double.NaN,
        Double.POSITIVE_INFINITY,
        Double.NEGATIVE_INFINITY,
        Float.NaN,
        Float.NEGATIVE_INFINITY
      )
    for (value in values) {
      assertThrows(IllegalArgumentException::class.java) { JsonValueEncoder.encode(value) }
    }
  }

  @Test
  fun mapsAreEncodedAsObjectsInIterationOrder() {
    val map = linkedMapOf<Any, Any?>("b" to 1, "a" to null, 3 to "three", Season.WINTER to true)

    assertThat(encode(map)).isEqualTo("""{"b":1,"a":null,"3":"three","WINTER":true}""")
  }

  @Test
  fun collectionsAndArraysAreEncodedAsJsonArrays() {
    assertThat(encode(listOf(1, "a", null))).isEqualTo("""[1,"a",null]""")
    assertThat(encode(linkedSetOf("x", "y"))).isEqualTo("""["x","y"]""")
    assertThat(encode(arrayOf<Any>("x", 2))).isEqualTo("""["x",2]""")
    assertThat(encode(intArrayOf(1, 2))).isEqualTo("[1,2]")
    assertThat(encode(longArrayOf(1L, 2L))).isEqualTo("[1,2]")
    assertThat(encode(shortArrayOf(1, 2))).isEqualTo("[1,2]")
    assertThat(encode(doubleArrayOf(1.5, 2.0))).isEqualTo("[1.5,2.0]")
    assertThat(encode(floatArrayOf(1.5f))).isEqualTo("[1.5]")
    assertThat(encode(booleanArrayOf(true, false))).isEqualTo("[true,false]")
    assertThat(encode(charArrayOf('a', 'b'))).isEqualTo("""["a","b"]""")
  }

  @Test
  fun byteArraysAreEncodedInTheHexFormatOfBytea() {
    // PostgreSQL reads `\x` followed by two hex digits a byte as `bytea`.
    assertThat(encode(byteArrayOf(1, -1, 0x2a))).isEqualTo("\"\\\\x01ff2a\"")
    assertThat(encode(mapOf("data" to byteArrayOf()))).isEqualTo("""{"data":"\\x"}""")
  }

  @Test
  fun nestedValuesAreEncodedRecursively() {
    val value =
      mapOf(
        "tags" to listOf("a", mapOf("k" to listOf(1, null))),
        "empty" to emptyMap<String, Any>(),
        "product" to mapOf("id" to 2, "product_name" to "Lime"),
        "json" to JsonPrimitive(3)
      )

    assertThat(encode(value))
      .isEqualTo(
        """{"tags":["a",{"k":[1,null]}],"empty":{},""" +
          """"product":{"id":2,"product_name":"Lime"},"json":3}"""
      )
  }

  @Test
  fun serializableClassesAreRejected() {
    // The encoder looks up no serializer by class, so that nothing depends on reflection. The
    // builders encode such an object with the serializer that the compiler finds for its type.
    val error =
      assertThrows(IllegalArgumentException::class.java) {
        encode(mapOf("product" to Product(1, "Kiwi")))
      }

    assertThat(error).hasMessageThat().contains(Product::class.qualifiedName!!)
    assertThat(error).hasMessageThat().contains("@Serializable")
    assertThat(error).hasMessageThat().contains("insert()")
  }

  @Test
  fun enumsAreEncodedByTheirName() {
    assertThat(encode(Season.WINTER)).isEqualTo("\"WINTER\"")
    // The @SerialName of a constant needs the serializer of the enum, which the encoder does not
    // look up.
    assertThat(encode(Status.ACTIVE)).isEqualTo("\"ACTIVE\"")
    // A constant with a body is an instance of a subclass of the enum class.
    assertThat(encode(Shape.CIRCLE)).isEqualTo("\"CIRCLE\"")
  }

  @Test
  fun uuidsAreEncodedAsStrings() {
    assertThat(encode(UUID.fromString("123e4567-e89b-12d3-a456-426614174000")))
      .isEqualTo("\"123e4567-e89b-12d3-a456-426614174000\"")
  }

  @Test
  fun javaTimeValuesAndOtherLibraryClassesAreRejected() {
    // The types are a closed set, so that no class is recognized by its name: core library
    // desugaring renames the java.time classes, for example to j$.time.Instant.
    val instant = Instant.parse("2026-10-08T04:50:06.123Z")
    val values =
      listOf(
        instant,
        OffsetDateTime.parse("2026-10-08T10:20:06.123+05:30"),
        instant.atZone(ZoneId.of("Asia/Kolkata")),
        java.net.URI("https://example.com"),
        1 to 2
      )

    for (value in values) {
      val error =
        assertThrows("$value", IllegalArgumentException::class.java) { encode(mapOf("v" to value)) }
      assertWithMessage("$value")
        .that(error)
        .hasMessageThat()
        .contains("java.time value to a String")
    }
  }

  @Test
  fun datesAreEncodedAsIso8601Timestamps() {
    // Date.toString() writes the local time and a zone name without the milliseconds, for example
    // `Thu Oct 08 10:20:06 IST 2026` on the JVM and `Thu Oct 08 10:20:06 GMT+05:30 2026` on
    // Android. PostgreSQL reads `IST` as Israel Standard Time and `GMT+05:30` as 5:30 west of
    // Greenwich, so these timestamps are 3.5 and 11 hours off.
    val defaultTimeZone = TimeZone.getDefault()
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
    try {
      val instant = Instant.parse("2026-10-08T04:50:06.123Z")

      val encoded = JsonValueEncoder.encode(Date(instant.toEpochMilli())) as JsonPrimitive

      assertThat(encoded.isString).isTrue()
      assertThat(encoded.content).isEqualTo("2026-10-08T04:50:06.123Z")
      assertThat(OffsetDateTime.parse(encoded.content).toInstant()).isEqualTo(instant)
    } finally {
      TimeZone.setDefault(defaultTimeZone)
    }
  }

  @Test
  fun sqlDatesAndTimesAreEncodedAsTheirSqlLiterals() {
    // java.sql.Date and java.sql.Time extend Date, but hold a local date and a local time. As an
    // instant in UTC, this date would be 2026-10-07T18:30:00.000Z in India.
    val defaultTimeZone = TimeZone.getDefault()
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
    try {
      assertThat(encode(java.sql.Date.valueOf("2026-10-08"))).isEqualTo("\"2026-10-08\"")
      assertThat(encode(java.sql.Time.valueOf("10:20:06"))).isEqualTo("\"10:20:06\"")
    } finally {
      TimeZone.setDefault(defaultTimeZone)
    }
  }

  @Test
  fun datesBeforeTheGregorianReformUseTheProlepticGregorianCalendar() {
    // PostgreSQL and java.time only use the Gregorian calendar. By default, SimpleDateFormat
    // switches to the Julian calendar before 1582-10-15, which moves these dates by 2 to 10 days.
    val texts =
      listOf("1582-10-14T00:00:00.000Z", "1000-01-01T00:00:00.000Z", "0001-01-01T00:00:00.000Z")

    for (text in texts) {
      assertWithMessage(text)
        .that(JsonValueEncoder.encode(Date(Instant.parse(text).toEpochMilli())))
        .isEqualTo(JsonPrimitive(text))
    }
  }

  @Test
  fun datesCanBeEncodedOnSeveralThreadsAtOnce() {
    // Each thread reuses its own SimpleDateFormat, which is not thread-safe.
    val iso8601 =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)
    val pool = Executors.newFixedThreadPool(8)
    try {
      val tasks =
        List(8) { thread ->
          pool.submit(
            Callable {
              List(500) { i ->
                val millis = 946_684_800_123L + (thread * 500L + i) * 86_399_001L
                val encoded = JsonValueEncoder.encode(Date(millis)) as JsonPrimitive
                iso8601.format(Instant.ofEpochMilli(millis)) to encoded.content
              }
            }
          )
        }

      for ((expected, encoded) in tasks.flatMap { it.get() }) {
        assertThat(encoded).isEqualTo(expected)
      }
    } finally {
      pool.shutdown()
    }
  }

  @Test
  fun otherValuesAreRejectedWithTheClassName() {
    val error =
      assertThrows(IllegalArgumentException::class.java) {
        JsonValueEncoder.encode(listOf(NotSerializable(1)))
      }

    assertThat(error).hasMessageThat().contains(NotSerializable::class.qualifiedName!!)
  }

  @Test
  fun anObjectOfAnAnonymousClassIsRejected() {
    assertThrows(IllegalArgumentException::class.java) { encode(object {}) }
  }

  @Serializable
  data class Product(
    val id: Int,
    @SerialName("product_name") val name: String,
    val tags: List<String> = emptyList(),
    val season: Season? = null
  )

  enum class Season {
    WINTER
  }

  @Serializable
  enum class Status {
    @SerialName("active") ACTIVE
  }

  enum class Shape {
    CIRCLE {
      override val sides = 0
    };

    abstract val sides: Int
  }

  class NotSerializable(val value: Int)
}
