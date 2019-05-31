// Copyright 2019 Google LLC
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

package com.google.firebase.firestore.util;

import android.content.Context;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.security.ProviderInstaller;
import com.google.firestore.v1.FirestoreGrpc;
import io.grpc.CallCredentials;
import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/**
 * Manages the GRPC channel and encapsulates all SSL and GRPC initialization.
 *
 * <p>All operations are dispatched to an internal worker queue and are not executed until the SSL
 * and GRPC Stub initialization completes.
 */
// PORTING NOTE: This class only exists on Android.
class GrpcCallProvider {

  private static final String LOG_TAG = "GrpcCallProvider";

  private static final RejectedExecutionHandler IGNORE_REJECTIONS_HANDLER =
      (runnable, executor) -> {
        // We ignore rejected executions, since some GRPC messages can arrive after shutdown.
      };

  private final ExecutorService channelQueue;
  private final ManagedChannel channel;

  private CallOptions callOptions;
  private boolean shutdown = false;

  GrpcCallProvider(
      AsyncQueue queue,
      Context context,
      ManagedChannel grpcChannel,
      CallCredentials firestoreHeaders) {
    this.channel = grpcChannel;

    ThreadFactory threadFactory =
        runnable -> {
          Thread thread = Executors.defaultThreadFactory().newThread(runnable);
          thread.setName("FirestoreGrpcWorker");
          thread.setDaemon(true);
          thread.setUncaughtExceptionHandler((crashingThread, throwable) -> queue.panic(throwable));
          return thread;
        };

    this.channelQueue =
        new ThreadPoolExecutor(
            /* corePoolSize= */ 1,
            /* maximumPoolSize= */ 1,
            /* keepAliveTime= */ 0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(),
            threadFactory,
            IGNORE_REJECTIONS_HANDLER);

    // We execute network initialization on an internal queue to not block operations that
    // depend on the AsyncQueue.
    channelQueue.execute(
        () -> {
          FirestoreGrpc.FirestoreStub firestoreStub =
              FirestoreGrpc.newStub(grpcChannel).withCallCredentials(firestoreHeaders);
          callOptions = firestoreStub.getCallOptions();

          try {
            ProviderInstaller.installIfNeeded(context);
          } catch (GooglePlayServicesNotAvailableException
              | GooglePlayServicesRepairableException e) {
            // Mark the SSL initialization as done, even though we may be using outdated SSL
            // ciphers. GRPC-Java recommends obtaining updated ciphers from GMSCore, but we allow
            // the device to fall back to other SSL ciphers if GMSCore is not available.
            Logger.warn(LOG_TAG, "Failed to update ssl context: %s", e);
          }
        });
  }

  /** Creates a new ClientCall. */
  <ReqT, RespT> ClientCall<ReqT, RespT> createClientCall(
      MethodDescriptor<ReqT, RespT> methodDescriptor) {
    Assert.hardAssert(!shutdown, "GrpcCallProvider already shut down");

    // Return a client call that is directly consumable. Note that we do not forward any operations
    // until the initialization of the SSL stack and GRPC stub completes.
    return new ClientCall<ReqT, RespT>() {
      private ClientCall<ReqT, RespT> call;

      private void ensureChannel() {
        Assert.hardAssert(channel != null, "Channel is not initialized");
        if (call == null) {
          call = channel.newCall(methodDescriptor, callOptions);
        }
      }

      @Override
      public void start(Listener<RespT> responseListener, Metadata headers) {
        channelQueue.execute(
            () -> {
              ensureChannel();
              call.start(responseListener, headers);
            });
      }

      @Override
      public void request(int numMessages) {
        channelQueue.execute(
            () -> {
              ensureChannel();
              call.request(numMessages);
            });
      }

      @Override
      public void cancel(@Nullable String message, @Nullable Throwable cause) {
        channelQueue.execute(
            () -> {
              ensureChannel();
              call.cancel(message, cause);
            });
      }

      @Override
      public void halfClose() {
        channelQueue.execute(
            () -> {
              ensureChannel();
              call.halfClose();
            });
      }

      @Override
      public void sendMessage(ReqT message) {
        channelQueue.execute(
            () -> {
              ensureChannel();
              call.sendMessage(message);
            });
      }
    };
  }

  /** Shuts down the GRPC channel and the internal worker queue. */
  public void shutdown() {
    shutdown = true;

    channelQueue.execute(
        () -> {
          Assert.hardAssert(
              channel != null, "Channel shutdown called when channel is not initialized");

          channel.shutdown();
          try {
            // TODO(rsgowman): Investigate occasional hangs in channel.shutdown().
            //
            // While running the integration tests, channel.shutdown() will occasionally timeout.
            // (Typically on ~4-5 different tests, differing from one run to the next.) We should
            // figure
            // this out. But in the meantime, just use an exceptionally short timeout here and skip
            // straight to shutdownNow() which works every time. (We don't support shutting down
            // firestore, so this should only be triggered from the test suite.)
            if (!channel.awaitTermination(1, TimeUnit.SECONDS)) {
              Logger.debug(
                  FirestoreChannel.class.getSimpleName(),
                  "Unable to gracefully shutdown the gRPC ManagedChannel. Will attempt an immediate shutdown.");
              channel.shutdownNow();

              // gRPC docs claim "Although forceful, the shutdown process is still not
              // instantaneous; isTerminated() will likely return false immediately after this
              // method
              // returns." Therefore, we still need to awaitTermination() again.
              if (!channel.awaitTermination(60, TimeUnit.SECONDS)) {
                // Something bad has happened. We could assert, but this is just resource cleanup
                // for a
                // resource that is likely only released at the end of the execution. So instead,
                // we'll
                // just log the error.
                Logger.warn(
                    FirestoreChannel.class.getSimpleName(),
                    "Unable to forcefully shutdown the gRPC ManagedChannel.");
              }
            }

          } catch (InterruptedException e) {
            // (Re-)Cancel if current thread also interrupted
            channel.shutdownNow();

            // Similar to above, something bad happened, but it's not worth asserting. Just log it.
            Logger.warn(
                FirestoreChannel.class.getSimpleName(),
                "Interrupted while shutting down the gRPC Managed Channel");
            // Preserve interrupt status
            Thread.currentThread().interrupt();
          }
        });
    channelQueue.shutdown();
  }
}
