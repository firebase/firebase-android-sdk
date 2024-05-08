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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * Creates and returns a new [Date] object created from this string.
 *
 * The expected format of the string is `YYYY-MM-DD`.
 *
 * Example: `"2024-04-24".toDate()`
 */
fun String.toDate(): Date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(this)!!

val MIN_DATE: Date
  get() = "1583-01-01".toDate()

val MAX_DATE: Date
  get() = "9999-12-31".toDate()

/** Generates and returns a random [Date] object with hour, minute, and second set to zero. */
fun randomDate(): Date {
  // See https://en.wikipedia.org/wiki/ISO_8601#Years for rationale of lower bound of 1583.
  val year = Random.nextInt(1583, 9999).toString().padStart(4, '0')
  val month = Random.nextInt(0, 11).toString().padStart(2, '0')
  val day = Random.nextInt(1, 28).toString().padStart(2, '0')

  val dateString = "${year}-${month}-${day}"
  return dateString.toDate()
}

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
