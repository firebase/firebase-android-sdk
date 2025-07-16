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

import com.google.firebase.dataconnect.LocalDate
import com.google.firebase.dataconnect.testutil.NullableReference
import com.google.firebase.dataconnect.testutil.dayRangeInYear
import com.google.firebase.dataconnect.testutil.property.arbitrary.DateEdgeCases.MAX_YEAR
import com.google.firebase.dataconnect.testutil.property.arbitrary.DateEdgeCases.MIN_YEAR
import com.google.firebase.dataconnect.toDataConnectLocalDate
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.Sample
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.next
import io.kotest.property.asSample
import java.util.UUID
import kotlin.random.nextInt
import org.threeten.bp.Month
import org.threeten.bp.Year

fun DataConnectArb.dateTestData(): Arb<DateTestData> = DateTestDataArb()

fun DataConnectArb.localDate(): Arb<LocalDate> = dateTestData().map { it.date }

fun DataConnectArb.threeNonNullDatesTestData(
  dateTestData: Arb<DateTestData> = dateTestData()
): Arb<ThreeDateTestDatas> = ThreeDateTestDatasArb(dateTestData.map { NullableReference(it) })

fun DataConnectArb.threePossiblyNullDatesTestData(
  dateTestData: Arb<NullableReference<DateTestData>> =
    dateTestData().orNullableReference(nullProbability = 0.333)
): Arb<ThreeDateTestDatas> = ThreeDateTestDatasArb(dateTestData)

/** An [Arb] that produces [DateTestData] objects that are accepted by Firebase Data Connect. */
private class DateTestDataArb : Arb<DateTestData>() {

  private val yearArb: Arb<Year> = Arb.intWithUniformNumDigitsDistribution(yearRange).map(Year::of)
  private val monthArb: Arb<Month> =
    Arb.intWithUniformNumDigitsDistribution(monthRange).map(Month::of)

  override fun sample(rs: RandomSource): Sample<DateTestData> {
    val year = yearArb.next(rs)
    val month = monthArb.next(rs)
    val dayRange = month.dayRangeInYear(year)
    val day = dayRange.random(rs.random)
    return toDateTestData(year = year, month = month, day = day).asSample()
  }

  override fun edgecase(rs: RandomSource): DateTestData {
    val year = rs.edgeCaseFrom(yearArb)
    val month = rs.edgeCaseFrom(monthArb)
    val day = rs.edgeCaseFrom(Arb.int(month.dayRangeInYear(year)))
    return toDateTestData(year = year, month = month, day = day)
  }

  private fun toDateTestData(year: Year, month: Month, day: Int): DateTestData {
    val localDate = LocalDate(year = year.value, month = month.value, day = day)

    val yearStr = "$year"
    val monthStr = "${month.value}".padStart(2, '0')
    val dayStr = "$day".padStart(2, '0')
    val string = "$yearStr-$monthStr-$dayStr"

    return DateTestData(localDate, string)
  }

  companion object {
    val yearRange: IntRange = MIN_YEAR..MAX_YEAR
    val monthRange: IntRange = 1..12

    private fun <T> RandomSource.edgeCaseFrom(arb: Arb<T>): T {
      if (random.nextBoolean()) {
        val edgeCase = arb.edgecase(this)
        if (edgeCase !== null) {
          return edgeCase
        }
      }
      return arb.next(this)
    }
  }
}

data class DateTestData(
  val date: LocalDate,
  val string: String,
) {
  constructor(
    date: java.time.LocalDate,
    string: String
  ) : this(date.toDataConnectLocalDate(), string)

  constructor(
    date: kotlinx.datetime.LocalDate,
    string: String
  ) : this(date.toDataConnectLocalDate(), string)
}

@Suppress("MemberVisibilityCanBePrivate")
object DateEdgeCases {
  // See https://en.wikipedia.org/wiki/ISO_8601#Years for rationale of lower bound of 1583.
  const val MIN_YEAR = 1583

  const val MAX_YEAR = 9999

  val min: DateTestData
    get() = DateTestData(LocalDate(MIN_YEAR, 1, 1), "$MIN_YEAR-01-01")
  val max: DateTestData
    get() = DateTestData(LocalDate(MAX_YEAR, 12, 31), "$MAX_YEAR-12-31")
  val epoch: DateTestData
    get() = DateTestData(LocalDate(1970, 1, 1), "1970-01-01")

  fun all(): List<DateTestData> = listOf(min, max, epoch)
}

data class ThreeDateTestDatas(
  val testData1: DateTestData?,
  val testData2: DateTestData?,
  val testData3: DateTestData?,
  private val index: Int,
) {
  init {
    require(index in 0..2) { "invalid index: $index (error code shfwcz4j4w)" }
  }

  val all: List<DateTestData?>
    get() = listOf(testData1, testData2, testData3)

  val selected: DateTestData? =
    when (index) {
      0 -> testData1
      1 -> testData2
      2 -> testData3
      else -> throw Exception("internal error: unknown index: $index")
    }

  val numMatchingSelected: Int = run {
    val v1 = if (testData1 == selected) 1 else 0
    val v2 = if (testData2 == selected) 1 else 0
    val v3 = if (testData3 == selected) 1 else 0
    v1 + v2 + v3
  }

  fun idsMatchingSelected(getter: (ItemNumber) -> UUID): List<UUID> =
    idsMatching(selected?.date, getter)

  fun idsMatching(
    localDate: LocalDate?,
    getter: (ItemNumber) -> UUID,
  ): List<UUID> {
    val ids = listOf(getter(ItemNumber.ONE), getter(ItemNumber.TWO), getter(ItemNumber.THREE))
    return ids.filterIndexed { index, _ -> all[index]?.date == localDate }
  }

  fun idsMatching(
    localDate: java.time.LocalDate?,
    getter: (ItemNumber) -> UUID,
  ): List<UUID> = idsMatching(localDate?.toDataConnectLocalDate(), getter)

  fun idsMatching(
    localDate: kotlinx.datetime.LocalDate?,
    getter: (ItemNumber) -> UUID,
  ): List<UUID> = idsMatching(localDate?.toDataConnectLocalDate(), getter)

  enum class ItemNumber {
    ONE,
    TWO,
    THREE,
  }
}

private class ThreeDateTestDatasArb(
  private val dateTestData: Arb<NullableReference<DateTestData>>
) : Arb<ThreeDateTestDatas>() {
  override fun sample(rs: RandomSource): Sample<ThreeDateTestDatas> =
    rs.nextThreeLocalDates { dateTestData.next(rs) }.asSample()

  override fun edgecase(rs: RandomSource): ThreeDateTestDatas {
    val result: ThreeDateTestDatas =
      rs.nextThreeLocalDates {
        if (rs.random.nextBoolean()) {
          dateTestData.edgecase(rs)!!
        } else {
          dateTestData.next(rs)
        }
      }

    return when (val case = rs.random.nextInt(0..4)) {
      0 -> result
      1 -> result.copy(testData2 = result.testData1)
      2 -> result.copy(testData3 = result.testData1)
      3 -> result.copy(testData3 = result.testData2)
      4 -> result.copy(testData2 = result.testData1, testData3 = result.testData1)
      else -> throw Exception("should never get here: case=$case (error code yzqq7kw3eh)")
    }
  }

  private fun RandomSource.nextThreeLocalDates(
    nextDateTestData: () -> NullableReference<DateTestData>
  ): ThreeDateTestDatas {
    val dates = List(3) { nextDateTestData().ref }
    val index = random.nextInt(dates.indices)
    return ThreeDateTestDatas(dates[0], dates[1], dates[2], index)
  }
}
