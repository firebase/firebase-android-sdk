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

import com.google.firebase.firestore.core.ListenSequence;
import com.google.firebase.firestore.model.DocumentKey;
import java.util.HashSet;
import java.util.Set;

/** Provides eager garbage collection for MemoryPersistence. */
class MemoryEagerReferenceDelegate implements ReferenceDelegate {
  private ReferenceSet inMemoryPins;
  private final MemoryPersistence persistence;
  private Set<DocumentKey> orphanedDocuments;

  MemoryEagerReferenceDelegate(MemoryPersistence persistence) {
    this.persistence = persistence;
  }

  @Override
  public long getCurrentSequenceNumber() {
    return ListenSequence.INVALID;
  }

  @Override
  public void setInMemoryPins(ReferenceSet inMemoryPins) {
    this.inMemoryPins = inMemoryPins;
  }

  @Override
  public void addReference(DocumentKey key) {
    orphanedDocuments.remove(key);
  }

  @Override
  public void removeReference(DocumentKey key) {
    orphanedDocuments.add(key);
  }

  @Override
  public void removeMutationReference(DocumentKey key) {
    orphanedDocuments.add(key);
  }

  @Override
  public void removeTarget(TargetData targetData) {
    MemoryTargetCache targetCache = persistence.getTargetCache();
    for (DocumentKey key : targetCache.getMatchingKeysForTargetId(targetData.getTargetId())) {
      orphanedDocuments.add(key);
    }
    targetCache.removeTargetData(targetData);
  }

  @Override
  public void onTransactionStarted() {
    orphanedDocuments = new HashSet<>();
  }

  /** In eager garbage collection, collection is run on transaction commit. */
  @Override
  public void onTransactionCommitted() {
    MemoryRemoteDocumentCache remoteDocuments = persistence.getRemoteDocumentCache();
    for (DocumentKey key : orphanedDocuments) {
      if (!isReferenced(key)) {
        remoteDocuments.remove(key);
      }
    }
    orphanedDocuments = null;
  }

  @Override
  public void updateLimboDocument(DocumentKey key) {
    if (isReferenced(key)) {
      orphanedDocuments.remove(key);
    } else {
      orphanedDocuments.add(key);
    }
  }

  private boolean mutationQueuesContainKey(DocumentKey key) {
    for (MemoryMutationQueue queue : persistence.getMutationQueues()) {
      if (queue.containsKey(key)) {
        return true;
      }
    }
    return false;
  }

  /** Returns true if the given document is referenced by anything. */
  private boolean isReferenced(DocumentKey key) {
    if (persistence.getTargetCache().containsKey(key)) {
      return true;
    }

    if (mutationQueuesContainKey(key)) {
      return true;
    }

    if (inMemoryPins != null && inMemoryPins.containsKey(key)) {
      return true;
    }

    return false;
  }
}
