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

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.sql.builders.FilterBuilder
import com.google.firebase.sql.builders.TransformBuilder
import com.google.firebase.sql.builders.insert
import com.google.firebase.sql.builders.update
import com.google.firebase.sql.builders.upsert
import java.io.IOException
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@Serializable private data class TestProduct(val id: Int, val name: String)

@Serializable private data class EchoArgs(@SerialName("input_text") val inputText: String)

/**
 * Runs the SDK against a real PostgREST server and PostgreSQL database.
 *
 * The tests call a Firebase SQL service in production with `Firebase.sql()`, like an app would;
 * [SqlTestService] describes how the service is chosen. `run_cloud_tests.sh` creates the tables and
 * functions of `src/androidTest/sql/e2e_schema.sql` if they do not exist. Every test uses ids from
 * [generateId], which are unique to the run, and [tearDown] deletes the rows with these ids, so the
 * tests do not depend on each other, on rows left by earlier runs, or on runs of other developers
 * against the same database.
 */
@RunWith(AndroidJUnit4::class)
class CloudSqlEndToEndTest {

  private val service = SqlTestService.fromArguments()
  private lateinit var app: FirebaseApp
  private lateinit var fsql: FirebaseSql

  /** The `inventory` schema, set by [testMultipleSchemas] so that [tearDown] cleans it up too. */
  private var inventoryFsql: FirebaseSql? = null

  /** The ids returned by [generateId]. Tests can generate ids from several threads. */
  private val createdIds: MutableSet<Int> = Collections.synchronizedSet(LinkedHashSet())

  @Before
  fun setUp() {
    FirebaseApp.clearInstancesForTest()
    Log.i(TAG, "Running against the $service")
    app = service.firebaseApp()
    fsql = service.sql(app)
  }

  @After
  fun tearDown() {
    try {
      if (::fsql.isInitialized) runBlocking { deleteCreatedRows() }
    } finally {
      FirebaseApp.clearInstancesForTest()
    }
  }

  // --- CRUD ---

  /** A row is read back with two filters, `single()` and `decodeSingle()`. */
  @Test
  fun insertedRowIsReadBackWithFiltersAndSingle() =
    runBlocking<Unit> {
      val ids =
        insertProducts(
          product("Inactive Product", "description" to "Multiple", "is_active" to false),
          product("Active Product", "description" to "Multiple", "is_active" to true)
        )

      val result =
        fsql
          .from("products")
          .select("id, name")
          .isIn("id", ids)
          .eq("description", "Multiple")
          .eq("is_active", false)
          .single()
          .executeOrFail()

      assertThat(result.decodeSingle<TestProduct>())
        .isEqualTo(TestProduct(ids[0], "Inactive Product"))
    }

  /** An insert returns no body, and with `select()` it returns the inserted row. */
  @Test
  fun insertReturnsTheRowOnlyWithSelect() =
    runBlocking<Unit> {
      val minimalId = generateId()
      val minimal =
        fsql
          .from("products")
          .insert(mapOf("id" to minimalId, "name" to "Min Product"))
          .executeOrFail()
      assertThat(minimal.data).isEmpty()
      assertThat(readProduct(minimalId)).containsEntry("name", "Min Product")

      val representationId = generateId()
      val representation =
        fsql
          .from("products")
          .insert(mapOf("id" to representationId, "name" to "Rep Product"))
          .select()
          .executeOrFail()
      assertThat(ids(representation)).containsExactly(representationId)
      assertThat(names(representation)).containsExactly("Rep Product")
    }

  /** A list body inserts every row, and the rows can be decoded with `decodeList()`. */
  @Test
  fun insertOfAListInsertsEveryRow() =
    runBlocking<Unit> {
      val id1 = generateId()
      val id2 = generateId()
      val inserted =
        fsql
          .from("products")
          .insert(
            listOf(
              mapOf("id" to id1, "name" to "List Product 1", "is_active" to false),
              mapOf("id" to id2, "name" to "List Product 2", "is_active" to true)
            )
          )
          .select()
          .executeOrFail()
      assertThat(names(inserted)).containsExactly("List Product 1", "List Product 2")

      val read =
        fsql
          .from("products")
          .select("id, name")
          .isIn("id", listOf(id1, id2))
          .order("name")
          .execute()
      assertThat(read.decodeList<TestProduct>())
        .containsExactly(TestProduct(id1, "List Product 1"), TestProduct(id2, "List Product 2"))
        .inOrder()
    }

  /**
   * A bulk insert whose objects have different keys succeeds, and missing columns become NULL.
   * PostgREST rejects such a body with PGRST102 ("All object keys must match") unless the `columns`
   * parameter lists the columns to insert.
   */
  @Test
  fun insertOfAListWithDifferentKeysFillsTheMissingColumnsWithNull() =
    runBlocking<Unit> {
      val id1 = generateId()
      val id2 = generateId()
      fsql
        .from("products")
        .insert(
          listOf(
            mapOf("id" to id1, "name" to "Bulk Full", "description" to "Has description"),
            mapOf("id" to id2, "name" to "Bulk Partial")
          )
        )
        .executeOrFail()

      assertThat(readProduct(id1, "name, description"))
        .containsExactly("name", "Bulk Full", "description", "Has description")
      assertThat(readProduct(id2, "name, description"))
        .containsExactly("name", "Bulk Partial", "description", null)
    }

  /** A kotlinx `JsonObject` body is sent as the JSON object that it holds. */
  @Test
  fun insertOfAJsonObjectBody() =
    runBlocking<Unit> {
      val id = generateId()
      fsql
        .from("products")
        .insert(
          buildJsonObject {
            put("id", id)
            put("name", "Json Body Product")
          }
        )
        .executeOrFail()

      assertThat(readProduct(id)).containsEntry("name", "Json Body Product")
    }

  /** An enum and a kotlinx `JsonArray` inside a map body are sent as a string and an array. */
  @Test
  fun insertOfAMapWithEnumAndJsonArrayValues() =
    runBlocking<Unit> {
      val id = generateId()
      fsql
        .from("products")
        .insert(
          mapOf(
            "id" to id,
            "name" to "Enum And Json Values",
            "description" to Season.WINTER,
            "tags" to
              buildJsonArray {
                add("sale")
                add("winter")
              }
          )
        )
        .executeOrFail()

      assertThat(readProduct(id, "description, tags"))
        .containsExactly("description", "WINTER", "tags", listOf("sale", "winter"))
    }

