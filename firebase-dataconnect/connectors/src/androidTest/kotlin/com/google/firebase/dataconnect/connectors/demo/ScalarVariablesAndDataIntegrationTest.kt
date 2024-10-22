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

@file:OptIn(DelicateKotest::class)

package com.google.firebase.dataconnect.connectors.demo

import com.google.firebase.dataconnect.connectors.demo.testutil.DemoConnectorIntegrationTestBase
import com.google.firebase.dataconnect.testutil.MAX_SAFE_INTEGER
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import io.kotest.common.DelicateKotest
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.distinct
import io.kotest.property.arbitrary.negativeDouble
import io.kotest.property.arbitrary.negativeInt
import io.kotest.property.arbitrary.negativeLong
import io.kotest.property.arbitrary.positiveDouble
import io.kotest.property.arbitrary.positiveInt
import io.kotest.property.arbitrary.positiveLong
import io.kotest.property.arbitrary.uuid
import io.kotest.property.checkAll
import java.util.UUID
import kotlin.math.nextDown
import kotlin.math.nextUp
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Ignore
import org.junit.Test

class ScalarVariablesAndDataIntegrationTest : DemoConnectorIntegrationTestBase() {

  @Test
  fun insertStringVariants() =
    runTest(timeout = 60.seconds) {
      checkAll(propTestConfig, Arb.thisTest.strings()) { strings ->
        val insertResult =
          connector.insertStringVariants.execute(
            nonNullWithNonEmptyValue = strings.string1,
            nonNullWithEmptyValue = "",
          ) {
            nullableWithNullValue = null
            nullableWithNonNullValue = strings.string2
            nullableWithEmptyValue = ""
          }

        val queryResult = connector.getStringVariantsByKey.execute(insertResult.data.key)
        queryResult.data.stringVariants shouldBe
          GetStringVariantsByKeyQuery.Data.StringVariants(
            nonNullWithNonEmptyValue = strings.string1,
            nonNullWithEmptyValue = "",
            nullableWithNullValue = null,
            nullableWithNonNullValue = strings.string2,
            nullableWithEmptyValue = "",
          )
      }
    }

  @Test
  fun insertStringVariantsWithDefaultValues() = runTest {
    val insertResult = connector.insertStringVariantsWithHardcodedDefaults.execute {}

    val queryResult = connector.getStringVariantsByKey.execute(insertResult.data.key)
    queryResult.data.stringVariants shouldBe
      GetStringVariantsByKeyQuery.Data.StringVariants(
        nonNullWithNonEmptyValue = HardcodedValues.NON_NULL_WITH_NON_EMPTY_STRING,
        nonNullWithEmptyValue = "",
        nullableWithNullValue = null,
        nullableWithNonNullValue = HardcodedValues.NULLABLE_WITH_NON_EMPTY_STRING,
        nullableWithEmptyValue = "",
      )
  }

  @Test
  fun updateStringVariantsToNonNullValues() =
    runTest(timeout = 60.seconds) {
      checkAll(propTestConfig, Arb.thisTest.strings()) { strings ->
        val insertResult =
          connector.insertStringVariants.execute(
            nonNullWithNonEmptyValue = strings.string1,
            nonNullWithEmptyValue = "",
          ) {
            nullableWithNullValue = null
            nullableWithNonNullValue = strings.string2
            nullableWithEmptyValue = ""
          }

        connector.updateStringVariantsByKey.execute(insertResult.data.key) {
          nonNullWithNonEmptyValue = ""
          nonNullWithEmptyValue = strings.string3
          nullableWithNullValue = strings.string4
          nullableWithNonNullValue = strings.string5
          nullableWithEmptyValue = strings.string6
        }

        val queryResult = connector.getStringVariantsByKey.execute(insertResult.data.key)
        queryResult.data.stringVariants shouldBe
          GetStringVariantsByKeyQuery.Data.StringVariants(
            nonNullWithNonEmptyValue = "",
            nonNullWithEmptyValue = strings.string3,
            nullableWithNullValue = strings.string4,
            nullableWithNonNullValue = strings.string5,
            nullableWithEmptyValue = strings.string6,
          )
      }
    }

  @Test
  fun updateStringVariantsToNullValues() =
    runTest(timeout = 60.seconds) {
      checkAll(propTestConfig, Arb.thisTest.strings()) { strings ->
        val insertResult =
          connector.insertStringVariants.execute(
            nonNullWithNonEmptyValue = strings.string1,
            nonNullWithEmptyValue = "",
          ) {
            nullableWithNullValue = null
            nullableWithNonNullValue = strings.string2
            nullableWithEmptyValue = ""
          }

        connector.updateStringVariantsByKey.execute(insertResult.data.key) {
          nullableWithNullValue = null
          nullableWithNonNullValue = null
          nullableWithEmptyValue = null
        }

        val queryResult = connector.getStringVariantsByKey.execute(insertResult.data.key)
        queryResult.data.stringVariants shouldBe
          GetStringVariantsByKeyQuery.Data.StringVariants(
            nonNullWithNonEmptyValue = strings.string1,
            nonNullWithEmptyValue = "",
            nullableWithNullValue = null,
            nullableWithNonNullValue = null,
            nullableWithEmptyValue = null,
          )
      }
    }

  @Test
  fun updateStringVariantsToUndefinedValues() =
    runTest(timeout = 60.seconds) {
      checkAll(propTestConfig, Arb.thisTest.strings()) { strings ->
        val insertResult =
          connector.insertStringVariants.execute(
            nonNullWithNonEmptyValue = strings.string1,
            nonNullWithEmptyValue = "",
          ) {
            nullableWithNullValue = null
            nullableWithNonNullValue = strings.string2
            nullableWithEmptyValue = ""
          }

        connector.updateStringVariantsByKey.execute(insertResult.data.key) {}

        val queryResult = connector.getStringVariantsByKey.execute(insertResult.data.key)
        queryResult.data.stringVariants shouldBe
          GetStringVariantsByKeyQuery.Data.StringVariants(
            nonNullWithNonEmptyValue = strings.string1,
            nonNullWithEmptyValue = "",
            nullableWithNullValue = null,
            nullableWithNonNullValue = strings.string2,
            nullableWithEmptyValue = "",
          )
      }
    }

