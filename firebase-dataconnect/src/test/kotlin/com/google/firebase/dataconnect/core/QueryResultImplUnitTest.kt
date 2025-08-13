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

@file:OptIn(ExperimentalKotest::class)

package com.google.firebase.dataconnect.core

import com.google.firebase.dataconnect.QueryResult
import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.queryRefImpl
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import io.kotest.assertions.assertSoftly
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.int
import io.kotest.property.assume
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

@Suppress("ReplaceCallWithBinaryOperator")
class QueryResultImplUnitTest {

  @Test
  fun `'data' should be the same object given to the constructor`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryRefImpl(), Arb.dataConnect.testData()) {
      query,
      data ->
      val queryResult = query.QueryResultImpl(data, QueryResult.Source.Server)
      queryResult.data shouldBeSameInstanceAs data
    }
  }

  @Test
  fun `'ref' should be the QueryRefImpl object that was used to create it`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryRefImpl(), Arb.dataConnect.testData()) {
      query,
      data ->
      val queryResult = query.QueryResultImpl(data, QueryResult.Source.Server)
      queryResult.ref shouldBeSameInstanceAs query
    }
  }

  @Test
  fun `toString() should contain the expected information`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryRefImpl(), Arb.dataConnect.testData()) {
      query,
      data ->
      val queryResult = query.QueryResultImpl(data, QueryResult.Source.Server)
      val toStringResult = queryResult.toString()
      assertSoftly {
        toStringResult shouldStartWith "QueryResultImpl("
        toStringResult shouldContainWithNonAbuttingText "data=$data"
        toStringResult shouldContainWithNonAbuttingText "ref=$query"
        toStringResult shouldEndWith ")"
      }
    }
  }

  @Test
  fun `equals() should return true for the exact same instance`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryResultImpl()) { queryResult ->
      queryResult.equals(queryResult) shouldBe true
    }
  }

  @Test
  fun `equals() should return true for an equal instance`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryRefImpl(), Arb.dataConnect.testData()) {
      query,
      data ->
      val queryResult1 = query.QueryResultImpl(data, QueryResult.Source.Server)
      val queryResult2 = query.QueryResultImpl(data, QueryResult.Source.Server)
      queryResult1.equals(queryResult2) shouldBe true
    }
  }

  @Test
  fun `equals() should return true if all properties are equal, and 'data' is null`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryRefImpl()) { query ->
      val queryResult1 = query.QueryResultImpl(null, QueryResult.Source.Server)
      val queryResult2 = query.QueryResultImpl(null, QueryResult.Source.Server)
      queryResult1.equals(queryResult2) shouldBe true
    }
  }

  @Test
  fun `equals() should return false for null`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryResultImpl()) { queryResult ->
      queryResult.equals(null) shouldBe false
    }
  }

  @Test
  fun `equals() should return false for a different type`() = runTest {
    val others = Arb.choice(Arb.dataConnect.string(), Arb.int(), Arb.dataConnect.queryRefImpl())
    checkAll(propTestConfig, Arb.dataConnect.queryResultImpl(), others) { queryResult, other ->
      queryResult.equals(other) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only 'data' differs`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.queryRefImpl(),
      Arb.dataConnect.testData(),
      Arb.dataConnect.testData()
    ) { query, data1, data2 ->
      assume(data1 != data2)
      val queryResult1 = query.QueryResultImpl(data1, QueryResult.Source.Server)
      val queryResult2 = query.QueryResultImpl(data2, QueryResult.Source.Server)
      queryResult1.equals(queryResult2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only 'ref' differs`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.queryRefImpl(),
      Arb.dataConnect.queryRefImpl(),
      Arb.dataConnect.testData()
    ) { query1, query2, data,
      ->
      assume(query1 != query2)
      val queryResult1 = query1.QueryResultImpl(data, QueryResult.Source.Server)
      val queryResult2 = query2.QueryResultImpl(data, QueryResult.Source.Server)
      queryResult1.equals(queryResult2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when data of first object is null and second is non-null`() =
    runTest {
      checkAll(propTestConfig, Arb.dataConnect.queryRefImpl(), Arb.dataConnect.testData()) {
        query,
        data,
        ->
        val queryResult1 = query.QueryResultImpl(null, QueryResult.Source.Server)
        val queryResult2 = query.QueryResultImpl(data, QueryResult.Source.Server)
        queryResult1.equals(queryResult2) shouldBe false
      }
    }

  @Test
  fun `equals() should return false when data of second object is null and first is non-null`() =
    runTest {
      checkAll(propTestConfig, Arb.dataConnect.queryRefImpl(), Arb.dataConnect.testData()) {
        query,
        data,
        ->
        val queryResult1 = query.QueryResultImpl(data, QueryResult.Source.Server)
        val queryResult2 = query.QueryResultImpl(null, QueryResult.Source.Server)
        queryResult1.equals(queryResult2) shouldBe false
      }
    }

  @Test
  fun `hashCode() should return the same value each time it is invoked on a given object`() =
    runTest {
      checkAll(propTestConfig, Arb.dataConnect.queryResultImpl()) { queryResult ->
        val hashCode1 = queryResult.hashCode()
        queryResult.hashCode() shouldBe hashCode1
        queryResult.hashCode() shouldBe hashCode1
        queryResult.hashCode() shouldBe hashCode1
      }
    }

  @Test
  fun `hashCode() should return the same value on equal objects`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryRefImpl(), Arb.dataConnect.testData()) {
      query,
      data,
      ->
      val queryResult1 = query.QueryResultImpl(data, QueryResult.Source.Server)
      val queryResult2 = query.QueryResultImpl(data, QueryResult.Source.Server)
      queryResult1.hashCode() shouldBe queryResult2.hashCode()
    }
  }

  @Test
  fun `hashCode() should return a different value if 'data' is different`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.queryRefImpl(),
      Arb.dataConnect.testData(),
      Arb.dataConnect.testData()
    ) { query, data1, data2,
      ->
      assume(data1.hashCode() != data2.hashCode())
      val queryResult1 = query.QueryResultImpl(data1, QueryResult.Source.Server)
      val queryResult2 = query.QueryResultImpl(data2, QueryResult.Source.Server)
      queryResult1.hashCode() shouldNotBe queryResult2.hashCode()
    }
  }

  @Test
  fun `hashCode() should return a different value if 'ref' is different`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.queryRefImpl(),
      Arb.dataConnect.queryRefImpl(),
      Arb.dataConnect.testData()
    ) { query1, query2, data,
      ->
      assume(query1.hashCode() != query2.hashCode())
      val queryResult1 = query1.QueryResultImpl(data, QueryResult.Source.Server)
      val queryResult2 = query2.QueryResultImpl(data, QueryResult.Source.Server)
      queryResult1.hashCode() shouldNotBe queryResult2.hashCode()
    }
  }

  private data class TestVariables(val value: String)

  private data class TestData(val value: String)

  private companion object {
    val propTestConfig = PropTestConfig(iterations = 20)

    fun DataConnectArb.testVariables(string: Arb<String> = string()): Arb<TestVariables> =
      arbitrary {
        TestVariables(string.bind())
      }

    fun DataConnectArb.testData(string: Arb<String> = string()): Arb<TestData> = arbitrary {
      TestData(string.bind())
    }

    fun DataConnectArb.queryResultImpl(
      query: Arb<QueryRefImpl<TestData?, TestVariables>> = queryRefImpl(),
      data: Arb<TestData> = testData()
    ): Arb<QueryRefImpl<TestData?, TestVariables>.QueryResultImpl> = arbitrary {
      query.bind().QueryResultImpl(data.bind(), QueryResult.Source.Server)
    }

    fun DataConnectArb.queryRefImpl(): Arb<QueryRefImpl<TestData?, TestVariables>> =
      queryRefImpl(Arb.dataConnect.testVariables())
  }
}
