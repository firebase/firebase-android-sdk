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
@file:OptIn(ExperimentalKotest::class)

package com.google.firebase.dataconnect

import com.google.firebase.dataconnect.SerializationTestData.serializationTestDataAllTypes
import com.google.firebase.dataconnect.SerializationTestData.withEmptyListOfUnitRecursive
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.twoValues
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import com.google.firebase.dataconnect.util.ProtoUtil.buildStructProto
import com.google.firebase.dataconnect.util.ProtoUtil.decodeFromStruct
import com.google.firebase.dataconnect.util.ProtoUtil.encodeToStruct
import com.google.protobuf.Struct
import com.google.protobuf.Value
import com.google.protobuf.Value.KindCase
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import org.junit.Ignore
import org.junit.Test

class ProtoStructDecoderUnitTest {

  @Test
  fun `decodeFromStruct() can encode and decode complex objects`() = runTest {
    // TODO(b/370992204) Remove the call to withEmptyListOfUnitRecursive() once the bug that a list
    //  of Unit is incorrectly decoded as an empty list is fixed.
    val arb = Arb.serializationTestDataAllTypes().map { it.withEmptyListOfUnitRecursive() }
    checkAll(propTestConfig, arb) { obj ->
      val struct = encodeToStruct(obj)
      val decodedObj = decodeFromStruct<SerializationTestData.AllTheTypes>(struct)
      decodedObj shouldBe obj
    }
  }

  @Ignore(
    "b/370992204: Re-enable this test once the bug that a list of Unit is incorrectly " +
      "decoded as an empty list is fixed"
  )
  @Test
  fun `decodeFromStruct() can encode and decode a list of non-nullable Unit`() = runTest {
    @Serializable data class TestData(val list: List<Unit>)
    checkAll(propTestConfig, Arb.list(Arb.constant(Unit))) { list ->
      val struct = encodeToStruct(TestData(list))
      val decodedObj = decodeFromStruct<TestData>(struct)
      decodedObj shouldBe TestData(list)
    }
  }

  @Test
  fun `decodeFromStruct() can encode and decode a list of nullable Unit`() = runTest {
    @Serializable data class TestData(val list: List<Unit?>)
    checkAll(propTestConfig, Arb.list(Arb.constant(Unit).orNull())) { list ->
      val struct = encodeToStruct(TestData(list))
      val decodedObj = decodeFromStruct<TestData>(struct)
      decodedObj shouldBe TestData(list)
    }
  }

  @Test
  fun `decodeFromStruct() can decode a Struct to Unit`() {
    val decodedTestData = decodeFromStruct<Unit>(Struct.getDefaultInstance())
    decodedTestData shouldBeSameInstanceAs Unit
  }

  @Test
  fun `decodeFromStruct() can decode a Struct with String values`() = runTest {
    @Serializable data class TestData(val value1: String, val value2: String)
    val strings = Arb.string()
    checkAll(propTestConfig, strings, strings) { value1, value2 ->
      val struct = encodeToStruct(TestData(value1 = value1, value2 = value2))
      val decodedTestData = decodeFromStruct<TestData>(struct)
      decodedTestData shouldBe TestData(value1 = value1, value2 = value2)
    }
  }

  @Test
  fun `decodeFromStruct() can decode a Struct with _nullable_ String values`() = runTest {
    @Serializable data class TestData(val value1: String?, val value2: String?)
    val nullableStrings = Arb.string().orNull()
    checkAll(propTestConfig, nullableStrings, nullableStrings) { value1, value2 ->
      val struct = encodeToStruct(TestData(value1 = value1, value2 = value2))
      val decodedTestData = decodeFromStruct<TestData>(struct)
      decodedTestData shouldBe TestData(value1 = value1, value2 = value2)
    }
  }

  @Test
  fun `decodeFromStruct() can decode a Struct with Boolean values`() = runTest {
    @Serializable data class TestData(val value1: Boolean, val value2: Boolean)
    val booleans = Arb.boolean()
    checkAll(propTestConfig, booleans, booleans) { value1, value2 ->
      val struct = encodeToStruct(TestData(value1 = value1, value2 = value2))
      val decodedTestData = decodeFromStruct<TestData>(struct)
      decodedTestData shouldBe TestData(value1 = value1, value2 = value2)
    }
  }

