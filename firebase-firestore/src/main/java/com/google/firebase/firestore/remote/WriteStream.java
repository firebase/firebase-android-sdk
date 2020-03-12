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

import static com.google.firebase.firestore.util.Assert.hardAssert;
import static com.google.firebase.firestore.util.Preconditions.checkNotNull;

import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.MutationResult;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firebase.firestore.util.AsyncQueue.TimerId;
import com.google.firestore.v1.FirestoreGrpc;
import com.google.firestore.v1.WriteRequest;
import com.google.firestore.v1.WriteResponse;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A Stream that implements the StreamingWrite RPC.
 *
 * <p>The StreamingWrite RPC requires the caller to maintain special {@code streamToken} state in
 * between calls, to help the server understand which responses the client has processed by the time
 * the next request is made. Every response may contain a {@code streamToken}; this value must be
 * passed to the next request.
 *
 * <p>After calling {@code start()} on this stream, the next request must be a handshake, containing
 * whatever streamToken is on hand. Once a response to this request is received, all pending
 * mutations may be submitted. When submitting multiple batches of mutations at the same time, it's
 * okay to use the same streamToken for the calls to {@code writeMutations}.
 *
 * @see <a
 *     href="https://github.com/googleapis/googleapis/blob/master/google/firestore/v1/firestore.proto#L139">firestore.proto</a>
 */
public class WriteStream extends AbstractStream<WriteRequest, WriteResponse, WriteStream.Callback> {

  /** The empty stream token. */
  public static final ByteString EMPTY_STREAM_TOKEN = ByteString.EMPTY;

  /** A callback interface for the set of events that can be emitted by the WriteStream */
  public interface Callback extends AbstractStream.StreamCallback {
    /** The handshake for this write stream has completed */
    void onHandshakeComplete();

    /** Response for the last write. */
    void onWriteResponse(SnapshotVersion commitVersion, List<MutationResult> mutationResults);
  }

  private final RemoteSerializer serializer;
  protected boolean handshakeComplete = false;
  private ByteString lastStreamToken = EMPTY_STREAM_TOKEN;

  WriteStream(
      FirestoreChannel channel,
      AsyncQueue workerQueue,
      RemoteSerializer serializer,
      WriteStream.Callback listener) {
    super(
        channel,
        FirestoreGrpc.getWriteMethod(),
        workerQueue,
        TimerId.WRITE_STREAM_CONNECTION_BACKOFF,
        TimerId.WRITE_STREAM_IDLE,
        listener);
    this.serializer = serializer;
  }

  @Override
  public void start() {
    this.handshakeComplete = false;
    super.start();
  }

  @Override
  protected void tearDown() {
    if (handshakeComplete) {
      // Send an empty write request to the backend to indicate imminent stream closure. This allows
      // the backend to clean up resources.
      writeMutations(Collections.emptyList());
    }
  }

  /**
   * Tracks whether or not a handshake has been successfully exchanged and the stream is ready to
   * accept mutations.
   */
  boolean isHandshakeComplete() {
    return handshakeComplete;
  }

  /**
   * Returns the last received stream token from the server, used to acknowledge which responses the
   * client has processed. Stream tokens are opaque checkpoint markers whose only real value is
   * their inclusion in the next request.
   *
   * <p>WriteStream implementations manage propagating this value from responses to the next
   * request.
   */
  ByteString getLastStreamToken() {
    return lastStreamToken;
  }

  /**
   * Sets the last received stream token from the server, replacing any value the stream has tracked
   * for itself.
   *
   * @param streamToken The streamToken value to use for the next request. A null streamToken is not
   *     allowed: use the empty array for the unset value.
   */
  void setLastStreamToken(ByteString streamToken) {
    this.lastStreamToken = checkNotNull(streamToken);
  }

  /**
   * Sends an initial streamToken to the server, performing the handshake required to make the
   * StreamingWrite RPC work. Subsequent {@link #writeMutations} calls should wait until a response
   * has been delivered to {@link WriteStream.Callback#onHandshakeComplete}.
   */
  void writeHandshake() {
    hardAssert(isOpen(), "Writing handshake requires an opened stream");
    hardAssert(!handshakeComplete, "Handshake already completed");
    // TODO: Support stream resumption. We intentionally do not set the stream token on the
    // handshake, ignoring any stream token we might have.
    WriteRequest.Builder request = WriteRequest.newBuilder().setDatabase(serializer.databaseName());

    writeRequest(request.build());
  }

  /**
   * Sends a list of mutations to the Firestore backend to apply
   *
   * @param mutations The mutations
   */
  void writeMutations(List<Mutation> mutations) {
    hardAssert(isOpen(), "Writing mutations requires an opened stream");
    hardAssert(handshakeComplete, "Handshake must be complete before writing mutations");
    WriteRequest.Builder request = WriteRequest.newBuilder();

    for (Mutation mutation : mutations) {
      request.addWrites(serializer.encodeMutation(mutation));
    }

    request.setStreamToken(lastStreamToken);
    writeRequest(request.build());
  }

  @Override
  public void onNext(WriteResponse response) {
    lastStreamToken = response.getStreamToken();

    if (!handshakeComplete) {
      // The first response is the handshake response
      handshakeComplete = true;

      listener.onHandshakeComplete();
    } else {
      // A successful first write response means the stream is healthy,
      // Note, that we could consider a successful handshake healthy, however,
      // the write itself might be causing an error we want to back off from.
      backoff.reset();

      SnapshotVersion commitVersion = serializer.decodeVersion(response.getCommitTime());

      int count = response.getWriteResultsCount();
      List<MutationResult> results = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        com.google.firestore.v1.WriteResult result = response.getWriteResults(i);
        results.add(serializer.decodeMutationResult(result, commitVersion));
      }
      listener.onWriteResponse(commitVersion, results);
    }
  }
}
