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
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.orderBy;
import static com.google.firebase.firestore.testutil.TestUtil.query;
import static com.google.firebase.firestore.testutil.TestUtil.version;
import static junit.framework.TestCase.assertEquals;

import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.core.Target;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldIndex;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.testutil.TestUtil;
import com.google.firebase.firestore.util.AsyncQueue;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
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

    IndexBackfiller.Results results = backfiller.backfill();
    assertEquals(2, results.getDocumentsProcessed());

    FieldIndex fieldIndex1 = indexManager.getFieldIndexes("coll1").iterator().next();
    FieldIndex fieldIndex2 = indexManager.getFieldIndexes("coll2").iterator().next();
    assertEquals(version(10), fieldIndex1.getIndexState().getReadTime());
    assertEquals(version(20), fieldIndex2.getIndexState().getReadTime());

    addDoc("coll1/docB", "foo", version(50, 10));
    addDoc("coll1/docC", "foo", version(50));
    addDoc("coll2/docB", "bar", version(60));
    addDoc("coll2/docC", "bar", version(60, 10));

    results = backfiller.backfill();
    assertEquals(4, results.getDocumentsProcessed());

    fieldIndex1 = indexManager.getFieldIndexes("coll1").iterator().next();
    fieldIndex2 = indexManager.getFieldIndexes("coll2").iterator().next();
    assertEquals(version(50, 10), fieldIndex1.getIndexState().getReadTime());
    assertEquals(version(60, 10), fieldIndex2.getIndexState().getReadTime());
  }

  @Test
  public void testBackfillFetchesDocumentsAfterEarliestReadTime() {
    addFieldIndex("coll1", "foo", version(10));

    // Documents before earliest read time should not be fetched.
    addDoc("coll1/docA", "foo", version(9));
    IndexBackfiller.Results results = backfiller.backfill();
    assertEquals(0, results.getDocumentsProcessed());

    // Read time of index should not change.
    Iterator<FieldIndex> it = indexManager.getFieldIndexes("coll1").iterator();
    assertEquals(version(10), it.next().getIndexState().getReadTime());

    // Documents that are after the earliest read time but before field index read time are fetched.
    addDoc("coll1/docB", "boo", version(19));
    results = backfiller.backfill();
    assertEquals(1, results.getDocumentsProcessed());

    // Field indexes should now hold the latest read time
    it = indexManager.getFieldIndexes("coll1").iterator();
    assertEquals(version(19), it.next().getIndexState().getReadTime());
  }

  @Test
  public void testBackfillWritesIndexEntries() {
    addFieldIndex("coll1", "foo");
    addFieldIndex("coll2", "bar");
    addDoc("coll1/docA", "foo", version(10));
    addDoc("coll1/docB", "boo", version(10));
    addDoc("coll2/docA", "bar", version(10));
    addDoc("coll2/docB", "car", version(10));

    IndexBackfiller.Results results = backfiller.backfill();
    assertEquals(4, results.getDocumentsProcessed());
  }

  @Test
  public void testBackfillWritesOldestDocumentFirst() {
    backfiller.setMaxDocumentsToProcess(2);

    addFieldIndex("coll1", "foo");
    Target target = query("coll1").orderBy(orderBy("foo")).toTarget();
    addDoc("coll1/docA", "foo", version(5));
    addDoc("coll1/docB", "foo", version(3));
    addDoc("coll1/docC", "foo", version(10));

    IndexBackfiller.Results results = backfiller.backfill();
    assertEquals(2, results.getDocumentsProcessed());

    verifyQueryResults("coll1", "coll1/docA", "coll1/docB");

    results = backfiller.backfill();
    assertEquals(1, results.getDocumentsProcessed());

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

    IndexBackfiller.Results results = backfiller.backfill();
    assertEquals(2, results.getDocumentsProcessed());

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

    IndexBackfiller.Results results = backfiller.backfill();
    assertEquals(1, results.getDocumentsProcessed());

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

    IndexBackfiller.Results results = backfiller.backfill();
    assertEquals(3, results.getDocumentsProcessed());

    verifyQueryResults("coll1", "coll1/docA", "coll1/docB");
    verifyQueryResults("coll2", "coll2/docA");
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
            FieldIndex.IndexState.create(0, version),
            fieldName,
            FieldIndex.Segment.Kind.ASCENDING);
    indexManager.addFieldIndex(fieldIndex);
  }

  private void addFieldIndex(String collectionGroup, String fieldName, long sequenceNumber) {
    FieldIndex fieldIndex =
        fieldIndex(
            collectionGroup,
            FieldIndex.UNKNOWN_ID,
            FieldIndex.IndexState.create(sequenceNumber, SnapshotVersion.NONE),
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
  private void addDoc(String path, String field, SnapshotVersion readTime) {
    MutableDocument doc = doc(path, 10, map(field, 2));
    persistence.getRemoteDocumentCache().add(doc, readTime);
  }
}
