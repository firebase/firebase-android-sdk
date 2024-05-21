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
import org.junit.Ignore
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
  fun insertTimestampWithNoNanosecondsForNonNullTimestampField() = runTest {
    val timestamp = "2024-05-18T12:45:56Z"
    val key = connector.insertNonNullTimestamp.executeWithStringVariables(timestamp).data.key
    assertNonNullTimestampByKeyEquals(key, "2024-05-18T12:45:56.000000Z")
  }

  @Test
  fun insertTimestampWithZeroNanosecondsForNonNullTimestampField() = runTest {
    val timestamp = "2024-05-18T12:45:56.000000000Z"
    val key = connector.insertNonNullTimestamp.executeWithStringVariables(timestamp).data.key
    assertNonNullTimestampByKeyEquals(key, "2024-05-18T12:45:56.000000Z")
  }

  @Test
  fun insertTimestampWith1NanosecondsDigitForNonNullTimestampField() = runTest {
    val timestamp = "2024-05-18T12:45:56.1Z"
    val key = connector.insertNonNullTimestamp.executeWithStringVariables(timestamp).data.key
    assertNonNullTimestampByKeyEquals(key, "2024-05-18T12:45:56.100000Z")
  }

  @Test
  fun insertTimestampWith2NanosecondsDigitsForNonNullTimestampField() = runTest {
    val timestamp = "2024-05-18T12:45:56.12Z"
    val key = connector.insertNonNullTimestamp.executeWithStringVariables(timestamp).data.key
    assertNonNullTimestampByKeyEquals(key, "2024-05-18T12:45:56.120000Z")
  }

  @Test
  fun insertTimestampWith3NanosecondsDigitsForNonNullTimestampField() = runTest {
    val timestamp = "2024-05-18T12:45:56.123Z"
    val key = connector.insertNonNullTimestamp.executeWithStringVariables(timestamp).data.key
    assertNonNullTimestampByKeyEquals(key, "2024-05-18T12:45:56.123000Z")
  }

  @Test
  fun insertTimestampWith4NanosecondsDigitsForNonNullTimestampField() = runTest {
    val timestamp = "2024-05-18T12:45:56.1234Z"
    val key = connector.insertNonNullTimestamp.executeWithStringVariables(timestamp).data.key
    assertNonNullTimestampByKeyEquals(key, "2024-05-18T12:45:56.123400Z")
  }

  @Test
  fun insertTimestampWith5NanosecondsDigitsForNonNullTimestampField() = runTest {
    val timestamp = "2024-05-18T12:45:56.12345Z"
    val key = connector.insertNonNullTimestamp.executeWithStringVariables(timestamp).data.key
    assertNonNullTimestampByKeyEquals(key, "2024-05-18T12:45:56.123450Z")
  }

  @Test
  fun insertTimestampWith6NanosecondsDigitsForNonNullTimestampField() = runTest {
    val timestamp = "2024-05-18T12:45:56.123456Z"
    val key = connector.insertNonNullTimestamp.executeWithStringVariables(timestamp).data.key
    assertNonNullTimestampByKeyEquals(key, "2024-05-18T12:45:56.123456Z")
  }

  @Test
  fun insertTimestampWith7NanosecondsDigitsForNonNullTimestampField() = runTest {
    val timestamp = "2024-05-18T12:45:56.1234567Z"
    val key = connector.insertNonNullTimestamp.executeWithStringVariables(timestamp).data.key
    assertNonNullTimestampByKeyEquals(key, "2024-05-18T12:45:56.123456Z")
  }

  @Test
  fun insertTimestampWith8NanosecondsDigitsForNonNullTimestampField() = runTest {
    val timestamp = "2024-05-18T12:45:56.12345678Z"
    val key = connector.insertNonNullTimestamp.executeWithStringVariables(timestamp).data.key
    assertNonNullTimestampByKeyEquals(key, "2024-05-18T12:45:56.123456Z")
  }

  @Test
  fun insertTimestampWith9NanosecondsDigitsForNonNullTimestampField() = runTest {
    val timestamp = "2024-05-18T12:45:56.123456789Z"
    val key = connector.insertNonNullTimestamp.executeWithStringVariables(timestamp).data.key
    assertNonNullTimestampByKeyEquals(key, "2024-05-18T12:45:56.123456Z")
  }

  @Test
  fun insertTimestampWithPlus0TimeZoneOffsetForNonNullTimestampField() = runTest {
    val timestamp = "2024-05-18T12:45:56.123456789+00:00"
    val key = connector.insertNonNullTimestamp.executeWithStringVariables(timestamp).data.key
    assertNonNullTimestampByKeyEquals(key, "2024-05-18T12:45:56.123456Z")
  }

  @Test
  fun insertTimestampWithPositiveNonZeroTimeZoneOffsetForNonNullTimestampField() = runTest {
    val timestamp = "2024-05-18T12:45:56.123456789+01:23"
    val key = connector.insertNonNullTimestamp.executeWithStringVariables(timestamp).data.key
    assertNonNullTimestampByKeyEquals(key, "2024-05-18T11:22:56.123456Z")
  }

  @Test
  fun insertTimestampWithNegativeNonZeroTimeZoneOffsetForNonNullTimestampField() = runTest {
    val timestamp = "2024-05-18T12:45:56.123456789-01:23"
    val key = connector.insertNonNullTimestamp.executeWithStringVariables(timestamp).data.key
    assertNonNullTimestampByKeyEquals(key, "2024-05-18T14:08:56.123456Z")
  }

  @Test
  @Ignore("TODO(b/341984878): Re-enable this test once the backend accepts leap seconds")
  fun insertTimestampWithLeapSecondStringForNonNullTimestampField() = runTest {
    val timestamp = "1990-12-31T23:59:60Z" // From RFC3339 section 5.8
    val key = connector.insertNonNullTimestamp.executeWithStringVariables(timestamp).data.key
    assertNonNullTimestampByKeyEquals(key, "1990-12-31T23:59:60.000000Z")
  }

  @Test
  fun insertTimestampWithLeapSecondDateForNonNullTimestampField() = runTest {
    val timestamp = timestampFromUTCDateAndTime(1990, 12, 31, 23, 59, 60, 0)
    val key = connector.insertNonNullTimestamp.execute(timestamp).data.key
    assertNonNullTimestampByKeyEquals(key, timestamp)
  }

  @Test
  @Ignore("TODO(b/341984878): Re-enable this test once the backend accepts lowercase T and Z")
  fun insertTimestampWithLowercaseTandZForNonNullTimestampField() = runTest {
    val timestamp = "2024-05-18t12:45:56.123456789z"
    val key = connector.insertNonNullTimestamp.executeWithStringVariables(timestamp).data.key
    assertNonNullTimestampByKeyEquals(key, "2024-05-18T12:45:56.123456Z")
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
        connector.insertNonNullTimestamp.executeWithStringVariables(invalidTimestamp)
      }
    }
  }

  @Test
  @Ignore(
    "TODO(b/341984878): Add these test cases back to `invalidTimestamps` once the " +
      "emulator is fixed to correctly reject them"
  )
  fun insertInvalidTimestampsValuesForNonNullTimestampFieldShouldFailBugs() = runTest {
    for (invalidTimestamp in invalidTimestampsThatAreErroneouslyAcceptedByTheServer) {
      assertThrows(DataConnectException::class) {
        connector.insertNonNullTimestamp.executeWithStringVariables(invalidTimestamp)
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
  fun insertTimestampWithNoNanosecondsForNullableTimestampField() = runTest {
    val timestamp = "2024-05-18T12:45:56Z"
    val key = connector.insertNullableTimestamp.executeWithStringVariables(timestamp).data.key
    assertNullableTimestampByKeyEquals(key, "2024-05-18T12:45:56.000000Z")
  }

  @Test
  fun insertTimestampWithZeroNanosecondsForNullableTimestampField() = runTest {
    val timestamp = "2024-05-18T12:45:56.000000000Z"
    val key = connector.insertNullableTimestamp.executeWithStringVariables(timestamp).data.key
    assertNullableTimestampByKeyEquals(key, "2024-05-18T12:45:56.000000Z")
  }

  @Test
  fun insertTimestampWith1NanosecondsDigitForNullableTimestampField() = runTest {
    val timestamp = "2024-05-18T12:45:56.1Z"
    val key = connector.insertNullableTimestamp.executeWithStringVariables(timestamp).data.key
    assertNullableTimestampByKeyEquals(key, "2024-05-18T12:45:56.100000Z")
  }

  @Test
  fun insertTimestampWith2NanosecondsDigitsForNullableTimestampField() = runTest {
    val timestamp = "2024-05-18T12:45:56.12Z"
    val key = connector.insertNullableTimestamp.executeWithStringVariables(timestamp).data.key
    assertNullableTimestampByKeyEquals(key, "2024-05-18T12:45:56.120000Z")
  }

  @Test
  fun insertTimestampWith3NanosecondsDigitsForNullableTimestampField() = runTest {
    val timestamp = "2024-05-18T12:45:56.123Z"
    val key = connector.insertNullableTimestamp.executeWithStringVariables(timestamp).data.key
    assertNullableTimestampByKeyEquals(key, "2024-05-18T12:45:56.123000Z")
  }

  @Test
  fun insertTimestampWith4NanosecondsDigitsForNullableTimestampField() = runTest {
    val timestamp = "2024-05-18T12:45:56.1234Z"
    val key = connector.insertNullableTimestamp.executeWithStringVariables(timestamp).data.key
    assertNullableTimestampByKeyEquals(key, "2024-05-18T12:45:56.123400Z")
  }

  @Test
  fun insertTimestampWith5NanosecondsDigitsForNullableTimestampField() = runTest {
    val timestamp = "2024-05-18T12:45:56.12345Z"
    val key = connector.insertNullableTimestamp.executeWithStringVariables(timestamp).data.key
    assertNullableTimestampByKeyEquals(key, "2024-05-18T12:45:56.123450Z")
  }

  @Test
  fun insertTimestampWith6NanosecondsDigitsForNullableTimestampField() = runTest {
    val timestamp = "2024-05-18T12:45:56.123456Z"
    val key = connector.insertNullableTimestamp.executeWithStringVariables(timestamp).data.key
    assertNullableTimestampByKeyEquals(key, "2024-05-18T12:45:56.123456Z")
  }

  @Test
  fun insertTimestampWith7NanosecondsDigitsForNullableTimestampField() = runTest {
    val timestamp = "2024-05-18T12:45:56.1234567Z"
    val key = connector.insertNullableTimestamp.executeWithStringVariables(timestamp).data.key
    assertNullableTimestampByKeyEquals(key, "2024-05-18T12:45:56.123456Z")
  }

  @Test
  fun insertTimestampWith8NanosecondsDigitsForNullableTimestampField() = runTest {
    val timestamp = "2024-05-18T12:45:56.12345678Z"
    val key = connector.insertNullableTimestamp.executeWithStringVariables(timestamp).data.key
    assertNullableTimestampByKeyEquals(key, "2024-05-18T12:45:56.123456Z")
  }

  @Test
  fun insertTimestampWith9NanosecondsDigitsForNullableTimestampField() = runTest {
    val timestamp = "2024-05-18T12:45:56.123456789Z"
    val key = connector.insertNullableTimestamp.executeWithStringVariables(timestamp).data.key
    assertNullableTimestampByKeyEquals(key, "2024-05-18T12:45:56.123456Z")
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
        connector.insertNullableTimestamp.executeWithStringVariables(invalidTimestamp)
      }
    }
  }

  @Test
  @Ignore(
    "TODO(b/341984878): Add these test cases back to `invalidTimestamps` once the " +
      "emulator is fixed to correctly reject them"
  )
  fun insertInvalidTimestampsValuesForNullableTimestampFieldShouldFailBugs() = runTest {
    for (invalidTimestamp in invalidTimestampsThatAreErroneouslyAcceptedByTheServer) {
      assertThrows(DataConnectException::class) {
        connector.insertNullableTimestamp.executeWithStringVariables(invalidTimestamp)
      }
    }
  }

  @Test
  @Ignore("TODO(b/341984878): Re-enable this test once the backend accepts leap seconds")
  fun insertTimestampWithLeapSecondStringForNullableTimestampField() = runTest {
    val timestamp = "1990-12-31T23:59:60Z" // From RFC3339 section 5.8
    val key = connector.insertNullableTimestamp.executeWithStringVariables(timestamp).data.key
    assertNullableTimestampByKeyEquals(key, "1990-12-31T23:59:60.000000Z")
  }

  @Test
  fun insertTimestampWithLeapSecondDateForNullableTimestampField() = runTest {
    val timestamp = timestampFromUTCDateAndTime(1990, 12, 31, 23, 59, 60, 0)
    val key = connector.insertNullableTimestamp.execute { value = timestamp }.data.key
    assertNullableTimestampByKeyEquals(key, timestamp)
  }

  @Test
  fun insertTimestampWithPlus0TimeZoneOffsetForNullableTimestampField() = runTest {
    val timestamp = "2024-05-18T12:45:56.123456789+00:00"
    val key = connector.insertNullableTimestamp.executeWithStringVariables(timestamp).data.key
    assertNullableTimestampByKeyEquals(key, "2024-05-18T12:45:56.123456Z")
  }

  @Test
  fun insertTimestampWithPositiveNonZeroTimeZoneOffsetForNullableTimestampField() = runTest {
    val timestamp = "2024-05-18T12:45:56.123456789+01:23"
    val key = connector.insertNullableTimestamp.executeWithStringVariables(timestamp).data.key
    assertNullableTimestampByKeyEquals(key, "2024-05-18T11:22:56.123456Z")
  }

  @Test
  fun insertTimestampWithNegativeNonZeroTimeZoneOffsetForNullableTimestampField() = runTest {
    val timestamp = "2024-05-18T12:45:56.123456789-01:23"
    val key = connector.insertNullableTimestamp.executeWithStringVariables(timestamp).data.key
    assertNullableTimestampByKeyEquals(key, "2024-05-18T14:08:56.123456Z")
  }

  @Test
  @Ignore("TODO(b/341984878): Re-enable this test once the backend accepts lowercase T and Z")
  fun insertTimestampWithLowercaseTandZForNullableTimestampField() = runTest {
    val timestamp = "2024-05-18t12:45:56.123456789z"
    val key = connector.insertNullableTimestamp.executeWithStringVariables(timestamp).data.key
    assertNullableTimestampByKeyEquals(key, "2024-05-18T12:45:56.123456Z")
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

    val invalidTimestamps =
      listOf(
        "",
        "foobar",

        // Partial timestamps
        "2",
        "20",
        "202",
        "2024",
        "2024-",
        "2024-0",
        "2024-05",
        "2024-05-",
        "2024-05-1",
        "2024-05-18",
        "2024-05-18T",
        "2024-05-18T1",
        "2024-05-18T12",
        "2024-05-18T12:",
        "2024-05-18T12:4",
        "2024-05-18T12:45",
        "2024-05-18T12:45:",
        "2024-05-18T12:45:5",

        // Missing components
        "-05-18T12:45:56.123456000Z",
        "2024--18T12:45:56.123456000Z",
        "2024-05-T12:45:56.123456000Z",
        "2024-05-18T:45:56.123456000Z",
        "2024-05-18T12::56.123456000Z",
        "2024-05-18T12:45:.123456000Z",

        // Invalid Year
        "2-05-18T12:45:56.123456Z",
        "20-05-18T12:45:56.123456Z",
        "202-05-18T12:45:56.123456Z",
        "20245-05-18T12:45:56.123456Z",
        "02024-05-18T12:45:56.123456Z",
        "ABCD-05-18T12:45:56.123456Z",

        // Invalid Month
        "2024-0-18T12:45:56.123456000Z",
        "2024-012-18T12:45:56.123456000Z",
        "2024-123-18T12:45:56.123456000Z",
        "2024-00-18T12:45:56.123456000Z",
        "2024-13-18T12:45:56.123456000Z",
        "2024-M-18T12:45:56.123456000Z",
        "2024-MA-18T12:45:56.123456000Z",

        // Invalid Day
        "2024-05-0T12:45:56.123456000Z",
        "2024-05-1T12:45:56.123456000Z",
        "2024-05-123T12:45:56.123456000Z",
        "2024-05-00T12:45:56.123456000Z",
        "2024-05-33T12:45:56.123456000Z",
        "2024-05-MT12:45:56.123456000Z",
        "2024-05-MAT12:45:56.123456000Z",

        // Invalid Hour
        // TODO(b/341984878): Uncomment once fixed: "2024-05-18T0:45:56.123456000Z",
        // TODO(b/341984878): Uncomment once fixed: "2024-05-18T1:45:56.123456000Z",
        "2024-05-18T012:45:56.123456000Z",
        "2024-05-18T123:45:56.123456000Z",
        "2024-05-18T24:45:56.123456000Z",
        "2024-05-18TM:45:56.123456000Z",
        "2024-05-18TMA:45:56.123456000Z",
        "2024-05-18TMAT:45:56.123456000Z",

        // Invalid Minute
        "2024-05-18T12:0:56.123456000Z",
        "2024-05-18T12:1:56.123456000Z",
        "2024-05-18T12:012:56.123456000Z",
        "2024-05-18T12:123:56.123456000Z",
        "2024-05-18T12:60:56.123456000Z",
        "2024-05-18T12:M:56.123456000Z",
        "2024-05-18T12:MA:56.123456000Z",
        "2024-05-18T12:MAT:56.123456000Z",

        // Invalid Second
        "2024-05-18T12:45:0.123456000Z",
        "2024-05-18T12:45:1.123456000Z",
        "2024-05-18T12:45:012.123456000Z",
        "2024-05-18T12:45:123.123456000Z",
        "2024-05-18T12:45:60.123456000Z",
        "2024-05-18T12:45:M.123456000Z",
        "2024-05-18T12:45:MA.123456000Z",
        "2024-05-18T12:45:MAT.123456000Z",

        // Invalid Nanosecond
        "2024-05-18T12:45:56.Z",
        // TODO(b/341984878): Uncomment once fixed: "2024-05-18T12:45:56.1234567890Z",
        "2024-05-18T12:45:56.MZ",
        "2024-05-18T12:45:56.MASDMASDMAZ",

        // Invalid Time Zone
        // TODO(b/341984878): Uncomment once fixed: "2024-05-18T12:45:56.123456000-00:00",
        "2024-05-18T12:45:56.123456000ZZ",
        "2024-05-18T12:45:56.123456000-0",
        "2024-05-18T12:45:56.123456000-00",
        "2024-05-18T12:45:56.123456000-:00",
        "2024-05-18T12:45:56.123456000-3:00",
        // TODO(b/341984878): Uncomment once fixed: "2024-05-18T12:45:56.123456000-24:00",
        // TODO(b/341984878): Uncomment once fixed: "2024-05-18T12:45:56.123456000-99:00",
        "2024-05-18T12:45:56.123456000-100:00",
        "2024-05-18T12:45:56.123456000-010:00",
        "2024-05-18T12:45:56.123456000-001:00",
        "2024-05-18T12:45:56.123456000-M:00",
        "2024-05-18T12:45:56.123456000-MA:00",
        "2024-05-18T12:45:56.123456000-MAT:00",
        "2024-05-18T12:45:56.123456000-02:",
        "2024-05-18T12:45:56.123456000-02:0",
        "2024-05-18T12:45:56.123456000-02:1",
        "2024-05-18T12:45:56.123456000-02:010",
        "2024-05-18T12:45:56.123456000-02:123",
        // TODO(b/341984878): Uncomment once fixed: "2024-05-18T12:45:56.123456000-02:60",
        // TODO(b/341984878): Uncomment once fixed: "2024-05-18T12:45:56.123456000-02:99",
        "2024-05-18T12:45:56.123456000-02:M",
        "2024-05-18T12:45:56.123456000-02:MA",
        "2024-05-18T12:45:56.123456000-02:MAT",
        "2024-05-18T12:45:56.123456000+0",
        "2024-05-18T12:45:56.123456000+00",
        "2024-05-18T12:45:56.123456000+:00",
        "2024-05-18T12:45:56.123456000+3:00",
        // TODO(b/341984878): Uncomment once fixed: "2024-05-18T12:45:56.123456000+24:00",
        // TODO(b/341984878): Uncomment once fixed: "2024-05-18T12:45:56.123456000+99:00",
        "2024-05-18T12:45:56.123456000+100:00",
        "2024-05-18T12:45:56.123456000+010:00",
        "2024-05-18T12:45:56.123456000+001:00",
        "2024-05-18T12:45:56.123456000+M:00",
        "2024-05-18T12:45:56.123456000+MA:00",
        "2024-05-18T12:45:56.123456000+MAT:00",
        "2024-05-18T12:45:56.123456000+02:",
        "2024-05-18T12:45:56.123456000+02:0",
        "2024-05-18T12:45:56.123456000+02:1",
        "2024-05-18T12:45:56.123456000+02:010",
        "2024-05-18T12:45:56.123456000+02:123",
        // TODO(b/341984878): Uncomment once fixed: "2024-05-18T12:45:56.123456000+02:60",
        // TODO(b/341984878): Uncomment once fixed: "2024-05-18T12:45:56.123456000+02:99",
        "2024-05-18T12:45:56.123456000+02:M",
        "2024-05-18T12:45:56.123456000+02:MA",
        "2024-05-18T12:45:56.123456000+02:MAT",

        // Bogus Characters
        "a2024-05-18T12:45:56.123456789Z",
        "2024-05-18T12:45:56.123456789Za",
        "2024:05-18T12:45:56.123456789Z",
        "2024-05:18T12:45:56.123456789Z",
        "2024-05-18 12:45:56.123456789Z",
        "2024-05-18T12-45:56.123456789Z",
        "2024-05-18T12:45-56.123456789Z",
        "2024-05-18T12:45:56-123456789Z",

        // Out-of-range Values
        "0000-01-01T12:45:56Z",
        "2024-00-22T12:45:56Z",
        "2024-13-22T12:45:56Z",
        "2024-11-00T12:45:56Z",
        "2024-01-32T12:45:56Z",
        "2025-02-29T12:45:56Z",
        "2024-02-30T12:45:56Z",
        "2024-03-32T12:45:56Z",
        "2024-04-31T12:45:56Z",
        "2024-05-32T12:45:56Z",
        "2024-06-31T12:45:56Z",
        "2024-07-32T12:45:56Z",
        "2024-08-32T12:45:56Z",
        "2024-09-31T12:45:56Z",
        "2024-10-32T12:45:56Z",
        "2024-11-31T12:45:56Z",
        "2024-12-32T12:45:56Z",

        // Test cases from https://scalars.graphql.org/andimarek/date-time (some omitted since they
        // are indeed valid for Firebase Data Connect)
        "2011-08-30T13:22:53.108-03", // The minutes of the offset are missing.
        "2011-08-30T13:22:53.108", // No offset provided.
        "2011-08-30", // No time provided.
        "2011-08-30T13:22:53.108+03:30:15", // Seconds are not allowed for the offset
        "2011-08-30T24:22:53.108Z", // 24 is not allowed as hour of the time.
        "2010-02-30T21:22:53.108Z", // 30th of February is not a valid date
        "2010-02-11T21:22:53.108Z+25:11", // 25 is not a valid hour for offset
      )

    // TODO(b/341984878): Remove elements from this list as they are fixed, and uncomment them
    //  in the list above.
    val invalidTimestampsThatAreErroneouslyAcceptedByTheServer =
      listOf(
        "2024-05-18T0:45:56.123456000Z",
        "2024-05-18T1:45:56.123456000Z",
        "2024-05-18T12:45:56.1234567890Z",
        "2024-05-18T12:45:56.123456000-00:00",
        "2024-05-18T12:45:56.123456000-24:00",
        "2024-05-18T12:45:56.123456000-99:00",
        "2024-05-18T12:45:56.123456000+24:00",
        "2024-05-18T12:45:56.123456000+99:00",
        "2024-05-18T12:45:56.123456000-02:60",
        "2024-05-18T12:45:56.123456000-02:99",
        "2024-05-18T12:45:56.123456000+02:60",
        "2024-05-18T12:45:56.123456000+02:99",
      )
  }
}
