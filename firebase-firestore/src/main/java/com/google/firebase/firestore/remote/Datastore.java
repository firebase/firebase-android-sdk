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

package com.google.firebase.firestore.remote;

import android.content.Context;
import android.os.Build;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.auth.CredentialsProvider;
import com.google.firebase.firestore.core.DatabaseInfo;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MaybeDocument;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.MutationResult;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firestore.v1.BatchGetDocumentsRequest;
import com.google.firestore.v1.BatchGetDocumentsResponse;
import com.google.firestore.v1.CommitRequest;
import com.google.firestore.v1.CommitResponse;
import com.google.firestore.v1.FirestoreGrpc;
import io.grpc.Status;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.net.ssl.SSLHandshakeException;

/**
 * Datastore represents a proxy for the remote server, hiding details of the RPC layer. It:
 *
 * <ul>
 *   <li>Manages connections to the server
 *   <li>Authenticates to the server
 *   <li>Manages threading and keeps higher-level code running on the worker queue
 *   <li>Serializes internal model objects to and from protocol buffers
 * </ul>
 *
 * <p>The Datastore is generally not responsible for understanding the higher-level protocol
 * involved in actually making changes or reading data, and is otherwise stateless.
 */
public class Datastore {

  /**
   * Error message to surface when Firestore fails to establish an SSL connection. A failed SSL
   * connection likely indicates that the developer needs to provide an updated OpenSSL stack as
   * part of their app's dependencies.
   */
  static final String SSL_DEPENDENCY_ERROR_MESSAGE =
      "The Cloud Firestore client failed to establish a secure connection. This is likely a "
          + "problem with your app, rather than with Cloud Firestore itself. See "
          + "https://bit.ly/2XFpdma for instructions on how to enable TLS on Android 4.x devices.";

  /** Set of lowercase, white-listed headers for logging purposes. */
  static final Set<String> WHITE_LISTED_HEADERS =
      new HashSet<>(
          Arrays.asList(
              "date",
              "x-google-backends",
              "x-google-netmon-label",
              "x-google-service",
              "x-google-gfe-request-trace"));

  private final DatabaseInfo databaseInfo;
  private final RemoteSerializer serializer;
  private final AsyncQueue workerQueue;

  private final FirestoreChannel channel;

  public Datastore(
      DatabaseInfo databaseInfo,
      AsyncQueue workerQueue,
      CredentialsProvider credentialsProvider,
      Context context,
      @Nullable GrpcMetadataProvider metadataProvider) {
    this.databaseInfo = databaseInfo;
    this.workerQueue = workerQueue;
    this.serializer = new RemoteSerializer(databaseInfo.getDatabaseId());

    channel =
        new FirestoreChannel(
            workerQueue, context, credentialsProvider, databaseInfo, metadataProvider);
  }

  void shutdown() {
    channel.shutdown();
  }

  AsyncQueue getWorkerQueue() {
    return workerQueue;
  }

  DatabaseInfo getDatabaseInfo() {
    return databaseInfo;
  }

  /** Creates a new WatchStream that is still unstarted but uses a common shared channel */
  WatchStream createWatchStream(WatchStream.Callback listener) {
    return new WatchStream(channel, workerQueue, serializer, listener);
  }

  /** Creates a new WriteStream that is still unstarted but uses a common shared channel */
  WriteStream createWriteStream(WriteStream.Callback listener) {
    return new WriteStream(channel, workerQueue, serializer, listener);
  }

  public Task<List<MutationResult>> commit(List<Mutation> mutations) {
    CommitRequest.Builder builder = CommitRequest.newBuilder();
    builder.setDatabase(serializer.databaseName());
    for (Mutation mutation : mutations) {
      builder.addWrites(serializer.encodeMutation(mutation));
    }
    return channel
        .runRpc(FirestoreGrpc.getCommitMethod(), builder.build())
        .continueWith(
            workerQueue.getExecutor(),
            task -> {
              if (!task.isSuccessful()) {
                if (task.getException() instanceof FirebaseFirestoreException
                    && ((FirebaseFirestoreException) task.getException()).getCode()
                        == FirebaseFirestoreException.Code.UNAUTHENTICATED) {
                  channel.invalidateToken();
                }
                throw task.getException();
              }
              CommitResponse response = task.getResult();
              SnapshotVersion commitVersion = serializer.decodeVersion(response.getCommitTime());

              int count = response.getWriteResultsCount();
              ArrayList<MutationResult> results = new ArrayList<>(count);
              for (int i = 0; i < count; i++) {
                com.google.firestore.v1.WriteResult result = response.getWriteResults(i);
                results.add(serializer.decodeMutationResult(result, commitVersion));
              }
              return results;
            });
  }