  /** An update changes only the rows that its filter matches. */
  @Test
  fun updateChangesOnlyTheMatchedRow() =
    runBlocking<Unit> {
      val (id1, id2) = insertProducts(product("Before Update"), product("Untouched"))

      fsql.from("products").update(mapOf("name" to "After Update")).eq("id", id1).executeOrFail()

      assertThat(readProduct(id1)).containsEntry("name", "After Update")
      assertThat(readProduct(id2)).containsEntry("name", "Untouched")
    }

  /** An update with a kotlinx `JsonObject` body sets the values that the object holds. */
  @Test
  fun updateWithAJsonObjectBody() =
    runBlocking<Unit> {
      val (id) = insertProducts(product("Before Json Update"))

      fsql
        .from("products")
        .update(buildJsonObject { put("name", "After Json Update") })
        .eq("id", id)
        .executeOrFail()

      assertThat(readProduct(id)).containsEntry("name", "After Json Update")
    }

  /** An upsert inserts a new row, then overwrites it. */
  @Test
  fun upsertInsertsThenUpdatesTheRow() =
    runBlocking<Unit> {
      val id = generateId()
      fsql
        .from("products")
        .upsert(mapOf("id" to id, "name" to "Upsert 1", "is_active" to false), onConflict = "id")
        .executeOrFail()
      assertThat(readProduct(id, "name, is_active"))
        .containsExactly("name", "Upsert 1", "is_active", false)

      fsql
        .from("products")
        .upsert(mapOf("id" to id, "name" to "Upsert 2", "is_active" to true), onConflict = "id")
        .executeOrFail()
      assertThat(readProduct(id, "name, is_active"))
        .containsExactly("name", "Upsert 2", "is_active", true)
    }

  /**
   * An upsert without `onConflict` resolves conflicts on the primary key, which PostgREST does when
   * the request has no `on_conflict` parameter. An empty `on_conflict=` fails to parse (PGRST100).
   */
  @Test
  fun upsertWithoutOnConflictUsesThePrimaryKey() =
    runBlocking<Unit> {
      val id = generateId()
      fsql.from("products").upsert(mapOf("id" to id, "name" to "Upsert Default 1")).executeOrFail()
      fsql.from("products").upsert(mapOf("id" to id, "name" to "Upsert Default 2")).executeOrFail()

      assertThat(readProduct(id)).containsEntry("name", "Upsert Default 2")
    }

  /** `ignoreDuplicates` keeps the existing row and returns no row for it. */
  @Test
  fun upsertWithIgnoreDuplicatesKeepsTheExistingRow() =
    runBlocking<Unit> {
      val (id) = insertProducts(product("Original Product"))

      val result =
        fsql
          .from("products")
          .upsert(
            mapOf("id" to id, "name" to "Ignored Product", "is_active" to true),
            onConflict = "id",
            ignoreDuplicates = true
          )
          .select()
          .executeOrFail()

      // ON CONFLICT DO NOTHING returns no row for the ignored duplicate.
      assertThat(result.data).isEqualTo("[]")
      assertThat(readProduct(id)).containsEntry("name", "Original Product")
    }

  /**
   * Same as above for an upsert with `defaultToNull = false`. With `Prefer: missing=default`,
   * PostgREST uses the column default for a missing key, which is NULL for these columns.
   */
  @Test
  fun upsertOfAListWithDifferentKeysUsesTheColumnDefaults() =
    runBlocking<Unit> {
      val id1 = generateId()
      val id2 = generateId()
      fsql
        .from("products")
        .upsert(
          listOf(
            mapOf("id" to id1, "name" to "Bulk Upsert Full", "is_active" to true),
            mapOf("id" to id2, "name" to "Bulk Upsert Partial")
          ),
          onConflict = "id",
          defaultToNull = false
        )
        .executeOrFail()

      assertThat(readProduct(id1, "name, is_active"))
        .containsExactly("name", "Bulk Upsert Full", "is_active", true)
      assertThat(readProduct(id2, "name, is_active"))
        .containsExactly("name", "Bulk Upsert Partial", "is_active", null)
    }

  /** A delete removes the matched row, and returns it with `select()`. */
  @Test
  fun deleteRemovesTheRowAndReturnsItWhenAsked() =
    runBlocking<Unit> {
      val (id1, id2) = insertProducts(product("Delete Rep Product"), product("Delete Me"))

      val withRepresentation = fsql.from("products").delete().eq("id", id1).select().executeOrFail()
      val withoutRepresentation = fsql.from("products").delete().eq("id", id2).executeOrFail()

      assertThat(names(withRepresentation)).containsExactly("Delete Rep Product")
      assertThat(withoutRepresentation.data).isEmpty()
      assertThat(readProduct(id1)).isNull()
      assertThat(readProduct(id2)).isNull()
    }

  /**
   * `insert`, `upsert`, and `update` write a `@Serializable` object with the serializer that the
   * compiler finds for its type.
   */
  @Test
  fun serializableObjectsAreWrittenWithTheSerializerOfTheirType() =
    runBlocking<Unit> {
      val id1 = generateId()
      val id2 = generateId()

      fsql.from("products").insert(TestProduct(id1, "Typed Insert")).executeOrFail()
      fsql.from("products").insert(listOf(TestProduct(id2, "Typed List"))).executeOrFail()
      fsql
        .from("products")
        .upsert(TestProduct(id1, "Typed Upsert"), onConflict = "id")
        .executeOrFail()
      fsql.from("products").update(TestProduct(id2, "Typed Update")).eq("id", id2).executeOrFail()

      val rows =
        fsql
          .from("products")
          .select("id, name")
          .isIn("id", listOf(id1, id2))
          .order("id")
          .executeOrFail()
          .decodeList<TestProduct>()
      assertThat(rows)
        .containsExactly(TestProduct(id1, "Typed Upsert"), TestProduct(id2, "Typed Update"))
        .inOrder()
    }

  /** An embedded resource is read through the foreign key. */
  @Test
  fun selectEmbedsTheReferencedRow() =
    runBlocking<Unit> {
      val catId = generateId()
      fsql.from("categories").insert(mapOf("id" to catId, "name" to "Electronics")).executeOrFail()
      val (prodId) = insertProducts(product("Smartphone", "category_id" to catId))

      val result =
        fsql.from("products").select("name, categories(name)").eq("id", prodId).single().execute()

      assertThat(result.rowMaps())
        .containsExactly(
          mapOf("name" to "Smartphone", "categories" to mapOf("name" to "Electronics"))
        )
    }

