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

@file:OptIn(ExperimentalKotest::class, ExperimentalFirebaseDataConnect::class)

package com.google.firebase.dataconnect.connectors.demo

import com.google.firebase.dataconnect.DataConnectException
import com.google.firebase.dataconnect.ExperimentalFirebaseDataConnect
import com.google.firebase.dataconnect.LocalDate
import com.google.firebase.dataconnect.MutationResult
import com.google.firebase.dataconnect.QueryResult
import com.google.firebase.dataconnect.connectors.demo.testutil.DemoConnectorIntegrationTestBase
import com.google.firebase.dataconnect.generated.GeneratedQuery
import com.google.firebase.dataconnect.testutil.property.arbitrary.DateTestData
import com.google.firebase.dataconnect.testutil.property.arbitrary.EdgeCases
import com.google.firebase.dataconnect.testutil.property.arbitrary.ThreeDateTestDatas
import com.google.firebase.dataconnect.testutil.property.arbitrary.ThreeDateTestDatas.ItemNumber
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.dateTestData
import com.google.firebase.dataconnect.testutil.property.arbitrary.invalidDateScalarString
import com.google.firebase.dataconnect.testutil.property.arbitrary.localDate
import com.google.firebase.dataconnect.testutil.property.arbitrary.orNullableReference
import com.google.firebase.dataconnect.testutil.property.arbitrary.threeNonNullDatesTestData
import com.google.firebase.dataconnect.testutil.property.arbitrary.threePossiblyNullDatesTestData
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import com.google.firebase.dataconnect.testutil.toTheeTenAbpJavaLocalDate
import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.withEdgecases
import io.kotest.property.checkAll
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.junit.Test

class DateScalarIntegrationTest : DemoConnectorIntegrationTestBase() {

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for CRUD operations on this table:
  // type DateNonNullable @table { value: Date!, tag: String }
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun dateNonNullable_MutationLocalDateVariable() =
    runTest(timeout = 1.minutes) {
      checkAll(propTestConfig, Arb.dataConnect.dateTestData()) { testData ->
        val insertResult = connector.dateNonNullableInsert.execute(testData.date)
        val returnedString =
          connector.dateNonNullableGetByKey.executeWithStringData(insertResult.data.key)
        returnedString shouldBe testData.string
      }
    }

  @Test
  fun dateNonNullable_MutationStringVariable() =
    runTest(timeout = 1.minutes) {
      checkAll(propTestConfig, Arb.dataConnect.dateTestData()) { testData ->
        val insertResult = connector.dateNonNullableInsert.execute(testData.string)
        val queryResult = connector.dateNonNullableGetByKey.execute(insertResult.data.key)
        queryResult.data.item?.value shouldBe testData.date
      }
    }

  @Test
  fun dateNonNullable_QueryLocalDateVariable() =
    dateNonNullable_QueryVariable { tag, dateTestData ->
      connector.dateNonNullableGetAllByTagAndValue.execute(tag = tag, dateTestData.date)
    }

  @Test
  fun dateNonNullable_QueryStringVariable() = dateNonNullable_QueryVariable { tag, dateTestData ->
    connector.dateNonNullableGetAllByTagAndValue.execute(tag = tag, value = dateTestData.string)
  }

  private fun dateNonNullable_QueryVariable(
    executeQuery:
      suspend (tag: String, date: DateTestData) -> QueryResult<
          DateNonNullableGetAllByTagAndValueQuery.Data, *
        >
  ) =
    runTest(timeout = 1.minutes) {
      checkAll(
        propTestConfig,
        Arb.dataConnect.tag(),
        Arb.dataConnect.threeNonNullDatesTestData()
      ) { tag, testDatas ->
        val insertResult = connector.dateNonNullableInsert3.execute(tag, testDatas)
        val queryResult = executeQuery(tag, testDatas.selected!!)
        val matchingIds = testDatas.idsMatchingSelected(insertResult)
        queryResult.data.items.map { it.id } shouldContainExactlyInAnyOrder matchingIds
      }
    }

  @Test
  fun dateNonNullable_MutationNullVariableShouldThrow() = runTest {
    val exception =
      shouldThrow<DataConnectException> { connector.dateNonNullableInsert.execute(null) }
    assertSoftly {
      exception.message shouldContainWithNonAbuttingText "\$value"
      exception.message shouldContainWithNonAbuttingText "is null"
    }
  }

  @Test
  fun dateNonNullable_QueryNullVariableShouldThrow() = runTest {
    val tag = Arb.dataConnect.tag().next(rs)
    val exception =
      shouldThrow<DataConnectException> {
        connector.dateNonNullableGetAllByTagAndValue.execute(tag = tag, value = null)
      }
    assertSoftly {
      exception.message shouldContainWithNonAbuttingText "\$value"
      exception.message shouldContainWithNonAbuttingText "is null"
    }
  }

