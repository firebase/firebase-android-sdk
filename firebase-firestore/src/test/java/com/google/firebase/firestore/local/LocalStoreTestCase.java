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

import static com.google.firebase.firestore.testutil.TestUtil.addedRemoteEvent;
import static com.google.firebase.firestore.testutil.TestUtil.assertSetEquals;
import static com.google.firebase.firestore.testutil.TestUtil.deleteMutation;
import static com.google.firebase.firestore.testutil.TestUtil.deletedDoc;
import static com.google.firebase.firestore.testutil.TestUtil.doc;
import static com.google.firebase.firestore.testutil.TestUtil.docMap;
import static com.google.firebase.firestore.testutil.TestUtil.filter;
import static com.google.firebase.firestore.testutil.TestUtil.key;
import static com.google.firebase.firestore.testutil.TestUtil.keySet;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.mergeMutation;
import static com.google.firebase.firestore.testutil.TestUtil.noChangeEvent;
import static com.google.firebase.firestore.testutil.TestUtil.orderBy;
import static com.google.firebase.firestore.testutil.TestUtil.patchMutation;
import static com.google.firebase.firestore.testutil.TestUtil.query;
import static com.google.firebase.firestore.testutil.TestUtil.resumeToken;
import static com.google.firebase.firestore.testutil.TestUtil.setMutation;
import static com.google.firebase.firestore.testutil.TestUtil.unknownDoc;
import static com.google.firebase.firestore.testutil.TestUtil.updateRemoteEvent;
import static com.google.firebase.firestore.testutil.TestUtil.values;
import static com.google.firebase.firestore.testutil.TestUtil.version;
import static com.google.firebase.firestore.testutil.TestUtil.viewChanges;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.firebase.Timestamp;
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.bundle.BundleMetadata;
import com.google.firebase.firestore.bundle.BundledQuery;
import com.google.firebase.firestore.bundle.NamedQuery;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.core.Target;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.MutationBatch;
import com.google.firebase.firestore.model.mutation.MutationBatchResult;
import com.google.firebase.firestore.model.mutation.MutationResult;
import com.google.firebase.firestore.model.mutation.SetMutation;
import com.google.firebase.firestore.remote.RemoteEvent;
import com.google.firebase.firestore.remote.WatchStream;
import com.google.firebase.firestore.remote.WriteStream;
import com.google.firebase.firestore.testutil.TestUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * These are tests for any implementation of the LocalStore.
 *
 * <p>To test a specific implementation of LocalStore:
 *
 * <ol>
 *   <li>Subclass LocalStoreTestCase
 *   <li>override {@link #getPersistence}, creating a new instance of Persistence.
 * </ol>
 */
public abstract class LocalStoreTestCase {
  private CountingQueryEngine queryEngine;
  private Persistence localStorePersistence;
  private LocalStore localStore;

  private List<MutationBatch> batches;
  private @Nullable ImmutableSortedMap<DocumentKey, Document> lastChanges;
  private @Nullable QueryResult lastQueryResult;
  private int lastTargetId;

  abstract Persistence getPersistence();

  abstract boolean garbageCollectorIsEager();

  @Before
  public void setUp() {
    batches = new ArrayList<>();
    lastChanges = null;
    lastQueryResult = null;
    lastTargetId = 0;

    localStorePersistence = getPersistence();
    queryEngine = new CountingQueryEngine(new DefaultQueryEngine());
    localStore = new LocalStore(localStorePersistence, queryEngine, User.UNAUTHENTICATED);
    localStore.start();
  }

  @After
  public void tearDown() {
    localStorePersistence.shutdown();
  }

  private void writeMutation(Mutation mutation) {
    writeMutations(asList(mutation));
  }

  private void writeMutations(List<Mutation> mutations) {
    LocalWriteResult result = localStore.writeLocally(mutations);
    batches.add(
        new MutationBatch(
            result.getBatchId(), Timestamp.now(), Collections.emptyList(), mutations));
    lastChanges = result.getChanges();
  }

  private void applyRemoteEvent(RemoteEvent event) {
    lastChanges = localStore.applyRemoteEvent(event);
  }

  private void notifyLocalViewChanges(LocalViewChanges changes) {
    localStore.notifyLocalViewChanges(asList(changes));
  }

  private void udpateViews(int targetId, boolean fromCache) {
    notifyLocalViewChanges(viewChanges(targetId, fromCache, asList(), asList()));
  }

  private void acknowledgeMutation(long documentVersion, @Nullable Object transformResult) {
    MutationBatch batch = batches.remove(0);
    SnapshotVersion version = version(documentVersion);
    MutationResult mutationResult =
        new MutationResult(
            version,
            transformResult != null
                ? Collections.singletonList(TestUtil.wrap(transformResult))
                : Collections.emptyList());
    MutationBatchResult result =
        MutationBatchResult.create(
            batch, version, singletonList(mutationResult), WriteStream.EMPTY_STREAM_TOKEN);
    lastChanges = localStore.acknowledgeBatch(result);
  }

  private void acknowledgeMutation(long documentVersion) {
    acknowledgeMutation(documentVersion, null);
  }

  private void rejectMutation() {
    MutationBatch batch = batches.get(0);
    batches.remove(0);
    lastChanges = localStore.rejectBatch(batch.getBatchId());
  }

  private int allocateQuery(Query query) {
    TargetData targetData = localStore.allocateTarget(query.toTarget());
    lastTargetId = targetData.getTargetId();
    return targetData.getTargetId();
  }

  private void executeQuery(Query query) {
    resetPersistenceStats();
    lastQueryResult = localStore.executeQuery(query, /* usePreviousResults= */ true);
  }

  private void releaseTarget(int targetId) {
    localStore.releaseTarget(targetId);
  }

  private void saveBundle(String bundleId, int createTime) {
    localStore.saveBundle(
        new BundleMetadata(
            bundleId,
            /* version= */ 1,
            version(createTime),
            /* totalDocuments= */ 1,
            /* totalBytes= */ 10));
  }

  private void bundleDocuments(MutableDocument... expected) {
    ImmutableSortedMap<DocumentKey, MutableDocument> documents = docMap(expected);
    lastChanges = localStore.applyBundledDocuments(documents, /* bundleId= */ "");
  }

  private void saveNamedQuery(NamedQuery namedQuery, DocumentKey... matchingKey) {
    localStore.saveNamedQuery(namedQuery, keySet(matchingKey));
  }

  /** Asserts that the last target ID is the given number. */
  private void assertTargetId(int targetId) {
    assertEquals(targetId, lastTargetId);
  }

  /** Asserts that a the lastChanges contain the docs in the given array. */
  private void assertChanged(MutableDocument... expected) {
    assertNotNull(lastChanges);

    List<Document> actualList =
        Lists.newArrayList(Iterables.transform(lastChanges, Entry::getValue));

    List<MutableDocument> expectedList = asList(expected);
    Collections.sort(expectedList, (d1, d2) -> d1.getKey().compareTo(d2.getKey()));

    assertEquals(expectedList, actualList);

    lastChanges = null;
  }

  /** Asserts that the given keys were removed. */
  private void assertRemoved(String... keyPaths) {
    assertNotNull(lastChanges);

    ImmutableSortedMap<DocumentKey, Document> actual = lastChanges;
    assertEquals(keyPaths.length, actual.size());
    int i = 0;
    for (Entry<DocumentKey, Document> actualEntry : actual) {
      assertEquals(key(keyPaths[i++]), actualEntry.getKey());
      assertFalse(actualEntry.getValue().isFoundDocument());
    }
    lastChanges = null;
  }

  /** Asserts that the given local store contains the given document. */
  private void assertContains(MutableDocument expected) {
    Document actual = localStore.readDocument(expected.getKey());
    assertEquals(expected, actual);
  }

  /** Asserts that the given local store does not contain the given document. */
  private void assertNotContains(String keyPathString) {
    DocumentKey key = DocumentKey.fromPathString(keyPathString);
    Document actual = localStore.readDocument(key);
    assertFalse(actual.isValidDocument());
  }

  private void assertQueryReturned(String... keys) {
    assertNotNull(lastQueryResult);
    ImmutableSortedMap<DocumentKey, Document> documents = lastQueryResult.getDocuments();
    for (String key : keys) {
      assertTrue("Expected query to return: " + key, documents.containsKey(key(key)));
    }
    assertEquals(documents.size(), keys.length);
  }

  private void assertQueryDocumentMapping(int targetId, DocumentKey... keys) {
    ImmutableSortedSet<DocumentKey> expectedKeys = keySet(keys);
    ImmutableSortedSet<DocumentKey> actualKeys = localStore.getRemoteDocumentKeys(targetId);
    assertEquals(expectedKeys, actualKeys);
  }

  private void assertHasNewerBundle(String bundleId, int createTime) {
    boolean hasNewerBundle =
        localStore.hasNewerBundle(
            new BundleMetadata(
                bundleId,
                /* version= */ 1,
                version(createTime),
                /* totalDocuments= */ 1,
                /* totalBytes= */ 10));
    assertTrue(hasNewerBundle);
  }

  private void assertNoNewerBundle(String bundleId, int createTime) {
    boolean hasNewerBundle =
        localStore.hasNewerBundle(
            new BundleMetadata(
                bundleId,
                /* version= */ 1,
                version(createTime),
                /* totalDocuments= */ 1,
                /* totalBytes= */ 10));
    assertFalse(hasNewerBundle);
  }

  private void assertHasNamedQuery(NamedQuery expectedNamedQuery) {
    NamedQuery actualNamedQuery = localStore.getNamedQuery(expectedNamedQuery.getName());
    assertEquals(expectedNamedQuery, actualNamedQuery);
  }

  /**
   * Asserts the expected numbers of mutations read by the MutationQueue since the last call to
   * `resetPersistenceStats()`.
   */
  private void assertMutationsRead(int byKey, int byQuery) {
    assertEquals("Mutations read (by query)", byQuery, queryEngine.getMutationsReadByQuery());
    assertEquals("Mutations read (by key)", byKey, queryEngine.getMutationsReadByKey());
  }

  /**
   * Asserts the expected numbers of documents read by the RemoteDocumentCache since the last call
   * to `resetPersistenceStats()`.
   */
  private void assertRemoteDocumentsRead(int byKey, int byQuery) {
    assertEquals(
        "Remote documents read (by query)", byQuery, queryEngine.getDocumentsReadByQuery());
    assertEquals("Remote documents read (by key)", byKey, queryEngine.getDocumentsReadByKey());
  }

  /** Resets the count of entities read by MutationQueue and the RemoteDocumentCache. */
  private void resetPersistenceStats() {
    queryEngine.resetCounts();
  }

  @Test
  public void testMutationBatchKeys() {
    SetMutation set1 = setMutation("foo/bar", map("foo", "bar"));
    SetMutation set2 = setMutation("foo/baz", map("foo", "baz"));
    MutationBatch batch =
        new MutationBatch(1, Timestamp.now(), Collections.emptyList(), asList(set1, set2));
    Set<DocumentKey> keys = batch.getKeys();
    assertEquals(2, keys.size());
  }

  @Test
  public void testHandlesSetMutation() {
    writeMutation(setMutation("foo/bar", map("foo", "bar")));
    assertChanged(doc("foo/bar", 0, map("foo", "bar")).setHasLocalMutations());
    assertContains(doc("foo/bar", 0, map("foo", "bar")).setHasLocalMutations());

    acknowledgeMutation(1);
    assertChanged(doc("foo/bar", 1, map("foo", "bar")).setHasCommittedMutations());
    if (garbageCollectorIsEager()) {
      // Nothing is pinning this anymore, as it has been acknowledged and there are no targets
      // active.
      assertNotContains("foo/bar");
    } else {
      assertContains(doc("foo/bar", 1, map("foo", "bar")).setHasCommittedMutations());
    }
  }

  @Test
  public void testHandlesSetMutationThenDocument() {
    writeMutation(setMutation("foo/bar", map("foo", "bar")));
    assertChanged(doc("foo/bar", 0, map("foo", "bar")).setHasLocalMutations());
    assertContains(doc("foo/bar", 0, map("foo", "bar")).setHasLocalMutations());

    Query query = Query.atPath(ResourcePath.fromString("foo"));
    int targetId = allocateQuery(query);
    applyRemoteEvent(
        updateRemoteEvent(
            doc("foo/bar", 2, map("it", "changed")).setHasLocalMutations(),
            asList(targetId),
            emptyList()));
    assertChanged(doc("foo/bar", 2, map("foo", "bar")).setHasLocalMutations());
    assertContains(doc("foo/bar", 2, map("foo", "bar")).setHasLocalMutations());
  }

  @Test
  public void testHandlesSetMutationThenAckThenRelease() {
    Query query = Query.atPath(ResourcePath.fromSegments(asList("foo")));
    allocateQuery(query);

    writeMutation(setMutation("foo/bar", map("foo", "bar")));
    notifyLocalViewChanges(viewChanges(2, /* fromCache= */ false, asList("foo/bar"), emptyList()));

    assertChanged(doc("foo/bar", 0, map("foo", "bar")).setHasLocalMutations());
    assertContains(doc("foo/bar", 0, map("foo", "bar")).setHasLocalMutations());

    acknowledgeMutation(1);

    assertChanged(doc("foo/bar", 1, map("foo", "bar")).setHasCommittedMutations());
    assertContains(doc("foo/bar", 1, map("foo", "bar")).setHasCommittedMutations());

    releaseTarget(2);

    // It has been acknowledged, and should no longer be retained as there is no target and mutation
    if (garbageCollectorIsEager()) {
      assertNotContains("foo/bar");
    } else {
      assertContains(doc("foo/bar", 1, map("foo", "bar")).setHasCommittedMutations());
    }
  }

  @Test
  public void testHandlesAckThenRejectThenRemoteEvent() {
    // Start a query that requires acks to be held.
    Query query = Query.atPath(ResourcePath.fromSegments(asList("foo")));
    int targetId = allocateQuery(query);

    writeMutation(setMutation("foo/bar", map("foo", "bar")));
    assertChanged(doc("foo/bar", 0, map("foo", "bar")).setHasLocalMutations());
    assertContains(doc("foo/bar", 0, map("foo", "bar")).setHasLocalMutations());

    // The last seen version is zero, so this ack must be held.
    acknowledgeMutation(1);
    assertChanged(doc("foo/bar", 1, map("foo", "bar")).setHasCommittedMutations());
    if (garbageCollectorIsEager()) {
      // Nothing is pinning this anymore, as it has been acknowledged and there are no targets
      // active.
      assertNotContains("foo/bar");
    } else {
      assertContains(doc("foo/bar", 1, map("foo", "bar")).setHasCommittedMutations());
    }

    writeMutation(setMutation("bar/baz", map("bar", "baz")));
    assertChanged(doc("bar/baz", 0, map("bar", "baz")).setHasLocalMutations());
    assertContains(doc("bar/baz", 0, map("bar", "baz")).setHasLocalMutations());

    rejectMutation();
    assertRemoved("bar/baz");
    assertNotContains("bar/baz");

    applyRemoteEvent(
        addedRemoteEvent(doc("foo/bar", 2, map("it", "changed")), asList(targetId), emptyList()));
    assertChanged(doc("foo/bar", 2, map("it", "changed")));
    assertContains(doc("foo/bar", 2, map("it", "changed")));
    assertNotContains("bar/baz");
  }

  @Test
  public void testHandlesDeletedDocumentThenSetMutationThenAck() {
    Query query = query("foo");
    int targetId = allocateQuery(query);
    applyRemoteEvent(updateRemoteEvent(deletedDoc("foo/bar", 2), asList(targetId), emptyList()));
    assertRemoved("foo/bar");
    // Under eager GC, there is no longer a reference for the document, and it should be
    // deleted.
    if (garbageCollectorIsEager()) {
      assertNotContains("foo/bar");
    } else {
      assertContains(deletedDoc("foo/bar", 2));
    }

    writeMutation(setMutation("foo/bar", map("foo", "bar")));
    assertChanged(doc("foo/bar", 0, map("foo", "bar")).setHasLocalMutations());
    assertContains(doc("foo/bar", 0, map("foo", "bar")).setHasLocalMutations());

    releaseTarget(targetId);
    acknowledgeMutation(3);
    assertChanged(doc("foo/bar", 3, map("foo", "bar")).setHasCommittedMutations());
    // It has been acknowledged, and should no longer be retained as there is no target and mutation
    if (garbageCollectorIsEager()) {
      assertNotContains("foo/bar");
    }
  }

  @Test
  public void testHandlesSetMutationThenDeletedDocument() {
    Query query = query("foo");
    int targetId = allocateQuery(query);
    writeMutation(setMutation("foo/bar", map("foo", "bar")));
    assertChanged(doc("foo/bar", 0, map("foo", "bar")).setHasLocalMutations());

    applyRemoteEvent(updateRemoteEvent(deletedDoc("foo/bar", 2), asList(targetId), emptyList()));
    assertChanged(doc("foo/bar", 0, map("foo", "bar")).setHasLocalMutations());
    assertContains(doc("foo/bar", 0, map("foo", "bar")).setHasLocalMutations());
  }

  @Test
  public void testHandlesDocumentThenSetMutationThenAckThenDocument() {
    Query query = Query.atPath(ResourcePath.fromString("foo"));
    int targetId = allocateQuery(query);
    applyRemoteEvent(
        addedRemoteEvent(doc("foo/bar", 2, map("it", "base")), asList(targetId), emptyList()));
    assertChanged(doc("foo/bar", 2, map("it", "base")));
    assertContains(doc("foo/bar", 2, map("it", "base")));

    writeMutation(setMutation("foo/bar", map("foo", "bar")));
    assertChanged(doc("foo/bar", 2, map("foo", "bar")).setHasLocalMutations());
    assertContains(doc("foo/bar", 2, map("foo", "bar")).setHasLocalMutations());

    acknowledgeMutation(3);
    // We haven't seen the remote event yet.
    assertChanged(doc("foo/bar", 3, map("foo", "bar")).setHasCommittedMutations());
    assertContains(doc("foo/bar", 3, map("foo", "bar")).setHasCommittedMutations());

    applyRemoteEvent(
        updateRemoteEvent(doc("foo/bar", 3, map("it", "changed")), asList(targetId), emptyList()));
    assertChanged(doc("foo/bar", 3, map("it", "changed")));
    assertContains(doc("foo/bar", 3, map("it", "changed")));
  }

  @Test
  public void testHandlesPatchWithoutPriorDocument() {
    writeMutation(patchMutation("foo/bar", map("foo", "bar")));
    assertRemoved("foo/bar");
    assertNotContains("foo/bar");

    acknowledgeMutation(1);
    assertChanged(unknownDoc("foo/bar", 1));

    if (garbageCollectorIsEager()) {
      // Nothing is pinning this anymore, as it has been acknowledged and there are no targets
      // active.
      assertNotContains("foo/bar");
    } else {
      assertContains(unknownDoc("foo/bar", 1));
    }
  }

  @Test
  public void testHandlesPatchMutationThenDocumentThenAck() {
    writeMutation(patchMutation("foo/bar", map("foo", "bar")));
    assertRemoved("foo/bar");
    assertNotContains("foo/bar");

    Query query = Query.atPath(ResourcePath.fromString("foo"));
    int targetId = allocateQuery(query);
    applyRemoteEvent(
        addedRemoteEvent(
            doc("foo/bar", 1, map("it", "base")).setHasLocalMutations(),
            asList(targetId),
            emptyList()));
    assertChanged(doc("foo/bar", 1, map("foo", "bar", "it", "base")).setHasLocalMutations());
    assertContains(doc("foo/bar", 1, map("foo", "bar", "it", "base")).setHasLocalMutations());

    acknowledgeMutation(2);

    assertChanged(doc("foo/bar", 2, map("foo", "bar", "it", "base")).setHasCommittedMutations());
    assertContains(doc("foo/bar", 2, map("foo", "bar", "it", "base")).setHasCommittedMutations());

    applyRemoteEvent(
        updateRemoteEvent(
            doc("foo/bar", 2, map("foo", "bar", "it", "base")), asList(targetId), emptyList()));
    assertChanged(doc("foo/bar", 2, map("foo", "bar", "it", "base")));
    assertContains(doc("foo/bar", 2, map("foo", "bar", "it", "base")));
  }

  @Test
  public void testHandlesPatchMutationThenAckThenDocument() {
    writeMutation(patchMutation("foo/bar", map("foo", "bar")));
    assertRemoved("foo/bar");
    assertNotContains("foo/bar");

    acknowledgeMutation(1);
    assertChanged(unknownDoc("foo/bar", 1));

    // There's no target pinning the doc, and we've ack'd the mutation.
    if (garbageCollectorIsEager()) {
      assertNotContains("foo/bar");
    } else {
      assertContains(unknownDoc("foo/bar", 1));
    }

    Query query = Query.atPath(ResourcePath.fromString("foo"));
    int targetId = allocateQuery(query);
    applyRemoteEvent(
        updateRemoteEvent(doc("foo/bar", 1, map("it", "base")), asList(targetId), emptyList()));
    assertChanged(doc("foo/bar", 1, map("it", "base")));
    assertContains(doc("foo/bar", 1, map("it", "base")));
  }

  @Test
  public void testHandlesDeleteMutationThenAck() {
    writeMutation(deleteMutation("foo/bar"));
    assertRemoved("foo/bar");
    assertContains(deletedDoc("foo/bar", 0));

    acknowledgeMutation(1);
    assertRemoved("foo/bar");
    // There's no target pinning the doc, and we've ack'd the mutation.
    if (garbageCollectorIsEager()) {
      assertNotContains("foo/bar");
    } else {
      assertContains(deletedDoc("foo/bar", 1).setHasCommittedMutations());
    }
  }

  @Test
  public void testHandlesDocumentThenDeleteMutationThenAck() {
    Query query = Query.atPath(ResourcePath.fromString("foo"));
    int targetId = allocateQuery(query);
    applyRemoteEvent(
        updateRemoteEvent(doc("foo/bar", 1, map("it", "base")), asList(targetId), emptyList()));
    assertChanged(doc("foo/bar", 1, map("it", "base")));
    assertContains(doc("foo/bar", 1, map("it", "base")));

    writeMutation(deleteMutation("foo/bar"));
    assertRemoved("foo/bar");
    assertContains(deletedDoc("foo/bar", 0));

    // Remove the target so only the mutation is pinning the document.
    releaseTarget(targetId);
    acknowledgeMutation(2);
    if (garbageCollectorIsEager()) {
      // Neither the target nor the mutation pin the document, it should be gone.
      assertNotContains("foo/bar");
    } else {
      assertContains(deletedDoc("foo/bar", 2).setHasCommittedMutations());
    }
  }

  @Test
  public void testHandlesDeleteMutationThenDocumentThenAck() {
    Query query = Query.atPath(ResourcePath.fromString("foo"));
    int targetId = allocateQuery(query);
    writeMutation(deleteMutation("foo/bar"));
    assertRemoved("foo/bar");
    assertContains(deletedDoc("foo/bar", 0));

    applyRemoteEvent(
        updateRemoteEvent(doc("foo/bar", 1, map("it", "base")), asList(targetId), emptyList()));
    assertRemoved("foo/bar");
    assertContains(deletedDoc("foo/bar", 0));

    releaseTarget(targetId);
    acknowledgeMutation(2);
    assertRemoved("foo/bar");
    if (garbageCollectorIsEager()) {
      // The doc is not pinned in a target and we've acknowledged the mutation. It shouldn't exist
      // anymore.
      assertNotContains("foo/bar");
    } else {
      assertContains(deletedDoc("foo/bar", 2).setHasCommittedMutations());
    }
  }

  @Test
  public void testHandlesDocumentThenDeletedDocumentThenDocument() {
    Query query = Query.atPath(ResourcePath.fromString("foo"));
    int targetId = allocateQuery(query);
    applyRemoteEvent(
        updateRemoteEvent(doc("foo/bar", 1, map("it", "base")), asList(targetId), emptyList()));
    assertChanged(doc("foo/bar", 1, map("it", "base")));
    assertContains(doc("foo/bar", 1, map("it", "base")));

    applyRemoteEvent(updateRemoteEvent(deletedDoc("foo/bar", 2), asList(targetId), emptyList()));
    assertRemoved("foo/bar");
    if (!garbageCollectorIsEager()) {
      assertContains(deletedDoc("foo/bar", 2));
    }

    applyRemoteEvent(
        updateRemoteEvent(doc("foo/bar", 3, map("it", "changed")), asList(targetId), emptyList()));
    assertChanged(doc("foo/bar", 3, map("it", "changed")));
    assertContains(doc("foo/bar", 3, map("it", "changed")));
  }

  @Test
  public void testHandlesSetMutationThenPatchMutationThenDocumentThenAckThenAck() {
    writeMutation(setMutation("foo/bar", map("foo", "old")));
    assertChanged(doc("foo/bar", 0, map("foo", "old")).setHasLocalMutations());
    assertContains(doc("foo/bar", 0, map("foo", "old")).setHasLocalMutations());

    writeMutation(patchMutation("foo/bar", map("foo", "bar")));
    assertChanged(doc("foo/bar", 0, map("foo", "bar")).setHasLocalMutations());
    assertContains(doc("foo/bar", 0, map("foo", "bar")).setHasLocalMutations());

    Query query = Query.atPath(ResourcePath.fromString("foo"));
    int targetId = allocateQuery(query);
    applyRemoteEvent(
        updateRemoteEvent(
            doc("foo/bar", 1, map("it", "base")).setHasLocalMutations(),
            asList(targetId),
            emptyList()));
    assertChanged(doc("foo/bar", 1, map("foo", "bar")).setHasLocalMutations());
    assertContains(doc("foo/bar", 1, map("foo", "bar")).setHasLocalMutations());

    releaseTarget(targetId);
    acknowledgeMutation(2); // set mutation
    assertChanged(doc("foo/bar", 2, map("foo", "bar")).setHasLocalMutations());
    assertContains(doc("foo/bar", 2, map("foo", "bar")).setHasLocalMutations());

    acknowledgeMutation(3); // patch mutation
    assertChanged(doc("foo/bar", 3, map("foo", "bar")).setHasCommittedMutations());
    if (garbageCollectorIsEager()) {
      // we've ack'd all of the mutations, nothing is keeping this pinned anymore
      assertNotContains("foo/bar");
    } else {
      assertContains(doc("foo/bar", 3, map("foo", "bar")).setHasCommittedMutations());
    }
  }

  @Test
  public void testHandlesSetMutationAndPatchMutationTogether() {
    writeMutations(
        asList(
            setMutation("foo/bar", map("foo", "old")),
            patchMutation("foo/bar", map("foo", "bar"))));

    assertChanged(doc("foo/bar", 0, map("foo", "bar")).setHasLocalMutations());
    assertContains(doc("foo/bar", 0, map("foo", "bar")).setHasLocalMutations());
  }

  @Test
  public void testHandlesSetMutationThenPatchMutationThenReject() {
    assumeTrue(garbageCollectorIsEager());

    writeMutation(setMutation("foo/bar", map("foo", "old")));
    assertContains(doc("foo/bar", 0, map("foo", "old")).setHasLocalMutations());
    acknowledgeMutation(1);
    assertNotContains("foo/bar");

    writeMutation(patchMutation("foo/bar", map("foo", "bar")));
    // A blind patch is not visible in the cache
    assertNotContains("foo/bar");

    rejectMutation();
    assertNotContains("foo/bar");
  }

  @Test
  public void testHandlesSetMutationsAndPatchMutationOfJustOneTogether() {
    writeMutations(
        asList(
            setMutation("foo/bar", map("foo", "old")),
            setMutation("bar/baz", map("bar", "baz")),
            patchMutation("foo/bar", map("foo", "bar"))));

    assertChanged(
        doc("bar/baz", 0, map("bar", "baz")).setHasLocalMutations(),
        doc("foo/bar", 0, map("foo", "bar")).setHasLocalMutations());

    assertContains(doc("foo/bar", 0, map("foo", "bar")).setHasLocalMutations());
    assertContains(doc("bar/baz", 0, map("bar", "baz")).setHasLocalMutations());
  }

  @Test
  public void testHandlesDeleteMutationThenPatchMutationThenAckThenAck() {
    writeMutation(deleteMutation("foo/bar"));
    assertRemoved("foo/bar");
    assertContains(deletedDoc("foo/bar", 0));

    writeMutation(patchMutation("foo/bar", map("foo", "bar")));
    assertRemoved("foo/bar");
    assertContains(deletedDoc("foo/bar", 0));

    acknowledgeMutation(2); // delete mutation
    assertRemoved("foo/bar");
    assertContains(deletedDoc("foo/bar", 2).setHasCommittedMutations());

    acknowledgeMutation(3); // patch mutation
    assertChanged(unknownDoc("foo/bar", 3));
    if (garbageCollectorIsEager()) {
      // There are no more pending mutations, the doc has been dropped
      assertNotContains("foo/bar");
    } else {
      assertContains(unknownDoc("foo/bar", 3));
    }
  }

  @Test
  public void testCollectsGarbageAfterChangeBatchWithNoTargetIDs() {
    assumeTrue(garbageCollectorIsEager());

    int targetId = 1;
    applyRemoteEvent(
        updateRemoteEvent(deletedDoc("foo/bar", 2), emptyList(), emptyList(), asList(targetId)));
    assertNotContains("foo/bar");

    applyRemoteEvent(
        updateRemoteEvent(
            doc("foo/bar", 2, map("foo", "bar")), emptyList(), emptyList(), asList(targetId)));
    assertNotContains("foo/bar");
  }

  @Test
  public void testCollectsGarbageAfterChangeBatch() {
    assumeTrue(garbageCollectorIsEager());

    Query query = Query.atPath(ResourcePath.fromString("foo"));
    allocateQuery(query);
    assertTargetId(2);

    List<Integer> none = asList();
    List<Integer> two = asList(2);
    applyRemoteEvent(addedRemoteEvent(doc("foo/bar", 2, map("foo", "bar")), two, none));
    assertContains(doc("foo/bar", 2, map("foo", "bar")));

    applyRemoteEvent(updateRemoteEvent(doc("foo/bar", 2, map("foo", "baz")), none, two));

    assertNotContains("foo/bar");
  }

  @Test
  public void testCollectsGarbageAfterAcknowledgedMutation() {
    assumeTrue(garbageCollectorIsEager());

    Query query = Query.atPath(ResourcePath.fromString("foo"));
    int targetId = allocateQuery(query);
    applyRemoteEvent(
        updateRemoteEvent(doc("foo/bar", 1, map("foo", "old")), asList(targetId), emptyList()));
    writeMutation(patchMutation("foo/bar", map("foo", "bar")));
    releaseTarget(targetId);
    writeMutation(setMutation("foo/bah", map("foo", "bah")));
    writeMutation(deleteMutation("foo/baz"));
    assertContains(doc("foo/bar", 1, map("foo", "bar")).setHasLocalMutations());
    assertContains(doc("foo/bah", 0, map("foo", "bah")).setHasLocalMutations());
    assertContains(deletedDoc("foo/baz", 0));

    acknowledgeMutation(3);
    assertNotContains("foo/bar");
    assertContains(doc("foo/bah", 0, map("foo", "bah")).setHasLocalMutations());
    assertContains(deletedDoc("foo/baz", 0));

    acknowledgeMutation(4);
    assertNotContains("foo/bar");
    assertNotContains("foo/bah");
    assertContains(deletedDoc("foo/baz", 0));

    acknowledgeMutation(5);
    assertNotContains("foo/bar");
    assertNotContains("foo/bah");
    assertNotContains("foo/baz");
  }

  @Test
  public void testCollectsGarbageAfterRejectedMutation() {
    assumeTrue(garbageCollectorIsEager());

    Query query = Query.atPath(ResourcePath.fromString("foo"));
    int targetId = allocateQuery(query);
    applyRemoteEvent(
        updateRemoteEvent(doc("foo/bar", 1, map("foo", "old")), asList(targetId), emptyList()));
    writeMutation(patchMutation("foo/bar", map("foo", "bar")));
    // Release the query so that our target count goes back to 0 and we are considered up-to-date.
    releaseTarget(targetId);
    writeMutation(setMutation("foo/bah", map("foo", "bah")));
    writeMutation(deleteMutation("foo/baz"));
    assertContains(doc("foo/bar", 1, map("foo", "bar")).setHasLocalMutations());
    assertContains(doc("foo/bah", 0, map("foo", "bah")).setHasLocalMutations());
    assertContains(deletedDoc("foo/baz", 0));

    rejectMutation(); // patch mutation
    assertNotContains("foo/bar");
    assertContains(doc("foo/bah", 0, map("foo", "bah")).setHasLocalMutations());
    assertContains(deletedDoc("foo/baz", 0));

    rejectMutation(); // set mutation
    assertNotContains("foo/bar");
    assertNotContains("foo/bah");
    assertContains(deletedDoc("foo/baz", 0));

    rejectMutation(); // delete mutation
    assertNotContains("foo/bar");
    assertNotContains("foo/bah");
    assertNotContains("foo/baz");
  }

  @Test
  public void testPinsDocumentsInTheLocalView() {
    assumeTrue(garbageCollectorIsEager());

    Query query = Query.atPath(ResourcePath.fromString("foo"));
    allocateQuery(query);
    assertTargetId(2);

    List<Integer> none = asList();
    List<Integer> two = asList(2);
    applyRemoteEvent(addedRemoteEvent(doc("foo/bar", 1, map("foo", "bar")), two, none));
    writeMutation(setMutation("foo/baz", map("foo", "baz")));
    assertContains(doc("foo/bar", 1, map("foo", "bar")));
    assertContains(doc("foo/baz", 0, map("foo", "baz")).setHasLocalMutations());

    notifyLocalViewChanges(
        viewChanges(2, /* fromCache= */ false, asList("foo/bar", "foo/baz"), emptyList()));
    applyRemoteEvent(updateRemoteEvent(doc("foo/bar", 1, map("foo", "bar")), none, two));
    applyRemoteEvent(updateRemoteEvent(doc("foo/baz", 2, map("foo", "baz")), two, none));
    acknowledgeMutation(2);
    assertContains(doc("foo/bar", 1, map("foo", "bar")));
    assertContains(doc("foo/baz", 2, map("foo", "baz")));

    notifyLocalViewChanges(
        viewChanges(2, /* fromCache= */ false, emptyList(), asList("foo/bar", "foo/baz")));
    releaseTarget(2);

    assertNotContains("foo/bar");
    assertNotContains("foo/baz");
  }

  @Test
  public void testThrowsAwayDocumentsWithUnknownTargetIDsImmediately() {
    assumeTrue(garbageCollectorIsEager());

    int unknownTargetID = 321;
    applyRemoteEvent(
        updateRemoteEvent(
            doc("foo/bar", 1, map()),
            /* updatedInTargets= */ asList(unknownTargetID),
            /* removedFromTargets= */ emptyList(),
            /* activeTargets= */ emptyList()));
    assertNotContains("foo/bar");
  }

  @Test
  public void testCanExecuteDocumentQueries() {
    localStore.writeLocally(
        asList(
            setMutation("foo/bar", map("foo", "bar")),
            setMutation("foo/baz", map("foo", "baz")),
            setMutation("foo/bar/Foo/Bar", map("Foo", "Bar"))));
    Query query = Query.atPath(ResourcePath.fromSegments(asList("foo", "bar")));
    QueryResult result = localStore.executeQuery(query, /* usePreviousResults= */ true);
    assertEquals(
        asList(doc("foo/bar", 0, map("foo", "bar")).setHasLocalMutations()),
        values(result.getDocuments()));
  }

  @Test
  public void testCanExecuteCollectionQueries() {
    localStore.writeLocally(
        asList(
            setMutation("fo/bar", map("fo", "bar")),
            setMutation("foo/bar", map("foo", "bar")),
            setMutation("foo/baz", map("foo", "baz")),
            setMutation("foo/bar/Foo/Bar", map("Foo", "Bar")),
            setMutation("fooo/blah", map("fooo", "blah"))));
    Query query = Query.atPath(ResourcePath.fromString("foo"));
    QueryResult result = localStore.executeQuery(query, /* usePreviousResults= */ true);
    assertEquals(
        asList(
            doc("foo/bar", 0, map("foo", "bar")).setHasLocalMutations(),
            doc("foo/baz", 0, map("foo", "baz")).setHasLocalMutations()),
        values(result.getDocuments()));
  }

  @Test
  public void testCanExecuteMixedCollectionQueries() {
    Query query = Query.atPath(ResourcePath.fromString("foo"));
    allocateQuery(query);
    assertTargetId(2);

    applyRemoteEvent(updateRemoteEvent(doc("foo/baz", 10, map("a", "b")), asList(2), emptyList()));
    applyRemoteEvent(updateRemoteEvent(doc("foo/bar", 20, map("a", "b")), asList(2), emptyList()));
    writeMutation(setMutation("foo/bonk", map("a", "b")));

    QueryResult result = localStore.executeQuery(query, /* usePreviousResults= */ true);
    assertEquals(
        asList(
            doc("foo/bar", 20, map("a", "b")),
            doc("foo/baz", 10, map("a", "b")),
            doc("foo/bonk", 0, map("a", "b")).setHasLocalMutations()),
        values(result.getDocuments()));
  }

  @Test
  public void testReadsAllDocumentsForInitialCollectionQueries() {
    Query query = Query.atPath(ResourcePath.fromString("foo"));
    allocateQuery(query);

    applyRemoteEvent(updateRemoteEvent(doc("foo/baz", 10, map()), asList(2), emptyList()));
    applyRemoteEvent(updateRemoteEvent(doc("foo/bar", 20, map()), asList(2), emptyList()));
    writeMutation(setMutation("foo/bonk", map()));

    resetPersistenceStats();

    localStore.executeQuery(query, /* usePreviousResults= */ true);

    assertRemoteDocumentsRead(/* byKey= */ 0, /* byQuery= */ 2);
    assertMutationsRead(/* byKey= */ 0, /* byQuery= */ 1);
  }

  @Test
  public void testPersistsResumeTokens() {
    assumeFalse(garbageCollectorIsEager());

    Query query = query("foo/bar");
    int targetId = allocateQuery(query);

    applyRemoteEvent(noChangeEvent(targetId, 1000));

    // Stop listening so that the query should become inactive (but persistent)
    localStore.releaseTarget(targetId);

    // Should come back with the same resume token
    TargetData targetData2 = localStore.allocateTarget(query.toTarget());
    assertEquals(resumeToken(1000), targetData2.getResumeToken());
  }

  @Test
  public void testDoesNotReplaceResumeTokenWithEmptyByteString() {
    assumeFalse(garbageCollectorIsEager());

    Query query = query("foo/bar");
    int targetId = allocateQuery(query);

    applyRemoteEvent(noChangeEvent(targetId, 1000));

    // New message with empty resume token should not replace the old resume token
    applyRemoteEvent(TestUtil.noChangeEvent(targetId, 2000, WatchStream.EMPTY_RESUME_TOKEN));

    // Stop listening so that the query should become inactive (but persistent)
    localStore.releaseTarget(targetId);

    // Should come back with the same resume token
    TargetData targetData2 = localStore.allocateTarget(query.toTarget());
    assertEquals(resumeToken(1000), targetData2.getResumeToken());
  }

  @Test
  public void testRemoteDocumentKeysForTarget() {
    Query query = Query.atPath(ResourcePath.fromString("foo"));
    allocateQuery(query);
    assertTargetId(2);

    applyRemoteEvent(addedRemoteEvent(doc("foo/baz", 10, map("a", "b")), asList(2), emptyList()));
    applyRemoteEvent(addedRemoteEvent(doc("foo/bar", 20, map("a", "b")), asList(2), emptyList()));
    writeMutation(setMutation("foo/bonk", map("a", "b")));

    ImmutableSortedSet<DocumentKey> keys = localStore.getRemoteDocumentKeys(2);
    assertSetEquals(asList(key("foo/bar"), key("foo/baz")), keys);

    keys = localStore.getRemoteDocumentKeys(2);
    assertSetEquals(asList(key("foo/bar"), key("foo/baz")), keys);
  }

  // TODO(mrschmidt): The FieldValue.increment() field transform tests below would probably be
  // better implemented as spec tests but currently they don't support transforms.

  @Test
  public void testHandlesSetMutationThenTransformThenTransform() {
    writeMutation(setMutation("foo/bar", map("sum", 0)));
    assertContains(doc("foo/bar", 0, map("sum", 0)).setHasLocalMutations());
    assertChanged(doc("foo/bar", 0, map("sum", 0)).setHasLocalMutations());

    writeMutation(patchMutation("foo/bar", map("sum", FieldValue.increment(1))));
    assertContains(doc("foo/bar", 0, map("sum", 1)).setHasLocalMutations());
    assertChanged(doc("foo/bar", 0, map("sum", 1)).setHasLocalMutations());

    writeMutation(patchMutation("foo/bar", map("sum", FieldValue.increment(2))));
    assertContains(doc("foo/bar", 0, map("sum", 3)).setHasLocalMutations());
    assertChanged(doc("foo/bar", 0, map("sum", 3)).setHasLocalMutations());
  }

  @Test
  public void testHandlesSetMutationThenAckThenTransformThenAckThenTransform() {
    // Since this test doesn't start a listen, Eager GC removes the documents from the cache as
    // soon as the mutation is applied. This creates a lot of special casing in this unit test but
    // does not expand its test coverage.
    assumeFalse(garbageCollectorIsEager());

    writeMutation(setMutation("foo/bar", map("sum", 0)));
    assertContains(doc("foo/bar", 0, map("sum", 0)).setHasLocalMutations());
    assertChanged(doc("foo/bar", 0, map("sum", 0)).setHasLocalMutations());

    acknowledgeMutation(1);
    assertChanged(doc("foo/bar", 1, map("sum", 0)).setHasCommittedMutations());
    assertContains(doc("foo/bar", 1, map("sum", 0)).setHasCommittedMutations());

    writeMutation(patchMutation("foo/bar", map("sum", FieldValue.increment(1))));
    assertContains(doc("foo/bar", 1, map("sum", 1)).setHasLocalMutations());
    assertChanged(doc("foo/bar", 1, map("sum", 1)).setHasLocalMutations());

    acknowledgeMutation(2, 1);
    assertChanged(doc("foo/bar", 2, map("sum", 1)).setHasCommittedMutations());
    assertContains(doc("foo/bar", 2, map("sum", 1)).setHasCommittedMutations());

    writeMutation(patchMutation("foo/bar", map("sum", FieldValue.increment(2))));
    assertContains(doc("foo/bar", 2, map("sum", 3)).setHasLocalMutations());
    assertChanged(doc("foo/bar", 2, map("sum", 3)).setHasLocalMutations());
  }

  @Test
  public void testUsesTargetMappingToExecuteQueries() {
    assumeFalse(garbageCollectorIsEager());
    assumeTrue(queryEngine.getSubject() instanceof DefaultQueryEngine);

    // This test verifies that once a target mapping has been written, only documents that match
    // the query are read from the RemoteDocumentCache.

    Query query =
        Query.atPath(ResourcePath.fromString("foo")).filter(filter("matches", "==", true));
    int targetId = allocateQuery(query);

    writeMutation(setMutation("foo/a", map("matches", true)));
    writeMutation(setMutation("foo/b", map("matches", true)));
    writeMutation(setMutation("foo/ignored", map("matches", false)));
    acknowledgeMutation(10);
    acknowledgeMutation(10);
    acknowledgeMutation(10);

    // Execute the query, but note that we read all existing documents from the RemoteDocumentCache
    // since we do not yet have target mapping.
    executeQuery(query);
    assertRemoteDocumentsRead(/* byKey= */ 0, /* byQuery= */ 2);

    // Issue a RemoteEvent to persist the target mapping.
    applyRemoteEvent(
        addedRemoteEvent(
            asList(doc("foo/a", 10, map("matches", true)), doc("foo/b", 10, map("matches", true))),
            asList(targetId),
            emptyList()));
    applyRemoteEvent(noChangeEvent(targetId, 10));
    udpateViews(targetId, /* fromCache= */ false);

    // Execute the query again, this time verifying that we only read the two documents that match
    // the query.
    executeQuery(query);
    assertRemoteDocumentsRead(/* byKey= */ 2, /* byQuery= */ 0);
    assertQueryReturned("foo/a", "foo/b");
  }

  @Test
  public void testLastLimboFreeSnapshotIsAdvancedDuringViewProcessing() {
    // This test verifies that the `lastLimboFreeSnapshot` version for TargetData is advanced when
    // we compute a limbo-free free view and that the mapping is persisted when we release a target.

    Query query = Query.atPath(ResourcePath.fromString("foo"));
    Target target = query.toTarget();
    int targetId = allocateQuery(query);

    // Advance the target snapshot.
    applyRemoteEvent(noChangeEvent(targetId, 10));

    // At this point, we have not yet confirmed that the target is limbo free.
    TargetData cachedTargetData = localStore.getTargetData(target);
    Assert.assertEquals(SnapshotVersion.NONE, cachedTargetData.getLastLimboFreeSnapshotVersion());

    // Mark the view synced, which updates the last limbo free snapshot version.
    udpateViews(targetId, /* fromCache=*/ false);
    cachedTargetData = localStore.getTargetData(target);
    Assert.assertEquals(version(10), cachedTargetData.getLastLimboFreeSnapshotVersion());

    // The last limbo free snapshot version is persisted even if we release the target.
    releaseTarget(targetId);

    if (!garbageCollectorIsEager()) {
      cachedTargetData = localStore.getTargetData(target);
      Assert.assertEquals(version(10), cachedTargetData.getLastLimboFreeSnapshotVersion());
    }
  }

  @Test
  public void testQueriesIncludeLocallyModifiedDocuments() {
    assumeFalse(garbageCollectorIsEager());

    // This test verifies that queries that have a persisted TargetMapping include documents that
    // were modified by local edits after the target mapping was written.
    Query query =
        Query.atPath(ResourcePath.fromString("foo")).filter(filter("matches", "==", true));
    int targetId = allocateQuery(query);

    applyRemoteEvent(
        addedRemoteEvent(
            asList(doc("foo/a", 10, map("matches", true))), asList(targetId), emptyList()));
    applyRemoteEvent(noChangeEvent(targetId, 10));
    udpateViews(targetId, /* fromCache= */ false);

    // Execute the query based on the RemoteEvent.
    executeQuery(query);
    assertQueryReturned("foo/a");

    // Write a document.
    writeMutation(setMutation("foo/b", map("matches", true)));

    // Execute the query and make sure that the pending mutation is included in the result.
    executeQuery(query);
    assertQueryReturned("foo/a", "foo/b");

    acknowledgeMutation(11);

    // Execute the query and make sure that the acknowledged mutation is included in the result.
    executeQuery(query);
    assertQueryReturned("foo/a", "foo/b");
  }

  @Test
  public void testQueriesIncludeDocumentsFromOtherQueries() {
    assumeFalse(garbageCollectorIsEager());

    // This test verifies that queries that have a persisted TargetMapping include documents that
    // were modified by other queries after the target mapping was written.

    Query filteredQuery =
        Query.atPath(ResourcePath.fromString("foo")).filter(filter("matches", "==", true));
    int targetId = allocateQuery(filteredQuery);

    applyRemoteEvent(
        addedRemoteEvent(
            asList(doc("foo/a", 10, map("matches", true))), asList(targetId), emptyList()));
    applyRemoteEvent(noChangeEvent(targetId, 10));
    udpateViews(targetId, /* fromCache=*/ false);
    releaseTarget(targetId);

    // Start another query and add more matching documents to the collection.
    Query fullQuery = Query.atPath(ResourcePath.fromString("foo"));
    targetId = allocateQuery(fullQuery);
    applyRemoteEvent(
        addedRemoteEvent(
            asList(doc("foo/a", 10, map("matches", true)), doc("foo/b", 20, map("matches", true))),
            asList(targetId),
            emptyList()));
    releaseTarget(targetId);

    // Run the original query again and ensure that both the original matches as well as all new
    // matches are included in the result set.
    allocateQuery(filteredQuery);
    executeQuery(filteredQuery);
    assertQueryReturned("foo/a", "foo/b");
  }

  @Test
  public void testQueriesFilterDocumentsThatNoLongerMatch() {
    assumeFalse(garbageCollectorIsEager());

    // This test verifies that documents that once matched a query are post-filtered if they no
    // longer match the query filter.

    // Add two document results for a simple filter query
    Query filteredQuery =
        Query.atPath(ResourcePath.fromString("foo")).filter(filter("matches", "==", true));
    int targetId = allocateQuery(filteredQuery);

    applyRemoteEvent(
        addedRemoteEvent(
            asList(doc("foo/a", 10, map("matches", true)), doc("foo/b", 10, map("matches", true))),
            asList(targetId),
            emptyList()));
    applyRemoteEvent(noChangeEvent(targetId, 10));
    udpateViews(targetId, /* fromCache=*/ false);
    releaseTarget(targetId);

    // Modify one of the documents to no longer match while the filtered query is inactive.
    Query fullQuery = Query.atPath(ResourcePath.fromString("foo"));
    targetId = allocateQuery(fullQuery);
    applyRemoteEvent(
        addedRemoteEvent(
            asList(doc("foo/a", 10, map("matches", true)), doc("foo/b", 20, map("matches", false))),
            asList(targetId),
            emptyList()));
    releaseTarget(targetId);

    // Re-run the filtered query and verify that the modified document is no longer returned.
    allocateQuery(filteredQuery);
    executeQuery(filteredQuery);
    assertQueryReturned("foo/a");
  }

  @Test
  public void testHandlesSetMutationThenTransformThenRemoteEventThenTransform() {
    Query query = Query.atPath(ResourcePath.fromString("foo"));
    allocateQuery(query);
    assertTargetId(2);

    writeMutation(setMutation("foo/bar", map("sum", 0)));
    assertChanged(doc("foo/bar", 0, map("sum", 0)).setHasLocalMutations());
    assertContains(doc("foo/bar", 0, map("sum", 0)).setHasLocalMutations());

    applyRemoteEvent(addedRemoteEvent(doc("foo/bar", 1, map("sum", 0)), asList(2), emptyList()));
    acknowledgeMutation(1);
    assertChanged(doc("foo/bar", 1, map("sum", 0)));
    assertContains(doc("foo/bar", 1, map("sum", 0)));

    writeMutation(patchMutation("foo/bar", map("sum", FieldValue.increment(1))));
    assertChanged(doc("foo/bar", 1, map("sum", 1)).setHasLocalMutations());
    assertContains(doc("foo/bar", 1, map("sum", 1)).setHasLocalMutations());

    // The value in this remote event gets ignored since we still have a pending transform mutation.
    applyRemoteEvent(addedRemoteEvent(doc("foo/bar", 2, map("sum", 1337)), asList(2), emptyList()));
    assertChanged(doc("foo/bar", 2, map("sum", 1)).setHasLocalMutations());
    assertContains(doc("foo/bar", 2, map("sum", 1)).setHasLocalMutations());

    // Add another increment. Note that we still compute the increment based on the local value.
    writeMutation(patchMutation("foo/bar", map("sum", FieldValue.increment(2))));
    assertChanged(doc("foo/bar", 2, map("sum", 3)).setHasLocalMutations());
    assertContains(doc("foo/bar", 2, map("sum", 3)).setHasLocalMutations());

    acknowledgeMutation(3, 1);
    assertChanged(doc("foo/bar", 3, map("sum", 3)).setHasLocalMutations());
    assertContains(doc("foo/bar", 3, map("sum", 3)).setHasLocalMutations());

    acknowledgeMutation(4, 1339);
    assertChanged(doc("foo/bar", 4, map("sum", 1339)).setHasCommittedMutations());
    assertContains(doc("foo/bar", 4, map("sum", 1339)).setHasCommittedMutations());
  }

  @Test
  public void testHoldsBackOnlyNonIdempotentTransforms() {
    Query query = Query.atPath(ResourcePath.fromString("foo"));
    allocateQuery(query);
    assertTargetId(2);

    writeMutation(setMutation("foo/bar", map("sum", 0, "array_union", new ArrayList<>())));
    assertChanged(
        doc("foo/bar", 0, map("sum", 0, "array_union", new ArrayList<>())).setHasLocalMutations());

    acknowledgeMutation(1);
    assertChanged(
        doc("foo/bar", 1, map("sum", 0, "array_union", new ArrayList<>()))
            .setHasCommittedMutations());

    applyRemoteEvent(
        addedRemoteEvent(
            doc("foo/bar", 1, map("sum", 0, "array_union", new ArrayList<>())),
            asList(2),
            emptyList()));
    assertChanged(doc("foo/bar", 1, map("sum", 0, "array_union", new ArrayList<>())));

    writeMutations(
        asList(
            patchMutation("foo/bar", map("sum", FieldValue.increment(1))),
            patchMutation("foo/bar", map("array_union", FieldValue.arrayUnion("foo")))));
    assertChanged(
        doc("foo/bar", 1, map("sum", 1, "array_union", Collections.singletonList("foo")))
            .setHasLocalMutations());

    // The sum transform is not idempotent and the backend's updated value is ignored. The
    // ArrayUnion transform is recomputed and includes the backend value.
    applyRemoteEvent(
        addedRemoteEvent(
            doc("foo/bar", 2, map("sum", 1337, "array_union", Collections.singletonList("bar"))),
            asList(2),
            emptyList()));
    assertChanged(
        doc("foo/bar", 2, map("sum", 1, "array_union", asList("bar", "foo")))
            .setHasLocalMutations());
  }

  @Test
  public void testHandlesMergeMutationWithTransformThenRemoteEvent() {
    Query query = Query.atPath(ResourcePath.fromString("foo"));
    allocateQuery(query);
    assertTargetId(2);

    writeMutation(
        mergeMutation("foo/bar", map("sum", FieldValue.increment(1)), Collections.EMPTY_LIST));
    assertChanged(doc("foo/bar", 0, map("sum", 1)).setHasLocalMutations());
    assertContains(doc("foo/bar", 0, map("sum", 1)).setHasLocalMutations());

    applyRemoteEvent(addedRemoteEvent(doc("foo/bar", 1, map("sum", 1337)), asList(2), emptyList()));
    assertChanged(doc("foo/bar", 1, map("sum", 1)).setHasLocalMutations());
    assertContains(doc("foo/bar", 1, map("sum", 1)).setHasLocalMutations());
  }

  @Test
  public void testHandlesPatchMutationWithTransformThenRemoteEvent() {
    Query query = Query.atPath(ResourcePath.fromString("foo"));
    allocateQuery(query);
    assertTargetId(2);

    writeMutation(patchMutation("foo/bar", map("sum", FieldValue.increment(1))));
    assertChanged(deletedDoc("foo/bar", 0));
    assertNotContains("foo/bar");

    // Note: This test reflects the current behavior, but it may be preferable to replay the
    // mutation once we receive the first value from the remote event.

    applyRemoteEvent(addedRemoteEvent(doc("foo/bar", 1, map("sum", 1337)), asList(2), emptyList()));
    assertChanged(doc("foo/bar", 1, map("sum", 1)).setHasLocalMutations());
    assertContains(doc("foo/bar", 1, map("sum", 1)).setHasLocalMutations());
  }

  @Test
  public void testHandlesSavingBundledDocuments() {
    bundleDocuments(doc("foo/bar", 1, map("sum", 1337)), deletedDoc("foo/bar1", 1));
    assertChanged(doc("foo/bar", 1, map("sum", 1337)), deletedDoc("foo/bar1", 1));
    assertContains(doc("foo/bar", 1, map("sum", 1337)));
    assertContains(deletedDoc("foo/bar1", 1));

    assertQueryDocumentMapping(/* targetId= */ 2, key("foo/bar"));
  }

  @Test
  public void testHandlesSavingBundlesDocumentsWithNewerExistingVersion() {
    Query query = Query.atPath(ResourcePath.fromString("foo"));
    allocateQuery(query);
    assertTargetId(2);

    applyRemoteEvent(addedRemoteEvent(doc("foo/bar", 2, map("sum", 1337)), asList(2), emptyList()));
    assertContains(doc("foo/bar", 2, map("sum", 1337)));

    bundleDocuments(doc("foo/bar", 1, map("sum", 1337)), deletedDoc("foo/bar1", 1));
    assertChanged(deletedDoc("foo/bar1", 1));
    assertContains(doc("foo/bar", 2, map("sum", 1337)));
    assertContains(deletedDoc("foo/bar1", 1));

    assertQueryDocumentMapping(/* targetId= */ 4, key("foo/bar"));
  }

  @Test
  public void testHandlesSavingBundledDocumentsWithOlderExistingVersion() {
    Query query = Query.atPath(ResourcePath.fromString("foo"));
    allocateQuery(query);
    assertTargetId(2);

    applyRemoteEvent(
        addedRemoteEvent(doc("foo/bar", 1, map("val", "to-delete")), asList(2), emptyList()));
    assertContains(doc("foo/bar", 1, map("val", "to-delete")));

    bundleDocuments(doc("foo/new", 1, map("sum", 1336)), deletedDoc("foo/bar", 2));
    assertChanged(doc("foo/new", 1, map("sum", 1336)), deletedDoc("foo/bar", 2));
    assertContains(doc("foo/new", 1, map("sum", 1336)));
    assertContains(deletedDoc("foo/bar", 2));

    assertQueryDocumentMapping(/* targetId= */ 4, key("foo/new"));
  }

  @Test
  public void testSavingBundledDocumentsWithSameExistingVersionShouldNotOverwrite() {
    Query query = Query.atPath(ResourcePath.fromString("foo"));
    allocateQuery(query);
    assertTargetId(2);

    applyRemoteEvent(
        addedRemoteEvent(doc("foo/bar", 1, map("val", "old")), asList(2), emptyList()));
    assertContains(doc("foo/bar", 1, map("val", "old")));

    bundleDocuments(doc("foo/bar", 1, map("val", "new")));
    assertChanged();
    assertContains(doc("foo/bar", 1, map("val", "old")));

    assertQueryDocumentMapping(/* targetId= */ 4, key("foo/bar"));
  }

  @Test
  public void testHandlesMergeMutationWithTransformThenBundledDocuments() {
    Query query = Query.atPath(ResourcePath.fromString("foo"));
    allocateQuery(query);
    assertTargetId(2);

    writeMutation(
        mergeMutation("foo/bar", map("sum", FieldValue.increment(1)), Collections.EMPTY_LIST));
    assertChanged(doc("foo/bar", 0, map("sum", 1)).setHasLocalMutations());
    assertContains(doc("foo/bar", 0, map("sum", 1)).setHasLocalMutations());

    bundleDocuments(doc("foo/bar", 1, map("sum", 1337)));
    assertChanged(doc("foo/bar", 1, map("sum", 1)).setHasLocalMutations());
    assertContains(doc("foo/bar", 1, map("sum", 1)).setHasLocalMutations());

    assertQueryDocumentMapping(/* targetId= */ 4, key("foo/bar"));
  }

  @Test
  public void testHandlesPatchMutationWithTransformThenBundledDocuments() {
    // Note: see comments in testHandlesPatchMutationWithTransformThenRemoteEvent().
    // The behavior for this and remote event is the same.
    Query query = Query.atPath(ResourcePath.fromString("foo"));
    allocateQuery(query);
    assertTargetId(2);

    writeMutation(patchMutation("foo/bar", map("sum", FieldValue.increment(1))));
    assertChanged(deletedDoc("foo/bar", 0));
    assertNotContains("foo/bar");

    bundleDocuments(doc("foo/bar", 1, map("sum", 1337)));
    assertChanged(doc("foo/bar", 1, map("sum", 1)).setHasLocalMutations());
    assertContains(doc("foo/bar", 1, map("sum", 1)).setHasLocalMutations());

    assertQueryDocumentMapping(/* targetId= */ 4, key("foo/bar"));
  }

  @Test
  public void testHandlesSavingAndCheckingBundleMetadata() {
    assertNoNewerBundle("test", /* createTime= */ 1);
    saveBundle("test", /* createTime= */ 2);
    assertHasNewerBundle("test", /* createTime= */ 1);
  }

  @Test
  public void testHandlesSavingAndLoadingNamedQueries() {
    Target target = Query.atPath(ResourcePath.fromString("foo")).toTarget();
    NamedQuery namedQuery =
        new NamedQuery(
            "testQuery",
            new BundledQuery(target, Query.LimitType.LIMIT_TO_FIRST),
            SnapshotVersion.NONE);
    saveNamedQuery(namedQuery);
    assertHasNamedQuery(namedQuery);
  }

  @Test
  public void testLoadingNamedQueriesAllocatesTargetsAndUpdatesTargetDocumentMapping() {
    bundleDocuments(doc("foo1/bar", 1, map("sum", 1337)), doc("foo2/bar", 1, map("sum", 42)));
    assertChanged(doc("foo1/bar", 1, map("sum", 1337)), doc("foo2/bar", 1, map("sum", 42)));
    assertContains(doc("foo1/bar", 1, map("sum", 1337)));
    assertContains(doc("foo2/bar", 1, map("sum", 42)));

    Target target1 = Query.atPath(ResourcePath.fromString("foo1")).toTarget();
    NamedQuery namedQuery1 =
        new NamedQuery(
            "query-1",
            new BundledQuery(target1, Query.LimitType.LIMIT_TO_FIRST),
            new SnapshotVersion(Timestamp.now()));
    saveNamedQuery(namedQuery1, key("foo1/bar"));
    assertHasNamedQuery(namedQuery1);

    assertQueryDocumentMapping(/* targetId= */ 4, key("foo1/bar"));

    Target target2 = Query.atPath(ResourcePath.fromString("foo2")).toTarget();
    NamedQuery namedQuery2 =
        new NamedQuery(
            "query-2",
            new BundledQuery(target2, Query.LimitType.LIMIT_TO_FIRST),
            new SnapshotVersion(Timestamp.now()));
    saveNamedQuery(namedQuery2, key("foo2/bar"));
    assertHasNamedQuery(namedQuery2);

    assertQueryDocumentMapping(/* targetId= */ 6, key("foo2/bar"));
  }

  @Test
  public void testHandlesSavingAndLoadingLimitToLastQueries() {
    Target target =
        Query.atPath(ResourcePath.fromString("foo"))
            .orderBy(orderBy("foo"))
            .limitToFirst(5) // Use `limitToFirst` so toTarget() does not flip ordering constraint
            .toTarget();
    NamedQuery namedQuery =
        new NamedQuery(
            "testQuery",
            new BundledQuery(target, Query.LimitType.LIMIT_TO_LAST),
            SnapshotVersion.NONE);
    saveNamedQuery(namedQuery);
    assertHasNamedQuery(namedQuery);
  }

  @Test
  public void testGetHighestUnacknowledgedBatchId() {
    assertEquals(MutationBatch.UNKNOWN, localStore.getHighestUnacknowledgedBatchId());

    writeMutation(setMutation("foo/bar", map("abc", 123)));
    assertEquals(1, localStore.getHighestUnacknowledgedBatchId());

    writeMutation(patchMutation("foo/bar", map("abc", 321)));
    assertEquals(2, localStore.getHighestUnacknowledgedBatchId());

    acknowledgeMutation(1);
    assertEquals(2, localStore.getHighestUnacknowledgedBatchId());

    rejectMutation();
    assertEquals(MutationBatch.UNKNOWN, localStore.getHighestUnacknowledgedBatchId());
  }

  @Test
  public void testOnlyPersistsUpdatesForDocumentsWhenVersionChanges() {
    Query query = Query.atPath(ResourcePath.fromString("foo"));
    allocateQuery(query);
    assertTargetId(2);

    applyRemoteEvent(
        addedRemoteEvent(doc("foo/bar", 1, map("val", "old")), asList(2), emptyList()));
    assertChanged(doc("foo/bar", 1, map("val", "old")));
    assertContains(doc("foo/bar", 1, map("val", "old")));

    applyRemoteEvent(
        addedRemoteEvent(
            asList(doc("foo/bar", 1, map("val", "new")), doc("foo/baz", 2, map("val", "new"))),
            asList(2),
            emptyList()));

    assertChanged(doc("foo/baz", 2, map("val", "new")));
    // The update for foo/bar is ignored.
    assertContains(doc("foo/bar", 1, map("val", "old")));
    assertContains(doc("foo/baz", 2, map("val", "new")));
  }
}
