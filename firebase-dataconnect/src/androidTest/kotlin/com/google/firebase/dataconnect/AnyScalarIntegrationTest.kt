/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(ExperimentalKotest::class)
@file:UseSerializers(UUIDSerializer::class)

package com.google.firebase.dataconnect

import com.google.firebase.dataconnect.serializers.UUIDSerializer
import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import com.google.firebase.dataconnect.testutil.expectedAnyScalarRoundTripValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.EdgeCases
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.filterNotAnyScalarMatching
import com.google.firebase.dataconnect.testutil.property.arbitrary.filterNotIncludesAllMatchingAnyScalars
import com.google.firebase.dataconnect.testutil.property.arbitrary.filterNotNull
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.orNull
import io.kotest.property.checkAll
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.serializer
import org.junit.Test

class AnyScalarIntegrationTest : DataConnectIntegrationTestBase() {

  private val dataConnect: FirebaseDataConnect by lazy {
    val connectorConfig = testConnectorConfig.copy(connector = "demo")
    dataConnectFactory.newInstance(connectorConfig)
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for inserting into and querying this table:
  // type AnyScalarNonNullable @table { value: Any!, tag: String, position: Int }
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun anyScalarNonNullable_MutationVariableEdgeCases() =
    runTest(timeout = 60.seconds) {
      assertSoftly {
        for (value in EdgeCases.anyScalar.all.filterNotNull()) {
          withClue("value=$value") {
            verifyAnyScalarRoundTrip(
              value,
              insertMutationName = "AnyScalarNonNullableInsert",
              getByKeyQueryName = "AnyScalarNonNullableGetByKey",
            )
          }
        }
      }
    }

  @Test
  fun anyScalarNonNullable_QueryVariableEdgeCases() =
    runTest(timeout = 60.seconds) {
      assertSoftly {
        for (value in EdgeCases.anyScalar.all.filterNotNull()) {
          val otherValues =
            Arb.dataConnect.anyScalar.any().filterNotNull().filterNotAnyScalarMatching(value)
          withClue("value=$value") {
            verifyAnyScalarQueryVariable(
              value,
              otherValues.next(),
              otherValues.next(),
              insert3MutationName = "AnyScalarNonNullableInsert3",
              getAllByTagAndValueQueryName = "AnyScalarNonNullableGetAllByTagAndValue"
            )
          }
        }
      }
    }

  @Test
  fun anyScalarNonNullable_MutationVariableNormalCases() =
    runTest(timeout = 60.seconds) {
      checkAll(normalCasePropTestConfig, Arb.dataConnect.anyScalar.any().filterNotNull()) { value ->
        verifyAnyScalarRoundTrip(
          value,
          insertMutationName = "AnyScalarNonNullableInsert",
          getByKeyQueryName = "AnyScalarNonNullableGetByKey",
        )
      }
    }

  @Test
  fun anyScalarNonNullable_QueryVariableNormalCases() =
    runTest(timeout = 60.seconds) {
      checkAll(normalCasePropTestConfig, Arb.dataConnect.anyScalar.any().filterNotNull()) { value ->
        val otherValues =
          Arb.dataConnect.anyScalar.any().filterNotNull().filterNotAnyScalarMatching(value)
        verifyAnyScalarQueryVariable(
          value,
          otherValues.next(),
          otherValues.next(),
          insert3MutationName = "AnyScalarNonNullableInsert3",
          getAllByTagAndValueQueryName = "AnyScalarNonNullableGetAllByTagAndValue"
        )
      }
    }

  @Test
  fun anyScalarNonNullable_MutationFailsIfAnyVariableIsMissing() = runTest {
    verifyMutationWithMissingAnyVariableFails("AnyScalarNonNullableInsert")
  }

  @Test
  fun anyScalarNonNullable_QueryFailsIfAnyVariableIsMissing() = runTest {
    verifyQueryWithMissingAnyVariableFails("AnyScalarNonNullableGetAllByTagAndValue")
  }

  @Test
  fun anyScalarNonNullable_MutationFailsIfAnyVariableIsNull() = runTest {
    verifyMutationWithNullAnyVariableFails("AnyScalarNonNullableInsert")
  }

  @Test
  fun anyScalarNonNullable_QueryFailsIfAnyVariableIsNull() = runTest {
    verifyQueryWithNullAnyVariableFails("AnyScalarNonNullableGetAllByTagAndValue")
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for inserting into and querying this table:
  // type AnyScalarNullable @table { value: Any, tag: String, position: Int }
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun anyScalarNullable_MutationVariableEdgeCases() =
    runTest(timeout = 60.seconds) {
      assertSoftly {
        for (value in EdgeCases.anyScalar.all) {
          withClue("value=$value") {
            verifyAnyScalarRoundTrip(
              value,
              insertMutationName = "AnyScalarNullableInsert",
              getByKeyQueryName = "AnyScalarNullableGetByKey",
            )
          }
        }
      }
    }

  @Test
  fun anyScalarNullable_QueryVariableEdgeCases() =
    runTest(timeout = 60.seconds) {
      assertSoftly {
        for (value in EdgeCases.anyScalar.all) {
          val otherValues = Arb.dataConnect.anyScalar.any().filterNotAnyScalarMatching(value)
          withClue("value=$value") {
            verifyAnyScalarQueryVariable(
              value,
              otherValues.next(),
              otherValues.next(),
              insert3MutationName = "AnyScalarNullableInsert3",
              getAllByTagAndValueQueryName = "AnyScalarNullableGetAllByTagAndValue"
            )
          }
        }
      }
    }

  @Test
  fun anyScalarNullable_MutationVariableNormalCases() =
    runTest(timeout = 60.seconds) {
      checkAll(normalCasePropTestConfig, Arb.dataConnect.anyScalar.any()) { value ->
        verifyAnyScalarRoundTrip(
          value,
          insertMutationName = "AnyScalarNullableInsert",
          getByKeyQueryName = "AnyScalarNullableGetByKey",
        )
      }
    }

  @Test
  fun anyScalarNullable_QueryVariableNormalCases() =
    runTest(timeout = 60.seconds) {
      checkAll(normalCasePropTestConfig, Arb.dataConnect.anyScalar.any()) { value ->
        val otherValues = Arb.dataConnect.anyScalar.any().filterNotAnyScalarMatching(value)
        verifyAnyScalarQueryVariable(
          value,
          otherValues.next(),
          otherValues.next(),
          insert3MutationName = "AnyScalarNullableInsert3",
          getAllByTagAndValueQueryName = "AnyScalarNullableGetAllByTagAndValue"
        )
      }
    }

  @Test
  fun anyScalarNullable_MutationSucceedsIfAnyVariableIsMissing() = runTest {
    verifyMutationWithMissingAnyVariableSucceeds(
      insertMutationName = "AnyScalarNullableInsert",
      getByKeyQueryName = "AnyScalarNullableGetByKey",
    )
  }

  @Test
  fun anyScalarNullable_QuerySucceedsIfAnyVariableIsMissing() = runTest {
    // TODO: factor this out to a reusable method
    val values = Arb.dataConnect.anyScalar.any()
    val tag = UUID.randomUUID().toString()
    val keys =
      executeInsert3Mutation(
        "AnyScalarNullableInsert3",
        tag,
        values.next(),
        values.next(),
        values.next()
      )

    val queryRef =
      dataConnect.query(
        operationName = "AnyScalarNullableGetAllByTagAndValue",
        variables = DataConnectUntypedVariables("tag" to tag),
        dataDeserializer = DataConnectUntypedData,
        variablesSerializer = DataConnectUntypedVariables,
      )
    val queryResult = queryRef.execute()
    queryResult.data.data shouldBe
      mapOf(
        "items" to
          listOf(
            mapOf("id" to keys.key1.id),
            mapOf("id" to keys.key2.id),
            mapOf("id" to keys.key3.id)
          )
      )
    queryResult.data.errors.shouldBeEmpty()
  }

  @Test
  fun anyScalarNullable_MutationSucceedsIfAnyVariableIsNull() = runTest {
    verifyMutationWithNullAnyVariableSucceeds(
      insertMutationName = "AnyScalarNullableInsert",
      getByKeyQueryName = "AnyScalarNullableGetByKey",
    )
  }

  @Test
  fun anyScalarNullable_QuerySucceedsIfAnyVariableIsNull() = runTest {
    // TODO: factor this out to a reusable method
    val values = Arb.dataConnect.anyScalar.any().filter { it !== null }
    val tag = UUID.randomUUID().toString()
    val keys =
      executeInsert3Mutation("AnyScalarNullableInsert3", tag, null, values.next(), values.next())

    val queryRef =
      dataConnect.query(
        operationName = "AnyScalarNullableGetAllByTagAndValue",
        variables = DataConnectUntypedVariables("tag" to tag, "value" to null),
        dataDeserializer = DataConnectUntypedData,
        variablesSerializer = DataConnectUntypedVariables,
      )
    val queryResult = queryRef.execute()
    queryResult.data.data shouldBe mapOf("items" to listOf(mapOf("id" to keys.key1.id)))
    queryResult.data.errors.shouldBeEmpty()
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for inserting into and querying this table:
  // type AnyScalarNullableListOfNullable @table { value: [Any], tag: String, position: Int }
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun anyScalarNullableListOfNullable_MutationVariableEdgeCases() =
    runTest(timeout = 60.seconds) {
      assertSoftly {
        val edgeCases = EdgeCases.anyScalar.lists.map { it.filterNotNull() }
        for (value in edgeCases) {
          withClue("value=$value") {
            val key = executeInsertMutation("AnyScalarNullableListOfNullableInsert", value)
            val expectedQueryResult = expectedAnyScalarRoundTripValue(value)
            verifyQueryResult2("AnyScalarNullableListOfNullableGetByKey", key, expectedQueryResult)
          }
        }
      }
    }

  @Test
  fun anyScalarNullableListOfNullable_QueryVariableEdgeCases() =
    runTest(timeout = 60.seconds) {
      val edgeCases =
        EdgeCases.anyScalar.lists.map { it.filterNotNull() }.filter { it.isNotEmpty() }
      val otherValues = Arb.dataConnect.anyScalar.list().map { it.filterNotNull() }

      assertSoftly {
        for (value in edgeCases) {
          val curOtherValues =
            otherValues.filterNotIncludesAllMatchingAnyScalars(value).orNull(nullProbability = 0.1)
          withClue("value=$value") {
            verifyAnyScalarQueryVariable(
              value,
              curOtherValues.next(),
              curOtherValues.next(),
              insert3MutationName = "AnyScalarNullableListOfNullableInsert3",
              getAllByTagAndValueQueryName = "AnyScalarNullableListOfNullableGetAllByTagAndValue"
            )
          }
        }
      }
    }

  @Test
  fun anyScalarNullableListOfNullable_MutationVariableNormalCases() =
    runTest(timeout = 60.seconds) {
      val values = Arb.dataConnect.anyScalar.list().map { it.filterNotNull() }
      checkAll(normalCasePropTestConfig, values) { value ->
        verifyAnyScalarRoundTrip(
          value,
          insertMutationName = "AnyScalarNullableListOfNullableInsert",
          getByKeyQueryName = "AnyScalarNullableListOfNullableGetByKey",
        )
      }
    }

  @Test
  fun anyScalarNullableListOfNullable_QueryVariableNormalCases() =
    runTest(timeout = 60.seconds) {
      val values =
        Arb.dataConnect.anyScalar.list().map { it.filterNotNull() }.filter { it.isNotEmpty() }
      val otherValues = Arb.dataConnect.anyScalar.list().map { it.filterNotNull() }

      checkAll(normalCasePropTestConfig, values) { value ->
        val curOtherValues =
          otherValues.filterNotIncludesAllMatchingAnyScalars(value).orNull(nullProbability = 0.1)
        verifyAnyScalarQueryVariable(
          value,
          curOtherValues.next(),
          curOtherValues.next(),
          insert3MutationName = "AnyScalarNullableListOfNullableInsert3",
          getAllByTagAndValueQueryName = "AnyScalarNullableListOfNullableGetAllByTagAndValue"
        )
      }
    }

  @Test
  fun anyScalarNullableListOfNullable_MutationSucceedsIfAnyVariableIsMissing() = runTest {
    val key = executeInsertMutation("AnyScalarNullableListOfNullableInsert", EmptyVariables)
    verifyQueryResult2("AnyScalarNullableListOfNullableGetByKey", key, null)
  }

  @Test
  fun anyScalarNullableListOfNullable_QuerySucceedsIfAnyVariableIsMissing() = runTest {
    val values = Arb.dataConnect.anyScalar.list().map { it.filterNotNull() }
    val tag = UUID.randomUUID().toString()
    val keys =
      executeInsert3Mutation(
        "AnyScalarNullableListOfNullableInsert3",
        tag,
        null,
        emptyList<Nothing>(),
        values.next()
      )
    val keyIds = listOf(keys.key1, keys.key2, keys.key3).map { it.id }
    val queryResult =
      executeGetAllByTagAndValueQuery(
        "AnyScalarNullableListOfNullableGetAllByTagAndValue",
        tag,
        OmitValue
      )
    val queryIds = queryResult.map { it.id }
    queryIds shouldContainExactlyInAnyOrder keyIds
  }

  @Test
  fun anyScalarNullableListOfNullable_MutationSucceedsIfAnyVariableIsNull() = runTest {
    val key = executeInsertMutation("AnyScalarNullableListOfNullableInsert", null)
    verifyQueryResult2("AnyScalarNullableListOfNullableGetByKey", key, null)
  }

  @Test
  fun anyScalarNullableListOfNullable_QuerySucceedsIfAnyVariableIsNull() = runTest {
    val values = Arb.dataConnect.anyScalar.list().map { it.filterNotNull() }
    val tag = UUID.randomUUID().toString()
    executeInsert3Mutation(
        "AnyScalarNullableListOfNullableInsert3",
        tag,
        null,
        emptyList<Nothing>(),
        values.next()
      )
      .key1
    val queryResult =
      executeGetAllByTagAndValueQuery(
        "AnyScalarNullableListOfNullableGetAllByTagAndValue",
        tag,
        null
      )
    queryResult.shouldBeEmpty()
  }

  @Test
  fun anyScalarNullableListOfNullable_QuerySucceedsIfAnyVariableIsEmpty() = runTest {
    val values = Arb.dataConnect.anyScalar.list().map { it.filterNotNull() }
    val tag = UUID.randomUUID().toString()
    val keys =
      executeInsert3Mutation(
        "AnyScalarNullableListOfNullableInsert3",
        tag,
        null,
        emptyList<Nothing>(),
        values.next()
      )
    val queryResult =
      executeGetAllByTagAndValueQuery(
        "AnyScalarNullableListOfNullableGetAllByTagAndValue",
        tag,
        emptyList<Nothing>()
      )
    val queryIds = queryResult.map { it.id }
    queryIds.shouldContainExactlyInAnyOrder(keys.key2.id, keys.key3.id)
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for inserting into and querying this table:
  // type AnyScalarNullableListOfNonNullable @table { value: [Any!], tag: String, position: Int }
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun anyScalarNullableListOfNonNullable_MutationVariableEdgeCases() =
    runTest(timeout = 60.seconds) {
      assertSoftly {
        val edgeCases = EdgeCases.anyScalar.lists.map { it.filterNotNull() }
        for (value in edgeCases) {
          withClue("value=$value") {
            val key = executeInsertMutation("AnyScalarNullableListOfNonNullableInsert", value)
            val expectedQueryResult = expectedAnyScalarRoundTripValue(value)
            verifyQueryResult2(
              "AnyScalarNullableListOfNonNullableGetByKey",
              key,
              expectedQueryResult
            )
          }
        }
      }
    }

  @Test
  fun anyScalarNullableListOfNonNullable_QueryVariableEdgeCases() =
    runTest(timeout = 60.seconds) {
      val edgeCases =
        EdgeCases.anyScalar.lists.map { it.filterNotNull() }.filter { it.isNotEmpty() }
      val otherValues = Arb.dataConnect.anyScalar.list().map { it.filterNotNull() }

      assertSoftly {
        for (value in edgeCases) {
          val curOtherValues =
            otherValues.filterNotIncludesAllMatchingAnyScalars(value).orNull(nullProbability = 0.1)
          withClue("value=$value") {
            verifyAnyScalarQueryVariable(
              value,
              curOtherValues.next(),
              curOtherValues.next(),
              insert3MutationName = "AnyScalarNullableListOfNonNullableInsert3",
              getAllByTagAndValueQueryName = "AnyScalarNullableListOfNonNullableGetAllByTagAndValue"
            )
          }
        }
      }
    }

  @Test
  fun anyScalarNullableListOfNonNullable_MutationVariableNormalCases() =
    runTest(timeout = 60.seconds) {
      val values = Arb.dataConnect.anyScalar.list().map { it.filterNotNull() }
      checkAll(normalCasePropTestConfig, values) { value ->
        verifyAnyScalarRoundTrip(
          value,
          insertMutationName = "AnyScalarNullableListOfNonNullableInsert",
          getByKeyQueryName = "AnyScalarNullableListOfNonNullableGetByKey",
        )
      }
    }

  @Test
  fun anyScalarNullableListOfNonNullable_QueryVariableNormalCases() =
    runTest(timeout = 60.seconds) {
      val values =
        Arb.dataConnect.anyScalar.list().map { it.filterNotNull() }.filter { it.isNotEmpty() }
      val otherValues = Arb.dataConnect.anyScalar.list().map { it.filterNotNull() }

      checkAll(normalCasePropTestConfig, values) { value ->
        val curOtherValues =
          otherValues.filterNotIncludesAllMatchingAnyScalars(value).orNull(nullProbability = 0.1)
        verifyAnyScalarQueryVariable(
          value,
          curOtherValues.next(),
          curOtherValues.next(),
          insert3MutationName = "AnyScalarNullableListOfNonNullableInsert3",
          getAllByTagAndValueQueryName = "AnyScalarNullableListOfNonNullableGetAllByTagAndValue"
        )
      }
    }

  @Test
  fun anyScalarNullableListOfNonNullable_MutationSucceedsIfAnyVariableIsMissing() = runTest {
    val key = executeInsertMutation("AnyScalarNullableListOfNonNullableInsert", EmptyVariables)
    verifyQueryResult2("AnyScalarNullableListOfNonNullableGetByKey", key, null)
  }

  @Test
  fun anyScalarNullableListOfNonNullable_QuerySucceedsIfAnyVariableIsMissing() = runTest {
    val values = Arb.dataConnect.anyScalar.list().map { it.filterNotNull() }
    val tag = UUID.randomUUID().toString()
    val keys =
      executeInsert3Mutation(
        "AnyScalarNullableListOfNonNullableInsert3",
        tag,
        null,
        emptyList<Nothing>(),
        values.next()
      )
    val keyIds = listOf(keys.key1, keys.key2, keys.key3).map { it.id }
    val queryResult =
      executeGetAllByTagAndValueQuery(
        "AnyScalarNullableListOfNonNullableGetAllByTagAndValue",
        tag,
        OmitValue
      )
    val queryIds = queryResult.map { it.id }
    queryIds shouldContainExactlyInAnyOrder keyIds
  }

  @Test
  fun anyScalarNullableListOfNonNullable_MutationSucceedsIfAnyVariableIsNull() = runTest {
    val key = executeInsertMutation("AnyScalarNullableListOfNonNullableInsert", null)
    verifyQueryResult2("AnyScalarNullableListOfNonNullableGetByKey", key, null)
  }

  @Test
  fun anyScalarNullableListOfNonNullable_QuerySucceedsIfAnyVariableIsNull() = runTest {
    val values = Arb.dataConnect.anyScalar.list().map { it.filterNotNull() }
    val tag = UUID.randomUUID().toString()
    executeInsert3Mutation(
        "AnyScalarNullableListOfNonNullableInsert3",
        tag,
        null,
        emptyList<Nothing>(),
        values.next()
      )
      .key1
    val queryResult =
      executeGetAllByTagAndValueQuery(
        "AnyScalarNullableListOfNonNullableGetAllByTagAndValue",
        tag,
        null
      )
    queryResult.shouldBeEmpty()
  }

  @Test
  fun anyScalarNullableListOfNonNullable_QuerySucceedsIfAnyVariableIsEmpty() = runTest {
    val values = Arb.dataConnect.anyScalar.list().map { it.filterNotNull() }
    val tag = UUID.randomUUID().toString()
    val keys =
      executeInsert3Mutation(
        "AnyScalarNullableListOfNonNullableInsert3",
        tag,
        null,
        emptyList<Nothing>(),
        values.next()
      )
    val queryResult =
      executeGetAllByTagAndValueQuery(
        "AnyScalarNullableListOfNonNullableGetAllByTagAndValue",
        tag,
        emptyList<Nothing>()
      )
    val queryIds = queryResult.map { it.id }
    queryIds.shouldContainExactlyInAnyOrder(keys.key2.id, keys.key3.id)
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for inserting into and querying this table:
  // type AnyScalarNonNullableListOfNullable @table { value: [Any]!, tag: String, position: Int }
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun anyScalarNonNullableListOfNullable_MutationVariableEdgeCases() =
    runTest(timeout = 60.seconds) {
      assertSoftly {
        for (value in EdgeCases.anyScalar.lists.map { it.filterNotNull() }) {
          withClue("value=$value") {
            verifyAnyScalarRoundTrip(
              value,
              insertMutationName = "AnyScalarNonNullableListOfNullableInsert",
              getByKeyQueryName = "AnyScalarNonNullableListOfNullableGetByKey",
            )
          }
        }
      }
    }

  @Test
  fun anyScalarNonNullableListOfNullable_QueryVariableEdgeCases() =
    runTest(timeout = 60.seconds) {
      val edgeCases =
        EdgeCases.anyScalar.lists.map { it.filterNotNull() }.filter { it.isNotEmpty() }
      val otherValues = Arb.dataConnect.anyScalar.list().map { it.filterNotNull() }

      assertSoftly {
        for (value in edgeCases) {
          val curOtherValues = otherValues.filterNotIncludesAllMatchingAnyScalars(value)
          withClue("value=$value") {
            verifyAnyScalarQueryVariable(
              value,
              curOtherValues.next(),
              curOtherValues.next(),
              insert3MutationName = "AnyScalarNonNullableListOfNullableInsert3",
              getAllByTagAndValueQueryName = "AnyScalarNonNullableListOfNullableGetAllByTagAndValue"
            )
          }
        }
      }
    }

  @Test
  fun anyScalarNonNullableListOfNullable_MutationVariableNormalCases() =
    runTest(timeout = 60.seconds) {
      val values = Arb.dataConnect.anyScalar.list().map { it.filterNotNull() }
      checkAll(normalCasePropTestConfig, values) { value ->
        verifyAnyScalarRoundTrip(
          value,
          insertMutationName = "AnyScalarNonNullableListOfNullableInsert",
          getByKeyQueryName = "AnyScalarNonNullableListOfNullableGetByKey",
        )
      }
    }

  @Test
  fun anyScalarNonNullableListOfNullable_QueryVariableNormalCases() =
    runTest(timeout = 60.seconds) {
      val values =
        Arb.dataConnect.anyScalar.list().map { it.filterNotNull() }.filter { it.isNotEmpty() }
      val otherValues = Arb.dataConnect.anyScalar.list().map { it.filterNotNull() }

      checkAll(normalCasePropTestConfig, values) { value ->
        val curOtherValues = otherValues.filterNotIncludesAllMatchingAnyScalars(value)
        verifyAnyScalarQueryVariable(
          value,
          curOtherValues.next(),
          curOtherValues.next(),
          insert3MutationName = "AnyScalarNonNullableListOfNullableInsert3",
          getAllByTagAndValueQueryName = "AnyScalarNonNullableListOfNullableGetAllByTagAndValue"
        )
      }
    }

  @Test
  fun anyScalarNonNullableListOfNullable_MutationFailsIfAnyVariableIsMissing() = runTest {
    verifyMutationWithMissingAnyVariableFails("AnyScalarNonNullableListOfNullableInsert")
  }

  @Test
  fun anyScalarNonNullableListOfNullable_QueryFailsIfAnyVariableIsMissing() = runTest {
    verifyQueryWithMissingAnyVariableFails("AnyScalarNonNullableListOfNullableGetAllByTagAndValue")
  }

  @Test
  fun anyScalarNonNullableListOfNullable_MutationFailsIfAnyVariableIsNull() = runTest {
    verifyMutationWithNullAnyVariableFails("AnyScalarNonNullableListOfNullableInsert")
  }

  @Test
  fun anyScalarNonNullableListOfNullable_QueryFailsIfAnyVariableIsNull() = runTest {
    verifyQueryWithNullAnyVariableFails("AnyScalarNonNullableListOfNullableGetAllByTagAndValue")
  }

  @Test
  fun anyScalarNonNullableListOfNullable_QuerySucceedsIfAnyVariableIsEmpty() = runTest {
    val values = Arb.dataConnect.anyScalar.list().map { it.filterNotNull() }
    val tag = UUID.randomUUID().toString()
    val keys =
      executeInsert3Mutation(
        "AnyScalarNonNullableListOfNullableInsert3",
        tag,
        values.next(),
        emptyList<Nothing>(),
        values.next()
      )
    val queryResult =
      executeGetAllByTagAndValueQuery(
        "AnyScalarNonNullableListOfNullableGetAllByTagAndValue",
        tag,
        emptyList<Nothing>()
      )
    val queryIds = queryResult.map { it.id }
    queryIds.shouldContainExactlyInAnyOrder(keys.key1.id, keys.key2.id, keys.key3.id)
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for inserting into and querying this table:
  // type AnyScalarNonNullableListOfNonNullable @table {
  //   value: [Any!]!, tag: String, position: Int }
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun anyScalarNonNullableListOfNonNullable_MutationVariableEdgeCases() =
    runTest(timeout = 60.seconds) {
      assertSoftly {
        for (value in EdgeCases.anyScalar.lists.map { it.filterNotNull() }) {
          withClue("value=$value") {
            verifyAnyScalarRoundTrip(
              value,
              insertMutationName = "AnyScalarNonNullableListOfNonNullableInsert",
              getByKeyQueryName = "AnyScalarNonNullableListOfNonNullableGetByKey",
            )
          }
        }
      }
    }

  @Test
  fun anyScalarNonNullableListOfNonNullable_QueryVariableEdgeCases() =
    runTest(timeout = 60.seconds) {
      val edgeCases =
        EdgeCases.anyScalar.lists.map { it.filterNotNull() }.filter { it.isNotEmpty() }
      val otherValues = Arb.dataConnect.anyScalar.list().map { it.filterNotNull() }

      assertSoftly {
        for (value in edgeCases) {
          val curOtherValues = otherValues.filterNotIncludesAllMatchingAnyScalars(value)
          withClue("value=$value") {
            verifyAnyScalarQueryVariable(
              value,
              curOtherValues.next(),
              curOtherValues.next(),
              insert3MutationName = "AnyScalarNonNullableListOfNonNullableInsert3",
              getAllByTagAndValueQueryName =
                "AnyScalarNonNullableListOfNonNullableGetAllByTagAndValue"
            )
          }
        }
      }
    }

  @Test
  fun anyScalarNonNullableListOfNonNullable_MutationVariableNormalCases() =
    runTest(timeout = 60.seconds) {
      val values = Arb.dataConnect.anyScalar.list().map { it.filterNotNull() }
      checkAll(normalCasePropTestConfig, values) { value ->
        verifyAnyScalarRoundTrip(
          value,
          insertMutationName = "AnyScalarNonNullableListOfNonNullableInsert",
          getByKeyQueryName = "AnyScalarNonNullableListOfNonNullableGetByKey",
        )
      }
    }

  @Test
  fun anyScalarNonNullableListOfNonNullable_QueryVariableNormalCases() =
    runTest(timeout = 60.seconds) {
      val values =
        Arb.dataConnect.anyScalar.list().map { it.filterNotNull() }.filter { it.isNotEmpty() }
      val otherValues = Arb.dataConnect.anyScalar.list().map { it.filterNotNull() }

      checkAll(normalCasePropTestConfig, values) { value ->
        val curOtherValues = otherValues.filterNotIncludesAllMatchingAnyScalars(value)
        verifyAnyScalarQueryVariable(
          value,
          curOtherValues.next(),
          curOtherValues.next(),
          insert3MutationName = "AnyScalarNonNullableListOfNonNullableInsert3",
          getAllByTagAndValueQueryName = "AnyScalarNonNullableListOfNonNullableGetAllByTagAndValue"
        )
      }
    }

  @Test
  fun anyScalarNonNullableListOfNonNullable_MutationFailsIfAnyVariableIsMissing() = runTest {
    verifyMutationWithMissingAnyVariableFails("AnyScalarNonNullableListOfNonNullableInsert")
  }

  @Test
  fun anyScalarNonNullableListOfNonNullable_QueryFailsIfAnyVariableIsMissing() = runTest {
    verifyQueryWithMissingAnyVariableFails(
      "AnyScalarNonNullableListOfNonNullableGetAllByTagAndValue"
    )
  }

  @Test
  fun anyScalarNonNullableListOfNonNullable_MutationFailsIfAnyVariableIsNull() = runTest {
    verifyMutationWithNullAnyVariableFails("AnyScalarNonNullableListOfNonNullableInsert")
  }

  @Test
  fun anyScalarNonNullableListOfNonNullable_QueryFailsIfAnyVariableIsNull() = runTest {
    verifyQueryWithNullAnyVariableFails("AnyScalarNonNullableListOfNonNullableGetAllByTagAndValue")
  }

  @Test
  fun anyScalarNonNullableListOfNonNullable_QuerySucceedsIfAnyVariableIsEmpty() = runTest {
    val values = Arb.dataConnect.anyScalar.list().map { it.filterNotNull() }
    val tag = UUID.randomUUID().toString()
    val keys =
      executeInsert3Mutation(
        "AnyScalarNonNullableListOfNonNullableInsert3",
        tag,
        values.next(),
        emptyList<Nothing>(),
        values.next()
      )
    val queryResult =
      executeGetAllByTagAndValueQuery(
        "AnyScalarNonNullableListOfNonNullableGetAllByTagAndValue",
        tag,
        emptyList<Nothing>()
      )
    val queryIds = queryResult.map { it.id }
    queryIds.shouldContainExactlyInAnyOrder(keys.key1.id, keys.key2.id, keys.key3.id)
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // End of tests; everything below is helper functions and classes.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  object EmptyVariables

  /**
   * Verifies that a value used as an `Any` scalar specified as a variable to a mutation is handled
   * correctly. This is done by specifying the `Any` scalar value as a variable to a mutation that
   * inserts a row into a table, followed by querying that row by its key to ensure that an equal
   * `Any` value comes back from the query.
   *
   * @param value The value of the `Any` scalar to use; must be `null`, a [Boolean], [String],
   * [Double], or a [Map], or [List] composed of these types.
   * @param insertMutationName The operation name of a GraphQL mutation that takes a single variable
   * named "value" of type `Any` or `[Any]`, with any nullability; this mutation must insert a row
   * into a table and return a key for that row, where the key is a single "id" of type `UUID`.
   * @param getByKeyQueryName The operation name of a GraphQL query that takes a single variable
   * named "key" whose value is the key type returned from the `insertMutationName` mutation; its
   * selection set must have a single field named "item" whose value is the `Any` value specified to
   * the `insertMutationName` mutation.
   */
  private suspend fun verifyAnyScalarRoundTrip(
    value: Any?,
    insertMutationName: String,
    getByKeyQueryName: String,
  ) {
    val key = executeInsertMutation(insertMutationName, value)
    val expectedQueryResult = expectedAnyScalarRoundTripValue(value)
    verifyQueryResult2(getByKeyQueryName, key, expectedQueryResult)
  }

  private suspend fun verifyAnyScalarQueryVariable(
    value: Any?,
    value2: Any?,
    value3: Any?,
    insert3MutationName: String,
    getAllByTagAndValueQueryName: String,
  ) {
    require(value != value2)
    require(value != value3)
    require(expectedAnyScalarRoundTripValue(value) != expectedAnyScalarRoundTripValue(value2))
    require(expectedAnyScalarRoundTripValue(value) != expectedAnyScalarRoundTripValue(value3))

    val tag = UUID.randomUUID().toString()
    val key = executeInsert3Mutation(insert3MutationName, tag, value, value2, value3).key1

    val queryResult = executeGetAllByTagAndValueQuery(getAllByTagAndValueQueryName, tag, value)
    queryResult.shouldContainExactlyInAnyOrder(key)
  }

  private inline fun <reified Data> mutationRefForVariables(
    operationName: String,
    variables: Map<String, Any?>,
    dataDeserializer: DeserializationStrategy<Data>,
  ): MutationRef<Data, DataConnectUntypedVariables> =
    dataConnect.mutation(
      operationName = operationName,
      variables = DataConnectUntypedVariables(variables),
      dataDeserializer,
      DataConnectUntypedVariables,
    )

  private inline fun <reified Data> queryRefForVariables(
    operationName: String,
    variables: Map<String, Any?>,
    dataDeserializer: DeserializationStrategy<Data>,
  ): QueryRef<Data, DataConnectUntypedVariables> =
    dataConnect.query(
      operationName = operationName,
      variables = DataConnectUntypedVariables(variables),
      dataDeserializer,
      DataConnectUntypedVariables,
    )

  private inline fun <reified Data> mutationRefForVariable(
    operationName: String,
    variable: Any?,
    dataDeserializer: DeserializationStrategy<Data>,
  ): MutationRef<Data, DataConnectUntypedVariables> =
    mutationRefForVariables(operationName, mapOf("value" to variable), dataDeserializer)

  private inline fun <reified Data> queryRefForVariable(
    operationName: String,
    variable: Any?,
    dataDeserializer: DeserializationStrategy<Data>,
  ): QueryRef<Data, DataConnectUntypedVariables> =
    queryRefForVariables(operationName, mapOf("value" to variable), dataDeserializer)

  private suspend fun verifyMutationWithNullAnyVariableFails(operationName: String) {
    val mutationRef = mutationRefForVariable(operationName, null, DataConnectUntypedData)
    mutationRef.verifyExecuteFailsDueToNullVariable()
  }

  private suspend fun verifyQueryWithNullAnyVariableFails(operationName: String) {
    val queryRef = queryRefForVariable(operationName, null, DataConnectUntypedData)
    queryRef.verifyExecuteFailsDueToNullVariable()
  }

  private suspend fun verifyMutationWithNullAnyVariableSucceeds(
    insertMutationName: String,
    getByKeyQueryName: String,
  ) {
    val key = executeInsertMutation(insertMutationName, null)
    verifyQueryResult2(getByKeyQueryName, key, null)
  }

  private suspend fun OperationRef<DataConnectUntypedData, *>
    .verifyExecuteFailsDueToNullVariable() {
    val result = execute()
    result.data.asClue {
      it.data.shouldBeNull()
      it.errors.shouldHaveAtLeastSize(1)
      it.errors[0].message shouldContainWithNonAbuttingTextIgnoringCase "\$value"
      it.errors[0].message shouldContainWithNonAbuttingTextIgnoringCase "is null"
    }
  }

  private suspend fun verifyMutationWithMissingAnyVariableFails(operationName: String) {
    val variables: Map<String, Any?> = emptyMap()
    val mutationRef = mutationRefForVariables(operationName, variables, DataConnectUntypedData)
    mutationRef.verifyExecuteFailsDueToMissingVariable()
  }

  private suspend fun verifyQueryWithMissingAnyVariableFails(operationName: String) {
    val variables: Map<String, Any?> = emptyMap()
    val queryRef = queryRefForVariables(operationName, variables, DataConnectUntypedData)
    queryRef.verifyExecuteFailsDueToMissingVariable()
  }

  private suspend fun verifyMutationWithMissingAnyVariableSucceeds(
    insertMutationName: String,
    getByKeyQueryName: String,
  ) {
    val key = executeInsertMutation(insertMutationName, EmptyVariables)
    verifyQueryResult2(getByKeyQueryName, key, null)
  }

  private suspend fun OperationRef<DataConnectUntypedData, *>
    .verifyExecuteFailsDueToMissingVariable() {
    val result = execute()
    result.data.asClue {
      it.data.shouldBeNull()
      it.errors.shouldHaveAtLeastSize(1)
      it.errors[0].message shouldContainWithNonAbuttingTextIgnoringCase "\$value"
      it.errors[0].message shouldContainWithNonAbuttingTextIgnoringCase "is missing"
    }
  }

  object OmitValue

  private suspend fun executeGetAllByTagAndValueQuery(
    queryName: String,
    tag: String,
    value: Any?
  ): List<TestTableKeyString> =
    executeGetAllByTagAndValueQuery(queryName, mapOf("tag" to tag, "value" to value))

  private suspend fun executeGetAllByTagAndValueQuery(
    queryName: String,
    tag: String,
    @Suppress("UNUSED_PARAMETER") value: OmitValue
  ): List<TestTableKeyString> = executeGetAllByTagAndValueQuery(queryName, mapOf("tag" to tag))

  private suspend fun executeGetAllByTagAndValueQuery(
    queryName: String,
    variables: Map<String, Any?>
  ): List<TestTableKeyString> {
    @Serializable data class QueryData(val items: List<TestTableKeyString>)
    val queryRef = queryRefForVariables(queryName, variables, serializer<QueryData>())
    val queryResult = queryRef.execute()
    return queryResult.data.items
  }

  private suspend fun executeInsert3Mutation(
    operationName: String,
    tag: String,
    value1: Any?,
    value2: Any?,
    value3: Any?,
  ): Insert3MutationDataStrings {
    val mutationRef =
      mutationRefForVariables<Insert3MutationDataStrings>(
        operationName,
        variables = mapOf("tag" to tag, "value1" to value1, "value2" to value2, "value3" to value3),
        dataDeserializer = serializer(),
      )
    return mutationRef.execute().data
  }

  private suspend fun executeInsertMutation(
    operationName: String,
    variable: Any?,
  ): TestTableKey {
    val mutationRef =
      mutationRefForVariable<InsertMutationData>(
        operationName,
        variable,
        dataDeserializer = serializer(),
      )
    return mutationRef.execute().data.key
  }

  private suspend fun executeInsertMutation(
    operationName: String,
    @Suppress("UNUSED_PARAMETER") variables: EmptyVariables,
  ): TestTableKey {
    val mutationRef =
      mutationRefForVariables<InsertMutationData>(
        operationName,
        emptyMap(),
        dataDeserializer = serializer(),
      )
    return mutationRef.execute().data.key
  }

  private suspend fun verifyQueryResult2(
    operationName: String,
    key: TestTableKey,
    expectedData: Any?
  ) {
    val queryRef =
      dataConnect.query(
        operationName = operationName,
        variables = QueryByKeyVariables(key),
        DataConnectUntypedData,
        serializer(),
      )
    val queryResult = queryRef.execute()
    queryResult.data.asClue {
      it.data.shouldNotBeNull()
      it.data shouldBe mapOf("item" to mapOf("value" to expectedData))
      it.errors.shouldBeEmpty()
    }
  }

  @Serializable data class TestTableKey(val id: UUID)
  @Serializable data class TestTableKeyString(val id: String)

  @Serializable private data class InsertMutationData(val key: TestTableKey)

  @Serializable
  private data class Insert3MutationDataStrings(
    val key1: TestTableKeyString,
    val key2: TestTableKeyString,
    val key3: TestTableKeyString
  )

  @Serializable private data class QueryByKeyVariables(val key: TestTableKey)

  private companion object {

    val normalCasePropTestConfig =
      PropTestConfig(iterations = 5, edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.0))
  }
}