  @Test
  fun insertIntVariants() =
    runTest(timeout = 60.seconds) {
      checkAll(propTestConfig, Arb.thisTest.ints()) { ints ->
        val insertResult =
          connector.insertIntVariants.execute(
            nonNullWithZeroValue = 0,
            nonNullWithPositiveValue = ints.positiveValue1,
            nonNullWithNegativeValue = ints.negativeValue1,
            nonNullWithMaxValue = Int.MAX_VALUE,
            nonNullWithMinValue = Int.MIN_VALUE,
          ) {
            nullableWithNullValue = null
            nullableWithZeroValue = 0
            nullableWithPositiveValue = ints.positiveValue2
            nullableWithNegativeValue = ints.negativeValue2
            nullableWithMaxValue = Int.MAX_VALUE
            nullableWithMinValue = Int.MIN_VALUE
          }

        val queryResult = connector.getIntVariantsByKey.execute(insertResult.data.key)
        queryResult.data.intVariants shouldBe
          GetIntVariantsByKeyQuery.Data.IntVariants(
            nonNullWithZeroValue = 0,
            nonNullWithPositiveValue = ints.positiveValue1,
            nonNullWithNegativeValue = ints.negativeValue1,
            nonNullWithMaxValue = Int.MAX_VALUE,
            nonNullWithMinValue = Int.MIN_VALUE,
            nullableWithNullValue = null,
            nullableWithZeroValue = 0,
            nullableWithPositiveValue = ints.positiveValue2,
            nullableWithNegativeValue = ints.negativeValue2,
            nullableWithMaxValue = Int.MAX_VALUE,
            nullableWithMinValue = Int.MIN_VALUE,
          )
      }
    }

  @Test
  fun insertIntVariantsWithDefaultValues() = runTest {
    val insertResult = connector.insertIntVariantsWithHardcodedDefaults.execute {}

    val queryResult = connector.getIntVariantsByKey.execute(insertResult.data.key)
    queryResult.data.intVariants shouldBe
      GetIntVariantsByKeyQuery.Data.IntVariants(
        nonNullWithZeroValue = 0,
        nonNullWithPositiveValue = HardcodedValues.NON_NULL_POSITIVE_INT,
        nonNullWithNegativeValue = HardcodedValues.NON_NULL_NEGATIVE_INT,
        nonNullWithMaxValue = Int.MAX_VALUE,
        nonNullWithMinValue = Int.MIN_VALUE,
        nullableWithNullValue = null,
        nullableWithZeroValue = 0,
        nullableWithPositiveValue = HardcodedValues.NULLABLE_POSITIVE_INT,
        nullableWithNegativeValue = HardcodedValues.NULLABLE_NEGATIVE_INT,
        nullableWithMaxValue = Int.MAX_VALUE,
        nullableWithMinValue = Int.MIN_VALUE,
      )
  }

  @Test
  fun updateIntVariantsToNonNullValues() =
    runTest(timeout = 60.seconds) {
      checkAll(propTestConfig, Arb.thisTest.ints()) { ints ->
        val insertResult =
          connector.insertIntVariants.execute(
            nonNullWithZeroValue = 0,
            nonNullWithPositiveValue = ints.positiveValue1,
            nonNullWithNegativeValue = ints.negativeValue1,
            nonNullWithMaxValue = Int.MAX_VALUE,
            nonNullWithMinValue = Int.MIN_VALUE,
          ) {
            nullableWithNullValue = null
            nullableWithZeroValue = 0
            nullableWithPositiveValue = ints.positiveValue2
            nullableWithNegativeValue = ints.negativeValue2
            nullableWithMaxValue = Int.MAX_VALUE
            nullableWithMinValue = Int.MIN_VALUE
          }

        connector.updateIntVariantsByKey.execute(insertResult.data.key) {
          nonNullWithZeroValue = ints.positiveValue3
          nonNullWithPositiveValue = Int.MAX_VALUE
          nonNullWithNegativeValue = Int.MIN_VALUE
          nonNullWithMaxValue = 1
          nonNullWithMinValue = -1
          nullableWithNullValue = ints.negativeValue3
          nullableWithZeroValue = 0
          nullableWithPositiveValue = Int.MAX_VALUE
          nullableWithNegativeValue = Int.MIN_VALUE
          nullableWithMaxValue = 1
          nullableWithMinValue = -1
        }

        val queryResult = connector.getIntVariantsByKey.execute(insertResult.data.key)
        queryResult.data.intVariants shouldBe
          GetIntVariantsByKeyQuery.Data.IntVariants(
            nonNullWithZeroValue = ints.positiveValue3,
            nonNullWithPositiveValue = Int.MAX_VALUE,
            nonNullWithNegativeValue = Int.MIN_VALUE,
            nonNullWithMaxValue = 1,
            nonNullWithMinValue = -1,
            nullableWithNullValue = ints.negativeValue3,
            nullableWithZeroValue = 0,
            nullableWithPositiveValue = Int.MAX_VALUE,
            nullableWithNegativeValue = Int.MIN_VALUE,
            nullableWithMaxValue = 1,
            nullableWithMinValue = -1,
          )
      }
    }

  @Test
  fun updateIntVariantsToNullValues() =
    runTest(timeout = 60.seconds) {
      checkAll(propTestConfig, Arb.thisTest.ints()) { ints ->
        val insertResult =
          connector.insertIntVariants.execute(
            nonNullWithZeroValue = 0,
            nonNullWithPositiveValue = ints.positiveValue1,
            nonNullWithNegativeValue = ints.negativeValue1,
            nonNullWithMaxValue = Int.MAX_VALUE,
            nonNullWithMinValue = Int.MIN_VALUE,
          ) {
            nullableWithNullValue = null
            nullableWithZeroValue = 0
            nullableWithPositiveValue = ints.positiveValue2
            nullableWithNegativeValue = ints.negativeValue2
            nullableWithMaxValue = Int.MAX_VALUE
            nullableWithMinValue = Int.MIN_VALUE
          }

        connector.updateIntVariantsByKey.execute(insertResult.data.key) {
          nullableWithNullValue = null
          nullableWithZeroValue = null
          nullableWithPositiveValue = null
          nullableWithNegativeValue = null
          nullableWithMaxValue = null
          nullableWithMinValue = null
        }

        val queryResult = connector.getIntVariantsByKey.execute(insertResult.data.key)
        queryResult.data.intVariants shouldBe
          GetIntVariantsByKeyQuery.Data.IntVariants(
            nonNullWithZeroValue = 0,
            nonNullWithPositiveValue = ints.positiveValue1,
            nonNullWithNegativeValue = ints.negativeValue1,
            nonNullWithMaxValue = Int.MAX_VALUE,
            nonNullWithMinValue = Int.MIN_VALUE,
            nullableWithNullValue = null,
            nullableWithZeroValue = null,
            nullableWithPositiveValue = null,
            nullableWithNegativeValue = null,
            nullableWithMaxValue = null,
            nullableWithMinValue = null,
          )
      }
    }

