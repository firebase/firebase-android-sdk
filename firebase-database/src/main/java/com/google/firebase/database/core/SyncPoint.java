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

package com.google.firebase.database.core;

import static com.google.firebase.database.core.utilities.Utilities.hardAssert;

import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.annotations.NotNull;
import com.google.firebase.database.annotations.Nullable;
import com.google.firebase.database.core.operation.Operation;
import com.google.firebase.database.core.persistence.PersistenceManager;
import com.google.firebase.database.core.utilities.Pair;
import com.google.firebase.database.core.view.CacheNode;
import com.google.firebase.database.core.view.Change;
import com.google.firebase.database.core.view.DataEvent;
import com.google.firebase.database.core.view.Event;
import com.google.firebase.database.core.view.QueryParams;
import com.google.firebase.database.core.view.QuerySpec;
import com.google.firebase.database.core.view.View;
import com.google.firebase.database.core.view.ViewCache;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.IndexedNode;
import com.google.firebase.database.snapshot.NamedNode;
import com.google.firebase.database.snapshot.Node;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SyncPoint represents a single location in a SyncTree with 1 or more event registrations, meaning
 * we need to maintain 1 or more Views at this location to cache server data and raise appropriate
 * events for server changes and user writes (set, transaction, update).
 *
 * <p>It's responsible for: - Maintaining the set of 1 or more views necessary at this location (a
 * SyncPoint with 0 views should be removed). - Proxying user / server operations to the views as
 * appropriate (i.e. applyServerOverwrite, applyUserOverwrite, etc.)
 */
public class SyncPoint {

  /**
   * The Views being tracked at this location in the tree, stored as a map where the key is a
   * QueryParams and the value is the View for that query.
   *
   * <p>NOTE: This list will be quite small (usually 1, but perhaps 2 or 3; any more is an odd use
   * case).
   */
  private final Map<QueryParams, View> views;

  private final PersistenceManager persistenceManager;

  public SyncPoint(PersistenceManager persistenceManager) {
    this.views = new HashMap<QueryParams, View>();
    this.persistenceManager = persistenceManager;
  }

  public boolean isEmpty() {
    return this.views.isEmpty();
  }

  private List<DataEvent> applyOperationToView(
      View view, Operation operation, WriteTreeRef writes, Node optCompleteServerCache) {
    View.OperationResult result = view.applyOperation(operation, writes, optCompleteServerCache);
    // Not a default query, track active children
    if (!view.getQuery().loadsAllData()) {
      Set<ChildKey> removed = new HashSet<ChildKey>();
      Set<ChildKey> added = new HashSet<ChildKey>();
      for (Change change : result.changes) {
        Event.EventType type = change.getEventType();
        if (type == Event.EventType.CHILD_ADDED) {
          added.add(change.getChildKey());
        } else if (type == Event.EventType.CHILD_REMOVED) {
          removed.add(change.getChildKey());
        }
      }
      if (!added.isEmpty() || !removed.isEmpty()) {
        this.persistenceManager.updateTrackedQueryKeys(view.getQuery(), added, removed);
      }
    }
    return result.events;
  }

  public List<DataEvent> applyOperation(
      Operation operation, WriteTreeRef writesCache, Node optCompleteServerCache) {
    QueryParams queryParams = operation.getSource().getQueryParams();
    if (queryParams != null) {
      View view = this.views.get(queryParams);
      hardAssert(view != null);
      return applyOperationToView(view, operation, writesCache, optCompleteServerCache);
    } else {
      List<DataEvent> events = new ArrayList<DataEvent>();
      for (Map.Entry<QueryParams, View> entry : this.views.entrySet()) {
        View view = entry.getValue();
        events.addAll(applyOperationToView(view, operation, writesCache, optCompleteServerCache));
      }
      return events;
    }
  }

