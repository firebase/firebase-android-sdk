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

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.firebase.sql.Count
import com.google.firebase.sql.constantsOf
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Tests the requests that [QueryBuilder] builds. The expected values follow how PostgREST parses
 * each part of the request.
 */
class QueryBuilderTest {

  private val executor = FakeOperationsExecutor()

  private fun from(table: String = "products"): QueryBuilder = OperationBuilderImpl(table, executor)

  // --- select ---

  @Test
  fun selectDefaultsToAllColumns() {
    val req = from().select().toRequest()

    assertThat(req.httpMethod).isEqualTo("GET")
    assertThat(req.pathSegments).containsExactly("products")
    assertThat(req.pathSegments).containsExactly("products")
    assertThat(req.queryParams).containsExactly("select" to "*")
    assertThat(req.headers).isEmpty()
    assertThat(req.body).isNull()
  }

  @Test
  fun selectWithHeadAndCountSendsHeadRequestWithCountPreference() {
    val req = from().select("id, name", head = true, count = Count.EXACT).toRequest()

    assertThat(req.httpMethod).isEqualTo("HEAD")
    assertThat(req.queryParams).containsExactly("select" to "id, name")
    assertThat(req.headers).containsExactly("Prefer", "count=exact")
  }

  @Test
  fun selectSendsEachCountAlgorithm() {
    val preferences =
      mapOf(
        Count.EXACT to "count=exact",
        Count.PLANNED to "count=planned",
        Count.ESTIMATED to "count=estimated"
      )

    assertThat(preferences.keys).containsExactlyElementsIn(constantsOf<Count>().values)
    for ((count, preference) in preferences) {
      val req = from().select(count = count).toRequest()
      assertWithMessage("$count").that(req.headers["Prefer"]).isEqualTo(preference)
    }
  }

  @Test
  fun selectKeepsEmbeddedResourcesAliasesSpacesAndTabs() {
    // PostgREST skips spaces and tabs around the items of `select`.
    val req = from("posts").select("id,\tauthor:user_id, comments(*)").toRequest()

    assertThat(req.queryParams).containsExactly("select" to "id,\tauthor:user_id, comments(*)")
  }

  @Test
  fun selectReplacesLineBreaksOutsideQuotesWithSpaces() {
    // PostgREST only skips spaces and tabs in `select`, so it rejects a line break after a comma
    // (PGRST100) and ignores a select that starts with one.
    val req = from().select("\n  id,\n  categories(\r\n    name\n  )\n").toRequest()

    assertThat(req.queryParams).containsExactly("select" to "id,   categories(      name   )")
  }

  @Test
  fun selectKeepsWhitespaceInsideQuotedNames() {
    // In PostgREST, a double-quoted name can contain any character, and `\` escapes the next one.
    val req = from().select("id,\n\"line\nbreak\",\"quoted \\\" \n\"").toRequest()

    assertThat(req.queryParams)
      .containsExactly("select" to "id, \"line\nbreak\",\"quoted \\\" \n\"")
  }

  // --- names ---

  @Test
  fun aTableNameIsSentAsOnePathSegment() {
    // The network client percent-encodes each segment, so a table named `rpc/x` is not mistaken
    // for the function `x`.
    val req = from("rpc/x").select().toRequest()

    assertThat(req.pathSegments).containsExactly("rpc/x")
  }

  @Test
  fun namesThatAreNotOnePathSegmentAreRejected() {
    // OkHttp drops a `.` segment and removes the segment before a `..` one, and an empty name
    // changes the path too, so each of these names would reach another URL.
    for (name in listOf("", ".", "..")) {
      assertThrows("from(\"$name\")", IllegalArgumentException::class.java) { from(name) }
      assertThrows("rpc(\"$name\")", IllegalArgumentException::class.java) {
        OperationBuilderImpl.rpc(name, executor = executor)
      }
    }
  }

  // --- insert ---

