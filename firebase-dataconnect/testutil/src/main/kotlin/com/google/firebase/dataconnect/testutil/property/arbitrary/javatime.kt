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
import com.google.firebase.dataconnect.testutil.property.arbitrary.JavaTimeEdgeCases.MAX_EPOCH_SECONDS
import com.google.firebase.dataconnect.testutil.property.arbitrary.JavaTimeEdgeCases.MAX_NANO
import com.google.firebase.dataconnect.testutil.property.arbitrary.JavaTimeEdgeCases.MAX_YEAR
import com.google.firebase.dataconnect.testutil.property.arbitrary.JavaTimeEdgeCases.MIN_EPOCH_SECONDS
import com.google.firebase.dataconnect.testutil.property.arbitrary.JavaTimeEdgeCases.MIN_NANO
import com.google.firebase.dataconnect.testutil.property.arbitrary.JavaTimeEdgeCases.MIN_YEAR
import com.google.firebase.dataconnect.testutil.toTimestamp
import io.kotest.common.mapError
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.orNull
import org.threeten.bp.Instant
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.ZoneOffset
import org.threeten.bp.temporal.ChronoUnit

data class InstantTestCase(
  /** A display name for this test case. */
  val name: String,

  /** The [Instant] being tested. */
  val instant: Instant,

  /**
   * An RFC3339 string representation of [instant].
   *
   * Any given instant can have more than one equivalent representation in RFC3339, and this is but
   * one such representation, chosen arbitrarily. For example, "2024-01-01T12:34:56.123Z" can
   * equivalently be represented as "2024-01-01T12:34:56.123000Z" (extra zeroes in the nanosecond).
   */
  val string: String,

  /** The [Timestamp] that is equivalent to [instant] */
  val timestamp: Timestamp,

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
) {

  companion object {
    fun from(name: String, instant: Instant, string: String? = null): InstantTestCase {
      val effectiveString = string ?: instant.toString()
      return InstantTestCase(
        name = name,
        instant = instant,
        string = effectiveString,
        timestamp = instant.toTimestamp(),
        fdcStringVariable = effectiveString.replace('z', 'Z').replace('t', 'T'),
        fdcFieldRegex = instant.toFdcFieldRegex(),
        fdcFieldTimestamp = instant.truncatedTo(ChronoUnit.MICROS).toTimestamp(),
      )
    }

    fun from(name: String, instantString: String): InstantTestCase {
      return from(
        name = name,
        instant = OffsetDateTime.parse(instantString).toInstant(),
        string = instantString,
      )
    }
  }
}

/**
 * Creates and returns a [Regex] object that should match the string sent back from the Data Connect
 * `executeQuery` operation for a field whose value is a timestamp with the given values. All
 * arguments must conform to their corresponding restrictions in RFC 3339 "Date and Time on the
 * Internet: Timestamps" https://datatracker.ietf.org/doc/html/rfc3339.
 */
private fun Instant.toFdcFieldRegex(): Regex {
  // Truncate the nanoseconds to microseconds because Firebase Data Connect only supports
  // microsecond precision (it discards any additional precision).
  val truncatedInstant = this.truncatedTo(ChronoUnit.MICROS)
  val dateTime = OffsetDateTime.ofInstant(truncatedInstant, ZoneOffset.UTC)

  val pattern = buildString {
    append("0*")
    append(dateTime.year)
    append('-')
    append("${dateTime.month.value}".padStart(2, '0'))
    append('-')
    append("${dateTime.dayOfMonth}".padStart(2, '0'))
    append("[tT]")
    append("${dateTime.hour}".padStart(2, '0'))
    append(':')
    append("${dateTime.minute}".padStart(2, '0'))
    append(':')
    append("${dateTime.second}".padStart(2, '0'))

    if (truncatedInstant.nano == 0) {
      append("(\\.0+)?")
    } else {
      append("\\.")
      append("${truncatedInstant.nano}".padStart(9, '0').substring(0, 6).trimEnd('0'))
      append("0*")
    }

    append("[zZ]")
  }

  return Regex(pattern)
}

data class Nanoseconds(
  val nanoseconds: Int,
  val string: String,
  val digitCounts: JavaTimeArbs.NanosecondComponents
)

sealed interface TimeOffset {

  val zoneOffset: ZoneOffset
  val rfc3339String: String

