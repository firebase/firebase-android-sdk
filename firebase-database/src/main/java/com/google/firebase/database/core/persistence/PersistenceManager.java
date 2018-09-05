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
import com.google.firebase.database.core.view.CacheNode;
import com.google.firebase.database.core.view.QuerySpec;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.Node;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

public interface PersistenceManager {

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

  /** Removes all writes */
  public void removeAllUserWrites();

  /**
   * @param path Path of user overwrite.
   * @param node Data of user write.
   */
  public void applyUserWriteToServerCache(Path path, Node node);

  /**
   * @param path Path of user merge.
   * @param merge Data of user merge.
   */
  public void applyUserWriteToServerCache(Path path, CompoundWrite merge);

  /**
   * Return a list of all writes that were persisted
   *
   * @return The list of writes
   */
  public List<UserWriteRecord> loadUserWrites();

  /**
   * Returns any cached node or children as a CacheNode. The query is *not* used to filter the node
   * but rather to determine if it can be considered complete.
   *
   * @param query The query at the path
   * @return The cached node or an empty CacheNode if no cache is available
   */
  public CacheNode serverCache(QuerySpec query);

  /**
   * Overwrite the server cache with the given node for a given query. The query is considered to be
   * complete after saving this node.
   *
   * @param query The query for which to apply this overwrite.
   * @param node The node to replace in the cache at the given path
   */
  public void updateServerCache(QuerySpec query, Node node);

  /**
   * Update the server cache at the given path with the given merge.
   *
   * <p>NOTE: This doesn't mark any queries complete, since the common case is that there's already
   * a complete query above this location.
   *
   * @param path The path for this merge
   * @param children The children to update
   */
  public void updateServerCache(Path path, CompoundWrite children);

  public void setQueryActive(QuerySpec query);

  public void setQueryInactive(QuerySpec query);

  public void setQueryComplete(QuerySpec query);

  public void setTrackedQueryKeys(QuerySpec query, Set<ChildKey> keys);

  public void updateTrackedQueryKeys(QuerySpec query, Set<ChildKey> added, Set<ChildKey> removed);

  public <T> T runInTransaction(Callable<T> callable);
}
