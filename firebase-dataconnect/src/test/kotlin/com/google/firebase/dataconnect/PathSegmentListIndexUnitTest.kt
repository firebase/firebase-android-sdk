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
import org.junit.Test

class PathSegmentListIndexUnitTest {

  @Test
  fun `index should equal the value given to the constructor`() {
    val segment = PathSegment.ListIndex(42)
    assertThat(segment.index).isEqualTo(42)
  }

  @Test
  fun `toString() should equal the field`() {
    val segment = PathSegment.ListIndex(42)
    assertThat(segment.toString()).isEqualTo("42")
  }

  @Test
  fun `equals() should return true for the same instance`() {
    val segment = PathSegment.ListIndex(42)
    assertThat(segment.equals(segment)).isTrue()
  }

  @Test
  fun `equals() should return true for an equal field`() {
    val segment1 = PathSegment.ListIndex(42)
    val segment2 = PathSegment.ListIndex(42)
    assertThat(segment1.equals(segment2)).isTrue()
  }

  @Test
  fun `equals() should return false for null`() {
    val segment = PathSegment.ListIndex(42)
    assertThat(segment.equals(null)).isFalse()
  }

  @Test
  fun `equals() should return false for a different type`() {
    val segment = PathSegment.ListIndex(42)
    assertThat(segment.equals(listOf("foo"))).isFalse()
  }

  @Test
  fun `equals() should return false for a different index`() {
    val segment1 = PathSegment.ListIndex(42)
    val segment2 = PathSegment.ListIndex(43)
    assertThat(segment1.equals(segment2)).isFalse()
  }

  @Test
  fun `hashCode() should return the same value as the field's hashCode() method`() {
    assertThat(PathSegment.ListIndex(42).hashCode()).isEqualTo(42.hashCode())
    assertThat(PathSegment.ListIndex(43).hashCode()).isEqualTo(43.hashCode())
  }
}
