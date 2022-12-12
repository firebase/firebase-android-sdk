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

import androidx.annotation.Nullable;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.local.OverlayedDocument;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.SnapshotVersion;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

  /** The unique ID of this mutation batch. */
  private final int batchId;

  /** The original write time of this mutation. */
  private final Timestamp localWriteTime;

  /**
   * Mutations that are used to populate the base values when this mutation is applied locally. This
   * can be used to locally overwrite values that are persisted in the remote document cache. Base
   * mutations are never sent to the backend.
   */
  private final List<Mutation> baseMutations;

  /**
   * The user-provided mutations in this mutation batch. User-provided mutations are applied both
   * locally and remotely on the backend.
   */
  private final List<Mutation> mutations;

  public MutationBatch(
      int batchId,
      Timestamp localWriteTime,
      List<Mutation> baseMutations,
      List<Mutation> mutations) {
    hardAssert(!mutations.isEmpty(), "Cannot create an empty mutation batch");
    this.batchId = batchId;
    this.localWriteTime = localWriteTime;
    this.baseMutations = baseMutations;
    this.mutations = mutations;
  }

  /**
   * Applies all the mutations in this MutationBatch to the specified document to create a new
   * remote document.
   *
   * @param document The document to apply mutations to.
   * @param batchResult The result of applying the MutationBatch to the backend.
   */
  public void applyToRemoteDocument(MutableDocument document, MutationBatchResult batchResult) {
    int size = mutations.size();
    List<MutationResult> mutationResults = batchResult.getMutationResults();
    hardAssert(
        mutationResults.size() == size,
        "Mismatch between mutations length (%d) and results length (%d)",
        size,
        mutationResults.size());

    for (int i = 0; i < size; i++) {
      Mutation mutation = mutations.get(i);
      if (mutation.getKey().equals(document.getKey())) {
        MutationResult mutationResult = mutationResults.get(i);
        mutation.applyToRemoteDocument(document, mutationResult);
      }
    }
  }

  /**
   * Computes the local view of a document given all the mutations in this batch.
   *
   * @param mutatedFields The document to be mutated.
   * @param mutatedFields Fields that are already mutated before applying the current one.
   * @return An {@link FieldMask} representing all the fields that are mutated.
   */
  public FieldMask applyToLocalView(MutableDocument document, @Nullable FieldMask mutatedFields) {
    // First, apply the base state. This allows us to apply non-idempotent transform against a
    // consistent set of values.
    for (int i = 0; i < baseMutations.size(); i++) {
      Mutation mutation = baseMutations.get(i);
      if (mutation.getKey().equals(document.getKey())) {
        mutatedFields = mutation.applyToLocalView(document, mutatedFields, localWriteTime);
      }
    }

    // Second, apply all user-provided mutations.
    for (int i = 0; i < mutations.size(); i++) {
      Mutation mutation = mutations.get(i);
      if (mutation.getKey().equals(document.getKey())) {
        mutatedFields = mutation.applyToLocalView(document, mutatedFields, localWriteTime);
      }
    }

    return mutatedFields;
  }

  /**
   * Computes the local view for all provided documents given the mutations in this batch. Returns a
   * {@code DocumentKey} to {@code Mutation} map which can be used to replace all the mutation
   * applications.
   */
  public Map<DocumentKey, Mutation> applyToLocalDocumentSet(
      Map<DocumentKey, OverlayedDocument> documentMap,
      Set<DocumentKey> documentsWithoutRemoteVersion) {
    // TODO(mrschmidt): This implementation is O(n^2). If we iterate through the mutations first
    // (as done in `applyToLocalView(MutableDocument d)`), we can reduce the complexity to
    // O(n).
    Map<DocumentKey, Mutation> overlays = new HashMap<>();
    for (DocumentKey key : getKeys()) {
      // TODO(mutabledocuments): This method should take a map of MutableDocuments and we should
      // remove this cast.
      OverlayedDocument overlayedDocument = documentMap.get(key);
      MutableDocument document = (MutableDocument) overlayedDocument.getDocument();
      FieldMask mutatedFields = applyToLocalView(document, documentMap.get(key).getMutatedFields());
      // Set mutationFields to null if the document is only from local mutations, this creates
      // a Set(or Delete) mutation, instead of trying to create a patch mutation as the overlay.
      mutatedFields = documentsWithoutRemoteVersion.contains(key) ? null : mutatedFields;
      Mutation overlay = Mutation.calculateOverlayMutation(document, mutatedFields);
      if (overlay != null) {
        overlays.put(key, overlay);
      }

      if (!document.isValidDocument()) {
        document.convertToNoDocument(SnapshotVersion.NONE);
      }
    }

    return overlays;
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
        && baseMutations.equals(that.baseMutations)
        && mutations.equals(that.mutations);
  }

  @Override
  public int hashCode() {
    int result = batchId;
    result = 31 * result + localWriteTime.hashCode();
    result = 31 * result + baseMutations.hashCode();
    result = 31 * result + mutations.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "MutationBatch(batchId="
        + batchId
        + ", localWriteTime="
        + localWriteTime
        + ", baseMutations="
        + baseMutations
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

  /** @return The user-provided mutations in this mutation batch. */
  public List<Mutation> getMutations() {
    return mutations;
  }

  /**
   * @return The mutations that are used to populate the base values when this mutation batch is
   *     applied locally.
   */
  public List<Mutation> getBaseMutations() {
    return baseMutations;
  }
}
