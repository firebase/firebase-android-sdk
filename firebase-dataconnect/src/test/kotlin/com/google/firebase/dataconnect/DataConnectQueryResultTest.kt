package com.google.firebase.dataconnect

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.google.firebase.FirebaseApp
import com.google.firebase.dataconnect.testutil.containsWithNonAdjacentText
import kotlin.random.Random
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DataConnectQueryResultTest {

  lateinit var sampleQuery: Query<TestData?, TestVariables>
  lateinit var sampleQuery1: Query<TestData?, TestVariables>
  lateinit var sampleQuery2: Query<TestData?, TestVariables>

  @Before
  fun prepareSampleQueries() {
    val context: Context = RuntimeEnvironment.getApplication()

    sampleQuery =
      Query(
        dataConnect = FirebaseDataConnect.newTestInstance(),
        operationName = "TestOperation",
        responseDeserializer = serializer(),
        variablesSerializer = serializer()
      )
    sampleQuery1 =
      Query(
        dataConnect = FirebaseDataConnect.newTestInstance(),
        operationName = "TestOperation",
        responseDeserializer = serializer(),
        variablesSerializer = serializer()
      )
    sampleQuery2 =
      Query(
        dataConnect = FirebaseDataConnect.newTestInstance(),
        operationName = "TestOperation",
        responseDeserializer = serializer(),
        variablesSerializer = serializer()
      )
  }

  @Test
  fun `variables should be the same object given to the constructor`() {
    val variables = TestVariables("boo")
    val dataConnectQueryResult =
      DataConnectQueryResult(
        data = SAMPLE_DATA,
        variables = variables,
        query = sampleQuery,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    assertThat(dataConnectQueryResult.variables).isSameInstanceAs(variables)
  }

  @Test
  fun `data should be the same object given to the constructor`() {
    val data = TestData("blah")
    val dataConnectQueryResult =
      DataConnectQueryResult(
        data = data,
        variables = SAMPLE_VARIABLES,
        query = sampleQuery,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    assertThat(dataConnectQueryResult.data).isSameInstanceAs(data)
  }

  @Test
  fun `ref should be the same object given to the constructor`() {
    val dataConnectQueryResult =
      DataConnectQueryResult(
        data = SAMPLE_DATA,
        variables = SAMPLE_VARIABLES,
        query = sampleQuery,
        sequenceNumber = 12345
      )

    assertThat(dataConnectQueryResult.ref).isSameInstanceAs(sampleQuery)
  }

  @Test
  fun `sequenceNumber should be the same object given to the constructor`() {
    val dataConnectQueryResult =
      DataConnectQueryResult(
        data = SAMPLE_DATA,
        variables = SAMPLE_VARIABLES,
        query = sampleQuery,
        sequenceNumber = 12345
      )

    assertThat(dataConnectQueryResult.sequenceNumber).isEqualTo(12345)
  }

  @Test
  fun `toString() should begin with the class name and contain text in parentheses`() {
    val dataConnectQueryResult =
      DataConnectQueryResult(
        data = SAMPLE_DATA,
        variables = SAMPLE_VARIABLES,
        query = sampleQuery,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    assertThat(dataConnectQueryResult.toString()).startsWith("DataConnectQueryResult(")
    assertThat(dataConnectQueryResult.toString()).endsWith(")")
  }

  @Test
  fun `toString() should incorporate the variables`() {
    val dataConnectQueryResult =
      DataConnectQueryResult(
        data = SAMPLE_DATA,
        variables = SpecifiedToString("TestVariablesToString"),
        query = sampleQuery.withVariablesSerializer(serializer<SpecifiedToString>()),
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    assertThat(dataConnectQueryResult.toString())
      .containsWithNonAdjacentText("TestVariablesToString")
  }

  @Test
  fun `toString() should incorporate the data`() {
    val dataConnectQueryResult =
      DataConnectQueryResult(
        data = SpecifiedToString("TestDataToString"),
        variables = SAMPLE_VARIABLES,
        query = sampleQuery.withResponseDeserializer(serializer<SpecifiedToString>()),
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    assertThat(dataConnectQueryResult.toString()).containsWithNonAdjacentText("TestDataToString")
  }

  @Test
  fun `toString() should NOT incorporate the sequenceNumber`() {
    val dataConnectQueryResult =
      DataConnectQueryResult(
        data = SAMPLE_DATA,
        variables = SAMPLE_VARIABLES,
        query = sampleQuery,
        sequenceNumber = 123456789123456789
      )
    assertThat(dataConnectQueryResult.toString()).doesNotContain("123456789123456789")
  }

  @Test
  fun `equals() should return true for the exact same instance`() {
    val dataConnectQueryResult =
      DataConnectQueryResult(
        data = SAMPLE_DATA,
        variables = SAMPLE_VARIABLES,
        query = sampleQuery,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    assertThat(dataConnectQueryResult.equals(dataConnectQueryResult)).isTrue()
  }

  @Test
  fun `equals() should return true for an equal instance`() {
    val dataConnectQueryResult1 =
      DataConnectQueryResult(
        data = SAMPLE_DATA,
        variables = SAMPLE_VARIABLES,
        query = sampleQuery,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    val dataConnectQueryResult2 =
      DataConnectQueryResult(
        data = SAMPLE_DATA,
        variables = SAMPLE_VARIABLES,
        query = sampleQuery,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    assertThat(dataConnectQueryResult1.equals(dataConnectQueryResult2)).isTrue()
  }

  @Test
  fun `equals() should return true if all properties are equal, and data is null`() {
    val dataConnectQueryResult1 =
      DataConnectQueryResult(
        data = null,
        variables = SAMPLE_VARIABLES,
        query = sampleQuery,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    val dataConnectQueryResult2 =
      DataConnectQueryResult(
        data = null,
        variables = SAMPLE_VARIABLES,
        query = sampleQuery,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    assertThat(dataConnectQueryResult1.equals(dataConnectQueryResult2)).isTrue()
  }

  @Test
  fun `equals() should return true for equal instances with different sequenceNumber`() {
    val dataConnectQueryResult1 =
      DataConnectQueryResult(
        data = SAMPLE_DATA,
        variables = SAMPLE_VARIABLES,
        query = sampleQuery,
        sequenceNumber = 1
      )

    val dataConnectQueryResult2 =
      DataConnectQueryResult(
        data = SAMPLE_DATA,
        variables = SAMPLE_VARIABLES,
        query = sampleQuery,
        sequenceNumber = 2
      )

    assertThat(dataConnectQueryResult1.equals(dataConnectQueryResult2)).isTrue()
  }

  @Test
  fun `equals() should return false for null`() {
    val dataConnectQueryResult =
      DataConnectQueryResult(
        data = SAMPLE_DATA,
        variables = SAMPLE_VARIABLES,
        query = sampleQuery,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    assertThat(dataConnectQueryResult.equals(null)).isFalse()
  }

  @Test
  fun `equals() should return false for a different type`() {
    val dataConnectQueryResult =
      DataConnectQueryResult(
        data = SAMPLE_DATA,
        variables = SAMPLE_VARIABLES,
        query = sampleQuery,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    assertThat(dataConnectQueryResult.equals(listOf("foo"))).isFalse()
  }

  @Test
  fun `equals() should return false when only data differs`() {
    val dataConnectQueryResult1 =
      DataConnectQueryResult(
        data = TestData("foo"),
        variables = SAMPLE_VARIABLES,
        query = sampleQuery,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    val dataConnectQueryResult2 =
      DataConnectQueryResult(
        data = TestData("bar"),
        variables = SAMPLE_VARIABLES,
        query = sampleQuery,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    assertThat(dataConnectQueryResult1.equals(dataConnectQueryResult2)).isFalse()
  }

  @Test
  fun `equals() should return false when only variables differs`() {
    val dataConnectQueryResult1 =
      DataConnectQueryResult(
        data = SAMPLE_DATA,
        variables = TestVariables("foo"),
        query = sampleQuery,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    val dataConnectQueryResult2 =
      DataConnectQueryResult(
        data = SAMPLE_DATA,
        variables = TestVariables("bar"),
        query = sampleQuery,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    assertThat(dataConnectQueryResult1.equals(dataConnectQueryResult2)).isFalse()
  }

  @Test
  fun `equals() should return false when only query differs`() {
    val dataConnectQueryResult1 =
      DataConnectQueryResult(
        data = SAMPLE_DATA,
        variables = SAMPLE_VARIABLES,
        query = sampleQuery1,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    val dataConnectQueryResult2 =
      DataConnectQueryResult(
        data = SAMPLE_DATA,
        variables = SAMPLE_VARIABLES,
        query = sampleQuery2,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    assertThat(dataConnectQueryResult1.equals(dataConnectQueryResult2)).isFalse()
  }

  @Test
  fun `equals() should return false when data of first object is null`() {
    val dataConnectQueryResult1 =
      DataConnectQueryResult(
        variables = SAMPLE_VARIABLES,
        data = null,
        query = sampleQuery,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    val dataConnectQueryResult2 =
      DataConnectQueryResult(
        data = TestData("bar"),
        variables = SAMPLE_VARIABLES,
        query = sampleQuery,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    assertThat(dataConnectQueryResult1.equals(dataConnectQueryResult2)).isFalse()
  }

  @Test
  fun `equals() should return false when data of second object is null`() {
    val dataConnectQueryResult1 =
      DataConnectQueryResult(
        data = TestData("foo"),
        variables = SAMPLE_VARIABLES,
        query = sampleQuery,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    val dataConnectQueryResult2 =
      DataConnectQueryResult(
        data = null,
        variables = SAMPLE_VARIABLES,
        query = sampleQuery,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    assertThat(dataConnectQueryResult1.equals(dataConnectQueryResult2)).isFalse()
  }

  @Test
  fun `hashCode() should return the same value each time it is invoked on a given object`() {
    val dataConnectQueryResult =
      DataConnectQueryResult(
        data = SAMPLE_DATA,
        variables = SAMPLE_VARIABLES,
        query = sampleQuery,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
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
        data = SAMPLE_DATA,
        variables = SAMPLE_VARIABLES,
        query = sampleQuery,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    val dataConnectQueryResult2 =
      DataConnectQueryResult(
        data = SAMPLE_DATA,
        variables = SAMPLE_VARIABLES,
        query = sampleQuery,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    assertThat(dataConnectQueryResult1.hashCode()).isEqualTo(dataConnectQueryResult2.hashCode())
  }

  @Test
  fun `hashCode() should return the same value on equal objects, even if sequenceNumber differs`() {
    val dataConnectQueryResult1 =
      DataConnectQueryResult(
        data = SAMPLE_DATA,
        variables = SAMPLE_VARIABLES,
        query = sampleQuery,
        sequenceNumber = 1
      )

    val dataConnectQueryResult2 =
      DataConnectQueryResult(
        variables = SAMPLE_VARIABLES,
        data = SAMPLE_DATA,
        query = sampleQuery,
        sequenceNumber = 2
      )

    assertThat(dataConnectQueryResult1.hashCode()).isEqualTo(dataConnectQueryResult2.hashCode())
  }

  @Test
  fun `hashCode() should return a different value if variables is different`() {
    val dataConnectQueryResult1 =
      DataConnectQueryResult(
        variables = TestVariables("foo"),
        data = SAMPLE_DATA,
        query = sampleQuery,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    val dataConnectQueryResult2 =
      DataConnectQueryResult(
        variables = TestVariables("bar"),
        data = SAMPLE_DATA,
        query = sampleQuery,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    assertThat(dataConnectQueryResult1.hashCode()).isNotEqualTo(dataConnectQueryResult2.hashCode())
  }

  @Test
  fun `hashCode() should return a different value if data is different`() {
    val dataConnectQueryResult1 =
      DataConnectQueryResult(
        variables = SAMPLE_VARIABLES,
        data = TestData("foo"),
        query = sampleQuery,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    val dataConnectQueryResult2 =
      DataConnectQueryResult(
        variables = SAMPLE_VARIABLES,
        data = TestData("bar"),
        query = sampleQuery,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    assertThat(dataConnectQueryResult1.hashCode()).isNotEqualTo(dataConnectQueryResult2.hashCode())
  }

  @Test
  fun `hashCode() should return a different value if query is different`() {
    val dataConnectQueryResult1 =
      DataConnectQueryResult(
        variables = SAMPLE_VARIABLES,
        data = TestData("foo"),
        query = sampleQuery1,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    val dataConnectQueryResult2 =
      DataConnectQueryResult(
        variables = SAMPLE_VARIABLES,
        data = TestData("bar"),
        query = sampleQuery2,
        sequenceNumber = SAMPLE_SEQUENCE_NUMBER
      )
    assertThat(dataConnectQueryResult1.hashCode()).isNotEqualTo(dataConnectQueryResult2.hashCode())
  }

  @Serializable data class TestVariables(val value: String)

  private val SAMPLE_VARIABLES = TestVariables("foo")

  @Serializable data class TestData(val value: String)

  private val SAMPLE_DATA = TestData("bar")

  private val SAMPLE_SEQUENCE_NUMBER: Long = -1

  @Serializable
  class SpecifiedToString(private val toStringReturnValue: String) {
    override fun toString() = toStringReturnValue
  }

  private fun FirebaseDataConnect.Companion.newTestInstance(): FirebaseDataConnect {
    val context: Context = RuntimeEnvironment.getApplication()
    val firebaseApp = mock(FirebaseApp::class.java)
    val blockingExecutor = MoreExecutors.directExecutor()
    val nonBlockingExecutor = MoreExecutors.directExecutor()

    return FirebaseDataConnect(
      context = context,
      app = firebaseApp,
      projectId = Random.nextAlphanumericString(),
      config =
        ConnectorConfig(
          connector = "Connector" + Random.nextAlphanumericString(),
          location = "Location" + Random.nextAlphanumericString(),
          service = "Service" + Random.nextAlphanumericString(),
        ),
      blockingExecutor = blockingExecutor,
      nonBlockingExecutor = nonBlockingExecutor,
      creator =
        FirebaseDataConnectFactory(
          context = context,
          firebaseApp = firebaseApp,
          blockingExecutor = blockingExecutor,
          nonBlockingExecutor = nonBlockingExecutor,
        ),
      settings = DataConnectSettings(host = Random.nextAlphanumericString())
    )
  }
}
