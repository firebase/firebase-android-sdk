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

package com.google.firebase.dataconnect.connectors.keywords

import com.google.firebase.dataconnect.connectors.demo.DemoConnector
import com.google.firebase.dataconnect.connectors.demo.GetFooByIdQuery
import com.google.firebase.dataconnect.connectors.demo.execute
import com.google.firebase.dataconnect.connectors.demo.testutil.TestDemoConnectorFactory
import com.google.firebase.dataconnect.connectors.`typealias`.DeleteFooMutation
import com.google.firebase.dataconnect.connectors.`typealias`.DoMutation
import com.google.firebase.dataconnect.connectors.`typealias`.FooKey
import com.google.firebase.dataconnect.connectors.`typealias`.GetFoosByBarQuery
import com.google.firebase.dataconnect.connectors.`typealias`.GetTwoFoosByIdQuery
import com.google.firebase.dataconnect.connectors.`typealias`.InsertTwoFoosMutation
import com.google.firebase.dataconnect.connectors.`typealias`.KeywordsConnector
import com.google.firebase.dataconnect.connectors.`typealias`.ReturnQuery
import com.google.firebase.dataconnect.connectors.`typealias`.execute
import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class KeywordsConnectorIntegrationTest : DataConnectIntegrationTestBase() {

  @get:Rule
  val keywordsConnectorFactory =
    TestKeywordsConnectorFactory(firebaseAppFactory, dataConnectFactory)

  @get:Rule
  val demoConnectorFactory = TestDemoConnectorFactory(firebaseAppFactory, dataConnectFactory)

  private val keywordsConnector: KeywordsConnector by lazy {
    keywordsConnectorFactory.newInstance()
  }

  private val demoConnector: DemoConnector by lazy { demoConnectorFactory.newInstance() }

  @Test
  fun mutationNameShouldBeEscapedIfItIsAKotlinKeyword() = runTest {
    val id = Arb.alphanumericString(prefix = "id_").next(rs)
    val bar = Arb.alphanumericString(prefix = "bar_").next(rs)

    // The "do" mutation inserts a Foo into the database.
    val mutationResult = keywordsConnector.`do`.execute(id = id) { this.bar = bar }

    withClue("mutationResult") { mutationResult.data shouldBe DoMutation.Data(FooKey(id)) }
    val queryResult = demoConnector.getFooById.execute(id)
    withClue("queryResult") {
      queryResult.data shouldBe GetFooByIdQuery.Data(GetFooByIdQuery.Data.Foo(bar))
    }
  }

  @Test
  fun queryNameShouldBeEscapedIfItIsAKotlinKeyword() = runTest {
    val id = Arb.alphanumericString(prefix = "id_").next(rs)
    val bar = Arb.alphanumericString(prefix = "bar_").next(rs)
    demoConnector.insertFoo.execute(id = id) { this.bar = bar }

    // The "return" query gets a Foo from the database by its ID.
    val queryResult = keywordsConnector.`return`.execute(id)

    queryResult.data shouldBe ReturnQuery.Data(ReturnQuery.Data.Foo(bar))
  }

  @Test
  fun mutationVariableNamesShouldBeEscapedIfTheyAreKotlinKeywords() = runTest {
    val id = Arb.alphanumericString(prefix = "id_").next(rs)
    val bar = Arb.alphanumericString(prefix = "bar_").next(rs)
    demoConnector.insertFoo.execute(id = id) { this.bar = bar }

    // The "is" variable is the ID of the row to delete.
    val mutationResult = keywordsConnector.deleteFoo.execute(`is` = id)

    withClue("mutationResult") { mutationResult.data shouldBe DeleteFooMutation.Data(FooKey(id)) }
    val queryResult = demoConnector.getFooById.execute(id)
    withClue("queryResult") { queryResult.data.foo.shouldBeNull() }
  }

  @Test
  fun queryVariableNamesShouldBeEscapedIfTheyAreKotlinKeywords() = runTest {
    val id1 = Arb.alphanumericString(prefix = "id1_").next(rs)
    val id2 = Arb.alphanumericString(prefix = "id2_").next(rs)
    val id3 = Arb.alphanumericString(prefix = "id3_").next(rs)
    val bar = Arb.alphanumericString(prefix = "bar_").next(rs)
    demoConnector.insertFoo.execute(id = id1) { this.bar = bar }
    demoConnector.insertFoo.execute(id = id2) { this.bar = bar }
    demoConnector.insertFoo.execute(id = id3) { this.bar = bar }

    // The "as" variable is the value of "bar" whose rows to return.
    val queryResult = keywordsConnector.getFoosByBar.execute { `as` = bar }

    queryResult.data.foos.shouldContainExactlyInAnyOrder(
      GetFoosByBarQuery.Data.FoosItem(id1),
      GetFoosByBarQuery.Data.FoosItem(id2),
      GetFoosByBarQuery.Data.FoosItem(id3),
    )
  }

  @Test
  fun mutationSelectionSetFieldNamesShouldBeEscapedIfTheyAreKotlinKeywords() = runTest {
    val id1 = Arb.alphanumericString(prefix = "id1_").next(rs)
    val id2 = Arb.alphanumericString(prefix = "id2_").next(rs)
    val bar1 = Arb.alphanumericString(prefix = "bar1_").next(rs)
    val bar2 = Arb.alphanumericString(prefix = "bar2_").next(rs)

    val mutationResult =
      keywordsConnector.insertTwoFoos.execute(id1 = id1, id2 = id2) {
        this.bar1 = bar1
        this.bar2 = bar2
      }

    // The `val` and `var` fields are the keys of the 1st and 2nd inserted rows, respectively.
    withClue("mutationResult") {
      mutationResult.data shouldBe
        InsertTwoFoosMutation.Data(
          `val` = FooKey(id1),
          `var` = FooKey(id2),
        )
    }

    val queryResult1 = demoConnector.getFooById.execute(id1)
    withClue("queryResult1") {
      queryResult1.data shouldBe GetFooByIdQuery.Data(GetFooByIdQuery.Data.Foo(bar1))
    }
    val queryResult2 = demoConnector.getFooById.execute(id2)
    withClue("queryResult2") {
      queryResult2.data shouldBe GetFooByIdQuery.Data(GetFooByIdQuery.Data.Foo(bar2))
    }
  }

  @Test
  fun querySelectionSetFieldNamesShouldBeEscapedIfTheyAreKotlinKeywords() = runTest {
    val id1 = Arb.alphanumericString(prefix = "id1_").next(rs)
    val id2 = Arb.alphanumericString(prefix = "id2_").next(rs)
    val bar1 = Arb.alphanumericString(prefix = "bar1_").next(rs)
    val bar2 = Arb.alphanumericString(prefix = "bar2_").next(rs)
    demoConnector.insertFoo.execute(id = id1) { bar = bar1 }
    demoConnector.insertFoo.execute(id = id2) { bar = bar2 }

    val queryResult = keywordsConnector.getTwoFoosById.execute(id1 = id1, id2 = id2)

    // The `super` and `this` fields are the rows with the 1st and 2nd IDs, respectively.
    queryResult.data shouldBe
      GetTwoFoosByIdQuery.Data(
        `super` = GetTwoFoosByIdQuery.Data.Super(id = id1, bar = bar1),
        `this` = GetTwoFoosByIdQuery.Data.This(id = id2, bar = bar2),
      )
  }
}
