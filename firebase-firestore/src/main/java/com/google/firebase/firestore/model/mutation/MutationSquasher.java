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

public class MutationSquasher {
  private DocumentKey key;

  private boolean exists;
  private Mutation currentSquashed = null;

  public MutationSquasher(DocumentKey key, boolean exists) {
    this.key = key;
    this.exists = exists;
  }

  public Mutation getMutation() {
    return currentSquashed;
  }

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
    if (!exists) {
      MutableDocument doc = MutableDocument.newNoDocument(key, SnapshotVersion.NONE);
      if (mutation.getPrecondition().isValidFor(doc)) {
        currentSquashed = mutation;
        exists = true;
      }
    } else {
      currentSquashed = mutation;
    }
  }

  private void squashPatchMutationWithDelete(
      DeleteMutation previouslySquashed, PatchMutation mutation) {
    MutableDocument doc = MutableDocument.newNoDocument(mutation.getKey(), SnapshotVersion.NONE);
    if (!mutation.getPrecondition().isValidFor(doc)) {
      return;
    }

    PatchMutation patchWithNoTransform =
        new PatchMutation(
            mutation.getKey(), mutation.getValue(), mutation.getMask(), mutation.getPrecondition());
    patchWithNoTransform.applyToLocalView(doc, Timestamp.now());

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
    Set mask = previouslySquashed.getMask().getMask();
    for (FieldTransform transform : mutation.getFieldTransforms()) {
      if (mask.contains(transform.getFieldPath())) {
        mask.remove(transform.getFieldPath());
      }
    }

    List<FieldTransform> transforms = new ArrayList<>();
    Set newMutationMask = mutation.getMask().getMask();
    for (FieldTransform transform : previouslySquashed.getFieldTransforms()) {
      if (!newMutationMask.contains(transform.getFieldPath())) {
        transforms.add(transform);
      }
    }

    currentSquashed =
        new PatchMutation(
            mutation.getKey(),
            mergeObjectValue(previouslySquashed.getValue(), mutation.getValue()),
            mergeMask(FieldMask.fromSet(mask), mutation.getMask()),
            previouslySquashed.getPrecondition(),
            mergeTransforms(transforms, mutation.getFieldTransforms()));
  }

  private static List<FieldTransform> mergeTransforms(
      List<FieldTransform> existing, List<FieldTransform> newTransforms) {
    Map<FieldPath, FieldTransform> transformMap = new HashMap<>();
    for (FieldTransform t : existing) {
      transformMap.put(t.getFieldPath(), t);
    }

    for (FieldTransform newTransform : newTransforms) {
      transformMap.put(
          newTransform.getFieldPath(),
          mergeTransform(transformMap.get(newTransform.getFieldPath()), newTransform));
    }

    return new ArrayList(transformMap.values());
  }

  private static FieldTransform mergeTransform(
      FieldTransform existing, FieldTransform newTransform) {
    if (existing == null) {
      return newTransform;
    }

    if (newTransform.getOperation() instanceof ServerTimestampOperation) {
      return newTransform;
    }

    if (newTransform.getOperation() instanceof ArrayTransformOperation) {
      if (!(existing.getOperation() instanceof ArrayTransformOperation)) {
        return newTransform;
      }

      ArrayTransformOperation.ArrayTransformList result;
      if (existing.getOperation() instanceof ArrayTransformOperation.ArrayTransformList) {
        result = (ArrayTransformOperation.ArrayTransformList) existing.getOperation();
      } else {
        result = new ArrayTransformOperation.ArrayTransformList();
        result.addTransform((ArrayTransformOperation) existing.getOperation());
      }

      result.addTransform((ArrayTransformOperation) newTransform.getOperation());
      return new FieldTransform(existing.getFieldPath(), result);
    }

    if (newTransform.getOperation() instanceof NumericIncrementTransformOperation) {
      if (!(existing.getOperation() instanceof NumericIncrementTransformOperation)) {
        return newTransform;
      }

      NumericIncrementTransformOperation existingOperation =
          (NumericIncrementTransformOperation) existing.getOperation();
      NumericIncrementTransformOperation newOperation =
          (NumericIncrementTransformOperation) newTransform.getOperation();
      Value newValue =
          Value.newBuilder()
              .setIntegerValue(
                  existingOperation.getOperand().getIntegerValue()
                      + newOperation.getOperand().getIntegerValue())
              .build();
      return new FieldTransform(
          existing.getFieldPath(), new NumericIncrementTransformOperation(newValue));
    }

    hardAssert(false, "Cannot reach");
    return null;
  }

  private static ObjectValue mergeObjectValue(ObjectValue existing, ObjectValue newValue) {
    Map existingValue = new HashMap(existing.getFieldsMap());
    existingValue.putAll(newValue.getFieldsMap());
    return ObjectValue.fromMap(existingValue);
  }

  private static FieldMask mergeMask(FieldMask existing, FieldMask newMask) {
    Set existingMask = new HashSet(existing.getMask());
    existingMask.addAll(newMask.getMask());
    return FieldMask.fromSet(existingMask);
  }
}
