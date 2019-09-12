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

package com.google.firebase.firestore.core;

import static com.google.firebase.firestore.util.Assert.hardAssert;

import androidx.annotation.Nullable;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.core.DocumentViewChange.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * QueryListener takes a series of internal view snapshots and determines when to raise events.
 *
 * <p>It uses an EventListener to dispatch events.
 *
 * <p>Note that this class can be created for any arbitrary thread but it's expected to be called
 * only from our worker thread.
 */
public class QueryListener {
  private final Query query;

  private final EventManager.ListenOptions options;

  private final EventListener<ViewSnapshot> listener;

  /**
   * Initial snapshots (e.g. from cache) may not be propagated to the wrapped observer. This flag is
   * set to true once we've actually raised an event.
   */
  private boolean raisedInitialEvent = false;

  private OnlineState onlineState = OnlineState.UNKNOWN;

  private @Nullable ViewSnapshot snapshot;

  public QueryListener(
      Query query, EventManager.ListenOptions options, EventListener<ViewSnapshot> listener) {
    this.query = query;
    this.listener = listener;
    this.options = options;
  }

  public Query getQuery() {
    return query;
  }

  /**
   * Applies the new ViewSnapshot to this listener, raising a user-facing event if applicable
   * (depending on what changed, whether the user has opted into metadata-only changes, etc.).
   * Returns true if a user-facing event was indeed raised.
   */
  public boolean onViewSnapshot(ViewSnapshot newSnapshot) {
    hardAssert(
        !newSnapshot.getChanges().isEmpty() || newSnapshot.didSyncStateChange(),
        "We got a new snapshot with no changes?");

    boolean raisedEvent = false;
    if (!options.includeDocumentMetadataChanges) {
      // Remove the metadata only changes
      List<DocumentViewChange> documentChanges = new ArrayList<>();
      for (DocumentViewChange change : newSnapshot.getChanges()) {
        if (change.getType() != Type.METADATA) {
          documentChanges.add(change);
        }
      }
      newSnapshot =
          new ViewSnapshot(
              newSnapshot.getQuery(),
              newSnapshot.getDocuments(),
              newSnapshot.getOldDocuments(),
              documentChanges,
              newSnapshot.isFromCache(),
              newSnapshot.getMutatedKeys(),
              newSnapshot.didSyncStateChange(),
              /* excludesMetadataChanges= */ true);
    }

    if (!raisedInitialEvent) {
      if (shouldRaiseInitialEvent(newSnapshot, onlineState)) {
        raiseInitialEvent(newSnapshot);
        raisedEvent = true;
      }
    } else if (shouldRaiseEvent(newSnapshot)) {
      listener.onEvent(newSnapshot, null);
      raisedEvent = true;
    }

    this.snapshot = newSnapshot;
    return raisedEvent;
  }

  public void onError(FirebaseFirestoreException error) {
    listener.onEvent(null, error);
  }

  /** Returns whether a snapshot was raised. */
  public boolean onOnlineStateChanged(OnlineState onlineState) {
    this.onlineState = onlineState;
    boolean raisedEvent = false;
    if (snapshot != null && !raisedInitialEvent && shouldRaiseInitialEvent(snapshot, onlineState)) {
      raiseInitialEvent(snapshot);
      raisedEvent = true;
    }
    return raisedEvent;
  }

  private boolean shouldRaiseInitialEvent(ViewSnapshot snapshot, OnlineState onlineState) {
    hardAssert(
        !raisedInitialEvent,
        "Determining whether to raise first event but already had first event.");

    // Always raise the first event when we're synced
    if (!snapshot.isFromCache()) {
      return true;
    }

    // NOTE: We consider OnlineState.UNKNOWN as online (it should become OFFLINE
    // or ONLINE if we wait long enough).
    boolean maybeOnline = !onlineState.equals(OnlineState.OFFLINE);
    // Don't raise the event if we're online, aren't synced yet (checked
    // above) and are waiting for a sync.
    if (options.waitForSyncWhenOnline && maybeOnline) {
      hardAssert(snapshot.isFromCache(), "Waiting for sync, but snapshot is not from cache");
      return false;
    }

    // Raise data from cache if we have any documents or we are offline
    return !snapshot.getDocuments().isEmpty() || onlineState.equals(OnlineState.OFFLINE);
  }

  private boolean shouldRaiseEvent(ViewSnapshot snapshot) {
    // We don't need to handle includeDocumentMetadataChanges here because the Metadata only
    // changes have already been stripped out if needed. At this point the only changes we will
    // see are the ones we should propagate.
    if (!snapshot.getChanges().isEmpty()) {
      return true;
    }

    boolean hasPendingWritesChanged =
        this.snapshot != null && this.snapshot.hasPendingWrites() != snapshot.hasPendingWrites();
    if (snapshot.didSyncStateChange() || hasPendingWritesChanged) {
      return options.includeQueryMetadataChanges;
    }

    // Generally we should have hit one of the cases above, but it's possible
    // to get here if there were only metadata docChanges and they got
    // stripped out.
    return false;
  }

  private void raiseInitialEvent(ViewSnapshot snapshot) {
    hardAssert(!raisedInitialEvent, "Trying to raise initial event for second time");
    snapshot =
        ViewSnapshot.fromInitialDocuments(
            snapshot.getQuery(),
            snapshot.getDocuments(),
            snapshot.getMutatedKeys(),
            snapshot.isFromCache(),
            snapshot.excludesMetadataChanges());
    raisedInitialEvent = true;
    listener.onEvent(snapshot, null);
  }
}
