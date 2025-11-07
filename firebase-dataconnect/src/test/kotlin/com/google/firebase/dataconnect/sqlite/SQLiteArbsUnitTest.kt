/*
 * Copyright 2025 Google LLC
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

package com.google.firebase.dataconnect.sqlite

import com.google.firebase.dataconnect.testutil.shouldNotContainLoneSurrogates
import io.kotest.assertions.assertSoftly
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SQLiteArbsUnitTest {

  @Test
  fun `nullColumnValue should always return a NullColumnValue instance`() = runTest {
    checkAll(propTestConfig, Arb.sqlite.nullColumnValue()) { value ->
      value shouldBe SQLiteArbs.NullColumnValue
    }
  }

  @Test
  fun `intColumnValue should generate IntColumnValue instances`() = runTest {
    val bindArgsValues = mutableListOf<Int>()
    checkAll(propTestConfig, Arb.sqlite.intColumnValue()) { value ->
      bindArgsValues.add(value.bindArgsValue)
    }

    assertSoftly {
      bindArgsValues shouldContain 0
      bindArgsValues shouldContain 1
      bindArgsValues shouldContain -1
      bindArgsValues shouldContain Int.MIN_VALUE
      bindArgsValues shouldContain Int.MAX_VALUE
      bindArgsValues.count { it < 0 } shouldBeGreaterThan 200
      bindArgsValues.count { it > 0 } shouldBeGreaterThan 200
    }
  }

  @Test
  fun `longColumnValue should generate LongColumnValue instances`() = runTest {
    val bindArgsValues = mutableListOf<Long>()
    checkAll(propTestConfig, Arb.sqlite.longColumnValue()) { value ->
      bindArgsValues.add(value.bindArgsValue)
    }

    assertSoftly {
      bindArgsValues shouldContain 0
      bindArgsValues shouldContain 1
      bindArgsValues shouldContain -1
      bindArgsValues shouldContain Long.MIN_VALUE
      bindArgsValues shouldContain Long.MAX_VALUE
      bindArgsValues.count { it < 0 } shouldBeGreaterThan 200
      bindArgsValues.count { it > 0 } shouldBeGreaterThan 200
    }
  }

  @Test
  fun `floatColumnValue should generate FloatColumnValue instances`() = runTest {
    val bindArgsValues = mutableListOf<Float>()
    checkAll(propTestConfig, Arb.sqlite.floatColumnValue()) { value ->
      bindArgsValues.add(value.bindArgsValue)
    }

    assertSoftly {
      bindArgsValues shouldContain 0.0f
      bindArgsValues shouldContain -0.0f
      bindArgsValues shouldContain Float.MIN_VALUE
      bindArgsValues shouldContain Float.MAX_VALUE
      bindArgsValues shouldContain Float.POSITIVE_INFINITY
      bindArgsValues shouldContain Float.NEGATIVE_INFINITY
      bindArgsValues.count { it < 0.0 } shouldBeGreaterThan 200
      bindArgsValues.count { it > 0.0 } shouldBeGreaterThan 200
      bindArgsValues.count { it.isNaN() } shouldBeGreaterThan 1
    }
  }

  @Test
  fun `doubleColumnValue should generate DoubleColumnValue instances`() = runTest {
    val bindArgsValues = mutableListOf<Double>()
    checkAll(propTestConfig, Arb.sqlite.doubleColumnValue()) { value ->
      bindArgsValues.add(value.bindArgsValue)
    }

    assertSoftly {
      bindArgsValues shouldContain 0.0f
      bindArgsValues shouldContain -0.0f
      bindArgsValues shouldContain Double.MIN_VALUE
      bindArgsValues shouldContain Double.MAX_VALUE
      bindArgsValues shouldContain Double.POSITIVE_INFINITY
      bindArgsValues shouldContain Double.NEGATIVE_INFINITY
      bindArgsValues.count { it < 0.0 } shouldBeGreaterThan 200
      bindArgsValues.count { it > 0.0 } shouldBeGreaterThan 200
      bindArgsValues.count { it.isNaN() } shouldBeGreaterThan 1
    }
  }

  @Test
  fun `stringColumnValue should generate StringColumnValue instances`() = runTest {
    val bindArgsValues = mutableListOf<String>()
    checkAll(propTestConfig, Arb.sqlite.stringColumnValue()) { value ->
      bindArgsValues.add(value.bindArgsValue)
    }

    assertSoftly {
      bindArgsValues shouldContain ""
      bindArgsValues.count { it.replace("'", "").isEmpty() } shouldBeGreaterThan 0
      bindArgsValues.count { it.startsWith("'") } shouldBeGreaterThan 0
      bindArgsValues.count { it.endsWith("'") } shouldBeGreaterThan 0
      bindArgsValues.count { it.startsWith("'") && it.endsWith("'") } shouldBeGreaterThan 0
    }
  }

  @Test
  fun `stringColumnValue should not produce lone surrogates`() = runTest {
    checkAll(propTestConfig, Arb.sqlite.stringColumnValue()) { value ->
      value.bindArgsValue.shouldNotContainLoneSurrogates()
    }
  }

  @Test
  fun `byteArrayColumnValue should generate ByteArrayColumnValue instances`() = runTest {
    val bindArgsValues = mutableListOf<ByteArray>()
    checkAll(propTestConfig, Arb.sqlite.byteArrayColumnValue()) { value ->
      bindArgsValues.add(value.bindArgsValue)
    }

    assertSoftly {
      bindArgsValues.count { it.isEmpty() } shouldBeGreaterThan 10
      bindArgsValues.count { it.size > 20 } shouldBeGreaterThan 200
      bindArgsValues.count { it.size < 20 } shouldBeGreaterThan 200
    }
  }

  @Test
  fun `columnValue should generate all ColumnValue types`() = runTest {
    val columnValues = mutableListOf<SQLiteArbs.ColumnValue<*>>()
    checkAll(propTestConfig, Arb.sqlite.columnValue()) { value -> columnValues.add(value) }

    assertSoftly {
      columnValues.count { it is SQLiteArbs.NullColumnValue } shouldBeGreaterThan 10
      columnValues.count { it is SQLiteArbs.IntColumnValue } shouldBeGreaterThan 10
      columnValues.count { it is SQLiteArbs.LongColumnValue } shouldBeGreaterThan 10
      columnValues.count { it is SQLiteArbs.FloatColumnValue } shouldBeGreaterThan 10
      columnValues.count { it is SQLiteArbs.DoubleColumnValue } shouldBeGreaterThan 10
      columnValues.count { it is SQLiteArbs.StringColumnValue } shouldBeGreaterThan 10
      columnValues.count { it is SQLiteArbs.ByteArrayColumnValue } shouldBeGreaterThan 10
    }
  }

  private companion object {

    @OptIn(ExperimentalKotest::class)
    val propTestConfig =
      PropTestConfig(
        iterations = 1000,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.5)
      )
  }
}
