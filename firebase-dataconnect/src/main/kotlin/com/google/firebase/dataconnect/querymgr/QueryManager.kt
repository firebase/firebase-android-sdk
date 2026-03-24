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

package com.google.firebase.dataconnect.querymgr

import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.core.DataConnectGrpcClientGlobals.deserialize
import com.google.firebase.dataconnect.core.DataConnectGrpcRPCs
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.util.DeserializeUtils.deserialize
import com.google.firebase.dataconnect.util.ImmutableByteArray
import com.google.firebase.dataconnect.util.ProtoUtil.calculateSha512
import com.google.firebase.dataconnect.util.ProtoUtil.encodeToStruct
import com.google.firebase.dataconnect.util.RequestIdGenerator.nextQueryRequestId
import google.firebase.dataconnect.proto.ExecuteQueryRequest as ExecuteQueryRequestProto
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule

internal class QueryManager(
  private val requestName: String,
  private val dataConnectGrpcRPCs: DataConnectGrpcRPCs,
  private val cpuBoundDispatcher: CoroutineDispatcher,
  private val secureRandom: Random,
  private val logger: Logger,
) {

  private val mutex = Mutex()
  private val localQueries = mutableMapOf<LocalKey<*>, LocalQueryState<*>>()

  suspend fun <Data, Variables> execute(
    operationName: String,
    variables: Variables,
    dataDeserializer: DeserializationStrategy<Data>,
    variablesSerializer: SerializationStrategy<Variables>,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
    dataSerializersModule: SerializersModule?,
    variablesSerializersModule: SerializersModule?,
    fetchPolicy: QueryRef.FetchPolicy,
  ): Data {
    val requestId = secureRandom.nextQueryRequestId()
    logger.debug { "[rid=$requestId] Executing query with operationName=$operationName" }

    val requestProto: ExecuteQueryRequestProto
    val queryId: ImmutableByteArray
    withContext(cpuBoundDispatcher) {
      val variablesStruct =
        encodeToStruct(variables, variablesSerializer, variablesSerializersModule)
      queryId = variablesStruct.calculateSha512(preamble = operationName)
      requestProto =
        ExecuteQueryRequestProto.newBuilder()
          .setName(requestName)
          .setOperationName(operationName)
          .setVariables(variablesStruct)
          .build()
    }

    val localKey = LocalKey(queryId, dataDeserializer, dataSerializersModule, fetchPolicy)
    val localState: LocalQueryState<Data> =
      mutex.withLock {
        @Suppress("UNCHECKED_CAST")
        localQueries.getOrPut(localKey) {
          LocalQueryState(requestProto, dataDeserializer, dataSerializersModule, fetchPolicy)
        } as LocalQueryState<Data>
      }

    val response =
      dataConnectGrpcRPCs.executeQuery(
        requestId = requestId,
        requestProto = localState.requestProto,
        authToken = null,
        appCheckToken = null,
        callerSdkType = callerSdkType,
      )

    return withContext(cpuBoundDispatcher) {
      response.deserialize(localState.dataDeserializer, localState.dataSerializersModule)
    }
  }

  data class LocalKey<Data>(
    val queryId: ImmutableByteArray,
    val dataDeserializer: DeserializationStrategy<Data>,
    val dataSerializersModule: SerializersModule?,
    val fetchPolicy: QueryRef.FetchPolicy,
  )
}

private data class LocalQueryState<Data>(
  val requestProto: ExecuteQueryRequestProto,
  val dataDeserializer: DeserializationStrategy<Data>,
  val dataSerializersModule: SerializersModule?,
  val fetchPolicy: QueryRef.FetchPolicy,
)
