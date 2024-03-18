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
package com.google.firebase.dataconnect.core

import com.google.firebase.dataconnect.*
import com.google.firebase.dataconnect.core.DataConnectGrpcClient.OperationResult
import com.google.firebase.dataconnect.util.*
import com.google.firebase.util.nextAlphanumericString
import com.google.protobuf.Struct
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.*
import kotlinx.serialization.DeserializationStrategy

internal class OldQueryManager(dataConnect: FirebaseDataConnectInternal) {
  private val logger =
    Logger("OldQueryManager").apply { debug { "Created by ${dataConnect.logger.nameWithId}" } }

  private val liveQueries = LiveQueries(dataConnect, parentLogger = logger)

  suspend fun <Data, Variables> execute(
    query: QueryRef<Data, Variables>
  ): SequencedReference<Result<Data>> =
    liveQueries.withLiveQuery(query) { it.execute(query.dataDeserializer) }

  suspend fun <Data, Variables> subscribe(
    query: QueryRef<Data, Variables>,
    executeQuery: Boolean,
    callback: suspend (SequencedReference<Result<Data>>) -> Unit,
  ): Nothing =
    liveQueries.withLiveQuery(query) { liveQuery ->
      liveQuery.subscribe(query.dataDeserializer, executeQuery = executeQuery, callback = callback)
    }
}

