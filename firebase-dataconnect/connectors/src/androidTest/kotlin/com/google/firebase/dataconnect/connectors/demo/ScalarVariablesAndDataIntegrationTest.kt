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
import io.kotest.common.DelicateKotest
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.distinct
import io.kotest.property.arbitrary.negativeDouble
import io.kotest.property.arbitrary.negativeInt
import io.kotest.property.arbitrary.negativeLong
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.positiveDouble
import io.kotest.property.arbitrary.positiveInt
import io.kotest.property.arbitrary.positiveLong
import io.kotest.property.arbitrary.uuid
import java.util.UUID
import kotlin.math.nextDown
import kotlin.math.nextUp
import kotlinx.coroutines.test.runTest
import org.junit.Ignore
import org.junit.Test

class ScalarVariablesAndDataIntegrationTest : DemoConnectorIntegrationTestBase() {

  private val distinctPositiveInts = Arb.positiveInt(max = Int.MAX_VALUE - 1).distinct()
  private val distinctNegativeInts = Arb.negativeInt(min = Int.MIN_VALUE + 1).distinct()
  private val distinctPositiveLongs = Arb.positiveLong(max = Long.MAX_VALUE - 1).distinct()
  private val distinctNegativeLongs = Arb.negativeLong(min = Long.MIN_VALUE + 1).distinct()

  private val distinctPositiveDoubles =
    Arb.positiveDouble(max = Double.MAX_VALUE.nextDown(), includeNonFiniteEdgeCases = false)
      .distinct()

  private val distinctNegativeDoubles =
    Arb.negativeDouble(min = (-Double.MAX_VALUE).nextUp(), includeNonFiniteEdgeCases = false)
      .distinct()

  @Test
  fun insertStringVariants() = runTest {
    val someString1 = Arb.alphanumericString(prefix = "someString1_").next(rs)
    val someString2 = Arb.alphanumericString(prefix = "someString2_").next(rs)

    val insertResult =
      connector.insertStringVariants.execute(
        nonNullWithNonEmptyValue = someString1,
        nonNullWithEmptyValue = "",
      ) {
        nullableWithNullValue = null
        nullableWithNonNullValue = someString2
        nullableWithEmptyValue = ""
      }

    val queryResult = connector.getStringVariantsByKey.execute(insertResult.data.key)
    queryResult.data.stringVariants shouldBe
      GetStringVariantsByKeyQuery.Data.StringVariants(
        nonNullWithNonEmptyValue = someString1,
        nonNullWithEmptyValue = "",
        nullableWithNullValue = null,
        nullableWithNonNullValue = someString2,
        nullableWithEmptyValue = "",
      )
  }

  @Test
  fun insertStringVariantsWithDefaultValues() = runTest {
    val insertResult = connector.insertStringVariantsWithHardcodedDefaults.execute {}

    val queryResult = connector.getStringVariantsByKey.execute(insertResult.data.key)
    queryResult.data.stringVariants shouldBe
      GetStringVariantsByKeyQuery.Data.StringVariants(
        nonNullWithNonEmptyValue = "pfnk98yqqs",
        nonNullWithEmptyValue = "",
        nullableWithNullValue = null,
        nullableWithNonNullValue = "af8k72s98t",
        nullableWithEmptyValue = "",
      )
  }

  @Test
  fun updateStringVariantsToNonNullValues() = runTest {
    val strings = List(6) { Arb.alphanumericString(prefix = "string${it}_").next(rs) }

    val insertResult =
      connector.insertStringVariants.execute(
        nonNullWithNonEmptyValue = strings[0],
        nonNullWithEmptyValue = "",
      ) {
        nullableWithNullValue = null
        nullableWithNonNullValue = strings[1]
        nullableWithEmptyValue = ""
      }

    connector.updateStringVariantsByKey.execute(insertResult.data.key) {
      nonNullWithNonEmptyValue = ""
      nonNullWithEmptyValue = strings[2]
      nullableWithNullValue = strings[3]
      nullableWithNonNullValue = strings[4]
      nullableWithEmptyValue = strings[5]
    }

    val queryResult = connector.getStringVariantsByKey.execute(insertResult.data.key)
    queryResult.data.stringVariants shouldBe
      GetStringVariantsByKeyQuery.Data.StringVariants(
        nonNullWithNonEmptyValue = "",
        nonNullWithEmptyValue = strings[2],
        nullableWithNullValue = strings[3],
        nullableWithNonNullValue = strings[4],
        nullableWithEmptyValue = strings[5],
      )
  }

