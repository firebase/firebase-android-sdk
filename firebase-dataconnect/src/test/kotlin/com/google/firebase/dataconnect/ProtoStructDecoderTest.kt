// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

@file:OptIn(ExperimentalSerializationApi::class, ExperimentalSerializationApi::class)

package com.google.firebase.dataconnect

import com.google.common.truth.Truth.assertThat
import com.google.protobuf.NullValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import com.google.protobuf.Value.KindCase
import com.google.protobuf.struct
import com.google.protobuf.value
import java.util.regex.Pattern
import kotlin.reflect.KClass
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertThrows
import org.junit.Test

class ProtoStructDecoderTest {

  @Test
  fun `decodeFromStruct() can decode a Struct to Unit`() {
    val decodedTestData = decodeFromStruct<Unit>(Struct.getDefaultInstance())
    assertThat(decodedTestData).isSameInstanceAs(Unit)
  }

  @Test
  fun `decodeFromStruct() can decode a Struct with String values`() {
    @Serializable data class TestData(val value1: String, val value2: String)
    val struct = encodeToStruct(TestData(value1 = "foo", value2 = "bar"))

    val decodedTestData = decodeFromStruct<TestData>(struct)

    assertThat(decodedTestData).isEqualTo(TestData(value1 = "foo", value2 = "bar"))
  }

  @Test
  fun `decodeFromStruct() can decode a Struct with _nullable_ String values`() {
    @Serializable data class TestData(val isNull: String?, val isNotNull: String?)
    val struct = encodeToStruct(TestData(isNull = null, isNotNull = "NotNull"))

    val decodedTestData = decodeFromStruct<TestData>(struct)

    assertThat(decodedTestData).isEqualTo(TestData(isNull = null, isNotNull = "NotNull"))
  }

  @Test
  fun `decodeFromStruct() can decode a Struct with Boolean values`() {
    @Serializable data class TestData(val value1: Boolean, val value2: Boolean)
    val struct = encodeToStruct(TestData(value1 = true, value2 = false))

    val decodedTestData = decodeFromStruct<TestData>(struct)

    assertThat(decodedTestData).isEqualTo(TestData(value1 = true, value2 = false))
  }

  @Test
  fun `decodeFromStruct() can decode a Struct with _nullable_ Boolean values`() {
    @Serializable data class TestData(val isNull: Boolean?, val isNotNull: Boolean?)
    val struct = encodeToStruct(TestData(isNull = null, isNotNull = true))

    val decodedTestData = decodeFromStruct<TestData>(struct)

    assertThat(decodedTestData).isEqualTo(TestData(isNull = null, isNotNull = true))
  }

  @Test
  fun `decodeFromStruct() can decode a Struct with Int values`() {
    @Serializable data class TestData(val value1: Int, val value2: Int)
    val struct = encodeToStruct(TestData(value1 = 123, value2 = -456))

    val decodedTestData = decodeFromStruct<TestData>(struct)

    assertThat(decodedTestData).isEqualTo(TestData(value1 = 123, value2 = -456))
  }

  @Test
  fun `decodeFromStruct() can decode a Struct with _nullable_ Int values`() {
    @Serializable data class TestData(val isNull: Int?, val isNotNull: Int?)
    val struct = encodeToStruct(TestData(isNull = null, isNotNull = 42))

    val decodedTestData = decodeFromStruct<TestData>(struct)

    assertThat(decodedTestData).isEqualTo(TestData(isNull = null, isNotNull = 42))
  }

  @Test
  fun `decodeFromStruct() can decode a Struct with extreme Int values`() {
    @Serializable data class TestData(val max: Int, val min: Int)
    val struct = encodeToStruct(TestData(max = Int.MAX_VALUE, min = Int.MIN_VALUE))

    val decodedTestData = decodeFromStruct<TestData>(struct)

    assertThat(decodedTestData).isEqualTo(TestData(max = Int.MAX_VALUE, min = Int.MIN_VALUE))
  }

