// Copyright 2018 Google LLC
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

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.firestore.BuildConfig;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FirebaseFirestoreException.Code;
import com.google.firebase.firestore.auth.CredentialsProvider;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.remote.FirestoreCallCredentials;
import com.google.firestore.v1.FirestoreGrpc;
import com.google.firestore.v1.FirestoreGrpc.FirestoreStub;
import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Wrapper class around io.grpc.Channel that adds headers, exception handling and simplifies
 * invoking RPCs.
 */
public class FirestoreChannel {

  private static final Metadata.Key<String> X_GOOG_API_CLIENT_HEADER =
      Metadata.Key.of("x-goog-api-client", Metadata.ASCII_STRING_MARSHALLER);

  private static final Metadata.Key<String> RESOURCE_PREFIX_HEADER =
      Metadata.Key.of("google-cloud-resource-prefix", Metadata.ASCII_STRING_MARSHALLER);

  // TODO: The gRPC version is determined using a package manifest, which is not available
  // to us at build time or runtime (it's empty when building in google3). So for now we omit the
  // version of grpc.
  private static final String X_GOOG_API_CLIENT_VALUE =
      "gl-java/ fire/" + BuildConfig.VERSION_NAME + " grpc/";

  /** The async worker queue that is used to dispatch events. */
  private final AsyncQueue asyncQueue;

  private final CredentialsProvider credentialsProvider;

  /** The underlying gRPC channel. */
  private final ManagedChannel channel;

  /** Call options to be used when invoking RPCs. */
  private final CallOptions callOptions;

  /** The value to use as resource prefix header. */
  private final String resourcePrefixValue;

  public FirestoreChannel(
      AsyncQueue asyncQueue,
      CredentialsProvider credentialsProvider,
      ManagedChannel grpcChannel,
      DatabaseId databaseId) {
    this.asyncQueue = asyncQueue;
    this.credentialsProvider = credentialsProvider;

    FirestoreCallCredentials firestoreHeaders = new FirestoreCallCredentials(credentialsProvider);
    FirestoreStub firestoreStub =
        FirestoreGrpc.newStub(grpcChannel).withCallCredentials(firestoreHeaders);
    this.channel = grpcChannel;
    this.callOptions = firestoreStub.getCallOptions();

    this.resourcePrefixValue =
        String.format(
            "projects/%s/databases/%s", databaseId.getProjectId(), databaseId.getDatabaseId());
  }

