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
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.TestDataConnectFactory
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.CreateDefaultPersonMutation.execute
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.CreatePersonMutation.execute
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.GetPersonQuery.execute
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataConnectUntypedVariablesIntegrationTest {

  @get:Rule val dataConnectLogLevelRule = DataConnectLogLevelRule()
  @get:Rule val dataConnectFactory = TestDataConnectFactory()

  private val personSchema
    get() = dataConnectFactory.personSchema

  @Test
  fun emptyMapWorksWithQuery() = runTest {
    personSchema.createPerson.execute(id = "Person1Id", name = "Person1Name", age = 42)
    personSchema.createPerson.execute(id = "Person2Id", name = "Person2Name", age = 43)
    val query = personSchema.getAllPeople.withVariablesSerializer(DataConnectUntypedVariables)

    val result = query.execute(DataConnectUntypedVariables())

    assertThat(result.variables).isEqualTo(DataConnectUntypedVariables())
    assertThat(result.data)
      .isEqualTo(
        PersonSchema.GetAllPeopleQuery.Response(
          people =
            listOf(
              PersonSchema.GetAllPeopleQuery.Response.Person(
                id = "Person1Id",
                name = "Person1Name",
                age = 42
              ),
              PersonSchema.GetAllPeopleQuery.Response.Person(
                id = "Person2Id",
                name = "Person2Name",
                age = 43
              ),
            )
        )
      )
  }

  @Test
  fun nonEmptyMapWorksWithQuery() = runTest {
    personSchema.createPerson.execute(id = "Person1Id", name = "Person1Name", age = 42)
    personSchema.createPerson.execute(id = "Person2Id", name = "Person2Name", age = 43)
    personSchema.createPerson.execute(id = "Person3Id", name = "Person3Name", age = null)
    val query = personSchema.getPerson.withVariablesSerializer(DataConnectUntypedVariables)

    val result = query.execute(DataConnectUntypedVariables("id" to "Person2Id"))

    assertThat(result.variables).isEqualTo(DataConnectUntypedVariables("id" to "Person2Id"))
    assertThat(result.data)
      .isEqualTo(
        PersonSchema.GetPersonQuery.Response(
          person = PersonSchema.GetPersonQuery.Response.Person(name = "Person2Name", age = 43)
        )
      )
  }

  @Test
  fun emptyMapWorksWithMutation() = runTest {
    val mutation =
      personSchema.createDefaultPerson.withVariablesSerializer(DataConnectUntypedVariables)

    mutation.execute(DataConnectUntypedVariables())

    val result = personSchema.getPerson.execute(id = "DefaultId")
    assertThat(result.data)
      .isEqualTo(
        PersonSchema.GetPersonQuery.Response(
          PersonSchema.GetPersonQuery.Response.Person(name = "DefaultName", age = 42)
        )
      )
  }

  @Test
  fun nonEmptyMapWorksWithMutation() = runTest {
    val mutation = personSchema.createPerson.withVariablesSerializer(DataConnectUntypedVariables)

    mutation.execute(
      DataConnectUntypedVariables(
        "data" to mapOf("id" to "PersonId", "name" to "TestPersonName", "age" to 42.0)
      )
    )

    val result = personSchema.getPerson.execute(id = "PersonId")
    assertThat(result.data)
      .isEqualTo(
        PersonSchema.GetPersonQuery.Response(
          PersonSchema.GetPersonQuery.Response.Person(name = "TestPersonName", age = 42)
        )
      )
  }
}
