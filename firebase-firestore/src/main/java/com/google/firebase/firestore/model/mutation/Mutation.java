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
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MaybeDocument;
import com.google.firebase.firestore.model.ObjectValue;
import com.google.firebase.firestore.model.SnapshotVersion;

/**
 * Represents a Mutation of a document. Different subclasses of Mutation will perform different
 * kinds of changes to a base document. For example, a SetMutation replaces the value of a document
 * and a DeleteMutation deletes a document.
 *
 * <p>In addition to the value of the document mutations also operate on the version. For local
 * mutations (mutations that haven't been committed yet), we preserve the existing version for Set,
 * Patch, and Transform mutations. For local deletes, we reset the version to 0.
 *
 * <p>Here's the expected transition table.
 *
 * <table>
 * <th><td>MUTATION</td><td>APPLIED TO</td><td>RESULTS IN</td></th>
 * <tr><td>SetMutation</td><td>Document(v3)</td><td>Document(v3)</td></tr>
 * <tr><td>SetMutation</td><td>NoDocument(v3)</td><td>Document(v0)</td></tr>
 * <tr><td>SetMutation</td><td>null</td><td>Document(v0)</td></tr>
 * <tr><td>PatchMutation</td><td>Document(v3)</td><td>Document(v3)</td></tr>
 * <tr><td>PatchMutation</td><td>NoDocument(v3)</td><td>NoDocument(v3)</td></tr>
 * <tr><td>PatchMutation</td><td>null</td><td>null</td></tr>
 * <tr><td>TransformMutation</td><td>Document(v3)</td><td>Document(v3)</td></tr>
 * <tr><td>TransformMutation</td><td>NoDocument(v3)</td><td>NoDocument(v3)</td></tr>
 * <tr><td>TransformMutation</td><td>null</td><td>null</td></tr>
 * <tr><td>DeleteMutation</td><td>Document(v3)</td><td>NoDocument(v0)</td></tr>
 * <tr><td>DeleteMutation</td><td>NoDocument(v3)</td><td>NoDocument(v0)</td></tr>
 * <tr><td>DeleteMutation</td><td>null</td><td>NoDocument(v0)</td></tr>
 * </table>
 *
 * For acknowledged mutations, we use the updateTime of the WriteResponse as the resulting version
 * for Set, Patch, and Transform mutations. As deletes have no explicit update time, we use the
 * commitTime of the WriteResponse for acknowledged deletes.
 *
 * <p>If a mutation is acknowledged by the backend but fails the precondition check locally, we
 * return an `UnknownDocument` and rely on Watch to send us the updated version.
 *
 * <p>Note that TransformMutations don't create Documents (in the case of being applied to a
 * NoDocument), even though they would on the backend. This is because the client always combines
 * the TransformMutation with a SetMutation or PatchMutation and we only want to apply the transform
 * if the prior mutation resulted in a Document (always true for a SetMutation, but not necessarily
 * for an PatchMutation).
 */
public abstract class Mutation {
  private final DocumentKey key;

  /** The precondition for the mutation. */
  private final Precondition precondition;

  Mutation(DocumentKey key, Precondition precondition) {
    this.key = key;
    this.precondition = precondition;
  }

  public DocumentKey getKey() {
    return key;
  }

  public Precondition getPrecondition() {
    return precondition;
  }

  /**
   * Applies this mutation to the given MaybeDocument for the purposes of computing a new remote
   * document. If the input document doesn't match the expected state (e.g. it is null or outdated),
   * an `UnknownDocument` can be returned.
   *
   * @param maybeDoc The document to mutate. The input document can be null if the client has no
   *     knowledge of the pre-mutation state of the document.
   * @param mutationResult The result of applying the mutation from the backend.
   * @return The mutated document. The returned document may be an UnknownDocument if the mutation
   *     could not be applied to the locally cached base document.
   */
  public abstract MaybeDocument applyToRemoteDocument(
      @Nullable MaybeDocument maybeDoc, MutationResult mutationResult);

  /**
   * Applies this mutation to the given MaybeDocument for the purposes of computing the new local
   * view of a document. Both the input and returned documents can be null.
   *
   * @param maybeDoc The document to mutate. The input document can be null if the client has no
   *     knowledge of the pre-mutation state of the document.
   * @param baseDoc The state of the document prior to this mutation batch. The input document can
   *     be null if the client has no knowledge of the pre-mutation state of the document.
   * @param localWriteTime A timestamp indicating the local write time of the batch this mutation is
   *     a part of.
   * @return The mutated document. The returned document may be null, but only if maybeDoc was null
   *     and the mutation would not create a new document.
   */
  @Nullable
  public abstract MaybeDocument applyToLocalView(
      @Nullable MaybeDocument maybeDoc, @Nullable MaybeDocument baseDoc, Timestamp localWriteTime);

  /**
   * If applicable, returns the base value to persist with this mutation. If a base value is
   * provided, the mutation is always applied to this base value, even if document has already been
   * updated.
   *
   * <p>The base value is a sparse object that consists of only the document fields for which this
   * mutation contains a non-idempotent transformation (e.g. a numeric increment). The provided
   * value guarantees consistent behavior for non-idempotent transforms and allow us to return the
   * same latency-compensated value even if the backend has already applied the mutation. The base
   * value is null for idempotent mutations, as they can be re-played even if the backend has
   * already applied them.
   *
   * @return a base value to store along with the mutation, or null for idempotent mutations.
   */
  @Nullable
  public abstract ObjectValue extractBaseValue(@Nullable MaybeDocument maybeDoc);

  /** Helper for derived classes to implement .equals(). */
  boolean hasSameKeyAndPrecondition(Mutation other) {
    return key.equals(other.key) && precondition.equals(other.precondition);
  }

  /** Helper for derived classes to implement .hashCode(). */
  int keyAndPreconditionHashCode() {
    return getKey().hashCode() * 31 + precondition.hashCode();
  }

  /** Helper for derived classes to implement .toString(). */
  String keyAndPreconditionToString() {
    return "key=" + key + ", precondition=" + precondition;
  }

  void verifyKeyMatches(@Nullable MaybeDocument maybeDoc) {
    if (maybeDoc != null) {
      hardAssert(
          maybeDoc.getKey().equals(getKey()),
          "Can only apply a mutation to a document with the same key");
    }
  }

  /**
   * Returns the version from the given document for use as the result of a mutation. Mutations are
   * defined to return the version of the base document only if it is an existing document. Deleted
   * and unknown documents have a post-mutation version of {@code SnapshotVersion.NONE}.
   */
  static SnapshotVersion getPostMutationVersion(@Nullable MaybeDocument maybeDoc) {
    if (maybeDoc instanceof Document) {
      return maybeDoc.getVersion();
    } else {
      return SnapshotVersion.NONE;
    }
  }
}
