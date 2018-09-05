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

public class LRUCachePolicy implements CachePolicy {
  private static final long SERVER_UPDATES_BETWEEN_CACHE_SIZE_CHECKS = 1000;
  private static final long MAX_NUMBER_OF_PRUNABLE_QUERIES_TO_KEEP = 1000;
  private static final float PERCENT_OF_QUERIES_TO_PRUNE_AT_ONCE =
      0.2f; // 20% at a time until we're below our max.

  public final long maxSizeBytes;

  public LRUCachePolicy(long maxSizeBytes) {
    this.maxSizeBytes = maxSizeBytes;
  }

  @Override
  public boolean shouldPrune(long currentSizeBytes, long countOfPrunableQueries) {
    return currentSizeBytes > maxSizeBytes
        || countOfPrunableQueries > MAX_NUMBER_OF_PRUNABLE_QUERIES_TO_KEEP;
  }

  @Override
  public boolean shouldCheckCacheSize(long serverUpdatesSinceLastCheck) {
    return serverUpdatesSinceLastCheck > SERVER_UPDATES_BETWEEN_CACHE_SIZE_CHECKS;
  }

  @Override
  public float getPercentOfQueriesToPruneAtOnce() {
    return PERCENT_OF_QUERIES_TO_PRUNE_AT_ONCE;
  }

  @Override
  public long getMaxNumberOfQueriesToKeep() {
    return MAX_NUMBER_OF_PRUNABLE_QUERIES_TO_KEEP;
  }
}
