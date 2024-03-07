package com.google.firebase.dataconnect

import com.google.common.truth.Truth.assertThat
import com.google.firebase.dataconnect.DataConnectError.PathSegment
import com.google.firebase.dataconnect.testutil.containsWithNonAdjacentText
import org.junit.Test

class DataConnectErrorUnitTest {

  @Test
  fun `message should be the same object given to the constructor`() {
    val message = "This is the test message"
    val dataConnectError =
      DataConnectError(message = message, path = SAMPLE_PATH, extensions = SAMPLE_EXTENSIONS)
    assertThat(dataConnectError.message).isSameInstanceAs(message)
  }

  @Test
  fun `path should be the same object given to the constructor`() {
    val path = listOf(PathSegment.Field("foo"), PathSegment.ListIndex(42))
    val dataConnectError =
      DataConnectError(message = SAMPLE_MESSAGE, path = path, extensions = SAMPLE_EXTENSIONS)
    assertThat(dataConnectError.path).isSameInstanceAs(path)
  }

  @Test
  fun `extensions should be the same object given to the constructor`() {
    val extensions = mapOf("foo" to 42, "bar" to "BAR")
    val dataConnectError =
      DataConnectError(message = SAMPLE_MESSAGE, path = SAMPLE_PATH, extensions = extensions)
    assertThat(dataConnectError.extensions).isSameInstanceAs(extensions)
  }

  @Test
  fun `toString() should incorporate the message`() {
    val message = "This is the test message"
    val dataConnectError =
      DataConnectError(message = message, path = SAMPLE_PATH, extensions = SAMPLE_EXTENSIONS)
    assertThat(dataConnectError.toString()).containsWithNonAdjacentText(message)
  }

  @Test
  fun `toString() should incorporate the fields from the path separated by dots`() {
    val path = listOf(PathSegment.Field("foo"), PathSegment.Field("bar"), PathSegment.Field("baz"))
    val dataConnectError =
      DataConnectError(message = SAMPLE_MESSAGE, path = path, extensions = SAMPLE_EXTENSIONS)
    assertThat(dataConnectError.toString()).containsWithNonAdjacentText("foo.bar.baz")
  }

  @Test
  fun `toString() should incorporate the list indexes from the path surround by square brackets`() {
    val path =
      listOf(PathSegment.ListIndex(42), PathSegment.ListIndex(99), PathSegment.ListIndex(1))
    val dataConnectError =
      DataConnectError(message = SAMPLE_MESSAGE, path = path, extensions = SAMPLE_EXTENSIONS)
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
      DataConnectError(message = SAMPLE_MESSAGE, path = path, extensions = SAMPLE_EXTENSIONS)
    assertThat(dataConnectError.toString()).containsWithNonAdjacentText("foo[99].bar[22][33]")
  }

  @Test
  fun `toString() should incorporate the extensions`() {
    val extensions = mapOf("foo" to 42, "bar" to "zzyzx")
    val dataConnectError =
      DataConnectError(message = SAMPLE_MESSAGE, path = SAMPLE_PATH, extensions = extensions)
    assertThat(dataConnectError.toString()).containsWithNonAdjacentText("foo=42")
    assertThat(dataConnectError.toString()).containsWithNonAdjacentText("bar=zzyzx")
  }

  @Test
  fun `toString() should sort the extensions by key, lexicographically`() {
    val extensions = mapOf("bbb" to "zzyzx", "ccc" to false, "aaa" to 42)
    val dataConnectError =
      DataConnectError(message = SAMPLE_MESSAGE, path = SAMPLE_PATH, extensions = extensions)
    assertThat(dataConnectError.toString()).containsMatch("aaa=.*bbb=.*ccc=.*")
  }

  @Test
  fun `equals() should return true for the exact same instance`() {
    val dataConnectError =
      DataConnectError(message = SAMPLE_MESSAGE, path = SAMPLE_PATH, extensions = SAMPLE_EXTENSIONS)
    assertThat(dataConnectError.equals(dataConnectError)).isTrue()
  }

