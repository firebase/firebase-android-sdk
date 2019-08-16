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
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.orderBy;
import static com.google.firebase.firestore.testutil.TestUtil.query;
import static com.google.firebase.firestore.testutil.TestUtil.version;
import static org.junit.Assert.assertEquals;

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
  private static final Document NON_MATCHING_DOC_B =
      doc("coll/b", 1, map("matches", false, "order", 2), Document.DocumentState.SYNCED);
  private static final Document UPDATED_MATCHING_DOC_B =
      doc("coll/b", 11, map("matches", true, "order", 2), Document.DocumentState.SYNCED);

  private MemoryPersistence persistence;
  private MemoryRemoteDocumentCache remoteDocumentCache;
  private QueryCache queryCache;
  private QueryEngine queryEngine;

  private boolean expectIndexFreeExecution;

  @Before
  public void setUp() {
    expectIndexFreeExecution = false;

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

  /** Add a document to local cache and the remote key mapping. */
  private void addDocumentToRemoteResult(Document doc) {
    persistence.runTransaction(
        "addDocumentToRemoteResult",
        () -> {
          queryCache.addMatchingKeys(
              DocumentKey.emptyKeySet().insert(doc.getKey()), TEST_TARGET_ID);
          remoteDocumentCache.add(doc, doc.getVersion());
        });
  }

  /** Add a document to local cache but not the remote key mapping. */
  private void addDocumentToLocalResult(Document doc) {
    remoteDocumentCache.add(doc, doc.getVersion());
  }

  private DocumentSet runQuery(Query query, QueryData queryData, boolean expectIndexFree) {
    expectIndexFreeExecution = expectIndexFree;
    ImmutableSortedMap<DocumentKey, Document> docs =
        queryEngine.getDocumentsMatchingQuery(
            query, queryData, queryCache.getMatchingKeysForTargetId(TEST_TARGET_ID));
    View view =
        new View(query, new ImmutableSortedSet<>(Collections.emptyList(), DocumentKey::compareTo));
    View.DocumentChanges viewDocChanges = view.computeDocChanges(docs);
    return view.applyChanges(viewDocChanges).getSnapshot().getDocuments();
  }

  @Test
  public void usesTargetMappingForInitialView() {
    Query query = query("coll").filter(filter("matches", "==", true));
    QueryData queryData = queryData(query, /* hasLimboFreeSnapshot= */ true);

    addDocumentToRemoteResult(MATCHING_DOC_A);
    addDocumentToRemoteResult(MATCHING_DOC_B);

    DocumentSet docs = runQuery(query, queryData, /* expectIndexFree= */ true);
    assertEquals(docSet(query.comparator(), MATCHING_DOC_A, MATCHING_DOC_B), docs);
  }

  @Test
  public void filtersNonMatchingInitialResults() {
    Query query = query("coll").filter(filter("matches", "==", true));
    QueryData queryData = queryData(query, /* hasLimboFreeSnapshot= */ true);

    addDocumentToRemoteResult(MATCHING_DOC_A);
    addDocumentToRemoteResult(MATCHING_DOC_B);

    addDocumentToLocalResult(PENDING_NON_MATCHING_DOC_A);

    DocumentSet docs = runQuery(query, queryData, /* expectIndexFree= */ true);
    assertEquals(docSet(query.comparator(), MATCHING_DOC_B), docs);
  }

  @Test
  public void includesChangesSinceInitialResults() {
    Query query = query("coll").filter(filter("matches", "==", true));
    QueryData originalQueryData = queryData(query, /* hasLimboFreeSnapshot= */ true);

    addDocumentToRemoteResult(MATCHING_DOC_A);
    addDocumentToRemoteResult(NON_MATCHING_DOC_B);

    DocumentSet docs = runQuery(query, originalQueryData, /* expectIndexFree= */ true);
    assertEquals(docSet(query.comparator(), MATCHING_DOC_A), docs);

    addDocumentToLocalResult(UPDATED_MATCHING_DOC_B);

    docs = runQuery(query, originalQueryData, /* expectIndexFree= */ true);
    assertEquals(docSet(query.comparator(), MATCHING_DOC_A, UPDATED_MATCHING_DOC_B), docs);
  }

  @Test
  public void doesNotUseInitialResultsWithoutLimboFreeSnapshotVersion() {
    Query query = query("coll").filter(filter("matches", "==", true));
    QueryData queryData = queryData(query, /* hasLimboFreeSnapshot= */ false);

    DocumentSet docs = runQuery(query, queryData, /* expectIndexFree= */ false);
    assertEquals(docSet(query.comparator()), docs);
  }

  @Test
  public void doesNotUseInitialResultsForUnfilteredCollectionQuery() {
    Query query = query("coll");
    QueryData queryData = queryData(query, /* hasLimboFreeSnapshot= */ true);

    DocumentSet docs = runQuery(query, queryData, /* expectIndexFree= */ false);
    assertEquals(docSet(query.comparator()), docs);
  }

  @Test
  public void doesNotUseInitialResultsForLimitQueryWithDocumentRemoval() {
    Query query = query("coll").filter(filter("matches", "==", true)).limit(1);

    addDocumentToRemoteResult(NON_MATCHING_DOC_A);
    QueryData queryData = queryData(query, /* hasLimboFreeSnapshot= */ true);
    addDocumentToLocalResult(MATCHING_DOC_B);

    DocumentSet docs = runQuery(query, queryData, /* expectIndexFree= */ false);
    assertEquals(docSet(query.comparator(), MATCHING_DOC_B), docs);
  }

  @Test
  public void doesNotUseInitialResultsForLimitQueryWithPendingWrite() {
    Query query =
        query("coll")
            .filter(filter("matches", "==", true))
            .orderBy(orderBy("order", "desc"))
            .limit(1);

    // Add a query mapping for a document that matches, but that sorts below another document due to
    // a pending write.
    addDocumentToRemoteResult(PENDING_MATCHING_DOC_A);

    QueryData queryData = queryData(query, /* hasLimboFreeSnapshot= */ true);

    addDocumentToLocalResult(MATCHING_DOC_B);

    DocumentSet docs = runQuery(query, queryData, /* expectIndexFree= */ false);
    assertEquals(docSet(query.comparator(), MATCHING_DOC_B), docs);
  }

  @Test
  public void doesNotUseInitialResultsForLimitQueryWithDocumentThatHasBeenUpdatedOutOfBand() {
    Query query =
        query("coll")
            .filter(filter("matches", "==", true))
            .orderBy(orderBy("order", "desc"))
            .limit(1);

    // Add a query mapping for a document that matches, but that sorts below another document based
    // due to an update that the SDK received after the query's snapshot was persisted.
    addDocumentToRemoteResult(UDPATED_DOC_A);

    QueryData queryData = queryData(query, /* hasLimboFreeSnapshot= */ true);

    addDocumentToLocalResult(MATCHING_DOC_B);

    DocumentSet docs = runQuery(query, queryData, /* expectIndexFree= */ false);
    assertEquals(docSet(query.comparator(), MATCHING_DOC_B), docs);
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
