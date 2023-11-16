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

import com.google.firebase.dataconnect.BaseRef
import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.MutationRef
import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.testutil.installEmulatorSchema
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

class PersonSchema(val dataConnect: FirebaseDataConnect) {

  suspend fun installEmulatorSchema() {
    dataConnect.installEmulatorSchema("testing_graphql_schemas/person")
  }

  val createPerson =
    dataConnect.mutation(
      operationName = "createPerson",
      operationSet = "ops",
      revision = "42",
      codec = CreatePersonMutation,
      variablesSerializer = serializer<CreatePersonMutation.Variables>(),
    )

  class CreatePersonMutation private constructor() {

    @Serializable data class PersonData(val id: String, val name: String, val age: Int? = null)
    @Serializable data class Variables(val data: PersonData)

    internal companion object Codec : BaseRef.Codec<Unit> {
      override fun decodeResult(map: Map<String, Any?>) {}
    }
  }

  val updatePerson =
    dataConnect.mutation(
      operationName = "updatePerson",
      operationSet = "ops",
      revision = "42",
      codec = UpdatePersonMutation,
      variablesSerializer = serializer<UpdatePersonMutation.Variables>(),
    )

  class UpdatePersonMutation private constructor() {

    @Serializable data class PersonData(val name: String? = null, val age: Int? = null)
    @Serializable data class Variables(val id: String, val data: PersonData)

    internal companion object Codec : BaseRef.Codec<Unit> {
      override fun decodeResult(map: Map<String, Any?>) {}
    }
  }

  val deletePerson =
    dataConnect.mutation(
      operationName = "deletePerson",
      operationSet = "ops",
      revision = "42",
      codec = DeletePersonMutation,
      variablesSerializer = serializer<DeletePersonMutation.Variables>(),
    )

  class DeletePersonMutation private constructor() {

    @Serializable data class Variables(val id: String)

    internal companion object Codec : BaseRef.Codec<Unit> {
      override fun decodeResult(map: Map<String, Any?>) {}
    }
  }

  val getPerson =
    dataConnect.query(
      operationName = "getPerson",
      operationSet = "ops",
      revision = "42",
      codec = GetPersonQuery,
      variablesSerializer = serializer<GetPersonQuery.Variables>(),
    )

  class GetPersonQuery private constructor() {

    @Serializable
    data class Variables(val id: String, val name: String? = null, val age: Int? = null)
    data class Result(val name: String, val age: Int? = null)

    internal companion object Codec : BaseRef.Codec<Result?> {
      override fun decodeResult(map: Map<String, Any?>) =
        (map["person"] as Map<*, *>?)?.let {
          Result(name = it["name"] as String, age = (it["age"] as Double?)?.toInt())
        }
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
  suspend fun QueryRef<PersonSchema.GetPersonQuery.Variables, PersonSchema.GetPersonQuery.Result?>
    .execute(id: String) = execute(PersonSchema.GetPersonQuery.Variables(id = id))

  fun QueryRef<PersonSchema.GetPersonQuery.Variables, PersonSchema.GetPersonQuery.Result?>
    .subscribe(id: String) = subscribe(PersonSchema.GetPersonQuery.Variables(id = id))
}
