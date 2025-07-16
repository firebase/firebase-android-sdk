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

////////////////////////////////////////////////////////////////////////////////
// THIS FILE WAS COPIED AND ADAPTED FROM LocalDateSerializerUnitTest.kt
// MAKE SURE THAT ANY CHANGES TO THIS FILE ARE BACKPORTED TO
// LocalDateIntegrationTest.kt AND PORTED TO KotlinxDatetimeLocalDateSerializerUnitTest.kt,
// if appropriate.
////////////////////////////////////////////////////////////////////////////////
package com.google.firebase.dataconnect.serializers

import com.google.firebase.dataconnect.serializers.LocalDateSerializerTesting.propTestConfig
import com.google.firebase.dataconnect.testutil.dayRangeInYear
import com.google.firebase.dataconnect.testutil.property.arbitrary.intWithUniformNumDigitsDistribution
import com.google.firebase.dataconnect.util.ProtoUtil.decodeFromValue
import com.google.firebase.dataconnect.util.ProtoUtil.encodeToValue
import com.google.firebase.dataconnect.util.ProtoUtil.toValueProto
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.arabic
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.ascii
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.booleanArray
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.cyrillic
import io.kotest.property.arbitrary.egyptianHieroglyphs
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.greekCoptic
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.katakana
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.merge
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.triple
import io.kotest.property.arbitrary.withEdgecases
import io.kotest.property.checkAll
import io.mockk.every
import io.mockk.mockk
import kotlin.random.nextInt
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encoding.Decoder
import org.junit.Test

////////////////////////////////////////////////////////////////////////////////
// THIS FILE WAS COPIED AND ADAPTED FROM LocalDateSerializerUnitTest.kt
// MAKE SURE THAT ANY CHANGES TO THIS FILE ARE BACKPORTED TO
// LocalDateIntegrationTest.kt AND PORTED TO KotlinxDatetimeLocalDateSerializerUnitTest.kt,
// if appropriate.
////////////////////////////////////////////////////////////////////////////////
class JavaTimeLocalDateSerializerUnitTest {

  @Test
  fun `serialize() should produce the expected serialized string`() = runTest {
    checkAll(propTestConfig, Arb.localDate()) { localDate ->
      val value = encodeToValue(localDate, JavaTimeLocalDateSerializer, serializersModule = null)
      value.stringValue shouldBe localDate.toYYYYMMDDWithZeroPadding()
    }
  }

  @Test
  fun `deserialize() should produce the expected LocalDate object`() = runTest {
    val numPaddingCharsArb = Arb.int(0..10)
    val arb = Arb.triple(numPaddingCharsArb, numPaddingCharsArb, numPaddingCharsArb)
    checkAll(propTestConfig, Arb.localDate(), arb) { localDate, paddingCharsTriple ->
      val (yearPadding, monthPadding, dayPadding) = paddingCharsTriple
      val value =
        localDate
          .toYYYYMMDDWithZeroPadding(
            yearPadding = yearPadding,
            monthPadding = monthPadding,
            dayPadding = dayPadding
          )
          .toValueProto()

      val decodedLocalDate =
        decodeFromValue(value, JavaTimeLocalDateSerializer, serializersModule = null)
      decodedLocalDate shouldBe localDate
    }
  }

  @Test
  fun `deserialize() should throw IllegalArgumentException when given unparseable strings`() =
    runTest {
      checkAll(propTestConfig, Arb.unparseableDate()) { encodedDate ->
        val decoder: Decoder = mockk { every { decodeString() } returns encodedDate }
        shouldThrow<IllegalArgumentException> { JavaTimeLocalDateSerializer.deserialize(decoder) }
      }
    }

