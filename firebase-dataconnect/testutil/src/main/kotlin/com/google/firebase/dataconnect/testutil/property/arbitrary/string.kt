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

package com.google.firebase.dataconnect.testutil.property.arbitrary

import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.Sample
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.asSample
import kotlin.Char.Companion.MAX_HIGH_SURROGATE
import kotlin.Char.Companion.MAX_LOW_SURROGATE
import kotlin.Char.Companion.MAX_SURROGATE
import kotlin.Char.Companion.MIN_HIGH_SURROGATE
import kotlin.Char.Companion.MIN_LOW_SURROGATE
import kotlin.Char.Companion.MIN_SURROGATE
import kotlin.random.nextInt

fun Arb.Companion.codepointWith1ByteUtf8Encoding(): Arb<Codepoint> =
  int(0 until 0x80).map(::Codepoint)

fun Arb.Companion.codepointWith2ByteUtf8Encoding(): Arb<Codepoint> =
  int(0x80 until 0x800).map(::Codepoint)

fun Arb.Companion.codepointWith3ByteUtf8Encoding(): Arb<Codepoint> =
  Arb.choice(
      int(0x800 until 0xd800),
      int(0xe000 until 0x10000),
    )
    .map(::Codepoint)

fun Arb.Companion.codepointWith4ByteUtf8Encoding(): Arb<Codepoint> =
  int(0x10000..0x10FFFF).map(::Codepoint)

fun Arb.Companion.codepointWithEvenNumByteUtf8EncodingDistribution(): Arb<Codepoint> =
  Arb.choice(
    codepointWith1ByteUtf8Encoding(),
    codepointWith2ByteUtf8Encoding(),
    codepointWith3ByteUtf8Encoding(),
    codepointWith4ByteUtf8Encoding(),
  )

fun Arb.Companion.stringWithEvenNumByteUtf8EncodingDistribution(length: IntRange): Arb<String> =
  string(length, codepointWithEvenNumByteUtf8EncodingDistribution())

fun Arb.Companion.stringWithLoneSurrogates(length: Int): Arb<StringWithLoneSurrogates> =
  StringWithLoneSurrogatesArb(length)

data class StringWithLoneSurrogates(val string: String, val loneSurrogateCount: Int)

private class StringWithLoneSurrogatesArb(private val length: Int) :
  Arb<StringWithLoneSurrogates>() {

  init {
    require(length > 0) { "invalid length: $length (start be greater than zero)" }
  }

  private val codepointArb: Arb<Codepoint> = Arb.codepointWithEvenNumByteUtf8EncodingDistribution()

  override fun sample(rs: RandomSource): Sample<StringWithLoneSurrogates> =
    rs.nextStringWithLoneSurrogates().asSample()

  private fun RandomSource.nextStringWithLoneSurrogates(
    length: Int = this@StringWithLoneSurrogatesArb.length,
    loneSurrogateCount: Int = 1 + random.nextInt(length),
    edgeCaseProbability: Float = random.nextFloat(),
  ): StringWithLoneSurrogates {
    require(length >= 0) { "invalid length: $length (must be greater than or equal to zero)" }
    require(loneSurrogateCount >= 0) {
      "invalid loneSurrogateCount: $loneSurrogateCount (must be greater than or equal to zero)"
    }
    require(loneSurrogateCount <= length) {
      "invalid loneSurrogateCount: $loneSurrogateCount (must be less than or equal to length=$length)"
    }

    val codepointGenerator = codepointArb.iterator(edgeCaseProbability)
    val codepoints = buildList {
      var charCount = loneSurrogateCount
      while (charCount != length) {
        val codepoint = codepointGenerator.next(this@nextStringWithLoneSurrogates).value
        val curCharCount = Character.charCount(codepoint)
        if (charCount + curCharCount <= length) {
          add(codepoint)
          charCount += curCharCount
        }
      }
    }

    val codepointsAndChars =
      buildList<Any> {
        addAll(codepoints)
        repeat(loneSurrogateCount) { add(nextSurrogateChar(edgeCaseProbability)) }

        shuffle(random)

        // Make sure that lone high surrogates are NOT followed by lone low surrogates; otherwise,
        // they will combine to make a valid code point. To rectify this, subtract 1024 from the
        // low surrogate to convert it to a high surrogate.
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

  override fun edgecase(rs: RandomSource): StringWithLoneSurrogates {
    val surrogateEdgeCaseProbability = rs.random.nextFloat()

    fun StringBuilder.appendStringWithNoLoneSurrogates(length: Int) {
      val sample = rs.nextStringWithLoneSurrogates(length = length, loneSurrogateCount = 0)
      append(sample.string)
    }

    fun StringBuilder.appendSurrogateChar() {
      append(rs.nextSurrogateChar(edgeCaseProbability = surrogateEdgeCaseProbability))
    }

    return when (EdgeCase.supportingLength(length).random(rs.random)) {
      EdgeCase.AllLoneSurrogates ->
        rs.nextStringWithLoneSurrogates(length = length, loneSurrogateCount = length)
      EdgeCase.BeginsWithLoneSurrogate ->
        StringWithLoneSurrogates(
          buildString {
            appendSurrogateChar()
            appendStringWithNoLoneSurrogates(this@StringWithLoneSurrogatesArb.length - 1)
          },
          1
        )
      EdgeCase.EndsWithLoneSurrogate ->
        StringWithLoneSurrogates(
          buildString {
            appendStringWithNoLoneSurrogates(this@StringWithLoneSurrogatesArb.length - 1)
            appendSurrogateChar()
          },
          1
        )
      EdgeCase.BeginsAndEndsWithLoneSurrogate ->
        StringWithLoneSurrogates(
          buildString {
            appendSurrogateChar()
            appendStringWithNoLoneSurrogates(this@StringWithLoneSurrogatesArb.length - 2)
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
