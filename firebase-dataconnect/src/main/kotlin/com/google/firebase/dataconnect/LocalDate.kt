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

package com.google.firebase.dataconnect

import android.annotation.SuppressLint
import com.google.firebase.dataconnect.serializers.LocalDateSerializer
import java.util.Objects
import kotlinx.serialization.Serializable

/**
 * A date without a time-zone in the ISO-8601 calendar system, such as `2007-12-03`. This is the
 * default Kotlin type used to represent a `Date` GraphQL custom scalar in Firebase Data Connect.
 *
 * ### Description (adapted from [java.time.LocalDate])
 *
 * [LocalDate] is an immutable date-time object that represents a date, often viewed as
 * year-month-day. For example, the value "2nd October 2007" can be stored in a [LocalDate].
 *
 * This class does not store or represent a time or time-zone. Instead, it is a description of the
 * date, as used for birthdays. It cannot represent an instant on the time-line without additional
 * information such as an offset or time-zone.
 *
 * ### Relationship to [java.time.LocalDate] and [kotlinx.datetime.LocalDate]
 *
 * This class exists solely to fill the gap for a "day-month-year" data type in Android API versions
 * less than 26. When the Firebase Android SDK updates its `minSdkVersion` to 26 or later, then this
 * class will be marked as "deprecated" and eventually removed.
 *
 * The [java.time.LocalDate] class was added in Android API 26 and should be used if it's available
 * instead of this class. If [java.time.LocalDate] is available then [kotlinx.datetime.LocalDate] is
 * a completely valid option as well, if it's desirable to take a dependency on
 * [kotlinx-datetime](https://github.com/Kotlin/kotlinx-datetime).
 *
 * Alternately, if your application has its `minSdkVersion` set to a value _less than_ 26, you can
 * use ["desugaring"](https://developer.android.com/studio/write/java8-support-table) to get access
 * [java.time.LocalDate] class regardless of the API version used at runtime.
 *
 * ### Using [java.time.LocalDate] and [kotlinx.datetime.LocalDate] in code generation.
 *
 * By default, the Firebase Data Connect code generation will use this class when generating code
 * for Kotlin. If, however, you want to use the preferable [java.time.LocalDate] or
 * [kotlinx.datetime.LocalDate] classes, add a `dateClass` entry in your `connector.yaml` set to the
 * fully-qualified class name that you'd like to use. For example,
 *
 * ```
 * connectorId: demo
 * authMode: PUBLIC
 * generate:
 *   kotlinSdk:
 *     outputDir: ../../.generated/demo
 *     dateClass: java.time.LocalDate # or kotlinx.datetime.LocalDate
 * ```
 *
 * ### Safe for concurrent use
 *
 * All methods and properties of [LocalDate] are thread-safe and may be safely called and/or
 * accessed concurrently from multiple threads and/or coroutines.
 *
 * @property year The year. The valid range is between 1583 and 9999, inclusive; however, this is
 * _not_ checked or enforced by this class. Values less than 1583 are not strictly forbidden;
 * however, their interpretation by the Data Connect backend is unspecified. See
 * [https://en.wikipedia.org/wiki/ISO_8601#Years](https://en.wikipedia.org/wiki/ISO_8601#Years) for
 * more details.
 * @property month The month. The valid range is between 1 and 12, inclusive; however, this is _not_
 * checked or enforced by this class.
 * @property day The day of the month. The valid range is between 1 and 31, inclusive; however, this
 * is _not_ checked or enforced by this class.
 */
@Serializable(with = LocalDateSerializer::class)
public class LocalDate(public val year: Int, public val month: Int, public val day: Int) {

  /**
   * Compares this object with another object for equality.
   *
   * @param other The object to compare to this for equality.
   * @return true if, and only if, the other object is an instance of [LocalDate] and has the same
   * values for [year], [month], and [day] as this object, respectively.
   */
  override fun equals(other: Any?): Boolean =
    other is LocalDate && other.year == year && other.month == month && other.day == day

  /**
   * Calculates and returns the hash code for this object.
   *
   * The hash code is _not_ guaranteed to be stable across application restarts.
   *
   * @return the hash code for this object, that incorporates the values of this object's public
   * properties.
   */
  override fun hashCode(): Int = Objects.hash(LocalDate::class, year, month, day)

