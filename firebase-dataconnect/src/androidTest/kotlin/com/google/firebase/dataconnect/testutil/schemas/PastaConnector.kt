/*
 * Copyright 2026 Google LLC
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
import com.google.firebase.dataconnect.MutationRef
import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.serializers.UUIDSerializer
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

class PastaConnector(val dataConnect: FirebaseDataConnect) {

  inner class Refs {

    fun insert(name: String): MutationRef<Data.Insert, Variables.Insert> =
      dataConnect.mutation(
        operationName = "Pasta_Insert",
        variables = Variables.Insert(name),
        dataDeserializer = serializer<Data.Insert>(),
        variablesSerializer = serializer(),
      )

    fun update(key: Key, name: String): MutationRef<Unit, Variables.Update> =
      dataConnect.mutation(
        operationName = "Pasta_Update",
        variables = Variables.Update(key, name),
        dataDeserializer = serializer<Unit>(),
        variablesSerializer = serializer(),
      )

    fun getByKey(key: Key): QueryRef<Data.Get, Variables.GetByKey> =
      dataConnect.query(
        operationName = "Pasta_GetByKey",
        variables = Variables.GetByKey(key),
        dataDeserializer = serializer<Data.Get>(),
        variablesSerializer = serializer(),
      )
  }

  val refs = Refs()

  suspend fun insert(name: String): Key = refs.insert(name).execute().data.key

  suspend fun update(key: Key, name: String) {
    refs.update(key, name).execute()
  }

  suspend fun getNameByKey(key: Key): String? = refs.getByKey(key).execute().data.item?.name

  object Variables {

    @Serializable data class GetByKey(val key: Key)

    @Serializable data class Insert(val name: String)

    @Serializable data class Update(val key: Key, val name: String)
  }

  object Data {

    @Serializable data class Insert(val key: Key)

    @Serializable
    data class Get(val item: Item?) {
      @Serializable data class Item(val name: String)
    }
  }

  @Serializable data class Key(val id: @Serializable(with = UUIDSerializer::class) UUID)

  companion object {
    const val CONNECTOR_NAME = "pasta"
  }
}
