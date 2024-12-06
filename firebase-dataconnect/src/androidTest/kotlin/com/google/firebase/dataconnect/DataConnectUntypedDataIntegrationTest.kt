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

@file:OptIn(ExperimentalFirebaseDataConnect::class)

package com.google.firebase.dataconnect

import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import com.google.firebase.dataconnect.testutil.randomId
import com.google.firebase.dataconnect.testutil.schemas.AllTypesSchema
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.junit.Test

class DataConnectUntypedDataIntegrationTest : DataConnectIntegrationTestBase() {

  private val allTypesSchema by lazy { AllTypesSchema(dataConnectFactory) }

  @Test
  fun primitiveTypes() = runTest {
    val id = randomId()
    allTypesSchema
      .createPrimitive(
        AllTypesSchema.PrimitiveData(
          id = id,
          idFieldNullable = "eebf7592cf744871873000a03a9af43e",
          intField = 42,
          intFieldNullable = 43,
          floatField = 99.0,
          floatFieldNullable = 100.0,
          booleanField = false,
          booleanFieldNullable = true,
          stringField = "TestStringValue",
          stringFieldNullable = "TestStringNullableValue",
        )
      )
      .execute()
    val query = allTypesSchema.getPrimitive(id = id).withDataDeserializer(DataConnectUntypedData)

    val result = query.execute()

    withClue("errors") { result.data.errors.shouldBeEmpty() }
    withClue("data") { result.data.data.shouldNotBeNull() }
    withClue("data.keys") { result.data.data!!.keys.shouldContainExactly("primitive") }
    withClue("data.keys[primitive]") {
      @Suppress("UNCHECKED_CAST")
      val primitive = (result.data.data!!.get("primitive") as Map<Any, Any?>)
      primitive shouldContainExactly
        mapOf(
          "id" to id,
          "idFieldNullable" to "eebf7592cf744871873000a03a9af43e",
          "intField" to 42.0,
          "intFieldNullable" to 43.0,
          "floatField" to 99.0,
          "floatFieldNullable" to 100.0,
          "booleanField" to false,
          "booleanFieldNullable" to true,
          "stringField" to "TestStringValue",
          "stringFieldNullable" to "TestStringNullableValue",
        )
    }
  }

  @Test
  fun nullPrimitiveTypes() = runTest {
    val id = randomId()
    allTypesSchema
      .createPrimitive(
        AllTypesSchema.PrimitiveData(
          id = id,
          idFieldNullable = null,
          intField = 42,
          intFieldNullable = null,
          floatField = 99.0,
          floatFieldNullable = null,
          booleanField = false,
          booleanFieldNullable = null,
          stringField = "TestStringValue",
          stringFieldNullable = null,
        )
      )
      .execute()
    val query = allTypesSchema.getPrimitive(id = id).withDataDeserializer(DataConnectUntypedData)

    val result = query.execute()

    withClue("errors") { result.data.errors.shouldBeEmpty() }
    withClue("data") { result.data.data.shouldNotBeNull() }
    withClue("data.keys") { result.data.data!!.keys.shouldContainExactly("primitive") }
    withClue("data.keys[primitive]") {
      @Suppress("UNCHECKED_CAST")
      val primitive = (result.data.data!!.get("primitive") as Map<Any, Any?>)
      primitive shouldContainExactly
        mapOf(
          "id" to id,
          "idFieldNullable" to null,
          "intField" to 42.0,
          "intFieldNullable" to null,
          "floatField" to 99.0,
          "floatFieldNullable" to null,
          "booleanField" to false,
          "booleanFieldNullable" to null,
          "stringField" to "TestStringValue",
          "stringFieldNullable" to null,
        )
    }
  }

