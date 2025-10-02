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

package com.google.firebase.dataconnect.testutil.schemas

import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.GetPeopleWithHardcodedNameQuery.hardcodedPeople
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PersonSchemaIntegrationTest : DataConnectIntegrationTestBase() {

  private val schema by lazy { PersonSchema(dataConnectFactory) }

  @Test
  fun createPersonShouldCreateTheSpecifiedPerson() = runTest {
    val personId = Arb.alphanumericString(prefix = "personId").next()
    schema.createPerson(id = personId, name = "TestName", age = 42).execute()

    val result = schema.getPerson(id = personId).execute()

    val person = withClue("result.data.person") { result.data.person.shouldNotBeNull() }
    assertSoftly {
      person.name shouldBe "TestName"
      person.age shouldBe 42
    }
  }

  @Test
  fun deletePersonShouldDeleteTheSpecifiedPerson() = runTest {
    val personId = Arb.alphanumericString(prefix = "personId").next()
    schema.createPerson(id = personId, name = "TestName", age = 42).execute()
    schema.getPerson(id = personId).execute().data.person.shouldNotBeNull()

    schema.deletePerson(id = personId).execute()

    schema.getPerson(id = personId).execute().data.person.shouldBeNull()
  }

  @Test
  fun updatePersonShouldUpdateTheSpecifiedPerson() = runTest {
    val personId = Arb.alphanumericString(prefix = "personId").next()
    schema.createPerson(id = personId, name = "TestName0", age = 42).execute()

    schema.updatePerson(id = personId, name = "TestName99", age = 999).execute()

    val result = schema.getPerson(id = personId).execute()
    val person = withClue("result.data.person") { result.data.person.shouldNotBeNull() }
    assertSoftly {
      person.name shouldBe "TestName99"
      person.age shouldBe 999
    }
  }

  @Test
  fun getPersonShouldReturnThePersonWithTheSpecifiedId() = runTest {
    val person1Id = Arb.alphanumericString(prefix = "person1Id").next()
    val person2Id = Arb.alphanumericString(prefix = "person2Id").next()
    val person3Id = Arb.alphanumericString(prefix = "person3Id").next()
    schema.createPerson(id = person1Id, name = "Name111", age = 111).execute()
    schema.createPerson(id = person2Id, name = "Name222", age = 222).execute()
    schema.createPerson(id = person3Id, name = "Name333", age = null).execute()

    val result1 = schema.getPerson(id = person1Id).execute()
    val result2 = schema.getPerson(id = person2Id).execute()
    val result3 = schema.getPerson(id = person3Id).execute()

    val person1 = withClue("result1.data.person") { result1.data.person.shouldNotBeNull() }
    assertSoftly {
      withClue("person1.name") { person1.name shouldBe "Name111" }
      withClue("person1.age") { person1.age shouldBe 111 }
    }

    val person2 = withClue("result2.data.person") { result2.data.person.shouldNotBeNull() }
    assertSoftly {
      withClue("person2.name") { person2.name shouldBe "Name222" }
      withClue("person2.age") { person2.age shouldBe 222 }
    }

    val person3 = withClue("result3.data.person") { result3.data.person.shouldNotBeNull() }
    assertSoftly {
      withClue("person3.name") { person3.name shouldBe "Name333" }
      withClue("person3.age") { person3.age.shouldBeNull() }
    }
  }

  @Test
  fun getPersonShouldReturnNullPersonIfThePersonDoesNotExist() = runTest {
    schema.deletePerson(id = "IdOfPersonThatDoesNotExit").execute()

    val result = schema.getPerson(id = "IdOfPersonThatDoesNotExit").execute()

    result.data.person.shouldBeNull()
  }

  @Test
  fun getNoPeopleShouldReturnEmptyList() = runTest {
    schema.getNoPeople.execute().data.people.shouldBeEmpty()
  }

  @Test
  fun getPeopleWithHardcodedNameShouldReturnTwoMatches() = runTest {
    schema.createPeopleWithHardcodedName.execute()

    val result = schema.getPeopleWithHardcodedName.execute()

    result.data.people.shouldContainExactlyInAnyOrder(hardcodedPeople)
  }

  @Test
  fun getPeopleByNameShouldReturnThePeopleWithTheGivenName() = runTest {
    val personName = Arb.alphanumericString(prefix = "personName").next()
    val person1Id = Arb.alphanumericString(prefix = "person1Id").next()
    val person2Id = Arb.alphanumericString(prefix = "person2Id").next()
    schema.createPerson(id = person1Id, name = personName, age = 1).execute()
    schema.createPerson(id = person2Id, name = personName, age = 2).execute()

    val result = schema.getPeopleByName(personName).execute()

    result.data.people.shouldContainExactlyInAnyOrder(
      PersonSchema.GetPeopleByNameQuery.Data.Person(id = person1Id, age = 1),
      PersonSchema.GetPeopleByNameQuery.Data.Person(id = person2Id, age = 2),
    )
  }
}
