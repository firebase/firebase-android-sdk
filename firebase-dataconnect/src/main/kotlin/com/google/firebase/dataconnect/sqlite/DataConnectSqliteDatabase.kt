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

import android.annotation.SuppressLint
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.CREATE_IF_NECESSARY
import android.database.sqlite.SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING
import android.database.sqlite.SQLiteDatabase.NO_LOCALIZED_COLLATORS
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.core.LoggerGlobals.warn
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.withContext

internal abstract class DataConnectSqliteDatabase(
  val file: File?,
  parentCoroutineScope: CoroutineScope,
  private val blockingDispatcher: CoroutineDispatcher,
  private val logger: Logger,
) {

  private val coroutineScope =
    CoroutineScope(
      parentCoroutineScope.coroutineContext +
        SupervisorJob(parentCoroutineScope.coroutineContext[Job]) +
        blockingDispatcher +
        CoroutineName(logger.nameWithId) +
        CoroutineExceptionHandler { context, throwable ->
          logger.warn(throwable) {
            "uncaught exception from a coroutine named ${context[CoroutineName]}: $throwable"
          }
        }
    )

  private val state: MutableStateFlow<State> =
    MutableStateFlow(
      State.Open(
        coroutineScope.async(CoroutineName("${logger.nameWithId} open"), CoroutineStart.LAZY) {
          open()
        }
      )
    )

  /**
   * Closes the underlying sqlite database, if it has been opened, and cancels an in-progress
   * database operations. This method suspends until the database is closed.
   */
  suspend fun close() {
    coroutineScope.cancel("${logger.nameWithId} close() called")

    val oldState = state.getAndUpdate { State.Closed }

    if (oldState !is State.Open) {
      return
    }

    oldState.job.cancel("${logger.nameWithId} close() called")
    val db = oldState.job.runCatching { await() }.getOrNull()
    if (db === null) {
      return
    }

    logger.debug { "closing sqlite database connection due to close() invocation" }
    val closeResult =
      withContext(NonCancellable + blockingDispatcher) { db.runCatching { close() } }
    closeResult.onFailure {
      logger.warn(it) { "closing sqlite database failed (ignoring) [f85m9phe33]" }
    }
  }

  /**
   * Runs a block with this object's sqlite database connection.
   *
   * This method call will open the database, if it has not yet been opened, and will throw any
   * exception resulting from such opening. This method will, therefore, suspend, until the database
   * is opened and [onOpen] has returned.
   *
   * This method throws an exception if [close] has been called.
   */
  protected suspend fun <T> withDb(
    operationName: String? = null,
    block: suspend CoroutineScope.(KSQLiteDatabase) -> T
  ): T {
    val db =
      when (val currentState = state.value) {
        is State.Open -> currentState.job.await()
        State.Closing,
        State.Closed ->
          throw IllegalStateException("${logger.nameWithId} has been closed [zgexv4ss46]")
      }

    val job =
      coroutineScope.async(CoroutineName("${logger.nameWithId} withDb $operationName")) {
        KSQLiteDatabase(db).use { kdb -> block(kdb) }
      }

    return job.await()
  }

  /**
   * Called immediately after opening the sqlite database and running some basic PRAGMA statements
   * to set up the connection (e.g. enabling foreign keys).
   *
   * The implementation is expected to do one-time setup for the database connection, mainly
   * creating and/or migrating the database schema.
   *
   * The method will be called from a coroutine whose dispatcher is appropriate for performing
   * blocking I/O operations; therefore, it is not necessary (or advisable, for performance reasons)
   * to offload blocking I/O operations to a separate dispatcher.
   */
  protected abstract suspend fun onOpen(db: KSQLiteDatabase)

  private suspend fun open(): SQLiteDatabase {
    check(coroutineContext[CoroutineDispatcher] === blockingDispatcher) {
      "internal error: open() is expected to be called by a coroutine that uses the " +
        "blocking dispatcher so that blocking database operations don't need to be " +
        "explicitly run on a separate dispatcher"
    }

    val dbPath = file?.absolutePath ?: ":memory:"

    // Specify NO_LOCALIZED_COLLATORS to gain the performance benefits of bitwise collation instead
    // of locale-aware collation. This means that sorting by TEXT fields must be done at the
    // application level.
    val openFlags = ENABLE_WRITE_AHEAD_LOGGING or CREATE_IF_NECESSARY or NO_LOCALIZED_COLLATORS
    logger.debug { "opening sqlite database: $dbPath" }
    val db = SQLiteDatabase.openDatabase(dbPath, null, openFlags)

    val initializeResult = runCatching {
      coroutineContext.ensureActive()
      logger.debug { "initializing sqlite database" }
      initializeSqliteDatabase(db)
      coroutineContext.ensureActive()
      logger.debug { "performing database-specific initializations" }
      KSQLiteDatabase(db).use { kdb -> onOpen(kdb) }
    }

    initializeResult.onFailure { initializeException ->
      logger.warn(initializeException) { "closing sqlite database due to initialization failure" }
      db
        .runCatching { close() }
        .onFailure { closeException ->
          logger.warn(closeException) { "closing sqlite database failed (ignoring) [m5trnpwqvw]" }
        }
    }

    // Re-throw any exception from the database initialization.
    initializeResult.getOrThrow()

    return db
  }

  private sealed interface State {
    data class Open(val job: Deferred<SQLiteDatabase>) : State
    object Closing : State
    object Closed : State
  }

  private companion object {

    fun initializeSqliteDatabase(db: SQLiteDatabase) {
      db.setForeignKeyConstraintsEnabled(true)

      // Enable "full" synchronous mode to get atomic, consistent, isolated, and durable (ACID)
      // properties. Note that ACID is only guaranteed because WAL mode is enabled by calling
      // db.enableWriteAheadLogging() above.
      // https://www.sqlite.org/pragma.html#pragma_synchronous
      db.execSQL("PRAGMA synchronous = FULL")

      @SuppressLint("UseKtx") db.beginTransaction()
      try {
        // Incur a slight performance penalty to eagerly report and isolate database corruption.
        // https://www.sqlite.org/pragma.html#pragma_cell_size_check
        db.execSQL("PRAGMA cell_size_check = true")

        // Explicitly specify UTF-8 as the text encoding in the database.
        // https://www.sqlite.org/pragma.html#pragma_encoding
        db.execSQL("PRAGMA encoding = 'UTF-8'")

        db.setTransactionSuccessful()
      } finally {
        db.endTransaction()
      }
    }
  }
}
