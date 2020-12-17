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

import androidx.annotation.Nullable;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.MaybeDocument;
import com.google.firebase.firestore.model.ObjectValue;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.model.UnknownDocument;
import com.google.firestore.v1.Value;
import java.util.ArrayList;
import java.util.List;

/**
 * A mutation that modifies fields of the document at the given key with the given values. The
 * values are applied through a field mask:
 *
 * <ul>
 *   <li>When a field is in both the mask and the values, the corresponding field is updated.
 *   <li>When a field is in neither the mask nor the values, the corresponding field is unmodified.
 *   <li>When a field is in the mask but not in the values, the corresponding field is deleted.
 *   <li>When a field is not in the mask but is in the values, the values map is ignored.
 * </ul>
 */
public final class PatchMutation extends Mutation {

  private final ObjectValue value;
  private final FieldMask mask;

  public PatchMutation(
      DocumentKey key, ObjectValue value, FieldMask mask, Precondition precondition) {
    this(key, value, mask, precondition, new ArrayList<>());
  }

  public PatchMutation(
      DocumentKey key,
      ObjectValue value,
      FieldMask mask,
      Precondition precondition,
      List<FieldTransform> fieldTransforms) {
    super(key, precondition, fieldTransforms);
    this.value = value;
    this.mask = mask;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    PatchMutation that = (PatchMutation) o;
    return hasSameKeyAndPrecondition(that)
        && value.equals(that.value)
        && getFieldTransforms().equals(that.getFieldTransforms());
  }

  @Override
  public int hashCode() {
    int result = keyAndPreconditionHashCode();
    result = 31 * result + value.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "PatchMutation{"
        + keyAndPreconditionToString()
        + ", mask="
        + mask
        + ", value="
        + value
        + "}";
  }

  /** Returns the fields and associated values to use when patching the document. */
  public ObjectValue getValue() {
    return value;
  }

  /**
   * Returns the mask to apply to {@link #getValue}, where only fields that are in both the
   * fieldMask and the value will be updated.
   */
  public FieldMask getMask() {
    return mask;
  }

  @Override
  public MaybeDocument applyToRemoteDocument(
      @Nullable MaybeDocument maybeDoc, MutationResult mutationResult) {
    verifyKeyMatches(maybeDoc);

    if (!this.getPrecondition().isValidFor(maybeDoc)) {
      // Since the mutation was not rejected, we know that the precondition matched on the backend.
      // We therefore must not have the expected version of the document in our cache and return an
      // UnknownDocument with the known updateTime.
      return new UnknownDocument(this.getKey(), mutationResult.getVersion());
    }

    List<Value> transformResults =
        mutationResult.getTransformResults() != null
            ? serverTransformResults(maybeDoc, mutationResult.getTransformResults())
            : new ArrayList<>();

    SnapshotVersion version = mutationResult.getVersion();
    ObjectValue newData = patchDocument(maybeDoc, transformResults);
    return new Document(getKey(), version, newData, Document.DocumentState.COMMITTED_MUTATIONS);
  }

  @Nullable
  @Override
  public MaybeDocument applyToLocalView(
      @Nullable MaybeDocument maybeDoc, @Nullable MaybeDocument baseDoc, Timestamp localWriteTime) {
    verifyKeyMatches(maybeDoc);

    if (!getPrecondition().isValidFor(maybeDoc)) {
      return maybeDoc;
    }

    List<Value> transformResults = localTransformResults(localWriteTime, maybeDoc, baseDoc);
    SnapshotVersion version = getPostMutationVersion(maybeDoc);
    ObjectValue newData = patchDocument(maybeDoc, transformResults);
    return new Document(getKey(), version, newData, Document.DocumentState.LOCAL_MUTATIONS);
  }

  /**
   * Patches the data of document if available or creates a new document. Note that this does not
   * check whether or not the precondition of this patch holds.
   */
  private ObjectValue patchDocument(
      @Nullable MaybeDocument maybeDoc, List<Value> transformResults) {
    ObjectValue data;
    if (maybeDoc instanceof Document) {
      data = ((Document) maybeDoc).getData();
    } else {
      data = ObjectValue.emptyObject();
    }
    data = patchObject(data);
    data = transformObject(data, transformResults);
    return data;
  }

  private ObjectValue patchObject(ObjectValue obj) {
    ObjectValue.Builder builder = obj.toBuilder();
    for (FieldPath path : mask.getMask()) {
      if (!path.isEmpty()) {
        Value newValue = value.get(path);
        if (newValue == null) {
          builder.delete(path);
        } else {
          builder.set(path, newValue);
        }
      }
    }
    return builder.build();
  }
}
