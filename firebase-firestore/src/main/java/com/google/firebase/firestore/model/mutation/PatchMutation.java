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
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.ObjectValue;
import com.google.firestore.v1.Value;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

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
  public void applyToRemoteDocument(MutableDocument document, MutationResult mutationResult) {
    verifyKeyMatches(document);

    if (!this.getPrecondition().isValidFor(document)) {
      // Since the mutation was not rejected, we know that the precondition matched on the backend.
      // We therefore must not have the expected version of the document in our cache and return an
      // UnknownDocument with the known updateTime.
      document.convertToUnknownDocument(mutationResult.getVersion());
      return;
    }

    Map<FieldPath, Value> transformResults =
        serverTransformResults(document, mutationResult.getTransformResults());
    ObjectValue value = document.getData();
    value.setAll(getPatch());
    value.setAll(transformResults);
    document
        .convertToFoundDocument(mutationResult.getVersion(), document.getData())
        .setHasCommittedMutations();
  }

  @Override
  public @Nullable FieldMask applyToLocalView(
      MutableDocument document,
      @Nullable FieldMask previousMask,
      int batchId,
      Timestamp localWriteTime) {
    verifyKeyMatches(document);

    if (!getPrecondition().isValidFor(document)) {
      return previousMask;
    }

    Map<FieldPath, Value> transformResults = localTransformResults(localWriteTime, document);
    Map<FieldPath, Value> patches = getPatch();
    ObjectValue value = document.getData();
    value.setAll(patches);
    value.setAll(transformResults);
    document
        .convertToFoundDocument(document.getVersion(), document.getData())
        .setHasLocalMutations();

    if (previousMask == null) {
      return null;
    }

    HashSet<FieldPath> mergedMaskSet = new HashSet<>(previousMask.getMask());
    mergedMaskSet.addAll(this.mask.getMask());
    mergedMaskSet.addAll(getFieldTransformPaths());
    return FieldMask.fromSet(mergedMaskSet);
  }

  private List<FieldPath> getFieldTransformPaths() {
    List<FieldPath> result = new ArrayList<>();
    for (FieldTransform fieldTransform : getFieldTransforms()) {
      result.add(fieldTransform.getFieldPath());
    }
    return result;
  }

  private Map<FieldPath, Value> getPatch() {
    Map<FieldPath, Value> result = new HashMap<>();
    for (FieldPath path : mask.getMask()) {
      if (!path.isEmpty()) {
        result.put(path, value.get(path));
      }
    }
    return result;
  }
}
