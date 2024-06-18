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

package com.google.firebase.dataconnect.core

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.dataconnect.*
import com.google.firebase.dataconnect.oldquerymgr.LiveQueries
import com.google.firebase.dataconnect.oldquerymgr.LiveQuery
import com.google.firebase.dataconnect.oldquerymgr.OldQueryManager
import com.google.firebase.dataconnect.oldquerymgr.RegisteredDataDeserialzer
import com.google.firebase.dataconnect.util.NullableReference
import com.google.firebase.dataconnect.util.SuspendingLazy
import com.google.firebase.util.nextAlphanumericString
import com.google.protobuf.Struct
import java.util.concurrent.Executor
import kotlin.random.Random
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.*
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy

internal interface FirebaseDataConnectInternal : FirebaseDataConnect {
  val logger: Logger

  val coroutineScope: CoroutineScope
  val blockingExecutor: Executor
  val blockingDispatcher: CoroutineDispatcher
  val nonBlockingExecutor: Executor
  val nonBlockingDispatcher: CoroutineDispatcher

  val lazyGrpcClient: SuspendingLazy<DataConnectGrpcClient>
  val lazyQueryManager: SuspendingLazy<OldQueryManager>
}

internal class FirebaseDataConnectImpl(
  private val context: Context,
  override val app: FirebaseApp,
  private val projectId: String,
  override val config: ConnectorConfig,
  override val blockingExecutor: Executor,
  override val nonBlockingExecutor: Executor,
  private val deferredAuthProvider: com.google.firebase.inject.Deferred<InternalAuthProvider>,
  private val creator: FirebaseDataConnectFactory,
  override val settings: DataConnectSettings,
) : FirebaseDataConnectInternal {

  override val logger =
    Logger("FirebaseDataConnectImpl").apply {
      debug {
        "New instance created with " +
          "app=${app.name}, projectId=$projectId, " +
          "config=$config, settings=$settings"
      }
    }

  override val blockingDispatcher = blockingExecutor.asCoroutineDispatcher()
  override val nonBlockingDispatcher = nonBlockingExecutor.asCoroutineDispatcher()

  override val coroutineScope =
    CoroutineScope(
      SupervisorJob() +
        nonBlockingDispatcher +
        CoroutineName(logger.nameWithId) +
        CoroutineExceptionHandler { _, throwable ->
          logger.warn(throwable) { "uncaught exception from a coroutine" }
        }
    )

  // Protects `closed`, `grpcClient`, `emulatorSettings`, and `queryManager`.
  private val mutex = Mutex()

  // All accesses to this variable _must_ have locked `mutex`.
  private var emulatorSettings: EmulatedServiceSettings? = null

  // All accesses to this variable _must_ have locked `mutex`.
  private var closed = false

  private val dataConnectAuth =
    SuspendingLazy(mutex) {
      if (closed) throw IllegalStateException("FirebaseDataConnect instance has been closed")
      DataConnectAuth(deferredAuthProvider, blockingExecutor, logger)
    }

  private val lazyGrpcRPCs =
    SuspendingLazy(mutex) {
      if (closed) throw IllegalStateException("FirebaseDataConnect instance has been closed")

      data class DataConnectBackendInfo(
        val host: String,
        val sslEnabled: Boolean,
        val isEmulator: Boolean
      )
      val backendInfoFromSettings =
        DataConnectBackendInfo(
          host = settings.host,
          sslEnabled = settings.sslEnabled,
          isEmulator = false
        )
      val backendInfoFromEmulatorSettings =
        emulatorSettings?.run {
          DataConnectBackendInfo(host = "$host:$port", sslEnabled = false, isEmulator = true)
        }
      val backendInfo =
        if (backendInfoFromEmulatorSettings == null) {
          backendInfoFromSettings
        } else {
          if (!settings.isDefaultHost()) {
            logger.warn(
              "Host has been set in DataConnectSettings and useEmulator, " +
                "emulator host will be used."
            )
          }
          backendInfoFromEmulatorSettings
        }

      logger.debug { "connecting to Data Connect backend: $backendInfo" }
      val grpcMetadata =
        DataConnectGrpcMetadata.forSystemVersions(
          dataConnectAuth = dataConnectAuth.getLocked(),
          connectorLocation = config.location,
          parentLogger = logger,
        )
      val dataConnectGrpcRPCs =
        DataConnectGrpcRPCs(
          context = context,
          host = backendInfo.host,
          sslEnabled = backendInfo.sslEnabled,
          blockingCoroutineDispatcher = blockingDispatcher,
          grpcMetadata = grpcMetadata,
          parentLogger = logger,
        )

      if (backendInfo.isEmulator) {
        logEmulatorVersion(dataConnectGrpcRPCs)
        streamEmulatorErrors(dataConnectGrpcRPCs)
      }

      dataConnectGrpcRPCs
    }

  override val lazyGrpcClient =
    SuspendingLazy(mutex) {
      DataConnectGrpcClient(
        projectId = projectId,
        connector = config,
        grpcRPCs = lazyGrpcRPCs.getLocked(),
        parentLogger = logger,
      )
    }

  override val lazyQueryManager =
    SuspendingLazy(mutex) {
      if (closed) throw IllegalStateException("FirebaseDataConnect instance has been closed")
      val grpcClient = lazyGrpcClient.getLocked()

      val registeredDataDeserialzerFactory =
        object : LiveQuery.RegisteredDataDeserialzerFactory {
          override fun <T> newInstance(
            dataDeserializer: DeserializationStrategy<T>,
            parentLogger: Logger
          ) =
            RegisteredDataDeserialzer<T>(
              dataDeserializer = dataDeserializer,
              blockingCoroutineDispatcher = blockingDispatcher,
              parentLogger = parentLogger,
            )
        }
      val liveQueryFactory =
        object : LiveQueries.LiveQueryFactory {
          override fun newLiveQuery(
            key: LiveQuery.Key,
            operationName: String,
            variables: Struct,
            parentLogger: Logger
          ) =
            LiveQuery(
              key = key,
              operationName = operationName,
              variables = variables,
              parentCoroutineScope = coroutineScope,
              nonBlockingCoroutineDispatcher = nonBlockingDispatcher,
              grpcClient = grpcClient,
              registeredDataDeserialzerFactory = registeredDataDeserialzerFactory,
              parentLogger = parentLogger,
            )
        }
      val liveQueries = LiveQueries(liveQueryFactory, parentLogger = logger)
      OldQueryManager(liveQueries)
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

  private fun logEmulatorVersion(dataConnectGrpcRPCs: DataConnectGrpcRPCs) {
    val requestId = "gei" + Random.nextAlphanumericString(length = 6)
    logger.debug { "[rid=$requestId] Getting Data Connect Emulator information" }

    val job =
      coroutineScope.async {
        val emulatorInfo = dataConnectGrpcRPCs.getEmulatorInfo(requestId)
        logger.debug { "[rid=$requestId] Data Connect Emulator version: ${emulatorInfo.version}" }

        logger.debug {
          "[rid=$requestId] Data Connect Emulator services" +
            " (count=${emulatorInfo.servicesCount}):"
        }
        emulatorInfo.servicesList.forEachIndexed { index, serviceInfo ->
          logger.debug {
            "[rid=$requestId]  service #${index+1}:" +
              " serviceId=${serviceInfo.serviceId}" +
              " connectionString=${serviceInfo.connectionString}"
          }
        }
      }

    job.invokeOnCompletion { exception ->
      if (exception !== null) {
        logger.debug {
          "[rid=$requestId] Getting Data Connect Emulator information FAILED: $exception"
        }
      }
    }
  }

  private fun streamEmulatorErrors(dataConnectGrpcRPCs: DataConnectGrpcRPCs) {
    val requestId = "see" + Random.nextAlphanumericString(length = 6)
    logger.debug { "[rid=$requestId] Streaming Data Connect Emulator errors" }

    val job =
      coroutineScope.async {
        // Do not log anything for each entry collected, as DataConnectGrpcRPCs already logs each
        // received message and there is nothing for this method to add to it.
        dataConnectGrpcRPCs.streamEmulatorIssues(requestId, config.serviceId).collect()
      }
    job.invokeOnCompletion { exception ->
      if (!(exception === null || exception is CancellationException)) {
        logger.debug {
          "[rid=$requestId] Streaming Data Connect Emulator errors FAILED: $exception"
        }
      }
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

  private val closeJob = MutableStateFlow(NullableReference<Deferred<Unit>>(null))

  override fun close() {
    logger.debug { "close() called" }
    @Suppress("DeferredResultUnused") runBlocking { nonBlockingClose() }
  }

  override suspend fun suspendingClose() {
    logger.debug { "suspendingClose() called" }
    nonBlockingClose().await()
  }

  private suspend fun nonBlockingClose(): Deferred<Unit> {
    coroutineScope.cancel()

    // Remove the reference to this `FirebaseDataConnect` instance from the
    // `FirebaseDataConnectFactory` that created it, so that the next time that `getInstance()` is
    // called with the same arguments that a new instance of `FirebaseDataConnect` will be created.
    creator.remove(this)

    mutex.withLock { closed = true }

    // Close Auth synchronously to avoid race conditions with auth callbacks. Since close()
    // is re-entrant, this is safe even if it's already been closed.
    dataConnectAuth.initializedValueOrNull?.close()

    // Start the job to asynchronously close the gRPC client.
    while (true) {
      val oldCloseJob = closeJob.value

      oldCloseJob.ref?.let {
        if (!it.isCancelled) {
          return it
        }
      }

      @OptIn(DelicateCoroutinesApi::class)
      val newCloseJob =
        GlobalScope.async<Unit>(start = CoroutineStart.LAZY) {
          lazyGrpcRPCs.initializedValueOrNull?.close()
        }

      newCloseJob.invokeOnCompletion { exception ->
        if (exception === null) {
          logger.debug { "close() completed successfully" }
        } else {
          logger.warn(exception) { "close() failed" }
        }
      }

      if (closeJob.compareAndSet(oldCloseJob, NullableReference(newCloseJob))) {
        newCloseJob.start()
        return newCloseJob
      }
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
}
