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
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.checkAll
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Test

class EnumIntegrationTest : DemoConnectorIntegrationTestBase() {

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for EnumNonNullable table.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun insertNonNullableNonNullEnumValue() = runTest {
    N5ekmae3jn.entries.forEach { enumValue ->
      val key = connector.enumNonNullableInsert.execute(enumValue).data.key
      val queryResult = connector.enumNonNullableGetByKey.execute(key).data
      withClue(queryResult) { queryResult.item?.value shouldBe enumValue }
    }
  }

  @Test
  fun updateNonNullableEnumValue() = runTest {
    val enumArb = Arb.enum<N5ekmae3jn>()
    checkAll(NUM_ITERATIONS, enumArb, enumArb) { value1, value2 ->
      val key = connector.enumNonNullableInsert.execute(value1).data.key
      val updateResult = connector.enumNonNullableUpdateByKey.execute(key) { value = value2 }
      withClue(updateResult) { updateResult.data.key shouldBe key }
      val queryResult = connector.enumNonNullableGetByKey.execute(key).data
      withClue(queryResult) { queryResult.item?.value shouldBe value2 }
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
      val insertResult = connector.enumNonNullableInsert3.execute(tag, value1, value2, value3).data
      val queryResult = connector.enumNonNullableGetAllByTagAndValue.execute(tag, value4).data
      val matchingIds = insertResult.idsForMatchingValues(value4, value1, value2, value3)
      withClue(queryResult) {
        queryResult.items.map { it.id } shouldContainExactlyInAnyOrder matchingIds
      }
    }
  }

  @Test
  fun queryNonNullableByUndefinedEnumValue() = runTest {
    val enumArb = Arb.enum<N5ekmae3jn>()
    checkAll(1, enumArb, enumArb, enumArb, Arb.dataConnect.tag()) { value1, value2, value3, tag ->
      val insertResult = connector.enumNonNullableInsert3.execute(tag, value1, value2, value3).data
      val queryResult = connector.enumNonNullableGetAllByTagAndMaybeValue.execute(tag).data
      withClue(queryResult) {
        queryResult.items.map { it.id } shouldContainExactlyInAnyOrder insertResult.ids
      }
    }
  }

  @Test
  fun queryNonNullableByNullEnumValue() = runTest {
    val enumArb = Arb.enum<N5ekmae3jn>()
    checkAll(1, enumArb, enumArb, enumArb, Arb.dataConnect.tag()) { value1, value2, value3, tag ->
      connector.enumNonNullableInsert3.execute(tag, value1, value2, value3)
      val queryResult =
        connector.enumNonNullableGetAllByTagAndMaybeValue.execute(tag) { value = null }.data
      withClue(queryResult) { queryResult.items.shouldBeEmpty() }
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
      val insertResult = connector.enumNonNullableInsert3.execute(tag, value1, value2, value3).data
      val queryResult = connector.enumNonNullableGetAllByTagAndDefaultValue.execute(tag).data
      val matchingIds =
        insertResult.idsForMatchingValues(N5ekmae3jn.XGWGVMYTHJ, value1, value2, value3)
      withClue(queryResult) {
        queryResult.items.map { it.id } shouldContainExactlyInAnyOrder matchingIds
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Helper classes and functions.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  private companion object {

    /** The default number of iterations to use in property-based tests. */
    const val NUM_ITERATIONS = 10

    val EnumNonNullableInsert3Mutation.Data.ids: List<UUID>
      get() = listOf(key1, key2, key3).map { it.id }

    fun EnumNonNullableInsert3Mutation.Data.idsForMatchingValues(
      value: N5ekmae3jn,
      value1: N5ekmae3jn,
      value2: N5ekmae3jn,
      value3: N5ekmae3jn
    ): List<UUID> =
      keysForMatchingValues(value, value1, key1, value2, key2, value3, key3).map { it.id }

    private fun <T, K> keysForMatchingValues(
      value: T,
      value1: T,
      key1: K,
      value2: T,
      key2: K,
      value3: T,
      key3: K,
    ): List<K> = buildList {
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
}
