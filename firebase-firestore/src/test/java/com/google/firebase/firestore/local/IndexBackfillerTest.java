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
import static com.google.firebase.firestore.testutil.TestUtil.doc;
import static com.google.firebase.firestore.testutil.TestUtil.fieldIndex;
import static com.google.firebase.firestore.testutil.TestUtil.key;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.orderBy;
import static com.google.firebase.firestore.testutil.TestUtil.query;
import static com.google.firebase.firestore.testutil.TestUtil.setMutation;
import static com.google.firebase.firestore.testutil.TestUtil.version;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertNotEquals;

import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.core.Target;
import com.google.firebase.firestore.index.IndexEntry;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldIndex;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.testutil.TestUtil;
import com.google.firebase.firestore.util.AsyncQueue;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class IndexBackfillerTest {
  /** Current state of indexing support. Used for restoring after test run. */
  private static final boolean supportsIndexing = Persistence.INDEXING_SUPPORT_ENABLED;

  @BeforeClass
  public static void beforeClass() {
    Persistence.INDEXING_SUPPORT_ENABLED = true;
  }

  @AfterClass
  public static void afterClass() {
    Persistence.INDEXING_SUPPORT_ENABLED = supportsIndexing;
  }

  @Rule public TestName name = new TestName();

  private SQLitePersistence persistence;
  private SQLiteIndexManager indexManager;
  private IndexBackfiller backfiller;

  @Before
  public void setUp() {
    persistence = PersistenceTestHelpers.createSQLitePersistence();
    indexManager = (SQLiteIndexManager) persistence.getIndexManager(User.UNAUTHENTICATED);
    indexManager.start();

    RemoteDocumentCache remoteDocumentCache = persistence.getRemoteDocumentCache();
    remoteDocumentCache.setIndexManager(indexManager);

    LocalDocumentsView localDocumentsView =
        new LocalDocumentsView(
            remoteDocumentCache,
            persistence.getMutationQueue(User.UNAUTHENTICATED, indexManager),
            persistence.getDocumentOverlay(User.UNAUTHENTICATED),
            indexManager);
    backfiller = new IndexBackfiller(persistence, new AsyncQueue());
    backfiller.setIndexManager(indexManager);
    backfiller.setLocalDocumentsView(localDocumentsView);
  }

  @After
  public void tearDown() {
    persistence.shutdown();
  }

  @Test
  public void testBackfillWritesLatestReadTimeToFieldIndexOnCompletion() {
    addFieldIndex("coll1", "foo");
    addFieldIndex("coll2", "bar");
    addDoc("coll1/docA", "foo", version(10));
    addDoc("coll2/docA", "bar", version(20));

    int documentsProcessed = backfiller.backfill();
    assertEquals(2, documentsProcessed);

    FieldIndex fieldIndex1 = indexManager.getFieldIndexes("coll1").iterator().next();
    FieldIndex fieldIndex2 = indexManager.getFieldIndexes("coll2").iterator().next();
    assertEquals(version(10), fieldIndex1.getIndexState().getOffset().getReadTime());
    assertEquals(version(20), fieldIndex2.getIndexState().getOffset().getReadTime());

    addDoc("coll1/docB", "foo", version(50, 10));
    addDoc("coll1/docC", "foo", version(50));
    addDoc("coll2/docB", "bar", version(60));
    addDoc("coll2/docC", "bar", version(60, 10));

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

    // Documents before earliest read time should not be fetched.
    addDoc("coll1/docA", "foo", version(9));
    int documentsProcessed = backfiller.backfill();
    assertEquals(0, documentsProcessed);

    // Read time of index should not change.
    Iterator<FieldIndex> it = indexManager.getFieldIndexes("coll1").iterator();
    assertEquals(version(10), it.next().getIndexState().getOffset().getReadTime());

    // Documents that are after the earliest read time but before field index read time are fetched.
    addDoc("coll1/docB", "boo", version(19));
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
    addDoc("coll1/docA", "foo", version(10));
    addDoc("coll1/docB", "boo", version(10));
    addDoc("coll2/docA", "bar", version(10));
    addDoc("coll2/docB", "car", version(10));

    int documentsProcessed = backfiller.backfill();
    assertEquals(4, documentsProcessed);
  }

  @Test
  public void testBackfillWritesOldestDocumentFirst() {
    backfiller.setMaxDocumentsToProcess(2);

    addFieldIndex("coll1", "foo");
    Target target = query("coll1").orderBy(orderBy("foo")).toTarget();
    addDoc("coll1/docA", "foo", version(5));
    addDoc("coll1/docB", "foo", version(3));
    addDoc("coll1/docC", "foo", version(10));

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
    Target target = query("coll1").orderBy(orderBy("foo")).toTarget();
    addDoc("coll1/docA", "foo", version(1));
    addDoc("coll1/docB", "foo", version(1));
    addDoc("coll1/docC", "foo", version(1));

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

    addDoc("coll1/docA", "foo", version(10));
    addDoc("coll1/docB", "foo", version(20));
    addDoc("coll2/docA", "foo", version(30));

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

    addDoc("coll1/doc", "foo", version(10));
    addDoc("coll2/doc", "foo", version(20));
    addDoc("coll3/doc", "foo", version(30));

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
    addDoc("coll1/docA", "foo", version(10));
    addDoc("coll1/docB", "foo", version(20));
    addDoc("coll2/docA", "foo", version(30));
    addDoc("coll2/docA", "foo", version(40));

    int documentsProcessed = backfiller.backfill();
    assertEquals(3, documentsProcessed);

    verifyQueryResults("coll1", "coll1/docA", "coll1/docB");
    verifyQueryResults("coll2", "coll2/docA");
  }

  @Test
  public void testBackfillUsesLatestReadTimeForEmptyCollections() {
    addFieldIndex("coll", "foo", version(1));
    addDoc("readtime/doc", "foo", version(2));

    int documentsProcessed = backfiller.backfill();
    assertEquals(0, documentsProcessed);

    addDoc("coll/ignored", "foo", version(2));
    addDoc("coll/added", "foo", version(3));

    documentsProcessed = backfiller.backfill();
    assertEquals(1, documentsProcessed);
  }

  @Test
  public void testBackfillHandlesLocalMutationsAfterRemoteDocs() {
    backfiller.setMaxDocumentsToProcess(2);
    addFieldIndex("coll1", "foo");

    addDoc("coll1/docA", "foo", version(10));
    addDoc("coll1/docB", "foo", version(20));
    addDoc("coll1/docC", "foo", version(30));
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
    addDoc("coll1/docA", "foo", version(10));
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

  // TODO(indexing): Support adding local mutations to collection group table so this test passes.
  @Test
  @Ignore
  public void testBackfillMutationsWithNoPriorRemoteDoc() {
    addFieldIndex("coll1", "foo");
    addSetMutationsToOverlay(2, "coll1/docA");
    addSetMutationsToOverlay(4, "coll1/docB");

    int documentsProcessed = backfiller.backfill();
    assertEquals(2, documentsProcessed);
  }

  @Test
  public void testBackfillMutationFinishesMutationBatchEvenIfItExceedsLimit() {
    backfiller.setMaxDocumentsToProcess(2);
    addFieldIndex("coll1", "foo");
    addDoc("coll1/docA", "foo", version(10));
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
    addDoc("coll1/docA", "foo", version(10));
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
    backfiller.setMaxDocumentsToProcess(1);
    addFieldIndex("coll1", "foo");
    FieldIndex fieldIndex = indexManager.getFieldIndexes("coll1").iterator().next();

    Document docA = addDoc("coll1/docA", "foo", version(10));
    int documentsProcessed = backfiller.backfill();
    assertEquals(1, documentsProcessed);
    SortedSet<IndexEntry> before = indexManager.getExistingIndexEntries(docA.getKey(), fieldIndex);

    // Update doc to new remote version with new value.
    removeDoc(docA);
    addDoc("coll1/docA", "foo", version(40), 5);
    documentsProcessed = backfiller.backfill();
    assertEquals(1, documentsProcessed);
    SortedSet<IndexEntry> after = indexManager.getExistingIndexEntries(docA.getKey(), fieldIndex);
    assertNotEquals(before.first().getDirectionalValue(), after.first().getDirectionalValue());
  }

  @Test
  public void testBackfillUpdatesDocsThatNoLongerMatch() {
    backfiller.setMaxDocumentsToProcess(1);
    addFieldIndex("coll1", "foo");
    FieldIndex fieldIndex = indexManager.getFieldIndexes("coll1").iterator().next();

    Document docA = addDoc("coll1/docA", "foo", version(10));
    int documentsProcessed = backfiller.backfill();
    assertEquals(1, documentsProcessed);
    SortedSet<IndexEntry> before = indexManager.getExistingIndexEntries(docA.getKey(), fieldIndex);

    // Update doc to new remote version with new value that doesn't match field index.
    removeDoc(docA);
    addDoc("coll1/docA", "bar", version(40), 5);
    documentsProcessed = backfiller.backfill();
    assertEquals(1, documentsProcessed);
    SortedSet<IndexEntry> after = indexManager.getExistingIndexEntries(docA.getKey(), fieldIndex);
    assertTrue(after.isEmpty());
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
            FieldIndex.IndexState.create(0, version, DocumentKey.empty()),
            fieldName,
            FieldIndex.Segment.Kind.ASCENDING);
    indexManager.addFieldIndex(fieldIndex);
  }

  private void addFieldIndex(String collectionGroup, String fieldName, long sequenceNumber) {
    FieldIndex fieldIndex =
        fieldIndex(
            collectionGroup,
            FieldIndex.UNKNOWN_ID,
            FieldIndex.IndexState.create(sequenceNumber, SnapshotVersion.NONE, DocumentKey.empty()),
            fieldName,
            FieldIndex.Segment.Kind.ASCENDING);
    indexManager.addFieldIndex(fieldIndex);
  }

  private void verifyQueryResults(String collectionGroup, String... expectedKeys) {
    Target target = query(collectionGroup).orderBy(orderBy("foo")).toTarget();
    FieldIndex persistedIndex = indexManager.getFieldIndex(target);
    Set<DocumentKey> actualKeys = indexManager.getDocumentsMatchingTarget(persistedIndex, target);
    assertThat(actualKeys)
        .containsExactlyElementsIn(Arrays.stream(expectedKeys).map(TestUtil::key).toArray());
  }

  /** Creates a document and adds it to the RemoteDocumentCache. */
  private Document addDoc(String path, String field, SnapshotVersion readTime) {
    return addDoc(path, field, readTime, 2);
  }

  /** Creates a document and adds it to the RemoteDocumentCache. */
  private Document addDoc(String path, String field, SnapshotVersion readTime, int value) {
    MutableDocument doc = doc(path, 10, map(field, value));
    persistence.getRemoteDocumentCache().add(doc, readTime);
    return doc;
  }

  /** Removes the specified document from the RemoteDocumentCache. */
  private void removeDoc(Document doc) {
    persistence.getRemoteDocumentCache().remove(doc.getKey());
  }

  /** Adds a set mutation to a batch with the specified id for every specified document path. */
  private void addSetMutationsToOverlay(int batchId, String... paths) {
    Map<DocumentKey, Mutation> map = new HashMap<>();
    for (String path : paths) {
      Mutation mutation = setMutation(path, map("foo", "bar"));
      map.put(key(path), mutation);
    }
    persistence.getDocumentOverlay(User.UNAUTHENTICATED).saveOverlays(batchId, map);
  }
}
