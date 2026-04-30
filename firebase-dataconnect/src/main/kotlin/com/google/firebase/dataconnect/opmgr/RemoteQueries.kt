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

import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.util.ImmutableByteArray
import com.google.firebase.dataconnect.util.ObjectLifecycleManager
import com.google.firebase.dataconnect.util.SuspendingWeakValueHashMap
import com.google.firebase.dataconnect.util.getOrPut
import com.google.firebase.dataconnect.util.open
import google.firebase.dataconnect.proto.ExecuteRequest
import kotlinx.coroutines.CoroutineDispatcher

internal class RemoteQueries(
  dataConnectStream: DataConnectStream,
  cacheManager: CacheManager?,
  private val cpuDispatcher: CoroutineDispatcher,
  ioDispatcher: CoroutineDispatcher,
  private val logger: Logger,
) {

  private val lifecycleManager =
    ObjectLifecycleManager<LifecycleResource, Unit>(cpuDispatcher, logger) {
      logger.debug { "opening" }

      val map =
        SuspendingWeakValueHashMap<Key, RemoteQuery>(
          nonBlockingDispatcher = cpuDispatcher,
          blockingDispatcher = ioDispatcher,
        )
      onClose(map::close)

      LifecycleResource(map, dataConnectStream, cacheManager)
    }

  suspend fun close() {
    logger.debug { "close()" }
    lifecycleManager.close()
  }

  suspend fun get(
    key: Key,
    executeRequest: ExecuteRequest,
  ): RemoteQuery {
    val lifecycleResource = lifecycleManager.open()
    return lifecycleResource.map.getOrPut(key) {
      RemoteQuery(
        key.queryId,
        executeRequest,
        lifecycleResource.dataConnectStream,
        lifecycleResource.cacheManager,
        cpuDispatcher = cpuDispatcher,
        logger,
      )
    }
  }

  private class LifecycleResource(
    val map: SuspendingWeakValueHashMap<Key, RemoteQuery>,
    val dataConnectStream: DataConnectStream,
    val cacheManager: CacheManager?,
  )

  data class Key(
    val queryId: ImmutableByteArray,
  )
}
