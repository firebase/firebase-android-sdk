package com.google.firebase.dataconnect

import com.google.common.truth.Truth.assertThat
import com.google.firebase.dataconnect.DataConnectError.PathSegment
import com.google.firebase.dataconnect.testutil.containsWithNonAdjacentText
import org.junit.Test

class DataConnectResultTest {

  @Test
  fun `variables should be the same object given to the constructor`() {
    val variables = TestVariables("boo")
    val dataConnectResult =
      DataConnectResult(variables = variables, data = SAMPLE_DATA, errors = SAMPLE_ERRORS)
    assertThat(dataConnectResult.variables).isSameInstanceAs(variables)
  }

  @Test
  fun `data should be the same object given to the constructor`() {
    val data = TestData("blah")
    val dataConnectResult =
      DataConnectResult(variables = SAMPLE_VARIABLES, data = data, errors = SAMPLE_ERRORS)
    assertThat(dataConnectResult.data).isSameInstanceAs(data)
  }

  @Test
  fun `errors should be the same object given to the constructor`() {
    val errors = listOf(SAMPLE_ERROR1)
    val dataConnectResult =
      DataConnectResult(variables = SAMPLE_VARIABLES, data = SAMPLE_DATA, errors = errors)
    assertThat(dataConnectResult.errors).isSameInstanceAs(errors)
  }

  @Test
  fun `errors should be empty if empty was given to the constructor`() {
    val dataConnectResult =
      DataConnectResult<TestVariables, TestData>(
        variables = SAMPLE_VARIABLES,
        data = SAMPLE_DATA,
        errors = emptyList()
      )
    assertThat(dataConnectResult.errors).isEmpty()
  }

  @Test
  fun `toString() should begin with the class name and contain text in parentheses`() {
    val dataConnectResult =
      DataConnectResult(variables = SAMPLE_VARIABLES, data = SAMPLE_DATA, errors = SAMPLE_ERRORS)
    assertThat(dataConnectResult.toString()).startsWith("DataConnectResult(")
    assertThat(dataConnectResult.toString()).endsWith(")")
  }

  @Test
  fun `toString() should incorporate the variables`() {
    val variables =
      object {
        override fun toString() = "TestVariablesToString"
      }
    val dataConnectResult =
      DataConnectResult(variables = variables, data = SAMPLE_DATA, errors = SAMPLE_ERRORS)
    assertThat(dataConnectResult.toString()).containsWithNonAdjacentText("TestVariablesToString")
  }

  @Test
  fun `toString() should incorporate the data`() {
    val data =
      object {
        override fun toString() = "TestDataToString"
      }
    val dataConnectResult =
      DataConnectResult(variables = SAMPLE_VARIABLES, data = data, errors = SAMPLE_ERRORS)
    assertThat(dataConnectResult.toString()).containsWithNonAdjacentText("TestDataToString")
  }

  @Test
  fun `toString() should incorporate the errors`() {
    val errors = listOf(SAMPLE_ERROR1, SAMPLE_ERROR2)
    val dataConnectResult =
      DataConnectResult(variables = SAMPLE_VARIABLES, data = SAMPLE_DATA, errors = errors)
    assertThat(dataConnectResult.toString()).containsWithNonAdjacentText(errors.toString())
  }

  @Test
  fun `toString() should incorporate the errors, if empty`() {
    val dataConnectResult =
      DataConnectResult(variables = SAMPLE_VARIABLES, data = SAMPLE_DATA, errors = emptyList())
    assertThat(dataConnectResult.toString())
      .containsWithNonAdjacentText(emptyList<DataConnectError>().toString())
  }

  @Test
  fun `equals() should return true for the exact same instance`() {
    val dataConnectResult =
      DataConnectResult(variables = SAMPLE_VARIABLES, data = SAMPLE_DATA, errors = SAMPLE_ERRORS)
    assertThat(dataConnectResult.equals(dataConnectResult)).isTrue()
  }

