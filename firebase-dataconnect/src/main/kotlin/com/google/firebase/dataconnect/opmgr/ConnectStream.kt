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
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabase
import com.google.firebase.dataconnect.util.RequestIdGenerator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule

internal class ConnectStream(
  private val requestName: String,
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
  ): OperationManager.ExecuteMutationResult<Data> {
    TODO()
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
    TODO()
  }
}
