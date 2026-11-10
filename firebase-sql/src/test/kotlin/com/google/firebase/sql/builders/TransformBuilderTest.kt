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
import com.google.firebase.sql.ExplainFormat
import com.google.firebase.sql.FirebaseSqlException
import com.google.firebase.sql.FirebaseSqlExceptionCategory
import com.google.firebase.sql.Order
import com.google.firebase.sql.ReservedHeaderException
import com.google.firebase.sql.core.NetworkResponse
import java.io.IOException
import java.lang.reflect.Modifier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests the modifiers of [TransformBuilder] and how `execute()` turns responses into results. The
 * expected values follow how PostgREST reads each query parameter, media type, and preference.
 *
 * Robolectric is needed because [FirebaseSqlException] is a `FirebaseException`, whose constructor
 * checks its message with `android.text.TextUtils`.
 */
@RunWith(RobolectricTestRunner::class)
class TransformBuilderTest {

  private val executor = FakeOperationsExecutor()

  private fun from(table: String = "products"): QueryBuilder = OperationBuilderImpl(table, executor)

  /** Returns the `Accept` header of the request built by [build]. */
  private fun accept(build: QueryBuilder.() -> TransformBuilder): String? =
    from().build().toRequest().headers["Accept"]

  // --- order, limit, offset, range ---

  @Test
  fun orderSendsTheColumnTheDirectionAndTheNullsPosition() {
    val asc = from().select().order("name", nullsFirst = false).toRequest()
    val desc = from().select().order("created_at", Order.DESCENDING, nullsFirst = true).toRequest()

    assertThat(asc.queryParams)
      .containsExactly("select" to "*", "order" to "name.asc.nullslast")
      .inOrder()
    assertThat(desc.queryParams)
      .containsExactly("select" to "*", "order" to "created_at.desc.nullsfirst")
      .inOrder()
  }

  @Test
  fun orderSendsNoNullsPositionByDefault() {
    // PostgreSQL then puts the nulls last in ascending order and first in descending order.
    val asc = from().select().order("name").toRequest()
    val desc = from().select().order("created_at", Order.DESCENDING).toRequest()

    assertThat(asc.queryParams).containsExactly("select" to "*", "order" to "name.asc").inOrder()
    assertThat(desc.queryParams)
      .containsExactly("select" to "*", "order" to "created_at.desc")
      .inOrder()
  }

  @Test
  fun ordersOnTheSameTableAreJoinedIntoOneParameter() {
    // PostgREST reads only the first `order` parameter, so all the columns go into one parameter.
    val req = from().select().order("name").order("id", Order.DESCENDING).toRequest()

    assertThat(req.queryParams)
      .containsExactly("select" to "*", "order" to "name.asc,id.desc")
      .inOrder()
  }

  @Test
  fun orderOnAReferencedTableUsesAPrefixedParameter() {
    val req =
      from("countries")
        .select("name, cities(name)")
        .order("name")
        .order("name", Order.DESCENDING, referencedTable = "cities")
        .toRequest()

    assertThat(req.queryParams)
      .containsExactly(
        "select" to "name, cities(name)",
        "order" to "name.asc",
        "cities.order" to "name.desc"
      )
      .inOrder()
  }

  @Test
  fun limitAndRangeApplyToTheTableOrToAReferencedTable() {
    val req =
      from()
        .select("*, comments(*)")
        .range(5, 14)
        .limit(2, referencedTable = "comments")
        .toRequest()

    assertThat(req.queryParams)
      .containsExactly(
        "select" to "*, comments(*)",
        "limit" to "10",
        "comments.limit" to "2",
        "offset" to "5"
      )
      .inOrder()
  }

  @Test
  fun theLastLimitWins() {
    val req = from().select().limit(10).limit(3).toRequest()

    assertThat(req.queryParams).containsExactly("select" to "*", "limit" to "3").inOrder()
  }

  @Test
  fun rangeSendsTheNumberOfRowsAndTheOffset() {
    val req = from().select().range(10, 19).toRequest()
    val ref = from().select().range(0, 4, referencedTable = "comments").toRequest()

    assertThat(req.queryParams)
      .containsExactly("select" to "*", "limit" to "10", "offset" to "10")
      .inOrder()
    assertThat(ref.queryParams)
      .containsExactly("select" to "*", "comments.limit" to "5", "comments.offset" to "0")
      .inOrder()
  }

