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

package com.google.firebase.dataconnect.util

import com.google.firebase.dataconnect.util.StringUtil.to0xHexString
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlin.collections.forEach
import kotlinx.coroutines.test.runTest
import org.junit.Test

class StringUtilUnitTest {

  @Test
  fun `Int to0xHexString() should return the correct string`() = runTest {
    checkAll(propTestConfig, Arb.int()) { int ->
      val expected = "0x" + int.toUInt().toString(16).uppercase().padStart(8, '0')
      int.to0xHexString() shouldBe expected
    }
  }

  @Test
  fun `ByteArray to0xHexString() should return the correct string`() = runTest {
    checkAll(propTestConfig, Arb.byteArray(Arb.int(0..100), Arb.byte())) { byteArray ->
      val expected = "0x" + byteArray.toExpectedHexString()
      byteArray.to0xHexString() shouldBe expected
    }
  }

  @Test
  fun `ByteArray to0xHexString(include0xPrefix=true)`() = runTest {
    checkAll(propTestConfig, Arb.byteArray(Arb.int(0..100), Arb.byte())) { byteArray ->
      val expected = "0x" + byteArray.toExpectedHexString()
      byteArray.to0xHexString(include0xPrefix = true) shouldBe expected
    }
  }

  @Test
  fun `ByteArray to0xHexString(include0xPrefix=false)`() = runTest {
    checkAll(propTestConfig, Arb.byteArray(Arb.int(0..100), Arb.byte())) { byteArray ->
      val expected = byteArray.toExpectedHexString()
      byteArray.to0xHexString(include0xPrefix = false) shouldBe expected
    }
  }

  private companion object {

    @OptIn(ExperimentalKotest::class)
    val propTestConfig =
      PropTestConfig(
        iterations = 1000,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2)
      )

    fun ByteArray.toExpectedHexString(): String = buildString {
      this@toExpectedHexString.forEach {
        append(it.toUByte().toString(16).uppercase().padStart(2, '0'))
      }
    }
  }
}
