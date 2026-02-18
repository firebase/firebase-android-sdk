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

package com.google.firebase.dataconnect.querymgr

import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.core.DataConnectGrpcClient
import com.google.firebase.dataconnect.core.DataConnectGrpcClient.OperationResult
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.util.NullableReference
import com.google.firebase.dataconnect.util.SequencedReference
import com.google.firebase.dataconnect.util.SequencedReference.Companion.map
import com.google.firebase.dataconnect.util.SequencedReference.Companion.nextSequenceNumber
import com.google.firebase.util.nextAlphanumericString
import com.google.protobuf.Struct
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.modules.SerializersModule

internal class LiveQuery(
  val key: Key,
  private val operationName: String,
  private val variables: Struct,
  parentCoroutineScope: CoroutineScope,
  nonBlockingCoroutineDispatcher: CoroutineDispatcher,
  private val grpcClient: DataConnectGrpcClient,
  private val registeredDataDeserializerFactory: RegisteredDataDeserializerFactory,
  parentLogger: Logger,
) : AutoCloseable {
  private val logger =
    Logger("LiveQuery").apply {
      debug {
        "created by ${parentLogger.nameWithId} with" +
          " operationName=$operationName" +
          " key=$key" +
          " grpcClient=${grpcClient.instanceId}"
      }
    }

  private val coroutineScope =
    CoroutineScope(
      SupervisorJob(parentCoroutineScope.coroutineContext[Job]) +
        nonBlockingCoroutineDispatcher +
        CoroutineName("LiveQuery[${logger.nameWithId}]")
    )

  // The `dataDeserializers` list may be safely read concurrently from multiple threads, as it uses
  // a `CopyOnWriteArrayList` that is completely thread-safe. Any mutating operations must be
  // performed while the `dataDeserializersWriteMutex` mutex is locked, so that
  // read-write-modify operations can be done atomically.
  private val dataDeserializersWriteMutex = Mutex()
  private val dataDeserializers = CopyOnWriteArrayList<RegisteredDataDeserializer<*>>()
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
    dataDeserializer: DeserializationStrategy<T>,
    dataSerializersModule: SerializersModule?,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): SequencedReference<Result<T>> {
    // Register the data deserializer _before_ waiting for the current job to complete. This
    // guarantees that the deserializer will be registered by the time the subsequent job (`newJob`
    // below) runs.
    val registeredDataDeserializer =
      registerDataDeserializer(dataDeserializer, dataSerializersModule)

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
            logger.debug { "using in-flight job to execute query" }
            currentJob
          } else {
            logger.debug { "creating new job to execute query" }
            coroutineScope.async { doExecute(callerSdkType) }.also { newJob -> job = newJob }
          }
        }
      }

    newJob.join()

    return registeredDataDeserializer.getLatestUpdate()!!
  }

  suspend fun <T> subscribe(
    dataDeserializer: DeserializationStrategy<T>,
    dataSerializersModule: SerializersModule?,
    executeQuery: Boolean,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
    callback: suspend (SequencedReference<Result<T>>) -> Unit,
  ): Nothing {
    val registeredDataDeserializer =
      registerDataDeserializer(dataDeserializer, dataSerializersModule)

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
      coroutineScope.launch {
        runCatching { execute(dataDeserializer, dataSerializersModule, callerSdkType) }
      }
    }

    registeredDataDeserializer.onSuccessfulUpdate(
      sinceSequenceNumber = effectiveSinceSequenceNumber
    ) {
      callback(it)
    }
  }

  private suspend fun doExecute(callerSdkType: FirebaseDataConnect.CallerSdkType) {
    val requestId = "qry" + Random.nextAlphanumericString(length = 10)
    val sequenceNumber = nextSequenceNumber()

    val executeQueryResult =
      grpcClient.runCatching {
        logger.debug(
          "Calling executeQuery() with requestId=$requestId callerSdkType=$callerSdkType"
        )
        executeQuery(
          requestId = requestId,
          operationName = operationName,
          variables = variables,
          callerSdkType = callerSdkType,
        )
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
    dataDeserializer: DeserializationStrategy<T>,
    dataSerializersModule: SerializersModule?,
  ): RegisteredDataDeserializer<T> =
    // First, check if the deserializer is already registered and, if it is, just return it.
    // Otherwise, lock the "write" mutex and register it. We still have to check again if it is
    // already registered because another thread could have concurrently registered it since we last
    // checked above.
    dataDeserializers
      .firstOrNull {
        it.dataDeserializer === dataDeserializer &&
          it.dataSerializersModule === dataSerializersModule
      }
      ?.let { it as RegisteredDataDeserializer<T> }
      ?: dataDeserializersWriteMutex.withLock {
        dataDeserializers
          .firstOrNull {
            it.dataDeserializer === dataDeserializer &&
              it.dataSerializersModule === dataSerializersModule
          }
          ?.let { it as RegisteredDataDeserializer<T> }
          ?: run {
            logger.debug {
              "Registering data deserializer $dataDeserializer " +
                "(dataSerializersModule=$dataSerializersModule)"
            }
            val registeredDataDeserializer =
              registeredDataDeserializerFactory.newInstance(
                dataDeserializer,
                dataSerializersModule,
                logger
              )
            dataDeserializers.add(registeredDataDeserializer)
            initialDataDeserializerUpdate.value.ref?.run {
              registeredDataDeserializer.update(requestId, sequencedResult)
            }
            registeredDataDeserializer
          }
      }

  data class Key(val operationName: String, val variablesHash: String)

  override fun close() {
    logger.debug("close() called")
    coroutineScope.cancel()
  }

  interface RegisteredDataDeserializerFactory {
    fun <T> newInstance(
      dataDeserializer: DeserializationStrategy<T>,
      dataSerializersModule: SerializersModule?,
      parentLogger: Logger
    ): RegisteredDataDeserializer<T>
  }
}
