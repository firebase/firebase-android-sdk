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
import com.google.android.gms.security.ProviderInstaller
import com.google.firebase.dataconnect.ConnectorConfig
import com.google.firebase.dataconnect.DataConnectException
import com.google.firebase.dataconnect.DataConnectUntypedVariables
import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.core.DataConnectAppCheck.GetAppCheckTokenResult
import com.google.firebase.dataconnect.core.DataConnectAuth.GetAuthTokenResult
import com.google.firebase.dataconnect.core.DataConnectGrpcMetadata.Companion.toStructProto
import com.google.firebase.dataconnect.core.DataConnectStream.IncomingResponse
import com.google.firebase.dataconnect.core.LoggerGlobals.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.core.LoggerGlobals.warn
import com.google.firebase.dataconnect.util.ProtoUtil.buildStructProto
import com.google.firebase.dataconnect.util.ProtoUtil.encodeToStruct
import com.google.firebase.dataconnect.util.ProtoUtil.toCompactString
import com.google.firebase.dataconnect.util.ProtoUtil.toStructProto
import com.google.firebase.dataconnect.util.SuspendingLazy
import com.google.protobuf.Struct
import google.firebase.dataconnect.proto.ConnectorServiceGrpc
import google.firebase.dataconnect.proto.ConnectorServiceGrpcKt
import google.firebase.dataconnect.proto.ConnectorStreamServiceGrpc
import google.firebase.dataconnect.proto.ConnectorStreamServiceGrpcKt
import google.firebase.dataconnect.proto.EmulatorInfo
import google.firebase.dataconnect.proto.EmulatorIssuesResponse
import google.firebase.dataconnect.proto.EmulatorServiceGrpc
import google.firebase.dataconnect.proto.EmulatorServiceGrpcKt
import google.firebase.dataconnect.proto.ExecuteMutationRequest
import google.firebase.dataconnect.proto.ExecuteMutationResponse
import google.firebase.dataconnect.proto.ExecuteQueryRequest
import google.firebase.dataconnect.proto.ExecuteQueryResponse
import google.firebase.dataconnect.proto.GetEmulatorInfoRequest
import google.firebase.dataconnect.proto.StreamEmulatorIssuesRequest
import google.firebase.dataconnect.proto.StreamRequest
import google.firebase.dataconnect.proto.StreamResponse
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.android.AndroidChannelBuilder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule

