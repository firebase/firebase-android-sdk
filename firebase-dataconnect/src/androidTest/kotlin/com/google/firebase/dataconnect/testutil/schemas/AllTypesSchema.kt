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

class AllTypesSchema(val dataConnect: FirebaseDataConnect) {

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
    // NOTE: GraphQL "Float" type is a "signed double-precision floating-point value", which is
    // equivalent to Java and Kotlin's `Double` type.
    val floatField: Double,
    val floatFieldNullable: Double?,
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
    val idListNullable: List<String>?,
    val idListOfNullable: List<String?>,
    val intList: List<Int>,
    val intListNullable: List<Int>?,
    val intListOfNullable: List<Int?>,
    // NOTE: GraphQL "Float" type is a "signed double-precision floating-point value", which is
    // equivalent to Java and Kotlin's `Double` type.
    val floatList: List<Double>,
    val floatListNullable: List<Double>?,
    val floatListOfNullable: List<Double?>,
    val booleanList: List<Boolean>,
    val booleanListNullable: List<Boolean>?,
    val booleanListOfNullable: List<Boolean?>,
    val stringList: List<String>,
    val stringListNullable: List<String>?,
    val stringListOfNullable: List<String?>,
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

  object CreateFarmerMutation {
    @Serializable data class Variables(val data: Farmer)

    @Serializable
    data class Farmer(
      val id: String,
      val name: String,
      val parentId: String?,
    )
  }

  val createFarmer =
    dataConnect.mutation(
      operationName = "createFarmer",
      variablesSerializer = serializer<CreateFarmerMutation.Variables>(),
      dataDeserializer = serializer<Unit>()
    )

  suspend fun createFarmer(id: String, name: String, parentId: String?) =
    createFarmer.execute(
      CreateFarmerMutation.Variables(
        CreateFarmerMutation.Farmer(id = id, name = name, parentId = parentId)
      )
    )

  object CreateFarmMutation {
    @Serializable data class Variables(val data: Farm)

    @Serializable data class Farm(val id: String, val name: String, val farmerId: String?)
  }

  val createFarm =
    dataConnect.mutation(
      operationName = "createFarm",
      variablesSerializer = serializer<CreateFarmMutation.Variables>(),
      dataDeserializer = serializer<Unit>()
    )

  suspend fun createFarm(id: String, name: String, farmerId: String?) =
    createFarm.execute(
      CreateFarmMutation.Variables(
        CreateFarmMutation.Farm(id = id, name = name, farmerId = farmerId)
      )
    )

  object CreateAnimalMutation {
    @Serializable data class Variables(val data: Animal)

    @Serializable
    data class Animal(
      val id: String,
      val farmId: String,
      val name: String,
      val species: String,
      val age: Int?
    )
  }

  val createAnimal =
    dataConnect.mutation(
      operationName = "createAnimal",
      variablesSerializer = serializer<CreateAnimalMutation.Variables>(),
      dataDeserializer = serializer<Unit>()
    )

  suspend fun createAnimal(id: String, farmId: String, name: String, species: String, age: Int?) =
    createAnimal.execute(
      CreateAnimalMutation.Variables(
        CreateAnimalMutation.Animal(
          id = id,
          farmId = farmId,
          name = name,
          species = species,
          age = age
        )
      )
    )

  object GetFarmQuery {
    @Serializable data class Variables(val id: String)

    @Serializable data class Data(val farm: Farm?)

    @Serializable
    data class Farm(
      val id: String,
      val name: String,
      val farmer: Farmer,
      val animals: List<Animal>
    )

    @Serializable data class Farmer(val id: String, val name: String, val parent: Parent?)

    @Serializable data class Parent(val id: String, val name: String, val parentId: String?)

    @Serializable
    data class Animal(val id: String, val name: String, val species: String, val age: Int?)

    suspend fun QueryRef<Variables, Data>.execute(id: String) = execute(Variables(id = id))
  }

  val getFarm =
    dataConnect.query(
      operationName = "getFarm",
      variablesSerializer = serializer<GetFarmQuery.Variables>(),
      dataDeserializer = serializer<GetFarmQuery.Data>()
    )

  suspend fun getFarm(id: String) = getFarm.execute(GetFarmQuery.Variables(id = id))

  companion object {
    const val OPERATION_SET = "ops"

    suspend fun TestDataConnectFactory.installAllTypesSchema(): AllTypesSchema =
      AllTypesSchema(newInstance(operationSet = OPERATION_SET)).apply { installEmulatorSchema() }
  }
}
