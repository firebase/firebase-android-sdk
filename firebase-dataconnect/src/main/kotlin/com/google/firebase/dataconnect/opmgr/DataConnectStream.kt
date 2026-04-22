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
import com.google.firebase.dataconnect.core.DataConnectAppCheck
import com.google.firebase.dataconnect.core.DataConnectAppCheck.GetAppCheckTokenResult
import com.google.firebase.dataconnect.core.DataConnectAuth
import com.google.firebase.dataconnect.core.DataConnectAuth.GetAuthTokenResult
import com.google.firebase.dataconnect.core.DataConnectGrpcRPCs
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.util.CoroutineUtils
import com.google.firebase.dataconnect.util.CoroutineUtils.createSupervisorCoroutineScope
import com.google.firebase.dataconnect.util.RequestIdGenerator
import google.firebase.dataconnect.proto.StreamRequest as StreamRequestProto
import google.firebase.dataconnect.proto.StreamResponse as StreamResponseProto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.job
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal class DataConnectStream(
  dataConnectGrpcRPCs: DataConnectGrpcRPCs,
  dataConnectAuth: DataConnectAuth,
  dataConnectAppCheck: DataConnectAppCheck,
  requestIdGenerator: RequestIdGenerator,
  private val cpuDispatcher: CoroutineDispatcher,
  private val logger: Logger,
) {
  private val state = MutableStateFlow<State>(State.Settings(dataConnectGrpcRPCs, dataConnectAuth, dataConnectAppCheck, requestIdGenerator))

  suspend fun close() {
    while (true) {
      val currentState = state.value

      val newState = when (currentState) {
        is State.Settings -> State.Closed
        is State.Connecting -> State.Closing(currentState)
        is State.Connected -> State.Closing(currentState)
        is State.Closing -> {
          currentState.coroutineScope.cancel("DataConnectStream.close() called")
          currentState.coroutineScope.coroutineContext.job.join()
          State.Closed
        }
        State.Closed -> return
      }

      state.compareAndSet(currentState, newState)
    }
  }

  private suspend fun ensureConnected(requestId: String): State.Connected {
    while (true) {
      val currentState = state.value

      val newState = when (currentState) {
        is State.Settings -> currentState.run {
          val coroutineScope = createSupervisorCoroutineScope(cpuDispatcher, logger)
          val job = coroutineScope.async(start = CoroutineStart.LAZY) {
            connect(currentState, requestId)
          }
          State.Connecting(currentState, coroutineScope, job)
        }
        is State.Connecting -> {
          currentState.job.join()
          State.Connected(currentState)
        }
        is State.Connected -> return currentState
        is State.Closing, State.Closed -> error("close() has been called")
      }

      state.compareAndSet(currentState, newState)
    }
  }

  private suspend fun CoroutineScope.connect(
    settings: State.Settings,
    requestId: String,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): DataConnectGrpcRPCs.ConnectResult = settings.run {
    val streamId = requestIdGenerator.nextStreamId()
    logger.debug { "[rid=requestId, sid=$streamId] Connecting to Data Connect backend" }

    val authTokenJob = async { dataConnectAuth.getToken(requestId)}
    val appCheckTokenJob = async { dataConnectAppCheck.getToken(requestId)}
    awaitAll(authTokenJob, appCheckTokenJob)
    val authToken = authTokenJob.await()
    val appCheckToken = appCheckTokenJob.await()

    dataConnectGrpcRPCs.connect2(
      streamId=streamId,
      authToken=authToken,
      appCheckToken=appCheckToken,
      callerSdkType=callerSdkType,
    )
  }

  private sealed interface StreamResponse {
    class Success(val response: StreamResponseProto): StreamResponse
    class Error(val response: StreamResponseProto): StreamResponse
  }

  private sealed interface State {

    class Settings(
      val dataConnectGrpcRPCs: DataConnectGrpcRPCs,
      val dataConnectAuth: DataConnectAuth,
      val dataConnectAppCheck: DataConnectAppCheck,
      val requestIdGenerator: RequestIdGenerator,
    ): State

    class Connecting(
      val settings: Settings,
      val coroutineScope: CoroutineScope,
      val job: Deferred<Unit>,
    ): State

    class Connected(
      val settings: Settings,
      val authUid: String?,
      val coroutineScope: CoroutineScope,
    ): State {
      constructor(state: Connecting, authUid: String?) : this(state.settings, authUid, state.coroutineScope)
    }

    class Closing(val coroutineScope: CoroutineScope): State {
      constructor(state: Connecting) : this(state.coroutineScope)
      constructor(state: Connected) : this(state.coroutineScope)
    }

    object Closed : State

  }


}

