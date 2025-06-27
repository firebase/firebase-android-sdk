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

package com.google.firebase.dataconnect

import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.enum
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.junit.Test

class EnumIntegrationTest : DataConnectIntegrationTestBase() {

  private val dataConnect: FirebaseDataConnect by lazy {
    val connectorConfig = testConnectorConfig.copy(connector = "demo")
    dataConnectFactory.newInstance(connectorConfig)
  }

  @Test
  fun insertNonNullableEnumValue() = runTest {
    N5ekmae3jn.values().forEach { enumValue ->
      val insertVariables = InsertNonNullableVariables(enumValue)
      val key = dataConnect.mutation(insertVariables).execute().data.key
      val queryVariables = GetNonNullableByKeyVariables(key)
      val queryResult = dataConnect.query(queryVariables).execute().data
      withClue(queryResult) { queryResult?.item?.value shouldBe enumValue }
    }
  }

  @Test
  fun updateNonNullableEnumValue() = runTest {
    val enumArb = Arb.enum<N5ekmae3jn>()
    checkAll(propTestConfig, enumArb, enumArb) { value1, value2 ->
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
  fun queryByNonNullableEnumValue() = runTest {
    val enumArb = Arb.enum<N5ekmae3jn>()
    checkAll(propTestConfig, enumArb, enumArb, enumArb, enumArb, Arb.dataConnect.tag()) {
      value1,
      value2,
      value3,
      value4,
      tag ->
      val insertVariables = Insert3NonNullableVariables(tag, value1, value2, value3)
      val insertResult = dataConnect.mutation(insertVariables).execute().data
      val matchingKeys = insertResult.keysForMatchingValues(value4, insertVariables)
      val queryVariables = GetNonNullableByTagAndValueVariables(tag, value4)
      val queryResult = dataConnect.query(queryVariables).execute().data
      withClue(queryResult) { queryResult.items shouldContainExactlyInAnyOrder matchingKeys }
    }
  }

  @Serializable private data class RowKey(val id: String)

  @Serializable private data class QueryAllData(val items: List<RowKey>)

  @Serializable private data class InsertData(val key: RowKey)

  @Serializable
  private data class Insert3Data(val key1: RowKey, val key2: RowKey, val key3: RowKey) {

    fun keysForMatchingValues(
      value: N5ekmae3jn,
      variables: Insert3NonNullableVariables
    ): List<RowKey> = buildList {
      if (variables.value1 == value) {
        add(key1)
      }
      if (variables.value2 == value) {
        add(key2)
      }
      if (variables.value3 == value) {
        add(key3)
      }
    }
  }

  @Serializable private data class InsertNonNullableVariables(val value: N5ekmae3jn)

  @Serializable
  private data class Insert3NonNullableVariables(
    val tag: String,
    val value1: N5ekmae3jn,
    val value2: N5ekmae3jn,
    val value3: N5ekmae3jn
  )

  @Serializable private data class GetNonNullableByKeyVariables(val key: RowKey)

  @Serializable
  private data class GetNonNullableByTagAndValueVariables(val tag: String, val value: N5ekmae3jn)

  @Serializable
  private data class UpdateNonNullableVariables(val key: RowKey, val value: N5ekmae3jn)

  @Serializable
  private data class GetNonNullableByKeyData(val item: Item?) {
    @Serializable data class Item(val value: N5ekmae3jn)
  }

  private enum class N5ekmae3jn {
    DPSKD6HR3A,
    XGWGVMYTHJ,
    QJX7C7RD5T,
    RGTB44C2M8,
    ZE6Z5778RV,
    N3HWNCRWBP,
  }

  private companion object {

    val propTestConfig = PropTestConfig(iterations = 10)

    fun FirebaseDataConnect.mutation(
      variables: InsertNonNullableVariables
    ): MutationRef<InsertData, InsertNonNullableVariables> =
      mutation("EnumNonNullable_Insert", variables, serializer(), serializer())

    fun FirebaseDataConnect.mutation(
      variables: Insert3NonNullableVariables
    ): MutationRef<Insert3Data, Insert3NonNullableVariables> =
      mutation("EnumNonNullable_Insert3", variables, serializer(), serializer())

    fun FirebaseDataConnect.mutation(
      variables: UpdateNonNullableVariables
    ): MutationRef<Unit, UpdateNonNullableVariables> =
      mutation("EnumNonNullable_UpdateByKey", variables, serializer(), serializer())

    fun FirebaseDataConnect.query(
      variables: GetNonNullableByKeyVariables
    ): QueryRef<GetNonNullableByKeyData?, GetNonNullableByKeyVariables> =
      query("EnumNonNullable_GetByKey", variables, serializer(), serializer())

    fun FirebaseDataConnect.query(
      variables: GetNonNullableByTagAndValueVariables
    ): QueryRef<QueryAllData, GetNonNullableByTagAndValueVariables> =
      query("EnumNonNullable_GetAllByTagAndValue", variables, serializer(), serializer())
  }
}
