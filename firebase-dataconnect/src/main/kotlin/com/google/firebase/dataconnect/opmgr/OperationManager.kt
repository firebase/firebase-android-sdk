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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule

internal class OperationManager(
  requestName: String,
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

  private val mutex = Mutex()
  private var state: State =
    State.New(
      requestName = requestName,
      dataConnectGrpcRPCs = dataConnectGrpcRPCs,
      dataConnectAuth = dataConnectAuth,
      dataConnectAppCheck = dataConnectAppCheck,
      ioDispatcher = ioDispatcher,
      cpuDispatcher = cpuDispatcher,
      requestIdGenerator = requestIdGenerator,
      cacheSettings = cacheSettings,
      currentTimeMillis = currentTimeMillis,
    )

  suspend fun start() {
    logger.debug { "start() called" }

    val newState: State.New =
      mutex.withLock {
        when (val currentState = this.state) {
          is State.New -> {
            this.state = State.Starting
            currentState
          }
          is State.Started,
          State.Starting ->
            throw IllegalStateException(
              "start() has already been called (state=$currentState) [bn64pmgp98]"
            )
          State.Closed,
          is State.Closing ->
            throw IllegalStateException("start() cannot be called after close() [n7pc2n7xcd]")
        }
      }

    val cacheDb: DataConnectCacheDatabase? =
      newState.cacheSettings?.run {
        val dbLogger = Logger("DataConnectCacheDatabase")
        dbLogger.debug { "created by ${logger.nameWithId}" }
        DataConnectCacheDatabase(dbFile, dbLogger).apply {
          runCatching { initialize() }.onFailure { close() }.getOrThrow()
        }
      }

    val coroutineScope =
      CoroutineScope(
        SupervisorJob() +
          CoroutineName(logger.nameWithId) +
          CoroutineExceptionHandler { context, throwable ->
            logger.warn(throwable) {
              "uncaught exception from a coroutine named ${context[CoroutineName]}: " +
                "$throwable [ekx3ehgakw]"
            }
          }
      )

    val operationExecutor =
      newState.run {
        val operationExecutorLogger = Logger("OperationExecutor")
        operationExecutorLogger.debug { "created by ${logger.nameWithId}" }
        OperationExecutor(
          requestName = requestName,
          dataConnectGrpcRPCs = dataConnectGrpcRPCs,
          dataConnectAuth = dataConnectAuth,
          dataConnectAppCheck = dataConnectAppCheck,
          ioDispatcher = ioDispatcher,
          cpuDispatcher = cpuDispatcher,
          cacheDb = cacheDb,
          currentTimeMillis = currentTimeMillis,
          logger = operationExecutorLogger,
        )
      }

    val startedState = State.Started(coroutineScope, cacheDb, operationExecutor)

    val oldState =
      mutex.withLock {
        val currentState = this.state
        if (currentState == State.Starting) {
          this.state = startedState
        }
        currentState
      }

    when (oldState) {
      State.Starting -> {}
      State.Closed,
      is State.Closing -> startedState.close()
      is State.New,
      is State.Started ->
        throw IllegalStateException(
          "internal error z5snhkxnf2: unexpected value for this.state: $oldState"
        )
    }
  }

  suspend fun close() {
    logger.debug { "close() called" }

    val closingState: State.Closing =
      mutex.withLock {
        when (val currentState = this.state) {
          State.Closed -> return
          is State.Started -> {
            val closingState = State.Closing(currentState)
            this.state = closingState
            closingState
          }
          is State.Closing -> currentState
          is State.New,
          State.Starting -> {
            this.state = State.Closed
            return
          }
        }
      }

    closingState.started.close()

    mutex.withLock {
      when (this.state) {
        State.Closed -> {}
        is State.Closing -> this.state = State.Closed
        is State.New,
        is State.Started,
        State.Starting ->
          throw IllegalStateException(
            "internal error ebdtpd3624: unexpected state: $state " +
              "(expected $closingState or ${State.Closed})"
          )
      }
    }
  }

  private suspend fun State.Started.close() {
    coroutineScope.cancel("close() called")
    coroutineScope.coroutineContext.job.join()
    cacheDb?.close()
  }

  data class ExecuteQueryResult<Data>(
    val data: Data,
    val source: DataSource,
  )

  data class ExecuteMutationResult<Data>(
    val data: Data,
  )

  suspend fun <Data, Variables> executeMutation(
    operationName: String,
    variables: Variables,
    dataDeserializer: DeserializationStrategy<Data>,
    variablesSerializer: SerializationStrategy<Variables>,
    dataSerializersModule: SerializersModule?,
    variablesSerializersModule: SerializersModule?,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): ExecuteMutationResult<Data> = withOperationExecutor { operationExecutor ->
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

  private suspend inline fun <T> withOperationExecutor(block: suspend (OperationExecutor) -> T): T {
    val currentState = mutex.withLock { this.state }

    val operationExecutor: OperationExecutor =
      when (currentState) {
        is State.Started -> currentState.operationExecutor
        State.Closed,
        is State.Closing -> throw IllegalStateException("close() has been called [cxnbhseye5]")
        is State.New -> throw IllegalStateException("start() has not yet been called [ytcf8se5r2]")
        State.Starting -> throw IllegalStateException("start() has not yet completed [wk7tkwf5v7]")
      }

    return block(operationExecutor)
  }

  private sealed interface State {
    data class New(
      val requestName: String,
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

    data object Starting : State {
      override fun toString() = "OperationManager.State.Starting"
    }

    data class Started(
      val coroutineScope: CoroutineScope,
      val cacheDb: DataConnectCacheDatabase?,
      val operationExecutor: OperationExecutor,
    ) : State {
      override fun toString() = "OperationManager.State.Started"
    }

    data class Closing(val started: Started) : State {
      override fun toString() = "OperationManager.State.Closing"
    }

    data object Closed : State {
      override fun toString() = "OperationManager.State.Closed"
    }
  }

  data class CacheSettings(val dbFile: File?, val maxAge: Duration)
}

internal suspend fun <Data, Variables> OperationManager.execute(
  ref: MutationRef<Data, Variables>,
): OperationManager.ExecuteMutationResult<Data> =
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
