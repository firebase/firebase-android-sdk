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

import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.firebase.dataconnect.testutil.shouldBeDefaultInstance
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.util.ProtoUtil.buildStructProto
import com.google.firebase.dataconnect.util.ProtoUtil.encodeToStruct
import com.google.firebase.dataconnect.util.ProtoUtil.toListValueProto
import com.google.protobuf.Struct
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.Test

class ProtoStructEncoderUnitTest {

  @Test
  fun `encodeToStruct() should throw if a NUMBER_VALUE is produced`() {
    val intValue: Int = Arb.int().next()
    val exception = shouldThrow<IllegalArgumentException> { encodeToStruct(intValue) }
    exception.message shouldContainWithNonAbuttingText "NUMBER_VALUE"
  }

  @Test
  fun `encodeToStruct() should throw if a BOOL_VALUE is produced`() {
    val booleanValue: Boolean = Arb.boolean().next()
    val exception = shouldThrow<IllegalArgumentException> { encodeToStruct(booleanValue) }
    exception.message shouldContainWithNonAbuttingText "BOOL_VALUE"
  }

  @Test
  fun `encodeToStruct() should throw if a STRING_VALUE is produced`() {
    val stringValue: String = Arb.string().next()
    val exception = shouldThrow<IllegalArgumentException> { encodeToStruct(stringValue) }
    exception.message shouldContainWithNonAbuttingText "STRING_VALUE"
  }

  @Test
  fun `encodeToStruct() should throw if a LIST_VALUE is produced`() {
    val listValue: List<String> = Arb.list(Arb.string(5..10), 1..10).next()
    val exception = shouldThrow<IllegalArgumentException> { encodeToStruct(listValue) }
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
  fun `encodeToStruct() should encode an class with all primitive types`() = runTest {
    @Serializable
    data class TestData(
      val iv: Int,
      val dv: Double,
      val bv: Boolean,
      val sv: String,
      val svn: String?,
    )
    val arb: Arb<Pair<TestData, Struct>> = arbitrary {
      val iv = Arb.int().bind()
      val dv = Arb.double().bind()
      val bv = Arb.boolean().bind()
      val sv = Arb.string().bind()
      val svn = Arb.string().orNull(0.33).bind()

      val testData = TestData(iv = iv, dv = dv, bv = bv, sv = sv, svn = svn)

      val struct = buildStructProto {
        put("iv", iv)
        put("dv", dv)
        put("bv", bv)
        put("sv", sv)
        if (svn === null) {
          putNull("svn")
        } else {
          put("svn", svn)
        }
      }

      Pair(testData, struct)
    }

    checkAll(propTestConfig, arb) { (testData, expectedStruct) ->
      val encodedStruct = encodeToStruct(testData)
      encodedStruct shouldBe expectedStruct
    }
  }

  @Test
  fun `encodeToStruct() should encode lists with all primitive types`() = runTest {
    @Serializable
    data class TestData(
      val iv: List<Int>,
      val dv: List<Double>,
      val bv: List<Boolean>,
      val sv: List<String>,
      val svn: List<String?>
    )
    val arb: Arb<Pair<TestData, Struct>> = arbitrary {
      val iv = Arb.list(Arb.int()).bind()
      val dv = Arb.list(Arb.double()).bind()
      val bv = Arb.list(Arb.boolean()).bind()
      val sv = Arb.list(Arb.string()).bind()
      val svn = Arb.list(Arb.string().orNull(0.33)).bind()

      val testData = TestData(iv = iv, dv = dv, bv = bv, sv = sv, svn = svn)

      val struct = buildStructProto {
        put("iv", iv.map { it.toDouble() }.toListValueProto())
        put("dv", dv.toListValueProto())
        put("bv", bv.toListValueProto())
        put("sv", sv.toListValueProto())
        put("svn", svn.toListValueProto())
      }

      Pair(testData, struct)
    }

    checkAll(propTestConfig, arb) { (testData, expectedStruct) ->
      val encodedStruct = encodeToStruct(testData)
      encodedStruct shouldBe expectedStruct
    }
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
  fun `encodeToStruct() should support OptionalVariable Value when T is not nullable`() = runTest {
    @Serializable
    data class TestData(val s: OptionalVariable<String>, val i: OptionalVariable<Int>)
    val arb = arbitrary {
      val s = OptionalVariable.Value(Arb.string().bind())
      val i = OptionalVariable.Value(Arb.int().bind())
      TestData(s, i)
    }

    checkAll(propTestConfig, arb) { testData ->
      val encodedStruct = encodeToStruct(testData)
      val expected = buildStructProto {
        put("s", testData.s.valueOrThrow())
        put("i", testData.i.valueOrThrow())
      }
      encodedStruct shouldBe expected
    }
  }

  @Test
  fun `encodeToStruct() should support OptionalVariable Value when T is nullable`() = runTest {
    @Serializable
    data class TestData(
      val s: OptionalVariable<String?>,
      val i: OptionalVariable<Int?>,
    )
    val arb = arbitrary {
      val s = OptionalVariable.Value(Arb.string().orNull(0.33).bind())
      val i = OptionalVariable.Value(Arb.int().orNull(0.33).bind())
      TestData(s, i)
    }

    checkAll(propTestConfig, arb) { testData ->
      val encodedStruct = encodeToStruct(testData)
      val expected = buildStructProto {
        put("s", testData.s.valueOrThrow())
        put("i", testData.i.valueOrThrow())
      }
      encodedStruct shouldBe expected
    }
  }

  private companion object {
    val propTestConfig = PropTestConfig(iterations = 20)
  }
}
