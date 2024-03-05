package com.google.firebase.dataconnect.testutil.schemas

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.TestDataConnectFactory
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.GetAllPeopleQuery.Response.Person
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
    schema.createPerson(id = "1234", name = "TestName", age = 42).execute()

    val result = schema.getPerson(id = "1234").execute()

    assertThat(result.data.person).isNotNull()
    val person = result.data.person!!
    assertThat(person.name).isEqualTo("TestName")
    assertThat(person.age).isEqualTo(42)
  }

  @Test
  fun deletePersonShouldDeleteTheSpecifiedPerson() = runTest {
    schema.createPerson(id = "1234", name = "TestName", age = 42).execute()
    assertThat(schema.getPerson(id = "1234").execute().data.person).isNotNull()

    schema.deletePerson(id = "1234").execute()

    assertThat(schema.getPerson(id = "1234").execute().data.person).isNull()
  }

  @Test
  fun updatePersonShouldUpdateTheSpecifiedPerson() = runTest {
    schema.createPerson(id = "1234", name = "TestName0", age = 42).execute()

    schema.updatePerson(id = "1234", name = "TestName99", age = 999).execute()

    val result = schema.getPerson(id = "1234").execute()
    assertThat(result.data.person?.name).isEqualTo("TestName99")
    assertThat(result.data.person?.age).isEqualTo(999)
  }

  @Test
  fun getPersonShouldReturnThePersonWithTheSpecifiedId() = runTest {
    schema.createPerson(id = "111", name = "Name111", age = 111).execute()
    schema.createPerson(id = "222", name = "Name222", age = 222).execute()
    schema.createPerson(id = "333", name = "Name333", age = null).execute()

    val result1 = schema.getPerson(id = "111").execute()
    val result2 = schema.getPerson(id = "222").execute()
    val result3 = schema.getPerson(id = "333").execute()

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
    schema.createPerson(id = "111", name = "Name111", age = 111).execute()

    val result = schema.getPerson(id = "IdOfPersonThatDoesNotExit").execute()

    assertThat(result.data.person).isNull()
  }

  @Test
  fun getAllPeopleShouldReturnEmptyListIfTheDatabaseIsEmpty() = runTest {
    assertThat(schema.getAllPeople.execute().data.people).isEmpty()
  }

  @Test
  fun getAllPeopleShouldReturnAllPeopleInTheDatabase() = runTest {
    schema.createPerson(id = "111", name = "Name111", age = 111).execute()
    schema.createPerson(id = "222", name = "Name222", age = 222).execute()
    schema.createPerson(id = "333", name = "Name333", age = null).execute()

    val result = schema.getAllPeople.execute()

    assertThat(result.data.people)
      .containsExactly(
        Person(id = "111", name = "Name111", age = 111),
        Person(id = "222", name = "Name222", age = 222),
        Person(id = "333", name = "Name333", age = null)
      )
  }
}
