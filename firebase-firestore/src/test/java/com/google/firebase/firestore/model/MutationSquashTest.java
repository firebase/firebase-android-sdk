package com.google.firebase.firestore.model;

import static com.google.firebase.firestore.testutil.TestUtil.deletedDoc;
import static com.google.firebase.firestore.testutil.TestUtil.doc;
import static com.google.firebase.firestore.testutil.TestUtil.field;
import static com.google.firebase.firestore.testutil.TestUtil.key;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.mergeMutation;
import static com.google.firebase.firestore.testutil.TestUtil.patchMutation;
import static com.google.firebase.firestore.testutil.TestUtil.setMutation;
import static com.google.firebase.firestore.testutil.TestUtil.unknownDoc;
import static org.junit.Assert.assertEquals;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.model.mutation.DeleteMutation;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.MutationSquash;
import com.google.firebase.firestore.model.mutation.Precondition;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests Mutations */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class MutationSquashTest {
  @Test
  public void testSquashOneSetMutation() {
    Map<String, Object> data = map("foo", "foo-value", "baz", "baz-value");
    MutableDocument setDoc = doc("collection/key", 0, data);
    MutableDocument original = setDoc.clone();

    Timestamp now = Timestamp.now();
    Mutation set = setMutation("collection/key", map("bar", "bar-value"));
    set.applyToLocalView(setDoc, now, MutationSquash.Type.None);

    Mutation squashed = MutationSquash.toMutation(original, setDoc, MutationSquash.Type.Set);
    squashed.applyToLocalView(original, now, MutationSquash.Type.None);
    assertEquals(original, setDoc);
  }

  @Test
  public void testSquashOnePatchMutation() {
    Map<String, Object> data = map("foo", map("bar", "bar-value"), "baz", "baz-value");
    MutableDocument patchDoc = doc("collection/key", 0, data);
    MutableDocument original = patchDoc.clone();

    Timestamp now = Timestamp.now();
    Mutation patch = patchMutation("collection/key", map("foo.bar", "new-bar-value"));
    patch.applyToLocalView(patchDoc, now, MutationSquash.Type.None);

    Mutation squashed = MutationSquash.toMutation(original, patchDoc, MutationSquash.Type.Patch);
    squashed.applyToLocalView(original, now, MutationSquash.Type.None);
    assertEquals(original, patchDoc);
  }

  @Test
  public void testSquashWithPatchWithMerge() {
    MutableDocument mergeDoc = deletedDoc("collection/key", 0);
    MutableDocument original = mergeDoc.clone();

    Timestamp now = Timestamp.now();
    Mutation upsert =
        mergeMutation(
            "collection/key", map("foo.bar", "new-bar-value"), Arrays.asList(field("foo.bar")));
    MutationSquash.Type type = upsert.applyToLocalView(mergeDoc, now, MutationSquash.Type.None);

    Mutation squashed = MutationSquash.toMutation(original, mergeDoc, type);
    squashed.applyToLocalView(original, now, MutationSquash.Type.None);
    assertEquals(original, mergeDoc);
  }

  @Test
  public void testSquashWithDeleteThenPatch() {
    MutableDocument doc = doc("collection/key", 0, map("foo", 1));
    MutableDocument original = doc.clone();

    Timestamp now = Timestamp.now();
    Mutation delete = new DeleteMutation(key("collection/key"), Precondition.NONE);
    delete.applyToLocalView(doc, now, MutationSquash.Type.None);

    now = Timestamp.now();
    Mutation patch = patchMutation("collection/key", map("foo.bar", "new-bar-value"));
    patch.applyToLocalView(doc, now, MutationSquash.Type.None);

    Mutation squashed = MutationSquash.toMutation(original, doc, MutationSquash.Type.Delete);
    squashed.applyToLocalView(original, now, MutationSquash.Type.None);
    assertEquals(doc, original);
  }

  @Test
  public void testSquashWithDeleteThenMerge() {
    MutableDocument doc = doc("collection/key", 0, map("foo", 1));
    MutableDocument original = doc.clone();

    Timestamp now = Timestamp.now();
    Mutation delete = new DeleteMutation(key("collection/key"), Precondition.NONE);
    delete.applyToLocalView(doc, now, MutationSquash.Type.None);

    now = Timestamp.now();
    Mutation patch =
        mergeMutation(
            "collection/key", map("foo.bar", "new-bar-value"), Arrays.asList(field("foo.bar")));
    patch.applyToLocalView(doc, now, MutationSquash.Type.None);

    Mutation squashed = MutationSquash.toMutation(original, doc, MutationSquash.Type.Patch);
    squashed.applyToLocalView(original, now, MutationSquash.Type.None);
    assertEquals(doc, original);
  }

  @Test
  public void testSquashMutationWithMultipleDeletes() {
    List<MutableDocument> docs =
        Lists.newArrayList(
            doc("collection/key", 0, map("foo", "foo-value", "bar.baz", 1)),
            deletedDoc("collection/key", 0),
            unknownDoc("collection/key", 0));
    List<Mutation> mutations =
        Lists.newArrayList(
            setMutation("collection/key", map("bar", "bar-value")),
            new DeleteMutation(key("collection/key"), Precondition.exists(true)),
            new DeleteMutation(key("collection/key"), Precondition.exists(true)),
            patchMutation(
                "collection/key",
                map("foo", "foo-patched-value", "bar.baz", FieldValue.serverTimestamp())));

    int caseNumber = 0;
    caseNumber += runPermutationTests(docs, Lists.newArrayList(mutations));

    // There are 4! * 3 cases
    assertEquals(72, caseNumber);
  }

  @Test
  public void testSquashMutationByCombinationsAndPermutations() {
    List<MutableDocument> docs =
        Lists.newArrayList(
            doc("collection/key", 0, map("foo", "foo-value", "bar.baz", 1)),
            deletedDoc("collection/key", 0),
            unknownDoc("collection/key", 0));
    List<Mutation> mutations =
        Lists.newArrayList(
            setMutation("collection/key", map("bar", "bar-value")),
            setMutation("collection/key", map("bar.rab", "bar.rab-value")),
            new DeleteMutation(key("collection/key"), Precondition.exists(true)),
            patchMutation(
                "collection/key",
                map("foo", "foo-patched-value", "bar.baz", FieldValue.increment(1))),
            patchMutation(
                "collection/key", map("foo", "foo-patched-value", "bar.baz", FieldValue.delete())),
            mergeMutation(
                "collection/key",
                map("arrays", FieldValue.arrayUnion(1, 2, 3)),
                Arrays.asList(field("arrays"))));

    int caseNumber = 0;
    for (int subsetSize = 0; subsetSize <= mutations.size(); ++subsetSize) {
      Set<Set<Mutation>> combinations = Sets.combinations(Sets.newHashSet(mutations), subsetSize);
      for (Set<Mutation> combination : combinations) {
        caseNumber += runPermutationTests(docs, Lists.newArrayList(combination));
      }
    }

    // There are (0! + 6*1! + 15*2! + 20*3! + 15*4! + 6*5! + 6!) * 3 = 5871 cases.
    assertEquals(5871, caseNumber);
  }

  private int runPermutationTests(List<MutableDocument> docs, List<Mutation> mutations) {
    Timestamp now = Timestamp.now();
    int caseNumber = 0;
    List<List<Mutation>> permutations = generatePermutations(Lists.newArrayList(mutations));
    for (MutableDocument doc : docs) {
      for (List<Mutation> permutation : permutations) {
        MutableDocument document = doc.clone();
        MutableDocument docCopy = document.clone();
        MutationSquash.Type type = MutationSquash.Type.None;
        for (Mutation mutation : permutation) {
          type = mutation.applyToLocalView(document, now, type);
        }
        Mutation squashed = MutationSquash.toMutation(docCopy, document, type);
        if (squashed != null) {
          squashed.applyToLocalView(docCopy, now, MutationSquash.Type.None);
        }
        assertEquals(getDescription(caseNumber, permutation, squashed), document, docCopy);

        caseNumber += 1;
      }
    }
    return caseNumber;
  }

  private String getDescription(int caseNumber, List<Mutation> mutations, Mutation squashed) {
    StringBuilder builder = new StringBuilder();
    builder.append("MutationSquash test (" + caseNumber + ") failed with:\n");
    builder.append("mutations:\n");
    for (Mutation mutation : mutations) {
      builder.append(mutation.toString() + "\n");
    }
    builder.append("\n");
    builder.append("squashed:\n");
    builder.append(squashed == null ? "null" : squashed.toString());
    builder.append("\n\n");

    return builder.toString();
  }

  private <E> List<List<E>> generatePermutations(List<E> mutations) {
    if (mutations.isEmpty()) {
      List<List<E>> result = Lists.newArrayList();
      result.add(Lists.newArrayList());
      return result;
    }

    E first = mutations.remove(0);
    List<List<E>> returnValue = Lists.newArrayList();
    // Generate permutation with first element removed
    List<List<E>> permutations = generatePermutations(mutations);
    for (List<E> permutation : permutations) {
      // Insert `first` to each possible position of the permutation.
      for (int index = 0; index <= permutation.size(); index++) {
        List<E> permutationCopy = Lists.newArrayList(permutation);
        permutationCopy.add(index, first);
        returnValue.add(permutationCopy);
      }
    }
    return returnValue;
  }
}
