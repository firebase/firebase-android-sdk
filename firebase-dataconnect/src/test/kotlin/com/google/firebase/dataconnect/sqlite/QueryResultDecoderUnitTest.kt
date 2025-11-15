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

package com.google.firebase.dataconnect.sqlite

import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.BadHeaderException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.ByteArrayEOFException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.Companion.decode
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.EntityNotFoundException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.NegativeEntityIdSizeException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.NegativeListSizeException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.NegativeStringByteCountException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.NegativeStringCharCountException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.NegativeStructKeyCountException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.UnknownKindCaseByteException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.UnknownStringTypeException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.UnknownStructTypeException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.Utf16EOFException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.Utf8EOFException
import com.google.firebase.dataconnect.sqlite.QueryResultDecoder.Utf8IncorrectNumCharactersException
import com.google.firebase.dataconnect.testutil.buildByteArray
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import com.google.firebase.dataconnect.util.StringUtil.to0xHexString
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.string.shouldMatch
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.Exhaustive
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.negativeInt
import io.kotest.property.arbitrary.positiveInt
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.withEdgecases
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import kotlinx.coroutines.test.runTest
import org.junit.Test

class QueryResultDecoderUnitTest {

  @Test
  fun `decode() should throw BadHeaderException`() = runTest {
    val badHeaderArb = Arb.int().filterNot { it == QueryResultCodec.QUERY_RESULT_HEADER }
    checkAll(propTestConfig, badHeaderArb) { badHeader ->
      val byteArray = buildByteArray { putInt(badHeader) }

      val exception = shouldThrow<BadHeaderException> { decode(byteArray, emptyList()) }

      assertSoftly {
        exception.message shouldContainWithNonAbuttingText "jk832sz9hx"
        exception.message shouldMatch
          Regex(".*read header 0x0*${badHeader.toUInt().toString(16)}.*", RegexOption.IGNORE_CASE)
        exception.message shouldMatch
          Regex(
            ".*expected 0x0*${QueryResultCodec.QUERY_RESULT_HEADER.toUInt().toString(16)}.*",
            RegexOption.IGNORE_CASE
          )
      }
    }
  }

  @Test
  fun `decode() should throw UnknownStructTypeException`() = runTest {
    checkAll(propTestConfig, nonStructKindCaseByteExhaustive()) { nonStructKindCaseByte ->
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_HEADER)
        put(nonStructKindCaseByte)
      }

      val exception = shouldThrow<UnknownStructTypeException> { decode(byteArray, emptyList()) }