  @Test
  fun rangeRejectsANegativeStartOrAnEndBeforeTheStart() {
    // A negative limit fails on the server with PGRST103.
    for ((first, last) in listOf(-1L to 3L, 5L to 4L, 5L to 0L)) {
      assertThrows("$first..$last", IllegalArgumentException::class.java) {
        from().select().range(first, last)
      }
    }
  }

  @Test
  fun rangeRejectsMoreRowsThanALongCanCount() {
    // range(0, Long.MAX_VALUE) has Long.MAX_VALUE + 1 rows, which would overflow to a negative
    // limit.
    assertThrows(IllegalArgumentException::class.java) { from().select().range(0, Long.MAX_VALUE) }
    assertThrows(IllegalArgumentException::class.java) { from().select().range(0L..Long.MAX_VALUE) }

    val req = from().select().range(1, Long.MAX_VALUE).toRequest()

    assertThat(req.queryParams)
      .containsExactly("select" to "*", "limit" to "${Long.MAX_VALUE}", "offset" to "1")
      .inOrder()
  }

  @Test
  fun rangeReplacesAnEarlierLimit() {
    val rangeLast = from().select().limit(5).range(10, 19).toRequest()
    val limitLast = from().select().range(10, 19).limit(3).toRequest()

    assertThat(rangeLast.queryParams)
      .containsExactly("select" to "*", "limit" to "10", "offset" to "10")
      .inOrder()
    assertThat(limitLast.queryParams)
      .containsExactly("select" to "*", "limit" to "3", "offset" to "10")
      .inOrder()
  }

  @Test
  fun queryParametersFollowAFixedOrder() {
    val req = from().select("id").eq("name", "Apple").order("id").range(10, 14).toRequest()

    assertThat(req.queryParams)
      .containsExactly(
        "select" to "id",
        "name" to "eq.Apple",
        "limit" to "5",
        "offset" to "10",
        "order" to "id.asc"
      )
      .inOrder()
  }

  // --- response format ---

  @Test
  fun singleRequestsAnObject() {
    val req = from().select().single().toRequest()

    assertThat(req.headers).containsExactly("Accept", "application/vnd.pgrst.object+json")
  }

  @Test
  fun maybeSingleOnASelectRequestsAJsonArray() {
    // The SDK reduces the array to at most one row, so a missing row is not an error.
    val req = from().select().maybeSingle().toRequest()

    assertThat(req.headers).containsExactly("Accept", "application/json")
  }

  @Test
  fun maybeSingleOnAMutationRequestsAnObject() {
    // With an object, PostgREST rejects and rolls back a mutation that affects more than one row.
    val requests =
      listOf(
        from().insert(mapOf("id" to 1)).select().maybeSingle().toRequest(),
        from().update(mapOf("name" to "Apple")).eq("id", 1).select().maybeSingle().toRequest(),
        from().delete().eq("id", 1).select().maybeSingle().toRequest()
      )

    for (req in requests) {
      assertThat(req.headers)
        .containsExactly(
          "Accept",
          "application/vnd.pgrst.object+json",
          "Prefer",
          "return=representation"
        )
    }
  }

  @Test
  fun csvAndGeojsonRequestTheirMediaTypes() {
    assertThat(from().select().csv().toRequest().headers).containsExactly("Accept", "text/csv")
    assertThat(from().select().geojson().toRequest().headers)
      .containsExactly("Accept", "application/geo+json")
  }

  @Test
  fun theLastResponseFormatWins() {
    assertThat(accept { select().single().csv() }).isEqualTo("text/csv")
    assertThat(accept { select().csv().single() }).isEqualTo("application/vnd.pgrst.object+json")
  }