  @Test
  fun `decodeFromStruct() can decode a Struct with Double values`() {
    @Serializable data class TestData(val value1: Double, val value2: Double)
    val struct = encodeToStruct(TestData(value1 = 123.45, value2 = -456.78))

    val decodedTestData = decodeFromStruct<TestData>(struct)

    assertThat(decodedTestData).isEqualTo(TestData(value1 = 123.45, value2 = -456.78))
  }

  @Test
  fun `decodeFromStruct() can decode a Struct with _nullable_ Double values`() {
    @Serializable data class TestData(val isNull: Double?, val isNotNull: Double?)
    val struct = encodeToStruct(TestData(isNull = null, isNotNull = 987.654))

    val decodedTestData = decodeFromStruct<TestData>(struct)

    assertThat(decodedTestData).isEqualTo(TestData(isNull = null, isNotNull = 987.654))
  }

  @Test
  fun `decodeFromStruct() can decode a Struct with extreme Double values`() {
    @Serializable
    data class TestData(
      val min: Double,
      val max: Double,
      val positiveInfinity: Double,
      val negativeInfinity: Double,
      val nan: Double
    )
    val struct =
      encodeToStruct(
        TestData(
          min = Double.MIN_VALUE,
          max = Double.MAX_VALUE,
          positiveInfinity = Double.POSITIVE_INFINITY,
          negativeInfinity = Double.NEGATIVE_INFINITY,
          nan = Double.NaN
        )
      )

    val decodedTestData = decodeFromStruct<TestData>(struct)

    assertThat(decodedTestData)
      .isEqualTo(
        TestData(
          min = Double.MIN_VALUE,
          max = Double.MAX_VALUE,
          positiveInfinity = Double.POSITIVE_INFINITY,
          negativeInfinity = Double.NEGATIVE_INFINITY,
          nan = Double.NaN
        )
      )
  }

  @Test
  fun `decodeFromStruct() can decode a Struct with nested Struct values`() {
    @Serializable data class TestDataA(val base: String)
    @Serializable data class TestDataB(val dataA: TestDataA)
    @Serializable data class TestDataC(val dataB: TestDataB)
    @Serializable data class TestDataD(val dataC: TestDataC)

    val struct = encodeToStruct(TestDataD(TestDataC(TestDataB(TestDataA("hello")))))

    val decodedTestData = decodeFromStruct<TestDataD>(struct)

    assertThat(decodedTestData).isEqualTo(TestDataD(TestDataC(TestDataB(TestDataA("hello")))))
  }

  @Test
  fun `decodeFromStruct() can decode a Struct with nested _nullable_ Struct values`() {
    @Serializable data class TestDataA(val base: String)
    @Serializable data class TestDataB(val dataANull: TestDataA?, val dataANotNull: TestDataA?)
    @Serializable data class TestDataC(val dataBNull: TestDataB?, val dataBNotNull: TestDataB?)
    @Serializable data class TestDataD(val dataCNull: TestDataC?, val dataCNotNull: TestDataC?)

    val struct =
      encodeToStruct(TestDataD(null, TestDataC(null, TestDataB(null, TestDataA("hello")))))

    val decodedTestData = decodeFromStruct<TestDataD>(struct)

    assertThat(decodedTestData)
      .isEqualTo(TestDataD(null, TestDataC(null, TestDataB(null, TestDataA("hello")))))
  }

  @Test
  fun `decodeFromStruct() can decode a Struct with nullable ListValue values`() {
    @Serializable data class TestData(val nullList: List<String>?, val nonNullList: List<String>?)
    val struct = encodeToStruct(TestData(nullList = null, nonNullList = listOf("a", "b")))

    val decodedTestData = decodeFromStruct<TestData>(struct)

    assertThat(decodedTestData).isEqualTo(TestData(nullList = null, nonNullList = listOf("a", "b")))
  }

  @Test
  fun `decodeFromStruct() can decode a ListValue of String`() {
    @Serializable data class TestData(val list: List<String>)
    val struct = encodeToStruct(TestData(listOf("elem1", "elem2")))

    val decodedTestData = decodeFromStruct<TestData>(struct)

    assertThat(decodedTestData).isEqualTo(TestData(listOf("elem1", "elem2")))
  }

