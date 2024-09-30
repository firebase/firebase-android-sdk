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
import com.google.firebase.dataconnect.DataConnectException
import com.google.firebase.dataconnect.connectors.demo.testutil.DemoConnectorIntegrationTestBase
import com.google.firebase.dataconnect.generated.GeneratedMutation
import com.google.firebase.dataconnect.generated.GeneratedQuery
import com.google.firebase.dataconnect.testutil.MAX_DATE
import com.google.firebase.dataconnect.testutil.MIN_DATE
import com.google.firebase.dataconnect.testutil.ZERO_DATE
import com.google.firebase.dataconnect.testutil.assertThrows
import com.google.firebase.dataconnect.testutil.dateFromYearMonthDayUTC
import com.google.firebase.dataconnect.testutil.executeWithEmptyVariables
import com.google.firebase.dataconnect.testutil.randomDate
import com.google.firebase.dataconnect.testutil.withDataDeserializer
import com.google.firebase.dataconnect.testutil.withVariablesSerializer
import java.util.Date
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.junit.Test

class DateScalarIntegrationTest : DemoConnectorIntegrationTestBase() {

  @Test
  fun insertTypicalValueForNonNullField() = runTest {
    val date = dateFromYearMonthDayUTC(1944, 1, 1)
    val key = connector.insertNonNullDate.execute(date).data.key
    assertNonNullDateByKeyEquals(key, "1944-01-01")
  }

  @Test
  fun insertMaxValueForNonNullDateField() = runTest {
    val key = connector.insertNonNullDate.execute(MIN_DATE).data.key
    assertNonNullDateByKeyEquals(key, "1583-01-01")
  }

  @Test
  fun insertMinValueForNonNullDateField() = runTest {
    val key = connector.insertNonNullDate.execute(MAX_DATE).data.key
    assertNonNullDateByKeyEquals(key, "9999-12-31")
  }

  @Test
  fun insertValueWithTimeForNonNullDateField() = runTest {
    // Use a date that, when converted to UTC, in on a different date to verify that the server does
    // the expected thing; that is, that it _drops_ the time zone information (rather than
    // converting the date to UTC then taking the YYYY-MM-DD of that). The server would use the date
    // "2024-03-27" if it did the erroneous conversion to UTC before taking the YYYY-MM-DD.
    val date = "2024-03-26T19:48:00.144-07:00"
    val key = connector.insertNonNullDate.executeWithStringVariables(date).data.key
    assertNonNullDateByKeyEquals(key, dateFromYearMonthDayUTC(2024, 3, 26))
  }

  @Test
  fun insertDateNotOnExactDateBoundaryForNonNullDateField() = runTest {
    val dateOnDateBoundary = dateFromYearMonthDayUTC(2000, 9, 14)
    val dateOffDateBoundary = Date(dateOnDateBoundary.time + 7200)

    val key = connector.insertNonNullDate.execute(dateOffDateBoundary).data.key
    assertNonNullDateByKeyEquals(key, dateOnDateBoundary)
  }

  @Test
  fun insertNoVariablesForNonNullDateFieldsWithSchemaDefaults() = runTest {
    val key = connector.insertNonNullDatesWithDefaults.execute {}.data.key
    val queryResult = connector.getNonNullDatesWithDefaultsByKey.execute(key)

    // Since we can't know the exact value of `request.time` just make sure that the exact same
    // value is used for both fields to which it is set.
    val expectedRequestTime = queryResult.data.nonNullDatesWithDefaults!!.requestTime1

    assertThat(
      queryResult.equals(
        GetNonNullDatesWithDefaultsByKeyQuery.Data(
          GetNonNullDatesWithDefaultsByKeyQuery.Data.NonNullDatesWithDefaults(
            valueWithVariableDefault = dateFromYearMonthDayUTC(6904, 11, 30),
            valueWithSchemaDefault = dateFromYearMonthDayUTC(2112, 1, 31),
            epoch = ZERO_DATE,
            requestTime1 = expectedRequestTime,
            requestTime2 = expectedRequestTime,
          )
        )
      )
    )
  }

  @Test
  fun insertNullForNonNullDateFieldShouldFail() = runTest {
    assertThrows(DataConnectException::class) {
      connector.insertNonNullDate.executeWithStringVariables(null).data.key
    }
  }