  @Test
  fun listsOfPrimitiveTypes() = runTest {
    val id = randomId()
    allTypesSchema
      .createPrimitiveList(
        AllTypesSchema.PrimitiveListData(
          id = id,
          idListNullable =
            listOf("257e52b0c3bf4414a7fa8824b605f134", "33561fab8645464cb81889ce9a72f8bf"),
          idListOfNullable =
            listOf("517cb2d648f34be3bb0d8ab81e57dabc", "1ebedd8f870746f2bd1bb72c2b71b354"),
          intList = listOf(42, 43, 44),
          intListNullable = listOf(45, 46),
          intListOfNullable = listOf(47, 48),
          floatList = listOf(12.3, 45.6, 78.9),
          floatListNullable = listOf(98.7, 65.4),
          floatListOfNullable = listOf(100.1, 100.2),
          booleanList = listOf(true, false, true, false),
          booleanListNullable = listOf(false, true, false, true),
          booleanListOfNullable = listOf(false, false, true, true),
          stringList = listOf("xxx", "yyy", "zzz"),
          stringListNullable = listOf("qqq", "rrr"),
          stringListOfNullable = listOf("sss", "ttt"),
        )
      )
      .execute()
    val query =
      allTypesSchema.getPrimitiveList(id = id).withDataDeserializer(DataConnectUntypedData)

    val result = query.execute()

    withClue("errors") { result.data.errors.shouldBeEmpty() }
    withClue("data") { result.data.data.shouldNotBeNull() }
    withClue("data.keys") { result.data.data!!.keys.shouldContainExactly("primitiveList") }
    withClue("data.keys[primitive]") {
      @Suppress("UNCHECKED_CAST")
      val primitive = (result.data.data!!.get("primitiveList") as Map<Any, Any?>)
      primitive shouldContainExactly
        mapOf(
          "id" to id,
          "idListNullable" to
            listOf("257e52b0c3bf4414a7fa8824b605f134", "33561fab8645464cb81889ce9a72f8bf"),
          "idListOfNullable" to
            listOf("517cb2d648f34be3bb0d8ab81e57dabc", "1ebedd8f870746f2bd1bb72c2b71b354"),
          "intList" to listOf(42.0, 43.0, 44.0),
          "intListNullable" to listOf(45.0, 46.0),
          "intListOfNullable" to listOf(47.0, 48.0),
          "floatList" to listOf(12.3, 45.6, 78.9),
          "floatListNullable" to listOf(98.7, 65.4),
          "floatListOfNullable" to listOf(100.1, 100.2),
          "booleanList" to listOf(true, false, true, false),
          "booleanListNullable" to listOf(false, true, false, true),
          "booleanListOfNullable" to listOf(false, false, true, true),
          "stringList" to listOf("xxx", "yyy", "zzz"),
          "stringListNullable" to listOf("qqq", "rrr"),
          "stringListOfNullable" to listOf("sss", "ttt"),
        )
    }
  }

  @Test
  fun nullListsOfPrimitiveTypes() = runTest {
    val id = randomId()
    allTypesSchema
      .createPrimitiveList(
        AllTypesSchema.PrimitiveListData(
          id = id,
          idListNullable = null,
          idListOfNullable =
            listOf("1a392d5a4b424425b9ad677ac8066697", "9faab31ea1084b53be6945fc47c4f0fc"),
          intList = listOf(42, 43, 44),
          intListNullable = null,
          intListOfNullable = listOf(47, 48),
          floatList = listOf(12.3, 45.6, 78.9),
          floatListNullable = null,
          floatListOfNullable = listOf(100.1, 100.2),
          booleanList = listOf(true, false, true, false),
          booleanListNullable = null,
          booleanListOfNullable = listOf(false, false, true, true),
          stringList = listOf("xxx", "yyy", "zzz"),
          stringListNullable = null,
          stringListOfNullable = listOf("sss", "ttt"),
        )
      )
      .execute()
    val query =
      allTypesSchema.getPrimitiveList(id = id).withDataDeserializer(DataConnectUntypedData)

    val result = query.execute()

    withClue("errors") { result.data.errors.shouldBeEmpty() }
    withClue("data") { result.data.data.shouldNotBeNull() }
    withClue("data.keys") { result.data.data!!.keys.shouldContainExactly("primitiveList") }
    withClue("data.keys[primitiveList]") {
      @Suppress("UNCHECKED_CAST")
      val primitive = (result.data.data!!.get("primitiveList") as Map<Any, Any?>)
      primitive shouldContainExactly
        mapOf(
          "id" to id,
          "idListNullable" to null,
          "idListOfNullable" to
            listOf("1a392d5a4b424425b9ad677ac8066697", "9faab31ea1084b53be6945fc47c4f0fc"),
          "intList" to listOf(42.0, 43.0, 44.0),
          "intListNullable" to null,
          "intListOfNullable" to listOf(47.0, 48.0),
          "floatList" to listOf(12.3, 45.6, 78.9),
          "floatListNullable" to null,
          "floatListOfNullable" to listOf(100.1, 100.2),
          "booleanList" to listOf(true, false, true, false),
          "booleanListNullable" to null,
          "booleanListOfNullable" to listOf(false, false, true, true),
          "stringList" to listOf("xxx", "yyy", "zzz"),
          "stringListNullable" to null,
          "stringListOfNullable" to listOf("sss", "ttt"),
        )
    }
  }

