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
import com.google.firebase.dataconnect.testutil.withMicrosecondPrecision
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.of
import kotlin.reflect.KClass

/** Information for a test case of a timestamp with Firebase Data Connect. */
data class TimestampTestData(
  /** A display name for this test case. */
  val name: String,

  /** The timestamp under test, with full (nanosecond) precision. */
  val timestamp: Timestamp,

  /**
   * An RFC3339 string representation of [timestamp].
   *
   * Any given timestamp can have more than one equivalent representation in RFC3339, and this is
   * but one such representation, chosen arbitrarily. For example, "2024-01-01T12:34:56.123Z" can
   * equivalently be represented as "2024-01-01T12:34:56.123000Z" (extra zeroes in the nanosecond).
   */
  val string: String,

  /**
   * The same string as [string] but (possibly) modified in such a way that it will be accepted when
   * sent as a variable in an `executeQuery` or `executeMutation` operation of Firebase Data
   * Connect.
   *
   * For example, the RFC3339 standard allows the "T" to be lowercase (ie. "t"), but Data Connect
   * fails to parse the lowercase "T"; therefore, this string will always have an uppercase "T".
   */
  val fdcStringVariable: String,

  /**
   * A regular expression that matches all valid RFC3339 string representations of [timestamp] as
   * would be returned in a field from `executeQuery` and `executeMutation` operations of Firebase
   * Data Connect.
   *
   * Notably, since Data Connect only supports microsecond precision, this regular expression
   * effectively converts the 3 nanosecond digits to 0. For example, for the timestamp
   * "2024-01-01T12:34:56.123456789Z" this regular expression would match
   * "2024-01-01T12:34:56.123456Z" or "2024-01-01T12:34:56.123456789000Z", but _not_ the string with
   * full nanosecond precision.
   */
  val fdcFieldRegex: Regex,

  /**
   * The same as [timestamp] but (possibly) modified to match a timestamp as would be returned in a
   * field of `executeQuery` and `executeMutation` operations of Firebase Data Connect.
   *
   * For example, nanoseconds will be truncated to microseconds because Data Connect only supports
   * microsecond precision.
   */
  val fdcFieldTimestamp: Timestamp,

  /** The individual components of this timestamp, such as year, month, hour, etc. */
  val components: TimestampComponents,
) {
  companion object
}

/**
 * The components that make up an RFC3339 timestamp.
 *
 * Each property's value must be in the valid range as specified by RFC3339; however, this is not
 * validated.
 */
data class TimestampComponents(
  val year: Int,
  val month: Int,
  val day: Int,
  val hour: Int,
  val minute: Int,
  val second: Int,
  val nanoseconds: Int,
  val nanosecondsNumDigits: Int,
  val timeZoneOffset: TimeZoneOffset,
  val t: Char,
) {
  init {
    require(nanosecondsNumDigits != 0 || nanoseconds == 0) {
      "nanoseconds must be zero when nanosecondsNumDigits==0, but nanoseconds=$nanoseconds"
    }
  }
}

sealed interface TimeZoneOffset {

  val offsetSeconds: Int
  val rfc3339String: String

  data class Z(val case: Case) : TimeZoneOffset {
    override val offsetSeconds = 0

    override val rfc3339String =
      when (case) {
        Case.Uppercase -> "Z"
        Case.Lowercase -> "z"
      }

    enum class Case {
      Uppercase,
      Lowercase,
    }
  }

  data class Offset(val hours: Int, val minutes: Int, val sign: Sign) : TimeZoneOffset {
    init {
      require(hours >= 0) { "hours must be non-negative, but got: $hours" }
      require(minutes >= 0) { "minutes must be non-negative, but got: $minutes" }
    }

    override val offsetSeconds = run {
      val multiplier =
        when (sign) {
          Sign.Positive -> 1
          Sign.Negative -> -1
        }
      multiplier * 60 * ((hours * 60) + minutes)
    }

    override val rfc3339String = buildString {
      append(
        when (sign) {
          Sign.Positive -> '+'
          Sign.Negative -> '-'
        }
      )
      append("$hours".padStart(2, '0'))
      append(':')
      append("$minutes".padStart(2, '0'))
    }

    enum class Sign {
      Positive,
      Negative,
    }
  }
}

