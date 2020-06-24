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

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.core.OnlineState;
import com.google.firebase.firestore.core.Transaction;
import com.google.firebase.firestore.local.LocalStore;
import com.google.firebase.firestore.local.QueryPurpose;
import com.google.firebase.firestore.local.TargetData;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.model.mutation.MutationBatch;
import com.google.firebase.firestore.model.mutation.MutationBatchResult;
import com.google.firebase.firestore.model.mutation.MutationResult;
import com.google.firebase.firestore.remote.ConnectivityMonitor.NetworkStatus;
import com.google.firebase.firestore.remote.WatchChange.DocumentChange;
import com.google.firebase.firestore.remote.WatchChange.ExistenceFilterWatchChange;
import com.google.firebase.firestore.remote.WatchChange.WatchTargetChange;
import com.google.firebase.firestore.remote.WatchChange.WatchTargetChangeType;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firebase.firestore.util.Logger;
import com.google.firebase.firestore.util.Util;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * RemoteStore handles all interaction with the backend through a simple, clean interface. This
 * class is not thread safe and should be only called from the worker AsyncQueue.
 */
public final class RemoteStore implements WatchChangeAggregator.TargetMetadataProvider {

  /** The maximum number of pending writes to allow. TODO: Negotiate this value with the backend. */
  private static final int MAX_PENDING_WRITES = 10;

  /** The log tag to use for this class. */
  private static final String LOG_TAG = "RemoteStore";

  /** A callback interface for events from RemoteStore. */
  public interface RemoteStoreCallback {
    /**
     * Handle a remote event to the sync engine, notifying any views of the changes, and releasing
     * any pending mutation batches that would become visible because of the snapshot version the
     * remote event contains.
     */
    void handleRemoteEvent(RemoteEvent remoteEvent);

    /**
     * Reject the listen for the given targetId. This can be triggered by the backend for any active
     * target.
     *
     * @param targetId The targetId corresponding to a listen initiated via listen()
     * @param error A description of the condition that has forced the rejection. Nearly always this
     *     will be an indication that the user is no longer authorized to see the data matching the
     *     target.
     */
    void handleRejectedListen(int targetId, Status error);

    /**
     * Applies the result of a successful write of a mutation batch to the sync engine, emitting
     * snapshots in any views that the mutation applies to, and removing the batch from the mutation
     * queue.
     */
    void handleSuccessfulWrite(MutationBatchResult successfulWrite);

    /**
     * Rejects the batch, removing the batch from the mutation queue, recomputing the local view of
     * any documents affected by the batch and then, emitting snapshots with the reverted value.
     */
    void handleRejectedWrite(int batchId, Status error);

    /**
     * Called whenever the online state of the client changes. This is based on the watch stream for
     * now.
     */
    void handleOnlineStateChange(OnlineState onlineState);

    /**
     * Returns the set of remote document keys for the given target ID. This list includes the
     * documents that were assigned to the target when we received the last snapshot.
     *
     * <p>Returns an empty set of document keys for unknown targets.
     */
    ImmutableSortedSet<DocumentKey> getRemoteKeysForTarget(int targetId);
  }

  private final RemoteStoreCallback remoteStoreCallback;
  private final LocalStore localStore;
  private final Datastore datastore;
  private final ConnectivityMonitor connectivityMonitor;

  /**
   * A mapping of watched targets that the client cares about tracking and the user has explicitly
   * called a 'listen' for this target.
   *
   * <p>These targets may or may not have been sent to or acknowledged by the server. On
   * re-establishing the listen stream, these targets should be sent to the server. The targets
   * removed with unlistens are removed eagerly without waiting for confirmation from the listen
   * stream.
   */
  private final Map<Integer, TargetData> listenTargets;

  private final OnlineStateTracker onlineStateTracker;

  private boolean networkEnabled = false;
  private final WatchStream watchStream;
  private final WriteStream writeStream;
  @Nullable private WatchChangeAggregator watchChangeAggregator;

