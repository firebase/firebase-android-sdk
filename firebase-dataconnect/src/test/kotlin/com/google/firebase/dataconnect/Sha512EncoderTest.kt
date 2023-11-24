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
import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.Test

class Sha512EncoderTest {

  @Test
  fun `calculateSha512() should return the same value for the same object`() {
    val obj = SerializationTestData.AllTheTypes.newInstance()
    assertThat(calculateSha512(obj)).isEqualTo(calculateSha512(obj))
  }

  @Test
  fun `calculateSha512() should return the same value for distinct, but equal, objects`() {
    val obj1 = SerializationTestData.AllTheTypes.newInstance(seed = "foo")
    val obj2 = SerializationTestData.AllTheTypes.newInstance(seed = "foo")
    assertThat(calculateSha512(obj1)).isEqualTo(calculateSha512(obj2))
  }

  @Test
  fun `calculateSha512() should return different values for lists with same elements in different order`() {
    val obj1 = listOf(1, 2, 3)
    val obj2 = listOf(1, 3, 2)

    val result1 = calculateSha512(obj1)
    val result2 = calculateSha512(obj2)

    assertThat(result1).isNotEqualTo(result2)
  }

  @Test
  fun `calculateSha512() should return different values for different objects`() {
    val obj1 = SerializationTestData.AllTheTypes.newInstance(seed = "foo")
    val obj2 = SerializationTestData.AllTheTypes.newInstance(seed = "bar")
    assertThat(calculateSha512(obj1)).isNotEqualTo(calculateSha512(obj2))
  }

  @Test
  fun `calculateSha512() should return different values for different Byte values`() {
    assertThat(calculateSha512(42.toByte())).isNotEqualTo(calculateSha512(43.toByte()))
  }

  @Test
  fun `calculateSha512() should return different values for different Char values`() {
    assertThat(calculateSha512('a')).isNotEqualTo(calculateSha512('b'))
  }

  @Test
  fun `calculateSha512() should return different values for different Double values`() {
    assertThat(calculateSha512(42.0)).isNotEqualTo(calculateSha512(43.0))
  }

  @Test
  fun `calculateSha512() should return different values for different Enum values`() {
    assertThat(calculateSha512(SerializationTestData.TestEnum.A))
      .isNotEqualTo(calculateSha512(SerializationTestData.TestEnum.B))
  }

  @Test
  fun `calculateSha512() should return different values for different Float values`() {
    assertThat(calculateSha512(42.0.toFloat())).isNotEqualTo(calculateSha512(43.0.toFloat()))
  }

  @Test
  fun `calculateSha512() should return different values for different Inline String values`() {
    assertThat(SerializationTestData.TestStringValueClass("foo"))
      .isNotEqualTo(calculateSha512(SerializationTestData.TestStringValueClass("bar")))
  }

  @Test
  fun `calculateSha512() should return different values for different Inline Int values`() {
    assertThat(SerializationTestData.TestIntValueClass(42))
      .isNotEqualTo(calculateSha512(SerializationTestData.TestIntValueClass(43)))
  }

  @Test
  fun `calculateSha512() should return different values for different Int values`() {
    assertThat(calculateSha512(42)).isNotEqualTo(calculateSha512(43))
  }

  @Test
  fun `calculateSha512() should return different values for different Long values`() {
    assertThat(calculateSha512(42.toLong())).isNotEqualTo(calculateSha512(43.toLong()))
  }

  @Test
  fun `calculateSha512() should return different values for different null values`() {
    assertThat(calculateSha512<String?>(null)).isNotEqualTo(calculateSha512<String?>("foo"))
  }

  @Test
  fun `calculateSha512() should return the same value for null`() {
    assertThat(calculateSha512<String?>(null)).isEqualTo(calculateSha512<String?>(null))
  }

  @Test
  fun `calculateSha512() should return different values for different Short values`() {
    assertThat(calculateSha512(42.toShort())).isNotEqualTo(calculateSha512(43.toShort()))
  }

  @Test
  fun `calculateSha512() should return different values for different String values`() {
    assertThat(calculateSha512("foo")).isNotEqualTo(calculateSha512("bar"))
  }

  @Test
  fun `calculateSha512() should return same value for list of Unit of same length`() {
    val list = listOf(Unit, Unit, Unit)
    assertThat(calculateSha512(list)).isEqualTo(calculateSha512(list))
  }

  @Test
  fun `calculateSha512() should return same value for list of nullable Unit of same length, ending with null`() {
    val list = listOf(Unit, Unit, null)
    assertThat(calculateSha512(list)).isEqualTo(calculateSha512(list))
  }

  @Test
  fun `calculateSha512() should return same value for list of nullable Unit of same length, ending with Unit`() {
    val list = listOf(Unit, null, null)
    assertThat(calculateSha512(list)).isEqualTo(calculateSha512(list))
  }

  @Test
  fun `calculateSha512() should return different value for list of Unit of different length`() {
    val list1 = listOf(Unit, Unit, Unit)
    val list2 = listOf(Unit, Unit)
    assertThat(calculateSha512(list1)).isNotEqualTo(calculateSha512(list2))
  }

  @Test
  fun `calculateSha512() should return different for list of nullable Unit of different length`() {
    val list1 = listOf(Unit, Unit)
    val list2 = listOf(Unit, null, Unit)
    assertThat(calculateSha512(list1)).isNotEqualTo(calculateSha512(list2))
  }

  @Test
  fun `calculateSha512() should return different for list of nullable Unit of same length`() {
    val list1 = listOf(Unit, Unit, null)
    val list2 = listOf(Unit, null, Unit)
    assertThat(calculateSha512(list1)).isNotEqualTo(calculateSha512(list2))
  }

  // TODO: add tests for nullables, lists, maps, classes, and objects
}