  @Test
  fun updateStringVariantsToNullValues() = runTest {
    val someString1 = Arb.alphanumericString(prefix = "someString1_").next(rs)
    val someString2 = Arb.alphanumericString(prefix = "someString2_").next(rs)

    val insertResult =
      connector.insertStringVariants.execute(
        nonNullWithNonEmptyValue = someString1,
        nonNullWithEmptyValue = "",
      ) {
        nullableWithNullValue = null
        nullableWithNonNullValue = someString2
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
        nonNullWithNonEmptyValue = someString1,
        nonNullWithEmptyValue = "",
        nullableWithNullValue = null,
        nullableWithNonNullValue = null,
        nullableWithEmptyValue = null,
      )
  }

  @Test
  fun updateStringVariantsToUndefinedValues() = runTest {
    val someString1 = Arb.alphanumericString(prefix = "someString1_").next(rs)
    val someString2 = Arb.alphanumericString(prefix = "someString2_").next(rs)

    val insertResult =
      connector.insertStringVariants.execute(
        nonNullWithNonEmptyValue = someString1,
        nonNullWithEmptyValue = "",
      ) {
        nullableWithNullValue = null
        nullableWithNonNullValue = someString2
        nullableWithEmptyValue = ""
      }

    connector.updateStringVariantsByKey.execute(insertResult.data.key) {}

    val queryResult = connector.getStringVariantsByKey.execute(insertResult.data.key)
    queryResult.data.stringVariants shouldBe
      GetStringVariantsByKeyQuery.Data.StringVariants(
        nonNullWithNonEmptyValue = someString1,
        nonNullWithEmptyValue = "",
        nullableWithNullValue = null,
        nullableWithNonNullValue = someString2,
        nullableWithEmptyValue = "",
      )
  }

  @Test
  fun insertIntVariants() = runTest {
    val positiveInts = List(2) { distinctPositiveInts.next(rs) }
    val negativeInts = List(2) { distinctNegativeInts.next(rs) }

    val insertResult =
      connector.insertIntVariants.execute(
        nonNullWithZeroValue = 0,
        nonNullWithPositiveValue = positiveInts[0],
        nonNullWithNegativeValue = negativeInts[0],
        nonNullWithMaxValue = Int.MAX_VALUE,
        nonNullWithMinValue = Int.MIN_VALUE,
      ) {
        nullableWithNullValue = null
        nullableWithZeroValue = 0
        nullableWithPositiveValue = positiveInts[1]
        nullableWithNegativeValue = negativeInts[1]
        nullableWithMaxValue = Int.MAX_VALUE
        nullableWithMinValue = Int.MIN_VALUE
      }

    val queryResult = connector.getIntVariantsByKey.execute(insertResult.data.key)
    queryResult.data.intVariants shouldBe
      GetIntVariantsByKeyQuery.Data.IntVariants(
        nonNullWithZeroValue = 0,
        nonNullWithPositiveValue = positiveInts[0],
        nonNullWithNegativeValue = negativeInts[0],
        nonNullWithMaxValue = Int.MAX_VALUE,
        nonNullWithMinValue = Int.MIN_VALUE,
        nullableWithNullValue = null,
        nullableWithZeroValue = 0,
        nullableWithPositiveValue = positiveInts[1],
        nullableWithNegativeValue = negativeInts[1],
        nullableWithMaxValue = Int.MAX_VALUE,
        nullableWithMinValue = Int.MIN_VALUE,
      )
  }

  @Test
  fun insertIntVariantsWithDefaultValues() = runTest {
    val insertResult = connector.insertIntVariantsWithHardcodedDefaults.execute {}

    val queryResult = connector.getIntVariantsByKey.execute(insertResult.data.key)
    queryResult.data.intVariants shouldBe
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
  }