  @Test
  fun `equals() should return true for an equal instance`() {
    val dataConnectResult1 =
      DataConnectResult(variables = SAMPLE_VARIABLES, data = SAMPLE_DATA, errors = SAMPLE_ERRORS)
    val dataConnectResult2 =
      DataConnectResult(variables = SAMPLE_VARIABLES, data = SAMPLE_DATA, errors = SAMPLE_ERRORS)
    assertThat(dataConnectResult1.equals(dataConnectResult2)).isTrue()
  }

  @Test
  fun `equals() should return true if all properties are equal, and data is null`() {
    val dataConnectResult1 =
      DataConnectResult(variables = SAMPLE_VARIABLES, data = null, errors = SAMPLE_ERRORS)
    val dataConnectResult2 =
      DataConnectResult(variables = SAMPLE_VARIABLES, data = null, errors = SAMPLE_ERRORS)
    assertThat(dataConnectResult1.equals(dataConnectResult2)).isTrue()
  }

  @Test
  fun `equals() should return false for null`() {
    val dataConnectResult =
      DataConnectResult(variables = SAMPLE_VARIABLES, data = SAMPLE_DATA, errors = SAMPLE_ERRORS)
    assertThat(dataConnectResult.equals(null)).isFalse()
  }

  @Test
  fun `equals() should return false for a different type`() {
    val dataConnectResult =
      DataConnectResult(variables = SAMPLE_VARIABLES, data = SAMPLE_DATA, errors = SAMPLE_ERRORS)
    assertThat(dataConnectResult.equals(listOf("foo"))).isFalse()
  }

  @Test
  fun `equals() should return false when only variables differs`() {
    val dataConnectResult1 =
      DataConnectResult(
        variables = TestVariables("foo"),
        data = SAMPLE_DATA,
        errors = SAMPLE_ERRORS
      )
    val dataConnectResult2 =
      DataConnectResult(
        variables = TestVariables("bar"),
        data = SAMPLE_DATA,
        errors = SAMPLE_ERRORS
      )
    assertThat(dataConnectResult1.equals(dataConnectResult2)).isFalse()
  }

  @Test
  fun `equals() should return false when only data differs`() {
    val dataConnectResult1 =
      DataConnectResult(
        variables = SAMPLE_VARIABLES,
        data = TestData("foo"),
        errors = SAMPLE_ERRORS
      )
    val dataConnectResult2 =
      DataConnectResult(
        variables = SAMPLE_VARIABLES,
        data = TestData("bar"),
        errors = SAMPLE_ERRORS
      )
    assertThat(dataConnectResult1.equals(dataConnectResult2)).isFalse()
  }

  @Test
  fun `equals() should return false when data of first object is null`() {
    val dataConnectResult1 =
      DataConnectResult(variables = SAMPLE_VARIABLES, data = null, errors = SAMPLE_ERRORS)
    val dataConnectResult2 =
      DataConnectResult(
        variables = SAMPLE_VARIABLES,
        data = TestData("bar"),
        errors = SAMPLE_ERRORS
      )
    assertThat(dataConnectResult1.equals(dataConnectResult2)).isFalse()
  }

  @Test
  fun `equals() should return false when data of second object is null`() {
    val dataConnectResult1 =
      DataConnectResult(
        variables = SAMPLE_VARIABLES,
        data = TestData("foo"),
        errors = SAMPLE_ERRORS
      )
    val dataConnectResult2 =
      DataConnectResult(variables = SAMPLE_VARIABLES, data = null, errors = SAMPLE_ERRORS)
    assertThat(dataConnectResult1.equals(dataConnectResult2)).isFalse()
  }

  @Test
  fun `equals() should return false when only errors differs`() {
    val dataConnectResult1 =
      DataConnectResult(
        variables = SAMPLE_VARIABLES,
        data = SAMPLE_DATA,
        errors = listOf(SAMPLE_ERROR1)
      )
    val dataConnectResult2 =
      DataConnectResult(
        variables = SAMPLE_VARIABLES,
        data = SAMPLE_DATA,
        errors = listOf(SAMPLE_ERROR2)
      )
    assertThat(dataConnectResult1.equals(dataConnectResult2)).isFalse()
  }

