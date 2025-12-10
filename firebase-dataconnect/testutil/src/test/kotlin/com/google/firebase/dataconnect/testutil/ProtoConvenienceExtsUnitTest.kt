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

import com.google.firebase.dataconnect.testutil.property.arbitrary.boolValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.kindNotSetValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.listValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.nullValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.numberValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.stringValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.property.arbitrary.value
import com.google.protobuf.Value
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.Exhaustive
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.list
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
  fun `Iterable toListValue`() = runTest {
    checkAll(propTestConfig, Arb.list(Arb.proto.value(), 0..5)) { values ->
      val listValue = values.toListValue()
      listValue.valuesList shouldContainExactly values
    }
  }

  @Test
  fun `Iterable toValueProto`() = runTest {
    checkAll(propTestConfig, Arb.list(Arb.proto.value(), 0..5)) { values ->
      val value = values.toValueProto()
      value.kindCase shouldBe Value.KindCase.LIST_VALUE
      value.listValue shouldBe values.toListValue()
    }
  }

  @Test
  fun `Value isNullValue returns true when kindCase is NULL_VALUE`() = runTest {
    checkAll(propTestConfig, Exhaustive.proto.nullValue()) { value ->
      value.isNullValue shouldBe true
    }
  }

  @Test
  fun `Value isNullValue returns false when kindCase is not NULL_VALUE`() = runTest {
    checkAll(propTestConfig, Arb.proto.value(exclude = Value.KindCase.NULL_VALUE)) { value ->
      value.isNullValue shouldBe false
    }
  }

  @Test
  fun `Value isNumberValue returns true when kindCase is NUMBER_VALUE`() = runTest {
    checkAll(propTestConfig, Arb.proto.numberValue()) { value -> value.isNumberValue shouldBe true }
  }

  @Test
  fun `Value isNumberValue returns false when kindCase is not NUMBER_VALUE`() = runTest {
    checkAll(propTestConfig, Arb.proto.value(exclude = Value.KindCase.NUMBER_VALUE)) { value ->
      value.isNumberValue shouldBe false
    }
  }

  @Test
  fun `Value isStringValue returns true when kindCase is STRING_VALUE`() = runTest {
    checkAll(propTestConfig, Arb.proto.stringValue()) { value -> value.isStringValue shouldBe true }
  }

  @Test
  fun `Value isStringValue returns false when kindCase is not STRING_VALUE`() = runTest {
    checkAll(propTestConfig, Arb.proto.value(exclude = Value.KindCase.STRING_VALUE)) { value ->
      value.isStringValue shouldBe false
    }
  }

  @Test
  fun `Value isBoolValue returns true when kindCase is BOOL_VALUE`() = runTest {
    checkAll(propTestConfig, Exhaustive.proto.boolValue()) { value ->
      value.isBoolValue shouldBe true
    }
  }

  @Test
  fun `Value isBoolValue returns false when kindCase is not BOOL_VALUE`() = runTest {
    checkAll(propTestConfig, Arb.proto.value(exclude = Value.KindCase.BOOL_VALUE)) { value ->
      value.isBoolValue shouldBe false
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

  @Test
  fun `Value isKindNotSet returns true when kindCase is KIND_NOT_SET`() = runTest {
    checkAll(propTestConfig, Exhaustive.proto.kindNotSetValue()) { value ->
      value.isKindNotSet shouldBe true
    }
  }

  @Test
  fun `Value isKindNotSet returns false when kindCase is not KIND_NOT_SET`() = runTest {
    checkAll(propTestConfig, Arb.proto.value(exclude = Value.KindCase.KIND_NOT_SET)) { value ->
      value.isKindNotSet shouldBe false
    }
  }

  @Test
  fun `Value numberValueOrNull returns numberValue when kindCase is NUMBER_VALUE`() = runTest {
    checkAll(propTestConfig, Arb.proto.numberValue()) { value ->
      value.numberValueOrNull shouldBe value.numberValue
    }
  }

  @Test
  fun `Value numberValueOrNull returns null when kindCase is not NUMBER_VALUE`() = runTest {
    checkAll(propTestConfig, Arb.proto.value(exclude = Value.KindCase.NUMBER_VALUE)) { value ->
      value.numberValueOrNull.shouldBeNull()
    }
  }

  @Test
  fun `Value boolValueOrNull returns boolValue when kindCase is BOOL_VALUE`() = runTest {
    checkAll(propTestConfig, Arb.proto.boolValue()) { value ->
      value.boolValueOrNull shouldBe value.boolValue
    }
  }

  @Test
  fun `Value boolValueOrNull returns null when kindCase is not BOOL_VALUE`() = runTest {
    checkAll(propTestConfig, Arb.proto.value(exclude = Value.KindCase.BOOL_VALUE)) { value ->
      value.boolValueOrNull.shouldBeNull()
    }
  }

  @Test
  fun `Value stringValueOrNull returns stringValue when kindCase is STRING_VALUE`() = runTest {
    checkAll(propTestConfig, Arb.proto.stringValue()) { value ->
      value.stringValueOrNull shouldBe value.stringValue
    }
  }

  @Test
  fun `Value stringValueOrNull returns null when kindCase is not STRING_VALUE`() = runTest {
    checkAll(propTestConfig, Arb.proto.value(exclude = Value.KindCase.STRING_VALUE)) { value ->
      value.stringValueOrNull.shouldBeNull()
    }
  }

  @Test
  fun `Value structValueOrNull returns structValue when kindCase is STRUCT_VALUE`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct().map { it.struct.toValueProto() }) { value ->
      value.structValueOrNull shouldBe value.structValue
    }
  }

  @Test
  fun `Value structValueOrNull returns null when kindCase is not STRUCT_VALUE`() = runTest {
    checkAll(propTestConfig, Arb.proto.value(exclude = Value.KindCase.STRUCT_VALUE)) { value ->
      value.structValueOrNull.shouldBeNull()
    }
  }

  @Test
  fun `Value listValueOrNull returns listValue when kindCase is LIST_VALUE`() = runTest {
    checkAll(propTestConfig, Arb.proto.listValue().map { it.listValue.toValueProto() }) { value ->
      value.listValueOrNull shouldBe value.listValue
    }
  }

  @Test
  fun `Value listValueOrNull returns null when kindCase is not LIST_VALUE`() = runTest {
    checkAll(propTestConfig, Arb.proto.value(exclude = Value.KindCase.LIST_VALUE)) { value ->
      value.listValueOrNull.shouldBeNull()
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
