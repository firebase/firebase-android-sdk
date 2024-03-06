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

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

public class QuerySubscription<Response, Variables>
internal constructor(query: QueryRef<Response, Variables>) {
  private val _query = MutableStateFlow(query)
  public val query: QueryRef<Response, Variables> by _query::value

  private val _lastResult = MutableStateFlow<DataConnectQueryResult<Response, Variables>?>(null)
  public val lastResult: DataConnectQueryResult<Response, Variables>? by _lastResult::value

  // Each collection of this flow triggers an implicit `reload()`.
  public val resultFlow: Flow<DataConnectQueryResult<Response, Variables>> = channelFlow {
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
        queryManager.onResult(
          query,
          sinceSequenceNumber = cachedResult?.sequenceNumber,
          executeQuery = shouldExecuteQuery
        ) {
          updateLastResult(it)
          send(it)
        }
      }
    }
  }

  // TODO: Replace with an actual implementation.
  public val exceptionFlow: Flow<DataConnectException?> = MutableStateFlow(null)

  public suspend fun reload() {
    val queryManager = query.dataConnect.lazyQueryManager.get()
    val result = queryManager.execute(query)
    updateLastResult(result)
  }

  public suspend fun update(variables: Variables) {
    _query.value = _query.value.withVariables(variables)
    reload()
  }

  private fun updateLastResult(newLastResult: DataConnectQueryResult<Response, Variables>) {
    // Update the last result in a compare-and-swap loop so that there is no possibility of
    // clobbering a newer result with an older result, compared using their sequence numbers.
    while (true) {
      val oldLastResult = _lastResult.value
      if (oldLastResult !== null && oldLastResult.sequenceNumber >= newLastResult.sequenceNumber) {
        return
      }
      if (_lastResult.compareAndSet(oldLastResult, newLastResult)) {
        return
      }
    }
  }
}
