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
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.*
import kotlinx.serialization.DeserializationStrategy

internal class QueryManager(
  grpcClient: DataConnectGrpcClient,
  coroutineScope: CoroutineScope,
  blockingDispatcher: CoroutineDispatcher,
  nonBlockingDispatcher: CoroutineDispatcher,
  creatorLoggerId: String
) {
  private val logger = Logger("QueryManager").apply { debug { "Created from $creatorLoggerId" } }

  private val liveQueries =
    LiveQueries(
      grpcClient = grpcClient,
      coroutineScope = coroutineScope,
      blockingDispatcher = blockingDispatcher,
      nonBlockingDispatcher = nonBlockingDispatcher,
      logger = logger
    )

  suspend fun <V, D> execute(ref: QueryRef<V, D>, variables: V): DataConnectResult<V, D> =
    liveQueries
      .withLiveQuery(ref, variables) { it.execute(ref.dataDeserializer) }
      .toDataConnectResult(variables)

  suspend fun <V, D> onResult(
    ref: QueryRef<V, D>,
    variables: V,
    sinceSequenceNumber: Long?,
    executeQuery: Boolean,
    callback: suspend (DataConnectResult<V, D>) -> Unit,
  ): Nothing =
    liveQueries.withLiveQuery(ref, variables) { liveQuery ->
      liveQuery.onResult(
        ref.dataDeserializer,
        sinceSequenceNumber = sinceSequenceNumber,
        executeQuery = executeQuery
      ) {
        callback(it.toDataConnectResult(variables))
      }
    }
}

private class LiveQuery(
  val key: Key,
  private val grpcClient: DataConnectGrpcClient,
  private val operationName: String,
  private val variables: Struct,
  coroutineScope: CoroutineScope,
  private val blockingDispatcher: CoroutineDispatcher,
  nonBlockingDispatcher: CoroutineDispatcher,
  private val logger: Logger,
) : AutoCloseable {
  private val coroutineScope =
    CoroutineScope(
      SupervisorJob(coroutineScope.coroutineContext[Job]) +
        nonBlockingDispatcher +
        CoroutineName("LiveQuery[$operationName ${variables.toCompactString()}]")
    )

  // The `dataDeserializers` list may be safely read concurrently from multiple threads, as it uses
  // a `CopyOnWriteArrayList` that is completely thread-safe. Any mutating operations must be
  // performed while the `dataDeserializersWriteMutex` mutex is locked, so that
  // read-write-modify operations can be done atomically.
  private val dataDeserializersWriteMutex = Mutex()
  private val dataDeserializers = CopyOnWriteArrayList<RegisteredDataDeserialzer<*>>()

  private val jobMutex = Mutex()
  private var job: Job? = null

  suspend fun <T> execute(
    dataDeserializer: DeserializationStrategy<T>
  ): DeserialzedOperationResult<T> {
    // Register the data deserialzier _before_ waiting for the current job to complete. This
    // guarantees that the deserializer will be registered by the time the subsequent job (`newJob`
    // below) runs.
    val registeredDataDeserializer = registerDataDeserializer(dataDeserializer)

    // Wait for the current job to complete (if any), and ignore its result. Waiting avoids running
    // multiple queries in parallel, which would not scale.
    val originalJob = jobMutex.withLock { job }?.also { it.join() }

    // Now that the job that was in progress when this method started has completed, we can run our
    // own query. But we're racing with other concurrent invocations of this method. The first one
    // wins and launches the new job, then awaits its completion; the others simply await completion
    // of the new job that was started by the winner.
    val newJob =
      jobMutex.withLock {
        job.let { currentJob ->
          if (currentJob !== null && currentJob !== originalJob) {
            currentJob
          } else {
            coroutineScope.async { doExecute() }.also { newJob -> job = newJob }
          }
        }
      }

    newJob.join()

    return registeredDataDeserializer.getLatestUpdate()!!.getOrThrow()
  }

  suspend fun <T> onResult(
    dataDeserializer: DeserializationStrategy<T>,
    sinceSequenceNumber: Long?,
    executeQuery: Boolean,
    callback: suspend (DeserialzedOperationResult<T>) -> Unit,
  ): Nothing {
    val registeredDataDeserializer = registerDataDeserializer(dataDeserializer)

    // Immediately deliver the most recent update to the callback, so the collector has some data
    // to work with while waiting for the network requests to complete.
    val cachedUpdate = registeredDataDeserializer.getLatestSuccessfulUpdate()
    val effectiveSinceSequenceNumber =
      if (cachedUpdate === null) {
        sinceSequenceNumber
      } else if (
        sinceSequenceNumber !== null && sinceSequenceNumber >= cachedUpdate.sequenceNumber
      ) {
        sinceSequenceNumber
      } else {
        callback(cachedUpdate)
        cachedUpdate.sequenceNumber
      }

    // Execute the query _after_ delivering the cached result, so that collectors deterministically
    // get invoked with cached results first (if any), then updated results after the query
    // executes.
    if (executeQuery) {
      coroutineScope.launch { runCatching { execute(dataDeserializer) } }
    }

    registeredDataDeserializer.onSuccessfulUpdate(
      sinceSequenceNumber = effectiveSinceSequenceNumber
    ) {
      callback(it)
    }
  }

  private suspend fun doExecute() {
    val requestId = Random.nextAlphanumericString()
    val sequenceNumber = nextSequenceNumber()

    val executeQueryResult =
      grpcClient.runCatching {
        executeQuery(
          requestId = requestId,
          operationName = operationName,
          variables = variables,
          sequenceNumber = sequenceNumber
        )
      }

    dataDeserializers.iterator().forEach {
      it.update(requestId, sequenceNumber, executeQueryResult)
    }
  }

  @Suppress("UNCHECKED_CAST")
  private suspend fun <T> registerDataDeserializer(
    dataDeserializer: DeserializationStrategy<T>
  ): RegisteredDataDeserialzer<T> =
    // First, check if the deserializer is already registered and, if it is, just return it.
    // Otherwise, lock the "write" mutex and register it. We still have to check again if it is
    // already registered because another thread could have concurrently registered it since we last
    // checked above.
    dataDeserializers
      .firstOrNull { it.deserializer === dataDeserializer }
      ?.let { it as RegisteredDataDeserialzer<T> }
      ?: dataDeserializersWriteMutex.withLock {
        dataDeserializers
          .firstOrNull { it.deserializer === dataDeserializer }
          ?.let { it as RegisteredDataDeserialzer<T> }
          ?: RegisteredDataDeserialzer(dataDeserializer, blockingDispatcher, logger).also {
            dataDeserializers.add(it)
          }
      }

  data class Key(val operationName: String, val variablesHash: String)

  override fun close() {
    coroutineScope.cancel()
  }
}

