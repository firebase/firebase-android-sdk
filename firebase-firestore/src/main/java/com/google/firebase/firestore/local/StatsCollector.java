// Copyright 2019 Google LLC
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

/**
 * Collects the operation count from the persistence layer. Implementing classes can expose this
 * information to measure the efficiency of persistence operations.
 *
 * <p>The only consumer of the operation counts is currently the LocalStoreTestCase (via {@link
 * com.google.firebase.firestore.local.AccumulatingStatsCollector}). If you are not interested in
 * the stats, you can use `newNoOpStatsCollector()` to return an empty stats collector.
 */
abstract class StatsCollector {
  /** A stats collector that discards all operation counts. */
  static class NoOpStatsCollector extends StatsCollector {
    @Override
    void recordRowsRead(String tag, int count) {}

    @Override
    void recordRowsDeleted(String tag, int count) {}

    @Override
    void recordRowsWritten(String tag, int count) {}
  }

  static NoOpStatsCollector newNoOpStatsCollector() {
    return new NoOpStatsCollector();
  }

  /** Records the number of rows read for the given tag. */
  abstract void recordRowsRead(String tag, int count);

  /** Records the number of rows deleted for the given tag. */
  abstract void recordRowsDeleted(String tag, int count);

  /** Records the number of rows written for the given tag. */
  abstract void recordRowsWritten(String tag, int count);
}
