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
import com.google.firebase.dataconnect.ExperimentalRealtimeQueries
import com.google.firebase.dataconnect.QuerySubscription
import com.google.firebase.dataconnect.QuerySubscriptionResult
import com.google.firebase.dataconnect.querymgr.subscribe
import com.google.firebase.dataconnect.util.throwIfCancellationException
import java.util.Objects
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * A temporary implementation of [QuerySubscription] that supports "realtime subscription updates".
 *
 * "Realtime subscription updates" is currently a work-in-progress; however, when it is completed,
 * this class will be renamed to [QuerySubscriptionImpl] and the existing [QuerySubscriptionImpl]
 * will be deleted.
 */
@ExperimentalRealtimeQueries
internal class RealtimeQuerySubscriptionImpl<Data, Variables>(
  override val query: RealtimeQueryRefImpl<Data, Variables>,
) : QuerySubscription<Data, Variables> {

  override val flow: Flow<QuerySubscriptionResult<Data, Variables>> = flow {
    val realtimeQueryManager = query.dataConnect.realtimeQueryManagerUnlessClosed ?: return@flow

    val dataResultFlow: Flow<Result<Data>> = realtimeQueryManager.subscribe(query)

    val queryResultFlow =
      dataResultFlow.map { dataResult ->
        dataResult.throwIfCancellationException()
        val queryResult = dataResult.map { query.RealtimeQueryResultImpl(it, DataSource.SERVER) }
        RealtimeQuerySubscriptionResultImpl(query, queryResult)
      }

    emitAll(queryResultFlow)
  }

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString(): String = "RealtimeQuerySubscriptionImpl(query=$query)"

  private inner class RealtimeQuerySubscriptionResultImpl(
    override val query: RealtimeQueryRefImpl<Data, Variables>,
    override val result: Result<RealtimeQueryRefImpl<Data, Variables>.RealtimeQueryResultImpl>,
  ) : QuerySubscriptionResult<Data, Variables> {

    override fun equals(other: Any?) =
      other is RealtimeQuerySubscriptionImpl<*, *>.RealtimeQuerySubscriptionResultImpl &&
        other.query == query &&
        other.result == result

    override fun hashCode() =
      Objects.hash(RealtimeQuerySubscriptionResultImpl::class, query, result)

    override fun toString() = "RealtimeQuerySubscriptionResultImpl(query=$query, result=$result)"
  }
}
