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
import kotlinx.serialization.serializer

public class FirebaseDataConnect
internal constructor(
  private val context: Context,
  public val app: FirebaseApp,
  private val projectId: String,
  public val config: ConnectorConfig,
  internal val blockingExecutor: Executor,
  internal val nonBlockingExecutor: Executor,
  private val creator: FirebaseDataConnectFactory,
  public val settings: DataConnectSettings,
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

  // Protects `closed`, `grpcClient`, `emulatorSettings`, and `queryManager`.
  private val mutex = Mutex()

  // All accesses to this variable _must_ have locked `mutex`.
  private var emulatorSettings: EmulatedServiceSettings? = null

  // All accesses to this variable _must_ have locked `mutex`.
  private var closed = false

  private val lazyGrpcClient =
    SuspendingLazy(mutex) {
      if (closed) throw IllegalStateException("FirebaseDataConnect instance has been closed")

      val hostAndPortFromSettings = Pair(settings.host, settings.sslEnabled)
      val hostAndPortFromEmulatorSettings = emulatorSettings?.run { Pair("$host:$port", false) }
      val (host, sslEnabled) =
        if (hostAndPortFromEmulatorSettings == null) {
          hostAndPortFromSettings
        } else {
          if (!settings.isDefaultHost()) {
            logger.warn(
              "Host has been set in DataConnectSettings and useEmulator, " +
                "emulator host will be used."
            )
          }
          hostAndPortFromEmulatorSettings
        }

      DataConnectGrpcClient(
        context = context,
        projectId = projectId,
        connector = config.connector,
        location = config.location,
        service = config.service,
        host = host,
        sslEnabled = sslEnabled,
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

  internal suspend fun <R, V> executeMutation(mutation: MutationRef<R, V>) =
    executeMutation(mutation, requestId = Random.nextAlphanumericString())

  public fun useEmulator(host: String = "10.0.2.2", port: Int = 9510): Unit = runBlocking {
    mutex.withLock {
      if (lazyGrpcClient.initializedValueOrNull != null) {
        throw IllegalStateException(
          "Cannot call useEmulator() after instance has already been initialized."
        )
      }
      emulatorSettings = EmulatedServiceSettings(host = host, port = port)
    }
  }

  private suspend fun <R, V> executeMutation(mutation: MutationRef<R, V>, requestId: String) =
    lazyGrpcClient
      .get()
      .executeMutation(
        requestId = requestId,
        sequenceNumber = nextSequenceNumber(),
        operationName = mutation.operationName,
        variables =
          if (mutation.variablesSerializer === DataConnectUntypedVariables.Serializer)
            (mutation.variables as DataConnectUntypedVariables).variables.toStructProto()
          else {
            encodeToStruct(mutation.variablesSerializer, mutation.variables)
          }
      )
      .runCatching {
        withContext(blockingDispatcher) { deserialize(mutation.responseDeserializer) }
      }
      .onFailure {
        logger.warn(it) { "executeMutation() [rid=$requestId] decoding response data failed: $it" }
      }
      .getOrThrow()
      .toDataConnectMutationResult(mutation)

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

  private data class EmulatedServiceSettings(val host: String, val port: Int)

  public companion object {
    @SuppressLint("FirebaseUseExplicitDependencies")
    public fun getInstance(
      app: FirebaseApp,
      config: ConnectorConfig,
      settings: DataConnectSettings? = null,
    ): FirebaseDataConnect =
      app.get(FirebaseDataConnectFactory::class.java).run {
        get(config = config, settings = settings)
      }

    public fun getInstance(
      config: ConnectorConfig,
      settings: DataConnectSettings? = null
    ): FirebaseDataConnect = getInstance(app = Firebase.app, config = config, settings = settings)

    public var logLevel: LogLevel
      get() = com.google.firebase.dataconnect.logLevel
      set(newLogLevel) {
        com.google.firebase.dataconnect.logLevel = newLogLevel
      }

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
  variables: Variables,
  responseDeserializer: DeserializationStrategy<Response>,
  variablesSerializer: SerializationStrategy<Variables>,
): QueryRef<Response, Variables> =
  QueryRef(
    dataConnect = this,
    operationName = operationName,
    variables = variables,
    responseDeserializer = responseDeserializer,
    variablesSerializer = variablesSerializer,
  )

public fun <Response> FirebaseDataConnect.query(
  operationName: String,
  responseDeserializer: DeserializationStrategy<Response>
): QueryRef<Response, Unit> =
  query(
    operationName = operationName,
    variables = Unit,
    responseDeserializer = responseDeserializer,
    variablesSerializer = serializer()
  )

public fun <Response, Variables> FirebaseDataConnect.mutation(
  operationName: String,
  variables: Variables,
  responseDeserializer: DeserializationStrategy<Response>,
  variablesSerializer: SerializationStrategy<Variables>,
): MutationRef<Response, Variables> =
  MutationRef(
    dataConnect = this,
    operationName = operationName,
    variables = variables,
    responseDeserializer = responseDeserializer,
    variablesSerializer = variablesSerializer,
  )

public fun <Response> FirebaseDataConnect.mutation(
  operationName: String,
  responseDeserializer: DeserializationStrategy<Response>
): MutationRef<Response, Unit> =
  mutation(
    operationName = operationName,
    variables = Unit,
    responseDeserializer = responseDeserializer,
    variablesSerializer = serializer()
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

  override fun equals(other: Any?): Boolean = (other is ConnectorConfig) && other.impl == impl

  override fun hashCode(): Int = impl.hashCode()

  override fun toString(): String =
    "ConnectorConfig(connector=$connector, location=$location, service=$service)"
}

public class DataConnectSettings(
  host: String = "dataconnect.googleapis.com",
  sslEnabled: Boolean = true
) {
  private val impl = Impl(host = host, sslEnabled = sslEnabled)

  public fun copy(
    host: String = this.host,
    sslEnabled: Boolean = this.sslEnabled
  ): DataConnectSettings = DataConnectSettings(host = host, sslEnabled = sslEnabled)

  public val host: String
    get() = impl.host
  public val sslEnabled: Boolean
    get() = impl.sslEnabled

  private data class Impl(val host: String, val sslEnabled: Boolean)

  override fun equals(other: Any?): Boolean = (other is DataConnectSettings) && other.impl == impl

  override fun hashCode(): Int = impl.hashCode()

  override fun toString(): String = "DataConnectSettings(host=$host, sslEnabled=$sslEnabled)"
}

internal fun DataConnectSettings.isDefaultHost() = host == DataConnectSettings().host

public open class DataConnectException
internal constructor(message: String, cause: Throwable? = null) : Exception(message, cause)
