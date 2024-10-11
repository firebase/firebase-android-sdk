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

import com.google.firebase.dataconnect.DataConnectException
import com.google.firebase.dataconnect.connectors.demo.testutil.DemoConnectorIntegrationTestBase
import com.google.firebase.dataconnect.generated.GeneratedMutation
import com.google.firebase.dataconnect.generated.GeneratedQuery
import com.google.firebase.dataconnect.testutil.dateFromYearMonthDayUTC
import com.google.firebase.dataconnect.testutil.executeWithEmptyVariables
import com.google.firebase.dataconnect.testutil.property.arbitrary.EdgeCases
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.date
import com.google.firebase.dataconnect.testutil.property.arbitrary.dateOffDayBoundary
import com.google.firebase.dataconnect.testutil.withDataDeserializer
import com.google.firebase.dataconnect.testutil.withVariablesSerializer
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.next
import io.kotest.property.checkAll
import java.util.Date
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.junit.Test

class DateScalarIntegrationTest : DemoConnectorIntegrationTestBase() {

  @Test
  fun nonNullDate_insert_NormalCases() = runTest {
    checkAll(20, Arb.dataConnect.date()) {
      val key = connector.insertNonNullDate.execute(it.date).data.key
      assertNonNullDateByKeyEquals(key, it.string)
    }
  }

  @Test
  fun nonNullDate_insert_EdgeCases() = runTest {
    assertSoftly {
      EdgeCases.dates.all.forEach {
        val key = connector.insertNonNullDate.execute(it.date).data.key
        assertNonNullDateByKeyEquals(key, it.string)
      }
    }
  }

  @Test
  fun nonNullDate_insert_ShouldIgnoreTimeZone() = runTest {
    // Use a date that, when converted to UTC, in on a different date to verify that the server does
    // the expected thing; that is, that it _drops_ the time zone information (rather than
    // converting the date to UTC then taking the YYYY-MM-DD of that). The server would use the date
    // "2024-03-27" if it did the erroneous conversion to UTC before taking the YYYY-MM-DD.
    val date = "2024-03-26T19:48:00.144-07:00"
    val key = connector.insertNonNullDate.executeWithStringVariables(date).data.key
    assertNonNullDateByKeyEquals(key, dateFromYearMonthDayUTC(2024, 3, 26))
  }

  @Test
  fun nonNullDate_insert_ShouldIgnoreTime() = runTest {
    checkAll(20, Arb.dataConnect.dateOffDayBoundary()) {
      val key = connector.insertNonNullDate.execute(it.date).data.key
      assertNonNullDateByKeyEquals(key, it.string)
    }
  }

  @Test
  fun nonNullDatesWithDefaults_insert_ShouldUseDefaultValuesIfNoVariablesSpecified() = runTest {
    val key = connector.insertNonNullDatesWithDefaults.execute {}.data.key
    val queryResult = connector.getNonNullDatesWithDefaultsByKey.execute(key)

    // Since we can't know the exact value of `request.time` just make sure that the exact same
    // value is used for both fields to which it is set.
    val expectedRequestTime = queryResult.data.nonNullDatesWithDefaults!!.requestTime1

    queryResult.data shouldBe
      GetNonNullDatesWithDefaultsByKeyQuery.Data(
        GetNonNullDatesWithDefaultsByKeyQuery.Data.NonNullDatesWithDefaults(
          valueWithVariableDefault = dateFromYearMonthDayUTC(6904, 11, 30),
          valueWithSchemaDefault = dateFromYearMonthDayUTC(2112, 1, 31),
          epoch = EdgeCases.dates.zero.date,
          requestTime1 = expectedRequestTime,
          requestTime2 = expectedRequestTime,
        )
      )
  }

  @Test
  fun nonNullDate_insert_ShouldFailIfDateVariableIsNull() = runTest {
    shouldThrow<DataConnectException> {
      connector.insertNonNullDate.executeWithStringVariables(null).data.key
    }
  }

  @Test
  fun nonNullDate_insert_ShouldFailIfDateVariableIsAnInt() = runTest {
    shouldThrow<DataConnectException> {
      connector.insertNonNullDate.executeWithIntVariables(Arb.int().next(rs)).data.key
    }
  }

  @Test
  fun nonNullDate_insert_ShouldFailIfDateVariableIsOmitted() = runTest {
    shouldThrow<DataConnectException> {
      connector.insertNonNullDate.executeWithEmptyVariables().data.key
    }
  }

  @Test
  fun nonNullDate_insert_ShouldFailIfDateVariableIsMalformed() = runTest {
    for (invalidDate in invalidDates) {
      shouldThrow<DataConnectException> {
        connector.insertNonNullDate.executeWithStringVariables(invalidDate).data.key
      }
    }
  }

