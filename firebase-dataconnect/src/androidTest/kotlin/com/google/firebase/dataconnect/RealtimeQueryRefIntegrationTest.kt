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

@file:OptIn(ExperimentalRealtimeQueries::class)

package com.google.firebase.dataconnect

import app.cash.turbine.test
import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import com.google.firebase.dataconnect.testutil.property.arbitrary.pair
import com.google.firebase.dataconnect.testutil.realtimeQuery
import com.google.firebase.dataconnect.testutil.registerDataConnectKotestPrinters
import com.google.firebase.dataconnect.testutil.schemas.RealtimeConnector
import com.google.firebase.dataconnect.testutil.schemas.RealtimeConnector.GetStringByKeyQuery
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.az
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.serializer
import org.junit.Before
import org.junit.Test

/**
 * Integration tests for the
 * [com.google.firebase.dataconnect.core.FirebaseDataConnectInternal.realtimeQuery] method and its
 * return value.
 */
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
      dataConnect.realtimeQuery(
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
      dataConnect.realtimeQuery(
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
      dataConnect.realtimeQuery(
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
      dataConnect.realtimeQuery(
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
}
