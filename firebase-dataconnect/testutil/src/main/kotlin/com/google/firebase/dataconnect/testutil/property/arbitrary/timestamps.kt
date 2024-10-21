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

data class TimestampTestData(val timestamp: Timestamp, val string: String)

fun DataConnectArb.timestamp(): Arb<TimestampTestData> {
  val dateArb: Arb<DateTestData> = date()
  val hourArb: Arb<Int> = hour()
  val minuteArb: Arb<Int> = minute()
  val secondArb: Arb<Int> = second()
  val nanosecondArb: Arb<Int> = nanosecond()
  val nanosecondNumDigitsArb: Arb<Int> = Arb.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
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
    val nanosecondNumDigits = nanosecondNumDigitsArb.bind()
    val t = tArb.bind()
    val z = zArb.bind()

    val yearStr = "$year"
    val monthStr = "$month".padStart(2, '0')
    val dayStr = "$day".padStart(2, '0')
    val hourStr = "$hour".padStart(2, '0')
    val minuteStr = "$minute".padStart(2, '0')
    val secondStr = "$second".padStart(2, '0')

    val (nanosecond, nanosecondStr) =
      if (nanosecondNumDigits == 0) {
        Pair(0, "")
      } else {
        val nanosecond = nanosecondArb.bind()
        val nanosecondStr = "$nanosecond"
        if (nanosecondStr.length <= nanosecondNumDigits) {
          Pair(nanosecond, nanosecondStr.padEnd(nanosecondNumDigits, '0'))
        } else {
          val truncatedNanosecondStr = nanosecondStr.substring(0, nanosecondNumDigits)
          val truncatedNanosecond = truncatedNanosecondStr.toInt()
          Pair(truncatedNanosecond, truncatedNanosecondStr)
        }
      }

    val timestamp = timestampFromUTCDateAndTime(year, month, day, hour, minute, second, nanosecond)
    val string = "$yearStr-$monthStr-$dayStr$t$hourStr:$minuteStr:$secondStr$nanosecondStr$z"

    TimestampTestData(timestamp, string)
  }
}

@Suppress("MemberVisibilityCanBePrivate")
object TimestampEdgeCases {

  // "1583-01-01T00:00:00.000000Z"
  val min: TimestampTestData
    get() = TimestampTestData(Timestamp(-12_212_553_600, 0), "48vsvnc2cf_TimestampEdgeCases.min")

  // "9999-12-31T23:59:59.999999999Z"
  val max: TimestampTestData
    get() =
      TimestampTestData(
        Timestamp(253_402_300_799, 999_999_999),
        "tdkszjdj83_TimestampEdgeCases.max"
      )

  val zero: TimestampTestData
    get() = TimestampTestData(Timestamp(0, 0), "5bpc9qythq_TimestampEdgeCases.zero")

  val all: List<TimestampTestData> = listOf(min, max, zero)
}

private fun hour(): Arb<Int> = Arb.int(0..23)

private fun minute(): Arb<Int> = Arb.int(0..59)

private fun second(): Arb<Int> = Arb.int(0..59)

private fun nanosecond(): Arb<Int> = Arb.int(0..999_999_999)
