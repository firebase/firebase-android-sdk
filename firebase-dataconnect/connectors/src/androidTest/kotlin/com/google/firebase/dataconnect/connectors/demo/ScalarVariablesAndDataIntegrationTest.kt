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
import com.google.firebase.dataconnect.testutil.randomAlphanumericString
import kotlinx.coroutines.test.*
import org.junit.Ignore
import org.junit.Test

class ScalarVariablesAndDataIntegrationTest : DemoConnectorIntegrationTestBase() {

  @Test
  fun stringVariants() = runTest {
    val id = randomAlphanumericString()

    connector.insertStringVariants.execute(
      id = id,
      nonNullWithNonEmptyValue = "some non-empty value for a *non*-nullable field",
      nonNullWithEmptyValue = "",
      nullableWithNullValue = null,
      nullableWithNonNullValue = "some non-empty value for a *nullable* field",
      nullableWithEmptyValue = "",
      emptyList = emptyList(),
      nonEmptyList = listOf("foo", "", "BAR")
    )

    val queryResult = connector.getStringVariantsById.execute(id)
    assertThat(queryResult.data.stringVariants)
      .isEqualTo(
        GetStringVariantsByIdQuery.Data.StringVariants(
          nonNullWithNonEmptyValue = "some non-empty value for a *non*-nullable field",
          nonNullWithEmptyValue = "",
          nullableWithNullValue = null,
          nullableWithNonNullValue = "some non-empty value for a *nullable* field",
          nullableWithEmptyValue = "",
          emptyList = emptyList(),
          nonEmptyList = listOf("foo", "", "BAR")
        )
      )
  }

  @Test()
  @Ignore("Un-ignore this test once the emulator fixes its handling of Int64 (b/331596857)")
  fun int64Variants() = runTest {
    val id = randomAlphanumericString()

    connector.insertInt64variants.execute(
      id = id,
      nonNullWithZeroValue = 0,
      nonNullWithPositiveValue = 4242424242424242,
      nonNullWithNegativeValue = -4242424242424242,
      nonNullWithMaxValue = Long.MAX_VALUE,
      nonNullWithMinValue = Long.MIN_VALUE,
      nullableWithNullValue = null,
      nullableWithZeroValue = 0,
      nullableWithPositiveValue = 2424242424242424,
      nullableWithNegativeValue = -2424242424242424,
      nullableWithMaxValue = Long.MAX_VALUE,
      nullableWithMinValue = Long.MIN_VALUE,
      emptyList = emptyList(),
      nonEmptyList = listOf(0, -1, 1, 99, -99, Long.MIN_VALUE, Long.MAX_VALUE)
    )

    val queryResult = connector.getInt64variantsById.execute(id)
    assertThat(queryResult.data.int64Variants)
      .isEqualTo(
        GetInt64variantsByIdQuery.Data.Int64variants(
          nonNullWithZeroValue = 0,
          nonNullWithPositiveValue = 4242424242424242,
          nonNullWithNegativeValue = -4242424242424242,
          nonNullWithMaxValue = Long.MAX_VALUE,
          nonNullWithMinValue = Long.MIN_VALUE,
          nullableWithNullValue = null,
          nullableWithZeroValue = 0,
          nullableWithPositiveValue = 2424242424242424,
          nullableWithNegativeValue = -2424242424242424,
          nullableWithMaxValue = Long.MAX_VALUE,
          nullableWithMinValue = Long.MIN_VALUE,
          emptyList = emptyList(),
          nonEmptyList = listOf(0, -1, 1, 99, -99, Long.MIN_VALUE, Long.MAX_VALUE)
        )
      )
  }

  // TODO: Repeat the tests above for Int, Float, and Boolean.
  //  Make sure to test boundary values, like Int.MAX_VALUE, Float.NaN, true, and false.
}
