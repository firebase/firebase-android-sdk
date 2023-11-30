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

import com.google.firebase.dataconnect.DataConnectGrpcClient.DeserialzedOperationResult
import com.google.firebase.dataconnect.DataConnectGrpcClient.OperationResult
import com.google.protobuf.Struct
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy

internal class QueryManager(
  grpcClient: DataConnectGrpcClient,
  coroutineScope: CoroutineScope,
  creatorLoggerId: String
) {
  private val logger = Logger("QueryManager").apply { debug { "Created from $creatorLoggerId" } }

  private val queryStates = QueryStates(grpcClient, coroutineScope, logger)

  suspend fun <V, D> execute(ref: QueryRef<V, D>, variables: V): DataConnectResult<V, D> =
    queryStates
      .withQueryState(ref, variables) { it.execute(ref.dataDeserializer) }
      .toDataConnectResult(variables)

  suspend fun <V, D> collectResults(
    ref: QueryRef<V, D>,
    variables: V,
    collector: FlowCollector<DataConnectResult<V, D>>
  ) {
    queryStates.withQueryState(ref, variables) {
      it.collectResults(ref.dataDeserializer, collector) { toDataConnectResult(variables) }
    }
  }

  suspend fun <V, D> collectExceptions(
    ref: QueryRef<V, D>,
    variables: V,
    collector: FlowCollector<Throwable?>
  ) {
    queryStates.withQueryState(ref, variables) {
      it.collectExceptions(ref.dataDeserializer, collector)
    }
  }
}

private data class QueryStateKey(val operationName: String, val variablesHash: String)

