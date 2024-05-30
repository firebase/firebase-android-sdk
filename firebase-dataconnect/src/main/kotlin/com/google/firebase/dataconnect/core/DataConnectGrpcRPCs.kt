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
import com.google.firebase.dataconnect.util.SuspendingLazy
import google.firebase.dataconnect.proto.ConnectorServiceGrpcKt
import google.firebase.dataconnect.proto.ExecuteMutationRequest
import google.firebase.dataconnect.proto.ExecuteMutationResponse
import google.firebase.dataconnect.proto.ExecuteQueryRequest
import google.firebase.dataconnect.proto.ExecuteQueryResponse
import io.grpc.ManagedChannelBuilder
import io.grpc.Metadata
import io.grpc.android.AndroidChannelBuilder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal interface DataConnectGrpcRPCs {

  suspend fun executeMutation(
    request: ExecuteMutationRequest,
    headers: Metadata
  ): ExecuteMutationResponse

  suspend fun executeQuery(request: ExecuteQueryRequest, headers: Metadata): ExecuteQueryResponse

  suspend fun close()
}

internal class DataConnectGrpcRPCsImpl(
  context: Context,
  host: String,
  sslEnabled: Boolean,
  private val coroutineDispatcher: CoroutineDispatcher,
) : DataConnectGrpcRPCs {

  private val logger =
    Logger("DataConnectGrpcRPCsImpl").apply { debug { "host=$host sslEnabled=$sslEnabled" } }

  private val mutex = Mutex()
  private var closed = false

  // Use the non-main-thread CoroutineDispatcher to avoid blocking operations on the main thread.
  private val lazyGrpcChannel =
    SuspendingLazy(mutex = mutex, coroutineContext = coroutineDispatcher) {
      check(!closed) { "DataConnectGrpcRPCsImpl is closed" }

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

      ManagedChannelBuilder.forTarget(host).let {
        if (!sslEnabled) {
          it.usePlaintext()
        }

        // Ensure gRPC recovers from a dead connection. This is not typically necessary, as
        // the OS will usually notify gRPC when a connection dies. But not always. This acts as a
        // failsafe.
        it.keepAliveTime(30, TimeUnit.SECONDS)

        it.executor(coroutineDispatcher.asExecutor())

        // Wrap the `ManagedChannelBuilder` in an `AndroidChannelBuilder`. This allows the channel
        // to respond more gracefully to network change events, such as switching from cellular to
        // wifi.
        AndroidChannelBuilder.usingBuilder(it).context(context).build()
      }
    }

  private val lazyGrpcStub =
    SuspendingLazy(mutex) {
      ConnectorServiceGrpcKt.ConnectorServiceCoroutineStub(lazyGrpcChannel.getLocked())
    }

  override suspend fun executeMutation(request: ExecuteMutationRequest, headers: Metadata) =
    lazyGrpcStub.get().executeMutation(request, headers)

  override suspend fun executeQuery(request: ExecuteQueryRequest, headers: Metadata) =
    lazyGrpcStub.get().executeQuery(request, headers)

  override suspend fun close() {
    mutex.withLock { closed = true }

    val grpcChannel = lazyGrpcChannel.initializedValueOrNull ?: return

    // Avoid blocking the calling thread by running potentially-blocking code on the dispatcher
    // given to the constructor, which should have similar semantics to [Dispatchers.IO].
    withContext(coroutineDispatcher) {
      grpcChannel.shutdownNow()
      grpcChannel.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS)
    }
  }
}

internal interface DataConnectGrpcRPCsFactory {

  val host: String
  val sslEnabled: Boolean

  fun newInstance(): DataConnectGrpcRPCs
}

internal class DataConnectGrpcRPCsFactoryImpl(
  private val context: Context,
  override val host: String,
  override val sslEnabled: Boolean,
  private val coroutineDispatcher: CoroutineDispatcher,
) : DataConnectGrpcRPCsFactory {
  override fun newInstance() =
    DataConnectGrpcRPCsImpl(context, host, sslEnabled, coroutineDispatcher)
}