  @Test
  fun `hashCode() should return the same value each time it is invoked on a given object`() {
    val dataConnectResult =
      DataConnectResult(variables = SAMPLE_VARIABLES, data = SAMPLE_DATA, errors = SAMPLE_ERRORS)
    val hashCode = dataConnectResult.hashCode()
    assertThat(dataConnectResult.hashCode()).isEqualTo(hashCode)
    assertThat(dataConnectResult.hashCode()).isEqualTo(hashCode)
    assertThat(dataConnectResult.hashCode()).isEqualTo(hashCode)
  }

  @Test
  fun `hashCode() should return the same value on equal objects`() {
    val dataConnectResult1 =
      DataConnectResult(variables = SAMPLE_VARIABLES, data = SAMPLE_DATA, errors = SAMPLE_ERRORS)
    val dataConnectResult2 =
      DataConnectResult(variables = SAMPLE_VARIABLES, data = SAMPLE_DATA, errors = SAMPLE_ERRORS)
    assertThat(dataConnectResult1.hashCode()).isEqualTo(dataConnectResult2.hashCode())
  }

  @Test
  fun `hashCode() should return a different value if variables is different`() {
    val dataConnectResult1 =
      DataConnectResult(
        variables = TestVariables("foo"),
        data = SAMPLE_DATA,
        errors = SAMPLE_ERRORS
      )
    val dataConnectResult2 =
      DataConnectResult(
        variables = TestVariables("bar"),
        data = SAMPLE_DATA,
        errors = SAMPLE_ERRORS
      )
    assertThat(dataConnectResult1.hashCode()).isNotEqualTo(dataConnectResult2.hashCode())
  }

  @Test
  fun `hashCode() should return a different value if data is different`() {
    val dataConnectResult1 =
      DataConnectResult(
        variables = SAMPLE_VARIABLES,
        data = TestData("foo"),
        errors = SAMPLE_ERRORS
      )
    val dataConnectResult2 =
      DataConnectResult(
        variables = SAMPLE_VARIABLES,
        data = TestData("bar"),
        errors = SAMPLE_ERRORS
      )
    assertThat(dataConnectResult1.hashCode()).isNotEqualTo(dataConnectResult2.hashCode())
  }

  @Test
  fun `hashCode() should return a different value if errors is different`() {
    val dataConnectResult1 =
      DataConnectResult(
        variables = SAMPLE_VARIABLES,
        data = SAMPLE_DATA,
        errors = listOf(SAMPLE_ERROR1)
      )
    val dataConnectResult2 =
      DataConnectResult(
        variables = SAMPLE_VARIABLES,
        data = SAMPLE_DATA,
        errors = listOf(SAMPLE_ERROR2)
      )
    assertThat(dataConnectResult1.hashCode()).isNotEqualTo(dataConnectResult2.hashCode())
  }
}

private data class TestVariables(val value: String)

private val SAMPLE_VARIABLES = TestVariables("foo")

private data class TestData(val value: String)

private val SAMPLE_DATA = TestData("bar")

private val SAMPLE_ERROR_MESSAGE1 = "This is a sample message"
private val SAMPLE_ERROR_PATH1 = listOf(PathSegment.Field("foo"), PathSegment.ListIndex(42))
private val SAMPLE_ERROR_EXTENSIONS1 = mapOf<String, Any>("foo" to 42, "bar" to "BAR")
private val SAMPLE_ERROR_MESSAGE2 = "This is a sample message 2"
private val SAMPLE_ERROR_PATH2 = listOf(PathSegment.Field("bar"), PathSegment.ListIndex(24))
private val SAMPLE_ERROR_EXTENSIONS2 = mapOf<String, Any>("blah" to 99, "zzz" to "foo")
private val SAMPLE_ERROR1 =
  DataConnectError(
    message = SAMPLE_ERROR_MESSAGE1,
    path = SAMPLE_ERROR_PATH1,
    extensions = SAMPLE_ERROR_EXTENSIONS1
  )
private val SAMPLE_ERROR2 =
  DataConnectError(
    message = SAMPLE_ERROR_MESSAGE2,
    path = SAMPLE_ERROR_PATH2,
    extensions = SAMPLE_ERROR_EXTENSIONS2
  )
private val SAMPLE_ERRORS = listOf(SAMPLE_ERROR1, SAMPLE_ERROR2)