@Suppress("UnusedReceiverParameter")
fun DataConnectArb.timeZoneOffset(
  type: Arb<KClass<out TimeZoneOffset>> =
    Arb.of(TimeZoneOffset.Z::class, TimeZoneOffset.Offset::class),
  case: Arb<TimeZoneOffset.Z.Case> = Arb.enum(),
  sign: Arb<TimeZoneOffset.Offset.Sign> = Arb.enum(),
  hour: Arb<Int> = hour(),
  minute: Arb<Int> = minute(),
): Arb<TimeZoneOffset> = arbitrary {
  when (val typeClass = type.bind()) {
    TimeZoneOffset.Z::class -> TimeZoneOffset.Z(case.bind())
    TimeZoneOffset.Offset::class ->
      TimeZoneOffset.Offset(
        hours = hour.bind(),
        minutes = minute.bind(),
        sign = sign.bind(),
      )
    else -> throw IllegalStateException("should never get here wwh9arx7x8: $typeClass")
  }
}

fun DataConnectArb.timestampComponents(
  dateArb: Arb<DateTestData> = date(),
  hourArb: Arb<Int> = hour(),
  minuteArb: Arb<Int> = minute(),
  secondArb: Arb<Int> = second(),
  nanosecondsNumDigitsArb: Arb<Int> = Arb.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
  nanosecondsArb: Arb<Int> = nanosecond(),
  timeZoneOffset: Arb<TimeZoneOffset> = timeZoneOffset(),
  tArb: Arb<Char> = Arb.of('t', 'T'),
): Arb<TimestampComponents> = arbitrary {
  val date = dateArb.bind()

  val nanosecondsNumDigits = nanosecondsNumDigitsArb.bind()
  val nanoseconds =
    if (nanosecondsNumDigits == 0) {
      0
    } else {
      nanosecondsArb.bind().withNumDigits(nanosecondsNumDigits)
    }

  TimestampComponents(
    year = date.year,
    month = date.month,
    day = date.day,
    hour = hourArb.bind(),
    minute = minuteArb.bind(),
    second = secondArb.bind(),
    nanoseconds = nanoseconds,
    nanosecondsNumDigits = nanosecondsNumDigits,
    timeZoneOffset = timeZoneOffset.bind(),
    t = tArb.bind(),
  )
}

fun DataConnectArb.timestamp(
  components: Arb<TimestampComponents> = timestampComponents(),
): Arb<TimestampTestData> =
  arbitrary(edgecases = TimestampEdgeCases.all) {
    components.bind().toTimestampTestData(name = "arbitrary")
  }

private fun TimestampComponents.toRfc3339String(): String = buildString {
  append(year)
  append('-')
  append("$month".padStart(2, '0'))
  append('-')
  append("$day".padStart(2, '0'))

  append(t)

  append("$hour".padStart(2, '0'))
  append(':')
  append("$minute".padStart(2, '0'))
  append(':')
  append("$second".padStart(2, '0'))

  if (nanosecondsNumDigits > 0) {
    append('.')
    append("$nanoseconds".padStart(9, '0').substring(0, nanosecondsNumDigits))
  }

  append(timeZoneOffset.rfc3339String)
}

/**
 * Creates and returns a [Regex] object that should match the string sent back from the Data Connect
 * `executeQuery` operation for a field whose value is a timestamp with the given values. All
 * arguments must conform to their corresponding restrictions in RFC 3339 "Date and Time on the
 * Internet: Timestamps" https://datatracker.ietf.org/doc/html/rfc3339.
 */
private fun TimestampComponents.toFdcFieldRegex(): Regex =
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

private fun TimestampComponents.toUtcTimestamp(): Timestamp =
  timestampFromUTCDateAndTime(
    year = year,
    month = month,
    day = day,
    hour = hour,
    minute = minute,
    second = second,
    nanoseconds = nanoseconds,
  )

@JvmName("toTimestampTestDataWithAllowingNullArguments")
private fun TimestampComponents.toTimestampTestData(
  name: String,
  timestamp: Timestamp? = null,
  string: String? = null,
  fdcStringVariable: String? = null,
  fdcFieldTimestamp: Timestamp? = null,
): TimestampTestData {
  val effectiveTimestamp = timestamp ?: toUtcTimestamp()
  val effectiveString = string ?: toRfc3339String()

  val effectiveFdcFieldTimestamp =
    fdcFieldTimestamp ?: effectiveTimestamp.withMicrosecondPrecision()

  val effectiveFdcStringVariable =
    fdcStringVariable
      ?: copy(
          t = 'T',
          timeZoneOffset = TimeZoneOffset.Z(TimeZoneOffset.Z.Case.Uppercase),
          nanosecondsNumDigits = 6
        )
        .toRfc3339String()

  return toTimestampTestData(
    name = name,
    timestamp = effectiveTimestamp,
    string = effectiveString,
    fdcStringVariable = effectiveFdcStringVariable,
    fdcFieldTimestamp = effectiveFdcFieldTimestamp,
  )
}

