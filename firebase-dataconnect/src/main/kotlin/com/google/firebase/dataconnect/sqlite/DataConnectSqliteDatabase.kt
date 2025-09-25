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

package com.google.firebase.dataconnect.sqlite

import android.database.sqlite.SQLiteDatabase
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.core.LoggerGlobals.warn
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.withContext

internal abstract class DataConnectSqliteDatabase(
  private val dbFile: File?,
  private val blockingDispatcher: CoroutineDispatcher,
  private val logger: Logger,
) {

  private val state = MutableStateFlow<State>(State.New)

  protected val db: KSQLiteDatabase
    get() =
      when (val currentState = state.value) {
        State.New -> throw IllegalStateException("database has not yet been opened [xc8vfe7gy2]")
        State.Opening ->
          throw IllegalStateException("database is in the process of opening [afebac2837]")
        is State.Opened -> currentState.kdb
        State.Closing -> throw IllegalStateException("database is closing [hj8ndj2g2v]")
        State.Closed -> throw IllegalStateException("database has been closed [zgexv4ss46]")
      }

  suspend fun open() {
    state.update { currentState ->
      when (currentState) {
        State.New -> State.Opening
        State.Opening -> throw IllegalStateException("open() has already been called [yq67wee272]")
        is State.Opened ->
          throw IllegalStateException("open() has already been called [qgf3wzacqq]")
        State.Closing ->
          throw IllegalStateException("open() cannot be called after close() [eh2ny3b83k]")
        State.Closed ->
          throw IllegalStateException("open() cannot be called after close() [qzzdc646y7]")
      }
    }

    val dbPath = dbFile?.absolutePath ?: ":memory:"
    val openResult = runCatching {
      withContext(blockingDispatcher) {
        logger.debug { "opening sqlite database: $dbPath" }
        val db = SQLiteDatabase.openOrCreateDatabase(dbPath, null)
        var initializeSuccess = false
        try {
          logger.debug { "initializing sqlite database connection" }
          initializeSqliteDatabase(db)
          onOpen(db)
          initializeSuccess = true
        } finally {
          if (!initializeSuccess) {
            logger.debug { "closing sqlite database connection due to initialization error" }
            db.close()
          }
        }
        db
      }
    }

    val dbOpenedState =
      openResult.fold(
        onSuccess = {
          logger.debug { "opening sqlite database completed successfully: $dbPath" }
          State.Opened(it)
        },
        onFailure = {
          logger.warn(it) { "opening sqlite database failed: $dbPath; call open() to try again" }
          State.New
        }
      )

    val finalState =
      state.updateAndGet { currentState ->
        when (currentState) {
          State.New -> throw IllegalStateException("unexpected state: $currentState [r2t2h8akbk]")
          State.Opening -> dbOpenedState
          is State.Opened ->
            throw IllegalStateException("unexpected state: $currentState [nywgp9899t]")
          State.Closing -> State.Closing
          State.Closed ->
            throw IllegalStateException("unexpected state: $currentState [gchkhg8xq7]")
        }
      }

    if (finalState is State.Closing && dbOpenedState is State.Opened) {
      val db = dbOpenedState.db
      val closeResult = withContext(blockingDispatcher) { runCatching { db.close() } }
      state.value = State.Closed
      closeResult.onFailure { logger.warn(it) { "closing sqlite database failed: $dbPath" } }
    }
  }

  suspend fun close() {
    val oldState =
      state.getAndUpdate { currentState ->
        when (currentState) {
          State.New -> State.Closed
          State.Opening -> State.Closing
          is State.Opened -> State.Closing
          State.Closing -> State.Closing
          State.Closed -> State.Closed
        }
      }

    if (oldState is State.Opened) {
      val db = oldState.db
      val closeResult = withContext(blockingDispatcher) { runCatching { db.close() } }
      state.value = State.Closed
      closeResult.onFailure { logger.warn(it) { "closing sqlite database failed: ${db.path}" } }
    }

    state.takeWhile { it != State.Closed }.collect()
  }

  protected abstract fun onOpen(db: SQLiteDatabase)

  private sealed interface State {
    object New : State
    object Opening : State
    data class Opened(val db: SQLiteDatabase) : State {
      val kdb = KSQLiteDatabase(db)
    }
    object Closing : State
    object Closed : State
  }

  private companion object {

    fun initializeSqliteDatabase(db: SQLiteDatabase): SQLiteDatabase {
      db.enableWriteAheadLogging()
      db.setForeignKeyConstraintsEnabled(true)

      // Prevent anyone else from connecting to the database. This is done mostly to prevent
      // unintentional corruption which could occur if accessing the database outside of the main
      // Android process for an application.
      // https://www.sqlite.org/pragma.html#pragma_locking_mode
      db.execSQL("PRAGMA locking_mode = EXCLUSIVE")

      // Incur a slight performance penalty to eagerly report and isolate database corruption.
      // https://www.sqlite.org/pragma.html#pragma_cell_size_check
      db.execSQL("PRAGMA cell_size_check = true")

      // Explicitly specify UTF-8 as the text encoding in the database.
      // https://www.sqlite.org/pragma.html#pragma_encoding
      db.execSQL("PRAGMA encoding = 'UTF-8'")

      // Enable "full" synchronous mode to get atomic, consistent, isolated, and durable (ACID)
      // properties. Note that ACID is only guaranteed because WAL mode is enabled by calling
      // db.enableWriteAheadLogging() above.
      // https://www.sqlite.org/pragma.html#pragma_synchronous
      db.execSQL("PRAGMA synchronous = FULL")

      return db
    }
  }
}
