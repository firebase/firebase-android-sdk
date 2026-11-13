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
import com.google.firebase.sql.Order
import com.google.firebase.sql.RpcMethod
import com.google.firebase.sql.constantsOf
import com.google.firebase.sql.core.defaultJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Tests the requests that [OperationBuilderImpl.rpc] builds. The expected values follow how
 * PostgREST reads the arguments of a function call from the body (POST) or from the query string
 * (GET and HEAD).
 */
class RpcBuilderTest {

  private val executor = FakeOperationsExecutor()

  @Test
  fun maxAffectedWorksWithAFunctionCall() {
    val post = OperationBuilderImpl.rpc("archive_products", executor = executor).maxAffected(10)
    val get =
      OperationBuilderImpl.rpc(
          "search",
          mapOf("q" to "kiwi"),
          method = RpcMethod.GET,
          executor = executor
        )
        .maxAffected(10)

    for (req in listOf(post.toRequest(), get.toRequest())) {
      assertThat(req.headers["Prefer"]).isEqualTo("handling=strict,max-affected=10")
    }
  }

  // --- POST ---

  @Test
  fun postSendsTheArgumentsAsAJsonBody() {
    val req =
      OperationBuilderImpl.rpc(
          "my_rpc",
          mapOf("name" to "Doe, John", "n" to 2),
          executor = executor
        )
        .toRequest()

    assertThat(req.httpMethod).isEqualTo("POST")
    assertThat(req.pathSegments).containsExactly("rpc", "my_rpc").inOrder()
    assertThat(req.pathSegments).containsExactly("rpc", "my_rpc").inOrder()
    assertThat(req.queryParams).isEmpty()
    assertThat(req.headers).isEmpty()
    assertThat(req.body).isEqualTo(json("""{"name":"Doe, John","n":2}"""))
  }

  @Test
  fun postWithoutArgumentsSendsAnEmptyObject() {
    // PostgREST would read an empty body of a function call as `{}` too.
    val req = OperationBuilderImpl.rpc("hello_world", executor = executor).toRequest()

    assertThat(req.httpMethod).isEqualTo("POST")
    assertThat(req.queryParams).isEmpty()
    assertThat(req.body).isEqualTo(json("{}"))
  }

  @Test
  fun postSendsEncodedArgumentsAsTheBody() {
    val req = OperationBuilderImpl.rpc("search", searchArgs(), executor = executor).toRequest()

    assertThat(req.body).isEqualTo(json("""{"search_text":"apple","ids":[1,2]}"""))
  }

  @Test
  fun postRejectsArgumentsThatAreNotAnObject() {
    // PostgREST rejects a list of objects with different keys (PGRST102), calls the function with
    // only the first object of any other list, and fails on other values, such as a String, even
    // one that holds JSON text.
    val notObjects =
      listOf(listOf(mapOf("a" to 1), mapOf("b" to 2)), listOf(mapOf("a" to 1)), 42, "{\"a\":1}")

    for (params in notObjects) {
      assertThrows(params.toString(), IllegalArgumentException::class.java) {
        OperationBuilderImpl.rpc("bulk", params, executor = executor)
      }
    }
  }

  @Test
  fun countSendsThePreferHeader() {
    val req =
      OperationBuilderImpl.rpc("my_rpc", count = Count.EXACT, executor = executor).toRequest()

    assertThat(req.headers).containsExactly("Prefer", "count=exact")
  }

  // --- GET ---

  @Test
  fun getSendsTheArgumentsAsQueryParameters() {
    // PostgREST reads the whole value as the argument, so the values are not quoted.
    val req =
      OperationBuilderImpl.rpc(
          "my_rpc",
          mapOf(
            "name" to "Doe, John",
            "n" to 2,
            "flag" to true,
            "season" to Season.WINTER,
            "price" to 12.5
          ),
          method = RpcMethod.GET,
          executor = executor
        )
        .toRequest()

    assertThat(req.httpMethod).isEqualTo("GET")
    assertThat(req.pathSegments).containsExactly("rpc", "my_rpc").inOrder()
    assertThat(req.queryParams)
      .containsExactly(
        "name" to "Doe, John",
        "n" to "2",
        "flag" to "true",
        "season" to "WINTER",
        "price" to "12.5"
      )
      .inOrder()
    assertThat(req.headers).isEmpty()
    assertThat(req.body).isNull()
  }

  @Test
  fun getSendsListArgumentsAsAnArrayLiteral() {
    val req =
      OperationBuilderImpl.rpc(
          "my_rpc",
          mapOf("ids" to listOf(1, 2), "tags" to listOf("a b", "c,d", "e")),
          method = RpcMethod.GET,
          executor = executor
        )
        .toRequest()

    assertThat(req.queryParams)
      .containsExactly("ids" to "{1,2}", "tags" to """{"a b","c,d",e}""")
      .inOrder()
  }

  @Test
  fun getSendsObjectArgumentsAsJsonText() {
    val req =
      OperationBuilderImpl.rpc(
          "my_rpc",
          mapOf("filter" to mapOf("status" to "active")),
          method = RpcMethod.GET,
          executor = executor
        )
        .toRequest()

    assertThat(req.queryParams).containsExactly("filter" to """{"status":"active"}""")
  }

  @Test
  fun getOmitsNullArguments() {
    // PostgREST would read `a=null` as the text "null". Without the argument, the function uses the
    // default value of the parameter.
    val req =
      OperationBuilderImpl.rpc(
          "my_rpc",
          mapOf("a" to null, "b" to "x"),
          method = RpcMethod.GET,
          executor = executor
        )
        .toRequest()

    assertThat(req.queryParams).containsExactly("b" to "x")
  }