  @Test
  fun insertIntForNonNullDateFieldShouldFail() = runTest {
    assertThrows(DataConnectException::class) {
      connector.insertNonNullDate.executeWithIntVariables(999_888).data.key
    }
  }

  @Test
  fun insertWithMissingValueNonNullDateFieldShouldFail() = runTest {
    assertThrows(DataConnectException::class) {
      connector.insertNonNullDate.executeWithEmptyVariables().data.key
    }
  }

  @Test
  fun insertInvalidDatesValuesForNonNullDateFieldShouldFail() = runTest {
    for (invalidDate in invalidDates) {
      assertThrows(DataConnectException::class) {
        connector.insertNonNullDate.executeWithStringVariables(invalidDate).data.key
      }
    }
  }

  @Test
  fun updateNonNullDateFieldToAnotherValidValue() = runTest {
    val date1 = randomDate()
    val date2 = dateFromYearMonthDayUTC(5654, 12, 1)
    val key = connector.insertNonNullDate.execute(date1).data.key
    connector.updateNonNullDate.execute(key) { value = date2 }
    assertNonNullDateByKeyEquals(key, "5654-12-01")
  }

  @Test
  fun updateNonNullDateFieldToMinValue() = runTest {
    val date = randomDate()
    val key = connector.insertNonNullDate.execute(date).data.key
    connector.updateNonNullDate.execute(key) { value = MIN_DATE }
    assertNonNullDateByKeyEquals(key, "1583-01-01")
  }

  @Test
  fun updateNonNullDateFieldToMaxValue() = runTest {
    val date = randomDate()
    val key = connector.insertNonNullDate.execute(date).data.key
    connector.updateNonNullDate.execute(key) { value = MAX_DATE }
    assertNonNullDateByKeyEquals(key, "9999-12-31")
  }

  @Test
  fun updateNonNullDateFieldToAnUndefinedValue() = runTest {
    val date = randomDate()
    val key = connector.insertNonNullDate.execute(date).data.key
    connector.updateNonNullDate.execute(key) {}
    assertNonNullDateByKeyEquals(key, date)
  }

  @Test
  fun insertTypicalValueForNullableField() = runTest {
    val date = dateFromYearMonthDayUTC(7611, 12, 1)
    val key = connector.insertNullableDate.execute { value = date }.data.key
    assertNullableDateByKeyEquals(key, "7611-12-01")
  }

  @Test
  fun insertMaxValueForNullableDateField() = runTest {
    val key = connector.insertNullableDate.execute { value = MIN_DATE }.data.key
    assertNullableDateByKeyEquals(key, "1583-01-01")
  }

  @Test
  fun insertMinValueForNullableDateField() = runTest {
    val key = connector.insertNullableDate.execute { value = MAX_DATE }.data.key
    assertNullableDateByKeyEquals(key, "9999-12-31")
  }

  @Test
  fun insertNullForNullableDateField() = runTest {
    val key = connector.insertNullableDate.execute { value = null }.data.key
    assertNullableDateByKeyEquals(key, null)
  }

  @Test
  fun insertUndefinedForNullableDateField() = runTest {
    val key = connector.insertNullableDate.execute {}.data.key
    assertNullableDateByKeyEquals(key, null)
  }

  @Test
  fun insertValueWithTimeForNullableDateField() = runTest {
    // Use a date that, when converted to UTC, in on a different date to verify that the server does
    // the expected thing; that is, that it _drops_ the time zone information (rather than
    // converting the date to UTC then taking the YYYY-MM-DD of that). The server would use the date
    // "2024-03-27" if it did the erroneous conversion to UTC before taking the YYYY-MM-DD.
    val date = "2024-03-26T19:48:00.144-07:00"
    val key = connector.insertNullableDate.executeWithStringVariables(date).data.key
    assertNullableDateByKeyEquals(key, dateFromYearMonthDayUTC(2024, 3, 26))
  }

  @Test
  fun insertDateNotOnExactDateBoundaryForNullableDateField() = runTest {
    val dateOnDateBoundary = dateFromYearMonthDayUTC(1812, 12, 22)
    val dateOffDateBoundary = Date(dateOnDateBoundary.time + 7200)

    val key = connector.insertNullableDate.execute { value = dateOffDateBoundary }.data.key
    assertNullableDateByKeyEquals(key, dateOnDateBoundary)
  }

