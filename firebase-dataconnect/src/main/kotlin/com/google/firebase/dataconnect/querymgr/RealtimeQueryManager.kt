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

package com.google.firebase.dataconnect.querymgr

import com.google.firebase.dataconnect.FirebaseDataConnect.CallerSdkType
import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.core.DataConnectBidiConnectStream
import com.google.firebase.dataconnect.core.DataConnectCache
import com.google.firebase.dataconnect.core.DataConnectGrpcClient
import com.google.firebase.dataconnect.core.DataConnectSerialization
import com.google.firebase.dataconnect.core.DataSource
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.core.QueryId
import com.google.firebase.dataconnect.core.calculateQueryId
import com.google.firebase.dataconnect.core.getEntityIdForPathFunction
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabase.SqliteSequenceNumber
import com.google.firebase.dataconnect.sqlite.GetEntityIdForPathFunction
import com.google.firebase.dataconnect.util.CoroutineUtils.createChildSupervisorScope
import com.google.firebase.dataconnect.util.IdStringGenerator
import com.google.firebase.dataconnect.util.TaggedReference
import com.google.firebase.dataconnect.util.map
import com.google.firebase.dataconnect.util.update
import com.google.protobuf.Struct
import java.lang.System.currentTimeMillis
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule

internal class RealtimeQueryManager(
  private val grpcClient: DataConnectGrpcClient,
  coroutineScope: CoroutineScope,
  private val idStringGenerator: IdStringGenerator,
  private val serialization: DataConnectSerialization,
  private val cache: DataConnectCache?,
  private val logger: Logger,
) {

  private var state = AtomicReference<State>(State.Disconnected)

  private val coroutineScope =
    coroutineScope.createChildSupervisorScope(logger).also {
      it.launch(start = CoroutineStart.UNDISPATCHED) {
        try {
          awaitCancellation()
        } finally {
          logger.debug { "cancellation signal received; setting state to Closing" }
          state.update { currentState ->
            if (currentState == State.Closed) currentState else State.Closing
          }
        }
      }

      it.coroutineContext.job.invokeOnCompletion {
        logger.debug { "scope Job is completed; setting state to Closed" }
        state.set(State.Closed)
      }
    }

  suspend fun <Data, Variables> subscribe(
    operationName: String,
    variables: Variables,
    dataDeserializer: DeserializationStrategy<Data>,
    variablesSerializer: SerializationStrategy<Variables>,
    callerSdkType: CallerSdkType,
    dataSerializersModule: SerializersModule?,
    variablesSerializersModule: SerializersModule?,
  ): Flow<Result<TaggedReference<SqliteSequenceNumber?, Data>>> {
    val variablesStruct: Struct =
      serialization.encodeVariables(
        variables,
        variablesSerializer,
        variablesSerializersModule,
      )

    val operationResultFlow =
      subscribe(
        operationName = operationName,
        variablesStruct,
        callerSdkType,
      )

    return operationResultFlow.map { taggedOperationResult ->
      runCatching {
        taggedOperationResult.map {
          serialization.decodeData(
            it.data,
            it.errors,
            dataDeserializer,
            dataSerializersModule,
          )
        }
      }
    }
  }

  private suspend fun subscribe(
    operationName: String,
    variables: Struct,
    callerSdkType: CallerSdkType,
  ): Flow<TaggedReference<SqliteSequenceNumber?, DataConnectGrpcClient.OperationResult>> {
    val requestId = idStringGenerator.next("sub")
    val connection = ensureConnected(requestId, callerSdkType) ?: return emptyFlow()

    val coroutineName = "${logger.nameWithId}-subscribe(rid=$requestId)[ecpvdvmzvj]"
    val job =
      coroutineScope.async(CoroutineName(coroutineName)) {
        connection.subscribe(requestId = requestId, operationName = operationName, variables)
      }

    return job.await()
  }

  // NOTE: This method MUST be called on a coroutine running in this.coroutineScope.
  private suspend fun State.Connected.subscribe(
    requestId: String,
    operationName: String,
    variables: Struct,
  ): Flow<TaggedReference<SqliteSequenceNumber?, DataConnectGrpcClient.OperationResult>> {
    // calculateQueryId() is a CPU intensive operation that should NOT be performed on the main
    // thread. This is the first reason why this method assumes it's running in this.coroutineScope.
    val queryId = calculateQueryId(operationName, variables)

    // Acquiring the lock by an arbitrary thread could result in priority inversion. This is the
    // second reason why this method assumes it's running in this.coroutineScope: control over the
    // thread that acquires the lock.
    mutex.withLock {
      return flowByQueryId.getOrPut(queryId) {
        stream
          .subscribe(requestId, operationName, variables)
          .updateCache(cache, queryId)
          .mapToOperationResponse()
      }
    }
  }

  private suspend fun ensureConnected(
    requestId: String,
    callerSdkType: CallerSdkType
  ): State.Connected? {
    var connectionAttempted = false

    while (true) {
      when (val currentState = state.get()) {
        State.Disconnected -> {
          val newState: State.Connecting = createConnectingState(requestId, callerSdkType)
          state.compareAndSet(currentState, newState)
        }
        is State.Connecting -> {
          val connectResult = currentState.job.runCatching { await() }
          val newState = connectResult.map(State::Connected).getOrElse { State.Disconnected }
          state.compareAndSet(currentState, newState)
          connectResult.onFailure { exception ->
            if (connectionAttempted) {
              throw exception
            }
            connectionAttempted = true
          }
        }
        is State.Connected -> return currentState
        State.Closing,
        State.Closed -> return null
      }
    }
  }

  private fun createConnectingState(
    requestId: String,
    callerSdkType: CallerSdkType,
  ) =
    State.Connecting(
      coroutineScope.async(start = CoroutineStart.LAZY) {
        grpcClient.connect(
          requestId = requestId,
          callerSdkType = callerSdkType,
          idStringGenerator = idStringGenerator,
        )
      }
    )

  private sealed interface State {
    object Disconnected : State {
      override fun toString() = "Disconnected"
    }

    class Connecting(val job: Deferred<DataConnectBidiConnectStream>) : State {
      override fun toString() = "Connecting"
    }

    class Connected(val stream: DataConnectBidiConnectStream) : State {
      val mutex = Mutex()
      val flowByQueryId:
        MutableMap<
          QueryId,
          Flow<TaggedReference<SqliteSequenceNumber?, DataConnectGrpcClient.OperationResult>>
        > =
        mutableMapOf()
      override fun toString() = "Connected"
    }

    sealed interface TerminatingState : State

    object Closing : TerminatingState {
      override fun toString() = "Closing"
    }

    object Closed : TerminatingState {
      override fun toString() = "Closed"
    }
  }
}

