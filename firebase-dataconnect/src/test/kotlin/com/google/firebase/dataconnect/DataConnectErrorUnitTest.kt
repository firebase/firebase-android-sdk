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
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnectError
import com.google.firebase.dataconnect.testutil.property.arbitrary.fieldPathSegment
import com.google.firebase.dataconnect.testutil.property.arbitrary.listIndexPathSegment
import com.google.firebase.dataconnect.testutil.property.arbitrary.pathSegment
import com.google.firebase.dataconnect.testutil.property.arbitrary.sourceLocation
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.az
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.string
import io.kotest.property.assume
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DataConnectErrorUnitTest {

  @Test
  fun `properties should be the same objects given to the constructor`() = runTest {
    val messages = Arb.dataConnect.string()
    val paths = Arb.list(Arb.dataConnect.pathSegment(), 0..5)
    val sourceLocations = Arb.list(Arb.dataConnect.sourceLocation(), 0..5)
    checkAll(propTestConfig, messages, paths, sourceLocations) { message, path, locations ->
      val dataConnectError = DataConnectError(message = message, path = path, locations = locations)
      assertSoftly {
        dataConnectError.message shouldBeSameInstanceAs message
        dataConnectError.path shouldBeSameInstanceAs path
        dataConnectError.locations shouldBeSameInstanceAs locations
      }
    }
  }

  @Test
  fun `toString() should incorporate the message`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.dataConnectError()) { dataConnectError ->
      dataConnectError.toString() shouldContainWithNonAbuttingText dataConnectError.message
    }
  }

  @Test
  fun `toString() should incorporate the fields from the path separated by dots`() = runTest {
    val paths = Arb.list(Arb.dataConnect.fieldPathSegment(), 0..5)
    checkAll(propTestConfig, Arb.dataConnect.dataConnectError(path = paths)) { dataConnectError ->
      val expectedSubstring = dataConnectError.path.joinToString(".")
      dataConnectError.toString() shouldContainWithNonAbuttingText expectedSubstring
    }
  }

  @Test
  fun `toString() should incorporate the list indexes from the path surround by square brackets`() =
    runTest {
      val paths = Arb.list(Arb.dataConnect.listIndexPathSegment(), 1..5)
      checkAll(propTestConfig, Arb.dataConnect.dataConnectError(path = paths)) { dataConnectError ->
        val expectedSubstring = dataConnectError.path.joinToString(separator = "") { "[$it]" }
        dataConnectError.toString() shouldContainWithNonAbuttingText expectedSubstring
      }
    }

  @Test
  fun `toString() should incorporate the fields and list indexes from the path`() {
    // Use an example instead of Arb here because using Arb would essentially be re-writing the
    // logic that is implemented in DataConnectError.toString().
    val path =
      listOf(
        PathSegment.Field("foo"),
        PathSegment.ListIndex(99),
        PathSegment.Field("bar"),
        PathSegment.ListIndex(22),
        PathSegment.ListIndex(33)
      )
    val dataConnectError = Arb.dataConnect.dataConnectError(path = Arb.constant(path)).next()

    dataConnectError.toString() shouldContainWithNonAbuttingText "foo[99].bar[22][33]"
  }

  @Test
  fun `toString() should incorporate the locations`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.dataConnectError()) { dataConnectError ->
      assertSoftly {
        dataConnectError.locations.forEach {
          dataConnectError.toString() shouldContainWithNonAbuttingText "${it.line}:${it.column}"
        }
      }
    }
  }

  @Test
  fun `equals() should return true for the exact same instance`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.dataConnectError()) { dataConnectError ->
      dataConnectError.equals(dataConnectError) shouldBe true
    }
  }

  @Test
  fun `equals() should return true for an equal instance`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.dataConnectError()) { dataConnectError1 ->
      val dataConnectError2 =
        DataConnectError(
          message = dataConnectError1.message,
          path = List(dataConnectError1.path.size) { dataConnectError1.path[it] },
          locations = List(dataConnectError1.locations.size) { dataConnectError1.locations[it] },
        )
      dataConnectError1.equals(dataConnectError2) shouldBe true
      dataConnectError2.equals(dataConnectError1) shouldBe true
    }
  }

  @Test
  fun `equals() should return false for null`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.dataConnectError()) { dataConnectError ->
      dataConnectError.equals(null) shouldBe false
    }
  }

  @Test
  fun `equals() should return false for a different type`() = runTest {
    val otherTypes = Arb.choice(Arb.string(), Arb.int(), Arb.dataConnect.sourceLocation())
    checkAll(propTestConfig, Arb.dataConnect.dataConnectError(), otherTypes) {
      dataConnectError,
      other ->
      dataConnectError.equals(other) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only message differs`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.dataConnectError(), Arb.string()) {
      dataConnectError1,
      newMessage ->
      assume(dataConnectError1.message != newMessage)
      val dataConnectError2 =
        DataConnectError(
          message = newMessage,
          path = dataConnectError1.path,
          locations = dataConnectError1.locations,
        )
      dataConnectError1.equals(dataConnectError2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when message differs only in character case`() = runTest {
    val message = Arb.string(1..100, Codepoint.az())
    checkAll(propTestConfig, Arb.dataConnect.dataConnectError(message = message)) { dataConnectError
      ->
      val dataConnectError1 =
        DataConnectError(
          message = dataConnectError.message.uppercase(),
          path = dataConnectError.path,
          locations = dataConnectError.locations,
        )
      val dataConnectError2 =
        DataConnectError(
          message = dataConnectError.message.lowercase(),
          path = dataConnectError.path,
          locations = dataConnectError.locations,
        )
      dataConnectError1.equals(dataConnectError2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when path differs`() = runTest {
    val paths = Arb.list(Arb.dataConnect.pathSegment(), 0..5)
    checkAll(propTestConfig, Arb.dataConnect.dataConnectError(), paths) {
      dataConnectError1,
      otherPath ->
      assume(dataConnectError1.path != otherPath)
      val dataConnectError2 =
        DataConnectError(
          message = dataConnectError1.message,
          path = otherPath,
          locations = dataConnectError1.locations,
        )
      dataConnectError1.equals(dataConnectError2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when locations differ`() = runTest {
    val location = Arb.list(Arb.dataConnect.sourceLocation(), 0..5)
    checkAll(propTestConfig, Arb.dataConnect.dataConnectError(), location) {
      dataConnectError1,
      otherLocations ->
      assume(dataConnectError1.locations != otherLocations)
      val dataConnectError2 =
        DataConnectError(
          message = dataConnectError1.message,
          path = dataConnectError1.path,
          locations = otherLocations,
        )
      dataConnectError1.equals(dataConnectError2) shouldBe false
    }
  }

  @Test
  fun `hashCode() should return the same value each time it is invoked on a given object`() =
    runTest {
      checkAll(propTestConfig, Arb.dataConnect.dataConnectError()) { dataConnectError ->
        val hashCode1 = dataConnectError.hashCode()
        dataConnectError.hashCode() shouldBe hashCode1
        dataConnectError.hashCode() shouldBe hashCode1
      }
    }

  @Test
  fun `hashCode() should return the same value on equal objects`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.dataConnectError()) { dataConnectError1 ->
      val dataConnectError2 =
        DataConnectError(
          message = dataConnectError1.message,
          path = dataConnectError1.path,
          locations = dataConnectError1.locations,
        )
      dataConnectError1.hashCode() shouldBe dataConnectError2.hashCode()
    }
  }

  @Test
  fun `hashCode() should return a different value if message is different`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.dataConnectError(), Arb.string()) {
      dataConnectError1,
      newMessage ->
      assume(dataConnectError1.message.hashCode() != newMessage.hashCode())
      val dataConnectError2 =
        DataConnectError(
          message = newMessage,
          path = dataConnectError1.path,
          locations = dataConnectError1.locations,
        )
      dataConnectError1.hashCode() shouldNotBe dataConnectError2.hashCode()
    }
  }

  @Test
  fun `hashCode() should return a different value if path is different`() = runTest {
    val paths = Arb.list(Arb.dataConnect.pathSegment(), 0..5)
    checkAll(propTestConfig, Arb.dataConnect.dataConnectError(), paths) { dataConnectError1, newPath
      ->
      assume(dataConnectError1.path.hashCode() != newPath.hashCode())
      val dataConnectError2 =
        DataConnectError(
          message = dataConnectError1.message,
          path = newPath,
          locations = dataConnectError1.locations,
        )
      dataConnectError1.hashCode() shouldNotBe dataConnectError2.hashCode()
    }
  }

  @Test
  fun `hashCode() should return a different value if locations is different`() = runTest {
    val locations = Arb.list(Arb.dataConnect.sourceLocation(), 0..5)
    checkAll(propTestConfig, Arb.dataConnect.dataConnectError(), locations) {
      dataConnectError1,
      newLocations ->
      assume(dataConnectError1.locations.hashCode() != newLocations.hashCode())
      val dataConnectError2 =
        DataConnectError(
          message = dataConnectError1.message,
          path = dataConnectError1.path,
          locations = newLocations,
        )
      dataConnectError1.hashCode() shouldNotBe dataConnectError2.hashCode()
    }
  }

  private companion object {
    val propTestConfig = PropTestConfig(iterations = 20)
  }
}
