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
import com.google.firebase.Timestamp
import com.google.firebase.dataconnect.connectors.demo.testutil.DemoConnectorIntegrationTestBase
import com.google.firebase.dataconnect.testutil.MAX_SAFE_INTEGER
import com.google.firebase.dataconnect.testutil.MAX_TIMESTAMP
import com.google.firebase.dataconnect.testutil.MIN_TIMESTAMP
import com.google.firebase.dataconnect.testutil.dateFromYearMonthDayUTC
import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.EdgeCases
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.date
import com.google.firebase.dataconnect.testutil.property.arbitrary.timestamp
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
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ListVariablesAndDataIntegrationTest : DemoConnectorIntegrationTestBase() {

  @Test
  fun insertNonNullableEmptyLists() = runTest {
    val key =
      connector.insertNonNullableLists
        .execute(
          strings = emptyList(),
          ints = emptyList(),
          floats = emptyList(),
          booleans = emptyList(),
          uuids = emptyList(),
          int64s = emptyList(),
          dates = emptyList(),
          timestamps = emptyList(),
        )
        .data
        .key

    val queryResult = connector.getNonNullableListsByKey.execute(key)

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
  fun insertNonNullableNonEmptyLists() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.nonEmptyLists()) { sendLists ->
      val key =
        connector.insertNonNullableLists
          .execute(
            strings = sendLists.strings,
            ints = sendLists.ints,
            floats = sendLists.floats,
            booleans = sendLists.booleans,
            uuids = sendLists.uuids,
            int64s = sendLists.int64s,
            dates = sendLists.dates,
            timestamps = sendLists.timestamps,
          )
          .data
          .key

      val queryResult = connector.getNonNullableListsByKey.execute(key)

      val receiveLists = sendLists.withRoundTripValues()
      assertThat(queryResult.data)
        .isEqualTo(
          GetNonNullableListsByKeyQuery.Data(
            GetNonNullableListsByKeyQuery.Data.NonNullableLists(
              strings = receiveLists.strings,
              ints = receiveLists.ints,
              floats = receiveLists.floats,
              booleans = receiveLists.booleans,
              uuids = receiveLists.uuids,
              int64s = receiveLists.int64s,
              dates = receiveLists.dates,
              timestamps = receiveLists.timestamps,
            )
          )
        )
    }
  }

  @Test
  fun insertNonNullableListsWithExtremeValues() = runTest {
    val sendEdgeCases = Lists.edgeCases

    val key =
      connector.insertNonNullableLists
        .execute(
          strings = sendEdgeCases.strings,
          ints = sendEdgeCases.ints,
          floats = sendEdgeCases.floats,
          booleans = sendEdgeCases.booleans,
          uuids = sendEdgeCases.uuids,
          int64s = sendEdgeCases.int64s,
          dates = sendEdgeCases.dates,
          timestamps = sendEdgeCases.timestamps,
        )
        .data
        .key

    val queryResult = connector.getNonNullableListsByKey.execute(key)

    val receiveEdgeCases = sendEdgeCases.withRoundTripValues()
    queryResult.data shouldBe
      GetNonNullableListsByKeyQuery.Data(
        GetNonNullableListsByKeyQuery.Data.NonNullableLists(
          strings = receiveEdgeCases.strings,
          ints = receiveEdgeCases.ints,
          floats = receiveEdgeCases.floats,
          booleans = receiveEdgeCases.booleans,
          uuids = receiveEdgeCases.uuids,
          int64s = receiveEdgeCases.int64s,
          dates = receiveEdgeCases.dates,
          timestamps = receiveEdgeCases.timestamps,
        )
      )
  }

  @Test
  fun updateNonNullableEmptyListsToNonEmpty() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.nonEmptyLists()) { sendLists ->
      val key =
        connector.insertNonNullableLists
          .execute(
            strings = emptyList(),
            ints = emptyList(),
            floats = emptyList(),
            booleans = emptyList(),
            uuids = emptyList(),
            int64s = emptyList(),
            dates = emptyList(),
            timestamps = emptyList(),
          )
          .data
          .key

      connector.updateNonNullableListsByKey.execute(key) {
        strings = sendLists.strings
        ints = sendLists.ints
        floats = sendLists.floats
        booleans = sendLists.booleans
        uuids = sendLists.uuids
        int64s = sendLists.int64s
        dates = sendLists.dates
        timestamps = sendLists.timestamps
      }

      val queryResult = connector.getNonNullableListsByKey.execute(key)

      val receiveLists = sendLists.withRoundTripValues()
      assertThat(queryResult.data)
        .isEqualTo(
          GetNonNullableListsByKeyQuery.Data(
            GetNonNullableListsByKeyQuery.Data.NonNullableLists(
              strings = receiveLists.strings,
              ints = receiveLists.ints,
              floats = receiveLists.floats,
              booleans = receiveLists.booleans,
              uuids = receiveLists.uuids,
              int64s = receiveLists.int64s,
              dates = receiveLists.dates,
              timestamps = receiveLists.timestamps,
            )
          )
        )
    }
  }

  @Test
  fun updateNonNullableNonEmptyListsToEmpty() = runTest {
    val key =
      connector.insertNonNullableLists
        .execute(
          strings = listOf("a", "b"),
          ints = listOf(1, 2, 3),
          floats = listOf(1.1, 2.2, 3.3),
          booleans = listOf(true, false, true, false),
          uuids =
            listOf(
              UUID.fromString("e7c0b51d-55ec-4c7f-b831-038e6377c4bc"),
              UUID.fromString("6365f797-3d23-482c-9159-bc28b68b8b6e")
            ),
          int64s = listOf(1, 2, 3),
          dates = listOf(dateFromYearMonthDayUTC(2024, 5, 7), dateFromYearMonthDayUTC(1978, 3, 30)),
          timestamps = listOf(Timestamp(123456789, 990000000), Timestamp(987654321, 110000000)),
        )
        .data
        .key

    connector.updateNonNullableListsByKey.execute(key) {
      strings = emptyList()
      ints = emptyList()
      floats = emptyList()
      booleans = emptyList()
      uuids = emptyList()
      int64s = emptyList()
      dates = emptyList()
      timestamps = emptyList()
    }

    val queryResult = connector.getNonNullableListsByKey.execute(key)

    assertThat(queryResult.data)
      .isEqualTo(
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
      )
  }

  @Test
  fun updateNonNullableWithUndefinedLists() = runTest {
    val key =
      connector.insertNonNullableLists
        .execute(
          strings = listOf("a", "b"),
          ints = listOf(1, 2, 3),
          floats = listOf(1.1, 2.2, 3.3),
          booleans = listOf(true, false, true, false),
          uuids =
            listOf(
              UUID.fromString("e60688ca-baae-4f79-8ef1-908220148399"),
              UUID.fromString("e2170f8a-9a53-478c-ae2f-9fb5b09da5c7")
            ),
          int64s = listOf(1, 2, 3),
          dates = listOf(dateFromYearMonthDayUTC(2024, 5, 7), dateFromYearMonthDayUTC(1978, 3, 30)),
          timestamps = listOf(Timestamp(123456789, 990000000), Timestamp(987654321, 110000000)),
        )
        .data
        .key

    connector.updateNonNullableListsByKey.execute(key) {}

    val queryResult = connector.getNonNullableListsByKey.execute(key)

    assertThat(queryResult.data)
      .isEqualTo(
        GetNonNullableListsByKeyQuery.Data(
          GetNonNullableListsByKeyQuery.Data.NonNullableLists(
            strings = listOf("a", "b"),
            ints = listOf(1, 2, 3),
            floats = listOf(1.1, 2.2, 3.3),
            booleans = listOf(true, false, true, false),
            uuids =
              listOf(
                UUID.fromString("e60688ca-baae-4f79-8ef1-908220148399"),
                UUID.fromString("e2170f8a-9a53-478c-ae2f-9fb5b09da5c7")
              ),
            int64s = listOf(1, 2, 3),
            dates =
              listOf(dateFromYearMonthDayUTC(2024, 5, 7), dateFromYearMonthDayUTC(1978, 3, 30)),
            timestamps = listOf(Timestamp(123456789, 990000000), Timestamp(987654321, 110000000)),
          )
        )
      )
  }

  @Test
  fun insertNullableEmptyLists() = runTest {
    val key =
      connector.insertNullableLists
        .execute {
          strings = emptyList()
          ints = emptyList()
          floats = emptyList()
          booleans = emptyList()
          uuids = emptyList()
          int64s = emptyList()
          dates = emptyList()
          timestamps = emptyList()
        }
        .data
        .key

    val queryResult = connector.getNullableListsByKey.execute(key)

    assertThat(queryResult.data)
      .isEqualTo(
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
      )
  }

  @Test
  fun insertNullableUndefinedLists() = runTest {
    val key = connector.insertNullableLists.execute {}.data.key

    val queryResult = connector.getNullableListsByKey.execute(key)

    assertThat(queryResult.data)
      .isEqualTo(
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
      )
  }

  @Test
  fun insertNullableNonEmptyLists() = runTest {
    val key =
      connector.insertNullableLists
        .execute {
          strings = listOf("a", "b")
          ints = listOf(1, 2, 3)
          floats = listOf(1.1, 2.2, 3.3)
          booleans = listOf(true, false, true, false)
          uuids =
            listOf(
              UUID.fromString("643da3eb-91cc-426f-850e-e6e4a0ef2060"),
              UUID.fromString("66acc445-e384-4770-8524-279663e56bb3")
            )
          int64s = listOf(1, 2, 3)
          dates = listOf(dateFromYearMonthDayUTC(2024, 5, 7), dateFromYearMonthDayUTC(1978, 3, 30))
          timestamps = listOf(Timestamp(123456789, 990000000), Timestamp(987654321, 110000000))
        }
        .data
        .key

    val queryResult = connector.getNullableListsByKey.execute(key)

    assertThat(queryResult.data)
      .isEqualTo(
        GetNullableListsByKeyQuery.Data(
          GetNullableListsByKeyQuery.Data.NullableLists(
            strings = listOf("a", "b"),
            ints = listOf(1, 2, 3),
            floats = listOf(1.1, 2.2, 3.3),
            booleans = listOf(true, false, true, false),
            uuids =
              listOf(
                UUID.fromString("643da3eb-91cc-426f-850e-e6e4a0ef2060"),
                UUID.fromString("66acc445-e384-4770-8524-279663e56bb3")
              ),
            int64s = listOf(1, 2, 3),
            dates =
              listOf(dateFromYearMonthDayUTC(2024, 5, 7), dateFromYearMonthDayUTC(1978, 3, 30)),
            timestamps = listOf(Timestamp(123456789, 990000000), Timestamp(987654321, 110000000)),
          )
        )
      )
  }

  @Test
  fun insertNullableListsWithExtremeValues() = runTest {
    val key =
      connector.insertNullableLists
        .execute {
          strings = listOf("")
          ints = listOf(0, 1, -1, Int.MAX_VALUE, Int.MIN_VALUE)
          // TODO(b/339440054) add -0.0 to the list once the bug is fixed
          floats = listOf(0.0, 1.0, -1.0, Double.MAX_VALUE, Double.MIN_VALUE, MAX_SAFE_INTEGER)
          booleans = emptyList() // Boolean have no "extreme" values
          uuids = emptyList() // UUID have no "extreme" values
          int64s =
            listOf(
              0,
              1,
              -1,
              Int.MAX_VALUE.toLong(),
              Int.MIN_VALUE.toLong(),
              Long.MAX_VALUE,
              Long.MIN_VALUE
            )
          dates = EdgeCases.dates.all.map { it.date }
          timestamps = listOf(MIN_TIMESTAMP, MAX_TIMESTAMP)
        }
        .data
        .key

    val queryResult = connector.getNullableListsByKey.execute(key)

    assertThat(queryResult.data)
      .isEqualTo(
        GetNullableListsByKeyQuery.Data(
          GetNullableListsByKeyQuery.Data.NullableLists(
            strings = listOf(""),
            ints = listOf(0, 1, -1, Int.MAX_VALUE, Int.MIN_VALUE),
            // TODO(b/339440054) add -0.0 to the list once the bug is fixed
            floats = listOf(0.0, 1.0, -1.0, Double.MAX_VALUE, Double.MIN_VALUE, MAX_SAFE_INTEGER),
            booleans = emptyList(), // Boolean have no "extreme" values
            uuids = emptyList(), // UUID have no "extreme" values
            int64s =
              listOf(
                0,
                1,
                -1,
                Int.MAX_VALUE.toLong(),
                Int.MIN_VALUE.toLong(),
                Long.MAX_VALUE,
                Long.MIN_VALUE
              ),
            dates = EdgeCases.dates.all.map { it.date },
            timestamps =
              listOf(
                MIN_TIMESTAMP.withMicrosecondPrecision(),
                MAX_TIMESTAMP.withMicrosecondPrecision()
              ),
          )
        )
      )
  }

  @Test
  fun updateNullableEmptyListsToNonEmpty() = runTest {
    val key =
      connector.insertNullableLists
        .execute {
          strings = emptyList()
          ints = emptyList()
          floats = emptyList()
          booleans = emptyList()
          uuids = emptyList()
          int64s = emptyList()
          dates = emptyList()
          timestamps = emptyList()
        }
        .data
        .key

    connector.updateNullableListsByKey.execute(key) {
      strings = listOf("a", "b")
      ints = listOf(1, 2, 3)
      floats = listOf(1.1, 2.2, 3.3)
      booleans = listOf(true, false, true, false)
      uuids =
        listOf(
          UUID.fromString("046b46f4-8a57-4611-ac1a-b2213278acad"),
          UUID.fromString("80fa16ff-51ce-480a-b117-97a2d37d19f1")
        )
      int64s = listOf(1, 2, 3)
      dates = listOf(dateFromYearMonthDayUTC(2024, 5, 7), dateFromYearMonthDayUTC(1978, 3, 30))
      timestamps = listOf(Timestamp(123456789, 990000000), Timestamp(987654321, 110000000))
    }

    val queryResult = connector.getNullableListsByKey.execute(key)

    assertThat(queryResult.data)
      .isEqualTo(
        GetNullableListsByKeyQuery.Data(
          GetNullableListsByKeyQuery.Data.NullableLists(
            strings = listOf("a", "b"),
            ints = listOf(1, 2, 3),
            floats = listOf(1.1, 2.2, 3.3),
            booleans = listOf(true, false, true, false),
            uuids =
              listOf(
                UUID.fromString("046b46f4-8a57-4611-ac1a-b2213278acad"),
                UUID.fromString("80fa16ff-51ce-480a-b117-97a2d37d19f1")
              ),
            int64s = listOf(1, 2, 3),
            dates =
              listOf(dateFromYearMonthDayUTC(2024, 5, 7), dateFromYearMonthDayUTC(1978, 3, 30)),
            timestamps = listOf(Timestamp(123456789, 990000000), Timestamp(987654321, 110000000)),
          )
        )
      )
  }

  @Test
  fun updateNullableNonEmptyListsToEmpty() = runTest {
    val key =
      connector.insertNullableLists
        .execute {
          strings = listOf("a", "b")
          ints = listOf(1, 2, 3)
          floats = listOf(1.1, 2.2, 3.3)
          booleans = listOf(true, false, true, false)
          uuids =
            listOf(
              UUID.fromString("a62c5afa-ded1-401c-aac4-a8e8d786a16f"),
              UUID.fromString("1dbf3cd7-ed04-4edd-9b77-65465f9fbaef")
            )
          int64s = listOf(1, 2, 3)
          dates = listOf(dateFromYearMonthDayUTC(2024, 5, 7), dateFromYearMonthDayUTC(1978, 3, 30))
          timestamps = listOf(Timestamp(123456789, 990000000), Timestamp(987654321, 110000000))
        }
        .data
        .key

    connector.updateNullableListsByKey.execute(key) {
      strings = emptyList()
      ints = emptyList()
      floats = emptyList()
      booleans = emptyList()
      uuids = emptyList()
      int64s = emptyList()
      dates = emptyList()
      timestamps = emptyList()
    }

    val queryResult = connector.getNullableListsByKey.execute(key)

    assertThat(queryResult.data)
      .isEqualTo(
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
      )
  }

  @Test
  fun updateNullableNonEmptyListsToNull() = runTest {
    val key =
      connector.insertNullableLists
        .execute {
          strings = listOf("a", "b")
          ints = listOf(1, 2, 3)
          floats = listOf(1.1, 2.2, 3.3)
          booleans = listOf(true, false, true, false)
          uuids =
            listOf(
              UUID.fromString("a62c5afa-ded1-401c-aac4-a8e8d786a16f"),
              UUID.fromString("1dbf3cd7-ed04-4edd-9b77-65465f9fbaef")
            )
          int64s = listOf(1, 2, 3)
          dates = listOf(dateFromYearMonthDayUTC(2024, 5, 7), dateFromYearMonthDayUTC(1978, 3, 30))
          timestamps = listOf(Timestamp(123456789, 990000000), Timestamp(987654321, 110000000))
        }
        .data
        .key

    connector.updateNullableListsByKey.execute(key) {
      strings = null
      ints = null
      floats = null
      booleans = null
      uuids = null
      int64s = null
      dates = null
      timestamps = null
    }

    val queryResult = connector.getNullableListsByKey.execute(key)

    assertThat(queryResult.data)
      .isEqualTo(
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
      )
  }

  @Test
  fun updateNullableWithUndefinedLists() = runTest {
    val key =
      connector.insertNullableLists
        .execute {
          strings = listOf("a", "b")
          ints = listOf(1, 2, 3)
          floats = listOf(1.1, 2.2, 3.3)
          booleans = listOf(true, false, true, false)
          uuids =
            listOf(
              UUID.fromString("505516e2-1af3-4a7a-afab-0fe4b8f2bc0d"),
              UUID.fromString("f0afdbfc-10a1-4446-8823-3bfc81ff3162")
            )
          int64s = listOf(1, 2, 3)
          dates = listOf(dateFromYearMonthDayUTC(2024, 5, 7), dateFromYearMonthDayUTC(1978, 3, 30))
          timestamps = listOf(Timestamp(123456789, 990000000), Timestamp(987654321, 110000000))
        }
        .data
        .key

    connector.updateNullableListsByKey.execute(key) {}

    val queryResult = connector.getNullableListsByKey.execute(key)

    assertThat(queryResult.data)
      .isEqualTo(
        GetNullableListsByKeyQuery.Data(
          GetNullableListsByKeyQuery.Data.NullableLists(
            strings = listOf("a", "b"),
            ints = listOf(1, 2, 3),
            floats = listOf(1.1, 2.2, 3.3),
            booleans = listOf(true, false, true, false),
            uuids =
              listOf(
                UUID.fromString("505516e2-1af3-4a7a-afab-0fe4b8f2bc0d"),
                UUID.fromString("f0afdbfc-10a1-4446-8823-3bfc81ff3162")
              ),
            int64s = listOf(1, 2, 3),
            dates =
              listOf(dateFromYearMonthDayUTC(2024, 5, 7), dateFromYearMonthDayUTC(1978, 3, 30)),
            timestamps = listOf(Timestamp(123456789, 990000000), Timestamp(987654321, 110000000)),
          )
        )
      )
  }

  private data class Lists(
    val strings: List<String>,
    val ints: List<Int>,
    val floats: List<Double>,
    val booleans: List<Boolean>,
    val uuids: List<UUID>,
    val int64s: List<Long>,
    val dates: List<Date>,
    val timestamps: List<Timestamp>,
  ) {

    fun withRoundTripValues(): Lists =
      copy(
        // -0.0 gets coerced to 0.0 due to lack of jsob support for -0.0 (see b/339440054).
        floats = floats.map { if (it != -0.0) it else 0.0 },
        timestamps = timestamps.map { it.withMicrosecondPrecision() },
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
            dates = EdgeCases.dates.all.map { it.date },
            timestamps = EdgeCases.timestamps.all.map { it.timestamp },
          )
    }
  }

  private companion object {
    @OptIn(ExperimentalKotest::class)
    val propTestConfig =
      PropTestConfig(iterations = 5, edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.5))

    @Suppress("UnusedReceiverParameter")
    fun DataConnectArb.nonEmptyLists(
      strings: Arb<List<String>> = Arb.list(Arb.dataConnect.string(), 1..100),
      ints: Arb<List<Int>> = Arb.list(Arb.int(), 1..100),
      floats: Arb<List<Double>> = Arb.list(Arb.dataConnect.float(), 1..100),
      booleans: Arb<List<Boolean>> = Arb.list(Arb.boolean(), 1..100),
      uuids: Arb<List<UUID>> = Arb.list(Arb.uuid(), 1..100),
      int64s: Arb<List<Long>> = Arb.list(Arb.long(), 1..100),
      dates: Arb<List<Date>> = Arb.list(Arb.dataConnect.date().map { it.date }, 1..100),
      timestamps: Arb<List<Timestamp>> =
        Arb.list(Arb.dataConnect.timestamp().map { it.timestamp }, 1..100),
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
