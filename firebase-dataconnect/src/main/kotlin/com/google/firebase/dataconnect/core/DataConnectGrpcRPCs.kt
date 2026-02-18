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
import com.google.firebase.dataconnect.DataConnectPath
import com.google.firebase.dataconnect.DataConnectPathSegment
import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.core.DataConnectGrpcMetadata.Companion.toStructProto
import com.google.firebase.dataconnect.core.LoggerGlobals.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.core.LoggerGlobals.warn
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabase
import com.google.firebase.dataconnect.sqlite.GetEntityIdForPathFunction
import com.google.firebase.dataconnect.util.ImmutableByteArray
import com.google.firebase.dataconnect.util.NullableReference
import com.google.firebase.dataconnect.util.ProtoUtil.buildStructProto
import com.google.firebase.dataconnect.util.ProtoUtil.calculateSha512
import com.google.firebase.dataconnect.util.ProtoUtil.toCompactString
import com.google.firebase.dataconnect.util.ProtoUtil.toDataConnectPath
import com.google.firebase.dataconnect.util.ProtoUtil.toStructProto
import com.google.firebase.dataconnect.util.SuspendingLazy
import com.google.protobuf.Struct
import google.firebase.dataconnect.proto.ConnectorServiceGrpc
import google.firebase.dataconnect.proto.ConnectorServiceGrpcKt
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
import io.grpc.ManagedChannelBuilder
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.android.AndroidChannelBuilder
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class DataConnectGrpcRPCs(
  context: Context,
  host: String,
  sslEnabled: Boolean,
  private val blockingCoroutineDispatcher: CoroutineDispatcher,
  private val grpcMetadata: DataConnectGrpcMetadata,
  private val cacheSettings: CacheSettings?,
  parentLogger: Logger,
) {
  private val logger =
    Logger("DataConnectGrpcRPCs").apply {
      debug {
        "created by ${parentLogger.nameWithId} with" +
          " host=$host" +
          " sslEnabled=$sslEnabled" +
          " cacheSettings=$cacheSettings" +
          " grpcMetadata=${grpcMetadata.instanceId}"
      }
    }
  val instanceId: String
    get() = logger.nameWithId

  private val mutex = Mutex()
  private var closed = false

  data class CacheSettings(val dbFile: File?)

  // Use the non-main-thread CoroutineDispatcher to avoid blocking operations on the main thread.
  private val lazyCacheDb =
    SuspendingLazy(mutex = mutex, coroutineContext = blockingCoroutineDispatcher) {
      check(!closed) { "DataConnectGrpcRPCs ${logger.nameWithId} instance has been closed" }
      if (cacheSettings === null) {
        NullableReference()
      } else {
        logger.debug { "Creating GRPC ManagedChannel for host=$host sslEnabled=$sslEnabled" }
        val dbFile = cacheSettings.dbFile
        val cacheLogger = Logger("DataConnectCacheDatabase")
        cacheLogger.debug { "created by ${logger.nameWithId} with dbFile=$dbFile" }
        val cacheDb = DataConnectCacheDatabase(dbFile, cacheLogger)
        cacheDb.initialize()
        NullableReference(cacheDb)
      }
    }

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

  private val lazyEmulatorGrpcStub =
    SuspendingLazy(mutex) {
      check(!closed) { "DataConnectGrpcRPCs ${logger.nameWithId} instance has been closed" }
      EmulatorServiceGrpcKt.EmulatorServiceCoroutineStub(lazyGrpcChannel.getLocked())
    }

  suspend fun executeMutation(
    requestId: String,
    request: ExecuteMutationRequest,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): ExecuteMutationResponse {
    val metadata = grpcMetadata.get(requestId, callerSdkType).metadata
    val kotlinMethodName = "executeMutation(${request.operationName})"

    logger.logGrpcSending(
      requestId = requestId,
      kotlinMethodName = kotlinMethodName,
      grpcMethod = ConnectorServiceGrpc.getExecuteMutationMethod(),
      metadata = metadata,
      request = request.toStructProto(),
      requestTypeName = "ExecuteMutationRequest",
    )

    val result = lazyGrpcStub.get().runCatching { executeMutation(request, metadata) }

    result.onSuccess {
      logger.logGrpcReceived(
        requestId = requestId,
        kotlinMethodName = kotlinMethodName,
        response = it.toStructProto(),
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
    val cacheDb: DataConnectCacheDatabase,
    val authUid: String?,
    val queryId: ImmutableByteArray,
  )

  private suspend fun queryCacheInfo(
    authToken: DataConnectAuth.GetAuthTokenResult?,
    request: ExecuteQueryRequest,
  ) =
    lazyCacheDb.get().ref?.let { cacheDb ->
      QueryCacheInfo(
        cacheDb,
        authUid = authToken?.authUid,
        queryId = request.toQueryId(),
      )
    }

  suspend fun executeQuery(
    requestId: String,
    request: ExecuteQueryRequest,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): ExecuteQueryResult {
    val (metadata, authToken) = grpcMetadata.get(requestId, callerSdkType)
    val kotlinMethodName = "executeQuery(${request.operationName})"

    logger.logGrpcSending(
      requestId = requestId,
      kotlinMethodName = kotlinMethodName,
      grpcMethod = ConnectorServiceGrpc.getExecuteQueryMethod(),
      metadata = metadata,
      request = request.toStructProto(),
      requestTypeName = "ExecuteQueryRequest",
    )

    val cacheInfo = queryCacheInfo(authToken, request)

    cacheInfo?.run {
      when (val cachedResult = cacheDb.getQueryResult(authUid, queryId)) {
        is DataConnectCacheDatabase.GetQueryResultResult.Found -> {
          logger.logGrpcReturningFromCache(
            requestId = requestId,
            kotlinMethodName = kotlinMethodName,
            cachedResult = cachedResult,
          )
          return ExecuteQueryResult.FromCache(cachedResult.struct)
        }
        is DataConnectCacheDatabase.GetQueryResultResult.Stale ->
          logger.logGrpcIgnoringStaleCache(
            requestId = requestId,
            kotlinMethodName = kotlinMethodName,
            cachedResult = cachedResult,
          )
        is DataConnectCacheDatabase.GetQueryResultResult.NotFound -> {}
      }
    }

    val result = lazyGrpcStub.get().runCatching { executeQuery(request, metadata) }

    result.onSuccess { response ->
      logger.logGrpcReceived(
        requestId = requestId,
        kotlinMethodName = kotlinMethodName,
        response = response.toStructProto(),
        responseTypeName = "ExecuteQueryResponse",
      )

      cacheInfo?.run {
        cacheDb.insertQueryResult(
          authUid,
          queryId,
          queryData = response.data,
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
        response = it.toStructProto(),
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
          response = response.toStructProto(),
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

    val grpcChannel = lazyGrpcChannel.initializedValueOrNull
    val cacheDb = lazyCacheDb.initializedValueOrNull?.ref

    if (grpcChannel === null && cacheDb === null) {
      return
    }

    // Avoid blocking the calling thread by running potentially-blocking code on the dispatcher
    // given to the constructor, which should have similar semantics to [Dispatchers.IO].
    withContext(blockingCoroutineDispatcher) {
      val grpcChannelShutdownResult = runCatching {
        grpcChannel?.shutdownNow()
        grpcChannel?.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS)
      }
      val cacheDbCloseResult = runCatching { cacheDb?.close() }

      grpcChannelShutdownResult.getOrThrow()
      cacheDbCloseResult.getOrThrow()
    }
  }

  private companion object {
    fun Logger.logGrpcSending(
      requestId: String,
      kotlinMethodName: String,
      grpcMethod: MethodDescriptor<*, *>,
      metadata: Metadata,
      request: Struct,
      requestTypeName: String
    ) = debug {
      val struct = buildStructProto {
        put("RPC", grpcMethod.fullMethodName)
        put("Metadata", metadata.toStructProto())
        put(requestTypeName, request)
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

    fun Logger.logGrpcReturningFromCache(
      requestId: String,
      kotlinMethodName: String,
      cachedResult: DataConnectCacheDatabase.GetQueryResultResult.Found,
    ) = debug {
      "$kotlinMethodName [rid=$requestId] returning result from cache: " +
        "${cachedResult.struct.toCompactString()} " +
        "(expires in ${cachedResult.millisUntilStale} milliseconds)"
    }

    fun Logger.logGrpcIgnoringStaleCache(
      requestId: String,
      kotlinMethodName: String,
      cachedResult: DataConnectCacheDatabase.GetQueryResultResult.Stale,
    ) = debug {
      "$kotlinMethodName [rid=$requestId] ignoring result from cache " +
        "because it expired ${cachedResult.millisStale} milliseconds ago"
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

    fun Logger.logGrpcReceived(
      requestId: String,
      kotlinMethodName: String,
      response: Struct,
      responseTypeName: String
    ) = debug {
      val struct = buildStructProto { put(responseTypeName, response) }
      "$kotlinMethodName [rid=$requestId] received: ${struct.toCompactString()}"
    }

    fun Logger.logGrpcFailed(
      requestId: String,
      kotlinMethodName: String,
      throwable: Throwable,
    ) = warn(throwable) { "$kotlinMethodName [rid=$requestId] FAILED: $throwable" }
  }
}

private fun ExecuteQueryRequest.toQueryId(): ImmutableByteArray {
  val queryId = variables.calculateSha512(preamble = operationName)
  return ImmutableByteArray.adopt(queryId)
}

@JvmName("getEntityIdForPathFunction_ExecuteQueryResponse")
private fun ExecuteQueryResponse.getEntityIdForPathFunction(): GetEntityIdForPathFunction? =
  if (extensions.dataConnectCount == 0) {
    null
  } else {
    extensions.dataConnectList.getEntityIdForPathFunction()
  }

@JvmName("getEntityIdForPathFunction_List_DataConnectProperties")
private fun List<DataConnectProperties>.getEntityIdForPathFunction(): GetEntityIdForPathFunction? {
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
