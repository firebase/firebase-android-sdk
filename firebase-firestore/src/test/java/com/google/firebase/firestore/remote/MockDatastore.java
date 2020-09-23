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

import android.content.Context;
import com.google.firebase.firestore.auth.EmptyCredentialsProvider;
import com.google.firebase.firestore.core.DatabaseInfo;
import com.google.firebase.firestore.local.TargetData;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.MutationResult;
import com.google.firebase.firestore.remote.WatchChange.WatchTargetChange;
import com.google.firebase.firestore.spec.SpecTestCase;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firebase.firestore.util.Util;
import io.grpc.Status;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A mock version of Datastore for SpecTest that allows the test to control the parts that would
 * normally be sent from the backend.
 *
 * <p>This class really only exists to make SpecTestCase work, but is in this package to give it
 * access to superclass constructors that would be unavailable from the spec package.
 */
public class MockDatastore extends Datastore {
  private class MockWatchStream extends WatchStream {

    private boolean open;

    /** Tracks the currently active watch targets as sent over the watch stream. */
    private final Map<Integer, TargetData> activeTargets = new HashMap<>();

    MockWatchStream(AsyncQueue workerQueue, WatchStream.Callback listener) {
      super(/*channel=*/ null, workerQueue, serializer, listener);
    }

    @Override
    public void start() {
      hardAssert(!open, "Trying to start already started watch stream");
      open = true;
      listener.onOpen();
    }

    @Override
    public void stop() {
      super.stop();
      activeTargets.clear();
      open = false;
    }

    @Override
    public boolean isStarted() {
      return open;
    }

    @Override
    public boolean isOpen() {
      return open;
    }

    @Override
    public void watchQuery(TargetData targetData) {
      String resumeToken = Util.toDebugString(targetData.getResumeToken());
      SpecTestCase.log(
          "      watchQuery("
              + targetData.getTarget()
              + ", "
              + targetData.getTargetId()
              + ", "
              + resumeToken
              + ")");
      // Snapshot version is ignored on the wire
      TargetData sentTargetData =
          targetData.withResumeToken(targetData.getResumeToken(), SnapshotVersion.NONE);
      watchStreamRequestCount += 1;
      this.activeTargets.put(targetData.getTargetId(), sentTargetData);
    }

    @Override
    public void unwatchTarget(int targetId) {
      SpecTestCase.log("      unwatchTarget(" + targetId + ")");
      this.activeTargets.remove(targetId);
    }

    /** Injects a stream failure as though it had come from the backend. */
    void failStream(Status status) {
      open = false;
      listener.onClose(status);
    }

    /** Injects a watch change as though it had come from the backend. */
    void writeWatchChange(WatchChange change, SnapshotVersion snapshotVersion) {
      if (change instanceof WatchTargetChange) {
        WatchTargetChange targetChange = (WatchTargetChange) change;
        if (targetChange.getCause() != null && !targetChange.getCause().isOk()) {
          for (Integer targetId : targetChange.getTargetIds()) {
            if (!activeTargets.containsKey(targetId)) {
              // Technically removing an unknown target is valid (e.g. it could race with a
              // server-side removal), but we want to pay extra careful attention in tests
              // that we only remove targets we listened too.
              throw new IllegalStateException("Removing a non-active target");
            }
            activeTargets.remove(targetId);
          }
        }
        if (!targetChange.getTargetIds().isEmpty()) {
          // If the list of target IDs is not empty, we reset the snapshot version to NONE as
          // done in `RemoteSerializer.decodeVersionFromListenResponse()`.
          snapshotVersion = SnapshotVersion.NONE;
        }
      }
      listener.onWatchChange(snapshotVersion, change);
    }
  }

  private class MockWriteStream extends WriteStream {

    private boolean open;
    private final List<List<Mutation>> sentWrites;

    MockWriteStream(AsyncQueue workerQueue, WriteStream.Callback listener) {
      super(/*channel=*/ null, workerQueue, serializer, listener);
      sentWrites = new ArrayList<>();
    }

