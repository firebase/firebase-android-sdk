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

import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.util.Supplier;

/**
 * Persistence is the lowest-level shared interface to persistent storage in Firestore.
 *
 * <p>Persistence is used to create MutationQueue and RemoteDocumentCache instances backed by
 * persistence (which might be in-memory or SQLite).
 *
 * <p>Persistence also exposes an API to run transactions against the backing store. All read and
 * write operations must be wrapped in a transaction. Implementations of Persistence only need to
 * guarantee that writes made against the transaction are not made to durable storage until the
 * transaction commits. Since memory-only storage components do not alter durable storage, they are
 * free to ignore the transaction.
 *
 * <p>This contract is enough to allow the LocalStore be be written independently of whether or not
 * the stored state actually is durably persisted. If persistent storage is enabled, writes are
 * grouped together to avoid inconsistent state that could cause crashes.
 *
 * <p>Concretely, when persistent storage is enabled, the persistent versions of MutationQueue,
 * RemoteDocumentCache, and others (the mutators) will defer their writes into a transaction. Once
 * the local store has completed one logical operation, it commits the transaction.
 *
 * <p>When persistent storage is disabled, the non-persistent versions of the mutators ignore the
 * transaction. This short-cut is allowed because memory-only storage leaves no state so it cannot
 * be inconsistent.
 *
 * <p>This simplifies the implementations of the mutators and allows memory-only implementations to
 * supplement the persistent ones without requiring any special dual-store implementation of
 * Persistence. The cost is that the LocalStore needs to be slightly careful about the order of its
 * reads and writes in order to avoid relying on being able to read back uncommitted writes.
 */
public abstract class Persistence {
  static final String TAG = Persistence.class.getSimpleName();

  /** Temporary setting for enabling indexing-specific code paths while in development. */
  // TODO: Remove this.
  public static boolean INDEXING_SUPPORT_ENABLED = false;

  // Local subclasses only, please.
  Persistence() {}

  /**
   * Starts persistent storage, opening the database or similar.
   *
   * <p>Throws an exception if the database could not be opened.
   */
  public abstract void start();

  /** Releases any resources held during eager shutdown. */
  public abstract void shutdown();

  public abstract boolean isStarted();

  abstract ReferenceDelegate getReferenceDelegate();

  /**
   * Returns a MutationQueue representing the persisted mutations for the given user.
   *
   * <p>Note: The implementation is free to return the same instance every time this is called for a
   * given user. In particular, the memory-backed implementation does this to emulate the persisted
   * implementation to the extent possible (e.g. in the case of uid switching from
   * sally=>jack=>sally, sally's mutation queue will be preserved).
   */
  abstract MutationQueue getMutationQueue(User user);

  /** Creates a TargetCache representing the persisted cache of queries. */
  abstract TargetCache getTargetCache();

  /** Creates a RemoteDocumentCache representing the persisted cache of remote documents. */
  abstract RemoteDocumentCache getRemoteDocumentCache();

  /** Creates an IndexManager that manages our persisted query indexes. */
  abstract IndexManager getIndexManager();

  /**
   * Performs an operation inside a persistence transaction. Any reads or writes against persistence
   * must be performed within a transaction. Writes will be committed atomically once the
   * transaction completes.
   *
   * @param action A description of the action performed by this transaction, used for logging.
   * @param operation The operation to run inside a transaction.
   */
  abstract void runTransaction(String action, Runnable operation);

  /**
   * Performs an operation inside a persistence transaction. Any reads or writes against persistence
   * must be performed within a transaction. Writes will be committed atomically once the
   * transaction completes.
   *
   * @param action A description of the action performed by this transaction, used for logging.
   * @param operation The operation to run inside a transaction.
   * @return The value returned from the operation.
   */
  abstract <T> T runTransaction(String action, Supplier<T> operation);
}