  @Test
  fun updateIntVariantsToUndefinedValues() =
    runTest(timeout = 60.seconds) {
      checkAll(propTestConfig, Arb.thisTest.ints()) { ints ->
        val insertResult =
          connector.insertIntVariants.execute(
            nonNullWithZeroValue = 0,
            nonNullWithPositiveValue = ints.positiveValue1,
            nonNullWithNegativeValue = ints.negativeValue1,
            nonNullWithMaxValue = Int.MAX_VALUE,
            nonNullWithMinValue = Int.MIN_VALUE,
          ) {
            nullableWithNullValue = null
            nullableWithZeroValue = 0
            nullableWithPositiveValue = ints.positiveValue2
            nullableWithNegativeValue = ints.negativeValue2
            nullableWithMaxValue = Int.MAX_VALUE
            nullableWithMinValue = Int.MIN_VALUE
          }

        connector.updateIntVariantsByKey.execute(insertResult.data.key) {}

        val queryResult = connector.getIntVariantsByKey.execute(insertResult.data.key)
        queryResult.data.intVariants shouldBe
          GetIntVariantsByKeyQuery.Data.IntVariants(
            nonNullWithZeroValue = 0,
            nonNullWithPositiveValue = ints.positiveValue1,
            nonNullWithNegativeValue = ints.negativeValue1,
            nonNullWithMaxValue = Int.MAX_VALUE,
            nonNullWithMinValue = Int.MIN_VALUE,
            nullableWithNullValue = null,
            nullableWithZeroValue = 0,
            nullableWithPositiveValue = ints.positiveValue2,
            nullableWithNegativeValue = ints.negativeValue2,
            nullableWithMaxValue = Int.MAX_VALUE,
            nullableWithMinValue = Int.MIN_VALUE,
          )
      }
    }

  @Test
  fun insertFloatVariants() =
    runTest(timeout = 60.seconds) {
      checkAll(propTestConfig, Arb.thisTest.doubles()) { doubles ->
        val insertResult =
          connector.insertFloatVariants.execute(
            nonNullWithZeroValue = 0.0,
            nonNullWithNegativeZeroValue = -0.0,
            nonNullWithPositiveValue = doubles.positiveValue1,
            nonNullWithNegativeValue = doubles.negativeValue1,
            nonNullWithMaxValue = Double.MAX_VALUE,
            nonNullWithMinValue = Double.MIN_VALUE,
            nonNullWithMaxSafeIntegerValue = MAX_SAFE_INTEGER,
          ) {
            nullableWithNullValue = null
            nullableWithZeroValue = 0.0
            nullableWithNegativeZeroValue = 0.0
            nullableWithPositiveValue = doubles.positiveValue2
            nullableWithNegativeValue = doubles.negativeValue2
            nullableWithMaxValue = Double.MAX_VALUE
            nullableWithMinValue = Double.MIN_VALUE
            nullableWithMaxSafeIntegerValue = MAX_SAFE_INTEGER
          }

        val queryResult = connector.getFloatVariantsByKey.execute(insertResult.data.key)
        queryResult.data.floatVariants shouldBe
          GetFloatVariantsByKeyQuery.Data.FloatVariants(
            nonNullWithZeroValue = 0.0,
            nonNullWithNegativeZeroValue = 0.0,
            nonNullWithPositiveValue = doubles.positiveValue1,
            nonNullWithNegativeValue = doubles.negativeValue1,
            nonNullWithMaxValue = Double.MAX_VALUE,
            nonNullWithMinValue = Double.MIN_VALUE,
            nonNullWithMaxSafeIntegerValue = MAX_SAFE_INTEGER,
            nullableWithNullValue = null,
            nullableWithZeroValue = 0.0,
            nullableWithNegativeZeroValue = 0.0,
            nullableWithPositiveValue = doubles.positiveValue2,
            nullableWithNegativeValue = doubles.negativeValue2,
            nullableWithMaxValue = Double.MAX_VALUE,
            nullableWithMinValue = Double.MIN_VALUE,
            nullableWithMaxSafeIntegerValue = MAX_SAFE_INTEGER,
          )
      }
    }

  @Test
  fun insertFloatVariantsWithDefaultValues() =
    runTest(timeout = 60.seconds) {
      val insertResult = connector.insertFloatVariantsWithHardcodedDefaults.execute {}

      val queryResult = connector.getFloatVariantsByKey.execute(insertResult.data.key)
      queryResult.data.floatVariants shouldBe
        GetFloatVariantsByKeyQuery.Data.FloatVariants(
          nonNullWithZeroValue = 0.0,
          nonNullWithNegativeZeroValue = 0.0,
          nonNullWithPositiveValue = HardcodedValues.NON_NULL_POSITIVE_DOUBLE,
          nonNullWithNegativeValue = HardcodedValues.NON_NULL_NEGATIVE_DOUBLE,
          nonNullWithMaxValue = Double.MAX_VALUE,
          nonNullWithMinValue = Double.MIN_VALUE,
          nonNullWithMaxSafeIntegerValue = MAX_SAFE_INTEGER,
          nullableWithNullValue = null,
          nullableWithZeroValue = 0.0,
          nullableWithNegativeZeroValue = 0.0,
          nullableWithPositiveValue = HardcodedValues.NULLABLE_POSITIVE_DOUBLE,
          nullableWithNegativeValue = HardcodedValues.NULLABLE_NEGATIVE_DOUBLE,
          nullableWithMaxValue = Double.MAX_VALUE,
          nullableWithMinValue = Double.MIN_VALUE,
          nullableWithMaxSafeIntegerValue = MAX_SAFE_INTEGER,
        )
    }

