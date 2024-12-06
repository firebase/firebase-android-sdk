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
package com.google.firebase.dataconnect.minimaldemo

import android.annotation.SuppressLint
import com.google.firebase.Timestamp
import com.google.firebase.dataconnect.LocalDate
import com.google.firebase.dataconnect.OptionalVariable
import com.google.firebase.dataconnect.minimaldemo.connector.InsertItemMutation
import com.google.firebase.dataconnect.toJavaLocalDate
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.Sample
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.next
import io.kotest.property.arbs.fooddrink.iceCreamFlavors
import io.kotest.property.asSample
import java.time.Instant
import java.time.LocalDateTime
import java.time.Month
import java.time.Year
import java.time.ZoneOffset
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

fun Arb.Companion.insertItemVariables(): Arb<InsertItemMutation.Variables> =
  InsertItemMutationVariablesArb()

private class InsertItemMutationVariablesArb(
  private val string: Arb<String?> = Arb.iceCreamFlavors().map { it.value },
  private val int: Arb<Int> = Arb.int(),
  private val int64: Arb<Long> = Arb.long(),
  private val float: Arb<Double> = Arb.double().filterNot { it.isNaN() || it.isInfinite() },
  private val boolean: Arb<Boolean> = Arb.boolean(),
  private val date: Arb<LocalDate> = Arb.dataConnectLocalDate(),
  private val timestamp: Arb<Timestamp> = Arb.firebaseTimestamp(),
) : Arb<InsertItemMutation.Variables>() {
  override fun edgecase(rs: RandomSource): InsertItemMutation.Variables =
    InsertItemMutation.Variables(
      string = string.optionalEdgeCase(rs),
      int = int.optionalEdgeCase(rs),
      int64 = int64.optionalEdgeCase(rs),
      float = float.optionalEdgeCase(rs),
      boolean = boolean.optionalEdgeCase(rs),
      date = date.optionalEdgeCase(rs),
      timestamp = timestamp.optionalEdgeCase(rs),
      any = OptionalVariable.Undefined,
    )

  override fun sample(rs: RandomSource): Sample<InsertItemMutation.Variables> =
    InsertItemMutation.Variables(
        string = OptionalVariable.Value(string.next(rs)),
        int = OptionalVariable.Value(int.next(rs)),
        int64 = OptionalVariable.Value(int64.next(rs)),
        float = OptionalVariable.Value(float.next(rs)),
        boolean = OptionalVariable.Value(boolean.next(rs)),
        date = OptionalVariable.Value(date.next(rs)),
        timestamp = OptionalVariable.Value(timestamp.next(rs)),
        any = OptionalVariable.Undefined,
      )
      .asSample()
}

fun Arb.Companion.dataConnectLocalDate(): Arb<LocalDate> = DataConnectLocalDateArb()

private class DataConnectLocalDateArb : Arb<LocalDate>() {

  private val yearArb: Arb<Year> = Arb.int(MIN_YEAR..MAX_YEAR).map { Year.of(it) }
  private val monthArb: Arb<Month> = Arb.enum<Month>()
  private val dayArbByMonthLengthLock = ReentrantLock()
  private val dayArbByMonthLength = mutableMapOf<Int, Arb<Int>>()

  override fun edgecase(rs: RandomSource): LocalDate {
    val year = yearArb.maybeEdgeCase(rs, edgeCaseProbability = 0.33f)
    val month = monthArb.maybeEdgeCase(rs, edgeCaseProbability = 0.33f)
    val day = Arb.dayOfMonth(year, month).maybeEdgeCase(rs, edgeCaseProbability = 0.33f)
    return LocalDate(year = year.value, month = month.value, day = day)
  }

  override fun sample(rs: RandomSource): Sample<LocalDate> {
    val year = yearArb.sample(rs).value
    val month = monthArb.sample(rs).value
    val day = Arb.dayOfMonth(year, month).sample(rs).value
    return LocalDate(year = year.value, month = month.value, day = day).asSample()
  }

  private fun Arb.Companion.dayOfMonth(year: Year, month: Month): Arb<Int> {
    val monthLength = year.atMonth(month).lengthOfMonth()
    return dayArbByMonthLengthLock.withLock {
      dayArbByMonthLength.getOrPut(monthLength) { Arb.int(1..monthLength) }
    }
  }

  companion object {
    const val MIN_YEAR = 1583
    const val MAX_YEAR = 9999
  }
}

fun Arb.Companion.firebaseTimestamp(): Arb<Timestamp> = FirebaseTimestampArb()

private class FirebaseTimestampArb : Arb<Timestamp>() {

  private val localDateArb = Arb.dataConnectLocalDate()
  private val hourArb = Arb.int(1..23)
  private val minuteArb = Arb.int(1..59)
  private val secondArb = Arb.int(1..59)
  private val nanosecondArb = Arb.int(0..999_999_999)

  override fun edgecase(rs: RandomSource) =
    localDateArb
      .maybeEdgeCase(rs, edgeCaseProbability = 0.2f)
      .toTimestampAtTime(
        hour = hourArb.maybeEdgeCase(rs, edgeCaseProbability = 0.2f),
        minute = minuteArb.maybeEdgeCase(rs, edgeCaseProbability = 0.2f),
        second = secondArb.maybeEdgeCase(rs, edgeCaseProbability = 0.2f),
        nanosecond = nanosecondArb.maybeEdgeCase(rs, edgeCaseProbability = 0.2f),
      )

  override fun sample(rs: RandomSource) =
    localDateArb
      .next(rs)
      .toTimestampAtTime(
        hour = hourArb.next(rs),
        minute = minuteArb.next(rs),
        second = secondArb.next(rs),
        nanosecond = nanosecondArb.next(rs),
      )
      .asSample()

  companion object {

    // Suppress the spurious "Call requires API level 26" warning, which can be safely ignored
    // because this application uses "desugaring" to ensure access to the java.time APIs even in
    // Android API versions less than 26.
    // See https://developer.android.com/studio/write/java8-support-table for details.
    @SuppressLint("NewApi")
    private fun LocalDate.toTimestampAtTime(
      hour: Int,
      minute: Int,
      second: Int,
      nanosecond: Int,
    ): Timestamp {
      val localDateTime: LocalDateTime = toJavaLocalDate().atTime(hour, minute, second, nanosecond)
      val instant: Instant = localDateTime.toInstant(ZoneOffset.UTC)
      return Timestamp(instant)
    }
  }
}

private fun <T> Arb<T>.optionalEdgeCase(rs: RandomSource): OptionalVariable<T?> {
  val discriminator = rs.random.nextFloat()
  return if (discriminator < 0.25f) {
    OptionalVariable.Undefined
  } else if (discriminator < 0.50f) {
    OptionalVariable.Value(null)
  } else {
    OptionalVariable.Value(edgecase(rs) ?: next(rs))
  }
}

private fun <T> Arb<T>.maybeEdgeCase(rs: RandomSource, edgeCaseProbability: Float = 0.5f): T {
  require(edgeCaseProbability >= 0.0 && edgeCaseProbability < 1.0) {
    "invalid edgeCaseProbability: $edgeCaseProbability"
  }
  return if (rs.random.nextFloat() >= edgeCaseProbability) {
    sample(rs).value
  } else {
    edgecase(rs) ?: sample(rs).value
  }
}
