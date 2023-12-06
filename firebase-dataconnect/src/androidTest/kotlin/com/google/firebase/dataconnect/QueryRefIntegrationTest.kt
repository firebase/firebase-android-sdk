// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.dataconnect

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.TestDataConnectFactory
import com.google.firebase.dataconnect.testutil.schemas.AllTypesSchema
import com.google.firebase.dataconnect.testutil.schemas.AllTypesSchema.GetAllPrimitiveListsQuery.execute
import com.google.firebase.dataconnect.testutil.schemas.AllTypesSchema.GetPrimitiveListQuery.execute
import com.google.firebase.dataconnect.testutil.schemas.AllTypesSchema.GetPrimitiveQuery.execute
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.CreatePersonMutation.execute
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.GetAllPeopleQuery.Data.Person
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.GetAllPeopleQuery.execute
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.GetPersonQuery.execute
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.UpdatePersonMutation.execute
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QueryRefIntegrationTest {

  @get:Rule val dataConnectLogLevelRule = DataConnectLogLevelRule()
  @get:Rule val dataConnectFactory = TestDataConnectFactory()

  private val personSchema
    get() = dataConnectFactory.personSchema
  private val allTypesSchema
    get() = dataConnectFactory.allTypesSchema

  @Test
  fun executeWithASingleResultReturnsTheCorrectResult() = runTest {
    personSchema.createPerson.execute(id = "TestId1", name = "TestName1", age = 42)
    personSchema.createPerson.execute(id = "TestId2", name = "TestName2", age = 43)
    personSchema.createPerson.execute(id = "TestId3", name = "TestName3", age = 44)

    val result = personSchema.getPerson.execute(id = "TestId2")

    assertThat(result.data.person?.name).isEqualTo("TestName2")
    assertThat(result.data.person?.age).isEqualTo(43)
  }

  @Test
  fun executeWithASingleResultReturnsTheUpdatedResult() = runTest {
    personSchema.createPerson.execute(id = "TestId", name = "TestName", age = 42)
    personSchema.updatePerson.execute(id = "TestId", name = "NewTestName", age = 99)

    val result = personSchema.getPerson.execute(id = "TestId")

    assertThat(result.data.person?.name).isEqualTo("NewTestName")
    assertThat(result.data.person?.age).isEqualTo(99)
  }

  @Test
  fun executeWithASingleResultReturnsNullIfNotFound() = runTest {
    personSchema.createPerson.execute(id = "TestId", name = "TestName", age = 42)

    val result = personSchema.getPerson.execute(id = "NotTheTestId")

    assertThat(result.data.person).isNull()
  }

  @Test
  fun executeWithAListResultReturnsAllResults() = runTest {
    personSchema.createPerson.execute(id = "TestId1", name = "TestName1", age = 42)
    personSchema.createPerson.execute(id = "TestId2", name = "TestName2", age = 43)
    personSchema.createPerson.execute(id = "TestId3", name = "TestName3", age = 44)

    val result = personSchema.getAllPeople.execute()

    assertThat(result.data.people)
      .containsExactly(
        Person(id = "TestId1", name = "TestName1", age = 42),
        Person(id = "TestId2", name = "TestName2", age = 43),
        Person(id = "TestId3", name = "TestName3", age = 44),
      )
  }

  @Test
  fun executeWithAllPrimitiveGraphQLTypesInDataNoneNull() = runTest {
    allTypesSchema.createPrimitive.execute(
      AllTypesSchema.CreatePrimitiveMutation.Variables(
        AllTypesSchema.PrimitiveData(
          id = "TestId",
          idFieldNullable = "TestNullableId",
          intField = 42,
          intFieldNullable = 43,
          floatField = 123.45,
          floatFieldNullable = 678.91,
          booleanField = true,
          booleanFieldNullable = false,
          stringField = "TestString",
          stringFieldNullable = "TestNullableString"
        )
      )
    )

    val result = allTypesSchema.getPrimitive.execute(id = "TestId")

    val primitive = result.data.primitive ?: error("result.data.primitive is null")
    assertThat(primitive.id).isEqualTo("TestId")
    assertThat(primitive.idFieldNullable).isEqualTo("TestNullableId")
    assertThat(primitive.intField).isEqualTo(42)
    assertThat(primitive.intFieldNullable).isEqualTo(43)
    assertThat(primitive.floatField).isEqualTo(123.45)
    assertThat(primitive.floatFieldNullable).isEqualTo(678.91)
    assertThat(primitive.booleanField).isEqualTo(true)
    assertThat(primitive.booleanFieldNullable).isEqualTo(false)
    assertThat(primitive.stringField).isEqualTo("TestString")
    assertThat(primitive.stringFieldNullable).isEqualTo("TestNullableString")
  }

  @Test
  fun executeWithAllPrimitiveGraphQLTypesInDataNullablesAreNull() = runTest {
    allTypesSchema.createPrimitive.execute(
      AllTypesSchema.CreatePrimitiveMutation.Variables(
        AllTypesSchema.PrimitiveData(
          id = "TestId",
          idFieldNullable = null,
          intField = 42,
          intFieldNullable = null,
          floatField = 123.45,
          floatFieldNullable = null,
          booleanField = true,
          booleanFieldNullable = null,
          stringField = "TestString",
          stringFieldNullable = null
        )
      )
    )

    val result = allTypesSchema.getPrimitive.execute(id = "TestId")

    val primitive = result.data.primitive ?: error("result.data.primitive is null")
    assertThat(primitive.idFieldNullable).isNull()
    assertThat(primitive.intFieldNullable).isNull()
    assertThat(primitive.floatFieldNullable).isNull()
    assertThat(primitive.booleanFieldNullable).isNull()
    assertThat(primitive.stringFieldNullable).isNull()
  }

  @Test
  fun executeWithAllListOfPrimitiveGraphQLTypesInData() = runTest {
    // NOTE: `null` list elements (a.k.a. "sparse arrays") are not supported: b/300331607
    allTypesSchema.createPrimitiveList.execute(
      AllTypesSchema.CreatePrimitiveListMutation.Variables(
        AllTypesSchema.PrimitiveListData(
          id = "TestId",
          idListNullable = listOf("aaa", "bbb"),
          idListOfNullable = listOf("ccc", "ddd"),
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
    )

    allTypesSchema.getAllPrimitiveLists.execute()

    val result = allTypesSchema.getPrimitiveList.execute(id = "TestId")

    val primitive = result.data.primitiveList ?: error("result.data.primitiveList is null")
    assertThat(primitive.id).isEqualTo("TestId")
    assertThat(primitive.idListNullable).containsExactly("aaa", "bbb").inOrder()
    assertThat(primitive.idListOfNullable).containsExactly("ccc", "ddd").inOrder()
    assertThat(primitive.intList).containsExactly(42, 43, 44).inOrder()
    assertThat(primitive.intListNullable).containsExactly(45, 46).inOrder()
    assertThat(primitive.intListOfNullable).containsExactly(47, 48).inOrder()
    assertThat(primitive.floatList).containsExactly(12.3, 45.6, 78.9).inOrder()
    assertThat(primitive.floatListNullable).containsExactly(98.7, 65.4).inOrder()
    assertThat(primitive.floatListOfNullable).containsExactly(100.1, 100.2).inOrder()
    assertThat(primitive.booleanList).containsExactly(true, false, true, false).inOrder()
    assertThat(primitive.booleanListNullable).containsExactly(false, true, false, true).inOrder()
    assertThat(primitive.booleanListOfNullable).containsExactly(false, false, true, true).inOrder()
    assertThat(primitive.stringList).containsExactly("xxx", "yyy", "zzz").inOrder()
    assertThat(primitive.stringListNullable).containsExactly("qqq", "rrr").inOrder()
    assertThat(primitive.stringListOfNullable).containsExactly("sss", "ttt").inOrder()
  }

  @Test
  fun executeWithNestedTypesInData() = runTest {
    allTypesSchema.createFarmer(id = "Farmer1Id", name = "Farmer1Name", parentId = null)
    allTypesSchema.createFarmer(id = "Farmer2Id", name = "Farmer2Name", parentId = "Farmer1Id")
    allTypesSchema.createFarmer(id = "Farmer3Id", name = "Farmer3Name", parentId = "Farmer2Id")
    allTypesSchema.createFarmer(id = "Farmer4Id", name = "Farmer4Name", parentId = "Farmer3Id")
    allTypesSchema.createFarm(id = "FarmId", name = "TestFarm", farmerId = "Farmer4Id")
    allTypesSchema.createAnimal(
      id = "Animal1Id",
      farmId = "FarmId",
      name = "Animal1Name",
      species = "Animal1Species",
      age = 1
    )
    allTypesSchema.createAnimal(
      id = "Animal2Id",
      farmId = "FarmId",
      name = "Animal2Name",
      species = "Animal2Species",
      age = 2
    )
    allTypesSchema.createAnimal(
      id = "Animal3Id",
      farmId = "FarmId",
      name = "Animal3Name",
      species = "Animal3Species",
      age = 3
    )
    allTypesSchema.createAnimal(
      id = "Animal4Id",
      farmId = "FarmId",
      name = "Animal4Name",
      species = "Animal4Species",
      age = null
    )

    val result = allTypesSchema.getFarm("FarmId")

    assertWithMessage("result.data.farm").that(result.data.farm).isNotNull()
    val farm = result.data.farm!!
    assertThat(farm.id).isEqualTo("FarmId")
    assertThat(farm.name).isEqualTo("TestFarm")
    assertWithMessage("farm.farmer")
      .that(farm.farmer)
      .isEqualTo(
        AllTypesSchema.GetFarmQuery.Farmer(
          id = "Farmer4Id",
          name = "Farmer4Name",
          parent =
            AllTypesSchema.GetFarmQuery.Parent(
              id = "Farmer3Id",
              name = "Farmer3Name",
              parentId = "Farmer2Id",
            )
        )
      )
    assertWithMessage("farm.animals")
      .that(farm.animals)
      .containsExactly(
        AllTypesSchema.GetFarmQuery.Animal(
          id = "Animal1Id",
          name = "Animal1Name",
          species = "Animal1Species",
          age = 1
        ),
        AllTypesSchema.GetFarmQuery.Animal(
          id = "Animal2Id",
          name = "Animal2Name",
          species = "Animal2Species",
          age = 2
        ),
        AllTypesSchema.GetFarmQuery.Animal(
          id = "Animal3Id",
          name = "Animal3Name",
          species = "Animal3Species",
          age = 3
        ),
        AllTypesSchema.GetFarmQuery.Animal(
          id = "Animal4Id",
          name = "Animal4Name",
          species = "Animal4Species",
          age = null
        ),
      )
  }

  @Test
  fun executeWithNestedNullTypesInData() = runTest {
    allTypesSchema.createFarmer(id = "Farmer1Id", name = "Farmer1Name", parentId = null)
    allTypesSchema.createFarm(id = "FarmId", name = "TestFarm", farmerId = "Farmer1Id")

    val result = allTypesSchema.getFarm("FarmId")

    assertWithMessage("result.data.farm").that(result.data.farm).isNotNull()
    result.data.farm!!.apply {
      assertThat(id).isEqualTo("FarmId")
      assertThat(name).isEqualTo("TestFarm")
      assertWithMessage("farm.farmer.parent").that(farmer.parent).isNull()
    }
  }

  @Test
  fun executeShouldThrowIfDataConnectInstanceIsClosed() = runTest {
    personSchema.dataConnect.close()

    val result = personSchema.getPerson.runCatching { execute(id = "foo") }

    assertWithMessage("result=${result.getOrNull()}").that(result.isFailure).isTrue()
    assertThat(result.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
  }

  @Test
  fun executeShouldSupportMassiveConcurrency() =
    runTest(timeout = 60.seconds) {
      val queryRef = personSchema.getPerson

      val deferreds = buildList {
        repeat(100_000) {
          // Use `Dispatchers.Default` as the dispatcher for the launched coroutines so that there
          // will be at least 2 threads used to run the coroutines (as documented by
          // `Dispatchers.Default`), introducing a guaranteed minimum level of parallelism, ensuring
          // that this test is indeed testing "massive concurrency".
          add(backgroundScope.async(Dispatchers.Default) { queryRef.execute(id = "foo") })
        }
      }

      val results = deferreds.map { it.await() }
      results.forEachIndexed { index, result ->
        assertWithMessage("results[$index]").that(result.data.person).isNull()
      }
    }
}
