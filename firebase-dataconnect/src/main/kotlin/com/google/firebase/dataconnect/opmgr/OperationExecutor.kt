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

package com.google.firebase.dataconnect.opmgr

import com.google.firebase.dataconnect.DataSource
import com.google.firebase.dataconnect.FirebaseDataConnect.CallerSdkType
import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.core.DataConnectAppCheck
import com.google.firebase.dataconnect.core.DataConnectAuth
import com.google.firebase.dataconnect.core.DataConnectGrpcRPCs
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.core.encodeVariables
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabase
import com.google.firebase.dataconnect.util.ImmutableByteArray
import com.google.firebase.dataconnect.util.ObjectLifecycleManager
import com.google.firebase.dataconnect.util.ProtoUtil.calculateSha512
import com.google.firebase.dataconnect.util.RequestIdGenerator
import com.google.firebase.dataconnect.util.open
import com.google.protobuf.Struct
import google.firebase.dataconnect.proto.ExecuteRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule

internal class OperationExecutor(
  dataConnectGrpcRPCs: DataConnectGrpcRPCs,
  dataConnectAuth: DataConnectAuth,
  dataConnectAppCheck: DataConnectAppCheck,
  cacheDb: DataConnectCacheDatabase?,
  currentTimeMillis: () -> Long,
  private val ioDispatcher: CoroutineDispatcher,
  private val cpuDispatcher: CoroutineDispatcher,
  private val requestIdGenerator: RequestIdGenerator,
  private val logger: Logger,
) {

  private val lifecycle =
    ObjectLifecycleManager<DataConnectStream, Unit>(cpuDispatcher, logger) {
      val dataConnectStream =
        DataConnectStream(
          dataConnectGrpcRPCs,
          dataConnectAuth,
          dataConnectAppCheck,
          requestIdGenerator,
          cpuDispatcher,
          Logger("DataConnectStream").also { it.debug { "created by ${logger.nameWithId}" } },
        )
      onClose { dataConnectStream.close() }
      dataConnectStream
    }

  suspend fun close() {
    lifecycle.close()
  }

  suspend fun <Data, Variables> executeMutation(
    operationName: String,
    variables: Variables,
    dataDeserializer: DeserializationStrategy<Data>,
    variablesSerializer: SerializationStrategy<Variables>,
    dataSerializersModule: SerializersModule?,
    variablesSerializersModule: SerializersModule?,
    callerSdkType: CallerSdkType,
  ): Data {
    val dataConnectStream = lifecycle.open()

    val requestId = requestIdGenerator.nextMutationRequestId()
    logger.debug {
      "[rid=$requestId] Executing mutation with operationName=$operationName " +
        "and variables=$variables"
    }

    val variablesStruct =
      withContext(cpuDispatcher) {
        encodeVariables(variables, variablesSerializer, variablesSerializersModule)
      }

    val request =
      ExecuteRequest.newBuilder()
        .setOperationName(operationName)
        .setVariables(variablesStruct)
        .build()

    val response = dataConnectStream.execute(requestId, request, callerSdkType).first()

    val data =
      withContext(cpuDispatcher) { response.deserialize(dataDeserializer, dataSerializersModule) }

    return data
  }

  suspend fun <Data, Variables> executeQuery(
    operationName: String,
    variables: Variables,
    dataDeserializer: DeserializationStrategy<Data>,
    variablesSerializer: SerializationStrategy<Variables>,
    dataSerializersModule: SerializersModule?,
    variablesSerializersModule: SerializersModule?,
    callerSdkType: CallerSdkType,
    fetchPolicy: QueryRef.FetchPolicy,
  ): OperationManager.ExecuteQueryResult<Data> {
    val dataConnectStream = lifecycle.open()

    val requestId = requestIdGenerator.nextQueryRequestId()
    logger.debug {
      "[rid=$requestId] Executing query with operationName=$operationName " +
        "and variables=$variables"
    }

    val variablesStruct =
      withContext(cpuDispatcher) {
        encodeVariables(variables, variablesSerializer, variablesSerializersModule)
      }

    val request =
      ExecuteRequest.newBuilder()
        .setOperationName(operationName)
        .setVariables(variablesStruct)
        .build()

    val response = dataConnectStream.execute(requestId, request, callerSdkType).first()

    val data =
      withContext(cpuDispatcher) { response.deserialize(dataDeserializer, dataSerializersModule) }

    return OperationManager.ExecuteQueryResult(data, DataSource.SERVER)
  }

  suspend fun <Data, Variables> subscribeQuery(
    operationName: String,
    variables: Variables,
    dataDeserializer: DeserializationStrategy<Data>,
    variablesSerializer: SerializationStrategy<Variables>,
    dataSerializersModule: SerializersModule?,
    variablesSerializersModule: SerializersModule?,
    callerSdkType: CallerSdkType,
  ): Flow<Result<OperationManager.ExecuteQueryResult<Data>>> {
    val dataConnectStream = lifecycle.open()

    val requestId = requestIdGenerator.nextQuerySubscriptionId()
    logger.debug {
      "[rid=$requestId] Subscribing to query with operationName=$operationName " +
        "and variables=$variables"
    }

    val variablesStruct =
      withContext(cpuDispatcher) {
        encodeVariables(variables, variablesSerializer, variablesSerializersModule)
      }

    val request =
      ExecuteRequest.newBuilder()
        .setOperationName(operationName)
        .setVariables(variablesStruct)
        .build()

    val responseFlow = dataConnectStream.subscribe(requestId, request, callerSdkType)

    return responseFlow
      .map { it.deserialize(dataDeserializer, dataSerializersModule) }
      .flowOn(cpuDispatcher)
      .map { OperationManager.ExecuteQueryResult(it, DataSource.SERVER) }
      .map { Result.success(it) }
  }
}

private fun calculateQueryId(
  operationName: String,
  variables: Struct,
): ImmutableByteArray = variables.calculateSha512(preamble = operationName)
