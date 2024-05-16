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

package com.google.firebase.dataconnect.testutil

import com.google.firebase.Timestamp
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.TimeZone
import kotlin.random.Random
import kotlin.random.nextInt

/**
 * Creates and returns a new [Date] object that represents the given year, month, and day in UTC.
 *
 * @param year The year; must be between 0 and 9999, inclusive.
 * @param month The month; must be between 1 and 12, inclusive.
 * @param day The day of the month; must be between 1 and 31, inclusive.
 */
fun dateFromYearMonthDayUTC(year: Int, month: Int, day: Int): Date {
  require(year in 0..9999) { "year must be between 0 and 9999, inclusive" }
  require(month in 1..12) { "month must be between 1 and 12, inclusive" }
  require(day in 1..31) { "day must be between 1 and 31, inclusive" }

  return GregorianCalendar(TimeZone.getTimeZone("UTC"))
    .apply {
      set(year, month - 1, day, 0, 0, 0)
      set(Calendar.MILLISECOND, 0)
    }
    .time
}

val MIN_DATE: Date
  get() = dateFromYearMonthDayUTC(1583, 1, 1)

val MAX_DATE: Date
  get() = dateFromYearMonthDayUTC(9999, 12, 31)

/**
 * Generates and returns a random [Date] object with hour, minute, and second set to zero.
 *
 * @see https://en.wikipedia.org/wiki/ISO_8601#Years for rationale of lower bound of 1583.
 */
fun randomDate(): Date =
  dateFromYearMonthDayUTC(
    year = Random.nextInt(1583..9999),
    month = Random.nextInt(1..12),
    day = Random.nextInt(1..28)
  )

/** Generates and returns a random [Timestamp] object. */
fun randomTimestamp(): Timestamp {
  val nanoseconds = Random.nextInt(1_000_000_000)
  val seconds = Random.nextLong(Timestamp.MIN_VALUE.seconds, Timestamp.MAX_VALUE.seconds)
  return Timestamp(seconds, nanoseconds)
}

fun Timestamp.withMicrosecondPrecision(): Timestamp {
  val result = Timestamp(seconds, ((nanoseconds.toLong() / 1_000) * 1_000).toInt())
  return result
}

// "1583-01-01T00:00:00.000000Z"
val Timestamp.Companion.MIN_VALUE
  get() = Timestamp(-12_212_553_600, 0)

// "9999-12-31T23:59:59.999999999Z"
val Timestamp.Companion.MAX_VALUE
  get() = Timestamp(253_402_300_799, 999_999_999)
