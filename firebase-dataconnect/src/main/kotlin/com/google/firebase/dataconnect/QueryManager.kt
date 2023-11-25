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
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class QueryManager(
  private val grpcClient: DataConnectGrpcClient,
  private val coroutineScope: CoroutineScope
) {

  private val mutex = Mutex()
  private val queryStateByQuery = mutableMapOf<QueryStateKey, QueryState>()

  suspend fun <V, D> execute(ref: QueryRef<V, D>, variables: V): DataConnectResult<V, D> {
    val variablesStruct = encodeToStruct(ref.variablesSerializer, variables)
    val variablesSha512 = calculateSha512(variablesStruct).toHexString()
    val queryStateKey =
      QueryStateKey(operationName = ref.operationName, variablesSha512 = variablesSha512)

    val queryState =
      mutex.withLock {
        queryStateByQuery.getOrPut(queryStateKey) {
          QueryState(
            grpcClient = grpcClient,
            operationName = ref.operationName,
            variables = variablesStruct,
            coroutineScope = coroutineScope,
          )
        }
      }

    val operationResult = queryState.execute()
    if (operationResult.data === null) {
      // TODO: include the variables and error list in the thrown exception
      throw DataConnectException("no data included in result")
    }

    return DataConnectResult(
      variables = variables,
      data = decodeFromStruct(ref.dataDeserializer, operationResult.data),
      errors = operationResult.errors,
    )
  }
}

private data class QueryStateKey(val operationName: String, val variablesSha512: String)

private class QueryState(
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
    // wins  and launches the new job, then awaits its completion; the others simply await
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
