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

@file:OptIn(ExperimentalSerializationApi::class)

package com.google.firebase.dataconnect

import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.firebase.dataconnect.testutil.shouldBeDefaultInstance
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.util.ProtoUtil.buildStructProto
import com.google.firebase.dataconnect.util.ProtoUtil.encodeToStruct
import com.google.protobuf.Struct
import io.kotest.assertions.throwables.shouldThrow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.junit.Test

class ProtoStructEncoderUnitTest {

  @Test
  fun `encodeToStruct() should throw if a NUMBER_VALUE is produced`() {
    val exception = shouldThrow<IllegalArgumentException> { encodeToStruct(42) }
    exception.message shouldContainWithNonAbuttingText "NUMBER_VALUE"
  }

  @Test
  fun `encodeToStruct() should throw if a BOOL_VALUE is produced`() {
    val exception = shouldThrow<IllegalArgumentException> { encodeToStruct(true) }
    exception.message shouldContainWithNonAbuttingText "BOOL_VALUE"
  }

  @Test
  fun `encodeToStruct() should throw if a STRING_VALUE is produced`() {
    val exception =
      shouldThrow<IllegalArgumentException> { encodeToStruct("arbitrary string value") }
    exception.message shouldContainWithNonAbuttingText "STRING_VALUE"
  }

  @Test
  fun `encodeToStruct() should throw if a LIST_VALUE is produced`() {
    val exception =
      shouldThrow<IllegalArgumentException> { encodeToStruct(listOf("element1", "element2")) }
    exception.message shouldContainWithNonAbuttingText "LIST_VALUE"
  }

  @Test
  fun `encodeToStruct() should return an empty struct if an empty map is given`() {
    val encodedStruct = encodeToStruct(emptyMap<Unit, Unit>())
    encodedStruct.shouldBeDefaultInstance()
  }

  @Test
  fun `encodeToStruct() should encode Unit as an empty struct`() {
    val encodedStruct = encodeToStruct(Unit)
    encodedStruct.shouldBeDefaultInstance()
  }

  @Test
  fun `encodeToStruct() should encode an class with all primitive types`() {
    @Serializable
    data class TestData(
      val iv: Int,
      val dv: Double,
      val bvt: Boolean,
      val bvf: Boolean,
      val sv: String,
      val nsvn: String?,
      val nsvnn: String?
    )
    val encodedStruct =
      encodeToStruct(
        TestData(
          iv = 42,
          dv = 1234.5,
          bvt = true,
          bvf = false,
          sv = "blah blah",
          nsvn = null,
          nsvnn = "I'm not null"
        )
      )

    encodedStruct.shouldBe(
      buildStructProto {
        put("iv", 42.0)
        put("dv", 1234.5)
        put("bvt", true)
        put("bvf", false)
        put("sv", "blah blah")
        putNull("nsvn")
        put("nsvnn", "I'm not null")
      }
    )
  }

  @Test
  fun `encodeToStruct() should encode lists with all primitive types`() {
    @Serializable
    data class TestData(
      val iv: List<Int>,
      val dv: List<Double>,
      val bv: List<Boolean>,
      val sv: List<String>,
      val nsv: List<String?>
    )
    val encodedStruct =
      encodeToStruct(
        TestData(
          iv = listOf(42, 43),
          dv = listOf(1234.5, 5678.9),
          bv = listOf(true, false, false, true),
          sv = listOf("abcde", "fghij"),
          nsv = listOf("klmno", null, "pqrst", null)
        )
      )

    encodedStruct.shouldBe(
      buildStructProto {
        putList("iv") {
          add(42.0)
          add(43.0)
        }
        putList("dv") {
          add(1234.5)
          add(5678.9)
        }
        putList("bv") {
          add(true)
          add(false)
          add(false)
          add(true)
        }
        putList("sv") {
          add("abcde")
          add("fghij")
        }
        putList("nsv") {
          add("klmno")
          addNull()
          add("pqrst")
          addNull()
        }
      }
    )
  }

  @Test
  fun `encodeToStruct() should support nested composite types`() {
    @Serializable data class TestData3(val s: String)
    @Serializable data class TestData2(val data3: TestData3, val data3N: TestData3?)
    @Serializable data class TestData1(val data2: TestData2)
    val encodedStruct = encodeToStruct(TestData1(TestData2(TestData3("zzzz"), null)))

    encodedStruct.shouldBe(
      buildStructProto {
        putStruct("data2") {
          putNull("data3N")
          putStruct("data3") { put("s", "zzzz") }
        }
      }
    )
  }

  @Test
  fun `encodeToStruct() should support OptionalVariable Undefined when T is not nullable`() {
    @Serializable data class TestData(val s: OptionalVariable<String>)

    val encodedStruct = encodeToStruct(TestData(OptionalVariable.Undefined))

    encodedStruct shouldBe Struct.getDefaultInstance()
  }

  @Test
  fun `encodeToStruct() should support OptionalVariable Undefined when T is nullable`() {
    @Serializable data class TestData(val s: OptionalVariable<String?>)

    val encodedStruct = encodeToStruct(TestData(OptionalVariable.Undefined))

    encodedStruct shouldBe Struct.getDefaultInstance()
  }

  @Test
  fun `encodeToStruct() should support OptionalVariable Value when T is not nullable`() {
    @Serializable data class TestData(val s: OptionalVariable<String>)

    val encodedStruct = encodeToStruct(TestData(OptionalVariable.Value("Hello")))

    encodedStruct shouldBe buildStructProto { put("s", "Hello") }
  }

  @Test
  fun `encodeToStruct() should support OptionalVariable Value when T is nullable but not null`() {
    @Serializable data class TestData(val s: OptionalVariable<String?>)

    val encodedStruct = encodeToStruct(TestData(OptionalVariable.Value("World")))

    encodedStruct shouldBe buildStructProto { put("s", "World") }
  }

  @Test
  fun `encodeToStruct() should support OptionalVariable Value when T is nullable and null`() {
    @Serializable data class TestData(val s: OptionalVariable<String?>)

    val encodedStruct = encodeToStruct(TestData(OptionalVariable.Value(null)))

    encodedStruct shouldBe buildStructProto { putNull("s") }
  }
}
