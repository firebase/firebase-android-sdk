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

package com.google.firebase.dataconnect.connectors.demo

import com.google.common.truth.Truth.assertThat
import com.google.firebase.dataconnect.connectors.demo.testutil.DemoConnectorIntegrationTestBase
import com.google.firebase.dataconnect.testutil.MAX_SAFE_INTEGER
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Ignore
import org.junit.Test

class ScalarVariablesAndDataIntegrationTest : DemoConnectorIntegrationTestBase() {

  @Test
  fun insertStringVariants() = runTest {
    val key =
      connector.insertStringVariants
        .execute(
          nonNullWithNonEmptyValue = "some non-empty value for a *non*-nullable field",
          nonNullWithEmptyValue = "",
        ) {
          nullableWithNullValue = null
          nullableWithNonNullValue = "some non-empty value for a *nullable* field"
          nullableWithEmptyValue = ""
        }
        .data
        .key

    val queryResult = connector.getStringVariantsByKey.execute(key)
    assertThat(queryResult.data.stringVariants)
      .isEqualTo(
        GetStringVariantsByKeyQuery.Data.StringVariants(
          nonNullWithNonEmptyValue = "some non-empty value for a *non*-nullable field",
          nonNullWithEmptyValue = "",
          nullableWithNullValue = null,
          nullableWithNonNullValue = "some non-empty value for a *nullable* field",
          nullableWithEmptyValue = "",
        )
      )
  }

  @Test
  fun insertStringVariantsWithDefaultValues() = runTest {
    val key = connector.insertStringVariantsWithHardcodedDefaults.execute {}.data.key

    val queryResult = connector.getStringVariantsByKey.execute(key)
    assertThat(queryResult.data.stringVariants)
      .isEqualTo(
        GetStringVariantsByKeyQuery.Data.StringVariants(
          nonNullWithNonEmptyValue = "pfnk98yqqs",
          nonNullWithEmptyValue = "",
          nullableWithNullValue = null,
          nullableWithNonNullValue = "af8k72s98t",
          nullableWithEmptyValue = "",
        )
      )
  }

  @Test
  fun updateStringVariantsToNonNullValues() = runTest {
    val key =
      connector.insertStringVariants
        .execute(
          nonNullWithNonEmptyValue = "d94gpbmwf6",
          nonNullWithEmptyValue = "",
        ) {
          nullableWithNullValue = null
          nullableWithNonNullValue = "wcwkenscxd"
          nullableWithEmptyValue = ""
        }
        .data
        .key

    connector.updateStringVariantsByKey.execute(key) {
      nonNullWithNonEmptyValue = ""
      nonNullWithEmptyValue = "q3vvetx52x"
      nullableWithNullValue = "d54kpn29pb"
      nullableWithNonNullValue = "sfbm8epy94"
      nullableWithEmptyValue = "pxhz7awrz9"
    }

    val queryResult = connector.getStringVariantsByKey.execute(key)
    assertThat(queryResult.data.stringVariants)
      .isEqualTo(
        GetStringVariantsByKeyQuery.Data.StringVariants(
          nonNullWithNonEmptyValue = "",
          nonNullWithEmptyValue = "q3vvetx52x",
          nullableWithNullValue = "d54kpn29pb",
          nullableWithNonNullValue = "sfbm8epy94",
          nullableWithEmptyValue = "pxhz7awrz9",
        )
      )
  }

  @Test
  fun updateStringVariantsToNullValues() = runTest {
    val key =
      connector.insertStringVariants
        .execute(
          nonNullWithNonEmptyValue = "pqb9vc52pp",
          nonNullWithEmptyValue = "",
        ) {
          nullableWithNullValue = null
          nullableWithNonNullValue = "xyka3gsmad"
          nullableWithEmptyValue = ""
        }
        .data
        .key

    connector.updateStringVariantsByKey.execute(key) {
      nullableWithNullValue = null
      nullableWithNonNullValue = null
      nullableWithEmptyValue = null
    }

    val queryResult = connector.getStringVariantsByKey.execute(key)
    assertThat(queryResult.data.stringVariants)
      .isEqualTo(
        GetStringVariantsByKeyQuery.Data.StringVariants(
          nonNullWithNonEmptyValue = "pqb9vc52pp",
          nonNullWithEmptyValue = "",
          nullableWithNullValue = null,
          nullableWithNonNullValue = null,
          nullableWithEmptyValue = null,
        )
      )
  }

  @Test
  fun updateStringVariantsToUndefinedValues() = runTest {
    val key =
      connector.insertStringVariants
        .execute(
          nonNullWithNonEmptyValue = "6t25b9jyxc",
          nonNullWithEmptyValue = "",
        ) {
          nullableWithNullValue = null
          nullableWithNonNullValue = "kybbsaxpkw"
          nullableWithEmptyValue = ""
        }
        .data
        .key

    connector.updateStringVariantsByKey.execute(key) {}

    val queryResult = connector.getStringVariantsByKey.execute(key)
    assertThat(queryResult.data.stringVariants)
      .isEqualTo(
        GetStringVariantsByKeyQuery.Data.StringVariants(
          nonNullWithNonEmptyValue = "6t25b9jyxc",
          nonNullWithEmptyValue = "",
          nullableWithNullValue = null,
          nullableWithNonNullValue = "kybbsaxpkw",
          nullableWithEmptyValue = "",
        )
      )
  }

