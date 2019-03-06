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

package com.google.firebase.firestore.local;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.firestore.testutil.TestUtil.key;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.patchMutation;
import static com.google.firebase.firestore.testutil.TestUtil.path;
import static com.google.firebase.firestore.testutil.TestUtil.setMutation;
import static com.google.firebase.firestore.testutil.TestUtil.streamToken;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.firebase.Timestamp;
import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.MutationBatch;
import com.google.firebase.firestore.model.mutation.SetMutation;
import com.google.firebase.firestore.remote.WriteStream;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

/**
 * These are tests for any implementation of the MutationQueue interface.
 *
 * <p>To test a specific implementation of MutationQueue:
 *
 * <ol>
 *   <li>Subclass MutationQueueTestCase.
 *   <li>Override {@link #getPersistence}, creating a new implementation of Persistence.
 * </ol>
 */
public abstract class MutationQueueTestCase {

  @Rule public TestName name = new TestName();

  private Persistence persistence;
  private MutationQueue mutationQueue;

  @Before
  public void setUp() {
    persistence = getPersistence();
    persistence.getReferenceDelegate().setInMemoryPins(new ReferenceSet());
    mutationQueue = persistence.getMutationQueue(User.UNAUTHENTICATED);
    mutationQueue.start();
  }

  @After
  public void tearDown() {
    persistence.shutdown();
  }

  abstract Persistence getPersistence();

  @Test
  public void testCountBatches() {
    assertEquals(0, batchCount());
    assertTrue(mutationQueue.isEmpty());

    MutationBatch batch1 = addMutationBatch();
    assertEquals(1, batchCount());
    assertFalse(mutationQueue.isEmpty());

    MutationBatch batch2 = addMutationBatch();
    assertEquals(2, batchCount());
    assertFalse(mutationQueue.isEmpty());

    removeMutationBatches(batch1);
    assertEquals(1, batchCount());

    removeMutationBatches(batch2);
    assertEquals(0, batchCount());
    assertTrue(mutationQueue.isEmpty());
  }

  @Test
  public void testAcknowledgeThenRemove() {
    MutationBatch batch1 = addMutationBatch();

    persistence.runTransaction(
        name.getMethodName(),
        () -> {
          mutationQueue.acknowledgeBatch(batch1, WriteStream.EMPTY_STREAM_TOKEN);
          mutationQueue.removeMutationBatch(batch1);
        });

    assertEquals(0, batchCount());
  }

  @Test
  public void testLookupMutationBatch() {
    // Searching on an empty queue should not find a non-existent batch
    MutationBatch notFound = mutationQueue.lookupMutationBatch(42);
    assertNull(notFound);

    List<MutationBatch> batches = createBatches(10);
    List<MutationBatch> removed = removeFirstBatches(3, batches);

    // After removing, a batch should not be found
    for (MutationBatch batch : removed) {
      notFound = mutationQueue.lookupMutationBatch(batch.getBatchId());
      assertNull(notFound);
    }

    // Remaining entries should still be found
    for (MutationBatch batch : batches) {
      MutationBatch found = mutationQueue.lookupMutationBatch(batch.getBatchId());
      assertNotNull(found);
      assertEquals(batch.getBatchId(), found.getBatchId());
    }

    // Even on a nonempty queue searching should not find a non-existent batch
    notFound = mutationQueue.lookupMutationBatch(42);
    assertNull(notFound);
  }

  @Test
  public void testNextMutationBatchAfterBatchId() {
    List<MutationBatch> batches = createBatches(10);
    List<MutationBatch> removed = removeFirstBatches(3, batches);

    for (int i = 0; i < batches.size() - 1; i++) {
      MutationBatch current = batches.get(i);
      MutationBatch next = batches.get(i + 1);
      MutationBatch found = mutationQueue.getNextMutationBatchAfterBatchId(current.getBatchId());
      assertNotNull(found);
      assertEquals(next.getBatchId(), found.getBatchId());
    }

    for (int i = 0; i < removed.size(); i++) {
      MutationBatch current = removed.get(i);
      MutationBatch next = batches.get(0);
      MutationBatch found = mutationQueue.getNextMutationBatchAfterBatchId(current.getBatchId());
      assertNotNull(found);
      assertEquals(next.getBatchId(), found.getBatchId());
    }

    MutationBatch first = batches.get(0);
    MutationBatch found = mutationQueue.getNextMutationBatchAfterBatchId(first.getBatchId() - 42);
    assertNotNull(found);
    assertEquals(first.getBatchId(), found.getBatchId());

    MutationBatch last = batches.get(batches.size() - 1);
    MutationBatch notFound = mutationQueue.getNextMutationBatchAfterBatchId(last.getBatchId());
    assertNull(notFound);
  }

