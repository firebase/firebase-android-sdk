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
@file:OptIn(com.google.firebase.dataconnect.ExperimentalFirebaseDataConnect::class)

package com.google.firebase.dataconnect.core

import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.MutationRef
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.core.LoggerGlobals.warn
import com.google.firebase.dataconnect.util.DeserializeUtils.deserialize
import com.google.firebase.dataconnect.util.RequestIdGenerator
import google.firebase.dataconnect.proto.ExecuteMutationRequest as ExecuteMutationRequestProto
import google.firebase.dataconnect.proto.ExecuteMutationResponse as ExecuteMutationResponseProto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule

internal class MutationManager(
  private val connectorResourceName: String,
  private val dataConnectGrpcRPCs: DataConnectGrpcRPCs,
  private val dataConnectAuth: DataConnectAuth,
  private val dataConnectAppCheck: DataConnectAppCheck,
  private val cpuDispatcher: CoroutineDispatcher,
  private val requestIdGenerator: RequestIdGenerator,
  private val logger: Logger,
) {

  suspend fun <Data, Variables> execute(
    operationName: String,
    variables: Variables,
    dataDeserializer: DeserializationStrategy<Data>,
    variablesSerializer: SerializationStrategy<Variables>,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
    dataSerializersModule: SerializersModule?,
    variablesSerializersModule: SerializersModule?,
  ): Data {
    val requestId = requestIdGenerator.nextMutationRequestId()
    logger.debug {
      "[rid=$requestId] Executing mutation with " +
        "operationName=$operationName and variables=$variables"
    }

    val requestProto: ExecuteMutationRequestProto =
      withContext(cpuDispatcher) {
        val variablesStruct =
          encodeVariables(variables, variablesSerializer, variablesSerializersModule)
        ExecuteMutationRequestProto.newBuilder()
          .setName(connectorResourceName)
          .setOperationName(operationName)
          .setVariables(variablesStruct)
          .build()
      }

    val response: ExecuteMutationResponseProto =
      retryOnGrpcUnauthenticatedError(
        requestId = requestId,
        getAuthToken = { dataConnectAuth.getToken(requestId) },
        getAppCheckToken = { dataConnectAppCheck.getToken(requestId) },
        forceRefreshTokens = {
          // TODO: Deduplicate forceRefresh() calls with other parallel calls
          dataConnectAuth.forceRefresh()
          dataConnectAppCheck.forceRefresh()
        },
        logger,
      ) { authTokenResult, appCheckTokenResult ->
        dataConnectGrpcRPCs.executeMutation(
          requestId = requestId,
          requestProto = requestProto,
          authToken = authTokenResult?.token,
          appCheckToken = appCheckTokenResult?.token,
          callerSdkType = callerSdkType,
        )
      }

    val dataResult =
      withContext(cpuDispatcher) {
        response.runCatching { deserialize(dataDeserializer, dataSerializersModule) }
      }

    dataResult.onFailure { logger.warn(it) { "[rid=$requestId] decoding response data failed" } }

    return dataResult.getOrThrow()
  }
}

internal suspend fun <Data, Variables> MutationManager.execute(
  ref: MutationRef<Data, Variables>
): Data =
  ref.run {
    execute(
      operationName = operationName,
      variables = variables,
      dataDeserializer = dataDeserializer,
      variablesSerializer = variablesSerializer,
      callerSdkType = callerSdkType,
      dataSerializersModule = dataSerializersModule,
      variablesSerializersModule = variablesSerializersModule,
    )
  }
