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

import com.google.firebase.dataconnect.DataConnectPath
import com.google.firebase.dataconnect.DataConnectPathSegment
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabase
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabase.GetQueryResultResult
import com.google.firebase.dataconnect.sqlite.GetEntityIdForPathFunction
import com.google.firebase.dataconnect.util.CoroutineUtils.createSupervisorCoroutineScope
import com.google.firebase.dataconnect.util.ImmutableByteArray
import com.google.firebase.dataconnect.util.ProtoUtil.toDataConnectPath
import com.google.protobuf.Duration as DurationProto
import com.google.protobuf.Struct
import google.firebase.dataconnect.proto.GraphqlResponseExtensions as GraphqlResponseExtensionsProto
import google.firebase.dataconnect.proto.GraphqlResponseExtensions.DataConnectProperties as DataConnectPropertiesProto
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job

internal class CacheManager(
  db: DataConnectCacheDatabase,
  maxAge: DurationProto,
  currentTimeMillis: () -> Long,
  cpuDispatcher: CoroutineDispatcher,
  private val logger: Logger,
) {

  private val state =
    MutableStateFlow<State>(
      State.Settings(
        createSupervisorCoroutineScope(cpuDispatcher, logger),
        db,
        maxAge,
        currentTimeMillis,
      )
    )

  suspend fun close() {
    logger.debug { "close() called" }

    while (true) {
      val currentState = state.value

      val newState =
        when (currentState) {
          is State.Settings -> State.Closed
          is State.Initializing -> State.Closing(currentState.settings)
          is State.Initialized -> State.Closing(currentState.settings)
          is State.Closing ->
            currentState.settings.run {
              db.close()
              coroutineScope.cancel("CacheManager.close() called")
              coroutineScope.coroutineContext.job.join()
              State.Closed
            }
          is State.Closed -> return
        }

      state.compareAndSet(currentState, newState)
    }
  }

  private suspend fun insertQueryResult(
    requestId: String,
    authUid: String?,
    queryId: ImmutableByteArray,
    queryData: Struct,
    extensions: GraphqlResponseExtensionsProto,
  ) {
    logger.debug { "[rid=$requestId] updating query result cache" }
    ensureInitialized().run {
      db.insertQueryResult(
        authUid = authUid,
        queryId = queryId,
        queryData = queryData,
        maxAge = maxAge,
        currentTimeMillis = currentTimeMillis(),
        getEntityIdForPath = extensions.entityIdForPathFunction,
      )
    }
  }

  private suspend fun insertQueryResult(
    requestId: String,
    authUid: String?,
    queryId: ImmutableByteArray,
    staleResult: KClass<out GetQueryResultResult>,
  ): GetQueryResultResult {
    logger.debug { "[rid=$requestId] getting query result from cache" }
    ensureInitialized().run {
      return db.getQueryResult(
        authUid = authUid,
        queryId = queryId,
        currentTimeMillis = currentTimeMillis(),
        staleResult = staleResult,
      )
    }
  }

  private suspend fun ensureInitialized(): State.Settings {
    while (true) {
      val currentState = state.value

      val newState =
        when (currentState) {
          is State.Settings ->
            currentState.run {
              State.Initializing(
                this,
                coroutineScope.async(start = CoroutineStart.LAZY) { db.initialize() },
              )
            }
          is State.Initializing ->
            currentState.run {
              initializeJob.await()
              State.Initialized(settings)
            }
          is State.Initialized -> return currentState.settings
          is State.Closing,
          State.Closed -> error("close() has been called")
        }

      state.compareAndSet(currentState, newState)
    }
  }

  private sealed interface State {

    class Settings(
      val coroutineScope: CoroutineScope,
      val db: DataConnectCacheDatabase,
      val maxAge: DurationProto,
      val currentTimeMillis: () -> Long,
    ) : State

    class Initializing(val settings: Settings, val initializeJob: Deferred<Unit>) : State

    class Initialized(val settings: Settings) : State

    class Closing(val settings: Settings) : State

    object Closed : State
  }
}

private val GraphqlResponseExtensionsProto.entityIdForPathFunction: GetEntityIdForPathFunction?
  get() =
    if (dataConnectCount == 0) {
      null
    } else {
      dataConnectList.entityIdForPathFunction
    }

private val List<DataConnectPropertiesProto>.entityIdForPathFunction: GetEntityIdForPathFunction?
  get() {
    val entityIdByPath: Map<DataConnectPath, String>
    val entityIdsByPath: Map<DataConnectPath, List<String>>

    run {
      val entityIdByPathBuilder = mutableMapOf<DataConnectPath, String>()
      val entityIdsByPathBuilder = mutableMapOf<DataConnectPath, List<String>>()

      this.filter { it.hasPath() && it.path.valuesCount > 0 }
        .filter { it.entityId.isNotEmpty() || it.entityIdsCount > 0 }
        .forEach {
          if (it.entityId.isNotEmpty()) {
            entityIdByPathBuilder[it.path.toDataConnectPath()] = it.entityId
          }
          if (it.entityIdsCount > 0) {
            entityIdsByPathBuilder[it.path.toDataConnectPath()] = it.entityIdsList
          }
        }

      if (entityIdByPathBuilder.isEmpty() && entityIdsByPathBuilder.isEmpty()) {
        return null
      }

      entityIdByPath = entityIdByPathBuilder.toMap()
      entityIdsByPath = entityIdsByPathBuilder.toMap()
    }

    if (entityIdByPath.isEmpty() && entityIdsByPath.isEmpty()) {
      return null
    }

    fun toEntityIdForPathFunction(path: DataConnectPath): String? {
      entityIdByPath[path]?.let { entityId ->
        return entityId
      }

      val lastSegment = path.lastOrNull() as? DataConnectPathSegment.ListIndex
      return lastSegment?.index?.let { index ->
        val parentPath = path.dropLast(1)
        val entityIds = entityIdsByPath[parentPath]
        entityIds?.getOrNull(index)
      }
    }

    return ::toEntityIdForPathFunction
  }
