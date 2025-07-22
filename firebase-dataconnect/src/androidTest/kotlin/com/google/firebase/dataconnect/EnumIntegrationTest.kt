/*
 * Copyright 2025 Google LLC
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

package com.google.firebase.dataconnect

import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.withNullAppended
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.orNull
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.junit.Ignore
import org.junit.Test

class EnumIntegrationTest : DataConnectIntegrationTestBase() {

  private val dataConnect: FirebaseDataConnect by lazy {
    val connectorConfig = testConnectorConfig.copy(connector = "demo")
    dataConnectFactory.newInstance(connectorConfig)
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for EnumNonNullable table.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun insertNonNullableNonNullEnumValue() = runTest {
    N5ekmae3jn.entries.forEach { enumValue ->
      val insertVariables = InsertNonNullableVariables(enumValue)
      val key = dataConnect.mutation(insertVariables).execute().data.key
      val queryVariables = GetNonNullableByKeyVariables(key)
      val queryResult = dataConnect.query(queryVariables).execute().data
      withClue(queryResult) { queryResult?.item?.value shouldBe enumValue }
    }
  }

  @Test
  fun insertNonNullableNullEnumValue() = runTest {
    val insertVariables = InsertNonNullableNullVariables(null)
    val mutation = dataConnect.mutation(insertVariables)
    shouldThrow<DataConnectOperationException> { mutation.execute() }
  }

  @Test
  fun updateNonNullableEnumValue() = runTest {
    val enumArb = Arb.enum<N5ekmae3jn>()
    checkAll(NUM_ITERATIONS, enumArb, enumArb) { value1, value2 ->
      val insertVariables = InsertNonNullableVariables(value1)
      val key = dataConnect.mutation(insertVariables).execute().data.key
      val updateVariables = UpdateNonNullableVariables(key, value2)
      dataConnect.mutation(updateVariables).execute()
      val queryVariables = GetNonNullableByKeyVariables(key)
      val queryResult = dataConnect.query(queryVariables).execute().data
      withClue(queryResult) { queryResult?.item?.value shouldBe value2 }
    }
  }

  @Test
  fun queryNonNullableEnumValue() = runTest {
    N5ekmae3jn.entries.forEach { enumValue ->
      val insertVariables = InsertNonNullableVariables(enumValue)
      val key = dataConnect.mutation(insertVariables).execute().data.key
      val queryVariables = GetNonNullableByKeyVariables(key)
      val queryRef = dataConnect.query(queryVariables)
      val queryResult = queryRef.execute().data
      withClue(queryResult) { queryResult?.item?.value shouldBe enumValue }
    }
  }

  @Test
  fun queryNonNullableByNonNullEnumValue() = runTest {
    val enumArb = Arb.enum<N5ekmae3jn>()
    checkAll(NUM_ITERATIONS, enumArb, enumArb, enumArb, enumArb, Arb.dataConnect.tag()) {
      value1,
      value2,
      value3,
      value4,
      tag ->
      val insertVariables = Insert3NonNullableVariables(tag, value1, value2, value3)
      val insertResult = dataConnect.mutation(insertVariables).execute().data
      val queryVariables = GetNonNullableByTagAndValueVariables(tag, value4)
      val queryResult = dataConnect.query(queryVariables).execute().data
      val matchingKeys = insertResult.keysForMatchingValues(value4, insertVariables)
      withClue(queryResult) { queryResult.items shouldContainExactlyInAnyOrder matchingKeys }
    }
  }

  @Test
  fun queryNonNullableByUndefinedEnumValue() = runTest {
    val enumArb = Arb.enum<N5ekmae3jn>()
    checkAll(1, enumArb, enumArb, enumArb, Arb.dataConnect.tag()) { value1, value2, value3, tag ->
      val insertVariables = Insert3NonNullableVariables(tag, value1, value2, value3)
      dataConnect.mutation(insertVariables).execute().data
      val queryVariables = GetNonNullableByTagAndValueVariables(tag, OptionalVariable.Undefined)
      val queryRef = dataConnect.query(queryVariables)
      shouldThrow<DataConnectOperationException> { queryRef.execute() }
    }
  }

  @Test
  fun queryNonNullableByNullEnumValue() = runTest {
    val enumArb = Arb.enum<N5ekmae3jn>()
    checkAll(1, enumArb, enumArb, enumArb, Arb.dataConnect.tag()) { value1, value2, value3, tag ->
      val insertVariables = Insert3NonNullableVariables(tag, value1, value2, value3)
      dataConnect.mutation(insertVariables).execute()
      val queryVariables = GetNonNullableByTagAndValueVariables(tag, OptionalVariable.Value(null))
      val queryRef = dataConnect.query(queryVariables)
      shouldThrow<DataConnectOperationException> { queryRef.execute() }
    }
  }

  @Test
  fun queryNonNullableByDefaultEnumValue() = runTest {
    val enumArb = Arb.enum<N5ekmae3jn>()
    checkAll(NUM_ITERATIONS, enumArb, enumArb, enumArb, Arb.dataConnect.tag()) {
      value1,
      value2,
      value3,
      tag ->
      val insertVariables = Insert3NonNullableVariables(tag, value1, value2, value3)
      val insertResult = dataConnect.mutation(insertVariables).execute().data
      val queryVariables = GetNonNullableByTagAndDefaultValueVariables(tag)
      val queryResult = dataConnect.query(queryVariables).execute().data
      val matchingKeys = insertResult.keysForMatchingValues(N5ekmae3jn.XGWGVMYTHJ, insertVariables)
      withClue(queryResult) { queryResult.items shouldContainExactlyInAnyOrder matchingKeys }
    }
  }

  @Serializable private data class InsertNonNullableVariables(val value: N5ekmae3jn)

  @Serializable private data class InsertNonNullableNullVariables(val value: N5ekmae3jn?)

  @Serializable
  private data class Insert3NonNullableVariables(
    val tag: String,
    val value1: N5ekmae3jn,
    val value2: N5ekmae3jn,
    val value3: N5ekmae3jn
  )

  @Serializable private data class GetNonNullableByKeyVariables(val key: RowKey)

  @Serializable
  private data class GetNonNullableByTagAndValueVariables(
    val tag: String,
    val value: OptionalVariable<N5ekmae3jn?>
  ) {
    constructor(tag: String, value: N5ekmae3jn) : this(tag, OptionalVariable.Value(value))
  }

  @Serializable private data class GetNonNullableByTagAndDefaultValueVariables(val tag: String)

  @Serializable
  private data class UpdateNonNullableVariables(val key: RowKey, val value: N5ekmae3jn)

  @Serializable
  private data class GetNonNullableByKeyData(val item: Item?) {
    @Serializable data class Item(val value: N5ekmae3jn)
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for EnumNullable table.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun insertNullableNonNullEnumValue() = runTest {
    N5ekmae3jn.entries.forEach { enumValue ->
      val insertVariables = InsertNullableVariables(enumValue)
      val key = dataConnect.mutation(insertVariables).execute().data.key
      val queryVariables = GetNullableByKeyVariables(key)
      val queryResult = dataConnect.query(queryVariables).execute().data
      withClue(queryResult) { queryResult?.item?.value shouldBe enumValue }
    }
  }

  @Test
  fun insertNullableNullEnumValue() = runTest {
    val insertVariables = InsertNullableVariables(null)
    val key = dataConnect.mutation(insertVariables).execute().data.key
    val queryVariables = GetNullableByKeyVariables(key)
    val queryResult = dataConnect.query(queryVariables).execute().data
    withClue(queryResult) { queryResult?.item?.value.shouldBeNull() }
  }

  @Test
  fun updateNullableEnumValue() = runTest {
    val enumArb = Arb.enum<N5ekmae3jn>().orNull(nullProbability = 0.5)
    checkAll(NUM_ITERATIONS, enumArb, enumArb) { value1, value2 ->
      val insertVariables = InsertNullableVariables(value1)
      val key = dataConnect.mutation(insertVariables).execute().data.key
      val updateVariables = UpdateNullableVariables(key, value2)
      dataConnect.mutation(updateVariables).execute()
      val queryVariables = GetNullableByKeyVariables(key)
      val queryResult = dataConnect.query(queryVariables).execute().data
      withClue(queryResult) { queryResult?.item?.value shouldBe value2 }
    }
  }

  @Test
  fun queryNullableEnumValue() = runTest {
    N5ekmae3jn.entries.withNullAppended().forEach { enumValue ->
      val insertVariables = InsertNullableVariables(enumValue)
      val key = dataConnect.mutation(insertVariables).execute().data.key
      val queryVariables = GetNullableByKeyVariables(key)
      val queryRef = dataConnect.query(queryVariables)
      val queryResult = queryRef.execute().data
      withClue(queryResult) { queryResult?.item?.value shouldBe enumValue }
    }
  }

  @Test
  fun queryNullableByEnumValue() = runTest {
    val enumArb = Arb.enum<N5ekmae3jn>().orNull(nullProbability = 0.33)
    checkAll(NUM_ITERATIONS, enumArb, enumArb, enumArb, enumArb, Arb.dataConnect.tag()) {
      value1,
      value2,
      value3,
      value4,
      tag ->
      val insertVariables = Insert3NullableVariables(tag, value1, value2, value3)
      val insertResult = dataConnect.mutation(insertVariables).execute().data
      val queryVariables = GetNullableByTagAndValueVariables(tag, value4)
      val queryResult = dataConnect.query(queryVariables).execute().data
      val matchingKeys = insertResult.keysForMatchingValues(value4, insertVariables)
      withClue(queryResult) { queryResult.items shouldContainExactlyInAnyOrder matchingKeys }
    }
  }

  @Test
  fun queryNullableByUndefinedEnumValue() = runTest {
    val enumArb = Arb.enum<N5ekmae3jn>().orNull(nullProbability = 0.5)
    checkAll(1, enumArb, enumArb, enumArb, Arb.dataConnect.tag()) { value1, value2, value3, tag ->
      val insertVariables = Insert3NullableVariables(tag, value1, value2, value3)
      val insertResult = dataConnect.mutation(insertVariables).execute().data
      val queryVariables = GetNullableByTagAndValueVariables(tag, OptionalVariable.Undefined)
      val queryResult = dataConnect.query(queryVariables).execute().data
      withClue(queryResult) { queryResult.items shouldContainExactlyInAnyOrder insertResult.keys }
    }
  }

  @Test
  fun queryNullableByDefaultEnumValue() = runTest {
    val enumArb = Arb.enum<N5ekmae3jn>().orNull(nullProbability = 0.33)
    checkAll(NUM_ITERATIONS, enumArb, enumArb, enumArb, Arb.dataConnect.tag()) {
      value1,
      value2,
      value3,
      tag ->
      val insertVariables = Insert3NullableVariables(tag, value1, value2, value3)
      val insertResult = dataConnect.mutation(insertVariables).execute().data
      val queryVariables = GetNullableByTagAndDefaultValueVariables(tag)
      val queryResult = dataConnect.query(queryVariables).execute().data
      val matchingKeys = insertResult.keysForMatchingValues(N5ekmae3jn.QJX7C7RD5T, insertVariables)
      withClue(queryResult) { queryResult.items shouldContainExactlyInAnyOrder matchingKeys }
    }
  }

  @Serializable private data class InsertNullableVariables(val value: N5ekmae3jn?)

  @Serializable
  private data class Insert3NullableVariables(
    val tag: String,
    val value1: N5ekmae3jn?,
    val value2: N5ekmae3jn?,
    val value3: N5ekmae3jn?
  )

  @Serializable private data class GetNullableByKeyVariables(val key: RowKey)

  @Serializable
  private data class GetNullableByTagAndValueVariables(
    val tag: String,
    val value: OptionalVariable<N5ekmae3jn?>
  ) {
    constructor(tag: String, value: N5ekmae3jn?) : this(tag, OptionalVariable.Value(value))
  }

  @Serializable private data class GetNullableByTagAndDefaultValueVariables(val tag: String)

  @Serializable private data class UpdateNullableVariables(val key: RowKey, val value: N5ekmae3jn?)

  @Serializable
  private data class GetNullableByKeyData(val item: Item?) {
    @Serializable data class Item(val value: N5ekmae3jn?)
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for EnumNonNullableTableDefault table.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun insertEnumNonNullableTableDefault() = runTest {
    val insertVariables = InsertNonNullableTableDefaultVariables
    val key = dataConnect.mutation(insertVariables).execute().data.key
    val queryVariables = GetNonNullableTableDefaultByKeyVariables(key)
    val queryResult = dataConnect.query(queryVariables).execute().data
    withClue(queryResult) { queryResult?.item?.value shouldBe N5ekmae3jn.RGTB44C2M8 }
  }

  @Serializable private object InsertNonNullableTableDefaultVariables

  @Serializable private data class GetNonNullableTableDefaultByKeyVariables(val key: RowKey)

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for EnumNullableTableDefault table.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun insertEnumNullableTableDefault() = runTest {
    val insertVariables = InsertNullableTableDefaultVariables
    val key = dataConnect.mutation(insertVariables).execute().data.key
    val queryVariables = GetNullableTableDefaultByKeyVariables(key)
    val queryResult = dataConnect.query(queryVariables).execute().data
    withClue(queryResult) { queryResult?.item?.value shouldBe N5ekmae3jn.ZE6Z5778RV }
  }

  @Serializable private object InsertNullableTableDefaultVariables

  @Serializable private data class GetNullableTableDefaultByKeyVariables(val key: RowKey)

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for EnumNonNullableListOfNonNullable table.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun insertNonNullableListOfNonNullable_ListContainingNonNullValues() = runTest {
    val enumArb = Arb.enum<N5ekmae3jn>()
    checkAll(NUM_ITERATIONS, Arb.list(enumArb, 0..5)) { value ->
      val insertVariables = InsertNonNullableListOfNonNullableVariables(value)
      val key = dataConnect.mutation(insertVariables).execute().data.key
      val queryVariables = GetNonNullableListOfNonNullableByKeyVariables(key)
      val queryResult = dataConnect.query(queryVariables).execute().data
      withClue(queryResult) { queryResult?.item?.value shouldBe value }
    }
  }

  @Test
  fun insertNonNullableListOfNonNullable_NullList() = runTest {
    val insertVariables = InsertNonNullableListOfNonNullableVariables(OptionalVariable.Value(null))
    val mutation = dataConnect.mutation(insertVariables)
    shouldThrow<DataConnectOperationException> { mutation.execute() }
  }

  @Test
  fun insertNonNullableListOfNonNullable_ListContainingNull() = runTest {
    val enumArb = Arb.enum<N5ekmae3jn>().orNull(nullProbability = 0.5)
    checkAll(1, Arb.list(enumArb, 0..9).filter { it.contains(null) }) { value ->
      val insertVariables = InsertNonNullableListOfNonNullableVariables(value)
      val key = dataConnect.mutation(insertVariables).execute().data.key
      val queryVariables = GetNonNullableListOfNonNullableByKeyVariables(key)
      val queryResult = dataConnect.query(queryVariables).execute().data
      withClue(queryResult) { queryResult?.item?.value shouldBe value }
    }
  }

  @Test
  fun queryNonNullableListOfNonNullableEnumValues() = runTest {
    val enumArb = Arb.enum<N5ekmae3jn>()
    checkAll(NUM_ITERATIONS, Arb.list(enumArb, 10..20)) { values ->
      val insertVariables = InsertNonNullableListOfNonNullableVariables(values)
      val key = dataConnect.mutation(insertVariables).execute().data.key
      val queryVariables = GetNonNullableListOfNonNullableByKeyVariables(key)
      val queryRef = dataConnect.query(queryVariables)
      val queryResult = queryRef.execute().data
      withClue(queryResult) { queryResult?.item?.value shouldBe values }
    }
  }

  @Serializable
  private data class InsertNonNullableListOfNonNullableVariables(
    val value: OptionalVariable<List<N5ekmae3jn?>?>
  ) {
    constructor(value: List<N5ekmae3jn?>) : this(OptionalVariable.Value(value))
  }

  @Serializable private data class GetNonNullableListOfNonNullableByKeyVariables(val key: RowKey)

  @Serializable
  private data class GetNonNullableListOfNonNullableByKeyData(val item: Item?) {
    @Serializable data class Item(val value: List<N5ekmae3jn?>)
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for EnumNonNullableListOfNullable table.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun insertNonNullableListOfNullable_ListNotContainingNull() = runTest {
    val enumArb = Arb.enum<N5ekmae3jn>()
    checkAll(NUM_ITERATIONS, Arb.list(enumArb, 0..5)) { value ->
      val insertVariables = InsertNonNullableListOfNullableVariables(value)
      val key = dataConnect.mutation(insertVariables).execute().data.key
      val queryVariables = GetNonNullableListOfNullableByKeyVariables(key)
      val queryResult = dataConnect.query(queryVariables).execute().data
      withClue(queryResult) { queryResult?.item?.value shouldBe value }
    }
  }

  @Test
  fun insertNonNullableListOfNullable_ListContainingNull() = runTest {
    val enumArb = Arb.enum<N5ekmae3jn>().orNull(nullProbability = 0.5)
    checkAll(1, Arb.list(enumArb, 0..9).filter { it.contains(null) }) { value ->
      val insertVariables = InsertNonNullableListOfNullableVariables(value)
      val key = dataConnect.mutation(insertVariables).execute().data.key
      val queryVariables = GetNonNullableListOfNullableByKeyVariables(key)
      val queryResult = dataConnect.query(queryVariables).execute().data
      withClue(queryResult) { queryResult?.item?.value shouldBe value }
    }
  }

  @Test
  fun insertNonNullableListOfNullable_NullList() = runTest {
    val insertVariables = InsertNonNullableListOfNullableVariables(OptionalVariable.Value(null))
    val mutation = dataConnect.mutation(insertVariables)
    shouldThrow<DataConnectOperationException> { mutation.execute() }
  }

  @Test
  fun queryNonNullableListOfNullableEnumValues() = runTest {
    val enumArb = Arb.enum<N5ekmae3jn>().orNull(nullProbability = 0.5)
    checkAll(NUM_ITERATIONS, Arb.list(enumArb, 10..20)) { values ->
      val insertVariables = InsertNonNullableListOfNullableVariables(values)
      val key = dataConnect.mutation(insertVariables).execute().data.key
      val queryVariables = GetNonNullableListOfNullableByKeyVariables(key)
      val queryRef = dataConnect.query(queryVariables)
      val queryResult = queryRef.execute().data
      withClue(queryResult) { queryResult?.item?.value shouldBe values }
    }
  }

  @Serializable
  private data class InsertNonNullableListOfNullableVariables(
    val value: OptionalVariable<List<N5ekmae3jn?>?>
  ) {
    constructor(value: List<N5ekmae3jn?>) : this(OptionalVariable.Value(value))
  }

  @Serializable private data class GetNonNullableListOfNullableByKeyVariables(val key: RowKey)

  @Serializable
  private data class GetNonNullableListOfNullableByKeyData(val item: Item?) {
    @Serializable data class Item(val value: List<N5ekmae3jn?>)
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for EnumNullableListOfNonNullable table.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun insertNullableListOfNonNullable_ListContainingNonNullValues() = runTest {
    val enumArb = Arb.enum<N5ekmae3jn>()
    checkAll(NUM_ITERATIONS, Arb.list(enumArb, 0..5)) { value ->
      val insertVariables = InsertNullableListOfNonNullableVariables(value)
      val key = dataConnect.mutation(insertVariables).execute().data.key
      val queryVariables = GetNullableListOfNonNullableByKeyVariables(key)
      val queryResult = dataConnect.query(queryVariables).execute().data
      withClue(queryResult) { queryResult?.item?.value shouldBe value }
    }
  }

  @Test
  fun insertNullableListOfNonNullable_NullList() = runTest {
    val insertVariables = InsertNullableListOfNonNullableVariables(null)
    val key = dataConnect.mutation(insertVariables).execute().data.key
    val queryVariables = GetNullableListOfNonNullableByKeyVariables(key)
    val queryResult = dataConnect.query(queryVariables).execute().data
    withClue(queryResult) { queryResult?.item?.value.shouldBeNull() }
  }

  @Test
  fun insertNullableListOfNonNullable_ListContainingNull() = runTest {
    val enumArb = Arb.enum<N5ekmae3jn>().orNull(nullProbability = 0.5)
    checkAll(1, Arb.list(enumArb, 0..9).filter { it.contains(null) }) { value ->
      val insertVariables = InsertNullableListOfNonNullableVariables(value)
      val key = dataConnect.mutation(insertVariables).execute().data.key
      val queryVariables = GetNullableListOfNonNullableByKeyVariables(key)
      val queryResult = dataConnect.query(queryVariables).execute().data
      withClue(queryResult) { queryResult?.item?.value shouldBe value }
    }
  }

  @Test
  fun queryNullableListOfNonNullableEnumValues() = runTest {
    val enumArb = Arb.enum<N5ekmae3jn>().orNull(nullProbability = 0.5)
    checkAll(NUM_ITERATIONS, Arb.list(enumArb, 10..20)) { values ->
      val insertVariables = InsertNullableListOfNonNullableVariables(values)
      val key = dataConnect.mutation(insertVariables).execute().data.key
      val queryVariables = GetNullableListOfNonNullableByKeyVariables(key)
      val queryRef = dataConnect.query(queryVariables)
      val queryResult = queryRef.execute().data
      withClue(queryResult) { queryResult?.item?.value shouldBe values }
    }
  }

  @Serializable
  private data class InsertNullableListOfNonNullableVariables(
    val value: OptionalVariable<List<N5ekmae3jn?>?>
  ) {
    constructor(value: List<N5ekmae3jn?>?) : this(OptionalVariable.Value(value))
  }

  @Serializable private data class GetNullableListOfNonNullableByKeyVariables(val key: RowKey)

  @Serializable
  private data class GetNullableListOfNonNullableByKeyData(val item: Item?) {
    @Serializable data class Item(val value: List<N5ekmae3jn?>?)
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for EnumNullableListOfNullable table.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun insertNullableListOfNullable_ListContainingNonNullValues() = runTest {
    val enumArb = Arb.enum<N5ekmae3jn>()
    checkAll(NUM_ITERATIONS, Arb.list(enumArb, 0..5)) { value ->
      val insertVariables = InsertNullableListOfNullableVariables(value)
      val key = dataConnect.mutation(insertVariables).execute().data.key
      val queryVariables = GetNullableListOfNullableByKeyVariables(key)
      val queryResult = dataConnect.query(queryVariables).execute().data
      withClue(queryResult) { queryResult?.item?.value shouldBe value }
    }
  }

  @Test
  fun insertNullableListOfNullable_NullList() = runTest {
    val insertVariables = InsertNullableListOfNullableVariables(null)
    val key = dataConnect.mutation(insertVariables).execute().data.key
    val queryVariables = GetNullableListOfNullableByKeyVariables(key)
    val queryResult = dataConnect.query(queryVariables).execute().data
    withClue(queryResult) { queryResult?.item?.value.shouldBeNull() }
  }

  @Test
  fun insertNullableListOfNullable_ListContainingNull() = runTest {
    val enumArb = Arb.enum<N5ekmae3jn>().orNull(nullProbability = 0.5)
    checkAll(1, Arb.list(enumArb, 0..9).filter { it.contains(null) }) { value ->
      val insertVariables = InsertNullableListOfNullableVariables(value)
      val key = dataConnect.mutation(insertVariables).execute().data.key
      val queryVariables = GetNullableListOfNullableByKeyVariables(key)
      val queryResult = dataConnect.query(queryVariables).execute().data
      withClue(queryResult) { queryResult?.item?.value shouldBe value }
    }
  }

  @Test
  fun queryNullableListOfNullableEnumValues() = runTest {
    val enumArb = Arb.enum<N5ekmae3jn>().orNull(nullProbability = 0.5)
    checkAll(NUM_ITERATIONS, Arb.list(enumArb, 10..20)) { values ->
      val insertVariables = InsertNullableListOfNullableVariables(values)
      val key = dataConnect.mutation(insertVariables).execute().data.key
      val queryVariables = GetNullableListOfNullableByKeyVariables(key)
      val queryRef = dataConnect.query(queryVariables)
      val queryResult = queryRef.execute().data
      withClue(queryResult) { queryResult?.item?.value shouldBe values }
    }
  }

  @Serializable
  private data class InsertNullableListOfNullableVariables(
    val value: OptionalVariable<List<N5ekmae3jn?>?>
  ) {
    constructor(value: List<N5ekmae3jn?>?) : this(OptionalVariable.Value(value))
  }

  @Serializable private data class GetNullableListOfNullableByKeyVariables(val key: RowKey)

  @Serializable
  private data class GetNullableListOfNullableByKeyData(val item: Item?) {
    @Serializable data class Item(val value: List<N5ekmae3jn?>?)
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for EnumKey table.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  @Ignore(
    "TODO(b/432793533) Re-enable this test once the emulator crash " +
      "caused by the \"EnumKey_GetByKey\" query is fixed."
  )
  fun enumAsPrimaryKey() = runTest {
    N5ekmae3jn.entries.forEach { enumValue ->
      val tag = Arb.dataConnect.tag().next(rs)
      val insertVariables = InsertEnumKeyVariables(enumValue, tag)
      val key = dataConnect.mutation(insertVariables).execute().data.key
      withClue(key) { key.enumValue shouldBe enumValue }
      val queryVariables = GetEnumKeyByKeyVariables(key)
      val queryResult = dataConnect.query(queryVariables).execute().data
      withClue(queryResult) { queryResult?.item?.tag shouldBe tag }
    }
  }

  @Serializable
  private data class InsertEnumKeyVariables(val enumValue: N5ekmae3jn, val tag: String)

  @Serializable private data class InsertEnumKeyData(val key: EnumKeyKey)

  @Serializable private data class EnumKeyKey(val id: String, val enumValue: N5ekmae3jn)

  @Serializable private data class GetEnumKeyByKeyVariables(val key: EnumKeyKey)

  @Serializable
  private data class GetEnumKeyByKeyData(val item: Item?) {
    @Serializable data class Item(val tag: String)
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for MultipleEnumColumns table.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun multipleEnumColumns() = runTest {
    checkAll(NUM_ITERATIONS, Arb.enum<N5ekmae3jn>(), Arb.enum<S7yayynb25>()) { enum1, enum2 ->
      val insertVariables = InsertMultipleEnumColumnsVariables(enum1, enum2)
      val key = dataConnect.mutation(insertVariables).execute().data.key
      val queryVariables = GetMultipleEnumColumnsByKeyVariables(key)
      val queryResult = dataConnect.query(queryVariables).execute().data
      withClue(queryResult) {
        assertSoftly {
          queryResult?.item?.enum1 shouldBe enum1
          queryResult?.item?.enum2 shouldBe enum2
        }
      }
    }
  }

  @Serializable
  private data class InsertMultipleEnumColumnsVariables(
    val enum1: N5ekmae3jn,
    val enum2: S7yayynb25
  )

  @Serializable private data class GetMultipleEnumColumnsByKeyVariables(val key: RowKey)

  @Serializable
  private data class GetMultipleEnumColumnsByKeyData(val item: Item?) {
    @Serializable data class Item(val enum1: N5ekmae3jn, val enum2: S7yayynb25)
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Helper classes and functions.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Serializable private data class RowKey(val id: String)

  @Serializable private data class QueryAllData(val items: List<RowKey>)

  @Serializable private data class InsertData(val key: RowKey)

  @Serializable
  private data class Insert3Data(val key1: RowKey, val key2: RowKey, val key3: RowKey) {

    val keys: List<RowKey>
      get() = listOf(key1, key2, key3)

    fun keysForMatchingValues(
      value: N5ekmae3jn,
      variables: Insert3NonNullableVariables
    ): List<RowKey> =
      keysForMatchingValues(value, variables.value1, variables.value2, variables.value3)

    fun keysForMatchingValues(
      value: N5ekmae3jn?,
      variables: Insert3NullableVariables
    ): List<RowKey> =
      keysForMatchingValues(value, variables.value1, variables.value2, variables.value3)

    private fun keysForMatchingValues(
      value: N5ekmae3jn?,
      value1: N5ekmae3jn?,
      value2: N5ekmae3jn?,
      value3: N5ekmae3jn?,
    ): List<RowKey> = buildList {
      if (value1 == value) {
        add(key1)
      }
      if (value2 == value) {
        add(key2)
      }
      if (value3 == value) {
        add(key3)
      }
    }
  }

  @Suppress("SpellCheckingInspection")
  private enum class N5ekmae3jn {
    DPSKD6HR3A,
    XGWGVMYTHJ,
    QJX7C7RD5T,
    RGTB44C2M8,
    ZE6Z5778RV,
    N3HWNCRWBP,
  }

  @Suppress("SpellCheckingInspection", "unused")
  enum class S7yayynb25 {
    XJ27ZAXKD3,
    R36KQ8PT5K,
    ETCV3FN9GH,
    NMAJAGZHDS
  }

  private companion object {

    /** The default number of iterations to use in property-based tests. */
    const val NUM_ITERATIONS = 10

    fun FirebaseDataConnect.mutation(
      variables: InsertNonNullableVariables
    ): MutationRef<InsertData, InsertNonNullableVariables> =
      mutation("EnumNonNullable_Insert", variables, serializer(), serializer())

    fun FirebaseDataConnect.mutation(
      variables: InsertNonNullableNullVariables
    ): MutationRef<Unit, InsertNonNullableNullVariables> =
      mutation("EnumNonNullable_Insert", variables, serializer(), serializer())

    fun FirebaseDataConnect.mutation(
      variables: InsertNonNullableTableDefaultVariables
    ): MutationRef<InsertData, InsertNonNullableTableDefaultVariables> =
      mutation("EnumNonNullableTableDefault_Insert", variables, serializer(), serializer())

    fun FirebaseDataConnect.mutation(
      variables: InsertNullableTableDefaultVariables
    ): MutationRef<InsertData, InsertNullableTableDefaultVariables> =
      mutation("EnumNullableTableDefault_Insert", variables, serializer(), serializer())

    fun FirebaseDataConnect.mutation(
      variables: InsertNonNullableListOfNonNullableVariables
    ): MutationRef<InsertData, InsertNonNullableListOfNonNullableVariables> =
      mutation("EnumNonNullableListOfNonNullable_Insert", variables, serializer(), serializer())

    fun FirebaseDataConnect.mutation(
      variables: InsertNonNullableListOfNullableVariables
    ): MutationRef<InsertData, InsertNonNullableListOfNullableVariables> =
      mutation("EnumNonNullableListOfNullable_Insert", variables, serializer(), serializer())

    fun FirebaseDataConnect.mutation(
      variables: InsertNullableListOfNonNullableVariables
    ): MutationRef<InsertData, InsertNullableListOfNonNullableVariables> =
      mutation("EnumNullableListOfNonNullable_Insert", variables, serializer(), serializer())

    fun FirebaseDataConnect.mutation(
      variables: InsertNullableListOfNullableVariables
    ): MutationRef<InsertData, InsertNullableListOfNullableVariables> =
      mutation("EnumNullableListOfNullable_Insert", variables, serializer(), serializer())

    fun FirebaseDataConnect.mutation(
      variables: Insert3NonNullableVariables
    ): MutationRef<Insert3Data, Insert3NonNullableVariables> =
      mutation("EnumNonNullable_Insert3", variables, serializer(), serializer())

    fun FirebaseDataConnect.mutation(
      variables: UpdateNonNullableVariables
    ): MutationRef<Unit, UpdateNonNullableVariables> =
      mutation("EnumNonNullable_UpdateByKey", variables, serializer(), serializer())

    fun FirebaseDataConnect.mutation(
      variables: InsertEnumKeyVariables
    ): MutationRef<InsertEnumKeyData, InsertEnumKeyVariables> =
      mutation("EnumKey_Insert", variables, serializer(), serializer())

    fun FirebaseDataConnect.mutation(
      variables: InsertMultipleEnumColumnsVariables
    ): MutationRef<InsertData, InsertMultipleEnumColumnsVariables> =
      mutation("MultipleEnumColumns_Insert", variables, serializer(), serializer())

    fun FirebaseDataConnect.query(
      variables: GetNonNullableByKeyVariables
    ): QueryRef<GetNonNullableByKeyData?, GetNonNullableByKeyVariables> =
      query("EnumNonNullable_GetByKey", variables, serializer(), serializer())

    fun FirebaseDataConnect.query(
      variables: GetNonNullableListOfNonNullableByKeyVariables
    ): QueryRef<
      GetNonNullableListOfNonNullableByKeyData?, GetNonNullableListOfNonNullableByKeyVariables
    > = query("EnumNonNullableListOfNonNullable_GetByKey", variables, serializer(), serializer())

    fun FirebaseDataConnect.query(
      variables: GetNonNullableListOfNullableByKeyVariables
    ): QueryRef<
      GetNonNullableListOfNullableByKeyData?, GetNonNullableListOfNullableByKeyVariables
    > = query("EnumNonNullableListOfNullable_GetByKey", variables, serializer(), serializer())

    fun FirebaseDataConnect.query(
      variables: GetNullableListOfNonNullableByKeyVariables
    ): QueryRef<
      GetNullableListOfNonNullableByKeyData?, GetNullableListOfNonNullableByKeyVariables
    > = query("EnumNullableListOfNonNullable_GetByKey", variables, serializer(), serializer())

    fun FirebaseDataConnect.query(
      variables: GetNullableListOfNullableByKeyVariables
    ): QueryRef<GetNullableListOfNullableByKeyData?, GetNullableListOfNullableByKeyVariables> =
      query("EnumNullableListOfNullable_GetByKey", variables, serializer(), serializer())

    fun FirebaseDataConnect.query(
      variables: GetNonNullableByTagAndValueVariables
    ): QueryRef<QueryAllData, GetNonNullableByTagAndValueVariables> =
      query("EnumNonNullable_GetAllByTagAndValue", variables, serializer(), serializer())

    fun FirebaseDataConnect.query(
      variables: GetNonNullableByTagAndDefaultValueVariables
    ): QueryRef<QueryAllData, GetNonNullableByTagAndDefaultValueVariables> =
      query("EnumNonNullable_GetAllByTagAndDefaultValue", variables, serializer(), serializer())

    fun FirebaseDataConnect.query(
      variables: GetEnumKeyByKeyVariables
    ): QueryRef<GetEnumKeyByKeyData?, GetEnumKeyByKeyVariables> =
      query("EnumKey_GetByKey", variables, serializer(), serializer())

    fun FirebaseDataConnect.query(
      variables: GetMultipleEnumColumnsByKeyVariables
    ): QueryRef<GetMultipleEnumColumnsByKeyData?, GetMultipleEnumColumnsByKeyVariables> =
      query("MultipleEnumColumns_GetByKey", variables, serializer(), serializer())

    fun FirebaseDataConnect.mutation(
      variables: InsertNullableVariables
    ): MutationRef<InsertData, InsertNullableVariables> =
      mutation("EnumNullable_Insert", variables, serializer(), serializer())

    fun FirebaseDataConnect.mutation(
      variables: Insert3NullableVariables
    ): MutationRef<Insert3Data, Insert3NullableVariables> =
      mutation("EnumNullable_Insert3", variables, serializer(), serializer())

    fun FirebaseDataConnect.mutation(
      variables: UpdateNullableVariables
    ): MutationRef<Unit, UpdateNullableVariables> =
      mutation("EnumNullable_UpdateByKey", variables, serializer(), serializer())

    fun FirebaseDataConnect.query(
      variables: GetNullableByKeyVariables
    ): QueryRef<GetNullableByKeyData?, GetNullableByKeyVariables> =
      query("EnumNullable_GetByKey", variables, serializer(), serializer())

    fun FirebaseDataConnect.query(
      variables: GetNonNullableTableDefaultByKeyVariables
    ): QueryRef<GetNullableByKeyData?, GetNonNullableTableDefaultByKeyVariables> =
      query("EnumNonNullableTableDefault_GetByKey", variables, serializer(), serializer())

    fun FirebaseDataConnect.query(
      variables: GetNullableTableDefaultByKeyVariables
    ): QueryRef<GetNullableByKeyData?, GetNullableTableDefaultByKeyVariables> =
      query("EnumNullableTableDefault_GetByKey", variables, serializer(), serializer())

    fun FirebaseDataConnect.query(
      variables: GetNullableByTagAndValueVariables
    ): QueryRef<QueryAllData, GetNullableByTagAndValueVariables> =
      query("EnumNullable_GetAllByTagAndValue", variables, serializer(), serializer())

    fun FirebaseDataConnect.query(
      variables: GetNullableByTagAndDefaultValueVariables
    ): QueryRef<QueryAllData, GetNullableByTagAndDefaultValueVariables> =
      query("EnumNullable_GetAllByTagAndDefaultValue", variables, serializer(), serializer())
  }
}
