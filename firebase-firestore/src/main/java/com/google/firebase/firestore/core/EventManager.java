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

import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.core.SyncEngine.SyncEngineCallback;
import com.google.firebase.firestore.util.Util;
import io.grpc.Status;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * EventManager is responsible for mapping queries to query event listeners. It handles "fan-out."
 * (Identical queries will re-use the same watch on the backend.)
 */
public final class EventManager implements SyncEngineCallback {

  private static class QueryListenersInfo {
    private final List<QueryListener> listeners;
    private ViewSnapshot viewSnapshot;
    private int targetId;

    QueryListenersInfo() {
      listeners = new ArrayList<>();
    }
  }

  /** Holds (internal) options for listening */
  public static class ListenOptions {
    /** Raise events when only metadata of documents changes */
    public boolean includeDocumentMetadataChanges;

    /** Raise events when only metadata of the query changes */
    public boolean includeQueryMetadataChanges;

    /** Wait for a sync with the server when online, but still raise events while offline. */
    public boolean waitForSyncWhenOnline;
  }

  private final SyncEngine syncEngine;

  private final Map<Query, QueryListenersInfo> queries;

  private final Set<EventListener<Void>> snapshotsInSyncListeners = new HashSet<>();

  private OnlineState onlineState = OnlineState.UNKNOWN;

  public EventManager(SyncEngine syncEngine) {
    this.syncEngine = syncEngine;
    queries = new HashMap<>();
    syncEngine.setCallback(this);
  }

  /**
   * Adds a query listener that will be called with new snapshots for the query. The EventManager is
   * responsible for multiplexing many listeners to a single listen in the SyncEngine and will
   * perform a listen if it's the first QueryListener added for a query.
   *
   * @return the targetId of the listen call in the SyncEngine.
   */
  public int addQueryListener(QueryListener queryListener) {
    Query query = queryListener.getQuery();

    QueryListenersInfo queryInfo = queries.get(query);
    boolean firstListen = queryInfo == null;
    if (firstListen) {
      queryInfo = new QueryListenersInfo();
      queries.put(query, queryInfo);
    }

    queryInfo.listeners.add(queryListener);

    // Run global snapshot listeners if a consistent snapshot has been emitted.
    boolean raisedEvent = queryListener.onOnlineStateChanged(onlineState);
    hardAssert(
        !raisedEvent, "onOnlineStateChanged() shouldn't raise an event for brand-new listeners.");

    if (queryInfo.viewSnapshot != null) {
      raisedEvent = queryListener.onViewSnapshot(queryInfo.viewSnapshot);
      if (raisedEvent) {
        raiseSnapshotsInSyncEvent();
      }
    }

    if (firstListen) {
      queryInfo.targetId = syncEngine.listen(query);
    }
    return queryInfo.targetId;
  }

  /** Removes a previously added listener. It's a no-op if the listener is not found. */
  public void removeQueryListener(QueryListener listener) {
    Query query = listener.getQuery();
    QueryListenersInfo queryInfo = queries.get(query);
    boolean lastListen = false;
    if (queryInfo != null) {
      queryInfo.listeners.remove(listener);
      lastListen = queryInfo.listeners.isEmpty();
    }

    if (lastListen) {
      queries.remove(query);
      syncEngine.stopListening(query);
    }
  }

  public void addSnapshotsInSyncListener(EventListener<Void> listener) {
    snapshotsInSyncListeners.add(listener);
    listener.onEvent(null, null);
  }

  public void removeSnapshotsInSyncListener(EventListener<Void> listener) {
    snapshotsInSyncListeners.remove(listener);
  }

  /** Call all global snapshot listeners that have been set. */
  private void raiseSnapshotsInSyncEvent() {
    for (EventListener<Void> listener : snapshotsInSyncListeners) {
      listener.onEvent(null, null);
    }
  }

  @Override
  public void onViewSnapshots(List<ViewSnapshot> snapshotList) {
    boolean raisedEvent = false;
    for (ViewSnapshot viewSnapshot : snapshotList) {
      Query query = viewSnapshot.getQuery();
      QueryListenersInfo info = queries.get(query);
      if (info != null) {
        for (QueryListener listener : info.listeners) {
          if (listener.onViewSnapshot(viewSnapshot)) {
            raisedEvent = true;
          }
        }
        info.viewSnapshot = viewSnapshot;
      }
    }
    if (raisedEvent) {
      raiseSnapshotsInSyncEvent();
    }
  }

  @Override
  public void onError(Query query, Status error) {
    QueryListenersInfo info = queries.get(query);
    if (info != null) {
      for (QueryListener listener : info.listeners) {
        listener.onError(Util.exceptionFromStatus(error));
      }
    }
    queries.remove(query);
  }

  @Override
  public void handleOnlineStateChange(OnlineState onlineState) {
    boolean raisedEvent = false;
    this.onlineState = onlineState;
    for (QueryListenersInfo info : queries.values()) {
      for (QueryListener listener : info.listeners) {
        if (listener.onOnlineStateChanged(onlineState)) {
          raisedEvent = true;
        }
      }
    }
    if (raisedEvent) {
      raiseSnapshotsInSyncEvent();
    }
  }
}