  @Test
  fun insertIntVariants() = runTest {
    val key =
      connector.insertIntVariants
        .execute(
          nonNullWithZeroValue = 0,
          nonNullWithPositiveValue = 42424242,
          nonNullWithNegativeValue = -42424242,
          nonNullWithMaxValue = Int.MAX_VALUE,
          nonNullWithMinValue = Int.MIN_VALUE,
        ) {
          nullableWithNullValue = null
          nullableWithZeroValue = 0
          nullableWithPositiveValue = 24242424
          nullableWithNegativeValue = -24242424
          nullableWithMaxValue = Int.MAX_VALUE
          nullableWithMinValue = Int.MIN_VALUE
        }
        .data
        .key

    val queryResult = connector.getIntVariantsByKey.execute(key)
    assertThat(queryResult.data.intVariants)
      .isEqualTo(
        GetIntVariantsByKeyQuery.Data.IntVariants(
          nonNullWithZeroValue = 0,
          nonNullWithPositiveValue = 42424242,
          nonNullWithNegativeValue = -42424242,
          nonNullWithMaxValue = Int.MAX_VALUE,
          nonNullWithMinValue = Int.MIN_VALUE,
          nullableWithNullValue = null,
          nullableWithZeroValue = 0,
          nullableWithPositiveValue = 24242424,
          nullableWithNegativeValue = -24242424,
          nullableWithMaxValue = Int.MAX_VALUE,
          nullableWithMinValue = Int.MIN_VALUE,
        )
      )
  }

  @Test
  fun insertIntVariantsWithDefaultValues() = runTest {
    val key = connector.insertIntVariantsWithHardcodedDefaults.execute {}.data.key

    val queryResult = connector.getIntVariantsByKey.execute(key)
    assertThat(queryResult.data.intVariants)
      .isEqualTo(
        GetIntVariantsByKeyQuery.Data.IntVariants(
          nonNullWithZeroValue = 0,
          nonNullWithPositiveValue = 819425,
          nonNullWithNegativeValue = -435970,
          nonNullWithMaxValue = Int.MAX_VALUE,
          nonNullWithMinValue = Int.MIN_VALUE,
          nullableWithNullValue = null,
          nullableWithZeroValue = 0,
          nullableWithPositiveValue = 635166,
          nullableWithNegativeValue = -171993,
          nullableWithMaxValue = Int.MAX_VALUE,
          nullableWithMinValue = Int.MIN_VALUE,
        )
      )
  }

  @Test
  fun updateIntVariantsToNonNullValues() = runTest {
    val key =
      connector.insertIntVariants
        .execute(
          nonNullWithZeroValue = 0,
          nonNullWithPositiveValue = 42424242,
          nonNullWithNegativeValue = -42424242,
          nonNullWithMaxValue = Int.MAX_VALUE,
          nonNullWithMinValue = Int.MIN_VALUE,
        ) {
          nullableWithNullValue = null
          nullableWithZeroValue = 0
          nullableWithPositiveValue = 24242424
          nullableWithNegativeValue = -24242424
          nullableWithMaxValue = Int.MAX_VALUE
          nullableWithMinValue = Int.MIN_VALUE
        }
        .data
        .key

    connector.updateIntVariantsByKey.execute(key) {
      nonNullWithZeroValue = 7878
      nonNullWithPositiveValue = Int.MAX_VALUE
      nonNullWithNegativeValue = Int.MIN_VALUE
      nonNullWithMaxValue = 1
      nonNullWithMinValue = -1
      nullableWithNullValue = 8787
      nullableWithZeroValue = 0
      nullableWithPositiveValue = Int.MAX_VALUE
      nullableWithNegativeValue = Int.MIN_VALUE
      nullableWithMaxValue = 1
      nullableWithMinValue = -1
    }

    val queryResult = connector.getIntVariantsByKey.execute(key)
    assertThat(queryResult.data.intVariants)
      .isEqualTo(
        GetIntVariantsByKeyQuery.Data.IntVariants(
          nonNullWithZeroValue = 7878,
          nonNullWithPositiveValue = Int.MAX_VALUE,
          nonNullWithNegativeValue = Int.MIN_VALUE,
          nonNullWithMaxValue = 1,
          nonNullWithMinValue = -1,
          nullableWithNullValue = 8787,
          nullableWithZeroValue = 0,
          nullableWithPositiveValue = Int.MAX_VALUE,
          nullableWithNegativeValue = Int.MIN_VALUE,
          nullableWithMaxValue = 1,
          nullableWithMinValue = -1,
        )
      )
  }

