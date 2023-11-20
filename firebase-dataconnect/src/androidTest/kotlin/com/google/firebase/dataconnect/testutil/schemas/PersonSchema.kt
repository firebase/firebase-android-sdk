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
import com.google.firebase.dataconnect.mutation
import com.google.firebase.dataconnect.query
import com.google.firebase.dataconnect.testutil.installEmulatorSchema
import kotlinx.serialization.Serializable

class PersonSchema(val dataConnect: FirebaseDataConnect) {

  suspend fun installEmulatorSchema() {
    dataConnect.installEmulatorSchema("testing_graphql_schemas/person")
  }

  val createPerson =
    dataConnect.mutation<CreatePersonMutation.Variables, Unit>(
      operationName = "createPerson",
      operationSet = "ops",
      revision = "42",
    )

  class CreatePersonMutation private constructor() {
    @Serializable data class PersonData(val id: String, val name: String, val age: Int? = null)
    @Serializable data class Variables(val data: PersonData)
  }

  val updatePerson =
    dataConnect.mutation<UpdatePersonMutation.Variables, Unit>(
      operationName = "updatePerson",
      operationSet = "ops",
      revision = "42",
    )

  class UpdatePersonMutation private constructor() {
    @Serializable data class PersonData(val name: String? = null, val age: Int? = null)
    @Serializable data class Variables(val id: String, val data: PersonData)
  }

  val deletePerson =
    dataConnect.mutation<DeletePersonMutation.Variables, Unit>(
      operationName = "deletePerson",
      operationSet = "ops",
      revision = "42",
    )

  class DeletePersonMutation private constructor() {
    @Serializable data class Variables(val id: String)
  }

  val getPerson =
    dataConnect.query<GetPersonQuery.Variables, GetPersonQuery.Data>(
      operationName = "getPerson",
      operationSet = "ops",
      revision = "42",
    )

  class GetPersonQuery private constructor() {
    @Serializable
    data class Variables(val id: String, val name: String? = null, val age: Int? = null)

    @Serializable
    data class Data(val person: Person?) {
      @Serializable data class Person(val name: String, val age: Int? = null)
    }
  }

  val getAllPeople =
    dataConnect.query<Unit, GetAllPeopleQuery.Data>(
      operationName = "getAllPeople",
      operationSet = "ops",
      revision = "42",
    )

  class GetAllPeopleQuery private constructor() {
    @Serializable
    data class Data(val people: List<Person>) {
      @Serializable data class Person(val id: String, val name: String, val age: Int?)
    }
  }
}

object CreatePersonMutationExt {
  suspend fun MutationRef<PersonSchema.CreatePersonMutation.Variables, Unit>.execute(
    id: String,
    name: String,
    age: Int?
  ) =
    execute(
      PersonSchema.CreatePersonMutation.Variables(
        PersonSchema.CreatePersonMutation.PersonData(id = id, name = name, age = age)
      )
    )
}

object UpdatePersonMutationExt {
  suspend fun MutationRef<PersonSchema.UpdatePersonMutation.Variables, Unit>.execute(
    id: String,
    name: String? = null,
    age: Int? = null
  ) =
    execute(
      PersonSchema.UpdatePersonMutation.Variables(
        id = id,
        data = PersonSchema.UpdatePersonMutation.PersonData(name = name, age = age)
      )
    )
}

object DeletePersonMutationExt {
  suspend fun MutationRef<PersonSchema.DeletePersonMutation.Variables, Unit>.execute(id: String) =
    execute(PersonSchema.DeletePersonMutation.Variables(id = id))
}

object GetPersonQueryExt {
  suspend fun QueryRef<PersonSchema.GetPersonQuery.Variables, PersonSchema.GetPersonQuery.Data>
    .execute(id: String) = execute(PersonSchema.GetPersonQuery.Variables(id = id))

  fun QueryRef<PersonSchema.GetPersonQuery.Variables, PersonSchema.GetPersonQuery.Data>.subscribe(
    id: String
  ) = subscribe(PersonSchema.GetPersonQuery.Variables(id = id))
}

object GetAllPeoplePersonQueryExt {
  suspend fun QueryRef<Unit, PersonSchema.GetAllPeopleQuery.Data>.execute() = execute(Unit)
  fun QueryRef<Unit, PersonSchema.GetAllPeopleQuery.Data>.subscribe() = subscribe(Unit)
}
