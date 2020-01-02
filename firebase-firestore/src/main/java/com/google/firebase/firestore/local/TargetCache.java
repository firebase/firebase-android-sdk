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

package com.google.firebase.firestore.local;

import androidx.annotation.Nullable;
import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.core.Target;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.util.Consumer;

/**
 * Represents cached targets received from the remote backend. This contains both a mapping between
 * targets and the documents that matched them according to the server, but also metadata about the
 * targets.
 *
 * <p>The cache is keyed by {@link Target} and entries in the cache are {@link TargetData}
 * instances.
 */
interface TargetCache {
  /**
   * Returns the highest target ID of any query in the cache. Typically called during startup to
   * seed a target ID generator and avoid collisions with existing queries. If there are no queries
   * in the cache, returns zero.
   */
  int getHighestTargetId();

  /**
   * Returns the highest sequence number that the cache has seen. This includes targets that have
   * been persisted in a previous run of the client.
   */
  long getHighestListenSequenceNumber();

  /** Returns the number of targets in the cache. */
  long getTargetCount();

  /** Call the consumer for each target in the cache. */
  void forEachTarget(Consumer<TargetData> consumer);

  /**
   * A global snapshot version representing the last consistent snapshot we received from the
   * backend. This is monotonically increasing and any snapshots received from the backend prior to
   * this version (e.g. for targets resumed with a resume_token) should be suppressed (buffered)
   * until the backend has caught up to this snapshot version again. This prevents our cache from
   * ever going backwards in time.
   *
   * <p>This is updated whenever our we get a TargetChange with a read_time and empty target_ids.
   */
  SnapshotVersion getLastRemoteSnapshotVersion();

  /**
   * Set the snapshot version representing the last consistent snapshot received from the backend.
   * (see getLastRemoteSnapshotVersion() for more details).
   *
   * @param snapshotVersion The new snapshot version.
   */
  void setLastRemoteSnapshotVersion(SnapshotVersion snapshotVersion);

  /**
   * Adds an entry in the cache. This entry should not already exist.
   *
   * <p>The cache key is extracted from {@link TargetData#getTarget}.
   *
   * @param targetData A TargetData instance to put in the cache.
   */
  void addTargetData(TargetData targetData);

  /**
   * Replaces an entry in the cache. An entry with the same key should already exist.
   *
   * <p>The cache key is extracted from {@link TargetData#getTarget()}.
   *
   * @param targetData A TargetData to replace an existing entry in the cache.
   */
  void updateTargetData(TargetData targetData);

  /**
   * Removes the cached entry for the given query data. This entry should already exist in the
   * cache. This method exists in the interface for testing purposes. Production code should instead
   * call {@link ReferenceDelegate#removeTarget(TargetData)}.
   */
  void removeTargetData(TargetData targetData);

  /**
   * Looks up a TargetData entry in the cache.
   *
   * @param target The target corresponding to the entry to look up.
   * @return The cached TargetData entry, or null if the cache has no entry for the query.
   */
  @Nullable
  TargetData getTargetData(Target target);

  /** Adds the given document keys to cached query results of the given target ID. */
  void addMatchingKeys(ImmutableSortedSet<DocumentKey> keys, int targetId);

  /** Removes the given document keys from the cached query results of the given target ID. */
  void removeMatchingKeys(ImmutableSortedSet<DocumentKey> keys, int targetId);

  ImmutableSortedSet<DocumentKey> getMatchingKeysForTargetId(int targetId);

  /** @return True if the document is part of any target */
  boolean containsKey(DocumentKey key);
}