@JvmName("toTimestampTestDataWithRejectingNullArguments")
private fun TimestampComponents.toTimestampTestData(
  name: String,
  timestamp: Timestamp,
  string: String,
  fdcStringVariable: String,
  fdcFieldTimestamp: Timestamp,
): TimestampTestData {
  return TimestampTestData(
    name = name,
    timestamp = timestamp,
    string = string,
    fdcStringVariable = fdcStringVariable,
    fdcFieldRegex = toFdcFieldRegex(),
    fdcFieldTimestamp = fdcFieldTimestamp,
    components = this
  )
}

private fun TimestampTestData.Companion.from(
  name: String,
  timestamp: Timestamp? = null,
  string: String? = null,
  fdcStringVariable: String? = null,
  fdcFieldTimestamp: Timestamp? = null,
  year: Int,
  month: Int,
  day: Int,
  hour: Int,
  minute: Int,
  second: Int,
  nanoseconds: Int,
  nanosecondsNumDigits: Int = 9,
  timeZoneOffset: TimeZoneOffset = TimeZoneOffset.Z(TimeZoneOffset.Z.Case.Uppercase),
  t: Char = 'T',
): TimestampTestData =
  TimestampComponents(
      year = year,
      month = month,
      day = day,
      hour = hour,
      minute = minute,
      second = second,
      nanoseconds = nanoseconds,
      nanosecondsNumDigits = nanosecondsNumDigits,
      timeZoneOffset = timeZoneOffset,
      t = t,
    )
    .toTimestampTestData(
      name = name,
      timestamp = timestamp,
      string = string,
      fdcStringVariable = fdcStringVariable,
      fdcFieldTimestamp = fdcFieldTimestamp,
    )

