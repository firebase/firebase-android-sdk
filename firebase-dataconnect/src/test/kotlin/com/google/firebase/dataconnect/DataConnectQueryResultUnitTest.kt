package com.google.firebase.dataconnect

import com.google.common.truth.Truth.assertThat
import com.google.firebase.dataconnect.testutil.containsWithNonAdjacentText
import com.google.firebase.dataconnect.testutil.newTestInstance
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Suppress("ReplaceCallWithBinaryOperator")
class DataConnectQueryResultUnitTest {

  private lateinit var sampleQuery: QueryRef<TestData?, TestVariables>
  private lateinit var sampleQuery1: QueryRef<TestData?, TestVariables>
  private lateinit var sampleQuery2: QueryRef<TestData?, TestVariables>

  @Before
  fun prepareSampleQueries() {
    sampleQuery =
      QueryRef(
        dataConnect = FirebaseDataConnect.newTestInstance(),
        operationName = "SampleQueryOperation",
        variables = TestVariables("SampleQueryVariables"),
        dataDeserializer = serializer(),
        variablesSerializer = serializer()
      )
    sampleQuery1 =
      QueryRef(
        dataConnect = FirebaseDataConnect.newTestInstance(),
        operationName = "SampleQuery1Operation",
        variables = TestVariables("SampleQuery1Variables"),
        dataDeserializer = serializer(),
        variablesSerializer = serializer()
      )
    sampleQuery2 =
      QueryRef(
        dataConnect = FirebaseDataConnect.newTestInstance(),
        operationName = "SampleQuery2Operation",
        variables = TestVariables("SampleQuery2Variables"),
        dataDeserializer = serializer(),
        variablesSerializer = serializer()
      )
  }

  @Test
  fun `'data' should be the same object given to the constructor`() {
    val data = TestData("blah")
    val dataConnectQueryResult =
      DataConnectQueryResult(
        data = data,
        query = sampleQuery,
        sequenceNumber = sampleSequenceNumber
      )

    assertThat(dataConnectQueryResult.data).isSameInstanceAs(data)
  }

  @Test
  fun `'ref' should be the same object given to the constructor for the 'query' argument`() {
    val dataConnectQueryResult =
      DataConnectQueryResult(
        data = TestData(),
        query = sampleQuery,
        sequenceNumber = sampleSequenceNumber
      )

    assertThat(dataConnectQueryResult.ref).isSameInstanceAs(sampleQuery)
  }

  @Test
  fun `sequenceNumber should be the same value given to the constructor`() {
    val dataConnectQueryResult =
      DataConnectQueryResult(data = TestData(), query = sampleQuery, sequenceNumber = 12345)

    assertThat(dataConnectQueryResult.sequenceNumber).isEqualTo(12345)
  }

  @Test
  fun `toString() should begin with the class name and contain text in parentheses`() {
    val dataConnectQueryResult =
      DataConnectQueryResult(
        data = TestData(),
        query = sampleQuery,
        sequenceNumber = sampleSequenceNumber
      )

    assertThat(dataConnectQueryResult.toString()).startsWith("DataConnectQueryResult(")
    assertThat(dataConnectQueryResult.toString()).endsWith(")")
  }

  @Test
  fun `toString() should incorporate 'data'`() {
    val data = TestData()
    val dataConnectQueryResult =
      DataConnectQueryResult(
        data = data,
        query = sampleQuery,
        sequenceNumber = sampleSequenceNumber
      )

    assertThat(dataConnectQueryResult.toString()).containsWithNonAdjacentText("data=$data")
  }

  @Test
  fun `toString() should incorporate 'ref'`() {
    val dataConnectQueryResult =
      DataConnectQueryResult(
        data = TestData(),
        query = sampleQuery,
        sequenceNumber = sampleSequenceNumber
      )

    assertThat(dataConnectQueryResult.toString()).containsWithNonAdjacentText("query=$sampleQuery")
  }

