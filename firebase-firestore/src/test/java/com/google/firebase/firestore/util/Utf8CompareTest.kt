@file:OptIn(ExperimentalKotest::class)

package com.google.firebase.firestore.util

import com.google.protobuf.ByteString
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlin.math.absoluteValue
import kotlinx.coroutines.test.runTest
import org.junit.Test

class Utf8CompareTest {

  @Test
  fun `should compare the same as byte arrays from ByteString copyFromUtf8`() = runTest {
    val testCaseArb = Arb.utf8CompareTestCase()
    val propTestConfig = PropTestConfig(iterations = 1_000_000, seed = 123123)
    checkAll(propTestConfig, testCaseArb) { testCase ->
      // Generate a unique "testCaseId" so that it's easy to set a conditional breakpoint
      // for a specifically-failing test case.
      val testCaseId = testCase.hashCode().absoluteValue

      val prefix = testCase.prefix.stringValue()
      val mid1 = testCase.mid1.stringValue()
      val mid2 = testCase.mid2.stringValue()
      val suffix = testCase.suffix.stringValue()

      val string1 = prefix + mid1 + suffix
      val string2 = prefix + mid2 + suffix

      val expected = run {
        val byteString1 = ByteString.copyFromUtf8(string1)
        val byteString2 = ByteString.copyFromUtf8(string2)
        Util.compareByteStrings(byteString1, byteString2)
      }

      withClue("string1=\"$string1\" string2=\"$string2\" [testCaseId==$testCaseId]") {
        assertSoftly {
          withClue("string1 to itself") {
            Utf8Compare.compareUtf8Strings(string1, string1) shouldBe 0
          }
          withClue("string1 to string2") {
            Utf8Compare.compareUtf8Strings(string1, string2) shouldBe expected
          }
          withClue("string2 to string1") {
            Utf8Compare.compareUtf8Strings(string2, string1) shouldBe -expected
          }
        }
      }
    }
  }

  private data class Utf8CompareTestCase(
    val prefix: Utf8CompareTestString,
    val mid1: Utf8CompareTestString,
    val mid2: Utf8CompareTestString,
    val suffix: Utf8CompareTestString,
  )

  private data class Utf8CompareTestString(val chunks: List<Chunk>) {

    fun stringValue(): String = buildString { chunks.forEach { it.appendTo(this) } }

    sealed interface Chunk {
      val typeName: String
      fun appendTo(sb: StringBuilder)
    }

    data class SingleCharChunk(override val typeName: String, val char: Int) : Chunk {
      override fun appendTo(sb: StringBuilder) {
        sb.append(char.toChar())
      }
      override fun toString() = "{$typeName: [$char]"
    }

    data class DualCharChunk(override val typeName: String, val char1: Int, val char2: Int) :
      Chunk {
      override fun appendTo(sb: StringBuilder) {
        sb.append(char1.toChar())
        sb.append(char2.toChar())
      }
      override fun toString() = "{$typeName: [$char1, $char2]"
    }
  }

  @Suppress("NAME_SHADOWING")
  private companion object {

    fun Arb.Companion.utf8CompareTestCase(
      testString: Arb<Utf8CompareTestString> = Arb.utf8CompareTestString()
    ): Arb<Utf8CompareTestCase> =
      Arb.bind(testString, testString, testString, testString) { prefix, mid1, mid2, suffix,
        ->
        Utf8CompareTestCase(prefix = prefix, mid1 = mid1, mid2 = mid2, suffix = suffix)
      }

    fun Arb.Companion.utf8CompareTestString(
      chunk: Arb<Utf8CompareTestString.Chunk> = Arb.utf8CompareTestStringChunk(),
      length: IntRange = 0..5
    ): Arb<Utf8CompareTestString> = Arb.list(chunk, length).map { Utf8CompareTestString(it) }

    fun Arb.Companion.utf8CompareTestStringChunk(): Arb<Utf8CompareTestString.Chunk> =
      Arb.choice(
        Arb.oneByteUtf8Chunk(),
        Arb.twoByteUtf8Chunk(),
        Arb.threeByteUtf8Chunk(),
        Arb.surrogatePairUtf8Chunk(),
      )

    fun Arb.Companion.utf8CompareTestStringChunk(
      typeName: String,
      codepoint: Arb<Int>
    ): Arb<Utf8CompareTestString.Chunk> =
      codepoint.map { Utf8CompareTestString.SingleCharChunk(typeName, it) }

    fun Arb.Companion.oneByteUtf8Chunk() =
      Arb.utf8CompareTestStringChunk("oneByte", Arb.oneByteUtf8CodePoint)
    fun Arb.Companion.twoByteUtf8Chunk() =
      Arb.utf8CompareTestStringChunk("twoByte", Arb.twoByteUtf8CodePoint)
    fun Arb.Companion.threeByteUtf8Chunk() =
      Arb.utf8CompareTestStringChunk("threeByte", Arb.threeByteUtf8CodePoint)

    fun Arb.Companion.surrogatePairUtf8Chunk(
      highSurrogateCodePoint: Arb<Int> = Arb.highSurrogateCodePoint,
      lowSurrogateCodePoint: Arb<Int> = Arb.lowSurrogateCodePoint
    ): Arb<Utf8CompareTestString.Chunk> =
      Arb.bind(highSurrogateCodePoint, lowSurrogateCodePoint) {
        highSurrogateCodePoint,
        lowSurrogateCodePoint ->
        Utf8CompareTestString.DualCharChunk(
          "surrogatePair",
          highSurrogateCodePoint,
          lowSurrogateCodePoint
        )
      }

    val HIGH_SURROGATE_CODE_POINTS: IntRange = 0xD800..0xDBFF
    val LOW_SURROGATE_CODE_POINTS = 0xDC00..0xDFFF
    val SURROGATE_CODE_POINTS = HIGH_SURROGATE_CODE_POINTS.first..LOW_SURROGATE_CODE_POINTS.last

    val Arb.Companion.oneByteUtf8CodePoint: Arb<Int>
      get() = Arb.int(0..0x7F)
    val Arb.Companion.twoByteUtf8CodePoint: Arb<Int>
      get() = Arb.int(0x80..0x7FF)
    val Arb.Companion.threeByteUtf8CodePoint: Arb<Int>
      get() =
        Arb.choice(
          Arb.int(0x800 until SURROGATE_CODE_POINTS.first),
          Arb.int(SURROGATE_CODE_POINTS.last + 1..0xFFFF),
        )
    val Arb.Companion.highSurrogateCodePoint: Arb<Int>
      get() = Arb.int(HIGH_SURROGATE_CODE_POINTS)
    val Arb.Companion.lowSurrogateCodePoint: Arb<Int>
      get() = Arb.int(LOW_SURROGATE_CODE_POINTS)
  }
}
