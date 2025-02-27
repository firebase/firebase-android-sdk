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

@file:OptIn(ExperimentalFirebaseDataConnect::class)

package com.google.firebase.dataconnect.connectors.demo

import com.google.firebase.Timestamp
import com.google.firebase.dataconnect.ExperimentalFirebaseDataConnect
import com.google.firebase.dataconnect.MutationResult
import com.google.firebase.dataconnect.OperationResult
import com.google.firebase.dataconnect.connectors.demo.testutil.DemoConnectorIntegrationTestBase
import com.google.firebase.dataconnect.generated.GeneratedMutation
import com.google.firebase.dataconnect.generated.GeneratedOperation
import com.google.firebase.dataconnect.generated.GeneratedQuery
import com.google.firebase.dataconnect.serializers.TimestampSerializer
import com.google.firebase.dataconnect.testutil.property.arbitrary.EdgeCases
import com.google.firebase.dataconnect.testutil.property.arbitrary.InstantTestCase
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.next
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.junit.Test

class TimestampScalarIntegrationTest : DemoConnectorIntegrationTestBase() {

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for inserting into and querying this table:
  // type NonNullTimestamp { value: Timestamp!, tag: String, position: Int }
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun nonNullTimestamp_MutationStringVariableEdgeCases() =
    runTest(timeout = 60.seconds) {
      assertSoftly {
        EdgeCases.javaTime.instants.all
          .distinctBy { it.fdcStringVariable }
          .forEach { verifyNonNullTimestampVariableRoundTrip(it, it.fdcStringVariable) }
      }
    }

  @Test
  fun nonNullTimestamp_MutationTimestampVariableEdgeCases() =
    runTest(timeout = 60.seconds) {
      assertSoftly {
        EdgeCases.javaTime.instants.all
          .distinctBy { it.timestamp }
          .forEach { verifyNonNullTimestampVariableRoundTrip(it, it.timestamp) }
      }
    }

  @Test
  fun nonNullTimestamp_QueryStringVariableEdgeCases() =
    runTest(timeout = 60.seconds) {
      assertSoftly {
        EdgeCases.javaTime.instants.all
          .distinctBy { it.fdcStringVariable }
          .map { QueryVariableTestCase.from(it) }
          .forEach { verifyNonNullTimestampQueryVariable(it, it.testCase.fdcStringVariable) }
      }
    }

  @Test
  fun nonNullTimestamp_QueryTimestampVariableEdgeCases() =
    runTest(timeout = 60.seconds) {
      assertSoftly {
        assertSoftly {
          EdgeCases.javaTime.instants.all
            .distinctBy { it.timestamp }
            .map { QueryVariableTestCase.from(it) }
            .forEach { verifyNonNullTimestampQueryVariable(it, it.testCase.timestamp) }
        }
      }
    }

  private suspend fun verifyNonNullTimestampVariableRoundTrip(
    testCase: InstantTestCase,
    timestampVariable: String
  ) {
    val insertResult = connector.nonNullTimestampInsert.executeWithStringVariable(timestampVariable)
    val queryResult = connector.nonNullTimestampGetByKey.execute(insertResult.data.key)
    val item = withClue("data") { queryResult.data.item.shouldNotBeNull() }
    item.value shouldBe testCase.fdcFieldTimestamp
  }

  private suspend fun verifyNonNullTimestampVariableRoundTrip(
    testCase: InstantTestCase,
    timestampVariable: Timestamp
  ) {
    withClue(testCase) {
      val insertResult = connector.nonNullTimestampInsert.execute(timestampVariable)
      val queryResult =
        connector.nonNullTimestampGetByKey.withStringData().execute(insertResult.data.key)
      val item = withClue("data") { queryResult.data.item.shouldNotBeNull() }
      item.value shouldMatch testCase.fdcFieldRegex
    }
  }

  private suspend fun verifyNonNullTimestampQueryVariable(
    testCase: QueryVariableTestCase,
    timestampVariable: String
  ) {
    withClue(testCase) {
      val insertResult =
        connector.nonNullTimestampInsert3.execute(
          value1 = testCase.value1.timestamp,
          value2 = testCase.value2.timestamp,
          value3 = testCase.value3.timestamp
        ) {
          tag = testCase.tag
        }

      val queryResult =
        connector.nonNullTimestampGetAllByTagAndValue.executeWithTagAndStringTimestampVariables(
          testCase.tag,
          timestampVariable,
        )

      val key = testCase.testCaseKeyFrom(insertResult)
      queryResult.ids.shouldContainExactlyInAnyOrder(key.id)
    }
  }

  private suspend fun verifyNonNullTimestampQueryVariable(
    testCase: QueryVariableTestCase,
    timestampVariable: Timestamp
  ) {
    withClue(testCase) {
      val insertResult =
        connector.nonNullTimestampInsert3.executeWithTagAndStringVariables(
          tag = testCase.tag,
          value1 = testCase.value1.fdcStringVariable,
          value2 = testCase.value2.fdcStringVariable,
          value3 = testCase.value3.fdcStringVariable
        )

      val queryResult =
        connector.nonNullTimestampGetAllByTagAndValue.execute(timestampVariable) {
          tag = testCase.tag
        }

      val key = testCase.testCaseKeyFrom(insertResult)
      queryResult.ids.shouldContainExactlyInAnyOrder(key.id)
    }
  }

