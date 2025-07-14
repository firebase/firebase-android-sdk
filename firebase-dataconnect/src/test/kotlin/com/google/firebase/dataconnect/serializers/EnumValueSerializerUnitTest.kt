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
@file:OptIn(ExperimentalKotest::class)

package com.google.firebase.dataconnect.serializers

import com.google.firebase.dataconnect.EnumValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.util.ProtoUtil.decodeFromValue
import com.google.firebase.dataconnect.util.ProtoUtil.encodeToValue
import com.google.protobuf.Value
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import org.junit.Test

class EnumValueSerializerUnitTest {

  @Test
  fun `serialize() should produce the expected serialized string for _known_ values`() = runTest {
    checkAll(propTestConfig, Arb.knownEnumValue()) { knownEnumValue: EnumValue.Known<Dog> ->
      val value = encodeToValue(knownEnumValue, Dog.serializer, serializersModule = null)
      value.stringValue shouldBe knownEnumValue.value.name
    }
  }

  @Test
  fun `serialize() should produce the expected serialized string for _unknown_ values`() = runTest {
    checkAll(propTestConfig, Arb.unknownEnumValue()) { unknownEnumValue: EnumValue.Unknown ->
      val value = encodeToValue(unknownEnumValue, Dog.serializer, serializersModule = null)
      value.stringValue shouldBe unknownEnumValue.stringValue
    }
  }

  @Test
  fun `deserialize() should produce the expected EnumValue object for _known_ values`() = runTest {
    checkAll(propTestConfig, Arb.knownEnumValue()) { knownEnumValue: EnumValue.Known<Dog> ->
      val value = knownEnumValue.toValueProto()
      val decodedEnumValue = decodeFromValue(value, Dog.serializer, serializersModule = null)
      decodedEnumValue shouldBe knownEnumValue
    }
  }

  @Test
  fun `deserialize() should produce the expected EnumValue object for _unknown_ values`() =
    runTest {
      checkAll(propTestConfig, Arb.unknownEnumValue()) { unknownEnumValue: EnumValue.Unknown ->
        val value = unknownEnumValue.toValueProto()
        val decodedEnumValue = decodeFromValue(value, Dog.serializer, serializersModule = null)
        decodedEnumValue shouldBe unknownEnumValue
      }
    }

  @Suppress("unused")
  enum class Dog {
    Boxer,
    Bulldog,
    Dachshund,
    Labrador,
    Poodle;

    companion object {
      val serializer: KSerializer<EnumValue<Dog>> = EnumValueSerializer(Dog.entries)
    }
  }

  private companion object {
    val propTestConfig =
      PropTestConfig(
        iterations = 500,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2)
      )

    fun EnumValue<*>.toValueProto(): Value = Value.newBuilder().setStringValue(stringValue).build()

    fun Arb.Companion.unknownEnumValue(
      stringValue: Arb<String> = Arb.dataConnect.string()
    ): Arb<EnumValue.Unknown> = stringValue.map { EnumValue.Unknown(it) }

    fun Arb.Companion.knownEnumValue(
      enumValue: Arb<Dog> = Arb.enum<Dog>()
    ): Arb<EnumValue.Known<Dog>> = enumValue.map { EnumValue.Known(it) }
  }
}
