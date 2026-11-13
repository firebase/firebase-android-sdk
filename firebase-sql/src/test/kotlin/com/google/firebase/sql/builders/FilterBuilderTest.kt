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
import com.google.firebase.sql.FilterOperator
import com.google.firebase.sql.TextSearchType
import com.google.firebase.sql.constantsOf
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Tests the filter query parameters that [FilterBuilder] builds. The expected values follow how
 * PostgREST parses each operator.
 */
class FilterBuilderTest {

  private val executor = FakeOperationsExecutor()

  /** Builds a select request on `products` and returns its query parameters except `select`. */
  private fun filters(chain: FilterBuilder.() -> FilterBuilder): List<Pair<String, String>> {
    val query: QueryBuilder = OperationBuilderImpl("products", executor)
    return query.select().chain().toRequest().queryParams.filterNot { it.first == "select" }
  }

  // --- values ---

  @Test
  fun anEnumIsSentByItsName() {
    // A String sends another value, for example the @SerialName of the constant.
    assertThat(filters { eq("status", Status.IN_STOCK).neq("status", "sold_out") })
      .containsExactly("status" to "eq.IN_STOCK", "status" to "neq.sold_out")
      .inOrder()
  }

  @Test
  fun aSerializableObjectIsRejectedAndAJsonElementIsSentAsJson() {
    val error =
      assertThrows(IllegalArgumentException::class.java) {
        filters { eq("details", Details("red")) }
      }

    assertThat(error).hasMessageThat().contains("@Serializable")
    assertThat(filters { eq("details", buildJsonObject { put("color", "red") }) })
      .containsExactly("details" to """eq.{"color":"red"}""")
  }

  @Serializable
  enum class Status {
    @SerialName("in_stock") IN_STOCK
  }

  @Serializable data class Details(val color: String)

  // --- comparison operators ---

  @Test
  fun comparisonOperatorsSendTheValueAfterTheOperator() {
    assertThat(
        filters {
          eq("name", "Apple")
            .neq("status", "OFFLINE")
            .gt("age", 18)
            .gte("score", 9.5)
            .lt("level", 10L)
            .lte("rank", 5)
        }
      )
      .containsExactly(
        "name" to "eq.Apple",
        "status" to "neq.OFFLINE",
        "age" to "gt.18",
        "score" to "gte.9.5",
        "level" to "lt.10",
        "rank" to "lte.5"
      )
      .inOrder()
  }

  @Test
  fun stringValuesAreSentWithoutQuotingOrEncoding() {
    // PostgREST reads everything after `eq.` as the value, so no quoting is needed. URL encoding is
    // done by the network client.
    assertThat(
        filters {
          eq("name", "Doe, John (Jr.) \"x\"").eq("note", "a&b=c+d% e?").eq("label", "null")
        }
      )
      .containsExactly(
        "name" to "eq.Doe, John (Jr.) \"x\"",
        "note" to "eq.a&b=c+d% e?",
        "label" to "eq.null"
      )
      .inOrder()
  }