internal class DataConnectGrpcRPCs(
  context: Context,
  host: String,
  sslEnabled: Boolean,
  private val connectorResourceName: String,
  private val blockingCoroutineDispatcher: CoroutineDispatcher,
  private val grpcMetadata: DataConnectGrpcMetadata,
  parentLogger: Logger,
) {
  private val logger =
    Logger("DataConnectGrpcRPCs").apply {
      debug {
        "created by ${parentLogger.nameWithId} with" +
          " host=$host" +
          " sslEnabled=$sslEnabled" +
          " grpcMetadata=${grpcMetadata.instanceId}"
      }
    }
  val instanceId: String
    get() = logger.nameWithId

  private val connectCoroutineScope =
    CoroutineScope(
      SupervisorJob() +
        CoroutineName("$instanceId-connect") +
        CoroutineExceptionHandler { context, throwable ->
          logger.warn(throwable) {
            "uncaught exception from a coroutine named ${context[CoroutineName]} [nqn32z7x79]"
          }
        }
    )

  private val mutex = Mutex()
  private var closed = false

  // Use the non-main-thread CoroutineDispatcher to avoid blocking operations on the main thread.
  private val lazyGrpcChannel =
    SuspendingLazy(mutex = mutex, coroutineContext = blockingCoroutineDispatcher) {
      check(!closed) { "DataConnectGrpcRPCs ${logger.nameWithId} instance has been closed" }
      logger.debug { "Creating GRPC ManagedChannel for host=$host sslEnabled=$sslEnabled" }

      // Upgrade the Android security provider using Google Play Services.
      //
      // We need to upgrade the Security Provider before any network channels are initialized
      // because okhttp maintains a list of supported providers that is initialized when the JVM
      // first resolves the static dependencies of ManagedChannel.
      //
      // If initialization fails for any reason, then a warning is logged and the original,
      // un-upgraded security provider is used.
      try {
        ProviderInstaller.installIfNeeded(context)
      } catch (e: Exception) {
        logger.warn(e) { "Failed to update ssl context" }
      }

      val grpcChannel =
        ManagedChannelBuilder.forTarget(host).let {
          if (!sslEnabled) {
            it.usePlaintext()
          }

          // Ensure gRPC recovers from a dead connection. This is not typically necessary, as
          // the OS will usually notify gRPC when a connection dies. But not always. This acts as a
          // failsafe.
          it.keepAliveTime(30, TimeUnit.SECONDS)

          it.executor(blockingCoroutineDispatcher.asExecutor())

          // Wrap the `ManagedChannelBuilder` in an `AndroidChannelBuilder`. This allows the channel
          // to respond more gracefully to network change events, such as switching from cellular to
          // wifi.
          AndroidChannelBuilder.usingBuilder(it).context(context).build()
        }

      logger.debug { "Creating GRPC ManagedChannel for host=$host sslEnabled=$sslEnabled done" }
      grpcChannel
    }

  private val lazyGrpcStub =
    SuspendingLazy(mutex) {
      check(!closed) { "DataConnectGrpcRPCs ${logger.nameWithId} instance has been closed" }
      ConnectorServiceGrpcKt.ConnectorServiceCoroutineStub(lazyGrpcChannel.getLocked())
    }

  private val lazyStreamGrpcStub =
    SuspendingLazy(mutex) {
      check(!closed) { "DataConnectGrpcRPCs ${logger.nameWithId} instance has been closed" }
      ConnectorStreamServiceGrpcKt.ConnectorStreamServiceCoroutineStub(lazyGrpcChannel.getLocked())
    }

  private val lazyEmulatorGrpcStub =
    SuspendingLazy(mutex) {
      check(!closed) { "DataConnectGrpcRPCs ${logger.nameWithId} instance has been closed" }
      EmulatorServiceGrpcKt.EmulatorServiceCoroutineStub(lazyGrpcChannel.getLocked())
    }

  suspend fun connect(
    streamId: String,
    authToken: GetAuthTokenResult?,
    appCheckToken: GetAppCheckTokenResult?,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): DataConnectStream {
    val metadata =
      grpcMetadata.get(
        authToken = authToken,
        appCheckToken = appCheckToken,
        callerSdkType = callerSdkType
      )
    val kotlinMethodName = "connect()"
    val initRequest =
      StreamRequest.newBuilder().setName(connectorResourceName).setRequestId("init").build()

    fun logOutgoingRequest(request: StreamRequest) {
      if (request === initRequest) {
        logger.logGrpcSending(
          requestId = streamId,
          kotlinMethodName = kotlinMethodName,
          grpcMethod = ConnectorStreamServiceGrpc.getConnectMethod(),
          metadata = metadata,
          structFromRequest = { initRequest.toStructProto(authToken?.authUid) },
          requestTypeName = "StreamRequest",
          authUid = authToken?.authUid,
        )
      } else {
        logger.logGrpcSendingStreaming(
          streamId = streamId,
          requestId = request.requestId,
          kotlinMethodName = kotlinMethodName,
          request = request,
          authUid = authToken?.authUid,
        )
      }
    }

    fun logIncomingResponse(response: StreamResponse) {
      logger.logGrpcReceivedStreaming(
        streamId = streamId,
        requestId = response.requestId,
        kotlinMethodName = kotlinMethodName,
        response = response,
      )
    }

    fun logCompletion(exception: Throwable?) {
      if (exception === null || exception is CancellationException) {
        logger.logGrpcCompleted(requestId = streamId, kotlinMethodName = kotlinMethodName)
      } else {
        logger.logGrpcFailed(
          requestId = streamId,
          kotlinMethodName = kotlinMethodName,
          throwable = exception,
        )
      }
    }

    val requestChannel = Channel<StreamRequest>(Channel.UNLIMITED)
    val outgoingRequests = run {
      val collected = AtomicBoolean(false)
      flow {
        check(collected.compareAndSet(false, true)) {
          "internal error h37dft6hbp: outgoingRequests may only be collected once " +
            "(streamId=$streamId)"
        }
        emit(initRequest)
        emitAll(requestChannel.receiveAsFlow())
      }
    }

    val incomingResponses =
      lazyStreamGrpcStub
        .get()
        .connect(outgoingRequests.onEach { logOutgoingRequest(it) }, metadata)
        .onEach { logIncomingResponse(it) }
        .map<_, IncomingResponse> { IncomingResponse.Data(it) }
        .onCompletion { exception ->
          logCompletion(exception)
          requestChannel.cancel()
          if (exception !== null) {
            emit(IncomingResponse.Completed)
          }
        }
        .catch { emit(IncomingResponse.Error(it)) }
        .shareIn(
          scope = connectCoroutineScope,
          started =
            SharingStarted.WhileSubscribed(stopTimeoutMillis = 60.seconds.inWholeMilliseconds),
        )
        .onSubscription { emit(IncomingResponse.Ready) }

    return DataConnectStream(connectCoroutineScope, requestChannel, incomingResponses)
  }

  suspend fun connect2(
    streamId: String,
    authToken: GetAuthTokenResult?,
    appCheckToken: GetAppCheckTokenResult?,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): Pair<Channel<StreamRequest>, Flow<StreamResponse>> {
    val metadata =
      grpcMetadata.get(
        authToken = authToken,
        appCheckToken = appCheckToken,
        callerSdkType = callerSdkType
      )
    val kotlinMethodName = "connect()"
    val initRequest =
      StreamRequest.newBuilder().setName(connectorResourceName).setRequestId("init").build()

    fun logOutgoingRequest(request: StreamRequest) {
      if (request === initRequest) {
        logger.logGrpcSending(
          requestId = streamId,
          kotlinMethodName = kotlinMethodName,
          grpcMethod = ConnectorStreamServiceGrpc.getConnectMethod(),
          metadata = metadata,
          structFromRequest = { initRequest.toStructProto(authToken?.authUid) },
          requestTypeName = "StreamRequest",
          authUid = authToken?.authUid,
        )
      } else {
        logger.logGrpcSendingStreaming(
          streamId = streamId,
          requestId = request.requestId,
          kotlinMethodName = kotlinMethodName,
          request = request,
          authUid = authToken?.authUid,
        )
      }
    }

    fun logIncomingResponse(response: StreamResponse) {
      logger.logGrpcReceivedStreaming(
        streamId = streamId,
        requestId = response.requestId,
        kotlinMethodName = kotlinMethodName,
        response = response,
      )
    }

    fun logCompletion(exception: Throwable?) {
      if (exception === null || exception is CancellationException) {
        logger.logGrpcCompleted(requestId = streamId, kotlinMethodName = kotlinMethodName)
      } else {
        logger.logGrpcFailed(
          requestId = streamId,
          kotlinMethodName = kotlinMethodName,
          throwable = exception,
        )
      }
    }

    val requestChannel = Channel<StreamRequest>(Channel.UNLIMITED)
    val outgoingRequests = run {
      val collected = AtomicBoolean(false)
      flow {
        check(collected.compareAndSet(false, true)) {
          "internal error h37dft6hbp: outgoingRequests may only be collected once " +
            "(streamId=$streamId)"
        }
        emit(initRequest)
        emitAll(requestChannel.receiveAsFlow())
      }
    }

    val incomingResponses =
      lazyStreamGrpcStub
        .get()
        .connect(outgoingRequests.onEach { logOutgoingRequest(it) }, metadata)
        .onEach { logIncomingResponse(it) }
        .onCompletion { exception ->
          logCompletion(exception)
          requestChannel.cancel()
        }

    return Pair(requestChannel, incomingResponses)
  }

  suspend fun executeMutation(
    requestId: String,
    operationName: String,
    variables: Struct,
    authToken: GetAuthTokenResult?,
    appCheckToken: GetAppCheckTokenResult?,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): ExecuteMutationResponse {
    val metadata = grpcMetadata.get(authToken, appCheckToken, callerSdkType)
    val kotlinMethodName = "executeMutation($operationName)"
    val requestProto =
      ExecuteMutationRequest.newBuilder()
        .setName(connectorResourceName)
        .setOperationName(operationName)
        .setVariables(variables)
        .build()

    logger.logGrpcSending(
      requestId = requestId,
      kotlinMethodName = kotlinMethodName,
      grpcMethod = ConnectorServiceGrpc.getExecuteMutationMethod(),
      metadata = metadata,
      structFromRequest = { requestProto.toStructProto() },
      requestTypeName = "ExecuteMutationRequest",
      authUid = authToken?.authUid,
    )

    val result = lazyGrpcStub.get().runCatching { executeMutation(requestProto, metadata) }

    result.onSuccess { response ->
      logger.logGrpcReceived(
        requestId = requestId,
        kotlinMethodName = kotlinMethodName,
        structFromResponse = { response.toStructProto() },
        responseTypeName = "ExecuteMutationResponse",
      )
    }
    result.onFailure {
      logger.logGrpcFailed(
        requestId = requestId,
        kotlinMethodName = kotlinMethodName,
        it,
      )
    }

    return result.getOrThrow()
  }

  suspend fun executeQuery(
    requestId: String,
    operationName: String,
    variables: Struct,
    authToken: GetAuthTokenResult?,
    appCheckToken: GetAppCheckTokenResult?,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): ExecuteQueryResponse {
    val metadata = grpcMetadata.get(authToken, appCheckToken, callerSdkType)
    val kotlinMethodName = "executeQuery($operationName)"
    val requestProto =
      ExecuteQueryRequest.newBuilder()
        .setName(connectorResourceName)
        .setOperationName(operationName)
        .setVariables(variables)
        .build()

    logger.logGrpcSending(
      requestId = requestId,
      kotlinMethodName = kotlinMethodName,
      grpcMethod = ConnectorServiceGrpc.getExecuteQueryMethod(),
      metadata = metadata,
      structFromRequest = { requestProto.toStructProto() },
      requestTypeName = "ExecuteQueryRequest",
      authUid = authToken?.authUid,
    )

    val result = lazyGrpcStub.get().runCatching { executeQuery(requestProto, metadata) }

    result.onSuccess { response ->
      logger.logGrpcReceived(
        requestId = requestId,
        kotlinMethodName = kotlinMethodName,
        structFromResponse = { response.toStructProto() },
        responseTypeName = "ExecuteQueryResponse",
      )
    }

    result.onFailure {
      logger.logGrpcFailed(
        requestId = requestId,
        kotlinMethodName = kotlinMethodName,
        it,
      )
    }

    return result.getOrThrow()
  }

  suspend fun getEmulatorInfo(requestId: String): EmulatorInfo {
    val request = GetEmulatorInfoRequest.getDefaultInstance()
    val kotlinMethodName = "getEmulatorInfo()"

    logger.logGrpcStarting(
      requestId = requestId,
      kotlinMethodName = kotlinMethodName,
      grpcMethod = EmulatorServiceGrpc.getGetEmulatorInfoMethod(),
    )

    val result = lazyEmulatorGrpcStub.get().runCatching { getEmulatorInfo(request) }

    result.onSuccess { response ->
      logger.logGrpcReceived(
        requestId = requestId,
        kotlinMethodName = kotlinMethodName,
        structFromResponse = { response.toStructProto() },
        responseTypeName = "EmulatorInfo",
      )
    }
    result.onFailure {
      logger.logGrpcFailed(
        requestId = requestId,
        kotlinMethodName = kotlinMethodName,
        it,
      )
    }

    return result.getOrThrow()
  }

  suspend fun streamEmulatorIssues(
    requestId: String,
    serviceId: String
  ): Flow<EmulatorIssuesResponse> {
    val request = StreamEmulatorIssuesRequest.newBuilder().setServiceId(serviceId).build()
    val kotlinMethodName = "streamEmulatorIssues(serviceId=$serviceId)"

    val flow = lazyEmulatorGrpcStub.get().streamEmulatorIssues(request)

    return flow
      .onStart {
        logger.logGrpcStarting(
          requestId = requestId,
          kotlinMethodName = kotlinMethodName,
          grpcMethod = EmulatorServiceGrpc.getStreamEmulatorIssuesMethod(),
        )
      }
      .onEach { response ->
        logger.logGrpcReceived(
          requestId = requestId,
          kotlinMethodName = kotlinMethodName,
          structFromResponse = { response.toStructProto() },
          responseTypeName = "EmulatorIssuesResponse"
        )
      }
      .onCompletion { exception ->
        if (exception === null || exception is CancellationException) {
          logger.logGrpcCompleted(requestId = requestId, kotlinMethodName = kotlinMethodName)
        } else {
          logger.logGrpcFailed(
            requestId = requestId,
            kotlinMethodName = kotlinMethodName,
            throwable = exception,
          )
        }
      }
  }

  suspend fun close() {
    logger.debug { "close()" }
    mutex.withLock { closed = true }

    connectCoroutineScope.cancel("close() called")
    connectCoroutineScope.coroutineContext.job.join()

    lazyGrpcChannel.initializedValueOrNull?.let { grpcChannel: ManagedChannel ->
      // Avoid blocking the calling thread by running potentially-blocking code on the dispatcher
      // given to the constructor, which should have similar semantics to [Dispatchers.IO].
      withContext(blockingCoroutineDispatcher) {
        grpcChannel.shutdownNow()
        grpcChannel.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS)
      }
    }
  }

  private companion object {
    inline fun Logger.logGrpcSending(
      requestId: String,
      kotlinMethodName: String,
      grpcMethod: MethodDescriptor<*, *>,
      metadata: Metadata,
      crossinline structFromRequest: () -> Struct,
      requestTypeName: String,
      authUid: String?,
    ) = debug {
      val struct = buildStructProto {
        put("RPC", grpcMethod.fullMethodName)
        put("Metadata", metadata.toStructProto(authUid))
        put(requestTypeName, structFromRequest())
      }
      // Sort the keys in the output string to be more meaningful than alphabetical.
      val keySortSelector: (String) -> String = {
        when (it) {
          "RPC" -> "AAAA"
          "Metadata" -> "AAAB"
          requestTypeName -> "AAAC"
          else -> it
        }
      }
      "$kotlinMethodName [rid=$requestId] sending: ${struct.toCompactString(keySortSelector)}"
    }

    fun Logger.logGrpcSendingStreaming(
      streamId: String,
      requestId: String,
      kotlinMethodName: String,
      request: StreamRequest,
      authUid: String?
    ) = debug {
      "$kotlinMethodName [sid=$streamId, rid=$requestId] sending StreamRequest: " +
        request.toCompactString(authUid)
    }

    fun Logger.logGrpcReceivedStreaming(
      streamId: String,
      requestId: String,
      kotlinMethodName: String,
      response: StreamResponse,
    ) = debug {
      "$kotlinMethodName [sid=$streamId, rid=$requestId] received StreamResponse: " +
        response.toCompactString()
    }

    fun Logger.logGrpcStarting(
      requestId: String,
      kotlinMethodName: String,
      grpcMethod: MethodDescriptor<*, *>,
    ) = debug { "$kotlinMethodName [rid=$requestId] starting ${grpcMethod.fullMethodName}" }

    fun Logger.logGrpcCompleted(
      requestId: String,
      kotlinMethodName: String,
    ) = debug { "$kotlinMethodName [rid=$requestId] completed" }

    inline fun Logger.logGrpcReceived(
      requestId: String,
      kotlinMethodName: String,
      crossinline structFromResponse: () -> Struct,
      responseTypeName: String
    ) = debug {
      val struct = buildStructProto { put(responseTypeName, structFromResponse()) }
      "$kotlinMethodName [rid=$requestId] received: ${struct.toCompactString()}"
    }

    fun Logger.logGrpcFailed(
      requestId: String,
      kotlinMethodName: String,
      throwable: Throwable,
    ) = warn(throwable) { "$kotlinMethodName [rid=$requestId] FAILED: $throwable" }
  }
}

