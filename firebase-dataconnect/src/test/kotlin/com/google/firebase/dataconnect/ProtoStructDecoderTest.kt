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
import com.google.protobuf.Struct
import com.google.protobuf.Value.KindCase
import java.util.regex.Pattern
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
  fun `decodeFromStruct() should throw SerializationException if attempting to decode an Int`() {
    assertThrowsExpectedDifferentKindCase<Int>(
      expectedKind = KindCase.NUMBER_VALUE,
      actualKind = KindCase.STRUCT_VALUE
    )
  }

  @Test
  fun `decodeFromStruct() should throw SerializationException if attempting to decode a Double`() {
    assertThrowsExpectedDifferentKindCase<Double>(
      expectedKind = KindCase.NUMBER_VALUE,
      actualKind = KindCase.STRUCT_VALUE
    )
  }

  @Test
  fun `decodeFromStruct() should throw SerializationException if attempting to decode a Boolean`() {
    assertThrowsExpectedDifferentKindCase<Boolean>(
      expectedKind = KindCase.BOOL_VALUE,
      actualKind = KindCase.STRUCT_VALUE
    )
  }

  @Test
  fun `decodeFromStruct() should throw SerializationException if attempting to decode a String`() {
    assertThrowsExpectedDifferentKindCase<String>(
      expectedKind = KindCase.STRING_VALUE,
      actualKind = KindCase.STRUCT_VALUE
    )
  }

  @Test
  fun `decodeFromStruct() should throw SerializationException if attempting to decode a List`() {
    assertThrowsExpectedDifferentKindCase<List<String>>(
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
  fun `decodeFromStruct() should throw SerializationException if attempting to decode a Inline`() {
    assertThrowsNotSupported<TestValueClass>()
  }

  @Test
  fun `decodeFromStruct() should throw SerializationException if attempting to decode a Long`() {
    assertThrowsNotSupported<Long>()
  }

  @Test
  fun `decodeFromStruct() should throw SerializationException if attempting to decode a Short`() {
    assertThrowsNotSupported<Short>()
  }
}

private inline fun <reified T> assertThrowsExpectedDifferentKindCase(
  expectedKind: KindCase,
  actualKind: KindCase
) {
  val exception =
    assertThrows(SerializationException::class.java) {
      decodeFromStruct<T>(Struct.getDefaultInstance())
    }
  assertThat(exception).hasMessageThat().ignoringCase().contains("expected $expectedKind")
  assertThat(exception).hasMessageThat().ignoringCase().contains("got $actualKind")
}

private inline fun <reified T> assertThrowsNotSupported() {
  val exception =
    assertThrows(SerializationException::class.java) {
      decodeFromStruct<T>(Struct.getDefaultInstance())
    }
  assertThat(exception)
    .hasMessageThat()
    .containsMatch(
      Pattern.compile(
        "decoding.*${Pattern.quote(T::class.qualifiedName!!)}.*not supported",
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

@Serializable @JvmInline value class TestValueClass(val a: Int)