  @Test
  fun `toString() should NOT incorporate the sequenceNumber`() {
    val dataConnectQueryResult =
      DataConnectQueryResult(
        data = TestData(),
        query = sampleQuery,
        sequenceNumber = 123456789123456789
      )

    assertThat(dataConnectQueryResult.toString()).doesNotContain("123456789123456789")
  }

  @Test
  fun `equals() should return true for the exact same instance`() {
    val dataConnectQueryResult =
      DataConnectQueryResult(
        data = TestData(),
        query = sampleQuery,
        sequenceNumber = sampleSequenceNumber
      )

    assertThat(dataConnectQueryResult.equals(dataConnectQueryResult)).isTrue()
  }

  @Test
  fun `equals() should return true for an equal instance`() {
    val dataConnectQueryResult1 =
      DataConnectQueryResult(
        data = TestData(),
        query = sampleQuery,
        sequenceNumber = sampleSequenceNumber
      )
    val dataConnectQueryResult2 =
      DataConnectQueryResult(
        data = TestData(),
        query = sampleQuery,
        sequenceNumber = sampleSequenceNumber
      )

    assertThat(dataConnectQueryResult1.equals(dataConnectQueryResult2)).isTrue()
  }

  @Test
  fun `equals() should return true if all properties are equal, and 'data' is null`() {
    val dataConnectQueryResult1 =
      DataConnectQueryResult(
        data = null,
        query = sampleQuery,
        sequenceNumber = sampleSequenceNumber
      )
    val dataConnectQueryResult2 =
      DataConnectQueryResult(
        data = null,
        query = sampleQuery,
        sequenceNumber = sampleSequenceNumber
      )

    assertThat(dataConnectQueryResult1.equals(dataConnectQueryResult2)).isTrue()
  }

  @Test
  fun `equals() should return true for equal instances with different 'sequenceNumber'`() {
    val dataConnectQueryResult1 =
      DataConnectQueryResult(data = TestData(), query = sampleQuery, sequenceNumber = 1)

    val dataConnectQueryResult2 =
      DataConnectQueryResult(data = TestData(), query = sampleQuery, sequenceNumber = 2)

    assertThat(dataConnectQueryResult1.equals(dataConnectQueryResult2)).isTrue()
  }

  @Test
  fun `equals() should return false for null`() {
    val dataConnectQueryResult =
      DataConnectQueryResult(
        data = TestData(),
        query = sampleQuery,
        sequenceNumber = sampleSequenceNumber
      )

    assertThat(dataConnectQueryResult.equals(null)).isFalse()
  }

  @Test
  fun `equals() should return false for a different type`() {
    val dataConnectQueryResult =
      DataConnectQueryResult(
        data = TestData(),
        query = sampleQuery,
        sequenceNumber = sampleSequenceNumber
      )

    assertThat(dataConnectQueryResult.equals(listOf("foo"))).isFalse()
  }

  @Test
  fun `equals() should return false when only 'data' differs`() {
    val dataConnectQueryResult1 =
      DataConnectQueryResult(
        data = TestData("foo"),
        query = sampleQuery,
        sequenceNumber = sampleSequenceNumber
      )
    val dataConnectQueryResult2 =
      DataConnectQueryResult(
        data = TestData("bar"),
        query = sampleQuery,
        sequenceNumber = sampleSequenceNumber
      )

    assertThat(dataConnectQueryResult1.equals(dataConnectQueryResult2)).isFalse()
  }

  @Test
  fun `equals() should return false when only 'query' differs`() {
    val dataConnectQueryResult1 =
      DataConnectQueryResult(
        data = TestData(),
        query = sampleQuery1,
        sequenceNumber = sampleSequenceNumber
      )
    val dataConnectQueryResult2 =
      DataConnectQueryResult(
        data = TestData(),
        query = sampleQuery2,
        sequenceNumber = sampleSequenceNumber
      )

    assertThat(dataConnectQueryResult1.equals(dataConnectQueryResult2)).isFalse()
  }

