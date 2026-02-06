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

import android.annotation.SuppressLint
import android.database.sqlite.SQLiteDatabase
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.warn
import com.google.firebase.dataconnect.sqlite.SQLiteDatabaseExts.execSQL
import com.google.firebase.dataconnect.sqlite.SQLiteDatabaseExts.getLastInsertRowId
import com.google.firebase.dataconnect.sqlite.SQLiteDatabaseExts.rawQuery
import com.google.firebase.dataconnect.util.ImmutableByteArray
import com.google.protobuf.Struct
import google.firebase.dataconnect.proto.kotlinsdk.EntityOrEntityList
import google.firebase.dataconnect.proto.kotlinsdk.QueryResult as QueryResultProto
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
      @SuppressLint("ThreadPoolCreation")
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

  @JvmInline private value class SqliteUserId(val sqliteRowId: Long)

  @JvmInline private value class SqliteQueryId(val sqliteRowId: Long)

  @JvmInline private value class SqliteEntityId(val sqliteRowId: Long)

  private fun SQLiteDatabase.getOrInsertAuthUid(authUid: String?): SqliteUserId {
    execSQL(logger, "INSERT OR IGNORE INTO users (auth_uid) VALUES (?)", arrayOf(authUid))
    return rawQuery(
      logger,
      "SELECT id FROM users WHERE auth_uid ${if (authUid === null) "IS NULL" else "= ?"}",
      if (authUid === null) emptyArray() else arrayOf(authUid),
    ) { cursor ->
      if (cursor.moveToNext()) {
        SqliteUserId(cursor.getLong(0))
      } else {
        throw AuthUidNotFoundException("authUid=$authUid (internal error m5m52ahrxz)")
      }
    }
  }

  class AuthUidNotFoundException(message: String) : Exception(message)

  private data class GetQueryResult(
    val id: SqliteQueryId,
    val proto: QueryResultProto,
  )

  private fun SQLiteDatabase.getQuery(
    user: SqliteUserId,
    queryId: ImmutableByteArray
  ): GetQueryResult? =
    rawQuery(
      logger,
      "SELECT id, data, flags FROM queries WHERE user_id=? AND query_id=?",
      bindArgs = arrayOf(user.sqliteRowId, queryId.peek()),
    ) { cursor ->
      if (!cursor.moveToNext()) {
        null
      } else {
        val id = SqliteQueryId(cursor.getLong(0))
        val protoBytes = cursor.getBlob(1)
        val flags = cursor.getLong(2).toULong()

        val parseResult = runCatching {
          // The lower 32 bits of the "flags" are "required". So if there are any flags set there
          // then fail because we don't know how to handle them.
          if (flags.toUInt() != 0u) {
            throw UnsupportedQueryFlagsException(
              "unsupported least-significant 32 bits of flags: " +
                "${flags.toUInt().toString(16)} (expected 0)"
            )
          }

          QueryResultProto.parseFrom(protoBytes)
        }

        parseResult.onFailure {
          logger.warn(it) {
            "Parsing QueryResultProto failed for id=$id, user=$user, " +
              "queryId=${queryId.to0xHexString()}, flags=$flags [ykb2vwrcge]"
          }
        }

        GetQueryResult(id, parseResult.getOrThrow())
      }
    }

  private fun SQLiteDatabase.insertQuery(
    user: SqliteUserId,
    queryId: ImmutableByteArray,
    queryResultProtoBytes: ImmutableByteArray,
  ): SqliteQueryId {
    execSQL(
      """
        INSERT OR REPLACE INTO queries
        (user_id, query_id, data, flags)
        VALUES (?, ?, ?, 0)
      """,
      arrayOf(user.sqliteRowId, queryId.peek(), queryResultProtoBytes.peek())
    )
    val rowId = getLastInsertRowId(logger)
    return SqliteQueryId(rowId)
  }

  private fun SQLiteDatabase.insertEntity(
    user: SqliteUserId,
    entityId: String,
    entityStructBytes: ImmutableByteArray,
  ): SqliteEntityId {
    execSQL(
      """
        INSERT OR REPLACE INTO entities
        (user_id, entity_id, data, flags)
        VALUES (?, ?, ?, 0)
      """,
      arrayOf(user.sqliteRowId, entityId, entityStructBytes.peek())
    )
    val rowId = getLastInsertRowId(logger)
    return SqliteEntityId(rowId)
  }

  private fun SQLiteDatabase.insertQueryIdEntityIdMapping(
    query: SqliteQueryId,
    entity: SqliteEntityId,
  ) {
    execSQL(
      """
        INSERT OR IGNORE INTO entity_query_map
        (query_id, entity_id)
        VALUES (?, ?)
      """,
      arrayOf(query.sqliteRowId, entity.sqliteRowId)
    )
  }

  private fun SQLiteDatabase.getEntities(
    user: SqliteUserId,
    entityIds: Collection<String>
  ): Map<String, Struct> {
    if (entityIds.isEmpty()) {
      return emptyMap()
    }

    val (sql, bindArgs) =
      SQLiteStatementBuilder().run {
        append("SELECT entity_id, data, flags FROM entities")
        append(" WHERE user_id=").appendBinding(user.sqliteRowId)
        append(" AND entity_id IN (")
        entityIds.forEachIndexed { index, entityId ->
          if (index > 0) append(", ")
          appendBinding(entityId)
        }
        append(")")
        build()
      }

    return buildMap {
      rawQuery(logger, sql, bindArgs) { cursor ->
        while (cursor.moveToNext()) {
          val entityId = cursor.getString(0)
          val data = cursor.getBlob(1)
          val flags = cursor.getLong(2).toULong()

          val parseResult = runCatching {
            // The lower 32 bits of the "flags" are "required". So if there are any flags set there
            // then fail because we don't know how to handle them.
            if (flags.toUInt() != 0u) {
              throw UnsupportedEntityFlagsException(
                "unsupported least-significant 32 bits of flags: " +
                  "${flags.toUInt().toString(16)} (expected 0)"
              )
            }

            Struct.parseFrom(data)
          }
          parseResult.onFailure { exception ->
            logger.warn(exception) {
              "Failed to parse entity Struct ${size+1}/${entityIds.size} " +
                "with user_id=${user.sqliteRowId}, entity_id=$entityId [fy74p3278j]"
            }
          }

          put(entityId, parseResult.getOrThrow())
        }
      }
    }
  }

  class UnsupportedEntityFlagsException(message: String) : Exception(message)

  class UnsupportedQueryFlagsException(message: String) : Exception(message)

  private fun SQLiteDatabase.deleteEntityIdMappingsForQuery(query: SqliteQueryId) {
    execSQL(logger, "DELETE FROM entity_query_map WHERE query_id=?", arrayOf(query.sqliteRowId))
  }

  suspend fun getQueryResult(authUid: String?, queryId: ImmutableByteArray): Struct? =
  // TODO: convert to read-only transaction so it can be run concurrently
  runReadWriteTransaction { sqliteDatabase ->
    val sqliteUserId = sqliteDatabase.getOrInsertAuthUid(authUid)
    val getQueryResult = sqliteDatabase.getQuery(sqliteUserId, queryId)
    if (getQueryResult === null) {
      null
    } else {
      val (sqliteQueryId, queryResultProto) = getQueryResult
      val entityIds: Set<String> = queryResultProto.referencedEntityIds()
      val entityStructByEntityId = sqliteDatabase.getEntities(sqliteUserId, entityIds)

      val rehydrateResult = runCatching {
        rehydrateQueryResult(queryResultProto, entityStructByEntityId)
      }
      rehydrateResult.onFailure {
        logger.warn {
          "rehydrateQueryResult failed failed for " +
            "id=${sqliteQueryId.sqliteRowId}, queryId=${queryId.to0xHexString()} [knpe3t4f5b]"
        }
      }
      rehydrateResult.getOrThrow()
    }
  }

  suspend fun insertQueryResult(
    authUid: String?,
    queryId: ImmutableByteArray,
    queryData: Struct,
    getEntityIdForPath: GetEntityIdForPathFunction?,
  ) {
    val (queryResultProto, entityStructById) = dehydrateQueryResult(queryData, getEntityIdForPath)
    val queryResultProtoBytes = ImmutableByteArray.adopt(queryResultProto.toByteArray())
    val entityStructBytesByEntityId =
      entityStructById.mapValues { ImmutableByteArray.adopt(it.value.toByteArray()) }

    runReadWriteTransaction { sqliteDatabase ->
      val user = sqliteDatabase.getOrInsertAuthUid(authUid)

      val query =
        sqliteDatabase.insertQuery(
          user = user,
          queryId = queryId,
          queryResultProtoBytes = queryResultProtoBytes,
        )

      sqliteDatabase.deleteEntityIdMappingsForQuery(query)

      entityStructBytesByEntityId.forEach { (entityId, entityStructBytes) ->
        val entity =
          sqliteDatabase.insertEntity(
            user = user,
            entityId = entityId,
            entityStructBytes = entityStructBytes,
          )

        sqliteDatabase.insertQueryIdEntityIdMapping(query, entity)
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

private fun QueryResultProto.referencedEntityIds(): Set<String> = buildSet {
  entitiesList.forEach { entityOrEntityList ->
    when (entityOrEntityList.kindCase) {
      EntityOrEntityList.KindCase.ENTITY -> add(entityOrEntityList.entity.entityId)
      EntityOrEntityList.KindCase.ENTITYLIST -> {
        entityOrEntityList.entityList.entitiesList.forEach { entity -> add(entity.entityId) }
      }
      EntityOrEntityList.KindCase.KIND_NOT_SET -> {}
    }
  }
}
