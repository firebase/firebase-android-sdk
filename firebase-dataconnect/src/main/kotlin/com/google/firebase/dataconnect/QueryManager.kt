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

import com.google.protobuf.Struct
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class QueryManager(grpcClient: DataConnectGrpcClient, coroutineScope: CoroutineScope) {
  private val queryStates = QueryStates(grpcClient, coroutineScope)

  suspend fun <V, D> execute(ref: QueryRef<V, D>, variables: V): DataConnectResult<V, D> =
    queryStates.letQueryState(ref, variables) { it.execute() }.toDataConnectResult(ref, variables)

}

private data class QueryStateKey(val operationName: String, val variablesSha512: String)

private class QueryState(
  val key: QueryStateKey,
  private val grpcClient: DataConnectGrpcClient,
  private val operationName: String,
  private val variables: Struct,
  private val coroutineScope: CoroutineScope,
) {
  private val mutex = Mutex()
  private var job: Deferred<DataConnectGrpcClient.OperationResult>? = null

  suspend fun execute(): DataConnectGrpcClient.OperationResult {
    // Wait for the current job to complete (if any), and ignore its result. Waiting avoids running
    // multiple queries in parallel, which would not scale.
    val originalJob = mutex.withLock { job }?.also { it.join() }

    // Now that the job that was in progress when this method started has completed, we can run our
    // own query. But we're racing with other concurrent invocations of this method. The first one
    // wins and launches the new job, then awaits its completion; the others simply await
    // completion of the new job that was started by the winner.
    val newJob =
      mutex.withLock {
        job.let { currentJob ->
          if (currentJob !== null && currentJob !== originalJob) {
            currentJob
          } else {
            coroutineScope
              .async {
                grpcClient.executeQuery(operationName = operationName, variables = variables)
              }
              .also { newJob -> job = newJob }
          }
        }
      }

    return newJob.await()
  }
}

private class QueryStates(
  private val grpcClient: DataConnectGrpcClient,
  private val coroutineScope: CoroutineScope
) {
  private val mutex = Mutex()

  // NOTE: All accesses to `queryStateByKey` and the `refCount` field of each value MUST be done
  // from a coroutine that has locked `mutex`; otherwise, such accesses (both reads and writes) are
  // data races and yield undefined behavior.
  private val queryStateByKey = mutableMapOf<QueryStateKey, ReferenceCounted<QueryState>>()

  suspend fun <V, D, R> letQueryState(
    ref: QueryRef<V, D>,
    variables: V,
    block: suspend (QueryState) -> R
  ): R {
    val queryState = mutex.withLock { acquireQueryState(ref, variables) }

    return try {
      block(queryState)
    } finally {
      mutex.withLock { withContext(NonCancellable) { releaseQueryState(queryState) } }
    }
  }

  // NOTE: This function MUST be called from a coroutine that has locked `mutex`.
  private fun <V, D> acquireQueryState(ref: QueryRef<V, D>, variables: V): QueryState {
    val variablesStruct = encodeToStruct(ref.variablesSerializer, variables)
    val variablesSha512 = calculateSha512(variablesStruct).toHexString()
    val key = QueryStateKey(operationName = ref.operationName, variablesSha512 = variablesSha512)

    val queryState =
      queryStateByKey.getOrPut(key) {
        ReferenceCounted(
          QueryState(
            key = key,
            grpcClient = grpcClient,
            operationName = ref.operationName,
            variables = variablesStruct,
            coroutineScope = coroutineScope,
          ),
          refCount = 0
        )
      }

    queryState.refCount++

    return queryState.obj
  }

  // NOTE: This function MUST be called from a coroutine that has locked `mutex`.
  private fun releaseQueryState(queryState: QueryState) {
    val referenceCountedQueryState =
      queryStateByKey[queryState.key].let {
        if (it === null) {
          error("unexpected null QueryState for key: ${queryState.key}")
        } else if (it.obj !== queryState) {
          error("unexpected QueryState for key: ${queryState.key}: $it")
        } else {
          it
        }
      }

    referenceCountedQueryState.refCount--
    if (referenceCountedQueryState.refCount == 0) {
      queryStateByKey.remove(queryState.key)
    }
  }
}

private fun <V, D> DataConnectGrpcClient.OperationResult.toDataConnectResult(
  ref: QueryRef<V, D>,
  variables: V
): DataConnectResult<V, D> {
  if (data === null) {
    // TODO: include the variables and error list in the thrown exception
    throw DataConnectException("no data included in result: errors=${errors}")
  }

  return DataConnectResult(
    variables = variables,
    data = decodeFromStruct(ref.dataDeserializer, data),
    errors = errors,
  )
}
