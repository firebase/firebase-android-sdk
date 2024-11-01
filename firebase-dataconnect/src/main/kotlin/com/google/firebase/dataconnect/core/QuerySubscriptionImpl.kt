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

import com.google.firebase.dataconnect.QuerySubscriptionResult
import com.google.firebase.dataconnect.util.NullableReference
import com.google.firebase.dataconnect.util.SequencedReference
import java.util.Objects
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

internal class QuerySubscriptionImpl<Data, Variables>(query: QueryRefImpl<Data, Variables>) :
  QuerySubscriptionInternal<Data, Variables> {
  private val _query = MutableStateFlow(query)
  override val query: QueryRefImpl<Data, Variables> by _query::value

  private val _lastResult = MutableStateFlow(NullableReference<QuerySubscriptionResultImpl>())
  override val lastResult: QuerySubscriptionResult<Data, Variables>?
    get() = _lastResult.value.ref

  // Each collection of this flow triggers an implicit `reload()`.
  override val flow: Flow<QuerySubscriptionResult<Data, Variables>> = channelFlow {
    lastResult?.also { send(it) }

    var collectJob: Job? = null
    _query.collect { query ->
      // We only need to execute the query upon initially collecting the flow. Subsequent changes to
      // the variables automatically get a call to reload() by update().
      val shouldExecuteQuery =
        collectJob.let {
          if (it === null) {
            true
          } else {
            it.cancelAndJoin()
            false
          }
        }

      collectJob = launch {
        val queryManager = query.dataConnect.lazyQueryManager.get()
        queryManager.subscribe(query, executeQuery = shouldExecuteQuery) { sequencedResult ->
          val querySubscriptionResult = QuerySubscriptionResultImpl(query, sequencedResult)
          send(querySubscriptionResult)
          updateLastResult(querySubscriptionResult)
        }
      }
    }
  }

  override suspend fun reload() {
    val query = query // save query to a local variable in case it changes.
    val sequencedResult = query.dataConnect.lazyQueryManager.get().execute(query)
    updateLastResult(QuerySubscriptionResultImpl(query, sequencedResult))
    sequencedResult.ref.getOrThrow()
  }

  override suspend fun update(variables: Variables) {
    _query.value = _query.value.copy(variables = variables)
    reload()
  }

  private fun updateLastResult(prospectiveLastResult: QuerySubscriptionResultImpl) {
    // Update the last result in a compare-and-swap loop so that there is no possibility of
    // clobbering a newer result with an older result, compared using their sequence numbers.
    // TODO: Fix this so that results from an old query do not clobber results from a new query,
    //  as set by a call to update()
    while (true) {
      val currentLastResult = _lastResult.value
      if (currentLastResult.ref != null) {
        val currentSequenceNumber = currentLastResult.ref.sequencedResult.sequenceNumber
        val prospectiveSequenceNumber = prospectiveLastResult.sequencedResult.sequenceNumber
        if (currentSequenceNumber >= prospectiveSequenceNumber) {
          return
        }
      }

      if (_lastResult.compareAndSet(currentLastResult, NullableReference(prospectiveLastResult))) {
        return
      }
    }
  }

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString(): String = "QuerySubscription(query=$query)"

  private inner class QuerySubscriptionResultImpl(
    override val query: QueryRefImpl<Data, Variables>,
    val sequencedResult: SequencedReference<Result<Data>>
  ) : QuerySubscriptionResult<Data, Variables> {
    override val result = sequencedResult.ref.map { query.QueryResultImpl(it) }

    override fun equals(other: Any?) =
      other is QuerySubscriptionImpl<*, *>.QuerySubscriptionResultImpl &&
        other.query == query &&
        other.result == result

    override fun hashCode() = Objects.hash(QuerySubscriptionResultImpl::class, query, result)

    override fun toString() = "QuerySubscriptionResultImpl(query=$query, result=$result)"
  }
}
