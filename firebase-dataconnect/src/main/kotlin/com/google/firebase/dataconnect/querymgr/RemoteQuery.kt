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
import com.google.firebase.dataconnect.core.DataConnectGrpcRPCs
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.warn
import com.google.firebase.dataconnect.util.SequenceNumberConflatedJobQueue
import com.google.firebase.dataconnect.util.SequencedReference
import google.firebase.dataconnect.proto.ExecuteQueryRequest as ExecuteQueryRequestProto
import google.firebase.dataconnect.proto.ExecuteQueryResponse as ExecuteQueryResponseProto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

internal class RemoteQuery(
  private val dataConnectGrpcRPCs: DataConnectGrpcRPCs,
  private val cacheUpdater: QueryCacheUpdater?,
  cpuDispatcher: CoroutineDispatcher,
  val requestProto: ExecuteQueryRequestProto,
  private val coroutineScope: CoroutineScope,
  private val logger: Logger,
) {

  private class ExecuteParams(
    val requestId: String,
    val authToken: String?,
    val appCheckToken: String?,
    val callerSdkType: FirebaseDataConnect.CallerSdkType,
  )

  private val executeSerializer =
    SequenceNumberConflatedJobQueue<ExecuteParams, ExecuteQueryResponseProto>(
      coroutineScope =
        CoroutineScope(
          coroutineScope.coroutineContext +
            SupervisorJob(coroutineScope.coroutineContext[Job]) +
            cpuDispatcher
        ),
    ) { (sequenceNumber, params) ->
      val executeQueryResponse: ExecuteQueryResponseProto =
        dataConnectGrpcRPCs.executeQuery(
          requestId = params.requestId,
          requestProto = requestProto,
          authToken = params.authToken,
          appCheckToken = params.appCheckToken,
          callerSdkType = params.callerSdkType,
        )

      cacheUpdater
        ?.runCatching { update(sequenceNumber, params.requestId, executeQueryResponse) }
        ?.onFailure { logger.warn(it) { "Failed to update cache" } }

      executeQueryResponse
    }

  suspend fun execute(
    requestId: String,
    sequenceNumber: Long,
    authToken: String?,
    appCheckToken: String?,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): SequencedReference<ExecuteQueryResponseProto> {
    val params =
      ExecuteParams(
        requestId = requestId,
        authToken = authToken,
        appCheckToken = appCheckToken,
        callerSdkType = callerSdkType,
      )
    return executeSerializer.execute(sequenceNumber, params)
  }
}
