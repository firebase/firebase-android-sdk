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
import java.util.GregorianCalendar
import java.util.TimeZone
import kotlin.random.Random

/** Generates and returns a random [Timestamp] object. */
fun randomTimestamp(): Timestamp {
  val nanoseconds = Random.nextInt(1_000_000_000)
  val seconds = Random.nextLong(MIN_TIMESTAMP.seconds, MAX_TIMESTAMP.seconds)
  return Timestamp(seconds, nanoseconds)
}

fun Timestamp.withMicrosecondPrecision(): Timestamp {
  val result = Timestamp(seconds, ((nanoseconds.toLong() / 1_000) * 1_000).toInt())
  return result
}

// "1583-01-01T00:00:00.000000Z"
val MIN_TIMESTAMP
  get() = Timestamp(-12_212_553_600, 0)

// "9999-12-31T23:59:59.999999999Z"
val MAX_TIMESTAMP
  get() = Timestamp(253_402_300_799, 999_999_999)

val ZERO_TIMESTAMP: Timestamp
  get() = Timestamp(0, 0)

/**
 * Creates and returns a new [Timestamp] object that represents the given date and time.
 *
 * @param year The year; must be between 0 and 9999, inclusive.
 * @param month The month; must be between 1 and 12, inclusive.
 * @param day The day of the month; must be between 1 and 31, inclusive.
 */
fun timestampFromUTCDateAndTime(
  year: Int,
  month: Int,
  day: Int,
  hour: Int,
  minute: Int,
  second: Int,
  nanoseconds: Int
): Timestamp {
  require(year in 0..9999) { "year must be between 0 and 9999, inclusive" }
  require(month in 1..12) { "month must be between 1 and 12, inclusive" }
  require(day in 1..31) { "day must be between 1 and 31, inclusive" }
  require(hour in 0..24) { "hour must be between 0 and 24, inclusive" }
  require(minute in 0..59) { "minute must be between 0 and 59, inclusive" }
  require(second in 0..60) { "second must be between 0 and 60, inclusive" }
  require(nanoseconds in 0..999_999_999) {
    "nanoseconds must be between 0 and 999,999,999, inclusive"
  }

  val seconds =
    GregorianCalendar(TimeZone.getTimeZone("UTC"))
      .apply {
        set(year, month - 1, day, hour, minute, second)
        set(Calendar.MILLISECOND, 0)
      }
      .timeInMillis / 1000

  return Timestamp(seconds, nanoseconds)
}
