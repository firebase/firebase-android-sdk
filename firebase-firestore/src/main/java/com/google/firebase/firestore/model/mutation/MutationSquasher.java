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

import static com.google.firebase.firestore.util.Assert.hardAssert;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.ObjectValue;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firestore.v1.Value;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Squashes multiple mutations applied on a document into a final mutation. It is guaranteed when
 * the final mutation is applied on a document, it is as if the entire mutation list has been
 * applied.
 */
public class MutationSquasher {

  /** The key of the document whose mutations will be squashed */
  private DocumentKey key;
  /** Whether the document in question exists after `currentSquashed` is updated */
  private boolean exists;
  /** The mutation that is squashed from all the mutations it has seen. */
  private Mutation currentSquashed = null;

  public MutationSquasher(DocumentKey key, boolean exists) {
    this.key = key;
    this.exists = exists;
  }

  /** Returns the squashed mutation */
  public Mutation getMutation() {
    return currentSquashed;
  }

  /** Squashes the given mutation */
  public void squash(Mutation mutation) {
    if (mutation instanceof SetMutation) {
      currentSquashed = mutation;
      exists = true;
    } else if (mutation instanceof DeleteMutation) {
      currentSquashed = mutation;
      exists = false;
    } else if (currentSquashed == null) {
      squashPatchMutationWithNull((PatchMutation) mutation);
    } else if (currentSquashed instanceof DeleteMutation) {
      squashPatchMutationWithDelete((DeleteMutation) currentSquashed, (PatchMutation) mutation);
    } else if (currentSquashed instanceof SetMutation) {
      squashPatchMutationWithSet((SetMutation) currentSquashed, (PatchMutation) mutation);
      exists = true;
    } else {
      squashPatchMutationWithPatch((PatchMutation) currentSquashed, (PatchMutation) mutation);
    }
  }

  private void squashPatchMutationWithNull(PatchMutation mutation) {
    if (exists) {
      currentSquashed = mutation;
    } else {
      MutableDocument doc = MutableDocument.newNoDocument(key, SnapshotVersion.NONE);
      // Skip given mutation if it does not apply to no document.
      if (mutation.getPrecondition().isValidFor(doc)) {
        currentSquashed = mutation;
        exists = true;
      }
    }
  }

  private void squashPatchMutationWithDelete(
      DeleteMutation previouslySquashed, PatchMutation mutation) {
    MutableDocument doc = MutableDocument.newNoDocument(mutation.getKey(), SnapshotVersion.NONE);
    // Skip given mutation if it does not apply to no document.
    if (!mutation.getPrecondition().isValidFor(doc)) {
      return;
    }

    PatchMutation patchWithNoTransform =
        new PatchMutation(
            mutation.getKey(), mutation.getValue(), mutation.getMask(), mutation.getPrecondition());
    patchWithNoTransform.applyToLocalView(doc, Timestamp.now());

    // A Patch on a Delete turns into Set.
    currentSquashed =
        new SetMutation(
            doc.getKey(),
            doc.getData(),
            Precondition.NONE,
            mergeTransforms(
                previouslySquashed.getFieldTransforms(), mutation.getFieldTransforms()));
    exists = true;
  }

  private void squashPatchMutationWithSet(SetMutation previouslySquashed, PatchMutation mutation) {
    MutableDocument doc = MutableDocument.newNoDocument(mutation.getKey(), SnapshotVersion.NONE);
    SetMutation setWithNoTransform =
        new SetMutation(
            previouslySquashed.getKey(), previouslySquashed.getValue(), Precondition.NONE);
    setWithNoTransform.applyToLocalView(doc, Timestamp.now());

    PatchMutation patchWithNoTransform =
        new PatchMutation(
            mutation.getKey(), mutation.getValue(), mutation.getMask(), mutation.getPrecondition());
    patchWithNoTransform.applyToLocalView(doc, Timestamp.now());

    // A Patch on a Set is still Set.
    currentSquashed =
        new SetMutation(
            doc.getKey(),
            doc.getData(),
            Precondition.NONE,
            mergeTransforms(
                previouslySquashed.getFieldTransforms(), mutation.getFieldTransforms()));
  }

