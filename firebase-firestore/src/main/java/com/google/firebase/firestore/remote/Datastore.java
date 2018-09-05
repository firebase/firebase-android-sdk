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

import android.support.annotation.VisibleForTesting;
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
import com.google.firebase.firestore.util.FirestoreChannel;
import com.google.firebase.firestore.util.Supplier;
import com.google.firestore.v1beta1.BatchGetDocumentsRequest;
import com.google.firestore.v1beta1.BatchGetDocumentsResponse;
import com.google.firestore.v1beta1.CommitRequest;
import com.google.firestore.v1beta1.CommitResponse;
import com.google.firestore.v1beta1.FirestoreGrpc;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

  /** Set of lowercase, white-listed headers for logging purposes. */
  public static final Set<String> WHITE_LISTED_HEADERS =
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

  private static Supplier<ManagedChannelBuilder<?>> overrideChannelBuilderSupplier;

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
    Datastore.overrideChannelBuilderSupplier = channelBuilderSupplier;
  }

  public Datastore(
      DatabaseInfo databaseInfo, AsyncQueue workerQueue, CredentialsProvider credentialsProvider) {
    this.databaseInfo = databaseInfo;
    this.workerQueue = workerQueue;
    this.serializer = new RemoteSerializer(databaseInfo.getDatabaseId());

    ManagedChannelBuilder<?> channelBuilder;
    if (overrideChannelBuilderSupplier != null) {
      channelBuilder = overrideChannelBuilderSupplier.get();
    } else {
      channelBuilder = ManagedChannelBuilder.forTarget(databaseInfo.getHost());
      if (!databaseInfo.isSslEnabled()) {
        // Note that the boolean flag does *NOT* indicate whether or not plaintext should be used
        channelBuilder.usePlaintext();
      }
    }

    // This ensures all callbacks are issued on the worker queue. If this call is removed,
    // all calls need to be audited to make sure they are executed on the right thread.
    channelBuilder.executor(workerQueue.getExecutor());

    channel =
        new FirestoreChannel(
            workerQueue, credentialsProvider, channelBuilder.build(), databaseInfo.getDatabaseId());
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
                com.google.firestore.v1beta1.WriteResult result = response.getWriteResults(i);
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

  public static boolean isPermanentWriteError(Status status) {
    // See go/firestore-client-errors
    switch (status.getCode()) {
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
        throw new IllegalArgumentException("Unknown gRPC status code: " + status.getCode());
    }
  }
}
