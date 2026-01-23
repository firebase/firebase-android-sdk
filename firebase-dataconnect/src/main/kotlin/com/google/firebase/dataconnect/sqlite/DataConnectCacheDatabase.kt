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

package com.google.firebase.dataconnect.sqlite

import android.database.sqlite.SQLiteDatabase
import com.google.firebase.dataconnect.DataConnectPath
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.warn
import com.google.firebase.dataconnect.sqlite.SQLiteDatabaseExts.execSQL
import com.google.firebase.dataconnect.sqlite.SQLiteDatabaseExts.getLastInsertRowId
import com.google.firebase.dataconnect.sqlite.SQLiteDatabaseExts.rawQuery
import com.google.firebase.dataconnect.util.ImmutableByteArray
import com.google.firebase.dataconnect.util.StringUtil.to0xHexString
import com.google.protobuf.Struct
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

  private val stateMutex = Mutex()
  private var state: State = State.New

  /**
   * Initializes this object.
   *
   * The behavior of calling any method of this class (other than [close]) is undefined before a
   * successful call of [initialize].
   *
   * This method must be called at most once; subsequent invocations will fail.
   */
  suspend fun initialize() {
    stateMutex.withLock {
      when (state) {
        State.New -> {}
        is State.InitializeCalled ->
          throw IllegalStateException("initialize() has already been called")
        is State.CloseCalled ->
          throw IllegalStateException("initialize() cannot be called after close()")
      }

      // Create a single-threaded dispatcher on which all write transactions will run.
      // This is the recommended approach to avoid SQLITE_BUSY errors and also allows us to
      // run higher-priority write transactions before lower-priority ones.
      val coroutineDispatcher =
        Executors.newSingleThreadExecutor { runnable -> Thread(runnable, logger.nameWithId) }
          .asCoroutineDispatcher()

      val coroutineJob = SupervisorJob()

      val coroutineScope =
        CoroutineScope(
          coroutineJob +
            CoroutineName(logger.nameWithId) +
            coroutineDispatcher +
            CoroutineExceptionHandler { context, throwable ->
              logger.warn(throwable) {
                "uncaught exception from a coroutine named ${context[CoroutineName]}: $throwable"
              }
            }
        )

      val initializeJob =
        coroutineScope.async {
          val sqliteDatabase = DataConnectSQLiteDatabaseOpener.open(dbFile, logger)

          var migrateSucceeded = false
          try {
            DataConnectCacheDatabaseMigrator.migrate(sqliteDatabase, logger)
            migrateSucceeded = true
          } finally {
            if (!migrateSucceeded) {
              sqliteDatabase
                .runCatching { close() }
                .onFailure { closeException ->
                  logger.warn(closeException) { "SQLiteDatabase.close() failed" }
                }
            }
          }

          sqliteDatabase
        }

      val sqliteDatabase =
        initializeJob
          .runCatching { await() }
          .fold(
            onSuccess = { it },
            onFailure = { initializeException ->
              coroutineScope.cancel()
              state = State.InitializeCalled.InitializeFailed(initializeException)
              throw initializeException
            }
          )

      val transactionQueue = Channel<Job>(Int.MAX_VALUE)

      val transactionsJob =
        coroutineScope.launch {
          for (transaction in transactionQueue) {
            transaction.join()
          }
        }

      state =
        State.InitializeCalled.Initialized(
          sqliteDatabase,
          coroutineScope,
          coroutineDispatcher,
          transactionsJob,
          transactionQueue
        )
    }
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
  suspend fun close() {
    val initializedState: State.InitializeCalled.Initialized =
      stateMutex.withLock {
        when (val currentState = state) {
          State.New,
          is State.InitializeCalled.InitializeFailed -> {
            state = State.CloseCalled.Closed
            return
          }
          is State.InitializeCalled.Initialized -> {
            state = State.CloseCalled.Closing(currentState)
            currentState
          }
          is State.CloseCalled.Closing -> currentState.initialized
          State.CloseCalled.Closed -> return
        }
      }

    initializedState.run {
      transactionQueue.close() // Stop accepting new transactions.
      transactionsJob.join() // Suspend until all enqueued transactions are complete.
      coroutineScope.async { sqliteDatabase.close() }.join()
      coroutineScope.cancel()
      coroutineScope.coroutineContext[Job]?.join()
      coroutineDispatcher.close()
    }

    stateMutex.withLock {
      check(state is State.CloseCalled) { "internal error: unexpected state: $state" }
      state = State.CloseCalled.Closed
    }
  }

  private fun SQLiteDatabase.getOrInsertAuthUid(authUid: String?): Long {
    execSQL(logger, "INSERT OR IGNORE INTO users (auth_uid) VALUES (?)", arrayOf(authUid))
    return rawQuery(
      logger,
      "SELECT id FROM users WHERE auth_uid ${if (authUid === null) "IS NULL" else "= ?"}",
      if (authUid === null) emptyArray() else arrayOf(authUid),
    ) { cursor ->
      if (cursor.moveToNext()) {
        cursor.getLong(0)
      } else {
        throw AuthUidNotFoundException("authUid=$authUid (internal error m5m52ahrxz)")
      }
    }
  }

  class QueryResults(val rowId: Long, val data: ByteArray) {
    operator fun component1() = rowId
    operator fun component2() = data
  }

  private fun SQLiteDatabase.getQueryResultsByQueryId(
    userRowId: Long,
    queryId: ByteArray
  ): QueryResults? =
    rawQuery(
      logger,
      "SELECT id, data FROM query_results WHERE user_id=? AND query_id=?",
      bindArgs = arrayOf(userRowId, queryId),
    ) { cursor ->
      if (cursor.moveToNext()) {
        QueryResults(
          rowId = cursor.getLong(0),
          data = cursor.getBlob(1),
        )
      } else {
        null
      }
    }

  class AuthUidNotFoundException(message: String) : Exception(message)

  private fun SQLiteDatabase.insertQueryResult(
    userRowId: Long,
    queryId: ByteArray,
    queryData: ByteArray
  ): Long {
    execSQL(
      """
        INSERT OR REPLACE INTO query_results
        (user_id, query_id, data)
        VALUES (?, ?, ?)
      """,
      arrayOf(userRowId, queryId, queryData)
    )
    return getLastInsertRowId(logger)
  }

  private fun SQLiteDatabase.insertEntity(
    userRowId: Long,
    entityId: ByteArray,
    entityData: ByteArray,
  ): Long {
    execSQL(
      """
        INSERT OR REPLACE INTO entities
        (user_id, entity_id, data)
        VALUES (?, ?, ?)
      """,
      arrayOf(userRowId, entityId, entityData)
    )
    return getLastInsertRowId(logger)
  }

  private fun SQLiteDatabase.insertQueryIdEntityIdMapping(queryRowId: Long, entityRowId: Long) {
    execSQL(
      """
        INSERT OR IGNORE INTO entity_query_results_map
        (query_id, entity_id)
        VALUES (?, ?)
      """,
      arrayOf(queryRowId, entityRowId)
    )
  }

  private fun SQLiteDatabase.getEntityRowIdsByQueryRowId(queryRowId: Long): List<Long> = buildList {
    rawQuery(
      logger,
      "SELECT entity_id FROM entity_query_results_map WHERE query_id=?",
      arrayOf(queryRowId)
    ) { cursor ->
      while (cursor.moveToNext()) {
        add(cursor.getLong(0))
      }
    }
  }

  private fun SQLiteDatabase.getEntitiesByRowIds(
    entityRowIds: Collection<Long>
  ): Map<ImmutableByteArray, Struct> {
    if (entityRowIds.isEmpty()) {
      return emptyMap()
    }

    val (sql, bindArgs) =
      SQLiteStatementBuilder().run {
        append("SELECT id, entity_id, data FROM entities WHERE ")
        entityRowIds.forEachIndexed { index, entityRowId ->
          if (index > 0) {
            append(" OR ")
          }
          append("id=").appendBinding(entityRowId)
        }
        build()
      }

    return buildMap {
      rawQuery(logger, sql, bindArgs) { cursor ->
        while (cursor.moveToNext()) {
          val rowId = cursor.getLong(0)
          val encodedEntityId = ImmutableByteArray.adopt(cursor.getBlob(1))
          val data = cursor.getBlob(2)

          val decodeResult = runCatching { QueryResultDecoder.decode(data) }
          decodeResult.onFailure { exception ->
            logger.warn(exception) {
              "QueryResultDecoder failed to decode entity ${size+1}/${entityRowIds.size} " +
                "with rowId=$rowId, encodedEntityId=${encodedEntityId.to0xHexString()} [fy74p3278j]"
            }
          }

          put(encodedEntityId, decodeResult.getOrThrow())
        }
      }
    }
  }

  private fun SQLiteDatabase.deleteEntityIdMappingsForQueryId(queryRowId: Long) {
    execSQL(logger, "DELETE FROM entity_query_results_map WHERE query_id=?", arrayOf(queryRowId))
  }

  suspend fun getQueryResult(authUid: String?, queryId: ByteArray): Struct? =
  // TODO: convert to read-only transaction so it can be run concurrently
  runReadWriteTransaction { sqliteDatabase ->
    val userRowId = sqliteDatabase.getOrInsertAuthUid(authUid)
    val queryResults = sqliteDatabase.getQueryResultsByQueryId(userRowId, queryId)
    if (queryResults === null) {
      null
    } else {
      val (queryRowId, queryData) = queryResults
      val entityRowIds = sqliteDatabase.getEntityRowIdsByQueryRowId(queryRowId)
      val entities = sqliteDatabase.getEntitiesByRowIds(entityRowIds)

      val decodeResult = runCatching { QueryResultDecoder.decode(queryData, entities) }
      decodeResult.onFailure {
        logger.warn {
          "QueryResultDecoder failed to decode query results " +
            "with rowId=$queryRowId, queryId=${queryId.to0xHexString()} [knpe3t4f5b]"
        }
      }
      decodeResult.getOrThrow()
    }
  }

  suspend fun insertQueryResult(
    authUid: String?,
    queryId: ByteArray,
    queryData: Struct,
    entityIdByPath: Map<DataConnectPath, String>,
  ) {
    val encodedQueryData: ByteArray
    val encodedEntities: List<Pair<ByteArray, ByteArray>>
    QueryResultEncoder.encode(queryData, entityIdByPath).also { queryDataEncodeResult ->
      encodedQueryData = queryDataEncodeResult.byteArray
      encodedEntities =
        queryDataEncodeResult.entityByPath.values.map {
          val entityEncodeResult = QueryResultEncoder.encode(it.struct)
          it.encodedId.disown() to entityEncodeResult.byteArray
        }
    }

    runReadWriteTransaction { sqliteDatabase ->
      val userRowId = sqliteDatabase.getOrInsertAuthUid(authUid)

      val queryRowId =
        sqliteDatabase.insertQueryResult(
          userRowId = userRowId,
          queryId = queryId,
          queryData = encodedQueryData,
        )

      sqliteDatabase.deleteEntityIdMappingsForQueryId(queryRowId)

      encodedEntities.forEach { (encodedEntityId, encodedEntityData) ->
        val entityRowId =
          sqliteDatabase.insertEntity(
            userRowId = userRowId,
            entityId = encodedEntityId,
            entityData = encodedEntityData,
          )

        sqliteDatabase.insertQueryIdEntityIdMapping(
          queryRowId = queryRowId,
          entityRowId = entityRowId,
        )
      }
    }
  }

  private suspend inline fun <T> runReadWriteTransaction(
    crossinline block: suspend (SQLiteDatabase) -> T
  ): T {
    val initializedState: State.InitializeCalled.Initialized =
      stateMutex.withLock {
        when (val currentState = state) {
          State.New ->
            throw IllegalStateException(
              "initialize() must be called before running a database transaction"
            )
          is State.InitializeCalled.InitializeFailed -> throw currentState.exception
          is State.InitializeCalled.Initialized -> currentState
          is State.CloseCalled ->
            throw IllegalStateException(
              "a database transaction cannot be started after calling close()"
            )
        }
      }

    return initializedState.run {
      coroutineScope {
        val job =
          async(coroutineDispatcher, start = CoroutineStart.LAZY) {
            sqliteDatabase.beginTransaction()
            try {
              val result = block(sqliteDatabase)
              sqliteDatabase.setTransactionSuccessful()
              result
            } finally {
              sqliteDatabase.endTransaction()
            }
          }

        transactionQueue.send(job)

        job.await()
      }
    }
  }

  private sealed interface State {

    object New : State

    sealed interface InitializeCalled : State {
      class InitializeFailed(val exception: Throwable) : InitializeCalled

      class Initialized(
        val sqliteDatabase: SQLiteDatabase,
        val coroutineScope: CoroutineScope,
        val coroutineDispatcher: ExecutorCoroutineDispatcher,
        val transactionsJob: Job,
        val transactionQueue: Channel<Job>,
      ) : InitializeCalled
    }

    sealed interface CloseCalled : State {
      class Closing(val initialized: InitializeCalled.Initialized) : CloseCalled
      object Closed : CloseCalled
    }
  }
}