  @Test
  fun updateIntVariantsToNullValues() = runTest {
    val key =
      connector.insertIntVariants
        .execute(
          nonNullWithZeroValue = 0,
          nonNullWithPositiveValue = 42424242,
          nonNullWithNegativeValue = -42424242,
          nonNullWithMaxValue = Int.MAX_VALUE,
          nonNullWithMinValue = Int.MIN_VALUE,
        ) {
          nullableWithNullValue = null
          nullableWithZeroValue = 0
          nullableWithPositiveValue = 24242424
          nullableWithNegativeValue = -24242424
          nullableWithMaxValue = Int.MAX_VALUE
          nullableWithMinValue = Int.MIN_VALUE
        }
        .data
        .key

    connector.updateIntVariantsByKey.execute(key) {
      nullableWithNullValue = null
      nullableWithZeroValue = null
      nullableWithPositiveValue = null
      nullableWithNegativeValue = null
      nullableWithMaxValue = null
      nullableWithMinValue = null
    }

    val queryResult = connector.getIntVariantsByKey.execute(key)
    assertThat(queryResult.data.intVariants)
      .isEqualTo(
        GetIntVariantsByKeyQuery.Data.IntVariants(
          nonNullWithZeroValue = 0,
          nonNullWithPositiveValue = 42424242,
          nonNullWithNegativeValue = -42424242,
          nonNullWithMaxValue = Int.MAX_VALUE,
          nonNullWithMinValue = Int.MIN_VALUE,
          nullableWithNullValue = null,
          nullableWithZeroValue = null,
          nullableWithPositiveValue = null,
          nullableWithNegativeValue = null,
          nullableWithMaxValue = null,
          nullableWithMinValue = null,
        )
      )
  }

  @Test
  fun updateIntVariantsToUndefinedValues() = runTest {
    val key =
      connector.insertIntVariants
        .execute(
          nonNullWithZeroValue = 0,
          nonNullWithPositiveValue = 42424242,
          nonNullWithNegativeValue = -42424242,
          nonNullWithMaxValue = Int.MAX_VALUE,
          nonNullWithMinValue = Int.MIN_VALUE,
        ) {
          nullableWithNullValue = null
          nullableWithZeroValue = 0
          nullableWithPositiveValue = 24242424
          nullableWithNegativeValue = -24242424
          nullableWithMaxValue = Int.MAX_VALUE
          nullableWithMinValue = Int.MIN_VALUE
        }
        .data
        .key

    connector.updateIntVariantsByKey.execute(key) {}

    val queryResult = connector.getIntVariantsByKey.execute(key)
    assertThat(queryResult.data.intVariants)
      .isEqualTo(
        GetIntVariantsByKeyQuery.Data.IntVariants(
          nonNullWithZeroValue = 0,
          nonNullWithPositiveValue = 42424242,
          nonNullWithNegativeValue = -42424242,
          nonNullWithMaxValue = Int.MAX_VALUE,
          nonNullWithMinValue = Int.MIN_VALUE,
          nullableWithNullValue = null,
          nullableWithZeroValue = 0,
          nullableWithPositiveValue = 24242424,
          nullableWithNegativeValue = -24242424,
          nullableWithMaxValue = Int.MAX_VALUE,
          nullableWithMinValue = Int.MIN_VALUE,
        )
      )
  }

  @Test
  fun insertFloatVariants() = runTest {
    val key =
      connector.insertFloatVariants
        .execute(
          nonNullWithZeroValue = 0.0,
          nonNullWithNegativeZeroValue = -0.0,
          nonNullWithPositiveValue = 123.456,
          nonNullWithNegativeValue = -987.654,
          nonNullWithMaxValue = Double.MAX_VALUE,
          nonNullWithMinValue = Double.MIN_VALUE,
          nonNullWithMaxSafeIntegerValue = MAX_SAFE_INTEGER,
        ) {
          nullableWithNullValue = null
          nullableWithZeroValue = 0.0
          nullableWithNegativeZeroValue = 0.0
          nullableWithPositiveValue = 789.012
          nullableWithNegativeValue = -321.098
          nullableWithMaxValue = Double.MAX_VALUE
          nullableWithMinValue = Double.MIN_VALUE
          nullableWithMaxSafeIntegerValue = MAX_SAFE_INTEGER
        }
        .data
        .key

    val queryResult = connector.getFloatVariantsByKey.execute(key)
    assertThat(queryResult.data.floatVariants)
      .isEqualTo(
        GetFloatVariantsByKeyQuery.Data.FloatVariants(
          nonNullWithZeroValue = 0.0,
          nonNullWithNegativeZeroValue = 0.0,
          nonNullWithPositiveValue = 123.456,
          nonNullWithNegativeValue = -987.654,
          nonNullWithMaxValue = Double.MAX_VALUE,
          nonNullWithMinValue = Double.MIN_VALUE,
          nonNullWithMaxSafeIntegerValue = MAX_SAFE_INTEGER,
          nullableWithNullValue = null,
          nullableWithZeroValue = 0.0,
          nullableWithNegativeZeroValue = 0.0,
          nullableWithPositiveValue = 789.012,
          nullableWithNegativeValue = -321.098,
          nullableWithMaxValue = Double.MAX_VALUE,
          nullableWithMinValue = Double.MIN_VALUE,
          nullableWithMaxSafeIntegerValue = MAX_SAFE_INTEGER,
        )
      )
  }