  /**
   * A multi-line select string works. PostgREST only skips spaces and tabs in `select`: it ignores
   * a select that starts with a line break (every column comes back and the embedded resource is
   * missing), and rejects a line break after a comma with PGRST100.
   */
  @Test
  fun selectWithMultilineColumns() =
    runBlocking<Unit> {
      val catId = generateId()
      fsql
        .from("categories")
        .insert(mapOf("id" to catId, "name" to "Multiline Category"))
        .executeOrFail()
      val (prodId) = insertProducts(product("Multiline Product", "category_id" to catId))

      val result =
        fsql
          .from("products")
          .select("""
            name,
            categories(name)
            """)
          .eq("id", prodId)
          .executeOrFail()

      assertThat(result.rowMaps())
        .containsExactly(
          mapOf(
            "name" to "Multiline Product",
            "categories" to mapOf("name" to "Multiline Category")
          )
        )
    }

  // --- Filters ---

  /** Each comparison operator matches exactly the expected rows, and excludes the others. */
  @Test
  fun comparisonOperatorsMatchExactlyTheExpectedRows() =
    runBlocking<Unit> {
      val ids = insertProducts(product("A_Product"), product("B_Product"), product("C_Product"))
      val (a, b, c) = ids

      suspend fun check(
        label: String,
        expected: List<Int>,
        filter: FilterBuilder.() -> FilterBuilder
      ) {
        assertWithMessage(label).that(idsMatching(ids, filter)).containsExactlyElementsIn(expected)
      }

      check("eq id", listOf(a)) { eq("id", a) }
      check("neq id", listOf(b, c)) { neq("id", a) }
      check("eq name", listOf(b)) { eq("name", "B_Product") }
      check("neq name", listOf(a, c)) { neq("name", "B_Product") }
      check("gt", listOf(b, c)) { gt("name", "A_Product") }
      check("gte", listOf(b, c)) { gte("name", "B_Product") }
      check("lt", listOf(a, b)) { lt("name", "C_Product") }
      check("lte", listOf(a, b)) { lte("name", "B_Product") }
    }

  /** `like` is case sensitive and `ilike` is not. */
  @Test
  fun likeAndIlike() =
    runBlocking<Unit> {
      val ids = insertProducts(product("HelloWorld"), product("Other"))

      assertThat(idsMatching(ids) { like("name", "%World") }).containsExactly(ids[0])
      assertThat(idsMatching(ids) { like("name", "%world") }).isEmpty()
      assertThat(idsMatching(ids) { ilike("name", "%world") }).containsExactly(ids[0])
    }

  /** `exact` uses the PostgREST `is` operator for true, false and null. */
  @Test
  fun exactMatchesBooleansAndNull() =
    runBlocking<Unit> {
      val ids =
        insertProducts(
          product("Is True", "is_active" to true),
          product("Is Null", "is_active" to null),
          product("Is False", "is_active" to false)
        )

      assertThat(idsMatching(ids) { exact("is_active", true) }).containsExactly(ids[0])
      assertThat(idsMatching(ids) { exact("is_active", null) }).containsExactly(ids[1])
      assertThat(idsMatching(ids) { exact("is_active", false) }).containsExactly(ids[2])
    }

  /**
   * `isIn`, `notIn`, and `filter()` and `filterNot()` with `FilterOperator.IN` match plain values
   * and values that contain a comma or parentheses. PostgREST splits a list on every comma outside
   * double quotes, so `in.(Doe, John)` holds the two values `Doe` and ` John`, and the wrong row
   * comes back without an error. Such values must be sent quoted, as `in.("Doe, John")`.
   */
  @Test
  fun inAndNotInMatchPlainAndQuotedValues() =
    runBlocking<Unit> {
      val ids =
        insertProducts(
          product("Doe, John"),
          product("Doe"),
          product("In (Paren)"),
          product("In Plain")
        )
      val (comma, doe, paren, plain) = ids
      val quoted = listOf("Doe, John", "In (Paren)")

      suspend fun check(
        label: String,
        expected: List<Int>,
        filter: FilterBuilder.() -> FilterBuilder
      ) {
        assertWithMessage(label).that(idsMatching(ids, filter)).containsExactlyElementsIn(expected)
      }

      check("isIn plain", listOf(comma, doe)) { isIn("id", listOf(comma, doe)) }
      check("notIn plain", listOf(doe, paren, plain)) { notIn("id", listOf(comma)) }
      check("filter in plain", listOf(comma, plain)) {
        filter("id", FilterOperator.IN, listOf(comma, plain))
      }
      check("not in plain", listOf(doe, paren)) {
        filterNot("id", FilterOperator.IN, listOf(comma, plain))
      }
      check("isIn quoted", listOf(comma, paren)) { isIn("name", quoted) }
      check("isIn comma only", listOf(comma)) { isIn("name", listOf("Doe, John")) }
      check("notIn quoted", listOf(doe, plain)) { notIn("name", quoted) }
      check("notIn comma only", listOf(doe, paren, plain)) { notIn("name", listOf("Doe, John")) }
      check("filter in quoted", listOf(comma, paren)) { filter("name", FilterOperator.IN, quoted) }
      check("not in quoted", listOf(doe, plain)) { filterNot("name", FilterOperator.IN, quoted) }
      check("not eq", listOf(comma, paren, plain)) { filterNot("name", FilterOperator.EQ, "Doe") }
    }

  /** `or()` combines with the other filters as `AND (… OR …)`. */
  @Test
  fun orCombinesWithTheOtherFilters() =
    runBlocking<Unit> {
      val ids =
        insertProducts(
          product("Cheap Active", "is_active" to true, "description" to "Group A"),
          product("Expensive Inactive", "is_active" to false, "description" to "Group B"),
          product("Cheap Inactive", "is_active" to false, "description" to "Group A"),
          // Only the `or` excludes this row.
          product("Pricey Active", "is_active" to true, "description" to "Group A")
        )

      // Query: description == 'Group A' AND (is_active == false OR name == 'Cheap Active')
      val result =
        fsql
          .from("products")
          .select("name")
          .isIn("id", ids)
          .eq("description", "Group A")
          .or("is_active.eq.false,name.eq.Cheap Active")
          .order("name")
          .executeOrFail()

      assertThat(names(result)).containsExactly("Cheap Active", "Cheap Inactive").inOrder()
    }

