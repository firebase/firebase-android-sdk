// Copyright 2019 Google LLC
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
import static com.google.firebase.firestore.testutil.TestUtil.docSet;
import static com.google.firebase.firestore.testutil.TestUtil.filter;
import static com.google.firebase.firestore.testutil.TestUtil.key;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.orderBy;
import static com.google.firebase.firestore.testutil.TestUtil.query;
import static com.google.firebase.firestore.testutil.TestUtil.version;
import static org.junit.Assert.assertEquals;

import com.google.android.gms.common.internal.Preconditions;
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.core.View;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.DocumentSet;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class IndexFreeQueryEngineTest {

  private static final int TEST_TARGET_ID = 1;

  private static final Document MATCHING_DOC_A =
      doc("coll/a", 1, map("matches", true, "order", 1), Document.DocumentState.SYNCED);
  private static final Document NON_MATCHING_DOC_A =
      doc("coll/a", 1, map("matches", false, "order", 1), Document.DocumentState.SYNCED);
  private static final Document PENDING_MATCHING_DOC_A =
      doc("coll/a", 1, map("matches", true, "order", 1), Document.DocumentState.LOCAL_MUTATIONS);
  private static final Document PENDING_NON_MATCHING_DOC_A =
      doc("coll/a", 1, map("matches", false, "order", 1), Document.DocumentState.LOCAL_MUTATIONS);
  private static final Document UDPATED_DOC_A =
      doc("coll/a", 11, map("matches", true, "order", 1), Document.DocumentState.SYNCED);
  private static final Document MATCHING_DOC_B =
      doc("coll/b", 1, map("matches", true, "order", 2), Document.DocumentState.SYNCED);
  private static final Document UPDATED_MATCHING_DOC_B =
      doc("coll/b", 11, map("matches", true, "order", 2), Document.DocumentState.SYNCED);

  private MemoryPersistence persistence;
  private MemoryRemoteDocumentCache remoteDocumentCache;
  private QueryCache queryCache;
  private QueryEngine queryEngine;

  private @Nullable Boolean expectIndexFreeExecution;

  @Before
  public void setUp() {
    expectIndexFreeExecution = null;

    persistence = MemoryPersistence.createEagerGcMemoryPersistence();
    queryCache = new MemoryQueryCache(persistence);
    queryEngine = new IndexFreeQueryEngine();

    remoteDocumentCache = persistence.getRemoteDocumentCache();

    LocalDocumentsView localDocuments =
        new LocalDocumentsView(
            remoteDocumentCache,
            persistence.getMutationQueue(User.UNAUTHENTICATED),
            new MemoryIndexManager()) {
          @Override
          public ImmutableSortedMap<DocumentKey, Document> getDocumentsMatchingQuery(
              Query query, SnapshotVersion sinceReadTime) {
            assertEquals(
                "Observed query execution mode did not match expectation",
                expectIndexFreeExecution,
                !SnapshotVersion.NONE.equals(sinceReadTime));
            return super.getDocumentsMatchingQuery(query, sinceReadTime);
          }
        };
    queryEngine.setLocalDocumentsView(localDocuments);
  }

  /** Adds the provided documents to the query target mapping. */
  private void persistQueryMapping(DocumentKey... documentKeys) {
    persistence.runTransaction(
        "persistQueryMapping",
        () -> {
          ImmutableSortedSet<DocumentKey> remoteKeys = DocumentKey.emptyKeySet();
          for (DocumentKey documentKey : documentKeys) {
            remoteKeys = remoteKeys.insert(documentKey);
          }
          queryCache.addMatchingKeys(remoteKeys, TEST_TARGET_ID);
        });
  }

  /** Adds the provided documents to the remote document cache. */
  private void addDocument(Document... docs) {
    persistence.runTransaction(
        "addDocument",
        () -> {
          for (Document doc : docs) {
            remoteDocumentCache.add(doc, doc.getVersion());
          }
        });
  }

  private <T> T expectIndexFreeQuery(Callable<T> c) throws Exception {
    try {
      expectIndexFreeExecution = true;
      return c.call();
    } finally {
      expectIndexFreeExecution = null;
    }
  }

  private <T> T expectFullCollectionQuery(Callable<T> c) throws Exception {
    try {
      expectIndexFreeExecution = false;
      return c.call();
    } finally {
      expectIndexFreeExecution = null;
    }
  }

  private DocumentSet runQuery(Query query, QueryData queryData) {
    Preconditions.checkNotNull(
        expectIndexFreeExecution,
        "Encountered runQuery() call not wrapped in expectIndexFreeQuery()/expectFullCollectionQuery()");
    ImmutableSortedMap<DocumentKey, Document> docs =
        queryEngine.getDocumentsMatchingQuery(
            query, queryData, queryCache.getMatchingKeysForTargetId(TEST_TARGET_ID));
    View view =
        new View(query, new ImmutableSortedSet<>(Collections.emptyList(), DocumentKey::compareTo));
    View.DocumentChanges viewDocChanges = view.computeDocChanges(docs);
    return view.applyChanges(viewDocChanges).getSnapshot().getDocuments();
  }

  @Test
  public void usesTargetMappingForInitialView() throws Exception {
    Query query = query("coll").filter(filter("matches", "==", true));
    QueryData queryData = queryData(query, /* hasLimboFreeSnapshot= */ true);

    addDocument(MATCHING_DOC_A, MATCHING_DOC_B);
    persistQueryMapping(MATCHING_DOC_A.getKey(), MATCHING_DOC_B.getKey());

    DocumentSet docs = expectIndexFreeQuery(() -> runQuery(query, queryData));
    assertEquals(docSet(query.comparator(), MATCHING_DOC_A, MATCHING_DOC_B), docs);
  }

  @Test
  public void filtersNonMatchingInitialResults() throws Exception {
    Query query = query("coll").filter(filter("matches", "==", true));
    QueryData queryData = queryData(query, /* hasLimboFreeSnapshot= */ true);

    addDocument(MATCHING_DOC_A, MATCHING_DOC_B);
    persistQueryMapping(MATCHING_DOC_A.getKey(), MATCHING_DOC_B.getKey());

    // Add a mutated document that is not yet part of query's set of remote keys.
    addDocument(PENDING_NON_MATCHING_DOC_A);

    DocumentSet docs = expectIndexFreeQuery(() -> runQuery(query, queryData));
    assertEquals(docSet(query.comparator(), MATCHING_DOC_B), docs);
  }

  @Test
  public void includesChangesSinceInitialResults() throws Exception {
    Query query = query("coll").filter(filter("matches", "==", true));
    QueryData originalQueryData = queryData(query, /* hasLimboFreeSnapshot= */ true);

    addDocument(MATCHING_DOC_A, MATCHING_DOC_B);
    persistQueryMapping(MATCHING_DOC_A.getKey(), MATCHING_DOC_B.getKey());

    DocumentSet docs = expectIndexFreeQuery(() -> runQuery(query, originalQueryData));
    assertEquals(docSet(query.comparator(), MATCHING_DOC_A, MATCHING_DOC_B), docs);

    addDocument(UPDATED_MATCHING_DOC_B);

    docs = expectIndexFreeQuery(() -> runQuery(query, originalQueryData));
    assertEquals(docSet(query.comparator(), MATCHING_DOC_A, UPDATED_MATCHING_DOC_B), docs);
  }

  @Test
  public void doesNotUseInitialResultsWithoutLimboFreeSnapshotVersion() throws Exception {
    Query query = query("coll").filter(filter("matches", "==", true));
    QueryData queryData = queryData(query, /* hasLimboFreeSnapshot= */ false);

    DocumentSet docs = expectFullCollectionQuery(() -> runQuery(query, queryData));
    assertEquals(docSet(query.comparator()), docs);
  }

  @Test
  public void doesNotUseInitialResultsForUnfilteredCollectionQuery() throws Exception {
    Query query = query("coll");
    QueryData queryData = queryData(query, /* hasLimboFreeSnapshot= */ true);

    DocumentSet docs = expectFullCollectionQuery(() -> runQuery(query, queryData));
    assertEquals(docSet(query.comparator()), docs);
  }

  @Test
  public void doesNotUseInitialResultsForLimitQueryWithDocumentRemoval() throws Exception {
    Query query = query("coll").filter(filter("matches", "==", true)).limit(1);

    // While the backend would never add DocA to the set of remote keys, this allows us to easily
    // simulate what would happen when a document no longer matches due to an out-of-band update.
    addDocument(NON_MATCHING_DOC_A);
    persistQueryMapping(NON_MATCHING_DOC_A.getKey());

    addDocument(MATCHING_DOC_B);

    QueryData queryData = queryData(query, /* hasLimboFreeSnapshot= */ true);
    DocumentSet docs = expectFullCollectionQuery(() -> runQuery(query, queryData));
    assertEquals(docSet(query.comparator(), MATCHING_DOC_B), docs);
  }

  @Test
  public void doesNotUseInitialResultsForLimitQueryWhenLastDocumentHasPendingWrite()
      throws Exception {
    Query query =
        query("coll")
            .filter(filter("matches", "==", true))
            .orderBy(orderBy("order", "desc"))
            .limit(1);

    // Add a query mapping for a document that matches, but that sorts below another document due to
    // a pending write.
    addDocument(PENDING_MATCHING_DOC_A);
    persistQueryMapping(PENDING_MATCHING_DOC_A.getKey());

    QueryData queryData = queryData(query, /* hasLimboFreeSnapshot= */ true);

    addDocument(MATCHING_DOC_B);

    DocumentSet docs = expectFullCollectionQuery(() -> runQuery(query, queryData));
    assertEquals(docSet(query.comparator(), MATCHING_DOC_B), docs);
  }

  @Test
  public void doesNotUseInitialResultsForLimitQueryWhenLastDocumentHasBeenUpdatedOutOfBand()
      throws Exception {
    Query query =
        query("coll")
            .filter(filter("matches", "==", true))
            .orderBy(orderBy("order", "desc"))
            .limit(1);

    // Add a query mapping for a document that matches, but that sorts below another document based
    // due to an update that the SDK received after the query's snapshot was persisted.
    addDocument(UDPATED_DOC_A);
    persistQueryMapping(UDPATED_DOC_A.getKey());

    QueryData queryData = queryData(query, /* hasLimboFreeSnapshot= */ true);

    addDocument(MATCHING_DOC_B);

    DocumentSet docs = expectFullCollectionQuery(() -> runQuery(query, queryData));
    assertEquals(docSet(query.comparator(), MATCHING_DOC_B), docs);
  }

  @Test
  public void limitQueriesUseInitialResultsIfLastDocumentInLimitIsUnchanged() throws Exception {
    Query query = query("coll").orderBy(orderBy("order")).limit(2);

    addDocument(doc("coll/a", 1, map("order", 1)));
    addDocument(doc("coll/b", 1, map("order", 3)));
    persistQueryMapping(key("coll/a"), key("coll/b"));
    QueryData queryData = queryData(query, /* hasLimboFreeSnapshot= */ true);

    // Update "coll/a" but make sure it still sorts before "coll/b"
    addDocument(doc("coll/a", 1, map("order", 2), Document.DocumentState.LOCAL_MUTATIONS));

    // Since the last document in the limit didn't change (and hence we know that all documents
    // written prior to query execution still sort after "coll/b"), we should use an Index-Free
    // query.
    DocumentSet docs = expectIndexFreeQuery(() -> runQuery(query, queryData));
    assertEquals(
        docSet(
            query.comparator(),
            doc("coll/a", 1, map("order", 2), Document.DocumentState.LOCAL_MUTATIONS),
            doc("coll/b", 1, map("order", 3))),
        docs);
  }

  private QueryData queryData(Query query, boolean hasLimboFreeSnapshot) {
    return new QueryData(
        query,
        TEST_TARGET_ID,
        1,
        QueryPurpose.LISTEN,
        version(10),
        hasLimboFreeSnapshot ? version(10) : SnapshotVersion.NONE,
        ByteString.EMPTY);
  }
}