  @Test
  public void testAllMutationBatchesAffectingDocumentKey() {
    List<Mutation> mutations =
        asList(
            setMutation("fob/bar", map("a", 1)),
            setMutation("foo/bar", map("a", 1)),
            patchMutation("foo/bar", map("b", 1)),
            setMutation("foo/bar/suffix/key", map("a", 1)),
            setMutation("foo/baz", map("a", 1)),
            setMutation("food/bar", map("a", 1)));

    // Store all the mutations.
    List<MutationBatch> batches = new ArrayList<>();
    persistence.runTransaction(
        "New mutation batch",
        () -> {
          for (Mutation mutation : mutations) {
            batches.add(
                mutationQueue.addMutationBatch(
                    Timestamp.now(), Collections.emptyList(), asList(mutation)));
          }
        });

    List<MutationBatch> expected = asList(batches.get(1), batches.get(2));
    List<MutationBatch> matches =
        mutationQueue.getAllMutationBatchesAffectingDocumentKey(key("foo/bar"));

    assertEquals(expected, matches);
  }

  @Test
  public void testAllMutationBatchesAffectingDocumentKeys() {
    List<Mutation> mutations =
        asList(
            setMutation("fob/bar", map("a", 1)),
            setMutation("foo/bar", map("a", 1)),
            patchMutation("foo/bar", map("b", 1)),
            setMutation("foo/bar/suffix/key", map("a", 1)),
            setMutation("foo/baz", map("a", 1)),
            setMutation("food/bar", map("a", 1)));

    // Store all the mutations.
    List<MutationBatch> batches = new ArrayList<>();
    persistence.runTransaction(
        "New mutation batch",
        () -> {
          for (Mutation mutation : mutations) {
            batches.add(
                mutationQueue.addMutationBatch(
                    Timestamp.now(), Collections.emptyList(), asList(mutation)));
          }
        });

    ImmutableSortedSet<DocumentKey> keys =
        DocumentKey.emptyKeySet().insert(key("foo/bar")).insert(key("foo/baz"));

    List<MutationBatch> expected = asList(batches.get(1), batches.get(2), batches.get(4));
    List<MutationBatch> matches = mutationQueue.getAllMutationBatchesAffectingDocumentKeys(keys);

    assertEquals(expected, matches);
  }

  // PORTING NOTE: this test only applies to Android, because it's the only platform where the
  // implementation of getAllMutationBatchesAffectingDocumentKeys might split the input into several
  // queries.
  @Test
  public void testAllMutationBatchesAffectingDocumentLotsOfDocumentKeys() {
    List<Mutation> mutations = new ArrayList<>();
    // Make sure to force SQLite implementation to split the large query into several smaller ones.
    int lotsOfMutations = 2000;
    for (int i = 0; i < lotsOfMutations; i++) {
      mutations.add(setMutation("foo/" + i, map("a", 1)));
    }
    List<MutationBatch> batches = new ArrayList<>();
    persistence.runTransaction(
        "New mutation batch",
        () -> {
          for (Mutation mutation : mutations) {
            batches.add(
                mutationQueue.addMutationBatch(
                    Timestamp.now(), Collections.emptyList(), asList(mutation)));
          }
        });

    // To make it easier validating the large resulting set, use a simple criteria to evaluate --
    // query all keys with an even number in them and make sure the corresponding batches make it
    // into the results.
    ImmutableSortedSet<DocumentKey> evenKeys = DocumentKey.emptyKeySet();
    List<MutationBatch> expected = new ArrayList<>();
    for (int i = 2; i < lotsOfMutations; i += 2) {
      evenKeys = evenKeys.insert(key("foo/" + i));
      expected.add(batches.get(i));
    }

    List<MutationBatch> matches =
        mutationQueue.getAllMutationBatchesAffectingDocumentKeys(evenKeys);
    assertThat(matches).containsExactlyElementsIn(expected).inOrder();
  }

  @Test
  public void testAllMutationBatchesAffectingQuery() {
    List<Mutation> mutations =
        asList(
            setMutation("fob/bar", map("a", 1)),
            setMutation("foo/bar", map("a", 1)),
            patchMutation("foo/bar", map("b", 1)),
            setMutation("foo/bar/suffix/key", map("a", 1)),
            setMutation("foo/baz", map("a", 1)),
            setMutation("food/bar", map("a", 1)));

    // Store all the mutations.
    List<MutationBatch> batches = new ArrayList<>();
    persistence.runTransaction(
        "New mutation batch",
        () -> {
          for (Mutation mutation : mutations) {
            batches.add(
                mutationQueue.addMutationBatch(
                    Timestamp.now(), Collections.emptyList(), asList(mutation)));
          }
        });

    List<MutationBatch> expected = asList(batches.get(1), batches.get(2), batches.get(4));

    Query query = Query.atPath(path("foo"));
    List<MutationBatch> matches = mutationQueue.getAllMutationBatchesAffectingQuery(query);

    assertEquals(expected, matches);
  }

