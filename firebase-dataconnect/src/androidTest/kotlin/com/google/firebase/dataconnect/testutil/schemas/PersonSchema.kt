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
import com.google.firebase.dataconnect.MutationRef
import com.google.firebase.dataconnect.QueryRef
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
    suspend fun MutationRef<Unit, Unit>.execute() = execute(Unit)
  }

  val createDefaultPerson =
    dataConnect.mutation(
      operationName = "createDefaultPerson",
      variablesSerializer = serializer<Unit>(),
      dataDeserializer = serializer<Unit>()
    )

  object CreatePersonMutation {
    @Serializable data class PersonData(val id: String, val name: String, val age: Int? = null)
    @Serializable data class Variables(val data: PersonData)

    suspend fun MutationRef<Variables, Unit>.execute(id: String, name: String, age: Int? = null) =
      execute(Variables(PersonData(id = id, name = name, age = age)))
  }

  val createPerson =
    dataConnect.mutation(
      operationName = "createPerson",
      variablesSerializer = serializer<CreatePersonMutation.Variables>(),
      dataDeserializer = serializer<Unit>()
    )

  object UpdatePersonMutation {
    @Serializable data class PersonData(val name: String? = null, val age: Int? = null)
    @Serializable data class Variables(val id: String, val data: PersonData)

    suspend fun MutationRef<Variables, Unit>.execute(
      id: String,
      name: String? = null,
      age: Int? = null
    ) = execute(Variables(id = id, data = PersonData(name = name, age = age)))
  }

  val updatePerson =
    dataConnect.mutation(
      operationName = "updatePerson",
      variablesSerializer = serializer<UpdatePersonMutation.Variables>(),
      dataDeserializer = serializer<Unit>()
    )

  object DeletePersonMutation {
    @Serializable data class Variables(val id: String)

    suspend fun MutationRef<Variables, Unit>.execute(id: String) = execute(Variables(id = id))
  }

  val deletePerson =
    dataConnect.mutation(
      operationName = "deletePerson",
      variablesSerializer = serializer<DeletePersonMutation.Variables>(),
      dataDeserializer = serializer<Unit>()
    )

  object GetPersonQuery {
    @Serializable data class Variables(val id: String)

    @Serializable
    data class Data(val person: Person?) {
      @Serializable data class Person(val name: String, val age: Int? = null)
    }

    suspend fun QueryRef<Variables, Data>.execute(id: String) = execute(Variables(id = id))

    fun QueryRef<Variables, Data>.subscribe(id: String) = subscribe(Variables(id = id))

    suspend fun QuerySubscription<Variables, Data>.update(id: String) = update(Variables(id = id))
  }

  val getPerson =
    dataConnect.query(
      operationName = "getPerson",
      variablesSerializer = serializer<GetPersonQuery.Variables>(),
      dataDeserializer = serializer<GetPersonQuery.Data>()
    )

  object GetAllPeopleQuery {
    @Serializable
    data class Data(val people: List<Person>) {
      @Serializable data class Person(val id: String, val name: String, val age: Int?)
    }

    suspend fun QueryRef<Unit, Data>.execute() = execute(Unit)

    fun QueryRef<Unit, Data>.subscribe() = subscribe(Unit)
  }

  val getAllPeople =
    dataConnect.query(
      operationName = "getAllPeople",
      variablesSerializer = serializer<Unit>(),
      dataDeserializer = serializer<GetAllPeopleQuery.Data>()
    )

  companion object {
    const val CONNECTOR = "ops"

    suspend fun TestDataConnectFactory.installPersonSchema(): PersonSchema =
      PersonSchema(newInstance(connector = CONNECTOR)).apply { installEmulatorSchema() }
  }
}
