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

  object CreatePrimitiveMutation {
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
    @Serializable data class Variables(val data: PrimitiveData)

    suspend fun MutationRef<Variables, Unit>.execute(
      id: String,
      idFieldNullable: String?,
      intField: Int,
      intFieldNullable: Int?,
      floatField: Float,
      floatFieldNullable: Float?,
      booleanField: Boolean,
      booleanFieldNullable: Boolean?,
      stringField: String,
      stringFieldNullable: String?,
    ) =
      execute(
        Variables(
          PrimitiveData(
            id = id,
            idFieldNullable = idFieldNullable,
            intField = intField,
            intFieldNullable = intFieldNullable,
            floatField = floatField,
            floatFieldNullable = floatFieldNullable,
            booleanField = booleanField,
            booleanFieldNullable = booleanFieldNullable,
            stringField = stringField,
            stringFieldNullable = stringFieldNullable,
          )
        )
      )
  }

  val createPrimitive =
    dataConnect.mutation(
      operationName = "createPrimitive",
      variablesSerializer = serializer<CreatePrimitiveMutation.Variables>(),
      dataDeserializer = serializer<Unit>()
    )

  object GetPrimitiveQuery {
    @Serializable data class Variables(val id: String)

    @Serializable
    data class Data(val primitive: PrimitiveData?) {
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
    }

    suspend fun QueryRef<Variables, Data>.execute(id: String) = execute(Variables(id = id))
  }

  val getPrimitive =
    dataConnect.query(
      operationName = "getPrimitive",
      variablesSerializer = serializer<GetPrimitiveQuery.Variables>(),
      dataDeserializer = serializer<GetPrimitiveQuery.Data>()
    )

  companion object {
    const val OPERATION_SET = "ops"

    suspend fun TestDataConnectFactory.installAllTypesSchema(): AllTypesSchema =
      AllTypesSchema(newInstance(operationSet = OPERATION_SET)).apply { installEmulatorSchema() }
  }
}
