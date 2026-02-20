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
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.dataconnect.CacheSettings
import com.google.firebase.dataconnect.ConnectorConfig
import com.google.firebase.dataconnect.DataConnectSettings
import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.FirebaseDataConnect.MutationRefOptionsBuilder
import com.google.firebase.dataconnect.FirebaseDataConnect.QueryRefOptionsBuilder
import com.google.firebase.dataconnect.core.LoggerGlobals.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.core.LoggerGlobals.warn
import com.google.firebase.dataconnect.isDefaultHost
import com.google.firebase.dataconnect.querymgr.LiveQueries
import com.google.firebase.dataconnect.querymgr.LiveQuery
import com.google.firebase.dataconnect.querymgr.QueryManager
import com.google.firebase.dataconnect.querymgr.RegisteredDataDeserializer
import com.google.firebase.dataconnect.util.AlphanumericStringUtil.toAlphaNumericString
import com.google.firebase.dataconnect.util.ProtoUtil.buildStructProto
import com.google.firebase.dataconnect.util.ProtoUtil.calculateSha512
import com.google.firebase.util.nextAlphanumericString
import com.google.protobuf.Struct
import java.util.concurrent.Executor
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule

internal interface FirebaseDataConnectInternal : FirebaseDataConnect {
  val logger: Logger

  val coroutineScope: CoroutineScope
  val blockingExecutor: Executor
  val blockingDispatcher: CoroutineDispatcher
  val nonBlockingExecutor: Executor
  val nonBlockingDispatcher: CoroutineDispatcher

  val grpcClient: DataConnectGrpcClient
  val queryManager: QueryManager

  suspend fun awaitAuthReady()
  suspend fun awaitAppCheckReady()
}

