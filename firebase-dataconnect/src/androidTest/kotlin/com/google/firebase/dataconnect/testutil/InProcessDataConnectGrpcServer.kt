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

package com.google.firebase.dataconnect.testutil

import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.util.buildStructProto
import google.firebase.dataconnect.proto.ConnectorServiceGrpc
import google.firebase.dataconnect.proto.ExecuteMutationRequest
import google.firebase.dataconnect.proto.ExecuteMutationResponse
import google.firebase.dataconnect.proto.ExecuteQueryRequest
import google.firebase.dataconnect.proto.ExecuteQueryResponse
import google.firebase.dataconnect.proto.executeMutationResponse
import google.firebase.dataconnect.proto.executeQueryResponse
import io.grpc.InsecureServerCredentials
import io.grpc.Metadata
import io.grpc.Server
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.okhttp.OkHttpServerBuilder
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A JUnit test rule that creates a GRPC server that listens on a local port and can be used in lieu
 * of a real GRPC server.
 */
class InProcessDataConnectGrpcServer :
  FactoryTestRule<InProcessDataConnectGrpcServer.ServerInfo, Nothing>() {

  override fun createInstance(params: Nothing?): ServerInfo {
    val serverInterceptor = ServerInterceptorImpl()
    val grpcServer =
      OkHttpServerBuilder.forPort(0, InsecureServerCredentials.create())
        .addService(ConnectorServiceImpl())
        .intercept(serverInterceptor)
        .build()
    grpcServer.start()

    return ServerInfo(grpcServer, serverInterceptor.metadatas)
  }

  override fun destroyInstance(instance: ServerInfo) {
    instance.server.shutdownNow()
  }

  data class ServerInfo(val server: Server, val metadatas: Flow<Metadata>)

  private class ServerInterceptorImpl : ServerInterceptor {

    private val _metadatas =
      MutableSharedFlow<Metadata>(replay = Int.MAX_VALUE, onBufferOverflow = DROP_OLDEST)

    val metadatas = _metadatas.asSharedFlow()

    override fun <ReqT : Any?, RespT : Any?> interceptCall(
      call: ServerCall<ReqT, RespT>,
      headers: Metadata,
      next: ServerCallHandler<ReqT, RespT>
    ): ServerCall.Listener<ReqT> {
      check(_metadatas.tryEmit(headers)) { "_metadatas.tryEmit(headers) failed" }
      return next.startCall(call, headers)
    }
  }

  private class ConnectorServiceImpl : ConnectorServiceGrpc.ConnectorServiceImplBase() {
    override fun executeQuery(
      request: ExecuteQueryRequest,
      responseObserver: StreamObserver<ExecuteQueryResponse>
    ) {
      responseObserver.onNext(executeQueryResponse { data = buildStructProto {} })
      responseObserver.onCompleted()
    }

    override fun executeMutation(
      request: ExecuteMutationRequest,
      responseObserver: StreamObserver<ExecuteMutationResponse>
    ) {
      responseObserver.onNext(executeMutationResponse { data = buildStructProto {} })
      responseObserver.onCompleted()
    }
  }
}

fun TestDataConnectFactory.newInstance(server: Server): FirebaseDataConnect =
  newInstance(DataConnectBackend.Custom(host = "127.0.0.1:${server.port}", sslEnabled = false))

fun TestDataConnectFactory.newInstance(
  serverInfo: InProcessDataConnectGrpcServer.ServerInfo
): FirebaseDataConnect = newInstance(serverInfo.server)