  @Test
  fun `decodeFromStruct() can decode a Struct with _nullable_ Boolean values`() = runTest {
    @Serializable data class TestData(val value1: Boolean?, val value2: Boolean?)
    val nullableBooleans = Arb.boolean().orNull()
    checkAll(propTestConfig, nullableBooleans, nullableBooleans) { value1, value2 ->
      val struct = encodeToStruct(TestData(value1 = value1, value2 = value2))
      val decodedTestData = decodeFromStruct<TestData>(struct)
      decodedTestData shouldBe TestData(value1 = value1, value2 = value2)
    }
  }

  @Test
  fun `decodeFromStruct() can decode a Struct with Int values`() = runTest {
    @Serializable data class TestData(val value1: Int, val value2: Int)
    val ints = Arb.int()
    checkAll(propTestConfig, ints, ints) { value1, value2 ->
      val struct = encodeToStruct(TestData(value1 = value1, value2 = value2))
      val decodedTestData = decodeFromStruct<TestData>(struct)
      decodedTestData shouldBe TestData(value1 = value1, value2 = value2)
    }
  }

  @Test
  fun `decodeFromStruct() can decode a Struct with _nullable_ Int values`() = runTest {
    @Serializable data class TestData(val value1: Int?, val value2: Int?)
    val nullableInts = Arb.int().orNull()
    checkAll(propTestConfig, nullableInts, nullableInts) { value1, value2 ->
      val struct = encodeToStruct(TestData(value1 = value1, value2 = value2))
      val decodedTestData = decodeFromStruct<TestData>(struct)
      decodedTestData shouldBe TestData(value1 = value1, value2 = value2)
    }
  }

  @Test
  fun `decodeFromStruct() can decode a Struct with Double values`() = runTest {
    @Serializable data class TestData(val value1: Double, val value2: Double)
    val doubles = Arb.double()
    checkAll(propTestConfig, doubles, doubles) { value1, value2 ->
      val struct = encodeToStruct(TestData(value1 = value1, value2 = value2))
      val decodedTestData = decodeFromStruct<TestData>(struct)
      decodedTestData shouldBe TestData(value1 = value1, value2 = value2)
    }
  }

  @Test
  fun `decodeFromStruct() can decode a Struct with _nullable_ Double values`() = runTest {
    @Serializable data class TestData(val value1: Double?, val value2: Double?)
    val nullableDoubles = Arb.double().orNull()
    checkAll(propTestConfig, nullableDoubles, nullableDoubles) { value1, value2 ->
      val struct = encodeToStruct(TestData(value1 = value1, value2 = value2))
      val decodedTestData = decodeFromStruct<TestData>(struct)
      decodedTestData shouldBe TestData(value1 = value1, value2 = value2)
    }
  }

  @Test
  fun `decodeFromStruct() can decode a Struct with nested Struct values`() = runTest {
    @Serializable data class TestDataA(val base: String)
    @Serializable data class TestDataB(val dataA: TestDataA)
    @Serializable data class TestDataC(val dataB: TestDataB)
    @Serializable data class TestDataD(val dataC: TestDataC)
    val arb: Arb<TestDataD> = arbitrary {
      TestDataD(TestDataC(TestDataB(TestDataA(Arb.string().bind()))))
    }

    checkAll(propTestConfig, arb) { value ->
      val struct = encodeToStruct(value)
      val decodedTestData = decodeFromStruct<TestDataD>(struct)
      decodedTestData shouldBe value
    }
  }

