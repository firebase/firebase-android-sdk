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
import java.util.List;
import java.util.Map;

/**
 * A mutation that creates or replaces the document at the given key with the object value contents.
 */
public final class SetMutation extends Mutation {
  private final ObjectValue value;

  public SetMutation(DocumentKey key, ObjectValue value, Precondition precondition) {
    this(key, value, precondition, new ArrayList<>());
  }

  public SetMutation(
      DocumentKey key,
      ObjectValue value,
      Precondition precondition,
      List<FieldTransform> fieldTransforms) {
    super(key, precondition, fieldTransforms);
    this.value = value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    SetMutation that = (SetMutation) o;
    return hasSameKeyAndPrecondition(that)
        && value.equals(that.value)
        && getFieldTransforms().equals(that.getFieldTransforms());
  }

  @Override
  public int hashCode() {
    int result = keyAndPreconditionHashCode();
    result = result * 31 + value.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "SetMutation{" + keyAndPreconditionToString() + ", value=" + value + "}";
  }

  @Override
  public void applyToRemoteDocument(MutableDocument document, MutationResult mutationResult) {
    verifyKeyMatches(document);

    // Unlike applyToLocalView, if we're applying a mutation to a remote document the server has
    // accepted the mutation so the precondition must have held.
    ObjectValue newData = value.clone();
    Map<FieldPath, Value> transformResults =
        serverTransformResults(document, mutationResult.getTransformResults());
    newData.setAll(transformResults);
    document
        .convertToFoundDocument(mutationResult.getVersion(), newData)
        .setHasCommittedMutations();
  }

  @Override
  public FieldMask applyToLocalView(
      MutableDocument document,
      @Nullable FieldMask previousMask,
      int batchId,
      Timestamp localWriteTime) {
    verifyKeyMatches(document);

    if (!this.getPrecondition().isValidFor(document)) {
      return previousMask;
    }

    Map<FieldPath, Value> transformResults = localTransformResults(localWriteTime, document);
    ObjectValue localValue = value.clone();
    localValue.setAll(transformResults);
    document
        .convertToFoundDocument(document.getVersion(), localValue)
        .setHasLocalMutations(batchId);
    // SetMutation overwrites all fields.
    return null;
  }

  /** Returns the object value to use when setting the document. */
  public ObjectValue getValue() {
    return value;
  }
}
