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

import com.google.firebase.dataconnect.sqlite2.QueryResultDecoder.Companion.decode
import com.google.firebase.dataconnect.sqlite2.QueryResultDecoder.NegativeStringByteCountException
import com.google.firebase.dataconnect.sqlite2.QueryResultDecoder.NegativeStringCharCountException
import com.google.firebase.dataconnect.sqlite2.QueryResultDecoder.NegativeStructKeyCountException
import com.google.firebase.dataconnect.sqlite2.QueryResultDecoder.UnknownKindCaseByteException
import com.google.firebase.dataconnect.sqlite2.QueryResultDecoder.UnknownStringTypeException
import com.google.firebase.dataconnect.sqlite2.QueryResultDecoder.Utf16EOFException
import com.google.firebase.dataconnect.sqlite2.QueryResultDecoder.Utf8EOFException
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.Exhaustive
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.negativeInt
import io.kotest.property.arbitrary.positiveInt
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.withEdgecases
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import java.nio.ByteBuffer
import kotlinx.coroutines.test.runTest
import org.junit.Test

class QueryResultDecoderUnitTest {

  @Test
  fun `decode() should throw NegativeStructKeyCountException`() = runTest {
    checkAll(propTestConfig, Arb.negativeInt()) { negativeStructKeyCount ->
      val byteArray = buildByteArray { putInt(negativeStructKeyCount) }

      val exception =
        shouldThrow<NegativeStructKeyCountException> { decode(byteArray, emptyList()) }

      assertSoftly {
        exception.message shouldContainWithNonAbuttingText "y9253xj96g"
        exception.message shouldContainWithNonAbuttingText negativeStructKeyCount.toString()
        exception.message shouldContainWithNonAbuttingTextIgnoringCase "struct key count"
        exception.message shouldContainWithNonAbuttingTextIgnoringCase
          "greater than or equal to zero"
      }
    }
  }

  @Test
  fun `decode() should throw NegativeStringByteCountException for utf8`() = runTest {
    checkAll(propTestConfig, Arb.positiveInt(), Arb.negativeInt()) {
      structKeyCount,
      negativeStringByteCount ->
      val byteArray = buildByteArray {
        putInt(structKeyCount)
        put(QueryResultCodec.VALUE_STRING_UTF8)
        putInt(negativeStringByteCount)
      }

      val exception =
        shouldThrow<NegativeStringByteCountException> { decode(byteArray, emptyList()) }

      assertSoftly {
        exception.message shouldContainWithNonAbuttingText "a9kma55y7m"
        exception.message shouldContainWithNonAbuttingText negativeStringByteCount.toString()
        exception.message shouldContainWithNonAbuttingTextIgnoringCase "string byte count"
        exception.message shouldContainWithNonAbuttingTextIgnoringCase
          "greater than or equal to zero"
      }
    }
  }

  @Test
  fun `decode() should throw NegativeStringCharCountException for utf8`() = runTest {
    checkAll(propTestConfig, Arb.positiveInt(), Arb.positiveInt(), Arb.negativeInt()) {
      structKeyCount,
      stringByteCount,
      negativeStringCharCount ->
      val byteArray = buildByteArray {
        putInt(structKeyCount)
        put(QueryResultCodec.VALUE_STRING_UTF8)
        putInt(stringByteCount)
        putInt(negativeStringCharCount)
      }

      val exception =
        shouldThrow<NegativeStringCharCountException> { decode(byteArray, emptyList()) }

      assertSoftly {
        exception.message shouldContainWithNonAbuttingText "gwybfam237"
        exception.message shouldContainWithNonAbuttingText negativeStringCharCount.toString()
        exception.message shouldContainWithNonAbuttingTextIgnoringCase "string char count"
        exception.message shouldContainWithNonAbuttingTextIgnoringCase
          "greater than or equal to zero"
      }
    }
  }

