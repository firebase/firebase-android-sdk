package com.google.firebase.dataconnect

import com.google.common.truth.Truth.assertThat
import com.google.firebase.dataconnect.DataConnectError.PathSegment
import org.junit.Test

class PathSegmentListIndexTest {

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
