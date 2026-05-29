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
import androidx.annotation.VisibleForTesting
import com.google.android.gms.security.ProviderInstaller
import com.google.firebase.dataconnect.CachedDataNotFoundException
import com.google.firebase.dataconnect.DataConnectPath
import com.google.firebase.dataconnect.DataConnectPathSegment
import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.QueryRef.FetchPolicy
import com.google.firebase.dataconnect.core.DataConnectAuth.AuthUid
import com.google.firebase.dataconnect.core.DataConnectGrpcMetadata.Companion.toStructProto
import com.google.firebase.dataconnect.core.LoggerGlobals.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.core.LoggerGlobals.warn
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabase
import com.google.firebase.dataconnect.sqlite.GetEntityIdForPathFunction
import com.google.firebase.dataconnect.util.CoroutineUtils
import com.google.firebase.dataconnect.util.GrpcBidiFlow
import com.google.firebase.dataconnect.util.GrpcBidiFlowListenerMessageFormatter
import com.google.firebase.dataconnect.util.IdStringGenerator
import com.google.firebase.dataconnect.util.ProtoUtil.buildStructProto
import com.google.firebase.dataconnect.util.ProtoUtil.toCompactString
import com.google.firebase.dataconnect.util.ProtoUtil.toDataConnectPath
import com.google.firebase.dataconnect.util.ProtoUtil.toStructProto
import com.google.firebase.dataconnect.util.SuspendingLazy
import com.google.firebase.dataconnect.util.copy
import com.google.protobuf.Struct
import google.firebase.dataconnect.proto.ConnectorServiceGrpc
import google.firebase.dataconnect.proto.ConnectorServiceGrpcKt
import google.firebase.dataconnect.proto.ConnectorStreamServiceGrpc
import google.firebase.dataconnect.proto.EmulatorInfo
import google.firebase.dataconnect.proto.EmulatorIssuesResponse
import google.firebase.dataconnect.proto.EmulatorServiceGrpc
import google.firebase.dataconnect.proto.EmulatorServiceGrpcKt
import google.firebase.dataconnect.proto.ExecuteMutationRequest
import google.firebase.dataconnect.proto.ExecuteMutationResponse
import google.firebase.dataconnect.proto.ExecuteQueryRequest
import google.firebase.dataconnect.proto.ExecuteQueryResponse
import google.firebase.dataconnect.proto.GetEmulatorInfoRequest
import google.firebase.dataconnect.proto.GraphqlResponseExtensions.DataConnectProperties
import google.firebase.dataconnect.proto.StreamEmulatorIssuesRequest
import google.firebase.dataconnect.proto.StreamRequest
import google.firebase.dataconnect.proto.StreamResponse
import io.grpc.CallOptions
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import io.grpc.android.AndroidChannelBuilder
import java.lang.System.currentTimeMillis
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class DataConnectGrpcRPCs(
  context: Context,
  host: String,
  sslEnabled: Boolean,
  @get:VisibleForTesting val connectorResourceName: String,
  private val nonBlockingCoroutineDispatcher: CoroutineDispatcher,
  private val blockingCoroutineDispatcher: CoroutineDispatcher,
  private val grpcMetadata: DataConnectGrpcMetadata,
  private val cache: DataConnectCache?,
  parentLogger: Logger,
) {
  private val logger =
    Logger("DataConnectGrpcRPCs").apply {
      debug {
        "created by ${parentLogger.nameWithId} with" +
          " host=$host" +
          " sslEnabled=$sslEnabled" +
          " cache=$cache" +
          " grpcMetadata=${grpcMetadata.instanceId}"
      }
    }
  val instanceId: String
    get() = logger.nameWithId

  @VisibleForTesting
  data class TestInfo(val context: Context, val host: String, val sslEnabled: Boolean)

  @get:VisibleForTesting val testInfo = TestInfo(context, host, sslEnabled)

  private val mutex = Mutex()
  private var closed = false

  // Use the non-main-thread CoroutineDispatcher to avoid blocking operations on the main thread.
  private val lazyGrpcChannel =
    SuspendingLazy(mutex = mutex, coroutineContext = blockingCoroutineDispatcher) {
      check(!closed) { "DataConnectGrpcRPCs ${logger.nameWithId} instance has been closed" }
      logger.debug { "Creating GRPC ManagedChannel for host=$host sslEnabled=$sslEnabled" }
      val executor = blockingCoroutineDispatcher.asExecutor()
      val grpcChannel = createGrpcManagedChannel(context, host, sslEnabled, executor, logger)
      logger.debug { "Creating GRPC ManagedChannel for host=$host sslEnabled=$sslEnabled done" }
      grpcChannel
    }

  private val lazyGrpcStub =
    SuspendingLazy(mutex) {
      check(!closed) { "DataConnectGrpcRPCs ${logger.nameWithId} instance has been closed" }
      ConnectorServiceGrpcKt.ConnectorServiceCoroutineStub(lazyGrpcChannel.getLocked())
    }

  private val lazyEmulatorGrpcStub =
    SuspendingLazy(mutex) {
      check(!closed) { "DataConnectGrpcRPCs ${logger.nameWithId} instance has been closed" }
      EmulatorServiceGrpcKt.EmulatorServiceCoroutineStub(lazyGrpcChannel.getLocked())
    }

  private val connectCoroutineScope =
    CoroutineUtils.createSupervisorCoroutineScope(
      nonBlockingCoroutineDispatcher,
      logger,
      coroutineName = "connectCoroutineScope@${logger.nameWithId}"
    )

  suspend fun executeMutation(
    requestId: String,
    operationName: String,
    variables: Struct,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
    authToken: DataConnectAuth.GetAuthTokenResult?,
    appCheckToken: DataConnectAppCheck.GetAppCheckTokenResult?,
  ): ExecuteMutationResponse {
    val metadata = grpcMetadata.get(authToken, appCheckToken, callerSdkType)
    val kotlinMethodName = "executeMutation($operationName)"

    val request =
      ExecuteMutationRequest.newBuilder().let {
        it.setName(connectorResourceName)
        it.setOperationName(operationName)
        it.setVariables(variables)
        it.build()
      }

    logger.logGrpcSending(
      requestId = requestId,
      kotlinMethodName = kotlinMethodName,
      grpcMethod = ConnectorServiceGrpc.getExecuteMutationMethod(),
      metadata = metadata,
      request = { request.toStructProto() },
      requestTypeName = "ExecuteMutationRequest",
      authUid = authToken?.authUid,
    )

    val result = lazyGrpcStub.get().runCatching { executeMutation(request, metadata) }

    result.onSuccess {
      logger.logGrpcReceived(
        requestId = requestId,
        kotlinMethodName = kotlinMethodName,
        response = { it.toStructProto() },
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

  sealed interface ExecuteQueryResult {
    @JvmInline value class FromCache(val data: Struct) : ExecuteQueryResult

    @JvmInline value class FromServer(val response: ExecuteQueryResponse) : ExecuteQueryResult
  }

  private class QueryCacheInfo(
    val cache: DataConnectCache,
    val authUid: AuthUid?,
    val queryId: QueryId,
  )

  private suspend fun DataConnectCache.queryCacheInfo(
    authToken: DataConnectAuth.GetAuthTokenResult?,
    request: ExecuteQueryRequest,
  ): QueryCacheInfo {
    val queryId =
      withContext(nonBlockingCoroutineDispatcher) {
        calculateQueryId(request.operationName, request.variables)
      }
    return QueryCacheInfo(this, authToken?.authUid, queryId)
  }

  suspend fun executeQuery(
    requestId: String,
    operationName: String,
    variables: Struct,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
    fetchPolicy: FetchPolicy,
    authToken: DataConnectAuth.GetAuthTokenResult?,
    appCheckToken: DataConnectAppCheck.GetAppCheckTokenResult?,
  ): ExecuteQueryResult {
    val metadata = grpcMetadata.get(authToken, appCheckToken, callerSdkType)
    val kotlinMethodName = "executeQuery($operationName)"

    val request =
      ExecuteQueryRequest.newBuilder().let {
        it.setName(connectorResourceName)
        it.setOperationName(operationName)
        it.setVariables(variables)
        it.build()
      }

    val cacheInfo = cache?.queryCacheInfo(authToken, request)
    if (cacheInfo == null && fetchPolicy == FetchPolicy.CACHE_ONLY) {
      throw CachedDataNotFoundException(
        "FetchPolicy.CACHE_ONLY cannot be used because local caching is not configured. " +
          "To use CACHE_ONLY, specify a DataConnectSettings object with a non-null `cacheSettings` " +
          "property to FirebaseDataConnect.getInstance() [sz664hyg7t]"
      )
    }

    logger.logGrpcSending(
      requestId = requestId,
      kotlinMethodName = kotlinMethodName,
      grpcMethod = ConnectorServiceGrpc.getExecuteQueryMethod(),
      metadata = metadata,
      request = { request.toStructProto() },
      requestTypeName = "ExecuteQueryRequest",
      authUid = authToken?.authUid,
    )

    if (fetchPolicy != FetchPolicy.SERVER_ONLY) {
      val cachedResult: ExecuteQueryResult.FromCache? =
        cacheInfo?.executeQueryAgainstCache(
          requestId = requestId,
          kotlinMethodName = kotlinMethodName,
          fetchPolicy = fetchPolicy,
        )
      if (cachedResult !== null) {
        return cachedResult
      }
    }

    if (fetchPolicy == FetchPolicy.CACHE_ONLY) {
      val exception =
        CachedDataNotFoundException("query was not found in the local cache [cck6p3fmd5]")
      logger.logGrpcFailed(
        requestId = requestId,
        kotlinMethodName = kotlinMethodName,
        throwable = exception,
      )
      throw exception
    }

    val result = lazyGrpcStub.get().runCatching { executeQuery(request, metadata) }

    result.onSuccess { response ->
      logger.logGrpcReceived(
        requestId = requestId,
        kotlinMethodName = kotlinMethodName,
        response = { response.toStructProto() },
        responseTypeName = "ExecuteQueryResponse",
      )

      cacheInfo?.run {
        cache
          .open()
          .insertQueryResult(
            authUid,
            queryId,
            queryData = response.data,
            maxAge = cache.maxAgeProto,
            currentTimeMillis = currentTimeMillis(),
            getEntityIdForPath = response.getEntityIdForPathFunction(),
          )
      }
    }

    result.onFailure {
      logger.logGrpcFailed(
        requestId = requestId,
        kotlinMethodName = kotlinMethodName,
        it,
      )
    }

    return ExecuteQueryResult.FromServer(result.getOrThrow())
  }

  private suspend fun QueryCacheInfo.executeQueryAgainstCache(
    requestId: String,
    kotlinMethodName: String,
    fetchPolicy: FetchPolicy,
  ): ExecuteQueryResult.FromCache? {
    val staleResult =
      when (fetchPolicy) {
        FetchPolicy.CACHE_ONLY -> DataConnectCacheDatabase.GetQueryResultResult.Found::class
        else -> DataConnectCacheDatabase.GetQueryResultResult.Stale::class
      }

    val cachedResult =
      cache.open().getQueryResult(authUid, queryId, currentTimeMillis(), staleResult)

    val cachedData: Struct? =
      when (cachedResult) {
        is DataConnectCacheDatabase.GetQueryResultResult.Found -> {
          logger.logGrpcReturningFromCache(
            requestId = requestId,
            kotlinMethodName = kotlinMethodName,
            cachedResult = cachedResult,
          )
          cachedResult.struct
        }
        is DataConnectCacheDatabase.GetQueryResultResult.Stale -> {
          logger.logGrpcIgnoringStaleCache(
            requestId = requestId,
            kotlinMethodName = kotlinMethodName,
            cachedResult = cachedResult,
          )
          null
        }
        is DataConnectCacheDatabase.GetQueryResultResult.NotFound -> null
      }

    return cachedData?.let(ExecuteQueryResult::FromCache)
  }

  suspend fun connect(
    streamId: String,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
    authToken: DataConnectAuth.GetAuthTokenResult?,
    appCheckToken: DataConnectAppCheck.GetAppCheckTokenResult?,
    idStringGenerator: IdStringGenerator,
  ): DataConnectBidiConnectStream {
    val metadata = grpcMetadata.get(authToken, appCheckToken, callerSdkType)

    val initRequest =
      StreamRequest.newBuilder().setRequestId("init").setName(connectorResourceName).build()

    // For low-level debugging, swap this `grpcBidiFlowListener` out for
    // PrintlnGrpcBidiFlowListener(ConnectGrpcBidiFlowListenerFormatter(authToken?.authUid))
    val grpcBidiFlowListener =
      ConnectGrpcBidiFlowListener(
        streamId = streamId,
        authUid = authToken?.authUid,
        initRequest = initRequest,
        kotlinMethodName = "connect()",
      )

    val flow =
      GrpcBidiFlow.create(
        grpcChannel = lazyGrpcChannel.get(),
        method = ConnectorStreamServiceGrpc.getConnectMethod(),
        callOptions = CallOptions.DEFAULT.withExecutor(blockingCoroutineDispatcher.asExecutor()),
        headers = { GrpcBidiFlow.HeadersResult(metadata, authToken?.authUid) },
        idStringGenerator = idStringGenerator,
        initRequests = listOf(initRequest),
        listener = grpcBidiFlowListener,
      )

    return DataConnectBidiConnectStream(
      flow,
      connectCoroutineScope,
      Logger("DataConnectBidiConnectStream[sid=$streamId]").also {
        it.debug { "created by ${logger.nameWithId}" }
      }
    )
  }

  private inner class ConnectGrpcBidiFlowListener(
    private val streamId: String,
    private val authUid: AuthUid?,
    private val initRequest: StreamRequest,
    private val kotlinMethodName: String,
  ) : GrpcBidiFlow.Listener<StreamRequest, StreamResponse> {

    override fun collectStarted(connectionId: String) = CollectorListener()

    private inner class CollectorListener :
      GrpcBidiFlow.Listener.CollectorListener<StreamRequest, StreamResponse> {

      @Volatile private var headers: Metadata? = null

      override fun collectCompleted(exception: Throwable?) {}

      override fun connectionStarting(
        method: MethodDescriptor<StreamRequest, StreamResponse>,
        callOptions: CallOptions,
        headers: Metadata?,
      ) {
        this.headers = headers?.copy()
      }

      override fun sendingMessage(message: StreamRequest) {
        if (message === initRequest) {
          logger.logGrpcSending(
            requestId = streamId,
            kotlinMethodName = kotlinMethodName,
            grpcMethod = ConnectorStreamServiceGrpc.getConnectMethod(),
            metadata = headers,
            request = { initRequest.toStructProto(authUid) },
            requestTypeName = "StreamRequest",
            authUid = authUid,
          )
        } else {
          logger.logGrpcSending(
            streamId = streamId,
            requestId = message.requestId,
            kotlinMethodName = kotlinMethodName,
            request = { message.toStructProto(authUid) },
            requestTypeName = "StreamRequest",
          )
        }
      }

      override fun sendingMessagesComplete() {}

      override fun sendingMessagesFailed(exception: Throwable) {}

      override fun receivedMessage(message: StreamResponse) {}

      override fun receivingMessagesComplete() {}

      override fun receivingMessagesFailed(exception: Throwable) {}

      override fun onCallReady() {}

      override fun onCallMessage(message: StreamResponse) {
        logger.logGrpcReceived(
          streamId = streamId,
          requestId = message.requestId,
          kotlinMethodName = kotlinMethodName,
          response = { message.toStructProto() },
          responseTypeName = "StreamResponse",
        )
      }

      override fun onCallClose(status: Status, trailers: Metadata, calculatedCause: Throwable?) {
        if (calculatedCause === null) {
          logger.logGrpcCompleted(
            requestId = streamId,
            kotlinMethodName = kotlinMethodName,
          )
        } else {
          logger.logGrpcFailed(
            requestId = streamId,
            kotlinMethodName = kotlinMethodName,
            calculatedCause,
          )
        }
      }
    }
  }

  @Suppress("unused")
  private class ConnectGrpcBidiFlowListenerFormatter(private val authUid: AuthUid?) :
    GrpcBidiFlowListenerMessageFormatter.Formatter<StreamRequest, StreamResponse>() {
    override fun connectionStartingHeaders(headers: Metadata?): String =
      headers?.toStructProto(authUid)?.toCompactString().toString()

    override fun onCloseTrailers(trailers: Metadata): String =
      trailers.toStructProto(authUid).toCompactString()

    override fun request(message: StreamRequest): String = message.toCompactString(authUid)

    override fun response(message: StreamResponse): String = message.toCompactString()
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

    result.onSuccess {
      logger.logGrpcReceived(
        requestId = requestId,
        kotlinMethodName = kotlinMethodName,
        response = { it.toStructProto() },
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
          response = { response.toStructProto() },
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

    connectCoroutineScope.cancel("DataConnectGrpcRPCs.close() called [xn8dqn8dzm]")

    lazyGrpcChannel.initializedValueOrNull?.let { grpcChannel ->
      withContext(blockingCoroutineDispatcher) {
        grpcChannel.shutdownNow()
        grpcChannel.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS)
      }
    }

    connectCoroutineScope.coroutineContext.job.join()
  }

  companion object {

    @VisibleForTesting
    fun createGrpcManagedChannel(
      context: Context,
      host: String,
      sslEnabled: Boolean,
      executor: Executor,
      logger: Logger
    ): ManagedChannel {
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

      return ManagedChannelBuilder.forTarget(host).let {
        if (!sslEnabled) {
          it.usePlaintext()
        }

        // Ensure gRPC recovers from a dead connection. This is not typically necessary, as
        // the OS will usually notify gRPC when a connection dies. But not always. This acts as a
        // failsafe.
        it.keepAliveTime(30, TimeUnit.SECONDS)

        it.executor(executor)

        // Wrap the `ManagedChannelBuilder` in an `AndroidChannelBuilder`. This allows the channel
        // to respond more gracefully to network change events, such as switching from cellular to
        // wifi.
        AndroidChannelBuilder.usingBuilder(it).context(context).build()
      }
    }

    private inline fun Logger.logGrpcSending(
      requestId: String,
      kotlinMethodName: String,
      grpcMethod: MethodDescriptor<*, *>,
      metadata: Metadata?,
      request: () -> Struct,
      requestTypeName: String,
      authUid: AuthUid?,
    ) = debug {
      val requestStruct = request()
      val struct = buildStructProto {
        put("RPC", grpcMethod.fullMethodName)
        put("Metadata", metadata?.toStructProto(authUid))
        put(requestTypeName, requestStruct)
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

    private inline fun Logger.logGrpcSending(
      streamId: String,
      requestId: String,
      kotlinMethodName: String,
      request: () -> Struct,
      requestTypeName: String,
    ) = debug {
      "$kotlinMethodName [sid=$streamId, rid=$requestId] sending $requestTypeName: " +
        request().toCompactString()
    }

    private inline fun Logger.logGrpcReceived(
      streamId: String,
      requestId: String,
      kotlinMethodName: String,
      response: () -> Struct,
      responseTypeName: String,
    ) = debug {
      "$kotlinMethodName [sid=$streamId, rid=$requestId] received $responseTypeName: " +
        response().toCompactString()
    }

    private fun Logger.logGrpcReturningFromCache(
      requestId: String,
      kotlinMethodName: String,
      cachedResult: DataConnectCacheDatabase.GetQueryResultResult.Found,
    ) = debug {
      "$kotlinMethodName [rid=$requestId] returning result from cache: " +
        "${cachedResult.struct.toCompactString()} " +
        "(expires in ${cachedResult.freshnessRemaining})"
    }

    private fun Logger.logGrpcIgnoringStaleCache(
      requestId: String,
      kotlinMethodName: String,
      cachedResult: DataConnectCacheDatabase.GetQueryResultResult.Stale,
    ) = debug {
      "$kotlinMethodName [rid=$requestId] ignoring result from cache " +
        "because it expired ${cachedResult.staleness} ago"
    }

    private fun Logger.logGrpcStarting(
      requestId: String,
      kotlinMethodName: String,
      grpcMethod: MethodDescriptor<*, *>,
    ) = debug { "$kotlinMethodName [rid=$requestId] starting ${grpcMethod.fullMethodName}" }

    private fun Logger.logGrpcCompleted(
      requestId: String,
      kotlinMethodName: String,
    ) = debug { "$kotlinMethodName [rid=$requestId] completed" }

    private inline fun Logger.logGrpcReceived(
      requestId: String,
      kotlinMethodName: String,
      response: () -> Struct,
      responseTypeName: String
    ) = debug {
      val responseStruct = response()
      val struct = buildStructProto { put(responseTypeName, responseStruct) }
      "$kotlinMethodName [rid=$requestId] received: ${struct.toCompactString()}"
    }

    private fun Logger.logGrpcFailed(
      requestId: String,
      kotlinMethodName: String,
      throwable: Throwable,
    ) =
      warn(throwable) {
        val status =
          when (throwable) {
            is StatusException -> throwable.status
            is StatusRuntimeException -> throwable.status
            else -> null
          }
        val statusString = status?.let { " with gRPC code=${status.code}" } ?: ""
        "$kotlinMethodName [rid=$requestId] FAILED$statusString: $throwable"
      }
  }
}

@JvmName("getEntityIdForPathFunction_ExecuteQueryResponse")
private fun ExecuteQueryResponse.getEntityIdForPathFunction(): GetEntityIdForPathFunction? =
  if (extensions.dataConnectCount == 0) {
    null
  } else {
    extensions.dataConnectList.getEntityIdForPathFunction()
  }

@JvmName("getEntityIdForPathFunction_List_DataConnectProperties")
internal fun List<DataConnectProperties>.getEntityIdForPathFunction(): GetEntityIdForPathFunction? {
  val entityIdByPath: Map<DataConnectPath, String>
  val entityIdsByPath: Map<DataConnectPath, List<String>>

  run {
    val entityIdByPathBuilder = mutableMapOf<DataConnectPath, String>()
    val entityIdsByPathBuilder = mutableMapOf<DataConnectPath, List<String>>()

    this.filter { it.hasPath() && it.path.valuesCount > 0 }
      .filter { it.entityId.isNotEmpty() || it.entityIdsCount > 0 }
      .forEach {
        if (it.entityId.isNotEmpty()) {
          entityIdByPathBuilder[it.path.toDataConnectPath()] = it.entityId
        }
        if (it.entityIdsCount > 0) {
          entityIdsByPathBuilder[it.path.toDataConnectPath()] = it.entityIdsList
        }
      }

    if (entityIdByPathBuilder.isEmpty() && entityIdsByPathBuilder.isEmpty()) {
      return null
    }

    entityIdByPath = entityIdByPathBuilder.toMap()
    entityIdsByPath = entityIdsByPathBuilder.toMap()
  }

  fun getEntityIdForPathFunction(path: DataConnectPath): String? {
    entityIdByPath[path]?.let { entityId ->
      return entityId
    }

    val lastSegment = path.lastOrNull() as? DataConnectPathSegment.ListIndex
    return lastSegment?.index?.let { index ->
      val parentPath = path.dropLast(1)
      val entityIds = entityIdsByPath[parentPath]
      entityIds?.getOrNull(index)
    }
  }

  return ::getEntityIdForPathFunction
}
