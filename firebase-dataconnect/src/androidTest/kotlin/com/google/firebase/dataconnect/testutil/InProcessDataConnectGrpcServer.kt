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

import com.google.firebase.dataconnect.util.buildStructProto
import google.firebase.dataconnect.proto.ConnectorServiceGrpc
import google.firebase.dataconnect.proto.ExecuteMutationRequest
import google.firebase.dataconnect.proto.ExecuteMutationResponse
import google.firebase.dataconnect.proto.ExecuteQueryRequest
import google.firebase.dataconnect.proto.ExecuteQueryResponse
import google.firebase.dataconnect.proto.executeMutationResponse
import google.firebase.dataconnect.proto.executeQueryResponse
import io.grpc.InsecureServerCredentials
import io.grpc.Server
import io.grpc.okhttp.OkHttpServerBuilder
import io.grpc.stub.StreamObserver

/**
 * A JUnit test rule that creates a GRPC server that listens on a local port and can be used in lieu
 * of a real GRPC server.
 */
class InProcessDataConnectGrpcServer : FactoryTestRule<Server, Nothing>() {

  override fun createInstance(params: Nothing?): Server {
    val grpcServer =
      OkHttpServerBuilder.forPort(0, InsecureServerCredentials.create())
        .addService(ConnectorServiceImpl())
        .build()
    grpcServer.start()
    return grpcServer
  }

  override fun destroyInstance(instance: Server) {
    instance.shutdownNow()
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
