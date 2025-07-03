package com.google.firebase.firestore.util

import com.google.protobuf.ByteString
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class Utf8CompareTest {

  @Test
  fun test() = runTest {
    val testCaseArb = Arb.utf8CompareTestCase()
    checkAll(100000, testCaseArb) { testCase ->
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

      assertSoftly {
        withClue("string1 to itself") {
          Utf8Compare.compareUtf8Strings(string1, string1) shouldBe 0
        }
        withClue("string1 with suffix") {
          Utf8Compare.compareUtf8Strings(string1 + "a", string1 + "b") shouldBe -1
        }
        withClue("string2 with suffix") {
          Utf8Compare.compareUtf8Strings(string2 + "b", string2 + "a") shouldBe 1
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

  private data class Utf8CompareTestCase(
    val prefix: Utf8CompareTestString,
    val mid1: Utf8CompareTestString,
    val mid2: Utf8CompareTestString,
    val suffix: Utf8CompareTestString,
  )

  private data class Utf8CompareTestString(val chunks: List<Chunk>) {
    data class Chunk(val typeName: String, val chars: List<Int>)

    fun stringValue(): String = buildString {
      chunks.map { it.chars }.flatten().joinToString(separator = "")
    }
  }

  private companion object {

    fun Arb.Companion.utf8CompareTestCase(testString: Arb<Utf8CompareTestString> = Arb.utf8CompareTestString()): Arb<Utf8CompareTestCase> = Arb.bind(testString, testString, testString, testString) {
      prefix, mid1, mid2, suffix, ->
      Utf8CompareTestCase(prefix=prefix, mid1=mid1, mid2=mid2, suffix=suffix)
    }

    fun Arb.Companion.utf8CompareTestString(chunk: Arb<Utf8CompareTestString.Chunk> = Arb.utf8CompareTestStringChunk(), length: IntRange = 0..5): Arb<Utf8CompareTestString> = Arb.list(chunk, length).map { Utf8CompareTestString(it) }

    fun Arb.Companion.utf8CompareTestStringChunk(): Arb<Utf8CompareTestString.Chunk> = Arb.choice(
      Arb.oneByteUtf8Chunk(),
      Arb.twoByteUtf8Chunk(),
      Arb.threeByteUtf8Chunk(),
      Arb.highSurrogateUtf8Chunk(),
      Arb.lowSurrogateUtf8Chunk(),
      Arb.surrogatePairUtf8Chunk(),
    )

    fun Arb.Companion.utf8CompareTestStringChunk(typeName: String, codepoint: Arb<Int>): Arb<Utf8CompareTestString.Chunk> = codepoint.map { Utf8CompareTestString.Chunk(typeName, listOf(it)) }

    fun Arb.Companion.oneByteUtf8Chunk() = Arb.utf8CompareTestStringChunk("oneByte", Arb.oneByteUtf8CodePoint)
    fun Arb.Companion.twoByteUtf8Chunk() = Arb.utf8CompareTestStringChunk("twoByte", Arb.twoByteUtf8CodePoint)
    fun Arb.Companion.threeByteUtf8Chunk() = Arb.utf8CompareTestStringChunk("threeByte", Arb.threeByteUtf8CodePoint)
    fun Arb.Companion.highSurrogateUtf8Chunk() = Arb.utf8CompareTestStringChunk("highSurrogate", Arb.highSurrogateCodePoint)
    fun Arb.Companion.lowSurrogateUtf8Chunk() = Arb.utf8CompareTestStringChunk("lowSurrogate", Arb.lowSurrogateCodePoint)

    fun Arb.Companion.surrogatePairUtf8Chunk(highSurrogateCodePoint: Arb<Int> = Arb.highSurrogateCodePoint, lowSurrogateCodePoint: Arb<Int> = Arb.lowSurrogateCodePoint): Arb<Utf8CompareTestString.Chunk> = Arb.bind(highSurrogateCodePoint, lowSurrogateCodePoint) { highSurrogateCodePoint, lowSurrogateCodePoint ->
      Utf8CompareTestString.Chunk("surrogatePair", listOf(highSurrogateCodePoint, lowSurrogateCodePoint))
    }

    val HIGH_SURROGATE_CODE_POINTS: IntRange = 0xD800..0xDBFF
    val LOW_SURROGATE_CODE_POINTS = 0xDC00..0xDFFF
    val SURROGATE_CODE_POINTS = HIGH_SURROGATE_CODE_POINTS.first..LOW_SURROGATE_CODE_POINTS.last

    val Arb.Companion.oneByteUtf8CodePoint: Arb<Int> get() = Arb.int(0..0x7F)
    val Arb.Companion.twoByteUtf8CodePoint: Arb<Int> get() = Arb.int(0x80..0x7FF)
    val Arb.Companion.threeByteUtf8CodePoint: Arb<Int> get() = Arb.choice(
      Arb.int(0x800 until SURROGATE_CODE_POINTS.first),
      Arb.int(SURROGATE_CODE_POINTS.last+1..0xFFFF),
    )
    val Arb.Companion.highSurrogateCodePoint: Arb<Int> get() = Arb.int(HIGH_SURROGATE_CODE_POINTS)
    val Arb.Companion.lowSurrogateCodePoint: Arb<Int> get() = Arb.int(LOW_SURROGATE_CODE_POINTS)

  }
}