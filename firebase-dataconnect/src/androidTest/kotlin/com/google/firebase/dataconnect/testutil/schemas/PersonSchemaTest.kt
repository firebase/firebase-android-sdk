package com.google.firebase.dataconnect.testutil.schemas

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.TestDataConnectFactory
import com.google.firebase.dataconnect.testutil.schemas.CreatePersonMutationExt.execute
import com.google.firebase.dataconnect.testutil.schemas.DeletePersonMutationExt.execute
import com.google.firebase.dataconnect.testutil.schemas.GetAllPeoplePersonQueryExt.execute
import com.google.firebase.dataconnect.testutil.schemas.GetPersonQueryExt.execute
import com.google.firebase.dataconnect.testutil.schemas.UpdatePersonMutationExt.execute
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonSchemaTest {

  @get:Rule val dataConnectFactory = TestDataConnectFactory()
  @get:Rule val dataConnectLogLevelRule = DataConnectLogLevelRule()

  private lateinit var schema: PersonSchema

  @Before
  fun initializePersonSchema() {
    schema = PersonSchema(dataConnectFactory.newInstance())
    runBlocking { schema.installEmulatorSchema() }
  }

  @Test
  fun createPersonShouldCreateTheSpecifiedPerson(): Unit = runBlocking {
    schema.createPerson.execute(id = "1234", name = "TestName", age = 42)

    val result = schema.getPerson.execute(id = "1234")

    assertThat(result.person?.name).isEqualTo("TestName")
    assertThat(result.person?.age).isEqualTo(42)
  }

  @Test
  fun deletePersonShouldDeleteTheSpecifiedPerson(): Unit = runBlocking {
    schema.createPerson.execute(id = "1234", name = "TestName", age = 42)
    assertThat(schema.getPerson.execute(id = "1234").person).isNotNull()

    schema.deletePerson.execute(id = "1234")

    assertThat(schema.getPerson.execute(id = "1234").person).isNull()
  }

  @Test
  fun updatePersonShouldUpdateTheSpecifiedPerson(): Unit = runBlocking {
    schema.createPerson.execute(id = "1234", name = "TestName0", age = 42)

    schema.updatePerson.execute(id = "1234", name = "TestName99", age = 999)

    val result = schema.getPerson.execute(id = "1234")
    assertThat(result.person?.name).isEqualTo("TestName99")
    assertThat(result.person?.age).isEqualTo(999)
  }

  @Test
  fun getPersonShouldReturnThePersonWithTheSpecifiedId(): Unit = runBlocking {
    schema.createPerson.execute(id = "111", name = "Name111", age = 111)
    schema.createPerson.execute(id = "222", name = "Name222", age = 222)
    schema.createPerson.execute(id = "333", name = "Name333", age = null)

    val result1 = schema.getPerson.execute(id = "111")
    val result2 = schema.getPerson.execute(id = "222")
    val result3 = schema.getPerson.execute(id = "333")

    assertThat(result1.person?.name).isEqualTo("Name111")
    assertThat(result1.person?.age).isEqualTo(111)
    assertThat(result2.person?.name).isEqualTo("Name222")
    assertThat(result2.person?.age).isEqualTo(222)
    assertThat(result3.person?.name).isEqualTo("Name333")
    assertThat(result3.person?.age).isNull()
  }

  @Test
  fun getPersonShouldReturnNullPersonIfThePersonDoesNotExist(): Unit = runBlocking {
    schema.createPerson.execute(id = "111", name = "Name111", age = 111)

    val result = schema.getPerson.execute(id = "IdOfPersonThatDoesNotExit")

    assertThat(result.person).isNull()
  }

  @Test
  fun getAllPeopleShouldReturnEmptyListIfTheDatabaseIsEmpty(): Unit = runBlocking {
    assertThat(schema.getAllPeople.execute().people).isEmpty()
  }

  @Test
  fun getAllPeopleShouldReturnAllPeopleInTheDatabase(): Unit = runBlocking {
    schema.createPerson.execute(id = "111", name = "Name111", age = 111)
    schema.createPerson.execute(id = "222", name = "Name222", age = 222)
    schema.createPerson.execute(id = "333", name = "Name333", age = null)

    val result = schema.getAllPeople.execute()

    assertThat(result.people)
      .containsExactly(
        PersonSchema.GetAllPeopleQuery.Result.Person(id = "111", name = "Name111", age = 111),
        PersonSchema.GetAllPeopleQuery.Result.Person(id = "222", name = "Name222", age = 222),
        PersonSchema.GetAllPeopleQuery.Result.Person(id = "333", name = "Name333", age = null),
      )
  }
}
