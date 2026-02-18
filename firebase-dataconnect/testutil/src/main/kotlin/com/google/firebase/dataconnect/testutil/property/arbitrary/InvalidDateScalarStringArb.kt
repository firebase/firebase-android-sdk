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
import com.google.firebase.dataconnect.testutil.dayRangeInYear
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.Sample
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.az
import io.kotest.property.arbitrary.choose
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.merge
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.string
import io.kotest.property.asSample
import kotlin.random.nextInt
import org.threeten.bp.Month
import org.threeten.bp.Year

fun DataConnectArb.invalidDateScalarString(): Arb<InvalidDateScalarString> =
  InvalidDateScalarStringArb()

private class InvalidDateScalarStringArb(
  private val validLocalDateArb: Arb<LocalDate> = DataConnectArb.localDate(),
) : Arb<InvalidDateScalarString>() {
  private val nonLeapYearArb: Arb<Year> = Arb.threeTenBp.year().filterNot { it.isLeap }
  private val leapYearArb: Arb<Year> = Arb.threeTenBp.year().filter { it.isLeap }
  private val invalidConfigArb: Arb<InvalidConfig> = Arb.invalidConfig()
  private val edgeCaseFlagsArb: Arb<EdgeCaseFlags> = Arb.edgeCaseFlags()
  private val nonDigitStringArb: Arb<String> =
    Arb.string(1..5, Codepoint.az().merge(Arb.constant(Codepoint('-'.code)))).filterNot {
      it == "-"
    }
  private val edgeCaseProbability = 0.15
  private val invalidChoiceArb: Arb<Int> =
    Arb.choose(
      3 to 0,
      5 to 1,
      1 to 2,
      1 to 3,
    )

  override fun sample(rs: RandomSource): Sample<InvalidDateScalarString> {
    val validLocalDate: LocalDate = validLocalDateArb.sample(rs).value
    val invalidConfig: InvalidConfig = invalidConfigArb.sample(rs).value
    val validMonth = Month.of(validLocalDate.month)
    val validYear = Year.of(validLocalDate.year)
    return InvalidDateScalarString(
        year = yearString(rs, invalidConfig.year, validYear),
        dash1 = dashString(rs, invalidConfig.dash1),
        month = monthString(rs, invalidConfig.month, validMonth),
        dash2 = dashString(rs, invalidConfig.dash2),
        day = dayString(rs, invalidConfig.day, validLocalDate.day, validMonth, validYear),
        debugDescription = "invalidConfig=$invalidConfig, validLocalDate=$validLocalDate",
      )
      .asSample()
  }

  override fun edgecase(rs: RandomSource): InvalidDateScalarString {
    val validLocalDate: LocalDate = validLocalDateArb.sample(rs).value
    val invalidChoice = invalidChoiceArb.next(rs)
    val invalidConfig: InvalidConfig =
      invalidConfigArb.sample(rs).value.let {
        when (invalidChoice) {
          0 -> it
          1 -> it.copyWithOnlyOneInvalidProperty(rs)
          2 -> InvalidConfig.allEmpty
          3 ->
            return InvalidDateScalarString.ofYearMonthDay(
              nonLeapYearArb.next(rs),
              Month.FEBRUARY,
              29,
              debugDescription = "Feb29 of a non-leap year"
            )
          4 ->
            return InvalidDateScalarString.ofYearMonthDay(
              leapYearArb.next(rs),
              Month.FEBRUARY,
              30,
              debugDescription = "Feb30 of a leap year"
            )
          else -> throw Exception("invalid invalidChoice: $invalidChoice (error code btb7w6h236)")
        }
      }
    val edgeCaseFlags: EdgeCaseFlags = edgeCaseFlagsArb.sample(rs).value
    val validMonth = Month.of(validLocalDate.month)
    val validYear = Year.of(validLocalDate.year)
    return InvalidDateScalarString(
      year =
        if (edgeCaseFlags.year) yearStringEdgeCase(rs, invalidConfig.year, validYear)
        else yearString(rs, invalidConfig.year, validYear),
      dash1 =
        if (edgeCaseFlags.dash1) dashStringEdgeCase(rs, invalidConfig.dash1)
        else dashString(rs, invalidConfig.dash1),
      month =
        if (edgeCaseFlags.month) monthStringEdgeCase(rs, invalidConfig.month, validMonth)
        else monthString(rs, invalidConfig.month, validMonth),
      dash2 =
        if (edgeCaseFlags.dash2) dashStringEdgeCase(rs, invalidConfig.dash2)
        else dashString(rs, invalidConfig.dash2),
      day =
        if (!edgeCaseFlags.day)
          dayString(rs, invalidConfig.day, validLocalDate.day, validMonth, validYear)
        else
          dayStringEdgeCase(
            rs,
            invalidConfig.day,
            validLocalDate.day,
            validMonth,
            validYear,
          ),
      debugDescription =
        "invalidConfig=$invalidConfig, " +
          "invalidChoice=$invalidChoice, " +
          "edgeCaseFlags=$edgeCaseFlags, " +
          "validLocalDate=$validLocalDate",
    )
  }

  private fun yearString(rs: RandomSource, reason: InvalidIntReason, validValue: Year): String =
    intString(rs, reason, validValue.value, validYearRange)

  private fun yearStringEdgeCase(
    rs: RandomSource,
    reason: InvalidIntReason,
    validValue: Year
  ): String = intStringEdgeCase(rs, reason, validValue.value, validYearRange)

  private fun monthString(rs: RandomSource, reason: InvalidIntReason, validValue: Month): String =
    intString(rs, reason, validValue.value, validMonthRange)

  private fun monthStringEdgeCase(
    rs: RandomSource,
    reason: InvalidIntReason,
    validValue: Month
  ): String = intStringEdgeCase(rs, reason, validValue.value, validMonthRange)

  private fun dayString(
    rs: RandomSource,
    reason: InvalidIntReason,
    validValue: Int,
    month: Month,
    year: Year,
  ): String = intString(rs, reason, validValue, month.dayRangeInYear(year))

  private fun dayStringEdgeCase(
    rs: RandomSource,
    reason: InvalidIntReason,
    validValue: Int,
    month: Month,
    year: Year,
  ): String = intStringEdgeCase(rs, reason, validValue, month.dayRangeInYear(year))

  private fun dashString(rs: RandomSource, reason: InvalidDashReason): String =
    when (reason) {
      InvalidDashReason.Valid -> "-"
      InvalidDashReason.NonDashesOnly -> rs.nextNonDigitString()
      InvalidDashReason.Prefix -> rs.nextNonDigitString() + "-"
      InvalidDashReason.Suffix -> "-" + rs.nextNonDigitString()
      InvalidDashReason.PrefixAndSuffix -> rs.nextNonDigitString() + "-" + rs.nextNonDigitString()
      InvalidDashReason.MoreThanOneDash -> "-".repeat(rs.random.nextInt(2..5))
      InvalidDashReason.Empty -> ""
    }

  private fun dashStringEdgeCase(rs: RandomSource, reason: InvalidDashReason): String =
    when (reason) {
      InvalidDashReason.Valid -> "-"
      InvalidDashReason.NonDashesOnly -> rs.nextNonDigitChar().toString()
      InvalidDashReason.Prefix -> rs.nextNonDigitChar() + "-"
      InvalidDashReason.Suffix -> "-" + rs.nextNonDigitChar()
      InvalidDashReason.PrefixAndSuffix -> {
        val prefixIsEdgeCase = rs.nextIsEdgeCase()
        val suffixIsEdgeCase = !prefixIsEdgeCase || rs.nextIsEdgeCase()
        val prefix = if (prefixIsEdgeCase) rs.nextNonDigitChar() else rs.nextNonDigitString()
        val suffix = if (suffixIsEdgeCase) rs.nextNonDigitChar() else rs.nextNonDigitString()
        "$prefix-$suffix"
      }
      InvalidDashReason.MoreThanOneDash -> "-".repeat(rs.random.nextInt(2..5))
      InvalidDashReason.Empty -> ""
    }

  private fun intString(
    rs: RandomSource,
    reason: InvalidIntReason,
    validValue: Int,
    validRange: IntRange
  ): String {
    val validLength = validRange.last.toString().length
    return when (reason) {
      InvalidIntReason.Valid -> "$validValue".padStart(validLength, '0')
      InvalidIntReason.NonDigitsOnly -> rs.nextNonDigitString()
      InvalidIntReason.NonDigitPrefix -> rs.nextNonDigitString() + "$validValue"
      InvalidIntReason.NonDigitSuffix -> "$validValue" + rs.nextNonDigitString()
      InvalidIntReason.NonDigitPrefixAndSuffix ->
        rs.nextNonDigitString() + "$validValue" + rs.nextNonDigitString()
      InvalidIntReason.Padding -> {
        val validValueStr = "$validValue"
        val paddingLengths =
          (1..20).toList().filterNot { it == validLength || it < validValueStr.length }
        val paddingLength = paddingLengths.random(rs.random)
        validValueStr.padStart(paddingLength, '0')
      }
      InvalidIntReason.TooLarge -> "${rs.random.nextInt(validRange.last + 1..Int.MAX_VALUE)}"
      InvalidIntReason.TooSmall -> "${rs.random.nextInt(Int.MIN_VALUE until validRange.first)}"
      InvalidIntReason.Empty -> ""
    }
  }

  private fun intStringEdgeCase(
    rs: RandomSource,
    reason: InvalidIntReason,
    validValue: Int,
    validRange: IntRange
  ): String {
    val validLength = validRange.last.toString().length
    return when (reason) {
      InvalidIntReason.Valid -> "$validValue".padStart(validLength, '0')
      InvalidIntReason.NonDigitsOnly -> rs.nextNonDigitChar().toString()
      InvalidIntReason.NonDigitPrefix -> rs.nextNonDigitChar() + "$validValue"
      InvalidIntReason.NonDigitSuffix -> "$validValue" + rs.nextNonDigitChar()
      InvalidIntReason.NonDigitPrefixAndSuffix -> {
        val prefixIsEdgeCase = rs.nextIsEdgeCase()
        val suffixIsEdgeCase = !prefixIsEdgeCase || rs.nextIsEdgeCase()
        val prefix = if (prefixIsEdgeCase) rs.nextNonDigitChar() else rs.nextNonDigitString()
        val suffix = if (suffixIsEdgeCase) rs.nextNonDigitChar() else rs.nextNonDigitString()
        "$prefix$validValue$suffix"
      }
      InvalidIntReason.Padding -> {
        val validValueStr = "$validValue"
        val paddingLengths =
          (1..20).toList().filterNot { it == validLength || it < validValueStr.length }
        val paddingLength =
          if (rs.random.nextBoolean()) paddingLengths.first() else paddingLengths.last()
        validValueStr.padStart(paddingLength, '0')
      }
      InvalidIntReason.TooLarge -> "${validRange.last + 1}"
      InvalidIntReason.TooSmall -> "${validRange.first - 1}"
      InvalidIntReason.Empty -> ""
    }
  }

  private fun RandomSource.nextNonDigitString(): String = nonDigitStringArb.next(this)

  private fun RandomSource.nextNonDigitChar(): Char =
    nonDigitStringArb.map { it.first() }.filterNot { it == '-' }.next(this)

  private fun RandomSource.nextIsEdgeCase(): Boolean = random.nextDouble() < edgeCaseProbability

  private fun Arb.Companion.edgeCaseFlags(): Arb<EdgeCaseFlags> = arbitrary { rs ->
    EdgeCaseFlags(
      year = rs.nextIsEdgeCase(),
      dash1 = rs.nextIsEdgeCase(),
      month = rs.nextIsEdgeCase(),
      dash2 = rs.nextIsEdgeCase(),
      day = rs.nextIsEdgeCase(),
    )
  }

  private enum class InvalidIntReason {
    Valid,
    NonDigitsOnly,
    NonDigitPrefix,
    NonDigitSuffix,
    NonDigitPrefixAndSuffix,
    Padding,
    TooLarge,
    TooSmall,
    Empty,
  }

  private enum class InvalidDashReason {
    Valid,
    NonDashesOnly,
    Prefix,
    Suffix,
    PrefixAndSuffix,
    MoreThanOneDash,
    Empty,
  }

  private data class EdgeCaseFlags(
    val year: Boolean,
    val dash1: Boolean,
    val month: Boolean,
    val dash2: Boolean,
    val day: Boolean,
  )

  private data class InvalidConfig(
    val year: InvalidIntReason,
    val dash1: InvalidDashReason,
    val month: InvalidIntReason,
    val dash2: InvalidDashReason,
    val day: InvalidIntReason,
  ) {
    val isValid: Boolean =
      year == InvalidIntReason.Valid &&
        dash1 == InvalidDashReason.Valid &&
        month == InvalidIntReason.Valid &&
        dash2 == InvalidDashReason.Valid &&
        day == InvalidIntReason.Valid

    private fun invalidPropertyIndexes(): List<Int> =
      List(5) {
          val isValid =
            when (it) {
              0 -> year != InvalidIntReason.Valid
              1 -> dash1 != InvalidDashReason.Valid
              2 -> month != InvalidIntReason.Valid
              3 -> dash2 != InvalidDashReason.Valid
              4 -> day != InvalidIntReason.Valid
              else -> throw Exception("invalid index: $it (error code pdkcse2hsj)")
            }
          if (isValid) it else null
        }
        .filterNotNull()
        .also {
          require(it.isNotEmpty()) {
            "no invalid properties found in $this (error code 44te27r37h)"
          }
        }

    fun copyWithOnlyOneInvalidProperty(rs: RandomSource): InvalidConfig {
      val invalidProperties = invalidPropertyIndexes()
      return when (val invalidIndex = invalidProperties.random(rs.random)) {
        0 -> allValid.copy(year = year)
        1 -> allValid.copy(dash1 = dash1)
        2 -> allValid.copy(month = month)
        3 -> allValid.copy(dash2 = dash2)
        4 -> allValid.copy(day = day)
        else -> throw Exception("invalid index: $invalidIndex (error code 8bheyzyq7x)")
      }
    }

    companion object {
      val allValid: InvalidConfig
        get() =
          InvalidConfig(
            year = InvalidIntReason.Valid,
            dash1 = InvalidDashReason.Valid,
            month = InvalidIntReason.Valid,
            dash2 = InvalidDashReason.Valid,
            day = InvalidIntReason.Valid,
          )

      val allEmpty: InvalidConfig
        get() =
          InvalidConfig(
            year = InvalidIntReason.Empty,
            dash1 = InvalidDashReason.Empty,
            month = InvalidIntReason.Empty,
            dash2 = InvalidDashReason.Empty,
            day = InvalidIntReason.Empty,
          )
    }
  }

  private companion object {
    val validYearRange: IntRange = 0..9999
    val validMonthRange: IntRange = 1..12

    fun Arb.Companion.invalidConfig(): Arb<InvalidConfig> {
      val invalidIntReasonArb: Arb<InvalidIntReason> =
        Arb.choose(
          3 to Arb.constant(InvalidIntReason.Valid),
          1 to Arb.enum<InvalidIntReason>().filterNot { it == InvalidIntReason.Valid },
        )
      val invalidDashReasonArb: Arb<InvalidDashReason> =
        Arb.choose(
          3 to Arb.constant(InvalidDashReason.Valid),
          1 to Arb.enum<InvalidDashReason>().filterNot { it == InvalidDashReason.Valid },
        )
      return arbitrary {
          InvalidConfig(
            year = invalidIntReasonArb.bind(),
            dash1 = invalidDashReasonArb.bind(),
            month = invalidIntReasonArb.bind(),
            dash2 = invalidDashReasonArb.bind(),
            day = invalidIntReasonArb.bind(),
          )
        }
        .filterNot { it.isValid }
    }
  }
}

data class InvalidDateScalarString(
  val year: String,
  val dash1: String,
  val month: String,
  val dash2: String,
  val day: String,
  val debugDescription: String,
) {
  fun toDateScalarString() = "$year$dash1$month$dash2$day"

  override fun toString(): String =
    "InvalidDateScalarString(" +
      "year=$year, " +
      "dash1=$dash1, " +
      "month=$month, " +
      "dash2=$dash2, " +
      "day=$day, " +
      "toDateScalarString()=${toDateScalarString()}, " +
      "debugDescription=$debugDescription" +
      ")"

  companion object {
    fun ofYearMonthDay(year: Year, month: Month, day: Int, debugDescription: String) =
      InvalidDateScalarString(
        year = year.value.toString().padStart(4, '0'),
        month = month.value.toString().padStart(2, '0'),
        day = day.toString().padStart(2, '0'),
        dash1 = "-",
        dash2 = "-",
        debugDescription = debugDescription,
      )
  }
}
