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

import android.annotation.SuppressLint
import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.app
import java.util.concurrent.Executor
import kotlin.random.Random
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy

class FirebaseDataConnect
internal constructor(
  private val context: Context,
  val app: FirebaseApp,
  private val projectId: String,
  val serviceConfig: ServiceConfig,
  internal val blockingExecutor: Executor,
  internal val nonBlockingExecutor: Executor,
  private val creator: FirebaseDataConnectFactory,
  val settings: FirebaseDataConnectSettings,
) : AutoCloseable {

  private val logger =
    Logger("FirebaseDataConnect").apply {
      debug {
        "New instance created with " +
          "app=${app.name}, projectId=$projectId, " +
          "serviceConfig=$serviceConfig, settings=$settings"
      }
    }

  private val coroutineScope =
    CoroutineScope(
      SupervisorJob() +
        nonBlockingExecutor.asCoroutineDispatcher() +
        CoroutineName("FirebaseDataConnect") +
        CoroutineExceptionHandler { _, throwable ->
          logger.warn(throwable) { "uncaught exception from a coroutine" }
        }
    )

  private val blockingDispatcher = blockingExecutor.asCoroutineDispatcher()
  private val nonBlockingDispatcher = nonBlockingExecutor.asCoroutineDispatcher()

  // Protects `closed`, `grpcClient`, and `queryManager`.
  private val mutex = Mutex()

  // All accesses to this variable _must_ have locked `mutex`.
  private var closed = false

  private val lazyGrpcClient =
    SuspendingLazy(mutex) {
      if (closed) throw IllegalStateException("FirebaseDataConnect instance has been closed")
      DataConnectGrpcClient(
        context = context,
        projectId = projectId,
        serviceId = serviceConfig.serviceId,
        location = serviceConfig.location,
        revision = serviceConfig.revision,
        operationSet = serviceConfig.operationSet,
        hostName = settings.hostName,
        port = settings.port,
        sslEnabled = settings.sslEnabled,
        blockingExecutor = blockingExecutor,
        parentLogger = logger,
      )
    }

  internal val lazyQueryManager =
    SuspendingLazy(mutex) {
      if (closed) throw IllegalStateException("FirebaseDataConnect instance has been closed")
      val grpcClient = lazyGrpcClient.initializedValueOrNull ?: lazyGrpcClient.getValueLocked()
      QueryManager(
        grpcClient = grpcClient,
        coroutineScope = coroutineScope,
        blockingDispatcher = nonBlockingDispatcher,
        nonBlockingDispatcher = nonBlockingDispatcher,
        parentLogger = logger,
      )
    }

  internal suspend fun <V, D> executeMutation(ref: MutationRef<V, D>, variables: V) =
    executeMutation(ref, variables, requestId = Random.nextAlphanumericString())

  private suspend fun <V, D> executeMutation(
    ref: MutationRef<V, D>,
    variables: V,
    requestId: String
  ) =
    (lazyGrpcClient.initializedValueOrNull ?: lazyGrpcClient.getValue())
      .executeMutation(
        requestId = requestId,
        sequenceNumber = nextSequenceNumber(),
        operationName = ref.operationName,
        variables =
          if (ref.variablesSerializer === DataConnectUntypedVariables.Serializer)
            (variables as DataConnectUntypedVariables).variables.toStructProto()
          else {
            encodeToStruct(ref.variablesSerializer, variables)
          }
      )
      .runCatching { withContext(blockingDispatcher) { deserialize(ref.dataDeserializer) } }
      .onFailure {
        logger.warn(it) { "executeMutation() [rid=$requestId] decoding response data failed: $it" }
      }
      .getOrThrow()
      .toDataConnectResult(variables)

  private val closeResult = MutableStateFlow<Result<Unit>?>(null)

  override fun close() {
    logger.debug { "close() called" }
    // Remove the reference to this `FirebaseDataConnect` instance from the
    // `FirebaseDataConnectFactory` that created it, so that the next time that `getInstance()` is
    // called with the same arguments that a new instance of `FirebaseDataConnect` will be created.
    creator.remove(this)

    // Set the `closed` flag to `true`, making sure to honor the requirement that `closed` is always
    // accessed by a coroutine that has acquired `mutex`
    runBlocking { mutex.withLock { closed = true } }

    // If a previous attempt was successful, then just return because there is nothing to do.
    if (closeResult.isResultSuccess) {
      return
    }

    // Clear the result of the previous failed attempt, since we're about to try again.
    closeResult.clearResultUnlessSuccess()

    // Launch an asynchronous coroutine to actually perform the remainder of the close operation,
    // as it potentially suspends and this close() function is a "normal", non-suspending function.
    @OptIn(DelicateCoroutinesApi::class) GlobalScope.launch { doClose() }
  }

  suspend fun awaitClose(): Unit = closeResult.filterNotNull().first().getOrThrow()

  private val closingMutex = Mutex()

  private suspend fun doClose() {
    closingMutex.withLock {
      if (closeResult.isResultSuccess) {
        return
      }

      closeResult.value =
        kotlin
          .runCatching {
            logger.debug { "Closing started" }
            lazyGrpcClient.initializedValueOrNull?.apply { close() }
            coroutineScope.cancel()
            logger.debug { "Closing completed" }
          }
          .onFailure { logger.warn(it) { "Closing failed" } }
    }
  }

  override fun toString() =
    "FirebaseDataConnect{" +
      "app=${app.name}, projectId=$projectId, " +
      "location=${serviceConfig.location}, serviceId=${serviceConfig.serviceId} " +
      "operationSet=${serviceConfig.operationSet}, revision=${serviceConfig.revision}" +
      "}"

  class ServiceConfig(serviceId: String, location: String, operationSet: String, revision: String) {
    private val impl =
      Impl(
        serviceId = serviceId,
        location = location,
        operationSet = operationSet,
        revision = revision
      )

    val serviceId: String
      get() = impl.serviceId
    val location: String
      get() = impl.location
    val operationSet: String
      get() = impl.operationSet
    val revision: String
      get() = impl.revision

    private data class Impl(
      val serviceId: String,
      val location: String,
      val operationSet: String,
      val revision: String
    )

    override fun equals(other: Any?) =
      (other as? ServiceConfig)?.let { other.impl == impl } ?: false

    override fun hashCode() = impl.hashCode()

    override fun toString() =
      "ServiceConfig(serviceId=$serviceId, location=$location,operationSet=$operationSet, revision=$revision)"
  }

  companion object {
    @SuppressLint("FirebaseUseExplicitDependencies")
    fun getInstance(
      app: FirebaseApp,
      serviceConfig: ServiceConfig,
      settings: FirebaseDataConnectSettings? = null,
    ): FirebaseDataConnect =
      app.get(FirebaseDataConnectFactory::class.java).run {
        get(serviceConfig = serviceConfig, settings = settings)
      }

    fun getInstance(
      serviceConfig: ServiceConfig,
      settings: FirebaseDataConnectSettings? = null
    ): FirebaseDataConnect =
      getInstance(app = Firebase.app, serviceConfig = serviceConfig, settings = settings)

    private fun MutableStateFlow<Result<Unit>?>.clearResultUnlessSuccess() {
      while (true) {
        val oldValue = value
        if (oldValue?.isSuccess == true) {
          return
        }
        if (compareAndSet(oldValue, null)) {
          return
        }
      }
    }

    private val MutableStateFlow<Result<Unit>?>.isResultSuccess
      get() = value?.isSuccess == true
  }
}

fun <VariablesType, DataType> FirebaseDataConnect.query(
  operationName: String,
  variablesSerializer: SerializationStrategy<VariablesType>,
  dataDeserializer: DeserializationStrategy<DataType>
): QueryRef<VariablesType, DataType> =
  QueryRef(
    dataConnect = this,
    operationName = operationName,
    variablesSerializer = variablesSerializer,
    dataDeserializer = dataDeserializer
  )

fun <VariablesType, DataType> FirebaseDataConnect.mutation(
  operationName: String,
  variablesSerializer: SerializationStrategy<VariablesType>,
  dataDeserializer: DeserializationStrategy<DataType>
): MutationRef<VariablesType, DataType> =
  MutationRef(
    dataConnect = this,
    operationName = operationName,
    variablesSerializer = variablesSerializer,
    dataDeserializer = dataDeserializer
  )

open class DataConnectException internal constructor(message: String, cause: Throwable? = null) :
  Exception(message, cause)

open class NetworkTransportException internal constructor(message: String, cause: Throwable) :
  DataConnectException(message, cause)

open class GraphQLException internal constructor(message: String, val errors: List<String>) :
  DataConnectException(message)