  @Test
  fun dateNonNullable_QueryOmittedVariableShouldMatchAll() = runTest {
    val tag = Arb.dataConnect.tag().next(rs)
    val testDatas = Arb.dataConnect.threeNonNullDatesTestData().next(rs)
    val insertResult = connector.dateNonNullableInsert3.execute(tag, testDatas)
    val queryResult = connector.dateNonNullableGetAllByTagAndMaybeValue.execute(tag) {}
    queryResult.data.items
      .map { it.id }
      .shouldContainExactlyInAnyOrder(
        insertResult.data.key1.id,
        insertResult.data.key2.id,
        insertResult.data.key3.id
      )
  }

  @Test
  fun dateNonNullable_QueryNullVariableShouldMatchNone() = runTest {
    val tag = Arb.dataConnect.tag().next(rs)
    val testDatas = Arb.dataConnect.threeNonNullDatesTestData().next(rs)
    connector.dateNonNullableInsert3.execute(tag, testDatas)
    val queryResult =
      connector.dateNonNullableGetAllByTagAndMaybeValue.execute(tag) { value = null }
    queryResult.data.items.shouldBeEmpty()
  }

  @Test
  fun dateNonNullable_Update() =
    runTest(timeout = 1.minutes) {
      checkAll(propTestConfig, Arb.dataConnect.localDate(), Arb.dataConnect.localDate()) {
        date1,
        date2 ->
        val insertResult = connector.dateNonNullableInsert.execute(date1)
        val updateResult =
          connector.dateNonNullableUpdateByKey.execute(insertResult.data.key) { value = date2 }
        updateResult.asClue { it.data.key shouldBe insertResult.data.key }
        val queryResult = connector.dateNonNullableGetByKey.execute(insertResult.data.key)
        val item = withClue("queryResult.data.item") { queryResult.data.item.shouldNotBeNull() }
        item.value shouldBe date2
      }
    }

  @Test
  fun dateNonNullable_UpdateToNullShouldFail() =
    runTest(timeout = 1.minutes) {
      checkAll(propTestConfig, Arb.dataConnect.localDate()) { date ->
        val insertResult = connector.dateNonNullableInsert.execute(date)
        shouldThrow<DataConnectException> {
          connector.dateNonNullableUpdateByKey.execute(insertResult.data.key) { value = null }
        }
      }
    }

  @Test
  fun dateNonNullable_UpdateToOmittedShouldLeaveValueUnchanged() =
    runTest(timeout = 1.minutes) {
      checkAll(propTestConfig, Arb.dataConnect.localDate()) { date ->
        val insertResult = connector.dateNonNullableInsert.execute(date)
        val updateResult = connector.dateNonNullableUpdateByKey.execute(insertResult.data.key) {}
        updateResult.asClue { it.data.key shouldBe insertResult.data.key }
        val queryResult = connector.dateNonNullableGetByKey.execute(insertResult.data.key)
        val item = withClue("queryResult.data.item") { queryResult.data.item.shouldNotBeNull() }
        item.value shouldBe date
      }
    }

  @Test
  fun dateNonNullable_UpdateMany() =
    runTest(timeout = 1.minutes) {
      checkAll(
        propTestConfig,
        Arb.dataConnect.tag(),
        Arb.dataConnect.threeNonNullDatesTestData(),
        Arb.dataConnect.localDate()
      ) { tag, testDatas, date2 ->
        val insertResult = connector.dateNonNullableInsert3.execute(tag, testDatas)
        val selectedDate = testDatas.selected!!
        val updateResult =
          connector.dateNonNullableUpdateByTagAndValue.execute(tag) {
            value = selectedDate.date
            newValue = date2
          }
        withClue("updateResult.data.count") {
          updateResult.data.count shouldBe testDatas.numMatchingSelected
        }
        val queryResult = connector.dateNonNullableGetAllByTagAndValue.execute(tag, date2)
        val matchingIds1 = testDatas.idsMatchingSelected(insertResult)
        val matchingIds2 = testDatas.idsMatching(insertResult, date2)
        val matchingIds = (matchingIds1 + matchingIds2).distinct()
        queryResult.data.items.map { it.id } shouldContainExactlyInAnyOrder matchingIds
      }
    }

  @Test
  fun dateNonNullable_UpdateManyNullValueShouldUpdateNone() =
    runTest(timeout = 1.minutes) {
      checkAll(
        propTestConfig,
        Arb.dataConnect.tag(),
        Arb.dataConnect.threeNonNullDatesTestData(),
        Arb.dataConnect.localDate()
      ) { tag, testDatas, date2 ->
        val insertResult = connector.dateNonNullableInsert3.execute(tag, testDatas)
        val updateResult =
          connector.dateNonNullableUpdateByTagAndValue.execute(tag) {
            value = null
            newValue = date2
          }
        withClue("updateResult.data.count") { updateResult.data.count shouldBe 0 }
        val queryResult = connector.dateNonNullableGetAllByTagAndMaybeValue.execute(tag) {}
        queryResult.data.items
          .map { it.id }
          .shouldContainExactlyInAnyOrder(
            insertResult.data.key1.id,
            insertResult.data.key2.id,
            insertResult.data.key3.id,
          )
      }
    }

