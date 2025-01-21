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
import com.google.firebase.dataconnect.testutil.SuspendingCountDownLatch
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.schemas.AllTypesSchema
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Test

class QueryRefIntegrationTest : DataConnectIntegrationTestBase() {

  private val personSchema by lazy { PersonSchema(dataConnectFactory) }
  private val allTypesSchema by lazy { AllTypesSchema(dataConnectFactory) }

  @Test
  fun executeWithASingleResultReturnsTheCorrectResult() = runTest {
    val person1Id = Arb.alphanumericString(prefix = "person1Id").next()
    val person2Id = Arb.alphanumericString(prefix = "person2Id").next()
    val person3Id = Arb.alphanumericString(prefix = "person3Id").next()
    personSchema.createPerson(id = person1Id, name = "TestName1", age = 42).execute()
    personSchema.createPerson(id = person2Id, name = "TestName2", age = 43).execute()
    personSchema.createPerson(id = person3Id, name = "TestName3", age = 44).execute()

    val result = personSchema.getPerson(id = person2Id).execute()

    val person = withClue("result.data.person") { result.data.person.shouldNotBeNull() }
    assertSoftly {
      withClue("person.name") { person.name shouldBe "TestName2" }
      withClue("person.age") { person.age shouldBe 43 }
    }
  }

  @Test
  fun executeWithASingleResultReturnsTheUpdatedResult() = runTest {
    val personId = Arb.alphanumericString(prefix = "personId").next()
    personSchema.createPerson(id = personId, name = "TestName", age = 42).execute()
    personSchema.updatePerson(id = personId, name = "NewTestName", age = 99).execute()

    val result = personSchema.getPerson(id = personId).execute()

    val person = withClue("result.data.person") { result.data.person.shouldNotBeNull() }
    assertSoftly {
      withClue("person.name") { person.name shouldBe "NewTestName" }
      withClue("person.age") { person.age shouldBe 99 }
    }
  }

  @Test
  fun executeWithASingleResultReturnsNullIfNotFound() = runTest {
    val personId = Arb.alphanumericString(prefix = "personId").next()
    personSchema.deletePerson(personId)

    val result = personSchema.getPerson(id = personId).execute()

    result.data.person.shouldBeNull()
  }

  @Test
  fun executeWithAListResultReturnsAllResults() = runTest {
    val personName = Arb.alphanumericString(prefix = "personName").next()
    val person1Id = Arb.alphanumericString(prefix = "person1Id").next()
    val person2Id = Arb.alphanumericString(prefix = "person2Id").next()
    val person3Id = Arb.alphanumericString(prefix = "person3Id").next()
    personSchema.createPerson(id = person1Id, name = personName, age = 42).execute()
    personSchema.createPerson(id = person2Id, name = personName, age = 43).execute()
    personSchema.createPerson(id = person3Id, name = personName, age = 44).execute()

    val result = personSchema.getPeopleByName(personName).execute()

    result.data.people.shouldContainExactlyInAnyOrder(
      PersonSchema.GetPeopleByNameQuery.Data.Person(id = person1Id, age = 42),
      PersonSchema.GetPeopleByNameQuery.Data.Person(id = person2Id, age = 43),
      PersonSchema.GetPeopleByNameQuery.Data.Person(id = person3Id, age = 44),
    )
  }