  @Test
  fun `equals() should return true for an equal instance`() {
    val dataConnectError1 =
      DataConnectError(
        message = "Test Message",
        path = listOf(PathSegment.Field("foo"), PathSegment.ListIndex(42)),
        extensions = mapOf<String, Any>("foo" to 42, "bar" to "BAR")
      )
    val dataConnectError2 =
      DataConnectError(
        message = "Test Message",
        path = listOf(PathSegment.Field("foo"), PathSegment.ListIndex(42)),
        extensions = mapOf<String, Any>("foo" to 42, "bar" to "BAR")
      )
    assertThat(dataConnectError1.equals(dataConnectError2)).isTrue()
  }

  @Test
  fun `equals() should return false for null`() {
    val dataConnectError =
      DataConnectError(message = SAMPLE_MESSAGE, path = SAMPLE_PATH, extensions = SAMPLE_EXTENSIONS)
    assertThat(dataConnectError.equals(null)).isFalse()
  }

  @Test
  fun `equals() should return false for a different type`() {
    val dataConnectError =
      DataConnectError(message = SAMPLE_MESSAGE, path = SAMPLE_PATH, extensions = SAMPLE_EXTENSIONS)
    assertThat(dataConnectError.equals(listOf("foo"))).isFalse()
  }

  @Test
  fun `equals() should return false when only message differs`() {
    val dataConnectError1 =
      DataConnectError(
        message = "Test Message1",
        path = SAMPLE_PATH,
        extensions = SAMPLE_EXTENSIONS
      )
    val dataConnectError2 =
      DataConnectError(
        message = "Test Message2",
        path = SAMPLE_PATH,
        extensions = SAMPLE_EXTENSIONS
      )
    assertThat(dataConnectError1.equals(dataConnectError2)).isFalse()
  }

  @Test
  fun `equals() should return false when message differs only in character case`() {
    val dataConnectError1 =
      DataConnectError(message = "A", path = SAMPLE_PATH, extensions = SAMPLE_EXTENSIONS)
    val dataConnectError2 =
      DataConnectError(message = "a", path = SAMPLE_PATH, extensions = SAMPLE_EXTENSIONS)
    assertThat(dataConnectError1.equals(dataConnectError2)).isFalse()
  }

  @Test
  fun `equals() should return false when path differs, with field`() {
    val dataConnectError1 =
      DataConnectError(
        message = SAMPLE_MESSAGE,
        path = listOf(PathSegment.Field("a")),
        extensions = SAMPLE_EXTENSIONS
      )
    val dataConnectError2 =
      DataConnectError(
        message = SAMPLE_MESSAGE,
        path = listOf(PathSegment.Field("z")),
        extensions = SAMPLE_EXTENSIONS
      )
    assertThat(dataConnectError1.equals(dataConnectError2)).isFalse()
  }

  @Test
  fun `equals() should return false when path differs, with list index`() {
    val dataConnectError1 =
      DataConnectError(
        message = SAMPLE_MESSAGE,
        path = listOf(PathSegment.ListIndex(1)),
        extensions = SAMPLE_EXTENSIONS
      )
    val dataConnectError2 =
      DataConnectError(
        message = SAMPLE_MESSAGE,
        path = listOf(PathSegment.ListIndex(2)),
        extensions = SAMPLE_EXTENSIONS
      )
    assertThat(dataConnectError1.equals(dataConnectError2)).isFalse()
  }

  @Test
  fun `equals() should return false when path differs, with field and list index`() {
    val dataConnectError1 =
      DataConnectError(
        message = SAMPLE_MESSAGE,
        path = listOf(PathSegment.ListIndex(1)),
        extensions = SAMPLE_EXTENSIONS
      )
    val dataConnectError2 =
      DataConnectError(
        message = SAMPLE_MESSAGE,
        path = listOf(PathSegment.Field("foo")),
        extensions = SAMPLE_EXTENSIONS
      )
    assertThat(dataConnectError1.equals(dataConnectError2)).isFalse()
  }