  @Test
  fun stripNullsRequestsAStrippedArrayOrObjectInAnyCallOrder() {
    // PostgREST only strips nulls for its own array and object media types. It ignores
    // `nulls=stripped` on `application/json`.
    assertThat(accept { select().stripNulls() })
      .isEqualTo("application/vnd.pgrst.array+json;nulls=stripped")
    assertThat(accept { insert(mapOf("id" to 1)).select().stripNulls() })
      .isEqualTo("application/vnd.pgrst.array+json;nulls=stripped")
    assertThat(accept { select().single().stripNulls() })
      .isEqualTo("application/vnd.pgrst.object+json;nulls=stripped")
    assertThat(accept { select().stripNulls().single() })
      .isEqualTo("application/vnd.pgrst.object+json;nulls=stripped")
    assertThat(accept { select().maybeSingle().stripNulls() })
      .isEqualTo("application/vnd.pgrst.array+json;nulls=stripped")
    assertThat(accept { update(mapOf("id" to 1)).eq("id", 1).select().stripNulls().maybeSingle() })
      .isEqualTo("application/vnd.pgrst.object+json;nulls=stripped")
  }

  @Test
  fun stripNullsCannotBeUsedWithCsvInAnyCallOrder() {
    // PostgREST strips nulls only from JSON, so the SDK rejects the combination instead of
    // ignoring stripNulls().
    val errors =
      listOf(
        assertThrows(IllegalStateException::class.java) { from().select().csv().stripNulls() },
        assertThrows(IllegalStateException::class.java) { from().select().stripNulls().csv() }
      )

    for (error in errors) {
      assertThat(error).hasMessageThat().isEqualTo("stripNulls() cannot be used with csv()")
    }
  }

  @Test
  fun stripNullsCanBeUsedOnceCsvIsReplaced() {
    // The last response format wins, so a CSV format that single() replaced is not a conflict.
    assertThat(accept { select().csv().single().stripNulls() })
      .isEqualTo("application/vnd.pgrst.object+json;nulls=stripped")
  }

  @Test
  fun stripNullsDoesNotChangeGeojson() {
    assertThat(accept { select().stripNulls().geojson() }).isEqualTo("application/geo+json")
  }

  // --- preferences ---

  @Test
  fun dryRunRollsBackTheTransaction() {
    val req = from().insert(mapOf("id" to 1)).select().dryRun().toRequest()

    assertThat(req.headers).containsExactly("Prefer", "return=representation,tx=rollback")
  }

  @Test
  fun maxAffectedSendsStrictHandling() {
    val req = from().update(mapOf("name" to "Apple")).eq("id", 1).maxAffected(1).toRequest()

    assertThat(req.headers).containsExactly("Prefer", "handling=strict,max-affected=1")
  }

  @Test
  fun preferencesFollowAFixedOrder() {
    val upsert =
      from()
        .upsert(mapOf("id" to 1), onConflict = "id", count = Count.EXACT, defaultToNull = false)
        .select()
        .dryRun()
        .toRequest()
    val update =
      from()
        .update(mapOf("name" to "Apple"), count = Count.PLANNED)
        .eq("id", 1)
        .maxAffected(2)
        .dryRun()
        .toRequest()

    assertThat(upsert.headers)
      .containsExactly(
        "Prefer",
        "return=representation,count=exact,missing=default,resolution=merge-duplicates,tx=rollback"
      )
    assertThat(update.headers)
      .containsExactly("Prefer", "count=planned,tx=rollback,handling=strict,max-affected=2")
  }

  @Test
  fun selectAfterAMutationReturnsTheRepresentation() {
    val insert = from().insert(mapOf("id" to 1)).select("id, name").toRequest()
    val update = from().update(mapOf("name" to "Apple")).eq("id", 1).select().toRequest()

    assertThat(insert.queryParams).containsExactly("select" to "id, name")
    assertThat(insert.headers).containsExactly("Prefer", "return=representation")
    assertThat(update.queryParams).containsExactly("select" to "*", "id" to "eq.1").inOrder()
    assertThat(update.headers).containsExactly("Prefer", "return=representation")
  }

  @Test
  fun selectAfterAMutationCleansTheColumnsLikeSelect() {
    val req = from().delete().eq("id", 1).select("\nid,\n  name\n").toRequest()

    assertThat(req.queryParams).containsExactly("select" to "id,   name", "id" to "eq.1").inOrder()
  }

  // --- explain ---

