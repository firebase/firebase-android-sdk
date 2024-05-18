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
import com.google.firebase.dataconnect.DataConnectException
import com.google.firebase.dataconnect.connectors.demo.testutil.DemoConnectorIntegrationTestBase
import com.google.firebase.dataconnect.generated.GeneratedMutation
import com.google.firebase.dataconnect.generated.GeneratedQuery
import com.google.firebase.dataconnect.testutil.MAX_TIMESTAMP
import com.google.firebase.dataconnect.testutil.MIN_TIMESTAMP
import com.google.firebase.dataconnect.testutil.ZERO_TIMESTAMP
import com.google.firebase.dataconnect.testutil.assertThrows
import com.google.firebase.dataconnect.testutil.executeWithEmptyVariables
import com.google.firebase.dataconnect.testutil.randomTimestamp
import com.google.firebase.dataconnect.testutil.timestampFromUTCDateAndTime
import com.google.firebase.dataconnect.testutil.withDataDeserializer
import com.google.firebase.dataconnect.testutil.withMicrosecondPrecision
import com.google.firebase.dataconnect.testutil.withVariablesSerializer
import kotlin.random.Random
import kotlin.random.nextInt
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.junit.Test

class TimestampScalarIntegrationTest : DemoConnectorIntegrationTestBase() {

  @Test
  fun insertTypicalValueForNonNullTimestampField() = runTest {
    val timestamp = timestampFromUTCDateAndTime(2361, 1, 16, 2, 36, 25, 253177157)
    val key = connector.insertNonNullTimestamp.execute(timestamp).data.key
    assertNonNullTimestampByKeyEquals(key, "2361-01-16T02:36:25.253177Z")
  }

  @Test
  fun insertMaxValueForNonNullTimestampField() = runTest {
    val key = connector.insertNonNullTimestamp.execute(MIN_TIMESTAMP).data.key
    assertNonNullTimestampByKeyEquals(key, "1583-01-01T00:00:00.000000Z")
  }

  @Test
  fun insertMinValueForNonNullTimestampField() = runTest {
    val key = connector.insertNonNullTimestamp.execute(MAX_TIMESTAMP).data.key
    assertNonNullTimestampByKeyEquals(key, "9999-12-31T23:59:59.999999Z")
  }

  @Test
  fun insertTimestampWithSingleDigitsForNonNullTimestampField() = runTest {
    val timestamp = timestampFromUTCDateAndTime(7513, 1, 2, 3, 4, 5, 6000)
    val key = connector.insertNonNullTimestamp.execute(timestamp).data.key
    assertNonNullTimestampByKeyEquals(key, "7513-01-02T03:04:05.000006Z")
  }

  @Test
  fun insertTimestampWithAllDigitsForNonNullTimestampField() = runTest {
    val timestamp = timestampFromUTCDateAndTime(8623, 10, 11, 12, 13, 14, 123456789)
    val key = connector.insertNonNullTimestamp.execute(timestamp).data.key
    assertNonNullTimestampByKeyEquals(key, "8623-10-11T12:13:14.123456Z")
  }

  @Test
  fun insertNoVariablesForNonNullTimestampFieldsWithDefaults() = runTest {
    val key = connector.insertNonNullTimestampsWithDefaults.execute {}.data.key
    val queryResult = connector.getNonNullTimestampsWithDefaultsByKey.execute(key)

    // Since we can't know the exact value of `request.time` just make sure that the exact same
    // value is used for both fields to which it is set.
    val expectedRequestTime = queryResult.data.nonNullTimestampsWithDefaults!!.requestTime1

    assertThat(
      queryResult.equals(
        GetNonNullTimestampsWithDefaultsByKeyQuery.Data(
          GetNonNullTimestampsWithDefaultsByKeyQuery.Data.NonNullTimestampsWithDefaults(
            valueWithVariableDefault =
              timestampFromUTCDateAndTime(3575, 4, 12, 10, 11, 12, 541991000),
            valueWithSchemaDefault = timestampFromUTCDateAndTime(6224, 1, 31, 14, 2, 45, 714214000),
            epoch = ZERO_TIMESTAMP,
            requestTime1 = expectedRequestTime,
            requestTime2 = expectedRequestTime,
          )
        )
      )
    )
  }

  @Test
  fun insertNullForNonNullTimestampFieldShouldFail() = runTest {
    assertThrows(DataConnectException::class) {
      connector.insertNonNullTimestamp.executeWithStringVariables(null).data.key
    }
  }