  @Test
  fun updateIntVariantsToNonNullValues() = runTest {
    val positiveInts = List(4) { distinctPositiveInts.next(rs) }
    val negativeInts = List(2) { distinctNegativeInts.next(rs) }

    val insertResult =
      connector.insertIntVariants.execute(
        nonNullWithZeroValue = 0,
        nonNullWithPositiveValue = positiveInts[0],
        nonNullWithNegativeValue = negativeInts[0],
        nonNullWithMaxValue = Int.MAX_VALUE,
        nonNullWithMinValue = Int.MIN_VALUE,
      ) {
        nullableWithNullValue = null
        nullableWithZeroValue = 0
        nullableWithPositiveValue = positiveInts[1]
        nullableWithNegativeValue = negativeInts[1]
        nullableWithMaxValue = Int.MAX_VALUE
        nullableWithMinValue = Int.MIN_VALUE
      }

    connector.updateIntVariantsByKey.execute(insertResult.data.key) {
      nonNullWithZeroValue = positiveInts[2]
      nonNullWithPositiveValue = Int.MAX_VALUE
      nonNullWithNegativeValue = Int.MIN_VALUE
      nonNullWithMaxValue = 1
      nonNullWithMinValue = -1
      nullableWithNullValue = positiveInts[3]
      nullableWithZeroValue = 0
      nullableWithPositiveValue = Int.MAX_VALUE
      nullableWithNegativeValue = Int.MIN_VALUE
      nullableWithMaxValue = 1
      nullableWithMinValue = -1
    }

    val queryResult = connector.getIntVariantsByKey.execute(insertResult.data.key)
    queryResult.data.intVariants shouldBe
      GetIntVariantsByKeyQuery.Data.IntVariants(
        nonNullWithZeroValue = positiveInts[2],
        nonNullWithPositiveValue = Int.MAX_VALUE,
        nonNullWithNegativeValue = Int.MIN_VALUE,
        nonNullWithMaxValue = 1,
        nonNullWithMinValue = -1,
        nullableWithNullValue = positiveInts[3],
        nullableWithZeroValue = 0,
        nullableWithPositiveValue = Int.MAX_VALUE,
        nullableWithNegativeValue = Int.MIN_VALUE,
        nullableWithMaxValue = 1,
        nullableWithMinValue = -1,
      )
  }

