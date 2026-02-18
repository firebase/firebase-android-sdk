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

////////////////////////////////////////////////////////////////////////////////
// THIS FILE WAS COPIED TO JavaTimeLocalDateIntegrationTest.kt and
// KotlinxDatetimeLocalDateIntegrationTest.kt AND ADAPTED TO TEST THE
// CORRESPONDING IMPLEMENTATIONS OF LocalDate. ANY CHANGES MADE TO THIS FILE
// MUST ALSO BE PORTED TO THOSE OTHER FILES, IF APPROPRIATE.
////////////////////////////////////////////////////////////////////////////////
package com.google.firebase.dataconnect

import com.google.firebase.dataconnect.serializers.UUIDSerializer
import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
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
import com.google.firebase.dataconnect.testutil.requestTimeAsDate
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import com.google.firebase.dataconnect.testutil.toTheeTenAbpJavaLocalDate
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
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.serializer
import org.junit.Test

////////////////////////////////////////////////////////////////////////////////
// THIS FILE WAS COPIED TO JavaTimeLocalDateIntegrationTest.kt and
// KotlinxDatetimeLocalDateIntegrationTest.kt AND ADAPTED TO TEST THE
// CORRESPONDING IMPLEMENTATIONS OF LocalDate. ANY CHANGES MADE TO THIS FILE
// MUST ALSO BE PORTED TO THOSE OTHER FILES, IF APPROPRIATE.
////////////////////////////////////////////////////////////////////////////////
class LocalDateIntegrationTest : DataConnectIntegrationTestBase() {