  /**
   * A list of up to MAX_PENDING_WRITES writes that we have fetched from the LocalStore via
   * fillWritePipeline() and have or will send to the write stream.
   *
   * <p>Whenever writePipeline.length > 0 the RemoteStore will attempt to start or restart the write
   * stream. When the stream is established the writes in the pipeline will be sent in order.
   *
   * <p>Writes remain in writePipeline until they are acknowledged by the backend and thus will
   * automatically be re-sent if the stream is interrupted / restarted before they're acknowledged.
   *
   * <p>Write responses from the backend are linked to their originating request purely based on
   * order, and so we can just poll() writes from the front of the writePipeline as we receive
   * responses.
   */
  private final Deque<MutationBatch> writePipeline;

  public RemoteStore(
      RemoteStoreCallback remoteStoreCallback,
      LocalStore localStore,
      Datastore datastore,
      AsyncQueue workerQueue,
      ConnectivityMonitor connectivityMonitor) {
    this.remoteStoreCallback = remoteStoreCallback;
    this.localStore = localStore;
    this.datastore = datastore;
    this.connectivityMonitor = connectivityMonitor;

    listenTargets = new HashMap<>();
    writePipeline = new ArrayDeque<>();

    onlineStateTracker =
        new OnlineStateTracker(workerQueue, remoteStoreCallback::handleOnlineStateChange);

    // Create new streams (but note they're not started yet).
    watchStream =
        datastore.createWatchStream(
            new WatchStream.Callback() {
              @Override
              public void onOpen() {
                handleWatchStreamOpen();
              }

              @Override
              public void onWatchChange(SnapshotVersion snapshotVersion, WatchChange watchChange) {
                handleWatchChange(snapshotVersion, watchChange);
              }

              @Override
              public void onClose(Status status) {
                handleWatchStreamClose(status);
              }
            });

    writeStream =
        datastore.createWriteStream(
            new WriteStream.Callback() {
              @Override
              public void onOpen() {
                writeStream.writeHandshake();
              }

              @Override
              public void onHandshakeComplete() {
                handleWriteStreamHandshakeComplete();
              }

              @Override
              public void onWriteResponse(
                  SnapshotVersion commitVersion, List<MutationResult> mutationResults) {
                handleWriteStreamMutationResults(commitVersion, mutationResults);
              }

              @Override
              public void onClose(Status status) {
                handleWriteStreamClose(status);
              }
            });

    connectivityMonitor.addCallback(
        (NetworkStatus networkStatus) -> {
          workerQueue.enqueueAndForget(
              () -> {
                // Porting Note: Unlike iOS, `restartNetwork()` is called even when the network
                // becomes unreachable as we don't have any other way to tear down our streams.

                // If the network has been explicitly disabled, make sure we don't accidentally
                // re-enable it.
                if (canUseNetwork()) {
                  // Tear down and re-create our network streams. This will ensure the backoffs are
                  // reset.
                  Logger.debug(LOG_TAG, "Restarting streams for network reachability change.");
                  restartNetwork();
                }
              });
        });
  }

  /** Re-enables the network. Only to be called as the counterpart to disableNetwork(). */
  public void enableNetwork() {
    networkEnabled = true;

    if (canUseNetwork()) {
      writeStream.setLastStreamToken(localStore.getLastStreamToken());

      if (shouldStartWatchStream()) {
        startWatchStream();
      } else {
        onlineStateTracker.updateState(OnlineState.UNKNOWN);
      }

      // This will start the write stream if necessary.
      fillWritePipeline();
    }
  }

  /**
   * Re-enables the network, and forces the state to ONLINE. Without this, the state will be
   * UNKNOWN. If the OnlineStateTracker updates the state from UNKNOWN to UNKNOWN, then it doesn't
   * trigger the callback.
   */
  @VisibleForTesting
  void forceEnableNetwork() {
    enableNetwork();
    onlineStateTracker.updateState(OnlineState.ONLINE);
  }

  /** Temporarily disables the network. The network can be re-enabled using enableNetwork(). */
  public void disableNetwork() {
    networkEnabled = false;
    disableNetworkInternal();

    // Set the OnlineState to OFFLINE so get()s return from cache, etc.
    onlineStateTracker.updateState(OnlineState.OFFLINE);
  }

  private void disableNetworkInternal() {
    watchStream.stop();
    writeStream.stop();

    if (!writePipeline.isEmpty()) {
      Logger.debug(LOG_TAG, "Stopping write stream with %d pending writes", writePipeline.size());
      writePipeline.clear();
    }

    cleanUpWatchStreamState();
  }

