package com.google.firebase.firestore.model.mutation;

import static com.google.firebase.firestore.util.Assert.fail;
import static com.google.firebase.firestore.util.Assert.hardAssert;

import com.google.common.collect.Sets;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.ObjectValue;
import com.google.firestore.v1.Value;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Utility class to compute a mutation from an original document, and a mutated version. There are
 * likely multiple mutations happened in between, hence "squash".
 */
public class MutationSquash {
  public enum Type {
    Set,
    Delete,
    Patch,
    // Patch but with precondition set to None.
    PatchWithMerge,
    None
  }

  /**
   * Give an original document, its mutated version, and the squash target type, returns a squashed
   * mutation, that would hold invariant: original + squashed_mutation = finalDoc.
   */
  @Nullable
  public static Mutation toMutation(MutableDocument original, MutableDocument finalDoc, Type type) {
    switch (type) {
      case Set:
        return toSetMutation(original, finalDoc);
      case Delete:
        return toDeleteMutation(original, finalDoc);
      case Patch:
        return toPatchMutation(original, finalDoc, false);
      case PatchWithMerge:
        return toPatchMutation(original, finalDoc, true);
      case None:
        return null;
      default:
        fail("Unrecognized squash mutation type");
    }
    return null;
  }

  private static SetMutation toSetMutation(MutableDocument original, MutableDocument finalDoc) {
    hardAssert(
        original.getKey().equals(finalDoc.getKey()), "Squashing documents with different keys.");
    return new SetMutation(finalDoc.getKey(), finalDoc.getData().clone(), Precondition.NONE);
  }

  private static DeleteMutation toDeleteMutation(
      MutableDocument original, MutableDocument finalDoc) {
    hardAssert(
        original.getKey().equals(finalDoc.getKey()), "Squashing documents with different keys.");
    return new DeleteMutation(finalDoc.getKey(), Precondition.NONE);
  }

  private static PatchMutation toPatchMutation(
      MutableDocument original, MutableDocument finalDoc, boolean merge) {
    hardAssert(
        original.getKey().equals(finalDoc.getKey()), "Squashing documents with different keys.");

    Map<String, Value> originalFields = original.getData().getFieldsMap();
    Map<String, Value> finalFields = finalDoc.getData().getFieldsMap();
    HashSet<FieldPath> mask = new HashSet<>();
    HashMap<String, Value> values = new HashMap<>();

    // Field updates
    Sets.SetView<String> intersection =
        Sets.intersection(originalFields.keySet(), finalFields.keySet());
    for (String key : intersection) {
      if (!originalFields.get(key).equals(finalFields.get(key))) {
        mask.add(FieldPath.fromSingleSegment(key));
        values.put(key, finalFields.get(key));
      }
    }

    // Field additions
    Sets.SetView<String> additions = Sets.difference(finalFields.keySet(), originalFields.keySet());
    for (String key : additions) {
      mask.add(FieldPath.fromSingleSegment(key));
      values.put(key, finalFields.get(key));
    }

    // Field deletions
    Sets.SetView<String> deletions = Sets.difference(originalFields.keySet(), finalFields.keySet());
    for (String key : deletions) {
      mask.add(FieldPath.fromSingleSegment(key));
    }

    return new PatchMutation(
        finalDoc.getKey(),
        ObjectValue.fromMap(values),
        FieldMask.fromSet(mask),
        merge ? Precondition.NONE : Precondition.exists(true));
  }
}
