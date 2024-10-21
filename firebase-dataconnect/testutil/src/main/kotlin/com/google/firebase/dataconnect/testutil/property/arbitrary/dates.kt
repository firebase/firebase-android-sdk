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

@file:Suppress("UnusedReceiverParameter")

package com.google.firebase.dataconnect.testutil.property.arbitrary

import com.google.firebase.dataconnect.testutil.dateFromYearMonthDayUTC
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import java.util.Date
import java.util.GregorianCalendar
import java.util.TimeZone

data class DateTestData(
  val date: Date,
  val string: String,
  val year: Int,
  val month: Int,
  val day: Int
) {
  fun withMillisOffset(millisOffset: Long): DateTestData =
    copy(date = Date(date.time + millisOffset))
}

fun DataConnectArb.date(): Arb<DateTestData> =
  arbitrary(edgecases = DateEdgeCases.all) {
    val year = year().bind()
    val month = month().bind()
    val day = day(month).bind()

    val date = dateFromYearMonthDayUTC(year, month, day)

    val yearStr = "$year"
    val monthStr = "$month".padStart(2, '0')
    val dayStr = "$day".padStart(2, '0')
    val string = "$yearStr-$monthStr-$dayStr"

    DateTestData(date, string, year = year, month = month, day = day)
  }

fun DataConnectArb.dateOffDayBoundary(): Arb<DateTestData> =
  arbitrary(edgecases = DateEdgeCases.offDayBoundary) {
    // Skip dates with the maximum year, as adding non-zero milliseconds will result in the year
    // 10,000, which is invalid.
    val dateAndStrings = date().filterNot { it.string.contains("9999") }
    // Don't add more than 86_400_000L, the number of milliseconds per day, to the date.
    val millisOffsets = Arb.long(0L until 86_400_000L)

    val dateAndString = dateAndStrings.bind()
    val millisOffset = millisOffsets.bind()
    val dateOffDayBoundary = Date(dateAndString.date.time + millisOffset)

    DateTestData(
      dateOffDayBoundary,
      dateAndString.string,
      year = dateAndString.year,
      month = dateAndString.month,
      day = dateAndString.day,
    )
  }

@Suppress("MemberVisibilityCanBePrivate")
object DateEdgeCases {
  // See https://en.wikipedia.org/wiki/ISO_8601#Years for rationale of lower bound of 1583.
  const val MIN_YEAR = 1583

  const val MAX_YEAR = 9999

  val min: DateTestData
    get() =
      DateTestData(
        date = dateFromYearMonthDayUTC(MIN_YEAR, 1, 1),
        string = "$MIN_YEAR-01-01",
        year = MIN_YEAR,
        month = 1,
        day = 1,
      )

  val max: DateTestData
    get() =
      DateTestData(
        date = dateFromYearMonthDayUTC(MAX_YEAR, 12, 31),
        string = "$MAX_YEAR-12-31",
        year = MAX_YEAR,
        month = 12,
        day = 31,
      )

  val zero: DateTestData
    get() =
      DateTestData(
        date = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply { timeInMillis = 0 }.time,
        string = "1970-01-01",
        year = 1970,
        month = 1,
        day = 1,
      )

  val all: List<DateTestData> = listOf(min, max, zero)

  val offDayBoundary: List<DateTestData> =
    listOf(
      min.withMillisOffset(1),
      max.withMillisOffset(1),
      zero.withMillisOffset(1),
    )
}

private fun maxDayForMonth(month: Int): Int {
  return when (month) {
    1 -> 31
    2 -> 28
    3 -> 31
    4 -> 30
    5 -> 31
    6 -> 30
    7 -> 31
    8 -> 31
    9 -> 30
    10 -> 31
    11 -> 30
    12 -> 31
    else ->
      throw IllegalArgumentException("invalid month: $month (must be between 1 and 12, inclusive)")
  }
}

private fun year(): Arb<Int> = Arb.int(DateEdgeCases.MIN_YEAR..DateEdgeCases.MAX_YEAR)

private fun month(): Arb<Int> = Arb.int(1..12)

private fun day(month: Int): Arb<Int> = Arb.int(1..maxDayForMonth(month))
