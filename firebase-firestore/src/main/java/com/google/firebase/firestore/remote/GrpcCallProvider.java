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

package com.google.firebase.firestore.remote;

import android.content.Context;
import android.support.annotation.VisibleForTesting;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.security.ProviderInstaller;
import com.google.firebase.firestore.core.DatabaseInfo;
import com.google.firebase.firestore.util.Assert;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firebase.firestore.util.Logger;
import com.google.firebase.firestore.util.Supplier;
import com.google.firestore.v1.FirestoreGrpc;
import io.grpc.CallCredentials;
import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.android.AndroidChannelBuilder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/**
 * Manages the gRPC channel and encapsulates all SSL and gRPC initialization.
 *
 * <p>All operations are dispatched to an internal worker queue and are not executed until the SSL
 * and gRPC Stub initialization completes.
 */
// PORTING NOTE: This class only exists on Android.
@VisibleForTesting
public class GrpcCallProvider {

  private static final String LOG_TAG = "GrpcCallProvider";

  private static final RejectedExecutionHandler IGNORE_REJECTIONS_HANDLER =
      (runnable, executor) -> {
        // We ignore rejected executions, since some gRPC messages can arrive after shutdown.
      };

  private static Supplier<ManagedChannelBuilder<?>> overrideChannelBuilderSupplier;

  private final ExecutorService channelQueue;
  private ManagedChannel channel;
  private CallOptions callOptions;
  private boolean shutdown = false;

  /**
   * Helper function to globally override the channel that RPCs use. Useful for testing when you
   * want to bypass SSL certificate checking.
   *
   * @param channelBuilderSupplier The supplier for a channel builder that is used to create gRPC
   *     channels.
   */
  @VisibleForTesting
  public static void overrideChannelBuilder(
      Supplier<ManagedChannelBuilder<?>> channelBuilderSupplier) {
    overrideChannelBuilderSupplier = channelBuilderSupplier;
  }

  GrpcCallProvider(
      AsyncQueue workerQueue,
      Context context,
      DatabaseInfo databaseInfo,
      CallCredentials firestoreHeaders) {
    ThreadFactory threadFactory =
        runnable -> {
          Thread thread = Executors.defaultThreadFactory().newThread(runnable);
          thread.setName("FirestoreGrpcWorker");
          thread.setDaemon(true);
          thread.setUncaughtExceptionHandler(
              (crashingThread, throwable) -> workerQueue.panic(throwable));
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
          channel = initChannel(workerQueue, context, databaseInfo);
          FirestoreGrpc.FirestoreStub firestoreStub =
              FirestoreGrpc.newStub(channel).withCallCredentials(firestoreHeaders);
          callOptions = firestoreStub.getCallOptions();
        });
  }

  /** Sets up the SSL provider and configures the gRPC channel. */
  private ManagedChannel initChannel(
      AsyncQueue workerQueue, Context context, DatabaseInfo databaseInfo) {
    try {
      // We need to upgrade the Security Provider before any network channels are initialized.
      // `OkHttp` maintains a list of supported providers that is initialized when the JVM first
      // resolves the static dependencies of ManagedChannel.
      ProviderInstaller.installIfNeeded(context);
    } catch (GooglePlayServicesNotAvailableException | GooglePlayServicesRepairableException e) {
      // Mark the SSL initialization as done, even though we may be using outdated SSL
      // ciphers. gRPC-Java recommends obtaining updated ciphers from GMSCore, but we allow
      // the device to fall back to other SSL ciphers if GMSCore is not available.
      Logger.warn(LOG_TAG, "Failed to update ssl context: %s", e);
    }

    ManagedChannelBuilder<?> channelBuilder;
    if (overrideChannelBuilderSupplier != null) {
      channelBuilder = overrideChannelBuilderSupplier.get();
    } else {
      channelBuilder = ManagedChannelBuilder.forTarget(databaseInfo.getHost());
      if (!databaseInfo.isSslEnabled()) {
        // Note that the boolean flag does *NOT* indicate whether or not plaintext should be
        // used
        channelBuilder.usePlaintext();
      }
    }

    // Ensure gRPC recovers from a dead connection. (Not typically necessary, as the OS will
    // usually notify gRPC when a connection dies. But not always. This acts as a failsafe.)
    channelBuilder.keepAliveTime(30, TimeUnit.SECONDS);

    // This ensures all callbacks are issued on the worker queue. If this call is removed,
    // all calls need to be audited to make sure they are executed on the right thread.
    channelBuilder.executor(workerQueue.getExecutor());

    // Wrap the ManagedChannelBuilder in an AndroidChannelBuilder. This allows the channel to
    // respond more gracefully to network change events (such as switching from cell to wifi).
    AndroidChannelBuilder androidChannelBuilder =
        AndroidChannelBuilder.fromBuilder(channelBuilder).context(context);

    return androidChannelBuilder.build();
  }

  /** Creates a new ClientCall. */
  <ReqT, RespT> ClientCall<ReqT, RespT> createClientCall(
      MethodDescriptor<ReqT, RespT> methodDescriptor) {
    Assert.hardAssert(!shutdown, "GrpcCallProvider already shut down");

    // Return a client call that is directly consumable. Note that we do not forward any operations
    // until the initialization of the SSL stack and gRPC stub completes.
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

  /** Shuts down the gRPC channel and the internal worker queue. */
  void shutdown() {
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
            // figure this out. But in the meantime, just use an exceptionally short timeout here
            // and skip straight to shutdownNow() which works every time. (We don't support shutting
            // down Firestore, so this should only be triggered from the test suite.)
            if (!channel.awaitTermination(1, TimeUnit.SECONDS)) {
              Logger.debug(
                  FirestoreChannel.class.getSimpleName(),
                  "Unable to gracefully shutdown the gRPC ManagedChannel. Will attempt an immediate shutdown.");
              channel.shutdownNow();

              // gRPC docs claim "Although forceful, the shutdown process is still not
              // instantaneous; isTerminated() will likely return false immediately after this
              // method returns." Therefore, we still need to awaitTermination() again.
              if (!channel.awaitTermination(60, TimeUnit.SECONDS)) {
                // Something bad has happened. We could assert, but this is just resource cleanup
                // for a resource that is likely only released at the end of the execution. So
                // instead, we'll just log the error.
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
