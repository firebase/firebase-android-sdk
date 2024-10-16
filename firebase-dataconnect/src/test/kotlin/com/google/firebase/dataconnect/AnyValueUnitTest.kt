/*
 * Copyright 2024 Google LLC
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

package com.google.firebase.dataconnect

import com.google.firebase.dataconnect.serializers.AnyValueSerializer
import com.google.firebase.dataconnect.testutil.DataConnectAnySerializer
import com.google.firebase.dataconnect.testutil.property.arbitrary.EdgeCases
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.filterNotNull
import com.google.firebase.dataconnect.util.ProtoUtil.encodeToValue
import io.kotest.assertions.assertSoftly
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.junit.Test

@Suppress("ReplaceCallWithBinaryOperator")
@OptIn(ExperimentalKotest::class)
class AnyValueUnitTest {

  @Test
  fun `default serializer should be AnyValueSerializer`() {
    serializer<AnyValue>() shouldBeSameInstanceAs AnyValueSerializer
  }

  @Test
  fun `constructor(String) creates an object with the expected value (edge cases)`() = runTest {
    for (value in EdgeCases.anyScalar.strings) {
      AnyValue(value).value shouldBe value
    }
  }

  @Test
  fun `constructor(String) creates an object with the expected value (normal cases)`() = runTest {
    checkAll(normalCasePropTestConfig, Arb.dataConnect.anyScalar.string()) {
      AnyValue(it).value shouldBe it
    }
  }

  @Test
  fun `constructor(Double) creates an object with the expected value (edge cases)`() = runTest {
    for (value in EdgeCases.anyScalar.numbers) {
      AnyValue(value).value shouldBe value
    }
  }

  @Test
  fun `constructor(Double) creates an object with the expected value (normal cases)`() = runTest {
    checkAll(normalCasePropTestConfig, Arb.dataConnect.anyScalar.number()) {
      AnyValue(it).value shouldBe it
    }
  }

  @Test
  fun `constructor(Boolean) creates an object with the expected value`() {
    assertSoftly {
      AnyValue(true).value shouldBe true
      AnyValue(false).value shouldBe false
    }
  }

  @Test
  fun `constructor(List) creates an object with the expected value (edge cases)`() {
    assertSoftly {
      for (value in EdgeCases.anyScalar.lists) {
        AnyValue(value).value shouldBe value
      }
    }
  }

  @Test
  fun `constructor(List) creates an object with the expected value (normal cases)`() = runTest {
    checkAll(normalCasePropTestConfig, Arb.dataConnect.anyScalar.list()) {
      AnyValue(it).value shouldBe it
    }
  }

  @Test
  fun `constructor(Map) creates an object with the expected value (edge cases)`() {
    assertSoftly {
      for (value in EdgeCases.anyScalar.maps) {
        AnyValue(value).value shouldBe value
      }
    }
  }

  @Test
  fun `constructor(Map) creates an object with the expected value (normal cases)`() = runTest {
    checkAll(normalCasePropTestConfig, Arb.dataConnect.anyScalar.map()) {
      AnyValue(it).value shouldBe it
    }
  }

  @Test
  fun `decode() can decode strings (edge cases)`() {
    val serializer = serializer<String>()
    assertSoftly {
      for (value in EdgeCases.anyScalar.strings) {
        AnyValue(value).decode(serializer) shouldBe value
      }
    }
  }

  @Test
  fun `decode() can decode strings (normal cases)`() = runTest {
    val serializer = serializer<String>()
    checkAll(normalCasePropTestConfig, Arb.dataConnect.anyScalar.string()) {
      AnyValue(it).decode(serializer) shouldBe it
    }
  }

  @Test
  fun `decode() can decode doubles (edge cases)`() {
    val serializer = serializer<Double>()
    assertSoftly {
      for (value in EdgeCases.anyScalar.numbers) {
        AnyValue(value).decode(serializer) shouldBe value
      }
    }
  }

  @Test
  fun `decode() can decode doubles (normal cases)`() = runTest {
    val serializer = serializer<Double>()
    checkAll(normalCasePropTestConfig, Arb.dataConnect.anyScalar.number()) {
      AnyValue(it).decode(serializer) shouldBe it
    }
  }

  @Test
  fun `decode() can decode booleans (edge cases)`() {
    val serializer = serializer<Boolean>()
    assertSoftly {
      AnyValue(true).decode(serializer) shouldBe true
      AnyValue(false).decode(serializer) shouldBe false
    }
  }

  @Test
  fun `decode() can decode lists (edge cases)`() {
    val serializer = ListSerializer(DataConnectAnySerializer)
    assertSoftly {
      for (value in EdgeCases.anyScalar.lists) {
        AnyValue(value).decode(serializer) shouldContainExactly value
      }
    }
  }

  @Test
  fun `decode() can decode lists (normal cases)`() = runTest {
    val serializer = ListSerializer(DataConnectAnySerializer)
    checkAll(normalCasePropTestConfig, Arb.dataConnect.anyScalar.list()) {
      AnyValue(it).decode(serializer) shouldContainExactly it
    }
  }

  @Test
  fun `decode() can decode maps (edge cases)`() {
    val serializer = MapSerializer<String, Any?>(serializer(), DataConnectAnySerializer)
    assertSoftly {
      for (value in EdgeCases.anyScalar.maps) {
        AnyValue(value).decode(serializer) shouldBe value
      }
    }
  }

  @Test
  fun `decode() can decode maps (normal cases)`() = runTest {
    val serializer = MapSerializer<String, Any?>(serializer(), DataConnectAnySerializer)
    checkAll(normalCasePropTestConfig, Arb.dataConnect.anyScalar.map()) {
      AnyValue(it).decode(serializer) shouldBe it
    }
  }

  @Test
  fun `decode() can decode nested AnyValue`() {
    @Serializable data class TestData(val a: Int, val b: AnyValue)
    val nestedAnyValueList = listOf("a", 12.34, mapOf("foo" to "bar"))
    val anyValue = AnyValue(mapOf("a" to 12.0, "b" to nestedAnyValueList))

    anyValue.decode(serializer<TestData>()) shouldBe
      TestData(a = 12, b = AnyValue(nestedAnyValueList))
  }

  @Test
  fun `decode() can decode @Serializable class with non-nullable scalar properties`() {
    @Serializable
    data class TestData(
      val int: Int,
      val string: String,
      val boolean: Boolean,
      val double: Double,
    )
    val testData = TestData(42, "w82qq4jbb6", true, 12.34)
    val anyValue = AnyValue(encodeToValue(testData))

    anyValue.decode(serializer<TestData>()) shouldBe testData
  }

  @Test
  fun `decode() can decode @Serializable class with nullable scalar properties with non-null values`() {
    @Serializable
    data class TestData(
      val int: Int?,
      val string: String?,
      val boolean: Boolean?,
      val double: Double?,
    )
    val testData = TestData(42, "srg7yzecwq", true, 12.34)
    val anyValue = AnyValue(encodeToValue(testData))

    anyValue.decode(serializer<TestData>()) shouldBe testData
  }

  @Test
  fun `decode() can decode @Serializable class with nullable scalar properties with null values`() {
    @Serializable
    data class TestData(
      val int: Int?,
      val string: String?,
      val boolean: Boolean?,
      val double: Double?,
    )
    val testData = TestData(null, null, null, null)
    val anyValue = AnyValue(encodeToValue(testData))

    anyValue.decode(serializer<TestData>()) shouldBe testData
  }

  @Test
  fun `decode() can decode @Serializable class with nested values`() {
    @Serializable data class Foo(val int: Int, val foo: Foo? = null)
    @Serializable data class TestData(val list: List<Foo>, val foo: Foo)
    val testData = TestData(listOf(Foo(111), Foo(222, Foo(333))), Foo(444, Foo(555)))
    val anyValue = AnyValue(encodeToValue(testData))

    anyValue.decode(serializer<TestData>()) shouldBe testData
  }

  @Test
  fun `decode() passes along the serialization module`() {
    val capturedSerializerModule = MutableStateFlow<SerializersModule?>(null)
    val stringSerializer = serializer<String>()
    val serializer =
      object : DeserializationStrategy<String> by stringSerializer {
        override fun deserialize(decoder: Decoder): String {
          capturedSerializerModule.value = decoder.serializersModule
          return stringSerializer.deserialize(decoder)
        }
      }
    val serializerModule = SerializersModule {}
    val anyValue = AnyValue("yqvjgabk2e")

    anyValue.decode(serializer, serializerModule)

    capturedSerializerModule.value shouldBeSameInstanceAs serializerModule
  }

  @Test
  fun `decode() uses the default serializer if not explicitly specified`() {
    val anyValue = AnyValue("mb6jq8jabp")
    anyValue.decode<String>() shouldBe "mb6jq8jabp"
  }

  @Test
  fun `equals(this) returns true`() {
    val anyValue = AnyValue(42.0)
    anyValue.equals(anyValue).shouldBeTrue()
  }

  @Test
  fun `equals(equal, but distinct, instance) returns true`() = runTest {
    checkAll(iterations = 1000, Arb.dataConnect.anyScalar.any().filterNotNull()) {
      val anyValue1 = AnyValue.fromAny(it)
      val anyValue2 = AnyValue.fromAny(it)
      anyValue1.equals(anyValue2).shouldBeTrue()
    }
  }

  @Test
  fun `equals(null) returns false`() {
    val anyValue = AnyValue(42.0)
    anyValue.equals(null).shouldBeFalse()
  }

  @Test
  fun `equals(some other type) returns false`() {
    val anyValue = AnyValue(42.0)
    anyValue.equals("not an AnyValue object").shouldBeFalse()
  }

  @Test
  fun `equals(unequal instance) returns false`() = runTest {
    val values = Arb.dataConnect.anyScalar.any().filterNotNull()
    checkAll(values) { value ->
      val anyValue1 = AnyValue.fromAny(value)
      val anyValue2 = AnyValue.fromAny(values.filterNot { it == value }.bind())
      anyValue1.equals(anyValue2).shouldBeFalse()
    }
  }

  @Test
  fun `hashCode() should return the same value when invoked repeatedly`() = runTest {
    val values = Arb.dataConnect.anyScalar.any().filterNotNull().map(AnyValue::fromAny)
    checkAll(values) { anyValue ->
      val hashCode = anyValue.hashCode()
      val hashCodes = List(100) { anyValue.hashCode() }.toSet()
      hashCodes.shouldContainExactly(hashCode)
    }
  }

  @Test
  fun `hashCode() should return different value when the encapsulated value has a different hash code`() =
    runTest {
      val values = Arb.dataConnect.anyScalar.any().filterNotNull()
      checkAll(normalCasePropTestConfig, values) { value1 ->
        val value2 = values.bind()
        val anyValue1 = AnyValue.fromAny(value1)
        val anyValue2 = AnyValue.fromAny(value2)
        if (value1.hashCode() == value2.hashCode()) {
          anyValue1.hashCode() shouldBe anyValue2.hashCode()
        } else {
          anyValue1.hashCode() shouldNotBe anyValue2.hashCode()
        }
      }
    }

  @Test
  fun `toString() should not throw`() = runTest {
    val values = Arb.dataConnect.anyScalar.any().filterNotNull().map(AnyValue::fromAny)
    checkAll(normalCasePropTestConfig, values) { it.toString() }
  }

  @Test
  fun `encode() can encode strings (edge cases)`() {
    val serializer = serializer<String>()
    assertSoftly {
      for (value in EdgeCases.anyScalar.strings) {
        AnyValue.encode(value, serializer) shouldBe AnyValue(value)
      }
    }
  }

  @Test
  fun `encode() can encode strings (normal cases)`() = runTest {
    val serializer = serializer<String>()
    checkAll(normalCasePropTestConfig, Arb.dataConnect.anyScalar.string()) {
      AnyValue.encode(it, serializer) shouldBe AnyValue(it)
    }
  }

  @Test
  fun `encode() can encode doubles (edge cases)`() {
    val serializer = serializer<Double>()
    assertSoftly {
      for (value in EdgeCases.anyScalar.numbers) {
        AnyValue.encode(value, serializer) shouldBe AnyValue(value)
      }
    }
  }

  @Test
  fun `encode() can encode doubles (normal cases)`() = runTest {
    val serializer = serializer<Double>()
    checkAll(normalCasePropTestConfig, Arb.dataConnect.anyScalar.number()) {
      AnyValue.encode(it, serializer) shouldBe AnyValue(it)
    }
  }

  @Test
  fun `encode() can encode booleans (edge cases)`() {
    val serializer = serializer<Boolean>()
    assertSoftly {
      AnyValue.encode(true, serializer) shouldBe AnyValue(true)
      AnyValue.encode(false, serializer) shouldBe AnyValue(false)
    }
  }

  @Test
  fun `encode() can encode lists (edge cases)`() {
    val serializer = ListSerializer(DataConnectAnySerializer)
    assertSoftly {
      for (value in EdgeCases.anyScalar.lists) {
        AnyValue.encode(value, serializer) shouldBe AnyValue(value)
      }
    }
  }

  @Test
  fun `encode() can encode lists (normal cases)`() = runTest {
    val serializer = ListSerializer(DataConnectAnySerializer)
    checkAll(normalCasePropTestConfig, Arb.dataConnect.anyScalar.list()) {
      AnyValue.encode(it, serializer) shouldBe AnyValue(it)
    }
  }

  @Test
  fun `encode() can encode maps (edge cases)`() {
    val serializer = MapSerializer<String, Any?>(serializer(), DataConnectAnySerializer)
    assertSoftly {
      for (value in EdgeCases.anyScalar.maps) {
        AnyValue.encode(value, serializer) shouldBe AnyValue(value)
      }
    }
  }

  @Test
  fun `encode() can encode maps (normal cases)`() = runTest {
    val serializer = MapSerializer<String, Any?>(serializer(), DataConnectAnySerializer)
    checkAll(normalCasePropTestConfig, Arb.dataConnect.anyScalar.map()) {
      AnyValue.encode(it, serializer) shouldBe AnyValue(it)
    }
  }

  @Test
  fun `encode() can encode nested AnyValue`() {
    @Serializable data class TestData(val a: Int, val b: AnyValue)
    val nestedAnyValueList = listOf("a", 12.34, mapOf("foo" to "bar"))
    val testData = TestData(a = 12, b = AnyValue(nestedAnyValueList))

    val anyValue = AnyValue.encode(testData)

    anyValue.value shouldBe mapOf("a" to 12.0, "b" to nestedAnyValueList)
  }

  @Test
  fun `encode() can encode @Serializable class with non-nullable scalar properties`() {
    @Serializable
    data class TestData(
      val int: Int,
      val string: String,
      val boolean: Boolean,
      val double: Double,
    )
    val testData = TestData(42, "gkg3jsp2jz", true, 12.34)

    val anyValue = AnyValue.encode(testData)

    anyValue.value shouldBe
      (mapOf("int" to 42.0, "string" to "gkg3jsp2jz", "boolean" to true, "double" to 12.34))
  }

  @Test
  fun `encode() can encode @Serializable class with nullable scalar properties with non-null values`() {
    @Serializable
    data class TestData(
      val int: Int?,
      val string: String?,
      val boolean: Boolean?,
      val double: Double?,
    )
    val testData = TestData(42, "mj64xgc2sz", true, 12.34)

    val anyValue = AnyValue.encode(testData)

    anyValue.value shouldBe
      (mapOf("int" to 42.0, "string" to "mj64xgc2sz", "boolean" to true, "double" to 12.34))
  }

  @Test
  fun `encode() can encode @Serializable class with nullable scalar properties with null values`() {
    @Serializable
    data class TestData(
      val int: Int?,
      val string: String?,
      val boolean: Boolean?,
      val double: Double?,
    )
    val testData = TestData(null, null, null, null)

    val anyValue = AnyValue.encode(testData)

    anyValue.value shouldBe
      (mapOf("int" to null, "string" to null, "boolean" to null, "double" to null))
  }

  @Test
  fun `encode() can encode @Serializable class with nested values`() {
    @Serializable data class Foo(val int: Int, val foo: Foo? = null)
    @Serializable data class TestData(val list: List<Foo>, val foo: Foo)
    val testData = TestData(listOf(Foo(111), Foo(222, Foo(333))), Foo(444, Foo(555)))

    val anyValue = AnyValue.encode(testData)

    anyValue.value shouldBe
      (mapOf(
        "list" to
          listOf(
            mapOf("int" to 111.0, "foo" to null),
            mapOf("int" to 222.0, "foo" to mapOf("int" to 333.0, "foo" to null))
          ),
        "foo" to mapOf("int" to 444.0, "foo" to mapOf("int" to 555.0, "foo" to null))
      ))
  }

  @Test
  fun `encode() passes along the serialization module`() {
    val capturedSerializerModule = MutableStateFlow<SerializersModule?>(null)
    val stringSerializer = serializer<String>()
    val serializer =
      object : SerializationStrategy<String> by stringSerializer {
        override fun serialize(encoder: Encoder, value: String) {
          capturedSerializerModule.value = encoder.serializersModule
          return stringSerializer.serialize(encoder, value)
        }
      }
    val serializerModule = SerializersModule {}

    AnyValue.encode("jn7wve4qwt", serializer, serializerModule)

    capturedSerializerModule.value shouldBeSameInstanceAs serializerModule
  }

  @Test
  fun `encode() uses the default serializer if not explicitly specified`() {
    AnyValue.encode("we47rcjzm4") shouldBe AnyValue("we47rcjzm4")
  }

  @Test
  fun `fromNullableAny() edge cases`() {
    for (value in EdgeCases.anyScalar.all) {
      val anyValue = AnyValue.fromAny(value)
      if (value === null) {
        anyValue.shouldBeNull()
      } else {
        anyValue.shouldNotBeNull()
        anyValue.value shouldBe value
      }
    }
  }

  @Test
  fun `fromNullableAny() normal cases`() = runTest {
    checkAll(normalCasePropTestConfig, Arb.dataConnect.anyScalar.any()) { value ->
      val anyValue = AnyValue.fromAny(value)
      if (value === null) {
        anyValue.shouldBeNull()
      } else {
        anyValue.shouldNotBeNull()
        anyValue.value shouldBe value
      }
    }
  }

  @Test
  fun `fromNonNullableAny() edge cases`() {
    for (value in EdgeCases.anyScalar.all.filterNotNull()) {
      val anyValue = AnyValue.fromAny(value)
      anyValue.value shouldBe value
    }
  }

  @Test
  fun `fromNonNullableAny() normal cases`() = runTest {
    checkAll(normalCasePropTestConfig, Arb.dataConnect.anyScalar.any().filterNotNull()) { value ->
      val anyValue = AnyValue.fromAny(value)
      anyValue.shouldNotBeNull()
      anyValue.value shouldBe value
    }
  }

  private companion object {

    val normalCasePropTestConfig =
      PropTestConfig(
        iterations = 100,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.0)
      )
  }
}
