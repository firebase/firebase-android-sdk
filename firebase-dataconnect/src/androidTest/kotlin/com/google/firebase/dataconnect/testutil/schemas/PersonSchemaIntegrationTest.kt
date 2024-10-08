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

import com.google.common.truth.Truth.assertThat
import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.GetPeopleWithHardcodedNameQuery.hardcodedPeople
import kotlinx.coroutines.test.*
import org.junit.Test

class PersonSchemaIntegrationTest : DataConnectIntegrationTestBase() {

  private val schema by lazy { PersonSchema(dataConnectFactory) }

  @Test
  fun createPersonShouldCreateTheSpecifiedPerson() = runTest {
    val personId = randomPersonId()
    schema.createPerson(id = personId, name = "TestName", age = 42).execute()

    val result = schema.getPerson(id = personId).execute()

    assertThat(result.data.person).isNotNull()
    val person = result.data.person!!
    assertThat(person.name).isEqualTo("TestName")
    assertThat(person.age).isEqualTo(42)
  }

  @Test
  fun deletePersonShouldDeleteTheSpecifiedPerson() = runTest {
    val personId = randomPersonId()
    schema.createPerson(id = personId, name = "TestName", age = 42).execute()
    assertThat(schema.getPerson(id = personId).execute().data.person).isNotNull()

    schema.deletePerson(id = personId).execute()

    assertThat(schema.getPerson(id = personId).execute().data.person).isNull()
  }

  @Test
  fun updatePersonShouldUpdateTheSpecifiedPerson() = runTest {
    val personId = randomPersonId()
    schema.createPerson(id = personId, name = "TestName0", age = 42).execute()

    schema.updatePerson(id = personId, name = "TestName99", age = 999).execute()

    val result = schema.getPerson(id = personId).execute()
    assertThat(result.data.person?.name).isEqualTo("TestName99")
    assertThat(result.data.person?.age).isEqualTo(999)
  }

  @Test
  fun getPersonShouldReturnThePersonWithTheSpecifiedId() = runTest {
    val person1Id = randomPersonId()
    val person2Id = randomPersonId()
    val person3Id = randomPersonId()
    schema.createPerson(id = person1Id, name = "Name111", age = 111).execute()
    schema.createPerson(id = person2Id, name = "Name222", age = 222).execute()
    schema.createPerson(id = person3Id, name = "Name333", age = null).execute()

    val result1 = schema.getPerson(id = person1Id).execute()
    val result2 = schema.getPerson(id = person2Id).execute()
    val result3 = schema.getPerson(id = person3Id).execute()

    assertThat(result1.data.person).isNotNull()
    val person1 = result1.data.person!!
    assertThat(person1.name).isEqualTo("Name111")
    assertThat(person1.age).isEqualTo(111)

    assertThat(result2.data.person).isNotNull()
    val person2 = result2.data.person!!
    assertThat(person2.name).isEqualTo("Name222")
    assertThat(person2.age).isEqualTo(222)

    assertThat(result3.data.person).isNotNull()
    val person3 = result3.data.person!!
    assertThat(person3.name).isEqualTo("Name333")
    assertThat(person3.age).isNull()
  }

  @Test
  fun getPersonShouldReturnNullPersonIfThePersonDoesNotExist() = runTest {
    schema.deletePerson(id = "IdOfPersonThatDoesNotExit").execute()

    val result = schema.getPerson(id = "IdOfPersonThatDoesNotExit").execute()

    assertThat(result.data.person).isNull()
  }

  @Test
  fun getNoPeopleShouldReturnEmptyList() = runTest {
    assertThat(schema.getNoPeople.execute().data.people).isEmpty()
  }

  @Test
  fun getPeopleWithHardcodedNameShouldReturnTwoMatches() = runTest {
    schema.createPeopleWithHardcodedName.execute()

    val result = schema.getPeopleWithHardcodedName.execute()

    assertThat(result.data.people).containsExactlyElementsIn(hardcodedPeople)
  }

  @Test
  fun getPeopleByNameShouldReturnThePeopleWithTheGivenName() = runTest {
    val personName = randomPersonName()
    val person1Id = randomPersonId()
    val person2Id = randomPersonId()
    schema.createPerson(id = person1Id, name = personName, age = 1).execute()
    schema.createPerson(id = person2Id, name = personName, age = 2).execute()

    val result = schema.getPeopleByName(personName).execute()

    assertThat(result.data.people)
      .containsExactly(
        PersonSchema.GetPeopleByNameQuery.Data.Person(id = person1Id, age = 1),
        PersonSchema.GetPeopleByNameQuery.Data.Person(id = person2Id, age = 2),
      )
  }
}
