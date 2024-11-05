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
import com.google.firebase.dataconnect.testutil.sortedParallelTo
import io.kotest.common.DelicateKotest
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.property.Arb
import io.kotest.property.arbitrary.distinct
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.next
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.junit.Test

class OrderDirectionIntegrationTest : DataConnectIntegrationTestBase() {

  private val dataConnect: FirebaseDataConnect by lazy {
    val connectorConfig = testConnectorConfig.copy(connector = "demo")
    dataConnectFactory.newInstance(connectorConfig)
  }

  @OptIn(DelicateKotest::class) private val uniqueInts = Arb.int().distinct()

  @Test
  fun orderDirectionQueryVariableOmittedShouldUseUnspecifiedOrder() = runTest {
    val tag = Arb.dataConnect.tag().next(rs)
    val values = List(5) { uniqueInts.next(rs) }
    val insertedIds = insertRow(tag, values)

    val queryIds = getRowIds(tag)

    queryIds shouldContainExactlyInAnyOrder insertedIds
  }

  @Test
  fun orderDirectionQueryVariableAscendingOrder() = runTest {
    val tag = Arb.dataConnect.tag().next(rs)
    val values = List(5) { uniqueInts.next(rs) }
    val insertedIds = insertRow(tag, values)

    val queryIds = getRowIds(tag, orderDirection = "ASC")

    val insertedIdsSorted = insertedIds.sortedParallelTo(values)
    queryIds shouldContainExactly insertedIdsSorted
  }

  @Test
  fun orderDirectionQueryVariableDescendingOrder() = runTest {
    val tag = Arb.dataConnect.tag().next(rs)
    val values = List(5) { uniqueInts.next(rs) }
    val insertedIds = insertRow(tag, values)

    val queryIds = getRowIds(tag, orderDirection = "DESC")

    val insertedIdsSorted = insertedIds.sortedParallelTo(values).reversed()
    queryIds shouldContainExactly insertedIdsSorted
  }

  private suspend fun insertRow(tag: String, values: List<Int>): List<String> {
    require(values.size == 5) { "values.size must be 5, but got ${values.size}" }
    return insertRow(tag, values[0], values[1], values[2], values[3], values[4])
  }

  private suspend fun insertRow(
    tag: String,
    value1: Int,
    value2: Int,
    value3: Int,
    value4: Int,
    value5: Int
  ): List<String> {
    val variables = OrderDirectionTestInsert5Variables(tag, value1, value2, value3, value4, value5)
    val mutationRef =
      dataConnect.mutation<OrderDirectionTestInsert5Data, OrderDirectionTestInsert5Variables>(
        operationName = "OrderDirectionTestInsert5",
        variables = variables,
        dataDeserializer = serializer(),
        variablesSerializer = serializer(),
      )
    val result = mutationRef.execute()
    return result.data.run { listOf(key1.id, key2.id, key3.id, key4.id, key5.id) }
  }

  private suspend fun getRowIds(tag: String, orderDirection: String? = null): List<String> {
    val optionalOrderDirection =
      if (orderDirection !== null) OptionalVariable.Value(orderDirection)
      else OptionalVariable.Undefined
    val variables = OrderDirectionTestGetAllByTagVariables(tag, optionalOrderDirection)
    val queryRef =
      dataConnect.query<OrderDirectionTestGetAllByTagData, OrderDirectionTestGetAllByTagVariables>(
        operationName = "OrderDirectionTestGetAllByTag",
        variables = variables,
        dataDeserializer = serializer(),
        variablesSerializer = serializer(),
      )
    val result = queryRef.execute()
    return result.data.items.map { it.id }
  }

  @Serializable
  data class OrderDirectionTestInsert5Variables(
    val tag: String,
    val value1: Int,
    val value2: Int,
    val value3: Int,
    val value4: Int,
    val value5: Int,
  )

  @Serializable data class OrderDirectionTestKey(val id: String)

  @Serializable
  data class OrderDirectionTestInsert5Data(
    val key1: OrderDirectionTestKey,
    val key2: OrderDirectionTestKey,
    val key3: OrderDirectionTestKey,
    val key4: OrderDirectionTestKey,
    val key5: OrderDirectionTestKey,
  )

  @Serializable
  data class OrderDirectionTestGetAllByTagVariables(
    val tag: String,
    val orderDirection: OptionalVariable<String>,
  )

  @Serializable
  data class OrderDirectionTestGetAllByTagData(val items: List<Item>) {
    @Serializable data class Item(val id: String)
  }
}