  /**
   * `or()` with `referencedTable` filters the embedded resource: the embedded category is null when
   * it does not match the conditions.
   */
  @Test
  fun orOnAReferencedTableFiltersTheEmbeddedResource() =
    runBlocking<Unit> {
      val catId = generateId()
      fsql.from("categories").insert(mapOf("id" to catId, "name" to "Or Category")).executeOrFail()
      val (prodId) = insertProducts(product("Or Product", "category_id" to catId))

      suspend fun read(conditions: String): List<Map<String, Any?>> =
        fsql
          .from("products")
          .select("name, categories(name)")
          .eq("id", prodId)
          .or(conditions, referencedTable = "categories")
          .executeOrFail()
          .rowMaps()

      assertThat(read("name.eq.Nope,name.eq.Other"))
        .containsExactly(mapOf("name" to "Or Product", "categories" to null))
      assertThat(read("name.eq.Nope,name.eq.Or Category"))
        .containsExactly(
          mapOf("name" to "Or Product", "categories" to mapOf("name" to "Or Category"))
        )
    }

  /** Array and range filters on plain values, with a range literal that contains a space. */
  @Test
  fun arrayAndRangeFiltersOnPlainValues() =
    runBlocking<Unit> {
      val ids =
        insertProducts(
          product("Advanced 1", "tags" to listOf("sale", "winter"), "price_range" to "[10, 50)"),
          product(
            "Advanced 2",
            "tags" to listOf("clearance", "summer"),
            "price_range" to "[50, 100)"
          )
        )

      assertThat(namesMatching(ids) { contains("tags", listOf("winter")) })
        .containsExactly("Advanced 1")
      assertThat(namesMatching(ids) { overlaps("tags", listOf("summer", "spring")) })
        .containsExactly("Advanced 2")
      // [10, 50) is strictly left of [50, 100).
      assertThat(namesMatching(ids) { rangeLt("price_range", "[50, 100)") })
        .containsExactly("Advanced 1")
    }

  /**
   * Array filter values that contain a comma or a space are quoted in the PostgreSQL array literal.
   * Unquoted, `{a,b}` is the array of `a` and `b`.
   */
  @Test
  fun arrayFiltersWithCommaAndSpaceInValues() =
    runBlocking<Unit> {
      val ids =
        insertProducts(
          product("Array A", "tags" to listOf("a,b", "c d")),
          product("Array B", "tags" to listOf("a", "b"))
        )

      assertThat(namesMatching(ids) { contains("tags", listOf("a,b")) }).containsExactly("Array A")
      assertThat(namesMatching(ids) { contained("tags", listOf("a,b", "c d", "e")) })
        .containsExactly("Array A")
      assertThat(namesMatching(ids) { overlaps("tags", listOf("c d", "x")) })
        .containsExactly("Array A")
      assertThat(namesMatching(ids) { contains("tags", listOf("a", "b")) })
        .containsExactly("Array B")
    }

  /** The range filters use the PostgreSQL range operators. */
  @Test
  fun rangeFilters() =
    runBlocking<Unit> {
      val ids =
        insertProducts(
          product("Range A", "price_range" to "[10,50)"),
          product("Range B", "price_range" to "[50,100)")
        )

      assertThat(namesMatching(ids) { rangeGt("price_range", "[1,5)") })
        .containsExactly("Range A", "Range B")
      assertThat(namesMatching(ids) { rangeLt("price_range", "[50,100)") })
        .containsExactly("Range A")
      assertThat(namesMatching(ids) { rangeGte("price_range", "[10,20)") })
        .containsExactly("Range A", "Range B")
      assertThat(namesMatching(ids) { rangeLte("price_range", "[40,60)") })
        .containsExactly("Range A")
      assertThat(namesMatching(ids) { adjacent("price_range", "[50,60)") })
        .containsExactly("Range A")
      assertThat(namesMatching(ids) { filter("price_range", FilterOperator.OV, "[40,60)") })
        .containsExactly("Range A", "Range B")
    }

  /** Like patterns that contain a comma are quoted in the PostgreSQL array literal. */
  @Test
  fun quantifiedLikeFilters() =
    runBlocking<Unit> {
      val ids = insertProducts(product("Doe, John"), product("Doe"))

      assertThat(namesMatching(ids) { likeAny("name", listOf("%Doe, J%", "%xyz%")) })
        .containsExactly("Doe, John")
      assertThat(namesMatching(ids) { likeAll("name", listOf("Doe*", "*John")) })
        .containsExactly("Doe, John")
      assertThat(namesMatching(ids) { ilikeAny("name", listOf("doe", "%XYZ%")) })
        .containsExactly("Doe")
      assertThat(namesMatching(ids) { ilikeAll("name", listOf("doe%", "%, j%")) })
        .containsExactly("Doe, John")
    }

  /** The `_ANY` and `_ALL` operators compare with each element of a list. */
  @Test
  fun quantifiedFilterOperators() =
    runBlocking<Unit> {
      val ids = insertProducts(product("Alpha"), product("Bravo"), product("Charlie"))

      assertThat(namesMatching(ids) { filter("id", FilterOperator.EQ_ANY, listOf(ids[0], ids[2])) })
        .containsExactly("Alpha", "Charlie")
      assertThat(namesMatching(ids) { filter("name", FilterOperator.EQ_ANY, listOf("Bravo", "x")) })
        .containsExactly("Bravo")
      assertThat(namesMatching(ids) { filter("name", FilterOperator.GT_ALL, listOf("A", "Bravo")) })
        .containsExactly("Charlie")
      assertThat(namesMatching(ids) { filter("name", FilterOperator.LT_ANY, listOf("A", "Bravo")) })
        .containsExactly("Alpha")
      assertThat(
          namesMatching(ids) { filter("name", FilterOperator.MATCH_ALL, listOf("^C", "e$")) }
        )
        .containsExactly("Charlie")
      assertThat(
          namesMatching(ids) { filter("name", FilterOperator.IMATCH_ANY, listOf("^a", "^b")) }
        )
        .containsExactly("Alpha", "Bravo")
      assertThat(
          namesMatching(ids) {
            filterNot("name", FilterOperator.EQ_ANY, listOf("Alpha", "Charlie"))
          }
        )
        .containsExactly("Bravo")
    }

  /** `regexMatch` and `regexIMatch` use the PostgreSQL `~` and `~*` operators. */
  @Test
  fun regexMatchAndRegexIMatch() =
    runBlocking<Unit> {
      val ids = insertProducts(product("Doe, John"), product("Doe"))

      assertThat(namesMatching(ids) { regexMatch("name", "^Doe, J[a-z]+$") })
        .containsExactly("Doe, John")
      assertThat(namesMatching(ids) { regexIMatch("name", "^doe$") }).containsExactly("Doe")
    }

  /** `isDistinct` matches the rows whose value is different, including NULL values. */
  @Test
  fun isDistinctMatchesNullValues() =
    runBlocking<Unit> {
      val ids =
        insertProducts(
          product("Distinct A", "description" to "same"),
          product("Distinct B", "description" to null)
        )

      assertThat(namesMatching(ids) { isDistinct("description", "same") })
        .containsExactly("Distinct B")
    }

