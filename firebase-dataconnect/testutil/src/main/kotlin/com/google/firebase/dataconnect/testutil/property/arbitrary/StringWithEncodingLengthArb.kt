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
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.withEdgecases
import io.kotest.property.asSample

/**
 * Generates strings that satisfy the specified difference between its UTF-8 encoding and UTF-16
 * encoding.
 *
 * That is, if the given [mode] is [Mode.Utf8EncodingLongerThanUtf16] then the generated strings
 * will all have a UTF-8 encoding is more bytes than its UTF-16 encoding. Conversely, if [mode] is
 * [Mode.Utf8EncodingShorterThanOrEqualToUtf16] then the generated strings will all have a UTF-8
 * encoding that is fewer bytes than, or the same number of bytes as, its UTF-16 encoding.
 *
 * This is useful for testing algorithms that behave differently depending on the length of the
 * encodings. For example, an algorithm may choose to use the UTF-8 encoding if it is shorter than
 * or equal in length to the UTF-16 encoding, or the UTF-16 encoding if it is shorter than the UTF-8
 * encoding.
 */
class StringWithEncodingLengthArb(private val mode: Mode, lengthRange: IntRange) : Arb<String>() {

  private val lengthArb = run {
    require(lengthRange.last >= mode.minCharCount) {
      "lengthRange.last=${lengthRange.last}, but must be at least ${mode.minCharCount} " +
        "when mode=$mode is used"
    }
    val modifiedFirst = lengthRange.first.coerceAtLeast(mode.minCharCount)
    val modifiedLast = lengthRange.last
    val modifiedLengthRange = modifiedFirst..modifiedLast

    modifiedLengthRange.let { range ->
      val edgeCases = listOf(range.first, range.first + 1, range.last, range.last - 1)
      Arb.int(range).withEdgecases(edgeCases.distinct().filter { it in range })
    }
  }

  private val codePointArbs =
    listOf(
      CodePointArb(Arb.codepointWith1ByteUtf8Encoding(), utf8ByteCount = 1, utf16CharCount = 1),
      CodePointArb(Arb.codepointWith2ByteUtf8Encoding(), utf8ByteCount = 2, utf16CharCount = 1),
      CodePointArb(Arb.codepointWith3ByteUtf8Encoding(), utf8ByteCount = 3, utf16CharCount = 1),
      CodePointArb(Arb.codepointWith4ByteUtf8Encoding(), utf8ByteCount = 4, utf16CharCount = 2),
    )

  private val codePointArbsWithUtf16CharCountEquals1 =
    codePointArbs.filter { it.utf16CharCount == 1 }

  private val fixByteCountArbs = codePointArbs.filter(mode.fixedByteCountCodepointArbFilter)

  override fun sample(rs: RandomSource): Sample<String> =
    sample(
        rs,
        lengthEdgeCaseProbability = rs.random.nextFloat(),
        codepointEdgeCaseProbability = rs.random.nextFloat(),
      )
      .asSample()

  override fun edgecase(rs: RandomSource): String =
    when (val case = rs.random.nextInt(3)) {
      0 -> sample(rs, lengthEdgeCaseProbability = 1.0f, codepointEdgeCaseProbability = 0.0f)
      1 -> sample(rs, lengthEdgeCaseProbability = 0.0f, codepointEdgeCaseProbability = 1.0f)
      2 -> sample(rs, lengthEdgeCaseProbability = 1.0f, codepointEdgeCaseProbability = 1.0f)
      else -> throw IllegalStateException("unexpected case: $case [jb56se5ky9]")
    }

  private fun sample(
    rs: RandomSource,
    lengthEdgeCaseProbability: Float,
    codepointEdgeCaseProbability: Float,
  ): String {
    val length = lengthArb.next(rs, lengthEdgeCaseProbability)
    val generator = Generator(rs, length, codepointEdgeCaseProbability)
    generator.populateCodepoints()
    generator.fixByteCounts()
    return generator.toString()
  }

  sealed class Mode {
    abstract val minCharCount: Int

    abstract val fixedByteCountCodepointArbFilter: (CodePointArb) -> Boolean

    abstract fun byteCountNeedsFixing(utf8ByteCountsSum: Int, utf16ByteCountsSum: Int): Boolean

    abstract fun byteCountIsFixable(
      charCount: Int,
      utf8ByteCount: Int,
      utf16ByteCount: Int,
    ): Boolean

    fun findIndexToFix(
      charCounts: List<Int>,
      utf8ByteCounts: List<Int>,
      utf16ByteCounts: List<Int>,
    ): Int =
      utf8ByteCounts.indices.first {
        byteCountIsFixable(charCounts[it], utf8ByteCounts[it], utf16ByteCounts[it])
      }

    data object Utf8EncodingLongerThanUtf16 : Mode() {
      override val minCharCount = 1

      override val fixedByteCountCodepointArbFilter = { arb: CodePointArb ->
        arb.utf8ByteCount > arb.utf16ByteCount
      }