  @Test
  fun insertFloatVariantsWithDefaultValues() = runTest {
    val key = connector.insertFloatVariantsWithHardcodedDefaults.execute {}.data.key

    val queryResult = connector.getFloatVariantsByKey.execute(key)
    assertThat(queryResult.data.floatVariants)
      .isEqualTo(
        GetFloatVariantsByKeyQuery.Data.FloatVariants(
          nonNullWithZeroValue = 0.0,
          nonNullWithNegativeZeroValue = 0.0,
          nonNullWithPositiveValue = 750.452,
          nonNullWithNegativeValue = -598.351,
          nonNullWithMaxValue = Double.MAX_VALUE,
          nonNullWithMinValue = Double.MIN_VALUE,
          nonNullWithMaxSafeIntegerValue = MAX_SAFE_INTEGER,
          nullableWithNullValue = null,
          nullableWithZeroValue = 0.0,
          nullableWithNegativeZeroValue = 0.0,
          nullableWithPositiveValue = 597.650,
          nullableWithNegativeValue = -181.366,
          nullableWithMaxValue = Double.MAX_VALUE,
          nullableWithMinValue = Double.MIN_VALUE,
          nullableWithMaxSafeIntegerValue = MAX_SAFE_INTEGER,
        )
      )
  }

  @Test
  fun updateFloatVariantsToNonNullValues() = runTest {
    val key =
      connector.insertFloatVariants
        .execute(
          nonNullWithZeroValue = 0.0,
          nonNullWithNegativeZeroValue = -0.0,
          nonNullWithPositiveValue = 662.096,
          nonNullWithNegativeValue = -817.024,
          nonNullWithMaxValue = Double.MAX_VALUE,
          nonNullWithMinValue = Double.MIN_VALUE,
          nonNullWithMaxSafeIntegerValue = MAX_SAFE_INTEGER,
        ) {
          nullableWithNullValue = null
          nullableWithZeroValue = 0.0
          nullableWithNegativeZeroValue = 0.0
          nullableWithPositiveValue = 990.273
          nullableWithNegativeValue = -383.185
          nullableWithMaxValue = Double.MAX_VALUE
          nullableWithMinValue = Double.MIN_VALUE
          nullableWithMaxSafeIntegerValue = MAX_SAFE_INTEGER
        }
        .data
        .key

    connector.updateFloatVariantsByKey.execute(key) {
      nonNullWithZeroValue = Double.MAX_VALUE
      nonNullWithNegativeZeroValue = Double.MIN_VALUE
      nonNullWithPositiveValue = MAX_SAFE_INTEGER
      nonNullWithNegativeValue = -0.0
      nonNullWithMaxValue = -270.396
      nonNullWithMinValue = 470.563
      nonNullWithMaxSafeIntegerValue = 0.0
      nullableWithNullValue = 607.386
      nullableWithZeroValue = Double.MIN_VALUE
      nullableWithNegativeZeroValue = MAX_SAFE_INTEGER
      nullableWithPositiveValue = -0.0
      nullableWithNegativeValue = MAX_SAFE_INTEGER
      nullableWithMaxValue = -930.342
      nullableWithMinValue = 563.398
      nullableWithMaxSafeIntegerValue = 0.0
    }

    val queryResult = connector.getFloatVariantsByKey.execute(key)
    assertThat(queryResult.data.floatVariants)
      .isEqualTo(
        GetFloatVariantsByKeyQuery.Data.FloatVariants(
          nonNullWithZeroValue = Double.MAX_VALUE,
          nonNullWithNegativeZeroValue = Double.MIN_VALUE,
          nonNullWithPositiveValue = MAX_SAFE_INTEGER,
          nonNullWithNegativeValue = 0.0,
          nonNullWithMaxValue = -270.396,
          nonNullWithMinValue = 470.563,
          nonNullWithMaxSafeIntegerValue = 0.0,
          nullableWithNullValue = 607.386,
          nullableWithZeroValue = Double.MIN_VALUE,
          nullableWithNegativeZeroValue = MAX_SAFE_INTEGER,
          nullableWithPositiveValue = 0.0,
          nullableWithNegativeValue = MAX_SAFE_INTEGER,
          nullableWithMaxValue = -930.342,
          nullableWithMinValue = 563.398,
          nullableWithMaxSafeIntegerValue = 0.0,
        )
      )
  }

  @Test
  fun updateFloatVariantsToNullValues() = runTest {
    val key =
      connector.insertFloatVariants
        .execute(
          nonNullWithZeroValue = 0.0,
          nonNullWithNegativeZeroValue = -0.0,
          nonNullWithPositiveValue = 225.954,
          nonNullWithNegativeValue = -432.366,
          nonNullWithMaxValue = Double.MAX_VALUE,
          nonNullWithMinValue = Double.MIN_VALUE,
          nonNullWithMaxSafeIntegerValue = MAX_SAFE_INTEGER,
        ) {
          nullableWithNullValue = null
          nullableWithZeroValue = 0.0
          nullableWithNegativeZeroValue = 0.0
          nullableWithPositiveValue = 446.040
          nullableWithNegativeValue = -573.104
          nullableWithMaxValue = Double.MAX_VALUE
          nullableWithMinValue = Double.MIN_VALUE
          nullableWithMaxSafeIntegerValue = MAX_SAFE_INTEGER
        }
        .data
        .key

    connector.updateFloatVariantsByKey.execute(key) {
      nullableWithNullValue = null
      nullableWithZeroValue = null
      nullableWithNegativeZeroValue = null
      nullableWithPositiveValue = null
      nullableWithNegativeValue = null
      nullableWithMaxValue = null
      nullableWithMinValue = null
      nullableWithMaxSafeIntegerValue = null
    }

    val queryResult = connector.getFloatVariantsByKey.execute(key)
    assertThat(queryResult.data.floatVariants)
      .isEqualTo(
        GetFloatVariantsByKeyQuery.Data.FloatVariants(
          nonNullWithZeroValue = 0.0,
          nonNullWithNegativeZeroValue = 0.0,
          nonNullWithPositiveValue = 225.954,
          nonNullWithNegativeValue = -432.366,
          nonNullWithMaxValue = Double.MAX_VALUE,
          nonNullWithMinValue = Double.MIN_VALUE,
          nonNullWithMaxSafeIntegerValue = MAX_SAFE_INTEGER,
          nullableWithNullValue = null,
          nullableWithZeroValue = null,
          nullableWithNegativeZeroValue = null,
          nullableWithPositiveValue = null,
          nullableWithNegativeValue = null,
          nullableWithMaxValue = null,
          nullableWithMinValue = null,
          nullableWithMaxSafeIntegerValue = null,
        )
      )
  }

