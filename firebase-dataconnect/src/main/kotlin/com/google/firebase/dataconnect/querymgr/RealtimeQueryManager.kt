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

import com.google.firebase.dataconnect.DataSource
import com.google.firebase.dataconnect.ExperimentalRealtimeQueries
import com.google.firebase.dataconnect.FirebaseDataConnect.CallerSdkType
import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.core.DataConnectBidiConnectStream
import com.google.firebase.dataconnect.core.DataConnectGrpcClient
import com.google.firebase.dataconnect.core.DataConnectSerialization
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.util.CoroutineUtils.createChildSupervisorScope
import com.google.firebase.dataconnect.util.IdStringGenerator
import com.google.firebase.dataconnect.util.ImmutableByteArray
import com.google.firebase.dataconnect.util.ProtoUtil.calculateSha512
import com.google.firebase.dataconnect.util.update
import com.google.protobuf.Struct
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule

@ExperimentalRealtimeQueries
internal class RealtimeQueryManager(
  private val grpcClient: DataConnectGrpcClient,
  coroutineScope: CoroutineScope,
  private val idStringGenerator: IdStringGenerator,
  private val serialization: DataConnectSerialization,
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
        logger.debug { "scope job is done; setting state to Closed" }
        state.set(State.Closing)
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
  ): Flow<Result<Data>> {
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

    return operationResultFlow.map {
      runCatching {
        serialization.decodeData(
          it.data,
          it.errors,
          dataDeserializer,
          dataSerializersModule,
        )
      }
    }
  }

  private suspend fun subscribe(
    operationName: String,
    variables: Struct,
    callerSdkType: CallerSdkType,
  ): Flow<DataConnectGrpcClient.OperationResult> {
    val requestId = idStringGenerator.next("sub")
    val connection = ensureConnected(requestId, callerSdkType)

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
  ): Flow<DataConnectGrpcClient.OperationResult> {
    // calculateSha512() is a CPU intensive operation that should NOT be performed on the main
    // thread. This is the first reason why this method assumes it's running in this.coroutineScope.
    val queryId = variables.calculateSha512(preamble = operationName)

    // Acquiring the lock by an arbitrary thread could result in priority inversion. This is the
    // second reason why this method assumes it's running in this.coroutineScope: control over the
    // thread that acquires the lock.
    mutex.withLock {
      return flowByQueryId.getOrPut(queryId) {
        val executeResponseFlow = stream.subscribe(requestId, operationName, variables)

        executeResponseFlow.map { executeResponse ->
          DataConnectGrpcClient.OperationResult(
            data = executeResponse.data,
            errors = executeResponse.errors,
            source = DataSource.SERVER,
          )
        }
      }
    }
  }

  private suspend fun ensureConnected(
    requestId: String,
    callerSdkType: CallerSdkType
  ): State.Connected {
    while (true) {
      val currentState = state.get()

      val newState =
        when (currentState) {
          State.Disconnected ->
            State.Connecting(
              coroutineScope.async(start = CoroutineStart.LAZY) {
                grpcClient.connect(
                  streamId = idStringGenerator.next("con"),
                  requestId = requestId,
                  callerSdkType = callerSdkType,
                )
              }
            )
          is State.Connecting -> {
            val stream = currentState.job.await()
            State.Connected(stream)
          }
          is State.Connected -> return currentState
          State.Closing,
          State.Closed -> throw CancellationException("RealtimeQueryManager has been closed")
        }

      state.compareAndSet(currentState, newState)
    }
  }

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
        MutableMap<ImmutableByteArray, Flow<DataConnectGrpcClient.OperationResult>> =
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

@OptIn(ExperimentalRealtimeQueries::class)
internal suspend fun <Data, Variables> RealtimeQueryManager.subscribe(
  queryRef: QueryRef<Data, Variables>
): Flow<Result<Data>> =
  subscribe(
    queryRef.operationName,
    queryRef.variables,
    queryRef.dataDeserializer,
    queryRef.variablesSerializer,
    queryRef.callerSdkType,
    queryRef.dataSerializersModule,
    queryRef.variablesSerializersModule,
  )
