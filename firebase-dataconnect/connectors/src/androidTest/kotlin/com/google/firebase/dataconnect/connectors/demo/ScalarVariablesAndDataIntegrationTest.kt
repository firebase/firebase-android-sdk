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

package com.google.firebase.dataconnect.connectors.demo

import com.google.common.truth.Truth.assertThat
import com.google.firebase.dataconnect.connectors.demo.testutil.DemoConnectorIntegrationTestBase
import com.google.firebase.util.nextAlphanumericString
import kotlin.random.Random
import kotlinx.coroutines.test.*
import org.junit.Test

class ScalarVariablesAndDataIntegrationTest : DemoConnectorIntegrationTestBase() {

  @Test
  fun mutationCorrectlySendsNonNullStringVariables() = runTest {
    val id = Random.nextAlphanumericString()

    connector.insertOneNonNullStringField.execute(id = id, value = "TestString")

    val queryResult = connector.getOneNonNullStringFieldById.execute(id)

    assertThat(queryResult.data.oneNonNullStringField?.value).isEqualTo("TestString")
  }

  @Test
  fun mutationCorrectlySendsNullableStringVariables() = runTest {
    val idForNonNullValue = "NonNull_" + Random.nextAlphanumericString()
    val idForNullValue = "Null_" + Random.nextAlphanumericString()

    connector.insertOneNullableStringField.execute(id = idForNonNullValue, value = "TestString")
    connector.insertOneNullableStringField.execute(id = idForNullValue, value = null)

    assertThat(
        connector.getOneNullableStringFieldById
          .execute(idForNonNullValue)
          .data
          .oneNullableStringField
          ?.value
      )
      .isEqualTo("TestString")
    assertThat(
        connector.getOneNullableStringFieldById
          .execute(idForNullValue)
          .data
          .oneNullableStringField
          ?.value
      )
      .isNull()
  }

  @Test
  fun mutationCorrectlySendsStringListVariables() = runTest {
    val idForNonEmptyList = "NonEmpty_" + Random.nextAlphanumericString()
    val idForEmptyList = "Empty_" + Random.nextAlphanumericString()

    connector.insertOneStringListField.execute(id = idForNonEmptyList, value = listOf("a", "b"))
    connector.insertOneStringListField.execute(id = idForEmptyList, value = emptyList())

    assertThat(
        connector.getOneStringListFieldById
          .execute(idForNonEmptyList)
          .data
          .oneStringListField
          ?.value
      )
      .containsExactly("a", "b")
      .inOrder()
    assertThat(
        connector.getOneStringListFieldById.execute(idForEmptyList).data.oneStringListField?.value
      )
      .isEmpty()
  }

  // TODO: Repeat the tests above for Int, Float, and Boolean.
  //  Make sure to test boundary values, like Int.MAX_VALUE, Float.NaN, true, and false.
}