  @Test
  fun getSendsEncodedArgumentsAsQueryParameters() {
    val req =
      OperationBuilderImpl.rpc("search", searchArgs(), method = RpcMethod.GET, executor = executor)
        .toRequest()

    assertThat(req.queryParams)
      .containsExactly("search_text" to "apple", "ids" to "{1,2}")
      .inOrder()
  }

  @Test
  fun getRejectsArgumentsThatAreNotAnObject() {
    assertThrows(IllegalArgumentException::class.java) {
      OperationBuilderImpl.rpc("my_rpc", listOf(1, 2), method = RpcMethod.GET, executor = executor)
        .toRequest()
    }
    assertThrows(IllegalArgumentException::class.java) {
      OperationBuilderImpl.rpc("my_rpc", "{\"a\":1}", method = RpcMethod.HEAD, executor = executor)
        .toRequest()
    }
  }

  @Test
  fun getArgumentsComeBeforeFilters() {
    val req =
      OperationBuilderImpl.rpc(
          "my_rpc",
          mapOf("a" to 1),
          method = RpcMethod.GET,
          executor = executor
        )
        .eq("b", 2)
        .toRequest()

    assertThat(req.queryParams).containsExactly("a" to "1", "b" to "eq.2").inOrder()
  }

  @Test
  fun getRejectsArgumentNamesThatStartWithDollar() {
    // The server ignores the query parameters whose names start with `$`, so the function would
    // run with the default value of the parameter.
    assertThrows(IllegalArgumentException::class.java) {
      OperationBuilderImpl.rpc(
          "my_rpc",
          mapOf("\$a" to 1),
          method = RpcMethod.GET,
          executor = executor
        )
        .toRequest()
    }
  }

  // --- HEAD ---

  @Test
  fun headSendsTheArgumentsAsQueryParametersAndKeepsTheCount() {
    // A HEAD request stays a HEAD request for any argument: PostgREST ignores `return=minimal` for
    // functions, so a POST would return the whole result.
    val req =
      OperationBuilderImpl.rpc(
          "echo",
          mapOf("input_text" to listOf("a", "b"), "filter" to mapOf("k" to 1)),
          method = RpcMethod.HEAD,
          count = Count.EXACT,
          executor = executor
        )
        .toRequest()

    assertThat(req.httpMethod).isEqualTo("HEAD")
    assertThat(req.queryParams)
      .containsExactly("input_text" to "{a,b}", "filter" to """{"k":1}""")
      .inOrder()
    assertThat(req.headers).containsExactly("Prefer", "count=exact")
    assertThat(req.body).isNull()
  }

  @Test
  fun eachMethodSendsItsHttpMethod() {
    val requests =
      constantsOf<RpcMethod>().values.associateWith { method ->
        OperationBuilderImpl.rpc("my_rpc", mapOf("a" to 1), method = method, executor = executor)
          .toRequest()
      }

    for ((method, req) in requests) {
      assertWithMessage("$method").that(req.httpMethod).isEqualTo("$method")
    }
    assertThat(requests.getValue(RpcMethod.POST).body).isEqualTo(json("""{"a":1}"""))
    assertThat(requests.getValue(RpcMethod.POST).queryParams).isEmpty()
    for (method in listOf(RpcMethod.GET, RpcMethod.HEAD)) {
      assertWithMessage("$method")
        .that(requests.getValue(method).queryParams)
        .containsExactly("a" to "1")
      assertWithMessage("$method").that(requests.getValue(method).body).isNull()
    }
  }

  @Test
  fun postIsTheDefaultMethod() {
    val default = OperationBuilderImpl.rpc("my_rpc", mapOf("a" to 1), executor = executor)
    val post =
      OperationBuilderImpl.rpc(
        "my_rpc",
        mapOf("a" to 1),
        method = RpcMethod.POST,
        executor = executor
      )

    assertThat(post.toRequest()).isEqualTo(default.toRequest())
  }

  // --- filters and modifiers ---

  @Test
  fun filtersAndModifiersApplyToTheResult() {
    val req =
      OperationBuilderImpl.rpc("get_top_products", mapOf("min_rating" to 4), executor = executor)
        .gt("price", 50)
        .order("price", Order.DESCENDING)
        .limit(5)
        .toRequest()

    assertThat(req.httpMethod).isEqualTo("POST")
    assertThat(req.queryParams)
      .containsExactly("price" to "gt.50", "limit" to "5", "order" to "price.desc")
      .inOrder()
    assertThat(req.body).isEqualTo(json("""{"min_rating":4}"""))
  }

  @Test
  fun aCustomPreferHeaderIsMergedWithTheCount() {
    val req =
      OperationBuilderImpl.rpc("my_rpc", count = Count.PLANNED, executor = executor)
        .setHeader("Prefer", "tx=rollback")
        .toRequest()

    assertThat(req.headers).containsExactly("Prefer", "tx=rollback,count=planned")
  }

  @Test
  fun singleAndMaybeSingleOnAPostRequestAnObject() {
    val single = OperationBuilderImpl.rpc("my_rpc", executor = executor).single().toRequest()
    val maybeSingle =
      OperationBuilderImpl.rpc("my_rpc", executor = executor).maybeSingle().toRequest()

    assertThat(single.headers).containsExactly("Accept", "application/vnd.pgrst.object+json")
    assertThat(maybeSingle.headers).containsExactly("Accept", "application/vnd.pgrst.object+json")
  }

  /** The arguments that `rpc()` encodes with the serializer of [SearchArgs]. */
  private fun searchArgs() =
    defaultJson.encodeToJsonElement(SearchArgs.serializer(), SearchArgs("apple", listOf(1, 2)))

  @Serializable
  data class SearchArgs(@SerialName("search_text") val searchText: String, val ids: List<Int>)

  enum class Season {
    WINTER
  }
}
