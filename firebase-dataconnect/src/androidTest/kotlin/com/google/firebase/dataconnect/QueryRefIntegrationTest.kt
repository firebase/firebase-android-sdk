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
import com.google.firebase.dataconnect.testutil.schemas.LazyAllTypesSchema
import com.google.firebase.dataconnect.testutil.schemas.LazyPersonSchema
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.GetAllPeopleQuery.Data.Person
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

  private val personSchema: PersonSchema by LazyPersonSchema(dataConnectFactory)
  private val allTypesSchema: AllTypesSchema by LazyAllTypesSchema(dataConnectFactory)

  @Test
  fun executeWithASingleResultReturnsTheCorrectResult() = runTest {
    personSchema.createPerson(id = "TestId1", name = "TestName1", age = 42).execute()
    personSchema.createPerson(id = "TestId2", name = "TestName2", age = 43).execute()
    personSchema.createPerson(id = "TestId3", name = "TestName3", age = 44).execute()

    val result = personSchema.getPerson(id = "TestId2").execute()

    assertThat(result.data.person?.name).isEqualTo("TestName2")
    assertThat(result.data.person?.age).isEqualTo(43)
  }

  @Test
  fun executeWithASingleResultReturnsTheUpdatedResult() = runTest {
    personSchema.createPerson(id = "TestId", name = "TestName", age = 42).execute()
    personSchema.updatePerson(id = "TestId", name = "NewTestName", age = 99).execute()

    val result = personSchema.getPerson(id = "TestId").execute()

    assertThat(result.data.person?.name).isEqualTo("NewTestName")
    assertThat(result.data.person?.age).isEqualTo(99)
  }

  @Test
  fun executeWithASingleResultReturnsNullIfNotFound() = runTest {
    personSchema.createPerson(id = "TestId", name = "TestName", age = 42).execute()

    val result = personSchema.getPerson(id = "NotTheTestId").execute()

    assertThat(result.data.person).isNull()
  }

  @Test
  fun executeWithAListResultReturnsAllResults() = runTest {
    personSchema.createPerson(id = "TestId1", name = "TestName1", age = 42).execute()
    personSchema.createPerson(id = "TestId2", name = "TestName2", age = 43).execute()
    personSchema.createPerson(id = "TestId3", name = "TestName3", age = 44).execute()

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
    allTypesSchema
      .createPrimitive(
        AllTypesSchema.CreatePrimitiveMutation.Variables(
          AllTypesSchema.PrimitiveData(
            id = "eb6e68bb-6157-4101-973f-cfc08e4b85ef",
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

    val result = allTypesSchema.getPrimitive(id = "eb6e68bb-6157-4101-973f-cfc08e4b85ef").execute()

    val primitive = result.data.primitive ?: error("result.data.primitive is null")
    assertThat(primitive.id).isEqualTo("eb6e68bb-6157-4101-973f-cfc08e4b85ef")
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
    allTypesSchema
      .createPrimitive(
        AllTypesSchema.CreatePrimitiveMutation.Variables(
          AllTypesSchema.PrimitiveData(
            id = "05f83a17-09e3-437f-8b9a-ba8bc774a56b",
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

    val result = allTypesSchema.getPrimitive(id = "05f83a17-09e3-437f-8b9a-ba8bc774a56b").execute()

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
    allTypesSchema
      .createPrimitiveList(
        AllTypesSchema.CreatePrimitiveListMutation.Variables(
          AllTypesSchema.PrimitiveListData(
            id = "3ecd2510-9ab6-4498-a703-78b414020cb7",
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

    val result =
      allTypesSchema.getPrimitiveList(id = "3ecd2510-9ab6-4498-a703-78b414020cb7").execute()

    val primitive = result.data.primitiveList ?: error("result.data.primitiveList is null")
    assertThat(primitive.id).isEqualTo("3ecd2510-9ab6-4498-a703-78b414020cb7")
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
    val farmer1Id = "0032eebf-dcbb-406c-9b54-ac5a743ea26a"
    val farmer2Id = "93e343dc-e0b2-4a38-a78a-d25e03bd9e1f"
    val farmer3Id = "34b5c829-cf18-4e6d-9e8f-dcf585dbc0e0"
    val farmer4Id = "355ef49d-99de-456b-a6dd-7c6a67089c3f"
    val farmId = "f68c3f00-23e5-42a9-87f7-a5b34cf33435"
    val animal1Id = "e70ad65e-4bc7-4e8b-8485-1178936c4795"
    val animal2Id = "bade6865-d435-4be8-b68c-b9bd69de0b92"
    val animal3Id = "d1c62270-3a4e-4bec-94c0-86914f319cc8"
    val animal4Id = "c9d4ca4e-6bba-40bc-8133-32989d595937"
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
    val farmerId = randomId()
    val farmId = randomId()
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
