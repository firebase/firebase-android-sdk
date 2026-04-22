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
import com.google.firebase.dataconnect.util.CoroutineUtils.createSupervisorCoroutineScope
import com.google.firebase.dataconnect.util.ImmutableByteArray
import com.google.firebase.dataconnect.util.SequenceNumberConflatedJobQueue
import com.google.firebase.dataconnect.util.SequencedReference
import com.google.protobuf.Struct
import kotlinx.coroutines.CoroutineDispatcher

internal typealias RemoteQueryExecuteFunction =
  suspend (
    requestId: String,
    operationName: String,
    variables: Struct,
  ) -> ExecuteResponse

internal class RemoteQuery(
  private val queryId: ImmutableByteArray,
  private val operationName: String,
  private val variables: Struct,
  private val executeFunction: RemoteQueryExecuteFunction,
  private val cacheManager: CacheManager?,
  cpuDispatcher: CoroutineDispatcher,
  private val logger: Logger,
) {

  suspend fun execute(
    requestId: String,
    sequenceNumber: Long,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): SequencedReference<ExecuteResponse> =
    jobQueue.execute(
      sequenceNumber,
      ExecuteParams(requestId = requestId, callerSdkType = callerSdkType),
    )

  private suspend fun execute(params: SequencedReference<ExecuteParams>): ExecuteResponse =
    execute(params.ref)

  private suspend fun execute(params: ExecuteParams): ExecuteResponse =
    params.run {
      val response = executeFunction(requestId, operationName, variables)

      cacheManager
        ?.runCatching {
          insertQueryResult(
            params.requestId,
            response.authUid,
            queryId,
            response.data,
            response.extensions
          )
        }
        ?.onFailure { logger.warn(it) { "[rid=$requestId] failed to update cache" } }

      response
    }

  private val jobQueue =
    SequenceNumberConflatedJobQueue<ExecuteParams, ExecuteResponse>(
      createSupervisorCoroutineScope(cpuDispatcher, logger),
      ::execute
    )

  private class ExecuteParams(
    val requestId: String,
    val callerSdkType: FirebaseDataConnect.CallerSdkType,
  )
}