  @Test
  fun `decodeFromStruct() can decode a Struct with nested _nullable_ Struct values`() = runTest {
    @Serializable data class TestDataA(val base: String)
    @Serializable data class TestDataB(val child1: TestDataA?, val child2: TestDataA?)
    @Serializable data class TestDataC(val child1: TestDataB?, val child2: TestDataB?)
    @Serializable data class TestDataD(val child1: TestDataC?, val child2: TestDataC?)
    val arbA: Arb<TestDataA> = arbitrary { TestDataA(Arb.string().bind()) }
    val arbB: Arb<TestDataB> = arbitrary { arbA.orNull(0.2).run { TestDataB(bind(), bind()) } }
    val arbC: Arb<TestDataC> = arbitrary { arbB.orNull(0.2).run { TestDataC(bind(), bind()) } }
    val arbD: Arb<TestDataD> = arbitrary { arbC.orNull(0.2).run { TestDataD(bind(), bind()) } }

    checkAll(propTestConfig, arbD) { value ->
      val struct = encodeToStruct(value)
      val decodedTestData = decodeFromStruct<TestDataD>(struct)
      decodedTestData shouldBe value
    }
  }

  @Test
  fun `decodeFromStruct() can decode a Struct with nullable ListValue values`() = runTest {
    @Serializable data class TestData(val value1: List<String>?, val value2: List<String>?)
    val arb: Arb<TestData> = arbitrary {
      Arb.list(Arb.string()).orNull(0.33).run { TestData(bind(), bind()) }
    }

    checkAll(propTestConfig, arb) { value ->
      val struct = encodeToStruct(value)
      val decodedTestData = decodeFromStruct<TestData>(struct)
      decodedTestData shouldBe value
    }
  }

  @Test
  fun `decodeFromStruct() can decode a ListValue of String`() = runTest {
    @Serializable data class TestData(val value1: List<String>, val value2: List<String>)
    val arb: Arb<TestData> = arbitrary { Arb.list(Arb.string()).run { TestData(bind(), bind()) } }

    checkAll(propTestConfig, arb) { value ->
      val struct = encodeToStruct(value)
      val decodedTestData = decodeFromStruct<TestData>(struct)
      decodedTestData shouldBe value
    }
  }

  @Test
  fun `decodeFromStruct() can decode a ListValue of _nullable_ String`() = runTest {
    @Serializable data class TestData(val value1: List<String?>, val value2: List<String?>)
    val arb: Arb<TestData> = arbitrary {
      Arb.list(Arb.string().orNull(0.33)).run { TestData(bind(), bind()) }
    }

    checkAll(propTestConfig, arb) { value ->
      val struct = encodeToStruct(value)
      val decodedTestData = decodeFromStruct<TestData>(struct)
      decodedTestData shouldBe value
    }
  }

  @Test
  fun `decodeFromStruct() can decode a ListValue of Boolean`() = runTest {
    @Serializable data class TestData(val value1: List<Boolean>, val value2: List<Boolean>)
    val arb: Arb<TestData> = arbitrary { Arb.list(Arb.boolean()).run { TestData(bind(), bind()) } }

    checkAll(propTestConfig, arb) { value ->
      val struct = encodeToStruct(value)
      val decodedTestData = decodeFromStruct<TestData>(struct)
      decodedTestData shouldBe value
    }
  }

  @Test
  fun `decodeFromStruct() can decode a ListValue of _nullable_ Boolean`() = runTest {
    @Serializable data class TestData(val value1: List<Boolean?>, val value2: List<Boolean?>)
    val arb: Arb<TestData> = arbitrary {
      Arb.list(Arb.boolean().orNull(0.33)).run { TestData(bind(), bind()) }
    }

    checkAll(propTestConfig, arb) { value ->
      val struct = encodeToStruct(value)
      val decodedTestData = decodeFromStruct<TestData>(struct)
      decodedTestData shouldBe value
    }
  }

  @Test
  fun `decodeFromStruct() can decode a ListValue of Int`() = runTest {
    @Serializable data class TestData(val value1: List<Int>, val value2: List<Int>)
    val arb: Arb<TestData> = arbitrary { Arb.list(Arb.int()).run { TestData(bind(), bind()) } }

    checkAll(propTestConfig, arb) { value ->
      val struct = encodeToStruct(value)
      val decodedTestData = decodeFromStruct<TestData>(struct)
      decodedTestData shouldBe value
    }
  }

  @Test
  fun `decodeFromStruct() can decode a ListValue of _nullable_ Int`() = runTest {
    @Serializable data class TestData(val value1: List<Int?>, val value2: List<Int?>)
    val arb: Arb<TestData> = arbitrary {
      Arb.list(Arb.int().orNull(0.33)).run { TestData(bind(), bind()) }
    }

    checkAll(propTestConfig, arb) { value ->
      val struct = encodeToStruct(value)
      val decodedTestData = decodeFromStruct<TestData>(struct)
      decodedTestData shouldBe value
    }
  }

