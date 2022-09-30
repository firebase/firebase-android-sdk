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

package com.google.firebase.firestore.local;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.firestore.testutil.TestUtil.deleteMutation;
import static com.google.firebase.firestore.testutil.TestUtil.doc;
import static com.google.firebase.firestore.testutil.TestUtil.fieldIndex;
import static com.google.firebase.firestore.testutil.TestUtil.filter;
import static com.google.firebase.firestore.testutil.TestUtil.key;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.orderBy;
import static com.google.firebase.firestore.testutil.TestUtil.patchMutation;
import static com.google.firebase.firestore.testutil.TestUtil.query;
import static com.google.firebase.firestore.testutil.TestUtil.setMutation;
import static com.google.firebase.firestore.testutil.TestUtil.version;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.core.Target;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldIndex;
import com.google.firebase.firestore.model.FieldIndex.IndexOffset;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.testutil.TestUtil;
import com.google.firebase.firestore.util.AsyncQueue;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class IndexBackfillerTest {

  @Rule public TestName name = new TestName();

  private Persistence persistence;
  private IndexManager indexManager;
  private RemoteDocumentCache remoteDocumentCache;
  private DocumentOverlayCache documentOverlayCache;
  private IndexBackfiller backfiller;

  @Before
  public void setUp() {
    persistence = PersistenceTestHelpers.createSQLitePersistence();
    remoteDocumentCache = persistence.getRemoteDocumentCache();
    documentOverlayCache = persistence.getDocumentOverlayCache(User.UNAUTHENTICATED);
    indexManager = persistence.getIndexManager(User.UNAUTHENTICATED);
    indexManager.start();

    RemoteDocumentCache remoteDocumentCache = persistence.getRemoteDocumentCache();
    remoteDocumentCache.setIndexManager(indexManager);
    LocalDocumentsView localDocumentsView =
        new LocalDocumentsView(
            remoteDocumentCache,
            persistence.getMutationQueue(User.UNAUTHENTICATED, indexManager),
            documentOverlayCache,
            indexManager);
    backfiller =
        new IndexBackfiller(
            persistence, new AsyncQueue(), () -> indexManager, () -> localDocumentsView);
  }

  @After
  public void tearDown() {
    persistence.shutdown();
  }

  @Test
  public void testBackfillWritesLatestReadTimeToFieldIndexOnCompletion() {
    addFieldIndex("coll1", "foo");
    addFieldIndex("coll2", "bar");
    addDoc("coll1/docA", version(10), "foo", 1);
    addDoc("coll2/docA", version(20), "bar", 1);

    int documentsProcessed = backfiller.backfill();
    assertEquals(2, documentsProcessed);

    FieldIndex fieldIndex1 = indexManager.getFieldIndexes("coll1").iterator().next();
    FieldIndex fieldIndex2 = indexManager.getFieldIndexes("coll2").iterator().next();
    assertEquals(version(10), fieldIndex1.getIndexState().getOffset().getReadTime());
    assertEquals(version(20), fieldIndex2.getIndexState().getOffset().getReadTime());

    addDoc("coll1/docB", version(50, 10), "foo", 1);
    addDoc("coll1/docC", version(50), "foo", 1);
    addDoc("coll2/docB", version(60), "bar", 1);
    addDoc("coll2/docC", version(60, 10), "bar", 1);

    documentsProcessed = backfiller.backfill();
    assertEquals(4, documentsProcessed);

    fieldIndex1 = indexManager.getFieldIndexes("coll1").iterator().next();
    fieldIndex2 = indexManager.getFieldIndexes("coll2").iterator().next();
    assertEquals(version(50, 10), fieldIndex1.getIndexState().getOffset().getReadTime());
    assertEquals(version(60, 10), fieldIndex2.getIndexState().getOffset().getReadTime());
  }

  @Test
  public void testBackfillFetchesDocumentsAfterEarliestReadTime() {
    addFieldIndex("coll1", "foo", version(10));

    // Documents before read time should not be fetched.
    addDoc("coll1/docA", version(9), "foo", 1);
    int documentsProcessed = backfiller.backfill();
    assertEquals(0, documentsProcessed);

    // Read time should be the highest read time from the cache.
    Iterator<FieldIndex> it = indexManager.getFieldIndexes("coll1").iterator();
    assertEquals(
        IndexOffset.create(version(10), DocumentKey.empty(), -1),
        it.next().getIndexState().getOffset());

    // Documents that are after the earliest read time but before field index read time are fetched.
    addDoc("coll1/docB", version(19), "boo", 1);
    documentsProcessed = backfiller.backfill();
    assertEquals(1, documentsProcessed);

    // Field indexes should now hold the latest read time
    it = indexManager.getFieldIndexes("coll1").iterator();
    assertEquals(version(19), it.next().getIndexState().getOffset().getReadTime());
  }

  @Test
  public void testBackfillWritesIndexEntries() {
    addFieldIndex("coll1", "foo");
    addFieldIndex("coll2", "bar");
    addDoc("coll1/docA", version(10), "foo", 1);
    addDoc("coll1/docB", version(10), "boo", 1);
    addDoc("coll2/docA", version(10), "bar", 1);
    addDoc("coll2/docB", version(10), "car", 1);

    int documentsProcessed = backfiller.backfill();
    assertEquals(4, documentsProcessed);
  }

  @Test
  public void testBackfillWritesOldestDocumentFirst() {
    backfiller.setMaxDocumentsToProcess(2);

    addFieldIndex("coll1", "foo");
    addDoc("coll1/docA", version(5), "foo", 1);
    addDoc("coll1/docB", version(3), "foo", 1);
    addDoc("coll1/docC", version(10), "foo", 1);

    int documentsProcessed = backfiller.backfill();
    assertEquals(2, documentsProcessed);

    verifyQueryResults("coll1", "coll1/docA", "coll1/docB");

    documentsProcessed = backfiller.backfill();
    assertEquals(1, documentsProcessed);

    verifyQueryResults("coll1", "coll1/docA", "coll1/docB", "coll1/docC");
  }

  @Test
  public void testBackfillUsesDocumentKeyOffsetForLargeSnapshots() {
    backfiller.setMaxDocumentsToProcess(2);

    addFieldIndex("coll1", "foo");
    addDoc("coll1/docA", version(1), "foo", 1);
    addDoc("coll1/docB", version(1), "foo", 1);
    addDoc("coll1/docC", version(1), "foo", 1);

    int documentsProcessed = backfiller.backfill();
    assertEquals(2, documentsProcessed);

    verifyQueryResults("coll1", "coll1/docA", "coll1/docB");

    documentsProcessed = backfiller.backfill();
    assertEquals(1, documentsProcessed);

    verifyQueryResults("coll1", "coll1/docA", "coll1/docB", "coll1/docC");
  }

  @Test
  public void testBackfillUpdatesCollectionGroups() {
    backfiller.setMaxDocumentsToProcess(2);

    addFieldIndex("coll1", "foo");
    addFieldIndex("coll2", "foo");

    addDoc("coll1/docA", version(10), "foo", 1);
    addDoc("coll1/docB", version(20), "foo", 1);
    addDoc("coll2/docA", version(30), "foo", 1);

    String collectionGroup = indexManager.getNextCollectionGroupToUpdate();
    assertEquals("coll1", collectionGroup);

    int documentsProcessed = backfiller.backfill();
    assertEquals(2, documentsProcessed);

    // Check that coll1 was backfilled and that coll2 is next
    collectionGroup = indexManager.getNextCollectionGroupToUpdate();
    assertEquals("coll2", collectionGroup);
  }

  @Test
  public void testBackfillPrioritizesNewCollectionGroups() {
    backfiller.setMaxDocumentsToProcess(1);

    // In this test case, `coll3` is a new collection group that hasn't been indexed, so it should
    // be processed ahead of the other collection groups.
    addFieldIndex("coll1", "foo", /* sequenceNumber= */ 1);
    addFieldIndex("coll2", "foo", /* sequenceNumber= */ 2);
    addFieldIndex("coll3", "foo", /* sequenceNumber= */ 0);

    addDoc("coll1/doc", version(10), "foo", 1);
    addDoc("coll2/doc", version(20), "foo", 1);
    addDoc("coll3/doc", version(30), "foo", 1);

    // Check that coll3 is the next collection ID the backfiller should update
    assertEquals("coll3", indexManager.getNextCollectionGroupToUpdate());

    int documentsProcessed = backfiller.backfill();
    assertEquals(1, documentsProcessed);

    verifyQueryResults("coll3", "coll3/doc");
  }

  @Test
  public void testBackfillWritesUntilCap() {
    backfiller.setMaxDocumentsToProcess(3);
    addFieldIndex("coll1", "foo");
    addFieldIndex("coll2", "foo");
    addDoc("coll1/docA", version(10), "foo", 1);
    addDoc("coll1/docB", version(20), "foo", 1);
    addDoc("coll2/docA", version(30), "foo", 1);
    addDoc("coll2/docA", version(40), "foo", 1);

    int documentsProcessed = backfiller.backfill();
    assertEquals(3, documentsProcessed);

    verifyQueryResults("coll1", "coll1/docA", "coll1/docB");
    verifyQueryResults("coll2", "coll2/docA");
  }

  @Test
  public void testBackfillUsesLatestReadTimeForEmptyCollections() {
    addFieldIndex("coll", "foo", version(1));
    addDoc("readtime/doc", version(1), "foo", 1);

    int documentsProcessed = backfiller.backfill();
    assertEquals(0, documentsProcessed);

    addDoc("coll/ignored", version(2), "foo", 1);
    addDoc("coll/added", version(3), "foo", 1);

    documentsProcessed = backfiller.backfill();
    assertEquals(2, documentsProcessed);
  }

  @Test
  public void testBackfillHandlesLocalMutationsAfterRemoteDocs() {
    backfiller.setMaxDocumentsToProcess(2);
    addFieldIndex("coll1", "foo");

    addDoc("coll1/docA", version(10), "foo", 1);
    addDoc("coll1/docB", version(20), "foo", 1);
    addDoc("coll1/docC", version(30), "foo", 1);
    addSetMutationsToOverlay(1, "coll1/docD");

    int documentsProcessed = backfiller.backfill();
    assertEquals(2, documentsProcessed);
    verifyQueryResults("coll1", "coll1/docA", "coll1/docB");

    documentsProcessed = backfiller.backfill();
    assertEquals(2, documentsProcessed);
    verifyQueryResults("coll1", "coll1/docA", "coll1/docB", "coll1/docC", "coll1/docD");
  }

  @Test
  public void testBackfillMutationsUpToDocumentLimitAndUpdatesBatchIdOnIndex() {
    backfiller.setMaxDocumentsToProcess(2);
    addFieldIndex("coll1", "foo");
    addDoc("coll1/docA", version(10), "foo", 1);
    addSetMutationsToOverlay(2, "coll1/docB");
    addSetMutationsToOverlay(3, "coll1/docC");
    addSetMutationsToOverlay(4, "coll1/docD");

    int documentsProcessed = backfiller.backfill();
    assertEquals(2, documentsProcessed);
    verifyQueryResults("coll1", "coll1/docA", "coll1/docB");
    FieldIndex fieldIndex = indexManager.getFieldIndexes("coll1").iterator().next();
    assertEquals(2, fieldIndex.getIndexState().getOffset().getLargestBatchId());

    documentsProcessed = backfiller.backfill();
    assertEquals(2, documentsProcessed);
    verifyQueryResults("coll1", "coll1/docA", "coll1/docB", "coll1/docC", "coll1/docD");
    fieldIndex = indexManager.getFieldIndexes("coll1").iterator().next();
    assertEquals(4, fieldIndex.getIndexState().getOffset().getLargestBatchId());
  }

  @Test
  public void testBackfillMutationFinishesMutationBatchEvenIfItExceedsLimit() {
    backfiller.setMaxDocumentsToProcess(2);
    addFieldIndex("coll1", "foo");
    addDoc("coll1/docA", version(10), "foo", 1);
    addSetMutationsToOverlay(2, "coll1/docB", "coll1/docC", "coll1/docD");
    addSetMutationsToOverlay(3, "coll1/docE");

    int documentsProcessed = backfiller.backfill();
    assertEquals(4, documentsProcessed);
    verifyQueryResults("coll1", "coll1/docA", "coll1/docB", "coll1/docC", "coll1/docD");
  }

  @Test
  public void testBackfillMutationsFromHighWaterMark() {
    backfiller.setMaxDocumentsToProcess(2);
    addFieldIndex("coll1", "foo");
    addDoc("coll1/docA", version(10), "foo", 1);
    addSetMutationsToOverlay(3, "coll1/docB");

    int documentsProcessed = backfiller.backfill();
    assertEquals(2, documentsProcessed);
    verifyQueryResults("coll1", "coll1/docA", "coll1/docB");

    addSetMutationsToOverlay(1, "coll1/docC");
    addSetMutationsToOverlay(2, "coll1/docD");
    documentsProcessed = backfiller.backfill();
    assertEquals(0, documentsProcessed);
  }

  @Test
  public void testBackfillUpdatesExistingDocToNewValue() {
    Query queryA = query("coll").filter(filter("foo", "==", 2));
    addFieldIndex("coll", "foo");

    addDoc("coll/doc", version(10), "foo", 1);

    int documentsProcessed = backfiller.backfill();
    assertEquals(1, documentsProcessed);
    verifyQueryResults(queryA);

    // Update doc to new remote version with new value.
    addDoc("coll/doc", version(40), "foo", 2);
    backfiller.backfill();

    verifyQueryResults(queryA, "coll/doc");
  }

  @Test
  public void testBackfillUpdatesDocsThatNoLongerMatch() {
    Query queryA = query("coll").filter(filter("foo", ">", 0));
    addFieldIndex("coll", "foo");
    addDoc("coll/doc", version(10), "foo", 1);

    int documentsProcessed = backfiller.backfill();
    assertEquals(1, documentsProcessed);
    verifyQueryResults(queryA, "coll/doc");

    // Update doc to new remote version with new value that doesn't match field index.
    addDoc("coll/doc", version(40), "foo", -1);

    documentsProcessed = backfiller.backfill();
    assertEquals(1, documentsProcessed);
    verifyQueryResults(queryA);
  }

  @Test
  public void testBackfillDoesNotProcessSameDocumentTwice() {
    addFieldIndex("coll", "foo");
    addDoc("coll/doc", version(5), "foo", 1);
    addSetMutationsToOverlay(1, "coll/doc");

    int documentsProcessed = backfiller.backfill();
    assertEquals(1, documentsProcessed);

    FieldIndex fieldIndex = indexManager.getFieldIndexes("coll").iterator().next();
    assertEquals(version(5), fieldIndex.getIndexState().getOffset().getReadTime());
    assertEquals(1, fieldIndex.getIndexState().getOffset().getLargestBatchId());
  }

  @Test
  public void testBackfillAppliesSetToRemoteDoc() {
    addFieldIndex("coll", "foo");
    addDoc("coll/doc", version(5), "boo", 1);

    int documentsProcessed = backfiller.backfill();
    assertEquals(1, documentsProcessed);

    Mutation patch = patchMutation("coll/doc", map("foo", 1));
    addMutationToOverlay("coll/doc", patch);
    documentsProcessed = backfiller.backfill();
    assertEquals(1, documentsProcessed);

    verifyQueryResults("coll", "coll/doc");
  }

  @Test
  public void testBackfillAppliesPatchToRemoteDoc() {
    Query queryA = query("coll").orderBy(orderBy("a"));
    Query queryB = query("coll").orderBy(orderBy("b"));

    addFieldIndex("coll", "a");
    addFieldIndex("coll", "b");
    addDoc("coll/doc", version(5), "a", 1);

    int documentsProcessed = backfiller.backfill();
    assertEquals(1, documentsProcessed);

    verifyQueryResults(queryA, "coll/doc");
    verifyQueryResults(queryB);

    Mutation patch = patchMutation("coll/doc", map("b", 1));
    addMutationToOverlay("coll/doc", patch);
    documentsProcessed = backfiller.backfill();
    assertEquals(1, documentsProcessed);

    verifyQueryResults(queryA, "coll/doc");
    verifyQueryResults(queryB, "coll/doc");
  }

  @Test
  public void testBackfillAppliesDeleteToRemoteDoc() {
    addFieldIndex("coll", "foo");
    addDoc("coll/doc", version(5), "foo", 1);

    int documentsProcessed = backfiller.backfill();
    assertEquals(1, documentsProcessed);

    Mutation delete = deleteMutation("coll/doc");
    addMutationToOverlay("coll/doc", delete);
    documentsProcessed = backfiller.backfill();
    assertEquals(1, documentsProcessed);

    Target target = query("coll").filter(filter("foo", "==", 2)).toTarget();
    List<DocumentKey> matching = indexManager.getDocumentsMatchingTarget(target);
    assertTrue(matching.isEmpty());
  }

  @Test
  public void testReindexesDocumentsWhenNewIndexIsAdded() {
    Query queryA = query("coll").orderBy(orderBy("a"));
    Query queryB = query("coll").orderBy(orderBy("b"));

    addFieldIndex("coll", "a");
    addDoc("coll/doc1", version(1), "a", 1);
    addDoc("coll/doc2", version(1), "b", 1);

    int documentsProcessed = backfiller.backfill();
    assertEquals(2, documentsProcessed);
    verifyQueryResults(queryA, "coll/doc1");
    verifyQueryResults(queryB);

    addFieldIndex("coll", "b");
    documentsProcessed = backfiller.backfill();
    assertEquals(2, documentsProcessed);

    verifyQueryResults(queryA, "coll/doc1");
    verifyQueryResults(queryB, "coll/doc2");
  }

  private void addFieldIndex(String collectionGroup, String fieldName) {
    FieldIndex fieldIndex =
        fieldIndex(collectionGroup, fieldName, FieldIndex.Segment.Kind.ASCENDING);
    indexManager.addFieldIndex(fieldIndex);
  }

  private void addFieldIndex(String collectionGroup, String fieldName, SnapshotVersion version) {
    FieldIndex fieldIndex =
        fieldIndex(
            collectionGroup,
            FieldIndex.UNKNOWN_ID,
            FieldIndex.IndexState.create(0, version, DocumentKey.empty(), -1),
            fieldName,
            FieldIndex.Segment.Kind.ASCENDING);
    indexManager.addFieldIndex(fieldIndex);
  }

  private void addFieldIndex(String collectionGroup, String fieldName, long sequenceNumber) {
    FieldIndex fieldIndex =
        fieldIndex(
            collectionGroup,
            FieldIndex.UNKNOWN_ID,
            FieldIndex.IndexState.create(sequenceNumber, IndexOffset.NONE),
            fieldName,
            FieldIndex.Segment.Kind.ASCENDING);
    indexManager.addFieldIndex(fieldIndex);
  }

  private void verifyQueryResults(Query query, String... expectedKeys) {
    Target target = query.toTarget();
    List<DocumentKey> actualKeys = indexManager.getDocumentsMatchingTarget(target);
    if (actualKeys == null) {
      assertEquals(0, expectedKeys.length);
    } else {
      assertThat(actualKeys)
          .containsExactlyElementsIn(Arrays.stream(expectedKeys).map(TestUtil::key).toArray());
    }
  }

  private void verifyQueryResults(String collectionGroup, String... expectedKeys) {
    verifyQueryResults(query(collectionGroup).orderBy(orderBy("foo")), expectedKeys);
  }

  /** Creates a document and adds it to the RemoteDocumentCache. */
  private void addDoc(String path, SnapshotVersion readTime, String field, int value) {
    MutableDocument doc = doc(path, 10, map(field, value));
    remoteDocumentCache.add(doc, readTime);
  }

  /** Adds a set mutation to a batch with the specified id for every specified document path. */
  private void addSetMutationsToOverlay(int batchId, String... paths) {
    Map<DocumentKey, Mutation> map = new HashMap<>();
    for (String path : paths) {
      map.put(key(path), setMutation(path, map("foo", "bar")));
    }
    documentOverlayCache.saveOverlays(batchId, map);
  }

  private void addMutationToOverlay(String path, Mutation mutation) {
    documentOverlayCache.saveOverlays(5, Collections.singletonMap(key(path), mutation));
  }
}
