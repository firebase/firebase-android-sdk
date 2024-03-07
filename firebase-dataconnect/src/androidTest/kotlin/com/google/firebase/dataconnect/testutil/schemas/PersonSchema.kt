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

package com.google.firebase.dataconnect.testutil.schemas

import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.mutation
import com.google.firebase.dataconnect.query
import com.google.firebase.dataconnect.testutil.TestDataConnectFactory
import com.google.firebase.dataconnect.testutil.installEmulatorSchema
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

class PersonSchema(val dataConnect: FirebaseDataConnect) {

  init {
    dataConnect.config.connector.let {
      require(it == CONNECTOR) {
        "The given FirebaseDataConnect has connector=$it, but expected $CONNECTOR"
      }
    }
  }

  suspend fun installEmulatorSchema() {
    dataConnect.installEmulatorSchema("testing_graphql_schemas/person")
  }

  val createDefaultPerson
    get() =
      dataConnect.mutation(
        operationName = "createDefaultPerson",
        dataDeserializer = serializer<Unit>()
      )

  object CreatePersonMutation {
    @Serializable data class PersonData(val id: String, val name: String, val age: Int? = null)
    @Serializable data class Variables(val data: PersonData)
  }

  fun createPerson(variables: CreatePersonMutation.Variables) =
    dataConnect.mutation(
      operationName = "createPerson",
      variables = variables,
      dataDeserializer = serializer<Unit>(),
      variablesSerializer = serializer<CreatePersonMutation.Variables>(),
    )

  fun createPerson(id: String, name: String, age: Int? = null) =
    createPerson(
      CreatePersonMutation.Variables(
        CreatePersonMutation.PersonData(id = id, name = name, age = age)
      )
    )

  object UpdatePersonMutation {
    @Serializable data class PersonData(val name: String? = null, val age: Int? = null)
    @Serializable data class Variables(val id: String, val data: PersonData)
  }

  fun updatePerson(variables: UpdatePersonMutation.Variables) =
    dataConnect.mutation(
      operationName = "updatePerson",
      variables = variables,
      dataDeserializer = serializer<Unit>(),
      variablesSerializer = serializer<UpdatePersonMutation.Variables>(),
    )

  fun updatePerson(id: String, name: String? = null, age: Int? = null) =
    updatePerson(
      UpdatePersonMutation.Variables(
        id = id,
        data = UpdatePersonMutation.PersonData(name = name, age = age)
      )
    )

  object DeletePersonMutation {
    @Serializable data class Variables(val id: String)
  }

  fun deletePerson(variables: DeletePersonMutation.Variables) =
    dataConnect.mutation(
      operationName = "deletePerson",
      variables = variables,
      dataDeserializer = serializer<Unit>(),
      variablesSerializer = serializer<DeletePersonMutation.Variables>(),
    )

  fun deletePerson(id: String) = deletePerson(DeletePersonMutation.Variables(id = id))

  object GetPersonQuery {
    @Serializable
    data class Data(val person: Person?) {
      @Serializable data class Person(val name: String, val age: Int? = null)
    }

    @Serializable data class Variables(val id: String)
  }

  fun getPerson(variables: GetPersonQuery.Variables) =
    dataConnect.query(
      operationName = "getPerson",
      variables = variables,
      dataDeserializer = serializer<GetPersonQuery.Data>(),
      variablesSerializer = serializer<GetPersonQuery.Variables>(),
    )

  fun getPerson(id: String) = getPerson(GetPersonQuery.Variables(id = id))

  object GetAllPeopleQuery {
    @Serializable
    data class Data(val people: List<Person>) {
      @Serializable data class Person(val id: String, val name: String, val age: Int?)
    }
  }

  val getAllPeople
    get() =
      dataConnect.query(
        operationName = "getAllPeople",
        dataDeserializer = serializer<GetAllPeopleQuery.Data>()
      )

  companion object {
    const val CONNECTOR = "ops"
  }
}

suspend fun TestDataConnectFactory.installPersonSchema() =
  PersonSchema(newInstance(connector = PersonSchema.CONNECTOR)).apply { installEmulatorSchema() }

fun LazyPersonSchema(dataConnectFactory: TestDataConnectFactory): Lazy<PersonSchema> = lazy {
  runBlocking { dataConnectFactory.installPersonSchema() }
}
