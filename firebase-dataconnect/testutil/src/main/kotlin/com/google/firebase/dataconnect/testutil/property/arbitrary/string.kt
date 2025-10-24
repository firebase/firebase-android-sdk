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
  private fun RandomSource.nextStringLength(): Int = random.nextInt(lengthRange)
  private fun RandomSource.nextStringLengthEdgeCase(): Int =
    if (random.nextBoolean()) lengthRange.first else lengthRange.last

  private val codepointArb: Arb<Codepoint> = Arb.codepointWithEvenNumByteUtf8EncodingDistribution()

  override fun sample(rs: RandomSource): Sample<StringWithLoneSurrogates> =
    rs.nextStringWithLoneSurrogates().asSample()

  private fun RandomSource.nextStringWithLoneSurrogates(
    length: Int = nextStringLength(),
    loneSurrogateProbability: Float = random.nextFloat(),
    edgeCaseProbability: Float = random.nextFloat(),
  ): StringWithLoneSurrogates {
    fun RandomSource.nextCharIsLoneSurrogate(): Boolean =
      random.nextFloat() < loneSurrogateProbability
    val codepointGenerator = codepointArb.iterator(edgeCaseProbability)

    var loneSurrogateCount = 0
    val sb = StringBuilder()
    while (sb.length < length) {
      if (!nextCharIsLoneSurrogate()) {
        val codePoint: Int = codepointGenerator.next(this@nextStringWithLoneSurrogates).value
        if (sb.length + Character.charCount(codePoint) <= length) {
          sb.appendCodePoint(codePoint)
        }
      } else {
        loneSurrogateCount++
        val char: Char =
          if (sb.lastIsHighSurrogate()) {
            nextHighSurrogateChar(edgeCaseProbability)
          } else {
            nextSurrogateChar(edgeCaseProbability)
          }
        sb.append(char)
      }
    }

    check(sb.length == length) { "internal error: sb.length=${sb.length} but length=$length" }
    return StringWithLoneSurrogates(sb.toString(), loneSurrogateCount)
  }

  private fun RandomSource.nextStringWithoutLoneSurrogates(
    length: Int,
    edgeCaseProbability: Float
  ): String {
    val sample =
      nextStringWithLoneSurrogates(
        length = length,
        loneSurrogateProbability = 0.0f,
        edgeCaseProbability = edgeCaseProbability,
      )
    check(sample.loneSurrogateCount == 0) {
      "internal error: sample.loneSurrogateCount=${sample.loneSurrogateCount} but expected 0"
    }
    return sample.string
  }

  override fun edgecase(rs: RandomSource): StringWithLoneSurrogates {
    val edgeCaseProbability = rs.random.nextFloat()

    val length =
      when (EdgeCase.entries.random(rs.random)) {
        EdgeCase.StringLength ->
          return rs.nextStringWithLoneSurrogates(
            length = rs.nextStringLengthEdgeCase(),
            edgeCaseProbability = edgeCaseProbability
          )
        EdgeCase.Contents -> rs.nextStringLength()
        EdgeCase.StringLengthAndContents -> rs.nextStringLengthEdgeCase()
      }

    return when (ContentsEdgeCase.entries.random(rs.random)) {
      ContentsEdgeCase.AllLoneSurrogates ->
        rs.nextStringWithLoneSurrogates(
          length = length,
          loneSurrogateProbability = 1.0f,
          edgeCaseProbability = edgeCaseProbability
        )
      ContentsEdgeCase.BeginsWithLoneSurrogate ->
        StringWithLoneSurrogates(
          buildString {
            append(rs.nextSurrogateChar(edgeCaseProbability = edgeCaseProbability))
            append(
              rs.nextStringWithoutLoneSurrogates(
                length = length - 1,
                edgeCaseProbability = edgeCaseProbability
              )
            )
          },
          1
        )
      ContentsEdgeCase.EndsWithLoneSurrogate ->
        StringWithLoneSurrogates(
          buildString {
            append(
              rs.nextStringWithoutLoneSurrogates(
                length = length - 1,
                edgeCaseProbability = edgeCaseProbability
              )
            )
            append(rs.nextSurrogateChar(edgeCaseProbability = edgeCaseProbability))
          },
          1
        )
      ContentsEdgeCase.BeginsAndEndsWithLoneSurrogate ->
        if (length <= 2) {
          rs.nextStringWithLoneSurrogates(
            length = length,
            loneSurrogateProbability = 1.0f,
            edgeCaseProbability = edgeCaseProbability
          )
        } else {
          StringWithLoneSurrogates(
            buildString {
              append(rs.nextSurrogateChar(edgeCaseProbability = edgeCaseProbability))
              append(
                rs.nextStringWithoutLoneSurrogates(
                  length = length - 2,
                  edgeCaseProbability = edgeCaseProbability
                )
              )
              append(rs.nextSurrogateChar(edgeCaseProbability = edgeCaseProbability))
            },
            2
          )
        }
    }
  }

  private enum class EdgeCase {
    StringLength,
    Contents,
    StringLengthAndContents,
  }

  private enum class ContentsEdgeCase {
    AllLoneSurrogates,
    BeginsWithLoneSurrogate,
    EndsWithLoneSurrogate,
    BeginsAndEndsWithLoneSurrogate,
  }

  private companion object {
    val highSurrogateRange: CharRange = MIN_HIGH_SURROGATE..MAX_HIGH_SURROGATE
    val lowSurrogateRange: CharRange = MIN_LOW_SURROGATE..MAX_LOW_SURROGATE
    val surrogateRange: CharRange = MIN_SURROGATE..MAX_SURROGATE

    val highSurrogateEdgeCases = listOf(highSurrogateRange.first, highSurrogateRange.last)
    val lowSurrogateEdgeCases = listOf(lowSurrogateRange.first, lowSurrogateRange.last)
    val surrogateEdgeCases = highSurrogateEdgeCases + lowSurrogateEdgeCases

    fun RandomSource.nextSurrogateChar(edgeCaseProbability: Float): Char =
      nextChar(edgeCaseProbability, surrogateRange, surrogateEdgeCases)

    fun RandomSource.nextHighSurrogateChar(edgeCaseProbability: Float): Char =
      nextChar(edgeCaseProbability, highSurrogateRange, highSurrogateEdgeCases)

    fun RandomSource.nextChar(
      edgeCaseProbability: Float,
      range: CharRange,
      edgeCases: List<Char>
    ): Char =
      if (random.nextFloat() < edgeCaseProbability) {
        edgeCases.random(random)
      } else {
        range.random(random)
      }

    fun CharSequence.lastIsHighSurrogate(): Boolean =
      lastOrNull().let { it !== null && it.isHighSurrogate() }
  }
}