  /** Add an event callback for the specified query. */
  public List<DataEvent> addEventRegistration(
      @NotNull EventRegistration eventRegistration,
      WriteTreeRef writesCache,
      CacheNode serverCache) {
    QuerySpec query = eventRegistration.getQuerySpec();
    View view = this.views.get(query.getParams());
    if (view == null) {
      // TODO: make writesCache take flag for complete server node
      Node eventCache =
          writesCache.calcCompleteEventCache(
              serverCache.isFullyInitialized() ? serverCache.getNode() : null);
      boolean eventCacheComplete;
      if (eventCache != null) {
        eventCacheComplete = true;
      } else {
        eventCache = writesCache.calcCompleteEventChildren(serverCache.getNode());
        eventCacheComplete = false;
      }
      IndexedNode indexed = IndexedNode.from(eventCache, query.getIndex());
      ViewCache viewCache =
          new ViewCache(new CacheNode(indexed, eventCacheComplete, false), serverCache);
      view = new View(query, viewCache);
      // If this is a non-default query we need to tell persistence our current view of the data
      if (!query.loadsAllData()) {
        Set<ChildKey> allChildren = new HashSet<ChildKey>();
        for (NamedNode node : view.getEventCache()) {
          allChildren.add(node.getName());
        }
        this.persistenceManager.setTrackedQueryKeys(query, allChildren);
      }
      this.views.put(query.getParams(), view);
    }

    // This is guaranteed to exist now, we just created anything that was missing
    view.addEventRegistration(eventRegistration);
    return view.getInitialEvents(eventRegistration);
  }

  /**
   * Remove event callback(s). Return cancelEvents if a cancelError is specified.
   *
   * <p>If query is the default query, we'll check all views for the specified eventRegistration. If
   * eventRegistration is null, we'll remove all callbacks for the specified view(s).
   *
   * @param {!fb.api.Query} query
   * @param {?fb.core.view.EventRegistration} eventRegistration If null, remove all callbacks.
   * @param {Error=} cancelError If a cancelError is provided, appropriate cancel events will be
   *     returned.
   * @return {{removed:!Array.<!fb.api.Query>, events:!Array.<!fb.core.view.Event>}} removed queries
   *     and any cancel events
   */
  public Pair<List<QuerySpec>, List<Event>> removeEventRegistration(
      @NotNull QuerySpec query,
      @Nullable EventRegistration eventRegistration,
      @Nullable DatabaseError cancelError) {
    List<QuerySpec> removed = new ArrayList<QuerySpec>();
    List<Event> cancelEvents = new ArrayList<Event>();
    boolean hadCompleteView = this.hasCompleteView();
    if (query.isDefault()) {
      // When you do ref.off(...), we search all views for the registration to remove.
      Iterator<Map.Entry<QueryParams, View>> iterator = this.views.entrySet().iterator();
      while (iterator.hasNext()) {
        Map.Entry<QueryParams, View> entry = iterator.next();
        View view = entry.getValue();
        cancelEvents.addAll(view.removeEventRegistration(eventRegistration, cancelError));
        if (view.isEmpty()) {
          iterator.remove();

          // We'll deal with complete views later.
          if (!view.getQuery().loadsAllData()) {
            removed.add(view.getQuery());
          }
        }
      }
    } else {
      // remove the callback from the specific view.
      View view = this.views.get(query.getParams());
      if (view != null) {
        cancelEvents.addAll(view.removeEventRegistration(eventRegistration, cancelError));
        if (view.isEmpty()) {
          this.views.remove(query.getParams());

          // We'll deal with complete views later.
          if (!view.getQuery().loadsAllData()) {
            removed.add(view.getQuery());
          }
        }
      }
    }

    if (hadCompleteView && !this.hasCompleteView()) {
      // We removed our last complete view.
      removed.add(QuerySpec.defaultQueryAtPath(query.getPath()));
    }
    return new Pair<List<QuerySpec>, List<Event>>(removed, cancelEvents);
  }

  public List<View> getQueryViews() {
    List<View> views = new ArrayList<View>();
    for (Map.Entry<QueryParams, View> entry : this.views.entrySet()) {
      View view = entry.getValue();
      if (!view.getQuery().loadsAllData()) {
        views.add(view);
      }
    }
    return views;
  }

  public Node getCompleteServerCache(Path path) {
    for (View view : this.views.values()) {
      if (view.getCompleteServerCache(path) != null) {
        return view.getCompleteServerCache(path);
      }
    }
    return null;
  }

  public View viewForQuery(QuerySpec query) {
    // TODO: iOS doesn't have this loadsAllData() case and I'm not sure it makes sense... but
    // leaving for now.
    if (query.loadsAllData()) {
      return this.getCompleteView();
    } else {
      return this.views.get(query.getParams());
    }
  }

  public boolean viewExistsForQuery(QuerySpec query) {
    return this.viewForQuery(query) != null;
  }

  public boolean hasCompleteView() {
    return this.getCompleteView() != null;
  }

  public View getCompleteView() {
    for (Map.Entry<QueryParams, View> entry : this.views.entrySet()) {
      View view = entry.getValue();
      if (view.getQuery().loadsAllData()) {
        return view;
      }
    }
    return null;
  }

  // Package private for testing purposes only
  Map<QueryParams, View> getViews() {
    return views;
  }
}