  private void squashPatchMutationWithPatch(
      PatchMutation previouslySquashed, PatchMutation mutation) {
    // Fields with transforms from `mutation` is taken out of the mask of `previouslySquashed` as
    // they will get overwritten.
    Set previousMask = previouslySquashed.getMask().getMask();
    for (FieldTransform transform : mutation.getFieldTransforms()) {
      if (previousMask.contains(transform.getFieldPath())) {
        previousMask.remove(transform.getFieldPath());
      }
    }

    // Fields in the mask from `mutation` is taken out of the transform list of `previouslySquashed`
    // as they will get overwritten.
    List<FieldTransform> previousTransforms = new ArrayList<>();
    Set newMutationMask = mutation.getMask().getMask();
    for (FieldTransform transform : previouslySquashed.getFieldTransforms()) {
      if (!newMutationMask.contains(transform.getFieldPath())) {
        previousTransforms.add(transform);
      }
    }

    currentSquashed =
        new PatchMutation(
            mutation.getKey(),
            mergeObjectValue(previouslySquashed.getValue(), mutation.getValue()),
            mergeMask(FieldMask.fromSet(previousMask), mutation.getMask()),
            previouslySquashed.getPrecondition(),
            mergeTransforms(previousTransforms, mutation.getFieldTransforms()));
  }

  private static List<FieldTransform> mergeTransforms(
      List<FieldTransform> previous, List<FieldTransform> newTransforms) {
    Map<FieldPath, FieldTransform> transformMap = new HashMap<>();
    for (FieldTransform t : previous) {
      transformMap.put(t.getFieldPath(), t);
    }

    for (FieldTransform newTransform : newTransforms) {
      transformMap.put(
          newTransform.getFieldPath(),
          mergeTransform(transformMap.get(newTransform.getFieldPath()), newTransform));
    }

    return new ArrayList(transformMap.values());
  }

  /** Merges a new transform into a previous transform on the same field. */
  private static FieldTransform mergeTransform(
      FieldTransform previous, FieldTransform newTransform) {
    if (previous == null) {
      return newTransform;
    }

    if (newTransform.getOperation() instanceof ServerTimestampOperation) {
      return newTransform;
    }

    if (newTransform.getOperation() instanceof ArrayTransformOperation) {
      // Simply overwrite if `previous` is not array transform.
      if (!(previous.getOperation() instanceof ArrayTransformOperation)) {
        return newTransform;
      }

      // array transforms need to be kept in order, the result is always a `ArrayTransformList`.
      ArrayTransformOperation.ArrayTransformList result;
      if (previous.getOperation() instanceof ArrayTransformOperation.ArrayTransformList) {
        result = (ArrayTransformOperation.ArrayTransformList) previous.getOperation();
      } else {
        result = new ArrayTransformOperation.ArrayTransformList();
        result.addTransform((ArrayTransformOperation) previous.getOperation());
      }

      result.addTransform((ArrayTransformOperation) newTransform.getOperation());
      return new FieldTransform(previous.getFieldPath(), result);
    }

    if (newTransform.getOperation() instanceof NumericIncrementTransformOperation) {
      // Simply overwrite if `previous` is not array transform.
      if (!(previous.getOperation() instanceof NumericIncrementTransformOperation)) {
        return newTransform;
      }

      // Squashed numeric increment is a new increment with operands from two transforms added
      // together.
      NumericIncrementTransformOperation existingOperation =
          (NumericIncrementTransformOperation) previous.getOperation();
      NumericIncrementTransformOperation newOperation =
          (NumericIncrementTransformOperation) newTransform.getOperation();
      Value newValue =
          Value.newBuilder()
              .setIntegerValue(
                  existingOperation.getOperand().getIntegerValue()
                      + newOperation.getOperand().getIntegerValue())
              .build();
      return new FieldTransform(
          previous.getFieldPath(), new NumericIncrementTransformOperation(newValue));
    }

    hardAssert(false, "Cannot reach");
    return null;
  }

  private static ObjectValue mergeObjectValue(ObjectValue previous, ObjectValue newValue) {
    Map existingValue = new HashMap(previous.getFieldsMap());
    existingValue.putAll(newValue.getFieldsMap());
    return ObjectValue.fromMap(existingValue);
  }

  private static FieldMask mergeMask(FieldMask previous, FieldMask newMask) {
    Set existingMask = new HashSet(previous.getMask());
    existingMask.addAll(newMask.getMask());
    return FieldMask.fromSet(existingMask);
  }
}
