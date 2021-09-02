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
import com.google.firebase.firestore.model.mutation.MutationSquasher;
import com.google.firebase.firestore.model.mutation.Precondition;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests MutationSquasher */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class MutationSquasherTest {
  @Test
  public void testSquashOneSetMutation() {
    Map<String, Object> data = map("foo", "foo-value", "baz", "baz-value");
    MutableDocument setDoc = doc("collection/key", 0, data);
    MutableDocument original = setDoc.clone();

    Timestamp now = Timestamp.now();
    Mutation set = setMutation("collection/key", map("bar", "bar-value"));
    set.applyToLocalView(setDoc, now);

    MutationSquasher squasher = new MutationSquasher(setDoc.getKey(), true);
    squasher.squash(set);
    squasher.getMutation().applyToLocalView(original, now);
    assertEquals(original, setDoc);
  }

  @Test
  public void testSquashOnePatchMutation() {
    Map<String, Object> data = map("foo", map("bar", "bar-value"), "baz", "baz-value");
    MutableDocument patchDoc = doc("collection/key", 0, data);
    MutableDocument original = patchDoc.clone();

    Timestamp now = Timestamp.now();
    Mutation patch = patchMutation("collection/key", map("foo.bar", "new-bar-value"));
    patch.applyToLocalView(patchDoc, now);

    MutationSquasher squasher = new MutationSquasher(patch.getKey(), true);
    squasher.squash(patch);
    squasher.getMutation().applyToLocalView(original, now);
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
    upsert.applyToLocalView(mergeDoc, now);

    MutationSquasher squasher = new MutationSquasher(mergeDoc.getKey(), true);
    squasher.squash(upsert);
    squasher.getMutation().applyToLocalView(original, now);
    assertEquals(original, mergeDoc);
  }

  @Test
  public void testSquashWithDeleteThenPatch() {
    MutableDocument doc = doc("collection/key", 0, map("foo", 1));
    MutableDocument original = doc.clone();

    Timestamp now = Timestamp.now();
    Mutation delete = new DeleteMutation(key("collection/key"), Precondition.NONE);
    delete.applyToLocalView(doc, now);

    now = Timestamp.now();
    Mutation patch = patchMutation("collection/key", map("foo.bar", "new-bar-value"));
    patch.applyToLocalView(doc, now);

    MutationSquasher squasher = new MutationSquasher(doc.getKey(), true);
    squasher.squash(delete);
    squasher.squash(patch);
    squasher.getMutation().applyToLocalView(original, now);
    assertEquals(doc, original);
  }

  @Test
  public void testSquashWithDeleteThenMerge() {
    MutableDocument doc = doc("collection/key", 0, map("foo", 1));
    MutableDocument original = doc.clone();

    Timestamp now = Timestamp.now();
    Mutation delete = new DeleteMutation(key("collection/key"), Precondition.NONE);
    delete.applyToLocalView(doc, now);

    now = Timestamp.now();
    Mutation patch =
        mergeMutation(
            "collection/key", map("foo.bar", "new-bar-value"), Arrays.asList(field("foo.bar")));
    patch.applyToLocalView(doc, now);

    MutationSquasher squasher = new MutationSquasher(doc.getKey(), true);
    squasher.squash(delete);
    squasher.squash(patch);
    squasher.getMutation().applyToLocalView(original, now);
    assertEquals(doc, original);
  }

  @Test
  public void testSquashPatchThenPatchToDeleteField() {
    MutableDocument doc = doc("collection/key", 0, map("foo", 1));
    MutableDocument original = doc.clone();

    Timestamp now = Timestamp.now();
    Mutation patch =
        patchMutation(
            "collection/key", map("foo", "foo-patched-value", "bar.baz", FieldValue.increment(1)));
    patch.applyToLocalView(doc, now);

    Mutation patchToDeleteField =
        patchMutation(
            "collection/key", map("foo", "foo-patched-value", "bar.baz", FieldValue.delete()));
    patchToDeleteField.applyToLocalView(doc, now);

    MutationSquasher squasher = new MutationSquasher(doc.getKey(), true);
    squasher.squash(patch);
    squasher.squash(patchToDeleteField);
    squasher.getMutation().applyToLocalView(original, now);
    assertEquals(doc, original);
  }

  @Test
  public void testSquashPatchThenMerge() {
    MutableDocument doc = doc("collection/key", 0, map("foo", 1));
    MutableDocument original = doc.clone();

    Timestamp now = Timestamp.now();
    Mutation patch =
        patchMutation(
            "collection/key", map("foo", "foo-patched-value", "bar.baz", FieldValue.increment(1)));
    patch.applyToLocalView(doc, now);

    Mutation merge =
        mergeMutation(
            "collection/key",
            map("arrays", FieldValue.arrayUnion(1, 2, 3)),
            Arrays.asList(field("arrays")));
    merge.applyToLocalView(doc, now);

    MutationSquasher squasher = new MutationSquasher(doc.getKey(), true);
    squasher.squash(patch);
    squasher.squash(merge);
    squasher.getMutation().applyToLocalView(original, now);
    assertEquals(doc, original);
  }

  @Test
  public void testSquashArrayUnionThenRemove() {
    MutableDocument doc = doc("collection/key", 0, map("foo", 1));
    MutableDocument original = doc.clone();

    Timestamp now = Timestamp.now();
    Mutation union =
        mergeMutation(
            "collection/key", map("arrays", FieldValue.arrayUnion(1, 2, 3)), Arrays.asList());
    Mutation remove =
        mergeMutation(
            "collection/key",
            map("foo", "xxx", "arrays", FieldValue.arrayRemove(2)),
            Arrays.asList(field("foo")));
    union.applyToLocalView(doc, now);
    remove.applyToLocalView(doc, now);

    MutationSquasher squasher = new MutationSquasher(doc.getKey(), true);
    squasher.squash(union);
    squasher.squash(remove);
    squasher.getMutation().applyToLocalView(original, now);
    assertEquals(doc, original);
  }

  // Below tests run on automatically generated mutation list, they are deterministic, but hard to
  // debug when they fail. They will print the failure case, and the best way to debug is recreate
  // the case manually in a separate test.

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
            new DeleteMutation(key("collection/key"), Precondition.NONE),
            new DeleteMutation(key("collection/key"), Precondition.NONE),
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
            new DeleteMutation(key("collection/key"), Precondition.NONE),
            patchMutation(
                "collection/key",
                map("foo", "foo-patched-value", "bar.baz", FieldValue.increment(1))),
            patchMutation(
                "collection/key", map("foo", "foo-patched-value", "bar.baz", FieldValue.delete())),
            patchMutation(
                "collection/key",
                map("foo", "foo-patched-value", "bar.baz", FieldValue.serverTimestamp())),
            mergeMutation(
                "collection/key",
                map("arrays", FieldValue.arrayUnion(1, 2, 3)),
                Arrays.asList(field("arrays"))));

    // Take all possible combinations of the subsets of the mutation list, run each combination for
    // all possible permutation, for all 3 different type of documents.
    int caseNumber = 0;
    for (int subsetSize = 0; subsetSize <= mutations.size(); ++subsetSize) {
      Set<Set<Mutation>> combinations = Sets.combinations(Sets.newHashSet(mutations), subsetSize);
      for (Set<Mutation> combination : combinations) {
        caseNumber += runPermutationTests(docs, Lists.newArrayList(combination));
      }
    }

    // There are (0! + 7*1! + 21*2! + 35*3! + 35*4! + 21*5! + 7*6! + 7!) * 3 = 41100 cases.
    assertEquals(41100, caseNumber);
  }

  @Test
  public void testSquashMutationByCombinationsAndPermutations_ArrayTransforms() {
    List<MutableDocument> docs =
        Lists.newArrayList(
            doc("collection/key", 0, map("foo", "foo-value", "bar.baz", 1)),
            deletedDoc("collection/key", 0),
            unknownDoc("collection/key", 0));
    List<Mutation> mutations =
        Lists.newArrayList(
            setMutation("collection/key", map("bar", "bar-value")),
            mergeMutation(
                "collection/key",
                map("foo", "xxx", "arrays", FieldValue.arrayRemove(2)),
                Arrays.asList(field("foo"))),
            new DeleteMutation(key("collection/key"), Precondition.NONE),
            patchMutation(
                "collection/key",
                map("foo", "foo-patched-value-1", "arrays", FieldValue.arrayUnion(4, 5))),
            patchMutation(
                "collection/key",
                map("foo", "foo-patched-value-2", "arrays", FieldValue.arrayRemove(5, 6))),
            mergeMutation(
                "collection/key",
                map("foo", "yyy", "arrays", FieldValue.arrayUnion(1, 2, 3, 999)),
                Arrays.asList(field("foo"))));

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

  // For each document in `docs`, calculate the squashed mutations of each possible permutation,
  // check whether this holds: document + squashed_mutation = document + mutation_list
  // Returns how many cases it has run.
  private int runPermutationTests(List<MutableDocument> docs, List<Mutation> mutations) {
    Timestamp now = Timestamp.now();
    int caseNumber = 0;
    List<List<Mutation>> permutations = generatePermutations(Lists.newArrayList(mutations));
    for (MutableDocument doc : docs) {
      for (List<Mutation> permutation : permutations) {
        MutableDocument forReport = doc.clone();
        MutableDocument document = doc.clone();
        MutableDocument docCopy = document.clone();
        MutationSquasher squasher = new MutationSquasher(doc.getKey(), doc.isFoundDocument());
        for (Mutation mutation : permutation) {
          mutation.applyToLocalView(document, now);
          squasher.squash(mutation);
        }
        if (squasher.getMutation() != null) {
          squasher.getMutation().applyToLocalView(docCopy, now);
        }
        assertEquals(
            getDescription(forReport, permutation, squasher.getMutation()), document, docCopy);

        caseNumber += 1;
      }
    }
    return caseNumber;
  }

  private String getDescription(
      MutableDocument document, List<Mutation> mutations, Mutation squashed) {
    StringBuilder builder = new StringBuilder();
    builder.append("MutationSquash test failed with:\n");
    builder.append("document:\n");
    builder.append(document + "\n");
    builder.append("\n");

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
