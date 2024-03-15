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

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.dataconnect.*
import com.google.firebase.dataconnect.querymgr.QueryManager
import com.google.firebase.dataconnect.util.SuspendingLazy
import java.util.concurrent.Executor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.*
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy

internal interface FirebaseDataConnectInternal : FirebaseDataConnect {
  val logger: Logger

  val blockingExecutor: Executor
  val blockingDispatcher: CoroutineDispatcher
  val nonBlockingExecutor: Executor
  val nonBlockingDispatcher: CoroutineDispatcher

  val lazyGrpcClient: SuspendingLazy<DataConnectGrpcClient>
  val lazyQueryManager: SuspendingLazy<QueryManager>
}

internal class FirebaseDataConnectImpl(
  private val context: Context,
  override val app: FirebaseApp,
  private val projectId: String,
  override val config: ConnectorConfig,
  override val blockingExecutor: Executor,
  override val nonBlockingExecutor: Executor,
  private val creator: FirebaseDataConnectFactory,
  override val settings: DataConnectSettings,
) : FirebaseDataConnectInternal {

  override val logger =
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

  override val blockingDispatcher = blockingExecutor.asCoroutineDispatcher()
  override val nonBlockingDispatcher = nonBlockingExecutor.asCoroutineDispatcher()

  // Protects `closed`, `grpcClient`, `emulatorSettings`, and `queryManager`.
  private val mutex = Mutex()

  // All accesses to this variable _must_ have locked `mutex`.
  private var emulatorSettings: EmulatedServiceSettings? = null

  // All accesses to this variable _must_ have locked `mutex`.
  private var closed = false

  override val lazyGrpcClient =
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

  override val lazyQueryManager =
    SuspendingLazy(mutex) {
      if (closed) throw IllegalStateException("FirebaseDataConnect instance has been closed")
      QueryManager(this)
    }

  override fun useEmulator(host: String, port: Int): Unit = runBlocking {
    mutex.withLock {
      if (lazyGrpcClient.initializedValueOrNull != null) {
        throw IllegalStateException(
          "Cannot call useEmulator() after instance has already been initialized."
        )
      }
      emulatorSettings = EmulatedServiceSettings(host = host, port = port)
    }
  }

  override fun <Data, Variables> query(
    operationName: String,
    variables: Variables,
    dataDeserializer: DeserializationStrategy<Data>,
    variablesSerializer: SerializationStrategy<Variables>,
  ) =
    QueryRefImpl(
      dataConnect = this,
      operationName = operationName,
      variables = variables,
      dataDeserializer = dataDeserializer,
      variablesSerializer = variablesSerializer,
    )

  override fun <Data, Variables> mutation(
    operationName: String,
    variables: Variables,
    dataDeserializer: DeserializationStrategy<Data>,
    variablesSerializer: SerializationStrategy<Variables>,
  ) =
    MutationRefImpl(
      dataConnect = this,
      operationName = operationName,
      variables = variables,
      dataDeserializer = dataDeserializer,
      variablesSerializer = variablesSerializer,
    )

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

  // The generated SDK relies on equals() and hashCode() using object identity.
  // Although you get this for free by just calling the methods of the superclass, be explicit
  // to ensure that nobody changes these implementations in the future.
  override fun equals(other: Any?): Boolean = other === this
  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString(): String =
    "FirebaseDataConnect(app=${app.name}, projectId=$projectId, config=$config, settings=$settings)"

  private data class EmulatedServiceSettings(val host: String, val port: Int)

  companion object {
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