  @Test
  fun updateFloatVariantsToUndefinedValues() = runTest {
    val key =
      connector.insertFloatVariants
        .execute(
          nonNullWithZeroValue = 0.0,
          nonNullWithNegativeZeroValue = -0.0,
          nonNullWithPositiveValue = 969.803,
          nonNullWithNegativeValue = -377.693,
          nonNullWithMaxValue = Double.MAX_VALUE,
          nonNullWithMinValue = Double.MIN_VALUE,
          nonNullWithMaxSafeIntegerValue = MAX_SAFE_INTEGER,
        ) {
          nullableWithNullValue = null
          nullableWithZeroValue = 0.0
          nullableWithNegativeZeroValue = 0.0
          nullableWithPositiveValue = 789.821
          nullableWithNegativeValue = -498.776
          nullableWithMaxValue = Double.MAX_VALUE
          nullableWithMinValue = Double.MIN_VALUE
          nullableWithMaxSafeIntegerValue = MAX_SAFE_INTEGER
        }
        .data
        .key

    connector.updateFloatVariantsByKey.execute(key) {}

    val queryResult = connector.getFloatVariantsByKey.execute(key)
    assertThat(queryResult.data.floatVariants)
      .isEqualTo(
        GetFloatVariantsByKeyQuery.Data.FloatVariants(
          nonNullWithZeroValue = 0.0,
          nonNullWithNegativeZeroValue = 0.0,
          nonNullWithPositiveValue = 969.803,
          nonNullWithNegativeValue = -377.693,
          nonNullWithMaxValue = Double.MAX_VALUE,
          nonNullWithMinValue = Double.MIN_VALUE,
          nonNullWithMaxSafeIntegerValue = MAX_SAFE_INTEGER,
          nullableWithNullValue = null,
          nullableWithZeroValue = 0.0,
          nullableWithNegativeZeroValue = 0.0,
          nullableWithPositiveValue = 789.821,
          nullableWithNegativeValue = -498.776,
          nullableWithMaxValue = Double.MAX_VALUE,
          nullableWithMinValue = Double.MIN_VALUE,
          nullableWithMaxSafeIntegerValue = MAX_SAFE_INTEGER,
        )
      )
  }

  @Test
  fun insertBooleanVariants() = runTest {
    val key =
      connector.insertBooleanVariants
        .execute(
          nonNullWithTrueValue = true,
          nonNullWithFalseValue = false,
        ) {
          nullableWithNullValue = null
          nullableWithTrueValue = true
          nullableWithFalseValue = false
        }
        .data
        .key

    val queryResult = connector.getBooleanVariantsByKey.execute(key)
    assertThat(queryResult.data.booleanVariants)
      .isEqualTo(
        GetBooleanVariantsByKeyQuery.Data.BooleanVariants(
          nonNullWithTrueValue = true,
          nonNullWithFalseValue = false,
          nullableWithNullValue = null,
          nullableWithTrueValue = true,
          nullableWithFalseValue = false,
        )
      )
  }

  @Test
  fun insertBooleanVariantsWithDefaultValues() = runTest {
    val key = connector.insertBooleanVariantsWithHardcodedDefaults.execute {}.data.key

    val queryResult = connector.getBooleanVariantsByKey.execute(key)
    assertThat(queryResult.data.booleanVariants)
      .isEqualTo(
        GetBooleanVariantsByKeyQuery.Data.BooleanVariants(
          nonNullWithTrueValue = true,
          nonNullWithFalseValue = false,
          nullableWithNullValue = null,
          nullableWithTrueValue = true,
          nullableWithFalseValue = false,
        )
      )
  }