  @Test
  fun insertSingleObjectSendsJsonObjectWithoutColumns() {
    val req = from().insert(mapOf("id" to 1, "name" to "Apple")).toRequest()

    assertThat(req.httpMethod).isEqualTo("POST")
    assertThat(req.pathSegments).containsExactly("products")
    assertThat(req.queryParams).isEmpty()
    assertThat(req.headers).isEmpty()
    assertThat(req.body).isEqualTo(json("""{"id":1,"name":"Apple"}"""))
  }

  @Test
  fun insertListSendsUnionOfKeysAsColumns() {
    // Without `columns`, PostgREST rejects a list whose objects have different keys (PGRST102).
    // With it, a key that is missing from an object is inserted as NULL, or as the column default
    // with `missing=default`.
    val req =
      from()
        .insert(
          listOf(
            mapOf("id" to 1, "name" to "Apple"),
            mapOf("id" to 2, "description" to "Green", "name" to "Pear")
          )
        )
        .toRequest()

    assertThat(req.queryParams).containsExactly("columns" to "id,name,description")
    assertThat(req.body)
      .isEqualTo(json("""[{"id":1,"name":"Apple"},{"id":2,"description":"Green","name":"Pear"}]"""))
  }

  @Test
  fun insertListQuotesColumnNamesThatAreNotPlainIdentifiers() {
    val req =
      from()
        .insert(
          listOf(
            mapOf(
              "plain_name\$1" to 1,
              "with space" to 2,
              "dash-name" to 3,
              "quote\"back\\slash" to 4,
              "comma,name" to 5
            )
          )
        )
        .toRequest()

    assertThat(req.queryParams)
      .containsExactly(
        "columns" to
          "plain_name\$1,\"with space\",\"dash-name\",\"quote\\\"back\\\\slash\",\"comma,name\""
      )
  }

  @Test
  fun insertEmptyListSendsNoColumns() {
    val req = from().insert(emptyList<Map<String, Any>>()).toRequest()

    assertThat(req.queryParams).isEmpty()
    assertThat(req.body).isEqualTo(json("[]"))
  }

  @Test
  fun insertSerializableObjectLeavesOutThePropertiesThatKeepTheirDefaultValue() {
    val req = from().insert(NewProduct(id = 7, name = "Kiwi")).toRequest()

    assertThat(req.body).isEqualTo(json("""{"id":7,"product_name":"Kiwi"}"""))
  }

  @Test
  fun insertSerializableObjectSendsThePropertiesThatDifferFromTheirDefaultValue() {
    val carrot =
      NewProduct(id = 8, name = "Carrot", tags = listOf("orange"), category = Category.VEGETABLE)

    assertThat(from().insert(carrot).toRequest().body)
      .isEqualTo(
        json("""{"id":8,"product_name":"Carrot","tags":["orange"],"category":"VEGETABLE"}""")
      )
  }

  @Test
  fun insertSendsAPropertyAnnotatedWithEncodeDefaultEvenWithItsDefaultValue() {
    assertThat(from().insert(Flag(id = 1)).toRequest().body)
      .isEqualTo(json("""{"id":1,"enabled":true}"""))
  }

  @Test
  fun insertListOfSerializableObjectsSendsColumns() {
    val products = listOf(NewProduct(1, "Kiwi"), NewProduct(2, "Lime", tags = listOf("green")))

    val req = from().insert(products).toRequest()

    // The union of the keys. The first row keeps the default tags, so its object has no tags key.
    assertThat(req.queryParams).containsExactly("columns" to "id,product_name,tags")
    assertThat(req.body)
      .isEqualTo(
        json(
          """[{"id":1,"product_name":"Kiwi"},
              {"id":2,"product_name":"Lime","tags":["green"]}]"""
        )
      )
  }

  @Test
  fun insertWithASerializerSendsTheSameRequestAsTheExtension() {
    val product = NewProduct(id = 7, name = "Kiwi")
    val products = listOf(NewProduct(1, "Kiwi"), NewProduct(2, "Lime"))

    assertThat(from().insert(product, NewProduct.serializer()).toRequest())
      .isEqualTo(from().insert(product).toRequest())
    assertThat(from().insert(products, ListSerializer(NewProduct.serializer())).toRequest())
      .isEqualTo(from().insert(products).toRequest())
  }

