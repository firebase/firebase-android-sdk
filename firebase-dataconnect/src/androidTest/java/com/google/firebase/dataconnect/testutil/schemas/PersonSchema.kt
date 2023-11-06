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
import com.google.firebase.dataconnect.testutil.installEmulatorSchema

class PersonSchema(val dataConnect: FirebaseDataConnect) {

  suspend fun installEmulatorSchema() {
    dataConnect.installEmulatorSchema("testing_graphql_schemas/person")
  }

  class CreatePersonMutationRef(dataConnect: FirebaseDataConnect) :
    MutationRef<CreatePersonMutationRef.Variables, Unit>(
      dataConnect = dataConnect,
      operationName = "createPerson",
      operationSet = "ops",
      revision = "42"
    ) {

    data class Variables(val id: String, val name: String, val age: Int? = null)

    override fun encodeVariables(variables: Variables): Map<String, Any?> =
      variables.run {
        mapOf(
          "data" to
            buildMap {
              put("id", id)
              put("name", name)
              age?.let { put("age", it) }
            }
        )
      }

    override fun decodeResult(map: Map<String, Any?>) {}
  }

  class UpdatePersonMutationRef(dataConnect: FirebaseDataConnect) :
    MutationRef<UpdatePersonMutationRef.Variables, Unit>(
      dataConnect = dataConnect,
      operationName = "updatePerson",
      operationSet = "ops",
      revision = "42"
    ) {

    data class Variables(val id: String, val name: String? = null, val age: Int? = null)

    override fun encodeVariables(variables: Variables): Map<String, Any?> =
      variables.run {
        mapOf(
          "id" to id,
          "data" to
            buildMap {
              name?.let { put("name", it) }
              age?.let { put("age", it) }
            }
        )
      }

    override fun decodeResult(map: Map<String, Any?>) {}
  }

  class GetPersonQueryRef(dataConnect: FirebaseDataConnect) :
    QueryRef<GetPersonQueryRef.Variables, GetPersonQueryRef.Result?>(
      dataConnect = dataConnect,
      operationName = "getPerson",
      operationSet = "ops",
      revision = "42"
    ) {

    data class Variables(val id: String, val name: String? = null, val age: Int? = null)
    data class Result(val name: String, val age: Int? = null)

    override fun encodeVariables(variables: Variables) = variables.run { mapOf("id" to id) }

    override fun decodeResult(map: Map<String, Any?>) =
      (map["person"] as Map<*, *>?)?.let {
        Result(name = it["name"] as String, age = (it["age"] as Double?)?.toInt())
      }
  }

  class DeletePersonMutationRef(dataConnect: FirebaseDataConnect) :
    MutationRef<DeletePersonMutationRef.Variables, Unit>(
      dataConnect = dataConnect,
      operationName = "deletePerson",
      operationSet = "ops",
      revision = "42"
    ) {

    data class Variables(val id: String)

    override fun encodeVariables(variables: Variables) = variables.run { mapOf("id" to id) }

    override fun decodeResult(map: Map<String, Any?>) {}
  }

  val createPerson = CreatePersonMutationRef(dataConnect)
  val updatePerson = UpdatePersonMutationRef(dataConnect)
  val deletePerson = DeletePersonMutationRef(dataConnect)
  val getPerson = GetPersonQueryRef(dataConnect)
}

suspend fun PersonSchema.CreatePersonMutationRef.execute(id: String, name: String, age: Int?) =
  execute(PersonSchema.CreatePersonMutationRef.Variables(id = id, name = name, age = age))

suspend fun PersonSchema.UpdatePersonMutationRef.execute(
  id: String,
  name: String? = null,
  age: Int? = null
) = execute(PersonSchema.UpdatePersonMutationRef.Variables(id = id, name = name, age = age))

suspend fun PersonSchema.DeletePersonMutationRef.execute(id: String) =
  execute(PersonSchema.DeletePersonMutationRef.Variables(id = id))

suspend fun PersonSchema.GetPersonQueryRef.execute(id: String) =
  execute(PersonSchema.GetPersonQueryRef.Variables(id = id))

fun PersonSchema.GetPersonQueryRef.subscribe(id: String) =
  subscribe(PersonSchema.GetPersonQueryRef.Variables(id = id))