private class QueryState(
  val key: QueryStateKey,
  private val grpcClient: DataConnectGrpcClient,
  private val operationName: String,
  private val variables: Struct,
  private val coroutineScope: CoroutineScope,
  private val logger: Logger,
) {
  private val mutex = Mutex()
  private var job: Deferred<OperationResult>? = null

  private val dataDeserializers = CopyOnWriteArrayList<DeserialzerInfo<*>>()

  private val operationResultFlow =
    MutableSharedFlow<OperationResult>(
      replay = 1,
      extraBufferCapacity = Int.MAX_VALUE,
      onBufferOverflow = BufferOverflow.SUSPEND
    )

  private val exceptionFlow =
    MutableSharedFlow<Throwable?>(
      replay = 1,
      extraBufferCapacity = Int.MAX_VALUE,
      onBufferOverflow = BufferOverflow.SUSPEND
    )

  suspend fun <T> execute(
    dataDeserializer: DeserializationStrategy<T>
  ): DeserialzedOperationResult<T> {
    // Wait for the current job to complete (if any), and ignore its result. Waiting avoids running
    // multiple queries in parallel, which would not scale.
    val originalJob = mutex.withLock { job }?.also { it.join() }

    // Now that the job that was in progress when this method started has completed, we can run our
    // own query. But we're racing with other concurrent invocations of this method. The first one
    // wins and launches the new job, then awaits its completion; the others simply await
    // completion of the new job that was started by the winner.
    val newJob =
      mutex.withLock {
        registerDataDeserializer(dataDeserializer)

        job.let { currentJob ->
          if (currentJob !== null && currentJob !== originalJob) {
            currentJob
          } else {
            coroutineScope.async { doExecute() }.also { newJob -> job = newJob }
          }
        }
      }

    // TODO: As an optimization, avoid calling deserialize() if the data was already deserialized
    // by someone else.
    return newJob.await().deserialize(dataDeserializer)
  }

  private suspend fun doExecute(): OperationResult {
    val requestId = Random.nextAlphanumericString()

    val executeQueryResult =
      grpcClient.runCatching {
        executeQuery(requestId = requestId, operationName = operationName, variables = variables)
      }

    mutex
      .withLock { dataDeserializers.iterator() }
      .forEach { it.update(requestId, executeQueryResult) }

    return executeQueryResult.fold(
      onSuccess = {
        operationResultFlow.emit(it)
        exceptionFlow.emit(null)
        it
      },
      onFailure = {
        exceptionFlow.emit(it)
        throw it
      }
    )
  }

  suspend fun <T, R> collectResults(
    dataDeserializer: DeserializationStrategy<T>,
    collector: FlowCollector<R>,
    mapResult: DeserialzedOperationResult<T>.() -> R
  ) =
    mutex
      .withLock { registerDataDeserializer(dataDeserializer) }
      .resultFlow
      .map { mapResult(it) }
      .collect(collector)

  suspend fun collectExceptions(
    dataDeserializer: DeserializationStrategy<*>,
    collector: FlowCollector<Throwable?>
  ) =
    mutex
      .withLock { registerDataDeserializer(dataDeserializer) }
      .exceptionFlow
      .map { it }
      .collect(collector)

  // NOTE: This function MUST be called by a coroutine that has `mutex` locked; otherwise, a data
  // race will occur, resulting in undefined behavior.
  @Suppress("UNCHECKED_CAST")
  private fun <T> registerDataDeserializer(
    dataDeserializer: DeserializationStrategy<T>
  ): DeserialzerInfo<T> =
    dataDeserializers.firstOrNull { it.deserializer === dataDeserializer } as? DeserialzerInfo<T>
      ?: DeserialzerInfo(dataDeserializer, logger).also { dataDeserializers.add(it) }

  private class DeserialzerInfo<T>(
    val deserializer: DeserializationStrategy<T>,
    private val logger: Logger
  ) {
    private val _resultFlow =
      MutableSharedFlow<DeserialzedOperationResult<T>>(
        replay = 1,
        extraBufferCapacity = Int.MAX_VALUE,
        onBufferOverflow = BufferOverflow.SUSPEND,
      )

    val resultFlow = _resultFlow.asSharedFlow()

    val _exceptionFlow =
      MutableSharedFlow<Throwable?>(
        replay = 1,
        extraBufferCapacity = Int.MAX_VALUE,
        onBufferOverflow = BufferOverflow.SUSPEND,
      )

    val exceptionFlow = _exceptionFlow.asSharedFlow()

    suspend fun update(requestId: String, result: Result<OperationResult>) {
      result.fold(
        onFailure = { _exceptionFlow.emit(it) },
        onSuccess = { operationResult ->
          operationResult
            .runCatching { deserialize(deserializer) }
            .fold(
              onSuccess = { deserializedOperationResult ->
                _resultFlow.emit(deserializedOperationResult)
                _exceptionFlow.emit(null)
              },
              onFailure = { deserializeException ->
                logger.warn(deserializeException) {
                  "executeQuery() [rid=$requestId] " + "decoding response data failed: $deserializeException"
                }
                _exceptionFlow.emit(deserializeException)
              }
            )
        },
      )
    }
  }
}

private class QueryStates(
  private val grpcClient: DataConnectGrpcClient,
  private val coroutineScope: CoroutineScope,
  private val logger: Logger,
) {
  private val mutex = Mutex()

  // NOTE: All accesses to `queryStateByKey` and the `refCount` field of each value MUST be done
  // from a coroutine that has locked `mutex`; otherwise, such accesses (both reads and writes) are
  // data races and yield undefined behavior.
  private val queryStateByKey = mutableMapOf<QueryStateKey, ReferenceCounted<QueryState>>()

  suspend fun <V, D, R> withQueryState(
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
    val variablesHash = variablesStruct.calculateSha512().toAlphaNumericString()
    val key = QueryStateKey(operationName = ref.operationName, variablesHash = variablesHash)

    val queryState =
      queryStateByKey.getOrPut(key) {
        ReferenceCounted(
          QueryState(
            key = key,
            grpcClient = grpcClient,
            operationName = ref.operationName,
            variables = variablesStruct,
            coroutineScope = coroutineScope,
            logger = logger,
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
