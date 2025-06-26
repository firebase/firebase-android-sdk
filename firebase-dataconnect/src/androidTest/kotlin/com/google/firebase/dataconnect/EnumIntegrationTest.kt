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

package com.google.firebase.dataconnect

import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
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
      val mutationRef = dataConnect.mutation(InsertNonNullableVariables(enumValue))
      val id = mutationRef.execute().data.key.id
      val queryRef = dataConnect.query(GetNonNullableByKeyVariables(id))
      val queryResult = queryRef.execute().data
      withClue(queryResult) { queryResult?.item?.value shouldBe enumValue }
    }
  }

  @Serializable private data class RowKey(val id: String)

  @Serializable private data class InsertData(val key: RowKey)

  @Serializable private data class InsertNonNullableVariables(val value: N5ekmae3jn)

  @Serializable
  private data class GetNonNullableByKeyVariables(val key: RowKey) {
    constructor(id: String) : this(RowKey(id))
  }

  @Serializable
  private data class GetNonNullableByKeyData(val item: Item?) {
    @Serializable data class Item(val value: N5ekmae3jn)
  }

  private enum class N5ekmae3jn {
    DPSKD6HR3A,
    XGWGVMYTHJ,
    QJX7C7RD5T,
  }

  private companion object {

    fun FirebaseDataConnect.mutation(
      variables: InsertNonNullableVariables
    ): MutationRef<InsertData, InsertNonNullableVariables> =
      mutation("EnumNonNullable_Insert", variables, serializer(), serializer())

    fun FirebaseDataConnect.query(
      variables: GetNonNullableByKeyVariables
    ): QueryRef<GetNonNullableByKeyData?, GetNonNullableByKeyVariables> =
      query("EnumNonNullable_GetByKey", variables, serializer(), serializer())
  }
}