private class RegisteredDataDeserialzer<T>(
  val deserializer: DeserializationStrategy<T>,
  private val deserializationDispatcher: CoroutineDispatcher,
  private val logger: Logger
) {
  // A flow that emits a value every time that there is an update, either a successful or an
  // unsuccessful update. There is no replay cache in this shared flow because there is no way to
  // atomically emit a new event and ensure that it has a larger sequence number, and we don't want
  // to "replay" an older result. Use `latestUpdate` instead of relying on the replay cache.
  private val updates =
    MutableSharedFlow<Update<T>>(
      replay = 0,
      extraBufferCapacity = Int.MAX_VALUE,
      onBufferOverflow = BufferOverflow.SUSPEND,
    )

  // The latest update (i.e. the update with the highest sequence number) that has ever been emitted
  // to `updates`. The `ref` of the value will be null if, and only if, no updates have ever
  // occurred.
  private val latestUpdate = MutableStateFlow<NullableReference<Update<T>>>(NullableReference(null))

  // The same as `latestUpdate`, except that it only store the latest _successful_ update. That is,
  // if there was a successful update followed by a failed update then the value of this flow would
  // be that successful update, whereas `latestUpdate` would store the failed one.
  //
  // This flow is updated by initializing the lazy value from `latestUpdate`; therefore, make sure
  // to initialize the lazy value from `latestUpdate` before getting this flow's value.
  private val latestSuccessfulUpdate =
    MutableStateFlow<NullableReference<DeserialzedOperationResult<T>>>(NullableReference(null))

  data class Update<T>(
    val sequenceNumber: Long,
    val result: SuspendingLazy<Result<DeserialzedOperationResult<T>>>
  )

  fun update(requestId: String, sequenceNumber: Long, result: Result<OperationResult>) {
    val newUpdate =
      Update(sequenceNumber = sequenceNumber, result = lazyDeserialize(requestId, result))

    // Use a compare-and-swap ("CAS") loop to ensure that an old update never clobbers a newer one.
    while (true) {
      val currentUpdate = latestUpdate.value
      if (currentUpdate.ref !== null && currentUpdate.ref.sequenceNumber > sequenceNumber) {
        break // don't clobber a newer update with an older one
      }
      if (latestUpdate.compareAndSet(currentUpdate, NullableReference(newUpdate))) {
        break
      }
    }

    // Emit to the `updates` shared flow _after_ setting `latestUpdate` to avoid others missing
    // the latest update.
    val emitSucceeded = updates.tryEmit(newUpdate)
    check(emitSucceeded) { "updates.tryEmit(newUpdate) should have returned true" }
  }

  suspend fun getLatestUpdate(): Result<DeserialzedOperationResult<T>>? =
    latestUpdate.value.ref?.result?.getValue()

  suspend fun getLatestSuccessfulUpdate(): DeserialzedOperationResult<T>? {
    // Call getLatestUpdate() to populate `latestSuccessfulUpdate` with the most recent update.
    getLatestUpdate()
    return latestSuccessfulUpdate.value.ref
  }

  suspend fun onSuccessfulUpdate(
    sinceSequenceNumber: Long?,
    callback: suspend (DeserialzedOperationResult<T>) -> Unit
  ): Nothing {
    var lastSequenceNumber = sinceSequenceNumber ?: Long.MIN_VALUE
    updates
      .onSubscription { latestUpdate.value.ref?.let { emit(it) } }
      .collect { update ->
        if (update.sequenceNumber > lastSequenceNumber) {
          update.result.getValue().onSuccess { deserializedOperationResult ->
            lastSequenceNumber = deserializedOperationResult.sequenceNumber
            callback(deserializedOperationResult)
          }
        }
      }
  }

  private fun lazyDeserialize(
    requestId: String,
    result: Result<OperationResult>
  ): SuspendingLazy<Result<DeserialzedOperationResult<T>>> = SuspendingLazy {
    result
      .mapCatching { withContext(deserializationDispatcher) { it.deserialize(deserializer) } }
      .onFailure {
        // If the overall result was successful then the failure _must_ have occurred during
        // deserialization. Log the deserialization failure so it doesn't go unnoticed.
        if (result.isSuccess) {
          logger.warn(it) { "executeQuery() [rid=$requestId] decoding response data failed: $it" }
        }
      }
      .onSuccess {
        // Update the latest successful update. Set the value in a compare-and-swap loop to ensure
        // that an older result does not clobber a newer one.
        while (true) {
          val latestSuccessful = latestSuccessfulUpdate.value
          if (
            latestSuccessful.ref !== null &&
              it.sequenceNumber <= latestSuccessful.ref.sequenceNumber
          ) {
            break
          }
          if (latestSuccessfulUpdate.compareAndSet(latestSuccessful, NullableReference(it))) {
            break
          }
        }
      }
  }
}

