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

import com.google.firebase.firestore.model.DocumentKey;

/**
 * A ReferenceDelegate instance handles all of the hooks into the document-reference lifecycle. This
 * includes being added to a target, being removed from a target, being subject to mutation, and
 * being mutated by the user.
 *
 * <p>Different implementations may do different things with each of these events. Not every
 * implementation needs to do something with every lifecycle hook.
 *
 * <p>Implementations that care about sequence numbers are responsible for generating them and
 * making them available.
 */
interface ReferenceDelegate {
  /**
   * Registers a ReferenceSet of documents that should be considered 'referenced' and not eligible
   * for removal during garbage collection.
   */
  void setInMemoryPins(ReferenceSet inMemoryPins);

  /** Notify the delegate that the given document was added to a target. */
  void addReference(DocumentKey key);

  /** Notify the delegate that the given document was removed from a target. */
  void removeReference(DocumentKey key);

  /** Notify the delegate that a document is no longer being mutated by the user. */
  void removeMutationReference(DocumentKey key);

  /**
   * Notify the delegate that a target was removed. The delegate may, but is not obligated to,
   * actually delete the target and associated data.
   */
  void removeTarget(TargetData targetData);

  /** Notify the delegate that a limbo document was updated. */
  void updateLimboDocument(DocumentKey key);

  /** Returns the sequence number of the current transaction. Only valid during a transaction. */
  long getCurrentSequenceNumber();

  /** Lifecycle hook to notify the delegate that a transaction has started. */
  void onTransactionStarted();

  /** Lifecycle hook to notify the delegate that a transaction has committed. */
  void onTransactionCommitted();
}