  private void restartNetwork() {
    networkEnabled = false;
    disableNetworkInternal();
    onlineStateTracker.updateState(OnlineState.UNKNOWN);
    writeStream.inhibitBackoff();
    watchStream.inhibitBackoff();
    enableNetwork();
  }

  /**
   * Starts up the remote store, creating streams, restoring state from LocalStore, etc. This should
   * called before using any other API endpoints in this class.
   */
  public void start() {
    // For now, all setup is handled by enableNetwork(). We might expand on this in the future.
    enableNetwork();
  }

  /**
   * Shuts down the remote store, tearing down connections and otherwise cleaning up. This is not
   * reversible and renders the Remote Store unusable.
   */
  public void shutdown() {
    Logger.debug(LOG_TAG, "Shutting down");
    connectivityMonitor.shutdown();
    networkEnabled = false;
    this.disableNetworkInternal();
    datastore.shutdown();
    // Set the OnlineState to UNKNOWN (rather than OFFLINE) to avoid potentially triggering
    // spurious listener events with cached data, etc.
    onlineStateTracker.updateState(OnlineState.UNKNOWN);
  }

  /**
   * Tells the RemoteStore that the currently authenticated user has changed.
   *
   * <p>In response the remote store tears down streams and clears up any tracked operations that
   * should not persist across users. Restarts the streams if appropriate.
   */
  public void handleCredentialChange() {
    // If the network has been explicitly disabled, make sure we don't accidentally re-enable it.
    if (canUseNetwork()) {
      // Tear down and re-create our network streams. This will ensure we get a fresh auth token
      // for the new user and re-fill the write pipeline with new mutations from the LocalStore
      // (since mutations are per-user).
      Logger.debug(LOG_TAG, "Restarting streams for new credential.");
      restartNetwork();
    }
  }

  // Watch Stream

  /**
   * Listens to the target identified by the given TargetData.
   *
   * <p>It is a no-op if the target of the given query data is already being listened to.
   */
  public void listen(TargetData targetData) {
    Integer targetId = targetData.getTargetId();
    if (listenTargets.containsKey(targetId)) {
      return;
    }

    listenTargets.put(targetId, targetData);

    if (shouldStartWatchStream()) {
      startWatchStream();
    } else if (watchStream.isOpen()) {
      sendWatchRequest(targetData);
    }
  }

  private void sendWatchRequest(TargetData targetData) {
    watchChangeAggregator.recordPendingTargetRequest(targetData.getTargetId());
    watchStream.watchQuery(targetData);
  }

  /**
   * Stops listening to the target with the given target ID.
   *
   * <p>It is an error if the given target id is not being listened to.
   *
   * <p>If this is called with the last active targetId, the watch stream enters idle mode and will
   * be torn down after one minute of inactivity.
   */
  public void stopListening(int targetId) {
    TargetData targetData = listenTargets.remove(targetId);
    hardAssert(
        targetData != null, "stopListening called on target no currently watched: %d", targetId);

    // The watch stream might not be started if we're in a disconnected state
    if (watchStream.isOpen()) {
      sendUnwatchRequest(targetId);
    }

    if (listenTargets.isEmpty()) {
      if (watchStream.isOpen()) {
        watchStream.markIdle();
      } else if (this.canUseNetwork()) {
        // Revert to OnlineState.UNKNOWN if the watch stream is not open and we have no listeners,
        // since without any listens to send we cannot confirm if the stream is healthy and upgrade
        // to OnlineState.ONLINE.
        this.onlineStateTracker.updateState(OnlineState.UNKNOWN);
      }
    }
  }

  private void sendUnwatchRequest(int targetId) {
    watchChangeAggregator.recordPendingTargetRequest(targetId);
    watchStream.unwatchTarget(targetId);
  }

  /**
   * Returns true if the network is enabled, the write stream has not yet been started and there are
   * pending writes.
   */
  private boolean shouldStartWriteStream() {
    return canUseNetwork() && !writeStream.isStarted() && !writePipeline.isEmpty();
  }