  @Test
  fun `decodeFromStruct() can decode a ListValue of _nullable_ String`() {
    @Serializable data class TestData(val list: List<String?>)
    val struct = encodeToStruct(TestData(listOf(null, "aaa", null, "bbb")))

    val decodedTestData = decodeFromStruct<TestData>(struct)

    assertThat(decodedTestData).isEqualTo(TestData(listOf(null, "aaa", null, "bbb")))
  }

  @Test
  fun `decodeFromStruct() can decode a ListValue of Boolean`() {
    @Serializable data class TestData(val list: List<Boolean>)
    val struct = encodeToStruct(TestData(listOf(true, false, true, false)))

    val decodedTestData = decodeFromStruct<TestData>(struct)

    assertThat(decodedTestData).isEqualTo(TestData(listOf(true, false, true, false)))
  }

  @Test
  fun `decodeFromStruct() can decode a ListValue of _nullable_ Boolean`() {
    @Serializable data class TestData(val list: List<Boolean?>)
    val struct = encodeToStruct(TestData(listOf(null, true, false, null, true, false)))

    val decodedTestData = decodeFromStruct<TestData>(struct)

    assertThat(decodedTestData).isEqualTo(TestData(listOf(null, true, false, null, true, false)))
  }

  @Test
  fun `decodeFromStruct() can decode a ListValue of Int`() {
    @Serializable data class TestData(val list: List<Int>)
    val struct = encodeToStruct(TestData(listOf(1, 0, -1, Int.MAX_VALUE, Int.MIN_VALUE)))

    val decodedTestData = decodeFromStruct<TestData>(struct)

    assertThat(decodedTestData).isEqualTo(TestData(listOf(1, 0, -1, Int.MAX_VALUE, Int.MIN_VALUE)))
  }

  @Test
  fun `decodeFromStruct() can decode a ListValue of _nullable_ Int`() {
    @Serializable data class TestData(val list: List<Int?>)
    val struct = encodeToStruct(TestData(listOf(1, 0, -1, Int.MAX_VALUE, Int.MIN_VALUE, null)))

    val decodedTestData = decodeFromStruct<TestData>(struct)

    assertThat(decodedTestData)
      .isEqualTo(TestData(listOf(1, 0, -1, Int.MAX_VALUE, Int.MIN_VALUE, null)))
  }

  @Test
  fun `decodeFromStruct() can decode a ListValue of Double`() {
    @Serializable data class TestData(val list: List<Double>)
    val struct =
      encodeToStruct(
        TestData(
          listOf(
            1.0,
            0.0,
            -0.0,
            -1.0,
            Double.MAX_VALUE,
            Double.MIN_VALUE,
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY
          )
        )
      )

    val decodedTestData = decodeFromStruct<TestData>(struct)

    assertThat(decodedTestData)
      .isEqualTo(
        TestData(
          listOf(
            1.0,
            0.0,
            -0.0,
            -1.0,
            Double.MAX_VALUE,
            Double.MIN_VALUE,
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY
          )
        )
      )
  }

  @Test
  fun `decodeFromStruct() can decode a ListValue of _nullable_ Double`() {
    @Serializable data class TestData(val list: List<Double?>)
    val struct =
      encodeToStruct(
        TestData(
          listOf(
            1.0,
            0.0,
            -0.0,
            -1.0,
            Double.MAX_VALUE,
            Double.MIN_VALUE,
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
            null
          )
        )
      )

    val decodedTestData = decodeFromStruct<TestData>(struct)

    assertThat(decodedTestData)
      .isEqualTo(
        TestData(
          listOf(
            1.0,
            0.0,
            -0.0,
            -1.0,
            Double.MAX_VALUE,
            Double.MIN_VALUE,
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
            null
          )
        )
      )
  }

  @Test
  fun `decodeFromStruct() can decode a ListValue of Struct`() {
    @Serializable data class TestDataA(val s1: String, val s2: String?)
    @Serializable data class TestData(val list: List<TestDataA>)
    val struct = encodeToStruct(TestData(listOf(TestDataA("aa", null), TestDataA("bb", null))))

    val decodedTestData = decodeFromStruct<TestData>(struct)

    assertThat(decodedTestData)
      .isEqualTo(TestData(listOf(TestDataA("aa", null), TestDataA("bb", null))))
  }

