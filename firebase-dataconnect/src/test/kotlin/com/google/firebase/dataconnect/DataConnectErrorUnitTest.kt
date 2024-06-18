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

package com.google.firebase.dataconnect

import com.google.common.truth.Truth.assertThat
import com.google.firebase.dataconnect.DataConnectError.PathSegment
import com.google.firebase.dataconnect.DataConnectError.SourceLocation
import com.google.firebase.dataconnect.testutil.containsWithNonAdjacentText
import org.junit.Test

class DataConnectErrorUnitTest {

  @Test
  fun `message should be the same object given to the constructor`() {
    val message = "This is the test message"
    val dataConnectError =
      DataConnectError(message = message, path = SAMPLE_PATH, locations = SAMPLE_LOCATIONS)
    assertThat(dataConnectError.message).isSameInstanceAs(message)
  }

  @Test
  fun `path should be the same object given to the constructor`() {
    val path = listOf(PathSegment.Field("foo"), PathSegment.ListIndex(42))
    val dataConnectError =
      DataConnectError(message = SAMPLE_MESSAGE, path = path, locations = SAMPLE_LOCATIONS)
    assertThat(dataConnectError.path).isSameInstanceAs(path)
  }

  @Test
  fun `locations should be the same object given to the constructor`() {
    val locations =
      listOf(SourceLocation(line = 0, column = -1), SourceLocation(line = 5, column = 6))
    val dataConnectError =
      DataConnectError(message = SAMPLE_MESSAGE, path = SAMPLE_PATH, locations = locations)
    assertThat(dataConnectError.locations).isSameInstanceAs(locations)
  }

  @Test
  fun `toString() should incorporate the message`() {
    val message = "This is the test message"
    val dataConnectError =
      DataConnectError(message = message, path = SAMPLE_PATH, locations = SAMPLE_LOCATIONS)
    assertThat(dataConnectError.toString()).containsWithNonAdjacentText(message)
  }

  @Test
  fun `toString() should incorporate the fields from the path separated by dots`() {
    val path = listOf(PathSegment.Field("foo"), PathSegment.Field("bar"), PathSegment.Field("baz"))
    val dataConnectError =
      DataConnectError(message = SAMPLE_MESSAGE, path = path, locations = SAMPLE_LOCATIONS)
    assertThat(dataConnectError.toString()).containsWithNonAdjacentText("foo.bar.baz")
  }

  @Test
  fun `toString() should incorporate the list indexes from the path surround by square brackets`() {
    val path =
      listOf(PathSegment.ListIndex(42), PathSegment.ListIndex(99), PathSegment.ListIndex(1))
    val dataConnectError =
      DataConnectError(message = SAMPLE_MESSAGE, path = path, locations = SAMPLE_LOCATIONS)
    assertThat(dataConnectError.toString()).containsWithNonAdjacentText("[42][99][1]")
  }

  @Test
  fun `toString() should incorporate the fields and list indexes from the path`() {
    val path =
      listOf(
        PathSegment.Field("foo"),
        PathSegment.ListIndex(99),
        PathSegment.Field("bar"),
        PathSegment.ListIndex(22),
        PathSegment.ListIndex(33)
      )
    val dataConnectError =
      DataConnectError(message = SAMPLE_MESSAGE, path = path, locations = SAMPLE_LOCATIONS)
    assertThat(dataConnectError.toString()).containsWithNonAdjacentText("foo[99].bar[22][33]")
  }

  @Test
  fun `toString() should incorporate the locations`() {
    val locations =
      listOf(SourceLocation(line = 1, column = 2), SourceLocation(line = -1, column = -2))
    val dataConnectError =
      DataConnectError(message = SAMPLE_MESSAGE, path = SAMPLE_PATH, locations = locations)
    assertThat(dataConnectError.toString()).containsWithNonAdjacentText("1:2")
    assertThat(dataConnectError.toString()).containsWithNonAdjacentText("-1:-2")
  }