  /** Each text search type uses its PostgREST operator. */
  @Test
  fun textSearchTypes() =
    runBlocking<Unit> {
      val ids = insertProducts(product("Search Product", "search_vector" to "'quick':1 'fox':2"))

      assertThat(idsMatching(ids) { textSearch("search_vector", "fox", TextSearchType.NONE) })
        .containsExactlyElementsIn(ids)
      assertThat(
          idsMatching(ids) { textSearch("search_vector", "quick fox", TextSearchType.PLAINTO) }
        )
        .containsExactlyElementsIn(ids)
      assertThat(
          idsMatching(ids) {
            textSearch("search_vector", "quick fox", TextSearchType.PHRASETO, "english")
          }
        )
        .containsExactlyElementsIn(ids)
      assertThat(
          idsMatching(ids) {
            textSearch("search_vector", "quick fox", TextSearchType.WEBSEARCH, "english")
          }
        )
        .containsExactlyElementsIn(ids)
      assertThat(
          idsMatching(ids) {
            textSearch("search_vector", "quick -fox", TextSearchType.WEBSEARCH, "english")
          }
        )
        .isEmpty()
    }

  /**
   * `&`, `=`, `+`, `%`, `#`, `?`, `/`, spaces, and non-ASCII characters must be percent-encoded in
   * the query string. Otherwise PostgREST splits the parameter, decodes `+` as a space, or never
   * receives the part after `#`, and the filter does not match the row.
   */
  @Test
  fun filterValuesWithReservedCharactersAreEncoded() =
    runBlocking<Unit> {
      val weirdName = "Tom & Jerry ? 50% Off / clearance + exactly=true #1 café"
      val (id) = insertProducts(product(weirdName, "is_active" to true))

      // Filtering on the id too keeps the test independent of rows left by earlier runs.
      val result =
        fsql
          .from("products")
          .select("id, name")
          .eq("id", id)
          .eq("name", weirdName)
          .single()
          .execute()
      assertThat(result.decodeSingle<TestProduct>()).isEqualTo(TestProduct(id, weirdName))
    }

  // --- Modifiers ---

  /** `order`, `limit`, and `range` select exactly one row. */
  @Test
  fun limitAndRangeSelectOneRowOfTheOrderedRows() =
    runBlocking<Unit> {
      val ids = insertProducts(product("Product A"), product("Product B"), product("Product C"))

      suspend fun page(offset: Long?): List<Any?> {
        val ordered =
          fsql.from("products").select("id, name").isIn("id", ids).order("name", Order.DESCENDING)
        return names((offset?.let { ordered.range(it, it) } ?: ordered.limit(1)).execute())
      }

      assertThat(page(offset = null)).containsExactly("Product C")
      assertThat(page(offset = 1)).containsExactly("Product B")
    }

  /**
   * A filter after `order()`, `limit()`, and `setHeader()` returns the same rows as before them:
   * the database filters the rows before it sorts them and applies the limit.
   */
  @Test
  fun filtersAfterModifiersReturnTheSameRowsAsBeforeThem() =
    runBlocking<Unit> {
      val ids =
        insertProducts(
          product("Order D", "is_active" to true),
          product("Order C", "is_active" to false),
          product("Order B", "is_active" to true),
          product("Order A", "is_active" to true)
        )

      val filtersFirst =
        fsql
          .from("products")
          .select("id, name")
          .isIn("id", ids)
          .eq("is_active", true)
          .order("name", Order.DESCENDING)
          .limit(2)
          .executeOrFail()
      val filtersLast =
        fsql
          .from("products")
          .select("id, name")
          .order("name", Order.DESCENDING)
          .limit(2)
          .setHeader("X-Request-Source", "e2e")
          .isIn("id", ids)
          .eq("is_active", true)
          .executeOrFail()

      // Filtering after the limit would leave only "Order D".
      assertThat(names(filtersFirst)).containsExactly("Order D", "Order B").inOrder()
      assertThat(names(filtersLast)).containsExactly("Order D", "Order B").inOrder()
    }

  /** A HEAD select returns only the count, which is 0 when no row matches. */
  @Test
  fun headWithCountReturnsOnlyTheCount() =
    runBlocking<Unit> {
      val ids = insertProducts(product("Head Product 1"), product("Head Product 2"))

      val result =
        fsql
          .from("products")
          .select(head = true, count = Count.EXACT)
          .isIn("id", ids)
          .executeOrFail()
      assertThat(result.data).isEmpty()
      assertThat(result.countOrNull()).isEqualTo(2)

      // PostgREST sends `Content-Range: */0` when no row matches.
      val empty =
        fsql.from("products").select(head = true, count = Count.EXACT).eq("id", -1).executeOrFail()
      assertThat(empty.countOrNull()).isEqualTo(0)
    }

  /** `maybeSingle()` on a read returns null for 0 rows, the row for 1 row, and fails for 2 rows. */
  @Test
  fun maybeSingleReadReturnsNullOrTheRowAndFailsForSeveralRows() =
    runBlocking<Unit> {
      val missingId = generateId() // Not inserted.
      val (oneId) = insertProducts(product("Maybe Product"))
      val duplicates = insertProducts(product("Duplicate Name"), product("Duplicate Name"))

      suspend fun maybeSingle(ids: List<Int>): OperationResult =
        fsql.from("products").select("id, name").isIn("id", ids).maybeSingle().execute()

      assertThat(maybeSingle(listOf(missingId)).data).isEqualTo("null")
      assertThat(maybeSingle(listOf(oneId)).rowMaps())
        .containsExactly(mapOf("id" to oneId.toLong(), "name" to "Maybe Product"))
      val exception = runCatching { maybeSingle(duplicates) }.exceptionOrNull()
      assertThat(exception).isInstanceOf(FirebaseSqlException::class.java)
      assertThat((exception as FirebaseSqlException).code).isEqualTo("PGRST116")
    }

  /**
   * `maybeSingle()` on an update that matches no row returns null. For a mutation, the SDK asks
   * PostgREST for a single object, and PostgREST answers 406 (PGRST116) when no row is affected.
   */
  @Test
  fun maybeSingleUpdateWithoutMatchingRowReturnsNull() =
    runBlocking<Unit> {
      val result =
        fsql
          .from("products")
          .update(mapOf("name" to "Nobody"))
          .eq("id", -1)
          .select()
          .maybeSingle()
          .executeOrFail()

      assertThat(result.data).isEqualTo("null")
    }

