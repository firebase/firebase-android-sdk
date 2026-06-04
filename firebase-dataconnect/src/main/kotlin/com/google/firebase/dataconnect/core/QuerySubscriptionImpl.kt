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
import com.google.firebase.dataconnect.sqlite.SqliteSequencedReference
import com.google.firebase.dataconnect.util.SequencedReference
import com.google.firebase.dataconnect.util.throwIfCancellationException
import java.util.Objects
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

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
    suspend fun onNonRealtimeUpdate(
      event: SequencedReference<Result<SourcedData<Data>>>,
    ) {
      val (source, _, data) = event.ref.getOrNull() ?: return
      emit(data, source)
    }

    suspend fun onRealtimeUpdate(
      event: Result<SqliteSequencedReference<Data>>,
    ) {
      event.throwIfCancellationException()
      val queryResult = event.map { query.QueryResultImpl(it.ref, DataSource.SERVER) }
      emit(queryResult)
    }

    private suspend fun emit(
      queryResult: Result<QueryRefImpl<Data, Variables>.QueryResultImpl>,
    ) {
      val subscriptionResult = QuerySubscriptionResultImpl(query, queryResult)
      channel.send(subscriptionResult)
    }

    private suspend fun emit(data: Data, source: DataSource) {
      emit(query.QueryResultImpl(data, source))
    }

    private suspend fun emit(queryResult: QueryRefImpl<Data, Variables>.QueryResultImpl) {
      emit(Result.success(queryResult))
    }
  }
}