      assertSoftly {
        exception.message shouldContainWithNonAbuttingText "s8b9jqegdy"
        exception.message shouldContainWithNonAbuttingText nonStructKindCaseByte.toString()
        exception.message shouldContainWithNonAbuttingTextIgnoringCase "non-struct kind case byte"
      }
    }
  }
  @Test
  fun `decode() should throw NegativeStructKeyCountException`() = runTest {
    checkAll(propTestConfig, Arb.negativeInt()) { negativeStructKeyCount ->
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_HEADER)
        put(QueryResultCodec.VALUE_STRUCT)
        putInt(negativeStructKeyCount)
      }

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
        putInt(QueryResultCodec.QUERY_RESULT_HEADER)
        put(QueryResultCodec.VALUE_STRUCT)
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
        putInt(QueryResultCodec.QUERY_RESULT_HEADER)
        put(QueryResultCodec.VALUE_STRUCT)
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
        putInt(QueryResultCodec.QUERY_RESULT_HEADER)
        put(QueryResultCodec.VALUE_STRUCT)
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
        putInt(QueryResultCodec.QUERY_RESULT_HEADER)
        put(QueryResultCodec.VALUE_STRUCT)
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
        putInt(QueryResultCodec.QUERY_RESULT_HEADER)
        put(QueryResultCodec.VALUE_STRUCT)
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
  fun `decode() should throw NegativeEntityIdSizeException`() = runTest {
    checkAll(propTestConfig, Arb.negativeInt()) { negativeEntityIdSize ->
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_HEADER)
        put(QueryResultCodec.VALUE_ENTITY)
        putInt(negativeEntityIdSize)
      }

      val exception = shouldThrow<NegativeEntityIdSizeException> { decode(byteArray, emptyList()) }

      assertSoftly {
        exception.message shouldContainWithNonAbuttingText "agvqmbgknh"
        exception.message shouldContainWithNonAbuttingText negativeEntityIdSize.toString()
        exception.message shouldContainWithNonAbuttingTextIgnoringCase "entity id size"
        exception.message shouldContainWithNonAbuttingTextIgnoringCase
          "greater than or equal to zero"
      }
    }
  }

  @Test
  fun `decode() should throw EntityNotFoundException`() = runTest {
    checkAll(propTestConfig, Arb.byteArray(Arb.int(0..50), Arb.byte())) { encodedEntityId ->
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_HEADER)
        put(QueryResultCodec.VALUE_ENTITY)
        putInt(encodedEntityId.size)
        put(encodedEntityId)
      }

      val exception = shouldThrow<EntityNotFoundException> { decode(byteArray, emptyList()) }

      assertSoftly {
        exception.message shouldContainWithNonAbuttingText "p583k77y7r"
        exception.message shouldContainWithNonAbuttingText encodedEntityId.to0xHexString()
        exception.message shouldContainWithNonAbuttingTextIgnoringCase "could not find entity"
      }
    }
  }

  @Test
  fun `decode() should be able to decode very long entity IDs`() = runTest {
    checkAll(
      @OptIn(ExperimentalKotest::class) propTestConfig.copy(iterations = 10),
      Arb.byteArray(Arb.int(2000..90000), Arb.byte()),
      Arb.proto.struct(depth = 1)
    ) { encodedEntityId, entityData ->
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_HEADER)
        put(QueryResultCodec.VALUE_ENTITY)
        putInt(encodedEntityId.size)
        put(encodedEntityId)
        putInt(entityData.struct.fieldsCount)
        entityData.struct.fieldsMap.keys.forEach { key ->
          val encodedKey = key.encodeToByteArray()
          put(QueryResultCodec.VALUE_STRING_UTF8)
          putInt(encodedKey.size)
          putInt(key.length)
          put(encodedKey)
          put(QueryResultCodec.VALUE_KIND_NOT_SET)
        }
      }
      val entity =
        QueryResultCodec.Entity(id = "", encodedId = encodedEntityId, data = entityData.struct)

      val decodeResult = decode(byteArray, listOf(entity))

      decodeResult shouldBe entityData.struct
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
          putInt(QueryResultCodec.QUERY_RESULT_HEADER)
          put(QueryResultCodec.VALUE_STRUCT)
          putInt(structKeyCount)
          put(QueryResultCodec.VALUE_STRING_UTF8)
          putInt(byteCount)
          putInt(charCount)
          put(stringUtf8Bytes)
        }

        val exception = shouldThrow<Utf8EOFException> { decode(byteArray, emptyList()) }

        assertSoftly {
          exception.message shouldContainWithNonAbuttingText "akn3x7p8rm"
          exception.message shouldContainWithNonAbuttingTextIgnoringCase
            "end of input reached prematurely"
          exception.message shouldContainWithNonAbuttingTextIgnoringCase
            "reading $charCount characters ($byteCount bytes) of a UTF-8 encoded string"
          exception.message shouldContainWithNonAbuttingTextIgnoringCase
            "got ${string.length} characters, ${charCount-string.length} fewer characters"
          exception.message shouldContainWithNonAbuttingTextIgnoringCase
            "${stringUtf8Bytes.size} bytes, ${byteCount-stringUtf8Bytes.size} fewer bytes"
        }
      }
    }

  @Test
  fun `decode() should throw Utf8IncorrectNumCharactersException with 'insufficient chars' message for utf8`() =
    runTest {
      checkAll(propTestConfig, Arb.positiveInt(), Arb.string(0..20), Arb.positiveInt(1)) {
        structKeyCount,
        string,
        charCountDelta ->
        val stringUtf8Bytes = string.encodeToByteArray()
        val charCount = string.length + charCountDelta
        val byteArray = buildByteArray {
          putInt(QueryResultCodec.QUERY_RESULT_HEADER)
          put(QueryResultCodec.VALUE_STRUCT)
          putInt(structKeyCount)
          put(QueryResultCodec.VALUE_STRING_UTF8)
          putInt(stringUtf8Bytes.size)
          putInt(charCount)
          put(stringUtf8Bytes)
        }

        val exception =
          shouldThrow<Utf8IncorrectNumCharactersException> { decode(byteArray, emptyList()) }

        assertSoftly {
          exception.message shouldContainWithNonAbuttingText "dhvzxrcrqe"
          exception.message shouldContainWithNonAbuttingTextIgnoringCase
            "expected to read $charCount characters"
          exception.message shouldContainWithNonAbuttingTextIgnoringCase
            "${stringUtf8Bytes.size} bytes"
          exception.message shouldContainWithNonAbuttingTextIgnoringCase
            "got ${string.length} characters, ${charCount - string.length} fewer characters"
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
          putInt(QueryResultCodec.QUERY_RESULT_HEADER)
          put(QueryResultCodec.VALUE_STRUCT)
          putInt(structKeyCount)
          put(QueryResultCodec.VALUE_STRING_UTF16)
          putInt(charCount)
          string.forEach(::putChar)
        }

        val exception = shouldThrow<Utf16EOFException> { decode(byteArray, emptyList()) }

        assertSoftly {
          exception.message shouldContainWithNonAbuttingText "e399qdvzdz"
          exception.message shouldContainWithNonAbuttingTextIgnoringCase
            "end of input reached prematurely"
          exception.message shouldContainWithNonAbuttingTextIgnoringCase
            "reading $charCount characters (${charCount*2} bytes) of a UTF-16 encoded string"
          exception.message shouldContainWithNonAbuttingTextIgnoringCase
            "got ${string.length} characters, ${charCount-string.length} fewer characters"
          exception.message shouldContainWithNonAbuttingTextIgnoringCase
            "${string.length*2} bytes, ${(charCount - string.length)*2} fewer bytes"
        }
      }
    }

  @Test
  fun `decode() should throw NegativeListSizeException`() = runTest {
    checkAll(propTestConfig, Arb.positiveInt(), Arb.negativeInt()) {
      structKeyCount,
      negativeListSize ->
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_HEADER)
        put(QueryResultCodec.VALUE_STRUCT)
        putInt(structKeyCount)
        put(QueryResultCodec.VALUE_STRING_EMPTY)
        put(QueryResultCodec.VALUE_LIST)
        putInt(negativeListSize)
      }

      val exception = shouldThrow<NegativeListSizeException> { decode(byteArray, emptyList()) }

      assertSoftly {
        exception.message shouldContainWithNonAbuttingText "yfvpf9pwt8"
        exception.message shouldContainWithNonAbuttingTextIgnoringCase
          "read list size $negativeListSize"
        exception.message shouldContainWithNonAbuttingTextIgnoringCase
          "greater than or equal to zero"
      }
    }
  }

  @Test
  fun `decode() should throw ByteArrayEOFException`() = runTest {
    checkAll(
      propTestConfig,
      Arb.byteArray(Arb.int(0..16384), Arb.byte()),
      Arb.positiveInt(32768),
    ) { encodedEntityId, byteCountDelta ->
      val byteArray = buildByteArray {
        putInt(QueryResultCodec.QUERY_RESULT_HEADER)
        put(QueryResultCodec.VALUE_ENTITY)
        putInt(encodedEntityId.size + byteCountDelta)
        put(encodedEntityId)
      }

      val exception = shouldThrow<ByteArrayEOFException> { decode(byteArray, emptyList()) }

      assertSoftly {
        exception.message shouldContainWithNonAbuttingText "dnx886qwmk"
        exception.message shouldContainWithNonAbuttingTextIgnoringCase
          "end of input reached prematurely"
        exception.message shouldContainWithNonAbuttingTextIgnoringCase
          "reading byte array of length ${encodedEntityId.size + byteCountDelta}"
        exception.message shouldContainWithNonAbuttingTextIgnoringCase
          "got ${encodedEntityId.size} bytes, $byteCountDelta fewer bytes"
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

    val kindCaseBytes: Set<Byte> =
      setOf(
        QueryResultCodec.VALUE_NULL,
        QueryResultCodec.VALUE_NUMBER,
        QueryResultCodec.VALUE_BOOL_TRUE,
        QueryResultCodec.VALUE_BOOL_FALSE,
        QueryResultCodec.VALUE_STRING_EMPTY,
        QueryResultCodec.VALUE_STRING_UTF8,
        QueryResultCodec.VALUE_STRING_UTF16,
        QueryResultCodec.VALUE_KIND_NOT_SET,
        QueryResultCodec.VALUE_LIST,
        QueryResultCodec.VALUE_STRUCT,
        QueryResultCodec.VALUE_ENTITY,
      )

    val stringTypeBytes: Set<Byte> =
      setOf(
        QueryResultCodec.VALUE_STRING_EMPTY,
        QueryResultCodec.VALUE_STRING_UTF8,
        QueryResultCodec.VALUE_STRING_UTF16,
      )

    val nonStringTypeBytes: Set<Byte> = kindCaseBytes - stringTypeBytes

    val structTypeBytes: Set<Byte> =
      setOf(
        QueryResultCodec.VALUE_STRUCT,
        QueryResultCodec.VALUE_ENTITY,
      )

    val nonStructTypeBytes: Set<Byte> = kindCaseBytes - structTypeBytes

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

    fun nonStructKindCaseByteExhaustive(): Exhaustive<Byte> =
      Exhaustive.collection(nonStructTypeBytes)
  }
}