  @Test
  fun nonNullDate_update_NormalCases() = runTest {
    checkAll(20, Arb.dataConnect.date(), Arb.dataConnect.date()) { date1, date2 ->
      val key = connector.insertNonNullDate.execute(date1.date).data.key
      connector.updateNonNullDate.execute(key) { value = date2.date }
      assertNonNullDateByKeyEquals(key, date2.string)
    }
  }

  @Test
  fun nonNullDate_update_EdgeCases() = runTest {
    val edgeCases = EdgeCases.dates.all
    val dates1 = edgeCases + List(edgeCases.size) { Arb.dataConnect.date().next(rs) } + edgeCases
    val dates2 = List(edgeCases.size) { Arb.dataConnect.date().next(rs) } + edgeCases + edgeCases

    assertSoftly {
      for ((date1, date2) in dates1.zip(dates2)) {
        withClue("date1=${date1.string} date2=${date2.string}") {
          val key = connector.insertNonNullDate.execute(date1.date).data.key
          connector.updateNonNullDate.execute(key) { value = date2.date }
          assertNonNullDateByKeyEquals(key, date2.string)
        }
      }
    }
  }

  @Test
  fun nonNullDate_update_DateVariableOmitted() = runTest {
    val date = Arb.dataConnect.date().next(rs)
    val key = connector.insertNonNullDate.execute(date.date).data.key
    connector.updateNonNullDate.execute(key) {}
    assertNonNullDateByKeyEquals(key, date.date)
  }

  @Test
  fun nullableDate_insert_NormalCases() = runTest {
    checkAll(20, Arb.dataConnect.date()) {
      val key = connector.insertNullableDate.execute { value = it.date }.data.key
      assertNullableDateByKeyEquals(key, it.string)
    }
  }

  @Test
  fun nullableDate_insert_EdgeCases() = runTest {
    val edgeCases = EdgeCases.dates.all + listOf(null)
    assertSoftly {
      edgeCases.forEach {
        val key = connector.insertNullableDate.execute { value = it?.date }.data.key
        if (it === null) {
          assertNullableDateByKeyHasNullInnerValue(key)
        } else {
          assertNullableDateByKeyEquals(key, it.string)
        }
      }
    }
  }

  @Test
  fun nullableDate_insert_ShouldUseNullIfDateVariableIsOmitted() = runTest {
    val key = connector.insertNullableDate.execute {}.data.key
    assertNullableDateByKeyHasNullInnerValue(key)
  }

  @Test
  fun nullableDate_insert_ShouldIgnoreTimeZone() = runTest {
    // Use a date that, when converted to UTC, in on a different date to verify that the server does
    // the expected thing; that is, that it _drops_ the time zone information (rather than
    // converting the date to UTC then taking the YYYY-MM-DD of that). The server would use the date
    // "2024-03-27" if it did the erroneous conversion to UTC before taking the YYYY-MM-DD.
    val date = "2024-03-26T19:48:00.144-07:00"
    val key = connector.insertNullableDate.executeWithStringVariables(date).data.key
    assertNullableDateByKeyEquals(key, dateFromYearMonthDayUTC(2024, 3, 26))
  }

  @Test
  fun nullableDate_insert_ShouldIgnoreTime() = runTest {
    checkAll(20, Arb.dataConnect.dateOffDayBoundary()) {
      val key = connector.insertNullableDate.execute { value = it.date }.data.key
      assertNullableDateByKeyEquals(key, it.string)
    }
  }

  @Test
  fun nullableDate_insert_ShouldFailIfDateVariableIsAnInt() = runTest {
    shouldThrow<DataConnectException> {
      connector.insertNullableDate.executeWithIntVariables(Arb.int().next(rs)).data.key
    }
  }

  @Test
  fun nullableDate_insert_ShouldFailIfDateVariableIsMalformed() = runTest {
    for (invalidDate in invalidDates) {
      shouldThrow<DataConnectException> {
        connector.insertNonNullDate.executeWithStringVariables(invalidDate).data.key
      }
    }
  }

  @Test
  fun nullableDatesWithDefaults_insert_ShouldUseDefaultValuesIfNoVariablesSpecified() = runTest {
    val key = connector.insertNullableDatesWithDefaults.execute {}.data.key
    val queryResult = connector.getNullableDatesWithDefaultsByKey.execute(key)

    // Since we can't know the exact value of `request.time` just make sure that the exact same
    // value is used for both fields to which it is set.
    val expectedRequestTime = queryResult.data.nullableDatesWithDefaults!!.requestTime1

    queryResult.data shouldBe
      GetNullableDatesWithDefaultsByKeyQuery.Data(
        GetNullableDatesWithDefaultsByKeyQuery.Data.NullableDatesWithDefaults(
          valueWithVariableDefault = dateFromYearMonthDayUTC(8113, 2, 9),
          valueWithSchemaDefault = dateFromYearMonthDayUTC(1921, 12, 2),
          epoch = EdgeCases.dates.zero.date,
          requestTime1 = expectedRequestTime,
          requestTime2 = expectedRequestTime,
        )
      )
  }

