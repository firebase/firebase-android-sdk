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
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.MaybeDocument;
import com.google.firebase.firestore.model.ObjectValue;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firestore.v1.Value;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Mutation of a document. Different subclasses of Mutation will perform different
 * kinds of changes to a base document. For example, a SetMutation replaces the value of a document
 * and a DeleteMutation deletes a document.
 *
 * <p>In addition to the value of the document mutations also operate on the version. For local
 * mutations (mutations that haven't been committed yet), we preserve the existing version for Set
 * and Patch mutations. For local deletes, we reset the version to 0.
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
 * <tr><td>DeleteMutation</td><td>Document(v3)</td><td>NoDocument(v0)</td></tr>
 * <tr><td>DeleteMutation</td><td>NoDocument(v3)</td><td>NoDocument(v0)</td></tr>
 * <tr><td>DeleteMutation</td><td>null</td><td>NoDocument(v0)</td></tr>
 * </table>
 *
 * For acknowledged mutations, we use the updateTime of the WriteResponse as the resulting version
 * for Set and Patch mutations. As deletes have no explicit update time, we use the commitTime of
 * the WriteResponse for acknowledged deletes.
 *
 * <p>If a mutation is acknowledged by the backend but fails the precondition check locally, we
 * return an `UnknownDocument` and rely on Watch to send us the updated version.
 *
 * <p>Field transforms are used only with Patch and Set Mutations. We use the `updateTransforms`
 * field to store transforms, rather than the `transforms` message.
 */
public abstract class Mutation {
  private final DocumentKey key;

  /** The precondition for the mutation. */
  private final Precondition precondition;

  private final List<FieldTransform> fieldTransforms;

  Mutation(DocumentKey key, Precondition precondition) {
    this(key, precondition, new ArrayList<>());
  }

  Mutation(DocumentKey key, Precondition precondition, List<FieldTransform> fieldTransforms) {
    this.key = key;
    this.precondition = precondition;
    this.fieldTransforms = fieldTransforms;
  }

  public DocumentKey getKey() {
    return key;
  }

  public Precondition getPrecondition() {
    return precondition;
  }

  public List<FieldTransform> getFieldTransforms() {
    return fieldTransforms;
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

  /**
   * Creates a list of "transform results" (a transform result is a field value representing the
   * result of applying a transform) for use after a mutation containing transforms has been
   * acknowledged by the server.
   *
   * @param baseDoc The document prior to applying this mutation batch.
   * @param serverTransformResults The transform results received by the server.
   * @return The transform results list.
   */
  protected List<Value> serverTransformResults(
      @Nullable MaybeDocument baseDoc, List<Value> serverTransformResults) {
    ArrayList<Value> transformResults = new ArrayList<>(fieldTransforms.size());
    hardAssert(
        fieldTransforms.size() == serverTransformResults.size(),
        "server transform count (%d) should match field transform count (%d)",
        serverTransformResults.size(),
        fieldTransforms.size());

    for (int i = 0; i < serverTransformResults.size(); i++) {
      FieldTransform fieldTransform = fieldTransforms.get(i);
      TransformOperation transform = fieldTransform.getOperation();

      Value previousValue = null;
      if (baseDoc instanceof Document) {
        previousValue = ((Document) baseDoc).getField(fieldTransform.getFieldPath());
      }

      transformResults.add(
          transform.applyToRemoteDocument(previousValue, serverTransformResults.get(i)));
    }
    return transformResults;
  }

  /**
   * Creates a list of "transform results" (a transform result is a field value representing the
   * result of applying a transform) for use when applying a transform locally.
   *
   * @param localWriteTime The local time of the mutation (used to generate ServerTimestampValues).
   * @param maybeDoc The current state of the document after applying all previous mutations.
   * @param baseDoc The document prior to applying this mutation batch.
   * @return The transform results list.
   */
  protected List<Value> localTransformResults(
      Timestamp localWriteTime, @Nullable MaybeDocument maybeDoc, @Nullable MaybeDocument baseDoc) {
    ArrayList<Value> transformResults = new ArrayList<>(fieldTransforms.size());
    for (FieldTransform fieldTransform : fieldTransforms) {
      TransformOperation transform = fieldTransform.getOperation();

      Value previousValue = null;
      if (maybeDoc instanceof Document) {
        previousValue = ((Document) maybeDoc).getField(fieldTransform.getFieldPath());
      }

      transformResults.add(transform.applyToLocalView(previousValue, localWriteTime));
    }
    return transformResults;
  }

  ObjectValue transformObject(ObjectValue objectValue, List<Value> transformResults) {
    hardAssert(
        transformResults.size() == fieldTransforms.size(), "Transform results length mismatch.");

    ObjectValue.Builder builder = objectValue.toBuilder();
    for (int i = 0; i < fieldTransforms.size(); i++) {
      FieldTransform fieldTransform = fieldTransforms.get(i);
      FieldPath fieldPath = fieldTransform.getFieldPath();
      builder.set(fieldPath, transformResults.get(i));
    }
    return builder.build();
  }

  public ObjectValue extractTransformBaseValue(@Nullable MaybeDocument maybeDoc) {
    ObjectValue.Builder baseObject = null;

    for (FieldTransform transform : fieldTransforms) {
      Value existingValue = null;
      if (maybeDoc instanceof Document) {
        existingValue = ((Document) maybeDoc).getField(transform.getFieldPath());
      }

      Value coercedValue = transform.getOperation().computeBaseValue(existingValue);
      if (coercedValue != null) {
        if (baseObject == null) {
          baseObject = ObjectValue.newBuilder();
        }
        baseObject.set(transform.getFieldPath(), coercedValue);
      }
    }

    return baseObject != null ? baseObject.build() : null;
  }
}
