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
import com.google.firebase.dataconnect.core.LoggerGlobals.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.core.LoggerGlobals.warn
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabase
import com.google.firebase.dataconnect.util.RequestIdGenerator
import java.io.File
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
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

  private val state =
    MutableStateFlow<State>(
      State.New(
        dataConnectGrpcRPCs = dataConnectGrpcRPCs,
        dataConnectAuth = dataConnectAuth,
        dataConnectAppCheck = dataConnectAppCheck,
        ioDispatcher = ioDispatcher,
        cpuDispatcher = cpuDispatcher,
        requestIdGenerator = requestIdGenerator,
        cacheSettings = cacheSettings,
        currentTimeMillis = currentTimeMillis,
      )
    )

  suspend fun <Data, Variables> executeMutation(
    operationName: String,
    variables: Variables,
    dataDeserializer: DeserializationStrategy<Data>,
    variablesSerializer: SerializationStrategy<Variables>,
    dataSerializersModule: SerializersModule?,
    variablesSerializersModule: SerializersModule?,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): Data = withOperationExecutor { operationExecutor ->
    operationExecutor.executeMutation(
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
  ): ExecuteQueryResult<Data> = withOperationExecutor { operationExecutor ->
    operationExecutor.executeQuery(
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
  ): Flow<Result<ExecuteQueryResult<Data>>> = withOperationExecutor { operationExecutor ->
    operationExecutor.subscribeQuery(
      operationName = operationName,
      variables = variables,
      dataDeserializer = dataDeserializer,
      variablesSerializer = variablesSerializer,
      dataSerializersModule = dataSerializersModule,
      variablesSerializersModule = variablesSerializersModule,
      callerSdkType = callerSdkType,
    )
  }

  suspend fun close() {
    logger.debug { "close() called" }

    while (true) {
      val currentState = state.value

      val newState =
        when (currentState) {
          State.Closed -> return
          is State.New -> State.Closed
          is State.Starting -> State.Closing(currentState.coroutineScope, currentState.cacheDb)
          is State.Started -> State.Closing(currentState.coroutineScope, currentState.cacheDb)
          is State.Closing -> {
            currentState.coroutineScope.cancel("close() called")
            currentState.coroutineScope.coroutineContext.job.join()
            currentState.cacheDb?.close()
            State.Closed
          }
        }

      state.compareAndSet(currentState, newState)
    }
  }

  private suspend fun ensureStarted(): State.Started {
    while (true) {
      when (val currentState = state.value) {
        is State.New -> state.compareAndSet(currentState, currentState.toStartingState())
        is State.Starting -> state.compareAndSet(currentState, currentState.job.await())
        is State.Started -> return currentState
        is State.Closing,
        State.Closed -> throw IllegalStateException("close() has been called [vpyvbt2k9z]")
      }
    }
  }

  private suspend inline fun <T> withOperationExecutor(block: suspend (OperationExecutor) -> T): T {
    val startedState = ensureStarted()
    return block(startedState.operationExecutor)
  }

  private sealed interface State {
    data class New(
      val dataConnectGrpcRPCs: DataConnectGrpcRPCs,
      val dataConnectAuth: DataConnectAuth,
      val dataConnectAppCheck: DataConnectAppCheck,
      val ioDispatcher: CoroutineDispatcher,
      val cpuDispatcher: CoroutineDispatcher,
      val requestIdGenerator: RequestIdGenerator,
      val cacheSettings: CacheSettings?,
      val currentTimeMillis: () -> Long,
    ) : State {
      override fun toString() = "OperationManager.State.New"
    }

    data class Starting(
      val coroutineScope: CoroutineScope,
      val cacheDb: DataConnectCacheDatabase?,
      val job: Deferred<Started>,
    ) : State {
      override fun toString() = "OperationManager.State.Starting"
    }

    data class Started(
      val coroutineScope: CoroutineScope,
      val cacheDb: DataConnectCacheDatabase?,
      val operationExecutor: OperationExecutor,
    ) : State {
      override fun toString() = "OperationManager.State.Started"
    }

    data class Closing(
      val coroutineScope: CoroutineScope,
      val cacheDb: DataConnectCacheDatabase?,
    ) : State {
      override fun toString() = "OperationManager.State.Closing"
    }

    data object Closed : State {
      override fun toString() = "OperationManager.State.Closed"
    }
  }

  data class CacheSettings(val dbFile: File?, val maxAge: Duration)

  private fun State.New.toStartingState(): State.Starting {
    val cacheDb: DataConnectCacheDatabase? =
      cacheSettings?.run {
        val dbLogger = Logger("DataConnectCacheDatabase")
        dbLogger.debug { "created by ${logger.nameWithId}" }
        DataConnectCacheDatabase(dbFile, dbLogger)
      }

    val coroutineScope =
      CoroutineScope(
        SupervisorJob() +
          CoroutineName(logger.nameWithId) +
          cpuDispatcher +
          CoroutineExceptionHandler { context, throwable ->
            logger.warn(throwable) {
              "uncaught exception from a coroutine named ${context[CoroutineName]?.name}: " +
                "$throwable [ekx3ehgakw]"
            }
          }
      )

    val job =
      coroutineScope.async(CoroutineName("OperationManager start"), start = CoroutineStart.LAZY) {
        cacheDb?.initialize()

        val operationExecutor = run {
          val operationExecutorLogger = Logger("OperationExecutor")
          operationExecutorLogger.debug { "created by ${logger.nameWithId}" }
          OperationExecutor(
            dataConnectGrpcRPCs = dataConnectGrpcRPCs,
            dataConnectAuth = dataConnectAuth,
            dataConnectAppCheck = dataConnectAppCheck,
            ioDispatcher = ioDispatcher,
            cpuDispatcher = cpuDispatcher,
            requestIdGenerator = requestIdGenerator,
            cacheDb = cacheDb,
            currentTimeMillis = currentTimeMillis,
            logger = operationExecutorLogger,
          )
        }

        State.Started(coroutineScope, cacheDb, operationExecutor)
      }

    return State.Starting(coroutineScope, cacheDb, job)
  }
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