  @Test
  fun explainRequestsThePlanForTheResponseMediaType() {
    assertThat(accept { select().explain() })
      .isEqualTo("application/vnd.pgrst.plan+text; for=\"application/json\"")
    assertThat(
        accept {
          select()
            .explain(
              analyze = true,
              verbose = true,
              settings = true,
              buffers = true,
              wal = true,
              format = ExplainFormat.JSON
            )
        }
      )
      .isEqualTo(
        "application/vnd.pgrst.plan+json; for=\"application/json\"; " +
          "options=analyze|verbose|settings|buffers|wal"
      )
  }

  @Test
  fun explainKeepsTheResponseFormatInAnyCallOrder() {
    val singlePlan = "application/vnd.pgrst.plan+text; for=\"application/vnd.pgrst.object+json\""

    assertThat(accept { select().single().explain() }).isEqualTo(singlePlan)
    assertThat(accept { select().explain().single() }).isEqualTo(singlePlan)
    assertThat(accept { select().csv().explain(format = ExplainFormat.JSON) })
      .isEqualTo("application/vnd.pgrst.plan+json; for=\"text/csv\"")
    assertThat(accept { select().explain(verbose = true).stripNulls() })
      .isEqualTo(
        "application/vnd.pgrst.plan+text; " +
          "for=\"application/vnd.pgrst.array+json;nulls=stripped\"; options=verbose"
      )
  }

  // --- custom headers ---

  @Test
  fun setHeaderAddsAHeader() {
    val req = from().select().setHeader("X-Request-Id", "42").toRequest()

    assertThat(req.headers).containsExactly("X-Request-Id", "42")
  }

  @Test
  fun setHeaderReplacesAComputedHeaderInAnyCase() {
    // HTTP header names are case-insensitive, so the request must not contain both headers.
    val req = from().select().single().setHeader("accept", "application/json").toRequest()

    assertThat(req.headers.values).containsExactly("application/json")
    assertThat(req.headers["Accept"]).isEqualTo("application/json")
  }

  @Test
  fun aCustomPreferHeaderIsMergedBeforeTheComputedPreferences() {
    // PostgREST uses the first value of each preference, so the custom values win, and the other
    // computed preferences are kept.
    val req =
      from()
        .insert(mapOf("id" to 1), count = Count.EXACT)
        .select()
        .setHeader("prefer", "return=minimal, tx=rollback")
        .toRequest()

    assertThat(req.headers.values)
      .containsExactly("return=minimal, tx=rollback,return=representation,count=exact")
  }

  @Test
  fun setHeaderRejectsTheReservedHeaders() {
    // The tokens, the telemetry headers, and the schema headers are reserved, so a request always
    // carries the tokens of the app and the schema of the settings. So are the routing and
    // heartbeat
    // headers, which the SDK does not send.
    val reservedHeaders =
      listOf(
        "x-firebase-auth-token",
        "X-Firebase-AppCheck",
        "x-goog-api-client",
        "X-Client-Version",
        "x-firebase-gmpid",
        "Accept-Profile",
        "CONTENT-PROFILE",
        "Authorization",
        "X-Retry-Count",
        "X-Firebase-SQLConnect-Affinity",
        "x-goog-request-params",
        "X-Firebase-Client",
        "x-firebase-client-log-type"
      )

    for (name in reservedHeaders) {
      val error =
        assertThrows(name, ReservedHeaderException::class.java) {
          from().select().setHeader(name, "value")
        }
      assertThat(error.headerName).isEqualTo(name)
      assertThat(error).hasMessageThat().contains(name)
      assertThat(error).hasMessageThat().contains("setHeader()")
    }
    // The other headers, including the media type headers, can still be set.
    from().select().setHeader("Accept", "text/csv").setHeader("Content-Type", "text/csv")
  }

  @Test
  fun onlyTheSchemaHeadersPointToSchemaName() {
    val schemaError =
      assertThrows(ReservedHeaderException::class.java) {
        from().select().setHeader("Accept-Profile", "inventory")
      }
    assertThat(schemaError).hasMessageThat().contains("SqlServiceSettings.schemaName")

    val tokenError =
      assertThrows(ReservedHeaderException::class.java) {
        from().select().setHeader("Authorization", "Bearer token")
      }
    assertThat(tokenError).hasMessageThat().doesNotContain("schemaName")
  }

  // --- Order and LongRange forms, and argument checks ---

