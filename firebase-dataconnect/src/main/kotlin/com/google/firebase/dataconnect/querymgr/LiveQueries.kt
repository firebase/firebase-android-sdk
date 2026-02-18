/*
 * Copyright 2024 Google LLC
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

import com.google.firebase.dataconnect.*
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.util.AlphanumericStringUtil.toAlphaNumericString
import com.google.firebase.dataconnect.util.ProtoUtil.calculateSha512
import com.google.firebase.dataconnect.util.ProtoUtil.encodeToStruct
import com.google.firebase.dataconnect.util.ProtoUtil.toStructProto
import com.google.firebase.dataconnect.util.ReferenceCounted
import com.google.protobuf.Struct
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class LiveQueries(
  private val liveQueryFactory: LiveQueryFactory,
  private val blockingDispatcher: CoroutineDispatcher,
  parentLogger: Logger,
) {
  private val logger =
    Logger("LiveQueries").apply { debug { "created by ${parentLogger.nameWithId}" } }
  val instanceId: String
    get() = logger.nameWithId

  private val mutex = Mutex()

  // NOTE: All accesses to `referenceCountedLiveQueryByKey` and the `refCount` field of each value
  // MUST be done from a coroutine that has locked `mutex`; otherwise, such accesses (both reads and
  // writes) are data races and yield undefined behavior.
  private val referenceCountedLiveQueryByKey =
    mutableMapOf<LiveQuery.Key, ReferenceCounted<LiveQuery>>()

  suspend fun <T, V, R> withLiveQuery(query: QueryRef<R, V>, block: suspend (LiveQuery) -> T): T {
    val liveQuery = mutex.withLock { acquireLiveQuery(query) }

    return try {
      block(liveQuery)
    } finally {
      withContext(NonCancellable) { mutex.withLock { releaseLiveQuery(liveQuery) } }
    }
  }

  // NOTE: This function MUST be called from a coroutine that has locked `mutex`.
  private suspend fun <R, V> acquireLiveQuery(query: QueryRef<R, V>): LiveQuery {
    val variablesStruct =
      withContext(blockingDispatcher) {
        if (query.variablesSerializer === DataConnectUntypedVariables.Serializer) {
          (query.variables as DataConnectUntypedVariables).variables.toStructProto()
        } else {
          encodeToStruct(
            query.variables,
            query.variablesSerializer,
            query.variablesSerializersModule
          )
        }
      }

    val variablesHash =
      withContext(blockingDispatcher) { variablesStruct.calculateSha512().toAlphaNumericString() }

    val key = LiveQuery.Key(operationName = query.operationName, variablesHash = variablesHash)

    val referenceCountedLiveQuery =
      referenceCountedLiveQueryByKey.getOrPut(key) {
        val liveQuery =
          liveQueryFactory.newLiveQuery(key, query.operationName, variablesStruct, logger)
        ReferenceCounted(liveQuery, refCount = 0)
      }

    referenceCountedLiveQuery.refCount++

    return referenceCountedLiveQuery.obj
  }

  // NOTE: This function MUST be called from a coroutine that has locked `mutex`.
  private fun releaseLiveQuery(liveQuery: LiveQuery) {
    val referenceCountedLiveQuery = referenceCountedLiveQueryByKey[liveQuery.key]

    if (referenceCountedLiveQuery === null) {
      error("unexpected null LiveQuery for key: ${liveQuery.key}")
    } else if (referenceCountedLiveQuery.obj !== liveQuery) {
      error("unexpected LiveQuery for key: ${liveQuery.key}: ${referenceCountedLiveQuery.obj}")
    }

    referenceCountedLiveQuery.refCount--
    if (referenceCountedLiveQuery.refCount == 0) {
      logger.debug { "refCount==0 for LiveQuery with key=${liveQuery.key}; removing the mapping" }
      referenceCountedLiveQueryByKey.remove(liveQuery.key)
      liveQuery.close()
    }
  }

  interface LiveQueryFactory {
    fun newLiveQuery(
      key: LiveQuery.Key,
      operationName: String,
      variables: Struct,
      parentLogger: Logger
    ): LiveQuery
  }
}