private val StatusException.isGrpcUnauthenticatedError: Boolean
  get() = status.code == Status.UNAUTHENTICATED.code

internal inline fun <T> retryOnGrpcUnauthenticatedError(
  requestId: String,
  getAuthToken: () -> GetAuthTokenResult?,
  getAppCheckToken: () -> GetAppCheckTokenResult?,
  forceRefreshTokens: () -> Unit,
  logger: Logger,
  block: (GetAuthTokenResult?, GetAppCheckTokenResult?) -> T,
): T {
  val authToken1 = getAuthToken()
  val appCheckToken1 = getAppCheckToken()
  val authUid1: String? = authToken1?.authUid

  val statusException: StatusException = run {
    try {
      return block(authToken1, appCheckToken1)
    } catch (e: StatusException) {
      e
    }
  }

  if (!statusException.isGrpcUnauthenticatedError) {
    throw statusException
  }

  // TODO(b/356877295) Only invalidate auth or appcheck tokens, but not both, to avoid
  //  spamming the appcheck attestation provider.
  forceRefreshTokens()

  val authToken2 = getAuthToken()
  val appCheckToken2 = getAppCheckToken()
  if (authToken1?.token == authToken2?.token && appCheckToken1?.token == appCheckToken2?.token) {
    throw statusException
  }

  logger.warn(statusException) {
    "[rid=$requestId] retrying with fresh Auth and/or AppCheck tokens " +
      "due to UNAUTHENTICATED error"
  }

  val authUid2 = authToken2?.authUid
  if (authUid1 != authUid2) {
    throw AuthUidChangedException("authUid changed from $authUid1 to $authUid2 [x7md4h6atc]")
  }

  return block(authToken2, appCheckToken2)
}

internal class AuthUidChangedException(message: String) : DataConnectException(message)

internal fun <Variables> encodeVariables(
  variables: Variables,
  serializer: SerializationStrategy<Variables>,
  serializersModule: SerializersModule?,
): Struct =
  if (serializer === DataConnectUntypedVariables.Serializer) {
    (variables as DataConnectUntypedVariables).variables.toStructProto()
  } else {
    encodeToStruct(variables, serializer, serializersModule)
  }

internal fun calculateConnectorResourceName(
  projectId: String,
  connectorConfig: ConnectorConfig
): String =
  "projects/$projectId" +
    "/locations/${connectorConfig.location}" +
    "/services/${connectorConfig.serviceId}" +
    "/connectors/${connectorConfig.connector}"