  private val dataConnect: FirebaseDataConnect by lazy {
    val connectorConfig = testConnectorConfig.copy(connector = "demo")
    dataConnectFactory.newInstance(connectorConfig)
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for inserting into and querying this table:
  // type DateNonNullable @table { value: Date!, tag: String }
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun dateNonNullable_MutationVariable() =
    runTest(timeout = 1.minutes) {
      checkAll(propTestConfig, Arb.dataConnect.localDate()) { localDate ->
        val insertResult = nonNullableDate.insert(localDate)
        val queryResult = nonNullableDate.getByKey(insertResult.data.key)
        queryResult.data.item?.value shouldBe localDate
      }
    }

  @Test
  fun dateNonNullable_QueryVariable() =
    runTest(timeout = 1.minutes) {
      checkAll(
        propTestConfig,
        Arb.dataConnect.tag(),
        Arb.dataConnect.threeNonNullDatesTestData()
      ) { tag, testDatas ->
        val insertResult = nonNullableDate.insert3(tag, testDatas)
        val queryResult = nonNullableDate.getAllByTagAndValue(tag, testDatas.selected!!.date)
        val matchingIds = testDatas.idsMatchingSelected(insertResult)
        queryResult.data.items.map { it.id } shouldContainExactlyInAnyOrder matchingIds
      }
    }

  @Test
  fun dateNonNullable_MutationNullVariableShouldThrow() = runTest {
    val exception = shouldThrow<DataConnectException> { nonNullableDate.insert(null) }
    assertSoftly {
      exception.message shouldContainWithNonAbuttingText "\$value"
      exception.message shouldContainWithNonAbuttingText "is null"
    }
  }

  @Test
  fun dateNonNullable_QueryNullVariableShouldThrow() = runTest {
    val tag = Arb.dataConnect.tag().next(rs)
    val exception =
      shouldThrow<DataConnectException> { nonNullableDate.getAllByTagAndValue(tag, null) }
    assertSoftly {
      exception.message shouldContainWithNonAbuttingText "\$value"
      exception.message shouldContainWithNonAbuttingText "is null"
    }
  }

  @Test
  fun dateNonNullable_QueryOmittedVariableShouldMatchAll() = runTest {
    val tag = Arb.dataConnect.tag().next(rs)
    val testDatas = Arb.dataConnect.threeNonNullDatesTestData().next(rs)
    val insertResult = nonNullableDate.insert3(tag, testDatas)
    val queryResult = nonNullableDate.getAllByTagAndMaybeValue(tag)
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
    nonNullableDate.insert3(tag, testDatas)
    val queryResult = nonNullableDate.getAllByTagAndMaybeValue(tag, null)
    queryResult.data.items.shouldBeEmpty()
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for inserting into and querying this table:
  // type DateNullable @table { value: Date, tag: String }
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun dateNullable_MutationVariable() =
    runTest(timeout = 1.minutes) {
      val localDates = Arb.dataConnect.localDate().orNullableReference(nullProbability = 0.2)
      checkAll(propTestConfig, localDates) { localDate ->
        val insertResult = nullableDate.insert(localDate.ref)
        val queryResult = nullableDate.getByKey(insertResult.data.key)
        queryResult.data.item?.value shouldBe localDate.ref
      }
    }

  @Test
  fun dateNullable_QueryVariable() =
    runTest(timeout = 1.minutes) {
      checkAll(
        propTestConfig,
        Arb.dataConnect.tag(),
        Arb.dataConnect.threePossiblyNullDatesTestData()
      ) { tag, testDatas ->
        val insertResult = nullableDate.insert3(tag, testDatas)
        val queryResult = nullableDate.getAllByTagAndValue(tag, testDatas.selected?.date)
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
        val insertResult = nullableDate.insert3(tag, testDatas)
        val queryResult = nullableDate.getAllByTagAndMaybeValue(tag)
        queryResult.data.items
          .map { it.id }
          .shouldContainExactlyInAnyOrder(
            insertResult.data.key1.id,
            insertResult.data.key2.id,
            insertResult.data.key3.id
          )
      }
    }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for default `Date` variable values.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun dateNonNullable_MutationVariableDefaults() = runTest {
    val insertResult = nonNullableDate.insertWithDefaults()
    val queryResult = nonNullableDate.getInsertedWithDefaultsByKey(insertResult.data.key)
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
        withClue("requestTime1") { item.requestTime1.shouldNotBeNull() }
        withClue("requestTime2") { item.requestTime2 shouldBe item.requestTime1 }
      }
    }

    withClue("requestTime validation") {
      val today = dataConnect.requestTimeAsDate().toTheeTenAbpJavaLocalDate()
      val yesterday = today.minusDays(1)
      val tomorrow = today.plusDays(1)
      val requestTime = item.requestTime1!!.toTheeTenAbpJavaLocalDate()
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
        val insertResult = nonNullableDate.insert3(tag, testDatas)
        val queryResult = nonNullableDate.getAllByTagAndDefaultValue(tag)
        val matchingIds = testDatas.idsMatching(insertResult, defaultTestData.date)
        queryResult.data.items.map { it.id } shouldContainExactlyInAnyOrder matchingIds
      }
    }

