package com.google.firebase.dataconnect.testutil.schemas

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.TestDataConnectFactory
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.CreatePersonMutation.execute
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.DeletePersonMutation.execute
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.GetAllPeopleQuery.Response.Person
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.GetAllPeopleQuery.execute
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.GetPersonQuery.execute
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.UpdatePersonMutation.execute
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonSchemaTest {

  @get:Rule val dataConnectLogLevelRule = DataConnectLogLevelRule()
  @get:Rule val dataConnectFactory = TestDataConnectFactory()

  private val schema
    get() = dataConnectFactory.personSchema

  @Test
  fun createPersonShouldCreateTheSpecifiedPerson() = runTest {
    schema.createPerson.execute(id = "1234", name = "TestName", age = 42)

    val result = schema.getPerson.execute(id = "1234")

    assertThat(result.data.person).isNotNull()
    val person = result.data.person!!
    assertThat(person.name).isEqualTo("TestName")
    assertThat(person.age).isEqualTo(42)
  }

  @Test
  fun deletePersonShouldDeleteTheSpecifiedPerson() = runTest {
    schema.createPerson.execute(id = "1234", name = "TestName", age = 42)
    assertThat(schema.getPerson.execute(id = "1234").data.person).isNotNull()

    schema.deletePerson.execute(id = "1234")

    assertThat(schema.getPerson.execute(id = "1234").data.person).isNull()
  }

  @Test
  fun updatePersonShouldUpdateTheSpecifiedPerson() = runTest {
    schema.createPerson.execute(id = "1234", name = "TestName0", age = 42)

    schema.updatePerson.execute(id = "1234", name = "TestName99", age = 999)

    val result = schema.getPerson.execute(id = "1234")
    assertThat(result.data.person?.name).isEqualTo("TestName99")
    assertThat(result.data.person?.age).isEqualTo(999)
  }

  @Test
  fun getPersonShouldReturnThePersonWithTheSpecifiedId() = runTest {
    schema.createPerson.execute(id = "111", name = "Name111", age = 111)
    schema.createPerson.execute(id = "222", name = "Name222", age = 222)
    schema.createPerson.execute(id = "333", name = "Name333", age = null)

    val result1 = schema.getPerson.execute(id = "111")
    val result2 = schema.getPerson.execute(id = "222")
    val result3 = schema.getPerson.execute(id = "333")

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
    schema.createPerson.execute(id = "111", name = "Name111", age = 111)

    val result = schema.getPerson.execute(id = "IdOfPersonThatDoesNotExit")

    assertThat(result.data.person).isNull()
  }

  @Test
  fun getAllPeopleShouldReturnEmptyListIfTheDatabaseIsEmpty() = runTest {
    assertThat(schema.getAllPeople.execute().data.people).isEmpty()
  }

  @Test
  fun getAllPeopleShouldReturnAllPeopleInTheDatabase() = runTest {
    schema.createPerson.execute(id = "111", name = "Name111", age = 111)
    schema.createPerson.execute(id = "222", name = "Name222", age = 222)
    schema.createPerson.execute(id = "333", name = "Name333", age = null)

    val result = schema.getAllPeople.execute()

    assertThat(result.data.people)
      .containsExactly(
        Person(id = "111", name = "Name111", age = 111),
        Person(id = "222", name = "Name222", age = 222),
        Person(id = "333", name = "Name333", age = null)
      )
  }
}