  @Test
  fun insertIntForNonNullTimestampFieldShouldFail() = runTest {
    assertThrows(DataConnectException::class) {
      connector.insertNonNullTimestamp.executeWithIntVariables(777_666).data.key
    }
  }

  @Test
  fun insertWithMissingValueNonNullTimestampFieldShouldFail() = runTest {
    assertThrows(DataConnectException::class) {
      connector.insertNonNullTimestamp.executeWithEmptyVariables().data.key
    }
  }

  @Test
  fun insertInvalidTimestampsValuesForNonNullTimestampFieldShouldFail() = runTest {
    for (invalidTimestamp in invalidTimestamps) {
      assertThrows(DataConnectException::class) {
        connector.insertNonNullTimestamp.executeWithStringVariables(invalidTimestamp).data.key
      }
    }
  }

  @Test
  fun updateNonNullTimestampFieldToAnotherValidValue() = runTest {
    val timestamp1 = randomTimestamp()
    val timestamp2 = timestampFromUTCDateAndTime(1795, 1, 12, 19, 3, 56, 40585847)
    val key = connector.insertNonNullTimestamp.execute(timestamp1).data.key
    connector.updateNonNullTimestamp.execute(key) { value = timestamp2 }
    assertNonNullTimestampByKeyEquals(key, "1795-01-12T19:03:56.040585Z")
  }

  @Test
  fun updateNonNullTimestampFieldToMinValue() = runTest {
    val timestamp = randomTimestamp()
    val key = connector.insertNonNullTimestamp.execute(timestamp).data.key
    connector.updateNonNullTimestamp.execute(key) { value = MIN_TIMESTAMP }
    assertNonNullTimestampByKeyEquals(key, "1583-01-01T00:00:00.000000Z")
  }

  @Test
  fun updateNonNullTimestampFieldToMaxValue() = runTest {
    val timestamp = randomTimestamp()
    val key = connector.insertNonNullTimestamp.execute(timestamp).data.key
    connector.updateNonNullTimestamp.execute(key) { value = MAX_TIMESTAMP }
    assertNonNullTimestampByKeyEquals(key, "9999-12-31T23:59:59.999999Z")
  }

  @Test
  fun updateNonNullTimestampFieldToAnUndefinedValue() = runTest {
    val timestamp = randomTimestamp()
    val key = connector.insertNonNullTimestamp.execute(timestamp).data.key
    connector.updateNonNullTimestamp.execute(key) {}
    assertNonNullTimestampByKeyEquals(key, timestamp.withMicrosecondPrecision())
  }

  @Test
  fun insertTypicalValueForNullableField() = runTest {
    val timestamp = timestampFromUTCDateAndTime(1891, 5, 13, 5, 20, 38, 646067609)
    val key = connector.insertNullableTimestamp.execute { value = timestamp }.data.key
    assertNullableTimestampByKeyEquals(key, "1891-05-13T05:20:38.646067Z")
  }

  @Test
  fun insertMaxValueForNullableTimestampField() = runTest {
    val key = connector.insertNullableTimestamp.execute { value = MIN_TIMESTAMP }.data.key
    assertNullableTimestampByKeyEquals(key, "1583-01-01T00:00:00.000000Z")
  }

  @Test
  fun insertMinValueForNullableTimestampField() = runTest {
    val key = connector.insertNullableTimestamp.execute { value = MAX_TIMESTAMP }.data.key
    assertNullableTimestampByKeyEquals(key, "9999-12-31T23:59:59.999999Z")
  }

  @Test
  fun insertNullForNullableTimestampField() = runTest {
    val key = connector.insertNullableTimestamp.execute { value = null }.data.key
    assertNullableTimestampByKeyEquals(key, null)
  }

  @Test
  fun insertUndefinedForNullableTimestampField() = runTest {
    val key = connector.insertNullableTimestamp.execute {}.data.key
    assertNullableTimestampByKeyEquals(key, null)
  }

  @Test
  fun insertTimestampWithSingleDigitsForNullableTimestampField() = runTest {
    val timestamp = timestampFromUTCDateAndTime(6651, 1, 2, 3, 4, 5, 6000)
    val key = connector.insertNullableTimestamp.execute { value = timestamp }.data.key
    assertNullableTimestampByKeyEquals(key, "6651-01-02T03:04:05.000006Z")
  }

  @Test
  fun insertTimestampWithAllDigitsForNullableTimestampField() = runTest {
    val timestamp = timestampFromUTCDateAndTime(7992, 10, 11, 12, 13, 14, 123456789)
    val key = connector.insertNullableTimestamp.execute { value = timestamp }.data.key
    assertNullableTimestampByKeyEquals(key, "7992-10-11T12:13:14.123456Z")
  }

