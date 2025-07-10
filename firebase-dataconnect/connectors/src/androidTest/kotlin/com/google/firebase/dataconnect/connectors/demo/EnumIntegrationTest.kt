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
import com.google.firebase.dataconnect.testutil.property.arbitrary.threeValues
import com.google.firebase.dataconnect.testutil.property.arbitrary.twoValues
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.orNull
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
    checkAll(NUM_ITERATIONS, Arb.twoValues(Arb.enum<N5ekmae3jn>())) { values ->
      val (value1, value2) = values
      val key = connector.enumNonNullableInsert.execute(value1).data.key
      val updateResult = connector.enumNonNullableUpdateByKey.execute(key) { value = value2 }
      withClue(updateResult) { updateResult.data.key shouldBe key }
      val queryResult = connector.enumNonNullableGetByKey.execute(key).data
      withClue(queryResult) { queryResult.item?.value shouldBe value2 }
    }
  }

  @Test
  fun queryNonNullableByNonNullEnumValue() = runTest {
    checkAll(NUM_ITERATIONS, Arb.insert3TestData(Arb.enum<N5ekmae3jn>())) { testData ->
      val (tag, insertValue1, insertValue2, insertValue3, queryValue) = testData
      val insertResult =
        connector.enumNonNullableInsert3.execute(tag, insertValue1, insertValue2, insertValue3).data
      val queryResult = connector.enumNonNullableGetAllByTagAndValue.execute(tag, queryValue).data
      val matchingIds = insertResult.idsForMatchingValues(testData)
      withClue(queryResult) {
        queryResult.items.map { it.id } shouldContainExactlyInAnyOrder matchingIds
      }
    }
  }

  @Test
  fun queryNonNullableByUndefinedEnumValue() = runTest {
    checkAll(NUM_ITERATIONS, Arb.insert3TestData(Arb.enum<N5ekmae3jn>())) { testData ->
      val (tag, insertValue1, insertValue2, insertValue3) = testData
      val insertResult =
        connector.enumNonNullableInsert3.execute(tag, insertValue1, insertValue2, insertValue3).data
      val queryResult = connector.enumNonNullableGetAllByTagAndMaybeValue.execute(tag).data
      withClue(queryResult) {
        queryResult.items.map { it.id } shouldContainExactlyInAnyOrder insertResult.ids
      }
    }
  }

  @Test
  fun queryNonNullableByNullEnumValue() = runTest {
    checkAll(NUM_ITERATIONS, Arb.insert3TestData(Arb.enum<N5ekmae3jn>())) { testData ->
      val (tag, insertValue1, insertValue2, insertValue3) = testData
      connector.enumNonNullableInsert3.execute(tag, insertValue1, insertValue2, insertValue3)
      val queryResult =
        connector.enumNonNullableGetAllByTagAndMaybeValue.execute(tag) { value = null }.data
      withClue(queryResult) { queryResult.items.shouldBeEmpty() }
    }
  }

  @Test
  fun queryNonNullableByDefaultEnumValue() = runTest {
    val enumArb = Arb.enum<N5ekmae3jn>()
    val queryValueArb = Arb.constant(N5ekmae3jn.XGWGVMYTHJ)
    checkAll(NUM_ITERATIONS, Arb.insert3TestData(enumArb, queryValue = queryValueArb)) { testData ->
      val (tag, insertValue1, insertValue2, insertValue3) = testData
      val insertResult =
        connector.enumNonNullableInsert3.execute(tag, insertValue1, insertValue2, insertValue3).data
      val queryResult = connector.enumNonNullableGetAllByTagAndDefaultValue.execute(tag).data
      val matchingIds = insertResult.idsForMatchingValues(testData)
      withClue(queryResult) {
        queryResult.items.map { it.id } shouldContainExactlyInAnyOrder matchingIds
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for EnumNullable table.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun insertNullableNonNullEnumValue() = runTest {
    N5ekmae3jn.entries.forEach { enumValue ->
      val key = connector.enumNullableInsert.execute { value = enumValue }.data.key
      val queryResult = connector.enumNullableGetByKey.execute(key).data
      withClue(queryResult) { queryResult.item?.value shouldBe enumValue }
    }
  }

  @Test
  fun insertNullableNullEnumValue() = runTest {
    val key = connector.enumNullableInsert.execute { value = null }.data.key
    val queryResult = connector.enumNullableGetByKey.execute(key).data
    withClue(queryResult) { queryResult.item?.value.shouldBeNull() }
  }

  @Test
  fun updateNullableEnumValue() = runTest {
    val enumArb = Arb.enum<N5ekmae3jn>().orNull(nullProbability = 0.5)
    checkAll(NUM_ITERATIONS, Arb.twoValues(enumArb)) { values ->
      val (value1, value2) = values
      val key = connector.enumNullableInsert.execute { value = value1 }.data.key
      connector.enumNullableUpdateByKey.execute(key) { value = value2 }
      val queryResult = connector.enumNullableGetByKey.execute(key).data
      withClue(queryResult) { queryResult.item?.value shouldBe value2 }
    }
  }

  @Test
  fun queryNullableByEnumValue() = runTest {
    val enumArb = Arb.enum<N5ekmae3jn>().orNull(nullProbability = 0.33)
    checkAll(NUM_ITERATIONS, Arb.insert3TestData(enumArb)) { testData ->
      val (tag, insertValue1, insertValue2, insertValue3, queryValue) = testData
      val insertResult =
        connector.enumNullableInsert3
          .execute(tag) {
            value1 = insertValue1
            value2 = insertValue2
            value3 = insertValue3
          }
          .data
      val queryResult =
        connector.enumNullableGetAllByTagAndValue.execute(tag) { value = queryValue }.data
      val matchingIds = insertResult.idsForMatchingValues(testData)
      withClue(queryResult) {
        queryResult.items.map { it.id } shouldContainExactlyInAnyOrder matchingIds
      }
    }
  }

  @Test
  fun queryNullableByUndefinedEnumValue() = runTest {
    val enumArb = Arb.enum<N5ekmae3jn>().orNull(nullProbability = 0.5)
    checkAll(NUM_ITERATIONS, Arb.insert3TestData(enumArb)) { testData ->
      val (tag, insertValue1, insertValue2, insertValue3) = testData
      val insertResult =
        connector.enumNullableInsert3
          .execute(tag) {
            value1 = insertValue1
            value2 = insertValue2
            value3 = insertValue3
          }
          .data
      val queryResult = connector.enumNullableGetAllByTagAndValue.execute(tag).data
      withClue(queryResult) {
        queryResult.items.map { it.id } shouldContainExactlyInAnyOrder insertResult.ids
      }
    }
  }

  @Test
  fun queryNullableByDefaultEnumValue() = runTest {
    val enumArb = Arb.enum<N5ekmae3jn>().orNull(nullProbability = 0.33)
    val queryValueArb = Arb.constant(N5ekmae3jn.QJX7C7RD5T)
    checkAll(NUM_ITERATIONS, Arb.insert3TestData(enumArb, queryValue = queryValueArb)) { testData ->
      val (tag, insertValue1, insertValue2, insertValue3) = testData
      val insertResult =
        connector.enumNullableInsert3
          .execute(tag) {
            value1 = insertValue1
            value2 = insertValue2
            value3 = insertValue3
          }
          .data
      val queryResult = connector.enumNullableGetAllByTagAndDefaultValue.execute(tag).data
      val matchingIds = insertResult.idsForMatchingValues(testData)
      withClue(queryResult) {
        queryResult.items.map { it.id } shouldContainExactlyInAnyOrder matchingIds
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Helper classes and functions.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  private data class Insert3TestData<T>(
    val tag: String,
    val value1: T,
    val value2: T,
    val value3: T,
    val queryValue: T
  )

  private companion object {

    /** The default number of iterations to use in property-based tests. */
    const val NUM_ITERATIONS = 10

    @Suppress("NAME_SHADOWING")
    fun <T> Arb.Companion.insert3TestData(
      insertValues: Arb<T>,
      queryValue: Arb<T> = insertValues,
      tag: Arb<String> = Arb.dataConnect.tag(),
    ): Arb<Insert3TestData<T>> =
      bind(tag, Arb.threeValues(insertValues), queryValue) { tag, insertValues, queryValue ->
        Insert3TestData(
          tag,
          insertValues.value1,
          insertValues.value2,
          insertValues.value3,
          queryValue
        )
      }

    val EnumNonNullableInsert3Mutation.Data.ids: List<UUID>
      get() = listOf(key1, key2, key3).map { it.id }

    val EnumNullableInsert3Mutation.Data.ids: List<UUID>
      get() = listOf(key1, key2, key3).map { it.id }

    fun EnumNonNullableInsert3Mutation.Data.idsForMatchingValues(
      testData: Insert3TestData<N5ekmae3jn>
    ): List<UUID> = keysForMatchingValues(testData, key1, key2, key3).map { it.id }

    fun EnumNullableInsert3Mutation.Data.idsForMatchingValues(
      testData: Insert3TestData<N5ekmae3jn?>
    ): List<UUID> = keysForMatchingValues(testData, key1, key2, key3).map { it.id }

    private fun <T, K> keysForMatchingValues(
      testData: Insert3TestData<T>,
      key1: K,
      key2: K,
      key3: K,
    ): List<K> = buildList {
      if (testData.value1 == testData.queryValue) {
        add(key1)
      }
      if (testData.value2 == testData.queryValue) {
        add(key2)
      }
      if (testData.value3 == testData.queryValue) {
        add(key3)
      }
    }
  }
}
