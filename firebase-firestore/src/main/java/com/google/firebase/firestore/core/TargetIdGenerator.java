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

/**
 * Generates monotonically increasing target IDs for sending targets to the watch stream.
 *
 * <p>The client constructs two generators, one for the query cache (via forTargetCache()), and one
 * for limbo documents (via forSyncEngine()). These two generators produce non-overlapping IDs (by
 * using even and odd IDs respectively).
 *
 * <p>By separating the target ID space, the query cache can generate target IDs that persist across
 * client restarts, while sync engine can independently generate in-memory target IDs that are
 * transient and can be reused after a restart.
 */
// TODO(mrschmidt): Explore removing this class in favor of generating these IDs directly in
// SyncEngine and LocalStore.
public class TargetIdGenerator {

  /**
   * Creates and returns the TargetIdGenerator for the local store.
   *
   * @return A shared instance of TargetIdGenerator.
   */
  public static TargetIdGenerator forTargetCache(int after) {
    TargetIdGenerator generator = new TargetIdGenerator(QUERY_CACHE_ID, after);
    // Make sure that the next call to `nextId()` returns the first value after 'after'.
    generator.nextId();
    return generator;
  }

  /**
   * Creates and returns the TargetIdGenerator for the sync engine.
   *
   * @return A shared instance of TargetIdGenerator.
   */
  public static TargetIdGenerator forSyncEngine() {
    // Sync engine assigns target IDs for limbo document detection.
    return new TargetIdGenerator(SYNC_ENGINE_ID, 1);
  }

  private static final int QUERY_CACHE_ID = 0;
  private static final int SYNC_ENGINE_ID = 1;

  private static final int RESERVED_BITS = 1;

  private int nextId;
  private int generatorId;

  /** Instantiates a new TargetIdGenerator, using the seed as the first target ID to return. */
  TargetIdGenerator(int generatorId, int seed) {
    hardAssert(
        (generatorId & RESERVED_BITS) == generatorId,
        "Generator ID %d contains more than %d reserved bits",
        generatorId,
        RESERVED_BITS);
    this.generatorId = generatorId;
    seek(seed);
  }

  private void seek(int targetId) {
    hardAssert(
        (targetId & RESERVED_BITS) == this.generatorId,
        "Cannot supply target ID from different generator ID");
    this.nextId = targetId;
  }

  /** @return the next id in the sequence */
  public int nextId() {
    int nextId = this.nextId;
    this.nextId += 1 << RESERVED_BITS;
    return nextId;
  }
}