  @Test
  fun `equals() should return true for the exact same instance`() {
    val dataConnectError =
      DataConnectError(message = SAMPLE_MESSAGE, path = SAMPLE_PATH, locations = SAMPLE_LOCATIONS)
    assertThat(dataConnectError.equals(dataConnectError)).isTrue()
  }

  @Test
  fun `equals() should return true for an equal instance`() {
    val dataConnectError1 =
      DataConnectError(
        message = "Test Message",
        path = listOf(PathSegment.Field("foo"), PathSegment.ListIndex(42)),
        locations =
          listOf(SourceLocation(line = 36, column = 32), SourceLocation(line = 4, column = 5))
      )
    val dataConnectError2 =
      DataConnectError(
        message = "Test Message",
        path = listOf(PathSegment.Field("foo"), PathSegment.ListIndex(42)),
        locations =
          listOf(SourceLocation(line = 36, column = 32), SourceLocation(line = 4, column = 5))
      )
    assertThat(dataConnectError1.equals(dataConnectError2)).isTrue()
  }

  @Test
  fun `equals() should return false for null`() {
    val dataConnectError =
      DataConnectError(message = SAMPLE_MESSAGE, path = SAMPLE_PATH, locations = SAMPLE_LOCATIONS)
    assertThat(dataConnectError.equals(null)).isFalse()
  }

  @Test
  fun `equals() should return false for a different type`() {
    val dataConnectError =
      DataConnectError(message = SAMPLE_MESSAGE, path = SAMPLE_PATH, locations = SAMPLE_LOCATIONS)
    assertThat(dataConnectError.equals(listOf("foo"))).isFalse()
  }

  @Test
  fun `equals() should return false when only message differs`() {
    val dataConnectError1 =
      DataConnectError(message = "Test Message1", path = SAMPLE_PATH, locations = SAMPLE_LOCATIONS)
    val dataConnectError2 =
      DataConnectError(message = "Test Message2", path = SAMPLE_PATH, locations = SAMPLE_LOCATIONS)
    assertThat(dataConnectError1.equals(dataConnectError2)).isFalse()
  }

  @Test
  fun `equals() should return false when message differs only in character case`() {
    val dataConnectError1 =
      DataConnectError(message = "A", path = SAMPLE_PATH, locations = SAMPLE_LOCATIONS)
    val dataConnectError2 =
      DataConnectError(message = "a", path = SAMPLE_PATH, locations = SAMPLE_LOCATIONS)
    assertThat(dataConnectError1.equals(dataConnectError2)).isFalse()
  }

  @Test
  fun `equals() should return false when path differs, with field`() {
    val dataConnectError1 =
      DataConnectError(
        message = SAMPLE_MESSAGE,
        path = listOf(PathSegment.Field("a")),
        locations = SAMPLE_LOCATIONS
      )
    val dataConnectError2 =
      DataConnectError(
        message = SAMPLE_MESSAGE,
        path = listOf(PathSegment.Field("z")),
        locations = SAMPLE_LOCATIONS
      )
    assertThat(dataConnectError1.equals(dataConnectError2)).isFalse()
  }

  @Test
  fun `equals() should return false when path differs, with list index`() {
    val dataConnectError1 =
      DataConnectError(
        message = SAMPLE_MESSAGE,
        path = listOf(PathSegment.ListIndex(1)),
        locations = SAMPLE_LOCATIONS
      )
    val dataConnectError2 =
      DataConnectError(
        message = SAMPLE_MESSAGE,
        path = listOf(PathSegment.ListIndex(2)),
        locations = SAMPLE_LOCATIONS
      )
    assertThat(dataConnectError1.equals(dataConnectError2)).isFalse()
  }

  @Test
  fun `equals() should return false when path differs, with field and list index`() {
    val dataConnectError1 =
      DataConnectError(
        message = SAMPLE_MESSAGE,
        path = listOf(PathSegment.ListIndex(1)),
        locations = SAMPLE_LOCATIONS
      )
    val dataConnectError2 =
      DataConnectError(
        message = SAMPLE_MESSAGE,
        path = listOf(PathSegment.Field("foo")),
        locations = SAMPLE_LOCATIONS
      )
    assertThat(dataConnectError1.equals(dataConnectError2)).isFalse()
  }

