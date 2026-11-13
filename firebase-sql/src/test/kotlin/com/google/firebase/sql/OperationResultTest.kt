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

package com.google.firebase.sql

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertThrows
import org.junit.Test

/** Tests how [OperationResult] decodes the data of a response, and how results are compared. */
class OperationResultTest {

  @Serializable data class Product(val id: Int, val name: String)

  private fun result(data: String, count: Long? = null, range: LongRange? = null) =
    OperationResult(data = data, status = 200, statusText = "OK", count = count, range = range)

  @Test
  fun decodeListDecodesEveryElementOfTheArray() {
    val json = "[{\"id\": 1, \"name\": \"Product 1\"}, {\"id\": 2, \"name\": \"Product 2\"}]"

    val products = result(json).decodeList<Product>()

    assertThat(products).containsExactly(Product(1, "Product 1"), Product(2, "Product 2")).inOrder()
  }

  @Test
  fun decodeSingleDecodesTheObject() {
    val product = result("{\"id\": 1, \"name\": \"Product 1\"}").decodeSingle<Product>()

    assertThat(product).isEqualTo(Product(1, "Product 1"))
  }

  @Test
  fun decodeSingleOrNullDecodesAValidObject() {
    val product = result("{\"id\": 1, \"name\": \"Product 1\"}").decodeSingleOrNull<Product>()

    assertThat(product).isEqualTo(Product(1, "Product 1"))
  }

  @Test
  fun decodeSingleAcceptsAnObjectOrAnArrayOfOneRow() {
    val product = Product(1, "Product 1")

    assertThat(result("{\"id\": 1, \"name\": \"Product 1\"}").decodeSingle<Product>())
      .isEqualTo(product)
    assertThat(result("[{\"id\": 1, \"name\": \"Product 1\"}]").decodeSingle<Product>())
      .isEqualTo(product)
    assertThat(result("[{\"id\": 1, \"name\": \"Product 1\"}]").decodeSingleOrNull<Product>())
      .isEqualTo(product)
    // A function that returns a scalar.
    assertThat(result("5").decodeSingle<Int>()).isEqualTo(5)
  }

  @Test
  fun decodeSingleThrowsWithoutExactlyOneRow() {
    // No row, like first() on an empty list.
    for (data in listOf("null", "[]")) {
      val error =
        assertThrows(data, NoSuchElementException::class.java) {
          result(data).decodeSingle<Product>()
        }
      assertWithMessage(data).that(error).hasMessageThat().contains("found no row")
    }

    // Several rows, like single() on a list of several elements.
    val error =
      assertThrows(IllegalArgumentException::class.java) {
        result("[{\"id\": 1, \"name\": \"A\"}, {\"id\": 2, \"name\": \"B\"}]")
          .decodeSingle<Product>()
      }
    // Not a SerializationException, which is an IllegalArgumentException too.
    assertThat(error.javaClass).isEqualTo(IllegalArgumentException::class.java)
    assertThat(error).hasMessageThat().contains("the data has 2 rows")
  }

  @Test
  fun decodeSingleOrNullReturnsNullOnlyWithoutARow() {
    for (data in listOf("null", "[]", "[null]")) {
      assertWithMessage(data).that(result(data).decodeSingleOrNull<Product>()).isNull()
    }
  }

  @Test
  fun decodeSingleOrNullThrowsForSeveralRows() {
    val error =
      assertThrows(IllegalArgumentException::class.java) {
        result("[{\"id\": 1, \"name\": \"A\"}, {\"id\": 2, \"name\": \"B\"}]")
          .decodeSingleOrNull<Product>()
      }

    assertThat(error.javaClass).isEqualTo(IllegalArgumentException::class.java)
    assertThat(error).hasMessageThat().contains("the data has 2 rows")
  }

  @Test
  fun theRowFunctionsThrowAnIllegalStateExceptionForAResponseWithoutBody() {
    // A HEAD request, or a mutation without select(), gives a response without body.
    val decoders =
      mapOf<String, () -> Any?>(
        "decodeList" to { result("").decodeList<Product>() },
        "decodeList(deserializer)" to { result("").decodeList(Product.serializer()) },
        "decodeSingle" to { result("").decodeSingle<Product>() },
        "decodeSingle(deserializer)" to { result("").decodeSingle(Product.serializer()) },
        "decodeSingleOrNull" to { result("").decodeSingleOrNull<Product>() },
        "decodeSingleOrNull(deserializer)" to
          {
            result("").decodeSingleOrNull(Product.serializer())
          },
      )

    for ((name, decode) in decoders) {
      val error = assertThrows(name, IllegalStateException::class.java) { decode() }
      assertWithMessage(name).that(error).hasMessageThat().contains("found no data")
      assertWithMessage(name).that(error).hasMessageThat().contains("call select()")
    }
  }

  @Test
  fun decodeAsDecodesAnyValue() {
    val map = result("{\"id\": 1, \"name\": \"Product 1\"}").decodeAs<Map<String, JsonElement>>()

    assertThat(map.keys).containsExactly("id", "name")
    assertThat(result("5").decodeAs<Int>()).isEqualTo(5)
    assertThat(result("\"text\"").decodeAs<String>()).isEqualTo("text")
  }