  /**
   * Returns true if the network is enabled, the watch stream has not yet been started and there are
   * active watch targets.
   */
  private boolean shouldStartWatchStream() {
    return canUseNetwork() && !watchStream.isStarted() && !listenTargets.isEmpty();
  }

  private void cleanUpWatchStreamState() {
    // If the connection is closed then we'll never get a snapshot version for the accumulated
    // changes and so we'll never be able to complete the batch. When we start up again the server
    // is going to resend these changes anyway, so just toss the accumulated state.
    watchChangeAggregator = null;
  }

  private void startWatchStream() {
    hardAssert(
        shouldStartWatchStream(),
        "startWatchStream() called when shouldStartWatchStream() is false.");
    watchChangeAggregator = new WatchChangeAggregator(this);
    watchStream.start();

    onlineStateTracker.handleWatchStreamStart();
  }

  private void handleWatchStreamOpen() {
    // Restore any existing watches.
    for (TargetData targetData : listenTargets.values()) {
      sendWatchRequest(targetData);
    }
  }

  private void handleWatchChange(SnapshotVersion snapshotVersion, WatchChange watchChange) {
    // Mark the connection as ONLINE because we got a message from the server.
    onlineStateTracker.updateState(OnlineState.ONLINE);

    hardAssert(
        (watchStream != null) && (watchChangeAggregator != null),
        "WatchStream and WatchStreamAggregator should both be non-null");

    WatchTargetChange watchTargetChange =
        watchChange instanceof WatchTargetChange ? (WatchTargetChange) watchChange : null;

    if (watchTargetChange != null
        && watchTargetChange.getChangeType().equals(WatchTargetChangeType.Removed)
        && watchTargetChange.getCause() != null) {
      // There was an error on a target, don't wait for a consistent snapshot to raise events
      processTargetError(watchTargetChange);
    } else {
      if (watchChange instanceof DocumentChange) {
        watchChangeAggregator.handleDocumentChange((DocumentChange) watchChange);
      } else if (watchChange instanceof ExistenceFilterWatchChange) {
        watchChangeAggregator.handleExistenceFilter((ExistenceFilterWatchChange) watchChange);
      } else {
        hardAssert(
            watchChange instanceof WatchTargetChange,
            "Expected watchChange to be an instance of WatchTargetChange");
        watchChangeAggregator.handleTargetChange((WatchTargetChange) watchChange);
      }

      if (!snapshotVersion.equals(SnapshotVersion.NONE)) {
        SnapshotVersion lastRemoteSnapshotVersion = this.localStore.getLastRemoteSnapshotVersion();
        if (snapshotVersion.compareTo(lastRemoteSnapshotVersion) >= 0) {
          // We have received a target change with a global snapshot if the snapshot
          // version is not equal to SnapshotVersion.MIN.
          raiseWatchSnapshot(snapshotVersion);
        }
      }
    }
  }

  private void handleWatchStreamClose(Status status) {
    if (Status.OK.equals(status)) {
      // Graceful stop (due to stop() or idle timeout). Make sure that's desirable.
      hardAssert(
          !shouldStartWatchStream(), "Watch stream was stopped gracefully while still needed.");
    }

    cleanUpWatchStreamState();

    // If we still need the watch stream, retry the connection.
    if (shouldStartWatchStream()) {
      onlineStateTracker.handleWatchStreamFailure(status);

      startWatchStream();
    } else {
      // We don't need to restart the watch stream because there are no active targets. The online
      // state is set to unknown because there is no active attempt at establishing a connection.
      onlineStateTracker.updateState(OnlineState.UNKNOWN);
    }
  }

  public boolean canUseNetwork() {
    // PORTING NOTE: This method exists mostly because web also has to take into account primary
    // vs. secondary state.
    return networkEnabled;
  }

