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

fun Arb.Companion.stringWithLoneSurrogates(length: IntRange): Arb<StringWithLoneSurrogates> =
  StringWithLoneSurrogatesArb(length)

data class StringWithLoneSurrogates(val string: String, val loneSurrogateCount: Int)

private class StringWithLoneSurrogatesArb(length: IntRange) : Arb<StringWithLoneSurrogates>() {

  init {
    require(!length.isEmpty()) { "invalid length range: $length (must be a non-empty range)" }
    require(length.start > 0) { "invalid length range: $length (start must be greater than zero)" }
  }

  private val lengthRange: IntRange = length
  private fun RandomSource.nextStringWithLoneSurrogatesLength(): Int = random.nextInt(lengthRange)
  private val codepointArb: Arb<Codepoint> = Arb.codepointWithEvenNumByteUtf8EncodingDistribution()
  private fun RandomSource.nextCodepoint(): Codepoint = codepointArb.sample(this).value

  override fun sample(rs: RandomSource): Sample<StringWithLoneSurrogates> {
    val length = rs.nextStringWithLoneSurrogatesLength()
    check(length > 0) { "internal error: invalid length: $length" }
    val loneSurrogateProbability = rs.random.nextDouble()

    val loneSurrogateIndices = mutableListOf<Int>()
    val codePoints: List<CodePointType> = buildList {
      var codePointCount = 0
      var charCount = 0
      while (charCount != length) {
        check(charCount < length) {
          "internal error v9gcbvh7cq: charCount ($charCount) should be less than length ($length)"
        }

        val isLoneSurrogate = rs.random.nextDouble() < loneSurrogateProbability

        val curCharCount: Int =
          if (isLoneSurrogate) {
            loneSurrogateIndices.add(codePointCount)
            1
          } else {
            val codePoint: CodePointType.NonLoneSurrogate =
              if (charCount + 1 == length) {
                rs.nextNonSurrogate()
              } else {
                rs.nextNonLoneSurrogate()
              }
            add(codePoint)
            codePoint.charCount
          }

        charCount += curCharCount
        codePointCount++
      }

      // Make sure there is at least 1 lone surrogate
      if (loneSurrogateIndices.isEmpty()) {
        val index = rs.random.nextInt(size)
        loneSurrogateIndices.add(index)
        val removedCodePoint = removeAt(index)
        when (removedCodePoint.charCount) {
          1 -> {}
          2 -> add(index, rs.nextNonSurrogate())
          else ->
            throw IllegalStateException(
              "internal error hcxwv7w6sq: " +
                "unexpected removedCodePoint.charCount: ${removedCodePoint.charCount} (expected 1 or 2)"
            )
        }
      }

      // Insert the lone surrogates
      loneSurrogateIndices.forEach { i ->
        if (i == 0 || get(i - 1) !is CodePointType.LoneHighSurrogate) {
          add(i, rs.nextLoneSurrogate())
        } else {
          add(i, rs.nextLoneHighSurrogate())
        }
      }
    }

    // Assemble the string from the chosen code points.
    val string = buildString {
      codePoints.forEach { codePoint ->
        when (codePoint) {
          is CodePointType.SingleCharCodePointType -> append(codePoint.char)
          is CodePointType.SurrogatePair -> append(codePoint.char1).append(codePoint.char2)
        }
      }
    }

    return StringWithLoneSurrogates(string, loneSurrogateIndices.size).asSample()
  }

  override fun edgecase(rs: RandomSource): StringWithLoneSurrogates? {
    return null // TODO("not yet implemented")
  }

  private sealed interface CodePointType {
    val charCount: Int

    sealed class SingleCharCodePointType(val char: Char) : CodePointType {
      override val charCount
        get() = 1
    }

    sealed interface NonLoneSurrogate : CodePointType
    class NonSurrogate(char: Char) : SingleCharCodePointType(char), NonLoneSurrogate

    sealed class LoneSurrogate(char: Char) : SingleCharCodePointType(char)
    class LoneHighSurrogate(char: Char) : LoneSurrogate(char)
    class LoneLowSurrogate(char: Char) : LoneSurrogate(char)

    class SurrogatePair(val char1: Char, val char2: Char) : CodePointType, NonLoneSurrogate {
      override val charCount
        get() = 2
    }
  }

  private val toCharsBuffer = CharArray(2)

  private fun RandomSource.nextNonLoneSurrogate(): CodePointType.NonLoneSurrogate =
    nextCodepoint().let { codepoint ->
      when (val charCount = Character.toChars(codepoint.value, toCharsBuffer, 0)) {
        1 -> CodePointType.NonSurrogate(toCharsBuffer[0])
        2 -> CodePointType.SurrogatePair(toCharsBuffer[0], toCharsBuffer[1])
        else ->
          throw IllegalStateException(
            "internal error hndtga9jqh: Character.toChars() returned $charCount, " +
              "but expected 1 or 2; codepoint.value=${codepoint.value}"
          )
      }
    }

  companion object {

    private val highSurrogateRange: CharRange = MIN_HIGH_SURROGATE..MAX_HIGH_SURROGATE
    private val lowSurrogateRange: CharRange = MIN_LOW_SURROGATE..MAX_LOW_SURROGATE
    private val nonSurrogateRange1: CharRange = Char.MIN_VALUE until MIN_SURROGATE
    private val nonSurrogateRange2: CharRange = MAX_SURROGATE + 1..Char.MAX_VALUE

    private fun RandomSource.nextChar(charRange: CharRange): Char =
      random.nextInt(charRange.first.code, charRange.last.code + 1).toChar()

    private fun RandomSource.nextHighSurrogateChar(): Char = nextChar(highSurrogateRange)
    private fun RandomSource.nextLowSurrogateChar(): Char = nextChar(lowSurrogateRange)

    private fun RandomSource.nextNonSurrogateChar(): Char =
      nextChar(if (random.nextBoolean()) nonSurrogateRange1 else nonSurrogateRange2)

    private fun RandomSource.nextLoneHighSurrogate() =
      CodePointType.LoneHighSurrogate(nextHighSurrogateChar())
    private fun RandomSource.nextLoneLowSurrogate() =
      CodePointType.LoneLowSurrogate(nextLowSurrogateChar())
    private fun RandomSource.nextLoneSurrogate(): CodePointType.LoneSurrogate =
      if (random.nextBoolean()) nextLoneHighSurrogate() else nextLoneLowSurrogate()
    private fun RandomSource.nextNonSurrogate() = CodePointType.NonSurrogate(nextNonSurrogateChar())
  }
}