  @Test
  fun dateNonNullable_UpdateManyNullNewValueShouldThrow() =
    runTest(timeout = 1.minutes) {
      checkAll(
        propTestConfig,
        Arb.dataConnect.tag(),
        Arb.dataConnect.threeNonNullDatesTestData(),
      ) { tag, testDatas ->
        connector.dateNonNullableInsert3.execute(tag, testDatas)
        shouldThrow<DataConnectException> {
          connector.dateNonNullableUpdateByTagAndValue.execute(tag) { newValue = null }
        }
      }
    }

  @Test
  fun dateNonNullable_UpdateManyOmittedValueShouldUpdateAll() =
    runTest(timeout = 1.minutes) {
      checkAll(
        propTestConfig,
        Arb.dataConnect.tag(),
        Arb.dataConnect.threeNonNullDatesTestData(),
        Arb.dataConnect.localDate()
      ) { tag, testDatas, date2 ->
        val insertResult = connector.dateNonNullableInsert3.execute(tag, testDatas)
        val updateResult =
          connector.dateNonNullableUpdateByTagAndValue.execute(tag) { newValue = date2 }
        withClue("updateResult.data.count") { updateResult.data.count shouldBe 3 }
        val queryResult = connector.dateNonNullableGetAllByTagAndValue.execute(tag, date2)
        queryResult.data.items
          .map { it.id }
          .shouldContainExactlyInAnyOrder(
            insertResult.data.key1.id,
            insertResult.data.key2.id,
            insertResult.data.key3.id,
          )
      }
    }

  @Test
  fun dateNonNullable_UpdateManyOmittedNewValueShouldNotChangeAny() =
    runTest(timeout = 1.minutes) {
      checkAll(
        propTestConfig,
        Arb.dataConnect.tag(),
        Arb.dataConnect.threeNonNullDatesTestData()
      ) { tag, testDatas ->
        val insertResult = connector.dateNonNullableInsert3.execute(tag, testDatas)
        val selectedDate = testDatas.selected!!.date
        val updateResult =
          connector.dateNonNullableUpdateByTagAndValue.execute(tag) { value = selectedDate }
        withClue("updateResult.data.count") {
          updateResult.data.count shouldBe testDatas.numMatchingSelected
        }
        val queryResult = connector.dateNonNullableGetAllByTagAndValue.execute(tag, selectedDate)
        val matchingIds = testDatas.idsMatchingSelected(insertResult)
        queryResult.data.items.map { it.id } shouldContainExactlyInAnyOrder matchingIds
      }
    }

  @Test
  fun dateNonNullable_DeleteMany() =
    runTest(timeout = 1.minutes) {
      checkAll(
        propTestConfig,
        Arb.dataConnect.tag(),
        Arb.dataConnect.threeNonNullDatesTestData()
      ) { tag, testDatas ->
        val insertResult = connector.dateNonNullableInsert3.execute(tag, testDatas)
        val selectedDate = testDatas.selected!!.date
        val deleteResult =
          connector.dateNonNullableDeleteByTagAndValue.execute(tag) { value = selectedDate }
        withClue("deleteResult.data.count") {
          deleteResult.data.count shouldBe testDatas.numMatchingSelected
        }
        val queryResult = connector.dateNonNullableGetAllByTagAndMaybeValue.execute(tag) {}
        val insertedIds = insertResult.data.run { listOf(key1, key2, key3).map { it.id } }
        val matchingIds = testDatas.idsMatchingSelected(insertResult)
        val remainingIds = insertedIds.filterNot { it in matchingIds }
        queryResult.data.items.map { it.id } shouldContainExactlyInAnyOrder remainingIds
      }
    }

  @Test
  fun dateNonNullable_DeleteManyNullValueShouldDeleteNone() =
    runTest(timeout = 1.minutes) {
      checkAll(
        propTestConfig,
        Arb.dataConnect.tag(),
        Arb.dataConnect.threeNonNullDatesTestData()
      ) { tag, testDatas ->
        val insertResult = connector.dateNonNullableInsert3.execute(tag, testDatas)
        val deleteResult =
          connector.dateNonNullableDeleteByTagAndValue.execute(tag) { value = null }
        withClue("deleteResult.data.count") { deleteResult.data.count shouldBe 0 }
        val queryResult = connector.dateNonNullableGetAllByTagAndMaybeValue.execute(tag) {}
        queryResult.data.items
          .map { it.id }
          .shouldContainExactlyInAnyOrder(
            insertResult.data.key1.id,
            insertResult.data.key2.id,
            insertResult.data.key3.id,
          )
      }
    }

