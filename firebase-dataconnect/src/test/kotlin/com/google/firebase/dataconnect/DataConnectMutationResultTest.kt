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
class DataConnectMutationResultTest {

  private lateinit var sampleMutation: MutationRef<TestData?, TestVariables>
  private lateinit var sampleMutation1: MutationRef<TestData?, TestVariables>
  private lateinit var sampleMutation2: MutationRef<TestData?, TestVariables>

  @Before
  fun prepareSampleQueries() {
    sampleMutation =
      MutationRef(
        dataConnect = FirebaseDataConnect.newTestInstance(),
        operationName = "SampleMutationOperation",
        variables = TestVariables("SampleMutationVariables"),
        responseDeserializer = serializer(),
        variablesSerializer = serializer()
      )
    sampleMutation1 =
      MutationRef(
        dataConnect = FirebaseDataConnect.newTestInstance(),
        operationName = "SampleMutation1Operation",
        variables = TestVariables("SampleMutation1Variables"),
        responseDeserializer = serializer(),
        variablesSerializer = serializer()
      )
    sampleMutation2 =
      MutationRef(
        dataConnect = FirebaseDataConnect.newTestInstance(),
        operationName = "SampleMutation2Operation",
        variables = TestVariables("SampleMutation2Variables"),
        responseDeserializer = serializer(),
        variablesSerializer = serializer()
      )
  }

  @Test
  fun `'data' should be the same object given to the constructor`() {
    val data = TestData("blah")
    val dataConnectMutationResult =
      DataConnectMutationResult(
        data = data,
        mutation = sampleMutation,
        sequenceNumber = sampleSequenceNumber
      )

    assertThat(dataConnectMutationResult.data).isSameInstanceAs(data)
  }

  @Test
  fun `'ref' should be the same object given to the constructor for the 'mutation' argument`() {
    val dataConnectMutationResult =
      DataConnectMutationResult(
        data = TestData(),
        mutation = sampleMutation,
        sequenceNumber = sampleSequenceNumber
      )

    assertThat(dataConnectMutationResult.ref).isSameInstanceAs(sampleMutation)
  }

  @Test
  fun `sequenceNumber should be the same value given to the constructor`() {
    val dataConnectMutationResult =
      DataConnectMutationResult(
        data = TestData(),
        mutation = sampleMutation,
        sequenceNumber = 12345
      )

    assertThat(dataConnectMutationResult.sequenceNumber).isEqualTo(12345)
  }

  @Test
  fun `toString() should begin with the class name and contain text in parentheses`() {
    val dataConnectMutationResult =
      DataConnectMutationResult(
        data = TestData(),
        mutation = sampleMutation,
        sequenceNumber = sampleSequenceNumber
      )

    assertThat(dataConnectMutationResult.toString()).startsWith("DataConnectMutationResult(")
    assertThat(dataConnectMutationResult.toString()).endsWith(")")
  }

  @Test
  fun `toString() should incorporate 'data'`() {
    val data = TestData()
    val dataConnectMutationResult =
      DataConnectMutationResult(
        data = data,
        mutation = sampleMutation,
        sequenceNumber = sampleSequenceNumber
      )

    assertThat(dataConnectMutationResult.toString()).containsWithNonAdjacentText("data=$data")
  }

  @Test
  fun `toString() should incorporate 'ref'`() {
    val dataConnectMutationResult =
      DataConnectMutationResult(
        data = TestData(),
        mutation = sampleMutation,
        sequenceNumber = sampleSequenceNumber
      )

    assertThat(dataConnectMutationResult.toString())
      .containsWithNonAdjacentText("mutation=$sampleMutation")
  }

  @Test
  fun `toString() should NOT incorporate the sequenceNumber`() {
    val dataConnectMutationResult =
      DataConnectMutationResult(
        data = TestData(),
        mutation = sampleMutation,
        sequenceNumber = 123456789123456789
      )

    assertThat(dataConnectMutationResult.toString()).doesNotContain("123456789123456789")
  }