  @Test
  fun updateBooleanVariantsToNonNullValues() = runTest {
    val key =
      connector.insertBooleanVariants
        .execute(
          nonNullWithTrueValue = true,
          nonNullWithFalseValue = false,
        ) {
          nullableWithNullValue = null
          nullableWithTrueValue = true
          nullableWithFalseValue = false
        }
        .data
        .key

    connector.updateBooleanVariantsByKey.execute(key) {
      nonNullWithTrueValue = false
      nonNullWithFalseValue = true
      nullableWithNullValue = true
      nullableWithTrueValue = false
      nullableWithFalseValue = true
    }

    val queryResult = connector.getBooleanVariantsByKey.execute(key)
    assertThat(queryResult.data.booleanVariants)
      .isEqualTo(
        GetBooleanVariantsByKeyQuery.Data.BooleanVariants(
          nonNullWithTrueValue = false,
          nonNullWithFalseValue = true,
          nullableWithNullValue = true,
          nullableWithTrueValue = false,
          nullableWithFalseValue = true,
        )
      )
  }

  @Test
  fun updateBooleanVariantsToNullValues() = runTest {
    val key =
      connector.insertBooleanVariants
        .execute(
          nonNullWithTrueValue = true,
          nonNullWithFalseValue = false,
        ) {
          nullableWithNullValue = null
          nullableWithTrueValue = true
          nullableWithFalseValue = false
        }
        .data
        .key

    connector.updateBooleanVariantsByKey.execute(key) {
      nullableWithNullValue = null
      nullableWithTrueValue = null
      nullableWithFalseValue = null
    }

    val queryResult = connector.getBooleanVariantsByKey.execute(key)
    assertThat(queryResult.data.booleanVariants)
      .isEqualTo(
        GetBooleanVariantsByKeyQuery.Data.BooleanVariants(
          nonNullWithTrueValue = true,
          nonNullWithFalseValue = false,
          nullableWithNullValue = null,
          nullableWithTrueValue = null,
          nullableWithFalseValue = null,
        )
      )
  }

  @Test
  fun updateBooleanVariantsToUndefinedValues() = runTest {
    val key =
      connector.insertBooleanVariants
        .execute(
          nonNullWithTrueValue = true,
          nonNullWithFalseValue = false,
        ) {
          nullableWithNullValue = null
          nullableWithTrueValue = true
          nullableWithFalseValue = false
        }
        .data
        .key

    connector.updateBooleanVariantsByKey.execute(key) {}

    val queryResult = connector.getBooleanVariantsByKey.execute(key)
    assertThat(queryResult.data.booleanVariants)
      .isEqualTo(
        GetBooleanVariantsByKeyQuery.Data.BooleanVariants(
          nonNullWithTrueValue = true,
          nonNullWithFalseValue = false,
          nullableWithNullValue = null,
          nullableWithTrueValue = true,
          nullableWithFalseValue = false,
        )
      )
  }

  @Test
  fun insertInt64Variants() = runTest {
    val key =
      connector.insertInt64variants
        .execute(
          nonNullWithZeroValue = 0,
          nonNullWithPositiveValue = 4242424242424242,
          nonNullWithNegativeValue = -4242424242424242,
          nonNullWithMaxValue = Long.MAX_VALUE,
          nonNullWithMinValue = Long.MIN_VALUE,
        ) {
          nullableWithNullValue = null
          nullableWithZeroValue = 0
          nullableWithPositiveValue = 2424242424242424
          nullableWithNegativeValue = -2424242424242424
          nullableWithMaxValue = Long.MAX_VALUE
          nullableWithMinValue = Long.MIN_VALUE
        }
        .data
        .key

    val queryResult = connector.getInt64variantsByKey.execute(key)
    assertThat(queryResult.data.int64Variants)
      .isEqualTo(
        GetInt64variantsByKeyQuery.Data.Int64variants(
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
        )
      )
  }

  @Test
  fun insertInt64VariantsWithDefaultValues() = runTest {
    val key = connector.insertInt64variantsWithHardcodedDefaults.execute {}.data.key

    val queryResult = connector.getInt64variantsByKey.execute(key)
    assertThat(queryResult.data.int64Variants)
      .isEqualTo(
        GetInt64variantsByKeyQuery.Data.Int64variants(
          nonNullWithZeroValue = 0,
          nonNullWithPositiveValue = 8140262498000722655,
          nonNullWithNegativeValue = -6722404680598014256,
          nonNullWithMaxValue = Long.MAX_VALUE,
          nonNullWithMinValue = Long.MIN_VALUE,
          nullableWithNullValue = null,
          nullableWithZeroValue = 0,
          nullableWithPositiveValue = 2623421399624774761,
          nullableWithNegativeValue = -1400927531111898547,
          nullableWithMaxValue = Long.MAX_VALUE,
          nullableWithMinValue = Long.MIN_VALUE,
        )
      )
  }