  @Test
  fun `decodeFromStruct() can decode a ListValue of Double`() = runTest {
    @Serializable data class TestData(val value1: List<Double>, val value2: List<Double>)
    val arb: Arb<TestData> = arbitrary { Arb.list(Arb.double()).run { TestData(bind(), bind()) } }

    checkAll(propTestConfig, arb) { value ->
      val struct = encodeToStruct(value)
      val decodedTestData = decodeFromStruct<TestData>(struct)
      decodedTestData shouldBe value
    }
  }

  @Test
  fun `decodeFromStruct() can decode a ListValue of _nullable_ Double`() = runTest {
    @Serializable data class TestData(val value1: List<Double?>, val value2: List<Double?>)
    val arb: Arb<TestData> = arbitrary {
      Arb.list(Arb.double().orNull(0.33)).run { TestData(bind(), bind()) }
    }

    checkAll(propTestConfig, arb) { value ->
      val struct = encodeToStruct(value)
      val decodedTestData = decodeFromStruct<TestData>(struct)
      decodedTestData shouldBe value
    }
  }

  @Test
  fun `decodeFromStruct() can decode a ListValue of Struct`() = runTest {
    @Serializable data class TestDataA(val s1: String, val s2: String?)
    @Serializable data class TestDataB(val value1: List<TestDataA>, val value2: List<TestDataA>)
    val arbA: Arb<TestDataA> = arbitrary {
      val value1 = Arb.string().bind()
      val value2 = Arb.string().orNull(0.33).bind()
      TestDataA(value1, value2)
    }
    val arb: Arb<TestDataB> = arbitrary { Arb.list(arbA).run { TestDataB(bind(), bind()) } }

    checkAll(propTestConfig, arb) { value ->
      val struct = encodeToStruct(value)
      val decodedTestData = decodeFromStruct<TestDataB>(struct)
      decodedTestData shouldBe value
    }
  }

  @Test
  fun `decodeFromStruct() can decode a ListValue of _nullable_ Struct`() = runTest {
    @Serializable data class TestDataA(val s1: String, val s2: String?)
    @Serializable data class TestDataB(val value1: List<TestDataA?>, val value2: List<TestDataA?>)
    val arbA: Arb<TestDataA> = arbitrary {
      val value1 = Arb.string().bind()
      val value2 = Arb.string().orNull(0.33).bind()
      TestDataA(value1, value2)
    }
    val arb: Arb<TestDataB> = arbitrary {
      Arb.list(arbA.orNull(0.33)).run { TestDataB(bind(), bind()) }
    }

    checkAll(propTestConfig, arb) { value ->
      val struct = encodeToStruct(value)
      val decodedTestData = decodeFromStruct<TestDataB>(struct)
      decodedTestData shouldBe value
    }
  }

  @Test
  fun `decodeFromStruct() can decode a ListValue of ListValue`() = runTest {
    @Serializable data class TestData(val value1: List<List<Int>>, val value2: List<List<Int>>)
    val arb: Arb<TestData> = arbitrary {
      Arb.list(Arb.list(Arb.int())).run { TestData(bind(), bind()) }
    }

    checkAll(propTestConfig, arb) { value ->
      val struct = encodeToStruct(value)
      val decodedTestData = decodeFromStruct<TestData>(struct)
      decodedTestData shouldBe value
    }
  }

  @Test
  fun `decodeFromStruct() can decode a ListValue of _nullable_ ListValue`() = runTest {
    @Serializable data class TestData(val value1: List<List<Int?>>, val value2: List<List<Int>?>)
    val arb: Arb<TestData> = arbitrary {
      val value1 = Arb.list(Arb.list(Arb.int().orNull(0.33))).bind()
      val value2 = Arb.list(Arb.list(Arb.int()).orNull(0.33)).bind()
      TestData(value1, value2)
    }

    checkAll(propTestConfig, arb) { value ->
      val struct = encodeToStruct(value)
      val decodedTestData = decodeFromStruct<TestData>(struct)
      decodedTestData shouldBe value
    }
  }

