// Copyright 2024 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.dataconnect.connectors.keywords

import com.google.common.truth.Truth.assertThat
import com.google.firebase.dataconnect.connectors.demo.*
import com.google.firebase.dataconnect.connectors.demo.testutil.*
import com.google.firebase.dataconnect.connectors.`typealias`.*
import com.google.firebase.dataconnect.connectors.`typealias`.DeleteFooMutation
import com.google.firebase.dataconnect.connectors.`typealias`.FooKey
import com.google.firebase.dataconnect.connectors.`typealias`.GetFoosByBarQuery
import com.google.firebase.dataconnect.testutil.*
import kotlinx.coroutines.test.*
import org.junit.Rule
import org.junit.Test

class KeywordsConnectorIntegrationTest : DataConnectIntegrationTestBase() {

  @get:Rule
  val keywordsConnectorFactory =
    TestKeywordsConnectorFactory(firebaseAppFactory, dataConnectFactory)

  @get:Rule
  val demoConnectorFactory = TestDemoConnectorFactory(firebaseAppFactory, dataConnectFactory)

  val keywordsConnector: KeywordsConnector by lazy { keywordsConnectorFactory.newInstance() }
  val demoConnector: DemoConnector by lazy { demoConnectorFactory.newInstance() }

  @Test
  fun mutationNameShouldBeEscapedIfItIsAKotlinKeyword() = runTest {
    val id = "id_" + randomAlphanumericString()
    val bar = "bar_" + randomAlphanumericString()

    // The "do" mutation inserts a Foo into the database.
    val mutationResult = keywordsConnector.`do`.execute(id = id, bar = bar)

    assertThat(mutationResult.data).isEqualTo(DoMutation.Data(FooKey(id)))
    val queryResult = demoConnector.getFooById.execute(id)
    assertThat(queryResult.data).isEqualTo(GetFooByIdQuery.Data(GetFooByIdQuery.Data.Foo(bar)))
  }

  @Test
  fun queryNameShouldBeEscapedIfItIsAKotlinKeyword() = runTest {
    val id = "id_" + randomAlphanumericString()
    val bar = "bar_" + randomAlphanumericString()
    demoConnector.insertFoo.execute(id = id, bar = bar)

    // The "return" query gets a Foo from the database by its ID.
    val queryResult = keywordsConnector.`return`.execute(id)

    assertThat(queryResult.data).isEqualTo(ReturnQuery.Data(ReturnQuery.Data.Foo(bar)))
  }

  @Test
  fun mutationVariableNamesShouldBeEscapedIfTheyAreKotlinKeywords() = runTest {
    val id = "id_" + randomAlphanumericString()
    val bar = "bar_" + randomAlphanumericString()
    demoConnector.insertFoo.execute(id = id, bar = bar)

    // The "is" variable is the ID of the row to delete.
    val mutationResult = keywordsConnector.deleteFoo.execute(`is` = id)

    assertThat(mutationResult.data).isEqualTo(DeleteFooMutation.Data(FooKey(id)))
    val queryResult = demoConnector.getFooById.execute(id)
    assertThat(queryResult.data.foo).isNull()
  }

  @Test
  fun queryVariableNamesShouldBeEscapedIfTheyAreKotlinKeywords() = runTest {
    val id1 = "id1_" + randomAlphanumericString()
    val id2 = "id2_" + randomAlphanumericString()
    val id3 = "id3_" + randomAlphanumericString()
    val bar = "bar_" + randomAlphanumericString()
    demoConnector.insertFoo.execute(id = id1, bar = bar)
    demoConnector.insertFoo.execute(id = id2, bar = bar)
    demoConnector.insertFoo.execute(id = id3, bar = bar)

    // The "as" variable is the value of "bar" whose rows to return.
    val queryResult = keywordsConnector.getFoosByBar.execute(`as` = bar)

    assertThat(queryResult.data.foos)
      .containsExactly(
        GetFoosByBarQuery.Data.FoosItem(id1),
        GetFoosByBarQuery.Data.FoosItem(id2),
        GetFoosByBarQuery.Data.FoosItem(id3),
      )
  }

  @Test
  fun mutationSelectionSetFieldNamesShouldBeEscapedIfTheyAreKotlinKeywords() = runTest {
    val id1 = "id1_" + randomAlphanumericString()
    val id2 = "id2_" + randomAlphanumericString()
    val bar1 = "bar1_" + randomAlphanumericString()
    val bar2 = "bar2_" + randomAlphanumericString()

    val mutationResult =
      keywordsConnector.insertTwoFoos.execute(id1 = id1, id2 = id2, bar1 = bar1, bar2 = bar2)

    // The `val` and `var` fields are the keys of the 1st and 2nd inserted rows, respectively.
    assertThat(mutationResult.data)
      .isEqualTo(
        InsertTwoFoosMutation.Data(
          `val` = FooKey(id1),
          `var` = FooKey(id2),
        )
      )
    val queryResult1 = demoConnector.getFooById.execute(id1)
    assertThat(queryResult1.data).isEqualTo(GetFooByIdQuery.Data(GetFooByIdQuery.Data.Foo(bar1)))
    val queryResult2 = demoConnector.getFooById.execute(id2)
    assertThat(queryResult2.data).isEqualTo(GetFooByIdQuery.Data(GetFooByIdQuery.Data.Foo(bar2)))
  }

  @Test
  fun querySelectionSetFieldNamesShouldBeEscapedIfTheyAreKotlinKeywords() = runTest {
    val id1 = "id1_" + randomAlphanumericString()
    val id2 = "id2_" + randomAlphanumericString()
    val bar1 = "bar1_" + randomAlphanumericString()
    val bar2 = "bar2_" + randomAlphanumericString()
    demoConnector.insertFoo.execute(id = id1, bar = bar1)
    demoConnector.insertFoo.execute(id = id2, bar = bar2)

    val queryResult = keywordsConnector.getTwoFoosById.execute(id1 = id1, id2 = id2)

    // The `super` and `this` fields are the rows with the 1st and 2nd IDs, respectively.
    assertThat(queryResult.data)
      .isEqualTo(
        GetTwoFoosByIdQuery.Data(
          `super` = GetTwoFoosByIdQuery.Data.Super(id = id1, bar = bar1),
          `this` = GetTwoFoosByIdQuery.Data.This(id = id2, bar = bar2),
        )
      )
  }
}
