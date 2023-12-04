package com.google.firebase.dataconnect

import com.google.common.truth.Truth.assertThat
import com.google.firebase.dataconnect.testutil.containsWithNonAdjacentText
import org.junit.Test

class DataConnectResultTest {

  @Test
  fun `variables should be the same object given to the constructor`() {
    val variables = TestVariables("boo")
    val dataConnectResult =
      DataConnectResult(
        variables = variables,
        data = SAMPLE_DATA,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    assertThat(dataConnectResult.variables).isSameInstanceAs(variables)
  }

  @Test
  fun `data should be the same object given to the constructor`() {
    val data = TestData("blah")
    val dataConnectResult =
      DataConnectResult(
        variables = SAMPLE_VARIABLES,
        data = data,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    assertThat(dataConnectResult.data).isSameInstanceAs(data)
  }

  @Test
  fun `sequenceNumber should be the same object given to the constructor`() {
    val dataConnectResult =
      DataConnectResult(variables = SAMPLE_VARIABLES, data = SAMPLE_DATA, sequenceNumber = 12345)

    assertThat(dataConnectResult.sequenceNumber).isEqualTo(12345)
  }

  @Test
  fun `toString() should begin with the class name and contain text in parentheses`() {
    val dataConnectResult =
      DataConnectResult(
        variables = SAMPLE_VARIABLES,
        data = SAMPLE_DATA,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
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
      DataConnectResult(
        variables = variables,
        data = SAMPLE_DATA,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    assertThat(dataConnectResult.toString()).containsWithNonAdjacentText("TestVariablesToString")
  }

  @Test
  fun `toString() should incorporate the data`() {
    val data =
      object {
        override fun toString() = "TestDataToString"
      }
    val dataConnectResult =
      DataConnectResult(
        variables = SAMPLE_VARIABLES,
        data = data,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    assertThat(dataConnectResult.toString()).containsWithNonAdjacentText("TestDataToString")
  }

  @Test
  fun `toString() should NOT incorporate the sequenceNumber`() {
    val dataConnectResult =
      DataConnectResult(
        variables = SAMPLE_VARIABLES,
        data = SAMPLE_DATA,
        sequenceNumber = 123456789123456789
      )
    assertThat(dataConnectResult.toString()).doesNotContain("123456789123456789")
  }

  @Test
  fun `equals() should return true for the exact same instance`() {
    val dataConnectResult =
      DataConnectResult(
        variables = SAMPLE_VARIABLES,
        data = SAMPLE_DATA,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    assertThat(dataConnectResult.equals(dataConnectResult)).isTrue()
  }

  @Test
  fun `equals() should return true for an equal instance`() {
    val dataConnectResult1 =
      DataConnectResult(
        variables = SAMPLE_VARIABLES,
        data = SAMPLE_DATA,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    val dataConnectResult2 =
      DataConnectResult(
        variables = SAMPLE_VARIABLES,
        data = SAMPLE_DATA,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    assertThat(dataConnectResult1.equals(dataConnectResult2)).isTrue()
  }

  @Test
  fun `equals() should return true if all properties are equal, and data is null`() {
    val dataConnectResult1 =
      DataConnectResult(
        variables = SAMPLE_VARIABLES,
        data = null,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    val dataConnectResult2 =
      DataConnectResult(
        variables = SAMPLE_VARIABLES,
        data = null,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    assertThat(dataConnectResult1.equals(dataConnectResult2)).isTrue()
  }

  @Test
  fun `equals() should return true for equal instances with different sequenceNumber`() {
    val dataConnectResult1 =
      DataConnectResult(variables = SAMPLE_VARIABLES, data = SAMPLE_DATA, sequenceNumber = 1)

    val dataConnectResult2 =
      DataConnectResult(variables = SAMPLE_VARIABLES, data = SAMPLE_DATA, sequenceNumber = 2)

    assertThat(dataConnectResult1.equals(dataConnectResult2)).isTrue()
  }

  @Test
  fun `equals() should return false for null`() {
    val dataConnectResult =
      DataConnectResult(
        variables = SAMPLE_VARIABLES,
        data = SAMPLE_DATA,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    assertThat(dataConnectResult.equals(null)).isFalse()
  }

  @Test
  fun `equals() should return false for a different type`() {
    val dataConnectResult =
      DataConnectResult(
        variables = SAMPLE_VARIABLES,
        data = SAMPLE_DATA,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    assertThat(dataConnectResult.equals(listOf("foo"))).isFalse()
  }

  @Test
  fun `equals() should return false when only variables differs`() {
    val dataConnectResult1 =
      DataConnectResult(
        variables = TestVariables("foo"),
        data = SAMPLE_DATA,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    val dataConnectResult2 =
      DataConnectResult(
        variables = TestVariables("bar"),
        data = SAMPLE_DATA,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    assertThat(dataConnectResult1.equals(dataConnectResult2)).isFalse()
  }

  @Test
  fun `equals() should return false when only data differs`() {
    val dataConnectResult1 =
      DataConnectResult(
        variables = SAMPLE_VARIABLES,
        data = TestData("foo"),
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    val dataConnectResult2 =
      DataConnectResult(
        variables = SAMPLE_VARIABLES,
        data = TestData("bar"),
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    assertThat(dataConnectResult1.equals(dataConnectResult2)).isFalse()
  }

  @Test
  fun `equals() should return false when data of first object is null`() {
    val dataConnectResult1 =
      DataConnectResult(
        variables = SAMPLE_VARIABLES,
        data = null,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    val dataConnectResult2 =
      DataConnectResult(
        variables = SAMPLE_VARIABLES,
        data = TestData("bar"),
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    assertThat(dataConnectResult1.equals(dataConnectResult2)).isFalse()
  }

  @Test
  fun `equals() should return false when data of second object is null`() {
    val dataConnectResult1 =
      DataConnectResult(
        variables = SAMPLE_VARIABLES,
        data = TestData("foo"),
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    val dataConnectResult2 =
      DataConnectResult(
        variables = SAMPLE_VARIABLES,
        data = null,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    assertThat(dataConnectResult1.equals(dataConnectResult2)).isFalse()
  }

  @Test
  fun `hashCode() should return the same value each time it is invoked on a given object`() {
    val dataConnectResult =
      DataConnectResult(
        variables = SAMPLE_VARIABLES,
        data = SAMPLE_DATA,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    val hashCode = dataConnectResult.hashCode()
    assertThat(dataConnectResult.hashCode()).isEqualTo(hashCode)
    assertThat(dataConnectResult.hashCode()).isEqualTo(hashCode)
    assertThat(dataConnectResult.hashCode()).isEqualTo(hashCode)
  }

  @Test
  fun `hashCode() should return the same value on equal objects`() {
    val dataConnectResult1 =
      DataConnectResult(
        variables = SAMPLE_VARIABLES,
        data = SAMPLE_DATA,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    val dataConnectResult2 =
      DataConnectResult(
        variables = SAMPLE_VARIABLES,
        data = SAMPLE_DATA,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    assertThat(dataConnectResult1.hashCode()).isEqualTo(dataConnectResult2.hashCode())
  }

  @Test
  fun `hashCode() should return the same value on equal objects, even if sequenceNumber differs`() {
    val dataConnectResult1 =
      DataConnectResult(variables = SAMPLE_VARIABLES, data = SAMPLE_DATA, sequenceNumber = 1)

    val dataConnectResult2 =
      DataConnectResult(variables = SAMPLE_VARIABLES, data = SAMPLE_DATA, sequenceNumber = 2)

    assertThat(dataConnectResult1.hashCode()).isEqualTo(dataConnectResult2.hashCode())
  }

  @Test
  fun `hashCode() should return a different value if variables is different`() {
    val dataConnectResult1 =
      DataConnectResult(
        variables = TestVariables("foo"),
        data = SAMPLE_DATA,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    val dataConnectResult2 =
      DataConnectResult(
        variables = TestVariables("bar"),
        data = SAMPLE_DATA,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    assertThat(dataConnectResult1.hashCode()).isNotEqualTo(dataConnectResult2.hashCode())
  }

  @Test
  fun `hashCode() should return a different value if data is different`() {
    val dataConnectResult1 =
      DataConnectResult(
        variables = SAMPLE_VARIABLES,
        data = TestData("foo"),
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    val dataConnectResult2 =
      DataConnectResult(
        variables = SAMPLE_VARIABLES,
        data = TestData("bar"),
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    assertThat(dataConnectResult1.hashCode()).isNotEqualTo(dataConnectResult2.hashCode())
  }

  private data class TestVariables(val value: String)

  private val SAMPLE_VARIABLES = TestVariables("foo")

  private data class TestData(val value: String)

  private val SAMPLE_DATA = TestData("bar")

  private val SAMPLE_SEQUENCE_NUMBER: Long = -1
}
