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
import com.google.firebase.dataconnect.core.LoggerGlobals.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabase
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabase.GetQueryResultResult
import com.google.firebase.dataconnect.sqlite.GetEntityIdForPathFunction
import com.google.firebase.dataconnect.util.CoroutineUtils.createSupervisorCoroutineScope
import com.google.firebase.dataconnect.util.ImmutableByteArray
import com.google.firebase.dataconnect.util.ProtoUtil.toDataConnectPath
import com.google.protobuf.Duration as DurationProto
import com.google.protobuf.Struct
import google.firebase.dataconnect.proto.GraphqlResponseExtensions.DataConnectProperties as DataConnectPropertiesProto
import java.io.File
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
  dbFile: File?,
  maxAge: DurationProto,
  currentTimeMillis: () -> Long,
  cpuDispatcher: CoroutineDispatcher,
  private val logger: Logger,
) {

  private val state =
    MutableStateFlow<State>(
      State.Settings(
        createSupervisorCoroutineScope(cpuDispatcher, logger),
        dbFile,
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
          is State.Settings -> State.Closing(currentState)
          is State.Initializing -> State.Closing(currentState)
          is State.Initialized -> State.Closing(currentState)
          is State.Closing ->
            currentState.run {
              db?.close()
              coroutineScope.cancel("CacheManager.close() called")
              coroutineScope.coroutineContext.job.join()
              State.Closed
            }
          is State.Closed -> return
        }

      state.compareAndSet(currentState, newState)
    }
  }

  suspend fun insertQueryResult(
    requestId: String,
    authUid: String?,
    queryId: ImmutableByteArray,
    queryData: Struct,
    extensions: List<DataConnectPropertiesProto>,
  ) {
    logger.debug { "[rid=$requestId] updating query result cache" }
    ensureInitialized().run {
      db.insertQueryResult(
        authUid = authUid,
        queryId = queryId,
        queryData = queryData,
        maxAge = settings.maxAge,
        currentTimeMillis = settings.currentTimeMillis(),
        getEntityIdForPath = extensions.entityIdForPathFunction,
      )
    }
  }

  suspend fun getQueryResult(
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
        currentTimeMillis = settings.currentTimeMillis(),
        staleResult = staleResult,
      )
    }
  }

  private suspend fun ensureInitialized(): State.Initialized {
    while (true) {
      val currentState = state.value

      val newState =
        when (currentState) {
          is State.Settings ->
            currentState.run {
              val dbLogger = Logger("DataConnectCacheDatabase")
              dbLogger.debug { "created by ${logger.nameWithId}" }
              val db = DataConnectCacheDatabase(dbFile, dbLogger)
              val initializeJob =
                coroutineScope.async(start = CoroutineStart.LAZY) { db.initialize() }
              State.Initializing(this, db, initializeJob)
            }
          is State.Initializing ->
            currentState.run {
              initializeJob.await()
              State.Initialized(settings, db)
            }
          is State.Initialized -> return currentState
          is State.Closing,
          State.Closed -> error("close() has been called")
        }

      state.compareAndSet(currentState, newState)
    }
  }

  private sealed interface State {

    class Settings(
      val coroutineScope: CoroutineScope,
      val dbFile: File?,
      val maxAge: DurationProto,
      val currentTimeMillis: () -> Long,
    ) : State

    class Initializing(
      val settings: Settings,
      val db: DataConnectCacheDatabase,
      val initializeJob: Deferred<Unit>,
    ) : State

    class Initialized(val settings: Settings, val db: DataConnectCacheDatabase) : State

    class Closing(val coroutineScope: CoroutineScope, val db: DataConnectCacheDatabase?) : State {
      constructor(
        settings: Settings,
        db: DataConnectCacheDatabase?
      ) : this(settings.coroutineScope, db)
      constructor(settings: Settings) : this(settings, null)
      constructor(initializing: Initializing) : this(initializing.settings, initializing.db)
      constructor(initialized: Initialized) : this(initialized.settings, initialized.db)
    }

    object Closed : State
  }
}

private val List<DataConnectPropertiesProto>.entityIdForPathFunction: GetEntityIdForPathFunction?
  get() {
    if (isEmpty()) {
      return null
    }

    val entityIdByPath: Map<DataConnectPath, String>
    val entityIdsByPath: Map<DataConnectPath, List<String>>

    run {
      val entityIdByPathBuilder = mutableMapOf<DataConnectPath, String>()
      val entityIdsByPathBuilder = mutableMapOf<DataConnectPath, List<String>>()

      forEach { properties: DataConnectPropertiesProto ->
        if (properties.hasPath() && properties.path.valuesCount > 0) {
          if (properties.entityId.isNotEmpty()) {
            entityIdByPathBuilder[properties.path.toDataConnectPath()] = properties.entityId
          }
          if (properties.entityIdsCount > 0) {
            entityIdsByPathBuilder[properties.path.toDataConnectPath()] = properties.entityIdsList
          }
        }
      }

      if (entityIdByPathBuilder.isEmpty() && entityIdsByPathBuilder.isEmpty()) {
        return null
      }

      entityIdByPath = entityIdByPathBuilder.toMap()
      entityIdsByPath = entityIdsByPathBuilder.toMap()
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
