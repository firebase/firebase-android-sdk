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

import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.core.DataConnectAppCheck
import com.google.firebase.dataconnect.core.DataConnectAuth
import com.google.firebase.dataconnect.core.DataConnectGrpcRPCs
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.core.encodeVariables
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabase
import com.google.firebase.dataconnect.util.DeserializeUtils.deserialize
import com.google.firebase.dataconnect.util.RequestIdGenerator
import google.firebase.dataconnect.proto.ExecuteMutationRequest
import google.firebase.dataconnect.proto.ExecuteMutationResponse
import google.firebase.dataconnect.proto.StreamRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule

internal class OperationExecutor(
  private val connectorResourceName: String,
  private val dataConnectGrpcRPCs: DataConnectGrpcRPCs,
  private val dataConnectAuth: DataConnectAuth,
  private val dataConnectAppCheck: DataConnectAppCheck,
  private val ioDispatcher: CoroutineDispatcher,
  private val cpuDispatcher: CoroutineDispatcher,
  private val requestIdGenerator: RequestIdGenerator,
  private val cacheDb: DataConnectCacheDatabase?,
  private val currentTimeMillis: () -> Long,
  private val logger: Logger,
) {

  suspend fun <Data, Variables> executeMutation(
    operationName: String,
    variables: Variables,
    dataDeserializer: DeserializationStrategy<Data>,
    variablesSerializer: SerializationStrategy<Variables>,
    dataSerializersModule: SerializersModule?,
    variablesSerializersModule: SerializersModule?,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): Data {
    val requestId = requestIdGenerator.nextMutationRequestId()
    logger.debug {
      "[rid=$requestId] Executing mutation with " +
        "operationName=$operationName and variables=$variables"
    }

    val response: ExecuteMutationResponse = coroutineScope {
      val authTokenJob = async { dataConnectAuth.getToken(requestId) }
      val appCheckTokenJob = async { dataConnectAppCheck.getToken(requestId) }

      val variablesStruct =
        withContext(cpuDispatcher) {
          encodeVariables(variables, variablesSerializer, variablesSerializersModule)
        }

      val requestProto: ExecuteMutationRequest =
        ExecuteMutationRequest.newBuilder()
          .setName(connectorResourceName)
          .setOperationName(operationName)
          .setVariables(variablesStruct)
          .build()

      val authToken: DataConnectAuth.GetAuthTokenResult? = authTokenJob.await()
      val appCheckToken: DataConnectAppCheck.GetAppCheckTokenResult? = appCheckTokenJob.await()

      dataConnectGrpcRPCs.executeMutation(
        requestId = requestId,
        requestProto = requestProto,
        authToken = authToken,
        appCheckToken = appCheckToken,
        callerSdkType = callerSdkType,
      )
    }

    return withContext(cpuDispatcher) {
      response.deserialize(dataDeserializer, dataSerializersModule)
    }
  }

  suspend fun <Data, Variables> executeQuery(
    operationName: String,
    variables: Variables,
    dataDeserializer: DeserializationStrategy<Data>,
    variablesSerializer: SerializationStrategy<Variables>,
    dataSerializersModule: SerializersModule?,
    variablesSerializersModule: SerializersModule?,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
    fetchPolicy: QueryRef.FetchPolicy,
  ): OperationManager.ExecuteQueryResult<Data> {
    val requestId = requestIdGenerator.nextQueryRequestId()
    logger.debug {
      "[rid=$requestId] Executing query with " +
        "operationName=$operationName and variables=$variables"
    }

    val authUid = dataConnectAuth.getToken(requestId)?.authUid

    TODO()
  }

  suspend fun <Data, Variables> subscribeQuery(
    operationName: String,
    variables: Variables,
    dataDeserializer: DeserializationStrategy<Data>,
    variablesSerializer: SerializationStrategy<Variables>,
    dataSerializersModule: SerializersModule?,
    variablesSerializersModule: SerializersModule?,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): Flow<Result<OperationManager.ExecuteQueryResult<Data>>> {
    val requestId = requestIdGenerator.nextQuerySubscriptionId()
    logger.debug {
      "[rid=$requestId] Subscribing to query with " +
        "operationName=$operationName and variables=$variables"
    }

    val authUid = dataConnectAuth.getToken(requestId)?.authUid

    TODO()
  }

  private fun <Variables> createStreamRequestExecuteProto(
    operationName: String,
    variables: Variables,
    variablesSerializer: SerializationStrategy<Variables>,
    variablesSerializersModule: SerializersModule?,
  ): StreamRequest.Execute {
    val variablesStruct =
      encodeVariables(variables, variablesSerializer, variablesSerializersModule)

    return StreamRequest.Execute.newBuilder()
      .setOperationName(operationName)
      .setVariables(variablesStruct)
      .build()
  }
}
