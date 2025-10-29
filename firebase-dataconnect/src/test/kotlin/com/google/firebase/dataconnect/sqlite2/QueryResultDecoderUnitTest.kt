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
import com.google.firebase.dataconnect.sqlite2.QueryResultDecoder.NegativeStructKeyCountException
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.negativeInt
import io.kotest.property.arbitrary.positiveInt
import io.kotest.property.checkAll
import java.io.ByteArrayOutputStream
import java.io.DataOutput
import java.io.DataOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Test

class QueryResultDecoderUnitTest {

  @Test
  fun `decode() should throw NegativeStructKeyCountException`() = runTest {
    checkAll(propTestConfig, Arb.negativeInt()) { negativeInt ->
      val byteArray = buildByteArray { writeInt(negativeInt) }

      val exception =
        shouldThrow<NegativeStructKeyCountException> { decode(byteArray, emptyList()) }

      assertSoftly {
        exception.message shouldContainWithNonAbuttingText "y9253xj96g"
        exception.message shouldContainWithNonAbuttingText negativeInt.toString()
        exception.message shouldContainWithNonAbuttingTextIgnoringCase "struct key count"
        exception.message shouldContainWithNonAbuttingTextIgnoringCase
          "greater than or equal to zero"
      }
    }
  }

  @Test
  fun `decode() should throw NegativeStringByteCountException`() = runTest {
    checkAll(propTestConfig, Arb.positiveInt(), Arb.negativeInt()) { positiveInt, negativeInt ->
      val byteArray = buildByteArray {
        writeInt(positiveInt)
        writeInt(negativeInt)
      }

      val exception =
        shouldThrow<NegativeStringByteCountException> { decode(byteArray, emptyList()) }

      assertSoftly {
        exception.message shouldContainWithNonAbuttingText "a9kma55y7m"
        exception.message shouldContainWithNonAbuttingText negativeInt.toString()
        exception.message shouldContainWithNonAbuttingTextIgnoringCase "string byte count"
        exception.message shouldContainWithNonAbuttingTextIgnoringCase
          "greater than or equal to zero"
      }
    }
  }

  @Test
  fun `decode() should throw UnknownKindCaseIntException`() = runTest {
    checkAll(propTestConfig, Arb.positiveInt(), invalidKindCaseByteArb()) {
      positiveInt,
      invalidKindCaseByte ->
      val byteArray = buildByteArray {
        writeInt(positiveInt)
        writeInt(0)
        writeByte(invalidKindCaseByte.toInt())
      }

      val exception =
        shouldThrow<QueryResultDecoder.UnknownKindCaseByteException> {
          decode(byteArray, emptyList())
        }

      assertSoftly {
        exception.message shouldContainWithNonAbuttingText "pmkb3sc2mn"
        exception.message shouldContainWithNonAbuttingText invalidKindCaseByte.toString()
        exception.message shouldContainWithNonAbuttingTextIgnoringCase "unknown kind case byte"
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

    fun buildByteArray(block: DataOutput.() -> Unit): ByteArray =
      ByteArrayOutputStream().use { byteArrayOutputStream ->
        DataOutputStream(byteArrayOutputStream).use { dataOutputStream -> block(dataOutputStream) }
        byteArrayOutputStream.toByteArray()
      }

    val kindCaseBytes =
      setOf(
        QueryResultCodec.VALUE_NULL,
        QueryResultCodec.VALUE_NUMBER,
        QueryResultCodec.VALUE_BOOL_TRUE,
        QueryResultCodec.VALUE_BOOL_FALSE,
        QueryResultCodec.VALUE_STRING_UTF8,
      )

    fun invalidKindCaseByteArb(): Arb<Byte> = Arb.byte().filterNot { it in kindCaseBytes }
  }
}
