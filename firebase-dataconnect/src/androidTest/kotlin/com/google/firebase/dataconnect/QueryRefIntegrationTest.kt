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

@file:OptIn(FlowPreview::class)

package com.google.firebase.dataconnect

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.TestDataConnectFactory
import com.google.firebase.dataconnect.testutil.schemas.AllTypesSchema
import com.google.firebase.dataconnect.testutil.schemas.AllTypesSchema.Companion.newAllTypesSchema
import com.google.firebase.dataconnect.testutil.schemas.AllTypesSchema.CreatePrimitiveMutation.execute
import com.google.firebase.dataconnect.testutil.schemas.AllTypesSchema.GetPrimitiveQuery.execute
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.Companion.newPersonSchema
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.CreatePersonMutation.execute
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.GetAllPeopleQuery.execute
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.GetPersonQuery.execute
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.UpdatePersonMutation.execute
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QueryRefIntegrationTest {

  @get:Rule val dataConnectFactory = TestDataConnectFactory()
  @get:Rule val dataConnectLogLevelRule = DataConnectLogLevelRule()

  private val personSchema: PersonSchema by lazy {
    dataConnectFactory.newPersonSchema().also { runBlocking { it.installEmulatorSchema() } }
  }

  private val allTypesSchema: AllTypesSchema by lazy {
    dataConnectFactory.newAllTypesSchema().also { runBlocking { it.installEmulatorSchema() } }
  }

  @Test
  fun executeWithASingleResultReturnsTheCorrectResult(): Unit = runBlocking {
    personSchema.createPerson.execute(id = "TestId1", name = "TestName1", age = 42)
    personSchema.createPerson.execute(id = "TestId2", name = "TestName2", age = 43)
    personSchema.createPerson.execute(id = "TestId3", name = "TestName3", age = 44)

    val result = personSchema.getPerson.execute(id = "TestId2")

    assertThat(result.data.person?.name).isEqualTo("TestName2")
    assertThat(result.data.person?.age).isEqualTo(43)
    assertThat(result.errors).isEmpty()
  }

  @Test
  fun executeWithASingleResultReturnsTheUpdatedResult(): Unit = runBlocking {
    personSchema.createPerson.execute(id = "TestId", name = "TestName", age = 42)
    personSchema.updatePerson.execute(id = "TestId", name = "NewTestName", age = 99)

    val result = personSchema.getPerson.execute(id = "TestId")

    assertThat(result.data.person?.name).isEqualTo("NewTestName")
    assertThat(result.data.person?.age).isEqualTo(99)
    assertThat(result.errors).isEmpty()
  }

  @Test
  fun executeWithASingleResultReturnsNullIfNotFound(): Unit = runBlocking {
    personSchema.createPerson.execute(id = "TestId", name = "TestName", age = 42)

    val result = personSchema.getPerson.execute(id = "NotTheTestId")

    assertThat(result.data.person).isNull()
    assertThat(result.errors).isEmpty()
  }

  @Test
  fun executeWithAListResultReturnsAllResults(): Unit = runBlocking {
    personSchema.createPerson.execute(id = "TestId1", name = "TestName1", age = 42)
    personSchema.createPerson.execute(id = "TestId2", name = "TestName2", age = 43)
    personSchema.createPerson.execute(id = "TestId3", name = "TestName3", age = 44)

    val result = personSchema.getAllPeople.execute()

    assertThat(result.data.people)
      .containsExactly(
        PersonSchema.GetAllPeopleQuery.Data.Person(id = "TestId1", name = "TestName1", age = 42),
        PersonSchema.GetAllPeopleQuery.Data.Person(id = "TestId2", name = "TestName2", age = 43),
        PersonSchema.GetAllPeopleQuery.Data.Person(id = "TestId3", name = "TestName3", age = 44),
      )
    assertThat(result.errors).isEmpty()
  }

  @Test
  fun executeWithAllPrimitiveGraphQLTypesInDataNoneNull(): Unit = runBlocking {
    allTypesSchema.createPrimitive.execute(
      id = "TestId",
      idFieldNullable = "TestNullableId",
      intField = 42,
      intFieldNullable = 43,
      floatField = 123.45f,
      floatFieldNullable = 678.91f,
      booleanField = true,
      booleanFieldNullable = false,
      stringField = "TestString",
      stringFieldNullable = "TestNullableString",
    )

    val result = allTypesSchema.getPrimitive.execute(id = "TestId")

    val primitive = result.data.primitive ?: error("result.data was null, but expected non")
    assertThat(primitive.id).isEqualTo("TestId")
    assertThat(primitive.idFieldNullable).isEqualTo("TestNullableId")
    assertThat(primitive.intField).isEqualTo(42)
    assertThat(primitive.intFieldNullable).isEqualTo(43)
    assertThat(primitive.floatField).isEqualTo(123.45f)
    assertThat(primitive.floatFieldNullable).isEqualTo(678.91f)
    assertThat(primitive.booleanField).isEqualTo(true)
    assertThat(primitive.booleanFieldNullable).isEqualTo(false)
    assertThat(primitive.stringField).isEqualTo("TestString")
    assertThat(primitive.stringFieldNullable).isEqualTo("TestNullableString")
  }

  @Test
  fun executeWithAllPrimitiveGraphQLTypesInDataNullablesAreNull(): Unit = runBlocking {
    allTypesSchema.createPrimitive.execute(
      id = "TestId",
      idFieldNullable = null,
      intField = 42,
      intFieldNullable = null,
      floatField = 123.45f,
      floatFieldNullable = null,
      booleanField = true,
      booleanFieldNullable = null,
      stringField = "TestString",
      stringFieldNullable = null,
    )

    val result = allTypesSchema.getPrimitive.execute(id = "TestId")

    val primitive = result.data.primitive ?: error("result.data was null, but expected non")
    assertThat(primitive.idFieldNullable).isNull()
    assertThat(primitive.intFieldNullable).isNull()
    assertThat(primitive.floatFieldNullable).isNull()
    assertThat(primitive.booleanFieldNullable).isNull()
    assertThat(primitive.stringFieldNullable).isNull()
  }
}
