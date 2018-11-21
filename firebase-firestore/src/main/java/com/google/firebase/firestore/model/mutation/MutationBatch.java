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

package com.google.firebase.firestore.model.mutation;

import static com.google.firebase.firestore.util.Assert.hardAssert;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MaybeDocument;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * A batch of mutations that will be sent as one unit to the backend. Batches can be marked as a
 * tombstone if the mutation queue does not remove them immediately. When a batch is a tombstone it
 * has no mutations.
 */
public final class MutationBatch {

  /**
   * A batch ID that was searched for and not found or a batch ID value known to be before all known
   * batches.
   *
   * <p>Batch ID values from the local store are non-negative so this value is before all batches.
   */
  public static final int UNKNOWN = -1;

  private final int batchId;
  private final Timestamp localWriteTime;
  private final List<Mutation> mutations;

  public MutationBatch(int batchId, Timestamp localWriteTime, List<Mutation> mutations) {
    hardAssert(!mutations.isEmpty(), "Cannot create an empty mutation batch");
    this.batchId = batchId;
    this.localWriteTime = localWriteTime;
    this.mutations = mutations;
  }

  /**
   * Applies all the mutations in this MutationBatch to the specified document to create a new
   * remote document.
   *
   * @param documentKey The key of the document to apply mutations to.
   * @param maybeDoc The document to apply mutations to.
   * @param batchResult The result of applying the MutationBatch to the backend.
   */
  @Nullable
  public MaybeDocument applyToRemoteDocument(
      DocumentKey documentKey, @Nullable MaybeDocument maybeDoc, MutationBatchResult batchResult) {
    if (maybeDoc != null) {
      hardAssert(
          maybeDoc.getKey().equals(documentKey),
          "applyToRemoteDocument: key %s doesn't match maybeDoc key %s",
          documentKey,
          maybeDoc.getKey());
    }

    int size = mutations.size();
    List<MutationResult> mutationResults = batchResult.getMutationResults();
    hardAssert(
        mutationResults.size() == size,
        "Mismatch between mutations length (%d) and results length (%d)",
        size,
        mutationResults.size());

    for (int i = 0; i < size; i++) {
      Mutation mutation = mutations.get(i);
      if (mutation.getKey().equals(documentKey)) {
        MutationResult mutationResult = mutationResults.get(i);
        maybeDoc = mutation.applyToRemoteDocument(maybeDoc, mutationResult);
      }
    }
    return maybeDoc;
  }

  /** Computes the local view of a document given all the mutations in this batch. */
  @Nullable
  public MaybeDocument applyToLocalView(DocumentKey documentKey, @Nullable MaybeDocument maybeDoc) {
    if (maybeDoc != null) {
      hardAssert(
          maybeDoc.getKey().equals(documentKey),
          "applyToRemoteDocument: key %s doesn't match maybeDoc key %s",
          documentKey,
          maybeDoc.getKey());
    }

    MaybeDocument baseDoc = maybeDoc;

    for (int i = 0; i < mutations.size(); i++) {
      Mutation mutation = mutations.get(i);
      if (mutation.getKey().equals(documentKey)) {
        maybeDoc = mutation.applyToLocalView(maybeDoc, baseDoc, localWriteTime);
      }
    }
    return maybeDoc;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    MutationBatch that = (MutationBatch) o;
    return batchId == that.batchId
        && localWriteTime.equals(that.localWriteTime)
        && mutations.equals(that.mutations);
  }

  @Override
  public int hashCode() {
    int result = batchId;
    result = 31 * result + localWriteTime.hashCode();
    result = 31 * result + mutations.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "MutationBatch(batchId="
        + batchId
        + ", localWriteTime="
        + localWriteTime
        + ", mutations="
        + mutations
        + ')';
  }

  /** Returns the set of unique keys referenced by all mutations in the batch. */
  public Set<DocumentKey> getKeys() {
    HashSet<DocumentKey> set = new HashSet<>();
    for (Mutation mutation : mutations) {
      set.add(mutation.getKey());
    }
    return set;
  }

  public int getBatchId() {
    return batchId;
  }

  /**
   * Returns the local time at which the mutation batch was created / written; used to assign local
   * times to server timestamps, etc.
   */
  public Timestamp getLocalWriteTime() {
    return localWriteTime;
  }

  public List<Mutation> getMutations() {
    return mutations;
  }
}