  private companion object {

    fun java.time.LocalDate.toYYYYMMDDWithZeroPadding(
      yearPadding: Int = 4,
      monthPadding: Int = 2,
      dayPadding: Int = 2,
    ): String {
      val yearString = year.toZeroPaddedString(yearPadding)
      val monthString = month.value.toZeroPaddedString(monthPadding)
      val dayString = dayOfMonth.toZeroPaddedString(dayPadding)
      return "$yearString-$monthString-$dayString"
    }

    fun Int.toZeroPaddedString(length: Int): String = buildString {
      append(this@toZeroPaddedString)
      val signChar =
        firstOrNull()?.let {
          if (it == '-') {
            deleteCharAt(0)
            it
          } else {
            null
          }
        }

      while (this.length < length) {
        insert(0, '0')
      }

      if (signChar !== null) {
        insert(0, signChar)
      }
    }

    fun Arb.Companion.localDate(
      year: Arb<Int> =
        intWithUniformNumDigitsDistribution(java.time.Year.MIN_VALUE..java.time.Year.MAX_VALUE),
      month: Arb<Int> = intWithUniformNumDigitsDistribution(1..12),
      day: Arb<Int> = intWithUniformNumDigitsDistribution(1..31),
    ): Arb<java.time.LocalDate> {
      fun Int.coerceDayOfMonthIntoValidRangeFor(month: Int, year: Int): Int {
        val monthObject = org.threeten.bp.Month.of(month)
        val yearObject = org.threeten.bp.Year.of(year)
        val dayRange = monthObject.dayRangeInYear(yearObject)
        return coerceIn(dayRange)
      }
      return arbitrary(
        edgecaseFn = { rs ->
          val yearInt = if (rs.random.nextBoolean()) year.next(rs) else year.edgecase(rs)!!
          val monthInt = if (rs.random.nextBoolean()) month.next(rs) else month.edgecase(rs)!!
          val dayInt = if (rs.random.nextBoolean()) day.next(rs) else day.edgecase(rs)!!
          val coercedDayInt =
            dayInt.coerceDayOfMonthIntoValidRangeFor(month = monthInt, year = yearInt)
          java.time.LocalDate.of(yearInt, monthInt, coercedDayInt)
        },
        sampleFn = {
          val yearInt = year.bind()
          val monthInt = month.bind()
          val dayInt = day.bind()
          val coercedDayInt =
            dayInt.coerceDayOfMonthIntoValidRangeFor(month = monthInt, year = yearInt)
          java.time.LocalDate.of(yearInt, monthInt, coercedDayInt)
        }
      )
    }

    private enum class UnparseableNumberReason {
      EmptyString,
      InvalidChars,
      GreaterThanIntMax,
      LessThanIntMin,
    }

    private val codepoints =
      Codepoint.ascii()
        .merge(Codepoint.egyptianHieroglyphs())
        .merge(Codepoint.arabic())
        .merge(Codepoint.cyrillic())
        .merge(Codepoint.greekCoptic())
        .merge(Codepoint.katakana())

    fun Arb.Companion.unparseableNumber(): Arb<String> {
      val reasonArb = enum<UnparseableNumberReason>()
      val validIntArb = intWithUniformNumDigitsDistribution(0..Int.MAX_VALUE)
      val validChars = listOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-').map { it.code }
      val invalidString =
        string(1..5, codepoints.filterNot { validChars.contains(it.value) }).withEdgecases("-")
      val tooLargeValues = long(Int.MAX_VALUE.toLong() + 1L..Long.MAX_VALUE)
      val tooSmallValues = long(Long.MIN_VALUE until Int.MIN_VALUE.toLong())
      return arbitrary { rs ->
        when (reasonArb.bind()) {
          UnparseableNumberReason.EmptyString -> ""
          UnparseableNumberReason.GreaterThanIntMax -> "${tooLargeValues.bind()}"
          UnparseableNumberReason.LessThanIntMin -> "${tooSmallValues.bind()}"
          UnparseableNumberReason.InvalidChars -> {
            val flags = Array(3) { rs.random.nextBoolean() }
            if (!flags[0]) {
              flags[2] = true
            }
            val prefix = if (flags[0]) invalidString.bind() else ""
            val mid = if (flags[1]) validIntArb.bind() else ""
            val suffix = if (flags[2]) invalidString.bind() else ""
            "$prefix$mid$suffix"
          }
        }
      }
    }

    fun Arb.Companion.unparseableDash(): Arb<String> {
      val invalidString =
        string(1..5, codepoints.filterNot { it.value == '-'.code || Character.isDigit(it.value) })
      return arbitrary { rs ->
        val flags = Array(3) { rs.random.nextBoolean() }
        if (!flags[0]) {
          flags[2] = true
        }

        val prefix = if (flags[0]) invalidString.bind() else ""
        val mid = if (flags[1]) "-" else ""
        val suffix = if (flags[2]) invalidString.bind() else ""

        "$prefix$mid$suffix"
      }
    }

    fun Arb.Companion.unparseableDate(): Arb<String> {
      val validNumber = intWithUniformNumDigitsDistribution(0..Int.MAX_VALUE)
      val unparseableNumber = unparseableNumber()
      val unparseableDash = unparseableDash()
      val booleanArray = booleanArray(Arb.constant(5), Arb.boolean())
      return arbitrary(edgecases = listOf("", "-", "--", "---")) { rs ->
        val invalidCharFlags = booleanArray.bind()
        if (invalidCharFlags.count { it } == 0) {
          invalidCharFlags[rs.random.nextInt(invalidCharFlags.indices)] = true
        }

        val year = if (invalidCharFlags[0]) unparseableNumber.bind() else validNumber.bind()
        val dash1 = if (invalidCharFlags[1]) unparseableDash.bind() else "-"
        val month = if (invalidCharFlags[2]) unparseableNumber.bind() else validNumber.bind()
        val dash2 = if (invalidCharFlags[3]) unparseableDash.bind() else "-"
        val day = if (invalidCharFlags[4]) unparseableNumber.bind() else validNumber.bind()

        "$year$dash1$month$dash2$day"
      }
    }
  }
}