  /**
   * `maybeSingle()` on an update that matches more than one row fails, and PostgREST rolls the
   * update back.
   */
  @Test
  fun maybeSingleUpdateOfSeveralRowsFailsAndRollsBack() =
    runBlocking<Unit> {
      val (id1, id2) = insertProducts(product("Maybe A"), product("Maybe B"))

      val error =
        fsql
          .from("products")
          .update(mapOf("name" to "Changed"))
          .isIn("id", listOf(id1, id2))
          .select()
          .maybeSingle()
          .executeAndGetError()
      assertThat(error.code).isEqualTo("PGRST116")
      assertThat(error.details).isEqualTo("The result contains 2 rows")
      assertThat(readProduct(id1)).containsEntry("name", "Maybe A")
      assertThat(readProduct(id2)).containsEntry("name", "Maybe B")
    }

  /**
   * `stripNulls()` removes null fields from lists, from `single()` objects, and from
   * `maybeSingle()` results, which the SDK reduces to one row on the client. PostgREST only strips
   * nulls for the `application/vnd.pgrst.array+json` and `application/vnd.pgrst.object+json` media
   * types, and ignores `nulls=stripped` on `application/json`.
   */
  @Test
  fun stripNullsRemovesNullFieldsFromEachResponseShape() =
    runBlocking<Unit> {
      val (id) = insertProducts(product("Strip Nulls"))
      for (shape in listOf("list", "single", "maybeSingle")) {
        val query = fsql.from("products").select("id, name, description").eq("id", id)
        val shaped: TransformBuilder =
          when (shape) {
            "single" -> query.single()
            "maybeSingle" -> query.maybeSingle()
            else -> query
          }
        val result = shaped.stripNulls().executeOrFail()

        assertWithMessage(shape)
          .that(result.rowMaps())
          .containsExactly(mapOf("id" to id.toLong(), "name" to "Strip Nulls"))
      }
    }

  /** `maxAffected()` makes PostgREST reject and roll back a delete that affects more rows. */
  @Test
  fun maxAffectedRejectsAndRollsBack() =
    runBlocking<Unit> {
      val (id1, id2) = insertProducts(product("Max A"), product("Max B"))

      val error =
        fsql
          .from("products")
          .delete()
          .isIn("id", listOf(id1, id2))
          .maxAffected(1)
          .executeAndGetError()

      assertThat(error.status).isEqualTo(400)
      assertThat(error.code).isEqualTo("PGRST124")
      assertThat(readProduct(id1)).isNotNull()
      assertThat(readProduct(id2)).isNotNull()
    }

  /** The count of an update is read from the `Content-Range` header of the 204 response. */
  @Test
  fun updateWithCountReturnsTheNumberOfUpdatedRows() =
    runBlocking<Unit> {
      val ids = insertProducts(product("Count A"), product("Count B"))

      val result =
        fsql
          .from("products")
          .update(mapOf("is_active" to true), count = Count.EXACT)
          .isIn("id", ids)
          .executeOrFail()

      assertThat(result.status).isEqualTo(204)
      assertThat(result.countOrNull()).isEqualTo(2L)
    }

  /**
   * `dryRun()` returns the inserted row without keeping it. Firebase SQL commits by default, so
   * e2e_setup.sql sets `pgrst.db_tx_end` to `commit-allow-override` in `postgrest.pre_config()`.
   */
  @Test
  fun dryRunDoesNotKeepTheInsertedRow() =
    runBlocking<Unit> {
      val id = generateId()

      val result =
        fsql
          .from("products")
          .insert(mapOf("id" to id, "name" to "Dry Run"))
          .select()
          .dryRun()
          .executeOrFail()

      assertThat(names(result)).containsExactly("Dry Run")
      assertThat(readProduct(id)).isNull()
    }

  /**
   * A custom `Prefer` header is sent together with the preferences that the SDK computes, so
   * PostgREST applies both the custom `tx=rollback` and the computed `return=representation`. Needs
   * the same `db-tx-end` setting as [dryRunDoesNotKeepTheInsertedRow].
   */
  @Test
  fun customPreferHeaderIsMergedWithTheComputedPreferences() =
    runBlocking<Unit> {
      val id = generateId()

      val result =
        fsql
          .from("products")
          .insert(mapOf("id" to id, "name" to "Merged Prefer"))
          .select()
          .setHeader("Prefer", "tx=rollback")
          .executeOrFail()

      assertThat(names(result)).containsExactly("Merged Prefer")
      assertThat(readProduct(id)).isNull()
    }

  /**
   * `explain()` returns the PostgreSQL plan. Firebase SQL turns plans off by default and answers
   * PGRST107, so e2e_setup.sql sets `pgrst.db_plan_enabled` to `true` in `postgrest.pre_config()`.
   */
  @Test
  fun testExplain() =
    runBlocking<Unit> {
      val json =
        fsql
          .from("products")
          .select()
          .eq("id", -1)
          .explain(format = ExplainFormat.JSON)
          .executeOrFail()
      val text =
        fsql.from("products").select().eq("id", -1).single().explain(verbose = true).executeOrFail()

      assertThat(json.data).contains("\"Plan\"")
      assertThat(text.data).contains("products")
    }

  // --- Response formats ---

  /** `csv()` returns a header line and one line per row, where PostgREST quotes text values. */
  @Test
  fun csvReturnsAHeaderAndOneLinePerRow() =
    runBlocking<Unit> {
      val (id) = insertProducts(product("CSV Product"))

      val result = fsql.from("products").select("id, name").eq("id", id).csv().executeOrFail()

      assertThat(result.data.trim().lines())
        .containsExactly("id,name", "$id,\"CSV Product\"")
        .inOrder()
    }

  // --- Functions ---

  /** A function is called with POST without and with arguments, and with GET. */
  @Test
  fun rpcCallsReturnTheResultOfTheFunction() =
    runBlocking<Unit> {
      val hello = fsql.rpc("hello_world").executeOrFail()
      assertThat(hello.decodeAs<String>()).isEqualTo("Hello from Cloud SQL!")

      val echo = fsql.rpc("echo_text", mapOf("input_text" to "RPC works")).executeOrFail()
      assertThat(echo.decodeAs<String>()).isEqualTo("RPC works")

      // GET RPC: the argument is sent in the query string.
      val echoGet =
        fsql
          .rpc("echo_text", mapOf("input_text" to "Tom & Jerry + 50%, (a)"), method = RpcMethod.GET)
          .executeOrFail()
      assertThat(echoGet.decodeAs<String>()).isEqualTo("Tom & Jerry + 50%, (a)")
    }

