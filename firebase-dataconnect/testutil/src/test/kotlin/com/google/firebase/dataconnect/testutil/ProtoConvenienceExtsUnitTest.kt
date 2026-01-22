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
package com.google.firebase.dataconnect.testutil

import com.google.firebase.dataconnect.testutil.property.arbitrary.listValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.property.arbitrary.value
import com.google.protobuf.Value
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.Exhaustive
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.boolean
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ProtoConvenienceExtsUnitTest {

  @Test
  fun `Boolean toValueProto`() = runTest {
    checkAll(propTestConfig, Exhaustive.boolean()) { boolean ->
      val value = boolean.toValueProto()
      value.kindCase shouldBe Value.KindCase.BOOL_VALUE
      value.boolValue shouldBe boolean
    }
  }

  @Test
  fun `String toValueProto`() = runTest {
    checkAll(propTestConfig, Arb.string()) { string ->
      val value = string.toValueProto()
      value.kindCase shouldBe Value.KindCase.STRING_VALUE
      value.stringValue shouldBe string
    }
  }

  @Test
  fun `Double toValueProto`() = runTest {
    checkAll(propTestConfig, Arb.double()) { double ->
      val value = double.toValueProto()
      value.kindCase shouldBe Value.KindCase.NUMBER_VALUE
      value.numberValue shouldBe double
    }
  }

  @Test
  fun `Struct toValueProto`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct().map { it.struct }) { struct ->
      val value = struct.toValueProto()
      value.kindCase shouldBe Value.KindCase.STRUCT_VALUE
      value.structValue shouldBe struct
    }
  }

  @Test
  fun `ListValue toValueProto`() = runTest {
    checkAll(propTestConfig, Arb.proto.listValue().map { it.listValue }) { listValue ->
      val value = listValue.toValueProto()
      value.kindCase shouldBe Value.KindCase.LIST_VALUE
      value.listValue shouldBe listValue
    }
  }

  @Test
  fun `Value isStructValue returns true when kindCase is STRUCT_VALUE`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct().map { it.struct.toValueProto() }) { value ->
      value.isStructValue shouldBe true
    }
  }

  @Test
  fun `Value isStructValue returns false when kindCase is not STRUCT_VALUE`() = runTest {
    checkAll(propTestConfig, Arb.proto.value(exclude = Value.KindCase.STRUCT_VALUE)) { value ->
      value.isStructValue shouldBe false
    }
  }

  @Test
  fun `Value isListValue returns true when kindCase is LIST_VALUE`() = runTest {
    checkAll(propTestConfig, Arb.proto.listValue().map { it.listValue.toValueProto() }) { value ->
      value.isListValue shouldBe true
    }
  }

  @Test
  fun `Value isListValue returns false when kindCase is not LIST_VALUE`() = runTest {
    checkAll(propTestConfig, Arb.proto.value(exclude = Value.KindCase.LIST_VALUE)) { value ->
      value.isListValue shouldBe false
    }
  }

  private companion object {
    @OptIn(ExperimentalKotest::class)
    val propTestConfig =
      PropTestConfig(
        iterations = 200,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.33),
        shrinkingMode = ShrinkingMode.Off,
      )
  }
}