  @Test
  fun dateNonNullable_DeleteManyOmittedValueShouldDeleteAll() =
    runTest(timeout = 1.minutes) {
      checkAll(
        propTestConfig,
        Arb.dataConnect.tag(),
        Arb.dataConnect.threeNonNullDatesTestData(),
      ) { tag, testDatas ->
        connector.dateNonNullableInsert3.execute(tag, testDatas)
        val deleteResult = connector.dateNonNullableDeleteByTagAndValue.execute(tag) {}
        withClue("deleteResult.data.count") { deleteResult.data.count shouldBe 3 }
        val queryResult = connector.dateNonNullableGetAllByTagAndMaybeValue.execute(tag) {}
        queryResult.data.items.shouldBeEmpty()
      }
    }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for inserting into and querying this table:
  // type DateNullable @table { value: Date, tag: String }
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun dateNullable_MutationLocalDateVariable() =
    runTest(timeout = 1.minutes) {
      val localDates = Arb.dataConnect.dateTestData().orNullableReference(nullProbability = 0.2)
      checkAll(propTestConfig, localDates) { testData ->
        val insertResult = connector.dateNullableInsert.execute { value = testData.ref?.date }
        val returnedString =
          connector.dateNullableGetByKey.executeWithStringData(insertResult.data.key)
        returnedString shouldBe testData.ref?.string
      }
    }

  @Test
  fun dateNullable_MutationStringVariable() =
    runTest(timeout = 1.minutes) {
      val localDates = Arb.dataConnect.dateTestData().orNullableReference(nullProbability = 0.2)
      checkAll(propTestConfig, localDates) { testData ->
        val insertResult = connector.dateNullableInsert.execute(testData.ref?.string)
        val queryResult = connector.dateNullableGetByKey.execute(insertResult.data.key)
        queryResult.data.item?.value shouldBe testData.ref?.date
      }
    }

  @Test
  fun dateNullable_QueryLocalDateVariable() = dateNullable_QueryVariable { tag, dateTestData ->
    connector.dateNullableGetAllByTagAndValue.execute(tag = tag) { value = dateTestData?.date }
  }

  @Test
  fun dateNullable_QueryStringVariable() = dateNullable_QueryVariable { tag, dateTestData ->
    connector.dateNullableGetAllByTagAndValue.execute(tag = tag, value = dateTestData?.string)
  }

  private fun dateNullable_QueryVariable(
    executeQuery:
      suspend (tag: String, date: DateTestData?) -> QueryResult<
          DateNullableGetAllByTagAndValueQuery.Data, *
        >
  ) =
    runTest(timeout = 1.minutes) {
      checkAll(
        propTestConfig,
        Arb.dataConnect.tag(),
        Arb.dataConnect.threePossiblyNullDatesTestData()
      ) { tag, testDatas ->
        val insertResult = connector.dateNullableInsert3.execute(tag, testDatas)
        val queryResult = executeQuery(tag, testDatas.selected)
        val matchingIds = testDatas.idsMatchingSelected(insertResult)
        queryResult.data.items.map { it.id } shouldContainExactlyInAnyOrder matchingIds
      }
    }

  @Test
  fun dateNullable_QueryOmittedVariable() =
    runTest(timeout = 1.minutes) {
      checkAll(
        propTestConfig,
        Arb.dataConnect.tag(),
        Arb.dataConnect.threePossiblyNullDatesTestData()
      ) { tag, testDatas ->
        val insertResult = connector.dateNullableInsert3.execute(tag, testDatas)
        val queryResult = connector.dateNullableGetAllByTagAndValue.execute(tag) {}
        queryResult.data.items
          .map { it.id }
          .shouldContainExactlyInAnyOrder(
            insertResult.data.key1.id,
            insertResult.data.key2.id,
            insertResult.data.key3.id
          )
      }
    }

  @Test
  fun dateNullable_Update() =
    runTest(timeout = 1.minutes) {
      val localDates = Arb.dataConnect.localDate().orNullableReference(nullProbability = 0.2)
      checkAll(propTestConfig, localDates, localDates) { date1, date2 ->
        val insertResult = connector.dateNullableInsert.execute { value = date1.ref }
        val updateResult =
          connector.dateNullableUpdateByKey.execute(insertResult.data.key) { value = date2.ref }
        updateResult.asClue { it.data.key shouldBe insertResult.data.key }
        val queryResult = connector.dateNullableGetByKey.execute(insertResult.data.key)
        val item = withClue("queryResult.data.item") { queryResult.data.item.shouldNotBeNull() }
        item.value shouldBe date2.ref
      }
    }

  @Test
  fun dateNullable_UpdateToOmittedShouldLeaveValueUnchanged() =
    runTest(timeout = 1.minutes) {
      val localDates = Arb.dataConnect.localDate().orNullableReference(nullProbability = 0.2)
      checkAll(propTestConfig, localDates) { date ->
        val insertResult = connector.dateNullableInsert.execute { value = date.ref }
        val updateResult = connector.dateNullableUpdateByKey.execute(insertResult.data.key) {}
        updateResult.asClue { it.data.key shouldBe insertResult.data.key }
        val queryResult = connector.dateNullableGetByKey.execute(insertResult.data.key)
        val item = withClue("queryResult.data.item") { queryResult.data.item.shouldNotBeNull() }
        item.value shouldBe date.ref
      }
    }