  /**
   * Shuts down the grpc channel. This is not reversible and renders the FirestoreChannel unusable.
   */
  public void shutdown() {
    channel.shutdown();
    try {
      // TODO(rsgowman): Investigate occasional hangs in channel.shutdown().
      //
      // While running the integration tests, channel.shutdown() will occasionally timeout.
      // (Typically on ~4-5 different tests, differing from one run to the next.) We should figure
      // this out. But in the meantime, just use an exceptionally short timeout here and skip
      // straight to shutdownNow() which works every time. (We don't support shutting down
      // firestore, so this should only be triggered from the test suite.)
      if (!channel.awaitTermination(1, TimeUnit.SECONDS)) {
        Logger.debug(
            FirestoreChannel.class.getSimpleName(),
            "Unable to gracefully shutdown the gRPC ManagedChannel. Will attempt an immediate shutdown.");
        channel.shutdownNow();

        // gRPC docs claim "Although forceful, the shutdown process is still not
        // instantaneous; isTerminated() will likely return false immediately after this method
        // returns." Therefore, we still need to awaitTermination() again.
        if (!channel.awaitTermination(60, TimeUnit.SECONDS)) {
          // Something bad has happened. We could assert, but this is just resource cleanup for a
          // resource that is likely only released at the end of the execution. So instead, we'll
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
  }

  /** Creates and starts a new bi-directional streaming RPC. */
  public <ReqT, RespT> ClientCall<ReqT, RespT> runBidiStreamingRpc(
      MethodDescriptor<ReqT, RespT> method, IncomingStreamObserver<RespT> observer) {
    ClientCall<ReqT, RespT> call = channel.newCall(method, callOptions);

    call.start(
        new ClientCall.Listener<RespT>() {
          @Override
          public void onHeaders(Metadata headers) {
            try {
              observer.onHeaders(headers);
            } catch (Throwable t) {
              asyncQueue.panic(t);
            }
          }

          @Override
          public void onMessage(RespT message) {
            try {
              observer.onNext(message);
              // Make sure next message can be delivered
              call.request(1);
            } catch (Throwable t) {
              asyncQueue.panic(t);
            }
          }

          @Override
          public void onClose(Status status, Metadata trailers) {
            try {
              observer.onClose(status);
            } catch (Throwable t) {
              asyncQueue.panic(t);
            }
          }

          @Override
          public void onReady() {
            try {
              observer.onReady();
            } catch (Throwable t) {
              asyncQueue.panic(t);
            }
          }
        },
        requestHeaders());

    // Make sure to allow the first incoming message, all subsequent messages
    call.request(1);

    return call;
  }

  /** Creates and starts a streaming response RPC. */
  public <ReqT, RespT> Task<List<RespT>> runStreamingResponseRpc(
      MethodDescriptor<ReqT, RespT> method, ReqT request) {
    TaskCompletionSource<List<RespT>> tcs = new TaskCompletionSource<>();

    ClientCall<ReqT, RespT> call = channel.newCall(method, callOptions);

    List<RespT> results = new ArrayList<>();

    call.start(
        new ClientCall.Listener<RespT>() {
          @Override
          public void onMessage(RespT message) {
            results.add(message);

            // Make sure next message can be delivered
            call.request(1);
          }

          @Override
          public void onClose(Status status, Metadata trailers) {
            if (status.isOk()) {
              tcs.setResult(results);
            } else {
              tcs.setException(Util.exceptionFromStatus(status));
            }
          }
        },
        requestHeaders());

    // Make sure to allow the first incoming message, all subsequent messages
    call.request(1);

    call.sendMessage(request);
    call.halfClose();

    return tcs.getTask();
  }

  /** Creates and starts a single response RPC. */
  public <ReqT, RespT> Task<RespT> runRpc(MethodDescriptor<ReqT, RespT> method, ReqT request) {
    TaskCompletionSource<RespT> tcs = new TaskCompletionSource<>();

    ClientCall<ReqT, RespT> call = channel.newCall(method, callOptions);

    call.start(
        new ClientCall.Listener<RespT>() {
          @Override
          public void onMessage(RespT message) {
            // This should only be called once, so setting the result directly is fine
            tcs.setResult(message);
          }

          @Override
          public void onClose(Status status, Metadata trailers) {
            if (status.isOk()) {
              if (!tcs.getTask().isComplete()) {
                tcs.setException(
                    new FirebaseFirestoreException(
                        "Received onClose with status OK, but no message.", Code.INTERNAL));
              }
            } else {
              tcs.setException(Util.exceptionFromStatus(status));
            }
          }
        },
        requestHeaders());

    // Make sure to allow the first incoming message. Set to 2 so if there there is a second message
    // the client will fail fast (by setting the result of the TaskCompletionSource) twice instead
    // of going unnoticed.
    call.request(2);

    call.sendMessage(request);
    call.halfClose();

    return tcs.getTask();
  }

  public void invalidateToken() {
    credentialsProvider.invalidateToken();
  }

  /** Returns the default headers for requests to the backend. */
  private Metadata requestHeaders() {
    Metadata headers = new Metadata();
    headers.put(X_GOOG_API_CLIENT_HEADER, X_GOOG_API_CLIENT_VALUE);
    // This header is used to improve routing and project isolation by the backend.
    headers.put(RESOURCE_PREFIX_HEADER, this.resourcePrefixValue);
    return headers;
  }
}
