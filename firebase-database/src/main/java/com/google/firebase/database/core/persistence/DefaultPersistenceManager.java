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

import com.google.firebase.database.core.CompoundWrite;
import com.google.firebase.database.core.Context;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.UserWriteRecord;
import com.google.firebase.database.core.utilities.Clock;
import com.google.firebase.database.core.utilities.DefaultClock;
import com.google.firebase.database.core.view.CacheNode;
import com.google.firebase.database.core.view.QuerySpec;
import com.google.firebase.database.logging.LogWrapper;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.EmptyNode;
import com.google.firebase.database.snapshot.IndexedNode;
import com.google.firebase.database.snapshot.Node;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

public class DefaultPersistenceManager implements PersistenceManager {

  private final PersistenceStorageEngine storageLayer;
  private final TrackedQueryManager trackedQueryManager;
  private final LogWrapper logger;
  private final CachePolicy cachePolicy;
  private long serverCacheUpdatesSinceLastPruneCheck = 0;

  public DefaultPersistenceManager(
      Context ctx, PersistenceStorageEngine engine, CachePolicy cachePolicy) {
    this(ctx, engine, cachePolicy, new DefaultClock());
  }

  public DefaultPersistenceManager(
      Context ctx, PersistenceStorageEngine engine, CachePolicy cachePolicy, Clock clock) {
    this.storageLayer = engine;
    this.logger = ctx.getLogger("Persistence");
    this.trackedQueryManager = new TrackedQueryManager(storageLayer, logger, clock);
    this.cachePolicy = cachePolicy;
  }

  /**
   * Save a user overwrite
   *
   * @param path The path for this write
   * @param node The node for this write
   * @param writeId The write id that was used for this write
   */
  @Override
  public void saveUserOverwrite(Path path, Node node, long writeId) {
    this.storageLayer.saveUserOverwrite(path, node, writeId);
  }

  /**
   * Save a user merge
   *
   * @param path The path for this merge
   * @param children The children for this merge
   * @param writeId The write id that was used for this merge
   */
  @Override
  public void saveUserMerge(Path path, CompoundWrite children, long writeId) {
    this.storageLayer.saveUserMerge(path, children, writeId);
  }

  /**
   * Remove a write with the given write id.
   *
   * @param writeId The write id to remove
   */
  @Override
  public void removeUserWrite(long writeId) {
    this.storageLayer.removeUserWrite(writeId);
  }

  @Override
  public void removeAllUserWrites() {
    this.storageLayer.removeAllUserWrites();
  }

  @Override
  public void applyUserWriteToServerCache(Path path, Node node) {
    // This is a hack to guess whether we already cached this because we got a server data update
    // for this write via an existing active default query.  If we didn't, then we'll manually cache
    // this and add a tracked query to mark it complete and keep it cached.
    // Unfortunately this is just a guess and it's possible that we *did* get an update (e.g. via a
    // filtered query) and by overwriting the cache here, we'll actually store an incorrect value
    // (e.g. in the case that we wrote a ServerValue.TIMESTAMP and the server resolved it to a
    // different value).
    // TODO[persistence]: Consider reworking.
    if (!this.trackedQueryManager.hasActiveDefaultQuery(path)) {
      this.storageLayer.overwriteServerCache(path, node);
      this.trackedQueryManager.ensureCompleteTrackedQuery(path);
    }
  }

  @Override
  public void applyUserWriteToServerCache(Path path, CompoundWrite merge) {
    // TODO: This could probably be optimized.
    for (Map.Entry<Path, Node> write : merge) {
      Path writePath = path.child(write.getKey());
      Node writeNode = write.getValue();
      this.applyUserWriteToServerCache(writePath, writeNode);
    }
  }

  /**
   * Return a list of all writes that were persisted
   *
   * @return The list of writes
   */
  @Override
  public List<UserWriteRecord> loadUserWrites() {
    return this.storageLayer.loadUserWrites();
  }

