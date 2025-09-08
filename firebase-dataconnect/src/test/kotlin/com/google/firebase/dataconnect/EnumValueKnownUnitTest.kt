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

package com.google.firebase.dataconnect

import com.google.firebase.dataconnect.testutil.property.arbitrary.distinctPair
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.of
import io.kotest.property.assume
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

@Suppress("ReplaceCallWithBinaryOperator")
class EnumValueKnownUnitTest {

  @Test
  fun `constructor() should set properties to corresponding arguments`() = runTest {
    checkAll(propTestConfig, Arb.enum<Food>()) { enum ->
      val enumValue = EnumValue.Known(enum)
      enumValue.value shouldBeSameInstanceAs enum
    }
  }

  @Test
  fun `stringValue property should be the name of the enum`() = runTest {
    checkAll(propTestConfig, Arb.enum<Food>()) { enum ->
      val enumValue = EnumValue.Known(enum)
      enumValue.stringValue shouldBe enum.name
    }
  }

  @Test
  fun `equals() should return true when invoked with itself`() = runTest {
    checkAll(propTestConfig, Arb.enum<Food>()) { enum ->
      val enumValue = EnumValue.Known(enum)
      enumValue.equals(enumValue) shouldBe true
    }
  }

  @Test
  fun `equals() should return true when invoked with a distinct, but equal, instance`() = runTest {
    checkAll(propTestConfig, Arb.enum<Food>()) { enum ->
      val enumValue1 = EnumValue.Known(enum)
      val enumValue2 = EnumValue.Known(enum)
      enumValue1.equals(enumValue2) shouldBe true
    }
  }

  @Test
  fun `equals() should return false when invoked with null`() = runTest {
    checkAll(propTestConfig, Arb.enum<Food>()) { enum ->
      val enumValue = EnumValue.Known(enum)
      enumValue.equals(null) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when invoked with a different type`() = runTest {
    val others = Arb.of("foo", 42, java.time.LocalDate.now())
    checkAll(propTestConfig, Arb.enum<Food>(), others) { enum, other ->
      val enumValue = EnumValue.Known(enum)
      enumValue.equals(other) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when the enum differs`() = runTest {
    checkAll(propTestConfig, Arb.enum<Food>().distinctPair()) { (enum1, enum2) ->
      val enumValue1 = EnumValue.Known(enum1)
      val enumValue2 = EnumValue.Known(enum2)
      enumValue1.equals(enumValue2) shouldBe false
    }
  }

  @Test
  fun `hashCode() should return the same value when invoked repeatedly`() = runTest {
    checkAll(propTestConfig, Arb.enum<Food>()) { enum ->
      val enumValue = EnumValue.Known(enum)
      val hashCode = enumValue.hashCode()
      repeat(5) { withClue("iteration=$it") { enumValue.hashCode() shouldBe hashCode } }
    }
  }

  @Test
  fun `hashCode() should return the same value when invoked on equal, but distinct, objects`() =
    runTest {
      checkAll(propTestConfig, Arb.enum<Food>()) { enum ->
        val enumValue1 = EnumValue.Known(enum)
        val enumValue2 = EnumValue.Known(enum)
        enumValue1.hashCode() shouldBe enumValue2.hashCode()
      }
    }

  @Test
  fun `hashCode() should return different values for different enum values`() = runTest {
    checkAll(propTestConfig, Arb.enum<Food>().distinctPair()) { (enum1, enum2) ->
      assume(enum1.hashCode() != enum2.hashCode())
      val enumValue1 = EnumValue.Known(enum1)
      val enumValue2 = EnumValue.Known(enum2)
      enumValue1.hashCode() shouldNotBe enumValue2.hashCode()
    }
  }

  @Test
  fun `toString() should return a string conforming to what is expected`() = runTest {
    checkAll(propTestConfig, Arb.enum<Food>()) { enum ->
      val enumValue = EnumValue.Known(enum)
      enumValue.toString() shouldBe "Known(${enum.name})"
    }
  }

  @Test
  fun `copy() with no arguments should return an equal, but distinct, instance`() = runTest {
    checkAll(propTestConfig, Arb.enum<Food>()) { enum ->
      val enumValue = EnumValue.Known(enum)
      val enumValueCopy = enumValue.copy()
      enumValue shouldBe enumValueCopy
      enumValue shouldNotBeSameInstanceAs enumValueCopy
    }
  }

  @Test
  fun `copy() with all arguments should return a new instance with the given arguments`() =
    runTest {
      checkAll(propTestConfig, Arb.enum<Food>().distinctPair()) { (enum1, enum2) ->
        val enumValue1 = EnumValue.Known(enum1)
        val enumValue2 = enumValue1.copy(enum2)
        enumValue2 shouldBe EnumValue.Known(enum2)
      }
    }

  @Suppress("unused")
  private enum class Food {
    Burrito,
    Cake,
    Pizza,
    Shawarma,
    Sushi,
  }

  private companion object {
    val propTestConfig = PropTestConfig(iterations = 50)
  }
}