  /** The arguments of a function are encoded from a `@Serializable` object, for POST and GET. */
  @Test
  fun rpcTakesTheArgumentsAsASerializableObject() =
    runBlocking<Unit> {
      val post = fsql.rpc("echo_text", EchoArgs("Typed arguments")).executeOrFail()
      val get =
        fsql.rpc("echo_text", EchoArgs("Typed GET & more"), method = RpcMethod.GET).executeOrFail()

      assertThat(post.decodeAs<String>()).isEqualTo("Typed arguments")
      assertThat(get.decodeAs<String>()).isEqualTo("Typed GET & more")
    }

  /**
   * A list argument of a GET RPC is sent as a PostgreSQL array literal (`{a,b}`), which is the text
   * that PostgreSQL reads for an array parameter. `echo_text` returns the text that it received.
   */
  @Test
  fun rpcGetSendsAListArgumentAsAnArrayLiteral() =
    runBlocking<Unit> {
      val result =
        fsql
          .rpc("echo_text", mapOf("input_text" to listOf("a", "b")), method = RpcMethod.GET)
          .executeOrFail()

      assertThat(result.decodeAs<String>()).isEqualTo("{a,b}")
    }

  /**
   * `rpc(method = RpcMethod.HEAD, count = Count.EXACT)` returns the count when an argument is a
   * list or an object, which is sent as JSON text. PostgREST reads the arguments of a HEAD call
   * from the query string, like for GET, and returns the count in the `Content-Range` header.
   */
  @Test
  fun rpcHeadWithListOrObjectArgumentReturnsTheCount() =
    runBlocking<Unit> {
      for (argument in listOf<Any>(listOf("a", "b"), mapOf("k" to 1))) {
        val result =
          fsql
            .rpc(
              "echo_text",
              mapOf("input_text" to argument),
              method = RpcMethod.HEAD,
              count = Count.EXACT
            )
            .executeOrFail()

        assertWithMessage("$argument").that(result.countOrNull()).isEqualTo(1L)
      }
    }

  // --- Instances and errors ---

  @Test
  fun twoAppsReadTheRowsThatTheOtherWrote() =
    runBlocking<Unit> {
      val app2 = service.firebaseApp("secondaryApp")

      try {
        val fsql2 = service.sql(app2)
        assertThat(fsql2).isNotSameInstanceAs(fsql)

        val id1 = generateId()
        val id2 = generateId()

        // Instance 1 writes, Instance 2 writes
        fsql
          .from("products")
          .insert(mapOf("id" to id1, "name" to "Instance 1", "is_active" to true))
          .executeOrFail()
        fsql2
          .from("products")
          .insert(mapOf("id" to id2, "name" to "Instance 2", "is_active" to false))
          .executeOrFail()

        // Cross-read: Instance 2 reads Instance 1's write, Instance 1 reads Instance 2's write
        val response1 = fsql2.from("products").select("name").eq("id", id1).single().execute()
        val response2 = fsql.from("products").select("name").eq("id", id2).single().execute()

        assertThat(names(response1)).containsExactly("Instance 1")
        assertThat(names(response2)).containsExactly("Instance 2")
      } finally {
        app2.delete()
      }
    }

  @Test
  fun eachInstanceUsesItsOwnHost() =
    runBlocking<Unit> {
      // `fsql2` points to a second host where nothing listens, so its connection is refused
      // right away. Each instance must use its own host.
      val fsql2 =
        Firebase.sql(app, service.settings().copy(host = "127.0.0.1", port = 1, sslEnabled = false))

      // 1. The first instance reaches the real server.
      val (id1) = insertProducts(product("DB 1 Product", "is_active" to true))
      assertThat(readProduct(id1)).containsEntry("name", "DB 1 Product")

      // 2. The second instance fails with the IOException of the network.
      val error =
        runCatching { fsql2.from("products").select().eq("id", id1).execute() }.exceptionOrNull()
      assertThat(error).isInstanceOf(IOException::class.java)
    }

  /**
   * Needs an `inventory` schema with its own `products` table, exposed by PostgREST next to
   * `public`. A Firebase SQL service exposes only its own schema, its database roles cannot create
   * another one, and they cannot change `postgrest.pre_config()` to expose it.
   */
  @Ignore("The Firebase SQL service does not expose an inventory schema; to be investigated")
  @Test
  fun testMultipleSchemas() =
    runBlocking<Unit> {
      // `fsql` uses the schema of the service, `public`, and `inventoryFsql` the "inventory"
      // schema.
      val inventoryFsql = service.sql(app, schema = "inventory").also { inventoryFsql = it }
      val id1 = generateId()
      val id2 = generateId()

      // Insert into 'public.products'
      insertProducts(mapOf("id" to id1, "name" to "Public Product", "is_active" to true))

      // Insert into 'inventory.products'
      inventoryFsql
        .from("products")
        .insert(mapOf("id" to id2, "name" to "Inventory Product", "is_active" to true))
        .executeOrFail()

      // 1. Verify Public Schema Isolation
      val publicCheck = fsql.from("products").select("name").isIn("id", listOf(id1, id2)).execute()
      assertThat(names(publicCheck)).containsExactly("Public Product")

      // 2. Verify Tenant Schema Isolation
      val inventoryCheck =
        inventoryFsql.from("products").select("name").isIn("id", listOf(id1, id2)).execute()
      assertThat(names(inventoryCheck)).containsExactly("Inventory Product")
    }

  /**
   * PostgREST reads the schema from the `Accept-Profile` header of reads and from the
   * `Content-Profile` header of writes, and rejects a schema that is not in its `db-schemas` with
   * PGRST106.
   */
  @Test
  fun schemaThatTheServerDoesNotExposeFails() =
    runBlocking<Unit> {
      val hidden = service.sql(app, schema = "not_exposed")
      val id = generateId()

      val read = hidden.from("products").select().eq("id", id).executeAndGetError()
      val write =
        hidden.from("products").insert(mapOf("id" to id, "name" to "Hidden")).executeAndGetError()

      for (error in listOf(read, write)) {
        assertThat(error.status).isEqualTo(406)
        assertThat(error.code).isEqualTo("PGRST106")
      }
      assertThat(readProduct(id)).isNull()
    }

  /** A filter on an unknown column fails with 400 and the PostgreSQL code 42703. */
  @Test
  fun filterOnAnUnknownColumnFails() =
    runBlocking<Unit> {
      val error = fsql.from("products").select().eq("invalid_col", "val").executeAndGetError()

      assertThat(error.status).isEqualTo(400)
      assertThat(error.code).isEqualTo("42703")
    }