  @Test
  fun `equals() should return false when data of first object is null and second is non-null`() {
    val dataConnectQueryResult1 =
      DataConnectQueryResult(
        data = null,
        query = sampleQuery,
        sequenceNumber = sampleSequenceNumber
      )
    val dataConnectQueryResult2 =
      DataConnectQueryResult(
        data = TestData("bar"),
        query = sampleQuery,
        sequenceNumber = sampleSequenceNumber
      )

    assertThat(dataConnectQueryResult1.equals(dataConnectQueryResult2)).isFalse()
  }

  @Test
  fun `equals() should return false when data of second object is null and first is non-null`() {
    val dataConnectQueryResult1 =
      DataConnectQueryResult(
        data = TestData("bar"),
        query = sampleQuery,
        sequenceNumber = sampleSequenceNumber
      )
    val dataConnectQueryResult2 =
      DataConnectQueryResult(
        data = null,
        query = sampleQuery,
        sequenceNumber = sampleSequenceNumber
      )

    assertThat(dataConnectQueryResult1.equals(dataConnectQueryResult2)).isFalse()
  }

  @Test
  fun `hashCode() should return the same value each time it is invoked on a given object`() {
    val dataConnectQueryResult =
      DataConnectQueryResult(
        data = TestData(),
        query = sampleQuery,
        sequenceNumber = sampleSequenceNumber
      )

    val hashCode = dataConnectQueryResult.hashCode()

    assertThat(dataConnectQueryResult.hashCode()).isEqualTo(hashCode)
    assertThat(dataConnectQueryResult.hashCode()).isEqualTo(hashCode)
    assertThat(dataConnectQueryResult.hashCode()).isEqualTo(hashCode)
  }

  @Test
  fun `hashCode() should return the same value on equal objects`() {
    val dataConnectQueryResult1 =
      DataConnectQueryResult(
        data = TestData(),
        query = sampleQuery,
        sequenceNumber = sampleSequenceNumber
      )
    val dataConnectQueryResult2 =
      DataConnectQueryResult(
        data = TestData(),
        query = sampleQuery,
        sequenceNumber = sampleSequenceNumber
      )

    assertThat(dataConnectQueryResult1.hashCode()).isEqualTo(dataConnectQueryResult2.hashCode())
  }

  @Test
  fun `hashCode() should return the same value on equal objects, even if sequenceNumber differs`() {
    val dataConnectQueryResult1 =
      DataConnectQueryResult(data = TestData(), query = sampleQuery, sequenceNumber = 1)

    val dataConnectQueryResult2 =
      DataConnectQueryResult(data = TestData(), query = sampleQuery, sequenceNumber = 2)

    assertThat(dataConnectQueryResult1.hashCode()).isEqualTo(dataConnectQueryResult2.hashCode())
  }

  @Test
  fun `hashCode() should return a different value if 'data' is different`() {
    val dataConnectQueryResult1 =
      DataConnectQueryResult(
        data = TestData("foo"),
        query = sampleQuery,
        sequenceNumber = sampleSequenceNumber
      )
    val dataConnectQueryResult2 =
      DataConnectQueryResult(
        data = TestData("bar"),
        query = sampleQuery,
        sequenceNumber = sampleSequenceNumber
      )

    assertThat(dataConnectQueryResult1.hashCode()).isNotEqualTo(dataConnectQueryResult2.hashCode())
  }

  @Test
  fun `hashCode() should return a different value if 'query' is different`() {
    val dataConnectQueryResult1 =
      DataConnectQueryResult(
        data = TestData(),
        query = sampleQuery1,
        sequenceNumber = sampleSequenceNumber
      )
    val dataConnectQueryResult2 =
      DataConnectQueryResult(
        data = TestData(),
        query = sampleQuery2,
        sequenceNumber = sampleSequenceNumber
      )

    assertThat(dataConnectQueryResult1.hashCode()).isNotEqualTo(dataConnectQueryResult2.hashCode())
  }

  @Serializable data class TestVariables(val value: String = "TestVariablesDefaultValue")

  @Serializable data class TestData(val value: String = "TestDataDefaultValue")

  companion object {
    private const val sampleSequenceNumber: Long = -1
  }
}
