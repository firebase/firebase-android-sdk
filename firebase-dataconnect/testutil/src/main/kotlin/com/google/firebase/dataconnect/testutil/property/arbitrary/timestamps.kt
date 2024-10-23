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

package com.google.firebase.dataconnect.testutil.property.arbitrary

import com.google.firebase.Timestamp
import com.google.firebase.dataconnect.testutil.property.arbitrary.DateEdgeCases.MIN_YEAR
import com.google.firebase.dataconnect.testutil.timestampFromUTCDateAndTime
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.of

data class TimestampTestData(
  val timestamp: Timestamp,
  val string: String,
  val roundTripRegex: Regex,
)

fun DataConnectArb.timestamp(): Arb<TimestampTestData> {
  val dateArb: Arb<DateTestData> = date()
  val hourArb: Arb<Int> = hour()
  val minuteArb: Arb<Int> = minute()
  val secondArb: Arb<Int> = second()
  val nanosecondsNumDigitsArb = Arb.of(-1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
  val nanosecondsArb = nanosecond()
  val tArb: Arb<Char> = Arb.of('t', 'T')
  val zArb: Arb<Char> = Arb.of('z', 'Z')

  return arbitrary(edgecases = TimestampEdgeCases.all) {
    val date = dateArb.bind()
    val year = date.year
    val month = date.month
    val day = date.day
    val hour = hourArb.bind()
    val minute = minuteArb.bind()
    val second = secondArb.bind()
    val t = tArb.bind()
    val z = zArb.bind()

    val nanosecondsNumDigits = nanosecondsNumDigitsArb.bind()
    val nanoseconds =
      if (nanosecondsNumDigits == -1) {
        0
      } else {
        nanosecondsArb.bind().withNumDigits(nanosecondsNumDigits)
      }

    TimestampTestData(
      timestamp = timestampFromUTCDateAndTime(year, month, day, hour, minute, second, nanoseconds),
      string =
        timestampStringFromComponents(
          year = year,
          month = month,
          day = day,
          t = t,
          hour = hour,
          minute = minute,
          second = second,
          nanoseconds = nanoseconds,
          nanosecondsNumDigits = if (nanosecondsNumDigits == -1) null else nanosecondsNumDigits,
          z = z,
        ),
      roundTripRegex =
        roundTripRegexFromTimestampComponents(
          year = year,
          month = month,
          day = day,
          hour = hour,
          minute = minute,
          second = second,
          nanoseconds = nanoseconds,
        )
    )
  }
}

private fun timestampStringFromComponents(
  year: Int,
  month: Int,
  day: Int,
  t: Char,
  hour: Int,
  minute: Int,
  second: Int,
  nanoseconds: Int,
  nanosecondsNumDigits: Int?,
  z: Char,
): String {
  val yearStr = "$year"
  val monthStr = "$month".padStart(2, '0')
  val dayStr = "$day".padStart(2, '0')
  val hourStr = "$hour".padStart(2, '0')
  val minuteStr = "$minute".padStart(2, '0')
  val secondStr = "$second".padStart(2, '0')

  val nanosecondsStrOrNull =
    if (nanosecondsNumDigits === null) {
      check(nanoseconds == 0) {
        "nanoseconds must be zero when nanosecondsNumDigits === null, but was: $nanoseconds"
      }
      null
    } else {
      "$nanoseconds".padStart(9, '0').substring(0 until nanosecondsNumDigits)
    }
  val nanosecondStr = if (nanosecondsStrOrNull === null) "" else ".$nanosecondsStrOrNull"

  return "$yearStr-$monthStr-$dayStr$t$hourStr:$minuteStr:$secondStr$nanosecondStr$z"
}

/**
 * Creates and returns a [Regex] object that should match the string sent back from the Data Connect
 * `executeQuery` operation for a field whose value is a timestamp with the given values. All
 * arguments must conform to their corresponding restrictions in RFC 3339 "Date and Time on the
 * Internet: Timestamps" https://datatracker.ietf.org/doc/html/rfc3339.
 */
private fun roundTripRegexFromTimestampComponents(
  year: Int,
  month: Int,
  day: Int,
  hour: Int,
  minute: Int,
  second: Int,
  nanoseconds: Int
) =
  Regex(
    buildString {
      append("$year".padStart(4, '0'))
      append('-')
      append("$month".padStart(2, '0'))
      append('-')
      append("$day".padStart(2, '0'))
      append("[tT]")
      append("$hour".padStart(2, '0'))
      append(':')
      append("$minute".padStart(2, '0'))
      append(':')
      append("$second".padStart(2, '0'))

      if (nanoseconds == 0) {
        append("(\\.0+)?")
      } else {
        append("\\.")
        append("$nanoseconds".padStart(9, '0').substring(0, 6).trimEnd('0'))
        append("0*")
      }

      append("[zZ]")
    }
  )

private fun Int.withNumDigits(numDigits: Int): Int {
  require(numDigits in 0..9) { "invalid numDigits: $numDigits" }
  if (numDigits == 0) {
    return 0
  }

  val s = buildString {
    append(this@withNumDigits)
    while (length < numDigits) {
      append('0')
    }
  }

  return s.substring(0, numDigits).toInt()
}

private fun hour(): Arb<Int> = Arb.int(0..23)

private fun minute(): Arb<Int> = Arb.int(0..59)

private fun second(): Arb<Int> = Arb.int(0..59)

private fun nanosecond(): Arb<Int> = Arb.int(0..999_999_999)

@Suppress("MemberVisibilityCanBePrivate")
object TimestampEdgeCases {

  val min: TimestampTestData
    get() =
      TimestampTestData(
        Timestamp(-12_212_553_600, 0),
        "1583-01-01T00:00:00.000000000Z",
        roundTripRegexFromTimestampComponents(
          year = 1583,
          month = 1,
          day = 1,
          hour = 0,
          minute = 0,
          second = 0,
          nanoseconds = 0,
        ),
      )

  val max: TimestampTestData
    get() =
      TimestampTestData(
        Timestamp(253_402_300_799, 999_999_999),
        "9999-12-31T23:59:59.999999999Z",
        roundTripRegexFromTimestampComponents(
          year = 9999,
          month = 12,
          day = 31,
          hour = 23,
          minute = 59,
          second = 59,
          nanoseconds = 999_999_999,
        ),
      )

  val zero: TimestampTestData
    get() =
      TimestampTestData(
        Timestamp(0, 0),
        "1970-01-01T00:00:00.000000000Z",
        roundTripRegexFromTimestampComponents(
          year = 1970,
          month = 1,
          day = 1,
          hour = 0,
          minute = 0,
          second = 0,
          nanoseconds = 0,
        ),
      )

  val singleDigits: TimestampTestData
    get() =
      TimestampTestData(
        timestampFromUTCDateAndTime(
          year = MIN_YEAR,
          month = 2,
          day = 3,
          hour = 4,
          minute = 5,
          second = 6,
          nanoseconds = 700_000_000,
        ),
        "$MIN_YEAR-02-03T04:05:06.7Z",
        roundTripRegexFromTimestampComponents(
          year = MIN_YEAR,
          month = 2,
          day = 3,
          hour = 4,
          minute = 5,
          second = 6,
          nanoseconds = 700_000_000,
        ),
      )

  val allDigits: TimestampTestData
    get() =
      TimestampTestData(
        timestampFromUTCDateAndTime(
          year = MIN_YEAR,
          month = 12,
          day = 15,
          hour = 12,
          minute = 35,
          second = 44,
          nanoseconds = 123_456_789,
        ),
        "$MIN_YEAR-12-15T12:35:44.123456789Z",
        roundTripRegexFromTimestampComponents(
          year = MIN_YEAR,
          month = 12,
          day = 15,
          hour = 12,
          minute = 35,
          second = 44,
          nanoseconds = 123_456_789,
        ),
      )

  val nanosecondsAbsent: TimestampTestData
    get() =
      TimestampTestData(
        timestampFromUTCDateAndTime(
          year = 7608,
          month = 11,
          day = 21,
          hour = 2,
          minute = 45,
          second = 8,
          nanoseconds = 0,
        ),
        "7608-11-21T02:45:08Z",
        roundTripRegexFromTimestampComponents(
          year = 7608,
          month = 11,
          day = 21,
          hour = 2,
          minute = 45,
          second = 8,
          nanoseconds = 0,
        ),
      )

  val nanoseconds1Digit: TimestampTestData
    get() =
      TimestampTestData(
        timestampFromUTCDateAndTime(
          year = 7608,
          month = 11,
          day = 21,
          hour = 2,
          minute = 45,
          second = 8,
          nanoseconds = 1,
        ),
        "7608-11-21T02:45:08.000000001Z",
        roundTripRegexFromTimestampComponents(
          year = 7608,
          month = 11,
          day = 21,
          hour = 2,
          minute = 45,
          second = 8,
          nanoseconds = 1,
        ),
      )

  val nanoseconds2Digits: TimestampTestData
    get() =
      TimestampTestData(
        timestampFromUTCDateAndTime(
          year = 7608,
          month = 11,
          day = 21,
          hour = 2,
          minute = 45,
          second = 8,
          nanoseconds = 12,
        ),
        "7608-11-21T02:45:08.000000012Z",
        roundTripRegexFromTimestampComponents(
          year = 7608,
          month = 11,
          day = 21,
          hour = 2,
          minute = 45,
          second = 8,
          nanoseconds = 12,
        ),
      )

  val nanoseconds3Digits: TimestampTestData
    get() =
      TimestampTestData(
        timestampFromUTCDateAndTime(
          year = 7608,
          month = 11,
          day = 21,
          hour = 2,
          minute = 45,
          second = 8,
          nanoseconds = 123,
        ),
        "7608-11-21T02:45:08.000000123Z",
        roundTripRegexFromTimestampComponents(
          year = 7608,
          month = 11,
          day = 21,
          hour = 2,
          minute = 45,
          second = 8,
          nanoseconds = 123,
        ),
      )

  val nanoseconds4Digits: TimestampTestData
    get() =
      TimestampTestData(
        timestampFromUTCDateAndTime(
          year = 7608,
          month = 11,
          day = 21,
          hour = 2,
          minute = 45,
          second = 8,
          nanoseconds = 1234,
        ),
        "7608-11-21T02:45:08.000001234Z",
        roundTripRegexFromTimestampComponents(
          year = 7608,
          month = 11,
          day = 21,
          hour = 2,
          minute = 45,
          second = 8,
          nanoseconds = 1234,
        ),
      )

  val nanoseconds5Digits: TimestampTestData
    get() =
      TimestampTestData(
        timestampFromUTCDateAndTime(
          year = 7608,
          month = 11,
          day = 21,
          hour = 2,
          minute = 45,
          second = 8,
          nanoseconds = 12345,
        ),
        "7608-11-21T02:45:08.000012345Z",
        roundTripRegexFromTimestampComponents(
          year = 7608,
          month = 11,
          day = 21,
          hour = 2,
          minute = 45,
          second = 8,
          nanoseconds = 12345,
        ),
      )

  val nanoseconds6Digits: TimestampTestData
    get() =
      TimestampTestData(
        timestampFromUTCDateAndTime(
          year = 7608,
          month = 11,
          day = 21,
          hour = 2,
          minute = 45,
          second = 8,
          nanoseconds = 123456,
        ),
        "7608-11-21T02:45:08.000123456Z",
        roundTripRegexFromTimestampComponents(
          year = 7608,
          month = 11,
          day = 21,
          hour = 2,
          minute = 45,
          second = 8,
          nanoseconds = 123456,
        ),
      )

  val nanoseconds7Digits: TimestampTestData
    get() =
      TimestampTestData(
        timestampFromUTCDateAndTime(
          year = 7608,
          month = 11,
          day = 21,
          hour = 2,
          minute = 45,
          second = 8,
          nanoseconds = 1234567,
        ),
        "7608-11-21T02:45:08.001234567Z",
        roundTripRegexFromTimestampComponents(
          year = 7608,
          month = 11,
          day = 21,
          hour = 2,
          minute = 45,
          second = 8,
          nanoseconds = 1234567,
        ),
      )

  val nanoseconds8Digits: TimestampTestData
    get() =
      TimestampTestData(
        timestampFromUTCDateAndTime(
          year = 7608,
          month = 11,
          day = 21,
          hour = 2,
          minute = 45,
          second = 8,
          nanoseconds = 12345678,
        ),
        "7608-11-21T02:45:08.012345678Z",
        roundTripRegexFromTimestampComponents(
          year = 7608,
          month = 11,
          day = 21,
          hour = 2,
          minute = 45,
          second = 8,
          nanoseconds = 12345678,
        ),
      )

  val nanoseconds9Digits: TimestampTestData
    get() =
      TimestampTestData(
        timestampFromUTCDateAndTime(
          year = 7608,
          month = 11,
          day = 21,
          hour = 2,
          minute = 45,
          second = 8,
          nanoseconds = 123456789,
        ),
        "7608-11-21T02:45:08.123456789Z",
        roundTripRegexFromTimestampComponents(
          year = 7608,
          month = 11,
          day = 21,
          hour = 2,
          minute = 45,
          second = 8,
          nanoseconds = 123456789,
        ),
      )

  val all: List<TimestampTestData>
    get() =
      listOf(
        min,
        max,
        zero,
        singleDigits,
        allDigits,
        nanosecondsAbsent,
        nanoseconds1Digit,
        nanoseconds2Digits,
        nanoseconds3Digits,
        nanoseconds4Digits,
        nanoseconds5Digits,
        nanoseconds6Digits,
        nanoseconds7Digits,
        nanoseconds8Digits,
        nanoseconds9Digits,
      )
}
