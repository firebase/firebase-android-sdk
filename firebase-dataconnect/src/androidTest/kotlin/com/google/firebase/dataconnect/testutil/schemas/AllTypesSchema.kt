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
import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.mutation
import com.google.firebase.dataconnect.query
import com.google.firebase.dataconnect.testutil.TestDataConnectFactory
import com.google.firebase.dataconnect.testutil.installEmulatorSchema
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

class AllTypesSchema(private val dataConnect: FirebaseDataConnect) {

  init {
    dataConnect.serviceConfig.operationSet.let {
      require(it == OPERATION_SET) {
        "The given FirebaseDataConnect has operationSet=$it, but expected $OPERATION_SET"
      }
    }
  }

  suspend fun installEmulatorSchema() {
    dataConnect.installEmulatorSchema("testing_graphql_schemas/alltypes")
  }

  @Serializable
  data class PrimitiveData(
    val id: String,
    val idFieldNullable: String?,
    val intField: Int,
    val intFieldNullable: Int?,
    val floatField: Float,
    val floatFieldNullable: Float?,
    val booleanField: Boolean,
    val booleanFieldNullable: Boolean?,
    val stringField: String,
    val stringFieldNullable: String?,
  )

  object CreatePrimitiveMutation {
    @Serializable data class Variables(val data: PrimitiveData)
  }

  val createPrimitive =
    dataConnect.mutation(
      operationName = "createPrimitive",
      variablesSerializer = serializer<CreatePrimitiveMutation.Variables>(),
      dataDeserializer = serializer<Unit>()
    )

  object GetPrimitiveQuery {
    @Serializable data class Variables(val id: String)

    @Serializable data class Data(val primitive: PrimitiveData?)

    suspend fun QueryRef<Variables, Data>.execute(id: String) = execute(Variables(id = id))
  }

  val getPrimitive =
    dataConnect.query(
      operationName = "getPrimitive",
      variablesSerializer = serializer<GetPrimitiveQuery.Variables>(),
      dataDeserializer = serializer<GetPrimitiveQuery.Data>()
    )

  @Serializable
  data class PrimitiveListData(
    val id: String,
    val idListNullable: List<String?>,
    val intList: List<Int>,
    val intListNullable: List<Int?>,
    val floatList: List<Float>,
    val floatListNullable: List<Float?>,
    val booleanList: List<Boolean>,
    val booleanListNullable: List<Boolean?>,
    val stringList: List<String>,
    val stringListNullable: List<String?>,
  )

  object CreatePrimitiveListMutation {
    @Serializable data class Variables(val data: PrimitiveListData)
  }

  val createPrimitiveList =
    dataConnect.mutation(
      operationName = "createPrimitiveList",
      variablesSerializer = serializer<CreatePrimitiveListMutation.Variables>(),
      dataDeserializer = serializer<Unit>()
    )

  object GetPrimitiveListQuery {
    @Serializable data class Variables(val id: String)

    @Serializable data class Data(val primitiveList: PrimitiveListData?)

    suspend fun QueryRef<Variables, Data>.execute(id: String) = execute(Variables(id = id))
  }

  val getPrimitiveList =
    dataConnect.query(
      operationName = "getPrimitiveList",
      variablesSerializer = serializer<GetPrimitiveListQuery.Variables>(),
      dataDeserializer = serializer<GetPrimitiveListQuery.Data>()
    )

  object GetAllPrimitiveListsQuery {
    @Serializable data class Data(val primitiveLists: List<PrimitiveListData>)

    suspend fun QueryRef<Unit, Data>.execute() = execute(Unit)
  }

  val getAllPrimitiveLists =
    dataConnect.query(
      operationName = "getAllPrimitiveLists",
      variablesSerializer = serializer<Unit>(),
      dataDeserializer = serializer<GetAllPrimitiveListsQuery.Data>()
    )

  companion object {
    const val OPERATION_SET = "ops"

    suspend fun TestDataConnectFactory.installAllTypesSchema(): AllTypesSchema =
      AllTypesSchema(newInstance(operationSet = OPERATION_SET)).apply { installEmulatorSchema() }
  }
}