private class LiveQueries(
  private val grpcClient: DataConnectGrpcClient,
  private val coroutineScope: CoroutineScope,
  private val blockingDispatcher: CoroutineDispatcher,
  private val nonBlockingDispatcher: CoroutineDispatcher,
  private val logger: Logger,
) {
  private val mutex = Mutex()

  // NOTE: All accesses to `referenceCountedLiveQueryByKey` and the `refCount` field of each value
  // MUST be done from a coroutine that has locked `mutex`; otherwise, such accesses (both reads and
  // writes) are data races and yield undefined behavior.
  private val referenceCountedLiveQueryByKey =
    mutableMapOf<LiveQuery.Key, ReferenceCounted<LiveQuery>>()

  suspend fun <V, D, R> withLiveQuery(
    ref: QueryRef<V, D>,
    variables: V,
    block: suspend (LiveQuery) -> R
  ): R {
    val liveQuery = mutex.withLock { acquireLiveQuery(ref, variables) }

    return try {
      block(liveQuery)
    } finally {
      mutex.withLock { withContext(NonCancellable) { releaseLiveQuery(liveQuery) } }
    }
  }

  // NOTE: This function MUST be called from a coroutine that has locked `mutex`.
  private fun <V, D> acquireLiveQuery(ref: QueryRef<V, D>, variables: V): LiveQuery {
    val variablesStruct =
      if (ref.variablesSerializer === DataConnectUntypedVariables.Serializer) {
        (variables as DataConnectUntypedVariables).variables.toStructProto()
      } else {
        encodeToStruct(ref.variablesSerializer, variables)
      }
    val variablesHash = variablesStruct.calculateSha512().toAlphaNumericString()
    val key = LiveQuery.Key(operationName = ref.operationName, variablesHash = variablesHash)

    val referenceCountedLiveQuery =
      referenceCountedLiveQueryByKey.getOrPut(key) {
        ReferenceCounted(
          LiveQuery(
            key = key,
            grpcClient = grpcClient,
            operationName = ref.operationName,
            variables = variablesStruct,
            coroutineScope = coroutineScope,
            blockingDispatcher = blockingDispatcher,
            nonBlockingDispatcher = nonBlockingDispatcher,
            logger = logger,
          ),
          refCount = 0
        )
      }

    referenceCountedLiveQuery.refCount++

    return referenceCountedLiveQuery.obj
  }

  // NOTE: This function MUST be called from a coroutine that has locked `mutex`.
  private fun releaseLiveQuery(liveQuery: LiveQuery) {
    val referenceCountedLiveQuery = referenceCountedLiveQueryByKey[liveQuery.key]

    if (referenceCountedLiveQuery === null) {
      error("unexpected null LiveQuery for key: ${liveQuery.key}")
    } else if (referenceCountedLiveQuery.obj !== liveQuery) {
      error("unexpected LiveQuery for key: ${liveQuery.key}: ${referenceCountedLiveQuery.obj}")
    }

    referenceCountedLiveQuery.refCount--
    if (referenceCountedLiveQuery.refCount == 0) {
      referenceCountedLiveQueryByKey.remove(liveQuery.key)
      liveQuery.close()
    }
  }
}