  data class Utc(val case: Case) : TimeOffset {
    override val zoneOffset: ZoneOffset = ZoneOffset.UTC

    override val rfc3339String = "${case.char}"

    override fun toString() =
      "Utc(case=$case, zoneOffset=ZoneOffset.UTC, rfc3339String=$rfc3339String)"

    @Suppress("unused")
    enum class Case(val char: Char) {
      Uppercase('Z'),
      Lowercase('z'),
    }
  }

  data class HhMm(val hours: Int, val minutes: Int, val sign: Sign) : TimeOffset {
    init {
      require(hours in validHours) {
        "invalid hours: $hours (must be in the closed range $validHours)"
      }
      require(minutes in validMinutes) {
        "invalid minutes: $minutes (must be in the closed range $validMinutes)"
      }
      require(hours != 18 || minutes == 0) { "invalid minutes: $minutes (must be 0 when hours=18)" }
    }

    override val zoneOffset: ZoneOffset =
      ZoneOffset.ofTotalSeconds(sign.multiplier * 60 * ((hours * 60) + minutes))

    override val rfc3339String = buildString {
      append(sign.char)
      append("$hours".padStart(2, '0'))
      append(':')
      append("$minutes".padStart(2, '0'))
    }

    fun toSeconds(): Int {
      val absValue = (hours * SECONDS_PER_HOUR) + (minutes * SECONDS_PER_MINUTE)
      return when (sign) {
        Sign.Positive -> absValue
        Sign.Negative -> -absValue
      }
    }

    override fun toString() =
      "HhMm(hours=$hours, minutes=$minutes, sign=$sign, " +
        "zoneOffset=$zoneOffset, rfc3339String=$rfc3339String)"

    operator fun compareTo(other: HhMm): Int = toSeconds() - other.toSeconds()

    @Suppress("unused")
    enum class Sign(val char: Char, val multiplier: Int) {
      Positive('+', 1),
      Negative('-', -1),
    }

    companion object {
      private const val SECONDS_PER_MINUTE: Int = 60
      private const val SECONDS_PER_HOUR: Int = 60 * SECONDS_PER_MINUTE

      val validHours = 0..18
      val validMinutes = 0..59

      val maxSeconds: Int = 18 * SECONDS_PER_HOUR

      fun forSeconds(seconds: Int, sign: Sign): HhMm {
        require(seconds in 0..maxSeconds) {
          "invalid seconds: $seconds (must be between 0 and $maxSeconds, inclusive)"
        }
        val hours = seconds / SECONDS_PER_HOUR
        val minutes = (seconds - (hours * SECONDS_PER_HOUR)) / SECONDS_PER_MINUTE
        return HhMm(hours = hours, minutes = minutes, sign = sign)
      }
    }
  }
}

@Suppress("MemberVisibilityCanBePrivate")
object JavaTimeArbs {