  /**
   * Data used in a test case that verifies that timestamps in query variables match the expected
   * rows in the database.
   */
  private data class QueryVariableTestCase(
    val tag: String,
    val testCase: InstantTestCase,
    val value1: InstantTestCase,
    val value2: InstantTestCase,
    val value3: InstantTestCase,
  ) {

    /**
     * The "value number" of the test case amongst the values. For example, if `this.testCase` is
     * the same object as `this.value2` then this property's value will be `2`.
     */
    private val testCaseValueNumber: Int =
      if (testCase === value1) {
        1
      } else if (testCase === value2) {
        2
      } else if (testCase === value3) {
        3
      } else {
        throw IllegalArgumentException(
          "testCase must be the same object as value1 or value2 or value3"
        )
      }

    fun testCaseKeyFrom(
      result: MutationResult<NonNullTimestampInsert3Mutation.Data, *>
    ): NonNullTimestampKey =
      when (testCaseValueNumber) {
        1 -> result.data.key1
        2 -> result.data.key2
        3 -> result.data.key3
        else ->
          throw IllegalStateException(
            "internal error qhexvekwe8: this=$this testCaseValueNumber=$testCaseValueNumber"
          )
      }

    companion object
  }

  private fun QueryVariableTestCase.Companion.from(
    testCase: InstantTestCase
  ): QueryVariableTestCase {
    val othersArb =
      Arb.dataConnect.javaTime.instantTestCase().filter {
        it.fdcFieldTimestamp != testCase.fdcFieldTimestamp
      }
    val (other1, other2) = Array(2) { othersArb.next(rs) }

    val testCases = listOf(testCase, other1, other2).shuffled(rs.random)

    return QueryVariableTestCase(
      tag = Arb.alphanumericString().next(rs),
      testCase = testCase,
      value1 = testCases[0],
      value2 = testCases[1],
      value3 = testCases[2],
    )
  }

  /**
   * A `Data` type that can be used in place of [NonNullTimestampGetByKeyQuery.Data] that types the
   * value as a [String] instead of a [Timestamp], allowing verification of the data sent over the
   * wire without possible confounding from timestamp deserialization.
   */
  @Serializable
  private data class NonNullTimestampGetByKeyQueryStringData(val item: TimestampStringValue?) {
    @Serializable data class TimestampStringValue(val value: String)
  }

  /**
   * A `Variables` type that can be used in place of [NonNullTimestampInsertMutation.Variables] that
   * types the value as a [String] instead of a [Timestamp], allowing verification of the data sent
   * over the wire without possible confounding from timestamp serialization.
   */
  @Serializable private data class TimestampStringVariables(val value: String?)

  /**
   * A `Variables` type that can be used in place of
   * [NonNullTimestampGetAllByTagAndValueQuery.Variables] that types the timestamp value as a
   * [String] instead of a [Timestamp], allowing sending strings over the wire that are immune to
   * bugs in [TimestampSerializer], or not even possible to generate with the serializer.
   */
  @Serializable
  private data class TagAndTimestampStringVariables(val tag: String, val value: String?)

  /**
   * A `Variables` type that can be used in place of [NonNullTimestampInsert3Mutation.Variables]
   * that types the values as [String] instead of [Timestamp], allowing verification of the data
   * sent over the wire without possible confounding from timestamp serialization.
   */
  @Serializable
  private data class Insert3TimestampsStringVariables(
    val tag: String,
    val value1: String?,
    val value2: String?,
    val value3: String?
  )

  private companion object {

    @OptIn(ExperimentalKotest::class)
    val normalPropTestConfig =
      PropTestConfig(
        iterations = 20,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2),
      )

    val OperationResult<NonNullTimestampGetAllByTagAndValueQuery.Data, *>.ids: List<UUID>
      get() = data.items.map { it.id }

    suspend fun <Data> GeneratedOperation<*, Data, *>.executeWithTagAndStringTimestampVariables(
      tag: String,
      value: String?
    ) =
      withVariablesSerializer(serializer<TagAndTimestampStringVariables>())
        .ref(TagAndTimestampStringVariables(tag, value))
        .execute()

    suspend fun <Data> GeneratedMutation<*, Data, *>.executeWithStringVariable(value: String?) =
      withVariablesSerializer(serializer<TimestampStringVariables>())
        .ref(TimestampStringVariables(value))
        .execute()

    suspend fun <Data> GeneratedMutation<*, Data, *>.executeWithTagAndStringVariables(
      tag: String,
      value1: String?,
      value2: String?,
      value3: String?
    ) =
      withVariablesSerializer(serializer<Insert3TimestampsStringVariables>())
        .ref(Insert3TimestampsStringVariables(tag, value1, value2, value3))
        .execute()

    suspend fun <Data> GeneratedQuery<*, Data, NonNullTimestampGetByKeyQuery.Variables>.execute(
      key: NonNullTimestampKey
    ) = ref(NonNullTimestampGetByKeyQuery.Variables(key)).execute()

    private fun NonNullTimestampGetByKeyQuery.withStringData() =
      withDataDeserializer(serializer<NonNullTimestampGetByKeyQueryStringData>())
  }
}
