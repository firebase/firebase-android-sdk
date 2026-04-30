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
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.warn
import com.google.firebase.dataconnect.util.ImmutableByteArray
import com.google.firebase.dataconnect.util.ObjectLifecycleManager
import com.google.firebase.dataconnect.util.SequenceNumberConflatedJobQueue
import com.google.firebase.dataconnect.util.SequencedReference
import com.google.firebase.dataconnect.util.open
import google.firebase.dataconnect.proto.ExecuteRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first

internal class RemoteQuery(
  private val queryId: ImmutableByteArray,
  private val executeRequest: ExecuteRequest,
  private val dataConnectStream: DataConnectStream,
  private val cacheManager: CacheManager?,
  cpuDispatcher: CoroutineDispatcher,
  private val logger: Logger,
) {

  private val lifecycle =
    ObjectLifecycleManager<_, Unit>(cpuDispatcher, logger) {
      SequenceNumberConflatedJobQueue<ExecuteParams, ExecuteResponse>(lifetimeScope) {
        executeFromJobQueue(it.ref.requestId, it.ref.callerSdkType)
      }
    }

  suspend fun close() {
    lifecycle.close()
  }

  suspend fun execute(
    sequenceNumber: Long,
    requestId: String,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): SequencedReference<ExecuteResponse> =
    lifecycle.open().enqueue(sequenceNumber, requestId, callerSdkType)

  private suspend fun executeFromJobQueue(
    requestId: String,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): ExecuteResponse {
    val response = dataConnectStream.execute(requestId, executeRequest, callerSdkType).first()

    cacheManager
      ?.runCatching {
        insertQueryResult(requestId, response.authUid, queryId, response.data, response.extensions)
      }
      ?.onFailure { logger.warn(it) { "[rid=$requestId] failed to update cache" } }

    return response
  }

  private class ExecuteParams(
    val requestId: String,
    val callerSdkType: FirebaseDataConnect.CallerSdkType,
  )

  companion object {

    private suspend fun SequenceNumberConflatedJobQueue<ExecuteParams, ExecuteResponse>.enqueue(
      sequenceNumber: Long,
      requestId: String,
      callerSdkType: FirebaseDataConnect.CallerSdkType,
    ) =
      execute(
        sequenceNumber,
        ExecuteParams(requestId = requestId, callerSdkType = callerSdkType),
      )
  }
}