  @Test
  fun insertIntForNullableDateFieldShouldFail() = runTest {
    assertThrows(DataConnectException::class) {
      connector.insertNullableDate.executeWithIntVariables(999_888).data.key
    }
  }

  @Test
  fun insertInvalidDatesValuesForNullableDateFieldShouldFail() = runTest {
    for (invalidDate in invalidDates) {
      assertThrows(DataConnectException::class) {
        connector.insertNullableDate.executeWithStringVariables(invalidDate).data.key
      }
    }
  }

  @Test
  fun insertNoVariablesForNullableDateFieldsWithSchemaDefaults() = runTest {
    val key = connector.insertNullableDatesWithDefaults.execute {}.data.key
    val queryResult = connector.getNullableDatesWithDefaultsByKey.execute(key)

    // Since we can't know the exact value of `request.time` just make sure that the exact same
    // value is used for both fields to which it is set.
    val expectedRequestTime = queryResult.data.nullableDatesWithDefaults!!.requestTime1

    assertThat(
      queryResult.equals(
        GetNullableDatesWithDefaultsByKeyQuery.Data(
          GetNullableDatesWithDefaultsByKeyQuery.Data.NullableDatesWithDefaults(
            valueWithVariableDefault = dateFromYearMonthDayUTC(8113, 2, 9),
            valueWithSchemaDefault = dateFromYearMonthDayUTC(1921, 12, 2),
            epoch = ZERO_DATE,
            requestTime1 = expectedRequestTime,
            requestTime2 = expectedRequestTime,
          )
        )
      )
    )
  }

  @Test
  fun updateNullableDateFieldToAnotherValidValue() = runTest {
    val date1 = randomDate()
    val date2 = dateFromYearMonthDayUTC(5654, 12, 1)
    val key = connector.insertNullableDate.execute { value = date1 }.data.key
    connector.updateNullableDate.execute(key) { value = date2 }
    assertNullableDateByKeyEquals(key, "5654-12-01")
  }

  @Test
  fun updateNullableDateFieldToMinValue() = runTest {
    val date = randomDate()
    val key = connector.insertNullableDate.execute { value = date }.data.key
    connector.updateNullableDate.execute(key) { value = MIN_DATE }
    assertNullableDateByKeyEquals(key, "1583-01-01")
  }

  @Test
  fun updateNullableDateFieldToMaxValue() = runTest {
    val date = randomDate()
    val key = connector.insertNullableDate.execute { value = date }.data.key
    connector.updateNullableDate.execute(key) { value = MAX_DATE }
    assertNullableDateByKeyEquals(key, "9999-12-31")
  }

  @Test
  fun updateNullableDateFieldToNull() = runTest {
    val date = randomDate()
    val key = connector.insertNullableDate.execute { value = date }.data.key
    connector.updateNullableDate.execute(key) { value = null }
    assertNullableDateByKeyEquals(key, null)
  }

  @Test
  fun updateNullableDateFieldToNonNull() = runTest {
    val date = randomDate()
    val key = connector.insertNullableDate.execute { value = null }.data.key
    connector.updateNullableDate.execute(key) { value = date }
    assertNullableDateByKeyEquals(key, date)
  }

  @Test
  fun updateNullableDateFieldToAnUndefinedValue() = runTest {
    val date = randomDate()
    val key = connector.insertNullableDate.execute { value = date }.data.key
    connector.updateNullableDate.execute(key) {}
    assertNullableDateByKeyEquals(key, date)
  }

  private suspend fun assertNonNullDateByKeyEquals(key: NonNullDateKey, expected: String) {
    val queryResult =
      connector.getNonNullDateByKey
        .withDataDeserializer(serializer<GetDateByKeyQueryStringData>())
        .execute(key)
    assertThat(queryResult.data).isEqualTo(GetDateByKeyQueryStringData(expected))
  }

  private suspend fun assertNonNullDateByKeyEquals(key: NonNullDateKey, expected: Date) {
    val queryResult = connector.getNonNullDateByKey.execute(key)
    assertThat(queryResult.data)
      .isEqualTo(GetNonNullDateByKeyQuery.Data(GetNonNullDateByKeyQuery.Data.Value(expected)))
  }

