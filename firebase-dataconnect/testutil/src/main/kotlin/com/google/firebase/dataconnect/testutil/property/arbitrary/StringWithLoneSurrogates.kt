/*
 * Copyright 2026 Google LLC
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

import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.Sample
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.int
import io.kotest.property.asSample
import kotlin.Char.Companion.MAX_HIGH_SURROGATE
import kotlin.Char.Companion.MAX_LOW_SURROGATE
import kotlin.Char.Companion.MAX_SURROGATE
import kotlin.Char.Companion.MIN_HIGH_SURROGATE
import kotlin.Char.Companion.MIN_LOW_SURROGATE
import kotlin.Char.Companion.MIN_SURROGATE

/**
 * Generates Strings with the given length that contain at least 1 UTF-16 "lone surrogate"
 * character.
 */
fun Arb.Companion.stringWithLoneSurrogates(lengthRange: IntRange): Arb<StringWithLoneSurrogates> =
  StringWithLoneSurrogatesArb(lengthRange)

data class StringWithLoneSurrogates(val string: String, val loneSurrogateCount: Int)

private class StringWithLoneSurrogatesArb(lengthRange: IntRange) : Arb<StringWithLoneSurrogates>() {

  init {
    require(lengthRange.first > 0) {
      "lengthRange.first must be greater than zero, but got ${lengthRange.first}"
    }
    require(lengthRange.last >= lengthRange.first) {
      "lengthRange.last (${lengthRange.last}) must be greater than or equal to " +
        "lengthRange.first (${lengthRange.first})"
    }
  }

  private val lengthArb = Arb.int(lengthRange)

  private val generator = StringWithLoneSurrogatesGenerator()

  override fun sample(rs: RandomSource): Sample<StringWithLoneSurrogates> =
    generator.sample(rs, rs.nextLength(edgeCaseProbability = 0.1f)).asSample()

  override fun edgecase(rs: RandomSource): StringWithLoneSurrogates =
    generator.edgecase(rs, rs.nextLength(edgeCaseProbability = 0.5f))

  private fun RandomSource.nextLength(edgeCaseProbability: Float): Int =
    if (random.nextFloat() < edgeCaseProbability) {
      lengthArb.edgecase(this)!!
    } else {
      lengthArb.sample(this).value
    }
}

private class StringWithLoneSurrogatesGenerator {
  private val codepointArb: Arb<Codepoint> = Arb.codepointWithEvenNumByteUtf8EncodingDistribution()

  fun sample(rs: RandomSource, length: Int): StringWithLoneSurrogates =
    rs.nextStringWithLoneSurrogates(length)

  private fun RandomSource.nextStringWithLoneSurrogates(
    length: Int,
    loneSurrogateCount: Int = 1 + random.nextInt(length),
    edgeCaseProbability: Float = random.nextFloat(),
  ): StringWithLoneSurrogates {
    require(length >= 0) { "invalid length: $length (must be greater than or equal to zero)" }
    require(loneSurrogateCount >= 0) {
      "invalid loneSurrogateCount: $loneSurrogateCount (must be greater than or equal to zero)"
    }
    require(loneSurrogateCount <= length) {
      "invalid loneSurrogateCount: $loneSurrogateCount " +
        "(must be less than or equal to length=$length)"
    }

    val codepoints = buildList {
      val codepointGenerator = codepointArb.iterator(edgeCaseProbability)
      var charCountRemaining = length - loneSurrogateCount
      while (charCountRemaining > 0) {
        val codepoint = codepointGenerator.next(this@nextStringWithLoneSurrogates).value
        val codepointCharCount = Character.charCount(codepoint)
        if (codepointCharCount <= charCountRemaining) {
          add(codepoint)
          charCountRemaining -= codepointCharCount
        }
      }
    }

    val codepointsAndChars =
      buildList<Any> {
        addAll(codepoints)
        repeat(loneSurrogateCount) { add(nextSurrogateChar(edgeCaseProbability)) }

        shuffle(random)

        // Make sure that lone high surrogates are NOT followed by lone low surrogates; otherwise,
        // they will combine to make a valid code point. To rectify this, subtract 1024 from a lone
        // low surrogate that follows a high surrogate to convert the low surrogate to a high
        // surrogate, thus breaking the valid combination.
        forEachIndexed { index, element ->
          if (index > 0 && element is Char && element.isLowSurrogate()) {
            val previousElement = get(index - 1)
            if (previousElement is Char && previousElement.isHighSurrogate()) {
              set(index, element - 1024)
            }
          }
        }
      }

    val string = buildString {
      codepointsAndChars.forEach {
        when (it) {
          is Char -> append(it)
          is Int -> appendCodePoint(it)
          else ->
            throw IllegalStateException(
              "internal error: it=$it should be Char or Int, but got ${it::class.qualifiedName}"
            )
        }
      }
    }

    check(string.length == length) {
      "internal error: string.length=${string.length} and length=$length, " +
        "but they should be equal (loneSurrogateCount=$loneSurrogateCount, " +
        "codepoints=$codepoints, codepointsAndChars=$codepointsAndChars)"
    }

    return StringWithLoneSurrogates(string, loneSurrogateCount)
  }

