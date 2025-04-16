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

import com.google.firebase.FirebaseApp
import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.util.ProtoUtil.buildStructProto
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
import io.grpc.Status
import io.grpc.StatusException
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
  FactoryTestRule<
    InProcessDataConnectGrpcServer.ServerInfo, InProcessDataConnectGrpcServer.Params
  >() {

  fun newInstance(
    errors: List<Status>? = null,
    executeQueryResponse: ExecuteQueryResponse? = null,
    executeMutationResponse: ExecuteMutationResponse? = null
  ): ServerInfo =
    createInstance(
      errors = errors,
      executeQueryResponse = executeQueryResponse,
      executeMutationResponse = executeMutationResponse
    )

  override fun createInstance(params: Params?): ServerInfo {
    return createInstance(
      params?.errors,
      params?.executeQueryResponse,
      params?.executeMutationResponse
    )
  }

  private fun createInstance(
    errors: List<Status>? = null,
    executeQueryResponse: ExecuteQueryResponse? = null,
    executeMutationResponse: ExecuteMutationResponse? = null
  ): ServerInfo {
    val serverInterceptor = ServerInterceptorImpl(errors ?: Params.defaults.errors)
    val connectorService =
      ConnectorServiceImpl(
        executeQueryResponse ?: Params.defaults.executeQueryResponse,
        executeMutationResponse ?: Params.defaults.executeMutationResponse
      )
    val grpcServer =
      OkHttpServerBuilder.forPort(0, InsecureServerCredentials.create())
        .addService(connectorService)
        .intercept(serverInterceptor)
        .build()
    grpcServer.start()
    return ServerInfo(grpcServer, serverInterceptor.metadatas)
  }

  data class Params(
    val errors: List<Status> = emptyList(),
    val executeQueryResponse: ExecuteQueryResponse? = null,
    val executeMutationResponse: ExecuteMutationResponse? = null
  ) {
    companion object {
      val defaults = Params()
    }
  }

  override fun destroyInstance(instance: ServerInfo) {
    instance.server.shutdownNow()
  }

  data class ServerInfo(val server: Server, val metadatas: Flow<Metadata>)

  private class ServerInterceptorImpl(errors: List<Status> = emptyList()) : ServerInterceptor {

    private val errors = errors.toList().iterator()

    private val _metadatas =
      MutableSharedFlow<Metadata>(replay = Int.MAX_VALUE, onBufferOverflow = DROP_OLDEST)

    val metadatas = _metadatas.asSharedFlow()

    override fun <ReqT : Any?, RespT : Any?> interceptCall(
      call: ServerCall<ReqT, RespT>,
      headers: Metadata,
      next: ServerCallHandler<ReqT, RespT>
    ): ServerCall.Listener<ReqT> {
      check(_metadatas.tryEmit(headers)) { "_metadatas.tryEmit(headers) failed" }

      synchronized(errors) {
        if (errors.hasNext()) {
          throw StatusException(errors.next())
        }
      }

      return next.startCall(call, headers)
    }
  }

  private class ConnectorServiceImpl(
    val executeQueryResponse: ExecuteQueryResponse? = null,
    val executeMutationResponse: ExecuteMutationResponse? = null
  ) : ConnectorServiceGrpc.ConnectorServiceImplBase() {
    override fun executeQuery(
      request: ExecuteQueryRequest,
      responseObserver: StreamObserver<ExecuteQueryResponse>
    ) {
      val responseData = buildStructProto { put("foo", "prj5hbhqcw") }
      val response =
        executeQueryResponse ?: ExecuteQueryResponse.newBuilder().setData(responseData).build()
      responseObserver.onNext(response)
      responseObserver.onCompleted()
    }

    override fun executeMutation(
      request: ExecuteMutationRequest,
      responseObserver: StreamObserver<ExecuteMutationResponse>
    ) {
      val responseData = buildStructProto { put("foo", "weevgvyecf") }
      val response =
        executeMutationResponse
          ?: ExecuteMutationResponse.newBuilder().setData(responseData).build()
      responseObserver.onNext(response)
      responseObserver.onCompleted()
    }
  }
}

fun TestDataConnectFactory.Params.copy(
  serverInfo: InProcessDataConnectGrpcServer.ServerInfo
): TestDataConnectFactory.Params =
  copy(
    backend =
      DataConnectBackend.Custom(host = "127.0.0.1:${serverInfo.server.port}", sslEnabled = false)
  )

fun TestDataConnectFactory.newInstance(
  serverInfo: InProcessDataConnectGrpcServer.ServerInfo
): FirebaseDataConnect = newInstance(TestDataConnectFactory.Params().copy(serverInfo))

fun TestDataConnectFactory.newInstance(
  firebaseApp: FirebaseApp,
  serverInfo: InProcessDataConnectGrpcServer.ServerInfo
): FirebaseDataConnect =
  newInstance(TestDataConnectFactory.Params(firebaseApp = firebaseApp).copy(serverInfo))
