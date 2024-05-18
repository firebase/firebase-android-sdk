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
import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.connectors.demo.testutil.DemoConnectorIntegrationTestBase
import com.google.firebase.dataconnect.testutil.MAX_DATE
import com.google.firebase.dataconnect.testutil.MIN_DATE
import com.google.firebase.dataconnect.testutil.dateFromYearMonthDayUTC
import com.google.firebase.dataconnect.testutil.withDataDeserializer
import java.util.Date
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.junit.Test

class DateScalarIntegrationTest : DemoConnectorIntegrationTestBase() {

  @Test
  fun insertNonNullDateVariableForNonNullFieldTypicalValue() = runTest {
    val date = dateFromYearMonthDayUTC(1944, 1, 1)
    val key = connector.insertNonNullDate.execute(date).data.key
    assertNonNullDateByKeyEquals(key, "1944-01-01")
  }

  @Test
  fun insertNonNullDateVariableForNonNullFieldMinValue() = runTest {
    val key = connector.insertNonNullDate.execute(MIN_DATE).data.key
    assertNonNullDateByKeyEquals(key, "1583-01-01")
  }

  @Test
  fun insertNonNullDateVariableForNonNullFieldMaxValue() = runTest {
    val key = connector.insertNonNullDate.execute(MAX_DATE).data.key
    assertNonNullDateByKeyEquals(key, "9999-12-31")
  }

  @Test
  fun updateNonNullDateField() = runTest {
    val date1 = dateFromYearMonthDayUTC(1877, 12, 13)
    val date2 = dateFromYearMonthDayUTC(1944, 5, 1)
    val key = connector.insertNonNullDate.execute(date1).data.key
    connector.updateNonNullDate.execute(key) { value = date2 }
    assertNonNullDateByKeyEquals(key, "1944-05-01")
  }

  private suspend fun assertNonNullDateByKeyEquals(key: NonNullDateKey, expected: String) {
    val queryResult = connector.getNonNullDateByKey.refWithStringData(key).execute()
    assertThat(queryResult.data).isEqualTo(GetDateByKeyQueryStringData(expected))
  }

  /**
   * A `Data` type that can be used in place of [GetNonNullDateByKeyQuery.Data] that types the value
   * as a [String] instead of a [Date], allowing verification of the data sent over the wire without
   * possible confounding from date deserialization.
   */
  @Serializable
  data class GetDateByKeyQueryStringData(val value: DateStringValue?) {
    constructor(value: String) : this(DateStringValue(value))
    @Serializable data class DateStringValue(val value: String)
  }

  private companion object {
    /**
     * Returns a [QueryRef] that uses [GetDateByKeyQueryStringData] instead of
     * [GetNonNullDateByKeyQuery.Data].
     */
    fun GetNonNullDateByKeyQuery.refWithStringData(
      key: NonNullDateKey
    ): QueryRef<GetDateByKeyQueryStringData, GetNonNullDateByKeyQuery.Variables> =
      ref(GetNonNullDateByKeyQuery.Variables(key = key))
        .withDataDeserializer(serializer<GetDateByKeyQueryStringData>())
  }
}
