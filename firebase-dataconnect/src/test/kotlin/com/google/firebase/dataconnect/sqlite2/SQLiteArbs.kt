/*
 * Copyright 2025 Google LLC
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

package com.google.firebase.dataconnect.sqlite2

import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.Sample
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.string
import io.kotest.property.asSample
import kotlin.random.nextInt

object SQLiteArbs {

  // Omit apostrophe (') because it gets tested explicitly.
  // Omit question mark (?) because it also gets tested explicitly.
  // Omit newlines because their effects when trimming indents are unpredictable for tests.
  private val sqliteSpecialCharCodes = listOf('\'', '?', '\r', '\n').map { it.code }

  fun codepointsWithoutSqliteSpecialChars() =
    Arb.dataConnect.codepoints.filterNot { sqliteSpecialCharCodes.contains(it.value) }

  fun stringWithoutSqliteSpecialChars() = Arb.string(0..20, codepointsWithoutSqliteSpecialChars())

  fun stringWithApostrophes(
    stringWithoutSqliteSpecialChars: Arb<String> = stringWithoutSqliteSpecialChars(),
    apostropheCount: IntRange = 1..3,
  ): Arb<StringWithApostrophes> =
    StringWithApostrophesArb(stringWithoutSqliteSpecialChars, apostropheCount)

  fun roundTrippableFloat(): Arb<Float> = Arb.float().filterNot { it.isNaN() || it == -0.0f }

  fun roundTrippableDouble(): Arb<Double> = Arb.double().filterNot { it.isNaN() || it == -0.0 }
}

val Arb.Companion.sqlite: SQLiteArbs
  get() = SQLiteArbs

data class StringWithApostrophes(
  val stringWithApostrophes: String,
  val stringWithApostrophesEscaped: String,
  val apostropheCount: Int,
) {
  companion object {

    fun beginsWithApostrophes(apostropheCount: Int, suffix: String): StringWithApostrophes =
      StringWithApostrophes(
        stringWithApostrophes = stringWithApostropheCount(apostropheCount) + suffix,
        stringWithApostrophesEscaped = stringWithApostropheCountEscaped(apostropheCount) + suffix,
        apostropheCount = apostropheCount,
      )

    fun endsWithApostrophes(apostropheCount: Int, prefix: String): StringWithApostrophes =
      StringWithApostrophes(
        stringWithApostrophes = prefix + stringWithApostropheCount(apostropheCount),
        stringWithApostrophesEscaped = prefix + stringWithApostropheCountEscaped(apostropheCount),
        apostropheCount = apostropheCount,
      )

    fun beginsAndEndsWithApostrophes(
      leadingApostropheCount: Int,
      trailingApostropheCount: Int,
      s: String
    ): StringWithApostrophes =
      StringWithApostrophes(
        stringWithApostrophes =
          stringWithApostropheCount(leadingApostropheCount) +
            s +
            stringWithApostropheCount(trailingApostropheCount),
        stringWithApostrophesEscaped =
          stringWithApostropheCountEscaped(leadingApostropheCount) +
            s +
            stringWithApostropheCountEscaped(trailingApostropheCount),
        apostropheCount = leadingApostropheCount + trailingApostropheCount,
      )

    private fun stringWithApostropheCount(apostropheCount: Int): String = buildString {
      repeat(apostropheCount) { append("'") }
    }

    private fun stringWithApostropheCountEscaped(apostropheCount: Int): String = buildString {
      repeat(apostropheCount) { append("''") }
    }
  }
}

private class StringWithApostrophesArb(
  private val stringWithoutSqliteSpecialChars: Arb<String>,
  private val apostropheCount: IntRange
) : Arb<StringWithApostrophes>() {

  init {
    require(apostropheCount.start >= 1) {
      "invalid apostrophe count: $apostropheCount (must start at least at 1)"
    }
  }

  override fun edgecase(rs: RandomSource): StringWithApostrophes {
    val s: String by
      lazy(LazyThreadSafetyMode.NONE) {
        when (rs.random.nextBoolean()) {
          true -> stringWithoutSqliteSpecialChars.edgecase(rs)!!
          false -> stringWithoutSqliteSpecialChars.next(rs)
        }
      }
    val apostropheCount =
      when (EdgeCaseApostropheCount.entries.random(rs.random)) {
        EdgeCaseApostropheCount.Minimum -> apostropheCount.start
        EdgeCaseApostropheCount.Maximum -> apostropheCount.endInclusive
        EdgeCaseApostropheCount.Random -> apostropheCount.random(rs.random)
      }
    check(apostropheCount > 0) { "invalid apostropheCount generated: $apostropheCount" }
    return when (apostropheCount) {
      1 ->
        when (EdgeCaseStringsWithOneApostrophe.entries.random(rs.random)) {
          EdgeCaseStringsWithOneApostrophe.BeginsWithApostrophe ->
            StringWithApostrophes.beginsWithApostrophes(1, s)
          EdgeCaseStringsWithOneApostrophe.EndsWithApostrophe ->
            StringWithApostrophes.endsWithApostrophes(1, s)
        }
      2 ->
        when (EdgeCaseStringsWithTwoApostrophes.entries.random(rs.random)) {
          EdgeCaseStringsWithTwoApostrophes.BeginsWithApostrophes ->
            StringWithApostrophes.beginsWithApostrophes(2, s)
          EdgeCaseStringsWithTwoApostrophes.EndsWithApostrophes ->
            StringWithApostrophes.endsWithApostrophes(2, s)
          EdgeCaseStringsWithTwoApostrophes.BeginsAndEndsWithApostrophes ->
            StringWithApostrophes.beginsAndEndsWithApostrophes(1, 1, s)
        }
      else ->
        when (EdgeCaseStringsWithThreeOrMoreApostrophes.entries.random(rs.random)) {
          EdgeCaseStringsWithThreeOrMoreApostrophes.BeginsWithApostrophes ->
            StringWithApostrophes.beginsWithApostrophes(apostropheCount, s)
          EdgeCaseStringsWithThreeOrMoreApostrophes.EndsWithApostrophes ->
            StringWithApostrophes.endsWithApostrophes(apostropheCount, s)
          EdgeCaseStringsWithThreeOrMoreApostrophes.BeginsAndEndsWithApostrophes -> {
            val leadingApostropheCount = 1 + rs.random.nextInt(apostropheCount - 2)
            val trailingApostropheCount = apostropheCount - leadingApostropheCount
            StringWithApostrophes.beginsAndEndsWithApostrophes(
              leadingApostropheCount,
              trailingApostropheCount,
              s
            )
          }
          EdgeCaseStringsWithThreeOrMoreApostrophes.BeginsAndEndsWithAndContainsApostrophes -> {
            val stringWithApostrophes = s.map { it.toString() }.toMutableList()
            val stringWithApostrophesEscaped = stringWithApostrophes.toMutableList()
            stringWithApostrophes.add("'")
            stringWithApostrophes.add(0, "'")
            stringWithApostrophesEscaped.add("''")
            stringWithApostrophesEscaped.add(0, "''")
            repeat(apostropheCount - 2) {
              val index = rs.random.nextInt(stringWithApostrophes.size)
              stringWithApostrophes.add(index, "'")
              stringWithApostrophesEscaped.add(index, "''")
            }
            StringWithApostrophes(
              stringWithApostrophes = stringWithApostrophes.joinToString(""),
              stringWithApostrophesEscaped = stringWithApostrophesEscaped.joinToString(""),
              apostropheCount = apostropheCount,
            )
          }
        }
    }
  }

  override fun sample(rs: RandomSource): Sample<StringWithApostrophes> {
    val stringWithoutSqliteSpecialChars = stringWithoutSqliteSpecialChars.next(rs)
    val stringWithApostrophes =
      stringWithoutSqliteSpecialChars.map { it.toString() }.toMutableList()
    val stringWithApostrophesEscaped = stringWithApostrophes.toMutableList()
    val apostropheCount = rs.random.nextInt(apostropheCount)
    repeat(apostropheCount) {
      val insertIndex = rs.random.nextInt(stringWithApostrophes.size + 1)
      stringWithApostrophes.add(insertIndex, "'")
      stringWithApostrophesEscaped.add(insertIndex, "''")
    }
    return StringWithApostrophes(
        stringWithApostrophes = stringWithApostrophes.joinToString(""),
        stringWithApostrophesEscaped = stringWithApostrophesEscaped.joinToString(""),
        apostropheCount = apostropheCount,
      )
      .asSample()
  }

  private enum class EdgeCaseApostropheCount {
    Minimum,
    Maximum,
    Random,
  }
  private enum class EdgeCaseStringsWithOneApostrophe {
    BeginsWithApostrophe,
    EndsWithApostrophe,
  }
  private enum class EdgeCaseStringsWithTwoApostrophes {
    BeginsWithApostrophes,
    EndsWithApostrophes,
    BeginsAndEndsWithApostrophes,
  }
  private enum class EdgeCaseStringsWithThreeOrMoreApostrophes {
    BeginsWithApostrophes,
    EndsWithApostrophes,
    BeginsAndEndsWithApostrophes,
    BeginsAndEndsWithAndContainsApostrophes,
  }
}
