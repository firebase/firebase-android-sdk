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

import android.util.SparseArray;
import com.google.firebase.firestore.util.Consumer;

/**
 * Persistence layers intending to use LRU Garbage collection should implement this interface. This
 * interface defines the operations that the LRU garbage collector needs from the persistence layer.
 */
public interface LruDelegate {

  /** Enumerates all the targets in the TargetCache. */
  void forEachTarget(Consumer<TargetData> consumer);

  long getSequenceNumberCount();

  /** Enumerates sequence numbers for documents not associated with a target. */
  void forEachOrphanedDocumentSequenceNumber(Consumer<Long> consumer);

  /**
   * Removes all targets that have a sequence number less than or equal to `upperBound`, and are not
   * present in the `activeTargetIds` set.
   *
   * @return the number of targets removed.
   */
  int removeTargets(long upperBound, SparseArray<?> activeTargetIds);

  /**
   * Removes all unreferenced documents from the cache that have a sequence number less than or
   * equal to the given sequence number.
   *
   * @return the number of documents removed.
   */
  int removeOrphanedDocuments(long upperBound);

  /** Access to the underlying LRU Garbage collector instance. */
  LruGarbageCollector getGarbageCollector();

  /** Return the size of the cache in bytes. */
  long getByteSize();
}
