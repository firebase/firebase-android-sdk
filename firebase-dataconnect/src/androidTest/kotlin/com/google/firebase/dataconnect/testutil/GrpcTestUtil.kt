/*
 * Copyright 2026 Google LLC
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
import com.google.firebase.dataconnect.core.DataConnectGrpcRPCs
import com.google.firebase.dataconnect.core.FirebaseDataConnectInternal
import com.google.firebase.dataconnect.core.Logger
import io.grpc.ManagedChannel
import io.mockk.mockk
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor

/**
 * Information about the gRPC configuration used by this [FirebaseDataConnect] instance.
 *
 * This is primarily intended for use in tests to inspect or replicate the gRPC connection
 * parameters, such as the host and whether SSL is enabled.
 */
internal val FirebaseDataConnect.grpcTestInfo: DataConnectGrpcRPCs.TestInfo
  get() = (this as FirebaseDataConnectInternal).grpcRPCs.testInfo

/**
 * Creates a gRPC [ManagedChannel] that connects to the same remote host as the given
 * [FirebaseDataConnect] instance, and registers the [ManagedChannel] for automatic cleanup after
 * the test completes.
 *
 * This is useful for integration tests that need to interact with the gRPC layer directly, for
 * example to verify interceptors or low-level communication.
 *
 * @param dataConnect The [FirebaseDataConnect] instance whose gRPC configuration (host, SSL, etc.)
 * to use to configure the new [ManagedChannel].
 * @return A new [ManagedChannel] configured to connect to the same backend as [dataConnect].
 */
internal fun DataConnectIntegrationTestBase.createGrpcManagedChannel(
  dataConnect: FirebaseDataConnect
): ManagedChannel {
  val (context, host, sslEnabled) = dataConnect.grpcTestInfo

  val managedChannel =
    DataConnectGrpcRPCs.createGrpcManagedChannel(
      context,
      host,
      sslEnabled,
      Dispatchers.IO.asExecutor(),
      mockk<Logger>(name = "TestGrpcManagedChannel", relaxed = true),
    )

  cleanups.register {
    managedChannel.shutdownNow()
    managedChannel.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS)
  }

  return managedChannel
}
