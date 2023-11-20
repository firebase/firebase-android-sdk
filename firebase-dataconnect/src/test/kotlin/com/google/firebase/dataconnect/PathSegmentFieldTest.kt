package com.google.firebase.dataconnect

import com.google.common.truth.Truth.assertThat
import com.google.firebase.dataconnect.DataConnectError.PathSegment
import org.junit.Test

class PathSegmentFieldTest {

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