  @Test
  fun `equals() should return false when extensions differs in values only`() {
    val dataConnectError1 =
      DataConnectError(
        message = SAMPLE_MESSAGE,
        path = SAMPLE_PATH,
        extensions = mapOf("foo" to 42)
      )
    val dataConnectError2 =
      DataConnectError(
        message = SAMPLE_MESSAGE,
        path = SAMPLE_PATH,
        extensions = mapOf("foo" to 43)
      )
    assertThat(dataConnectError1.equals(dataConnectError2)).isFalse()
  }

  @Test
  fun `equals() should return false when extensions differs in keys only`() {
    val dataConnectError1 =
      DataConnectError(
        message = SAMPLE_MESSAGE,
        path = SAMPLE_PATH,
        extensions = mapOf("foo" to 42)
      )
    val dataConnectError2 =
      DataConnectError(
        message = SAMPLE_MESSAGE,
        path = SAMPLE_PATH,
        extensions = mapOf("bar" to 42)
      )
    assertThat(dataConnectError1.equals(dataConnectError2)).isFalse()
  }

  @Test
  fun `hashCode() should return the same value each time it is invoked on a given object`() {
    val dataConnectError =
      DataConnectError(message = SAMPLE_MESSAGE, path = SAMPLE_PATH, extensions = SAMPLE_EXTENSIONS)
    val hashCode = dataConnectError.hashCode()
    assertThat(dataConnectError.hashCode()).isEqualTo(hashCode)
    assertThat(dataConnectError.hashCode()).isEqualTo(hashCode)
    assertThat(dataConnectError.hashCode()).isEqualTo(hashCode)
  }

  @Test
  fun `hashCode() should return the same value on equal objects`() {
    val dataConnectError1 =
      DataConnectError(message = SAMPLE_MESSAGE, path = SAMPLE_PATH, extensions = SAMPLE_EXTENSIONS)
    val dataConnectError2 =
      DataConnectError(message = SAMPLE_MESSAGE, path = SAMPLE_PATH, extensions = SAMPLE_EXTENSIONS)
    assertThat(dataConnectError1.hashCode()).isEqualTo(dataConnectError2.hashCode())
  }

  @Test
  fun `hashCode() should return a different value if message is different`() {
    val dataConnectError1 =
      DataConnectError(
        message = "Test Message 1",
        path = SAMPLE_PATH,
        extensions = SAMPLE_EXTENSIONS
      )
    val dataConnectError2 =
      DataConnectError(
        message = "Test Message 2",
        path = SAMPLE_PATH,
        extensions = SAMPLE_EXTENSIONS
      )
    assertThat(dataConnectError1.hashCode()).isNotEqualTo(dataConnectError2.hashCode())
  }

  @Test
  fun `hashCode() should return a different value if path is different`() {
    val dataConnectError1 =
      DataConnectError(
        message = SAMPLE_MESSAGE,
        path = listOf(PathSegment.Field("foo")),
        extensions = SAMPLE_EXTENSIONS
      )
    val dataConnectError2 =
      DataConnectError(
        message = SAMPLE_MESSAGE,
        path = listOf(PathSegment.ListIndex(42)),
        extensions = SAMPLE_EXTENSIONS
      )
    assertThat(dataConnectError1.hashCode()).isNotEqualTo(dataConnectError2.hashCode())
  }

  @Test
  fun `hashCode() should return a different value if extensions is different`() {
    val dataConnectError1 =
      DataConnectError(
        message = SAMPLE_MESSAGE,
        path = SAMPLE_PATH,
        extensions = mapOf("foo" to 42)
      )
    val dataConnectError2 =
      DataConnectError(
        message = SAMPLE_MESSAGE,
        path = SAMPLE_PATH,
        extensions = mapOf("bar" to 24)
      )
    assertThat(dataConnectError1.hashCode()).isNotEqualTo(dataConnectError2.hashCode())
  }
}

private val SAMPLE_MESSAGE = "This is a sample message"
private val SAMPLE_PATH = listOf(PathSegment.Field("foo"), PathSegment.ListIndex(42))
private val SAMPLE_EXTENSIONS = mapOf<String, Any>("foo" to 42, "bar" to "BAR")