  @Test
  fun dateNullable_MutationVariableDefaults() = runTest {
    val insertResult = nullableDate.insertWithDefaults()
    val queryResult = nullableDate.getInsertedWithDefaultsByKey(insertResult.data.key)
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
      val today = dataConnect.requestTimeAsDate().toTheeTenAbpJavaLocalDate()
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
        val insertResult = nullableDate.insert3(tag, testDatas)
        val queryResult = nullableDate.getAllByTagAndDefaultValue(tag)
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
            nonNullableDate.insert(testData.toDateScalarString())
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
            nonNullableDate.getAllByTagAndValue(tag = tag, value = testData.toDateScalarString())
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
          shouldThrow<DataConnectException> { nullableDate.insert(testData.toDateScalarString()) }
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
            nullableDate.getAllByTagAndValue(tag = tag, value = testData.toDateScalarString())
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

  @Serializable private data class SingleKeyVariables(val key: Key)

  @Serializable private data class SingleKeyData(val key: Key)

  @Serializable
  private data class MultipleKeysData(val items: List<Item>) {
    @Serializable data class Item(val id: UUID)
  }

  @Serializable private data class ThreeKeysData(val key1: Key, val key2: Key, val key3: Key)

  @Serializable private data class InsertVariables(val value: LocalDate?)

  @Serializable private data class InsertStringVariables(val value: String)

  @Serializable
  private data class Insert3Variables(
    val tag: String,
    val value1: LocalDate?,
    val value2: LocalDate?,
    val value3: LocalDate?,
  )

  @Serializable private data class TagVariables(val tag: String)

  @Serializable private data class TagAndValueVariables(val tag: String, val value: LocalDate?)

  @Serializable private data class TagAndStringValueVariables(val tag: String, val value: String)

  @Serializable
  private data class QueryData(val item: Item?) {
    @Serializable data class Item(val value: LocalDate?)
  }

  @Serializable
  private data class GetInsertedWithDefaultsByKeyQueryData(val item: Item?) {
    @Serializable
    data class Item(
      val valueWithVariableDefault: LocalDate,
      val valueWithVariableNullDefault: LocalDate?,
      val valueWithSchemaDefault: LocalDate,
      val valueWithSchemaNullDefault: LocalDate?,
      val valueWithNoDefault: LocalDate?,
      val epoch: LocalDate?,
      val requestTime1: LocalDate?,
      val requestTime2: LocalDate?,
    )
  }

  @Serializable private data class Key(val id: UUID)

  /** Operations for querying and mutating the table that stores non-nullable Date scalar values. */
  private val nonNullableDate =
    Operations(
      getByKeyQueryName = "DateNonNullable_GetByKey",
      getAllByTagAndValueQueryName = "DateNonNullable_GetAllByTagAndValue",
      getAllByTagAndMaybeValueQueryName = "DateNonNullable_GetAllByTagAndMaybeValue",
      getAllByTagAndDefaultValueQueryName = "DateNonNullable_GetAllByTagAndDefaultValue",
      insertMutationName = "DateNonNullable_Insert",
      insert3MutationName = "DateNonNullable_Insert3",
      insertWithDefaultsMutationName = "DateNonNullableWithDefaults_Insert",
      getInsertedWithDefaultsByKeyQueryName = "DateNonNullableWithDefaults_GetByKey",
    )

  /** Operations for querying and mutating the table that stores nullable Date scalar values. */
  private val nullableDate =
    Operations(
      getByKeyQueryName = "DateNullable_GetByKey",
      getAllByTagAndValueQueryName = "DateNullable_GetAllByTagAndValue",
      getAllByTagAndMaybeValueQueryName = "DateNullable_GetAllByTagAndValue",
      getAllByTagAndDefaultValueQueryName = "DateNullable_GetAllByTagAndDefaultValue",
      insertMutationName = "DateNullable_Insert",
      insert3MutationName = "DateNullable_Insert3",
      insertWithDefaultsMutationName = "DateNullableWithDefaults_Insert",
      getInsertedWithDefaultsByKeyQueryName = "DateNullableWithDefaults_GetByKey",
    )

  private inner class Operations(
    getByKeyQueryName: String,
    getAllByTagAndValueQueryName: String,
    getAllByTagAndMaybeValueQueryName: String,
    getAllByTagAndDefaultValueQueryName: String,
    insertMutationName: String,
    insert3MutationName: String,
    insertWithDefaultsMutationName: String,
    getInsertedWithDefaultsByKeyQueryName: String,
  ) {

    suspend fun insert(localDate: LocalDate?): MutationResult<SingleKeyData, InsertVariables> =
      insert(InsertVariables(localDate))

    suspend fun insert(variables: InsertVariables): MutationResult<SingleKeyData, InsertVariables> =
      mutations.insert(variables).execute()

    suspend fun insert(localDate: String): MutationResult<SingleKeyData, InsertStringVariables> =
      insert(InsertStringVariables(localDate))

    suspend fun insert(
      variables: InsertStringVariables
    ): MutationResult<SingleKeyData, InsertStringVariables> = mutations.insert(variables).execute()

    suspend fun insert3(
      tag: String,
      testDatas: ThreeDateTestDatas,
    ): MutationResult<ThreeKeysData, Insert3Variables> =
      insert3(
        tag = tag,
        value1 = testDatas.testData1?.date,
        value2 = testDatas.testData2?.date,
        value3 = testDatas.testData3?.date
      )

    suspend fun insert3(
      tag: String,
      value1: LocalDate?,
      value2: LocalDate?,
      value3: LocalDate?,
    ): MutationResult<ThreeKeysData, Insert3Variables> =
      insert3(Insert3Variables(tag = tag, value1 = value1, value2 = value2, value3 = value3))

    suspend fun insert3(
      variables: Insert3Variables
    ): MutationResult<ThreeKeysData, Insert3Variables> = mutations.insert3(variables).execute()

    suspend fun getByKey(key: Key): QueryResult<QueryData, SingleKeyVariables> =
      getByKey(SingleKeyVariables(key))

    suspend fun getByKey(
      variables: SingleKeyVariables
    ): QueryResult<QueryData, SingleKeyVariables> = queries.getByKey(variables).execute()

    suspend fun getAllByTagAndValue(
      tag: String,
      value: LocalDate?
    ): QueryResult<MultipleKeysData, TagAndValueVariables> =
      getAllByTagAndValue(TagAndValueVariables(tag, value))

    suspend fun getAllByTagAndValue(
      variables: TagAndValueVariables
    ): QueryResult<MultipleKeysData, TagAndValueVariables> =
      queries.getAllByTagAndValue(variables).execute()

    suspend fun getAllByTagAndValue(
      tag: String,
      value: String
    ): QueryResult<MultipleKeysData, TagAndStringValueVariables> =
      getAllByTagAndValue(TagAndStringValueVariables(tag, value))

    suspend fun getAllByTagAndValue(
      variables: TagAndStringValueVariables
    ): QueryResult<MultipleKeysData, TagAndStringValueVariables> =
      queries.getAllByTagAndValue(variables).execute()

    suspend fun getAllByTagAndMaybeValue(
      tag: String,
    ): QueryResult<MultipleKeysData, TagVariables> = getAllByTagAndMaybeValue(TagVariables(tag))

    suspend fun getAllByTagAndMaybeValue(
      variables: TagVariables
    ): QueryResult<MultipleKeysData, TagVariables> =
      queries.getAllByTagAndMaybeValue(variables).execute()

    suspend fun getAllByTagAndMaybeValue(
      tag: String,
      value: Nothing?,
    ): QueryResult<MultipleKeysData, TagAndValueVariables> =
      getAllByTagAndMaybeValue(TagAndValueVariables(tag, value))

    suspend fun getAllByTagAndMaybeValue(
      variables: TagAndValueVariables
    ): QueryResult<MultipleKeysData, TagAndValueVariables> =
      queries.getAllByTagAndMaybeValue(variables).execute()

    suspend fun getAllByTagAndDefaultValue(
      tag: String
    ): QueryResult<MultipleKeysData, TagVariables> = getAllByTagAndDefaultValue(TagVariables(tag))

    suspend fun getAllByTagAndDefaultValue(
      variables: TagVariables
    ): QueryResult<MultipleKeysData, TagVariables> =
      queries.getAllByTagAndDefaultValue(variables).execute()

    suspend fun insertWithDefaults(): MutationResult<SingleKeyData, Unit> =
      mutations.insertWithDefaults().execute()

    suspend fun getInsertedWithDefaultsByKey(
      key: Key
    ): QueryResult<GetInsertedWithDefaultsByKeyQueryData, SingleKeyVariables> =
      getInsertedWithDefaultsByKey(SingleKeyVariables(key))

    suspend fun getInsertedWithDefaultsByKey(
      variables: SingleKeyVariables
    ): QueryResult<GetInsertedWithDefaultsByKeyQueryData, SingleKeyVariables> =
      queries.getInsertedWithDefaultsByKey(variables).execute()

    private val queries =
      object {
        fun getByKey(variables: SingleKeyVariables): QueryRef<QueryData, SingleKeyVariables> =
          dataConnect.query(
            getByKeyQueryName,
            variables,
            serializer(),
            serializer(),
          )

        inline fun <reified Variables> getAllByTagAndValue(
          variables: Variables
        ): QueryRef<MultipleKeysData, Variables> =
          dataConnect.query(
            getAllByTagAndValueQueryName,
            variables,
            serializer(),
            serializer(),
          )

        fun getAllByTagAndMaybeValue(
          variables: TagVariables
        ): QueryRef<MultipleKeysData, TagVariables> =
          dataConnect.query(
            getAllByTagAndMaybeValueQueryName,
            variables,
            serializer(),
            serializer(),
          )

        fun getAllByTagAndMaybeValue(
          variables: TagAndValueVariables
        ): QueryRef<MultipleKeysData, TagAndValueVariables> =
          dataConnect.query(
            getAllByTagAndMaybeValueQueryName,
            variables,
            serializer(),
            serializer(),
          )

        fun getAllByTagAndDefaultValue(
          variables: TagVariables
        ): QueryRef<MultipleKeysData, TagVariables> =
          dataConnect.query(
            getAllByTagAndDefaultValueQueryName,
            variables,
            serializer(),
            serializer(),
          )

        fun getInsertedWithDefaultsByKey(
          variables: SingleKeyVariables
        ): QueryRef<GetInsertedWithDefaultsByKeyQueryData, SingleKeyVariables> =
          dataConnect.query(
            getInsertedWithDefaultsByKeyQueryName,
            variables,
            serializer(),
            serializer(),
          )
      }

    private val mutations =
      object {
        inline fun <reified Variables> insert(
          variables: Variables
        ): MutationRef<SingleKeyData, Variables> =
          dataConnect.mutation(
            insertMutationName,
            variables,
            serializer(),
            serializer(),
          )

        fun insert3(variables: Insert3Variables): MutationRef<ThreeKeysData, Insert3Variables> =
          dataConnect.mutation(
            insert3MutationName,
            variables,
            serializer(),
            serializer(),
          )

        fun insertWithDefaults(): MutationRef<SingleKeyData, Unit> =
          dataConnect.mutation(
            insertWithDefaultsMutationName,
            Unit,
            serializer(),
            serializer(),
          )
      }
  }

  private companion object {
    val propTestConfig =
      PropTestConfig(
        iterations = 20,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.5),
      )

    fun ThreeDateTestDatas.idsMatchingSelected(
      result: MutationResult<ThreeKeysData, *>
    ): List<UUID> = idsMatchingSelected(result.data)

    fun ThreeDateTestDatas.idsMatchingSelected(data: ThreeKeysData): List<UUID> =
      idsMatchingSelected {
        data.uuidFromItemNumber(it)
      }

    fun ThreeDateTestDatas.idsMatching(
      result: MutationResult<ThreeKeysData, *>,
      localDate: LocalDate?,
    ): List<UUID> = idsMatching(result.data, localDate)

    fun ThreeDateTestDatas.idsMatching(
      data: ThreeKeysData,
      localDate: LocalDate?,
    ): List<UUID> = idsMatching(localDate) { data.uuidFromItemNumber(it) }

    fun ThreeKeysData.uuidFromItemNumber(itemNumber: ItemNumber): UUID =
      when (itemNumber) {
        ItemNumber.ONE -> key1
        ItemNumber.TWO -> key2
        ItemNumber.THREE -> key3
      }.id
  }
}