  @Test
  fun `decodeFromStruct() can decode a Struct with Inline values`() = runTest {
    @Serializable data class TestData(val s: TestStringValueClass, val i: TestIntValueClass)
    val arb: Arb<TestData> = arbitrary {
      TestData(Arb.testStringValueClass().bind(), Arb.testIntValueClass().bind())
    }

    checkAll(propTestConfig, arb) { value ->
      val struct = encodeToStruct(value)
      val decodedTestData = decodeFromStruct<TestData>(struct)
      decodedTestData shouldBe value
    }
  }

  @Test
  fun `decodeFromStruct() can decode a Struct with _nullable_ Inline values`() = runTest {
    @Serializable
    data class TestData(
      val string: TestStringValueClass?,
      val nullString: TestStringValueClass?,
      val int: TestIntValueClass?,
      val nullInt: TestIntValueClass?
    )
    val arb: Arb<TestData> = arbitrary {
      TestData(
        Arb.testStringValueClass().bind(),
        Arb.testStringValueClass().orNull(0.33).bind(),
        Arb.testIntValueClass().bind(),
        Arb.testIntValueClass().orNull(0.33).bind(),
      )
    }

    checkAll(propTestConfig, arb) { value ->
      val struct = encodeToStruct(value)
      val decodedTestData = decodeFromStruct<TestData>(struct)
      decodedTestData shouldBe value
    }
  }

  @Test
  fun `decodeFromStruct() can decode a ListValue with Inline values`() = runTest {
    @Serializable
    data class TestData(val s: List<TestStringValueClass>, val i: List<TestIntValueClass>)
    val arb: Arb<TestData> = arbitrary {
      TestData(
        Arb.list(Arb.testStringValueClass()).bind(),
        Arb.list(Arb.testIntValueClass()).bind(),
      )
    }

    checkAll(propTestConfig, arb) { value ->
      val struct = encodeToStruct(value)
      val decodedTestData = decodeFromStruct<TestData>(struct)
      decodedTestData shouldBe value
    }
  }

  @Test
  fun `decodeFromStruct() can decode a ListValue with _nullable_ Inline values`() = runTest {
    @Serializable
    data class TestData(val s: List<TestStringValueClass?>, val i: List<TestIntValueClass?>)
    val arb: Arb<TestData> = arbitrary {
      TestData(
        Arb.list(Arb.testStringValueClass().orNull(0.33)).bind(),
        Arb.list(Arb.testIntValueClass().orNull(0.33)).bind(),
      )
    }

    checkAll(propTestConfig, arb) { value ->
      val struct = encodeToStruct(value)
      val decodedTestData = decodeFromStruct<TestData>(struct)
      decodedTestData shouldBe value
    }
  }

  @Test
  fun `decodeFromStruct() ignores unknown struct keys`() = runTest {
    @Serializable data class TestData1(val value1: String, val value2: String)
    @Serializable data class TestData2(val value1: String)

    val testData1Arb: Arb<TestData1> =
      Arb.twoValues(Arb.dataConnect.string()).map { (value1, value2) -> TestData1(value1, value2) }

    checkAll(propTestConfig, testData1Arb) { testData1 ->
      val struct = encodeToStruct(testData1)
      val decodedTestData = decodeFromStruct<TestData2>(struct)
      decodedTestData shouldBe TestData2(testData1.value1)
    }
  }

  @Test
  fun `decodeFromStruct() should throw SerializationException if attempting to decode an Int`() {
    assertDecodeFromStructThrowsIncorrectKindCase<Int>(
      expectedKind = KindCase.NUMBER_VALUE,
      actualKind = KindCase.STRUCT_VALUE,
    )
  }

  @Test
  fun `decodeFromStruct() should throw SerializationException if attempting to decode a Double`() {
    assertDecodeFromStructThrowsIncorrectKindCase<Double>(
      expectedKind = KindCase.NUMBER_VALUE,
      actualKind = KindCase.STRUCT_VALUE,
    )
  }

