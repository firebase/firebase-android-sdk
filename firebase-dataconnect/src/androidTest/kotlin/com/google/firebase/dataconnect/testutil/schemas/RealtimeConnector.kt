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

import com.google.firebase.dataconnect.ConnectorConfig
import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.serializers.UUIDSerializer
import com.google.firebase.dataconnect.testutil.TestDataConnectFactory
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

class RealtimeConnector private constructor(val dataConnect: FirebaseDataConnect) {

  val getStringByKey = GetStringByKeyQuery(this)

  val insertString = InsertStringMutation(this)

  val updateString = UpdateStringMutation(this)

  val deleteString = DeleteStringMutation(this)

  suspend fun getString(key: Key) = getStringByKey.execute(key)

  suspend fun insertString(name: String) = insertString.execute(name)

  suspend fun updateString(key: Key, name: String) = updateString.execute(key, name)

  suspend fun deleteString(key: Key) = deleteString.execute(key)

  class GetStringByKeyQuery(val connector: RealtimeConnector) {

    suspend fun execute(key: Key) = execute(Variables(key))

    suspend fun execute(variables: Variables) = queryRef(variables).execute().data.item

    fun queryRef(variables: Variables) =
      connector.dataConnect.query(OPERATION_NAME, variables, serializer<Data>(), serializer())

    @Serializable data class Variables(val key: Key)

    @Serializable
    data class Data(val item: Item?) {
      @Serializable data class Item(val name: String)
    }

    companion object {
      const val OPERATION_NAME = "RealtimeString_GetByKey"
    }
  }

  class InsertStringMutation(val connector: RealtimeConnector) {
    @Serializable data class Variables(val name: String)
    @Serializable data class Data(val key: Key)

    suspend fun execute(name: String) = execute(Variables(name = name))

    suspend fun execute(variables: Variables) = mutationRef(variables).execute().data.key

    fun mutationRef(variables: Variables) =
      connector.dataConnect.mutation(OPERATION_NAME, variables, serializer<Data>(), serializer())

    companion object {
      const val OPERATION_NAME = "RealtimeString_Insert"
    }
  }

  class UpdateStringMutation(val connector: RealtimeConnector) {
    @Serializable data class Variables(val key: Key, val name: String)

    suspend fun execute(key: Key, name: String) = execute(Variables(key = key, name = name))

    suspend fun execute(variables: Variables) {
      mutationRef(variables).execute()
    }

    fun mutationRef(variables: Variables) =
      connector.dataConnect.mutation(OPERATION_NAME, variables, serializer<Unit>(), serializer())

    companion object {
      const val OPERATION_NAME = "RealtimeString_Update"
    }
  }

  class DeleteStringMutation(val connector: RealtimeConnector) {
    @Serializable data class Variables(val key: Key)

    suspend fun execute(key: Key) = execute(Variables(key = key))

    suspend fun execute(variables: Variables) {
      mutationRef(variables).execute()
    }

    fun mutationRef(variables: Variables) =
      connector.dataConnect.mutation(OPERATION_NAME, variables, serializer<Unit>(), serializer())

    companion object {
      const val OPERATION_NAME = "RealtimeString_Delete"
    }
  }

  @Serializable data class Key(val id: @Serializable(with = UUIDSerializer::class) UUID)

  companion object {
    val config =
      ConnectorConfig(
        connector = "realtime",
        location = "us-central1",
        serviceId = "sid2ehn9ct8te",
      )

    fun getInstance(dataConnectFactory: TestDataConnectFactory): RealtimeConnector {
      val dataConnect = dataConnectFactory.newInstance(config)
      return RealtimeConnector(dataConnect)
    }
  }
}
