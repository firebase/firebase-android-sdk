// Copyright 2021 Google LLC
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
import com.google.firebase.firestore.util.Assert;
import java.util.HashSet;

/**
 * A marker type to indicate an empty mutation that does nothing. This is useful as the initial
 * result of {@code Mutation.squash}.
 */
public final class EmptyMutation extends Mutation {

  public EmptyMutation(DocumentKey key, Precondition precondition) {
    super(key, precondition);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    EmptyMutation that = (EmptyMutation) o;
    return hasSameKeyAndPrecondition(that);
  }

  @Override
  public int hashCode() {
    return keyAndPreconditionHashCode();
  }

  @Override
  public String toString() {
    return "EmptyMutation{" + keyAndPreconditionToString() + "}";
  }

  @Override
  public void applyToRemoteDocument(MutableDocument document, MutationResult mutationResult) {
    return;
  }

  public void applyToLocalView(MutableDocument document, Timestamp localWriteTime) {
    return;
  }

  @Override
  public Mutation squash(
      Mutation baseMutation, MutableDocument document, Timestamp localWriteTime) {
    throw Assert.fail("EmptyMutation should never be used to squash.");
  }

  @Override
  protected FieldUpdate getFieldUpdate(FieldPath fieldPath) {
    return new FieldUpdate(FieldUpdate.Type.ABSENT, null);
  }

  @Nullable
  protected ObjectValue getValue() {
    return null;
  }

  protected FieldMask getMask() {
    return FieldMask.fromSet(new HashSet<>());
  }
}