  fun instantTestCase(): Arb<InstantTestCase> {
    val yearArb = year()
    val monthArb = month()
    val tArb = Arb.of('t', 'T')
    val hourArb = hour()
    val minuteArb = minute()
    val secondArb = second()
    val nanosecondArb = nanosecond().orNull(nullProbability = 0.15)

    return arbitrary(JavaTimeInstantEdgeCases.all) {
      val year = yearArb.bind()
      val month = monthArb.bind()
      val day = day(month).bind()
      val t = tArb.bind()
      val hour = hourArb.bind()
      val minute = minuteArb.bind()
      val second = secondArb.bind()
      val nanosecond = nanosecondArb.bind()

      val instantUtc =
        OffsetDateTime.of(
            year,
            month,
            day,
            hour,
            minute,
            second,
            nanosecond?.nanoseconds ?: 0,
            ZoneOffset.UTC,
          )
          .toInstant()

      // The valid range below was copied from:
      // com.google.firebase.Timestamp.Timestamp.validateRange() 253_402_300_800
      val validEpochSecondRange = -62_135_596_800..253_402_300_800

      val numSecondsBelowMaxEpochSecond = validEpochSecondRange.last - instantUtc.epochSecond
      require(numSecondsBelowMaxEpochSecond > 0) {
        "internal error gh98nqedss: " +
          "invalid numSecondsBelowMaxEpochSecond: $numSecondsBelowMaxEpochSecond"
      }
      val minTimeZoneOffset =
        if (numSecondsBelowMaxEpochSecond >= TimeOffset.HhMm.maxSeconds) {
          null
        } else {
          TimeOffset.HhMm.forSeconds(
            numSecondsBelowMaxEpochSecond.toInt(),
            TimeOffset.HhMm.Sign.Negative
          )
        }

      val numSecondsAboveMinEpochSecond = instantUtc.epochSecond - validEpochSecondRange.first
      require(numSecondsAboveMinEpochSecond > 0) {
        "internal error mje6a4mrbm: " +
          "invalid numSecondsAboveMinEpochSecond: $numSecondsAboveMinEpochSecond"
      }
      val maxTimeZoneOffset =
        if (numSecondsAboveMinEpochSecond >= TimeOffset.HhMm.maxSeconds) {
          null
        } else {
          TimeOffset.HhMm.forSeconds(
            numSecondsAboveMinEpochSecond.toInt(),
            TimeOffset.HhMm.Sign.Positive
          )
        }

      val timeOffset = timeOffset(min = minTimeZoneOffset, max = maxTimeZoneOffset).bind()

      val instant =
        OffsetDateTime.of(
            year,
            month,
            day,
            hour,
            minute,
            second,
            nanosecond?.nanoseconds ?: 0,
            timeOffset.zoneOffset
          )
          .toInstant()

      require(instant.epochSecond >= validEpochSecondRange.first) {
        "internal error weppxzqj2y: " +
          "instant.epochSecond out of range by " +
          "${validEpochSecondRange.first - instant.epochSecond}: ${instant.epochSecond} (" +
          "validEpochSecondRange.first=${validEpochSecondRange.first}, " +
          "year=$year, month=$month, day=$day, " +
          "hour=$hour, minute=$minute, second=$second, " +
          "nanosecond=$nanosecond timeOffset=$timeOffset, " +
          "minTimeZoneOffset=$minTimeZoneOffset, maxTimeZoneOffset=$maxTimeZoneOffset)"
      }
      require(instant.epochSecond <= validEpochSecondRange.last) {
        "internal error yxga5xy9bm: " +
          "instant.epochSecond out of range by " +
          "${instant.epochSecond - validEpochSecondRange.last}: ${instant.epochSecond} (" +
          "validEpochSecondRange.last=${validEpochSecondRange.last}, " +
          "year=$year, month=$month, day=$day, " +
          "hour=$hour, minute=$minute, second=$second, " +
          "nanosecond=$nanosecond timeOffset=$timeOffset, " +
          "minTimeZoneOffset=$minTimeZoneOffset, maxTimeZoneOffset=$maxTimeZoneOffset)"
      }

      val string = buildString {
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

        if (nanosecond !== null) {
          append('.')
          append(nanosecond.string)
        }

        append(timeOffset.rfc3339String)
      }

      InstantTestCase.from(name = "arbitrary", instant = instant, string = string)
    }
  }

  fun timeOffset(
    min: TimeOffset.HhMm?,
    max: TimeOffset.HhMm?,
  ): Arb<TimeOffset> = Arb.choice(timeOffsetUtc(), timeOffsetHhMm(min = min, max = max))

  fun timeOffsetUtc(
    case: Arb<TimeOffset.Utc.Case> = Arb.enum(),
  ): Arb<TimeOffset.Utc> = arbitrary { TimeOffset.Utc(case.bind()) }

