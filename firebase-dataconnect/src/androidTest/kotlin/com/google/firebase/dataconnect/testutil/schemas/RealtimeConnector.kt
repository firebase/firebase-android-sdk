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
import com.google.firebase.dataconnect.core.DataConnectGrpcRPCs
import com.google.firebase.dataconnect.core.FirebaseDataConnectInternal
import com.google.firebase.dataconnect.serializers.UUIDSerializer
import com.google.firebase.dataconnect.testutil.DataConnectBackend
import com.google.firebase.dataconnect.testutil.TestDataConnectFactory
import io.kotest.assertions.print.print
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

class RealtimeConnector private constructor(dataConnectInternal: FirebaseDataConnectInternal) {

  val dataConnect: FirebaseDataConnect = dataConnectInternal

  internal val dataConnectGrpcRPCs: DataConnectGrpcRPCs by
    lazy(LazyThreadSafetyMode.PUBLICATION) { dataConnectInternal.grpcRPCs }

  val resourceName: String = dataConnectInternal.connectorResourceName

  val getStringByKey = GetStringByKeyQuery(this)

  val insertString = InsertStringMutation(this)

  val updateString = UpdateStringMutation(this)

  val deleteString = DeleteStringMutation(this)

  suspend fun getString(key: Key): GetStringByKeyQuery.Data.Item? = getStringByKey.execute(key)

  suspend fun insertString(name: String): Key = insertString.execute(name)

  suspend fun updateString(key: Key, name: String): Unit = updateString.execute(key, name)

  suspend fun deleteString(key: Key): Unit = deleteString.execute(key)

  class GetStringByKeyQuery(val connector: RealtimeConnector) {

    fun variables(key: Key): Variables = Variables(key)

    suspend fun execute(key: Key): Data.Item? = execute(variables(key))

    suspend fun execute(variables: Variables): Data.Item? = queryRef(variables).execute().data.item

    fun queryRef(key: Key) = queryRef(variables(key))

    fun queryRef(variables: Variables) =
      connector.dataConnect.query(OPERATION_NAME, variables, serializer<Data>(), serializer())

    @Serializable
    data class Variables(val key: Key) {
      constructor(id: UUID) : this(Key(id))
    }

    @Serializable
    data class Data(val item: Item?) {
      constructor(name: String) : this(Item(name))
      @Serializable
      data class Item(val name: String) {
        companion object {
          fun fromNameOrNull(name: String?): Item? = if (name == null) null else Item(name)
        }
      }
    }

    companion object {
      const val OPERATION_NAME = "RealtimeString_GetByKey"
    }
  }

  class InsertStringMutation(val connector: RealtimeConnector) {
    @Serializable data class Variables(val name: String)
    @Serializable data class Data(val key: Key)

    suspend fun execute(name: String): Key = execute(Variables(name = name))

    suspend fun execute(variables: Variables): Key = mutationRef(variables).execute().data.key

    fun mutationRef(variables: Variables) =
      connector.dataConnect.mutation(OPERATION_NAME, variables, serializer<Data>(), serializer())

    companion object {
      const val OPERATION_NAME = "RealtimeString_Insert"
    }
  }

  class UpdateStringMutation(val connector: RealtimeConnector) {
    @Serializable data class Variables(val key: Key, val name: String)

    suspend fun execute(key: Key, name: String): Unit = execute(Variables(key = key, name = name))

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

    suspend fun execute(key: Key): Unit = execute(Variables(key = key))

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

    fun getInstance(
      dataConnectFactory: TestDataConnectFactory,
      backend: DataConnectBackend? = null,
    ): RealtimeConnector {
      val dataConnect = dataConnectFactory.newInstance(config, backend)
      return RealtimeConnector(dataConnect as FirebaseDataConnectInternal)
    }

    fun getInstance(dataConnect: FirebaseDataConnect): RealtimeConnector {
      require(dataConnect.config == config) {
        "The given FirebaseDataConnect has a config that " +
          "does not match the config required for RealtimeConnector: " +
          "actual=${dataConnect.config.print().value}, " +
          "expected=${config.print().value}"
      }
      return RealtimeConnector(dataConnect as FirebaseDataConnectInternal)
    }
  }
}
