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

import com.google.firebase.dataconnect.core.DataConnectGrpcRPCs
import com.google.firebase.dataconnect.util.ImmutableByteArray
import google.firebase.dataconnect.proto.ExecuteQueryRequest as ExecuteQueryRequestProto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

internal class RemoteQueries(
  private val dataConnectGrpcRPCs: DataConnectGrpcRPCs,
  private val cpuDispatcher: CoroutineDispatcher,
  private val coroutineScope: CoroutineScope,
) {

  private val map = mutableMapOf<Key, RemoteQuery>()

  fun getOrPut(
    key: Key,
    requestProto: ExecuteQueryRequestProto,
  ): RemoteQuery =
    map.getOrPut(key) {
      RemoteQuery(dataConnectGrpcRPCs, cpuDispatcher, requestProto, coroutineScope)
    }

  data class Key(
    val authUid: String?,
    val queryId: ImmutableByteArray,
  )
}