  /**
   * Returns a string representation of this object, useful for debugging.
   *
   * The string representation is _not_ guaranteed to be stable and may change without notice at any
   * time. Therefore, the only recommended usage of the returned string is debugging and/or logging.
   * Namely, parsing the returned string or storing the returned string in non-volatile storage
   * should generally be avoided in order to be robust in case that the string representation
   * changes.
   *
   * @return a string representation of this object, which includes the class name and the values of
   * all public properties.
   */
  override fun toString(): String = "LocalDate(year=$year, month=$month, day=$day)"
}

/**
 * Creates and returns a [java.time.LocalDate] object that represents the same date as this object.
 *
 * Be sure to _only_ call this method if [java.time.LocalDate] is available; otherwise the behavior
 * is undefined. If your application's `minSdkVersion` is greater than or equal to `26`, or if you
 * have configured ["desugaring"](https://developer.android.com/studio/write/java8-support-table)
 * then it is guaranteed to be available. Otherwise, check [android.os.Build.VERSION.SDK_INT] at
 * runtime and verify that its value is at least [android.os.Build.VERSION_CODES.O] before calling
 * this method.
 *
 * @see java.time.LocalDate.toDataConnectLocalDate
 * @see kotlinx.datetime.LocalDate.toDataConnectLocalDate
 * @see toKotlinxLocalDate
 */
@SuppressLint("NewApi")
public fun LocalDate.toJavaLocalDate(): java.time.LocalDate =
  java.time.LocalDate.of(year, month, day)

/**
 * Creates and returns a [LocalDate] object that represents the same date as this
 * [java.time.LocalDate] object. This is the inverse operation of [LocalDate.toJavaLocalDate].
 *
 * Be sure to _only_ call this method if [java.time.LocalDate] is available. See the documentation
 * for [LocalDate.toJavaLocalDate] for details.
 *
 * @see toJavaLocalDate
 * @see toKotlinxLocalDate
 * @see kotlinx.datetime.LocalDate.toDataConnectLocalDate
 */
@SuppressLint("NewApi")
public fun java.time.LocalDate.toDataConnectLocalDate(): LocalDate =
  LocalDate(year = year, month = monthValue, day = dayOfMonth)

/**
 * Creates and returns a [kotlinx.datetime.LocalDate] object that represents the same date as this
 * object.
 *
 * Be sure to _only_ call this method if your application has a dependency on
 * `org.jetbrains.kotlinx:kotlinx-datetime`; otherwise, the behavior of this method is undefined. If
 * your `minSdkVersion` is less than `26` then you _may_ also need to configure
 * ["desugaring"](https://developer.android.com/studio/write/java8-support-table).
 *
 * @see kotlinx.datetime.LocalDate.toDataConnectLocalDate
 * @see java.time.LocalDate.toDataConnectLocalDate
 * @see toJavaLocalDate
 */
public fun LocalDate.toKotlinxLocalDate(): kotlinx.datetime.LocalDate =
  kotlinx.datetime.LocalDate(year = year, monthNumber = month, dayOfMonth = day)

/**
 * Creates and returns a [LocalDate] object that represents the same date as the given
 * [kotlinx.datetime.LocalDate] object. This is the inverse operation of [toKotlinxLocalDate].
 *
 * Be sure to _only_ call this method if your application has a dependency on
 * `org.jetbrains.kotlinx:kotlinx-datetime`. See the documentation for [toKotlinxLocalDate] for
 * details.
 *
 * @see toKotlinxLocalDate
 * @see toJavaLocalDate
 * @see java.time.LocalDate.toDataConnectLocalDate
 */
public fun kotlinx.datetime.LocalDate.toDataConnectLocalDate(): LocalDate =
  LocalDate(year = year, month = monthNumber, day = dayOfMonth)

/** Creates and returns a new [LocalDate] instance with the given property values. */
public fun LocalDate.copy(
  year: Int = this.year,
  month: Int = this.month,
  day: Int = this.day,
): LocalDate = LocalDate(year = year, month = month, day = day)
