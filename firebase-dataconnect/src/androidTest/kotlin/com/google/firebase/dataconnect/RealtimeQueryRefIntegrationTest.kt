/*
 * Copyright 2026 Google LLC
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

package com.google.firebase.dataconnect

import app.cash.turbine.test
import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import com.google.firebase.dataconnect.testutil.property.arbitrary.pair
import com.google.firebase.dataconnect.testutil.registerDataConnectKotestPrinters
import com.google.firebase.dataconnect.testutil.schemas.RealtimeConnector
import com.google.firebase.dataconnect.testutil.schemas.RealtimeConnector.GetStringByKeyQuery
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.az
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlin.time.Duration
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.serializer
import org.junit.Before
import org.junit.Test

class RealtimeQueryRefIntegrationTest : DataConnectIntegrationTestBase() {

  private val nameArb = Arb.string(size = 4, Codepoint.az()).map { "name_$it" }

  @Before
  fun registerPrinters() {
    registerDataConnectKotestPrinters()
  }

  @Test
  fun emittedResultsHaveTheCorrectQueryProperty() = runTest {
    val dataConnect = dataConnectFactory.newInstance(RealtimeConnector.config)
    val connector = RealtimeConnector.getInstance(dataConnect)
    val (name1, name2) = nameArb.pair().sample()
    val key = connector.insertString(name1)

    val ref =
      dataConnect.query(
        GetStringByKeyQuery.OPERATION_NAME,
        GetStringByKeyQuery.Variables(key),
        serializer<GetStringByKeyQuery.Data>(),
        serializer(),
      )

    ref.subscribe().flow.test {
      awaitItem().query shouldBeSameInstanceAs ref
      connector.updateString(key, name2)
      awaitItem().query shouldBeSameInstanceAs ref
      connector.deleteString(key)
      awaitItem().query shouldBeSameInstanceAs ref
    }
  }

  @Test
  fun emittedResultsHaveResultsWithTheCorrectQuery() = runTest {
    val dataConnect = dataConnectFactory.newInstance(RealtimeConnector.config)
    val connector = RealtimeConnector.getInstance(dataConnect)
    val (name1, name2) = nameArb.pair().sample()
    val key = connector.insertString(name1)

    val ref =
      dataConnect.query(
        GetStringByKeyQuery.OPERATION_NAME,
        GetStringByKeyQuery.Variables(key),
        serializer<GetStringByKeyQuery.Data>(),
        serializer(),
      )

    ref.subscribe().flow.test {
      awaitItem().result.getOrThrow().ref shouldBeSameInstanceAs ref
      connector.updateString(key, name2)
      awaitItem().result.getOrThrow().ref shouldBeSameInstanceAs ref
      connector.deleteString(key)
      awaitItem().result.getOrThrow().ref shouldBeSameInstanceAs ref
    }
  }

  @Test
  fun emittedResultsHaveResultsWithTheCorrectData() = runTest {
    val dataConnect = dataConnectFactory.newInstance(RealtimeConnector.config)
    val connector = RealtimeConnector.getInstance(dataConnect)
    val (name1, name2) = nameArb.pair().sample()
    val key = connector.insertString(name1)

    val ref =
      dataConnect.query(
        GetStringByKeyQuery.OPERATION_NAME,
        GetStringByKeyQuery.Variables(key),
        serializer<GetStringByKeyQuery.Data>(),
        serializer(),
      )

    ref.subscribe().flow.test {
      awaitItem().result.getOrThrow().data shouldBe GetStringByKeyQuery.Data(name1)
      connector.updateString(key, name2)
      awaitItem().result.getOrThrow().data shouldBe GetStringByKeyQuery.Data(name2)
      connector.deleteString(key)
      awaitItem().result.getOrThrow().data shouldBe GetStringByKeyQuery.Data(null)
    }
  }

  @Test
  fun emittedResultsHaveResultsWithTheCorrectDataSource() = runTest {
    val dataConnect = dataConnectFactory.newInstance(RealtimeConnector.config)
    val connector = RealtimeConnector.getInstance(dataConnect)
    val (name1, name2) = nameArb.pair().sample()
    val key = connector.insertString(name1)

    val ref =
      dataConnect.query(
        GetStringByKeyQuery.OPERATION_NAME,
        GetStringByKeyQuery.Variables(key),
        serializer<GetStringByKeyQuery.Data>(),
        serializer(),
      )

    ref.subscribe().flow.test {
      awaitItem().result.getOrThrow().dataSource shouldBe DataSource.SERVER
      connector.updateString(key, name2)
      awaitItem().result.getOrThrow().dataSource shouldBe DataSource.SERVER
      connector.deleteString(key)
      awaitItem().result.getOrThrow().dataSource shouldBe DataSource.SERVER
    }
  }

  @Test
  fun emittedResultsUpdateTheLocalCacheForTheUnderlyingQuery() = runTest {
    val dataConnect =
      dataConnectFactory.newInstance(
        RealtimeConnector.config,
        CacheSettings(maxAge = Duration.INFINITE)
      )
    val connector = RealtimeConnector.getInstance(dataConnect)
    val fetchPolicyArb = Arb.of(QueryRef.FetchPolicy.PREFER_CACHE, QueryRef.FetchPolicy.CACHE_ONLY)

    checkAll(propTestConfig, nameArb.pair(), fetchPolicyArb) { (name1, name2), fetchPolicy ->
      val key = connector.insertString(name1)

      val ref =
        dataConnect.query(
          GetStringByKeyQuery.OPERATION_NAME,
          GetStringByKeyQuery.Variables(key),
          serializer<GetStringByKeyQuery.Data>(),
          serializer(),
        )

      suspend fun verifyCachedData(
        clue: String,
        fetchPolicy: QueryRef.FetchPolicy,
        expectedNames: List<String>?,
      ) {
        withClue(clue) {
          val result = ref.execute(fetchPolicy)
          result.dataSource shouldBe DataSource.CACHE
          if (expectedNames == null) {
            result.data.item.shouldBeNull()
          } else {
            result.data shouldBeIn expectedNames.map(GetStringByKeyQuery::Data)
          }
        }
      }

      suspend fun verifyCachedData(
        clue: String,
        fetchPolicy: QueryRef.FetchPolicy,
        vararg expectedNames: String,
      ) = verifyCachedData(clue, fetchPolicy, expectedNames.toList())

      ref.subscribe().flow.test {
        suspend fun awaitItemAndCheckName(clue: String, expectedName: String?) {
          val queryResult = awaitItem().result.shouldBeSuccess()
          val expectedData =
            GetStringByKeyQuery.Data(GetStringByKeyQuery.Data.Item.fromNameOrNull(expectedName))
          check(queryResult.data == expectedData) {
            "$clue internal test error cvyx4f32ft: queryResult=$queryResult, " +
              "but expected its data to be $expectedData"
          }
        }

        awaitItemAndCheckName("awaitCheck1", name1)
        verifyCachedData("cacheCheck1", fetchPolicy, name1)

        connector.updateString(key, name2)
        verifyCachedData("cacheCheck2", fetchPolicy, name1, name2)
        awaitItemAndCheckName("awaitCheck2", name2)
        verifyCachedData("cacheCheck3", fetchPolicy, name2)

        connector.deleteString(key)
        verifyCachedData("cacheCheck4", fetchPolicy, name2)
        awaitItemAndCheckName("awaitCheck3", null)
        verifyCachedData("cacheCheck5", fetchPolicy, null)
      }
    }
  }
}

@OptIn(ExperimentalKotest::class)
private val propTestConfig =
  PropTestConfig(
    iterations = 10,
    edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2),
    shrinkingMode = ShrinkingMode.Off,
  )
