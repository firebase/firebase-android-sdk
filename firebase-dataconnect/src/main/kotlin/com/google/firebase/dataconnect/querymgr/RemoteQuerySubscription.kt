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

import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.core.DataConnectAppCheck.GetAppCheckTokenResult
import com.google.firebase.dataconnect.core.DataConnectAuth.GetAuthTokenResult
import com.google.firebase.dataconnect.core.DataConnectStream
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.warn
import com.google.firebase.dataconnect.util.SequencedReference.Companion.nextSequenceNumber
import google.firebase.dataconnect.proto.ExecuteQueryResponse as ExecuteQueryResponseProto
import google.firebase.dataconnect.proto.ExecuteRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class RemoteQuerySubscription(
  private val cacheUpdater: QueryCacheUpdater?,
  private val cpuDispatcher: CoroutineDispatcher,
  val requestProto: ExecuteRequest,
  private val streamManager: QueryManager.StreamManager,
  private val parentCoroutineScope: CoroutineScope,
  private val logger: Logger,
) {

  private val mutex = Mutex()
  private var activeFlow: ActiveFlow? = null

  suspend fun subscribe(
    requestId: String,
    authToken: GetAuthTokenResult?,
    appCheckToken: GetAppCheckTokenResult?,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): Flow<ExecuteQueryResponseProto> {
    val activeFlow =
      getOrStartActiveFlow(
        requestId = requestId,
        authToken = authToken,
        appCheckToken = appCheckToken,
        callerSdkType = callerSdkType,
      )

    return activeFlow.flow.filterIsInstance<IncomingResponse.Data>().map { it.response }
  }

  private suspend fun getOrStartActiveFlow(
    requestId: String,
    authToken: GetAuthTokenResult?,
    appCheckToken: GetAppCheckTokenResult?,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): ActiveFlow =
    mutex.withLock {
      activeFlow?.let {
        if (it.coroutineScope.isActive && it.stream.isAlive) {
          return it
        }
      }
      this.activeFlow = null

      val coroutineScope = createCoroutineScope()
      val stream =
        streamManager.getOrCreate(
          authToken = authToken,
          appCheckToken = appCheckToken,
          callerSdkType = callerSdkType,
        )

      val flow =
        stream
          .subscribe(requestId, requestProto)
          .consumeAsFlow()
          .map<_, IncomingResponse> { IncomingResponse.Data(it) }
          .onCompletion { exception ->
            if (exception !== null) {
              emit(IncomingResponse.Completed)
            }
          }
          .catch { emit(IncomingResponse.Error(it)) }
          .shareIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
          )

      val activeFlow = ActiveFlow(stream, flow, coroutineScope)
      this.activeFlow = activeFlow

      if (cacheUpdater !== null) {
        coroutineScope.launch {
          flow.filterIsInstance<IncomingResponse.Data>().conflate().collect {
            cacheUpdater.update(nextSequenceNumber(), requestId, it.response)
          }
        }
      }

      coroutineScope.launch {
        flow.filterIsInstance<IncomingResponse.TerminalResponse>().collect {
          if (this@RemoteQuerySubscription.activeFlow === activeFlow) {
            this@RemoteQuerySubscription.activeFlow = null
          }
          when (it) {
            IncomingResponse.Completed -> coroutineScope.cancel()
            is IncomingResponse.Error ->
              coroutineScope.cancel("IncomingResponse.Error", it.throwable)
          }
        }
      }

      activeFlow
    }

  private class ActiveFlow(
    val stream: DataConnectStream,
    val flow: SharedFlow<IncomingResponse>,
    val coroutineScope: CoroutineScope,
  )

  private fun createCoroutineScope(): CoroutineScope =
    CoroutineScope(
      SupervisorJob(parentCoroutineScope.coroutineContext.job) +
        cpuDispatcher +
        CoroutineName("${logger.nameWithId} RemoteQuerySubscription") +
        CoroutineExceptionHandler { context, throwable ->
          logger.warn(throwable) {
            "uncaught exception from a coroutine named ${context[CoroutineName]}: " +
              "$throwable [y88ms37hyr]"
          }
        }
    )

  sealed interface IncomingResponse {
    data class Data(val response: ExecuteQueryResponseProto) : IncomingResponse
    sealed interface TerminalResponse : IncomingResponse
    data class Error(val throwable: Throwable) : TerminalResponse
    data object Completed : TerminalResponse
  }
}