  @Test
  fun `decodeFromStruct() should throw SerializationException if attempting to decode a Boolean`() {
    assertDecodeFromStructThrowsIncorrectKindCase<Boolean>(
      expectedKind = KindCase.BOOL_VALUE,
      actualKind = KindCase.STRUCT_VALUE
    )
  }

  @Test
  fun `decodeFromStruct() should throw SerializationException if attempting to decode a String`() {
    assertDecodeFromStructThrowsIncorrectKindCase<String>(
      expectedKind = KindCase.STRING_VALUE,
      actualKind = KindCase.STRUCT_VALUE
    )
  }

  @Test
  fun `decodeFromStruct() should throw SerializationException if attempting to decode a List`() {
    assertDecodeFromStructThrowsIncorrectKindCase<List<String>>(
      expectedKind = KindCase.LIST_VALUE,
      actualKind = KindCase.STRUCT_VALUE
    )
  }

  @Test
  fun `decodeFromStruct() should throw SerializationException if decoding a Boolean value found a different type`() {
    @Serializable data class TestEncodeSubData(val someValue: String)
    @Serializable data class TestEncodeData(val aaa: TestEncodeSubData)
    @Serializable data class TestDecodeSubData(val someValue: Boolean)
    @Serializable data class TestDecodeData(val aaa: TestDecodeSubData)

    assertDecodeFromStructThrowsIncorrectKindCase<TestDecodeData>(
      expectedKind = KindCase.BOOL_VALUE,
      actualKind = KindCase.STRING_VALUE,
      struct = encodeToStruct(TestEncodeData(TestEncodeSubData("foo"))),
      actualValue = "foo",
      path = "aaa.someValue"
    )
  }

  @Test
  fun `decodeFromStruct() should throw SerializationException if decoding an Int value found a different type`() {
    @Serializable data class TestEncodeSubData(val someValue: String)
    @Serializable data class TestEncodeData(val aaa: TestEncodeSubData)
    @Serializable data class TestDecodeSubData(val someValue: Int)
    @Serializable data class TestDecodeData(val aaa: TestDecodeSubData)

    assertDecodeFromStructThrowsIncorrectKindCase<TestDecodeData>(
      expectedKind = KindCase.NUMBER_VALUE,
      actualKind = KindCase.STRING_VALUE,
      struct = encodeToStruct(TestEncodeData(TestEncodeSubData("foo"))),
      actualValue = "foo",
      path = "aaa.someValue"
    )
  }

  @Test
  fun `decodeFromStruct() should throw SerializationException if decoding a Double value found a different type`() {
    @Serializable data class TestEncodeSubData(val someValue: String)
    @Serializable data class TestEncodeData(val aaa: TestEncodeSubData)
    @Serializable data class TestDecodeSubData(val someValue: Double)
    @Serializable data class TestDecodeData(val aaa: TestDecodeSubData)

    assertDecodeFromStructThrowsIncorrectKindCase<TestDecodeData>(
      expectedKind = KindCase.NUMBER_VALUE,
      actualKind = KindCase.STRING_VALUE,
      struct = encodeToStruct(TestEncodeData(TestEncodeSubData("foo"))),
      actualValue = "foo",
      path = "aaa.someValue"
    )
  }

  @Test
  fun `decodeFromStruct() should throw SerializationException if decoding a String value found a different type`() {
    @Serializable data class TestEncodeSubData(val someValue: Int)
    @Serializable data class TestEncodeData(val aaa: TestEncodeSubData)
    @Serializable data class TestDecodeSubData(val someValue: String)
    @Serializable data class TestDecodeData(val aaa: TestDecodeSubData)

    assertDecodeFromStructThrowsIncorrectKindCase<TestDecodeData>(
      expectedKind = KindCase.STRING_VALUE,
      actualKind = KindCase.NUMBER_VALUE,
      struct = encodeToStruct(TestEncodeData(TestEncodeSubData(42))),
      actualValue = 42.0,
      path = "aaa.someValue"
    )
  }

