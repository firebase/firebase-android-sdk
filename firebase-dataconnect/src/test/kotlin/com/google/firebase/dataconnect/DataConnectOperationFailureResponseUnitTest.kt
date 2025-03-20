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
@file:Suppress("ReplaceCallWithBinaryOperator")

package com.google.firebase.dataconnect

import com.google.firebase.dataconnect.DataConnectOperationFailureResponse.ErrorInfo.PathSegment
import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb.fieldPathSegment as fieldPathSegmentArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb.listIndexPathSegment as listIndexPathSegmentArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.assume
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

private val propTestConfig =
  PropTestConfig(iterations = 20, edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.25))

/** Unit tests for [PathSegment.Field] */
class DataConnectOperationFailureResponsePathSegmentFieldUnitTest {

  @Test
  fun `constructor should set field property`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string()) { field ->
      val pathSegment = PathSegment.Field(field)
      pathSegment.field shouldBeSameInstanceAs field
    }
  }

  @Test
  fun `toString() should return a string equal to the field property`() = runTest {
    checkAll(propTestConfig, fieldPathSegmentArb()) { pathSegment: PathSegment.Field ->
      pathSegment.toString() shouldBeSameInstanceAs pathSegment.field
    }
  }

  @Test
  fun `equals() should return true for the exact same instance`() = runTest {
    checkAll(propTestConfig, fieldPathSegmentArb()) { pathSegment: PathSegment.Field ->
      pathSegment.equals(pathSegment) shouldBe true
    }
  }

  @Test
  fun `equals() should return true for an equal instance`() = runTest {
    checkAll(propTestConfig, fieldPathSegmentArb()) { pathSegment1: PathSegment.Field ->
      val pathSegment2 = PathSegment.Field(pathSegment1.field)
      pathSegment1.equals(pathSegment2) shouldBe true
      pathSegment2.equals(pathSegment1) shouldBe true
    }
  }

  @Test
  fun `equals() should return false for null`() = runTest {
    checkAll(propTestConfig, fieldPathSegmentArb()) { pathSegment: PathSegment.Field ->
      pathSegment.equals(null) shouldBe false
    }
  }

  @Test
  fun `equals() should return false for a different type`() = runTest {
    val otherTypes = Arb.choice(Arb.string(), Arb.int(), listIndexPathSegmentArb())
    checkAll(propTestConfig, fieldPathSegmentArb(), otherTypes) {
      pathSegment: PathSegment.Field,
      other ->
      pathSegment.equals(other) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when field differs`() = runTest {
    checkAll(propTestConfig, fieldPathSegmentArb(), fieldPathSegmentArb()) {
      pathSegment1: PathSegment.Field,
      pathSegment2: PathSegment.Field ->
      assume(pathSegment1.field != pathSegment2.field)
      pathSegment1.equals(pathSegment2) shouldBe false
    }
  }

  @Test
  fun `hashCode() should return the same value each time it is invoked on a given object`() =
    runTest {
      checkAll(propTestConfig, fieldPathSegmentArb()) { pathSegment: PathSegment.Field ->
        val hashCode1 = pathSegment.hashCode()
        pathSegment.hashCode() shouldBe hashCode1
        pathSegment.hashCode() shouldBe hashCode1
      }
    }

  @Test
  fun `hashCode() should return the same value on equal objects`() = runTest {
    checkAll(propTestConfig, fieldPathSegmentArb()) { pathSegment1: PathSegment.Field ->
      val pathSegment2 = PathSegment.Field(pathSegment1.field)
      pathSegment1.hashCode() shouldBe pathSegment2.hashCode()
    }
  }

  @Test
  fun `hashCode() should return a different value if field is different`() = runTest {
    checkAll(propTestConfig, fieldPathSegmentArb(), fieldPathSegmentArb()) {
      pathSegment1: PathSegment.Field,
      pathSegment2: PathSegment.Field ->
      assume(pathSegment1.field.hashCode() != pathSegment2.field.hashCode())
      pathSegment1.hashCode() shouldNotBe pathSegment2.hashCode()
    }
  }
}

/** Unit tests for [PathSegment.ListIndex] */
class DataConnectOperationFailureResponsePathSegmentListIndexUnitTest {

  @Test
  fun `constructor should set index property`() = runTest {
    checkAll(propTestConfig, Arb.int()) { listIndex ->
      val pathSegment = PathSegment.ListIndex(listIndex)
      pathSegment.index shouldBe listIndex
    }
  }

  @Test
  fun `toString() should return a string equal to the index property`() = runTest {
    checkAll(propTestConfig, listIndexPathSegmentArb()) { pathSegment: PathSegment.ListIndex ->
      pathSegment.toString() shouldBe "${pathSegment.index}"
    }
  }

  @Test
  fun `equals() should return true for the exact same instance`() = runTest {
    checkAll(propTestConfig, listIndexPathSegmentArb()) { pathSegment: PathSegment.ListIndex ->
      pathSegment.equals(pathSegment) shouldBe true
    }
  }

  @Test
  fun `equals() should return true for an equal instance`() = runTest {
    checkAll(propTestConfig, listIndexPathSegmentArb()) { pathSegment1: PathSegment.ListIndex ->
      val pathSegment2 = PathSegment.ListIndex(pathSegment1.index)
      pathSegment1.equals(pathSegment2) shouldBe true
      pathSegment2.equals(pathSegment1) shouldBe true
    }
  }

  @Test
  fun `equals() should return false for null`() = runTest {
    checkAll(propTestConfig, listIndexPathSegmentArb()) { pathSegment: PathSegment.ListIndex ->
      pathSegment.equals(null) shouldBe false
    }
  }

  @Test
  fun `equals() should return false for a different type`() = runTest {
    val otherTypes = Arb.choice(Arb.string(), Arb.int(), fieldPathSegmentArb())
    checkAll(propTestConfig, listIndexPathSegmentArb(), otherTypes) {
      pathSegment: PathSegment.ListIndex,
      other ->
      pathSegment.equals(other) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when field differs`() = runTest {
    checkAll(propTestConfig, listIndexPathSegmentArb(), listIndexPathSegmentArb()) {
      pathSegment1: PathSegment.ListIndex,
      pathSegment2: PathSegment.ListIndex ->
      assume(pathSegment1.index != pathSegment2.index)
      pathSegment1.equals(pathSegment2) shouldBe false
    }
  }

  @Test
  fun `hashCode() should return the same value each time it is invoked on a given object`() =
    runTest {
      checkAll(propTestConfig, listIndexPathSegmentArb()) { pathSegment: PathSegment.ListIndex ->
        val hashCode1 = pathSegment.hashCode()
        pathSegment.hashCode() shouldBe hashCode1
        pathSegment.hashCode() shouldBe hashCode1
      }
    }

  @Test
  fun `hashCode() should return the same value on equal objects`() = runTest {
    checkAll(propTestConfig, listIndexPathSegmentArb()) { pathSegment1: PathSegment.ListIndex ->
      val pathSegment2 = PathSegment.ListIndex(pathSegment1.index)
      pathSegment1.hashCode() shouldBe pathSegment2.hashCode()
    }
  }

  @Test
  fun `hashCode() should return a different value if index is different`() = runTest {
    checkAll(propTestConfig, listIndexPathSegmentArb(), listIndexPathSegmentArb()) {
      pathSegment1: PathSegment.ListIndex,
      pathSegment2: PathSegment.ListIndex ->
      assume(pathSegment1.index.hashCode() != pathSegment2.index.hashCode())
      pathSegment1.hashCode() shouldNotBe pathSegment2.hashCode()
    }
  }
}