    @Override
    public void start() {
      hardAssert(!open, "Trying to start already started write stream");
      handshakeComplete = false;
      open = true;
      sentWrites.clear();
      listener.onOpen();
    }

    @Override
    public void stop() {
      super.stop();
      sentWrites.clear();
      open = false;
      handshakeComplete = false;
    }

    @Override
    public boolean isStarted() {
      return open;
    }

    @Override
    public boolean isOpen() {
      return open;
    }

    @Override
    public void writeHandshake() {
      hardAssert(!handshakeComplete, "Handshake already completed");
      writeStreamRequestCount += 1;
      handshakeComplete = true;
      listener.onHandshakeComplete();
    }

    @Override
    public void writeMutations(List<Mutation> mutations) {
      writeStreamRequestCount += 1;
      sentWrites.add(mutations);
    }

    /** Injects a write ack as though it had come from the backend in response to a write. */
    void ackWrite(SnapshotVersion commitVersion, List<MutationResult> results) {
      listener.onWriteResponse(commitVersion, results);
    }

    /** Injects a stream failure as though it had come from the backend. */
    void failStream(Status status) {
      open = false;
      sentWrites.clear();
      listener.onClose(status);
    }

    /** Returns a previous write that had been "sent to the backend". */
    List<Mutation> waitForWriteSend() {
      hardAssert(!sentWrites.isEmpty(), "Writes need to happen before you can wait on them.");
      return sentWrites.remove(0);
    }

    /** Returns the number of writes that have been sent to the backend but not waited on yet. */
    int getWritesSent() {
      return sentWrites.size();
    }
  }

  private MockWatchStream watchStream;
  private MockWriteStream writeStream;
  private final RemoteSerializer serializer;

  private int writeStreamRequestCount;
  private int watchStreamRequestCount;

  public MockDatastore(DatabaseInfo databaseInfo, AsyncQueue workerQueue, Context context) {
    super(databaseInfo, workerQueue, new EmptyCredentialsProvider(), context, null);
    this.serializer = new RemoteSerializer(getDatabaseInfo().getDatabaseId());
  }

  @Override
  WatchStream createWatchStream(WatchStream.Callback listener) {
    watchStream = new MockWatchStream(getWorkerQueue(), listener);
    return watchStream;
  }

  @Override
  WriteStream createWriteStream(WriteStream.Callback listener) {
    writeStream = new MockWriteStream(getWorkerQueue(), listener);
    return writeStream;
  }

  public int getWriteStreamRequestCount() {
    return writeStreamRequestCount;
  }

  public int getWatchStreamRequestCount() {
    return watchStreamRequestCount;
  }

  /** Returns a previous write that had been "sent to the backend". */
  public List<Mutation> waitForWriteSend() {
    return writeStream.waitForWriteSend();
  }

  /** Returns the number of writes that have been sent to the backend but not waited on yet. */
  public int writesSent() {
    return writeStream.getWritesSent();
  }

  /** Injects a write ack as though it had come from the backend in response to a write. */
  public void ackWrite(SnapshotVersion commitVersion, List<MutationResult> results) {
    writeStream.ackWrite(commitVersion, results);
  }

  /** Injects a failed write response as though it had come from the backend. */
  public void failWrite(Status status) {
    writeStream.failStream(status);
  }

  /** Injects a watch change as though it had come from the backend. */
  public void writeWatchChange(WatchChange change, SnapshotVersion snapshotVersion) {
    watchStream.writeWatchChange(change, snapshotVersion);
  }

  /** Injects a stream failure as though it had come from the backend. */
  public void failWatchStream(Status status) {
    watchStream.failStream(status);
  }

  /** Returns the map of active targets on the watch stream, keyed by target ID. */
  public Map<Integer, TargetData> activeTargets() {
    // Make a defensive copy as the watch stream continues to modify the Map of active targets.
    return new HashMap<>(watchStream.activeTargets);
  }

  /** Helper method to expose stream state to verify in tests. */
  public boolean isWatchStreamOpen() {
    return watchStream.isOpen();
  }
}
