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

import com.google.firebase.dataconnect.DataSource as PublicDataSource
import com.google.firebase.dataconnect.QuerySubscription
import com.google.firebase.dataconnect.QuerySubscriptionResult
import com.google.firebase.dataconnect.core.DataSource as CoreDataSource
import com.google.firebase.dataconnect.querymgr.subscribe
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabase.SqliteSequenceNumber
import com.google.firebase.dataconnect.util.SequencedReference
import com.google.firebase.dataconnect.util.TaggedReference
import com.google.firebase.dataconnect.util.throwIfCancellationException
import java.util.Objects
import java.util.concurrent.atomic.AtomicLong
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
    val lastCacheSequenceNumber = AtomicLong(Long.MIN_VALUE)

    // TODO: Modify RealtimeQueryManager to produce updates when queries are executed.
    //  This "hack" essentially "injects" the executeQuery responses in to the subscription to
    //  mimic the pre-existing behavior.
    val nonRealtimeJob = launch {
      nonRealtimeQueryManager.subscribe(query, executeQuery = false) {
        onNonRealtimeUpdate(it, lastCacheSequenceNumber, this@channelFlow)
      }
    }

    try {
      realtimeQueryManager.subscribe(query).collect {
        onRealtimeUpdate(it, lastCacheSequenceNumber, this@channelFlow)
      }
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

  private suspend fun onNonRealtimeUpdate(
    event: SequencedReference<Result<TaggedReference<CoreDataSource, Data>>>,
    lastCacheSequenceNumber: AtomicLong,
    channel: SendChannel<QuerySubscriptionResultImpl>,
  ) {
    val sourceDataPair = event.ref.getOrNull() ?: return

    val currentLastCacheSequenceNumber = lastCacheSequenceNumber.get()
    val (source, data) = sourceDataPair
    if (!shouldEmit(source, currentLastCacheSequenceNumber)) {
      return
    }

    update(lastCacheSequenceNumber, currentLastCacheSequenceNumber, source)

    val queryResult = query.QueryResultImpl(data, source.toDataSourceEnum())
    val subscriptionResult = QuerySubscriptionResultImpl(query, Result.success(queryResult))
    channel.send(subscriptionResult)
  }

  private suspend fun onRealtimeUpdate(
    event: Result<TaggedReference<SqliteSequenceNumber?, Data>>,
    lastCacheSequenceNumber: AtomicLong,
    channel: SendChannel<QuerySubscriptionResultImpl>,
  ) {
    event.throwIfCancellationException()

    event.onSuccess { ref ->
      val eventSequenceNumber: Long? = ref.tag?.sequenceNumber
      if (eventSequenceNumber != null) {
        val lastSequenceNumber = lastCacheSequenceNumber.get()
        if (eventSequenceNumber > lastSequenceNumber) {
          lastCacheSequenceNumber.compareAndSet(lastSequenceNumber, eventSequenceNumber)
        }
      }
    }

    val queryResult = event.map { query.QueryResultImpl(it.ref, PublicDataSource.SERVER) }
    val subscriptionResult = QuerySubscriptionResultImpl(query, queryResult)
    channel.send(subscriptionResult)
  }
}

private fun shouldEmit(source: CoreDataSource, lastCacheSequenceNumber: Long): Boolean =
  when (source) {
    CoreDataSource.Server -> true
    is CoreDataSource.Cache -> shouldEmit(source, lastCacheSequenceNumber)
  }

private fun shouldEmit(source: CoreDataSource.Cache, lastCacheSequenceNumber: Long): Boolean {
  // Return true if `lastCacheSequenceNumber` is "unset", as that indicates that there have been no
  // results published yet. No matter how old the cached data is, it's better to publish *some* data
  // than nothing at all.
  if (lastCacheSequenceNumber == Long.MIN_VALUE) {
    return true
  }

  // Return false if the `sqliteSequenceNumber` of the cached data is null. This null value
  // indicates that the cached data is so old that it came from a previous version of the app that
  // used an older version of the data connect sdk that did not set the sequence number. Therefore,
  // the cached data cannot possibly be newer than whatever data `lastCacheSequenceNumber`
  // corresponds to.
  if (source.sqliteSequenceNumber == null) {
    return false
  }

  // Return whether the cached data is newer than whatever data `lastCacheSequenceNumber`
  // corresponds to.
  return source.sqliteSequenceNumber.sequenceNumber > lastCacheSequenceNumber
}

private fun update(
  lastCacheSequenceNumber: AtomicLong,
  expectedValue: Long,
  source: CoreDataSource
) {
  when (source) {
    is CoreDataSource.Cache -> update(lastCacheSequenceNumber, expectedValue, source)
    CoreDataSource.Server -> return
  }
}

private fun update(
  lastCacheSequenceNumber: AtomicLong,
  expectedValue: Long,
  source: CoreDataSource.Cache
) {
  val sqliteSequenceNumber = source.sqliteSequenceNumber ?: return
  if (sqliteSequenceNumber.sequenceNumber > expectedValue) {
    lastCacheSequenceNumber.compareAndSet(expectedValue, sqliteSequenceNumber.sequenceNumber)
  }
}
