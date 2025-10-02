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

package com.google.firebase.dataconnect.connectors.demo

import com.google.firebase.dataconnect.AnyValue
import com.google.firebase.dataconnect.DataConnectException
import com.google.firebase.dataconnect.OperationRef
import com.google.firebase.dataconnect.connectors.demo.testutil.DemoConnectorIntegrationTestBase
import com.google.firebase.dataconnect.fromAny
import com.google.firebase.dataconnect.generated.GeneratedMutation
import com.google.firebase.dataconnect.generated.GeneratedQuery
import com.google.firebase.dataconnect.testutil.expectedAnyScalarRoundTripValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.EdgeCases
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.filterNotAnyScalarMatching
import com.google.firebase.dataconnect.testutil.property.arbitrary.filterNotIncludesAllMatchingAnyScalars
import com.google.firebase.dataconnect.testutil.property.arbitrary.filterNotNull
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.junit.Test

class AnyScalarIntegrationTest : DemoConnectorIntegrationTestBase() {

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for inserting into and querying this table:
  // type AnyScalarNonNullable @table { value: Any!, tag: String, position: Int }
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun anyScalarNonNullable_MutationVariableEdgeCases() =
    runTest(timeout = 60.seconds) {
      assertSoftly {
        for (value in EdgeCases.anyScalar.all.filterNotNull()) {
          withClue("value=$value") { verifyAnyScalarNonNullableRoundTrip(value) }
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
          withClue("value=$value otherValues=$otherValues") {
            verifyAnyScalarNonNullableQueryVariable(value, otherValues.next(), otherValues.next())
          }
        }
      }
    }

  @Test
  fun anyScalarNonNullable_MutationVariableNormalCases() =
    runTest(timeout = 60.seconds) {
      checkAll(normalCasePropTestConfig, Arb.dataConnect.anyScalar.any().filterNotNull()) { value ->
        verifyAnyScalarNonNullableRoundTrip(value)
      }
    }

  @Test
  fun anyScalarNonNullable_QueryVariableNormalCases() =
    runTest(timeout = 60.seconds) {
      checkAll(normalCasePropTestConfig, Arb.dataConnect.anyScalar.any().filterNotNull()) { value ->
        val otherValues =
          Arb.dataConnect.anyScalar.any().filterNotNull().filterNotAnyScalarMatching(value)
        verifyAnyScalarNonNullableQueryVariable(value, otherValues.next(), otherValues.next())
      }
    }

  @Test
  fun anyScalarNonNullable_MutationFailsIfAnyVariableIsMissing() = runTest {
    connector.anyScalarNonNullableInsert.verifyFailsWithMissingVariableValue()
  }

  @Test
  fun anyScalarNonNullable_QueryFailsIfAnyVariableIsMissing() = runTest {
    connector.anyScalarNonNullableGetAllByTagAndValue.verifyFailsWithMissingVariableValue()
  }

  @Test
  fun anyScalarNonNullable_MutationFailsIfAnyVariableIsNull() = runTest {
    connector.anyScalarNonNullableInsert.verifyFailsWithNullVariableValue()
  }

  @Test
  fun anyScalarNonNullable_QueryFailsIfAnyVariableIsNull() = runTest {
    connector.anyScalarNonNullableGetAllByTagAndValue.verifyFailsWithNullVariableValue()
  }

  private suspend fun verifyAnyScalarNonNullableRoundTrip(value: Any) {
    val anyValue = AnyValue.fromAny(value)
    val expectedQueryResult = AnyValue.fromAny(expectedAnyScalarRoundTripValue(value))
    val key = connector.anyScalarNonNullableInsert.execute(anyValue) {}.data.key

    val queryResult = connector.anyScalarNonNullableGetByKey.execute(key)
    queryResult.data shouldBe
      AnyScalarNonNullableGetByKeyQuery.Data(
        AnyScalarNonNullableGetByKeyQuery.Data.Item(expectedQueryResult)
      )
  }

