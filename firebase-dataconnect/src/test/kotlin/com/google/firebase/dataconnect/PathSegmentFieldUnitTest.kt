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

@file:Suppress("ReplaceCallWithBinaryOperator")

package com.google.firebase.dataconnect

import com.google.firebase.dataconnect.DataConnectError.PathSegment
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.fieldPathSegment
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.assume
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PathSegmentFieldUnitTest {

  @Test
  fun `field should equal the value given to the constructor`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string()) { field ->
      val segment = PathSegment.Field(field)
      segment.field shouldBe field
    }
  }

  @Test
  fun `toString() should equal the field`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string()) { field ->
      val segment = PathSegment.Field(field)
      segment.toString() shouldBe field
    }
  }

  @Test
  fun `equals() should return true for the same instance`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.fieldPathSegment()) { segment ->
      segment.equals(segment) shouldBe true
    }
  }

  @Test
  fun `equals() should return true for an equal field`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string()) { field ->
      val segment1 = PathSegment.Field(field)
      val segment2 = PathSegment.Field(field)
      segment1.equals(segment2) shouldBe true
    }
  }

  @Test
  fun `equals() should return false for null`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.fieldPathSegment()) { segment ->
      segment.equals(null) shouldBe false
    }
  }

  @Test
  fun `equals() should return false for a different type`() = runTest {
    val others = Arb.choice(Arb.string(), Arb.int(), Arb.dataConnect.dataConnectSettings())
    checkAll(propTestConfig, Arb.dataConnect.fieldPathSegment(), others) { segment, other ->
      segment.equals(other) shouldBe false
    }
  }

  @Test
  fun `equals() should return false for a different field`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string(), Arb.dataConnect.string()) { field1, field2 ->
      assume(field1 != field2)
      val segment1 = PathSegment.Field(field1)
      val segment2 = PathSegment.Field(field2)
      segment1.equals(segment2) shouldBe false
    }
  }

  @Test
  fun `hashCode() should return the same value as the field's hashCode() method`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string()) { field ->
      val segment = PathSegment.Field(field)
      segment.hashCode() shouldBe field.hashCode()
    }
  }

  private companion object {
    val propTestConfig = PropTestConfig(iterations = 20)
  }
}