  @Test
  fun insertEncodesAGenericClassWithItsSerializer() {
    // The serializer of Box<Int> needs the type argument, which only the compiler knows.
    val req = from().insert(Box(1)).toRequest()

    assertThat(req.body).isEqualTo(json("""{"value":1}"""))
  }

  @Test
  fun insertSendsAJsonArrayAsItIs() {
    val rows = buildJsonArray {
      addJsonObject { put("id", 1) }
      addJsonObject {
        put("id", 2)
        put("name", "Pear")
      }
    }

    val req = from().insert(rows).toRequest()

    assertThat(req.queryParams).containsExactly("columns" to "id,name")
    assertThat(req.body).isEqualTo(rows)
  }

  @Test
  fun insertRejectsASerializableObjectInsideAMap() {
    // Only the object itself is encoded with its serializer.
    val error =
      assertThrows(IllegalArgumentException::class.java) {
        from().insert(mapOf("product" to NewProduct(1, "Kiwi")))
      }

    assertThat(error).hasMessageThat().contains("@Serializable")
  }

  @Test
  fun insertRejectsValuesWithoutAJsonRepresentation() {
    // The builder throws before anything is sent. JSON has no representation for NaN, infinity, or
    // a class without a serializer. JsonValueEncoderTest covers every type that can be encoded.
    val error =
      assertThrows(IllegalArgumentException::class.java) {
        from().insert(mapOf("value" to NotSerializable(1))).toRequest()
      }
    assertThat(error).hasMessageThat().contains(NotSerializable::class.qualifiedName!!)

    for (number in listOf<Number>(Double.NaN, Float.POSITIVE_INFINITY)) {
      assertThrows("$number", IllegalArgumentException::class.java) {
        from().insert(mapOf("price" to number)).toRequest()
      }
    }
  }

  @Test
  fun insertSendsReturnCountAndMissingPreferences() {
    val req =
      from()
        .insert(mapOf("id" to 1), count = Count.EXACT, defaultToNull = false)
        .select()
        .toRequest()

    assertThat(req.headers)
      .containsExactly("Prefer", "return=representation,count=exact,missing=default")
  }

  @Test
  fun insertReturnsNoBodyUnlessSelectAsksForTheRows() {
    // Without a return preference, PostgREST answers a mutation without a body.
    assertThat(from().insert(mapOf("id" to 1)).toRequest().headers).isEmpty()
    assertThat(from().insert(mapOf("id" to 1)).select().toRequest().headers)
      .containsExactly("Prefer", "return=representation")
  }

  // --- upsert ---

  @Test
  fun upsertWithOnConflictMergesDuplicates() {
    val req = from().upsert(mapOf("id" to 1, "name" to "Apple"), onConflict = "id").toRequest()

    assertThat(req.httpMethod).isEqualTo("POST")
    assertThat(req.queryParams).containsExactly("on_conflict" to "id")
    assertThat(req.headers).containsExactly("Prefer", "resolution=merge-duplicates")
    assertThat(req.body).isEqualTo(json("""{"id":1,"name":"Apple"}"""))
  }

  @Test
  fun upsertWithoutOnConflictOmitsOnConflict() {
    // Without on_conflict, PostgREST resolves conflicts on the primary key. An empty on_conflict=
    // fails to parse (PGRST100).
    val req = from().upsert(mapOf("id" to 1)).toRequest()

    assertThat(req.queryParams).isEmpty()
    assertThat(req.headers).containsExactly("Prefer", "resolution=merge-duplicates")
  }

