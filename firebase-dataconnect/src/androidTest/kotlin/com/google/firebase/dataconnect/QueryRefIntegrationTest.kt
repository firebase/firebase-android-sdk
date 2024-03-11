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
import com.google.firebase.dataconnect.testutil.randomId
import com.google.firebase.dataconnect.testutil.schemas.AllTypesSchema
import com.google.firebase.dataconnect.testutil.schemas.AllTypesSchema.Companion.randomAnimalId
import com.google.firebase.dataconnect.testutil.schemas.AllTypesSchema.Companion.randomFarmId
import com.google.firebase.dataconnect.testutil.schemas.AllTypesSchema.Companion.randomFarmerId
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.Companion.randomPersonId
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.Companion.randomPersonName
import java.util.UUID
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

  private val personSchema by lazy { PersonSchema(dataConnectFactory) }
  private val allTypesSchema by lazy { AllTypesSchema(dataConnectFactory) }

  @Test
  fun executeWithASingleResultReturnsTheCorrectResult() = runTest {
    val person1Id = randomPersonId()
    val person2Id = randomPersonId()
    val person3Id = randomPersonId()
    personSchema.createPerson(id = person1Id, name = "TestName1", age = 42).execute()
    personSchema.createPerson(id = person2Id, name = "TestName2", age = 43).execute()
    personSchema.createPerson(id = person3Id, name = "TestName3", age = 44).execute()

    val result = personSchema.getPerson(id = person2Id).execute()

    assertThat(result.data.person?.name).isEqualTo("TestName2")
    assertThat(result.data.person?.age).isEqualTo(43)
  }

  @Test
  fun executeWithASingleResultReturnsTheUpdatedResult() = runTest {
    val personId = randomPersonId()
    personSchema.createPerson(id = personId, name = "TestName", age = 42).execute()
    personSchema.updatePerson(id = personId, name = "NewTestName", age = 99).execute()

    val result = personSchema.getPerson(id = personId).execute()

    assertThat(result.data.person?.name).isEqualTo("NewTestName")
    assertThat(result.data.person?.age).isEqualTo(99)
  }

  @Test
  fun executeWithASingleResultReturnsNullIfNotFound() = runTest {
    val personId = randomPersonId()
    personSchema.deletePerson(personId)

    val result = personSchema.getPerson(id = personId).execute()

    assertThat(result.data.person).isNull()
  }

  @Test
  fun executeWithAListResultReturnsAllResults() = runTest {
    val personName = randomPersonName()
    val person1Id = randomPersonId()
    val person2Id = randomPersonId()
    val person3Id = randomPersonId()
    personSchema.createPerson(id = person1Id, name = personName, age = 42).execute()
    personSchema.createPerson(id = person2Id, name = personName, age = 43).execute()
    personSchema.createPerson(id = person3Id, name = personName, age = 44).execute()

    val result = personSchema.getPeopleByName(personName).execute()

    assertThat(result.data.people)
      .containsExactly(
        PersonSchema.GetPeopleByNameQuery.Data.Person(id = person1Id, age = 42),
        PersonSchema.GetPeopleByNameQuery.Data.Person(id = person2Id, age = 43),
        PersonSchema.GetPeopleByNameQuery.Data.Person(id = person3Id, age = 44),
      )
  }

  @Test
  fun executeWithAllPrimitiveGraphQLTypesInDataNoneNull() = runTest {
    val id = randomId()
    allTypesSchema
      .createPrimitive(
        AllTypesSchema.CreatePrimitiveMutation.Variables(
          AllTypesSchema.PrimitiveData(
            id = id,
            idFieldNullable = "e03b3062-bf60-4428-956a-17c0bc444691",
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
      .execute()

    val result = allTypesSchema.getPrimitive(id = id).execute()

    val primitive = result.data.primitive ?: error("result.data.primitive is null")
    assertThat(primitive.id).isEqualTo(id)
    assertThat(primitive.idFieldNullable).isEqualTo("e03b3062-bf60-4428-956a-17c0bc444691")
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
    val id = randomId()
    allTypesSchema
      .createPrimitive(
        AllTypesSchema.CreatePrimitiveMutation.Variables(
          AllTypesSchema.PrimitiveData(
            id = id,
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
      .execute()

    val result = allTypesSchema.getPrimitive(id = id).execute()

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
    val id = randomId()
    allTypesSchema
      .createPrimitiveList(
        AllTypesSchema.CreatePrimitiveListMutation.Variables(
          AllTypesSchema.PrimitiveListData(
            id = id,
            idListNullable =
              listOf(
                "1c2a5a6d-f81c-4252-ac86-383bb93d3dfb",
                "b53f44ae-5be9-4354-b58d-10db98690954"
              ),
            idListOfNullable =
              listOf(
                "e87004fc-b45d-4b83-8ccb-3ffca5c98e8d",
                "ad08635e-7b49-4511-9b6e-daa3b390235e"
              ),
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
      .execute()

    allTypesSchema.getAllPrimitiveLists.execute()

    val result = allTypesSchema.getPrimitiveList(id = id).execute()

    val primitive = result.data.primitiveList ?: error("result.data.primitiveList is null")
    assertThat(primitive.id).isEqualTo(id)
    assertThat(primitive.idListNullable)
      .containsExactly(
        "1c2a5a6d-f81c-4252-ac86-383bb93d3dfb",
        "b53f44ae-5be9-4354-b58d-10db98690954"
      )
      .inOrder()
    assertThat(primitive.idListOfNullable)
      .containsExactly(
        "e87004fc-b45d-4b83-8ccb-3ffca5c98e8d",
        "ad08635e-7b49-4511-9b6e-daa3b390235e"
      )
      .inOrder()
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
    val farmer1Id = randomFarmerId()
    val farmer2Id = randomFarmerId()
    val farmer3Id = randomFarmerId()
    val farmer4Id = randomFarmerId()
    val farmId = randomFarmId()
    val animal1Id = randomAnimalId()
    val animal2Id = randomAnimalId()
    val animal3Id = randomAnimalId()
    val animal4Id = randomAnimalId()
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
        age = 2
      )
      .execute()
    allTypesSchema
      .createAnimal(
        id = animal3Id,
        farmId = farmId,
        name = "Animal3Name",
        species = "Animal3Species",
        age = 3
      )
      .execute()
    allTypesSchema
      .createAnimal(
        id = animal4Id,
        farmId = farmId,
        name = "Animal4Name",
        species = "Animal4Species",
        age = null
      )
      .execute()

    val result = allTypesSchema.getFarm(farmId).execute()

    assertWithMessage("result.data.farm").that(result.data.farm).isNotNull()
    val farm = result.data.farm!!
    assertThat(farm.id).isEqualTo(farmId)
    assertThat(farm.name).isEqualTo("TestFarm")
    assertWithMessage("farm.farmer")
      .that(farm.farmer)
      .isEqualTo(
        AllTypesSchema.GetFarmQuery.Farmer(
          id = farmer4Id,
          name = "Farmer4Name",
          parent =
            AllTypesSchema.GetFarmQuery.Parent(
              id = farmer3Id,
              name = "Farmer3Name",
              parentId = farmer2Id,
            )
        )
      )
    assertWithMessage("farm.animals")
      .that(farm.animals)
      .containsExactly(
        AllTypesSchema.GetFarmQuery.Animal(
          id = animal1Id,
          name = "Animal1Name",
          species = "Animal1Species",
          age = 1
        ),
        AllTypesSchema.GetFarmQuery.Animal(
          id = animal2Id,
          name = "Animal2Name",
          species = "Animal2Species",
          age = 2
        ),
        AllTypesSchema.GetFarmQuery.Animal(
          id = animal3Id,
          name = "Animal3Name",
          species = "Animal3Species",
          age = 3
        ),
        AllTypesSchema.GetFarmQuery.Animal(
          id = animal4Id,
          name = "Animal4Name",
          species = "Animal4Species",
          age = null
        ),
      )
  }

  @Test
  fun executeWithNestedNullTypesInData() = runTest {
    val farmerId = randomFarmerId()
    val farmId = randomFarmId()
    allTypesSchema.createFarmer(id = farmerId, name = "FarmerName", parentId = null).execute()
    allTypesSchema.createFarm(id = farmId, name = "TestFarm", farmerId = farmerId).execute()

    val result = allTypesSchema.getFarm(farmId).execute()

    assertWithMessage("result.data.farm").that(result.data.farm).isNotNull()
    result.data.farm!!.apply {
      assertThat(id).isEqualTo(farmId)
      assertThat(name).isEqualTo("TestFarm")
      assertWithMessage("farm.farmer.parent").that(farmer.parent).isNull()
    }
  }

  @Test
  fun executeShouldThrowIfDataConnectInstanceIsClosed() = runTest {
    personSchema.dataConnect.close()

    val result = personSchema.getPerson(id = "foo").runCatching { execute() }

    assertWithMessage("result=${result.getOrNull()}").that(result.isFailure).isTrue()
    assertThat(result.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
  }

  @Test
  fun executeShouldSupportMassiveConcurrency() =
    runTest(timeout = 60.seconds) {
      val query = personSchema.getPerson(id = "foo")

      val deferreds = buildList {
        repeat(25_000) {
          // Use `Dispatchers.Default` as the dispatcher for the launched coroutines so that there
          // will be at least 2 threads used to run the coroutines (as documented by
          // `Dispatchers.Default`), introducing a guaranteed minimum level of parallelism, ensuring
          // that this test is indeed testing "massive concurrency".
          add(backgroundScope.async(Dispatchers.Default) { query.execute() })
        }
      }

      val results = deferreds.map { it.await() }
      results.forEachIndexed { index, result ->
        assertWithMessage("results[$index]").that(result.data.person).isNull()
      }
    }

  private companion object {
    fun randomId(): String = UUID.randomUUID().toString()
  }
}
