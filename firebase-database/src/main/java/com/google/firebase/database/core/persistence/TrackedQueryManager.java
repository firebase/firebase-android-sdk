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

package com.google.firebase.database.core.persistence;

import static com.google.firebase.database.core.utilities.Utilities.hardAssert;

import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.utilities.Clock;
import com.google.firebase.database.core.utilities.ImmutableTree;
import com.google.firebase.database.core.utilities.Predicate;
import com.google.firebase.database.core.utilities.Utilities;
import com.google.firebase.database.core.view.QueryParams;
import com.google.firebase.database.core.view.QuerySpec;
import com.google.firebase.database.logging.LogWrapper;
import com.google.firebase.database.snapshot.ChildKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TrackedQueryManager {

  private static final Predicate<Map<QueryParams, TrackedQuery>> HAS_DEFAULT_COMPLETE_PREDICATE =
      new Predicate<Map<QueryParams, TrackedQuery>>() {
        @Override
        public boolean evaluate(Map<QueryParams, TrackedQuery> trackedQueries) {
          TrackedQuery trackedQuery = trackedQueries.get(QueryParams.DEFAULT_PARAMS);
          return trackedQuery != null && trackedQuery.complete;
        }
      };

  private static final Predicate<Map<QueryParams, TrackedQuery>> HAS_ACTIVE_DEFAULT_PREDICATE =
      new Predicate<Map<QueryParams, TrackedQuery>>() {
        @Override
        public boolean evaluate(Map<QueryParams, TrackedQuery> trackedQueries) {
          TrackedQuery trackedQuery = trackedQueries.get(QueryParams.DEFAULT_PARAMS);
          return trackedQuery != null && trackedQuery.active;
        }
      };

  private static final Predicate<TrackedQuery> IS_QUERY_PRUNABLE_PREDICATE =
      new Predicate<TrackedQuery>() {
        @Override
        public boolean evaluate(TrackedQuery query) {
          return !query.active;
        }
      };

  private static final Predicate<TrackedQuery> IS_QUERY_UNPRUNABLE_PREDICATE =
      new Predicate<TrackedQuery>() {
        @Override
        public boolean evaluate(TrackedQuery query) {
          return !IS_QUERY_PRUNABLE_PREDICATE.evaluate(query);
        }
      };

  // In-memory cache of tracked queries.  Should always be in-sync with the DB.
  private ImmutableTree<Map<QueryParams, TrackedQuery>> trackedQueryTree;

  // DB, where we permanently store tracked queries.
  private final PersistenceStorageEngine storageLayer;

  private final LogWrapper logger;
  private final Clock clock;

  // ID we'll assign to the next tracked query.
  private long currentQueryId = 0;

  private static void assertValidTrackedQuery(QuerySpec query) {
    hardAssert(
        !query.loadsAllData() || query.isDefault(),
        "Can't have tracked non-default query that loads all data");
  }

  private static QuerySpec normalizeQuery(QuerySpec query) {
    // If the query loadsAllData, we don't care about orderBy.
    // So just treat it as a default query.
    return query.loadsAllData() ? QuerySpec.defaultQueryAtPath(query.getPath()) : query;
  }

  public TrackedQueryManager(
      PersistenceStorageEngine storageLayer, LogWrapper logger, Clock clock) {
    this.storageLayer = storageLayer;
    this.logger = logger;
    this.clock = clock;
    this.trackedQueryTree = new ImmutableTree<Map<QueryParams, TrackedQuery>>(null);

    resetPreviouslyActiveTrackedQueries();

    // Populate our cache from the storage layer.
    List<TrackedQuery> trackedQueries = this.storageLayer.loadTrackedQueries();
    for (TrackedQuery query : trackedQueries) {
      currentQueryId = Math.max(query.id + 1, currentQueryId);
      cacheTrackedQuery(query);
    }
  }

  private void resetPreviouslyActiveTrackedQueries() {
    // Minor hack: We do most of our transactions at the SyncTree level, but it is very inconvenient
    // to do so here, so the transaction goes here. :-/
    try {
      this.storageLayer.beginTransaction();
      this.storageLayer.resetPreviouslyActiveTrackedQueries(clock.millis());
      this.storageLayer.setTransactionSuccessful();
    } finally {
      this.storageLayer.endTransaction();
    }
  }

  public TrackedQuery findTrackedQuery(QuerySpec query) {
    query = normalizeQuery(query);
    Map<QueryParams, TrackedQuery> set = this.trackedQueryTree.get(query.getPath());
    return (set != null) ? set.get(query.getParams()) : null;
  }

  public void removeTrackedQuery(QuerySpec query) {
    query = normalizeQuery(query);
    TrackedQuery trackedQuery = findTrackedQuery(query);
    hardAssert(trackedQuery != null, "Query must exist to be removed.");

    this.storageLayer.deleteTrackedQuery(trackedQuery.id);
    Map<QueryParams, TrackedQuery> trackedQueries = this.trackedQueryTree.get(query.getPath());
    trackedQueries.remove(query.getParams());
    if (trackedQueries.isEmpty()) {
      this.trackedQueryTree = this.trackedQueryTree.remove(query.getPath());
    }
  }

  public void setQueryActive(QuerySpec query) {
    setQueryActiveFlag(query, true);
  }

  public void setQueryInactive(QuerySpec query) {
    setQueryActiveFlag(query, false);
  }

  private void setQueryActiveFlag(QuerySpec query, boolean isActive) {
    query = normalizeQuery(query);
    TrackedQuery trackedQuery = findTrackedQuery(query);

    // Regardless of whether it's now active or no longer active, we update the lastUse time.
    long lastUse = clock.millis();
    if (trackedQuery != null) {
      trackedQuery = trackedQuery.updateLastUse(lastUse).setActiveState(isActive);
    } else {
      hardAssert(
          isActive, "If we're setting the query to inactive, we should already be tracking it!");
      trackedQuery =
          new TrackedQuery(this.currentQueryId++, query, lastUse, /*complete=*/ false, isActive);
    }

    saveTrackedQuery(trackedQuery);
  }

  public void setQueryCompleteIfExists(QuerySpec query) {
    query = normalizeQuery(query);
    TrackedQuery trackedQuery = findTrackedQuery(query);
    if (trackedQuery != null && !trackedQuery.complete) {
      saveTrackedQuery(trackedQuery.setComplete());
    }
  }

  public void setQueriesComplete(Path path) {
    this.trackedQueryTree
        .subtree(path)
        .foreach(
            new ImmutableTree.TreeVisitor<Map<QueryParams, TrackedQuery>, Void>() {
              @Override
              public Void onNodeValue(
                  Path relativePath, Map<QueryParams, TrackedQuery> value, Void accum) {
                for (Map.Entry<QueryParams, TrackedQuery> e : value.entrySet()) {
                  TrackedQuery trackedQuery = e.getValue();
                  if (!trackedQuery.complete) {
                    saveTrackedQuery(trackedQuery.setComplete());
                  }
                }
                return null;
              }
            });
  }

  public boolean isQueryComplete(QuerySpec query) {
    if (this.includedInDefaultCompleteQuery(query.getPath())) {
      return true;
    } else if (query.loadsAllData()) {
      // We didn't find a default complete query, so must not be complete.
      return false;
    } else {
      Map<QueryParams, TrackedQuery> trackedQueries = this.trackedQueryTree.get(query.getPath());
      return trackedQueries != null
          && trackedQueries.containsKey(query.getParams())
          && trackedQueries.get(query.getParams()).complete;
    }
  }

  public PruneForest pruneOldQueries(CachePolicy cachePolicy) {
    List<TrackedQuery> prunable = getQueriesMatching(IS_QUERY_PRUNABLE_PREDICATE);
    long countToPrune = calculateCountToPrune(cachePolicy, prunable.size());
    PruneForest forest = new PruneForest();

    if (logger.logsDebug()) {
      logger.debug(
          "Pruning old queries.  Prunable: "
              + prunable.size()
              + " Count to prune: "
              + countToPrune);
    }

    Collections.sort(
        prunable,
        new Comparator<TrackedQuery>() {
          @Override
          public int compare(TrackedQuery q1, TrackedQuery q2) {
            return Utilities.compareLongs(q1.lastUse, q2.lastUse);
          }
        });

    for (int i = 0; i < countToPrune; i++) {
      TrackedQuery toPrune = prunable.get(i);
      forest = forest.prune(toPrune.querySpec.getPath());
      removeTrackedQuery(toPrune.querySpec);
    }

    // Keep the rest of the prunable queries.
    for (int i = (int) countToPrune; i < prunable.size(); i++) {
      TrackedQuery toKeep = prunable.get(i);
      forest = forest.keep(toKeep.querySpec.getPath());
    }

    // Also keep the unprunable queries.
    List<TrackedQuery> unprunable = getQueriesMatching(IS_QUERY_UNPRUNABLE_PREDICATE);
    if (logger.logsDebug()) {
      logger.debug("Unprunable queries: " + unprunable.size());
    }
    for (TrackedQuery toKeep : unprunable) {
      forest = forest.keep(toKeep.querySpec.getPath());
    }

    return forest;
  }

  private static long calculateCountToPrune(CachePolicy cachePolicy, long prunableCount) {
    long countToKeep = prunableCount;

    // prune by percentage.
    float percentToKeep = 1 - cachePolicy.getPercentOfQueriesToPruneAtOnce();
    countToKeep = (long) Math.floor(countToKeep * percentToKeep);

    // Make sure we're not keeping more than the max.
    countToKeep = Math.min(countToKeep, cachePolicy.getMaxNumberOfQueriesToKeep());

    // Now we know how many to prune.
    return prunableCount - countToKeep;
  }

  /**
   * Uses our tracked queries to figure out what complete children we have.
   *
   * @param path Path to find complete data children under.
   * @return Set of complete ChildKeys
   */
  public Set<ChildKey> getKnownCompleteChildren(Path path) {
    hardAssert(
        !this.isQueryComplete(QuerySpec.defaultQueryAtPath(path)), "Path is fully complete.");

    Set<ChildKey> completeChildren = new HashSet<ChildKey>();
    // First, get complete children from any queries at this location.
    Set<Long> queryIds = filteredQueryIdsAtPath(path);
    if (!queryIds.isEmpty()) {
      completeChildren.addAll(storageLayer.loadTrackedQueryKeys(queryIds));
    }

    // Second, get any complete default queries immediately below us.
    for (Map.Entry<ChildKey, ImmutableTree<Map<QueryParams, TrackedQuery>>> childEntry :
        this.trackedQueryTree.subtree(path).getChildren()) {
      ChildKey childKey = childEntry.getKey();
      ImmutableTree<Map<QueryParams, TrackedQuery>> childTree = childEntry.getValue();
      if (childTree.getValue() != null
          && HAS_DEFAULT_COMPLETE_PREDICATE.evaluate(childTree.getValue())) {
        completeChildren.add(childKey);
      }
    }

    return completeChildren;
  }

  public void ensureCompleteTrackedQuery(Path path) {
    if (!this.includedInDefaultCompleteQuery(path)) {
      // TODO[persistence]: What if it's included in the tracked keys of a query?  Do we still want
      // to add a new tracked query for it?

      QuerySpec querySpec = QuerySpec.defaultQueryAtPath(path);
      TrackedQuery trackedQuery = findTrackedQuery(querySpec);
      if (trackedQuery == null) {
        trackedQuery =
            new TrackedQuery(
                this.currentQueryId++,
                querySpec,
                clock.millis(),
                /*complete=*/ true,
                /*active=*/ false);
      } else {
        hardAssert(!trackedQuery.complete, "This should have been handled above!");
        trackedQuery = trackedQuery.setComplete();
      }
      saveTrackedQuery(trackedQuery);
    }
  }

  public boolean hasActiveDefaultQuery(Path path) {
    return this.trackedQueryTree.rootMostValueMatching(path, HAS_ACTIVE_DEFAULT_PREDICATE) != null;
  }

  public long countOfPrunableQueries() {
    return getQueriesMatching(IS_QUERY_PRUNABLE_PREDICATE).size();
  }

  // Used for tests to assert we're still in-sync with the DB.  Don't call it in production, since
  // it's slow.
  void verifyCache() {
    List<TrackedQuery> storedTrackedQueries = this.storageLayer.loadTrackedQueries();

    final List<TrackedQuery> trackedQueries = new ArrayList<TrackedQuery>();
    this.trackedQueryTree.foreach(
        new ImmutableTree.TreeVisitor<Map<QueryParams, TrackedQuery>, Void>() {
          @Override
          public Void onNodeValue(
              Path relativePath, Map<QueryParams, TrackedQuery> value, Void accum) {
            for (TrackedQuery trackedQuery : value.values()) {
              trackedQueries.add(trackedQuery);
            }
            return null;
          }
        });
    Collections.sort(
        trackedQueries,
        new Comparator<TrackedQuery>() {
          @Override
          public int compare(TrackedQuery o1, TrackedQuery o2) {
            return Utilities.compareLongs(o1.id, o2.id);
          }
        });

    hardAssert(
        storedTrackedQueries.equals(trackedQueries),
        "Tracked queries out of sync.  Tracked queries: "
            + trackedQueries
            + " Stored queries: "
            + storedTrackedQueries);
  }

  private boolean includedInDefaultCompleteQuery(Path path) {
    return this.trackedQueryTree.findRootMostMatchingPath(path, HAS_DEFAULT_COMPLETE_PREDICATE)
        != null;
  }

  private Set<Long> filteredQueryIdsAtPath(Path path) {
    final Set<Long> ids = new HashSet<Long>();

    Map<QueryParams, TrackedQuery> queries = this.trackedQueryTree.get(path);
    if (queries != null) {
      for (TrackedQuery query : queries.values()) {
        if (!query.querySpec.loadsAllData()) {
          ids.add(query.id);
        }
      }
    }
    return ids;
  }

  private void cacheTrackedQuery(TrackedQuery query) {
    assertValidTrackedQuery(query.querySpec);

    Map<QueryParams, TrackedQuery> trackedSet =
        this.trackedQueryTree.get(query.querySpec.getPath());
    if (trackedSet == null) {
      trackedSet = new HashMap<QueryParams, TrackedQuery>();
      this.trackedQueryTree = this.trackedQueryTree.set(query.querySpec.getPath(), trackedSet);
    }

    // Sanity check.
    TrackedQuery existing = trackedSet.get(query.querySpec.getParams());
    hardAssert(existing == null || existing.id == query.id);

    trackedSet.put(query.querySpec.getParams(), query);
  }

  private void saveTrackedQuery(TrackedQuery query) {
    cacheTrackedQuery(query);
    storageLayer.saveTrackedQuery(query);
  }

  private List<TrackedQuery> getQueriesMatching(Predicate<TrackedQuery> predicate) {
    List<TrackedQuery> matching = new ArrayList<TrackedQuery>();
    for (Map.Entry<Path, Map<QueryParams, TrackedQuery>> entry : this.trackedQueryTree) {
      for (TrackedQuery query : entry.getValue().values()) {
        if (predicate.evaluate(query)) {
          matching.add(query);
        }
      }
    }
    return matching;
  }
}
