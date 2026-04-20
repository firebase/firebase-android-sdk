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
import com.google.firebase.dataconnect.util.SuspendingWeakValueHashMap
import com.google.firebase.dataconnect.util.getOrPut
import com.google.protobuf.Struct
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow

internal class RemoteQueries(
  executeFunction: RemoteQueryExecuteFunction,
  cacheManager: CacheManager?,
  cpuDispatcher: CoroutineDispatcher,
  private val logger: Logger,
) : AutoCloseable {

  private val state =
    MutableStateFlow<State>(State.Settings(executeFunction, cacheManager, cpuDispatcher))

  override fun close() {
    logger.debug { "close() called" }

    while (true) {
      val currentState = this.state.value

      val newState =
        when (currentState) {
          is State.Settings -> State.Closed
          is State.Open -> State.Closing(currentState)
          is State.Closing -> {
            currentState.map.close()
            State.Closed
          }
          State.Closed -> return
        }

      state.compareAndSet(currentState, newState)
    }
  }

  suspend fun get(
    key: Key,
    operationName: String,
    variables: Struct,
  ): RemoteQuery =
    ensureOpen().run {
      map.getOrPut(key) {
        RemoteQuery(
          queryId = key.queryId,
          operationName = operationName,
          variables = variables,
          executeFunction = this.executeFunction,
          cacheManager = this.cacheManager,
          cpuDispatcher = cpuDispatcher,
          logger = logger,
        )
      }
    }

  private fun ensureOpen(): State.Open {
    while (true) {
      val currentState = state.value

      val newState =
        when (currentState) {
          is State.Settings -> State.Open(currentState)
          is State.Open -> return currentState
          is State.Closing,
          State.Closed -> error("close() has been called")
        }

      state.compareAndSet(currentState, newState)
    }
  }

  data class Key(
    val queryId: ImmutableByteArray,
  )

  private sealed interface State {
    class Settings(
      val executeFunction: RemoteQueryExecuteFunction,
      val cacheManager: CacheManager?,
      val cpuDispatcher: CoroutineDispatcher,
    ) : State

    class Open(
      val map: SuspendingWeakValueHashMap<Key, RemoteQuery>,
      val executeFunction: RemoteQueryExecuteFunction,
      val cacheManager: CacheManager?,
      val cpuDispatcher: CoroutineDispatcher,
    ) : State {
      constructor(
        state: Settings
      ) : this(
        SuspendingWeakValueHashMap(state.cpuDispatcher),
        state.executeFunction,
        state.cacheManager,
        state.cpuDispatcher,
      )
    }

    class Closing(val map: SuspendingWeakValueHashMap<Key, RemoteQuery>) : State {
      constructor(state: Open) : this(state.map)
    }

    object Closed : State
  }
}
