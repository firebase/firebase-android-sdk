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

import androidx.annotation.Nullable;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.model.mutation.DeleteMutation;
import com.google.firebase.firestore.model.mutation.Mutation;
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
public class MutationSquashTest {
  @Test
  public void testSquashOneSetMutation() {
    Map<String, Object> data = map("foo", "foo-value", "baz", "baz-value");
    squashRoundTrips(
        doc("collection/key", 0, data), setMutation("collection/key", map("bar", "bar-value")));
  }

  @Test
  public void testSquashOnePatchMutation() {
    Map<String, Object> data = map("foo", map("bar", "bar-value"), "baz", "baz-value");
    squashRoundTrips(
        doc("collection/key", 0, data),
        patchMutation("collection/key", map("foo.bar", "new-bar-value")));
  }

  @Test
  public void testSquashWithPatchWithMerge() {
    Mutation upsert =
        mergeMutation(
            "collection/key", map("foo.bar", "new-bar-value"), Arrays.asList(field("foo.bar")));
    squashRoundTrips(deletedDoc("collection/key", 0), upsert);
  }

  @Test
  public void testSquashWithDeleteThenPatch() {
    MutableDocument doc = doc("collection/key", 0, map("foo", 1));
    Mutation delete = new DeleteMutation(key("collection/key"), Precondition.NONE);
    Mutation patch = patchMutation("collection/key", map("foo.bar", "new-bar-value"));

    squashRoundTrips(doc, delete, patch);
  }

  @Test
  public void testSquashWithDeleteThenMerge() {
    MutableDocument doc = doc("collection/key", 0, map("foo", 1));
    Mutation delete = new DeleteMutation(key("collection/key"), Precondition.NONE);
    Mutation patch =
        mergeMutation(
            "collection/key", map("foo.bar", "new-bar-value"), Arrays.asList(field("foo.bar")));

    squashRoundTrips(doc, delete, patch);
  }

  @Test
  public void testSquashPatchThenPatchToDeleteField() {
    MutableDocument doc = doc("collection/key", 0, map("foo", 1));
    Mutation patch =
        patchMutation(
            "collection/key", map("foo", "foo-patched-value", "bar.baz", FieldValue.increment(1)));
    Mutation patchToDeleteField =
        patchMutation(
            "collection/key", map("foo", "foo-patched-value", "bar.baz", FieldValue.delete()));

    squashRoundTrips(doc, patch, patchToDeleteField);
  }

  @Test
  public void testSquashPatchThenMerge() {
    MutableDocument doc = doc("collection/key", 0, map("foo", 1));
    Mutation patch =
        patchMutation(
            "collection/key", map("foo", "foo-patched-value", "bar.baz", FieldValue.increment(1)));
    Mutation merge =
        mergeMutation(
            "collection/key",
            map("arrays", FieldValue.arrayUnion(1, 2, 3)),
            Arrays.asList(field("arrays")));

    squashRoundTrips(doc, patch, merge);
  }

  @Test
  public void testSquashArrayUnionThenRemove() {
    MutableDocument doc = doc("collection/key", 0, map("foo", 1));
    Mutation union =
        mergeMutation(
            "collection/key", map("arrays", FieldValue.arrayUnion(1, 2, 3)), Arrays.asList());
    Mutation remove =
        mergeMutation(
            "collection/key",
            map("foo", "xxx", "arrays", FieldValue.arrayRemove(2)),
            Arrays.asList(field("foo")));

    squashRoundTrips(doc, union, remove);
  }

  @Test
  public void testSquashSetThenIncrement() {
    MutableDocument doc = doc("collection/key", 0, map("foo", 1));
    Mutation set = setMutation("collection/key", map("foo", 2));
    Mutation update = patchMutation("collection/key", map("foo", FieldValue.increment(2)));

    squashRoundTrips(doc, set, update);
  }

  @Test
  public void testSquashSetThenPatchOnDeletedDoc() {
    MutableDocument doc = deletedDoc("collection/key", 0);
    Mutation set = setMutation("collection/key", map("bar", "bar-value"));
    Mutation patch =
        patchMutation(
            "collection/key",
            map("foo", "foo-patched-value", "bar.baz", FieldValue.serverTimestamp()));

    squashRoundTrips(doc, set, patch);
  }

