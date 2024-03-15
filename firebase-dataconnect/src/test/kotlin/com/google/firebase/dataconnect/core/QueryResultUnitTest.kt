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
class QueryResultImplUnitTest {

  @Mock(name = "mockFirebaseDataConnectInternal", stubOnly = true)
  private lateinit var mockFirebaseDataConnectInternal: FirebaseDataConnectInternal
  @Mock(name = "mockDataDeserializer", stubOnly = true)
  private lateinit var mockDataDeserializer: DeserializationStrategy<TestData?>
  @Mock(name = "mockVariablesSerializer", stubOnly = true)
  private lateinit var mockVariablesSerializer: SerializationStrategy<TestVariables>

  private lateinit var sampleQuery: QueryRefImpl<TestData?, TestVariables>
  private lateinit var sampleQuery1: QueryRefImpl<TestData?, TestVariables>
  private lateinit var sampleQuery2: QueryRefImpl<TestData?, TestVariables>

  @Before
  fun prepareSampleQuerys() {
    MockitoAnnotations.initMocks(this)

    sampleQuery =
      QueryRefImpl(
        dataConnect = mockFirebaseDataConnectInternal,
        operationName = "sampleQueryOperationName",
        variables = TestVariables("sampleQueryTestData"),
        dataDeserializer = mockDataDeserializer,
        variablesSerializer = mockVariablesSerializer,
      )
    sampleQuery1 =
      QueryRefImpl(
        dataConnect = mockFirebaseDataConnectInternal,
        operationName = "sampleQueryOperationName1",
        variables = TestVariables("sampleQueryTestData1"),
        dataDeserializer = mockDataDeserializer,
        variablesSerializer = mockVariablesSerializer,
      )
    sampleQuery2 =
      QueryRefImpl(
        dataConnect = mockFirebaseDataConnectInternal,
        operationName = "sampleQueryOperationName2",
        variables = TestVariables("sampleQueryTestData2"),
        dataDeserializer = mockDataDeserializer,
        variablesSerializer = mockVariablesSerializer,
      )
  }

  @Test
  fun `'data' should be the same object given to the constructor`() {
    val data = TestData()
    val queryResult = sampleQuery.QueryResultImpl(data)

    assertThat(queryResult.data).isSameInstanceAs(data)
  }

  @Test
  fun `'ref' should be the QueryRefImpl object that was used to create it`() {
    val queryResult = sampleQuery.QueryResultImpl(TestData())

    assertThat(queryResult.ref).isSameInstanceAs(sampleQuery)
  }

  @Test
  fun `toString() should begin with the class name and contain text in parentheses`() {
    val queryResult = sampleQuery.QueryResultImpl(TestData())

    assertThat(queryResult.toString()).startsWith("QueryResultImpl(")
    assertThat(queryResult.toString()).endsWith(")")
  }

  @Test
  fun `toString() should incorporate 'data'`() {
    val data = TestData()
    val queryResult = sampleQuery.QueryResultImpl(data)

    assertThat(queryResult.toString()).containsWithNonAdjacentText("data=$data")
  }

  @Test
  fun `toString() should incorporate 'ref'`() {
    val queryResult = sampleQuery.QueryResultImpl(TestData())

    assertThat(queryResult.toString()).containsWithNonAdjacentText("ref=$sampleQuery")
  }

  @Test
  fun `equals() should return true for the exact same instance`() {
    val queryResult = sampleQuery.QueryResultImpl(TestData())

    assertThat(queryResult.equals(queryResult)).isTrue()
  }

  @Test
  fun `equals() should return true for an equal instance`() {
    val queryResult1 = sampleQuery.QueryResultImpl(TestData())
    val queryResult2 = sampleQuery.QueryResultImpl(TestData())

    assertThat(queryResult1.equals(queryResult2)).isTrue()
  }

  @Test
  fun `equals() should return true if all properties are equal, and 'data' is null`() {
    val queryResult1 = sampleQuery.QueryResultImpl(null)
    val queryResult2 = sampleQuery.QueryResultImpl(null)

    assertThat(queryResult1.equals(queryResult2)).isTrue()
  }

  @Test
  fun `equals() should return false for null`() {
    val queryResult = sampleQuery.QueryResultImpl(TestData())

    assertThat(queryResult.equals(null)).isFalse()
  }

  @Test
  fun `equals() should return false for a different type`() {
    val queryResult = sampleQuery.QueryResultImpl(TestData())

    assertThat(queryResult.equals(listOf("foo"))).isFalse()
  }

  @Test
  fun `equals() should return false when only 'data' differs`() {
    val queryResult1 = sampleQuery.QueryResultImpl(TestData("foo"))
    val queryResult2 = sampleQuery.QueryResultImpl(TestData("bar"))

    assertThat(queryResult1.equals(queryResult2)).isFalse()
  }

  @Test
  fun `equals() should return false when only 'ref' differs`() {
    val queryResult1 = sampleQuery1.QueryResultImpl(TestData())
    val queryResult2 = sampleQuery2.QueryResultImpl(TestData())

    assertThat(queryResult1.equals(queryResult2)).isFalse()
  }

  @Test
  fun `equals() should return false when data of first object is null and second is non-null`() {
    val queryResult1 = sampleQuery.QueryResultImpl(null)
    val queryResult2 = sampleQuery.QueryResultImpl(TestData("bar"))

    assertThat(queryResult1.equals(queryResult2)).isFalse()
  }

  @Test
  fun `equals() should return false when data of second object is null and first is non-null`() {
    val queryResult1 = sampleQuery.QueryResultImpl(TestData("bar"))
    val queryResult2 = sampleQuery.QueryResultImpl(null)

    assertThat(queryResult1.equals(queryResult2)).isFalse()
  }

  @Test
  fun `hashCode() should return the same value each time it is invoked on a given object`() {
    val queryResult = sampleQuery.QueryResultImpl(TestData())

    val hashCode = queryResult.hashCode()

    assertThat(queryResult.hashCode()).isEqualTo(hashCode)
    assertThat(queryResult.hashCode()).isEqualTo(hashCode)
    assertThat(queryResult.hashCode()).isEqualTo(hashCode)
  }

  @Test
  fun `hashCode() should return the same value on equal objects`() {
    val queryResult1 = sampleQuery.QueryResultImpl(TestData())
    val queryResult2 = sampleQuery.QueryResultImpl(TestData())

    assertThat(queryResult1.hashCode()).isEqualTo(queryResult2.hashCode())
  }

  @Test
  fun `hashCode() should return a different value if 'data' is different`() {
    val queryResult1 = sampleQuery.QueryResultImpl(TestData("foo"))
    val queryResult2 = sampleQuery.QueryResultImpl(TestData("bar"))

    assertThat(queryResult1.hashCode()).isNotEqualTo(queryResult2.hashCode())
  }

  @Test
  fun `hashCode() should return a different value if 'ref' is different`() {
    val queryResult1 = sampleQuery1.QueryResultImpl(TestData())
    val queryResult2 = sampleQuery2.QueryResultImpl(TestData())

    assertThat(queryResult1.hashCode()).isNotEqualTo(queryResult2.hashCode())
  }

  data class TestVariables(val value: String = "TestVariablesDefaultValue")

  data class TestData(val value: String = "TestDataDefaultValue")
}
