/*
 * Copyright 2024 Google LLC
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

package com.google.firebase.dataconnect.connectors.demo.testutil

import com.google.firebase.dataconnect.QueryResult
import com.google.firebase.dataconnect.connectors.demo.DemoConnector
import com.google.firebase.dataconnect.connectors.demo.GetFooByIdQuery
import com.google.firebase.dataconnect.connectors.demo.GetFoosByBarQuery
import com.google.firebase.dataconnect.connectors.demo.execute
import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.MatcherResult.Companion.invoke
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import org.junit.Rule

abstract class DemoConnectorIntegrationTestBase : DataConnectIntegrationTestBase() {

  @get:Rule
  val demoConnectorFactory = TestDemoConnectorFactory(firebaseAppFactory, dataConnectFactory)

  val connector: DemoConnector by lazy { demoConnectorFactory.newInstance() }

  fun Arb.Companion.fooId(): Arb<String> = Arb.alphanumericString(prefix = "FooId_")
  fun Arb.Companion.bar(): Arb<String> = Arb.alphanumericString(prefix = "Bar_")

  suspend fun fooWithId(id: String): QueryResult<GetFooByIdQuery.Data, GetFooByIdQuery.Variables> =
    connector.getFooById.execute(id)

  suspend fun foosWithBar(
    bar: String
  ): QueryResult<GetFoosByBarQuery.Data, GetFoosByBarQuery.Variables> =
    connector.getFoosByBar.execute { this.bar = bar }

  suspend fun insertFooWithRandomId(): String = connector.insertFooWithRandomId()

  private suspend fun DemoConnector.insertFooWithRandomId(): String {
    val fooId = Arb.fooId().next(rs)
    insertFoo.execute(id = fooId) { bar = Arb.bar().next(rs) }
    return fooId
  }

  companion object {
    @JvmName("getFooByIdShouldNotExist")
    fun QueryResult<GetFooByIdQuery.Data, GetFooByIdQuery.Variables>.shouldNotExist() {
      this shouldNot GetFooById.exist()
    }

    infix fun QueryResult<GetFooByIdQuery.Data, GetFooByIdQuery.Variables>.shouldExistWithBar(
      bar: String
    ) {
      this should GetFooById.existWithBar(bar)
    }

    @JvmName("GetFoosByBarShouldExist")
    fun QueryResult<GetFoosByBarQuery.Data, GetFoosByBarQuery.Variables>.shouldExist() {
      this should GetFoosByBar.exist()
    }

    @JvmName("GetFoosByBarShouldNotExist")
    fun QueryResult<GetFoosByBarQuery.Data, GetFoosByBarQuery.Variables>.shouldNotExist() {
      this shouldNot GetFoosByBar.exist()
    }

    object GetFooById {
      fun exist() =
        object : Matcher<QueryResult<GetFooByIdQuery.Data, GetFooByIdQuery.Variables>> {
          override fun test(
            value: QueryResult<GetFooByIdQuery.Data, GetFooByIdQuery.Variables>
          ): MatcherResult {
            return invoke(
              value.data.foo !== null,
              { "Expected Foo with ID ${value.ref.variables.id} to exist, but it did not exist." },
              {
                "Expected Foo with ID ${value.ref.variables.id} to not exist," +
                  " but it did exist with bar: ${value.data.foo!!.bar}."
              }
            )
          }
        }

      fun existWithBar(bar: String) =
        object : Matcher<QueryResult<GetFooByIdQuery.Data, GetFooByIdQuery.Variables>> {
          override fun test(
            value: QueryResult<GetFooByIdQuery.Data, GetFooByIdQuery.Variables>
          ): MatcherResult {
            val exists = value.data.foo !== null
            val barMatches = value.data.foo.let { it?.bar == bar }
            return invoke(
              exists && barMatches,
              {
                "Expected Foo with ID ${value.ref.variables.id} to have bar=$bar, " +
                  if (exists) "but bar does not match: ${value.data.foo?.bar}"
                  else "but no Foo with that ID exists"
              },
              {
                throw Exception(
                  "existWithBar() does not support negation," +
                    " because it's ambiguous if the 'not' refers to 'exist' or 'bar'."
                )
              }
            )
          }
        }
    }

    object GetFoosByBar {

      fun exist() =
        object : Matcher<QueryResult<GetFoosByBarQuery.Data, GetFoosByBarQuery.Variables>> {
          override fun test(
            value: QueryResult<GetFoosByBarQuery.Data, GetFoosByBarQuery.Variables>
          ): MatcherResult {
            return invoke(
              value.data.foos.isNotEmpty(),
              {
                "Expected at least 1 Foo with Bar ${value.ref.variables.bar} to exist," +
                  " but none existed."
              },
              {
                "Expected no Foos with Bar ${value.ref.variables.bar} to exist," +
                  " but ${value.data.foos.size} existed."
              },
            )
          }
        }
    }
  }
}