  @Test
  fun decodeAsOrNullReturnsNullWhenTheDataCannotBeDecoded() {
    assertThat(result("5").decodeAsOrNull<Int>()).isEqualTo(5)
    for (data in listOf("", "null", "\"text\"", "[1, 2]")) {
      assertWithMessage(data).that(result(data).decodeAsOrNull<Int>()).isNull()
    }
  }

  @Test
  fun decodingErrorsAreTheSerializationExceptionsOfKotlinxSerialization() {
    // single() gives an object, and a read without single() gives an array.
    val decoders =
      mapOf<String, () -> Any?>(
        "decodeList of an object" to
          {
            result("{\"id\": 1, \"name\": \"A\"}").decodeList<Product>()
          },
        "decodeList(deserializer)" to { result("{}").decodeList(Product.serializer()) },
        "decodeSingle of a row of another type" to
          {
            result("[{\"id\": \"one\", \"name\": \"A\"}]").decodeSingle<Product>()
          },
        "decodeSingle(deserializer)" to { result("{}").decodeSingle(Product.serializer()) },
        "decodeSingleOrNull of invalid JSON" to
          {
            result("{\"id\": ").decodeSingleOrNull<Product>()
          },
        "decodeSingleOrNull of a row of another type" to
          {
            result("{\"id\": \"one\", \"name\": \"A\"}").decodeSingleOrNull<Product>()
          },
        "decodeAs" to { result("\"text\"").decodeAs<Int>() },
        "decodeAs of empty data" to { result("").decodeAs<Int>() },
        "decodeAs(deserializer)" to { result("[]").decodeAs(Product.serializer()) },
      )

    for ((name, decode) in decoders) {
      // Thrown as it is, without a wrapper.
      assertThrows(name, SerializationException::class.java) { decode() }
    }
  }

  @Test
  fun theDecodeFunctionsAcceptADeserializer() {
    val product = Product(1, "Product 1")
    val rows = result("[{\"id\": 1, \"name\": \"Product 1\"}]")
    val row = result("{\"id\": 1, \"name\": \"Product 1\"}")
    val noRow = result("null")

    assertThat(rows.decodeList(Product.serializer())).containsExactly(product)
    assertThat(row.decodeSingle(Product.serializer())).isEqualTo(product)
    assertThat(row.decodeSingleOrNull(Product.serializer())).isEqualTo(product)
    assertThat(noRow.decodeSingleOrNull(Product.serializer())).isNull()
    assertThat(rows.decodeAs(ListSerializer(Product.serializer()))).containsExactly(product)
    assertThat(rows.decodeAsOrNull(ListSerializer(Product.serializer()))).containsExactly(product)
    assertThat(row.decodeAsOrNull(ListSerializer(Product.serializer()))).isNull()
  }

  @Test
  fun countOrNullAndRangeOrNullGiveTheValuesOfTheResponse() {
    assertThat(result("[]", count = 250, range = 20L..39L).countOrNull()).isEqualTo(250L)
    assertThat(result("[]", count = 250, range = 20L..39L).rangeOrNull()).isEqualTo(20L..39L)
    assertThat(result("[]").countOrNull()).isNull()
    assertThat(result("[]").rangeOrNull()).isNull()
  }

  @Test
  fun decodeListGivesTheRowsAsJsonObjectsWithoutAClass() {
    val rows = result("""[{"id": 1, "name": "Kiwi"}]""").decodeList<JsonObject>()

    assertThat(rows.single()["id"]?.jsonPrimitive?.long).isEqualTo(1L)
    assertThat(rows.single()["name"]?.jsonPrimitive?.content).isEqualTo("Kiwi")
  }

  @Test
  fun resultsWithEqualPropertiesAreEqual() {
    val first = result("[]", count = 3, range = 0L..2L)
    val second = result("[]", count = 3, range = 0L..2L)

    assertThat(second).isEqualTo(first)
    assertThat(second.hashCode()).isEqualTo(first.hashCode())
  }

  @Test
  fun resultsThatDifferInAnyPropertyAreNotEqual() {
    val base = OperationResult("[]", 200, "OK", count = 3, range = 0L..2L)
    val others =
      listOf(
        OperationResult("[{}]", 200, "OK", count = 3, range = 0L..2L),
        OperationResult("[]", 201, "OK", count = 3, range = 0L..2L),
        OperationResult("[]", 200, "Created", count = 3, range = 0L..2L),
        OperationResult("[]", 200, "OK", count = null, range = 0L..2L),
        OperationResult("[]", 200, "OK", count = 3, range = null),
      )

    for (other in others) {
      assertWithMessage("$other").that(other).isNotEqualTo(base)
    }
  }

  @Test
  fun toStringContainsEveryProperty() {
    val text = OperationResult("[]", 200, "OK", count = 3, range = 0L..2L).toString()

    assertThat(text)
      .isEqualTo("OperationResult(status=200, statusText=OK, count=3, range=0..2, data=[])")
  }
}