  @Test
  fun insertIntForNullableTimestampFieldShouldFail() = runTest {
    assertThrows(DataConnectException::class) {
      connector.insertNullableTimestamp.executeWithIntVariables(555_444).data.key
    }
  }

  @Test
  fun insertInvalidTimestampsValuesForNullableTimestampFieldShouldFail() = runTest {
    for (invalidTimestamp in invalidTimestamps) {
      assertThrows(DataConnectException::class) {
        connector.insertNullableTimestamp.executeWithStringVariables(invalidTimestamp).data.key
      }
    }
  }

  @Test
  fun insertNoVariablesForNullableTimestampFieldsWithSchemaDefaults() = runTest {
    val key = connector.insertNullableTimestampsWithDefaults.execute {}.data.key
    val queryResult = connector.getNullableTimestampsWithDefaultsByKey.execute(key)

    // Since we can't know the exact value of `request.time` just make sure that the exact same
    // value is used for both fields to which it is set.
    val expectedRequestTime = queryResult.data.nullableTimestampsWithDefaults!!.requestTime1

    assertThat(
      queryResult.equals(
        GetNullableTimestampsWithDefaultsByKeyQuery.Data(
          GetNullableTimestampsWithDefaultsByKeyQuery.Data.NullableTimestampsWithDefaults(
            valueWithVariableDefault =
              timestampFromUTCDateAndTime(2554, 12, 20, 13, 3, 45, 110429000),
            valueWithSchemaDefault = timestampFromUTCDateAndTime(1621, 12, 3, 1, 22, 3, 513914000),
            epoch = ZERO_TIMESTAMP,
            requestTime1 = expectedRequestTime,
            requestTime2 = expectedRequestTime,
          )
        )
      )
    )
  }

  @Test
  fun updateNullableTimestampFieldToAnotherValidValue() = runTest {
    val timestamp1 = randomTimestamp()
    val timestamp2 = timestampFromUTCDateAndTime(7947, 7, 22, 13, 19, 55, 669650046)
    val key = connector.insertNullableTimestamp.execute { value = timestamp1 }.data.key
    connector.updateNullableTimestamp.execute(key) { value = timestamp2 }
    assertNullableTimestampByKeyEquals(key, "7947-07-22T13:19:55.669650Z")
  }

  @Test
  fun updateNullableTimestampFieldToMinValue() = runTest {
    val timestamp = randomTimestamp()
    val key = connector.insertNullableTimestamp.execute { value = timestamp }.data.key
    connector.updateNullableTimestamp.execute(key) { value = MIN_TIMESTAMP }
    assertNullableTimestampByKeyEquals(key, "1583-01-01T00:00:00.000000Z")
  }

  @Test
  fun updateNullableTimestampFieldToMaxValue() = runTest {
    val timestamp = randomTimestamp()
    val key = connector.insertNullableTimestamp.execute { value = timestamp }.data.key
    connector.updateNullableTimestamp.execute(key) { value = MAX_TIMESTAMP }
    assertNullableTimestampByKeyEquals(key, "9999-12-31T23:59:59.999999Z")
  }

  @Test
  fun updateNullableTimestampFieldToNull() = runTest {
    val timestamp = randomTimestamp()
    val key = connector.insertNullableTimestamp.execute { value = timestamp }.data.key
    connector.updateNullableTimestamp.execute(key) { value = null }
    assertNullableTimestampByKeyEquals(key, null)
  }

  @Test
  fun updateNullableTimestampFieldToNonNull() = runTest {
    val timestamp = randomTimestamp()
    val key = connector.insertNullableTimestamp.execute { value = null }.data.key
    connector.updateNullableTimestamp.execute(key) { value = timestamp }
    assertNullableTimestampByKeyEquals(key, timestamp.withMicrosecondPrecision())
  }

  @Test
  fun updateNullableTimestampFieldToAnUndefinedValue() = runTest {
    val timestamp = randomTimestamp()
    val key = connector.insertNullableTimestamp.execute { value = timestamp }.data.key
    connector.updateNullableTimestamp.execute(key) {}
    assertNullableTimestampByKeyEquals(key, timestamp.withMicrosecondPrecision())
  }

  private suspend fun assertNonNullTimestampByKeyEquals(
    key: NonNullTimestampKey,
    expected: String
  ) {
    val queryResult =
      connector.getNonNullTimestampByKey
        .withDataDeserializer(serializer<GetTimestampByKeyQueryStringData>())
        .execute(key)
    assertThat(queryResult.data).isEqualTo(GetTimestampByKeyQueryStringData(expected))
  }

