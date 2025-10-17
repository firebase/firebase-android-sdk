/*
 * Copyright 2025 Google LLC
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

package com.google.firebase.dataconnect.sqlite2

import androidx.core.os.CancellationSignal
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.warn
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.job

/**
 * Provides and manages access to the sqlite database used to stored cached query results for
 * Firebase Data Connect.
 *
 * ### Safe for concurrent use
 *
 * All methods and properties of [DataConnectCacheDatabase] are thread-safe and may be safely called
 * and/or accessed concurrently from multiple threads and/or coroutines.
 */
internal class DataConnectCacheDatabase(private val dbFile: File?, private val logger: Logger) {

  private val state = MutableStateFlow<State>(State.New)

  /**
   * Initializes this object.
   *
   * The behavior of calling any method of this class (other than [close]) is undefined before a
   * successful call of [initialize].
   *
   * This method must be called at most once; subsequent invocations will fail.
   */
  suspend fun initialize() {
    val initializingState =
      state.updateAndGet { currentState ->
        when (currentState) {
          State.New -> State.Initializing(CancellationSignal())
          is State.Initializing,
          is State.Initialized ->
            throw IllegalStateException("initialize() has already been called")
          State.Closed -> throw IllegalStateException("initialize() cannot be called after close()")
        }
      }
    check(initializingState is State.Initializing) {
      "internal error j8h5349z4q: initializingState=$initializingState, but expected Initializing"
    }

    // Create a single-threaded dispatcher on which all write transactions will run.
    // This is the recommended approach to avoid SQLITE_BUSY errors and also allows us to
    // run higher-priority write transactions before lower-priority ones.
    val dispatcher =
      Executors.newSingleThreadExecutor { runnable -> Thread(runnable, logger.nameWithId) }
        .asCoroutineDispatcher()

    val coroutineScope =
      CoroutineScope(
        SupervisorJob() +
          CoroutineName(logger.nameWithId) +
          dispatcher +
          CoroutineExceptionHandler { context, throwable ->
            logger.warn(throwable) {
              "uncaught exception from a coroutine named ${context[CoroutineName]}: $throwable"
            }
          }
      )

    coroutineScope.coroutineContext.job.invokeOnCompletion { dispatcher.close() }

    val initializeJob =
      coroutineScope.async {
        ensureActive()
        val db = DataConnectSQLiteDatabaseOpener.open(dbFile, logger)

        var migrationSucceeded = false
        try {
          ensureActive()
          DataConnectCacheDatabaseMigrator.migrate(db, logger)
          migrationSucceeded = true
        } finally {
          if (!migrationSucceeded) {
            db.runCatching { close() }
          }
        }

        val initializedState = State.Initialized(coroutineScope)
        if (!state.compareAndSet(initializingState, initializedState)) {
          db.close()
        }
      }

    initializingState.cancellationSignal.setOnCancelListener {
      initializeJob.cancel("initialize() cancelled by a call to close()")
    }

    initializeJob.invokeOnCompletion { exception ->
      if (exception !== null) {
        coroutineScope.cancel("initialize() failed: $exception")
      }
    }

    initializeJob.await()
  }

  /**
   * Closes this object, releasing any resources that it holds.
   *
   * The behavior of calling any method of this class (other than [close] itself) is undefined after
   * having called [close].
   *
   * This method is idempotent; it is safe to call more than once. Subsequent invocation will
   * suspend, like the original invocation, until the "close" operation has completed.
   */
  fun close() {
    state.update { currentState ->
      when (currentState) {
        State.New -> {}
        is State.Initializing -> currentState.cancellationSignal.cancel()
        is State.Initialized -> currentState.coroutineScope.cancel("close() called")
        State.Closed -> {}
      }
      State.Closed
    }
  }

  private sealed interface State {

    object New : State

    class Initializing(val cancellationSignal: CancellationSignal) : State

    class Initialized(val coroutineScope: CoroutineScope) : State

    object Closed : State
  }
}
