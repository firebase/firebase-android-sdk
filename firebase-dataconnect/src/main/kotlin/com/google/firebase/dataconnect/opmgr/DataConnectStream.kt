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
import com.google.firebase.dataconnect.core.DataConnectAuth
import com.google.firebase.dataconnect.core.DataConnectGrpcRPCs
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.util.CoroutineUtils.awaitAll
import com.google.firebase.dataconnect.util.ObjectLifecycleManager
import com.google.firebase.dataconnect.util.RequestIdGenerator
import google.firebase.dataconnect.proto.StreamRequest
import google.firebase.dataconnect.proto.StreamResponse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow

internal class DataConnectStream(
  dataConnectGrpcRPCs: DataConnectGrpcRPCs,
  dataConnectAuth: DataConnectAuth,
  dataConnectAppCheck: DataConnectAppCheck,
  requestIdGenerator: RequestIdGenerator,
  private val cpuDispatcher: CoroutineDispatcher,
  private val logger: Logger,
) {

  private class LifecycleResource(
    val coroutineScope: CoroutineScope,
    val dataConnectGrpcRPCs: DataConnectGrpcRPCs,
    val dataConnectAuth: DataConnectAuth,
    val dataConnectAppCheck: DataConnectAppCheck,
    val requestIdGenerator: RequestIdGenerator,
  )

  private val lifecycle =
    ObjectLifecycleManager<LifecycleResource>(
      cpuDispatcher,
      logger,
    ) {
      LifecycleResource(
          coroutineScope = lifetimeScope,
          dataConnectGrpcRPCs = dataConnectGrpcRPCs,
          dataConnectAuth = dataConnectAuth,
          dataConnectAppCheck = dataConnectAppCheck,
          requestIdGenerator = requestIdGenerator,
        )
        .toOpenResultWithNothingToClose()
    }

  suspend fun connect(
    requestId: String,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ) =
    lifecycle.open().run {
      val (streamId, authToken, appCheckToken) =
        awaitAll(
          coroutineScope.async { requestIdGenerator.nextStreamId() },
          coroutineScope.async { dataConnectAuth.getToken(requestId) },
          coroutineScope.async { dataConnectAppCheck.getToken(requestId) },
        )

      val (outgoingRequests: Channel<StreamRequest>, incomingResponses: Flow<StreamResponse>) =
        dataConnectGrpcRPCs.connect2(streamId, authToken, appCheckToken, callerSdkType)
    }

  suspend fun close() {
    lifecycle.close()
  }
}
