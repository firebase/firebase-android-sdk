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
import com.google.firebase.firestore.core.ListenSequence;
import java.util.Comparator;
import java.util.PriorityQueue;

/** Implements the steps for LRU garbage collection. */
class LruGarbageCollector {
  private final LruDelegate delegate;

  LruGarbageCollector(LruDelegate delegate) {
    this.delegate = delegate;
  }

  /** Given a percentile of target to collect, returns the number of targets to collect. */
  int calculateQueryCount(int percentile) {
    long targetCount = delegate.getTargetCount();
    return (int) ((percentile / 100.0f) * targetCount);
  }

  /**
   * Used to calculate the nth sequence number. Keeps a rolling buffer of the lowest n values passed
   * to addElement, and finally reports the largest of them in getMaxValue().
   */
  private static class RollingSequenceNumberBuffer {
    // Invert the comparison because we want to keep the smallest values.
    private static final Comparator<Long> COMPARATOR = (Long a, Long b) -> b.compareTo(a);
    private final PriorityQueue<Long> queue;
    private final int maxElements;

    RollingSequenceNumberBuffer(int count) {
      this.maxElements = count;
      this.queue = new PriorityQueue<>(count, COMPARATOR);
    }

    void addElement(Long sequenceNumber) {
      if (queue.size() < maxElements) {
        queue.add(sequenceNumber);
      } else {
        Long highestValue = queue.peek();
        if (sequenceNumber < highestValue) {
          queue.poll();
          queue.add(sequenceNumber);
        }
      }
    }

    long getMaxValue() {
      return queue.peek();
    }
  }

  /** Returns the nth sequence number, counting in order from the smallest. */
  long nthSequenceNumber(int count) {
    if (count == 0) {
      return ListenSequence.INVALID;
    }
    RollingSequenceNumberBuffer buffer = new RollingSequenceNumberBuffer(count);
    delegate.forEachTarget(queryData -> buffer.addElement(queryData.getSequenceNumber()));
    delegate.forEachOrphanedDocumentSequenceNumber(buffer::addElement);
    return buffer.getMaxValue();
  }

  /**
   * Removes targets with a sequence number equal to or less than the given upper bound, and removes
   * document associations with those targets.
   */
  int removeTargets(long upperBound, SparseArray<?> activeTargetIds) {
    return delegate.removeTargets(upperBound, activeTargetIds);
  }

  /**
   * Removes documents that have a sequence number equal to or less than the upper bound and are not
   * otherwise pinned.
   */
  int removeOrphanedDocuments(long upperBound) {
    return delegate.removeOrphanedDocuments(upperBound);
  }
}