  @Test
  fun `equals() should return false when locations differ`() {
    val dataConnectError1 =
      DataConnectError(
        message = SAMPLE_MESSAGE,
        path = SAMPLE_PATH,
        locations = listOf(SourceLocation(line = 36, column = 32))
      )
    val dataConnectError2 =
      DataConnectError(
        message = SAMPLE_MESSAGE,
        path = SAMPLE_PATH,
        locations = listOf(SourceLocation(line = 32, column = 36))
      )
    assertThat(dataConnectError1.equals(dataConnectError2)).isFalse()
  }

  @Test
  fun `hashCode() should return the same value each time it is invoked on a given object`() {
    val dataConnectError =
      DataConnectError(message = SAMPLE_MESSAGE, path = SAMPLE_PATH, locations = SAMPLE_LOCATIONS)
    val hashCode = dataConnectError.hashCode()
    assertThat(dataConnectError.hashCode()).isEqualTo(hashCode)
    assertThat(dataConnectError.hashCode()).isEqualTo(hashCode)
    assertThat(dataConnectError.hashCode()).isEqualTo(hashCode)
  }

  @Test
  fun `hashCode() should return the same value on equal objects`() {
    val dataConnectError1 =
      DataConnectError(message = SAMPLE_MESSAGE, path = SAMPLE_PATH, locations = SAMPLE_LOCATIONS)
    val dataConnectError2 =
      DataConnectError(message = SAMPLE_MESSAGE, path = SAMPLE_PATH, locations = SAMPLE_LOCATIONS)
    assertThat(dataConnectError1.hashCode()).isEqualTo(dataConnectError2.hashCode())
  }

  @Test
  fun `hashCode() should return a different value if message is different`() {
    val dataConnectError1 =
      DataConnectError(message = "Test Message 1", path = SAMPLE_PATH, locations = SAMPLE_LOCATIONS)
    val dataConnectError2 =
      DataConnectError(message = "Test Message 2", path = SAMPLE_PATH, locations = SAMPLE_LOCATIONS)
    assertThat(dataConnectError1.hashCode()).isNotEqualTo(dataConnectError2.hashCode())
  }

  @Test
  fun `hashCode() should return a different value if path is different`() {
    val dataConnectError1 =
      DataConnectError(
        message = SAMPLE_MESSAGE,
        path = listOf(PathSegment.Field("foo")),
        locations = SAMPLE_LOCATIONS
      )
    val dataConnectError2 =
      DataConnectError(
        message = SAMPLE_MESSAGE,
        path = listOf(PathSegment.ListIndex(42)),
        locations = SAMPLE_LOCATIONS
      )
    assertThat(dataConnectError1.hashCode()).isNotEqualTo(dataConnectError2.hashCode())
  }

  @Test
  fun `hashCode() should return a different value if locations is different`() {
    val dataConnectError1 =
      DataConnectError(
        message = SAMPLE_MESSAGE,
        path = SAMPLE_PATH,
        locations = listOf(SourceLocation(line = 81, column = 18))
      )
    val dataConnectError2 =
      DataConnectError(
        message = SAMPLE_MESSAGE,
        path = SAMPLE_PATH,
        locations = listOf(SourceLocation(line = 18, column = 81))
      )
    assertThat(dataConnectError1.hashCode()).isNotEqualTo(dataConnectError2.hashCode())
  }

  private companion object {
    val SAMPLE_MESSAGE = "This is a sample message"
    val SAMPLE_PATH = listOf(PathSegment.Field("foo"), PathSegment.ListIndex(42))
    val SAMPLE_LOCATIONS =
      listOf(SourceLocation(line = 42, column = 24), SourceLocation(line = 91, column = 19))
  }
}
