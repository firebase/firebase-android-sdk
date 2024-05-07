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
import com.google.firebase.dataconnect.*
import com.google.firebase.dataconnect.connectors.demo.testutil.*
import com.google.firebase.dataconnect.testutil.*
import java.text.SimpleDateFormat
import java.util.UUID
import kotlinx.coroutines.test.*
import org.junit.Test

class ListVariablesAndDataIntegrationTest : DemoConnectorIntegrationTestBase() {

  @Test
  fun nonNullableEmptyLists() = runTest {
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
  fun nonNullableNonEmptyLists() = runTest {
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
          dates = listOf(dateFromYYYYMMDD("2024-05-07"), dateFromYYYYMMDD("1978-03-30")),
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
            dates = listOf(dateFromYYYYMMDD("2024-05-07"), dateFromYYYYMMDD("1978-03-30")),
            timestamps = listOf(Timestamp(123456789, 990000000), Timestamp(987654321, 110000000)),
          )
        )
      )
  }

  @Test
  fun nonNullableListsWithExtremeValues() = runTest {
    val dateFormatter = SimpleDateFormat("yyyy-MM-dd")

    val key =
      connector.insertNonNullableLists
        .execute(
          strings = listOf(""),
          ints = listOf(0, 1, -1, Int.MAX_VALUE, Int.MIN_VALUE),
          floats =
            listOf(0.0, -0.0, 1.0, -1.0, Double.MAX_VALUE, Double.MIN_VALUE, MAX_SAFE_INTEGER),
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
          timestamps = listOf(Timestamp.MIN_VALUE, Timestamp.MAX_VALUE),
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
            floats =
              listOf(0.0, -0.0, 1.0, -1.0, Double.MAX_VALUE, Double.MIN_VALUE, MAX_SAFE_INTEGER),
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
            timestamps = listOf(Timestamp.MIN_VALUE, Timestamp.MAX_VALUE),
          )
        )
      )
  }
}