  fun timeOffsetHhMm(
    sign: Arb<TimeOffset.HhMm.Sign> = Arb.enum(),
    hour: Arb<Int> = Arb.intWithUniformNumDigitsDistribution(0..18),
    minute: Arb<Int> = minute(),
    min: TimeOffset.HhMm?,
    max: TimeOffset.HhMm?,
  ): Arb<TimeOffset.HhMm> {
    require(min === null || max === null || min.toSeconds() < max.toSeconds()) {
      "min must be strictly less than max, but got: " +
        "min=$min (${min!!.toSeconds()} seconds), " +
        "max=$max (${max!!.toSeconds()} seconds), " +
        "a difference of ${min.toSeconds() - max.toSeconds()} seconds"
    }

    fun isBetweenMinAndMax(other: TimeOffset.HhMm): Boolean =
      (min === null || other >= min) && (max === null || other <= max)

    return arbitrary(
      edgecases =
        listOf(
            TimeOffset.HhMm(hours = 0, minutes = 0, sign = TimeOffset.HhMm.Sign.Positive),
            TimeOffset.HhMm(hours = 0, minutes = 0, sign = TimeOffset.HhMm.Sign.Negative),
            TimeOffset.HhMm(hours = 17, minutes = 59, sign = TimeOffset.HhMm.Sign.Positive),
            TimeOffset.HhMm(hours = 17, minutes = 59, sign = TimeOffset.HhMm.Sign.Negative),
            TimeOffset.HhMm(hours = 18, minutes = 0, sign = TimeOffset.HhMm.Sign.Positive),
            TimeOffset.HhMm(hours = 18, minutes = 0, sign = TimeOffset.HhMm.Sign.Negative),
          )
          .filter(::isBetweenMinAndMax)
    ) {
      var count = 0
      var hhmm: TimeOffset.HhMm
      while (true) {
        count++
        hhmm = TimeOffset.HhMm(hours = hour.bind(), minutes = minute.bind(), sign = sign.bind())
        if (isBetweenMinAndMax(hhmm)) {
          break
        } else if (count > 1000) {
          throw Exception("internal error j878fp4gmr: exhausted attempts to generate HhMm")
        }
      }
      hhmm
    }
  }

  fun year(): Arb<Int> = Arb.int(MIN_YEAR..MAX_YEAR)

  fun month(): Arb<Int> = Arb.int(1..12)

  fun day(month: Int): Arb<Int> = Arb.int(1..maxDayForMonth(month))

  fun hour(): Arb<Int> = Arb.int(0..23)

  fun minute(): Arb<Int> = Arb.int(0..59)

  fun second(): Arb<Int> = Arb.int(0..59)

  fun nanosecond(): Arb<Nanoseconds> {
    val digits = Arb.of('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
    val nonZeroDigits = Arb.of('1', '2', '3', '4', '5', '6', '7', '8', '9')
    val nanosecondComponents = nanosecondComponents()
    return arbitrary {
      val digitCounts = nanosecondComponents.bind()

      val nanosecondsString = buildString {
        repeat(digitCounts.leadingZeroes) { append('0') }
        if (digitCounts.proper > 0) {
          append(nonZeroDigits.bind())
          if (digitCounts.proper > 1) {
            if (digitCounts.proper > 2) {
              repeat(digitCounts.proper - 2) { append(digits.bind()) }
            }
            append(nonZeroDigits.bind())
          }
        }
        repeat(digitCounts.trailingZeroes) { append('0') }
      }

      val nanosecondsStringTrimmed = nanosecondsString.padEnd(9, '0').trimStart('0')
      val nanosecondsInt =
        if (nanosecondsStringTrimmed.isEmpty()) {
          0
        } else {
          val toIntResult = nanosecondsStringTrimmed.runCatching { toInt() }
          toIntResult.mapError { exception ->
            Exception(
              "internal error qbdgapmye2: " +
                "failed to parse nanosecondsStringTrimmed as an int: " +
                "\"$nanosecondsStringTrimmed\" (digitCounts=$digitCounts)",
              exception
            )
          }
          toIntResult.getOrThrow()
        }

      check(nanosecondsInt in 0..999_999_999) {
        "internal error c7j2myw6bd: " +
          "nanosecondsStringTrimmed parsed to a value outside the valid range: " +
          "$nanosecondsInt (digitCounts=$digitCounts)"
      }

      Nanoseconds(nanosecondsInt, nanosecondsString, digitCounts)
    }
  }

  data class NanosecondComponents(val leadingZeroes: Int, val proper: Int, val trailingZeroes: Int)

  private fun nanosecondComponents(): Arb<NanosecondComponents> =
    arbitrary(
      edgecases =
        listOf(
          NanosecondComponents(9, 0, 0),
          NanosecondComponents(0, 9, 0),
          NanosecondComponents(0, 0, 9),
          NanosecondComponents(0, 0, 0),
        )
    ) { rs ->
      val counts = IntArray(4)
      while (counts.sum() != 9) {
        val index = rs.random.nextInt(counts.size)
        counts[index]++
      }
      NanosecondComponents(counts[0], counts[1], counts[2])
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
        throw IllegalArgumentException(
          "invalid month: $month (must be between 1 and 12, inclusive)"
        )
    }
  }
}

object JavaTimeEdgeCases {