  @Test
  fun updateIntVariantsToNullValues() = runTest {
    val nonNullPositiveInt = distinctPositiveInts.next(rs)
    val nonNullNegativeInt = distinctNegativeInts.next(rs)

    val insertResult =
      connector.insertIntVariants.execute(
        nonNullWithZeroValue = 0,
        nonNullWithPositiveValue = nonNullPositiveInt,
        nonNullWithNegativeValue = nonNullNegativeInt,
        nonNullWithMaxValue = Int.MAX_VALUE,
        nonNullWithMinValue = Int.MIN_VALUE,
      ) {
        nullableWithNullValue = null
        nullableWithZeroValue = 0
        nullableWithPositiveValue = distinctPositiveInts.next(rs)
        nullableWithNegativeValue = distinctNegativeInts.next(rs)
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
        nonNullWithPositiveValue = nonNullPositiveInt,
        nonNullWithNegativeValue = nonNullNegativeInt,
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

  @Test
  fun updateIntVariantsToUndefinedValues() = runTest {
    val positiveInts = List(2) { distinctPositiveInts.next(rs) }
    val negativeInts = List(2) { distinctNegativeInts.next(rs) }

    val insertResult =
      connector.insertIntVariants.execute(
        nonNullWithZeroValue = 0,
        nonNullWithPositiveValue = positiveInts[0],
        nonNullWithNegativeValue = negativeInts[0],
        nonNullWithMaxValue = Int.MAX_VALUE,
        nonNullWithMinValue = Int.MIN_VALUE,
      ) {
        nullableWithNullValue = null
        nullableWithZeroValue = 0
        nullableWithPositiveValue = positiveInts[1]
        nullableWithNegativeValue = negativeInts[1]
        nullableWithMaxValue = Int.MAX_VALUE
        nullableWithMinValue = Int.MIN_VALUE
      }

    connector.updateIntVariantsByKey.execute(insertResult.data.key) {}

    val queryResult = connector.getIntVariantsByKey.execute(insertResult.data.key)
    queryResult.data.intVariants shouldBe
      GetIntVariantsByKeyQuery.Data.IntVariants(
        nonNullWithZeroValue = 0,
        nonNullWithPositiveValue = positiveInts[0],
        nonNullWithNegativeValue = negativeInts[0],
        nonNullWithMaxValue = Int.MAX_VALUE,
        nonNullWithMinValue = Int.MIN_VALUE,
        nullableWithNullValue = null,
        nullableWithZeroValue = 0,
        nullableWithPositiveValue = positiveInts[1],
        nullableWithNegativeValue = negativeInts[1],
        nullableWithMaxValue = Int.MAX_VALUE,
        nullableWithMinValue = Int.MIN_VALUE,
      )
  }

  @Test
  fun insertFloatVariants() = runTest {
    val positiveDoubles = List(2) { distinctPositiveDoubles.next(rs) }
    val negativeDoubles = List(2) { distinctNegativeDoubles.next(rs) }

    val insertResult =
      connector.insertFloatVariants.execute(
        nonNullWithZeroValue = 0.0,
        nonNullWithNegativeZeroValue = -0.0,
        nonNullWithPositiveValue = positiveDoubles[0],
        nonNullWithNegativeValue = negativeDoubles[0],
        nonNullWithMaxValue = Double.MAX_VALUE,
        nonNullWithMinValue = Double.MIN_VALUE,
        nonNullWithMaxSafeIntegerValue = MAX_SAFE_INTEGER,
      ) {
        nullableWithNullValue = null
        nullableWithZeroValue = 0.0
        nullableWithNegativeZeroValue = 0.0
        nullableWithPositiveValue = positiveDoubles[1]
        nullableWithNegativeValue = negativeDoubles[1]
        nullableWithMaxValue = Double.MAX_VALUE
        nullableWithMinValue = Double.MIN_VALUE
        nullableWithMaxSafeIntegerValue = MAX_SAFE_INTEGER
      }

    val queryResult = connector.getFloatVariantsByKey.execute(insertResult.data.key)
    queryResult.data.floatVariants shouldBe
      GetFloatVariantsByKeyQuery.Data.FloatVariants(
        nonNullWithZeroValue = 0.0,
        nonNullWithNegativeZeroValue = 0.0,
        nonNullWithPositiveValue = positiveDoubles[0],
        nonNullWithNegativeValue = negativeDoubles[0],
        nonNullWithMaxValue = Double.MAX_VALUE,
        nonNullWithMinValue = Double.MIN_VALUE,
        nonNullWithMaxSafeIntegerValue = MAX_SAFE_INTEGER,
        nullableWithNullValue = null,
        nullableWithZeroValue = 0.0,
        nullableWithNegativeZeroValue = 0.0,
        nullableWithPositiveValue = positiveDoubles[1],
        nullableWithNegativeValue = negativeDoubles[1],
        nullableWithMaxValue = Double.MAX_VALUE,
        nullableWithMinValue = Double.MIN_VALUE,
        nullableWithMaxSafeIntegerValue = MAX_SAFE_INTEGER,
      )
  }

  @Test
  fun insertFloatVariantsWithDefaultValues() = runTest {
    val insertResult = connector.insertFloatVariantsWithHardcodedDefaults.execute {}

    val queryResult = connector.getFloatVariantsByKey.execute(insertResult.data.key)
    queryResult.data.floatVariants shouldBe
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
  }

  @Test
  fun updateFloatVariantsToNonNullValues() = runTest {
    val positiveDoubles = List(3) { distinctPositiveDoubles.next(rs) }
    val negativeDoubles = List(2) { distinctNegativeDoubles.next(rs) }

    val insertResult =
      connector.insertFloatVariants.execute(
        nonNullWithZeroValue = 0.0,
        nonNullWithNegativeZeroValue = -0.0,
        nonNullWithPositiveValue = distinctPositiveDoubles.next(rs),
        nonNullWithNegativeValue = distinctNegativeDoubles.next(rs),
        nonNullWithMaxValue = Double.MAX_VALUE,
        nonNullWithMinValue = Double.MIN_VALUE,
        nonNullWithMaxSafeIntegerValue = MAX_SAFE_INTEGER,
      ) {
        nullableWithNullValue = null
        nullableWithZeroValue = 0.0
        nullableWithNegativeZeroValue = 0.0
        nullableWithPositiveValue = distinctPositiveDoubles.next(rs)
        nullableWithNegativeValue = distinctNegativeDoubles.next(rs)
        nullableWithMaxValue = Double.MAX_VALUE
        nullableWithMinValue = Double.MIN_VALUE
        nullableWithMaxSafeIntegerValue = MAX_SAFE_INTEGER
      }

    connector.updateFloatVariantsByKey.execute(insertResult.data.key) {
      nonNullWithZeroValue = Double.MAX_VALUE
      nonNullWithNegativeZeroValue = Double.MIN_VALUE
      nonNullWithPositiveValue = MAX_SAFE_INTEGER
      nonNullWithNegativeValue = -0.0
      nonNullWithMaxValue = negativeDoubles[0]
      nonNullWithMinValue = positiveDoubles[0]
      nonNullWithMaxSafeIntegerValue = 0.0
      nullableWithNullValue = positiveDoubles[1]
      nullableWithZeroValue = Double.MIN_VALUE
      nullableWithNegativeZeroValue = MAX_SAFE_INTEGER
      nullableWithPositiveValue = -0.0
      nullableWithNegativeValue = MAX_SAFE_INTEGER
      nullableWithMaxValue = negativeDoubles[1]
      nullableWithMinValue = positiveDoubles[2]
      nullableWithMaxSafeIntegerValue = 0.0
    }

    val queryResult = connector.getFloatVariantsByKey.execute(insertResult.data.key)
    queryResult.data.floatVariants shouldBe
      GetFloatVariantsByKeyQuery.Data.FloatVariants(
        nonNullWithZeroValue = Double.MAX_VALUE,
        nonNullWithNegativeZeroValue = Double.MIN_VALUE,
        nonNullWithPositiveValue = MAX_SAFE_INTEGER,
        nonNullWithNegativeValue = 0.0,
        nonNullWithMaxValue = negativeDoubles[0],
        nonNullWithMinValue = positiveDoubles[0],
        nonNullWithMaxSafeIntegerValue = 0.0,
        nullableWithNullValue = positiveDoubles[1],
        nullableWithZeroValue = Double.MIN_VALUE,
        nullableWithNegativeZeroValue = MAX_SAFE_INTEGER,
        nullableWithPositiveValue = 0.0,
        nullableWithNegativeValue = MAX_SAFE_INTEGER,
        nullableWithMaxValue = negativeDoubles[1],
        nullableWithMinValue = positiveDoubles[2],
        nullableWithMaxSafeIntegerValue = 0.0,
      )
  }

  @Test
  fun updateFloatVariantsToNullValues() = runTest {
    val positiveDouble = distinctPositiveDoubles.next(rs)
    val negativeDouble = distinctNegativeDoubles.next(rs)

    val insertResult =
      connector.insertFloatVariants.execute(
        nonNullWithZeroValue = 0.0,
        nonNullWithNegativeZeroValue = -0.0,
        nonNullWithPositiveValue = positiveDouble,
        nonNullWithNegativeValue = negativeDouble,
        nonNullWithMaxValue = Double.MAX_VALUE,
        nonNullWithMinValue = Double.MIN_VALUE,
        nonNullWithMaxSafeIntegerValue = MAX_SAFE_INTEGER,
      ) {
        nullableWithNullValue = null
        nullableWithZeroValue = 0.0
        nullableWithNegativeZeroValue = 0.0
        nullableWithPositiveValue = distinctPositiveDoubles.next(rs)
        nullableWithNegativeValue = distinctPositiveDoubles.next(rs)
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
        nonNullWithPositiveValue = positiveDouble,
        nonNullWithNegativeValue = negativeDouble,
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

  @Test
  fun updateFloatVariantsToUndefinedValues() = runTest {
    val positiveDoubles = List(2) { distinctPositiveDoubles.next(rs) }
    val negativeDoubles = List(2) { distinctNegativeDoubles.next(rs) }

    val insertResult =
      connector.insertFloatVariants.execute(
        nonNullWithZeroValue = 0.0,
        nonNullWithNegativeZeroValue = -0.0,
        nonNullWithPositiveValue = positiveDoubles[0],
        nonNullWithNegativeValue = negativeDoubles[0],
        nonNullWithMaxValue = Double.MAX_VALUE,
        nonNullWithMinValue = Double.MIN_VALUE,
        nonNullWithMaxSafeIntegerValue = MAX_SAFE_INTEGER,
      ) {
        nullableWithNullValue = null
        nullableWithZeroValue = 0.0
        nullableWithNegativeZeroValue = 0.0
        nullableWithPositiveValue = positiveDoubles[1]
        nullableWithNegativeValue = negativeDoubles[1]
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
        nonNullWithPositiveValue = positiveDoubles[0],
        nonNullWithNegativeValue = negativeDoubles[0],
        nonNullWithMaxValue = Double.MAX_VALUE,
        nonNullWithMinValue = Double.MIN_VALUE,
        nonNullWithMaxSafeIntegerValue = MAX_SAFE_INTEGER,
        nullableWithNullValue = null,
        nullableWithZeroValue = 0.0,
        nullableWithNegativeZeroValue = 0.0,
        nullableWithPositiveValue = positiveDoubles[1],
        nullableWithNegativeValue = negativeDoubles[1],
        nullableWithMaxValue = Double.MAX_VALUE,
        nullableWithMinValue = Double.MIN_VALUE,
        nullableWithMaxSafeIntegerValue = MAX_SAFE_INTEGER,
      )
  }

  @Test
  fun insertBooleanVariants() = runTest {
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
  fun insertBooleanVariantsWithDefaultValues() = runTest {
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
  fun updateBooleanVariantsToNonNullValues() = runTest {
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
  fun updateBooleanVariantsToNullValues() = runTest {
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
  fun updateBooleanVariantsToUndefinedValues() = runTest {
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
  fun insertInt64Variants() = runTest {
    val positiveLongs = List(2) { distinctPositiveLongs.next(rs) }
    val negativeLongs = List(2) { distinctNegativeLongs.next(rs) }

    val insertResult =
      connector.insertInt64variants.execute(
        nonNullWithZeroValue = 0,
        nonNullWithPositiveValue = positiveLongs[0],
        nonNullWithNegativeValue = negativeLongs[0],
        nonNullWithMaxValue = Long.MAX_VALUE,
        nonNullWithMinValue = Long.MIN_VALUE,
      ) {
        nullableWithNullValue = null
        nullableWithZeroValue = 0
        nullableWithPositiveValue = positiveLongs[1]
        nullableWithNegativeValue = negativeLongs[1]
        nullableWithMaxValue = Long.MAX_VALUE
        nullableWithMinValue = Long.MIN_VALUE
      }

    val queryResult = connector.getInt64variantsByKey.execute(insertResult.data.key)
    queryResult.data.int64Variants shouldBe
      GetInt64variantsByKeyQuery.Data.Int64variants(
        nonNullWithZeroValue = 0,
        nonNullWithPositiveValue = positiveLongs[0],
        nonNullWithNegativeValue = negativeLongs[0],
        nonNullWithMaxValue = Long.MAX_VALUE,
        nonNullWithMinValue = Long.MIN_VALUE,
        nullableWithNullValue = null,
        nullableWithZeroValue = 0,
        nullableWithPositiveValue = positiveLongs[1],
        nullableWithNegativeValue = negativeLongs[1],
        nullableWithMaxValue = Long.MAX_VALUE,
        nullableWithMinValue = Long.MIN_VALUE,
      )
  }

  @Test
  fun insertInt64VariantsWithDefaultValues() = runTest {
    val insertResult = connector.insertInt64variantsWithHardcodedDefaults.execute {}

    val queryResult = connector.getInt64variantsByKey.execute(insertResult.data.key)
    queryResult.data.int64Variants shouldBe
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
  }

  @Test
  fun updateInt64VariantsToNonNullValues() = runTest {
    val positiveLongs = List(3) { distinctPositiveLongs.next(rs) }
    val negativeLongs = List(2) { distinctNegativeLongs.next(rs) }

    val insertResult =
      connector.insertInt64variants.execute(
        nonNullWithZeroValue = 0,
        nonNullWithPositiveValue = distinctPositiveLongs.next(rs),
        nonNullWithNegativeValue = distinctNegativeLongs.next(rs),
        nonNullWithMaxValue = Long.MAX_VALUE,
        nonNullWithMinValue = Long.MIN_VALUE,
      ) {
        nullableWithNullValue = null
        nullableWithZeroValue = 0
        nullableWithPositiveValue = distinctPositiveLongs.next(rs)
        nullableWithNegativeValue = distinctNegativeLongs.next(rs)
        nullableWithMaxValue = Long.MAX_VALUE
        nullableWithMinValue = Long.MIN_VALUE
      }

    connector.updateInt64variantsByKey.execute(insertResult.data.key) {
      nonNullWithZeroValue = Long.MAX_VALUE
      nonNullWithPositiveValue = Long.MIN_VALUE
      nonNullWithNegativeValue = 0
      nonNullWithMaxValue = positiveLongs[0]
      nonNullWithMinValue = negativeLongs[0]
      nullableWithNullValue = Long.MIN_VALUE
      nullableWithZeroValue = Long.MAX_VALUE
      nullableWithPositiveValue = negativeLongs[1]
      nullableWithNegativeValue = positiveLongs[1]
      nullableWithMaxValue = 0
      nullableWithMinValue = positiveLongs[2]
    }

    val queryResult = connector.getInt64variantsByKey.execute(insertResult.data.key)
    queryResult.data.int64Variants shouldBe
      GetInt64variantsByKeyQuery.Data.Int64variants(
        nonNullWithZeroValue = Long.MAX_VALUE,
        nonNullWithPositiveValue = Long.MIN_VALUE,
        nonNullWithNegativeValue = 0,
        nonNullWithMaxValue = positiveLongs[0],
        nonNullWithMinValue = negativeLongs[0],
        nullableWithNullValue = Long.MIN_VALUE,
        nullableWithZeroValue = Long.MAX_VALUE,
        nullableWithPositiveValue = negativeLongs[1],
        nullableWithNegativeValue = positiveLongs[1],
        nullableWithMaxValue = 0,
        nullableWithMinValue = positiveLongs[2],
      )
  }

  @Test
  fun updateInt64VariantsToNullValues() = runTest {
    val positiveLong = distinctPositiveLongs.next(rs)
    val negativeLong = distinctNegativeLongs.next(rs)

    val insertResult =
      connector.insertInt64variants.execute(
        nonNullWithZeroValue = 0,
        nonNullWithPositiveValue = positiveLong,
        nonNullWithNegativeValue = negativeLong,
        nonNullWithMaxValue = Long.MAX_VALUE,
        nonNullWithMinValue = Long.MIN_VALUE,
      ) {
        nullableWithNullValue = null
        nullableWithZeroValue = 0
        nullableWithPositiveValue = distinctPositiveLongs.next(rs)
        nullableWithNegativeValue = distinctNegativeLongs.next(rs)
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
        nonNullWithPositiveValue = positiveLong,
        nonNullWithNegativeValue = negativeLong,
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

  @Test
  fun updateInt64VariantsToUndefinedValues() = runTest {
    val positiveLongs = List(2) { distinctPositiveLongs.next(rs) }
    val negativeLongs = List(2) { distinctNegativeLongs.next(rs) }

    val insertResult =
      connector.insertInt64variants.execute(
        nonNullWithZeroValue = 0,
        nonNullWithPositiveValue = positiveLongs[0],
        nonNullWithNegativeValue = negativeLongs[0],
        nonNullWithMaxValue = Long.MAX_VALUE,
        nonNullWithMinValue = Long.MIN_VALUE,
      ) {
        nullableWithNullValue = null
        nullableWithZeroValue = 0
        nullableWithPositiveValue = positiveLongs[1]
        nullableWithNegativeValue = negativeLongs[1]
        nullableWithMaxValue = Long.MAX_VALUE
        nullableWithMinValue = Long.MIN_VALUE
      }

    connector.updateInt64variantsByKey.execute(insertResult.data.key) {}

    val queryResult = connector.getInt64variantsByKey.execute(insertResult.data.key)
    queryResult.data.int64Variants shouldBe
      GetInt64variantsByKeyQuery.Data.Int64variants(
        nonNullWithZeroValue = 0,
        nonNullWithPositiveValue = positiveLongs[0],
        nonNullWithNegativeValue = negativeLongs[0],
        nonNullWithMaxValue = Long.MAX_VALUE,
        nonNullWithMinValue = Long.MIN_VALUE,
        nullableWithNullValue = null,
        nullableWithZeroValue = 0,
        nullableWithPositiveValue = positiveLongs[1],
        nullableWithNegativeValue = negativeLongs[1],
        nullableWithMaxValue = Long.MAX_VALUE,
        nullableWithMinValue = Long.MIN_VALUE,
      )
  }

  @Test
  fun insertUUIDVariants() = runTest {
    val uuids = List(2) { Arb.uuid().next(rs) }

    val insertResult =
      connector.insertUuidVariants.execute(
        nonNullValue = uuids[0],
      ) {
        nullableWithNullValue = null
        nullableWithNonNullValue = uuids[1]
      }

    val queryResult = connector.getUuidVariantsByKey.execute(insertResult.data.key)
    queryResult.data.uUIDVariants shouldBe
      GetUuidVariantsByKeyQuery.Data.UUidVariants(
        nonNullValue = uuids[0],
        nullableWithNullValue = null,
        nullableWithNonNullValue = uuids[1],
      )
  }

  @Test
  @Ignore("TODO(b/341070491) Re-enable this test once default values for UUID variables is fixed")
  fun insertUUIDVariantsWithDefaultValues() = runTest {
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
  fun updateUUIDVariantsToNonNullValues() = runTest {
    val uuids = List(3) { Arb.uuid().next(rs) }

    val insertResult =
      connector.insertUuidVariants.execute(nonNullValue = Arb.uuid().next(rs)) {
        nullableWithNullValue = null
        nullableWithNonNullValue = Arb.uuid().next(rs)
      }

    connector.updateUuidVariantsByKey.execute(insertResult.data.key) {
      nonNullValue = uuids[0]
      nullableWithNullValue = uuids[1]
      nullableWithNonNullValue = uuids[2]
    }

    val queryResult = connector.getUuidVariantsByKey.execute(insertResult.data.key)
    queryResult.data.uUIDVariants shouldBe
      GetUuidVariantsByKeyQuery.Data.UUidVariants(
        nonNullValue = uuids[0],
        nullableWithNullValue = uuids[1],
        nullableWithNonNullValue = uuids[2],
      )
  }

  @Test
  fun updateUUIDVariantsToNullValues() = runTest {
    val uuid = Arb.uuid().next(rs)

    val insertResult =
      connector.insertUuidVariants.execute(nonNullValue = uuid) {
        nullableWithNullValue = null
        nullableWithNonNullValue = Arb.uuid().next(rs)
      }

    connector.updateUuidVariantsByKey.execute(insertResult.data.key) {
      nullableWithNullValue = null
      nullableWithNonNullValue = null
    }

    val queryResult = connector.getUuidVariantsByKey.execute(insertResult.data.key)
    queryResult.data.uUIDVariants shouldBe
      GetUuidVariantsByKeyQuery.Data.UUidVariants(
        nonNullValue = uuid,
        nullableWithNullValue = null,
        nullableWithNonNullValue = null,
      )
  }

  @Test
  fun updateUUIDVariantsToUndefinedValues() = runTest {
    val uuids = List(2) { Arb.uuid().next(rs) }

    val insertResult =
      connector.insertUuidVariants.execute(
        nonNullValue = uuids[0],
      ) {
        nullableWithNullValue = null
        nullableWithNonNullValue = uuids[1]
      }

    connector.updateUuidVariantsByKey.execute(insertResult.data.key) {}

    val queryResult = connector.getUuidVariantsByKey.execute(insertResult.data.key)
    queryResult.data.uUIDVariants shouldBe
      GetUuidVariantsByKeyQuery.Data.UUidVariants(
        nonNullValue = uuids[0],
        nullableWithNullValue = null,
        nullableWithNonNullValue = uuids[1],
      )
  }
}
