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
package com.google.firebase.dataconnect.core

import com.google.firebase.dataconnect.DataSource
import com.google.firebase.dataconnect.QuerySubscription
import com.google.firebase.dataconnect.QuerySubscriptionResult
import com.google.firebase.dataconnect.querymgr.subscribe
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabase.SqliteSequenceNumber
import com.google.firebase.dataconnect.sqlite.SqliteSequencedReference
import com.google.firebase.dataconnect.util.SequencedReference
import com.google.firebase.dataconnect.util.throwIfCancellationException
import java.util.Objects
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class QuerySubscriptionImpl<Data, Variables>(
  override val query: QueryRefImpl<Data, Variables>,
) : QuerySubscription<Data, Variables> {

  override val flow: Flow<QuerySubscriptionResult<Data, Variables>> = channelFlow {
    val realtimeQueryManager =
      query.dataConnect.realtimeQueryManagerUnlessClosed ?: return@channelFlow
    val nonRealtimeQueryManager = query.dataConnect.queryManagerUnlessClosed ?: return@channelFlow

    val resultSender = ResultSender(this@channelFlow)

    // TODO: Remove the event emitting from the non-realtime query manager by improving the realtime
    //  query manager to emit events from cache and in response to executeQuery() calls.
    val nonRealtimeJob = launch {
      nonRealtimeQueryManager.subscribe(query, executeQuery = false) {
        resultSender.onNonRealtimeUpdate(it)
      }
    }

    try {
      realtimeQueryManager.subscribe(query).collect { resultSender.onRealtimeUpdate(it) }
    } finally {
      nonRealtimeJob.cancel()
    }
  }

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString(): String = "QuerySubscription(query=$query)"

  private inner class QuerySubscriptionResultImpl(
    override val query: QueryRefImpl<Data, Variables>,
    override val result: Result<QueryRefImpl<Data, Variables>.QueryResultImpl>,
  ) : QuerySubscriptionResult<Data, Variables> {

    override fun equals(other: Any?) =
      other is QuerySubscriptionImpl<*, *>.QuerySubscriptionResultImpl &&
        other.query == query &&
        other.result == result

    override fun hashCode() = Objects.hash(QuerySubscriptionResultImpl::class, query, result)

    override fun toString() = "QuerySubscriptionResultImpl(query=$query, result=$result)"
  }

  private inner class ResultSender(
    val channel: SendChannel<QuerySubscriptionResultImpl>,
  ) {
    private val mutex = Mutex()
    private var lastEmittedSqliteSequenceNumber: SqliteSequenceNumber? = null

    suspend fun onNonRealtimeUpdate(
      event: SequencedReference<Result<SourcedData<Data>>>,
    ) {
      val (source, sqliteSequenceNumber, data) = event.ref.getOrNull() ?: return
      mutex.withLock {
        if (shouldEmitNonRealtime(source, sqliteSequenceNumber, lastEmittedSqliteSequenceNumber)) {
          emit(data, source, sqliteSequenceNumber)
        }
      }
    }

    suspend fun onRealtimeUpdate(
      event: Result<SqliteSequencedReference<Data>>,
    ) {
      event.throwIfCancellationException()

      mutex.withLock {
        if (shouldEmitRealtime(event, lastEmittedSqliteSequenceNumber)) {
          val queryResult = event.map { query.QueryResultImpl(it.ref, DataSource.SERVER) }
          emit(queryResult, event.getOrNull()?.sqliteSequenceNumber)
        }
      }
    }

    private suspend fun emit(
      queryResult: Result<QueryRefImpl<Data, Variables>.QueryResultImpl>,
      dataSqliteSequenceNumber: SqliteSequenceNumber?,
    ) {
      val subscriptionResult = QuerySubscriptionResultImpl(query, queryResult)
      channel.send(subscriptionResult)
      if (dataSqliteSequenceNumber != null) {
        lastEmittedSqliteSequenceNumber = dataSqliteSequenceNumber
      }
    }

    private suspend fun emit(
      data: Data,
      source: DataSource,
      dataSqliteSequenceNumber: SqliteSequenceNumber?,
    ) {
      emit(query.QueryResultImpl(data, source), dataSqliteSequenceNumber)
    }

    private suspend fun emit(
      queryResult: QueryRefImpl<Data, Variables>.QueryResultImpl,
      dataSqliteSequenceNumber: SqliteSequenceNumber?,
    ) {
      emit(Result.success(queryResult), dataSqliteSequenceNumber)
    }
  }
}

private fun shouldEmitNonRealtime(
  dataSource: DataSource,
  dataSqliteSequenceNumber: SqliteSequenceNumber?,
  lastEmittedSqliteSequenceNumber: SqliteSequenceNumber?,
): Boolean {
  // Emit the data if `lastEmittedSqliteSequenceNumber` is null, as that indicates that there have
  // been no results emitted yet. Regardless of the age of the data, we may as well emit something.
  if (lastEmittedSqliteSequenceNumber == null) {
    return true
  }

  // Return an appropriate value when `dataSqliteSequenceNumber` is null. The meaning of a null
  // value depends on the source of the data.
  if (dataSqliteSequenceNumber == null) {
    return when (dataSource) {
      // Do not emit the data because it's so old that it was saved to cache by an older version of
      // the SDK that lacked SqliteSequenceNumber support; therefore, the data cannot possibly be
      // newer than the data previously emitted with `lastEmittedSqliteSequenceNumber`.
      DataSource.CACHE -> false
      // Emit the data because either saving the data to the cache failed, or caching is not enabled
      // at all. Either way, there is no way to tell if this data is older than the data previously
      // emitted with `lastEmittedSqliteSequenceNumber`, so assume that it is newer.
      DataSource.SERVER -> true
    }
  }

  // Emit the data if, and only if, it is newer than the data previously emitted with
  // `lastEmittedSqliteSequenceNumber`.
  return dataSqliteSequenceNumber > lastEmittedSqliteSequenceNumber
}

private fun shouldEmitRealtime(
  event: Result<SqliteSequencedReference<*>>,
  lastEmittedSqliteSequenceNumber: SqliteSequenceNumber?,
): Boolean {
  // Emit failures unconditionally, since they do not have an associated SqliteSequenceNumbers.
  if (event.isFailure) {
    return true
  }

  // Emit the data if `lastEmittedSqliteSequenceNumber` is null, as that indicates that there have
  // been no results emitted yet. Regardless of the age of the data, we may as well emit something.
  if (lastEmittedSqliteSequenceNumber == null) {
    return true
  }

  // Emit the data if its SqliteSequenceNumber is null because that means saving the data to the
  // cache failed. In this case there is no way to tell if this data is older than the data
  // previously emitted with `lastEmittedSqliteSequenceNumber`, so assume that it is newer.
  val dataSqliteSequenceNumber = event.getOrThrow().sqliteSequenceNumber ?: return true

  // Emit the data if, and only if, it is newer than the data previously emitted with
  // `lastEmittedSqliteSequenceNumber`.
  return dataSqliteSequenceNumber > lastEmittedSqliteSequenceNumber
}