  @Test
  fun orderWithAnOrderSendsThePositionOfTheNullsOnlyWhenItIsSet() {
    val req =
      from()
        .select("*, cities(name)")
        .order("price", Order.DESCENDING)
        .order("name", Order.ASCENDING, nullsFirst = true)
        .order("stock", Order.DESCENDING, nullsFirst = false)
        .order("name", Order.DESCENDING, referencedTable = "cities")
        .toRequest()

    assertThat(req.queryParams)
      .containsExactly(
        "select" to "*, cities(name)",
        "order" to "price.desc,name.asc.nullsfirst,stock.desc.nullslast",
        "cities.order" to "name.desc"
      )
      .inOrder()
  }

  @Test
  fun rangeTakesALongRange() {
    assertThat(from().select().range(10L..19L).toRequest())
      .isEqualTo(from().select().range(10, 19).toRequest())
    assertThat(from().select().range(0L..4L, referencedTable = "comments").toRequest())
      .isEqualTo(from().select().range(0, 4, referencedTable = "comments").toRequest())
    assertThrows(IllegalArgumentException::class.java) { from().select().range(5L..4L) }
  }

  @Test
  fun limitRejectsANegativeCount() {
    assertThrows(IllegalArgumentException::class.java) { from().select().limit(-1) }
    assertThat(from().select().limit(0).toRequest().queryParams)
      .containsExactly("select" to "*", "limit" to "0")
      .inOrder()
  }

  @Test
  fun aTopLevelLimitOrRangeIsRejectedOnAnUpdateOrADelete() {
    // PostgREST would apply them only to the returned rows, and still change every row that
    // matches.
    val mutations =
      mapOf(
        "update" to from().update(mapOf("name" to "Apple")).eq("id", 1),
        "delete" to from().delete().eq("id", 1)
      )
    val calls =
      mapOf<String, (FilterBuilder) -> Any>(
        "limit" to { it.limit(10) },
        "range" to { it.range(0, 9) },
        "range(LongRange)" to { it.range(0L..9L) }
      )
    for ((kind, mutation) in mutations) {
      for ((name, call) in calls) {
        val error =
          assertThrows("$kind $name", IllegalStateException::class.java) { call(mutation) }
        assertWithMessage("$kind $name").that(error).hasMessageThat().contains("maxAffected()")
      }
      // The embedded rows that select() returns can be limited.
      mutation.select("*, comments(*)").limit(5, referencedTable = "comments")
      mutation.select("*, comments(*)").range(0, 4, referencedTable = "comments")
    }
    // A read and the rows that an insert returns can be limited.
    from().select().limit(10)
    from().insert(mapOf("id" to 1)).select().limit(10)
  }

  @Test
  fun maxAffectedWorksOnlyWithAnUpdateOrADelete() {
    val others =
      mapOf(
        "select" to from().select(),
        "insert" to from().insert(mapOf("id" to 1)),
        "upsert" to from().upsert(mapOf("id" to 1))
      )
    for ((name, builder) in others) {
      val error = assertThrows(name, IllegalStateException::class.java) { builder.maxAffected(1) }
      assertWithMessage(name).that(error).hasMessageThat().contains("maxAffected()")
    }
    from().update(mapOf("name" to "Apple")).eq("id", 1).maxAffected(0)
    from().delete().eq("id", 1).maxAffected(1)
    assertThrows(IllegalArgumentException::class.java) {
      from().delete().eq("id", 1).maxAffected(-1)
    }
  }

  // --- execute ---

  @Test
  fun executeSendsTheBuiltRequest() =
    runBlocking<Unit> {
      val builder = from().select("id").eq("id", 1).single()

      builder.execute()

      assertThat(executor.lastRequest).isEqualTo(builder.toRequest())
    }

  @Test
  fun executeReturnsTheBodyTheStatusTheCountAndTheRange() =
    runBlocking<Unit> {
      executor.response =
        NetworkResponse(206, "Partial Content", "[{\"id\":1}]", count = 5, range = 0L..0L)

      val result = from().select(count = Count.EXACT).limit(1).execute()

      assertThat(result.status).isEqualTo(206)
      assertThat(result.statusText).isEqualTo("Partial Content")
      assertThat(result.data).isEqualTo("[{\"id\":1}]")
      assertThat(result.countOrNull()).isEqualTo(5L)
      assertThat(result.rangeOrNull()).isEqualTo(0L..0L)
    }