  @Test
  fun `decodeFromStruct() can decode a ListValue of _nullable_ Struct`() {
    @Serializable data class TestDataA(val s1: String, val s2: String?)
    @Serializable data class TestData(val list: List<TestDataA?>)
    val struct =
      encodeToStruct(TestData(listOf(null, TestDataA("aa", null), TestDataA("bb", null), null)))

    val decodedTestData = decodeFromStruct<TestData>(struct)

    assertThat(decodedTestData)
      .isEqualTo(TestData(listOf(null, TestDataA("aa", null), TestDataA("bb", null), null)))
  }

  @Test
  fun `decodeFromStruct() can decode a ListValue of ListValue`() {
    @Serializable data class TestData(val list: List<List<Int>>)
    val struct = encodeToStruct(TestData(listOf(listOf(1, 2, 3), listOf(4, 5, 6))))

    val decodedTestData = decodeFromStruct<TestData>(struct)

    assertThat(decodedTestData).isEqualTo(TestData(listOf(listOf(1, 2, 3), listOf(4, 5, 6))))
  }

  @Test
  fun `decodeFromStruct() can decode a ListValue of _nullable_ ListValue`() {
    @Serializable data class TestData(val list: List<List<Int>?>)
    val struct = encodeToStruct(TestData(listOf(listOf(1, 2, 3), listOf(4, 5, 6), null)))

    val decodedTestData = decodeFromStruct<TestData>(struct)

    assertThat(decodedTestData).isEqualTo(TestData(listOf(listOf(1, 2, 3), listOf(4, 5, 6), null)))
  }

  @Test
  fun `decodeFromStruct() can decode a Struct with Inline values`() {
    @Serializable data class TestData(val s: TestStringValueClass, val i: TestIntValueClass)
    val struct = encodeToStruct(TestData(TestStringValueClass("TestString"), TestIntValueClass(42)))

    val decodedTestData = decodeFromStruct<TestData>(struct)

    assertThat(decodedTestData)
      .isEqualTo(TestData(TestStringValueClass("TestString"), TestIntValueClass(42)))
  }

  @Test
  fun `decodeFromStruct() can decode a Struct with _nullable_ Inline values`() {
    @Serializable
    data class TestData(
      val s: TestStringValueClass?,
      val snull: TestStringValueClass?,
      val i: TestIntValueClass?,
      val inull: TestIntValueClass?
    )
    val struct =
      encodeToStruct(
        TestData(TestStringValueClass("TestString"), null, TestIntValueClass(42), null)
      )

    val decodedTestData = decodeFromStruct<TestData>(struct)

    assertThat(decodedTestData)
      .isEqualTo(TestData(TestStringValueClass("TestString"), null, TestIntValueClass(42), null))
  }

  @Test
  fun `decodeFromStruct() can decode a ListValue with Inline values`() {
    @Serializable
    data class TestData(val s: List<TestStringValueClass>, val i: List<TestIntValueClass>)
    val struct =
      encodeToStruct(
        TestData(
          listOf(TestStringValueClass("TestString1"), TestStringValueClass("TestString2")),
          listOf(TestIntValueClass(42), TestIntValueClass(43))
        )
      )

    val decodedTestData = decodeFromStruct<TestData>(struct)

    assertThat(decodedTestData)
      .isEqualTo(
        TestData(
          listOf(TestStringValueClass("TestString1"), TestStringValueClass("TestString2")),
          listOf(TestIntValueClass(42), TestIntValueClass(43))
        )
      )
  }

