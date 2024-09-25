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

class PathSegmentFieldUnitTest {

  @Test
  fun `field should equal the value given to the constructor`() {
    val segment = PathSegment.Field("foo")
    assertThat(segment.field).isEqualTo("foo")
  }

  @Test
  fun `toString() should equal the field`() {
    val segment = PathSegment.Field("foo")
    assertThat(segment.toString()).isEqualTo("foo")
  }

  @Test
  fun `equals() should return true for the same instance`() {
    val segment = PathSegment.Field("foo")
    assertThat(segment.equals(segment)).isTrue()
  }

  @Test
  fun `equals() should return true for an equal field`() {
    val segment1 = PathSegment.Field("foo")
    val segment2 = PathSegment.Field("foo")
    assertThat(segment1.equals(segment2)).isTrue()
  }

  @Test
  fun `equals() should return false for null`() {
    val segment = PathSegment.Field("foo")
    assertThat(segment.equals(null)).isFalse()
  }

  @Test
  fun `equals() should return false for a different type`() {
    val segment = PathSegment.Field("foo")
    assertThat(segment.equals(listOf("foo"))).isFalse()
  }

  @Test
  fun `equals() should return false for a different field`() {
    val segment1 = PathSegment.Field("foo")
    val segment2 = PathSegment.Field("bar")
    assertThat(segment1.equals(segment2)).isFalse()
  }

  @Test
  fun `hashCode() should return the same value as the field's hashCode() method`() {
    assertThat(PathSegment.Field("foo").hashCode()).isEqualTo("foo".hashCode())
    assertThat(PathSegment.Field("bar").hashCode()).isEqualTo("bar".hashCode())
  }
}