  @Test
  fun executeReturnsAnEmptyStringWithoutABody() =
    runBlocking<Unit> {
      executor.response = NetworkResponse(204, "No Content", null)

      val result = from().delete().eq("id", 1).execute()

      assertThat(result.data).isEmpty()
    }

  @Test
  fun executeThrowsTheErrorOfAFailedRequest() {
    val error =
      FirebaseSqlException(
        "failed to parse filter",
        FirebaseSqlExceptionCategory.POSTGREST,
        code = "PGRST100",
        status = 400
      )
    executor.response = NetworkResponse(400, "Bad Request", null, error = error)

    val thrown =
      assertThrows(FirebaseSqlException::class.java) { runBlocking { from().select().execute() } }

    assertThat(thrown).isSameInstanceAs(error)
  }

  @Test
  fun executeThrowsTheErrorOfARequestWithoutAResponse() {
    val error = IOException("The network is unreachable.")
    executor.response =
      NetworkResponse(0, "Transport Error", null, error = error, isTransportError = true)

    val thrown = assertThrows(IOException::class.java) { runBlocking { from().select().execute() } }

    assertThat(thrown).isSameInstanceAs(error)
  }

  // --- noRetry() ---

  @Test
  fun retriesAreOnByDefaultAndNoRetryTurnsThemOff() {
    assertThat(from().select().toRequest().retryEnabled).isTrue()
    assertThat(from().select().noRetry().toRequest().retryEnabled).isFalse()
  }

  // --- maybeSingle() and single() ---

  @Test
  fun maybeSingleReturnsTheOnlyRow() =
    runBlocking<Unit> {
      executor.response = NetworkResponse(200, "OK", "[{\"id\":1}]")

      val result = from().select().maybeSingle().execute()

      assertThat(json(result.data)).isEqualTo(json("{\"id\":1}"))
    }

  @Test
  fun maybeSingleReturnsNullWithoutRowsAndKeepsTheCount() =
    runBlocking<Unit> {
      executor.response = NetworkResponse(200, "OK", "[]", count = 0)

      val result = from().select(count = Count.EXACT).maybeSingle().execute()

      assertThat(result.data).isEqualTo("null")
      assertThat(result.countOrNull()).isEqualTo(0L)
    }

  @Test
  fun maybeSingleThrowsThePostgrestErrorForMoreThanOneRow() {
    executor.response = NetworkResponse(200, "OK", "[{\"id\":1},{\"id\":2}]")

    val error =
      assertThrows(FirebaseSqlException::class.java) {
        runBlocking { from().select().maybeSingle().execute() }
      }

    assertThat(error.message).isEqualTo("Cannot coerce the result to a single JSON object")
    assertThat(error.status).isEqualTo(406)
    assertThat(error.code).isEqualTo("PGRST116")
    assertThat(error.details).isEqualTo("The result contains 2 rows")
    assertThat(error.hint).isNull()
    assertThat(error.category).isEqualTo(FirebaseSqlExceptionCategory.POSTGREST)
  }

  @Test
  fun maybeSingleTurnsTheNoRowsErrorOfAMutationIntoNull() =
    runBlocking<Unit> {
      // For a mutation, the SDK requests an object, and PostgREST answers 406 when no row matches.
      executor.response =
        NetworkResponse(
          406,
          "Not Acceptable",
          null,
          error = notSingular("The result contains 0 rows")
        )

      val result =
        from().update(mapOf("name" to "Apple")).eq("id", 1).select().maybeSingle().execute()

      assertThat(result.status).isEqualTo(200)
      assertThat(result.data).isEqualTo("null")
    }

  @Test
  fun maybeSingleKeepsTheMultipleRowsErrorOfAMutation() {
    // "10 rows" contains "0 rows", so it checks that only the error for zero rows becomes null.
    for (details in listOf("The result contains 2 rows", "The result contains 10 rows")) {
      val error = notSingular(details)
      executor.response = NetworkResponse(406, "Not Acceptable", null, error = error)

      val thrown =
        assertThrows(details, FirebaseSqlException::class.java) {
          runBlocking { from().delete().eq("name", "Apple").select().maybeSingle().execute() }
        }

      assertWithMessage(details).that(thrown).isSameInstanceAs(error)
    }
  }

