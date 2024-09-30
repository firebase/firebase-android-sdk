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
import com.google.firebase.dataconnect.testutil.MAX_DATE
import com.google.firebase.dataconnect.testutil.MAX_SAFE_INTEGER
import com.google.firebase.dataconnect.testutil.MAX_TIMESTAMP
import com.google.firebase.dataconnect.testutil.MIN_DATE
import com.google.firebase.dataconnect.testutil.MIN_TIMESTAMP
import com.google.firebase.dataconnect.testutil.dateFromYearMonthDayUTC
import com.google.firebase.dataconnect.testutil.withMicrosecondPrecision
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Ignore
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
  fun insertNonNullableNonEmptyLists() = runTest {
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
                UUID.fromString("e7c0b51d-55ec-4c7f-b831-038e6377c4bc"),
                UUID.fromString("6365f797-3d23-482c-9159-bc28b68b8b6e")
              ),
            int64s = listOf(1, 2, 3),
            dates =
              listOf(dateFromYearMonthDayUTC(2024, 5, 7), dateFromYearMonthDayUTC(1978, 3, 30)),
            timestamps = listOf(Timestamp(123456789, 990000000), Timestamp(987654321, 110000000)),
          )
        )
      )
  }

  @Ignore(
    "b/339440054 Fix this test once -0.0 is correctly sent from the backend " +
      "instead of being converted to 0.0"
  )
  @Test
  fun floatCorrectlySerializesNegativeZero() {
    TODO(
      "this test is merely a placeholder as a reminder " +
        "and should be removed once the test is updated"
    )
  }

  @Test
  fun insertNonNullableListsWithExtremeValues() = runTest {
    val key =
      connector.insertNonNullableLists
        .execute(
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
          dates = listOf(MIN_DATE, MAX_DATE),
          timestamps = listOf(MIN_TIMESTAMP, MAX_TIMESTAMP),
        )
        .data
        .key

    val queryResult = connector.getNonNullableListsByKey.execute(key)

    assertThat(queryResult.data)
      .isEqualTo(
        GetNonNullableListsByKeyQuery.Data(
          GetNonNullableListsByKeyQuery.Data.NonNullableLists(
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
            dates = listOf(MIN_DATE, MAX_DATE),
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
  fun updateNonNullableEmptyListsToNonEmpty() = runTest {
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
      strings = listOf("a", "b")
      ints = listOf(1, 2, 3)
      floats = listOf(1.1, 2.2, 3.3)
      booleans = listOf(true, false, true, false)
      uuids =
        listOf(
          UUID.fromString("317835d2-efae-4981-b70f-64ff31126921"),
          UUID.fromString("91597f71-8f85-4ae5-ac4d-909287c8c52c")
        )
      int64s = listOf(1, 2, 3)
      dates = listOf(dateFromYearMonthDayUTC(2024, 5, 7), dateFromYearMonthDayUTC(1978, 3, 30))
      timestamps = listOf(Timestamp(123456789, 990000000), Timestamp(987654321, 110000000))
    }

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
                UUID.fromString("317835d2-efae-4981-b70f-64ff31126921"),
                UUID.fromString("91597f71-8f85-4ae5-ac4d-909287c8c52c")
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
          dates = listOf(MIN_DATE, MAX_DATE)
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
            dates = listOf(MIN_DATE, MAX_DATE),
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
}
