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

@file:OptIn(ExperimentalFirebaseDataConnect::class)

package com.google.firebase.dataconnect.connectors.demo

import com.google.firebase.dataconnect.ExperimentalFirebaseDataConnect
import com.google.firebase.dataconnect.connectors.demo.EnumValue.Known
import com.google.firebase.dataconnect.connectors.demo.EnumValue.Unknown
import com.google.firebase.dataconnect.connectors.demo.testutil.DemoConnectorIntegrationTestBase
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.threeValues
import com.google.firebase.dataconnect.testutil.property.arbitrary.twoValues
import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.Exhaustive
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.orNull
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.enum
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Test

class EnumIntegrationTest : DemoConnectorIntegrationTestBase() {

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for EnumNonNullable table.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun insertNonNullableNonNullKnownEnumValue() = runTest {
    checkAll(Exhaustive.enum<N5ekmae3jn>()) { enumValue ->
      val key = connector.enumNonNullableInsert.execute(enumValue).data.key
      val queryResult = connector.enumNonNullableGetByKey.execute(key)
      queryResult.asClue {
        val item = it.data.item.shouldNotBeNull()
        item.value shouldBe Known(enumValue)
      }
    }
  }

  @Test
  fun updateNonNullableEnumValue() = runTest {
    checkAll(NUM_ITERATIONS, Arb.twoValues(Arb.enum<N5ekmae3jn>())) { (value1, value2) ->
      val key = connector.enumNonNullableInsert.execute(value1).data.key
      val updateResult = connector.enumNonNullableUpdateByKey.execute(key) { value = value2 }
      updateResult.asClue { it.data.key shouldBe key }
      val queryResult = connector.enumNonNullableGetByKey.execute(key)
      queryResult.asClue {
        val item = it.data.item.shouldNotBeNull()
        item.value shouldBe Known(value2)
      }
    }
  }

  @Test
  fun queryNonNullableNonNullUnknownEnumValue() = runTest {
    checkAll(Exhaustive.enum<N5ekmae3jn>()) { enumValue ->
      val key = connector.enumNonNullableInsert.execute(enumValue).data.key
      val queryRef =
        connector.enumNonNullableGetByKey
          .ref(key)
          .withDataDeserializer(EnumSubsetGetByKeyQuery.dataDeserializer)
      val queryResult = queryRef.execute()
      val expectedEnumValue: EnumValue<N5ekmae3jnSubset> = enumValue.toN5ekmae3jnSubsetEnumValue()
      queryResult.asClue {
        val item = it.data.item.shouldNotBeNull()
        item.value shouldBe expectedEnumValue
      }
    }
  }

  @Test
  fun queryNonNullableByNonNullEnumValue() = runTest {
    checkAll(NUM_ITERATIONS, Arb.insert3TestData(Arb.enum<N5ekmae3jn>())) { testData ->
      val (tag, insertValue1, insertValue2, insertValue3, queryValue) = testData
      val insertResult =
        connector.enumNonNullableInsert3.execute(tag, insertValue1, insertValue2, insertValue3).data
      val queryResult = connector.enumNonNullableGetAllByTagAndValue.execute(tag, queryValue)
      val matchingIds = insertResult.idsForMatchingValues(testData)
      queryResult.asClue {
        it.data.items.map { item -> item.id } shouldContainExactlyInAnyOrder matchingIds
      }
    }
  }

  @Test
  fun queryNonNullableByUndefinedEnumValue() = runTest {
    checkAll(NUM_ITERATIONS, Arb.insert3TestData(Arb.enum<N5ekmae3jn>())) {
      (tag, insertValue1, insertValue2, insertValue3) ->
      val insertResult =
        connector.enumNonNullableInsert3.execute(tag, insertValue1, insertValue2, insertValue3).data
      val queryResult = connector.enumNonNullableGetAllByTagAndMaybeValue.execute(tag)
      queryResult.asClue {
        it.data.items.map { item -> item.id } shouldContainExactlyInAnyOrder insertResult.ids
      }
    }
  }