  @Test
  fun dateNullable_UpdateMany() =
    runTest(timeout = 1.minutes) {
      val localDates = Arb.dataConnect.localDate().orNullableReference(nullProbability = 0.2)
      checkAll(
        propTestConfig,
        Arb.dataConnect.tag(),
        Arb.dataConnect.threePossiblyNullDatesTestData(),
        localDates
      ) { tag, testDatas, date2 ->
        val insertResult = connector.dateNullableInsert3.execute(tag, testDatas)
        val selectedDate = testDatas.selected?.date
        val updateResult =
          connector.dateNullableUpdateByTagAndValue.execute(tag) {
            value = selectedDate
            newValue = date2.ref
          }
        withClue("updateResult.data.count") {
          updateResult.data.count shouldBe testDatas.numMatchingSelected
        }
        val queryResult =
          connector.dateNullableGetAllByTagAndValue.execute(tag) { value = date2.ref }
        val matchingIds1 = testDatas.idsMatchingSelected(insertResult)
        val matchingIds2 = testDatas.idsMatching(insertResult, date2.ref)
        val matchingIds = (matchingIds1 + matchingIds2).distinct()
        queryResult.data.items.map { it.id } shouldContainExactlyInAnyOrder matchingIds
      }
    }

  @Test
  fun dateNullable_UpdateManyOmittedValueShouldUpdateAll() =
    runTest(timeout = 1.minutes) {
      val localDates = Arb.dataConnect.localDate().orNullableReference(nullProbability = 0.2)
      checkAll(
        propTestConfig,
        Arb.dataConnect.tag(),
        Arb.dataConnect.threePossiblyNullDatesTestData(),
        localDates
      ) { tag, testDatas, date2 ->
        val insertResult = connector.dateNullableInsert3.execute(tag, testDatas)
        val updateResult =
          connector.dateNullableUpdateByTagAndValue.execute(tag) { newValue = date2.ref }
        withClue("updateResult.data.count") { updateResult.data.count shouldBe 3 }
        val queryResult =
          connector.dateNullableGetAllByTagAndValue.execute(tag) { value = date2.ref }
        queryResult.data.items
          .map { it.id }
          .shouldContainExactlyInAnyOrder(
            insertResult.data.key1.id,
            insertResult.data.key2.id,
            insertResult.data.key3.id,
          )
      }
    }

  @Test
  fun dateNullable_UpdateManyOmittedNewValueShouldNotChangeAny() =
    runTest(timeout = 1.minutes) {
      checkAll(
        propTestConfig,
        Arb.dataConnect.tag(),
        Arb.dataConnect.threePossiblyNullDatesTestData()
      ) { tag, testDatas ->
        val insertResult = connector.dateNullableInsert3.execute(tag, testDatas)
        val selectedDate = testDatas.selected?.date
        val updateResult =
          connector.dateNullableUpdateByTagAndValue.execute(tag) { value = selectedDate }
        withClue("updateResult.data.count") {
          updateResult.data.count shouldBe testDatas.numMatchingSelected
        }
        val queryResult =
          connector.dateNullableGetAllByTagAndValue.execute(tag) { value = selectedDate }
        val matchingIds = testDatas.idsMatchingSelected(insertResult)
        queryResult.data.items.map { it.id } shouldContainExactlyInAnyOrder matchingIds
      }
    }

  @Test
  fun dateNullable_DeleteMany() =
    runTest(timeout = 1.minutes) {
      checkAll(
        propTestConfig,
        Arb.dataConnect.tag(),
        Arb.dataConnect.threePossiblyNullDatesTestData(),
      ) { tag, testDatas ->
        val insertResult = connector.dateNullableInsert3.execute(tag, testDatas)
        val selectedDate = testDatas.selected?.date
        val deleteResult =
          connector.dateNullableDeleteByTagAndValue.execute(tag) { value = selectedDate }
        withClue("deleteResult.data.count") {
          deleteResult.data.count shouldBe testDatas.numMatchingSelected
        }
        val queryResult = connector.dateNullableGetAllByTagAndValue.execute(tag) {}
        val insertedIds = insertResult.data.run { listOf(key1, key2, key3).map { it.id } }
        val matchingIds = testDatas.idsMatchingSelected(insertResult)
        val remainingIds = insertedIds.filterNot { it in matchingIds }
        queryResult.data.items.map { it.id } shouldContainExactlyInAnyOrder remainingIds
      }
    }

