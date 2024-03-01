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
import com.google.firebase.dataconnect.Mutation
import com.google.firebase.dataconnect.Query
import com.google.firebase.dataconnect.QuerySubscription
import com.google.firebase.dataconnect.mutation
import com.google.firebase.dataconnect.query
import com.google.firebase.dataconnect.testutil.TestDataConnectFactory
import com.google.firebase.dataconnect.testutil.installEmulatorSchema
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

class PersonSchema(val dataConnect: FirebaseDataConnect) {

  init {
    dataConnect.serviceConfig.connector.let {
      require(it == CONNECTOR) {
        "The given FirebaseDataConnect has operationSet=$it, but expected $CONNECTOR"
      }
    }
  }

  suspend fun installEmulatorSchema() {
    dataConnect.installEmulatorSchema("testing_graphql_schemas/person")
  }

  object CreateDefaultPersonMutation {
    suspend fun Mutation<Unit, Unit>.execute() = execute(Unit)
  }

  val createDefaultPerson =
    dataConnect.mutation(
      operationName = "createDefaultPerson",
      responseDeserializer = serializer<Unit>(),
      variablesSerializer = serializer<Unit>(),
    )

  object CreatePersonMutation {
    @Serializable data class PersonData(val id: String, val name: String, val age: Int? = null)
    @Serializable data class Variables(val data: PersonData)

    suspend fun Mutation<Unit, Variables>.execute(id: String, name: String, age: Int? = null) =
      execute(Variables(PersonData(id = id, name = name, age = age)))
  }

  val createPerson =
    dataConnect.mutation(
      operationName = "createPerson",
      responseDeserializer = serializer<Unit>(),
      variablesSerializer = serializer<CreatePersonMutation.Variables>(),
    )

  object UpdatePersonMutation {
    @Serializable data class PersonData(val name: String? = null, val age: Int? = null)
    @Serializable data class Variables(val id: String, val data: PersonData)

    suspend fun Mutation<Unit, Variables>.execute(
      id: String,
      name: String? = null,
      age: Int? = null
    ) = execute(Variables(id = id, data = PersonData(name = name, age = age)))
  }

  val updatePerson =
    dataConnect.mutation(
      operationName = "updatePerson",
      responseDeserializer = serializer<Unit>(),
      variablesSerializer = serializer<UpdatePersonMutation.Variables>(),
    )

  object DeletePersonMutation {
    @Serializable data class Variables(val id: String)

    suspend fun Mutation<Unit, Variables>.execute(id: String) = execute(Variables(id = id))
  }

  val deletePerson =
    dataConnect.mutation(
      operationName = "deletePerson",
      responseDeserializer = serializer<Unit>(),
      variablesSerializer = serializer<DeletePersonMutation.Variables>(),
    )

  object GetPersonQuery {
    @Serializable
    data class Response(val person: Person?) {
      @Serializable data class Person(val name: String, val age: Int? = null)
    }

    @Serializable data class Variables(val id: String)

    suspend fun Query<Response, Variables>.execute(id: String) = execute(Variables(id = id))

    fun Query<Response, Variables>.subscribe(id: String) = subscribe(Variables(id = id))

    suspend fun QuerySubscription<Response, Variables>.update(id: String) =
      update(Variables(id = id))
  }

  val getPerson =
    dataConnect.query(
      operationName = "getPerson",
      responseDeserializer = serializer<GetPersonQuery.Response>(),
      variablesSerializer = serializer<GetPersonQuery.Variables>(),
    )

  object GetAllPeopleQuery {
    @Serializable
    data class Response(val people: List<Person>) {
      @Serializable data class Person(val id: String, val name: String, val age: Int?)
    }

    suspend fun Query<Response, Unit>.execute() = execute(Unit)

    fun Query<Response, Unit>.subscribe() = subscribe(Unit)
  }

  val getAllPeople =
    dataConnect.query(
      operationName = "getAllPeople",
      responseDeserializer = serializer<GetAllPeopleQuery.Response>(),
      variablesSerializer = serializer<Unit>(),
    )

  companion object {
    const val CONNECTOR = "ops"

    suspend fun TestDataConnectFactory.installPersonSchema(): PersonSchema =
      PersonSchema(newInstance(connector = CONNECTOR)).apply { installEmulatorSchema() }
  }
}
