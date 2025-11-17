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

import com.google.firebase.dataconnect.sqlite.CodedIntegersTesting.calculateSInt32Size
import com.google.firebase.dataconnect.sqlite.CodedIntegersTesting.calculateSInt64Size
import com.google.firebase.dataconnect.sqlite.CodedIntegersTesting.calculateUInt32Size
import com.google.firebase.dataconnect.sqlite.CodedIntegersTesting.calculateUInt64Size
import com.google.firebase.dataconnect.sqlite.CodedIntegersTesting.sint32Arb
import com.google.firebase.dataconnect.sqlite.CodedIntegersTesting.sint64Arb
import com.google.firebase.dataconnect.sqlite.CodedIntegersTesting.uint32Arb
import com.google.firebase.dataconnect.sqlite.CodedIntegersTesting.uint64Arb
import io.kotest.assertions.assertSoftly
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.checkAll
import java.nio.ByteBuffer
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CodedIntegersUnitTest {

  @Test
  fun `computeUInt32Size() returns the correct value`() = runTest {
    checkAll(propTestConfig, uint32Arb()) { value ->
      CodedIntegers.computeUInt32Size(value) shouldBe value.calculateUInt32Size()
    }
  }

  @Test
  fun `putUInt32() round trips with getUInt32()`() = runTest {
    val byteBuffer = ByteBuffer.allocate(CodedIntegers.MAX_VARINT32_SIZE)
    checkAll(propTestConfig, uint32Arb()) { value ->
      byteBuffer.clear()
      CodedIntegers.putUInt32(value, byteBuffer)
      byteBuffer.flip()
      CodedIntegers.getUInt32(byteBuffer) shouldBe value
    }
  }

  @Test
  fun `putUInt32() puts the number of bytes calculated by computeUInt32Size()`() = runTest {
    val byteBuffer = ByteBuffer.allocate(CodedIntegers.MAX_VARINT32_SIZE)
    checkAll(propTestConfig, uint32Arb()) { value ->
      byteBuffer.clear()
      CodedIntegers.putUInt32(value, byteBuffer)
      byteBuffer.position() shouldBe CodedIntegers.computeUInt32Size(value)
    }
  }

  @Test
  fun `computeUInt64Size() returns the correct value`() = runTest {
    checkAll(propTestConfig, uint64Arb()) { value ->
      val byteBuffer = ByteBuffer.allocate(CodedIntegers.MAX_VARINT64_SIZE)
      assertSoftly {
        byteBuffer.clear()
        CodedIntegers.putUInt64(value, byteBuffer)
        CodedIntegers.computeUInt64Size(value) shouldBe value.calculateUInt64Size()
      }
    }
  }

  @Test
  fun `putUInt64() round trips with getUInt64()`() = runTest {
    val byteBuffer = ByteBuffer.allocate(CodedIntegers.MAX_VARINT64_SIZE)
    checkAll(propTestConfig, uint64Arb()) { value ->
      byteBuffer.clear()
      CodedIntegers.putUInt64(value, byteBuffer)
      byteBuffer.flip()
      CodedIntegers.getUInt64(byteBuffer) shouldBe value
    }
  }

  @Test
  fun `putUInt64() puts the number of bytes calculated by computeUInt64Size()`() = runTest {
    val byteBuffer = ByteBuffer.allocate(CodedIntegers.MAX_VARINT64_SIZE)
    checkAll(propTestConfig, uint64Arb()) { value ->
      byteBuffer.clear()
      CodedIntegers.putUInt64(value, byteBuffer)
      byteBuffer.position() shouldBe CodedIntegers.computeUInt64Size(value)
    }
  }

  @Test
  fun `computeSInt32Size() returns the correct value`() = runTest {
    checkAll(propTestConfig, sint32Arb()) { value ->
      CodedIntegers.computeSInt32Size(value) shouldBe value.calculateSInt32Size()
    }
  }

  @Test
  fun `putSInt32() round trips with getSInt32()`() = runTest {
    val byteBuffer = ByteBuffer.allocate(CodedIntegers.MAX_VARINT32_SIZE)
    checkAll(propTestConfig, sint32Arb()) { value ->
      byteBuffer.clear()
      CodedIntegers.putSInt32(value, byteBuffer)
      byteBuffer.flip()
      CodedIntegers.getSInt32(byteBuffer) shouldBe value
    }
  }

  @Test
  fun `putSInt32() puts the number of bytes calculated by computeSInt32Size()`() = runTest {
    val byteBuffer = ByteBuffer.allocate(CodedIntegers.MAX_VARINT32_SIZE)
    checkAll(propTestConfig, sint32Arb()) { value ->
      byteBuffer.clear()
      CodedIntegers.putSInt32(value, byteBuffer)
      byteBuffer.position() shouldBe CodedIntegers.computeSInt32Size(value)
    }
  }

  @Test
  fun `computeSInt64Size() returns the correct value`() = runTest {
    checkAll(propTestConfig, sint64Arb()) { value ->
      CodedIntegers.computeSInt64Size(value) shouldBe value.calculateSInt64Size()
    }
  }

  @Test
  fun `putSInt64() round trips with getSInt64()`() = runTest {
    val byteBuffer = ByteBuffer.allocate(CodedIntegers.MAX_VARINT64_SIZE)
    checkAll(propTestConfig, sint64Arb()) { value ->
      byteBuffer.clear()
      CodedIntegers.putSInt64(value, byteBuffer)
      byteBuffer.flip()
      CodedIntegers.getSInt64(byteBuffer) shouldBe value
    }
  }

  @Test
  fun `putSInt64() puts the number of bytes calculated by computeSInt64Size()`() = runTest {
    val byteBuffer = ByteBuffer.allocate(CodedIntegers.MAX_VARINT64_SIZE)
    checkAll(propTestConfig, sint64Arb()) { value ->
      byteBuffer.clear()
      CodedIntegers.putSInt64(value, byteBuffer)
      byteBuffer.position() shouldBe CodedIntegers.computeSInt64Size(value)
    }
  }

  private companion object {

    @OptIn(ExperimentalKotest::class)
    val propTestConfig =
      PropTestConfig(
        iterations = 1000,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.25),
        shrinkingMode = ShrinkingMode.Off,
      )
  }
}