  fun edgecase(rs: RandomSource, length: Int): StringWithLoneSurrogates {
    val surrogateEdgeCaseProbability = rs.random.nextFloat()

    fun StringBuilder.appendStringWithNoLoneSurrogates(length: Int) {
      val sample = rs.nextStringWithLoneSurrogates(length = length, loneSurrogateCount = 0)
      append(sample.string)
    }

    fun StringBuilder.appendSurrogateChar() {
      append(rs.nextSurrogateChar(edgeCaseProbability = surrogateEdgeCaseProbability))
    }

    val stringWithLoneSurrogatesLength = length

    return when (EdgeCase.supportingLength(length).random(rs.random)) {
      EdgeCase.AllLoneSurrogates ->
        rs.nextStringWithLoneSurrogates(
          length = stringWithLoneSurrogatesLength,
          loneSurrogateCount = stringWithLoneSurrogatesLength
        )
      EdgeCase.BeginsWithLoneSurrogate ->
        StringWithLoneSurrogates(
          buildString {
            appendSurrogateChar()
            appendStringWithNoLoneSurrogates(stringWithLoneSurrogatesLength - 1)
          },
          1
        )
      EdgeCase.EndsWithLoneSurrogate ->
        StringWithLoneSurrogates(
          buildString {
            appendStringWithNoLoneSurrogates(stringWithLoneSurrogatesLength - 1)
            appendSurrogateChar()
          },
          1
        )
      EdgeCase.BeginsAndEndsWithLoneSurrogate ->
        StringWithLoneSurrogates(
          buildString {
            appendSurrogateChar()
            appendStringWithNoLoneSurrogates(stringWithLoneSurrogatesLength - 2)
            appendSurrogateChar()
          },
          2
        )
    }
  }

  private enum class EdgeCase(val supportsLength1: Boolean, val supportsLength2: Boolean) {
    AllLoneSurrogates(true, true),
    BeginsWithLoneSurrogate(false, true),
    EndsWithLoneSurrogate(false, true),
    BeginsAndEndsWithLoneSurrogate(false, false);

    companion object {

      val instancesSupportingLength1 = entries.filter { it.supportsLength1 }
      val instancesSupportingLength2 = entries.filter { it.supportsLength2 }

      fun supportingLength(length: Int): List<EdgeCase> =
        if (length == 1) {
          instancesSupportingLength1
        } else if (length == 2) {
          instancesSupportingLength2
        } else {
          entries
        }
    }
  }

  private companion object {
    val highSurrogateRange: CharRange = MIN_HIGH_SURROGATE..MAX_HIGH_SURROGATE
    val lowSurrogateRange: CharRange = MIN_LOW_SURROGATE..MAX_LOW_SURROGATE
    val surrogateRange: CharRange = MIN_SURROGATE..MAX_SURROGATE

    val highSurrogateEdgeCases = listOf(highSurrogateRange.first, highSurrogateRange.last)
    val lowSurrogateEdgeCases = listOf(lowSurrogateRange.first, lowSurrogateRange.last)
    val surrogateEdgeCases = highSurrogateEdgeCases + lowSurrogateEdgeCases

    fun RandomSource.nextSurrogateChar(edgeCaseProbability: Float): Char =
      if (random.nextFloat() < edgeCaseProbability) {
        surrogateEdgeCases.random(random)
      } else {
        surrogateRange.random(random)
      }
  }
}
