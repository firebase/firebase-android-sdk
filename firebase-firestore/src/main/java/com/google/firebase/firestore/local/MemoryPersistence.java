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

import static com.google.firebase.firestore.util.Assert.hardAssert;

import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.util.Supplier;
import java.util.HashMap;
import java.util.Map;

/**
 * An in-memory implementation of the Persistence interface. Values are stored only in RAM and are
 * never persisted to any durable storage.
 */
public final class MemoryPersistence extends Persistence {

  // The persistence objects backing MemoryPersistence are retained here to make it easier to write
  // tests affecting both the in-memory and SQLite-backed persistence layers. Tests can create a new
  // LocalStore wrapping this Persistence instance and this will make the in-memory persistence
  // layer behave as if it were actually persisting values.
  private final Map<User, MemoryMutationQueue> mutationQueues;
  private final Map<User, MemoryDocumentOverlayCache> overlays;
  private final MemoryIndexManager indexManager;
  private final MemoryTargetCache targetCache;
  private final MemoryBundleCache bundleCache;
  private final MemoryRemoteDocumentCache remoteDocumentCache;
  private ReferenceDelegate referenceDelegate;

  private boolean started;

  public static MemoryPersistence createEagerGcMemoryPersistence() {
    MemoryPersistence persistence = new MemoryPersistence();
    persistence.setReferenceDelegate(new MemoryEagerReferenceDelegate(persistence));
    return persistence;
  }

  public static MemoryPersistence createLruGcMemoryPersistence(
      LruGarbageCollector.Params params, LocalSerializer serializer) {
    MemoryPersistence persistence = new MemoryPersistence();
    persistence.setReferenceDelegate(
        new MemoryLruReferenceDelegate(persistence, params, serializer));
    return persistence;
  }

  /** Use static helpers to instantiate */
  private MemoryPersistence() {
    mutationQueues = new HashMap<>();
    indexManager = new MemoryIndexManager();
    targetCache = new MemoryTargetCache(this);
    bundleCache = new MemoryBundleCache();
    remoteDocumentCache = new MemoryRemoteDocumentCache();
    overlays = new HashMap<>();
  }

  @Override
  public void start() {
    hardAssert(!started, "MemoryPersistence double-started!");
    started = true;
  }

  @Override
  public void shutdown() {
    // TODO: This assertion seems problematic, since we may attempt shutdown in the finally
    // block after failing to initialize.
    hardAssert(started, "MemoryPersistence shutdown without start");
    started = false;
  }

  @Override
  public boolean isStarted() {
    return started;
  }

  @Override
  public ReferenceDelegate getReferenceDelegate() {
    return referenceDelegate;
  }

  private void setReferenceDelegate(ReferenceDelegate delegate) {
    referenceDelegate = delegate;
  }

  @Override
  MutationQueue getMutationQueue(User user, IndexManager indexManager) {
    MemoryMutationQueue queue = mutationQueues.get(user);
    if (queue == null) {
      queue = new MemoryMutationQueue(this, user);
      mutationQueues.put(user, queue);
    }
    return queue;
  }

  Iterable<MemoryMutationQueue> getMutationQueues() {
    return mutationQueues.values();
  }

  @Override
  MemoryTargetCache getTargetCache() {
    return targetCache;
  }

  @Override
  MemoryRemoteDocumentCache getRemoteDocumentCache() {
    return remoteDocumentCache;
  }

  @Override
  MemoryIndexManager getIndexManager(User user) {
    // We do not currently support indices for memory persistence, so we can return the same shared
    // instance of the memory index manager.
    return indexManager;
  }

  @Override
  BundleCache getBundleCache() {
    return bundleCache;
  }

  @Override
  DocumentOverlayCache getDocumentOverlayCache(User user) {
    MemoryDocumentOverlayCache overlay = overlays.get(user);
    if (overlay == null) {
      overlay = new MemoryDocumentOverlayCache();
      overlays.put(user, overlay);
    }
    return overlay;
  }

  @Override
  OverlayMigrationManager getOverlayMigrationManager() {
    return new MemoryOverlayMigrationManager();
  }

  @Override
  void runTransaction(String action, Runnable operation) {
    referenceDelegate.onTransactionStarted();
    try {
      operation.run();
    } finally {
      referenceDelegate.onTransactionCommitted();
    }
  }

  @Override
  <T> T runTransaction(String action, Supplier<T> operation) {
    referenceDelegate.onTransactionStarted();
    T result;
    try {
      result = operation.get();
    } finally {
      referenceDelegate.onTransactionCommitted();
    }
    return result;
  }
}
