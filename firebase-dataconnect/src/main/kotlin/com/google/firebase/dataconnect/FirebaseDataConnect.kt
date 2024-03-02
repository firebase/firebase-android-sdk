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

public class FirebaseDataConnect
internal constructor(
  private val context: Context,
  public val app: FirebaseApp,
  private val projectId: String,
  public val config: ConnectorConfig,
  internal val blockingExecutor: Executor,
  internal val nonBlockingExecutor: Executor,
  private val creator: FirebaseDataConnectFactory,
  public val settings: FirebaseDataConnectSettings,
) : AutoCloseable {

  private val logger =
    Logger("FirebaseDataConnect").apply {
      debug {
        "New instance created with " +
          "app=${app.name}, projectId=$projectId, " +
          "config=$config, settings=$settings"
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
        connector = config.connector,
        location = config.location,
        service = config.service,
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
      QueryManager(
        grpcClient = lazyGrpcClient.getLocked(),
        coroutineScope = coroutineScope,
        blockingDispatcher = blockingDispatcher,
        nonBlockingDispatcher = nonBlockingDispatcher,
        parentLogger = logger,
      )
    }

  internal suspend fun <R, V> executeMutation(mutation: Mutation<R, V>, variables: V) =
    executeMutation(mutation, variables, requestId = Random.nextAlphanumericString())

  private suspend fun <R, V> executeMutation(
    mutation: Mutation<R, V>,
    variables: V,
    requestId: String
  ) =
    lazyGrpcClient
      .get()
      .executeMutation(
        requestId = requestId,
        sequenceNumber = nextSequenceNumber(),
        operationName = mutation.operationName,
        variables =
          if (mutation.variablesSerializer === DataConnectUntypedVariables.Serializer)
            (variables as DataConnectUntypedVariables).variables.toStructProto()
          else {
            encodeToStruct(mutation.variablesSerializer, variables)
          }
      )
      .runCatching {
        withContext(blockingDispatcher) { deserialize(mutation.responseDeserializer) }
      }
      .onFailure {
        logger.warn(it) { "executeMutation() [rid=$requestId] decoding response data failed: $it" }
      }
      .getOrThrow()
      .toDataConnectMutationResult(mutation, variables)

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

  // TODO: Delete this function and the properties that it uses since it does not have a use case.
  internal suspend fun awaitClose(): Unit = closeResult.filterNotNull().first().getOrThrow()

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

  override fun toString(): String =
    "FirebaseDataConnect(app=${app.name}, projectId=$projectId, config=$config, settings=$settings)"

  public companion object {
    @SuppressLint("FirebaseUseExplicitDependencies")
    public fun getInstance(
      app: FirebaseApp,
      config: ConnectorConfig,
      settings: FirebaseDataConnectSettings? = null,
    ): FirebaseDataConnect =
      app.get(FirebaseDataConnectFactory::class.java).run {
        get(config = config, settings = settings)
      }

    public fun getInstance(
      config: ConnectorConfig,
      settings: FirebaseDataConnectSettings? = null
    ): FirebaseDataConnect = getInstance(app = Firebase.app, config = config, settings = settings)

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

public fun <Response, Variables> FirebaseDataConnect.query(
  operationName: String,
  responseDeserializer: DeserializationStrategy<Response>,
  variablesSerializer: SerializationStrategy<Variables>,
): Query<Response, Variables> =
  Query(
    dataConnect = this,
    operationName = operationName,
    responseDeserializer = responseDeserializer,
    variablesSerializer = variablesSerializer,
  )

public fun <Response, Variables> FirebaseDataConnect.mutation(
  operationName: String,
  responseDeserializer: DeserializationStrategy<Response>,
  variablesSerializer: SerializationStrategy<Variables>,
): Mutation<Response, Variables> =
  Mutation(
    dataConnect = this,
    operationName = operationName,
    responseDeserializer = responseDeserializer,
    variablesSerializer = variablesSerializer,
  )

public class ConnectorConfig(connector: String, location: String, service: String) {
  private val impl = Impl(connector = connector, location = location, service = service)

  public fun copy(
    connector: String = this.connector,
    location: String = this.location,
    service: String = this.service
  ): ConnectorConfig =
    ConnectorConfig(connector = connector, location = location, service = service)

  public val connector: String
    get() = impl.connector
  public val location: String
    get() = impl.location
  public val service: String
    get() = impl.service

  private data class Impl(val connector: String, val location: String, val service: String)

  override fun equals(other: Any?): Boolean =
    (other as? ConnectorConfig)?.let { other.impl == impl } ?: false

  override fun hashCode(): Int = impl.hashCode()

  override fun toString(): String =
    "ConnectorConfig(connector=$connector, location=$location, service=$service)"
}

public open class DataConnectException
internal constructor(message: String, cause: Throwable? = null) : Exception(message, cause)