  @Test
  fun `equals() should return true for the exact same instance`() {
    val dataConnectMutationResult =
      DataConnectMutationResult(
        data = TestData(),
        mutation = sampleMutation,
        sequenceNumber = sampleSequenceNumber
      )

    assertThat(dataConnectMutationResult.equals(dataConnectMutationResult)).isTrue()
  }

  @Test
  fun `equals() should return true for an equal instance`() {
    val dataConnectMutationResult1 =
      DataConnectMutationResult(
        data = TestData(),
        mutation = sampleMutation,
        sequenceNumber = sampleSequenceNumber
      )
    val dataConnectMutationResult2 =
      DataConnectMutationResult(
        data = TestData(),
        mutation = sampleMutation,
        sequenceNumber = sampleSequenceNumber
      )

    assertThat(dataConnectMutationResult1.equals(dataConnectMutationResult2)).isTrue()
  }

  @Test
  fun `equals() should return true if all properties are equal, and 'data' is null`() {
    val dataConnectMutationResult1 =
      DataConnectMutationResult(
        data = null,
        mutation = sampleMutation,
        sequenceNumber = sampleSequenceNumber
      )
    val dataConnectMutationResult2 =
      DataConnectMutationResult(
        data = null,
        mutation = sampleMutation,
        sequenceNumber = sampleSequenceNumber
      )

    assertThat(dataConnectMutationResult1.equals(dataConnectMutationResult2)).isTrue()
  }

  @Test
  fun `equals() should return true for equal instances with different 'sequenceNumber'`() {
    val dataConnectMutationResult1 =
      DataConnectMutationResult(data = TestData(), mutation = sampleMutation, sequenceNumber = 1)

    val dataConnectMutationResult2 =
      DataConnectMutationResult(data = TestData(), mutation = sampleMutation, sequenceNumber = 2)

    assertThat(dataConnectMutationResult1.equals(dataConnectMutationResult2)).isTrue()
  }

  @Test
  fun `equals() should return false for null`() {
    val dataConnectMutationResult =
      DataConnectMutationResult(
        data = TestData(),
        mutation = sampleMutation,
        sequenceNumber = sampleSequenceNumber
      )

    assertThat(dataConnectMutationResult.equals(null)).isFalse()
  }

  @Test
  fun `equals() should return false for a different type`() {
    val dataConnectMutationResult =
      DataConnectMutationResult(
        data = TestData(),
        mutation = sampleMutation,
        sequenceNumber = sampleSequenceNumber
      )

    assertThat(dataConnectMutationResult.equals(listOf("foo"))).isFalse()
  }

  @Test
  fun `equals() should return false when only 'data' differs`() {
    val dataConnectMutationResult1 =
      DataConnectMutationResult(
        data = TestData("foo"),
        mutation = sampleMutation,
        sequenceNumber = sampleSequenceNumber
      )
    val dataConnectMutationResult2 =
      DataConnectMutationResult(
        data = TestData("bar"),
        mutation = sampleMutation,
        sequenceNumber = sampleSequenceNumber
      )

    assertThat(dataConnectMutationResult1.equals(dataConnectMutationResult2)).isFalse()
  }

  @Test
  fun `equals() should return false when only 'mutation' differs`() {
    val dataConnectMutationResult1 =
      DataConnectMutationResult(
        data = TestData(),
        mutation = sampleMutation1,
        sequenceNumber = sampleSequenceNumber
      )
    val dataConnectMutationResult2 =
      DataConnectMutationResult(
        data = TestData(),
        mutation = sampleMutation2,
        sequenceNumber = sampleSequenceNumber
      )

    assertThat(dataConnectMutationResult1.equals(dataConnectMutationResult2)).isFalse()
  }

  @Test
  fun `equals() should return false when data of first object is null and second is non-null`() {
    val dataConnectMutationResult1 =
      DataConnectMutationResult(
        data = null,
        mutation = sampleMutation,
        sequenceNumber = sampleSequenceNumber
      )
    val dataConnectMutationResult2 =
      DataConnectMutationResult(
        data = TestData("bar"),
        mutation = sampleMutation,
        sequenceNumber = sampleSequenceNumber
      )

    assertThat(dataConnectMutationResult1.equals(dataConnectMutationResult2)).isFalse()
  }