  const val MIN_EPOCH_SECONDS: Long = -12_212_553_600
  const val MAX_EPOCH_SECONDS: Long = 253_402_300_799
  const val MIN_NANO: Int = 0
  const val MAX_NANO: Int = 999_999_999
  const val MIN_YEAR = 1583
  const val MAX_YEAR = 9999

  val instants: JavaTimeInstantEdgeCases = JavaTimeInstantEdgeCases
}

object JavaTimeInstantEdgeCases {

  val all: List<InstantTestCase>
    get() =
      listOf(
        min,
        max,
        zero,
        InstantTestCase.from("single digits", "2345-02-03T04:05:06.7Z"),
        InstantTestCase.from("all digits", "2345-12-15T12:35:44.123456789Z"),
        InstantTestCase.from("+00:00 time offset", "2024-05-18T12:45:56.123456789+00:00"),
        InstantTestCase.from("nanoseconds omitted", "1970-01-01T00:00:00Z"),
        InstantTestCase.from("1 nanosecond digit", "1970-01-01T00:00:00.1Z"),
        InstantTestCase.from("2 nanosecond digits", "1970-01-01T00:00:00.12Z"),
        InstantTestCase.from("3 nanosecond digits", "1970-01-01T00:00:00.123Z"),
        InstantTestCase.from("4 nanosecond digits", "1970-01-01T00:00:00.1234Z"),
        InstantTestCase.from("5 nanosecond digits", "1970-01-01T00:00:00.12345Z"),
        InstantTestCase.from("6 nanosecond digits", "1970-01-01T00:00:00.123456Z"),
        InstantTestCase.from("7 nanosecond digits", "1970-01-01T00:00:00.1234567Z"),
        InstantTestCase.from("8 nanosecond digits", "1970-01-01T00:00:00.12345678Z"),
        InstantTestCase.from("9 nanosecond digits", "1970-01-01T00:00:00.123456789Z"),
        InstantTestCase.from("1 trailing nanosecond 0", "1970-01-01T00:00:00.50Z"),
        InstantTestCase.from("2 trailing nanosecond 0s", "1970-01-01T00:00:00.500Z"),
        InstantTestCase.from("3 trailing nanosecond 0s", "1970-01-01T00:00:00.5000Z"),
        InstantTestCase.from("4 trailing nanosecond 0s", "1970-01-01T00:00:00.50000Z"),
        InstantTestCase.from("5 trailing nanosecond 0s", "1970-01-01T00:00:00.500000Z"),
        InstantTestCase.from("6 trailing nanosecond 0s", "1970-01-01T00:00:00.5000000Z"),
        InstantTestCase.from("7 trailing nanosecond 0s", "1970-01-01T00:00:00.50000000Z"),
        InstantTestCase.from("8 trailing nanosecond 0s", "1970-01-01T00:00:00.500000000Z"),
      )

  private val min: InstantTestCase
    get() =
      Instant.parse("1583-01-01T00:00:00Z").let {
        require(it.epochSecond == MIN_EPOCH_SECONDS) {
          "incorrect epochSecond: ${it.epochSecond} " +
            "(expected $MIN_EPOCH_SECONDS, error code bd87sjx3zr)"
        }
        require(it.nano == MIN_NANO) {
          "incorrect nano: ${it.nano} (expected $MIN_NANO, error code ct6pjpdy78)"
        }
        InstantTestCase.from(name = "min", instant = it)
      }

  private val max: InstantTestCase
    get() =
      Instant.parse("9999-12-31T23:59:59.999999999Z").let {
        require(it.epochSecond == MAX_EPOCH_SECONDS) {
          "incorrect epochSecond: ${it.epochSecond} " +
            "(expected $MAX_EPOCH_SECONDS, error code g3db88z5hv)"
        }
        require(it.nano == MAX_NANO) {
          "incorrect nano: ${it.nano} (expected $MAX_NANO, error code pqnnf2hnf3)"
        }
        InstantTestCase.from(name = "max", instant = it)
      }

  private val zero: InstantTestCase
    get() =
      Instant.EPOCH.let {
        require(it.epochSecond == 0L) {
          "incorrect epochSecond: ${it.epochSecond} " + "(expected 0, error code sjte7fpk2p)"
        }
        require(it.nano == 0) { "incorrect nano: ${it.nano} (expected 0, error code t7hmrynw3e)" }
        InstantTestCase.from(name = "zero", instant = it)
      }
}
