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
import com.google.firebase.firestore.model.UnknownDocument;
import com.google.firestore.v1.Value;
import java.util.ArrayList;
import java.util.List;

/**
 * A mutation that modifies specific fields of the document with transform operations. Currently the
 * only supported transform is a server timestamp, but IP Address, increment(n), etc. could be
 * supported in the future.
 *
 * <p>It is somewhat similar to a PatchMutation in that it patches specific fields and has no effect
 * when applied to null or a NoDocument (see comment on Mutation.applyTo() for rationale).
 */
public final class TransformMutation extends Mutation {
  private final List<FieldTransform> fieldTransforms;

  public TransformMutation(DocumentKey key, List<FieldTransform> fieldTransforms) {
    // NOTE: We set a precondition of exists: true as a safety-check, since we always combine
    // TransformMutations with a SetMutation or PatchMutation which (if successful) should
    // end up with an existing document.
    super(key, Precondition.exists(true));
    this.fieldTransforms = fieldTransforms;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    TransformMutation that = (TransformMutation) o;
    return hasSameKeyAndPrecondition(that) && fieldTransforms.equals(that.fieldTransforms);
  }

  @Override
  public int hashCode() {
    int result = keyAndPreconditionHashCode();
    result = 31 * result + fieldTransforms.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "TransformMutation{"
        + keyAndPreconditionToString()
        + ", fieldTransforms="
        + fieldTransforms
        + "}";
  }

  public List<FieldTransform> getFieldTransforms() {
    return fieldTransforms;
  }

  @Override
  public MaybeDocument applyToRemoteDocument(
      @Nullable MaybeDocument maybeDoc, MutationResult mutationResult) {
    verifyKeyMatches(maybeDoc);

    hardAssert(
        mutationResult.getTransformResults() != null,
        "Transform results missing for TransformMutation.");

    if (!this.getPrecondition().isValidFor(maybeDoc)) {
      // Since the mutation was not rejected, we know that the precondition matched on the backend.
      // We therefore must not have the expected version of the document in our cache and return an
      // UnknownDocument with the known updateTime.
      return new UnknownDocument(this.getKey(), mutationResult.getVersion());
    }

    Document doc = requireDocument(maybeDoc);
    List<Value> transformResults =
        serverTransformResults(doc, mutationResult.getTransformResults());
    ObjectValue newData = transformObject(doc.getData(), transformResults);
    return new Document(
        getKey(), mutationResult.getVersion(), newData, Document.DocumentState.COMMITTED_MUTATIONS);
  }

  @Nullable
  @Override
  public MaybeDocument applyToLocalView(
      @Nullable MaybeDocument maybeDoc, @Nullable MaybeDocument baseDoc, Timestamp localWriteTime) {
    verifyKeyMatches(maybeDoc);

    if (!this.getPrecondition().isValidFor(maybeDoc)) {
      return maybeDoc;
    }

    Document doc = requireDocument(maybeDoc);
    List<Value> transformResults = localTransformResults(localWriteTime, maybeDoc, baseDoc);
    ObjectValue newData = transformObject(doc.getData(), transformResults);
    return new Document(
        getKey(), doc.getVersion(), newData, Document.DocumentState.LOCAL_MUTATIONS);
  }

  @Nullable
  @Override
  public ObjectValue extractBaseValue(@Nullable MaybeDocument maybeDoc) {
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

  /**
   * Asserts that the given MaybeDocument is actually a Document and verifies that it matches the
   * key for this mutation. Since we only support transformations with precondition exists this
   * method is guaranteed to be safe.
   */
  private Document requireDocument(@Nullable MaybeDocument maybeDoc) {
    hardAssert(maybeDoc instanceof Document, "Unknown MaybeDocument type %s", maybeDoc);
    Document doc = (Document) maybeDoc;
    hardAssert(doc.getKey().equals(getKey()), "Can only transform a document with the same key");
    return doc;
  }

  /**
   * Creates a list of "transform results" (a transform result is a field value representing the
   * result of applying a transform) for use after a TransformMutation has been acknowledged by the
   * server.
   *
   * @param baseDoc The document prior to applying this mutation batch.
   * @param serverTransformResults The transform results received by the server.
   * @return The transform results list.
   */
  private List<Value> serverTransformResults(
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
   * result of applying a transform) for use when applying a TransformMutation locally.
   *
   * @param localWriteTime The local time of the transform mutation (used to generate
   *     ServerTimestampValues).
   * @param maybeDoc The current state of the document after applying all previous mutations.
   * @param baseDoc The document prior to applying this mutation batch.
   * @return The transform results list.
   */
  private List<Value> localTransformResults(
      Timestamp localWriteTime, @Nullable MaybeDocument maybeDoc, @Nullable MaybeDocument baseDoc) {
    ArrayList<Value> transformResults = new ArrayList<>(fieldTransforms.size());
    for (FieldTransform fieldTransform : fieldTransforms) {
      TransformOperation transform = fieldTransform.getOperation();

      Value previousValue = null;
      if (maybeDoc instanceof Document) {
        previousValue = ((Document) maybeDoc).getField(fieldTransform.getFieldPath());
      }

      if (previousValue == null && baseDoc instanceof Document) {
        // If the current document does not contain a value for the mutated field, use the value
        // that existed before applying this mutation batch. This solves an edge case where a
        // PatchMutation clears the values in a nested map before the TransformMutation is applied.
        previousValue = ((Document) baseDoc).getField(fieldTransform.getFieldPath());
      }

      transformResults.add(transform.applyToLocalView(previousValue, localWriteTime));
    }
    return transformResults;
  }

  private ObjectValue transformObject(ObjectValue objectValue, List<Value> transformResults) {
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
}