  @Test
  fun dateNullable_DeleteManyOmittedValueShouldDeleteAll() =
    runTest(timeout = 1.minutes) {
      checkAll(
        propTestConfig,
        Arb.dataConnect.tag(),
        Arb.dataConnect.threePossiblyNullDatesTestData(),
      ) { tag, testDatas ->
        connector.dateNullableInsert3.execute(tag, testDatas)
        val deleteResult = connector.dateNullableDeleteByTagAndValue.execute(tag) {}
        withClue("deleteResult.data.count") { deleteResult.data.count shouldBe 3 }
        val queryResult = connector.dateNullableGetAllByTagAndValue.execute(tag) {}
        queryResult.data.items.shouldBeEmpty()
      }
    }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for default `Date` variable values.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun dateNonNullable_MutationVariableDefaults() = runTest {
    val insertResult = connector.dateNonNullableWithDefaultsInsert.execute {}
    val queryResult = connector.dateNonNullableWithDefaultsGetByKey.execute(insertResult.data.key)
    val item = withClue("queryResult.data.item") { queryResult.data.item.shouldNotBeNull() }

    assertSoftly {
      withClue(item) {
        withClue("valueWithVariableDefault") {
          item.valueWithVariableDefault shouldBe LocalDate(6904, 11, 30)
        }
        withClue("valueWithSchemaDefault") {
          item.valueWithSchemaDefault shouldBe LocalDate(2112, 1, 31)
        }
        withClue("epoch") { item.epoch shouldBe EdgeCases.dates.epoch.date }
        withClue("requestTime2") { item.requestTime2 shouldBe item.requestTime1 }
      }
    }

    withClue("requestTime validation") {
      val today = connector.requestTime().toTheeTenAbpJavaLocalDate()
      val yesterday = today.minusDays(1)
      val tomorrow = today.plusDays(1)
      val requestTime = item.requestTime1.toTheeTenAbpJavaLocalDate()
      requestTime.shouldBeIn(yesterday, today, tomorrow)
    }
  }

  @Test
  fun dateNonNullable_QueryVariableDefaults() =
    runTest(timeout = 1.minutes) {
      val defaultTestData = DateTestData(LocalDate(2692, 5, 21), "2692-05-21")
      val localDateArb = Arb.dataConnect.dateTestData().withEdgecases(defaultTestData)
      checkAll(
        propTestConfig,
        Arb.dataConnect.threeNonNullDatesTestData(localDateArb),
        Arb.dataConnect.tag()
      ) { testDatas, tag ->
        val insertResult = connector.dateNonNullableInsert3.execute(tag, testDatas)
        val queryResult = connector.dateNonNullableGetAllByTagAndDefaultValue.execute(tag) {}
        val matchingIds = testDatas.idsMatching(insertResult, defaultTestData.date)
        queryResult.data.items.map { it.id } shouldContainExactlyInAnyOrder matchingIds
      }
    }

  @Test
  fun dateNullable_MutationVariableDefaults() = runTest {
    val insertResult = connector.dateNullableWithDefaultsInsert.execute {}
    val queryResult = connector.dateNullableWithDefaultsGetByKey.execute(insertResult.data.key)
    val item = withClue("queryResult.data.item") { queryResult.data.item.shouldNotBeNull() }

    assertSoftly {
      withClue(item) {
        withClue("valueWithVariableDefault") {
          item.valueWithVariableDefault shouldBe LocalDate(8113, 2, 9)
        }
        withClue("valueWithVariableNullDefault") {
          item.valueWithVariableNullDefault.shouldBeNull()
        }
        withClue("valueWithSchemaDefault") {
          item.valueWithSchemaDefault shouldBe LocalDate(1921, 12, 2)
        }
        withClue("valueWithSchemaNullDefault") { item.valueWithSchemaNullDefault.shouldBeNull() }
        withClue("valueWithNoDefault") { item.valueWithNoDefault.shouldBeNull() }
        withClue("epoch") { item.epoch shouldBe EdgeCases.dates.epoch.date }
        withClue("requestTime1") { item.requestTime1.shouldNotBeNull() }
        withClue("requestTime2") { item.requestTime2 shouldBe item.requestTime1 }
      }
    }

    withClue("requestTime validation") {
      val today = connector.requestTime().toTheeTenAbpJavaLocalDate()
      val yesterday = today.minusDays(1)
      val tomorrow = today.plusDays(1)
      val requestTime = item.requestTime1!!.toTheeTenAbpJavaLocalDate()
      requestTime.shouldBeIn(yesterday, today, tomorrow)
    }
  }

  @Test
  fun dateNullable_QueryVariableDefaults() =
    runTest(timeout = 1.minutes) {
      val defaultTestData = DateTestData(LocalDate(1771, 10, 28), "1771-10-28")
      val dateTestDataArb =
        Arb.dataConnect
          .dateTestData()
          .withEdgecases(defaultTestData)
          .orNullableReference(nullProbability = 0.333)
      checkAll(
        propTestConfig,
        Arb.dataConnect.threePossiblyNullDatesTestData(dateTestDataArb),
        Arb.dataConnect.tag()
      ) { testDatas, tag ->
        val insertResult = connector.dateNullableInsert3.execute(tag, testDatas)
        val queryResult = connector.dateNullableGetAllByTagAndDefaultValue.execute(tag) {}
        val matchingIds = testDatas.idsMatching(insertResult, defaultTestData.date)
        queryResult.data.items.map { it.id } shouldContainExactlyInAnyOrder matchingIds
      }
    }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Invalid Date String Tests
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun dateNonNullable_MutationInvalidDateVariable() =
    runTest(timeout = 1.minutes) {
      checkAll(propTestConfig.copy(iterations = 500), Arb.dataConnect.invalidDateScalarString()) {
        testData ->
        val exception =
          shouldThrow<DataConnectException> {
            connector.dateNonNullableInsert.execute(testData.toDateScalarString())
          }
        assertSoftly {
          exception.message shouldContainWithNonAbuttingText "\$value"
          exception.message shouldContainWithNonAbuttingTextIgnoringCase "invalid"
        }
      }
    }