  /**
   * Concurrent requests all succeed: the connection pool does not deadlock or run out of threads.
   */
  @Test
  fun concurrentInsertsAndReadsAllSucceed() =
    runBlocking<Unit> {
      val ids = (1..CONCURRENT_REQUESTS).map { generateId() }

      coroutineScope {
        ids
          .map { id ->
            async(Dispatchers.IO) {
              insertProducts(mapOf("id" to id, "name" to "Concurrent $id", "is_active" to true))
            }
          }
          .awaitAll()
      }

      coroutineScope {
        ids
          .map { id ->
            async(Dispatchers.IO) {
              val result = fsql.from("products").select("name").eq("id", id).single().execute()
              assertThat(names(result)).containsExactly("Concurrent $id")
            }
          }
          .awaitAll()
      }
    }

  // --- Helpers ---

  private enum class Season {
    WINTER
  }

  /**
   * Returns a new id, and remembers it so that [tearDown] deletes the rows with this id. The ids
   * start at a random base chosen once per run, so runs against the same database do not collide.
   */
  private fun generateId(): Int =
    (runIdBase + nextIdOffset.incrementAndGet()).also { createdIds.add(it) }

  /** Returns a product row with a new id from [generateId], the [name] and the other [columns]. */
  private fun product(name: String, vararg columns: Pair<String, Any?>): Map<String, Any?> =
    mapOf("id" to generateId(), "name" to name) + columns

  /**
   * Inserts the [rows] into `products` and returns their ids, in order. A failed insert fails the
   * test with the PostgREST error. The ids must come from [generateId] so that they are cleaned up.
   */
  private suspend fun insertProducts(vararg rows: Map<String, Any?>): List<Int> {
    fsql.from("products").insert(rows.toList()).executeOrFail()
    return rows.map { (it.getValue("id") as Number).toInt() }
  }

  /**
   * Returns the rows of the result as maps of Kotlin values, to compare them with `mapOf(…)`: a
   * `Long` or a `Double` for a number, a `List` for an array, and a `Map` for an object. The object
   * of `single()` gives one map.
   */
  private fun OperationResult.rowMaps(): List<Map<String, Any?>> {
    if (data.isBlank()) return emptyList()
    return when (val element = decodeAs<JsonElement>()) {
      is JsonArray -> element.map { (it as JsonObject).toKotlinMap() }
      is JsonObject -> listOf(element.toKotlinMap())
      else -> emptyList()
    }
  }

  private fun JsonElement.toKotlinValue(): Any? =
    when (this) {
      is JsonNull -> null
      is JsonPrimitive ->
        if (isString) content else booleanOrNull ?: longOrNull ?: doubleOrNull ?: content
      is JsonArray -> map { it.toKotlinValue() }
      is JsonObject -> toKotlinMap()
    }

  private fun JsonObject.toKotlinMap(): Map<String, Any?> = mapValues { (_, value) ->
    value.toKotlinValue()
  }

  /** Returns the `name` of each row of the result, in order. */
  private fun names(result: OperationResult): List<Any?> = result.rowMaps().map { it["name"] }

  /** Returns the `id` of each row of the result, in order. */
  private fun ids(result: OperationResult): List<Int> =
    result.rowMaps().map { (it.getValue("id") as Number).toInt() }

  /** Returns the ids of the products among [ids] that the filter matches. */
  private suspend fun idsMatching(
    ids: List<Int>,
    applyFilter: FilterBuilder.() -> FilterBuilder
  ): List<Int> = ids(fsql.from("products").select("id").isIn("id", ids).applyFilter().execute())

  /** Returns the names of the products among [ids] that the filter matches, ordered by name. */
  private suspend fun namesMatching(
    ids: List<Int>,
    applyFilter: FilterBuilder.() -> FilterBuilder
  ): List<Any?> =
    names(
      fsql.from("products").select("name").isIn("id", ids).applyFilter().order("name").execute()
    )

  /** Reads back one product as a column-to-value map, or null if there is no such product. */
  private suspend fun readProduct(id: Int, columns: String = "id, name"): Map<String, Any?>? =
    fsql.from("products").select(columns).eq("id", id).executeOrFail().rowMaps().singleOrNull()

  /**
   * Deletes the rows whose id came from [generateId], so that rows do not pile up in the shared
   * database from one run to the next. Other rows are never deleted. A delete that fails is logged
   * and does not change the result of the test.
   */
  private suspend fun deleteCreatedRows() {
    val ids = synchronized(createdIds) { createdIds.toList() }
    // `products.category_id` references `categories`, so the products are deleted first.
    val tables =
      listOfNotNull(
        fsql to "products",
        fsql to "categories",
        inventoryFsql?.let { it to "products" }
      )
    for ((client, table) in tables) {
      for (chunk in ids.chunked(MAX_IDS_PER_DELETE)) {
        try {
          client.from(table).delete().isIn("id", chunk).execute()
        } catch (e: FirebaseSqlException) {
          Log.w(TAG, "Could not delete the test rows of $table: ${e.message}")
        } catch (e: IOException) {
          Log.w(TAG, "Could not delete the test rows of $table: ${e.message}")
        }
      }
    }
  }

  /**
   * Executes the request and returns the result. If the request fails, fails the test with the HTTP
   * status and the PostgREST error, so the cause shows up in the test report.
   */
  private suspend fun TransformBuilder.executeOrFail(): OperationResult =
    try {
      execute()
    } catch (e: FirebaseSqlException) {
      throw AssertionError(
        "HTTP ${e.status ?: 0} ${e.code}: ${e.message}" + (e.details?.let { " ($it)" } ?: ""),
        e
      )
    }

  /** Executes the request, which must fail, and returns its error. */
  private suspend fun TransformBuilder.executeAndGetError(): FirebaseSqlException {
    val result =
      try {
        execute()
      } catch (e: FirebaseSqlException) {
        return e
      }
    throw AssertionError("The request succeeded with HTTP ${result.status}: ${result.data}")
  }

  private companion object {
    private const val TAG = "CloudSqlEndToEndTest"

    /** Keeps the `id=in.(...)` filter of a cleanup delete short. */
    private const val MAX_IDS_PER_DELETE = 100

    private const val CONCURRENT_REQUESTS = 50

    /**
     * The first id of this run, chosen once per run. Half of the positive `Int` range leaves room
     * for [nextIdOffset], so every id stays a positive `Int`.
     */
    private val runIdBase: Int = Random.nextInt(1, Int.MAX_VALUE / 2)

    /** Counts the ids of this run, from any thread. */
    private val nextIdOffset = AtomicInteger()
  }
}