  @Test
  fun `decodeFromStruct() should throw SerializationException if decoding a List value found a different type`() {
    @Serializable data class TestEncodeSubData(val someValue: Boolean)
    @Serializable data class TestEncodeData(val aaa: TestEncodeSubData)
    @Serializable data class TestDecodeSubData(val someValue: List<Int>)
    @Serializable data class TestDecodeData(val aaa: TestDecodeSubData)

    assertDecodeFromStructThrowsIncorrectKindCase<TestDecodeData>(
      expectedKind = KindCase.LIST_VALUE,
      actualKind = KindCase.BOOL_VALUE,
      struct = encodeToStruct(TestEncodeData(TestEncodeSubData(true))),
      actualValue = true,
      path = "aaa.someValue"
    )
  }

  @Test
  fun `decodeFromStruct() should throw SerializationException if decoding a Struct value found a different type`() {
    @Serializable data class TestEncodeSubData(val someValue: Int)
    @Serializable data class TestEncodeData(val aaa: TestEncodeSubData)
    @Serializable data class TestDecodeSubData2(val someValue: Int)
    @Serializable data class TestDecodeSubData(val someValue: TestDecodeSubData2)
    @Serializable data class TestDecodeData(val aaa: TestDecodeSubData)

    assertDecodeFromStructThrowsIncorrectKindCase<TestDecodeData>(
      expectedKind = KindCase.STRUCT_VALUE,
      actualKind = KindCase.NUMBER_VALUE,
      struct = encodeToStruct(TestEncodeData(TestEncodeSubData(42))),
      actualValue = 42.0,
      path = "aaa.someValue"
    )
  }

  @Test
  fun `decodeFromStruct() should throw SerializationException if decoding a Struct value found null`() {
    @Serializable data class TestDecodeSubData2(val someValue: String)
    @Serializable data class TestDecodeSubData(val bbb: TestDecodeSubData2)
    @Serializable data class TestDecodeData(val aaa: TestDecodeSubData)
    val struct = buildStructProto { putStruct("aaa") { putNull("bbb") } }

    assertDecodeFromStructThrowsIncorrectKindCase<TestDecodeData>(
      expectedKind = KindCase.STRUCT_VALUE,
      actualKind = KindCase.NULL_VALUE,
      struct = struct,
      actualValue = null,
      path = "aaa.bbb"
    )
  }

  @Serializable @JvmInline private value class TestStringValueClass(val a: String)

  @Serializable @JvmInline private value class TestIntValueClass(val a: Int)

  private companion object {
    val propTestConfig = PropTestConfig(iterations = 20)

    fun Arb.Companion.testStringValueClass(): Arb<TestStringValueClass> = arbitrary {
      TestStringValueClass(Arb.string().bind())
    }
    fun Arb.Companion.testIntValueClass(): Arb<TestIntValueClass> = arbitrary {
      TestIntValueClass(Arb.int().bind())
    }
  }

  // TODO: Add tests for decoding to objects with unsupported field types (e.g. Byte, Char) and
  // list elements of unsupported field types (e.g. Byte, Char).

}

/**
 * Asserts that `decodeFromStruct<T>` throws [SerializationException], with a message that indicates
 * that the "kind" of the [Value] being decoded differed from what was expected.
 *
 * @param expectedKind The expected "kind" of the [Value] being decoded that should be incorporated
 * into the exception's message.
 * @param actualKind The actual "kind" of the [Value] being decoded that should be incorporated into
 * the exception's message.
 */
private inline fun <reified T> assertDecodeFromStructThrowsIncorrectKindCase(
  expectedKind: KindCase,
  actualKind: KindCase,
  actualValue: Any? = Struct.getDefaultInstance().fieldsMap,
  struct: Struct = Struct.getDefaultInstance(),
  path: String? = null
) {
  val exception = shouldThrow<SerializationException> { decodeFromStruct<T>(struct) }
  // The error message is expected to look something like this:
  // "expected NUMBER_VALUE, but got STRUCT_VALUE"
  assertSoftly {
    exception.message shouldContainWithNonAbuttingTextIgnoringCase "expected $expectedKind"
    exception.message shouldContainWithNonAbuttingTextIgnoringCase "got $actualKind"
    exception.message shouldContainWithNonAbuttingTextIgnoringCase "($actualValue)"
    if (path !== null) {
      exception.message shouldContainWithNonAbuttingTextIgnoringCase "decoding \"$path\""
    }
  }
}