  @Test
  fun upsertListSendsColumnsAndOnConflict() {
    val req =
      from()
        .upsert(
          listOf(mapOf("id" to 1, "name" to "Apple"), mapOf("id" to 2, "is_active" to true)),
          onConflict = "id",
          defaultToNull = false
        )
        .toRequest()

    assertThat(req.queryParams)
      .containsExactly("columns" to "id,name,is_active", "on_conflict" to "id")
    assertThat(req.headers).containsExactly("Prefer", "missing=default,resolution=merge-duplicates")
  }

  @Test
  fun upsertSendsAllPreferences() {
    val req =
      from()
        .upsert(
          mapOf("id" to 1),
          onConflict = "id",
          ignoreDuplicates = true,
          count = Count.PLANNED,
          defaultToNull = false
        )
        .toRequest()

    assertThat(req.headers)
      .containsExactly("Prefer", "count=planned,missing=default,resolution=ignore-duplicates")
  }

  @Test
  fun upsertRejectsABlankOnConflict() {
    for (onConflict in listOf("", " ")) {
      assertThrows("'$onConflict'", IllegalArgumentException::class.java) {
        from().upsert(mapOf("id" to 1), onConflict = onConflict)
      }
    }
  }

  // --- update and delete ---

  @Test
  fun updateSendsPatchWithBodyAndFilters() {
    val req = from().update(mapOf("name" to "Apple"), count = Count.PLANNED).eq("id", 1).toRequest()

    assertThat(req.httpMethod).isEqualTo("PATCH")
    assertThat(req.queryParams).containsExactly("id" to "eq.1")
    assertThat(req.headers).containsExactly("Prefer", "count=planned")
    assertThat(req.body).isEqualTo(json("""{"name":"Apple"}"""))
  }

  @Test
  fun upsertAndUpdateEncodeSerializableObjects() {
    val product = NewProduct(id = 7, name = "Kiwi")
    val body = json("""{"id":7,"product_name":"Kiwi"}""")

    val upsert = from().upsert(product, onConflict = "id").toRequest()
    val update = from().update(product).eq("id", 7).toRequest()

    assertThat(upsert.body).isEqualTo(body)
    assertThat(upsert.queryParams).containsExactly("on_conflict" to "id")
    assertThat(update.httpMethod).isEqualTo("PATCH")
    assertThat(update.body).isEqualTo(body)
    assertThat(from().upsert(product, NewProduct.serializer()).toRequest())
      .isEqualTo(from().upsert(product).toRequest())
    assertThat(from().update(product, NewProduct.serializer()).toRequest())
      .isEqualTo(from().update(product).toRequest())
  }

  @Test
  fun writesRejectDataThatIsNotRows() {
    // A String is encoded as a JSON string, which PostgREST rejects.
    val json = """[{"id":1}]"""

    assertThrows(IllegalArgumentException::class.java) { from().insert(json) }
    assertThrows(IllegalArgumentException::class.java) { from().upsert(json) }
    assertThrows(IllegalArgumentException::class.java) { from().update(json) }
    assertThrows(IllegalArgumentException::class.java) { from().insert(listOf(1, 2)) }
    assertThrows(IllegalArgumentException::class.java) {
      from().update(listOf(mapOf("name" to "Pear")))
    }
  }

  @Test
  fun deleteSendsDeleteWithoutBody() {
    val req = from().delete(count = Count.EXACT).eq("name", "Apple").toRequest()

    assertThat(req.httpMethod).isEqualTo("DELETE")
    assertThat(req.queryParams).containsExactly("name" to "eq.Apple")
    assertThat(req.headers).containsExactly("Prefer", "count=exact")
    assertThat(req.body).isNull()
  }

  @Serializable
  data class NewProduct(
    val id: Int,
    @SerialName("product_name") val name: String,
    val tags: List<String> = emptyList(),
    val category: Category = Category.FRUIT
  )

  enum class Category {
    FRUIT,
    VEGETABLE
  }

  @OptIn(ExperimentalSerializationApi::class)
  @Serializable
  data class Flag(val id: Int, @EncodeDefault val enabled: Boolean = true)

  @Serializable data class Box<T>(val value: T)

  class NotSerializable(val value: Int)
}
