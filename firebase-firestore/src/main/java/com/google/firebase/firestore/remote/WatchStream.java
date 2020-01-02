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

import com.google.firebase.firestore.local.TargetData;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firebase.firestore.util.AsyncQueue.TimerId;
import com.google.firestore.v1.FirestoreGrpc;
import com.google.firestore.v1.ListenRequest;
import com.google.firestore.v1.ListenResponse;
import com.google.protobuf.ByteString;
import java.util.Map;

/**
 * A Stream that implements the StreamingWatch RPC.
 *
 * <p>Once the WatchStream has started, any number of watchQuery and unwatchTargetId calls can be
 * sent to control what changes will be sent from the server for WatchChanges.
 *
 * @see <a
 *     href="https://github.com/googleapis/googleapis/blob/master/google/firestore/v1/firestore.proto#L147">firestore.proto</a>
 */
public class WatchStream
    extends AbstractStream<ListenRequest, ListenResponse, WatchStream.Callback> {
  /**
   * The default resume token to use must be non-null for ease of operating with the protocol buffer
   * API. In particular, ByteString#copyFrom will NPE if passed null bytes.
   */
  public static final ByteString EMPTY_RESUME_TOKEN = ByteString.EMPTY;

  /** A callback interface for the set of events that can be emitted by the WatchStream */
  interface Callback extends AbstractStream.StreamCallback {
    /** A new change from the watch stream. Snapshot version will ne non-null if it was set */
    void onWatchChange(SnapshotVersion snapshotVersion, WatchChange watchChange);
  }

  private final RemoteSerializer serializer;

  WatchStream(
      FirestoreChannel channel,
      AsyncQueue workerQueue,
      RemoteSerializer serializer,
      WatchStream.Callback listener) {
    super(
        channel,
        FirestoreGrpc.getListenMethod(),
        workerQueue,
        TimerId.LISTEN_STREAM_CONNECTION_BACKOFF,
        TimerId.LISTEN_STREAM_IDLE,
        listener);
    this.serializer = serializer;
  }

  /**
   * Registers interest in the results of the given query. If the query includes a resumeToken it
   * will be included in the request. Results that affect the query will be streamed back as
   * WatchChange messages that reference the targetID included in query.
   */
  public void watchQuery(TargetData targetData) {
    hardAssert(isOpen(), "Watching queries requires an open stream");
    ListenRequest.Builder request =
        ListenRequest.newBuilder()
            .setDatabase(serializer.databaseName())
            .setAddTarget(serializer.encodeTarget(targetData));

    Map<String, String> labels = serializer.encodeListenRequestLabels(targetData);
    if (labels != null) {
      request.putAllLabels(labels);
    }

    writeRequest(request.build());
  }

  /** Unregisters interest in the results of the query associated with the given target ID. */
  public void unwatchTarget(int targetId) {
    hardAssert(isOpen(), "Unwatching targets requires an open stream");
    ListenRequest request =
        ListenRequest.newBuilder()
            .setDatabase(serializer.databaseName())
            .setRemoveTarget(targetId)
            .build();

    writeRequest(request);
  }

  @Override
  public void onNext(com.google.firestore.v1.ListenResponse listenResponse) {
    // A successful response means the stream is healthy
    backoff.reset();

    WatchChange watchChange = serializer.decodeWatchChange(listenResponse);
    SnapshotVersion snapshotVersion = serializer.decodeVersionFromListenResponse(listenResponse);
    listener.onWatchChange(snapshotVersion, watchChange);
  }
}