  @Test
  fun `decode() should throw NegativeStringCharCountException for utf16`() = runTest {
    checkAll(propTestConfig, Arb.positiveInt(), Arb.negativeInt()) {
      structKeyCount,
      negativeStringCharCount ->
      val byteArray = buildByteArray {
        putInt(structKeyCount)
        put(QueryResultCodec.VALUE_STRING_UTF16)
        putInt(negativeStringCharCount)
      }

      val exception =
        shouldThrow<NegativeStringCharCountException> { decode(byteArray, emptyList()) }

      assertSoftly {
        exception.message shouldContainWithNonAbuttingText "gwybfam237"
        exception.message shouldContainWithNonAbuttingText negativeStringCharCount.toString()
        exception.message shouldContainWithNonAbuttingTextIgnoringCase "string char count"
        exception.message shouldContainWithNonAbuttingTextIgnoringCase
          "greater than or equal to zero"
      }
    }
  }

  @Test
  fun `decode() should throw UnknownStringTypeException`() = runTest {
    checkAll(propTestConfig, nonStringKindCaseByteExhaustive(), Arb.positiveInt()) {
      nonStringKindCaseByte,
      structKeyCount ->
      val byteArray = buildByteArray {
        putInt(structKeyCount)
        put(nonStringKindCaseByte)
      }

      val exception = shouldThrow<UnknownStringTypeException> { decode(byteArray, emptyList()) }

      assertSoftly {
        exception.message shouldContainWithNonAbuttingText "hfvxx849cv"
        exception.message shouldContainWithNonAbuttingText nonStringKindCaseByte.toString()
        exception.message shouldContainWithNonAbuttingTextIgnoringCase "non-string kind case byte"
      }
    }
  }

  @Test
  fun `decode() should throw UnknownKindCaseByteException`() = runTest {
    checkAll(propTestConfig, Arb.positiveInt(), invalidKindCaseByteArb()) {
      structKeyCount,
      invalidKindCaseByte ->
      val byteArray = buildByteArray {
        putInt(structKeyCount)
        put(QueryResultCodec.VALUE_STRING_EMPTY)
        put(invalidKindCaseByte)
      }

      val exception = shouldThrow<UnknownKindCaseByteException> { decode(byteArray, emptyList()) }

      assertSoftly {
        exception.message shouldContainWithNonAbuttingText "pmkb3sc2mn"
        exception.message shouldContainWithNonAbuttingText invalidKindCaseByte.toString()
        exception.message shouldContainWithNonAbuttingTextIgnoringCase "unknown kind case byte"
      }
    }
  }

  @Test
  fun `decode() should throw Utf8EOFException with 'insufficient bytes' message for utf8`() =
    runTest {
      checkAll(
        propTestConfig,
        Arb.positiveInt(),
        Arb.string(0..20),
        Arb.positiveInt(1),
        Arb.positiveInt(1)
      ) { structKeyCount, string, byteCountDelta, charCountDelta ->
        val stringUtf8Bytes = string.encodeToByteArray()
        val byteCount = stringUtf8Bytes.size + byteCountDelta
        val charCount = string.length + charCountDelta
        val byteArray = buildByteArray {
          putInt(structKeyCount)
          put(QueryResultCodec.VALUE_STRING_UTF8)
          putInt(byteCount)
          putInt(charCount)
          put(stringUtf8Bytes)
        }

        val exception = shouldThrow<Utf8EOFException> { decode(byteArray, emptyList()) }

        assertSoftly {
          exception.message shouldContainWithNonAbuttingText "c8d6bbnms9"
          exception.message shouldContainWithNonAbuttingTextIgnoringCase
            "expected to read $byteCount bytes"
          exception.message shouldContainWithNonAbuttingTextIgnoringCase "$charCount characters"
          exception.message shouldContainWithNonAbuttingTextIgnoringCase
            "got ${stringUtf8Bytes.size} bytes"
          exception.message shouldContainWithNonAbuttingTextIgnoringCase
            "${string.length} characters"
        }
      }
    }

