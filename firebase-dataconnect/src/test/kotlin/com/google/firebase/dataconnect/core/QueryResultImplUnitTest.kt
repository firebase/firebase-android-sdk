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
import com.google.firebase.dataconnect.testutil.property.arbitrary.distinctPair
import com.google.firebase.dataconnect.testutil.property.arbitrary.queryRefImpl
import com.google.firebase.dataconnect.testutil.property.arbitrary.queryResult
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.assume
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

@Suppress("ReplaceCallWithBinaryOperator")
class QueryResultImplUnitTest {

  @Test
  fun `properties should be the same objects given to or inferred by the constructor`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.queryRefImpl(),
      Arb.dataConnect.testData(),
      Arb.dataConnect.queryResult.source()
    ) { ref, data, source ->
      val queryResult = ref.QueryResultImpl(data, source)
      assertSoftly {
        withClue("ref") { queryResult.ref shouldBeSameInstanceAs ref }
        withClue("data") { queryResult.data shouldBeSameInstanceAs data }
        withClue("source") { queryResult.source shouldBeSameInstanceAs source }
      }
    }
  }

  @Test
  fun `toString() should contain the expected information`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.queryRefImpl(),
      Arb.dataConnect.testData(),
      Arb.dataConnect.queryResult.source()
    ) { query, data, source ->
      val queryResult = query.QueryResultImpl(data, source)
      val toStringResult = queryResult.toString()
      assertSoftly {
        toStringResult shouldStartWith "QueryResultImpl("
        toStringResult shouldContainWithNonAbuttingText "data=$data"
        toStringResult shouldContainWithNonAbuttingText "source=$source"
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
    checkAll(
      propTestConfig,
      Arb.dataConnect.queryRefImpl(),
      Arb.dataConnect.testData(),
      Arb.dataConnect.queryResult.source()
    ) { query, data, source ->
      val queryResult1 = query.QueryResultImpl(data, source)
      val queryResult2 = query.QueryResultImpl(data, source)
      queryResult1.equals(queryResult2) shouldBe true
    }
  }

  @Test
  fun `equals() should return true if all properties are equal, and 'data' is null`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.queryRefImpl(),
      Arb.dataConnect.queryResult.source()
    ) { query, source ->
      val queryResult1 = query.QueryResultImpl(null, source)
      val queryResult2 = query.QueryResultImpl(null, source)
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
      Arb.dataConnect.testData().distinctPair(),
      Arb.dataConnect.queryResult.source(),
    ) { query, (data1, data2), source ->
      val queryResult1 = query.QueryResultImpl(data1, source)
      val queryResult2 = query.QueryResultImpl(data2, source)
      queryResult1.equals(queryResult2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only 'source' differs`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.queryRefImpl(),
      Arb.dataConnect.testData(),
      Arb.dataConnect.queryResult.source().distinctPair(),
    ) { query, data, (source1, source2) ->
      val queryResult1 = query.QueryResultImpl(data, source1)
      val queryResult2 = query.QueryResultImpl(data, source2)
      queryResult1.equals(queryResult2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only 'ref' differs`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.queryRefImpl().distinctPair(),
      Arb.dataConnect.testData(),
      Arb.dataConnect.queryResult.source(),
    ) { (query1, query2), data, source ->
      assume(query1 != query2)
      val queryResult1 = query1.QueryResultImpl(data, source)
      val queryResult2 = query2.QueryResultImpl(data, source)
      queryResult1.equals(queryResult2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when data of first data is null and second is non-null`() =
    runTest {
      checkAll(
        propTestConfig,
        Arb.dataConnect.queryRefImpl(),
        Arb.dataConnect.testData(),
        Arb.dataConnect.queryResult.source()
      ) { query, data, source,
        ->
        val queryResult1 = query.QueryResultImpl(null, source)
        val queryResult2 = query.QueryResultImpl(data, source)
        queryResult1.equals(queryResult2) shouldBe false
      }
    }

  @Test
  fun `equals() should return false when data of second object is null and first is non-null`() =
    runTest {
      checkAll(
        propTestConfig,
        Arb.dataConnect.queryRefImpl(),
        Arb.dataConnect.testData(),
        Arb.dataConnect.queryResult.source()
      ) { query, data, source,
        ->
        val queryResult1 = query.QueryResultImpl(data, source)
        val queryResult2 = query.QueryResultImpl(null, source)
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
    checkAll(
      propTestConfig,
      Arb.dataConnect.queryRefImpl(),
      Arb.dataConnect.testData(),
      Arb.dataConnect.queryResult.source()
    ) { query, data, source,
      ->
      val queryResult1 = query.QueryResultImpl(data, source)
      val queryResult2 = query.QueryResultImpl(data, source)
      queryResult1.hashCode() shouldBe queryResult2.hashCode()
    }
  }

  @Test
  fun `hashCode() should return a different value if 'data' is different`() = runTest {
    checkAll(
      hashEqualityPropTestConfig,
      Arb.dataConnect.queryRefImpl(),
      Arb.dataConnect.testData().distinctPair(),
      Arb.dataConnect.queryResult.source(),
    ) { query, (data1, data2), source,
      ->
      assume(data1.hashCode() != data2.hashCode())
      val queryResult1 = query.QueryResultImpl(data1, source)
      val queryResult2 = query.QueryResultImpl(data2, source)
      queryResult1.hashCode() shouldNotBe queryResult2.hashCode()
    }
  }

  @Test
  fun `hashCode() should return a different value if 'source' is different`() = runTest {
    checkAll(
      hashEqualityPropTestConfig,
      Arb.dataConnect.queryRefImpl(),
      Arb.dataConnect.testData(),
      Arb.dataConnect.queryResult.source().distinctPair(),
    ) { query, data, (source1, source2),
      ->
      assume(source1.hashCode() != source2.hashCode())
      val queryResult1 = query.QueryResultImpl(data, source1)
      val queryResult2 = query.QueryResultImpl(data, source2)
      queryResult1.hashCode() shouldNotBe queryResult2.hashCode()
    }
  }

  @Test
  fun `hashCode() should return a different value if 'ref' is different`() = runTest {
    checkAll(
      hashEqualityPropTestConfig,
      Arb.dataConnect.queryRefImpl().distinctPair(),
      Arb.dataConnect.testData(),
      Arb.dataConnect.queryResult.source(),
    ) { (query1, query2), data, source ->
      assume(query1.hashCode() != query2.hashCode())
      val queryResult1 = query1.QueryResultImpl(data, source)
      val queryResult2 = query2.QueryResultImpl(data, source)
      queryResult1.hashCode() shouldNotBe queryResult2.hashCode()
    }
  }

  private data class TestVariables(val value: String)

  private data class TestData(val value: String)

  private companion object {
    val propTestConfig = PropTestConfig(iterations = 20)

    // Allow a small number of failures to account for the rare, but possible situation where two
    // distinct instances produce the same hash code.
    val hashEqualityPropTestConfig =
      propTestConfig.copy(
        minSuccess = propTestConfig.iterations!! - 2,
        maxFailure = 2,
      )

    fun DataConnectArb.testVariables(string: Arb<String> = string()): Arb<TestVariables> =
      string.map { TestVariables(it) }

    fun DataConnectArb.testData(string: Arb<String> = string()): Arb<TestData> =
      string.map { TestData(it) }

    fun DataConnectArb.queryResultImpl(
      query: Arb<QueryRefImpl<TestData?, TestVariables>> = queryRefImpl(),
      data: Arb<TestData> = testData(),
      source: Arb<QueryResult.Source> = queryResult.source(),
    ): Arb<QueryRefImpl<TestData?, TestVariables>.QueryResultImpl> =
      Arb.bind(query, data, source) { query, data, source -> query.QueryResultImpl(data, source) }

    fun DataConnectArb.queryRefImpl(): Arb<QueryRefImpl<TestData?, TestVariables>> =
      queryRefImpl(Arb.dataConnect.testVariables())
  }
}