  @Test
  fun singleKeepsTheNoRowsError() {
    val error = notSingular("The result contains 0 rows")
    executor.response = NetworkResponse(406, "Not Acceptable", null, error = error)

    val thrown =
      assertThrows(FirebaseSqlException::class.java) {
        runBlocking { from().select().eq("id", 1).single().execute() }
      }

    assertThat(thrown).isSameInstanceAs(error)
  }

  @Test
  fun maybeSingleLeavesABodyThatIsNotAnArrayUnchanged() =
    runBlocking<Unit> {
      executor.response = NetworkResponse(200, "OK", "")
      val head = from().select(head = true, count = Count.EXACT).maybeSingle().execute()
      executor.response = NetworkResponse(200, "OK", "{\"id\":1}")
      val obj = from().select().maybeSingle().execute()

      assertThat(head.data).isEmpty()
      assertThat(obj.data).isEqualTo("{\"id\":1}")
    }

  @Test
  fun maybeSingleLeavesAnExplainPlanUnchanged() =
    runBlocking<Unit> {
      // With explain(), PostgREST returns the plan instead of the rows. A JSON plan is an array.
      val plan = "[{\"Plan\":{\"Node Type\":\"Seq Scan\",\"Relation Name\":\"products\"}}]"
      executor.response = NetworkResponse(200, "OK", plan)

      val result = from().select().maybeSingle().explain(format = ExplainFormat.JSON).execute()

      assertThat(result.data).isEqualTo(plan)
    }

  // --- dryRun() ---

  @Test
  fun dryRunSucceedsWhenServerAppliesTxRollback() =
    runBlocking<Unit> {
      executor.response =
        NetworkResponse(
          status = 201,
          statusText = "Created",
          body = "[{\"id\":1}]",
          preferenceApplied = "return=representation, tx=rollback"
        )

      val result = from().insert(mapOf("id" to 1)).select().dryRun().execute()

      assertThat(result.status).isEqualTo(201)
      assertThat(result.data).isEqualTo("[{\"id\":1}]")
    }

  @Test
  fun dryRunThrowsWhenServerDoesNotApplyTxRollback() {
    // When PostgREST has db-tx-end = "commit", it ignores tx=rollback and omits it from
    // Preference-Applied.
    executor.response =
      NetworkResponse(
        status = 201,
        statusText = "Created",
        body = "[{\"id\":1}]",
        preferenceApplied = "return=representation"
      )

    val error =
      assertThrows(FirebaseSqlException::class.java) {
        runBlocking { from().insert(mapOf("id" to 1)).select().dryRun().execute() }
      }

    assertThat(error).hasMessageThat().contains("tx=rollback")
    assertThat(error).hasMessageThat().contains("committed")
    // The insert was committed with the status of a successful request.
    assertThat(error.status).isEqualTo(201)
    // The server answered successfully; the SDK detected that the changes were committed.
    assertThat(error.category).isEqualTo(FirebaseSqlExceptionCategory.CLIENT)
  }

  @Test
  fun dryRunThrowsWithoutAPreferenceAppliedHeader() {
    // Without the header, for example when a proxy drops it, nothing confirms the rollback.
    executor.response = NetworkResponse(201, "Created", "[{\"id\":1}]")

    val error =
      assertThrows(FirebaseSqlException::class.java) {
        runBlocking { from().insert(mapOf("id" to 1)).select().dryRun().execute() }
      }

    assertThat(error.status).isEqualTo(201)
    assertThat(error).hasMessageThat().contains("tx=rollback")
  }

  @Test
  fun dryRunIsNotCheckedWithExplain() =
    runBlocking<Unit> {
      // PostgREST sends no Preference-Applied header with a plan, so there is nothing to check.
      val plan = "[{\"Plan\":{\"Node Type\":\"ModifyTable\"}}]"
      executor.response = NetworkResponse(200, "OK", plan)

      val result =
        from()
          .insert(mapOf("id" to 1))
          .dryRun()
          .explain(analyze = true, format = ExplainFormat.JSON)
          .execute()

      assertThat(result.data).isEqualTo(plan)
    }