  @Test
  public void testAllMutationBatchesAffectingQuery_withCompoundBatches() {
    Map<String, Object> value = map("a", 1);

    // Store all the mutations.
    List<MutationBatch> batches = new ArrayList<>();
    persistence.runTransaction(
        "New mutation batch",
        () -> {
          batches.add(
              mutationQueue.addMutationBatch(
                  Timestamp.now(),
                  Collections.emptyList(),
                  asList(setMutation("foo/bar", value), setMutation("foo/bar/baz/quux", value))));
          batches.add(
              mutationQueue.addMutationBatch(
                  Timestamp.now(),
                  Collections.emptyList(),
                  asList(setMutation("foo/bar", value), setMutation("foo/baz", value))));
        });

    List<MutationBatch> expected = asList(batches.get(0), batches.get(1));

    Query query = Query.atPath(path("foo"));
    List<MutationBatch> matches = mutationQueue.getAllMutationBatchesAffectingQuery(query);

    assertEquals(expected, matches);
  }

  @Test
  public void testRemoveMutationBatches() {
    List<MutationBatch> batches = createBatches(10);

    removeMutationBatches(batches.remove(0));
    assertEquals(9, batchCount());

    List<MutationBatch> found;

    found = mutationQueue.getAllMutationBatches();
    assertEquals(batches, found);
    assertEquals(9, found.size());

    removeMutationBatches(batches.get(0), batches.get(1), batches.get(2));
    batches.remove(batches.get(0));
    batches.remove(batches.get(0));
    batches.remove(batches.get(0));
    assertEquals(6, batchCount());

    found = mutationQueue.getAllMutationBatches();
    assertEquals(batches, found);
    assertEquals(6, found.size());

    removeMutationBatches(batches.remove(0));
    assertEquals(5, batchCount());

    found = mutationQueue.getAllMutationBatches();
    assertEquals(batches, found);
    assertEquals(5, found.size());

    removeMutationBatches(batches.remove(0));
    assertEquals(4, batchCount());

    removeMutationBatches(batches.remove(0));
    assertEquals(3, batchCount());

    found = mutationQueue.getAllMutationBatches();
    assertEquals(batches, found);
    assertEquals(3, found.size());

    removeMutationBatches(batches.toArray(new MutationBatch[0]));
    found = mutationQueue.getAllMutationBatches();
    assertEquals(emptyList(), found);
    assertEquals(0, found.size());
    assertTrue(mutationQueue.isEmpty());
  }

  @Test
  public void testStreamToken() {
    ByteString streamToken1 = streamToken("token1");
    ByteString streamToken2 = streamToken("token2");

    persistence.runTransaction(
        "initial stream token", () -> mutationQueue.setLastStreamToken(streamToken1));

    MutationBatch batch1 = addMutationBatch();
    addMutationBatch();

    assertEquals(streamToken1, mutationQueue.getLastStreamToken());

    persistence.runTransaction(
        "acknowledgeBatchId", () -> mutationQueue.acknowledgeBatch(batch1, streamToken2));

    assertEquals(streamToken2, mutationQueue.getLastStreamToken());
  }

  /** Creates a new MutationBatch with the next batch ID and a set of dummy mutations. */
  private MutationBatch addMutationBatch() {
    return addMutationBatch("foo/bar");
  }

  /**
   * Creates a new MutationBatch with the given key, the next batch ID and a set of dummy mutations.
   */
  private MutationBatch addMutationBatch(String key) {
    SetMutation mutation = setMutation(key, map("a", 1));

    return persistence.runTransaction(
        "New mutation batch",
        () ->
            mutationQueue.addMutationBatch(
                Timestamp.now(), Collections.emptyList(), asList(mutation)));
  }

  /**
   * Creates a list of batches containing <tt>number</tt> dummy MutationBatches. Each has a
   * different batchId.
   */
  private List<MutationBatch> createBatches(int number) {
    List<MutationBatch> batches = new ArrayList<>(number);
    for (int i = 0; i < number; i++) {
      batches.add(addMutationBatch());
    }
    return batches;
  }

  private void acknowledgeBatch(MutationBatch batch) {
    persistence.runTransaction(
        "Ack batchId", () -> mutationQueue.acknowledgeBatch(batch, WriteStream.EMPTY_STREAM_TOKEN));
  }

  /** Calls removeMutationBatches on the mutation queue in a new transaction and commits. */
  private void removeMutationBatches(MutationBatch... batches) {
    persistence.runTransaction(
        "Remove mutation batches",
        () -> {
          for (MutationBatch batch : batches) {
            mutationQueue.removeMutationBatch(batch);
          }
        });
  }

  /** Returns the number of mutation batches in the mutation queue. */
  private int batchCount() {
    return mutationQueue.getAllMutationBatches().size();
  }

  /**
   * Removes the first n entries from the given <tt>batches</tt> and returns them.
   *
   * @param n The number of batches to remove..
   * @param batches The list to mutate, removing entries from it.
   * @return A new list containing all the entries that were removed from @a batches.
   */
  private List<MutationBatch> removeFirstBatches(int n, List<MutationBatch> batches) {
    List<MutationBatch> removed = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      MutationBatch batch = batches.get(0);
      removeMutationBatches(batch);
      batches.remove(0);
      removed.add(batch);
    }
    return removed;
  }
}