  @Test
  fun updateFloatVariantsToNonNullValues() =
    runTest(timeout = 60.seconds) {
      checkAll(propTestConfig, Arb.thisTest.doubles()) { doubles ->
        val insertResult =
          connector.insertFloatVariants.execute(
            nonNullWithZeroValue = 0.0,
            nonNullWithNegativeZeroValue = -0.0,
            nonNullWithPositiveValue = doubles.positiveValue1,
            nonNullWithNegativeValue = doubles.negativeValue1,
            nonNullWithMaxValue = Double.MAX_VALUE,
            nonNullWithMinValue = Double.MIN_VALUE,
            nonNullWithMaxSafeIntegerValue = MAX_SAFE_INTEGER,
          ) {
            nullableWithNullValue = null
            nullableWithZeroValue = 0.0
            nullableWithNegativeZeroValue = 0.0
            nullableWithPositiveValue = doubles.positiveValue2
            nullableWithNegativeValue = doubles.negativeValue2
            nullableWithMaxValue = Double.MAX_VALUE
            nullableWithMinValue = Double.MIN_VALUE
            nullableWithMaxSafeIntegerValue = MAX_SAFE_INTEGER
          }

        connector.updateFloatVariantsByKey.execute(insertResult.data.key) {
          nonNullWithZeroValue = Double.MAX_VALUE
          nonNullWithNegativeZeroValue = Double.MIN_VALUE
          nonNullWithPositiveValue = MAX_SAFE_INTEGER
          nonNullWithNegativeValue = -0.0
          nonNullWithMaxValue = doubles.negativeValue3
          nonNullWithMinValue = doubles.positiveValue3
          nonNullWithMaxSafeIntegerValue = 0.0
          nullableWithNullValue = doubles.positiveValue4
          nullableWithZeroValue = Double.MIN_VALUE
          nullableWithNegativeZeroValue = MAX_SAFE_INTEGER
          nullableWithPositiveValue = -0.0
          nullableWithNegativeValue = MAX_SAFE_INTEGER
          nullableWithMaxValue = doubles.negativeValue4
          nullableWithMinValue = doubles.positiveValue5
          nullableWithMaxSafeIntegerValue = 0.0
        }

        val queryResult = connector.getFloatVariantsByKey.execute(insertResult.data.key)
        queryResult.data.floatVariants shouldBe
          GetFloatVariantsByKeyQuery.Data.FloatVariants(
            nonNullWithZeroValue = Double.MAX_VALUE,
            nonNullWithNegativeZeroValue = Double.MIN_VALUE,
            nonNullWithPositiveValue = MAX_SAFE_INTEGER,
            nonNullWithNegativeValue = 0.0,
            nonNullWithMaxValue = doubles.negativeValue3,
            nonNullWithMinValue = doubles.positiveValue3,
            nonNullWithMaxSafeIntegerValue = 0.0,
            nullableWithNullValue = doubles.positiveValue4,
            nullableWithZeroValue = Double.MIN_VALUE,
            nullableWithNegativeZeroValue = MAX_SAFE_INTEGER,
            nullableWithPositiveValue = 0.0,
            nullableWithNegativeValue = MAX_SAFE_INTEGER,
            nullableWithMaxValue = doubles.negativeValue4,
            nullableWithMinValue = doubles.positiveValue5,
            nullableWithMaxSafeIntegerValue = 0.0,
          )
      }
    }