  @Test
  fun `decode() should throw Utf8EOFException with 'insufficient chars' message for utf8`() =
    runTest {
      checkAll(propTestConfig, Arb.positiveInt(), Arb.string(0..20), Arb.positiveInt(1)) {
        structKeyCount,
        string,
        charCountDelta ->
        val stringUtf8Bytes = string.encodeToByteArray()
        val charCount = string.length + charCountDelta
        val byteArray = buildByteArray {
          putInt(structKeyCount)
          put(QueryResultCodec.VALUE_STRING_UTF8)
          putInt(stringUtf8Bytes.size)
          putInt(charCount)
          put(stringUtf8Bytes)
        }

        val exception = shouldThrow<Utf8EOFException> { decode(byteArray, emptyList()) }

        assertSoftly {
          exception.message shouldContainWithNonAbuttingText "dhvzxrcrqe"
          exception.message shouldContainWithNonAbuttingTextIgnoringCase
            "expected to read $charCount characters"
          exception.message shouldContainWithNonAbuttingTextIgnoringCase
            "${stringUtf8Bytes.size} bytes"
          exception.message shouldContainWithNonAbuttingTextIgnoringCase
            "got ${string.length} characters"
        }
      }
    }

  @Test
  fun `decode() should throw Utf16EOFException with 'insufficient chars' message for utf16`() =
    runTest {
      checkAll(propTestConfig, Arb.positiveInt(), Arb.string(0..20), Arb.positiveInt(1)) {
        structKeyCount,
        string,
        charCountDelta ->
        val charCount = string.length + charCountDelta
        val byteArray = buildByteArray {
          putInt(structKeyCount)
          put(QueryResultCodec.VALUE_STRING_UTF16)
          putInt(charCount)
          string.forEach(::putChar)
        }

        val exception = shouldThrow<Utf16EOFException> { decode(byteArray, emptyList()) }

        assertSoftly {
          exception.message shouldContainWithNonAbuttingText "e399qdvzdz"
          exception.message shouldContainWithNonAbuttingTextIgnoringCase
            "expected to read $charCount characters"
          exception.message shouldContainWithNonAbuttingTextIgnoringCase
            "${string.length * 2} bytes"
          exception.message shouldContainWithNonAbuttingTextIgnoringCase
            "got ${string.length} characters"
        }
      }
    }

  private companion object {

    @OptIn(ExperimentalKotest::class)
    val propTestConfig =
      PropTestConfig(
        iterations = 1000,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.33)
      )

    fun buildByteArray(block: ByteBuffer.() -> Unit): ByteArray {
      val byteBuffer = ByteBuffer.allocate(1024)
      block(byteBuffer)
      byteBuffer.flip()
      val byteArray = ByteArray(byteBuffer.remaining())
      byteBuffer.get(byteArray)
      return byteArray
    }

    val kindCaseBytes: Set<Byte> =
      setOf(
        QueryResultCodec.VALUE_NULL,
        QueryResultCodec.VALUE_NUMBER,
        QueryResultCodec.VALUE_BOOL_TRUE,
        QueryResultCodec.VALUE_BOOL_FALSE,
        QueryResultCodec.VALUE_STRING_EMPTY,
        QueryResultCodec.VALUE_STRING_UTF8,
        QueryResultCodec.VALUE_STRING_UTF16,
      )

    val stringTypeBytes: Set<Byte> =
      setOf(
        QueryResultCodec.VALUE_STRING_EMPTY,
        QueryResultCodec.VALUE_STRING_UTF8,
        QueryResultCodec.VALUE_STRING_UTF16,
      )

    val nonStringTypeBytes: Set<Byte> = kindCaseBytes - stringTypeBytes

    val invalidKindCaseByteEdgeCases: List<Byte> =
      buildSet {
          add(Byte.MIN_VALUE)
          add(Byte.MAX_VALUE)
          add(0)
          add(-1)
          add(1)
          kindCaseBytes.forEach { kindCaseByte ->
            repeat(3) { offset ->
              add((kindCaseByte + offset).toByte())
              add((kindCaseByte - offset).toByte())
            }
          }
          kindCaseBytes.forEach { kindCaseByte -> remove(kindCaseByte) }
        }
        .distinct()
        .sorted()

    fun invalidKindCaseByteArb(): Arb<Byte> =
      Arb.byte().filterNot { it in kindCaseBytes }.withEdgecases(invalidKindCaseByteEdgeCases)

    fun nonStringKindCaseByteExhaustive(): Exhaustive<Byte> =
      Exhaustive.collection(nonStringTypeBytes)
  }
}
