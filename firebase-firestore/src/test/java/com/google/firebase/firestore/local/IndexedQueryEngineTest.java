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

import static com.google.firebase.firestore.testutil.TestUtil.doc;
import static com.google.firebase.firestore.testutil.TestUtil.docMap;
import static com.google.firebase.firestore.testutil.TestUtil.filter;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.query;
import static org.junit.Assert.assertTrue;

import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.SnapshotVersion;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class IndexedQueryEngineTest {
  /** Current state of indexing support. Used for restoring after test run. */
  private static final boolean supportsIndexing = Persistence.INDEXING_SUPPORT_ENABLED;

  private IndexedQueryEngine queryEngine;
  private IndexManager indexManager;
  private RemoteDocumentCache remoteDocuments;

  @BeforeClass
  public static void beforeClass() {
    Persistence.INDEXING_SUPPORT_ENABLED = true;
  }

  @BeforeClass
  public static void afterClass() {
    Persistence.INDEXING_SUPPORT_ENABLED = supportsIndexing;
  }

  @Before
  public void setUp() {
    SQLitePersistence persistence = PersistenceTestHelpers.createSQLitePersistence();
    indexManager = persistence.getIndexManager(User.UNAUTHENTICATED);
    indexManager.start();

    remoteDocuments = persistence.getRemoteDocumentCache();
    remoteDocuments.setIndexManager(indexManager);
    queryEngine = new IndexedQueryEngine();
    queryEngine.setLocalDocumentsView(
        new LocalDocumentsView(
            remoteDocuments,
            persistence.getMutationQueue(User.UNAUTHENTICATED, indexManager),
            persistence.getDocumentOverlay(User.UNAUTHENTICATED),
            indexManager));
    queryEngine.setIndexManager(indexManager);
  }

  @Test
  public void combinesIndexedWithNonIndexedResults() {
    MutableDocument doc1 = doc("coll/a", 1, map("foo", true));
    MutableDocument doc2 = doc("coll/b", 2, map("foo", true));
    MutableDocument doc3 = doc("coll/c", 3, map("foo", true));

    remoteDocuments.add(doc1, doc1.getVersion());
    indexManager.updateIndexEntries(docMap(doc1));

    remoteDocuments.add(doc2, doc2.getVersion());
    indexManager.updateIndexEntries(docMap(doc2));

    remoteDocuments.add(doc3, doc3.getVersion());

    Query queryWithFilter = query("coll").filter(filter("foo", "==", true));
    ImmutableSortedMap<DocumentKey, Document> results =
        queryEngine.getDocumentsMatchingQuery(
            queryWithFilter, SnapshotVersion.NONE, DocumentKey.emptyKeySet());

    assertTrue(results.containsKey(doc1.getKey()));
    assertTrue(results.containsKey(doc2.getKey()));
    assertTrue(results.containsKey(doc3.getKey()));
  }
}