private class LiveQuery(
  private val dataConnect: FirebaseDataConnectInternal,
  val key: Key,
  private val operationName: String,
  private val variables: Struct,
  parentLogger: Logger,
) : AutoCloseable {
  private val logger =
    Logger("LiveQuery").apply {
      debug { "Created by ${parentLogger.nameWithId} " + "with key: $key" }
    }

  private val coroutineScope =
    CoroutineScope(
      SupervisorJob(dataConnect.coroutineScope.coroutineContext[Job]) +
        dataConnect.nonBlockingDispatcher +
        CoroutineName("LiveQuery[$operationName ${variables.toCompactString()}]")
    )

  // The `dataDeserializers` list may be safely read concurrently from multiple threads, as it uses
  // a `CopyOnWriteArrayList` that is completely thread-safe. Any mutating operations must be
  // performed while the `dataDeserializersWriteMutex` mutex is locked, so that
  // read-write-modify operations can be done atomically.
  private val dataDeserializersWriteMutex = Mutex()
  private val dataDeserializers = CopyOnWriteArrayList<RegisteredDataDeserialzer<*>>()
  private data class Update(
    val requestId: String,
    val sequencedResult: SequencedReference<Result<OperationResult>>
  )
  // Also, `initialDataDeserializerUpdate` must only be accessed while
  // `dataDeserializersWriteMutex` is held.
  private val initialDataDeserializerUpdate =
    MutableStateFlow<NullableReference<Update>>(NullableReference(null))

  private val jobMutex = Mutex()
  private var job: Job? = null

  suspend fun <T> execute(
    dataDeserializer: DeserializationStrategy<T>
  ): SequencedReference<Result<T>> {
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

    return registeredDataDeserializer.getLatestUpdate()!!
  }

  suspend fun <T> subscribe(
    dataDeserializer: DeserializationStrategy<T>,
    executeQuery: Boolean,
    callback: suspend (SequencedReference<Result<T>>) -> Unit,
  ): Nothing {
    val registeredDataDeserializer = registerDataDeserializer(dataDeserializer)

    // Immediately deliver the most recent update to the callback, so the collector has some data
    // to work with while waiting for the network requests to complete.
    val cachedUpdate = registeredDataDeserializer.getLatestSuccessfulUpdate()
    val effectiveSinceSequenceNumber =
      if (cachedUpdate === null) {
        0
      } else {
        callback(cachedUpdate.map { Result.success(it) })
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
      dataConnect.lazyGrpcClient.get().runCatching {
        logger.debug("Calling executeQuery() with requestId=$requestId")
        executeQuery(requestId = requestId, operationName = operationName, variables = variables)
      }

    // Normally, setting the value of `initialDataDeserializerUpdate` would be done in a compare-
    // and-swap ("CAS") loop to avoid clobbering a newer update with an older one; however, since
    // all writes _must_ be done by a coroutine with `dataDeserializersWriteMutex` locked, the CAS
    // loop isn't necessary and its value can just be set directly.
    dataDeserializersWriteMutex.withLock {
      initialDataDeserializerUpdate.value.let {
        it.ref.let { oldUpdate ->
          if (oldUpdate === null || oldUpdate.sequencedResult.sequenceNumber < sequenceNumber) {
            initialDataDeserializerUpdate.value =
              NullableReference(
                Update(requestId, SequencedReference(sequenceNumber, executeQueryResult))
              )
          }
        }
      }
    }

    dataDeserializers.iterator().forEach {
      it.update(requestId, SequencedReference(sequenceNumber, executeQueryResult))
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
      .firstOrNull { it.dataDeserializer === dataDeserializer }
      ?.let { it as RegisteredDataDeserialzer<T> }
      ?: dataDeserializersWriteMutex.withLock {
        dataDeserializers
          .firstOrNull { it.dataDeserializer === dataDeserializer }
          ?.let { it as RegisteredDataDeserialzer<T> }
          ?: Random.nextAlphanumericString().let { registrationId ->
            logger.debug {
              "Registering data deserializer $dataDeserializer " +
                "with registrationId=$registrationId"
            }
            RegisteredDataDeserialzer(
                dataConnect = dataConnect,
                registrationId = registrationId,
                dataDeserializer = dataDeserializer,
                parentLogger = logger
              )
              .also {
                dataDeserializers.add(it)
                initialDataDeserializerUpdate.value.ref?.run {
                  it.update(requestId, sequencedResult)
                }
              }
          }
      }

  data class Key(val operationName: String, val variablesHash: String)

  override fun close() {
    logger.debug("close() called")
    coroutineScope.cancel()
  }
}

private class RegisteredDataDeserialzer<T>(
  private val dataConnect: FirebaseDataConnectInternal,
  registrationId: String,
  val dataDeserializer: DeserializationStrategy<T>,
  parentLogger: Logger
) {
  private val logger =
    Logger("RegisteredDataDeserialzer").apply {
      debug { "Created by ${parentLogger.nameWithId} " + "with registrationId=$registrationId" }
    }

  // A flow that emits a value every time that there is an update, either a successful or an
  // unsuccessful update. There is no replay cache in this shared flow because there is no way to
  // atomically emit a new event and ensure that it has a larger sequence number, and we don't want
  // to "replay" an older result. Use `latestUpdate` instead of relying on the replay cache.
  private val updates =
    MutableSharedFlow<SequencedReference<SuspendingLazy<Result<T>>>>(
      replay = 0,
      extraBufferCapacity = Int.MAX_VALUE,
      onBufferOverflow = BufferOverflow.SUSPEND,
    )

  // The latest update (i.e. the update with the highest sequence number) that has ever been emitted
  // to `updates`. The `ref` of the value will be null if, and only if, no updates have ever
  // occurred.
  private val latestUpdate =
    MutableStateFlow<NullableReference<SequencedReference<SuspendingLazy<Result<T>>>>>(
      NullableReference(null)
    )

  // The same as `latestUpdate`, except that it only store the latest _successful_ update. That is,
  // if there was a successful update followed by a failed update then the value of this flow would
  // be that successful update, whereas `latestUpdate` would store the failed one.
  //
  // This flow is updated by initializing the lazy value from `latestUpdate`; therefore, make sure
  // to initialize the lazy value from `latestUpdate` before getting this flow's value.
  private val latestSuccessfulUpdate =
    MutableStateFlow<NullableReference<SequencedReference<T>>>(NullableReference(null))

  fun update(requestId: String, sequencedResult: SequencedReference<Result<OperationResult>>) {
    val newUpdate =
      SequencedReference(
        sequencedResult.sequenceNumber,
        lazyDeserialize(requestId, sequencedResult)
      )

    // Use a compare-and-swap ("CAS") loop to ensure that an old update never clobbers a newer one.
    while (true) {
      val currentUpdate = latestUpdate.value
      if (
        currentUpdate.ref !== null &&
          currentUpdate.ref.sequenceNumber > sequencedResult.sequenceNumber
      ) {
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

  suspend fun getLatestUpdate(): SequencedReference<Result<T>>? =
    latestUpdate.value.ref?.mapSuspending { it.get() }

  suspend fun getLatestSuccessfulUpdate(): SequencedReference<T>? {
    // Call getLatestUpdate() to populate `latestSuccessfulUpdate` with the most recent update.
    getLatestUpdate()
    return latestSuccessfulUpdate.value.ref
  }

  suspend fun onSuccessfulUpdate(
    sinceSequenceNumber: Long?,
    callback: suspend (SequencedReference<Result<T>>) -> Unit
  ): Nothing {
    var lastSequenceNumber = sinceSequenceNumber ?: Long.MIN_VALUE
    updates
      .onSubscription { latestUpdate.value.ref?.let { emit(it) } }
      .collect { update ->
        if (update.sequenceNumber > lastSequenceNumber) {
          lastSequenceNumber = update.sequenceNumber
          callback(update.mapSuspending { it.get() })
        }
      }
  }

  private fun lazyDeserialize(
    requestId: String,
    sequencedResult: SequencedReference<Result<OperationResult>>
  ): SuspendingLazy<Result<T>> = SuspendingLazy {
    sequencedResult.ref
      .mapCatching {
        withContext(dataConnect.blockingDispatcher) { it.deserialize(dataDeserializer) }
      }
      .onFailure {
        // If the overall result was successful then the failure _must_ have occurred during
        // deserialization. Log the deserialization failure so it doesn't go unnoticed.
        if (sequencedResult.ref.isSuccess) {
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
              sequencedResult.sequenceNumber <= latestSuccessful.ref.sequenceNumber
          ) {
            break
          }
          if (
            latestSuccessfulUpdate.compareAndSet(
              latestSuccessful,
              NullableReference(SequencedReference(sequencedResult.sequenceNumber, it))
            )
          ) {
            break
          }
        }
      }
  }
}

private class LiveQueries(
  val dataConnect: FirebaseDataConnectInternal,
  parentLogger: Logger,
) {
  private val logger =
    Logger("LiveQueries").apply { debug { "Created by ${parentLogger.nameWithId}" } }

  private val mutex = Mutex()

  // NOTE: All accesses to `referenceCountedLiveQueryByKey` and the `refCount` field of each value
  // MUST be done from a coroutine that has locked `mutex`; otherwise, such accesses (both reads and
  // writes) are data races and yield undefined behavior.
  private val referenceCountedLiveQueryByKey =
    mutableMapOf<LiveQuery.Key, ReferenceCounted<LiveQuery>>()

  suspend fun <T, V, R> withLiveQuery(query: QueryRef<R, V>, block: suspend (LiveQuery) -> T): T {
    val liveQuery = mutex.withLock { acquireLiveQuery(query) }

    return try {
      block(liveQuery)
    } finally {
      withContext(NonCancellable) { mutex.withLock { releaseLiveQuery(liveQuery) } }
    }
  }

  // NOTE: This function MUST be called from a coroutine that has locked `mutex`.
  private fun <R, V> acquireLiveQuery(query: QueryRef<R, V>): LiveQuery {
    val variablesStruct =
      if (query.variablesSerializer === DataConnectUntypedVariables.Serializer) {
        (query.variables as DataConnectUntypedVariables).variables.toStructProto()
      } else {
        encodeToStruct(query.variablesSerializer, query.variables)
      }
    val variablesHash = variablesStruct.calculateSha512().toAlphaNumericString()
    val key = LiveQuery.Key(operationName = query.operationName, variablesHash = variablesHash)

    val referenceCountedLiveQuery =
      referenceCountedLiveQueryByKey.getOrPut(key) {
        ReferenceCounted(
          LiveQuery(
            dataConnect = dataConnect,
            key = key,
            operationName = query.operationName,
            variables = variablesStruct,
            parentLogger = logger,
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
      logger.debug { "refCount==0 for LiveQuery with key=${liveQuery.key}; removing the mapping" }
      referenceCountedLiveQueryByKey.remove(liveQuery.key)
      liveQuery.close()
    }
  }
}