  private suspend fun assertNullableDateByKeyEquals(key: NullableDateKey, expected: String) {
    val queryResult =
      connector.getNullableDateByKey
        .withDataDeserializer(serializer<GetDateByKeyQueryStringData>())
        .execute(key)
    assertThat(queryResult.data).isEqualTo(GetDateByKeyQueryStringData(expected))
  }

  private suspend fun assertNullableDateByKeyEquals(key: NullableDateKey, expected: Date?) {
    val queryResult = connector.getNullableDateByKey.execute(key)
    assertThat(queryResult.data)
      .isEqualTo(GetNullableDateByKeyQuery.Data(GetNullableDateByKeyQuery.Data.Value(expected)))
  }

  /**
   * A `Data` type that can be used in place of [GetNonNullDateByKeyQuery.Data] that types the value
   * as a [String] instead of a [Date], allowing verification of the data sent over the wire without
   * possible confounding from date deserialization.
   */
  @Serializable
  private data class GetDateByKeyQueryStringData(val value: DateStringValue?) {
    constructor(value: String) : this(DateStringValue(value))
    @Serializable data class DateStringValue(val value: String)
  }

  /**
   * A `Variables` type that can be used in place of [InsertNonNullDateMutation.Variables] that
   * types the value as a [String] instead of a [Date], allowing verification of the data sent over
   * the wire without possible confounding from date serialization.
   */
  @Serializable private data class InsertDateStringVariables(val value: String?)

  /**
   * A `Variables` type that can be used in place of [InsertNonNullDateMutation.Variables] that
   * types the value as a [Int] instead of a [Date], allowing verification that the server fails
   * with an expected error (rather than crashing, for example).
   */
  @Serializable private data class InsertDateIntVariables(val value: Int)

  private companion object {

    suspend fun <Data> GeneratedMutation<*, Data, *>.executeWithStringVariables(value: String?) =
      withVariablesSerializer(serializer<InsertDateStringVariables>())
        .ref(InsertDateStringVariables(value))
        .execute()

    suspend fun <Data> GeneratedMutation<*, Data, *>.executeWithIntVariables(value: Int) =
      withVariablesSerializer(serializer<InsertDateIntVariables>())
        .ref(InsertDateIntVariables(value))
        .execute()

    suspend fun <Data> GeneratedQuery<*, Data, GetNonNullDateByKeyQuery.Variables>.execute(
      key: NonNullDateKey
    ) = ref(GetNonNullDateByKeyQuery.Variables(key)).execute()

    suspend fun <Data> GeneratedQuery<*, Data, GetNullableDateByKeyQuery.Variables>.execute(
      key: NullableDateKey
    ) = ref(GetNullableDateByKeyQuery.Variables(key)).execute()

    val invalidDates =
      listOf(
        // Partial dates
        "2",
        "20",
        "202",
        "2024",
        "2024-",
        "2024-0",
        "2024-01",
        "2024-01-",
        "2024-01-0",
        "2024-01-04T",

        // Missing components
        "",
        "2024-",
        "-05-17",
        "2024-05",
        "2024--17",
        "-05-",

        // Invalid year
        "2-05-17",
        "20-05-17",
        "202-05-17",
        "20245-05-17",
        "02024-05-17",
        "ABCD-05-17",
        "-123-05-17",

        // Invalid month
        "2024-1-17",
        "2024-012-17",
        "2024-123-17",
        "2024-00-17",
        "2024-13-17",
        "2024-M-17",
        "2024-MA-17",

        // Invalid day
        "2024-05-1",
        "2024-05-123",
        "2024-05-012",
        "2024-05-00",
        "2024-05-32",
        "2024-05-A",
        "2024-05-AB",
        "2024-05-ABC",

        // Out-of-range Values
        "0000-01-01",
        "2024-00-22",
        "2024-13-22",
        "2024-11-00",
        "2024-01-32",
        "2025-02-29",
        "2024-02-30",
        "2024-03-32",
        "2024-04-31",
        "2024-05-32",
        "2024-06-31",
        "2024-07-32",
        "2024-08-32",
        "2024-09-31",
        "2024-10-32",
        "2024-11-31",
        "2024-12-32",
      )
  }
}