  private suspend fun verifyAnyScalarNonNullableQueryVariable(
    value: Any,
    value2: Any,
    value3: Any,
  ) {
    require(value != value2)
    require(value != value3)
    require(expectedAnyScalarRoundTripValue(value) != expectedAnyScalarRoundTripValue(value2))
    require(expectedAnyScalarRoundTripValue(value) != expectedAnyScalarRoundTripValue(value3))

    val tag = UUID.randomUUID().toString()
    val anyValue = AnyValue.fromAny(value)
    val anyValue2 = AnyValue.fromAny(value2)
    val anyValue3 = AnyValue.fromAny(value3)
    val keys =
      connector.anyScalarNonNullableInsert3
        .execute(anyValue, anyValue2, anyValue3) { this.tag = tag }
        .data

    val queryResult =
      connector.anyScalarNonNullableGetAllByTagAndValue.execute(anyValue) { this.tag = tag }
    val queryIds = queryResult.data.items.map { it.id }
    queryIds.shouldContainExactlyInAnyOrder(keys.key1.id)
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
          withClue("value=$value") { verifyAnyScalarNullableRoundTrip(value) }
        }
      }
    }

  @Test
  fun anyScalarNullable_QueryVariableEdgeCases() =
    runTest(timeout = 60.seconds) {
      assertSoftly {
        for (value in EdgeCases.anyScalar.all) {
          val otherValues = Arb.dataConnect.anyScalar.any().filterNotAnyScalarMatching(value)
          withClue("value=$value otherValues=$otherValues") {
            verifyAnyScalarNullableQueryVariable(value, otherValues.next(), otherValues.next())
          }
        }
      }
    }

  @Test
  fun anyScalarNullable_MutationVariableNormalCases() =
    runTest(timeout = 60.seconds) {
      checkAll(normalCasePropTestConfig, Arb.dataConnect.anyScalar.any()) { value ->
        verifyAnyScalarNullableRoundTrip(value)
      }
    }

  @Test
  fun anyScalarNullable_QueryVariableNormalCases() =
    runTest(timeout = 60.seconds) {
      checkAll(normalCasePropTestConfig, Arb.dataConnect.anyScalar.any()) { value ->
        val otherValues = Arb.dataConnect.anyScalar.any().filterNotAnyScalarMatching(value)
        verifyAnyScalarNullableQueryVariable(value, otherValues.next(), otherValues.next())
      }
    }

  @Test
  fun anyScalarNullable_MutationSucceedsIfAnyVariableIsMissing() = runTest {
    val key = connector.anyScalarNullableInsert.execute {}.data.key
    val queryResult = connector.anyScalarNullableGetByKey.execute(key)
    queryResult.data.asClue { it.item?.value.shouldBeNull() }
  }

  @Test
  fun anyScalarNullable_QuerySucceedsIfAnyVariableIsMissing() = runTest {
    val values = Arb.dataConnect.anyScalar.any().map { AnyValue.fromAny(it) }
    val tag = UUID.randomUUID().toString()
    val keys =
      connector.anyScalarNullableInsert3
        .execute {
          this.tag = tag
          this.value1 = values.next()
          this.value2 = values.next()
          this.value3 = values.next()
        }
        .data
    val keyIds = listOf(keys.key1, keys.key2, keys.key3).map { it.id }

    val queryResult = connector.anyScalarNullableGetAllByTagAndValue.execute { this.tag = tag }
    val queryIds = queryResult.data.items.map { it.id }
    queryIds shouldContainExactlyInAnyOrder keyIds
  }

  @Test
  fun anyScalarNullable_MutationSucceedsIfAnyVariableIsNull() = runTest {
    val key = connector.anyScalarNullableInsert.execute { value = null }.data.key
    val queryResult = connector.anyScalarNullableGetByKey.execute(key)
    queryResult.data.asClue { it.item?.value.shouldBeNull() }
  }

  @Test
  fun anyScalarNullable_QuerySucceedsIfAnyVariableIsNull() = runTest {
    val values = Arb.dataConnect.anyScalar.any().filter { it !== null }.map { AnyValue.fromAny(it) }
    val tag = UUID.randomUUID().toString()
    val keys =
      connector.anyScalarNullableInsert3
        .execute {
          this.tag = tag
          this.value1 = null
          this.value2 = values.next()
          this.value3 = values.next()
        }
        .data

    val queryResult =
      connector.anyScalarNullableGetAllByTagAndValue.execute {
        this.tag = tag
        this.value = null
      }

    val queryIds = queryResult.data.items.map { it.id }
    queryIds.shouldContainExactlyInAnyOrder(keys.key1.id)
  }

  private suspend fun verifyAnyScalarNullableRoundTrip(value: Any?) {
    val anyValue = AnyValue.fromAny(value)
    val expectedQueryResult = AnyValue.fromAny(expectedAnyScalarRoundTripValue(value))
    val key = connector.anyScalarNullableInsert.execute { this.value = anyValue }.data.key

    val queryResult = connector.anyScalarNullableGetByKey.execute(key)
    queryResult.data shouldBe
      AnyScalarNullableGetByKeyQuery.Data(
        AnyScalarNullableGetByKeyQuery.Data.Item(expectedQueryResult)
      )
  }

  private suspend fun verifyAnyScalarNullableQueryVariable(
    value: Any?,
    value2: Any?,
    value3: Any?
  ) {
    require(value != value2)
    require(value != value3)
    require(expectedAnyScalarRoundTripValue(value) != expectedAnyScalarRoundTripValue(value2))
    require(expectedAnyScalarRoundTripValue(value) != expectedAnyScalarRoundTripValue(value3))

    val tag = UUID.randomUUID().toString()
    val anyValue = AnyValue.fromAny(value)
    val anyValue2 = AnyValue.fromAny(value2)
    val anyValue3 = AnyValue.fromAny(value3)
    val keys =
      connector.anyScalarNullableInsert3
        .execute {
          this.tag = tag
          this.value1 = anyValue
          this.value2 = anyValue2
          this.value3 = anyValue3
        }
        .data

    val queryResult =
      connector.anyScalarNullableGetAllByTagAndValue.execute {
        this.value = anyValue
        this.tag = tag
      }
    val queryIds = queryResult.data.items.map { it.id }
    queryIds.shouldContainExactlyInAnyOrder(keys.key1.id)
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
          withClue("value=$value") { verifyAnyScalarNullableListOfNullableRoundTrip(value) }
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
            verifyAnyScalarNullableListOfNullableQueryVariable(
              value,
              curOtherValues.next(),
              curOtherValues.next()
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
        verifyAnyScalarNullableListOfNullableRoundTrip(value)
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
        verifyAnyScalarNullableListOfNullableQueryVariable(
          value,
          curOtherValues.next(),
          curOtherValues.next()
        )
      }
    }

  @Test
  fun anyScalarNullableListOfNullable_MutationSucceedsIfAnyVariableIsMissing() = runTest {
    val key = connector.anyScalarNullableListOfNullableInsert.execute {}.data.key
    val queryResult = connector.anyScalarNullableListOfNullableGetByKey.execute(key)
    queryResult.data.asClue { it.item?.value.shouldBeNull() }
  }

  @Test
  fun anyScalarNullableListOfNullable_QuerySucceedsIfAnyVariableIsMissing() = runTest {
    val values =
      Arb.dataConnect.anyScalar.list().map { it.filterNotNull() }.map { it.map(AnyValue::fromAny) }
    val tag = UUID.randomUUID().toString()
    val keys =
      connector.anyScalarNullableListOfNullableInsert3
        .execute {
          this.tag = tag
          this.value1 = null
          this.value2 = emptyList()
          this.value3 = values.next()
        }
        .data
    val keyIds = listOf(keys.key1, keys.key2, keys.key3).map { it.id }

    val queryResult =
      connector.anyScalarNullableListOfNullableGetAllByTagAndValue.execute { this.tag = tag }
    val queryIds = queryResult.data.items.map { it.id }
    queryIds shouldContainExactlyInAnyOrder keyIds
  }

  @Test
  fun anyScalarNullableListOfNullable_MutationSucceedsIfAnyVariableIsNull() = runTest {
    val key = connector.anyScalarNullableListOfNullableInsert.execute { value = null }.data.key
    val queryResult = connector.anyScalarNullableListOfNullableGetByKey.execute(key)
    queryResult.data.asClue { it.item?.value.shouldBeNull() }
  }

  @Test
  fun anyScalarNullableListOfNullable_QuerySucceedsIfAnyVariableIsNull() = runTest {
    val values =
      Arb.dataConnect.anyScalar.list().map { it.filterNotNull() }.map { it.map(AnyValue::fromAny) }
    val tag = UUID.randomUUID().toString()
    connector.anyScalarNullableListOfNullableInsert3.execute {
      this.tag = tag
      this.value1 = null
      this.value2 = emptyList()
      this.value3 = values.next()
    }

    val queryResult =
      connector.anyScalarNullableListOfNullableGetAllByTagAndValue.execute {
        this.tag = tag
        this.value = null
      }
    val queryIds = queryResult.data.items.map { it.id }
    queryIds.shouldBeEmpty()
  }

  @Test
  fun anyScalarNullableListOfNullable_QuerySucceedsIfAnyVariableIsEmpty() = runTest {
    val values =
      Arb.dataConnect.anyScalar.list().map { it.filterNotNull() }.map { it.map(AnyValue::fromAny) }
    val tag = UUID.randomUUID().toString()
    val keys =
      connector.anyScalarNullableListOfNullableInsert3
        .execute {
          this.tag = tag
          this.value1 = null
          this.value2 = emptyList()
          this.value3 = values.next()
        }
        .data

    val queryResult =
      connector.anyScalarNullableListOfNullableGetAllByTagAndValue.execute {
        this.tag = tag
        this.value = emptyList()
      }
    val queryIds = queryResult.data.items.map { it.id }
    queryIds.shouldContainExactlyInAnyOrder(keys.key2.id, keys.key3.id)
  }

  private suspend fun verifyAnyScalarNullableListOfNullableRoundTrip(value: List<Any>?) {
    val anyValue = value?.map { AnyValue.fromAny(it) }
    val expectedQueryResult = value?.map { AnyValue.fromAny(expectedAnyScalarRoundTripValue(it)) }
    val key =
      connector.anyScalarNullableListOfNullableInsert.execute { this.value = anyValue }.data.key

    val queryResult = connector.anyScalarNullableListOfNullableGetByKey.execute(key)
    queryResult.data shouldBe
      AnyScalarNullableListOfNullableGetByKeyQuery.Data(
        AnyScalarNullableListOfNullableGetByKeyQuery.Data.Item(expectedQueryResult)
      )
  }

  private suspend fun verifyAnyScalarNullableListOfNullableQueryVariable(
    value: List<Any>?,
    value2: List<Any>?,
    value3: List<Any>?,
  ) {
    require(value != value2)
    require(value != value3)
    // TODO: implement a check to ensure that value is not a subset of value2 and value3.

    val tag = UUID.randomUUID().toString()
    val anyValue = value?.map(AnyValue::fromAny)
    val anyValue2 = value2?.map(AnyValue::fromAny)
    val anyValue3 = value3?.map(AnyValue::fromAny)
    val keys =
      connector.anyScalarNullableListOfNullableInsert3
        .execute {
          this.tag = tag
          this.value1 = anyValue
          this.value2 = anyValue2
          this.value3 = anyValue3
        }
        .data

    val queryResult =
      connector.anyScalarNullableListOfNullableGetAllByTagAndValue.execute {
        this.value = anyValue
        this.tag = tag
      }
    val queryIds = queryResult.data.items.map { it.id }
    queryIds.shouldContainExactlyInAnyOrder(keys.key1.id)
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
          withClue("value=$value") { verifyAnyScalarNullableListOfNonNullableRoundTrip(value) }
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
            verifyAnyScalarNullableListOfNonNullableQueryVariable(
              value,
              curOtherValues.next(),
              curOtherValues.next()
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
        verifyAnyScalarNullableListOfNonNullableRoundTrip(value)
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
        verifyAnyScalarNullableListOfNonNullableQueryVariable(
          value,
          curOtherValues.next(),
          curOtherValues.next()
        )
      }
    }

  @Test
  fun anyScalarNullableListOfNonNullable_MutationSucceedsIfAnyVariableIsMissing() = runTest {
    val key = connector.anyScalarNullableListOfNonNullableInsert.execute {}.data.key
    val queryResult = connector.anyScalarNullableListOfNonNullableGetByKey.execute(key)
    queryResult.data.asClue { it.item?.value.shouldBeNull() }
  }

  @Test
  fun anyScalarNullableListOfNonNullable_QuerySucceedsIfAnyVariableIsMissing() = runTest {
    val values =
      Arb.dataConnect.anyScalar.list().map { it.filterNotNull() }.map { it.map(AnyValue::fromAny) }
    val tag = UUID.randomUUID().toString()
    val keys =
      connector.anyScalarNullableListOfNonNullableInsert3
        .execute {
          this.tag = tag
          this.value1 = null
          this.value2 = emptyList()
          this.value3 = values.next()
        }
        .data
    val keyIds = listOf(keys.key1, keys.key2, keys.key3).map { it.id }

    val queryResult =
      connector.anyScalarNullableListOfNonNullableGetAllByTagAndValue.execute { this.tag = tag }
    val queryIds = queryResult.data.items.map { it.id }
    queryIds shouldContainExactlyInAnyOrder keyIds
  }

  @Test
  fun anyScalarNullableListOfNonNullable_MutationSucceedsIfAnyVariableIsNull() = runTest {
    val key = connector.anyScalarNullableListOfNonNullableInsert.execute { value = null }.data.key
    val queryResult = connector.anyScalarNullableListOfNonNullableGetByKey.execute(key)
    queryResult.data.asClue { it.item?.value.shouldBeNull() }
  }

  @Test
  fun anyScalarNullableListOfNonNullable_QuerySucceedsIfAnyVariableIsNull() = runTest {
    val values =
      Arb.dataConnect.anyScalar.list().map { it.filterNotNull() }.map { it.map(AnyValue::fromAny) }
    val tag = UUID.randomUUID().toString()
    connector.anyScalarNullableListOfNonNullableInsert3.execute {
      this.tag = tag
      this.value1 = null
      this.value2 = emptyList()
      this.value3 = values.next()
    }

    val queryResult =
      connector.anyScalarNullableListOfNonNullableGetAllByTagAndValue.execute {
        this.tag = tag
        this.value = null
      }
    val queryIds = queryResult.data.items.map { it.id }
    queryIds.shouldBeEmpty()
  }

  @Test
  fun anyScalarNullableListOfNonNullable_QuerySucceedsIfAnyVariableIsEmpty() = runTest {
    val values =
      Arb.dataConnect.anyScalar.list().map { it.filterNotNull() }.map { it.map(AnyValue::fromAny) }
    val tag = UUID.randomUUID().toString()
    val keys =
      connector.anyScalarNullableListOfNonNullableInsert3
        .execute {
          this.tag = tag
          this.value1 = null
          this.value2 = emptyList()
          this.value3 = values.next()
        }
        .data

    val queryResult =
      connector.anyScalarNullableListOfNonNullableGetAllByTagAndValue.execute {
        this.tag = tag
        this.value = emptyList()
      }
    val queryIds = queryResult.data.items.map { it.id }
    queryIds.shouldContainExactlyInAnyOrder(keys.key2.id, keys.key3.id)
  }

  private suspend fun verifyAnyScalarNullableListOfNonNullableRoundTrip(value: List<Any>?) {
    val anyValue = value?.map { AnyValue.fromAny(it) }
    val expectedQueryResult = value?.map { AnyValue.fromAny(expectedAnyScalarRoundTripValue(it)) }
    val key =
      connector.anyScalarNullableListOfNonNullableInsert.execute { this.value = anyValue }.data.key

    val queryResult = connector.anyScalarNullableListOfNonNullableGetByKey.execute(key)
    queryResult.data shouldBe
      AnyScalarNullableListOfNonNullableGetByKeyQuery.Data(
        AnyScalarNullableListOfNonNullableGetByKeyQuery.Data.Item(expectedQueryResult)
      )
  }

  private suspend fun verifyAnyScalarNullableListOfNonNullableQueryVariable(
    value: List<Any>?,
    value2: List<Any>?,
    value3: List<Any>?,
  ) {
    require(value != value2)
    require(value != value3)
    // TODO: implement a check to ensure that value is not a subset of value2 and value3.

    val tag = UUID.randomUUID().toString()
    val anyValue = value?.map(AnyValue::fromAny)
    val anyValue2 = value2?.map(AnyValue::fromAny)
    val anyValue3 = value3?.map(AnyValue::fromAny)
    val keys =
      connector.anyScalarNullableListOfNonNullableInsert3
        .execute {
          this.tag = tag
          this.value1 = anyValue
          this.value2 = anyValue2
          this.value3 = anyValue3
        }
        .data

    val queryResult =
      connector.anyScalarNullableListOfNonNullableGetAllByTagAndValue.execute {
        this.value = anyValue
        this.tag = tag
      }
    val queryIds = queryResult.data.items.map { it.id }
    queryIds.shouldContainExactlyInAnyOrder(keys.key1.id)
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for inserting into and querying this table:
  // type AnyScalarNonNullableListOfNullable @table { value: [Any]!, tag: String, position: Int }
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun anyScalarNonNullableListOfNullable_MutationVariableEdgeCases() =
    runTest(timeout = 60.seconds) {
      assertSoftly {
        val edgeCases = EdgeCases.anyScalar.lists.map { it.filterNotNull() }
        for (value in edgeCases) {
          withClue("value=$value") { verifyAnyScalarNonNullableListOfNullableRoundTrip(value) }
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
            verifyAnyScalarNonNullableListOfNullableQueryVariable(
              value,
              curOtherValues.next(),
              curOtherValues.next()
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
        verifyAnyScalarNonNullableListOfNullableRoundTrip(value)
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
        verifyAnyScalarNonNullableListOfNullableQueryVariable(
          value,
          curOtherValues.next(),
          curOtherValues.next()
        )
      }
    }

  @Test
  fun anyScalarNonNullableListOfNullable_MutationFailsIfAnyVariableIsMissing() = runTest {
    connector.anyScalarNonNullableListOfNullableInsert.verifyFailsWithMissingVariableValue()
  }

  @Test
  fun anyScalarNonNullableListOfNullable_QueryFailsIfAnyVariableIsMissing() = runTest {
    connector.anyScalarNonNullableListOfNullableGetAllByTagAndValue
      .verifyFailsWithMissingVariableValue()
  }

  @Test
  fun anyScalarNonNullableListOfNullable_MutationFailsIfAnyVariableIsNull() = runTest {
    connector.anyScalarNonNullableListOfNullableInsert.verifyFailsWithNullVariableValue()
  }

  @Test
  fun anyScalarNonNullableListOfNullable_QueryFailsIfAnyVariableIsNull() = runTest {
    connector.anyScalarNonNullableListOfNullableGetAllByTagAndValue
      .verifyFailsWithNullVariableValue()
  }

  @Test
  fun anyScalarNonNullableListOfNullable_QuerySucceedsIfAnyVariableIsEmpty() = runTest {
    val values =
      Arb.dataConnect.anyScalar.list().map { it.filterNotNull() }.map { it.map(AnyValue::fromAny) }
    val tag = UUID.randomUUID().toString()
    val keys =
      connector.anyScalarNonNullableListOfNullableInsert3
        .execute(value1 = emptyList(), value2 = values.next(), value3 = values.next()) {
          this.tag = tag
        }
        .data

    val queryResult =
      connector.anyScalarNonNullableListOfNullableGetAllByTagAndValue.execute(value = emptyList()) {
        this.tag = tag
      }
    val queryIds = queryResult.data.items.map { it.id }
    queryIds.shouldContainExactlyInAnyOrder(keys.key1.id, keys.key2.id, keys.key3.id)
  }

  private suspend fun verifyAnyScalarNonNullableListOfNullableRoundTrip(value: List<Any>) {
    val anyValue = value.map { AnyValue.fromAny(it) }
    val expectedQueryResult = value.map { AnyValue.fromAny(expectedAnyScalarRoundTripValue(it)) }
    val key = connector.anyScalarNonNullableListOfNullableInsert.execute(anyValue) {}.data.key

    val queryResult = connector.anyScalarNonNullableListOfNullableGetByKey.execute(key)
    queryResult.data shouldBe
      AnyScalarNonNullableListOfNullableGetByKeyQuery.Data(
        AnyScalarNonNullableListOfNullableGetByKeyQuery.Data.Item(expectedQueryResult)
      )
  }

  private suspend fun verifyAnyScalarNonNullableListOfNullableQueryVariable(
    value: List<Any>,
    value2: List<Any>,
    value3: List<Any>,
  ) {
    require(value != value2)
    require(value != value3)
    // TODO: implement a check to ensure that value is not a subset of value2 and value3.

    val tag = UUID.randomUUID().toString()
    val anyValue = value.map(AnyValue::fromAny)
    val anyValue2 = value2.map(AnyValue::fromAny)
    val anyValue3 = value3.map(AnyValue::fromAny)
    val keys =
      connector.anyScalarNonNullableListOfNullableInsert3
        .execute(value1 = anyValue, value2 = anyValue2, value3 = anyValue3) { this.tag = tag }
        .data

    val queryResult =
      connector.anyScalarNonNullableListOfNullableGetAllByTagAndValue.execute(anyValue) {
        this.tag = tag
      }
    val queryIds = queryResult.data.items.map { it.id }
    queryIds.shouldContainExactlyInAnyOrder(keys.key1.id)
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
        val edgeCases = EdgeCases.anyScalar.lists.map { it.filterNotNull() }
        for (value in edgeCases) {
          withClue("value=$value") { verifyAnyScalarNonNullableListOfNonNullableRoundTrip(value) }
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
            verifyAnyScalarNonNullableListOfNonNullableQueryVariable(
              value,
              curOtherValues.next(),
              curOtherValues.next()
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
        verifyAnyScalarNonNullableListOfNonNullableRoundTrip(value)
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
        verifyAnyScalarNonNullableListOfNonNullableQueryVariable(
          value,
          curOtherValues.next(),
          curOtherValues.next()
        )
      }
    }

  @Test
  fun anyScalarNonNullableListOfNonNullable_MutationFailsIfAnyVariableIsMissing() = runTest {
    connector.anyScalarNonNullableListOfNonNullableInsert.verifyFailsWithMissingVariableValue()
  }

  @Test
  fun anyScalarNonNullableListOfNonNullable_QueryFailsIfAnyVariableIsMissing() = runTest {
    connector.anyScalarNonNullableListOfNonNullableGetAllByTagAndValue
      .verifyFailsWithMissingVariableValue()
  }

  @Test
  fun anyScalarNonNullableListOfNonNullable_MutationFailsIfAnyVariableIsNull() = runTest {
    connector.anyScalarNonNullableListOfNonNullableInsert.verifyFailsWithNullVariableValue()
  }

  @Test
  fun anyScalarNonNullableListOfNonNullable_QueryFailsIfAnyVariableIsNull() = runTest {
    connector.anyScalarNonNullableListOfNonNullableGetAllByTagAndValue
      .verifyFailsWithNullVariableValue()
  }

  @Test
  fun anyScalarNonNullableListOfNonNullable_QuerySucceedsIfAnyVariableIsEmpty() = runTest {
    val values =
      Arb.dataConnect.anyScalar.list().map { it.filterNotNull() }.map { it.map(AnyValue::fromAny) }
    val tag = UUID.randomUUID().toString()
    val keys =
      connector.anyScalarNonNullableListOfNonNullableInsert3
        .execute(value1 = emptyList(), value2 = values.next(), value3 = values.next()) {
          this.tag = tag
        }
        .data

    val queryResult =
      connector.anyScalarNonNullableListOfNonNullableGetAllByTagAndValue.execute(
        value = emptyList()
      ) {
        this.tag = tag
      }
    val queryIds = queryResult.data.items.map { it.id }
    queryIds.shouldContainExactlyInAnyOrder(keys.key1.id, keys.key2.id, keys.key3.id)
  }

  private suspend fun verifyAnyScalarNonNullableListOfNonNullableRoundTrip(value: List<Any>) {
    val anyValue = value.map { AnyValue.fromAny(it) }
    val expectedQueryResult = value.map { AnyValue.fromAny(expectedAnyScalarRoundTripValue(it)) }
    val key = connector.anyScalarNonNullableListOfNonNullableInsert.execute(anyValue) {}.data.key

    val queryResult = connector.anyScalarNonNullableListOfNonNullableGetByKey.execute(key)
    queryResult.data shouldBe
      AnyScalarNonNullableListOfNonNullableGetByKeyQuery.Data(
        AnyScalarNonNullableListOfNonNullableGetByKeyQuery.Data.Item(expectedQueryResult)
      )
  }

  private suspend fun verifyAnyScalarNonNullableListOfNonNullableQueryVariable(
    value: List<Any>,
    value2: List<Any>,
    value3: List<Any>,
  ) {
    require(value != value2)
    require(value != value3)
    // TODO: implement a check to ensure that value is not a subset of value2 and value3.

    val tag = UUID.randomUUID().toString()
    val anyValue = value.map(AnyValue::fromAny)
    val anyValue2 = value2.map(AnyValue::fromAny)
    val anyValue3 = value3.map(AnyValue::fromAny)
    val keys =
      connector.anyScalarNonNullableListOfNonNullableInsert3
        .execute(value1 = anyValue, value2 = anyValue2, value3 = anyValue3) { this.tag = tag }
        .data

    val queryResult =
      connector.anyScalarNonNullableListOfNonNullableGetAllByTagAndValue.execute(anyValue) {
        this.tag = tag
      }
    val queryIds = queryResult.data.items.map { it.id }
    queryIds.shouldContainExactlyInAnyOrder(keys.key1.id)
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // End of tests; everything below is helper functions and classes.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Serializable private data class VariablesWithNullValue(val value: String?)

  private companion object {

    @OptIn(ExperimentalKotest::class)
    val normalCasePropTestConfig =
      PropTestConfig(iterations = 5, edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.0))

    suspend fun GeneratedMutation<*, *, *>.verifyFailsWithMissingVariableValue() {
      val mutationRef =
        connector.dataConnect.mutation(
          operationName = operationName,
          variables = Unit,
          dataDeserializer = dataDeserializer,
          variablesSerializer = serializer<Unit>(),
        )
      mutationRef.verifyExecuteFailsDueToMissingVariable()
    }

    suspend fun GeneratedQuery<*, *, *>.verifyFailsWithMissingVariableValue() {
      val queryRef =
        connector.dataConnect.query(
          operationName = operationName,
          variables = Unit,
          dataDeserializer = dataDeserializer,
          variablesSerializer = serializer<Unit>(),
        )
      queryRef.verifyExecuteFailsDueToMissingVariable()
    }

    suspend fun OperationRef<*, *>.verifyExecuteFailsDueToMissingVariable() {
      val exception = shouldThrow<DataConnectException> { execute() }
      exception.message shouldContainWithNonAbuttingTextIgnoringCase "\$value"
      exception.message shouldContainWithNonAbuttingTextIgnoringCase "is missing"
    }

    suspend fun GeneratedMutation<*, *, *>.verifyFailsWithNullVariableValue() {
      val mutationRef =
        connector.dataConnect.mutation(
          operationName = operationName,
          variables = VariablesWithNullValue(null),
          dataDeserializer = dataDeserializer,
          variablesSerializer = serializer<VariablesWithNullValue>(),
        )

      mutationRef.verifyExecuteFailsDueToNullVariable()
    }

    suspend fun GeneratedQuery<*, *, *>.verifyFailsWithNullVariableValue() {
      val queryRef =
        connector.dataConnect.query(
          operationName = operationName,
          variables = VariablesWithNullValue(null),
          dataDeserializer = dataDeserializer,
          variablesSerializer = serializer<VariablesWithNullValue>(),
        )

      queryRef.verifyExecuteFailsDueToNullVariable()
    }

    suspend fun OperationRef<*, *>.verifyExecuteFailsDueToNullVariable() {
      val exception = shouldThrow<DataConnectException> { execute() }
      exception.message shouldContainWithNonAbuttingTextIgnoringCase "\$value"
      exception.message shouldContainWithNonAbuttingTextIgnoringCase "is null"
    }
  }
}
