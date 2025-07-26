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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class EnumValueSerializerUnitTest {

  @Test
  fun `serialize() should produce the expected serialized string for _known_ values`() = runTest {
    checkAll(propTestConfig, Arb.knownEnumValue()) { knownEnumValue: EnumValue.Known<Dog> ->
      val encodedValue = Json.encodeToJsonElement(Dog.serializer, knownEnumValue)
      encodedValue.jsonPrimitive.content shouldBe knownEnumValue.value.name
    }
  }

  @Test
  fun `serialize() should produce the expected serialized string for _unknown_ values`() = runTest {
    checkAll(propTestConfig, Arb.unknownEnumValue()) { unknownEnumValue: EnumValue.Unknown ->
      val encodedValue = Json.encodeToJsonElement(Dog.serializer, unknownEnumValue)
      encodedValue.jsonPrimitive.content shouldBe unknownEnumValue.stringValue
    }
  }

  @Test
  fun `deserialize() should produce the expected EnumValue object for _known_ values`() = runTest {
    checkAll(propTestConfig, Arb.knownEnumValue()) { knownEnumValue: EnumValue.Known<Dog> ->
      val encodedValue = JsonPrimitive(knownEnumValue.value.name)
      val decodedEnumValue = Json.decodeFromJsonElement(Dog.serializer, encodedValue)
      decodedEnumValue shouldBe knownEnumValue
    }
  }

  @Test
  fun `deserialize() should produce the expected EnumValue object for _unknown_ values`() =
    runTest {
      checkAll(propTestConfig, Arb.unknownEnumValue()) { unknownEnumValue: EnumValue.Unknown ->
        val encodedValue = JsonPrimitive(unknownEnumValue.stringValue)
        val decodedEnumValue = Json.decodeFromJsonElement(Dog.serializer, encodedValue)
        decodedEnumValue shouldBe unknownEnumValue
      }
    }

  @Suppress("unused")
  private enum class Dog {
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

    fun Arb.Companion.unknownEnumValue(
      stringValue: Arb<String> = Arb.dataConnect.string()
    ): Arb<EnumValue.Unknown> = stringValue.map { EnumValue.Unknown(it) }

    fun Arb.Companion.knownEnumValue(
      enumValue: Arb<Dog> = Arb.enum<Dog>()
    ): Arb<EnumValue.Known<Dog>> = enumValue.map { EnumValue.Known(it) }
  }
}