  @Test
  fun dateNonNullable_QueryInvalidDateVariable() =
    runTest(timeout = 1.minutes) {
      checkAll(
        propTestConfig.copy(iterations = 500),
        Arb.dataConnect.tag(),
        Arb.dataConnect.invalidDateScalarString()
      ) { tag, testData ->
        val exception =
          shouldThrow<DataConnectException> {
            connector.dateNonNullableGetAllByTagAndValue.execute(
              tag = tag,
              value = testData.toDateScalarString()
            )
          }
        assertSoftly {
          exception.message shouldContainWithNonAbuttingText "\$value"
          exception.message shouldContainWithNonAbuttingTextIgnoringCase "invalid"
        }
      }
    }

  @Test
  fun dateNullable_MutationInvalidDateVariable() =
    runTest(timeout = 1.minutes) {
      checkAll(propTestConfig.copy(iterations = 500), Arb.dataConnect.invalidDateScalarString()) {
        testData ->
        val exception =
          shouldThrow<DataConnectException> {
            connector.dateNullableInsert.execute(testData.toDateScalarString())
          }
        assertSoftly {
          exception.message shouldContainWithNonAbuttingText "\$value"
          exception.message shouldContainWithNonAbuttingTextIgnoringCase "invalid"
        }
      }
    }

  @Test
  fun dateNullable_QueryInvalidDateVariable() =
    runTest(timeout = 1.minutes) {
      checkAll(
        propTestConfig.copy(iterations = 500),
        Arb.dataConnect.tag(),
        Arb.dataConnect.invalidDateScalarString()
      ) { tag, testData ->
        val exception =
          shouldThrow<DataConnectException> {
            connector.dateNullableGetAllByTagAndValue.execute(
              tag = tag,
              value = testData.toDateScalarString()
            )
          }
        assertSoftly {
          exception.message shouldContainWithNonAbuttingText "\$value"
          exception.message shouldContainWithNonAbuttingTextIgnoringCase "invalid"
        }
      }
    }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Helper methods and classes.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Serializable
  private data class StringItemData(val item: Item?) {
    @Serializable data class Item(val value: String?)
  }

  @Serializable private data class NullValueVariables(val value: Nothing?)

  @Serializable private data class StringValueVariables(val value: String?)

  @Serializable private data class TagAndStringValueVariables(val tag: String, val value: String?)

  @Serializable private data class TagAndNullValueVariables(val tag: String, val value: Nothing?)

  private suspend fun DemoConnector.requestTime(): LocalDate {
    val insertResult = exprValuesInsert.execute()
    val queryResult = exprValuesGetByKey.execute(insertResult.data.key)
    return withClue("exprValuesGetByKey queryResult.data.item") {
        queryResult.data.item.shouldNotBeNull()
      }
      .requestTimeAsDate
  }

  private companion object {

    val propTestConfig =
      PropTestConfig(iterations = 20, edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.5))

    suspend fun DateNullableInsert3Mutation.execute(
      tag: String,
      testDatas: ThreeDateTestDatas,
    ): MutationResult<DateNullableInsert3Mutation.Data, DateNullableInsert3Mutation.Variables> =
      execute(tag = tag) {
        value1 = testDatas.testData1?.date
        value2 = testDatas.testData2?.date
        value3 = testDatas.testData3?.date
      }

    suspend fun DateNonNullableInsert3Mutation.execute(
      tag: String,
      testDatas: ThreeDateTestDatas,
    ): MutationResult<
      DateNonNullableInsert3Mutation.Data, DateNonNullableInsert3Mutation.Variables
    > =
      execute(
        tag = tag,
        value1 = testDatas.testData1!!.date,
        value2 = testDatas.testData2!!.date,
        value3 = testDatas.testData3!!.date,
      )

    suspend fun DateNonNullableGetByKeyQuery.executeWithStringData(
      key: DateNonNullableKey
    ): String? = withDataDeserializer(serializer<StringItemData>()).execute(key).data.item?.value

    suspend fun DateNullableGetByKeyQuery.executeWithStringData(key: DateNullableKey): String? =
      withDataDeserializer(serializer<StringItemData>()).execute(key).data.item?.value

    suspend fun DateNonNullableInsertMutation.execute(
      date: String
    ): MutationResult<DateNonNullableInsertMutation.Data, StringValueVariables> =
      withVariablesSerializer(serializer<StringValueVariables>())
        .ref(StringValueVariables(date))
        .execute()

