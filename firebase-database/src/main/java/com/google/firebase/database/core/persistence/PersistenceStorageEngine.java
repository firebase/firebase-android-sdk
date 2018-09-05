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

import com.google.firebase.database.core.CompoundWrite;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.UserWriteRecord;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.Node;
import java.util.List;
import java.util.Set;

/**
 * This class provides an interface to a persistent cache. The persistence cache persists user
 * writes, cached server data and the corresponding completeness tree. There exists one
 * PersistentCache per repo.
 */
public interface PersistenceStorageEngine {
  /**
   * Save a user overwrite
   *
   * @param path The path for this write
   * @param node The node for this write
   * @param writeId The write id that was used for this write
   */
  public void saveUserOverwrite(Path path, Node node, long writeId);

  /**
   * Save a user merge
   *
   * @param path The path for this merge
   * @param children The children for this merge
   * @param writeId The write id that was used for this merge
   */
  public void saveUserMerge(Path path, CompoundWrite children, long writeId);

  /**
   * Remove a write with the given write id.
   *
   * @param writeId The write id to remove
   */
  public void removeUserWrite(long writeId);

  /**
   * Return a list of all writes that were persisted
   *
   * @return The list of writes
   */
  public List<UserWriteRecord> loadUserWrites();

  /** Removes all user writes */
  public void removeAllUserWrites();

  /**
   * Loads all data at a path. It has no knowledge of whether the data is "complete" or not.
   *
   * @param path The path at which to load the node.
   * @return The node that was loaded.
   */
  public Node serverCache(Path path);

  /**
   * Overwrite the server cache at the given path with the given node.
   *
   * @param path The path to update
   * @param node The node to write to the cache.
   */
  public void overwriteServerCache(Path path, Node node);

  /**
   * Update the server cache at the given path with the given node, merging each child into the
   * cache.
   *
   * @param path The path to update
   * @param node The node to merge into the cache.
   */
  public void mergeIntoServerCache(Path path, Node node);

  /**
   * Update the server cache at the given path with the given children, merging each one into the
   * cache.
   *
   * @param path The path for this merge
   * @param children The children to update
   */
  public void mergeIntoServerCache(Path path, CompoundWrite children);

  public long serverCacheEstimatedSizeInBytes();

  public void saveTrackedQuery(TrackedQuery trackedQuery);

  public void deleteTrackedQuery(long trackedQueryId);

  public List<TrackedQuery> loadTrackedQueries();

  public void resetPreviouslyActiveTrackedQueries(long lastUse);

  public void saveTrackedQueryKeys(long trackedQueryId, Set<ChildKey> keys);

  public void updateTrackedQueryKeys(
      long trackedQueryId, Set<ChildKey> added, Set<ChildKey> removed);

  public Set<ChildKey> loadTrackedQueryKeys(long trackedQueryId);

  public Set<ChildKey> loadTrackedQueryKeys(Set<Long> trackedQueryIds);

  public void pruneCache(Path root, PruneForest pruneForest);

  public void beginTransaction();

  public void endTransaction();

  public void setTransactionSuccessful();

  public void close();
}
