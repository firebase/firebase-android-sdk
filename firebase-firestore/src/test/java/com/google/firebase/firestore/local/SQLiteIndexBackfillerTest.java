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

import static com.google.firebase.firestore.local.SQLiteIndexManagerTest.getCollectionGroupsOrderByUpdateTime;
import static com.google.firebase.firestore.testutil.TestUtil.doc;
import static com.google.firebase.firestore.testutil.TestUtil.field;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.version;
import static junit.framework.TestCase.assertEquals;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.model.FieldIndex;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.util.AsyncQueue;
import java.util.List;
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
public class SQLiteIndexBackfillerTest {
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
  private LocalDocumentsView localDocumentsView;

  @Before
  public void setUp() {
    persistence = PersistenceTestHelpers.createSQLitePersistence();
    indexManager = (SQLiteIndexManager) persistence.getIndexManager(User.UNAUTHENTICATED);
    RemoteDocumentCache remoteDocumentCache = persistence.getRemoteDocumentCache();
    remoteDocumentCache.setIndexManager(indexManager);
    localDocumentsView =
        new LocalDocumentsView(
            remoteDocumentCache,
            persistence.getMutationQueue(User.UNAUTHENTICATED),
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

  // TODO(indexing): Re-enable this test once we have counters implemented.
  @Test
  @Ignore
  public void testBackfillWritesLatestReadTimeToFieldIndexOnCompletion() {
    addFieldIndex("coll1", "foo");
    addFieldIndex("coll2", "bar");
    addDoc("coll1/docA", "foo", version(10, 0));
    addDoc("coll2/docA", "bar", version(20, 0));

    IndexBackfiller.Results results = backfiller.backfill();
    assertEquals(2, results.getEntriesAdded());

    FieldIndex fieldIndex1 = indexManager.getFieldIndexes("coll1").get(0);
    FieldIndex fieldIndex2 = indexManager.getFieldIndexes("coll2").get(0);
    assertEquals(version(10, 0), fieldIndex1.getUpdateTime());
    assertEquals(version(20, 0), fieldIndex2.getUpdateTime());

    addDoc("coll1/docB", "foo", version(50, 10));
    addDoc("coll1/docC", "foo", version(50, 0));
    addDoc("coll2/docB", "bar", version(60, 0));
    addDoc("coll2/docC", "bar", version(60, 10));

    results = backfiller.backfill();
    assertEquals(4, results.getEntriesAdded());

    fieldIndex1 = indexManager.getFieldIndexes("coll1").get(0);
    fieldIndex2 = indexManager.getFieldIndexes("coll2").get(0);
    assertEquals(version(50, 10), fieldIndex1.getUpdateTime());
    assertEquals(version(60, 10), fieldIndex2.getUpdateTime());
  }

  @Test
  public void testBackfillFetchesDocumentsAfterEarliestReadTime() {
    addFieldIndex("coll1", "foo", version(10, 0));
    addFieldIndex("coll1", "boo", version(20, 0));
    addFieldIndex("coll1", "moo", version(30, 0));

    // Documents before earliest read time should not be fetched.
    addDoc("coll1/docA", "foo", version(9, 0));
    IndexBackfiller.Results results = backfiller.backfill();
    assertEquals(0, results.getEntriesAdded());

    // Documents that are after the earliest read time but before field index read time are fetched.
    addDoc("coll1/docB", "boo", version(19, 0));
    results = backfiller.backfill();
    assertEquals(1, results.getEntriesAdded());

    // Field indexes should still hold the latest read time.
    FieldIndex fieldIndex1 = indexManager.getFieldIndexes("coll1").get(0);
    FieldIndex fieldIndex2 = indexManager.getFieldIndexes("coll1").get(1);
    FieldIndex fieldIndex3 = indexManager.getFieldIndexes("coll1").get(2);
    assertEquals(version(10, 0), fieldIndex1.getUpdateTime());
    assertEquals(version(20, 0), fieldIndex2.getUpdateTime());
    assertEquals(version(30, 0), fieldIndex3.getUpdateTime());
  }

  @Test
  public void testBackfillWritesIndexEntries() {
    addFieldIndex("coll1", "foo");
    addFieldIndex("coll2", "bar");
    addDoc("coll1/docA", "foo", version(10, 0));
    addDoc("coll1/docB", "boo", version(10, 0));
    addDoc("coll2/docA", "bar", version(10, 0));
    addDoc("coll2/docB", "car", version(10, 0));

    IndexBackfiller.Results results = backfiller.backfill();
    assertEquals(2, results.getEntriesAdded());
  }

  @Test
  public void testBackfillUpdatesCollectionGroups() {
    addFieldIndex("coll1", "foo");
    addFieldIndex("coll2", "foo");
    addFieldIndex("coll3", "foo");
    addCollectionGroup("coll1", new Timestamp(30, 0));
    addCollectionGroup("coll2", new Timestamp(30, 30));
    addCollectionGroup("coll3", new Timestamp(10, 0));
    addDoc("coll1/docA", "foo", version(10, 0));
    addDoc("coll2/docA", "foo", version(10, 0));
    addDoc("coll3/docA", "foo", version(10, 0));

    IndexBackfiller.Results results = backfiller.backfill();
    assertEquals(3, results.getEntriesAdded());

    // Check that index entries are written in order of the collection group update times by
    // verifying the collection group update times have been updated in the correct order.
    List<String> collectionGroups = getCollectionGroupsOrderByUpdateTime(persistence);
    assertEquals(3, collectionGroups.size());
    assertEquals("coll3", collectionGroups.get(0));
    assertEquals("coll1", collectionGroups.get(1));
    assertEquals("coll2", collectionGroups.get(2));
  }

  @Test
  @Ignore("Flaky")
  // TODO(indexing): This test is flaky. Fix.
  public void testBackfillPrioritizesNewCollectionGroups() {
    // In this test case, `coll3` is a new collection group that hasn't been indexed, so it should
    // be processed ahead of the other collection groups.
    addFieldIndex("coll1", "foo");
    addFieldIndex("coll2", "foo");
    addCollectionGroup("coll1", new Timestamp(1, 0));
    addCollectionGroup("coll2", new Timestamp(2, 0));
    addFieldIndex("coll3", "foo");

    IndexBackfiller.Results results = backfiller.backfill();
    assertEquals(0, results.getEntriesAdded());

    // Check that index entries are written in order of the collection group update times by
    // verifying the collection group update times have been updated in the correct order.
    List<String> collectionGroups = getCollectionGroupsOrderByUpdateTime(persistence);
    assertEquals(3, collectionGroups.size());
    assertEquals("coll3", collectionGroups.get(0));
    assertEquals("coll1", collectionGroups.get(1));
    assertEquals("coll2", collectionGroups.get(2));
  }

  @Test
  public void testBackfillWritesUntilCap() {
    backfiller.setMaxIndexEntriesToProcess(3);
    addFieldIndex("coll1", "foo");
    addFieldIndex("coll2", "foo");
    addCollectionGroup("coll1", new Timestamp(1, 0));
    addDoc("coll1/docA", "foo", version(10, 0));
    addDoc("coll1/docB", "foo", version(10, 0));
    addDoc("coll2/docA", "foo", version(10, 0));
    addDoc("coll2/docB", "foo", version(10, 0));

    IndexBackfiller.Results results = backfiller.backfill();
    assertEquals(3, results.getEntriesAdded());

    // Check that collection groups are updated even if the backfiller hits the write cap. Since
    // `coll1` was already in the table, `coll2` should be processed first, and thus appear first
    // in the ordering.
    List<String> collectionGroups = getCollectionGroupsOrderByUpdateTime(persistence);
    assertEquals(2, collectionGroups.size());
    assertEquals("coll2", collectionGroups.get(0));
    assertEquals("coll1", collectionGroups.get(1));
  }

  private void addFieldIndex(String collectionGroup, String fieldName) {
    indexManager.addFieldIndex(
        new FieldIndex(collectionGroup)
            .withAddedField(field(fieldName), FieldIndex.Segment.Kind.ASCENDING));
  }

  private void addFieldIndex(String collectionGroup, String fieldName, SnapshotVersion readTime) {
    indexManager.addFieldIndex(
        new FieldIndex(collectionGroup)
            .withAddedField(field(fieldName), FieldIndex.Segment.Kind.ASCENDING)
            .withUpdateTime(readTime));
  }

  private void addCollectionGroup(String collectionGroup, Timestamp updateTime) {
    indexManager.setCollectionGroupUpdateTime(collectionGroup, updateTime);
  }

  /** Creates a document and adds it to the RemoteDocumentCache. */
  private void addDoc(String path, String field, SnapshotVersion readTime) {
    MutableDocument doc = doc(path, 10, map(field, 2));
    persistence.getRemoteDocumentCache().add(doc, readTime);
  }
}