      override fun byteCountNeedsFixing(utf8ByteCountsSum: Int, utf16ByteCountsSum: Int) =
        utf8ByteCountsSum <= utf16ByteCountsSum

      override fun byteCountIsFixable(charCount: Int, utf8ByteCount: Int, utf16ByteCount: Int) =
        utf8ByteCount <= utf16ByteCount
    }

    data object Utf8EncodingShorterThanOrEqualToUtf16 : Mode() {
      override val minCharCount = 0

      override val fixedByteCountCodepointArbFilter = { arb: CodePointArb ->
        arb.utf8ByteCount <= arb.utf16ByteCount && arb.utf16CharCount == 1
      }

      override fun byteCountNeedsFixing(utf8ByteCountsSum: Int, utf16ByteCountsSum: Int) =
        utf8ByteCountsSum > utf16ByteCountsSum

      override fun byteCountIsFixable(charCount: Int, utf8ByteCount: Int, utf16ByteCount: Int) =
        utf8ByteCount > utf16ByteCount
    }
  }

  class CodePointArb(val arb: Arb<Codepoint>, val utf8ByteCount: Int, val utf16CharCount: Int) {
    val utf16ByteCount = utf16CharCount * 2
  }

  private inner class Generator(
    private val rs: RandomSource,
    private val length: Int,
    private val codepointEdgeCaseProbability: Float,
  ) {
    private val codepoints = mutableListOf<Int>()
    private val utf8ByteCounts = mutableListOf<Int>()
    private var utf8ByteCountsSum = 0
    private val utf16ByteCounts = mutableListOf<Int>()
    private var utf16ByteCountsSum = 0
    private val charCounts = mutableListOf<Int>()
    private var charCountsSum = 0

    fun populateCodepoints() {
      while (charCountsSum < length) {
        val codePointArb = run {
          val candidateCodePointArbs =
            if (charCountsSum + 1 == length) {
              codePointArbsWithUtf16CharCountEquals1
            } else {
              codePointArbs
            }
          candidateCodePointArbs.random(rs.random)
        }

        val codepoint = codePointArb.arb.next(rs, codepointEdgeCaseProbability).value
        check(Character.charCount(codepoint) == codePointArb.utf16CharCount) {
          "codepoint=$codepoint, charCount(codepoint)=${Character.charCount(codepoint)}, " +
            "codePointArb.utf16CharCount=${codePointArb.utf16CharCount}, but the char counts " +
            "should be equal [ka3sm2q7xm]"
        }

        codepoints.add(codepoint)
        utf8ByteCounts.add(codePointArb.utf8ByteCount)
        utf8ByteCountsSum += codePointArb.utf8ByteCount
        utf16ByteCounts.add(codePointArb.utf16ByteCount)
        utf16ByteCountsSum += codePointArb.utf16ByteCount
        charCounts.add(codePointArb.utf16CharCount)
        charCountsSum += codePointArb.utf16CharCount
      }

      check(charCountsSum == length) {
        "charCountsSum=$charCountsSum and length=$length, but they should be equal " +
          "(codepoints=$codepoints, utf8ByteCounts=$utf8ByteCounts, " +
          "utf16ByteCounts=$utf16ByteCounts, charCounts=$charCounts) [mvdxbck2sc]"
      }
    }

    fun fixByteCounts() {
      while (mode.byteCountNeedsFixing(utf8ByteCountsSum, utf16ByteCountsSum)) {
        val fixByteCountArb = fixByteCountArbs.random(rs.random)
        val index = mode.findIndexToFix(charCounts, utf8ByteCounts, utf16ByteCounts)

        codepoints.removeAt(index)
        val oldCharCount = charCounts[index]
        charCounts.removeAt(index)
        utf8ByteCountsSum -= utf8ByteCounts[index]
        utf8ByteCounts.removeAt(index)
        utf16ByteCountsSum -= utf16ByteCounts[index]
        utf16ByteCounts.removeAt(index)

        var newCharCount = 0
        while (newCharCount < oldCharCount) {
          val codepoint = fixByteCountArb.arb.next(rs, codepointEdgeCaseProbability).value
          val codepointCharCount = Character.charCount(codepoint)
          newCharCount += codepointCharCount

          codepoints.add(codepoint)
          charCounts.add(codepointCharCount)
          utf8ByteCountsSum += fixByteCountArb.utf8ByteCount
          utf8ByteCounts.add(fixByteCountArb.utf8ByteCount)
          utf16ByteCountsSum += fixByteCountArb.utf16ByteCount
          utf16ByteCounts.add(fixByteCountArb.utf16ByteCount)
        }

        check(newCharCount == oldCharCount) {
          "newCharCount=$newCharCount, oldCharCount=$oldCharCount, but they should be equal"
        }
      }
    }

    override fun toString(): String {
      codepoints.shuffle(rs.random)
      val sb = StringBuilder()
      codepoints.forEach(sb::appendCodePoint)
      return sb.toString()
    }
  }
}