  @Test
  fun queryNonNullableByNullEnumValue() = runTest {
    checkAll(NUM_ITERATIONS, Arb.insert3TestData(Arb.enum<N5ekmae3jn>())) {
      (tag, insertValue1, insertValue2, insertValue3) ->
      connector.enumNonNullableInsert3.execute(tag, insertValue1, insertValue2, insertValue3)
      val queryResult =
        connector.enumNonNullableGetAllByTagAndMaybeValue.execute(tag) { value = null }
      queryResult.asClue { it.data.items.shouldBeEmpty() }
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
      val queryResult = connector.enumNonNullableGetAllByTagAndDefaultValue.execute(tag)
      val matchingIds = insertResult.idsForMatchingValues(testData)
      queryResult.asClue {
        it.data.items.map { item -> item.id } shouldContainExactlyInAnyOrder matchingIds
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for EnumNullable table.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun insertNullableNonNullEnumValue() = runTest {
    checkAll(Exhaustive.enum<N5ekmae3jn>()) { enumValue ->
      val key = connector.enumNullableInsert.execute { value = enumValue }.data.key
      val queryResult = connector.enumNullableGetByKey.execute(key)
      queryResult.asClue {
        val item = it.data.item.shouldNotBeNull()
        item.value shouldBe Known(enumValue)
      }
    }
  }

  @Test
  fun insertNullableNullEnumValue() = runTest {
    val key = connector.enumNullableInsert.execute { value = null }.data.key
    val queryResult = connector.enumNullableGetByKey.execute(key)
    queryResult.asClue {
      val item = it.data.item.shouldNotBeNull()
      item.value.shouldBeNull()
    }
  }

  @Test
  fun updateNullableEnumValue() = runTest {
    val enumArb = Arb.enum<N5ekmae3jn>().orNull(nullProbability = 0.5)
    checkAll(NUM_ITERATIONS, Arb.twoValues(enumArb)) { (value1, value2) ->
      val key = connector.enumNullableInsert.execute { value = value1 }.data.key
      connector.enumNullableUpdateByKey.execute(key) { value = value2 }
      val queryResult = connector.enumNullableGetByKey.execute(key)
      queryResult.asClue {
        val item = it.data.item.shouldNotBeNull()
        if (value2 === null) {
          item.value.shouldBeNull()
        } else {
          item.value shouldBe Known(value2)
        }
      }
    }
  }

  @Test
  fun queryNullableNonNullUnknownEnumValue() = runTest {
    checkAll(Exhaustive.enum<N5ekmae3jn>()) { enumValue ->
      val key = connector.enumNullableInsert.execute { value = enumValue }.data.key
      val queryRef =
        connector.enumNullableGetByKey
          .ref(key)
          .withDataDeserializer(EnumSubsetGetByKeyQuery.dataDeserializer)
      val queryResult = queryRef.execute()
      val expectedEnumValue: EnumValue<N5ekmae3jnSubset> = enumValue.toN5ekmae3jnSubsetEnumValue()
      queryResult.asClue {
        val item = it.data.item.shouldNotBeNull()
        item.value shouldBe expectedEnumValue
      }
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
        connector.enumNullableGetAllByTagAndValue.execute(tag) { value = queryValue }
      val matchingIds = insertResult.idsForMatchingValues(testData)
      queryResult.asClue {
        it.data.items.map { item -> item.id } shouldContainExactlyInAnyOrder matchingIds
      }
    }
  }

  @Test
  fun queryNullableByUndefinedEnumValue() = runTest {
    val enumArb = Arb.enum<N5ekmae3jn>().orNull(nullProbability = 0.5)
    checkAll(NUM_ITERATIONS, Arb.insert3TestData(enumArb)) {
      (tag, insertValue1, insertValue2, insertValue3) ->
      val insertResult =
        connector.enumNullableInsert3
          .execute(tag) {
            value1 = insertValue1
            value2 = insertValue2
            value3 = insertValue3
          }
          .data
      val queryResult = connector.enumNullableGetAllByTagAndValue.execute(tag)
      queryResult.asClue {
        it.data.items.map { item -> item.id } shouldContainExactlyInAnyOrder insertResult.ids
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
      val queryResult = connector.enumNullableGetAllByTagAndDefaultValue.execute(tag)
      val matchingIds = insertResult.idsForMatchingValues(testData)
      queryResult.asClue {
        it.data.items.map { item -> item.id } shouldContainExactlyInAnyOrder matchingIds
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for EnumNonNullableTableDefault table.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun insertEnumNonNullableTableDefault() = runTest {
    val key = connector.enumNonNullableTableDefaultInsert.execute().data.key
    val queryResult = connector.enumNonNullableTableDefaultGetByKey.execute(key)
    queryResult.asClue {
      val item = it.data.item.shouldNotBeNull()
      item.value shouldBe Known(N5ekmae3jn.RGTB44C2M8)
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for EnumNullableTableDefault table.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun insertEnumNullableTableDefault() = runTest {
    val key = connector.enumNullableTableDefaultInsert.execute().data.key
    val queryResult = connector.enumNullableTableDefaultGetByKey.execute(key)
    queryResult.asClue {
      val item = it.data.item.shouldNotBeNull()
      item.value shouldBe Known(N5ekmae3jn.ZE6Z5778RV)
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for EnumNonNullableListOfNonNullable table.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun insertNonNullableListOfNonNullable() = runTest {
    checkAll(NUM_ITERATIONS, Arb.list(Arb.enum<N5ekmae3jn>(), 0..5)) { values ->
      val key = connector.enumNonNullableListOfNonNullableInsert.execute(values).data.key
      val queryResult = connector.enumNonNullableListOfNonNullableGetByKey.execute(key)
      queryResult.asClue {
        val item = it.data.item.shouldNotBeNull()
        item.value shouldBe values.map(::Known)
      }
    }
  }

  @Test
  fun queryNonNullableListOfNonNullableUnknownEnumValues() = runTest {
    checkAll(NUM_ITERATIONS, Arb.list(Arb.enum<N5ekmae3jn>(), 10..20)) { values ->
      val key = connector.enumNonNullableListOfNonNullableInsert.execute(values).data.key
      val queryRef =
        connector.enumNonNullableListOfNonNullableGetByKey
          .ref(key)
          .withDataDeserializer(EnumSubsetListGetByKeyQuery.dataDeserializer)
      val expectedEnumValues: List<EnumValue<N5ekmae3jnSubset>> =
        values.map { it.toN5ekmae3jnSubsetEnumValue() }
      val queryResult = queryRef.execute()
      queryResult.asClue {
        val item = it.data.item.shouldNotBeNull()
        item.value shouldBe expectedEnumValues
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for EnumNonNullableListOfNullable table.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun insertNonNullableListOfNullable() = runTest {
    checkAll(NUM_ITERATIONS, Arb.list(Arb.enum<N5ekmae3jn>(), 0..5)) { values ->
      val key = connector.enumNonNullableListOfNullableInsert.execute(values).data.key
      val queryResult = connector.enumNonNullableListOfNullableGetByKey.execute(key)
      queryResult.asClue {
        val item = it.data.item.shouldNotBeNull()
        item.value shouldBe values.map(::Known)
      }
    }
  }

  @Test
  fun queryNonNullableListOfNullableUnknownEnumValues() = runTest {
    checkAll(NUM_ITERATIONS, Arb.list(Arb.enum<N5ekmae3jn>(), 10..20)) { values ->
      val key = connector.enumNonNullableListOfNullableInsert.execute(values).data.key
      val queryRef =
        connector.enumNonNullableListOfNullableGetByKey
          .ref(key)
          .withDataDeserializer(EnumSubsetListGetByKeyQuery.dataDeserializer)
      val expectedEnumValues: List<EnumValue<N5ekmae3jnSubset>> =
        values.map { it.toN5ekmae3jnSubsetEnumValue() }
      val queryResult = queryRef.execute()
      queryResult.asClue {
        val item = it.data.item.shouldNotBeNull()
        item.value shouldBe expectedEnumValues
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for EnumNullableListOfNonNullable table.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun insertNullableListOfNonNullable_NonNullList() = runTest {
    checkAll(NUM_ITERATIONS, Arb.list(Arb.enum<N5ekmae3jn>(), 0..5)) { values ->
      val key = connector.enumNullableListOfNonNullableInsert.execute { value = values }.data.key
      val queryResult = connector.enumNullableListOfNonNullableGetByKey.execute(key)
      queryResult.asClue {
        val item = it.data.item.shouldNotBeNull()
        item.value shouldBe values.map(::Known)
      }
    }
  }

  @Test
  fun insertNullableListOfNonNullable_NullList() = runTest {
    val key = connector.enumNullableListOfNonNullableInsert.execute { value = null }.data.key
    val queryResult = connector.enumNullableListOfNonNullableGetByKey.execute(key)
    queryResult.asClue {
      val item = it.data.item.shouldNotBeNull()
      item.value.shouldBeNull()
    }
  }

  @Test
  fun queryNullableListOfNonNullableUnknownEnumValues() = runTest {
    checkAll(NUM_ITERATIONS, Arb.list(Arb.enum<N5ekmae3jn>(), 10..20)) { values ->
      val key = connector.enumNullableListOfNonNullableInsert.execute { value = values }.data.key
      val queryRef =
        connector.enumNullableListOfNonNullableGetByKey
          .ref(key)
          .withDataDeserializer(EnumSubsetListGetByKeyQuery.dataDeserializer)
      val expectedEnumValues: List<EnumValue<N5ekmae3jnSubset>> =
        values.map { it.toN5ekmae3jnSubsetEnumValue() }
      val queryResult = queryRef.execute()
      queryResult.asClue {
        val item = it.data.item.shouldNotBeNull()
        item.value shouldBe expectedEnumValues
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for EnumNullableListOfNullable table.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun insertNullableListOfNullable_NonNullList() = runTest {
    checkAll(NUM_ITERATIONS, Arb.list(Arb.enum<N5ekmae3jn>(), 0..5)) { values ->
      val key = connector.enumNullableListOfNullableInsert.execute { value = values }.data.key
      val queryResult = connector.enumNullableListOfNullableGetByKey.execute(key)
      queryResult.asClue {
        val item = it.data.item.shouldNotBeNull()
        item.value shouldBe values.map(::Known)
      }
    }
  }

  @Test
  fun insertNullableListOfNullable_NullList() = runTest {
    val key = connector.enumNullableListOfNullableInsert.execute { value = null }.data.key
    val queryResult = connector.enumNullableListOfNullableGetByKey.execute(key)
    queryResult.asClue {
      val item = it.data.item.shouldNotBeNull()
      item.value.shouldBeNull()
    }
  }

  @Test
  fun queryNullableListOfNullableUnknownEnumValues() = runTest {
    checkAll(NUM_ITERATIONS, Arb.list(Arb.enum<N5ekmae3jn>(), 10..20)) { values ->
      val key = connector.enumNullableListOfNullableInsert.execute { value = values }.data.key
      val queryRef =
        connector.enumNullableListOfNullableGetByKey
          .ref(key)
          .withDataDeserializer(EnumSubsetListGetByKeyQuery.dataDeserializer)
      val expectedEnumValues: List<EnumValue<N5ekmae3jnSubset>> =
        values.map { it.toN5ekmae3jnSubsetEnumValue() }
      val queryResult = queryRef.execute()
      queryResult.asClue {
        val item = it.data.item.shouldNotBeNull()
        item.value shouldBe expectedEnumValues
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for EnumKotlinKeywords table.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun enumKotlinKeywords() = runTest {
    Break.entries.forEach { enumValue ->
      val key = connector.enumKotlinKeywordsInsert.execute(enumValue).data.key
      val queryResult = connector.enumKotlinKeywordsGetByKey.execute(key)
      queryResult.asClue {
        val item = it.data.item.shouldNotBeNull()
        item.value shouldBe Known(enumValue)
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for EnumKey table.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun enumAsPrimaryKey() = runTest {
    checkAll(Exhaustive.enum<N5ekmae3jn>()) { enumValue ->
      val tagValue = Arb.dataConnect.tag().next(randomSource())
      val key = connector.enumKeyInsert.execute(enumValue) { tag = tagValue }.data.key
      key.asClue { it.enumValue shouldBe Known(enumValue) }
      val queryResult = connector.enumKeyGetByKey.execute(key)
      queryResult.asClue {
        val item = it.data.item.shouldNotBeNull()
        item.tag shouldBe tagValue
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for MultipleEnumColumns table.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun multipleEnumColumns() = runTest {
    checkAll(NUM_ITERATIONS, Arb.enum<N5ekmae3jn>(), Arb.enum<S7yayynb25>()) { enum1, enum2 ->
      val key = connector.multipleEnumColumnsInsert.execute(enum1, enum2).data.key
      val queryResult = connector.multipleEnumColumnsGetByKey.execute(key)
      queryResult.asClue {
        val item = it.data.item.shouldNotBeNull()
        assertSoftly {
          item.enum1 shouldBe Known(enum1)
          item.enum2 shouldBe Known(enum2)
        }
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

    @Suppress("SpellCheckingInspection")
    fun N5ekmae3jn.toN5ekmae3jnSubsetOrNull(): N5ekmae3jnSubset? =
      when (this) {
        N5ekmae3jn.DPSKD6HR3A -> N5ekmae3jnSubset.DPSKD6HR3A
        N5ekmae3jn.XGWGVMYTHJ -> N5ekmae3jnSubset.XGWGVMYTHJ
        N5ekmae3jn.QJX7C7RD5T -> N5ekmae3jnSubset.QJX7C7RD5T
        else -> null
      }

    @Suppress("SpellCheckingInspection")
    fun N5ekmae3jn.toN5ekmae3jnSubsetEnumValue(): EnumValue<N5ekmae3jnSubset> {
      return Known(toN5ekmae3jnSubsetOrNull() ?: return Unknown(name))
    }

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
