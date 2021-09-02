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

import com.google.firebase.firestore.index.IndexEntry;
import javax.annotation.Nullable;

/**
 * Persistence layers intending to perform index backfills should implement this interface. This
 * interface defines the operations that the Index Backfiller needs from the persistence layer.
 */
public interface IndexBackfillerDelegate {
  /** Access to the underlying LRU Garbage collector instance. */
  IndexBackfiller getIndexBackfiller();

  /** Writes the specified indexes to the persistence layer. */
  void addIndexEntry();

  /** Removes the specified indexes from the persistence layer. */
  void removeIndexEntry();

  @Nullable
  IndexEntry getIndexEntry(int indexId);
}