  @Test
  fun otherValuesAreSentAsTheirJsonText() {
    assertThat(
        filters {
          eq("is_active", true)
            .eq("season", Season.WINTER)
            .eq("initial", 'K')
            .eq("price", JsonPrimitive(3))
            .eq("label", JsonPrimitive("text"))
            .eq("description", JsonNull)
            .eq("uuid", UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))
            .eq("metadata", mapOf("origin" to "NZ", "organic" to true))
            .eq("tags", listOf("a", "b"))
        }
      )
      .containsExactly(
        "is_active" to "eq.true",
        "season" to "eq.WINTER",
        "initial" to "eq.K",
        "price" to "eq.3",
        "label" to "eq.text",
        "description" to "eq.null",
        "uuid" to "eq.123e4567-e89b-12d3-a456-426614174000",
        "metadata" to "eq.{\"origin\":\"NZ\",\"organic\":true}",
        "tags" to "eq.[\"a\",\"b\"]"
      )
      .inOrder()
  }

  @Test
  fun aDateFilterValueIsSentAsAnIso8601Timestamp() {
    val instant = Instant.parse("2026-10-08T04:50:06.123Z")

    assertThat(filters { gte("created_at", Date(instant.toEpochMilli())) })
      .containsExactly("created_at" to "gte.2026-10-08T04:50:06.123Z")
  }

  @Test
  fun aJavaTimeFilterValueIsRejectedAndItsStringIsSentAsIs() {
    val zonedDateTime = Instant.parse("2026-10-08T04:50:06.123Z").atZone(ZoneId.of("Asia/Kolkata"))

    assertThrows(IllegalArgumentException::class.java) {
      filters { lt("updated_at", zonedDateTime) }
    }
    assertThat(filters { lt("updated_at", zonedDateTime.toOffsetDateTime().toString()) })
      .containsExactly("updated_at" to "lt.2026-10-08T10:20:06.123+05:30")
  }

  @Test
  fun filtersOnTheSameColumnAreAllSent() {
    // PostgREST combines all filters with AND, including several filters on one column.
    assertThat(filters { gte("price", 10).lte("price", 20) })
      .containsExactly("price" to "gte.10", "price" to "lte.20")
      .inOrder()
  }

  @Test
  fun embeddedResourceColumnsUseDotNotation() {
    assertThat(filters { eq("categories.name", "Fruit") })
      .containsExactly("categories.name" to "eq.Fruit")
  }

  // --- pattern operators ---

  @Test
  fun likeAndIlikeSendThePatternAsIs() {
    assertThat(filters { like("name", "%Apple*").ilike("email", "%@gmail.com") })
      .containsExactly("name" to "like.%Apple*", "email" to "ilike.%@gmail.com")
      .inOrder()
  }

  @Test
  fun quantifiedLikeFiltersSendAnArrayLiteral() {
    assertThat(
        filters {
          likeAll("a", listOf("%x%", "%y%"))
            .likeAny("b", listOf("%x%", "%y%"))
            .ilikeAll("c", listOf("%x%", "%y%"))
            .ilikeAny("d", listOf("%x%", "%y%"))
        }
      )
      .containsExactly(
        "a" to "like(all).{%x%,%y%}",
        "b" to "like(any).{%x%,%y%}",
        "c" to "ilike(all).{%x%,%y%}",
        "d" to "ilike(any).{%x%,%y%}"
      )
      .inOrder()
  }

  @Test
  fun quantifiedLikeFiltersQuotePatternsThatPostgresWouldSplit() {
    // PostgreSQL parses the value as an array literal, so an element with a comma, a brace, a
    // quote, a backslash, or whitespace must be quoted. An empty element or NULL must be quoted to
    // stay a string.
    assertThat(
        filters {
          likeAny(
            "name",
            listOf("%Doe, J%", "%a b%", "%{x}%", "%\"q\"%", "%\\%", "", "null", "%ok%")
          )
        }
      )
      .containsExactly(
        "name" to """like(any).{"%Doe, J%","%a b%","%{x}%","%\"q\"%","%\\%","","null",%ok%}"""
      )
  }

  @Test
  fun regexFiltersUseTheMatchAndImatchOperators() {
    assertThat(
        filters {
          regexMatch("name", "^[A-Z][a-z]+$").regexIMatch("email", "^[a-z0-9._%+-]+@domain\\.com$")
        }
      )
      .containsExactly(
        "name" to "match.^[A-Z][a-z]+$",
        "email" to "imatch.^[a-z0-9._%+-]+@domain\\.com$"
      )
      .inOrder()
  }

  // --- match, is, isdistinct ---

  @Test
  fun matchSendsOneEqualityFilterPerEntry() {
    assertThat(filters { match(mapOf("status" to "active", "age" to 30, "is_active" to true)) })
      .containsExactly("status" to "eq.active", "age" to "eq.30", "is_active" to "eq.true")
      .inOrder()
  }

  @Test
  fun exactSendsTheIsOperator() {
    assertThat(filters { exact("verified", true).exact("hidden", false).exact("deleted_at", null) })
      .containsExactly("verified" to "is.true", "hidden" to "is.false", "deleted_at" to "is.null")
      .inOrder()
  }

  @Test
  fun isDistinctSendsTheIsdistinctOperator() {
    assertThat(filters { isDistinct("status", "active").isDistinct("level", 3) })
      .containsExactly("status" to "isdistinct.active", "level" to "isdistinct.3")
      .inOrder()
  }

  // --- in ---

  @Test
  fun isInAndNotInSendAPostgrestList() {
    assertThat(filters { isIn("id", listOf(1, 2, 3)).notIn("status", listOf("banned", "deleted")) })
      .containsExactly("id" to "in.(1,2,3)", "status" to "not.in.(banned,deleted)")
      .inOrder()
  }

  @Test
  fun isInAndNotInWithOnlyAnEmptyStringCompareWithEq() {
    // PostgREST reads `in.("")` as an empty list: `isIn` would match no row and `notIn` every row.
    assertThat(
        filters {
          isIn("a", listOf(""))
            .notIn("b", listOf(""))
            .filter("c", FilterOperator.IN, listOf(""))
            .isIn("d", listOf("", "x"))
        }
      )
      .containsExactly("a" to "eq.", "b" to "not.eq.", "c" to "eq.", "d" to """in.("",x)""")
      .inOrder()
  }

  @Test
  fun columnsWithTheNameOfAPostgrestParameterAreQuoted() {
    // PostgREST reads these names as parameters: `limit=eq.5` would be dropped, and `order=eq.5`
    // would fail to parse.
    assertThat(
        filters {
          eq("limit", 5)
            .eq("order", 1)
            .gt("cities.offset", 2)
            .isIn("select", listOf(1))
            .match(mapOf("or" to true))
            .eq("ordered", 3)
        }
      )
      .containsExactly(
        "\"limit\"" to "eq.5",
        "\"order\"" to "eq.1",
        "cities.\"offset\"" to "gt.2",
        "\"select\"" to "in.(1)",
        "\"or\"" to "eq.true",
        "ordered" to "eq.3"
      )
      .inOrder()
  }

  @Test
  fun filterWithAListSendsAnArrayLiteralForArrayOperators() {
    assertThat(
        filters {
          filter("tags", FilterOperator.CS, listOf("a", "b c"))
            .filterNot("tags", FilterOperator.OV, listOf("x"))
            .likeAny("name", listOf("A%", "B%"))
            .filter("meta", FilterOperator.CS, mapOf("a" to 1))
        }
      )
      .containsExactly(
        "tags" to """cs.{a,"b c"}""",
        "tags" to "not.ov.{x}",
        "name" to "like(any).{A%,B%}",
        "meta" to """cs.{"a":1}"""
      )
      .inOrder()
  }

  @Test
  fun inListsQuoteValuesThatPostgrestWouldSplitOrTrim() {
    // PostgREST splits the list on commas and ends it at `)`. A quoted value can contain any
    // character, and `\` escapes the next one. Spaces inside a value are kept. PostgREST requires
    // `in.(...)` with parentheses, and rejects a JSON array `in.[...]` with PGRST100, so
    // `filter()` and `filterNot()` with `FilterOperator.IN` format a list the same way.
    val values =
      listOf("Doe, John", "(x)", "say \"hi\"", "back\\slash", " padded ", "inner space", "")
    val list = """("Doe, John","(x)","say \"hi\"","back\\slash"," padded ",inner space,"")"""

    assertThat(
        filters {
          isIn("name", values)
            .notIn("name", values)
            .filter("name", FilterOperator.IN, values)
            .filterNot("name", FilterOperator.IN, values)
            .filter("id", FilterOperator.IN, listOf(1, 2, 3))
            .filterNot("id", FilterOperator.IN, listOf(4, 5))
            .filter("empty_col", FilterOperator.IN, emptyList<String>())
            .filterNot("empty_col", FilterOperator.IN, emptyList<String>())
        }
      )
      .containsExactly(
        "name" to "in.$list",
        "name" to "not.in.$list",
        "name" to "in.$list",
        "name" to "not.in.$list",
        "id" to "in.(1,2,3)",
        "id" to "not.in.(4,5)",
        "empty_col" to "in.()",
        "empty_col" to "not.in.()"
      )
      .inOrder()
  }

  @Test
  fun isInWithAnEmptyListSendsAnEmptyList() {
    // `in.()` matches no rows, and `not.in.()` matches all rows.
    assertThat(filters { isIn("id", emptyList()).notIn("status", emptyList()) })
      .containsExactly("id" to "in.()", "status" to "not.in.()")
      .inOrder()
  }

  @Test
  fun isInSendsOtherValuesAsTheirJsonText() {
    assertThat(filters { isIn("value", listOf(1, 2.5, true, Season.WINTER, 'c')) })
      .containsExactly("value" to "in.(1,2.5,true,WINTER,c)")
  }

  // --- array operators ---

  @Test
  fun arrayFiltersSendAnArrayLiteral() {
    assertThat(
        filters {
          contains("tags", listOf("swift", "ios"))
            .contained("roles", listOf("admin", "user"))
            .overlaps("categories", listOf("tech", "mobile"))
        }
      )
      .containsExactly(
        "tags" to "cs.{swift,ios}",
        "roles" to "cd.{admin,user}",
        "categories" to "ov.{tech,mobile}"
      )
      .inOrder()
  }

  @Test
  fun arrayFiltersQuoteElementsThatPostgresWouldSplit() {
    assertThat(
        filters {
          contains(
              "tags",
              listOf("a,b", "c d", "{x}", "q\"uote", "back\\slash", "", "NULL", "plain")
            )
            .contains("notes", listOf("tab\there", "line\nbreak"))
        }
      )
      .containsExactly(
        "tags" to """cs.{"a,b","c d","{x}","q\"uote","back\\slash","","NULL",plain}""",
        "notes" to "cs.{\"tab\there\",\"line\nbreak\"}"
      )
      .inOrder()
  }

  @Test
  fun arrayFiltersEncodeNumbersNullsNestedListsAndObjects() {
    assertThat(
        filters {
          contains("ints", listOf(1, 2))
            .contains("matrix", listOf(listOf(1, 2), listOf(3, 4)))
            .contains("mixed", listOf(JsonNull, true, 2.5))
            .contains("docs", listOf(mapOf("a" to 1)))
        }
      )
      .containsExactly(
        "ints" to "cs.{1,2}",
        "matrix" to "cs.{{1,2},{3,4}}",
        "mixed" to "cs.{NULL,true,2.5}",
        "docs" to """cs.{"{\"a\":1}"}"""
      )
      .inOrder()
  }

  // --- range operators ---

  @Test
  fun rangeOperatorsSendTheRangeAsIs() {
    assertThat(
        filters {
          rangeLt("during1", "[2026-01-01,2026-01-15)")
            .rangeGt("during2", "[2026-02-01,2026-02-15)")
            .rangeGte("during3", "[2026-03-01,2026-03-15)")
            .rangeLte("during4", "[2026-04-01,2026-04-15)")
            .adjacent("during5", "[2026-05-01,2026-05-15)")
        }
      )
      .containsExactly(
        "during1" to "sl.[2026-01-01,2026-01-15)",
        "during2" to "sr.[2026-02-01,2026-02-15)",
        "during3" to "nxl.[2026-03-01,2026-03-15)",
        "during4" to "nxr.[2026-04-01,2026-04-15)",
        "during5" to "adj.[2026-05-01,2026-05-15)"
      )
      .inOrder()
  }

  // --- full-text search ---

  @Test
  fun textSearchMapsTheTypeToAnOperator() {
    assertThat(
        filters {
          textSearch("a", "swift concurrency", TextSearchType.PLAINTO, config = "english")
            .textSearch("b", "swift concurrency", TextSearchType.PHRASETO)
            .textSearch("c", "swift -objc", TextSearchType.WEBSEARCH, config = "english")
            .textSearch("d", "'swift' & 'actor'", TextSearchType.NONE)
        }
      )
      .containsExactly(
        "a" to "plfts(english).swift concurrency",
        "b" to "phfts.swift concurrency",
        "c" to "wfts(english).swift -objc",
        "d" to "fts.'swift' & 'actor'"
      )
      .inOrder()
  }

  @Test
  fun textSearchRejectsConfigurationsThatAreNotNames() {
    for (config in listOf("pg_catalog.english", "en-us", "")) {
      assertThrows(config, IllegalArgumentException::class.java) {
        filters { textSearch("a", "swift", TextSearchType.NONE, config) }
      }
    }
  }

  // --- FilterOperator forms ---

  @Test
  fun filterAndFilterNotTakeAFilterOperator() {
    assertThat(
        filters {
          filter("a", FilterOperator.EQ, 1)
            .filter("b", FilterOperator.IN, listOf(1, 2))
            .filter("c", FilterOperator.CS, listOf("x", "y"))
            .filter("d", FilterOperator.IS, null)
            .filter("e", FilterOperator.ISDISTINCT, "x")
            .filterNot("f", FilterOperator.IS, null)
            .filterNot("g", FilterOperator.IN, listOf(3))
            .filter("h", FilterOperator.PLFTS, "swift")
        }
      )
      .containsExactly(
        "a" to "eq.1",
        "b" to "in.(1,2)",
        "c" to "cs.{x,y}",
        "d" to "is.null",
        "e" to "isdistinct.x",
        "f" to "not.is.null",
        "g" to "not.in.(3)",
        "h" to "plfts.swift"
      )
      .inOrder()
  }

  @Test
  fun everyFilterOperatorSendsItsPostgrestOperator() {
    val postgrestOperators =
      listOf(
        "eq",
        "neq",
        "gt",
        "gte",
        "lt",
        "lte",
        "like",
        "match",
        "ilike",
        "imatch",
        "is",
        "in",
        "cs",
        "cd",
        "sl",
        "sr",
        "nxl",
        "nxr",
        "adj",
        "ov",
        "fts",
        "plfts",
        "phfts",
        "wfts",
        "isdistinct",
        "eq(any)",
        "eq(all)",
        "gt(any)",
        "gt(all)",
        "gte(any)",
        "gte(all)",
        "lt(any)",
        "lt(all)",
        "lte(any)",
        "lte(all)",
        "like(any)",
        "like(all)",
        "ilike(any)",
        "ilike(all)",
        "match(any)",
        "match(all)",
        "imatch(any)",
        "imatch(all)"
      )

    assertThat(
        constantsOf<FilterOperator>().values.map { filters { filter("a", it, "v") }.single() }
      )
      .containsExactlyElementsIn(postgrestOperators.map { "a" to "$it.v" })
      .inOrder()
  }

  @Test
  fun quantifiedOperatorsSendAListAsAnArrayLiteral() {
    assertThat(
        filters {
          filter("id", FilterOperator.EQ_ANY, listOf(1, 2))
            .filter("price", FilterOperator.LT_ALL, listOf(2.5, 3.0))
            .filter("name", FilterOperator.IMATCH_ANY, listOf("^a", "b c"))
            .filterNot("name", FilterOperator.LIKE_ALL, listOf("A%", "%,%"))
            .filter("tag", FilterOperator.GTE_ANY, "{x,y}")
        }
      )
      .containsExactly(
        "id" to "eq(any).{1,2}",
        "price" to "lt(all).{2.5,3.0}",
        "name" to "imatch(any).{^a,\"b c\"}",
        "name" to "not.like(all).{A%,\"%,%\"}",
        "tag" to "gte(any).{x,y}"
      )
      .inOrder()
  }

  // --- logical operators ---

  @Test
  fun orAndAndWrapTheConditionsInParentheses() {
    // Unlike a column named `or`, the `or` and `and` parameters themselves are not quoted.
    assertThat(filters { or("status.eq.OFFLINE,name.eq.test").and("age.gt.21,role.eq.admin") })
      .containsExactly(
        "or" to "(status.eq.OFFLINE,name.eq.test)",
        "and" to "(age.gt.21,role.eq.admin)"
      )
      .inOrder()
  }

  @Test
  fun orAndAndOnAReferencedTableFilterTheEmbeddedResource() {
    assertThat(
        filters {
          or("name.eq.A,name.eq.B", referencedTable = "categories")
            .and("id.gt.1,id.lt.9", referencedTable = "categories")
            .or("a.eq.1,b.eq.2")
            .and("c.eq.3", referencedTable = "cities")
        }
      )
      .containsExactly(
        "categories.or" to "(name.eq.A,name.eq.B)",
        "categories.and" to "(id.gt.1,id.lt.9)",
        "or" to "(a.eq.1,b.eq.2)",
        "cities.and" to "(c.eq.3)"
      )
      .inOrder()
  }

  // --- generic filters ---

  @Test
  fun filterAndFilterNotSendTheOperatorAndTheValue() {
    assertThat(
        filters {
          filter("price", FilterOperator.GTE, 49.99)
            .filter("id", FilterOperator.IN, "(1,2)")
            .filter("metadata", FilterOperator.CS, mapOf("organic" to true))
            .filterNot("status", FilterOperator.EQ, "in_stock")
            .filterNot("tags", FilterOperator.CS, "{a,b}")
            .filterNot("deleted_at", FilterOperator.IS, "null")
            .filterNot("archived_at", FilterOperator.IS, null)
            .filter("parent_id", FilterOperator.IS, null)
        }
      )
      .containsExactly(
        "price" to "gte.49.99",
        "id" to "in.(1,2)",
        "metadata" to "cs.{\"organic\":true}",
        "status" to "not.eq.in_stock",
        "tags" to "not.cs.{a,b}",
        "deleted_at" to "not.is.null",
        "archived_at" to "not.is.null",
        "parent_id" to "is.null"
      )
      .inOrder()
  }

  @Test
  fun namesThatStartWithDollarAreRejected() {
    // The server ignores the query parameters whose names start with `$`, so the filter would be
    // dropped without an error, and an update() or a delete() could change every row.
    assertThrows(IllegalArgumentException::class.java) { filters { eq("\$amount", 5) } }
    assertThrows(IllegalArgumentException::class.java) { filters { match(mapOf("\$amount" to 5)) } }
    assertThrows(IllegalArgumentException::class.java) {
      filters { or("a.eq.1,b.eq.2", referencedTable = "\$items") }
    }
    // Only names are affected: a value can contain `$`.
    assertThat(filters { eq("price", "\$5") }).containsExactly("price" to "eq.\$5")
  }

  enum class Season {
    WINTER
  }
}