  @Test
  fun updateFloatVariantsToNullValues() =
    runTest(timeout = 60.seconds) {
      checkAll(propTestConfig, Arb.thisTest.doubles()) { doubles ->
        val insertResult =
          connector.insertFloatVariants.execute(
            nonNullWithZeroValue = 0.0,
            nonNullWithNegativeZeroValue = -0.0,
            nonNullWithPositiveValue = doubles.positiveValue1,
            nonNullWithNegativeValue = doubles.negativeValue1,
            nonNullWithMaxValue = Double.MAX_VALUE,
            nonNullWithMinValue = Double.MIN_VALUE,
            nonNullWithMaxSafeIntegerValue = MAX_SAFE_INTEGER,
          ) {
            nullableWithNullValue = null
            nullableWithZeroValue = 0.0
            nullableWithNegativeZeroValue = 0.0
            nullableWithPositiveValue = doubles.positiveValue2
            nullableWithNegativeValue = doubles.negativeValue2
            nullableWithMaxValue = Double.MAX_VALUE
            nullableWithMinValue = Double.MIN_VALUE
            nullableWithMaxSafeIntegerValue = MAX_SAFE_INTEGER
          }

        connector.updateFloatVariantsByKey.execute(insertResult.data.key) {
          nullableWithNullValue = null
          nullableWithZeroValue = null
          nullableWithNegativeZeroValue = null
          nullableWithPositiveValue = null
          nullableWithNegativeValue = null
          nullableWithMaxValue = null
          nullableWithMinValue = null
          nullableWithMaxSafeIntegerValue = null
        }

        val queryResult = connector.getFloatVariantsByKey.execute(insertResult.data.key)
        queryResult.data.floatVariants shouldBe
          GetFloatVariantsByKeyQuery.Data.FloatVariants(
            nonNullWithZeroValue = 0.0,
            nonNullWithNegativeZeroValue = 0.0,
            nonNullWithPositiveValue = doubles.positiveValue1,
            nonNullWithNegativeValue = doubles.negativeValue1,
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
      }
    }

  @Test
  fun updateFloatVariantsToUndefinedValues() =
    runTest(timeout = 60.seconds) {
      checkAll(propTestConfig, Arb.thisTest.doubles()) { doubles ->
        val insertResult =
          connector.insertFloatVariants.execute(
            nonNullWithZeroValue = 0.0,
            nonNullWithNegativeZeroValue = -0.0,
            nonNullWithPositiveValue = doubles.positiveValue1,
            nonNullWithNegativeValue = doubles.negativeValue1,
            nonNullWithMaxValue = Double.MAX_VALUE,
            nonNullWithMinValue = Double.MIN_VALUE,
            nonNullWithMaxSafeIntegerValue = MAX_SAFE_INTEGER,
          ) {
            nullableWithNullValue = null
            nullableWithZeroValue = 0.0
            nullableWithNegativeZeroValue = 0.0
            nullableWithPositiveValue = doubles.positiveValue2
            nullableWithNegativeValue = doubles.negativeValue2
            nullableWithMaxValue = Double.MAX_VALUE
            nullableWithMinValue = Double.MIN_VALUE
            nullableWithMaxSafeIntegerValue = MAX_SAFE_INTEGER
          }

        connector.updateFloatVariantsByKey.execute(insertResult.data.key) {}

        val queryResult = connector.getFloatVariantsByKey.execute(insertResult.data.key)
        queryResult.data.floatVariants shouldBe
          GetFloatVariantsByKeyQuery.Data.FloatVariants(
            nonNullWithZeroValue = 0.0,
            nonNullWithNegativeZeroValue = 0.0,
            nonNullWithPositiveValue = doubles.positiveValue1,
            nonNullWithNegativeValue = doubles.negativeValue1,
            nonNullWithMaxValue = Double.MAX_VALUE,
            nonNullWithMinValue = Double.MIN_VALUE,
            nonNullWithMaxSafeIntegerValue = MAX_SAFE_INTEGER,
            nullableWithNullValue = null,
            nullableWithZeroValue = 0.0,
            nullableWithNegativeZeroValue = 0.0,
            nullableWithPositiveValue = doubles.positiveValue2,
            nullableWithNegativeValue = doubles.negativeValue2,
            nullableWithMaxValue = Double.MAX_VALUE,
            nullableWithMinValue = Double.MIN_VALUE,
            nullableWithMaxSafeIntegerValue = MAX_SAFE_INTEGER,
          )
      }
    }

  @Test
  fun insertBooleanVariants() =
    runTest(timeout = 60.seconds) {
      val insertResult =
        connector.insertBooleanVariants.execute(
          nonNullWithTrueValue = true,
          nonNullWithFalseValue = false,
        ) {
          nullableWithNullValue = null
          nullableWithTrueValue = true
          nullableWithFalseValue = false
        }

      val queryResult = connector.getBooleanVariantsByKey.execute(insertResult.data.key)
      queryResult.data.booleanVariants shouldBe
        GetBooleanVariantsByKeyQuery.Data.BooleanVariants(
          nonNullWithTrueValue = true,
          nonNullWithFalseValue = false,
          nullableWithNullValue = null,
          nullableWithTrueValue = true,
          nullableWithFalseValue = false,
        )
    }

  @Test
  fun insertBooleanVariantsWithDefaultValues() =
    runTest(timeout = 60.seconds) {
      val insertResult = connector.insertBooleanVariantsWithHardcodedDefaults.execute {}

      val queryResult = connector.getBooleanVariantsByKey.execute(insertResult.data.key)
      queryResult.data.booleanVariants shouldBe
        GetBooleanVariantsByKeyQuery.Data.BooleanVariants(
          nonNullWithTrueValue = true,
          nonNullWithFalseValue = false,
          nullableWithNullValue = null,
          nullableWithTrueValue = true,
          nullableWithFalseValue = false,
        )
    }

  @Test
  fun updateBooleanVariantsToNonNullValues() =
    runTest(timeout = 60.seconds) {
      val insertResult =
        connector.insertBooleanVariants.execute(
          nonNullWithTrueValue = true,
          nonNullWithFalseValue = false,
        ) {
          nullableWithNullValue = null
          nullableWithTrueValue = true
          nullableWithFalseValue = false
        }

      connector.updateBooleanVariantsByKey.execute(insertResult.data.key) {
        nonNullWithTrueValue = false
        nonNullWithFalseValue = true
        nullableWithNullValue = true
        nullableWithTrueValue = false
        nullableWithFalseValue = true
      }

      val queryResult = connector.getBooleanVariantsByKey.execute(insertResult.data.key)
      queryResult.data.booleanVariants shouldBe
        GetBooleanVariantsByKeyQuery.Data.BooleanVariants(
          nonNullWithTrueValue = false,
          nonNullWithFalseValue = true,
          nullableWithNullValue = true,
          nullableWithTrueValue = false,
          nullableWithFalseValue = true,
        )
    }

  @Test
  fun updateBooleanVariantsToNullValues() =
    runTest(timeout = 60.seconds) {
      val insertResult =
        connector.insertBooleanVariants.execute(
          nonNullWithTrueValue = true,
          nonNullWithFalseValue = false,
        ) {
          nullableWithNullValue = null
          nullableWithTrueValue = true
          nullableWithFalseValue = false
        }

      connector.updateBooleanVariantsByKey.execute(insertResult.data.key) {
        nullableWithNullValue = null
        nullableWithTrueValue = null
        nullableWithFalseValue = null
      }

      val queryResult = connector.getBooleanVariantsByKey.execute(insertResult.data.key)
      queryResult.data.booleanVariants shouldBe
        GetBooleanVariantsByKeyQuery.Data.BooleanVariants(
          nonNullWithTrueValue = true,
          nonNullWithFalseValue = false,
          nullableWithNullValue = null,
          nullableWithTrueValue = null,
          nullableWithFalseValue = null,
        )
    }

  @Test
  fun updateBooleanVariantsToUndefinedValues() =
    runTest(timeout = 60.seconds) {
      val insertResult =
        connector.insertBooleanVariants.execute(
          nonNullWithTrueValue = true,
          nonNullWithFalseValue = false,
        ) {
          nullableWithNullValue = null
          nullableWithTrueValue = true
          nullableWithFalseValue = false
        }

      connector.updateBooleanVariantsByKey.execute(insertResult.data.key) {}

      val queryResult = connector.getBooleanVariantsByKey.execute(insertResult.data.key)
      queryResult.data.booleanVariants shouldBe
        GetBooleanVariantsByKeyQuery.Data.BooleanVariants(
          nonNullWithTrueValue = true,
          nonNullWithFalseValue = false,
          nullableWithNullValue = null,
          nullableWithTrueValue = true,
          nullableWithFalseValue = false,
        )
    }

  @Test
  fun insertInt64Variants() =
    runTest(timeout = 60.seconds) {
      checkAll(propTestConfig, Arb.thisTest.longs()) { longs ->
        val insertResult =
          connector.insertInt64variants.execute(
            nonNullWithZeroValue = 0,
            nonNullWithPositiveValue = longs.positiveValue1,
            nonNullWithNegativeValue = longs.negativeValue1,
            nonNullWithMaxValue = Long.MAX_VALUE,
            nonNullWithMinValue = Long.MIN_VALUE,
          ) {
            nullableWithNullValue = null
            nullableWithZeroValue = 0
            nullableWithPositiveValue = longs.positiveValue2
            nullableWithNegativeValue = longs.negativeValue2
            nullableWithMaxValue = Long.MAX_VALUE
            nullableWithMinValue = Long.MIN_VALUE
          }

        val queryResult = connector.getInt64variantsByKey.execute(insertResult.data.key)
        queryResult.data.int64Variants shouldBe
          GetInt64variantsByKeyQuery.Data.Int64variants(
            nonNullWithZeroValue = 0,
            nonNullWithPositiveValue = longs.positiveValue1,
            nonNullWithNegativeValue = longs.negativeValue1,
            nonNullWithMaxValue = Long.MAX_VALUE,
            nonNullWithMinValue = Long.MIN_VALUE,
            nullableWithNullValue = null,
            nullableWithZeroValue = 0,
            nullableWithPositiveValue = longs.positiveValue2,
            nullableWithNegativeValue = longs.negativeValue2,
            nullableWithMaxValue = Long.MAX_VALUE,
            nullableWithMinValue = Long.MIN_VALUE,
          )
      }
    }

  @Test
  fun insertInt64VariantsWithDefaultValues() =
    runTest(timeout = 60.seconds) {
      val insertResult = connector.insertInt64variantsWithHardcodedDefaults.execute {}

      val queryResult = connector.getInt64variantsByKey.execute(insertResult.data.key)
      queryResult.data.int64Variants shouldBe
        GetInt64variantsByKeyQuery.Data.Int64variants(
          nonNullWithZeroValue = 0,
          nonNullWithPositiveValue = HardcodedValues.NON_NULL_POSITIVE_LONG,
          nonNullWithNegativeValue = HardcodedValues.NON_NULL_NEGATIVE_LONG,
          nonNullWithMaxValue = Long.MAX_VALUE,
          nonNullWithMinValue = Long.MIN_VALUE,
          nullableWithNullValue = null,
          nullableWithZeroValue = 0,
          nullableWithPositiveValue = HardcodedValues.NULLABLE_POSITIVE_LONG,
          nullableWithNegativeValue = HardcodedValues.NULLABLE_NEGATIVE_LONG,
          nullableWithMaxValue = Long.MAX_VALUE,
          nullableWithMinValue = Long.MIN_VALUE,
        )
    }

  @Test
  fun updateInt64VariantsToNonNullValues() =
    runTest(timeout = 60.seconds) {
      checkAll(propTestConfig, Arb.thisTest.longs()) { longs ->
        val insertResult =
          connector.insertInt64variants.execute(
            nonNullWithZeroValue = 0,
            nonNullWithPositiveValue = longs.positiveValue1,
            nonNullWithNegativeValue = longs.negativeValue1,
            nonNullWithMaxValue = Long.MAX_VALUE,
            nonNullWithMinValue = Long.MIN_VALUE,
          ) {
            nullableWithNullValue = null
            nullableWithZeroValue = 0
            nullableWithPositiveValue = longs.positiveValue2
            nullableWithNegativeValue = longs.negativeValue2
            nullableWithMaxValue = Long.MAX_VALUE
            nullableWithMinValue = Long.MIN_VALUE
          }

        connector.updateInt64variantsByKey.execute(insertResult.data.key) {
          nonNullWithZeroValue = Long.MAX_VALUE
          nonNullWithPositiveValue = Long.MIN_VALUE
          nonNullWithNegativeValue = 0
          nonNullWithMaxValue = longs.positiveValue3
          nonNullWithMinValue = longs.negativeValue3
          nullableWithNullValue = Long.MIN_VALUE
          nullableWithZeroValue = Long.MAX_VALUE
          nullableWithPositiveValue = longs.negativeValue4
          nullableWithNegativeValue = longs.positiveValue4
          nullableWithMaxValue = 0
          nullableWithMinValue = longs.positiveValue5
        }

        val queryResult = connector.getInt64variantsByKey.execute(insertResult.data.key)
        queryResult.data.int64Variants shouldBe
          GetInt64variantsByKeyQuery.Data.Int64variants(
            nonNullWithZeroValue = Long.MAX_VALUE,
            nonNullWithPositiveValue = Long.MIN_VALUE,
            nonNullWithNegativeValue = 0,
            nonNullWithMaxValue = longs.positiveValue3,
            nonNullWithMinValue = longs.negativeValue3,
            nullableWithNullValue = Long.MIN_VALUE,
            nullableWithZeroValue = Long.MAX_VALUE,
            nullableWithPositiveValue = longs.negativeValue4,
            nullableWithNegativeValue = longs.positiveValue4,
            nullableWithMaxValue = 0,
            nullableWithMinValue = longs.positiveValue5,
          )
      }
    }

  @Test
  fun updateInt64VariantsToNullValues() =
    runTest(timeout = 60.seconds) {
      checkAll(propTestConfig, Arb.thisTest.longs()) { longs ->
        val insertResult =
          connector.insertInt64variants.execute(
            nonNullWithZeroValue = 0,
            nonNullWithPositiveValue = longs.positiveValue1,
            nonNullWithNegativeValue = longs.negativeValue1,
            nonNullWithMaxValue = Long.MAX_VALUE,
            nonNullWithMinValue = Long.MIN_VALUE,
          ) {
            nullableWithNullValue = null
            nullableWithZeroValue = 0
            nullableWithPositiveValue = longs.positiveValue2
            nullableWithNegativeValue = longs.negativeValue2
            nullableWithMaxValue = Long.MAX_VALUE
            nullableWithMinValue = Long.MIN_VALUE
          }

        connector.updateInt64variantsByKey.execute(insertResult.data.key) {
          nullableWithNullValue = null
          nullableWithZeroValue = null
          nullableWithPositiveValue = null
          nullableWithNegativeValue = null
          nullableWithMaxValue = null
          nullableWithMinValue = null
        }

        val queryResult = connector.getInt64variantsByKey.execute(insertResult.data.key)
        queryResult.data.int64Variants shouldBe
          GetInt64variantsByKeyQuery.Data.Int64variants(
            nonNullWithZeroValue = 0,
            nonNullWithPositiveValue = longs.positiveValue1,
            nonNullWithNegativeValue = longs.negativeValue1,
            nonNullWithMaxValue = Long.MAX_VALUE,
            nonNullWithMinValue = Long.MIN_VALUE,
            nullableWithNullValue = null,
            nullableWithZeroValue = null,
            nullableWithPositiveValue = null,
            nullableWithNegativeValue = null,
            nullableWithMaxValue = null,
            nullableWithMinValue = null,
          )
      }
    }

  @Test
  fun updateInt64VariantsToUndefinedValues() =
    runTest(timeout = 60.seconds) {
      checkAll(propTestConfig, Arb.thisTest.longs()) { longs ->
        val insertResult =
          connector.insertInt64variants.execute(
            nonNullWithZeroValue = 0,
            nonNullWithPositiveValue = longs.positiveValue1,
            nonNullWithNegativeValue = longs.negativeValue1,
            nonNullWithMaxValue = Long.MAX_VALUE,
            nonNullWithMinValue = Long.MIN_VALUE,
          ) {
            nullableWithNullValue = null
            nullableWithZeroValue = 0
            nullableWithPositiveValue = longs.positiveValue2
            nullableWithNegativeValue = longs.negativeValue2
            nullableWithMaxValue = Long.MAX_VALUE
            nullableWithMinValue = Long.MIN_VALUE
          }

        connector.updateInt64variantsByKey.execute(insertResult.data.key) {}

        val queryResult = connector.getInt64variantsByKey.execute(insertResult.data.key)
        queryResult.data.int64Variants shouldBe
          GetInt64variantsByKeyQuery.Data.Int64variants(
            nonNullWithZeroValue = 0,
            nonNullWithPositiveValue = longs.positiveValue1,
            nonNullWithNegativeValue = longs.negativeValue1,
            nonNullWithMaxValue = Long.MAX_VALUE,
            nonNullWithMinValue = Long.MIN_VALUE,
            nullableWithNullValue = null,
            nullableWithZeroValue = 0,
            nullableWithPositiveValue = longs.positiveValue2,
            nullableWithNegativeValue = longs.negativeValue2,
            nullableWithMaxValue = Long.MAX_VALUE,
            nullableWithMinValue = Long.MIN_VALUE,
          )
      }
    }

  @Test
  fun insertUUIDVariants() =
    runTest(timeout = 60.seconds) {
      checkAll(propTestConfig, Arb.thisTest.uuids()) { uuids ->
        val insertResult =
          connector.insertUuidVariants.execute(
            nonNullValue = uuids.uuid1,
          ) {
            nullableWithNullValue = null
            nullableWithNonNullValue = uuids.uuid2
          }

        val queryResult = connector.getUuidVariantsByKey.execute(insertResult.data.key)
        queryResult.data.uUIDVariants shouldBe
          GetUuidVariantsByKeyQuery.Data.UUidVariants(
            nonNullValue = uuids.uuid1,
            nullableWithNullValue = null,
            nullableWithNonNullValue = uuids.uuid2,
          )
      }
    }

  @Test
  @Ignore("TODO(b/341070491) Re-enable this test once default values for UUID variables is fixed")
  fun insertUUIDVariantsWithDefaultValues() =
    runTest(timeout = 60.seconds) {
      // TODO(b/341070491) Update the definition of the "InsertUUIDVariantsWithHardcodedDefaults"
      //  mutation in GraphQL and change .execute() to .execute{}.
      val insertResult = connector.insertUuidVariantsWithHardcodedDefaults.execute()

      val queryResult = connector.getUuidVariantsByKey.execute(insertResult.data.key)
      queryResult.data.uUIDVariants shouldBe
        GetUuidVariantsByKeyQuery.Data.UUidVariants(
          nonNullValue = UUID.fromString("66576fdc-1a35-4b59-8c8b-d3beb65956ca"),
          nullableWithNullValue = null,
          nullableWithNonNullValue = UUID.fromString("59ab3886-8b84-4233-a5e6-da58c0e8b97d"),
        )
    }

  @Test
  fun updateUUIDVariantsToNonNullValues() =
    runTest(timeout = 60.seconds) {
      checkAll(propTestConfig, Arb.thisTest.uuids()) { uuids ->
        val insertResult =
          connector.insertUuidVariants.execute(nonNullValue = uuids.uuid1) {
            nullableWithNullValue = null
            nullableWithNonNullValue = uuids.uuid2
          }

        connector.updateUuidVariantsByKey.execute(insertResult.data.key) {
          nonNullValue = uuids.uuid3
          nullableWithNullValue = uuids.uuid4
          nullableWithNonNullValue = uuids.uuid5
        }

        val queryResult = connector.getUuidVariantsByKey.execute(insertResult.data.key)
        queryResult.data.uUIDVariants shouldBe
          GetUuidVariantsByKeyQuery.Data.UUidVariants(
            nonNullValue = uuids.uuid3,
            nullableWithNullValue = uuids.uuid4,
            nullableWithNonNullValue = uuids.uuid5,
          )
      }
    }

  @Test
  fun updateUUIDVariantsToNullValues() =
    runTest(timeout = 60.seconds) {
      checkAll(propTestConfig, Arb.thisTest.uuids()) { uuids ->
        val insertResult =
          connector.insertUuidVariants.execute(nonNullValue = uuids.uuid1) {
            nullableWithNullValue = null
            nullableWithNonNullValue = uuids.uuid2
          }

        connector.updateUuidVariantsByKey.execute(insertResult.data.key) {
          nullableWithNullValue = null
          nullableWithNonNullValue = null
        }

        val queryResult = connector.getUuidVariantsByKey.execute(insertResult.data.key)
        queryResult.data.uUIDVariants shouldBe
          GetUuidVariantsByKeyQuery.Data.UUidVariants(
            nonNullValue = uuids.uuid1,
            nullableWithNullValue = null,
            nullableWithNonNullValue = null,
          )
      }
    }

  @Test
  fun updateUUIDVariantsToUndefinedValues() =
    runTest(timeout = 60.seconds) {
      checkAll(propTestConfig, Arb.thisTest.uuids()) { uuids ->
        val insertResult =
          connector.insertUuidVariants.execute(
            nonNullValue = uuids.uuid1,
          ) {
            nullableWithNullValue = null
            nullableWithNonNullValue = uuids.uuid2
          }

        connector.updateUuidVariantsByKey.execute(insertResult.data.key) {}

        val queryResult = connector.getUuidVariantsByKey.execute(insertResult.data.key)
        queryResult.data.uUIDVariants shouldBe
          GetUuidVariantsByKeyQuery.Data.UUidVariants(
            nonNullValue = uuids.uuid1,
            nullableWithNullValue = null,
            nullableWithNonNullValue = uuids.uuid2,
          )
      }
    }

  /** Values that are hardcoded into the GraphQL schema and/or operations. */
  @Suppress("SpellCheckingInspection")
  private object HardcodedValues {
    const val NON_NULL_WITH_NON_EMPTY_STRING = "pfnk98yqqs"
    const val NULLABLE_WITH_NON_EMPTY_STRING = "af8k72s98t"
    const val NON_NULL_POSITIVE_INT = 819425
    const val NON_NULL_NEGATIVE_INT = -435970
    const val NULLABLE_POSITIVE_INT = 635166
    const val NULLABLE_NEGATIVE_INT = -171993
    const val NON_NULL_POSITIVE_LONG = 8140262498000722655
    const val NON_NULL_NEGATIVE_LONG = -6722404680598014256
    const val NULLABLE_POSITIVE_LONG = 2623421399624774761
    const val NULLABLE_NEGATIVE_LONG = -1400927531111898547
    const val NON_NULL_POSITIVE_DOUBLE = 750.452
    const val NON_NULL_NEGATIVE_DOUBLE = -598.351
    const val NULLABLE_POSITIVE_DOUBLE = 597.650
    const val NULLABLE_NEGATIVE_DOUBLE = -181.366
  }

  private data class ArbitraryStrings(
    val string1: String,
    val string2: String,
    val string3: String,
    val string4: String,
    val string5: String,
    val string6: String,
  )

  private data class ArbitraryInts(
    val positiveValue1: Int,
    val positiveValue2: Int,
    val positiveValue3: Int,
    val negativeValue1: Int,
    val negativeValue2: Int,
    val negativeValue3: Int,
  )

  private data class ArbitraryLongs(
    val positiveValue1: Long,
    val positiveValue2: Long,
    val positiveValue3: Long,
    val positiveValue4: Long,
    val positiveValue5: Long,
    val negativeValue1: Long,
    val negativeValue2: Long,
    val negativeValue3: Long,
    val negativeValue4: Long,
  )

  private data class ArbitraryDoubles(
    val positiveValue1: Double,
    val positiveValue2: Double,
    val positiveValue3: Double,
    val positiveValue4: Double,
    val positiveValue5: Double,
    val negativeValue1: Double,
    val negativeValue2: Double,
    val negativeValue3: Double,
    val negativeValue4: Double,
  )

  private data class ArbitraryUUIDs(
    val uuid1: UUID,
    val uuid2: UUID,
    val uuid3: UUID,
    val uuid4: UUID,
    val uuid5: UUID,
  )

  private object MyArbitrary {
    fun strings(
      string: Arb<String> = Arb.dataConnect.string().distinct(),
    ): Arb<ArbitraryStrings> = arbitrary {
      ArbitraryStrings(
        string1 = string.bind(),
        string2 = string.bind(),
        string3 = string.bind(),
        string4 = string.bind(),
        string5 = string.bind(),
        string6 = string.bind(),
      )
    }

    fun ints(
      positiveInts: Arb<Int> = Arb.positiveInt(max = Int.MAX_VALUE - 1).distinct(),
      negativeInts: Arb<Int> = Arb.negativeInt(min = Int.MIN_VALUE + 1).distinct(),
    ): Arb<ArbitraryInts> = arbitrary {
      ArbitraryInts(
        positiveValue1 = positiveInts.bind(),
        positiveValue2 = positiveInts.bind(),
        positiveValue3 = positiveInts.bind(),
        negativeValue1 = negativeInts.bind(),
        negativeValue2 = negativeInts.bind(),
        negativeValue3 = negativeInts.bind(),
      )
    }

    fun longs(
      positiveLongs: Arb<Long> = Arb.positiveLong(max = Long.MAX_VALUE - 1).distinct(),
      negativeLongs: Arb<Long> = Arb.negativeLong(min = Long.MIN_VALUE + 1).distinct(),
    ): Arb<ArbitraryLongs> = arbitrary {
      ArbitraryLongs(
        positiveValue1 = positiveLongs.bind(),
        positiveValue2 = positiveLongs.bind(),
        positiveValue3 = positiveLongs.bind(),
        positiveValue4 = positiveLongs.bind(),
        positiveValue5 = positiveLongs.bind(),
        negativeValue1 = negativeLongs.bind(),
        negativeValue2 = negativeLongs.bind(),
        negativeValue3 = negativeLongs.bind(),
        negativeValue4 = negativeLongs.bind(),
      )
    }

    fun doubles(
      positiveDoubles: Arb<Double> =
        Arb.positiveDouble(max = Double.MAX_VALUE.nextDown(), includeNonFiniteEdgeCases = false)
          .distinct(),
      negativeDoubles: Arb<Double> =
        Arb.negativeDouble(min = (-Double.MAX_VALUE).nextUp(), includeNonFiniteEdgeCases = false)
          .distinct(),
    ): Arb<ArbitraryDoubles> = arbitrary {
      ArbitraryDoubles(
        positiveValue1 = positiveDoubles.bind(),
        positiveValue2 = positiveDoubles.bind(),
        positiveValue3 = positiveDoubles.bind(),
        positiveValue4 = positiveDoubles.bind(),
        positiveValue5 = positiveDoubles.bind(),
        negativeValue1 = negativeDoubles.bind(),
        negativeValue2 = negativeDoubles.bind(),
        negativeValue3 = negativeDoubles.bind(),
        negativeValue4 = negativeDoubles.bind(),
      )
    }

    fun uuids(
      uuids: Arb<UUID> = Arb.uuid().distinct(),
    ): Arb<ArbitraryUUIDs> = arbitrary {
      ArbitraryUUIDs(
        uuid1 = uuids.bind(),
        uuid2 = uuids.bind(),
        uuid3 = uuids.bind(),
        uuid4 = uuids.bind(),
        uuid5 = uuids.bind(),
      )
    }
  }

  private val Arb.Companion.thisTest
    get() = MyArbitrary

  private companion object {
    @OptIn(ExperimentalKotest::class)
    val propTestConfig =
      PropTestConfig(
        iterations = 10,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.33)
      )
  }
}
