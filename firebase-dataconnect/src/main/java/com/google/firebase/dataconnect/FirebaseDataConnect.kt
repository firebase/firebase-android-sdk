// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.firebase.dataconnect

import android.content.Context
import com.google.android.gms.security.ProviderInstaller
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.android.AndroidChannelBuilder
import java.util.concurrent.TimeUnit

enum class GrpcConnectionEncryption {
  PLAINTEXT,
  ENCRYPTED,
}

/**
 * Open a GRPC connection.
 *
 * @param context A context to use; this context will simply be used to get the application context;
 * therefore, specifying a transient context, such as an `Activity`, will _not_ result in that
 * context being leaked.
 * @param host The host name of the server to which to connect. (e.g. `"firestore.googleapis.com"`,
 * `"10.0.2.2:9510"`)
 * @param encryption The encryption to use.
 * @param logger A logger to use.
 */
fun createManagedChannel(
  context: Context,
  host: String,
  encryption: GrpcConnectionEncryption,
  logger: Logger
): ManagedChannel {
  upgradeAndroidSecurityProvider(context, logger)

  val channelBuilder = ManagedChannelBuilder.forTarget(host)

  when (encryption) {
    GrpcConnectionEncryption.PLAINTEXT -> channelBuilder.usePlaintext()
    GrpcConnectionEncryption.ENCRYPTED -> {}
  }

  // Ensure gRPC recovers from a dead connection. This is not typically necessary, as
  // the OS will  usually notify gRPC when a connection dies. But not always. This acts as a
  // failsafe.
  channelBuilder.keepAliveTime(30, TimeUnit.SECONDS)

  // Wrap the `ManagedChannelBuilder` in an `AndroidChannelBuilder`. This allows the channel to
  // respond more gracefully to network change events, such as switching from cellular to wifi.
  return AndroidChannelBuilder.usingBuilder(channelBuilder)
    .context(context.applicationContext)
    .build()
}

/**
 * Upgrade the Android security provider using Google Play Services.
 *
 * We need to upgrade the Security Provider before any network channels are initialized because
 * okhttp maintains a list of supported providers that is initialized when the JVM first resolves
 * the static dependencies of ManagedChannel.
 *
 * If initialization fails for any reason, then a warning is logged and this function returns as if
 * successful.
 */
private fun upgradeAndroidSecurityProvider(context: Context, logger: Logger) {
  try {
    ProviderInstaller.installIfNeeded(context.applicationContext)
  } catch (e: Exception) {
    logger.warn(e) { "Failed to update ssl context" }
  }
}
