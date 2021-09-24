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

import static com.google.firebase.firestore.testutil.TestUtil.doc;
import static com.google.firebase.firestore.testutil.TestUtil.field;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.version;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;

import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.index.IndexEntry;
import com.google.firebase.firestore.model.FieldIndex;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.SnapshotVersion;
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
  private LocalStore localStore;

  @Before
  public void setUp() {
    persistence = PersistenceTestHelpers.createSQLitePersistence();
    indexManager = (SQLiteIndexManager) persistence.getIndexManager();
    backfiller = persistence.getIndexBackfiller();
    CountingQueryEngine queryEngine = new CountingQueryEngine(new DefaultQueryEngine());
    localStore = new LocalStore(persistence, queryEngine, User.UNAUTHENTICATED);
  }

  @After
  public void tearDown() {
    persistence.shutdown();
  }

  @Test
  public void testBackfillFetchesNewIndexes() {
    addFieldIndex("coll1", "foo");
    addFieldIndex("coll2", "foo");
    backfiller.backfill(localStore);
    assertEquals(2, backfiller.getFieldIndexQueue().size());
    addFieldIndex("coll3", "foo");
    addFieldIndex("coll4", "foo");
    addFieldIndex("coll5", "foo");
    backfiller.backfill(localStore);
    assertEquals(5, backfiller.getFieldIndexQueue().size());
    for (int i = 0; i < 5; i++) {
      assertEquals("coll" + (i + 1), backfiller.getFieldIndexQueue().get(i).getCollectionGroup());
    }
  }

  @Test
  public void testBackfillMovesProcessedFieldIndexesToTheEndOfQueue() {
    for (int i = 0; i < 5; i++) {
      addFieldIndex("coll" + i, "foo");
    }
    backfiller.setMaxFieldIndexesToProcess(2);
    backfiller.backfill(localStore);

    // Processed field indexes should go to the back of the queue.
    assertEquals(5, backfiller.getFieldIndexQueue().size());
    assertEquals(1, backfiller.getFieldIndexQueue().get(3).getIndexId());
    assertEquals(2, backfiller.getFieldIndexQueue().get(4).getIndexId());

    backfiller.backfill(localStore);
    assertEquals(3, backfiller.getFieldIndexQueue().get(3).getIndexId());
    assertEquals(4, backfiller.getFieldIndexQueue().get(4).getIndexId());
  }

  // TODO(indexing): Use RemoteDocumentCache read time rather than document's version.
  @Test
  @Ignore
  public void testBackfillWritesToIndexConfigOnCompletion() {
    // Check that index_config is updated to new version after each write.
    addFieldIndex("coll1", "foo");
    addDoc("coll1/docA", "foo", version(10, 20));
    backfiller.backfill(localStore);
    assertEquals(version(10, 20), indexManager.getFieldIndexes(0).get(0).getVersion());

    addDoc("coll1/docA", "foo", version(20, 30));
    backfiller.backfill(localStore);
    assertEquals(version(2, 30), indexManager.getFieldIndexes(0).get(0).getVersion());
  }

  // TODO(indexing): Use RemoteDocumentCache read time rather than document's version.
  @Test
  @Ignore
  public void testBackfillFetchesDocumentsWithSnapshotVersion() {}

  @Test
  public void testBackfillWritesIndexEntries() {
    addFieldIndex("coll1", "foo");
    addFieldIndex("coll2", "bar");
    addDoc("coll1/docA", "foo", version(10, 0));
    addDoc("coll1/docB", "boo", version(10, 0));
    addDoc("coll2/docA", "bar", version(10, 0));
    addDoc("coll2/docB", "car", version(10, 0));

    IndexBackfiller.Results results = backfiller.backfill(localStore);
    assertEquals(2, results.getEntriesAdded());
  }

  @Test
  public void testAddAndRemoveIndexEntry() {
    IndexEntry testEntry =
        new IndexEntry(1, "TEST_BLOB".getBytes(), "sample-uid", "coll/sample-documentId");
    persistence.runTransaction(
        "testAddAndRemoveIndexEntry",
        () -> {
          backfiller.addIndexEntry(testEntry);
          IndexEntry entry = backfiller.getIndexEntry(1);
          assertNotNull(entry);
          assertEquals("TEST_BLOB", new String(entry.getIndexValue()));
          assertEquals("coll/sample-documentId", entry.getDocumentName());
          assertEquals("sample-uid", entry.getUid());

          backfiller.removeIndexEntry(1, "sample-uid", "coll/sample-documentId");
          entry = backfiller.getIndexEntry(1);
          assertNull(entry);
        });
  }

  void addFieldIndex(String collectionId, String fieldName) {
    indexManager.addFieldIndex(
        new FieldIndex(collectionId)
            .withAddedField(field(fieldName), FieldIndex.Segment.Kind.ORDERED));
  }

  private void addDoc(String path, String field, SnapshotVersion readTime) {
    MutableDocument doc = doc(path, 10, map(field, 2));
    persistence.getRemoteDocumentCache().add(doc, readTime);
  }
}
