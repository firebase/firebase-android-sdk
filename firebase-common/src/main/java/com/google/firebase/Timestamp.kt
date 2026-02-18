/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase

import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.RequiresApi
import java.time.Instant
import java.util.Date

/**
 * A Timestamp represents a point in time independent of any time zone or calendar.
 *
 * Represented as seconds and fractions of seconds at nanosecond resolution in UTC Epoch time. It is
 * encoded using the Proleptic Gregorian Calendar which extends the Gregorian calendar backwards to
 * year one. Furthermore,It is encoded assuming all minutes are 60 seconds long, specifically leap
 * seconds are "smeared" so that no leap second table is needed for interpretation. Range is from
 * 0001-01-01T00:00:00Z to 9999-12-31T23:59:59.999999999Z. By restricting to that range, we ensure
 * that we can convert to and from RFC 3339 date strings.
 *
 * @see <a href="https://git.page.link/timestamp-proto">Timestamp</a>The ref timestamp definition
 */
class Timestamp : Comparable<Timestamp>, Parcelable {
  val seconds: Long
  val nanoseconds: Int

  /**
   * Creates a new [Timestamp].
   *
   * @param seconds represents seconds of UTC time since Unix epoch 1970-01-01T00:00:00Z. Must be
   * from 0001-01-01T00:00:00Z to 9999-12-31T23:59:59Z inclusive.
   * @param nanoseconds represents non-negative fractions of a second at nanosecond resolution.
   * Negative second values with fractions must still have non-negative nanoseconds values that
   * count forward in time. Must be from 0 to 999,999,999 inclusive.
   */
  constructor(seconds: Long, nanoseconds: Int) {
    validateRange(seconds, nanoseconds)

    this.seconds = seconds
    this.nanoseconds = nanoseconds
  }

  constructor(date: Date) {
    val (seconds, nanoseconds) = date.toPreciseTime()

    validateRange(seconds, nanoseconds)

    this.seconds = seconds
    this.nanoseconds = nanoseconds
  }

  @RequiresApi(Build.VERSION_CODES.O) constructor(time: Instant) : this(time.epochSecond, time.nano)

  /**
   * Returns a new [Date] corresponding to this timestamp.
   *
   * This may lose precision.
   */
  fun toDate(): Date = Date(seconds * 1_000 + (nanoseconds / 1_000_000))

  /** Returns a new [Instant] that matches the time defined by this timestamp. */
  @RequiresApi(Build.VERSION_CODES.O)
  fun toInstant(): Instant = Instant.ofEpochSecond(seconds, nanoseconds.toLong())

  override fun compareTo(other: Timestamp): Int =
    compareValuesBy(this, other, Timestamp::seconds, Timestamp::nanoseconds)

  override fun equals(other: Any?): Boolean =
    other === this || other is Timestamp && compareTo(other) == 0

  override fun hashCode(): Int {
    val prime = 37
    val initialHash = prime * seconds.toInt()
    val withHighOrderBits = prime * initialHash + seconds.shr(32).toInt()

    return prime * withHighOrderBits + nanoseconds
  }

  override fun toString(): String = "Timestamp(seconds=$seconds, nanoseconds=$nanoseconds)"

  override fun describeContents(): Int = 0

  override fun writeToParcel(dest: Parcel, flags: Int) {
    dest.writeLong(seconds)
    dest.writeInt(nanoseconds)
  }

  companion object {
    @JvmField
    val CREATOR =
      object : Parcelable.Creator<Timestamp> {
        override fun createFromParcel(source: Parcel): Timestamp =
          Timestamp(source.readLong(), source.readInt())
        override fun newArray(size: Int): Array<Timestamp?> = arrayOfNulls(size)
      }

    @JvmStatic fun now() = Timestamp(Date())

    private fun Date.toPreciseTime(): Pair<Long, Int> {
      val seconds = time / 1_000
      val nanoseconds = ((time % 1_000) * 1_000_000).toInt()

      if (nanoseconds < 0) return (seconds - 1) to (nanoseconds + 1_000_000_000)

      return seconds to nanoseconds
    }

    /**
     * Ensures that the date and time are within what we consider valid ranges.
     *
     * More specifically, the nanoseconds need to be less than 1 billion- otherwise it would trip
     * over into seconds, and need to be greater than zero.
     *
     * The seconds need to be after the date `1/1/1` and before the date `1/1/10000`.
     *
     * @throws IllegalArgumentException if the date and time are considered invalid
     */
    private fun validateRange(seconds: Long, nanoseconds: Int) {
      require(nanoseconds in 0 until 1_000_000_000) {
        "Timestamp nanoseconds out of range: $nanoseconds"
      }

      require(seconds in -62_135_596_800 until 253_402_300_800) {
        "Timestamp seconds out of range: $seconds"
      }
    }
  }
}
