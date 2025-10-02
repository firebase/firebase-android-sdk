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

import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.copy
import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase.Companion.testConnectorConfig
import com.google.firebase.dataconnect.testutil.TestDataConnectFactory
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

class PersonSchema(val dataConnect: FirebaseDataConnect) {

  constructor(
    dataConnectFactory: TestDataConnectFactory
  ) : this(dataConnectFactory.newInstance(testConnectorConfig.copy(connector = CONNECTOR)))

  init {
    dataConnect.config.connector.let {
      require(it == CONNECTOR) {
        "The given FirebaseDataConnect has connector=$it, but expected $CONNECTOR"
      }
    }
  }

  object CreateDefaultPersonMutation {
    @Serializable
    data class Data(val person_insert: PersonKey) {
      @Serializable data class PersonKey(val id: String)
    }
  }

  val createDefaultPerson
    get() =
      dataConnect.mutation(
        operationName = "createDefaultPerson",
        variables = Unit,
        dataDeserializer = serializer<CreateDefaultPersonMutation.Data>(),
        variablesSerializer = serializer<Unit>()
      )

  object CreatePersonMutation {
    const val operationName = "createPerson"

    @Serializable
    data class Data(val person_insert: PersonKey) {
      @Serializable data class PersonKey(val id: String)
    }
    @Serializable data class Variables(val id: String, val name: String, val age: Int? = null)
  }

  fun createPerson(variables: CreatePersonMutation.Variables) =
    dataConnect.mutation(
      operationName = CreatePersonMutation.operationName,
      variables = variables,
      dataDeserializer = serializer<CreatePersonMutation.Data>(),
      variablesSerializer = serializer<CreatePersonMutation.Variables>(),
    )

  fun createPersonAuth(id: String, name: String, age: Int? = null) =
    createPersonAuth(CreatePersonAuthMutation.Variables(id = id, name = name, age = age))

  object CreatePersonAuthMutation {
    @Serializable
    data class Data(val person_insert: PersonKey) {
      @Serializable data class PersonKey(val id: String)
    }
    @Serializable data class Variables(val id: String, val name: String, val age: Int? = null)
  }

  fun createPersonAuth(variables: CreatePersonAuthMutation.Variables) =
    dataConnect.mutation(
      operationName = "createPersonAuth",
      variables = variables,
      dataDeserializer = serializer<CreatePersonAuthMutation.Data>(),
      variablesSerializer = serializer<CreatePersonAuthMutation.Variables>(),
    )

  fun createPerson(id: String, name: String, age: Int? = null) =
    createPerson(CreatePersonMutation.Variables(id = id, name = name, age = age))

  object CreateOrUpdatePersonMutation {
    @Serializable
    data class Data(val person_upsert: PersonKey) {
      @Serializable data class PersonKey(val id: String)
    }
    @Serializable data class Variables(val id: String, val name: String, val age: Int? = null)
  }

  fun createOrUpdatePerson(variables: CreateOrUpdatePersonMutation.Variables) =
    dataConnect.mutation(
      operationName = "createOrUpdatePerson",
      variables = variables,
      dataDeserializer = serializer<CreateOrUpdatePersonMutation.Data>(),
      variablesSerializer = serializer<CreateOrUpdatePersonMutation.Variables>(),
    )

  fun createOrUpdatePerson(id: String, name: String, age: Int? = null) =
    createOrUpdatePerson(CreateOrUpdatePersonMutation.Variables(id = id, name = name, age = age))

  object UpdatePersonMutation {
    @Serializable
    data class Variables(val id: String, val name: String? = null, val age: Int? = null)
  }

  fun updatePerson(variables: UpdatePersonMutation.Variables) =
    dataConnect.mutation(
      operationName = "updatePerson",
      variables = variables,
      dataDeserializer = serializer<Unit>(),
      variablesSerializer = serializer<UpdatePersonMutation.Variables>(),
    )

  fun updatePerson(id: String, name: String? = null, age: Int? = null) =
    updatePerson(UpdatePersonMutation.Variables(id = id, name = name, age = age))

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
    const val operationName = "getPerson"

    @Serializable
    data class Data(val person: Person?) {
      @Serializable data class Person(val name: String, val age: Int? = null)
    }

    @Serializable data class Variables(val id: String)
  }

  fun getPerson(variables: GetPersonQuery.Variables) =
    dataConnect.query(
      operationName = GetPersonQuery.operationName,
      variables = variables,
      dataDeserializer = serializer<GetPersonQuery.Data>(),
      variablesSerializer = serializer<GetPersonQuery.Variables>(),
    )

  fun getPerson(id: String) = getPerson(GetPersonQuery.Variables(id = id))

  object GetPersonAuthQuery {
    @Serializable
    data class Data(val person: Person?) {
      @Serializable data class Person(val name: String, val age: Int? = null)
    }

    @Serializable data class Variables(val id: String)
  }

  fun getPersonAuth(variables: GetPersonAuthQuery.Variables) =
    dataConnect.query(
      operationName = "getPersonAuth",
      variables = variables,
      dataDeserializer = serializer<GetPersonAuthQuery.Data>(),
      variablesSerializer = serializer<GetPersonAuthQuery.Variables>(),
    )

  fun getPersonAuth(id: String) = getPersonAuth(GetPersonAuthQuery.Variables(id = id))

  object GetPeopleByNameQuery {
    @Serializable
    data class Data(val people: List<Person>) {
      @Serializable data class Person(val id: String, val age: Int? = null)
    }

    @Serializable data class Variables(val name: String)
  }

  fun getPeopleByName(variables: GetPeopleByNameQuery.Variables) =
    dataConnect.query(
      operationName = "getPeopleByName",
      variables = variables,
      dataDeserializer = serializer<GetPeopleByNameQuery.Data>(),
      variablesSerializer = serializer<GetPeopleByNameQuery.Variables>(),
    )

  fun getPeopleByName(name: String) = getPeopleByName(GetPeopleByNameQuery.Variables(name = name))

  object GetNoPeopleQuery {
    @Serializable
    data class Data(val people: List<Person>) {
      @Serializable data class Person(val id: String)
    }
  }

  val getNoPeople
    get() =
      dataConnect.query(
        operationName = "getNoPeople",
        variables = Unit,
        dataDeserializer = serializer<GetNoPeopleQuery.Data>(),
        variablesSerializer = serializer<Unit>()
      )

  object GetPeopleWithHardcodedNameQuery {
    // These values *must* match the hardcoded values in the graphql source.
    val hardcodedPeople
      get() =
        listOf(
          Data.Person(id = "HardcodedNamePerson1Id_v1", age = null),
          Data.Person(id = "HardcodedNamePerson2Id_v1", age = 42)
        )

    @Serializable
    data class Data(val people: List<Person>) {
      @Serializable data class Person(val id: String, val age: Int?)
    }
  }

  val getPeopleWithHardcodedName
    get() =
      dataConnect.query(
        operationName = "getPeopleWithHardcodedName",
        variables = Unit,
        dataDeserializer = serializer<GetPeopleWithHardcodedNameQuery.Data>(),
        variablesSerializer = serializer<Unit>()
      )

  val createPeopleWithHardcodedName
    get() =
      dataConnect.mutation(
        operationName = "createPeopleWithHardcodedName",
        variables = Unit,
        dataDeserializer = serializer<Unit>(),
        variablesSerializer = serializer<Unit>()
      )

  companion object {
    const val CONNECTOR = "person"
  }
}
