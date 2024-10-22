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

private fun timestampStringFromComponents(
  year: Int,
  month: Int,
  day: Int,
  t: Char,
  hour: Int,
  minute: Int,
  second: Int,
  nanosecond: Int,
  nanosecondNumDigits: Int?,
  z: Char,
): String {
  val yearStr = "$year"
  val monthStr = "$month".padStart(2, '0')
  val dayStr = "$day".padStart(2, '0')
  val hourStr = "$hour".padStart(2, '0')
  val minuteStr = "$minute".padStart(2, '0')
  val secondStr = "$second".padStart(2, '0')

  val nanosecondStrOrNull =
    if (nanosecondNumDigits === null) {
      check(nanosecond == 0) {
        "nanosecond must be zero when nanosecondNumDigits === null, but was: $nanosecond"
      }
      null
    } else {
      "$nanosecond".padStart(9, '0').substring(0 until nanosecondNumDigits)
    }
  val nanosecondStr = if (nanosecondStrOrNull === null) "" else ".$nanosecondStrOrNull"

  return "$yearStr-$monthStr-$dayStr$t$hourStr:$minuteStr:$secondStr$nanosecondStr$z"
}

private fun roundTripRegexFromTimestampComponents(
  year: Int,
  month: Int,
  day: Int,
  hour: Int,
  minute: Int,
  second: Int,
  nanosecond: Int,
  nanosecondNumSignificantDigits: Int,
) =
  Regex(
    buildString {
      append("0*")
      append(year)
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

      if (nanosecond == 0) {
        append("(\\.0+)?")
      } else {
        append("\\.")
        append(
          "$nanosecond"
            .padStart(9, '0')
            .substring(0 until nanosecondNumSignificantDigits)
            .trimEnd('0')
        )
        append("0*")
      }

      append("[zZ]")
    }
  )

private fun Int.withNumDigits(numDigits: Int): Int {
  require(numDigits in 0..9) { "invalid numDigits: $numDigits" }
  return if (numDigits == 0) {
    0
  } else {
    "${this}000000000".substring(0, numDigits).toInt()
  }
}

fun DataConnectArb.timestamp(): Arb<TimestampTestData> {
  val dateArb: Arb<DateTestData> = date()
  val hourArb: Arb<Int> = hour()
  val minuteArb: Arb<Int> = minute()
  val secondArb: Arb<Int> = second()
  val nanosecondNumDigitsArb = Arb.of(-1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
  val nanosecondArb = nanosecond()
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

    val nanosecondNumDigits = nanosecondNumDigitsArb.bind()
    val nanosecond =
      if (nanosecondNumDigits == -1) {
        0
      } else {
        nanosecondArb.bind().withNumDigits(nanosecondNumDigits)
      }

    TimestampTestData(
      timestamp = timestampFromUTCDateAndTime(year, month, day, hour, minute, second, nanosecond),
      string =
        timestampStringFromComponents(
          year = year,
          month = month,
          day = day,
          t = t,
          hour = hour,
          minute = minute,
          second = second,
          nanosecond = nanosecond,
          nanosecondNumDigits = if (nanosecondNumDigits == -1) null else nanosecondNumDigits,
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
          nanosecond = nanosecond,
          nanosecondNumSignificantDigits = 6,
        )
    )
  }
}

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
          nanosecond = 0,
          nanosecondNumSignificantDigits = 6,
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
          nanosecond = 999_999_999,
          nanosecondNumSignificantDigits = 6,
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
          nanosecond = 0,
          nanosecondNumSignificantDigits = 6,
        ),
      )

  val singleDigits: TimestampTestData
    get() =
      TimestampTestData(
        timestampFromUTCDateAndTime(
          year = 1,
          month = 2,
          day = 3,
          hour = 4,
          minute = 5,
          second = 6,
          nanoseconds = 700_000_000,
        ),
        "indeterminate 7mmpdgy9g3",
        roundTripRegexFromTimestampComponents(
          year = 1,
          month = 2,
          day = 3,
          hour = 4,
          minute = 5,
          second = 6,
          nanosecond = 700_000_000,
          nanosecondNumSignificantDigits = 6,
        ),
      )

  val allDigits: TimestampTestData
    get() =
      TimestampTestData(
        timestampFromUTCDateAndTime(
          year = 1234,
          month = 12,
          day = 15,
          hour = 21,
          minute = 35,
          second = 59,
          nanoseconds = 123_456_789,
        ),
        "indeterminate ym567evx8g",
        roundTripRegexFromTimestampComponents(
          year = 1234,
          month = 12,
          day = 15,
          hour = 21,
          minute = 35,
          second = 59,
          nanosecond = 123_456_789,
          nanosecondNumSignificantDigits = 6,
        ),
      )

  val all: List<TimestampTestData> = listOf(min, max, zero, singleDigits, allDigits)
}

private fun hour(): Arb<Int> = Arb.int(0..23)

private fun minute(): Arb<Int> = Arb.int(0..59)

private fun second(): Arb<Int> = Arb.int(0..59)

private fun nanosecond(): Arb<Int> = Arb.int(0..999_999_999)