  @Test
  fun `decodeFromStruct() can decode a ListValue with _nullable_ Inline values`() {
    @Serializable
    data class TestData(val s: List<TestStringValueClass?>, val i: List<TestIntValueClass?>)
    val struct =
      encodeToStruct(
        TestData(
          listOf(TestStringValueClass("TestString1"), null, TestStringValueClass("TestString2")),
          listOf(TestIntValueClass(42), null, TestIntValueClass(43))
        )
      )

    val decodedTestData = decodeFromStruct<TestData>(struct)

    assertThat(decodedTestData)
      .isEqualTo(
        TestData(
          listOf(TestStringValueClass("TestString1"), null, TestStringValueClass("TestString2")),
          listOf(TestIntValueClass(42), null, TestIntValueClass(43))
        )
      )
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
  fun `decodeFromStruct() should throw SerializationException if attempting to decode a Byte`() {
    assertThrowsNotSupported<Byte>()
  }

  @Test
  fun `decodeFromStruct() should throw SerializationException if attempting to decode a Char`() {
    assertThrowsNotSupported<Char>()
  }

  @Test
  fun `decodeFromStruct() should throw SerializationException if attempting to decode a Enum`() {
    assertThrowsNotSupported<TestEnum>()
  }

  @Test
  fun `decodeFromStruct() should throw SerializationException if attempting to decode a Float`() {
    assertThrowsNotSupported<Float>()
  }

  @Test
  fun `decodeFromStruct() should throw SerializationException if attempting to decode an Inline of supported type`() {
    assertDecodeFromStructThrowsIncorrectKindCase<TestStringValueClass>(
      expectedKind = KindCase.STRING_VALUE,
      actualKind = KindCase.STRUCT_VALUE
    )
    assertDecodeFromStructThrowsIncorrectKindCase<TestIntValueClass>(
      expectedKind = KindCase.NUMBER_VALUE,
      actualKind = KindCase.STRUCT_VALUE
    )
  }

  @Test
  fun `decodeFromStruct() should throw SerializationException if attempting to decode an Inline of _unsupported_ type`() {
    assertThrowsNotSupported<TestByteValueClass>(expectedTypeInMessage = Byte::class)
  }

  @Test
  fun `decodeFromStruct() should throw SerializationException if attempting to decode a Long`() {
    assertThrowsNotSupported<Long>()
  }

  @Test
  fun `decodeFromStruct() should throw SerializationException if attempting to decode a Short`() {
    assertThrowsNotSupported<Short>()
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
    val struct = struct {
      fields["aaa"] = value {
        structValue = struct { fields["bbb"] = value { nullValue = NullValue.NULL_VALUE } }
      }
    }

    assertDecodeFromStructThrowsIncorrectKindCase<TestDecodeData>(
      expectedKind = KindCase.STRUCT_VALUE,
      actualKind = KindCase.NULL_VALUE,
      struct = struct,
      actualValue = null,
      path = "aaa.bbb"
    )
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
  val exception = assertThrows(SerializationException::class.java) { decodeFromStruct<T>(struct) }
  // The error message is expected to look something like this:
  // "expected NUMBER_VALUE, but got STRUCT_VALUE"
  assertThat(exception).hasMessageThat().ignoringCase().contains("expected $expectedKind")
  assertThat(exception).hasMessageThat().ignoringCase().contains("got $actualKind")
  assertThat(exception).hasMessageThat().ignoringCase().contains("($actualValue)")
  if (path !== null) {
    assertThat(exception).hasMessageThat().ignoringCase().contains("decoding \"$path\"")
  }
}

/**
 * Asserts that `decodeFromStruct<T>` throws [SerializationException], with a message that indicates
 * that the type `T` being decoded is not supported.
 *
 * @param expectedTypeInMessage The type that the exception's message should indicate is not
 * supported; if not specified, use `T`. Note that the only case where this argument's value should
 * be anything _other_ than `T` is for _value classes_ that are mapped to a primitive type.
 */
private inline fun <reified T : Any> assertThrowsNotSupported(
  expectedTypeInMessage: KClass<*> = T::class
) {
  val exception =
    assertThrows(SerializationException::class.java) {
      decodeFromStruct<T>(Struct.getDefaultInstance())
    }
  assertThat(exception)
    .hasMessageThat()
    .containsMatch(
      Pattern.compile(
        "decoding.*${Pattern.quote(expectedTypeInMessage.qualifiedName!!)}.*not supported",
        Pattern.CASE_INSENSITIVE
      )
    )
}

private enum class TestEnum {
  A,
  B,
  C,
  D
}

@Serializable @JvmInline value class TestStringValueClass(val a: String)

@Serializable @JvmInline value class TestIntValueClass(val a: Int)

@Serializable @JvmInline value class TestByteValueClass(val a: Byte)