  @Test
  fun updateInt64VariantsToNonNullValues() = runTest {
    val key =
      connector.insertInt64variants
        .execute(
          nonNullWithZeroValue = 0,
          nonNullWithPositiveValue = 4242424242424242,
          nonNullWithNegativeValue = -4242424242424242,
          nonNullWithMaxValue = Long.MAX_VALUE,
          nonNullWithMinValue = Long.MIN_VALUE,
        ) {
          nullableWithNullValue = null
          nullableWithZeroValue = 0
          nullableWithPositiveValue = 2424242424242424
          nullableWithNegativeValue = -2424242424242424
          nullableWithMaxValue = Long.MAX_VALUE
          nullableWithMinValue = Long.MIN_VALUE
        }
        .data
        .key

    connector.updateInt64variantsByKey.execute(key) {
      nonNullWithZeroValue = Long.MAX_VALUE
      nonNullWithPositiveValue = Long.MIN_VALUE
      nonNullWithNegativeValue = 0
      nonNullWithMaxValue = 6252443364575076407
      nonNullWithMinValue = -2729456791747763772
      nullableWithNullValue = Long.MIN_VALUE
      nullableWithZeroValue = Long.MAX_VALUE
      nullableWithPositiveValue = -8687725805487568442
      nullableWithNegativeValue = 2353423753564688753
      nullableWithMaxValue = 0
      nullableWithMinValue = 1138055334163106400
    }

    val queryResult = connector.getInt64variantsByKey.execute(key)
    assertThat(queryResult.data.int64Variants)
      .isEqualTo(
        GetInt64variantsByKeyQuery.Data.Int64variants(
          nonNullWithZeroValue = Long.MAX_VALUE,
          nonNullWithPositiveValue = Long.MIN_VALUE,
          nonNullWithNegativeValue = 0,
          nonNullWithMaxValue = 6252443364575076407,
          nonNullWithMinValue = -2729456791747763772,
          nullableWithNullValue = Long.MIN_VALUE,
          nullableWithZeroValue = Long.MAX_VALUE,
          nullableWithPositiveValue = -8687725805487568442,
          nullableWithNegativeValue = 2353423753564688753,
          nullableWithMaxValue = 0,
          nullableWithMinValue = 1138055334163106400,
        )
      )
  }

  @Test
  fun updateInt64VariantsToNullValues() = runTest {
    val key =
      connector.insertInt64variants
        .execute(
          nonNullWithZeroValue = 0,
          nonNullWithPositiveValue = 6015655135498983208,
          nonNullWithNegativeValue = -6239673548840053697,
          nonNullWithMaxValue = Long.MAX_VALUE,
          nonNullWithMinValue = Long.MIN_VALUE,
        ) {
          nullableWithNullValue = null
          nullableWithZeroValue = 0
          nullableWithPositiveValue = 2139268131023575155
          nullableWithNegativeValue = -7753368718652189037
          nullableWithMaxValue = Long.MAX_VALUE
          nullableWithMinValue = Long.MIN_VALUE
        }
        .data
        .key

    connector.updateInt64variantsByKey.execute(key) {
      nullableWithNullValue = null
      nullableWithZeroValue = null
      nullableWithPositiveValue = null
      nullableWithNegativeValue = null
      nullableWithMaxValue = null
      nullableWithMinValue = null
    }

    val queryResult = connector.getInt64variantsByKey.execute(key)
    assertThat(queryResult.data.int64Variants)
      .isEqualTo(
        GetInt64variantsByKeyQuery.Data.Int64variants(
          nonNullWithZeroValue = 0,
          nonNullWithPositiveValue = 6015655135498983208,
          nonNullWithNegativeValue = -6239673548840053697,
          nonNullWithMaxValue = Long.MAX_VALUE,
          nonNullWithMinValue = Long.MIN_VALUE,
          nullableWithNullValue = null,
          nullableWithZeroValue = null,
          nullableWithPositiveValue = null,
          nullableWithNegativeValue = null,
          nullableWithMaxValue = null,
          nullableWithMinValue = null,
        )
      )
  }

  @Test
  fun updateInt64VariantsToUndefinedValues() = runTest {
    val key =
      connector.insertInt64variants
        .execute(
          nonNullWithZeroValue = 0,
          nonNullWithPositiveValue = 6701682660019975832,
          nonNullWithNegativeValue = -4478250605910359747,
          nonNullWithMaxValue = Long.MAX_VALUE,
          nonNullWithMinValue = Long.MIN_VALUE,
        ) {
          nullableWithNullValue = null
          nullableWithZeroValue = 0
          nullableWithPositiveValue = 5813549730210600934
          nullableWithNegativeValue = -8226376165047801337
          nullableWithMaxValue = Long.MAX_VALUE
          nullableWithMinValue = Long.MIN_VALUE
        }
        .data
        .key

    connector.updateInt64variantsByKey.execute(key) {}

    val queryResult = connector.getInt64variantsByKey.execute(key)
    assertThat(queryResult.data.int64Variants)
      .isEqualTo(
        GetInt64variantsByKeyQuery.Data.Int64variants(
          nonNullWithZeroValue = 0,
          nonNullWithPositiveValue = 6701682660019975832,
          nonNullWithNegativeValue = -4478250605910359747,
          nonNullWithMaxValue = Long.MAX_VALUE,
          nonNullWithMinValue = Long.MIN_VALUE,
          nullableWithNullValue = null,
          nullableWithZeroValue = 0,
          nullableWithPositiveValue = 5813549730210600934,
          nullableWithNegativeValue = -8226376165047801337,
          nullableWithMaxValue = Long.MAX_VALUE,
          nullableWithMinValue = Long.MIN_VALUE,
        )
      )
  }