  /**
   * Returns any cached node or children as a CacheNode. The query is *not* used to filter the node
   * but rather to determine if it can be considered complete.
   *
   * @param query The query at the path
   * @return The cached node or an empty CacheNode if no cache is available
   */
  @Override
  public CacheNode serverCache(QuerySpec query) {
    Set<ChildKey> trackedKeys;
    boolean complete;
    // TODO[persistence]: Should we use trackedKeys to find out if this location is a child of a
    // complete query?
    if (this.trackedQueryManager.isQueryComplete(query)) {
      complete = true;
      TrackedQuery trackedQuery = this.trackedQueryManager.findTrackedQuery(query);
      if (!query.loadsAllData() && trackedQuery != null && trackedQuery.complete) {
        trackedKeys = this.storageLayer.loadTrackedQueryKeys(trackedQuery.id);
      } else {
        trackedKeys = null;
      }
    } else {
      complete = false;
      trackedKeys = trackedQueryManager.getKnownCompleteChildren(query.getPath());
    }

    // TODO[persistence]: Only load the tracked key data rather than load everything and then filter
    Node serverCacheNode = storageLayer.serverCache(query.getPath());
    if (trackedKeys != null) {
      Node filteredNode = EmptyNode.Empty();
      for (ChildKey key : trackedKeys) {
        filteredNode =
            filteredNode.updateImmediateChild(key, serverCacheNode.getImmediateChild(key));
      }
      return new CacheNode(
          IndexedNode.from(filteredNode, query.getIndex()), complete, /*filtered=*/ true);
    } else {
      return new CacheNode(
          IndexedNode.from(serverCacheNode, query.getIndex()), complete, /*filtered=*/ false);
    }
  }

  @Override
  public void updateServerCache(QuerySpec query, Node node) {
    if (query.loadsAllData()) {
      this.storageLayer.overwriteServerCache(query.getPath(), node);
    } else {
      this.storageLayer.mergeIntoServerCache(query.getPath(), node);
    }
    setQueryComplete(query);
    doPruneCheckAfterServerUpdate();
  }

  @Override
  public void updateServerCache(Path path, CompoundWrite children) {
    this.storageLayer.mergeIntoServerCache(path, children);
    doPruneCheckAfterServerUpdate();
  }

  @Override
  public void setQueryActive(QuerySpec query) {
    this.trackedQueryManager.setQueryActive(query);
  }

  @Override
  public void setQueryInactive(QuerySpec query) {
    this.trackedQueryManager.setQueryInactive(query);
  }

  @Override
  public void setQueryComplete(QuerySpec query) {
    if (query.loadsAllData()) {
      this.trackedQueryManager.setQueriesComplete(query.getPath());
    } else {
      this.trackedQueryManager.setQueryCompleteIfExists(query);
    }
  }

  @Override
  public void setTrackedQueryKeys(QuerySpec query, Set<ChildKey> keys) {
    hardAssert(!query.loadsAllData(), "We should only track keys for filtered queries.");
    TrackedQuery trackedQuery = this.trackedQueryManager.findTrackedQuery(query);
    hardAssert(
        trackedQuery != null && trackedQuery.active,
        "We only expect tracked keys for currently-active queries.");

    this.storageLayer.saveTrackedQueryKeys(trackedQuery.id, keys);
    // TODO: In the future we may want to try to prune the no-longer-tracked keys.
  }

  @Override
  public void updateTrackedQueryKeys(QuerySpec query, Set<ChildKey> added, Set<ChildKey> removed) {
    hardAssert(!query.loadsAllData(), "We should only track keys for filtered queries.");
    TrackedQuery trackedQuery = this.trackedQueryManager.findTrackedQuery(query);
    hardAssert(
        trackedQuery != null && trackedQuery.active,
        "We only expect tracked keys for currently-active queries.");

    this.storageLayer.updateTrackedQueryKeys(trackedQuery.id, added, removed);
    // TODO: In the future we may want to try to prune the no-longer-tracked keys.
  }

  @Override
  public <T> T runInTransaction(Callable<T> callable) {
    this.storageLayer.beginTransaction();
    try {
      T result = callable.call();
      this.storageLayer.setTransactionSuccessful();
      return result;
    } catch (Throwable e) {
      logger.error("Caught Throwable.", e);
      throw new RuntimeException(e);
    } finally {
      this.storageLayer.endTransaction();
    }
  }

  private void doPruneCheckAfterServerUpdate() {
    serverCacheUpdatesSinceLastPruneCheck++;
    if (cachePolicy.shouldCheckCacheSize(serverCacheUpdatesSinceLastPruneCheck)) {
      if (logger.logsDebug()) {
        logger.debug("Reached prune check threshold.");
      }
      serverCacheUpdatesSinceLastPruneCheck = 0;
      boolean canPrune = true;
      long cacheSize = storageLayer.serverCacheEstimatedSizeInBytes();
      if (logger.logsDebug()) {
        logger.debug("Cache size: " + cacheSize);
      }
      while (canPrune
          && cachePolicy.shouldPrune(cacheSize, trackedQueryManager.countOfPrunableQueries())) {
        PruneForest pruneForest = this.trackedQueryManager.pruneOldQueries(cachePolicy);
        if (pruneForest.prunesAnything()) {
          this.storageLayer.pruneCache(Path.getEmptyPath(), pruneForest);
        } else {
          canPrune = false;
        }
        cacheSize = storageLayer.serverCacheEstimatedSizeInBytes();
        if (logger.logsDebug()) {
          logger.debug("Cache size after prune: " + cacheSize);
        }
      }
    }
  }
}