  @Test
  fun nestedStructs() = runTest {
    val farmer1Id = randomId()
    val farmer2Id = randomId()
    val farmer3Id = randomId()
    val farmer4Id = randomId()
    val farmId = randomId()
    val animal1Id = randomId()
    val animal2Id = randomId()
    allTypesSchema.createFarmer(id = farmer1Id, name = "Farmer1Name", parentId = null).execute()
    allTypesSchema
      .createFarmer(id = farmer2Id, name = "Farmer2Name", parentId = farmer1Id)
      .execute()
    allTypesSchema
      .createFarmer(id = farmer3Id, name = "Farmer3Name", parentId = farmer2Id)
      .execute()
    allTypesSchema
      .createFarmer(id = farmer4Id, name = "Farmer4Name", parentId = farmer3Id)
      .execute()
    allTypesSchema.createFarm(id = farmId, name = "TestFarm", farmerId = farmer4Id).execute()
    allTypesSchema
      .createAnimal(
        id = animal1Id,
        farmId = farmId,
        name = "Animal1Name",
        species = "Animal1Species",
        age = 1
      )
      .execute()
    allTypesSchema
      .createAnimal(
        id = animal2Id,
        farmId = farmId,
        name = "Animal2Name",
        species = "Animal2Species",
        age = null
      )
      .execute()
    val query = allTypesSchema.getFarm(id = farmId).withDataDeserializer(DataConnectUntypedData)

    val result = query.execute()

    withClue("errors") { result.data.errors.shouldBeEmpty() }
    withClue("data") { result.data.data.shouldNotBeNull() }
    withClue("data.keys") { result.data.data!!.keys.shouldContainExactly("farm") }
    val farm = withClue("data.keys[farm]") { result.data.data!!.get("farm") as Map<*, *> }

    withClue("farm.keys") {
      farm.keys.shouldContainExactlyInAnyOrder("id", "name", "farmer", "animals")
    }
    withClue("farm[id]") { farm["id"] shouldBe farmId }
    withClue("farm[name]") { farm["name"] shouldBe "TestFarm" }
    val animals = withClue("farm[animals]") { farm["animals"] as List<*> }
    withClue("animals") {
      animals.shouldContainExactlyInAnyOrder(
        mapOf(
          "id" to animal1Id,
          "name" to "Animal1Name",
          "species" to "Animal1Species",
          "age" to 1.0
        ),
        mapOf(
          "id" to animal2Id,
          "name" to "Animal2Name",
          "species" to "Animal2Species",
          "age" to null
        ),
      )
    }

    val farmer = withClue("farm[farmer]") { farm["farmer"] as Map<*, *> }
    withClue("farmer.keys") { farmer.keys.shouldContainExactlyInAnyOrder("id", "name", "parent") }
    withClue("farmer[id]") { farmer["id"] shouldBe farmer4Id }
    withClue("farmer[name]") { farmer["name"] shouldBe "Farmer4Name" }
    val parent = withClue("farmer[parent]") { farmer["parent"] as Map<*, *> }
    withClue("parent.keys") { parent.keys.shouldContainExactlyInAnyOrder("id", "name", "parentId") }
    withClue("parent[id]") { parent["id"] shouldBe farmer3Id }
    withClue("parent[name]") { parent["name"] shouldBe "Farmer3Name" }
    withClue("parent[parentId]") { parent["parentId"] shouldBe farmer2Id }
  }

  @Test
  fun nestedNullStructs() = runTest {
    val farmerId = randomId()
    val farmId = randomId()
    allTypesSchema.createFarmer(id = farmerId, name = "FarmerName", parentId = null).execute()
    allTypesSchema.createFarm(id = farmId, name = "TestFarm", farmerId = farmerId).execute()
    val query = allTypesSchema.getFarm(id = farmId).withDataDeserializer(DataConnectUntypedData)

    val result = query.execute()

    withClue("errors") { result.data.errors.shouldBeEmpty() }
    withClue("data") { result.data.data.shouldNotBeNull() }
    withClue("data.keys") { result.data.data!!.keys.shouldContainExactly("farm") }
    val farm = withClue("data[farm]") { result.data.data!!["farm"] as Map<*, *> }
    withClue("farm.keys") {
      farm.keys.shouldContainExactlyInAnyOrder("id", "name", "farmer", "animals")
    }

    val farmer = withClue("farm[farmer]") { farm["farmer"] as Map<*, *> }
    withClue("farmer.keys") { farmer.keys.shouldContainExactlyInAnyOrder("id", "name", "parent") }
    withClue("farmer[id]") { farmer["id"] shouldBe farmerId }
    withClue("farmer[name]") { farmer["name"] shouldBe "FarmerName" }
    withClue("farmer[parent]") { farmer["parent"].shouldBeNull() }
  }

  @Test
  fun queryErrorsReturnedByServerArePutInTheErrorsListInsteadOfThrowingAnException() = runTest {
    @Serializable data class BogusVariables(val foo: String)

    val query =
      allTypesSchema
        .getPrimitive("foo")
        .withVariablesSerializer(BogusVariables(foo = "bar"), serializer())
        .withDataDeserializer(DataConnectUntypedData)

    val result = query.execute()

    assertSoftly {
      result.data.data.shouldBeNull()
      result.data.errors.shouldNotBeEmpty()
    }
  }

  @Test
  fun mutationErrorsReturnedByServerArePutInTheErrorsListInsteadOfThrowingAnException() = runTest {
    @Serializable data class BogusVariables(val foo: String)

    val mutation =
      allTypesSchema
        .createAnimal("", "", "", "", 42)
        .withVariablesSerializer(BogusVariables(foo = "bar"), serializer())
        .withDataDeserializer(DataConnectUntypedData)

    val result = mutation.execute()

    assertSoftly {
      result.data.data.shouldBeNull()
      result.data.errors.shouldNotBeEmpty()
    }
  }
}
