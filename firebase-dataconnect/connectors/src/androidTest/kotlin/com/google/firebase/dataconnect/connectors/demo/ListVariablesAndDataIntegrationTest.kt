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

import com.google.firebase.Timestamp
import com.google.firebase.dataconnect.LocalDate
import com.google.firebase.dataconnect.connectors.demo.testutil.DemoConnectorIntegrationTestBase
import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.EdgeCases
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.localDate
import com.google.firebase.dataconnect.testutil.withMicrosecondPrecision
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.uuid
import io.kotest.property.checkAll
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ListVariablesAndDataIntegrationTest : DemoConnectorIntegrationTestBase() {

  @Test
  fun insertNonNullableEmptyLists() =
    runTest(timeout = 60.seconds) {
      val insertResult =
        connector.insertNonNullableLists.execute(
          strings = emptyList(),
          ints = emptyList(),
          floats = emptyList(),
          booleans = emptyList(),
          uuids = emptyList(),
          int64s = emptyList(),
          dates = emptyList(),
          timestamps = emptyList(),
        )

      val queryResult = connector.getNonNullableListsByKey.execute(insertResult.data.key)

      queryResult.data shouldBe
        GetNonNullableListsByKeyQuery.Data(
          GetNonNullableListsByKeyQuery.Data.NonNullableLists(
            strings = emptyList(),
            ints = emptyList(),
            floats = emptyList(),
            booleans = emptyList(),
            uuids = emptyList(),
            int64s = emptyList(),
            dates = emptyList(),
            timestamps = emptyList(),
          )
        )
    }

  @Test
  fun insertNonNullableNonEmptyLists() =
    runTest(timeout = 60.seconds) {
      checkAll(propTestConfig, Arb.dataConnect.nonEmptyLists()) { lists ->
        val insertResult =
          connector.insertNonNullableLists.execute(
            strings = lists.strings,
            ints = lists.ints,
            floats = lists.floats,
            booleans = lists.booleans,
            uuids = lists.uuids,
            int64s = lists.int64s,
            dates = lists.dates,
            timestamps = lists.timestamps,
          )

        val queryResult = connector.getNonNullableListsByKey.execute(insertResult.data.key)

        queryResult.data shouldBe lists.withRoundTripValues().toGetNonNullableListsByKeyData()
      }
    }

  @Test
  fun insertNonNullableListsWithEdgeCases() =
    runTest(timeout = 60.seconds) {
      val sendEdgeCases = Lists.edgeCases

      val insertResult =
        connector.insertNonNullableLists.execute(
          strings = sendEdgeCases.strings,
          ints = sendEdgeCases.ints,
          floats = sendEdgeCases.floats,
          booleans = sendEdgeCases.booleans,
          uuids = sendEdgeCases.uuids,
          int64s = sendEdgeCases.int64s,
          dates = sendEdgeCases.dates,
          timestamps = sendEdgeCases.timestamps,
        )

      val queryResult = connector.getNonNullableListsByKey.execute(insertResult.data.key)

      queryResult.data shouldBe sendEdgeCases.withRoundTripValues().toGetNonNullableListsByKeyData()
    }

  @Test
  fun updateNonNullableEmptyListsToNonEmpty() =
    runTest(timeout = 60.seconds) {
      checkAll(propTestConfig, Arb.dataConnect.nonEmptyLists()) { lists ->
        val insertResult =
          connector.insertNonNullableLists.execute(
            strings = emptyList(),
            ints = emptyList(),
            floats = emptyList(),
            booleans = emptyList(),
            uuids = emptyList(),
            int64s = emptyList(),
            dates = emptyList(),
            timestamps = emptyList(),
          )

        connector.updateNonNullableListsByKey.execute(insertResult.data.key) {
          strings = lists.strings
          ints = lists.ints
          floats = lists.floats
          booleans = lists.booleans
          uuids = lists.uuids
          int64s = lists.int64s
          dates = lists.dates
          timestamps = lists.timestamps
        }

        val queryResult = connector.getNonNullableListsByKey.execute(insertResult.data.key)

        queryResult.data shouldBe lists.withRoundTripValues().toGetNonNullableListsByKeyData()
      }
    }

  @Test
  fun updateNonNullableNonEmptyListsToEmpty() =
    runTest(timeout = 60.seconds) {
      checkAll(propTestConfig, Arb.dataConnect.nonEmptyLists()) { lists ->
        val insertResult =
          connector.insertNonNullableLists.execute(
            strings = lists.strings,
            ints = lists.ints,
            floats = lists.floats,
            booleans = lists.booleans,
            uuids = lists.uuids,
            int64s = lists.int64s,
            dates = lists.dates,
            timestamps = lists.timestamps,
          )

        connector.updateNonNullableListsByKey.execute(insertResult.data.key) {
          strings = emptyList()
          ints = emptyList()
          floats = emptyList()
          booleans = emptyList()
          uuids = emptyList()
          int64s = emptyList()
          dates = emptyList()
          timestamps = emptyList()
        }

        val queryResult = connector.getNonNullableListsByKey.execute(insertResult.data.key)

        queryResult.data shouldBe
          GetNonNullableListsByKeyQuery.Data(
            GetNonNullableListsByKeyQuery.Data.NonNullableLists(
              strings = emptyList(),
              ints = emptyList(),
              floats = emptyList(),
              booleans = emptyList(),
              uuids = emptyList(),
              int64s = emptyList(),
              dates = emptyList(),
              timestamps = emptyList(),
            )
          )
      }
    }

  @Test
  fun updateNonNullableWithUndefinedLists() =
    runTest(timeout = 60.seconds) {
      checkAll(propTestConfig, Arb.dataConnect.nonEmptyLists()) { lists ->
        val insertResult =
          connector.insertNonNullableLists.execute(
            strings = lists.strings,
            ints = lists.ints,
            floats = lists.floats,
            booleans = lists.booleans,
            uuids = lists.uuids,
            int64s = lists.int64s,
            dates = lists.dates,
            timestamps = lists.timestamps,
          )

        connector.updateNonNullableListsByKey.execute(insertResult.data.key) {}

        val queryResult = connector.getNonNullableListsByKey.execute(insertResult.data.key)

        queryResult.data shouldBe lists.withRoundTripValues().toGetNonNullableListsByKeyData()
      }
    }

  @Test
  fun insertNullableEmptyLists() =
    runTest(timeout = 60.seconds) {
      val insertResult =
        connector.insertNullableLists.execute {
          strings = emptyList()
          ints = emptyList()
          floats = emptyList()
          booleans = emptyList()
          uuids = emptyList()
          int64s = emptyList()
          dates = emptyList()
          timestamps = emptyList()
        }

      val queryResult = connector.getNullableListsByKey.execute(insertResult.data.key)

      queryResult.data shouldBe
        GetNullableListsByKeyQuery.Data(
          GetNullableListsByKeyQuery.Data.NullableLists(
            strings = emptyList(),
            ints = emptyList(),
            floats = emptyList(),
            booleans = emptyList(),
            uuids = emptyList(),
            int64s = emptyList(),
            dates = emptyList(),
            timestamps = emptyList(),
          )
        )
    }

  @Test
  fun insertNullableUndefinedLists() =
    runTest(timeout = 60.seconds) {
      val insertResult = connector.insertNullableLists.execute {}

      val queryResult = connector.getNullableListsByKey.execute(insertResult.data.key)

      queryResult.data shouldBe
        GetNullableListsByKeyQuery.Data(
          GetNullableListsByKeyQuery.Data.NullableLists(
            strings = null,
            ints = null,
            floats = null,
            booleans = null,
            uuids = null,
            int64s = null,
            dates = null,
            timestamps = null,
          )
        )
    }

  @Test
  fun insertNullableNonEmptyLists() =
    runTest(timeout = 60.seconds) {
      checkAll(propTestConfig, Arb.dataConnect.nonEmptyLists()) { lists ->
        val insertResult =
          connector.insertNullableLists.execute {
            strings = lists.strings
            ints = lists.ints
            floats = lists.floats
            booleans = lists.booleans
            uuids = lists.uuids
            int64s = lists.int64s
            dates = lists.dates
            timestamps = lists.timestamps
          }

        val queryResult = connector.getNullableListsByKey.execute(insertResult.data.key)

        queryResult.data shouldBe lists.withRoundTripValues().toGetNullableListsByKeyData()
      }
    }

  @Test
  fun insertNullableListsWithEdgeCases() =
    runTest(timeout = 60.seconds) {
      val edgeCases = Lists.edgeCases

      val insertResult =
        connector.insertNullableLists.execute {
          strings = edgeCases.strings
          ints = edgeCases.ints
          floats = edgeCases.floats
          booleans = edgeCases.booleans
          uuids = edgeCases.uuids
          int64s = edgeCases.int64s
          dates = edgeCases.dates
          timestamps = edgeCases.timestamps
        }

      val queryResult = connector.getNullableListsByKey.execute(insertResult.data.key)

      queryResult.data shouldBe edgeCases.withRoundTripValues().toGetNullableListsByKeyData()
    }

  @Test
  fun updateNullableEmptyListsToNonEmpty() =
    runTest(timeout = 60.seconds) {
      checkAll(propTestConfig, Arb.dataConnect.nonEmptyLists()) { lists ->
        val insertResult =
          connector.insertNullableLists.execute {
            strings = emptyList()
            ints = emptyList()
            floats = emptyList()
            booleans = emptyList()
            uuids = emptyList()
            int64s = emptyList()
            dates = emptyList()
            timestamps = emptyList()
          }

        connector.updateNullableListsByKey.execute(insertResult.data.key) {
          strings = lists.strings
          ints = lists.ints
          floats = lists.floats
          booleans = lists.booleans
          uuids = lists.uuids
          int64s = lists.int64s
          dates = lists.dates
          timestamps = lists.timestamps
        }

        val queryResult = connector.getNullableListsByKey.execute(insertResult.data.key)

        queryResult.data shouldBe lists.withRoundTripValues().toGetNullableListsByKeyData()
      }
    }

  @Test
  fun updateNullableNonEmptyListsToEmpty() =
    runTest(timeout = 60.seconds) {
      checkAll(propTestConfig, Arb.dataConnect.nonEmptyLists()) { lists ->
        val insertResult =
          connector.insertNullableLists.execute {
            strings = lists.strings
            ints = lists.ints
            floats = lists.floats
            booleans = lists.booleans
            uuids = lists.uuids
            int64s = lists.int64s
            dates = lists.dates
            timestamps = lists.timestamps
          }

        connector.updateNullableListsByKey.execute(insertResult.data.key) {
          strings = emptyList()
          ints = emptyList()
          floats = emptyList()
          booleans = emptyList()
          uuids = emptyList()
          int64s = emptyList()
          dates = emptyList()
          timestamps = emptyList()
        }

        val queryResult = connector.getNullableListsByKey.execute(insertResult.data.key)

        queryResult.data shouldBe
          GetNullableListsByKeyQuery.Data(
            GetNullableListsByKeyQuery.Data.NullableLists(
              strings = emptyList(),
              ints = emptyList(),
              floats = emptyList(),
              booleans = emptyList(),
              uuids = emptyList(),
              int64s = emptyList(),
              dates = emptyList(),
              timestamps = emptyList(),
            )
          )
      }
    }

  @Test
  fun updateNullableNonEmptyListsToNull() =
    runTest(timeout = 60.seconds) {
      checkAll(propTestConfig, Arb.dataConnect.nonEmptyLists()) { lists ->
        val insertResult =
          connector.insertNullableLists.execute {
            strings = lists.strings
            ints = lists.ints
            floats = lists.floats
            booleans = lists.booleans
            uuids = lists.uuids
            int64s = lists.int64s
            dates = lists.dates
            timestamps = lists.timestamps
          }

        connector.updateNullableListsByKey.execute(insertResult.data.key) {
          strings = null
          ints = null
          floats = null
          booleans = null
          uuids = null
          int64s = null
          dates = null
          timestamps = null
        }

        val queryResult = connector.getNullableListsByKey.execute(insertResult.data.key)

        queryResult.data shouldBe
          GetNullableListsByKeyQuery.Data(
            GetNullableListsByKeyQuery.Data.NullableLists(
              strings = null,
              ints = null,
              floats = null,
              booleans = null,
              uuids = null,
              int64s = null,
              dates = null,
              timestamps = null,
            )
          )
      }
    }

  @Test
  fun updateNullableWithUndefinedLists() =
    runTest(timeout = 60.seconds) {
      checkAll(propTestConfig, Arb.dataConnect.nonEmptyLists()) { lists ->
        val insertResult =
          connector.insertNullableLists.execute {
            strings = lists.strings
            ints = lists.ints
            floats = lists.floats
            booleans = lists.booleans
            uuids = lists.uuids
            int64s = lists.int64s
            dates = lists.dates
            timestamps = lists.timestamps
          }

        connector.updateNullableListsByKey.execute(insertResult.data.key) {}

        val queryResult = connector.getNullableListsByKey.execute(insertResult.data.key)

        queryResult.data shouldBe lists.withRoundTripValues().toGetNullableListsByKeyData()
      }
    }

  private data class Lists(
    val strings: List<String>,
    val ints: List<Int>,
    val floats: List<Double>,
    val booleans: List<Boolean>,
    val uuids: List<UUID>,
    val int64s: List<Long>,
    val dates: List<LocalDate>,
    val timestamps: List<Timestamp>,
  ) {

    fun withRoundTripValues(): Lists =
      copy(
        // -0.0 gets coerced to 0.0 due to lack of JSONB support for -0.0 (see b/339440054).
        floats = floats.map { if (it != -0.0) it else 0.0 },
        timestamps = timestamps.map { it.withMicrosecondPrecision() },
      )

    fun toGetNonNullableListsByKeyData(): GetNonNullableListsByKeyQuery.Data =
      GetNonNullableListsByKeyQuery.Data(
        GetNonNullableListsByKeyQuery.Data.NonNullableLists(
          strings = strings,
          ints = ints,
          floats = floats,
          booleans = booleans,
          uuids = uuids,
          int64s = int64s,
          dates = dates,
          timestamps = timestamps,
        )
      )

    fun toGetNullableListsByKeyData(): GetNullableListsByKeyQuery.Data =
      GetNullableListsByKeyQuery.Data(
        GetNullableListsByKeyQuery.Data.NullableLists(
          strings = strings,
          ints = ints,
          floats = floats,
          booleans = booleans,
          uuids = uuids,
          int64s = int64s,
          dates = dates,
          timestamps = timestamps,
        )
      )

    companion object {
      val edgeCases: Lists
        get() =
          Lists(
            strings = EdgeCases.strings,
            ints = EdgeCases.ints,
            floats = EdgeCases.floats,
            booleans = EdgeCases.booleans,
            uuids = EdgeCases.uuids,
            int64s = EdgeCases.int64s,
            dates = EdgeCases.dates.all().map { it.date },
            timestamps = EdgeCases.javaTime.instants.all.map { it.timestamp },
          )
    }
  }

  private companion object {
    @OptIn(ExperimentalKotest::class)
    val propTestConfig =
      PropTestConfig(
        iterations = 10,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.33)
      )

    @Suppress("UnusedReceiverParameter")
    fun DataConnectArb.nonEmptyLists(
      strings: Arb<List<String>> = Arb.list(Arb.dataConnect.string(), 1..100),
      ints: Arb<List<Int>> = Arb.list(Arb.int(), 1..100),
      floats: Arb<List<Double>> = Arb.list(Arb.dataConnect.float(), 1..100),
      booleans: Arb<List<Boolean>> = Arb.list(Arb.boolean(), 1..100),
      uuids: Arb<List<UUID>> = Arb.list(Arb.uuid(), 1..100),
      int64s: Arb<List<Long>> = Arb.list(Arb.long(), 1..100),
      dates: Arb<List<LocalDate>> = Arb.list(Arb.dataConnect.localDate(), 1..100),
      timestamps: Arb<List<Timestamp>> =
        Arb.list(Arb.dataConnect.javaTime.instantTestCase().map { it.timestamp }, 1..100),
    ): Arb<Lists> = arbitrary {
      Lists(
        strings = strings.bind(),
        ints = ints.bind(),
        floats = floats.bind(),
        booleans = booleans.bind(),
        uuids = uuids.bind(),
        int64s = int64s.bind(),
        dates = dates.bind(),
        timestamps = timestamps.bind(),
      )
    }
  }
}