private fun Int.withNumDigits(numDigits: Int): Int {
  require(numDigits >= 0) { "invalid numDigits: $numDigits" }
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
      TimestampTestData.from(
        name = "minimum timestamp",
        timestamp = Timestamp(-12_212_553_600, 0),
        string = "1583-01-01T00:00:00Z",
        year = 1583,
        month = 1,
        day = 1,
        hour = 0,
        minute = 0,
        second = 0,
        nanoseconds = 0,
      )

  val max: TimestampTestData
    get() =
      TimestampTestData.from(
        name = "maximum timestamp",
        timestamp = Timestamp(253_402_300_799, 999_999_999),
        string = "9999-12-31T23:59:59.999999999Z",
        year = 9999,
        month = 12,
        day = 31,
        hour = 23,
        minute = 59,
        second = 59,
        nanoseconds = 999_999_999,
      )

  val zero: TimestampTestData
    get() =
      TimestampTestData.from(
        name = "zero timestamp",
        timestamp = Timestamp(0, 0),
        string = "1970-01-01T00:00:00.000000000Z",
        year = 1970,
        month = 1,
        day = 1,
        hour = 0,
        minute = 0,
        second = 0,
        nanoseconds = 0,
      )

  // The time zone offset "+00:00" is identical to "Z" (i.e. UTC).
  // https://datatracker.ietf.org/doc/html/rfc3339#section-4.3
  val plusZeroTimeZoneOffset: TimestampTestData
    get() {
      val string = "2024-05-18T12:45:56.123456789+00:00"
      return TimestampTestData.from(
        name = "+00:00 time zone offset",
        string = string,
        fdcStringVariable = string,
        year = 2024,
        month = 5,
        day = 18,
        hour = 12,
        minute = 45,
        second = 56,
        nanoseconds = 123456789,
      )
    }

  val positiveNonZeroTimeZoneOffset: TimestampTestData
    get() {
      val string = "2024-05-18T12:45:56.123456789+01:23"
      return TimestampTestData.from(
        name = "positive, non-zero time zone offset",
        string = string,
        fdcStringVariable = string,
        year = 2024,
        month = 5,
        day = 18,
        hour = 11,
        minute = 22,
        second = 56,
        nanoseconds = 123456789,
      )
    }

  val negativeNonZeroTimeZoneOffset: TimestampTestData
    get() {
      val string = "2024-05-18T12:45:56.123456789-01:23"
      return TimestampTestData.from(
        name = "negative, non-zero time zone offset",
        string = string,
        fdcStringVariable = string,
        year = 2024,
        month = 5,
        day = 18,
        hour = 14,
        minute = 8,
        second = 56,
        nanoseconds = 123456789,
      )
    }

  val singleDigits: TimestampTestData
    get() =
      TimestampTestData.from(
        name = "single digits",
        string = "$MIN_YEAR-02-03T04:05:06.7Z",
        year = MIN_YEAR,
        month = 2,
        day = 3,
        hour = 4,
        minute = 5,
        second = 6,
        nanoseconds = 700_000_000,
      )

  val allDigits: TimestampTestData
    get() =
      TimestampTestData.from(
        name = "all digits",
        string = "2345-12-15T12:35:44.123456789Z",
        year = 2345,
        month = 12,
        day = 15,
        hour = 12,
        minute = 35,
        second = 44,
        nanoseconds = 123_456_789,
      )

  val nanosecondsOmitted: TimestampTestData
    get() =
      TimestampTestData.from(
        name = "nanoseconds omitted",
        string = "7608-11-21T02:45:08Z",
        year = 7608,
        month = 11,
        day = 21,
        hour = 2,
        minute = 45,
        second = 8,
        nanoseconds = 0,
      )

  val nanoseconds1Digit: TimestampTestData
    get() =
      TimestampTestData.from(
        name = "1 digit of nanoseconds",
        string = "7608-11-21T02:45:08.000000001Z",
        year = 7608,
        month = 11,
        day = 21,
        hour = 2,
        minute = 45,
        second = 8,
        nanoseconds = 1,
      )

  val nanoseconds2Digits: TimestampTestData
    get() =
      TimestampTestData.from(
        name = "2 digits of nanoseconds",
        string = "7608-11-21T02:45:08.000000012Z",
        year = 7608,
        month = 11,
        day = 21,
        hour = 2,
        minute = 45,
        second = 8,
        nanoseconds = 12,
      )

  val nanoseconds3Digits: TimestampTestData
    get() =
      TimestampTestData.from(
        name = "3 digits of nanoseconds",
        string = "7608-11-21T02:45:08.000000123Z",
        year = 7608,
        month = 11,
        day = 21,
        hour = 2,
        minute = 45,
        second = 8,
        nanoseconds = 123,
      )

  val nanoseconds4Digits: TimestampTestData
    get() =
      TimestampTestData.from(
        name = "4 digits of nanoseconds",
        string = "7608-11-21T02:45:08.000001234Z",
        year = 7608,
        month = 11,
        day = 21,
        hour = 2,
        minute = 45,
        second = 8,
        nanoseconds = 1234,
      )

  val nanoseconds5Digits: TimestampTestData
    get() =
      TimestampTestData.from(
        name = "5 digits of nanoseconds",
        string = "7608-11-21T02:45:08.000012345Z",
        year = 7608,
        month = 11,
        day = 21,
        hour = 2,
        minute = 45,
        second = 8,
        nanoseconds = 12345,
      )

  val nanoseconds6Digits: TimestampTestData
    get() =
      TimestampTestData.from(
        name = "6 digits of nanoseconds",
        string = "7608-11-21T02:45:08.000123456Z",
        year = 7608,
        month = 11,
        day = 21,
        hour = 2,
        minute = 45,
        second = 8,
        nanoseconds = 123456,
      )

  val nanoseconds7Digits: TimestampTestData
    get() =
      TimestampTestData.from(
        name = "7 digits of nanoseconds",
        string = "7608-11-21T02:45:08.001234567Z",
        year = 7608,
        month = 11,
        day = 21,
        hour = 2,
        minute = 45,
        second = 8,
        nanoseconds = 1234567,
      )

  val nanoseconds8Digits: TimestampTestData
    get() =
      TimestampTestData.from(
        name = "8 digits of nanoseconds",
        string = "7608-11-21T02:45:08.012345678Z",
        year = 7608,
        month = 11,
        day = 21,
        hour = 2,
        minute = 45,
        second = 8,
        nanoseconds = 12345678,
      )

  val nanoseconds9Digits: TimestampTestData
    get() =
      TimestampTestData.from(
        name = "9 digits of nanoseconds",
        string = "7608-11-21T02:45:08.123456789Z",
        year = 7608,
        month = 11,
        day = 21,
        hour = 2,
        minute = 45,
        second = 8,
        nanoseconds = 123456789,
      )

  val all: List<TimestampTestData>
    get() =
      listOf(
        min,
        max,
        zero,
        positiveNonZeroTimeZoneOffset,
        plusZeroTimeZoneOffset,
        negativeNonZeroTimeZoneOffset,
        singleDigits,
        allDigits,
        nanosecondsOmitted,
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