internal suspend fun <Data, Variables> RealtimeQueryManager.subscribe(
  queryRef: QueryRef<Data, Variables>
): Flow<Result<TaggedReference<SqliteSequenceNumber?, Data>>> =
  subscribe(
    queryRef.operationName,
    queryRef.variables,
    queryRef.dataDeserializer,
    queryRef.variablesSerializer,
    queryRef.callerSdkType,
    queryRef.dataSerializersModule,
    queryRef.variablesSerializersModule,
  )

private fun Flow<
  TaggedReference<SqliteSequenceNumber?, DataConnectBidiConnectStream.ExecuteResponse>
>
  .mapToOperationResponse():
  Flow<TaggedReference<SqliteSequenceNumber?, DataConnectGrpcClient.OperationResult>> =
  map { taggedExecuteResponse ->
    taggedExecuteResponse.map { executeResponse ->
      DataConnectGrpcClient.OperationResult(
        data = executeResponse.data,
        errors = executeResponse.errors,
        source = DataSource.Server,
      )
    }
  }

private fun Flow<DataConnectBidiConnectStream.ExecuteResponse>.updateCache(
  cache: DataConnectCache?,
  queryId: QueryId,
): Flow<TaggedReference<SqliteSequenceNumber?, DataConnectBidiConnectStream.ExecuteResponse>> =
  map { response ->
    val data = response.data
    val sqliteSequenceNumber =
      if (cache == null || data == null) {
        null
      } else {
        cache
          .open()
          .insertQueryResult(
            response.authUid,
            queryId,
            data,
            cache.maxAgeProto,
            currentTimeMillis(),
            response.getEntityIdForPathFunction(),
          )
      }
    TaggedReference(sqliteSequenceNumber, response)
  }

@JvmName("getEntityIdForPathFunction_DataConnectBidiConnectStream_ExecuteResponse")
private fun DataConnectBidiConnectStream.ExecuteResponse.getEntityIdForPathFunction():
  GetEntityIdForPathFunction? =
  if (extensions.isEmpty()) {
    null
  } else {
    extensions.getEntityIdForPathFunction()
  }