  @Test
  fun executeWithAllPrimitiveGraphQLTypesInDataNoneNull() = runTest {
    val id = Arb.dataConnect.uuid().next()
    allTypesSchema
      .createPrimitive(
        AllTypesSchema.PrimitiveData(
          id = id,
          idFieldNullable = "e03b3062bf604428956a17c0bc444691",
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
      .execute()

    val result = allTypesSchema.getPrimitive(id = id).execute()

    val primitive = withClue("result.data.primitive") { result.data.primitive.shouldNotBeNull() }
    assertSoftly {
      withClue("id") { primitive.id shouldBe id }
      withClue("idFieldNullable") {
        primitive.idFieldNullable shouldBe "e03b3062bf604428956a17c0bc444691"
      }
      withClue("intField") { primitive.intField shouldBe 42 }
      withClue("intFieldNullable") { primitive.intFieldNullable shouldBe 43 }
      withClue("floatField") { primitive.floatField shouldBe 123.45 }
      withClue("floatFieldNullable") { primitive.floatFieldNullable shouldBe 678.91 }
      withClue("booleanField") { primitive.booleanField shouldBe true }
      withClue("booleanFieldNullable") { primitive.booleanFieldNullable shouldBe false }
      withClue("stringField") { primitive.stringField shouldBe "TestString" }
      withClue("stringFieldNullable") {
        primitive.stringFieldNullable shouldBe "TestNullableString"
      }
    }
  }

  @Test
  fun executeWithAllPrimitiveGraphQLTypesInDataNullablesAreNull() = runTest {
    val id = Arb.dataConnect.uuid().next()
    allTypesSchema
      .createPrimitive(
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
      .execute()

    val result = allTypesSchema.getPrimitive(id = id).execute()

    val primitive = withClue("result.data.primitive") { result.data.primitive.shouldNotBeNull() }
    assertSoftly {
      withClue("idFieldNullable") { primitive.idFieldNullable.shouldBeNull() }
      withClue("intFieldNullable") { primitive.intFieldNullable.shouldBeNull() }
      withClue("floatFieldNullable") { primitive.floatFieldNullable.shouldBeNull() }
      withClue("booleanFieldNullable") { primitive.booleanFieldNullable.shouldBeNull() }
      withClue("stringFieldNullable") { primitive.stringFieldNullable.shouldBeNull() }
    }
  }

  @Test
  fun executeWithAllListOfPrimitiveGraphQLTypesInData() = runTest {
    // NOTE: `null` list elements (a.k.a. "sparse arrays") are not supported: b/300331607
    val id = Arb.dataConnect.uuid().next()
    allTypesSchema
      .createPrimitiveList(
        AllTypesSchema.PrimitiveListData(
          id = id,
          idListNullable =
            listOf("1c2a5a6df81c4252ac86383bb93d3dfb", "b53f44ae5be94354b58d10db98690954"),
          idListOfNullable =
            listOf("e87004fcb45d4b838ccb3ffca5c98e8d", "ad08635e7b4945119b6edaa3b390235e"),
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

    allTypesSchema.getAllPrimitiveLists.execute()

    val result = allTypesSchema.getPrimitiveList(id = id).execute()

    val primitive =
      withClue("result.data.primitiveList") { result.data.primitiveList.shouldNotBeNull() }
    assertSoftly {
      withClue("id") { primitive.id shouldBe id }
      withClue("idListNullable") {
        primitive.idListNullable.shouldContainExactly(
          "1c2a5a6df81c4252ac86383bb93d3dfb",
          "b53f44ae5be94354b58d10db98690954"
        )
      }
      withClue("idListOfNullable") {
        primitive.idListOfNullable.shouldContainExactly(
          "e87004fcb45d4b838ccb3ffca5c98e8d",
          "ad08635e7b4945119b6edaa3b390235e"
        )
      }
      withClue("intList") { primitive.intList.shouldContainExactly(42, 43, 44) }
      withClue("intListNullable") { primitive.intListNullable.shouldContainExactly(45, 46) }
      withClue("intListOfNullable") { primitive.intListOfNullable.shouldContainExactly(47, 48) }
      withClue("floatList") { primitive.floatList.shouldContainExactly(12.3, 45.6, 78.9) }
      withClue("floatListNullable") { primitive.floatListNullable.shouldContainExactly(98.7, 65.4) }
      withClue("floatListOfNullable") {
        primitive.floatListOfNullable.shouldContainExactly(100.1, 100.2)
      }
      withClue("booleanList") {
        primitive.booleanList.shouldContainExactly(true, false, true, false)
      }
      withClue("booleanListNullable") {
        primitive.booleanListNullable.shouldContainExactly(false, true, false, true)
      }
      withClue("booleanListOfNullable") {
        primitive.booleanListOfNullable.shouldContainExactly(false, false, true, true)
      }
      withClue("stringList") { primitive.stringList.shouldContainExactly("xxx", "yyy", "zzz") }
      withClue("stringListNullable") {
        primitive.stringListNullable.shouldContainExactly("qqq", "rrr")
      }
      withClue("stringListOfNullable") {
        primitive.stringListOfNullable.shouldContainExactly("sss", "ttt")
      }
    }
  }

  @Test
  fun executeWithNestedTypesInData() = runTest {
    val farmer1Id = Arb.alphanumericString(prefix = "farmer1Id").next()
    val farmer2Id = Arb.alphanumericString(prefix = "farmer2Id").next()
    val farmer3Id = Arb.alphanumericString(prefix = "farmer3Id").next()
    val farmer4Id = Arb.alphanumericString(prefix = "farmer4Id").next()
    val farmId = Arb.alphanumericString(prefix = "farmId").next()
    val animal1Id = Arb.alphanumericString(prefix = "animal1Id").next()
    val animal2Id = Arb.alphanumericString(prefix = "animal2Id").next()
    val animal3Id = Arb.alphanumericString(prefix = "animal3Id").next()
    val animal4Id = Arb.alphanumericString(prefix = "animal4Id").next()
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

    val farm = withClue("result.data.farm") { result.data.farm.shouldNotBeNull() }
    withClue("farm.id") { farm.id shouldBe farmId }
    withClue("farm.name") { farm.name shouldBe "TestFarm" }
    withClue("farm.farmer") {
      farm.farmer shouldBe
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
    }
    withClue("farm.animals") {
      farm.animals.shouldContainExactlyInAnyOrder(
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
  }

  @Test
  fun executeWithNestedNullTypesInData() = runTest {
    val farmerId = Arb.alphanumericString(prefix = "farmerId").next()
    val farmId = Arb.alphanumericString(prefix = "farmId").next()
    allTypesSchema.createFarmer(id = farmerId, name = "FarmerName", parentId = null).execute()
    allTypesSchema.createFarm(id = farmId, name = "TestFarm", farmerId = farmerId).execute()

    val result = allTypesSchema.getFarm(farmId).execute()

    val farm = withClue("result.data.farm") { result.data.farm.shouldNotBeNull() }
    assertSoftly {
      withClue("farm.id") { farm.id shouldBe farmId }
      withClue("farm.name") { farm.name shouldBe "TestFarm" }
      withClue("farm.farmer.parent") { farm.farmer.parent.shouldBeNull() }
    }
  }

  @Test
  fun executeShouldThrowIfDataConnectInstanceIsClosed() = runTest {
    personSchema.dataConnect.close()

    val result = personSchema.getPerson(id = "foo").runCatching { execute() }

    withClue("result=${result.getOrNull()}") { result.isFailure shouldBe true }
    withClue("exception") { result.exceptionOrNull().shouldBeInstanceOf<IllegalStateException>() }
  }

  @Test
  fun executeShouldSupportMassiveConcurrency() =
    runTest(timeout = 5.minutes) {
      val latch = SuspendingCountDownLatch(25_000)
      val query = personSchema.getPerson(id = "foo")

      val deferreds =
        List(latch.count) {
          // Use `Dispatchers.Default` as the dispatcher for the launched coroutines so that there
          // will be at least 2 threads used to run the coroutines (as documented by
          // `Dispatchers.Default`), introducing a guaranteed minimum level of parallelism, ensuring
          // that this test is indeed testing "massive concurrency".
          backgroundScope.async(Dispatchers.Default) {
            latch.countDown().await()
            query.execute()
          }
        }

      val results = deferreds.map { it.await() }
      assertSoftly {
        results.forEachIndexed { index, result ->
          withClue("results[$index]") { result.data.person.shouldBeNull() }
        }
      }
    }
}