  @Test
  fun insertUUIDVariants() = runTest {
    val key =
      connector.insertUuidvariants
        .execute(
          nonNullValue = UUID.fromString("9ceda52f-18a1-431b-b9f7-89b674ca4bee"),
        ) {
          nullableWithNullValue = null
          nullableWithNonNullValue = UUID.fromString("7ca7c62a-c551-4cb9-8f86-0a2ce3d68b72")
        }
        .data
        .key

    val queryResult = connector.getUuidvariantsByKey.execute(key)
    assertThat(queryResult.data.uUIDVariants)
      .isEqualTo(
        GetUuidvariantsByKeyQuery.Data.UUidvariants(
          nonNullValue = UUID.fromString("9ceda52f-18a1-431b-b9f7-89b674ca4bee"),
          nullableWithNullValue = null,
          nullableWithNonNullValue = UUID.fromString("7ca7c62a-c551-4cb9-8f86-0a2ce3d68b72"),
        )
      )
  }

  @Test
  @Ignore("TODO(b/341070491) Re-enable this test once default values for UUID variables is fixed")
  fun insertUUIDVariantsWithDefaultValues() = runTest {
    // TODO(b/341070491) Update the definition of the "InsertUUIDVariantsWithHardcodedDefaults"
    //  mutation in GraphQL and change .execute() to .execute{}.
    val key = connector.insertUuidvariantsWithHardcodedDefaults.execute().data.key

    val queryResult = connector.getUuidvariantsByKey.execute(key)
    assertThat(queryResult.data.uUIDVariants)
      .isEqualTo(
        GetUuidvariantsByKeyQuery.Data.UUidvariants(
          nonNullValue = UUID.fromString("66576fdc-1a35-4b59-8c8b-d3beb65956ca"),
          nullableWithNullValue = null,
          nullableWithNonNullValue = UUID.fromString("59ab3886-8b84-4233-a5e6-da58c0e8b97d"),
        )
      )
  }

  @Test
  fun updateUUIDVariantsToNonNullValues() = runTest {
    val key =
      connector.insertUuidvariants
        .execute(
          nonNullValue = UUID.fromString("e0e9539c-5723-4063-b490-20b0f28c82fc"),
        ) {
          nullableWithNullValue = null
          nullableWithNonNullValue = UUID.fromString("c198ecf2-8de5-438f-8b9e-4d07e07d2a7e")
        }
        .data
        .key

    connector.updateUuidvariantsByKey.execute(key) {
      nonNullValue = UUID.fromString("a4d3f3cb-f88a-4aeb-9440-b446780e3f1f")
      nullableWithNullValue = UUID.fromString("e6fda23b-26ab-422c-a461-75bf2cd08775")
      nullableWithNonNullValue = UUID.fromString("22d122a7-45c6-4f7a-ba0b-bf00aa47c77a")
    }

    val queryResult = connector.getUuidvariantsByKey.execute(key)
    assertThat(queryResult.data.uUIDVariants)
      .isEqualTo(
        GetUuidvariantsByKeyQuery.Data.UUidvariants(
          nonNullValue = UUID.fromString("a4d3f3cb-f88a-4aeb-9440-b446780e3f1f"),
          nullableWithNullValue = UUID.fromString("e6fda23b-26ab-422c-a461-75bf2cd08775"),
          nullableWithNonNullValue = UUID.fromString("22d122a7-45c6-4f7a-ba0b-bf00aa47c77a"),
        )
      )
  }

  @Test
  fun updateUUIDVariantsToNullValues() = runTest {
    val key =
      connector.insertUuidvariants
        .execute(
          nonNullValue = UUID.fromString("a319232e-ef2b-4bb2-96e7-c31893914b77"),
        ) {
          nullableWithNullValue = null
          nullableWithNonNullValue = UUID.fromString("95ba2d8e-7908-4b60-999c-7c292616c920")
        }
        .data
        .key

    connector.updateUuidvariantsByKey.execute(key) {
      nullableWithNullValue = null
      nullableWithNonNullValue = null
    }

    val queryResult = connector.getUuidvariantsByKey.execute(key)
    assertThat(queryResult.data.uUIDVariants)
      .isEqualTo(
        GetUuidvariantsByKeyQuery.Data.UUidvariants(
          nonNullValue = UUID.fromString("a319232e-ef2b-4bb2-96e7-c31893914b77"),
          nullableWithNullValue = null,
          nullableWithNonNullValue = null,
        )
      )
  }

  @Test
  fun updateUUIDVariantsToUndefinedValues() = runTest {
    val key =
      connector.insertUuidvariants
        .execute(
          nonNullValue = UUID.fromString("c72c5a7c-f179-48a5-83fb-171a148b0192"),
        ) {
          nullableWithNullValue = null
          nullableWithNonNullValue = UUID.fromString("dd55c183-616a-4bc8-a4e0-2a32101450d7")
        }
        .data
        .key

    connector.updateUuidvariantsByKey.execute(key) {}

    val queryResult = connector.getUuidvariantsByKey.execute(key)
    assertThat(queryResult.data.uUIDVariants)
      .isEqualTo(
        GetUuidvariantsByKeyQuery.Data.UUidvariants(
          nonNullValue = UUID.fromString("c72c5a7c-f179-48a5-83fb-171a148b0192"),
          nullableWithNullValue = null,
          nullableWithNonNullValue = UUID.fromString("dd55c183-616a-4bc8-a4e0-2a32101450d7"),
        )
      )
  }
}
