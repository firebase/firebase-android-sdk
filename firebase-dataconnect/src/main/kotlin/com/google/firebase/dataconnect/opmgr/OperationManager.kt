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
import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.MutationRef
import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.core.DataConnectAppCheck
import com.google.firebase.dataconnect.core.DataConnectAuth
import com.google.firebase.dataconnect.core.DataConnectGrpcRPCs
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabase
import com.google.firebase.dataconnect.util.ObjectLifecycleManager
import com.google.firebase.dataconnect.util.RequestIdGenerator
import java.io.File
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule

internal class OperationManager(
  dataConnectGrpcRPCs: DataConnectGrpcRPCs,
  dataConnectAuth: DataConnectAuth,
  dataConnectAppCheck: DataConnectAppCheck,
  ioDispatcher: CoroutineDispatcher,
  cpuDispatcher: CoroutineDispatcher,
  requestIdGenerator: RequestIdGenerator,
  cacheSettings: CacheSettings?,
  currentTimeMillis: () -> Long,
  private val logger: Logger,
) {

  private val lifecycle =
    ObjectLifecycleManager(
      openResource = {
        logger.debug { "opening" }

        val cacheDb =
          cacheSettings?.let {
            val cacheDb = DataConnectCacheDatabase(it.dbFile, logger)
            launch { cacheDb.initialize() }
            cacheDb
          }

        val operationExecutor =
          OperationExecutor(
            dataConnectGrpcRPCs,
            dataConnectAuth,
            dataConnectAppCheck,
            cacheDb,
            currentTimeMillis,
            ioDispatcher,
            cpuDispatcher,
            requestIdGenerator,
            logger,
          )

        RunningState(cacheDb, operationExecutor)
      },
      closeResource = {
        it.operationExecutor.close()
        it.cacheDb?.close()
      },
      cpuDispatcher,
      logger,
    )

  suspend fun close() {
    logger.debug { "close() called" }
    lifecycle.close()
  }

  private class RunningState(
    val cacheDb: DataConnectCacheDatabase?,
    val operationExecutor: OperationExecutor,
  )

  suspend fun <Data, Variables> executeMutation(
    operationName: String,
    variables: Variables,
    dataDeserializer: DeserializationStrategy<Data>,
    variablesSerializer: SerializationStrategy<Variables>,
    dataSerializersModule: SerializersModule?,
    variablesSerializersModule: SerializersModule?,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): Data {
    val runningState = lifecycle.open()
    return runningState.operationExecutor.executeMutation(
      operationName = operationName,
      variables = variables,
      dataDeserializer = dataDeserializer,
      variablesSerializer = variablesSerializer,
      dataSerializersModule = dataSerializersModule,
      variablesSerializersModule = variablesSerializersModule,
      callerSdkType = callerSdkType,
    )
  }

  data class ExecuteQueryResult<Data>(
    val data: Data,
    val source: DataSource,
  )

  suspend fun <Data, Variables> executeQuery(
    operationName: String,
    variables: Variables,
    dataDeserializer: DeserializationStrategy<Data>,
    variablesSerializer: SerializationStrategy<Variables>,
    dataSerializersModule: SerializersModule?,
    variablesSerializersModule: SerializersModule?,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
    fetchPolicy: QueryRef.FetchPolicy,
  ): ExecuteQueryResult<Data> {
    val runningState = lifecycle.open()
    return runningState.operationExecutor.executeQuery(
      operationName = operationName,
      variables = variables,
      dataDeserializer = dataDeserializer,
      variablesSerializer = variablesSerializer,
      dataSerializersModule = dataSerializersModule,
      variablesSerializersModule = variablesSerializersModule,
      callerSdkType = callerSdkType,
      fetchPolicy = fetchPolicy,
    )
  }

  suspend fun <Data, Variables> subscribeQuery(
    operationName: String,
    variables: Variables,
    dataDeserializer: DeserializationStrategy<Data>,
    variablesSerializer: SerializationStrategy<Variables>,
    dataSerializersModule: SerializersModule?,
    variablesSerializersModule: SerializersModule?,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): Flow<Result<ExecuteQueryResult<Data>>> {
    val runningState = lifecycle.open()
    return runningState.operationExecutor.subscribeQuery(
      operationName = operationName,
      variables = variables,
      dataDeserializer = dataDeserializer,
      variablesSerializer = variablesSerializer,
      dataSerializersModule = dataSerializersModule,
      variablesSerializersModule = variablesSerializersModule,
      callerSdkType = callerSdkType,
    )
  }

  data class CacheSettings(val dbFile: File?, val maxAge: Duration)
}

internal suspend fun <Data, Variables> OperationManager.execute(
  ref: MutationRef<Data, Variables>,
): Data =
  ref.run {
    executeMutation(
      operationName = operationName,
      variables = variables,
      dataDeserializer = dataDeserializer,
      variablesSerializer = variablesSerializer,
      dataSerializersModule = dataSerializersModule,
      variablesSerializersModule = variablesSerializersModule,
      callerSdkType = callerSdkType,
    )
  }

internal suspend fun <Data, Variables> OperationManager.execute(
  ref: QueryRef<Data, Variables>,
  fetchPolicy: QueryRef.FetchPolicy,
): OperationManager.ExecuteQueryResult<Data> =
  ref.run {
    executeQuery(
      operationName = operationName,
      variables = variables,
      dataDeserializer = dataDeserializer,
      variablesSerializer = variablesSerializer,
      dataSerializersModule = dataSerializersModule,
      variablesSerializersModule = variablesSerializersModule,
      callerSdkType = callerSdkType,
      fetchPolicy = fetchPolicy,
    )
  }

internal suspend fun <Data, Variables> OperationManager.subscribe(
  ref: QueryRef<Data, Variables>,
): Flow<Result<OperationManager.ExecuteQueryResult<Data>>> =
  ref.run {
    subscribeQuery(
      operationName = operationName,
      variables = variables,
      dataDeserializer = dataDeserializer,
      variablesSerializer = variablesSerializer,
      dataSerializersModule = dataSerializersModule,
      variablesSerializersModule = variablesSerializersModule,
      callerSdkType = callerSdkType,
    )
  }