  @Test
  fun dryRunWithMaybeSingleTurnsTheNoRowsErrorOfAMutationIntoNull() =
    runBlocking<Unit> {
      // A mutation without rows changes nothing, so there is nothing to roll back.
      executor.response =
        NetworkResponse(
          406,
          "Not Acceptable",
          null,
          error = notSingular("The result contains 0 rows")
        )

      val result =
        from()
          .update(mapOf("name" to "Apple"))
          .eq("id", 1)
          .select()
          .maybeSingle()
          .dryRun()
          .execute()

      assertThat(result.data).isEqualTo("null")
    }

  // --- filters after modifiers ---

  @Test
  fun everyModifierReturnsAFilterBuilderExceptTheOnesThatChangeTheTypeOfTheResponse() {
    // Catches a modifier that is added to TransformBuilder without an override in FilterBuilder.
    val responseTypeModifiers = setOf("single", "maybeSingle", "csv", "geojson", "explain")
    val modifiers =
      TransformBuilder::class.java.methods.filter {
        !it.isSynthetic &&
          !Modifier.isStatic(it.modifiers) &&
          it.returnType == TransformBuilder::class.java
      }
    assertThat(modifiers.map { it.name })
      .containsAtLeastElementsIn(responseTypeModifiers + listOf("order", "limit", "setHeader"))

    for (modifier in modifiers) {
      val onFilterBuilder =
        FilterBuilder::class.java.getMethod(modifier.name, *modifier.parameterTypes)
      val expected =
        if (modifier.name in responseTypeModifiers) TransformBuilder::class.java
        else FilterBuilder::class.java
      assertWithMessage(modifier.toString()).that(onFilterBuilder.returnType).isEqualTo(expected)
    }
  }

  @Test
  fun aFilterAfterTheModifiersSendsTheSameRequestAsBeforeThem() {
    // The SDK sends the query parameters in a fixed order, whatever the order of the calls.
    val filtersFirst = from().select().eq("category_id", 1).order("price").limit(10).toRequest()
    val filtersLast = from().select().order("price").limit(10).eq("category_id", 1).toRequest()

    assertThat(filtersLast.queryParams)
      .containsExactly(
        "select" to "*",
        "category_id" to "eq.1",
        "limit" to "10",
        "order" to "price.asc"
      )
      .inOrder()
    assertThat(filtersLast).isEqualTo(filtersFirst)
  }

  @Test
  fun aFilterCanFollowEachModifierThatReturnsAFilterBuilder() {
    val read = from().select()
    val update = from().update(mapOf("price" to 0.5))
    // The type of the map checks that each modifier returns a FilterBuilder.
    val modified: Map<String, FilterBuilder> =
      mapOf(
        "order" to read.order("name", Order.DESCENDING),
        "limit" to read.limit(10),
        "range" to read.range(0, 9),
        "range with a LongRange" to read.range(0L..9L),
        "stripNulls" to read.stripNulls(),
        "noRetry" to read.noRetry(),
        "setHeader" to read.setHeader("X-Source", "search"),
        "dryRun" to update.dryRun(),
        "maxAffected" to update.maxAffected(1)
      )

    for ((modifier, builder) in modified) {
      val withFilter = builder.eq("id", 42).toRequest()
      val withoutFilter = builder.toRequest()

      assertWithMessage(modifier)
        .that(withFilter.queryParams)
        .containsExactlyElementsIn(withoutFilter.queryParams + ("id" to "eq.42"))
      assertWithMessage(modifier).that(withFilter.headers).isEqualTo(withoutFilter.headers)
    }
  }

  @Test
  fun aQueryCanBeBuiltStepByStepInOneVariable() {
    var query = from().select()
    query = query.lte("price", 5)
    query = query.order("price")
    query = query.eq("category_id", 1)
    val request = query.limit(20).toRequest()

    assertThat(request.queryParams)
      .containsExactly(
        "select" to "*",
        "price" to "lte.5",
        "category_id" to "eq.1",
        "limit" to "20",
        "order" to "price.asc"
      )
      .inOrder()
  }

  private fun notSingular(details: String) =
    FirebaseSqlException(
      message = "Cannot coerce the result to a single JSON object",
      category = FirebaseSqlExceptionCategory.POSTGREST,
      status = 406,
      code = "PGRST116",
      details = details
    )
}