internal class FirebaseDataConnectImpl(
  private val context: Context,
  override val app: FirebaseApp,
  private val projectId: String,
  override val config: ConnectorConfig,
  override val blockingExecutor: Executor,
  override val nonBlockingExecutor: Executor,
  deferredAuthProvider: com.google.firebase.inject.Deferred<InternalAuthProvider>,
  deferredAppCheckProvider: com.google.firebase.inject.Deferred<InteropAppCheckTokenProvider>,
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
  val instanceId: String
    get() = logger.nameWithId

  override val blockingDispatcher = blockingExecutor.asCoroutineDispatcher()
  override val nonBlockingDispatcher = nonBlockingExecutor.asCoroutineDispatcher()

  override val coroutineScope =
    CoroutineScope(
      SupervisorJob() +
        nonBlockingDispatcher +
        CoroutineName(instanceId) +
        CoroutineExceptionHandler { context, throwable ->
          logger.warn(throwable) {
            val coroutineName = context[CoroutineName]?.name
            "WARNING: uncaught exception from coroutine named \"$coroutineName\" " +
              "(error code jszxcbe37k)"
          }
        }
    )

  private val dataConnectAuth: DataConnectAuth =
    DataConnectAuth(
        deferredAuthProvider = deferredAuthProvider,
        parentCoroutineScope = coroutineScope,
        blockingDispatcher = blockingDispatcher,
        logger = Logger("DataConnectAuth").apply { debug { "created by $instanceId" } },
      )
      .apply { initialize() }

  override suspend fun awaitAuthReady() {
    dataConnectAuth.awaitTokenProvider()
  }

  private val dataConnectAppCheck: DataConnectAppCheck =
    DataConnectAppCheck(
        deferredAppCheckTokenProvider = deferredAppCheckProvider,
        parentCoroutineScope = coroutineScope,
        blockingDispatcher = blockingDispatcher,
        logger = Logger("DataConnectAppCheck").apply { debug { "created by $instanceId" } },
      )
      .apply { initialize() }

  override suspend fun awaitAppCheckReady() {
    dataConnectAppCheck.awaitTokenProvider()
  }

  private sealed interface State {
    data class New(val emulatorSettings: EmulatedServiceSettings?) : State {
      constructor() : this(null)
    }
    data class Initialized(
      val grpcRPCs: DataConnectGrpcRPCs,
      val grpcClient: DataConnectGrpcClient,
      val queryManager: QueryManager
    ) : State
    data class Closing(val grpcRPCs: DataConnectGrpcRPCs, val closeJob: Deferred<Unit>) : State
    object Closed : State
  }

  private val state = MutableStateFlow<State>(State.New())

  override val grpcClient: DataConnectGrpcClient
    get() = initialize().grpcClient
  override val queryManager: QueryManager
    get() = initialize().queryManager

  private fun initialize(): State.Initialized {
    val newState =
      state.updateAndGet { currentState ->
        when (currentState) {
          is State.New -> {
            val grpcRPCs = createDataConnectGrpcRPCs(currentState.emulatorSettings)
            val grpcClient = createDataConnectGrpcClient(grpcRPCs)
            val queryManager = createQueryManager(grpcClient)
            State.Initialized(grpcRPCs, grpcClient, queryManager)
          }
          is State.Initialized -> currentState
          is State.Closing -> currentState
          is State.Closed -> currentState
        }
      }

    return when (newState) {
      is State.New ->
        throw IllegalStateException(
          "newState should be Initialized, but got New (error code sh2rf4wwjx)"
        )
      is State.Initialized -> newState
      is State.Closing,
      State.Closed -> throw IllegalStateException("FirebaseDataConnect instance has been closed")
    }
  }

  private data class DataConnectBackendInfo(
    val host: String,
    val sslEnabled: Boolean,
    val isEmulator: Boolean
  )

  private fun calculateCacheDbUniqueName(backendInfo: DataConnectBackendInfo): String {
    val struct = buildStructProto {
      put("projectId", app.options.projectId)
      put("appName", app.name)
      put("connectorId", config.connector)
      put("serviceId", config.serviceId)
      put("location", config.location)
      put("host", backendInfo.host)
      put("sslEnabled", backendInfo.sslEnabled)
      put("isEmulator", backendInfo.isEmulator)
    }
    val sha512Bytes = struct.calculateSha512()
    return sha512Bytes.toAlphaNumericString()
  }

  private fun createDataConnectGrpcRPCs(
    emulatorSettings: EmulatedServiceSettings?
  ): DataConnectGrpcRPCs {
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

    val cacheSettings =
      settings.cacheSettings?.run {
        val dbFile =
          when (storage) {
            CacheSettings.Storage.MEMORY -> null
            CacheSettings.Storage.PERSISTENT -> {
              val dbName = "dataconnect_" + calculateCacheDbUniqueName(backendInfo)
              context.getDatabasePath(dbName)
            }
          }
        DataConnectGrpcRPCs.CacheSettings(dbFile)
      }

    logger.debug { "connecting to Data Connect backend: $backendInfo" }
    val grpcMetadata =
      DataConnectGrpcMetadata.forSystemVersions(
        firebaseApp = app,
        dataConnectAuth = dataConnectAuth,
        dataConnectAppCheck = dataConnectAppCheck,
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
        cacheSettings = cacheSettings,
        parentLogger = logger,
      )

    if (backendInfo.isEmulator) {
      logEmulatorVersion(dataConnectGrpcRPCs)
      streamEmulatorErrors(dataConnectGrpcRPCs)
    }

    return dataConnectGrpcRPCs
  }

  private fun createDataConnectGrpcClient(grpcRPCs: DataConnectGrpcRPCs): DataConnectGrpcClient =
    DataConnectGrpcClient(
      projectId = projectId,
      connector = config,
      grpcRPCs = grpcRPCs,
      dataConnectAuth = dataConnectAuth,
      dataConnectAppCheck = dataConnectAppCheck,
      logger = Logger("DataConnectGrpcClient").apply { debug { "created by $instanceId" } },
    )

  private fun createQueryManager(grpcClient: DataConnectGrpcClient): QueryManager {
    val registeredDataDeserializerFactory =
      object : LiveQuery.RegisteredDataDeserializerFactory {
        override fun <T> newInstance(
          dataDeserializer: DeserializationStrategy<T>,
          dataSerializersModule: SerializersModule?,
          parentLogger: Logger
        ) =
          RegisteredDataDeserializer(
            dataDeserializer = dataDeserializer,
            dataSerializersModule = dataSerializersModule,
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
            registeredDataDeserializerFactory = registeredDataDeserializerFactory,
            parentLogger = parentLogger,
          )
      }
    val liveQueries = LiveQueries(liveQueryFactory, blockingDispatcher, parentLogger = logger)
    return QueryManager(liveQueries)
  }

  override fun useEmulator(host: String, port: Int): Unit = runBlocking {
    state.update { currentState ->
      when (currentState) {
        is State.New ->
          currentState.copy(emulatorSettings = EmulatedServiceSettings(host = host, port = port))
        is State.Initialized ->
          throw IllegalStateException(
            "Cannot call useEmulator() after instance has already been initialized."
          )
        is State.Closing -> currentState
        is State.Closed -> currentState
      }
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
    optionsBuilder: (QueryRefOptionsBuilder<Data, Variables>.() -> Unit)?,
  ): QueryRefImpl<Data, Variables> {
    val options =
      object : QueryRefOptionsBuilder<Data, Variables> {
        override var callerSdkType: FirebaseDataConnect.CallerSdkType? = null
        override var variablesSerializersModule: SerializersModule? = null
        override var dataSerializersModule: SerializersModule? = null
      }
    optionsBuilder?.let { it(options) }

    return QueryRefImpl(
      dataConnect = this,
      operationName = operationName,
      variables = variables,
      dataDeserializer = dataDeserializer,
      variablesSerializer = variablesSerializer,
      callerSdkType = options.callerSdkType ?: FirebaseDataConnect.CallerSdkType.Base,
      variablesSerializersModule = options.variablesSerializersModule,
      dataSerializersModule = options.dataSerializersModule,
    )
  }

  override fun <Data, Variables> mutation(
    operationName: String,
    variables: Variables,
    dataDeserializer: DeserializationStrategy<Data>,
    variablesSerializer: SerializationStrategy<Variables>,
    optionsBuilder: (MutationRefOptionsBuilder<Data, Variables>.() -> Unit)?,
  ): MutationRefImpl<Data, Variables> {
    val options =
      object : MutationRefOptionsBuilder<Data, Variables> {
        override var callerSdkType: FirebaseDataConnect.CallerSdkType? = null
        override var variablesSerializersModule: SerializersModule? = null
        override var dataSerializersModule: SerializersModule? = null
      }
    optionsBuilder?.let { it(options) }

    return MutationRefImpl(
      dataConnect = this,
      operationName = operationName,
      variables = variables,
      dataDeserializer = dataDeserializer,
      variablesSerializer = variablesSerializer,
      callerSdkType = options.callerSdkType ?: FirebaseDataConnect.CallerSdkType.Base,
      variablesSerializersModule = options.variablesSerializersModule,
      dataSerializersModule = options.dataSerializersModule,
    )
  }

  override fun close() {
    logger.debug { "close() called" }
    @Suppress("DeferredResultUnused") closeInternal()
  }

  override suspend fun suspendingClose() {
    logger.debug { "suspendingClose() called" }
    closeInternal()?.await()
  }

  private fun closeInternal(): Deferred<Unit>? {
    coroutineScope.cancel()

    // Remove the reference to this `FirebaseDataConnect` instance from the
    // `FirebaseDataConnectFactory` that created it, so that the next time that `getInstance()` is
    // called with the same arguments that a new instance of `FirebaseDataConnect` will be created.
    creator.remove(this)

    // Close Auth and AppCheck synchronously to avoid race conditions with auth callbacks.
    // Since close() is re-entrant, this is safe even if they have already been closed.
    dataConnectAuth.close()
    dataConnectAppCheck.close()

    fun createCloseJob(grpcRPCs: DataConnectGrpcRPCs): Deferred<Unit> {
      @OptIn(DelicateCoroutinesApi::class)
      val closeJob = GlobalScope.async(start = CoroutineStart.LAZY) { grpcRPCs.close() }
      closeJob.invokeOnCompletion { exception ->
        if (exception !== null) {
          logger.warn(exception) { "close() failed" }
        } else {
          logger.debug { "close() completed successfully" }
          state.update { currentState ->
            check(currentState is State.Closing) {
              "currentState is ${currentState}, but expected Closing (error code hsee7gfxvz)"
            }
            check(currentState.closeJob === closeJob) {
              "currentState.closeJob is ${currentState.closeJob}, but expected $closeJob " +
                "(error code n3x86pr6qn)"
            }
            State.Closed
          }
        }
      }
      return closeJob
    }

    val newState =
      state.updateAndGet { currentState ->
        when (currentState) {
          is State.New -> State.Closed
          is State.Initialized ->
            State.Closing(currentState.grpcRPCs, createCloseJob(currentState.grpcRPCs))
          is State.Closing ->
            if (currentState.closeJob.isCancelled) {
              currentState.copy(closeJob = createCloseJob(currentState.grpcRPCs))
            } else {
              currentState
            }
          is State.Closed -> State.Closed
        }
      }

    return when (newState) {
      is State.Initialized,
      is State.New ->
        throw IllegalStateException(
          "internal error: newState is $newState, but expected Closing or Closed " +
            "(error code n3x86pr6qn)"
        )
      is State.Closing -> newState.closeJob.apply { start() }
      is State.Closed -> null
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
