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

import com.google.firebase.dataconnect.testutil.dayRangeInYear
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.map
import org.threeten.bp.LocalDate
import org.threeten.bp.Month
import org.threeten.bp.Year

val Arb.Companion.threeTenBp: ThreeTenBpArbs
  get() = ThreeTenBpArbs

@Suppress("MemberVisibilityCanBePrivate", "unused")
object ThreeTenBpArbs {
  fun year(intArb: Arb<Int> = yearInt()): Arb<Year> = intArb.map(Year::of)

  fun yearInt(): Arb<Int> = Arb.intWithUniformNumDigitsDistribution(Year.MIN_VALUE..Year.MAX_VALUE)

  fun month(): Arb<Month> = Arb.enum<Month>()

  fun monthInt(monthArb: Arb<Month> = month()): Arb<Int> = monthArb.map(Month::getValue)

  fun localDate(yearArb: Arb<Year> = year(), monthArb: Arb<Month> = month()): Arb<LocalDate> =
    yearArb.flatMap { year ->
      monthArb.flatMap { month ->
        Arb.intWithUniformNumDigitsDistribution(month.dayRangeInYear(year)).map { day ->
          LocalDate.of(year.value, month, day)
        }
      }
    }
}
