package com.google.firebase.dataconnect.core

import com.google.common.truth.Truth.assertThat
import com.google.firebase.dataconnect.testutil.containsWithNonAdjacentText
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

@Suppress("ReplaceCallWithBinaryOperator")
class MutationResultImplUnitTest {

  @Mock(name = "mockFirebaseDataConnectInternal", stubOnly = true)
  private lateinit var mockFirebaseDataConnectInternal: FirebaseDataConnectInternal
  @Mock(name = "mockDataDeserializer", stubOnly = true)
  private lateinit var mockDataDeserializer: DeserializationStrategy<TestData?>
  @Mock(name = "mockVariablesSerializer", stubOnly = true)
  private lateinit var mockVariablesSerializer: SerializationStrategy<TestVariables>

  private lateinit var sampleMutation: MutationRefImpl<TestData?, TestVariables>
  private lateinit var sampleMutation1: MutationRefImpl<TestData?, TestVariables>
  private lateinit var sampleMutation2: MutationRefImpl<TestData?, TestVariables>

  @Before
  fun prepareSampleMutations() {
    MockitoAnnotations.initMocks(this)

    sampleMutation =
      MutationRefImpl(
        dataConnect = mockFirebaseDataConnectInternal,
        operationName = "sampleMutationOperationName",
        variables = TestVariables("sampleMutationTestData"),
        dataDeserializer = mockDataDeserializer,
        variablesSerializer = mockVariablesSerializer,
      )
    sampleMutation1 =
      MutationRefImpl(
        dataConnect = mockFirebaseDataConnectInternal,
        operationName = "sampleMutationOperationName1",
        variables = TestVariables("sampleMutationTestData1"),
        dataDeserializer = mockDataDeserializer,
        variablesSerializer = mockVariablesSerializer,
      )
    sampleMutation2 =
      MutationRefImpl(
        dataConnect = mockFirebaseDataConnectInternal,
        operationName = "sampleMutationOperationName2",
        variables = TestVariables("sampleMutationTestData2"),
        dataDeserializer = mockDataDeserializer,
        variablesSerializer = mockVariablesSerializer,
      )
  }

  @Test
  fun `'data' should be the same object given to the constructor`() {
    val data = TestData()
    val mutationResult = sampleMutation.MutationResultImpl(data)

    assertThat(mutationResult.data).isSameInstanceAs(data)
  }

  @Test
  fun `'ref' should be the MutationRefImpl object that was used to create it`() {
    val mutationResult = sampleMutation.MutationResultImpl(TestData())

    assertThat(mutationResult.ref).isSameInstanceAs(sampleMutation)
  }

  @Test
  fun `toString() should begin with the class name and contain text in parentheses`() {
    val mutationResult = sampleMutation.MutationResultImpl(TestData())

    assertThat(mutationResult.toString()).startsWith("MutationResultImpl(")
    assertThat(mutationResult.toString()).endsWith(")")
  }

  @Test
  fun `toString() should incorporate 'data'`() {
    val data = TestData()
    val mutationResult = sampleMutation.MutationResultImpl(data)

    assertThat(mutationResult.toString()).containsWithNonAdjacentText("data=$data")
  }

  @Test
  fun `toString() should incorporate 'ref'`() {
    val mutationResult = sampleMutation.MutationResultImpl(TestData())

    assertThat(mutationResult.toString()).containsWithNonAdjacentText("ref=$sampleMutation")
  }

  @Test
  fun `equals() should return true for the exact same instance`() {
    val mutationResult = sampleMutation.MutationResultImpl(TestData())

    assertThat(mutationResult.equals(mutationResult)).isTrue()
  }

  @Test
  fun `equals() should return true for an equal instance`() {
    val mutationResult1 = sampleMutation.MutationResultImpl(TestData())
    val mutationResult2 = sampleMutation.MutationResultImpl(TestData())

    assertThat(mutationResult1.equals(mutationResult2)).isTrue()
  }

  @Test
  fun `equals() should return true if all properties are equal, and 'data' is null`() {
    val mutationResult1 = sampleMutation.MutationResultImpl(null)
    val mutationResult2 = sampleMutation.MutationResultImpl(null)

    assertThat(mutationResult1.equals(mutationResult2)).isTrue()
  }

  @Test
  fun `equals() should return false for null`() {
    val mutationResult = sampleMutation.MutationResultImpl(TestData())

    assertThat(mutationResult.equals(null)).isFalse()
  }

  @Test
  fun `equals() should return false for a different type`() {
    val mutationResult = sampleMutation.MutationResultImpl(TestData())

    assertThat(mutationResult.equals(listOf("foo"))).isFalse()
  }

  @Test
  fun `equals() should return false when only 'data' differs`() {
    val mutationResult1 = sampleMutation.MutationResultImpl(TestData("foo"))
    val mutationResult2 = sampleMutation.MutationResultImpl(TestData("bar"))

    assertThat(mutationResult1.equals(mutationResult2)).isFalse()
  }

  @Test
  fun `equals() should return false when only 'ref' differs`() {
    val mutationResult1 = sampleMutation1.MutationResultImpl(TestData())
    val mutationResult2 = sampleMutation2.MutationResultImpl(TestData())

    assertThat(mutationResult1.equals(mutationResult2)).isFalse()
  }

  @Test
  fun `equals() should return false when data of first object is null and second is non-null`() {
    val mutationResult1 = sampleMutation.MutationResultImpl(null)
    val mutationResult2 = sampleMutation.MutationResultImpl(TestData("bar"))

    assertThat(mutationResult1.equals(mutationResult2)).isFalse()
  }

  @Test
  fun `equals() should return false when data of second object is null and first is non-null`() {
    val mutationResult1 = sampleMutation.MutationResultImpl(TestData("bar"))
    val mutationResult2 = sampleMutation.MutationResultImpl(null)

    assertThat(mutationResult1.equals(mutationResult2)).isFalse()
  }

  @Test
  fun `hashCode() should return the same value each time it is invoked on a given object`() {
    val mutationResult = sampleMutation.MutationResultImpl(TestData())

    val hashCode = mutationResult.hashCode()

    assertThat(mutationResult.hashCode()).isEqualTo(hashCode)
    assertThat(mutationResult.hashCode()).isEqualTo(hashCode)
    assertThat(mutationResult.hashCode()).isEqualTo(hashCode)
  }

  @Test
  fun `hashCode() should return the same value on equal objects`() {
    val mutationResult1 = sampleMutation.MutationResultImpl(TestData())
    val mutationResult2 = sampleMutation.MutationResultImpl(TestData())

    assertThat(mutationResult1.hashCode()).isEqualTo(mutationResult2.hashCode())
  }

  @Test
  fun `hashCode() should return a different value if 'data' is different`() {
    val mutationResult1 = sampleMutation.MutationResultImpl(TestData("foo"))
    val mutationResult2 = sampleMutation.MutationResultImpl(TestData("bar"))

    assertThat(mutationResult1.hashCode()).isNotEqualTo(mutationResult2.hashCode())
  }

  @Test
  fun `hashCode() should return a different value if 'ref' is different`() {
    val mutationResult1 = sampleMutation1.MutationResultImpl(TestData())
    val mutationResult2 = sampleMutation2.MutationResultImpl(TestData())

    assertThat(mutationResult1.hashCode()).isNotEqualTo(mutationResult2.hashCode())
  }

  data class TestVariables(val value: String = "TestVariablesDefaultValue")

  data class TestData(val value: String = "TestDataDefaultValue")
}
