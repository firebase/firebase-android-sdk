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
internal constructor(internal val query: Query<Response, Variables>, variables: Variables) {
  private val _variables = MutableStateFlow(variables)
  public val variables: Variables by _variables::value

  private val _lastResult = MutableStateFlow<DataConnectResult<Response, Variables>?>(null)
  public val lastResult: DataConnectResult<Response, Variables>? by _lastResult::value

  // Each collection of this flow triggers an implicit `reload()`.
  public val resultFlow: Flow<DataConnectResult<Response, Variables>> = channelFlow {
    val cachedResult = lastResult?.also { send(it) }

    var collectJob: Job? = null
    _variables.collect { variables ->
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
          variables,
          sinceSequenceNumber = cachedResult?.sequenceNumber,
          executeQuery = shouldExecuteQuery
        ) {
          updateLastResult(it)
          send(it)
        }
      }
    }
  }

  public suspend fun reload() {
    val queryManager = query.dataConnect.lazyQueryManager.get()
    val result = queryManager.execute(query, variables)
    updateLastResult(result)
  }

  public suspend fun update(variables: Variables) {
    _variables.value = variables
    reload()
  }

  private fun updateLastResult(newLastResult: DataConnectResult<Response, Variables>) {
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
