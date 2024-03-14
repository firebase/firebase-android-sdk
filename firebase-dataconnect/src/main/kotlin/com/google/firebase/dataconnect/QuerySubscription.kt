// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.firebase.dataconnect

import java.util.Objects
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

public class QuerySubscription<Data, Variables>
internal constructor(query: QueryRef<Data, Variables>) {
  private val _query = MutableStateFlow(query)
  public val query: QueryRef<Data, Variables> by _query::value

  private val _lastResult =
    MutableStateFlow(
      NullableReference<SequencedReference<QuerySubscriptionResult<Data, Variables>>>()
    )
  public val lastResult: QuerySubscriptionResult<Data, Variables>?
    get() = _lastResult.value.ref?.ref

  // Each collection of this flow triggers an implicit `reload()`.
  public val resultFlow: Flow<QuerySubscriptionResult<Data, Variables>> = channelFlow {
    val cachedResult = lastResult?.also { send(it) }

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
          sequencedResult
            .map { it.toQuerySubscriptionResult(query) }
            .let {
              send(it.ref)
              updateLastResult(it)
            }
        }
      }
    }
  }

  public suspend fun reload() {
    val query = query // save query to a local variable in case it changes.
    val result = query.dataConnect.lazyQueryManager.get().execute(query)
    updateLastResult(result.map { it.toQuerySubscriptionResult(query) })
  }

  public suspend fun update(variables: Variables) {
    _query.value = _query.value.withVariables(variables)
    reload()
  }

  private fun updateLastResult(
    newLastResult: SequencedReference<QuerySubscriptionResult<Data, Variables>>
  ) {
    // Update the last result in a compare-and-swap loop so that there is no possibility of
    // clobbering a newer result with an older result, compared using their sequence numbers.
    while (true) {
      val oldLastResult = _lastResult.value
      if (
        oldLastResult.ref !== null &&
          oldLastResult.ref.sequenceNumber >= newLastResult.sequenceNumber
      ) {
        return
      }
      if (_lastResult.compareAndSet(oldLastResult, NullableReference(newLastResult))) {
        return
      }
    }
  }

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString(): String = "QuerySubscription(query=$query)"

  private fun Result<Data>.toQuerySubscriptionResult(
    query: QueryRef<Data, Variables>
  ): QuerySubscriptionResult<Data, Variables> =
    fold(
      onSuccess = {
        QuerySubscriptionResult.Success(this@QuerySubscription, DataConnectQueryResult(it, query))
      },
      onFailure = {
        QuerySubscriptionResult.Failure(this@QuerySubscription, it as DataConnectException)
      }
    )

  private data class State<Data, Variables>(
    val query: QueryRef<Data, Variables>,
    val lastResult: QuerySubscriptionResult<Data, Variables>?,
    val lastSuccessfulResult: DataConnectQueryResult<Data, Variables>?,
  )
}

public sealed class QuerySubscriptionResult<Data, Variables>
protected constructor(public val subscription: QuerySubscription<Data, Variables>) {

  // Implement `equals()` to simply use object identity of the `QuerySubscription`.
  // Since `QuerySubscription` is stateful, it has no meaningful concept of "equality" so
  // `QuerySubscriptionResult` just uses object identity to determine equality. That is, two
  // `QuerySubscriptionResult` objects that are identical, except were produced by different
  // `QuerySubscription` objects, are considered to be unequal.
  override fun equals(other: Any?): Boolean =
    other is QuerySubscriptionResult<*, *> && other.subscription === subscription

  override fun hashCode(): Int =
    Objects.hash("QuerySubscriptionResult", System.identityHashCode(subscription))

  override fun toString(): String = "QuerySubscriptionResult(subscription=$subscription)"

  public class Success<Data, Variables>
  internal constructor(
    subscription: QuerySubscription<Data, Variables>,
    public val result: DataConnectQueryResult<Data, Variables>
  ) : QuerySubscriptionResult<Data, Variables>(subscription) {

    override fun equals(other: Any?): Boolean =
      other is Success<*, *> && super.equals(other) && other.result == result

    override fun hashCode(): Int = Objects.hash("Success", super.hashCode(), result)

    override fun toString(): String =
      "QuerySubscriptionResult.Success(result=$result, subscription=$subscription)"
  }

  public class Failure<Data, Variables>
  internal constructor(
    subscription: QuerySubscription<Data, Variables>,
    public val exception: DataConnectException
  ) : QuerySubscriptionResult<Data, Variables>(subscription) {

    override fun equals(other: Any?): Boolean =
      other is Failure<*, *> && super.equals(other) && other.exception == exception

    override fun hashCode(): Int = Objects.hash("Failure", super.hashCode(), exception)

    override fun toString(): String =
      "QuerySubscriptionResult.Failure(exception=$exception, subscription=$subscription)"
  }
}
