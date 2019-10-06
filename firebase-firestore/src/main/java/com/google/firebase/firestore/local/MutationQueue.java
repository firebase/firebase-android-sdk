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

import androidx.annotation.Nullable;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.MutationBatch;
import com.google.protobuf.ByteString;
import java.util.List;

/** A queue of mutations to apply to the remote store. */
interface MutationQueue {
  /**
   * Starts the mutation queue, performing any initial reads that might be required to establish
   * invariants, etc.
   */
  void start();

  /** Returns true if this queue contains no mutation batches. */
  boolean isEmpty();

  /** Acknowledges the given batch. */
  void acknowledgeBatch(MutationBatch batch, ByteString streamToken);

  /** Returns the current stream token for this mutation queue. */
  ByteString getLastStreamToken();

  /** Sets the stream token for this mutation queue. */
  void setLastStreamToken(ByteString streamToken);

  /**
   * Creates a new mutation batch and adds it to this mutation queue.
   *
   * @param localWriteTime The original write time of this mutation.
   * @param baseMutations Mutations that are used to populate the base values when this mutation is
   *     applied locally. These mutations are used to locally overwrite values that are persisted in
   *     the remote document cache.
   * @param mutations The user-provided mutations in this mutation batch.
   */
  MutationBatch addMutationBatch(
      Timestamp localWriteTime, List<Mutation> baseMutations, List<Mutation> mutations);

  /** Loads the mutation batch with the given batchId. */
  @Nullable
  MutationBatch lookupMutationBatch(int batchId);

  /**
   * Returns the first unacknowledged mutation batch after the passed in batchId in the mutation
   * queue or null if empty.
   *
   * @param batchId The batch to search after, or {@link MutationBatch#UNKNOWN} for the first
   *     mutation in the queue.
   * @return the next mutation or null if there wasn't one.
   */
  @Nullable
  MutationBatch getNextMutationBatchAfterBatchId(int batchId);

  /**
   * @return The largest (latest) batch id in mutation queue for the current user that is pending
   *     server response, {@link MutationBatch#UNKNOWN} if the queue is empty.
   */
  int getHighestUnacknowledgedBatchId();

  /** Returns all mutation batches in the mutation queue. */
  // TODO: PERF: Current consumer only needs mutated keys; if we can provide that
  // cheaply, we should replace this.
  List<MutationBatch> getAllMutationBatches();

  /**
   * Finds all mutation batches that could @em possibly affect the given document key. Not all
   * mutations in a batch will necessarily affect the document key, so when looping through the
   * batch you'll need to check that the mutation itself matches the key.
   *
   * <p>Note that because of this requirement implementations are free to return mutation batches
   * that don't contain the document key at all if it's convenient.
   *
   * <p>Batches are guaranteed to be sorted by batch ID.
   */
  List<MutationBatch> getAllMutationBatchesAffectingDocumentKey(DocumentKey documentKey);

  /**
   * Finds all mutation batches that could @em possibly affect the given set of document keys. Not
   * all mutations in a batch will necessarily affect each key, so when looping through the batch
   * you'll need to check that the mutation itself matches the key.
   *
   * <p>Note that because of this requirement implementations are free to return mutation batches
   * that don't contain any of the document keys at all if it's convenient.
   *
   * <p>Batches are guaranteed to be sorted by batch ID.
   */
  List<MutationBatch> getAllMutationBatchesAffectingDocumentKeys(
      Iterable<DocumentKey> documentKeys);

  /**
   * Finds all mutation batches that could affect the results for the given query. Not all mutations
   * in a batch will necessarily affect the query, so when looping through the batch you'll need to
   * check that the mutation itself matches the query.
   *
   * <p>Note that because of this requirement implementations are free to return mutation batches
   * that don't match the query at all if it's convenient.
   *
   * <p>Batches are guaranteed to be sorted by batch ID.
   *
   * <p>NOTE: A PatchMutation does not need to include all fields in the query filter criteria in
   * order to be a match (but any fields it does contain do need to match).
   */
  List<MutationBatch> getAllMutationBatchesAffectingQuery(Query query);

  /**
   * Removes the given mutation batch from the queue. This is useful in two circumstances:
   *
   * <ul>
   *   <li>Removing applied mutations from the head of the queue
   *   <li>Removing rejected mutations from anywhere in the queue
   * </ul>
   */
  void removeMutationBatch(MutationBatch batch);

  /** Performs a consistency check, examining the mutation queue for any leaks, if possible. */
  void performConsistencyCheck();
}