  @Test
  fun `equals() should return false when data of second object is null and first is non-null`() {
    val dataConnectMutationResult1 =
      DataConnectMutationResult(
        data = TestData("bar"),
        mutation = sampleMutation,
        sequenceNumber = sampleSequenceNumber
      )
    val dataConnectMutationResult2 =
      DataConnectMutationResult(
        data = null,
        mutation = sampleMutation,
        sequenceNumber = sampleSequenceNumber
      )

    assertThat(dataConnectMutationResult1.equals(dataConnectMutationResult2)).isFalse()
  }

  @Test
  fun `hashCode() should return the same value each time it is invoked on a given object`() {
    val dataConnectMutationResult =
      DataConnectMutationResult(
        data = TestData(),
        mutation = sampleMutation,
        sequenceNumber = sampleSequenceNumber
      )

    val hashCode = dataConnectMutationResult.hashCode()

    assertThat(dataConnectMutationResult.hashCode()).isEqualTo(hashCode)
    assertThat(dataConnectMutationResult.hashCode()).isEqualTo(hashCode)
    assertThat(dataConnectMutationResult.hashCode()).isEqualTo(hashCode)
  }

  @Test
  fun `hashCode() should return the same value on equal objects`() {
    val dataConnectMutationResult1 =
      DataConnectMutationResult(
        data = TestData(),
        mutation = sampleMutation,
        sequenceNumber = sampleSequenceNumber
      )
    val dataConnectMutationResult2 =
      DataConnectMutationResult(
        data = TestData(),
        mutation = sampleMutation,
        sequenceNumber = sampleSequenceNumber
      )

    assertThat(dataConnectMutationResult1.hashCode())
      .isEqualTo(dataConnectMutationResult2.hashCode())
  }

  @Test
  fun `hashCode() should return the same value on equal objects, even if sequenceNumber differs`() {
    val dataConnectMutationResult1 =
      DataConnectMutationResult(data = TestData(), mutation = sampleMutation, sequenceNumber = 1)

    val dataConnectMutationResult2 =
      DataConnectMutationResult(data = TestData(), mutation = sampleMutation, sequenceNumber = 2)

    assertThat(dataConnectMutationResult1.hashCode())
      .isEqualTo(dataConnectMutationResult2.hashCode())
  }

  @Test
  fun `hashCode() should return a different value if 'data' is different`() {
    val dataConnectMutationResult1 =
      DataConnectMutationResult(
        data = TestData("foo"),
        mutation = sampleMutation,
        sequenceNumber = sampleSequenceNumber
      )
    val dataConnectMutationResult2 =
      DataConnectMutationResult(
        data = TestData("bar"),
        mutation = sampleMutation,
        sequenceNumber = sampleSequenceNumber
      )

    assertThat(dataConnectMutationResult1.hashCode())
      .isNotEqualTo(dataConnectMutationResult2.hashCode())
  }

  @Test
  fun `hashCode() should return a different value if 'mutation' is different`() {
    val dataConnectMutationResult1 =
      DataConnectMutationResult(
        data = TestData(),
        mutation = sampleMutation1,
        sequenceNumber = sampleSequenceNumber
      )
    val dataConnectMutationResult2 =
      DataConnectMutationResult(
        data = TestData(),
        mutation = sampleMutation2,
        sequenceNumber = sampleSequenceNumber
      )

    assertThat(dataConnectMutationResult1.hashCode())
      .isNotEqualTo(dataConnectMutationResult2.hashCode())
  }

  @Serializable data class TestVariables(val value: String = "TestVariablesDefaultValue")

  @Serializable data class TestData(val value: String = "TestDataDefaultValue")

  companion object {
    private const val sampleSequenceNumber: Long = -1
  }
}