    suspend fun DateNullableInsertMutation.execute(
      date: String?
    ): MutationResult<DateNullableInsertMutation.Data, StringValueVariables> =
      withVariablesSerializer(serializer<StringValueVariables>())
        .ref(StringValueVariables(date))
        .execute()

    suspend fun DateNonNullableInsertMutation.execute(
      date: Nothing?
    ): MutationResult<DateNonNullableInsertMutation.Data, NullValueVariables> =
      withVariablesSerializer(serializer<NullValueVariables>())
        .ref(NullValueVariables(date))
        .execute()

    suspend fun DateNonNullableGetAllByTagAndValueQuery.execute(
      tag: String,
      value: String,
    ): QueryResult<DateNonNullableGetAllByTagAndValueQuery.Data, TagAndStringValueVariables> =
      withVariablesSerializer(serializer<TagAndStringValueVariables>())
        .ref(TagAndStringValueVariables(tag = tag, value = value))
        .execute()

    suspend fun DateNonNullableGetAllByTagAndValueQuery.execute(
      tag: String,
      value: Nothing?,
    ): QueryResult<DateNonNullableGetAllByTagAndValueQuery.Data, TagAndNullValueVariables> =
      withVariablesSerializer(serializer<TagAndNullValueVariables>())
        .ref(TagAndNullValueVariables(tag = tag, value = value))
        .execute()

    suspend fun DateNullableGetAllByTagAndValueQuery.execute(
      tag: String,
      value: String?,
    ): QueryResult<DateNullableGetAllByTagAndValueQuery.Data, TagAndStringValueVariables> =
      withVariablesSerializer(serializer<TagAndStringValueVariables>())
        .ref(TagAndStringValueVariables(tag = tag, value = value))
        .execute()

    @JvmName("idsMatching_DateNonNullable")
    fun ThreeDateTestDatas.idsMatching(
      result: MutationResult<DateNonNullableInsert3Mutation.Data, *>,
      localDate: LocalDate?,
    ): List<UUID> = idsMatching(result.data, localDate)

    @JvmName("idsMatching_DateNonNullable")
    fun ThreeDateTestDatas.idsMatching(
      data: DateNonNullableInsert3Mutation.Data,
      localDate: LocalDate?,
    ): List<UUID> = idsMatching(localDate) { data.uuidFromItemNumber(it) }

    @JvmName("idsMatchingSelected_DateNonNullable")
    fun ThreeDateTestDatas.idsMatchingSelected(
      result: MutationResult<DateNonNullableInsert3Mutation.Data, *>
    ): List<UUID> = idsMatchingSelected(result.data)

    @JvmName("idsMatchingSelected_DateNonNullable")
    fun ThreeDateTestDatas.idsMatchingSelected(
      data: DateNonNullableInsert3Mutation.Data
    ): List<UUID> = idsMatchingSelected { data.uuidFromItemNumber(it) }

    fun DateNonNullableInsert3Mutation.Data.uuidFromItemNumber(itemNumber: ItemNumber): UUID =
      when (itemNumber) {
        ItemNumber.ONE -> key1
        ItemNumber.TWO -> key2
        ItemNumber.THREE -> key3
      }.id

    @JvmName("idsMatching_DateNullable")
    fun ThreeDateTestDatas.idsMatching(
      result: MutationResult<DateNullableInsert3Mutation.Data, *>,
      localDate: LocalDate?,
    ): List<UUID> = idsMatching(result.data, localDate)

    @JvmName("idsMatching_DateNullable")
    fun ThreeDateTestDatas.idsMatching(
      data: DateNullableInsert3Mutation.Data,
      localDate: LocalDate?,
    ): List<UUID> = idsMatching(localDate) { data.uuidFromItemNumber(it) }

    @JvmName("idsMatchingSelected_DateNullable")
    fun ThreeDateTestDatas.idsMatchingSelected(
      result: MutationResult<DateNullableInsert3Mutation.Data, *>
    ): List<UUID> = idsMatchingSelected(result.data)

    @JvmName("idsMatchingSelected_DateNullable")
    fun ThreeDateTestDatas.idsMatchingSelected(data: DateNullableInsert3Mutation.Data): List<UUID> =
      idsMatchingSelected {
        data.uuidFromItemNumber(it)
      }

    fun DateNullableInsert3Mutation.Data.uuidFromItemNumber(itemNumber: ItemNumber): UUID =
      when (itemNumber) {
        ItemNumber.ONE -> key1
        ItemNumber.TWO -> key2
        ItemNumber.THREE -> key3
      }.id

    suspend fun <Data> GeneratedQuery<*, Data, DateNonNullableGetByKeyQuery.Variables>.execute(
      key: DateNonNullableKey
    ) = ref(DateNonNullableGetByKeyQuery.Variables(key)).execute()

    suspend fun <Data> GeneratedQuery<*, Data, DateNullableGetByKeyQuery.Variables>.execute(
      key: DateNullableKey
    ) = ref(DateNullableGetByKeyQuery.Variables(key)).execute()
  }
}
