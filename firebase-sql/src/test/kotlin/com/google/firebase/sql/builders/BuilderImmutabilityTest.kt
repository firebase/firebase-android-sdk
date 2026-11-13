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
import com.google.firebase.sql.Count
import com.google.firebase.sql.RpcMethod
import com.google.firebase.sql.core.NetworkResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Tests that the builders are immutable: every call returns a new builder and leaves the builder
 * that it is called on unchanged. A builder can therefore be reused as the base of several
 * requests, executed more than once, and shared between threads.
 */
class BuilderImmutabilityTest {

  private val executor = FakeOperationsExecutor()

  private fun from(table: String = "products"): QueryBuilder = OperationBuilderImpl(table, executor)

  @Test
  fun filtersAndModifiersDoNotChangeTheBuilderTheyAreCalledOn() {
    val base = from().select("id")

    val active = base.eq("is_active", true).limit(10)
    val cheap = base.lt("price", 5).order("price")
    base.match(mapOf("name" to "Kiwi")).range(0, 9).limit(5)

    assertThat(base.toRequest().queryParams).containsExactly("select" to "id")
    assertThat(active.toRequest().queryParams)
      .containsExactly("select" to "id", "is_active" to "eq.true", "limit" to "10")
      .inOrder()
    assertThat(cheap.toRequest().queryParams)
      .containsExactly("select" to "id", "price" to "lt.5", "order" to "price.asc")
      .inOrder()
  }

  @Test
  fun headersAndPreferencesDoNotChangeTheBuilderTheyAreCalledOn() {
    val base = from().select()

    val single = base.single().setHeader("X-Custom", "1")
    base.stripNulls()
    base.dryRun()
    base.explain(analyze = true)
    base.noRetry()
    base.csv()

    assertThat(base.toRequest().headers).isEmpty()
    assertThat(single.toRequest().headers)
      .containsExactly("Accept", "application/vnd.pgrst.object+json", "X-Custom", "1")
  }

  @Test
  fun aQueryBuilderCanStartSeveralRequests() {
    val table = from()

    val select = table.select("id")
    val delete = table.delete().eq("id", 1)
    val insert = table.insert(mapOf("id" to 2), count = Count.EXACT)

    val selectRequest = select.toRequest()
    assertThat(selectRequest.httpMethod).isEqualTo("GET")
    assertThat(selectRequest.queryParams).containsExactly("select" to "id")
    assertThat(selectRequest.headers).isEmpty()
    val deleteRequest = delete.toRequest()
    assertThat(deleteRequest.httpMethod).isEqualTo("DELETE")
    assertThat(deleteRequest.queryParams).containsExactly("id" to "eq.1")
    assertThat(deleteRequest.headers).isEmpty()
    val insertRequest = insert.toRequest()
    assertThat(insertRequest.httpMethod).isEqualTo("POST")
    assertThat(insertRequest.queryParams).isEmpty()
    assertThat(insertRequest.headers).containsExactly("Prefer", "count=exact")
  }

  @Test
  fun aMutationBuilderCanBeReused() {
    val update = from().update(mapOf("price" to 1))

    val first = update.eq("id", 1).select("id")
    val second = update.eq("id", 2)

    assertThat(update.toRequest().queryParams).isEmpty()
    assertThat(update.toRequest().headers).isEmpty()
    assertThat(first.toRequest().queryParams)
      .containsExactly("select" to "id", "id" to "eq.1")
      .inOrder()
    assertThat(first.toRequest().headers).containsExactly("Prefer", "return=representation")
    assertThat(second.toRequest().queryParams).containsExactly("id" to "eq.2")
    assertThat(second.toRequest().headers).isEmpty()
  }

  @Test
  fun aFunctionCallBuilderCanBeReused() {
    val call =
      OperationBuilderImpl.rpc(
        "search",
        mapOf("q" to "kiwi"),
        method = RpcMethod.GET,
        executor = executor
      )

    val filtered = call.eq("id", 1).limit(1)

    assertThat(call.toRequest().queryParams).containsExactly("q" to "kiwi")
    assertThat(filtered.toRequest().queryParams)
      .containsExactly("q" to "kiwi", "id" to "eq.1", "limit" to "1")
      .inOrder()
  }

  @Test
  fun aBuilderCanBeExecutedMoreThanOnce() =
    runBlocking<Unit> {
      executor.response = NetworkResponse(200, "OK", """[{"id":1}]""")
      val query = from().select().eq("id", 1).maybeSingle()

      val first = query.execute()
      val second = query.execute()

      assertThat(first.data).isEqualTo("""{"id":1}""")
      assertThat(second.data).isEqualTo("""{"id":1}""")
      assertThat(executor.requests).hasSize(2)
      assertThat(executor.requests[1]).isEqualTo(executor.requests[0])
    }

  @Test
  fun aBuilderCanBeSharedBetweenThreads() =
    runBlocking<Unit> {
      val base = from().select("id")

      val requests =
        (1..200)
          .map { id ->
            async(Dispatchers.Default) { base.eq("id", id).limit(id.toLong()).toRequest() }
          }
          .awaitAll()

      requests.forEachIndexed { index, request ->
        val id = index + 1
        assertThat(request.queryParams)
          .containsExactly("select" to "id", "id" to "eq.$id", "limit" to "$id")
          .inOrder()
      }
    }
}