  /**
   * Takes a batch of changes from the Datastore, repackages them as a RemoteEvent, and passes that
   * on to the listener, which is typically the SyncEngine.
   */
  private void raiseWatchSnapshot(SnapshotVersion snapshotVersion) {
    hardAssert(
        !snapshotVersion.equals(SnapshotVersion.NONE),
        "Can't raise event for unknown SnapshotVersion");
    RemoteEvent remoteEvent = watchChangeAggregator.createRemoteEvent(snapshotVersion);

    // Update in-memory resume tokens. LocalStore will update the persistent view of these when
    // applying the completed RemoteEvent.
    for (Entry<Integer, TargetChange> entry : remoteEvent.getTargetChanges().entrySet()) {
      TargetChange targetChange = entry.getValue();
      if (!targetChange.getResumeToken().isEmpty()) {
        int targetId = entry.getKey();
        TargetData targetData = this.listenTargets.get(targetId);
        // A watched target might have been removed already.
        if (targetData != null) {
          this.listenTargets.put(
              targetId, targetData.withResumeToken(targetChange.getResumeToken(), snapshotVersion));
        }
      }
    }

    // Re-establish listens for the targets that have been invalidated by  existence filter
    // mismatches.
    for (int targetId : remoteEvent.getTargetMismatches()) {
      TargetData targetData = this.listenTargets.get(targetId);
      // A watched target might have been removed already.
      if (targetData != null) {
        // Clear the resume token for the query, since we're in a known mismatch state.
        this.listenTargets.put(
            targetId,
            targetData.withResumeToken(ByteString.EMPTY, targetData.getSnapshotVersion()));

        // Cause a hard reset by unwatching and rewatching immediately, but deliberately don't send
        // a resume token so that we get a full update.
        this.sendUnwatchRequest(targetId);

        // Mark the query we send as being on behalf of an existence filter  mismatch, but don't
        // actually retain that in listenTargets. This ensures that we flag the first re-listen this
        // way without impacting future listens of this target (that might happen e.g. on
        // reconnect).
        TargetData requestTargetData =
            new TargetData(
                targetData.getTarget(),
                targetId,
                targetData.getSequenceNumber(),
                QueryPurpose.EXISTENCE_FILTER_MISMATCH);
        this.sendWatchRequest(requestTargetData);
      }
    }

    // Finally raise remote event
    remoteStoreCallback.handleRemoteEvent(remoteEvent);
  }

  private void processTargetError(WatchTargetChange targetChange) {
    hardAssert(targetChange.getCause() != null, "Processing target error without a cause");
    for (Integer targetId : targetChange.getTargetIds()) {
      // Ignore targets that have been removed already.
      if (listenTargets.containsKey(targetId)) {
        listenTargets.remove(targetId);
        watchChangeAggregator.removeTarget(targetId);
        remoteStoreCallback.handleRejectedListen(targetId, targetChange.getCause());
      }
    }
  }

  // Write Stream

  /**
   * Attempts to fill our write pipeline with writes from the LocalStore.
   *
   * <p>Called internally to bootstrap or refill the write pipeline and by SyncEngine whenever there
   * are new mutations to process.
   *
   * <p>Starts the write stream if necessary.
   */
  public void fillWritePipeline() {
    int lastBatchIdRetrieved =
        writePipeline.isEmpty() ? MutationBatch.UNKNOWN : writePipeline.getLast().getBatchId();
    while (canAddToWritePipeline()) {
      MutationBatch batch = localStore.getNextMutationBatch(lastBatchIdRetrieved);
      if (batch == null) {
        if (writePipeline.size() == 0) {
          writeStream.markIdle();
        }
        break;
      }
      addToWritePipeline(batch);
      lastBatchIdRetrieved = batch.getBatchId();
    }

    if (shouldStartWriteStream()) {
      startWriteStream();
    }
  }

  /**
   * Returns true if we can add to the write pipeline (i.e. it is not full and the network is
   * enabled).
   */
  private boolean canAddToWritePipeline() {
    return canUseNetwork() && writePipeline.size() < MAX_PENDING_WRITES;
  }

  /**
   * Queues additional writes to be sent to the write stream, sending them immediately if the write
   * stream is established.
   */
  private void addToWritePipeline(MutationBatch mutationBatch) {
    hardAssert(canAddToWritePipeline(), "addToWritePipeline called when pipeline is full");

    writePipeline.add(mutationBatch);

    if (writeStream.isOpen() && writeStream.isHandshakeComplete()) {
      writeStream.writeMutations(mutationBatch.getMutations());
    }
  }

