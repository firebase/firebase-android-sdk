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
import com.google.firebase.dataconnect.core.DataConnectBidiConnectStream
import com.google.firebase.dataconnect.core.DataConnectGrpcClient
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.util.CoroutineUtils.createChildSupervisorScope
import com.google.firebase.dataconnect.util.IdStringGenerator
import com.google.firebase.dataconnect.util.ImmutableByteArray
import com.google.firebase.dataconnect.util.ProtoUtil.calculateSha512
import com.google.protobuf.Struct
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

@ExperimentalRealtimeQueries
internal class RealtimeQueryManager(
  private val grpcClient: DataConnectGrpcClient,
  coroutineScope: CoroutineScope,
  private val idStringGenerator: IdStringGenerator,
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
          println("zzyzx cancellation signal received; setting state to Closing")
          transitionToTerminalState(State.Closing)
        }
      }

      it.coroutineContext.job.invokeOnCompletion {
        logger.debug { "scope job is done; setting state to Closed" }
        println("zzyzx scope job is done; setting state to Closed")
        transitionToTerminalState(State.Closed)
      }
    }

  private fun transitionToTerminalState(newState: State) {
    while (true) {
      val currentState = state.get()
      if (currentState == State.Closed) {
        break
      } else if (state.compareAndSet(currentState, newState)) {
        break
      }
    }
  }

  suspend fun subscribe(
    requestId: String,
    operationName: String,
    variables: Struct,
    callerSdkType: CallerSdkType,
  ): Flow<DataConnectGrpcClient.OperationResult> {
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
  ): State.Connected? {
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
          State.Closing, State.Closed -> return null
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

    object Closing : State {
      override fun toString() = "Closing"
    }

    object Closed : State {
      override fun toString() = "Closed"
    }
  }
}