  // TODO(overlay): This test fails because deleting nested fields like bar.baz leaves an empty bar
  // without
  // squashing. With squashing bar.baz or bar is completely gone. May not be an actual issue, but we
  // need
  // to resolve this before merge.
  @Test
  public void testSquashFieldDeletionOfNestedField() {
    MutableDocument doc = doc("collection/key", 0, map("bar.baz", 1));
    Mutation patch1 =
        patchMutation(
            "collection/key", map("foo", "foo-patched-value", "bar.baz", FieldValue.increment(1)));
    Mutation patch2 =
        patchMutation(
            "collection/key",
            map("foo", "foo-patched-value", "bar.baz", FieldValue.serverTimestamp()));
    Mutation patch3 =
        patchMutation(
            "collection/key", map("foo", "foo-patched-value", "bar.baz", FieldValue.delete()));

    squashRoundTrips(doc, patch1, patch2, patch3);
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
            doc("collection/key", 0, map("foo", "foo-value", "bar", 1)),
            deletedDoc("collection/key", 0),
            unknownDoc("collection/key", 0));
    List<Mutation> mutations =
        Lists.newArrayList(
            setMutation("collection/key", map("bar", "bar-value")),
            setMutation("collection/key", map("bar.rab", "bar.rab-value")),
            new DeleteMutation(key("collection/key"), Precondition.NONE),
            patchMutation(
                "collection/key",
                map("foo", "foo-patched-value-incr", "bar", FieldValue.increment(1))),
            patchMutation(
                "collection/key",
                map("foo", "foo-patched-value-delete", "bar", FieldValue.delete())),
            patchMutation(
                "collection/key",
                map("foo", "foo-patched-value-st", "bar", FieldValue.serverTimestamp())),
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

  @Test
  public void testSquashMutationByCombinationsAndPermutations_Increments() {
    List<MutableDocument> docs =
        Lists.newArrayList(
            doc("collection/key", 0, map("foo", "foo-value", "bar", 1)),
            deletedDoc("collection/key", 0),
            unknownDoc("collection/key", 0));
    List<Mutation> mutations =
        Lists.newArrayList(
            setMutation("collection/key", map("bar", "bar-value")),
            mergeMutation(
                "collection/key",
                map("foo", "foo-merge", "bar", FieldValue.increment(2)),
                Arrays.asList(field("foo"))),
            new DeleteMutation(key("collection/key"), Precondition.NONE),
            patchMutation(
                "collection/key",
                map("foo", "foo-patched-value-1", "bar", FieldValue.increment(-1.4))),
            patchMutation(
                "collection/key",
                map("foo", "foo-patched-value-2", "bar", FieldValue.increment(3.3))),
            mergeMutation(
                "collection/key",
                map("foo", "yyy", "bar", FieldValue.increment(-41)),
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
        Mutation squashed = null;
        for (Mutation mutation : permutation) {
          mutation.applyToLocalView(document, now);
          squashed = mutation.squash(docCopy, squashed, now);
        }
        if (squashed != null) {
          squashed.applyToLocalView(docCopy, now);
        }
        assertEquals(getDescription(forReport, permutation, squashed), document, docCopy);

        caseNumber += 1;
      }
    }
    return caseNumber;
  }

  private String getDescription(
      MutableDocument document, List<Mutation> mutations, @Nullable Mutation squashed) {
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
    builder.append(squashed == null ? "null" : squashed);
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

  private void squashRoundTrips(MutableDocument doc, Mutation... mutations) {
    MutableDocument toApplySquashedMutation = doc.clone();
    Timestamp now = Timestamp.now();

    Mutation squashed = null;
    for (Mutation m : mutations) {
      m.applyToLocalView(doc, now);
      squashed = m.squash(toApplySquashedMutation, squashed, now);
    }

    squashed.applyToLocalView(toApplySquashedMutation, now);

    assertEquals(doc, toApplySquashedMutation);
  }
}