  private void startWriteStream() {
    hardAssert(
        shouldStartWriteStream(),
        "startWriteStream() called when shouldStartWriteStream() is false.");
    writeStream.start();
  }

  /**
   * Handles a successful handshake response from the server, which is our cue to send any pending
   * writes.
   */
  private void handleWriteStreamHandshakeComplete() {
    // Record the stream token.
    localStore.setLastStreamToken(writeStream.getLastStreamToken());

    // Send the write pipeline now that stream is established.
    for (MutationBatch batch : writePipeline) {
      writeStream.writeMutations(batch.getMutations());
    }
  }

  /**
   * Handles a successful StreamingWriteResponse from the server that contains a mutation result.
   */
  private void handleWriteStreamMutationResults(
      SnapshotVersion commitVersion, List<MutationResult> results) {
    // This is a response to a write containing mutations and should be correlated to the first
    // write in our write pipeline.
    MutationBatch batch = writePipeline.poll();

    MutationBatchResult mutationBatchResult =
        MutationBatchResult.create(batch, commitVersion, results, writeStream.getLastStreamToken());
    remoteStoreCallback.handleSuccessfulWrite(mutationBatchResult);

    // It's possible that with the completion of this mutation another slot has freed up.
    fillWritePipeline();
  }

  private void handleWriteStreamClose(Status status) {
    if (Status.OK.equals(status)) {
      // Graceful stop (due to stop() or idle timeout). Make sure that's desirable.
      hardAssert(
          !shouldStartWriteStream(), "Write stream was stopped gracefully while still needed.");
    }

    // If the write stream closed due to an error, invoke the error callbacks if there are pending
    // writes.
    if (!status.isOk() && !writePipeline.isEmpty()) {
      // TODO: handle UNAUTHENTICATED status, see go/firestore-client-errors
      if (writeStream.isHandshakeComplete()) {
        // This error affects the actual writes
        handleWriteError(status);
      } else {
        // If there was an error before the handshake has finished, it's possible that the server is
        // unable to process the stream token we're sending. (Perhaps it's too old?)
        handleWriteHandshakeError(status);
      }
    }

    // The write stream may have already been restarted by refilling the write pipeline for failed
    // writes. In that case, we don't want to start the write stream again.
    if (shouldStartWriteStream()) {
      startWriteStream();
    }
  }

  private void handleWriteHandshakeError(Status status) {
    hardAssert(!status.isOk(), "Handling write error with status OK.");
    // Reset the token if it's a permanent error, signaling the write stream is no longer valid.
    // Note that the handshake does not count as a write: see comments on isPermanentWriteError for
    // details.
    if (Datastore.isPermanentError(status)) {
      String token = Util.toDebugString(writeStream.getLastStreamToken());
      Logger.debug(
          LOG_TAG,
          "RemoteStore error before completed handshake; resetting stream token %s: %s",
          token,
          status);
      writeStream.setLastStreamToken(WriteStream.EMPTY_STREAM_TOKEN);
      localStore.setLastStreamToken(WriteStream.EMPTY_STREAM_TOKEN);
    }
  }

  private void handleWriteError(Status status) {
    hardAssert(!status.isOk(), "Handling write error with status OK.");
    // Only handle permanent errors here. If it's transient, just let the retry logic kick in.
    if (Datastore.isPermanentWriteError(status)) {
      // If this was a permanent error, the request itself was the problem so it's not going
      // to succeed if we resend it.
      MutationBatch batch = writePipeline.poll();

      // In this case it's also unlikely that the server itself is melting down -- this was
      // just a bad request, so inhibit backoff on the next restart
      writeStream.inhibitBackoff();

      remoteStoreCallback.handleRejectedWrite(batch.getBatchId(), status);

      // It's possible that with the completion of this mutation another slot has freed up.
      fillWritePipeline();
    }
  }

  public Transaction createTransaction() {
    return new Transaction(datastore);
  }

  @Override
  public ImmutableSortedSet<DocumentKey> getRemoteKeysForTarget(int targetId) {
    return this.remoteStoreCallback.getRemoteKeysForTarget(targetId);
  }

  @Nullable
  @Override
  public TargetData getTargetDataForTarget(int targetId) {
    return this.listenTargets.get(targetId);
  }
}