  private suspend fun assertNonNullTimestampByKeyEquals(
    key: NonNullTimestampKey,
    expected: Timestamp
  ) {
    val queryResult = connector.getNonNullTimestampByKey.execute(key)
    assertThat(queryResult.data)
      .isEqualTo(
        GetNonNullTimestampByKeyQuery.Data(GetNonNullTimestampByKeyQuery.Data.Value(expected))
      )
  }

  private suspend fun assertNullableTimestampByKeyEquals(
    key: NullableTimestampKey,
    expected: String
  ) {
    val queryResult =
      connector.getNullableTimestampByKey
        .withDataDeserializer(serializer<GetTimestampByKeyQueryStringData>())
        .execute(key)
    assertThat(queryResult.data).isEqualTo(GetTimestampByKeyQueryStringData(expected))
  }

  private suspend fun assertNullableTimestampByKeyEquals(
    key: NullableTimestampKey,
    expected: Timestamp?
  ) {
    val queryResult = connector.getNullableTimestampByKey.execute(key)
    assertThat(queryResult.data)
      .isEqualTo(
        GetNullableTimestampByKeyQuery.Data(GetNullableTimestampByKeyQuery.Data.Value(expected))
      )
  }

  /**
   * A `Data` type that can be used in place of [GetNonNullTimestampByKeyQuery.Data] that types the
   * value as a [String] instead of a [Timestamp], allowing verification of the data sent over the
   * wire without possible confounding from timestamp deserialization.
   */
  @Serializable
  private data class GetTimestampByKeyQueryStringData(val value: TimestampStringValue?) {
    constructor(value: String) : this(TimestampStringValue(value))
    @Serializable data class TimestampStringValue(val value: String)
  }

  /**
   * A `Variables` type that can be used in place of [InsertNonNullTimestampMutation.Variables] that
   * types the value as a [String] instead of a [Timestamp], allowing verification of the data sent
   * over the wire without possible confounding from timestamp serialization.
   */
  @Serializable private data class InsertTimestampStringVariables(val value: String?)

  /**
   * A `Variables` type that can be used in place of [InsertNonNullTimestampMutation.Variables] that
   * types the value as a [Int] instead of a [Timestamp], allowing verification that the server
   * fails with an expected error (rather than crashing, for example).
   */
  @Serializable private data class InsertTimestampIntVariables(val value: Int)

  private companion object {

    suspend fun <Data> GeneratedMutation<*, Data, *>.executeWithStringVariables(value: String?) =
      withVariablesSerializer(serializer<InsertTimestampStringVariables>())
        .ref(InsertTimestampStringVariables(value))
        .execute()

    suspend fun <Data> GeneratedMutation<*, Data, *>.executeWithIntVariables(value: Int) =
      withVariablesSerializer(serializer<InsertTimestampIntVariables>())
        .ref(InsertTimestampIntVariables(value))
        .execute()

    suspend fun <Data> GeneratedQuery<*, Data, GetNonNullTimestampByKeyQuery.Variables>.execute(
      key: NonNullTimestampKey
    ) = ref(GetNonNullTimestampByKeyQuery.Variables(key)).execute()

    suspend fun <Data> GeneratedQuery<*, Data, GetNullableTimestampByKeyQuery.Variables>.execute(
      key: NullableTimestampKey
    ) = ref(GetNullableTimestampByKeyQuery.Variables(key)).execute()

    /** Convenience function to use when writing tests that will generate random timestamps. */
    @Suppress("unused")
    fun printRandomTimestamps() {
      repeat(100) {
        val year = Random.nextInt(0..9999)
        val month = Random.nextInt(0..11)
        val day = Random.nextInt(0..28)
        val hour = Random.nextInt(0..23)
        val minute = Random.nextInt(0..59)
        val second = Random.nextInt(0..59)
        val nanoseconds = Random.nextInt(0..999_999_999)
        println(
          buildString {
            append(
              "timestampFromDateAndTimeUTC($year, $month, $day, $hour, $minute, $second, $nanoseconds)"
            )
            append(" // ")
            append("$year".padStart(4, '0'))
            append('-')
            append("$month".padStart(2, '0'))
            append('-')
            append("$day".padStart(2, '0'))
            append('T')
            append("$hour".padStart(2, '0'))
            append(':')
            append("$minute".padStart(2, '0'))
            append(':')
            append("$second".padStart(2, '0'))
            append('.')
            append("$nanoseconds".padStart(9, '0'))
            append('Z')
          }
        )
      }
    }

    val invalidTimestamps = listOf("")
  }
}