  public Task<List<MaybeDocument>> lookup(List<DocumentKey> keys) {
    BatchGetDocumentsRequest.Builder builder = BatchGetDocumentsRequest.newBuilder();
    builder.setDatabase(serializer.databaseName());
    for (DocumentKey key : keys) {
      builder.addDocuments(serializer.encodeKey(key));
    }
    return channel
        .runStreamingResponseRpc(FirestoreGrpc.getBatchGetDocumentsMethod(), builder.build())
        .continueWith(
            workerQueue.getExecutor(),
            task -> {
              if (!task.isSuccessful()) {
                if (task.getException() instanceof FirebaseFirestoreException
                    && ((FirebaseFirestoreException) task.getException()).getCode()
                        == FirebaseFirestoreException.Code.UNAUTHENTICATED) {
                  channel.invalidateToken();
                }
              }

              Map<DocumentKey, MaybeDocument> resultMap = new HashMap<>();
              List<BatchGetDocumentsResponse> responses = task.getResult();
              for (BatchGetDocumentsResponse response : responses) {
                MaybeDocument doc = serializer.decodeMaybeDocument(response);
                resultMap.put(doc.getKey(), doc);
              }
              List<MaybeDocument> results = new ArrayList<>();
              for (DocumentKey key : keys) {
                results.add(resultMap.get(key));
              }
              return results;
            });
  }

  /**
   * Determines whether the given status has an error code that represents a permanent error when
   * received in response to a non-write operation.
   *
   * @see #isPermanentWriteError for classifying write errors.
   */
  public static boolean isPermanentError(Status status) {
    return isPermanentError(FirebaseFirestoreException.Code.fromValue(status.getCode().value()));
  }

  /**
   * Determines whether the given error code represents a permanent error when received in response
   * to a non-write operation.
   *
   * @see #isPermanentWriteError for classifying write errors.
   */
  public static boolean isPermanentError(FirebaseFirestoreException.Code code) {
    // See go/firestore-client-errors
    switch (code) {
      case OK:
        throw new IllegalArgumentException("Treated status OK as error");
      case CANCELLED:
      case UNKNOWN:
      case DEADLINE_EXCEEDED:
      case RESOURCE_EXHAUSTED:
      case INTERNAL:
      case UNAVAILABLE:
      case UNAUTHENTICATED:
        // Unauthenticated means something went wrong with our token and we need
        // to retry with new credentials which will happen automatically.
        return false;
      case INVALID_ARGUMENT:
      case NOT_FOUND:
      case ALREADY_EXISTS:
      case PERMISSION_DENIED:
      case FAILED_PRECONDITION:
      case ABORTED:
        // Aborted might be retried in some scenarios, but that is dependant on
        // the context and should handled individually by the calling code.
        // See https://cloud.google.com/apis/design/errors.
      case OUT_OF_RANGE:
      case UNIMPLEMENTED:
      case DATA_LOSS:
        return true;
      default:
        throw new IllegalArgumentException("Unknown gRPC status code: " + code);
    }
  }

  /**
   * Determine whether the given status maps to the error that GRPC-Java throws when an Android
   * device is missing required SSL Ciphers.
   *
   * <p>This error is non-recoverable and must be addressed by the app developer.
   */
  public static boolean isMissingSslCiphers(Status status) {
    Status.Code code = status.getCode();
    Throwable t = status.getCause();

    // Check for the presence of a cipher error in the event of an SSLHandshakeException. This is
    // the special case of SSLHandshakeException that contains the cipher error.
    boolean hasCipherError = false;
    if (t instanceof SSLHandshakeException && t.getMessage().contains("no ciphers available")) {
      hasCipherError = true;
    }

    return Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP
        && code.equals(Status.Code.UNAVAILABLE)
        && hasCipherError;
  }

  /**
   * Determines whether the given status has an error code that represents a permanent error when
   * received in response to a write operation.
   *
   * <p>Write operations must be handled specially because as of b/119437764, ABORTED errors on the
   * write stream should be retried too (even though ABORTED errors are not generally retryable).
   *
   * <p>Note that during the initial handshake on the write stream an ABORTED error signals that we
   * should discard our stream token (i.e. it is permanent). This means a handshake error should be
   * classified with isPermanentError, above.
   */
  public static boolean isPermanentWriteError(Status status) {
    return isPermanentError(status) && !status.getCode().equals(Status.Code.ABORTED);
  }
}