  @Test
  fun nullableDate_update_NormalCases() = runTest {
    checkAll(20, Arb.dataConnect.date(), Arb.dataConnect.date()) { date1, date2 ->
      val key = connector.insertNullableDate.execute { value = date1.date }.data.key
      connector.updateNullableDate.execute(key) { value = date2.date }
      assertNullableDateByKeyEquals(key, date2.string)
    }
  }

  @Test
  fun nullableDate_update_EdgeCases() = runTest {
    val edgeCases = EdgeCases.dates.all
    val dates1 = edgeCases + List(edgeCases.size) { Arb.dataConnect.date().next(rs) } + edgeCases
    val dates2 = List(edgeCases.size) { Arb.dataConnect.date().next(rs) } + edgeCases + edgeCases

    assertSoftly {
      for ((date1, date2) in dates1.zip(dates2)) {
        withClue("date1=${date1.string} date2=${date2.string}") {
          val key = connector.insertNullableDate.execute { value = date1.date }.data.key
          connector.updateNullableDate.execute(key) { value = date2.date }
          assertNullableDateByKeyEquals(key, date2.string)
        }
      }
    }
  }

  @Test
  fun nullableDate_update_UpdateNonNullValueToNull() = runTest {
    val date = Arb.dataConnect.date().next(rs).date
    val key = connector.insertNullableDate.execute { value = date }.data.key
    connector.updateNullableDate.execute(key) { value = null }
    assertNullableDateByKeyHasNullInnerValue(key)
  }

  @Test
  fun nullableDate_update_UpdateNullValueToNonNull() = runTest {
    val date = Arb.dataConnect.date().next(rs).date
    val key = connector.insertNullableDate.execute { value = null }.data.key
    connector.updateNullableDate.execute(key) { value = date }
    assertNullableDateByKeyEquals(key, date)
  }

  @Test
  fun nullableDate_update_DateVariableOmitted() = runTest {
    val date = Arb.dataConnect.date().next(rs).date
    val key = connector.insertNullableDate.execute { value = date }.data.key
    connector.updateNullableDate.execute(key) {}
    assertNullableDateByKeyEquals(key, date)
  }

  private suspend fun assertNonNullDateByKeyEquals(key: NonNullDateKey, expected: String) {
    val queryResult =
      connector.getNonNullDateByKey
        .withDataDeserializer(serializer<GetDateByKeyQueryStringData>())
        .execute(key)
    queryResult.data shouldBe GetDateByKeyQueryStringData(expected)
  }

  private suspend fun assertNonNullDateByKeyEquals(key: NonNullDateKey, expected: Date) {
    val queryResult = connector.getNonNullDateByKey.execute(key)
    queryResult.data shouldBe
      GetNonNullDateByKeyQuery.Data(GetNonNullDateByKeyQuery.Data.Value(expected))
  }

  private suspend fun assertNullableDateByKeyHasNullInnerValue(key: NullableDateKey) {
    val queryResult =
      connector.getNullableDateByKey
        .withDataDeserializer(serializer<GetDateByKeyQueryStringData>())
        .execute(key)
    queryResult.data shouldBe
      GetDateByKeyQueryStringData(GetDateByKeyQueryStringData.DateStringValue(null))
  }

  private suspend fun assertNullableDateByKeyEquals(key: NullableDateKey, expected: String) {
    val queryResult =
      connector.getNullableDateByKey
        .withDataDeserializer(serializer<GetDateByKeyQueryStringData>())
        .execute(key)
    queryResult.data shouldBe GetDateByKeyQueryStringData(expected)
  }

  private suspend fun assertNullableDateByKeyEquals(key: NullableDateKey, expected: Date) {
    val queryResult = connector.getNullableDateByKey.execute(key)
    queryResult.data shouldBe
      GetNullableDateByKeyQuery.Data(GetNullableDateByKeyQuery.Data.Value(expected))
  }

  /**
   * A `Data` type that can be used in place of [GetNonNullDateByKeyQuery.Data] that types the value
   * as a [String] instead of a [Date], allowing verification of the data sent over the wire without
   * possible confounding from date deserialization.
   */
  @Serializable
  private data class GetDateByKeyQueryStringData(val value: DateStringValue?) {
    constructor(value: String) : this(DateStringValue(value))

    @Serializable data class DateStringValue(val value: String?)
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
