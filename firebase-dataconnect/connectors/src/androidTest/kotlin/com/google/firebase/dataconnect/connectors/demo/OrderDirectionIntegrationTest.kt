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

import com.google.firebase.dataconnect.connectors.demo.testutil.DemoConnectorIntegrationTestBase
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.sortedParallelTo
import io.kotest.common.DelicateKotest
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.property.Arb
import io.kotest.property.arbitrary.distinct
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.next
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Test

class OrderDirectionIntegrationTest : DemoConnectorIntegrationTestBase() {

  @OptIn(DelicateKotest::class) private val uniqueInts = Arb.int().distinct()

  @Test
  fun orderDirectionQueryVariableOmittedShouldUseUnspecifiedOrder() = runTest {
    val tag = Arb.dataConnect.tag().next(rs)
    val values = List(5) { uniqueInts.next(rs) }
    val insertedIds = insertRow(tag, values)

    val queryResult = connector.orderDirectionTestGetAllByTag.execute(tag) {}

    val queryIds = queryResult.data.items.map { it.id }
    queryIds shouldContainExactlyInAnyOrder insertedIds
  }

  @Test
  fun orderDirectionQueryVariableAscendingOrder() = runTest {
    val tag = Arb.dataConnect.tag().next(rs)
    val values = List(5) { uniqueInts.next(rs) }
    val insertedIds = insertRow(tag, values)

    val queryResult =
      connector.orderDirectionTestGetAllByTag.execute(tag) { orderDirection = OrderDirection.ASC }

    val queryIds = queryResult.data.items.map { it.id }
    val insertedIdsSorted = insertedIds.sortedParallelTo(values)
    queryIds shouldContainExactly insertedIdsSorted
  }

  @Test
  fun orderDirectionQueryVariableDescendingOrder() = runTest {
    val tag = Arb.dataConnect.tag().next(rs)
    val values = List(5) { uniqueInts.next(rs) }
    val insertedIds = insertRow(tag, values)

    val queryResult =
      connector.orderDirectionTestGetAllByTag.execute(tag) { orderDirection = OrderDirection.DESC }

    val queryIds = queryResult.data.items.map { it.id }
    val insertedIdsSorted = insertedIds.sortedParallelTo(values).reversed()
    queryIds shouldContainExactly insertedIdsSorted
  }

  private suspend fun insertRow(tag: String, values: List<Int>): List<UUID> {
    require(values.size == 5) { "values.size must be 5, but got ${values.size}" }
    return insertRow(tag, values[0], values[1], values[2], values[3], values[4])
  }

  private suspend fun insertRow(
    tag: String,
    value1: Int,
    value2: Int,
    value3: Int,
    value4: Int,
    value5: Int,
  ): List<UUID> {
    val result =
      connector.orderDirectionTestInsert5.execute(tag, value1, value2, value3, value4, value5)
    return result.data.run { listOf(key1.id, key2.id, key3.id, key4.id, key5.id) }
  }
}
