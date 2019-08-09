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
import static com.google.firebase.firestore.testutil.TestUtil.filter;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.orderBy;
import static com.google.firebase.firestore.testutil.TestUtil.query;
import static com.google.firebase.firestore.testutil.TestUtil.values;
import static com.google.firebase.firestore.testutil.TestUtil.version;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;

import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentCollections;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MaybeDocument;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class IndexFreeQueryEngineTest {

  private static final Document MATCHING_DOC_A =
      doc("coll/a", 1, map("matches", true, "order", 1), Document.DocumentState.SYNCED);
  private static final Document NON_MATCHING_DOC_A =
      doc("coll/a", 1, map("matches", false, "order", 1), Document.DocumentState.SYNCED);
  private static final Document PENDING_MATCHING_DOC_A =
      doc("coll/a", 1, map("matches", true, "order", 1), Document.DocumentState.LOCAL_MUTATIONS);
  private static final Document PENDING_UDPATED_DOC_A =
      doc("coll/a", 11, map("matches", true, "order", 1), Document.DocumentState.SYNCED);
  private static final Document MATCHING_DOC_B =
      doc("coll/b", 1, map("matches", true, "order", 2), Document.DocumentState.SYNCED);
  private static final Document NON_MATCHING_DOC_B =
      doc("coll/b", 1, map("matches", false, "order", 2), Document.DocumentState.SYNCED);
  private static final Document UPDATED_MATCHING_DOC_B =
      doc("coll/b", 11, map("matches", true, "order", 2), Document.DocumentState.SYNCED);

  private static final int TEST_TARGET_ID = 1;

  private final Map<DocumentKey, Document> existingQueryResults = new HashMap<>();
  private final Map<DocumentKey, Document> updatedQueryResults = new HashMap<>();

  private Boolean expectIndexFreeExecution;

  @Mock private LocalDocumentsView localDocumentsView;
  @Mock private QueryCache queryCache;
  private QueryEngine queryEngine;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    existingQueryResults.clear();
    updatedQueryResults.clear();

    queryEngine = new IndexFreeQueryEngine();
    queryEngine.setLocalDocumentsView(localDocumentsView);

    doAnswer(
            getMatchingKeysForTargetIdInvocation -> {
              int targetId = getMatchingKeysForTargetIdInvocation.getArgument(0);
              Assert.assertEquals(TEST_TARGET_ID, targetId);
              return existingQueryResults.keySet();
            })
        .when(queryCache)
        .getMatchingKeysForTargetId(anyInt());

    doAnswer(
            getDocumentsInvocation -> {
              Iterable<DocumentKey> keys = getDocumentsInvocation.getArgument(0);

              ImmutableSortedMap<DocumentKey, MaybeDocument> docs =
                  DocumentCollections.emptyMaybeDocumentMap();
              for (DocumentKey key : keys) {
                docs = docs.insert(key, existingQueryResults.get(key));
              }

              return docs;
            })
        .when(localDocumentsView)
        .getDocuments(any());

    doAnswer(
            getDocumentsMatchingQueryInvocation -> {
              Query query = getDocumentsMatchingQueryInvocation.getArgument(0);
              SnapshotVersion snapshotVersion = getDocumentsMatchingQueryInvocation.getArgument(1);

              Assert.assertNotNull(
                  "Did you call enforceIndexFreeExecution()/preventIndexFreeExecution()?",
                  expectIndexFreeExecution);
              assertEquals(expectIndexFreeExecution, !SnapshotVersion.NONE.equals(snapshotVersion));
              expectIndexFreeExecution = null; // Reset to make sure every query is annotated

              ImmutableSortedMap<DocumentKey, MaybeDocument> matchingDocs =
                  DocumentCollections.emptyMaybeDocumentMap();

              for (Document doc : updatedQueryResults.values()) {
                if (query.matches(doc)) {
                  matchingDocs = matchingDocs.insert(doc.getKey(), doc);
                }
              }

              return matchingDocs;
            })
        .when(localDocumentsView)
        .getDocumentsMatchingQuery(any(), any());
  }

  private void addExistingResult(Document doc) {
    existingQueryResults.put(doc.getKey(), doc);
  }

  private void addUpdatedResult(Document doc) {
    updatedQueryResults.put(doc.getKey(), doc);
  }

  @Test
  public void usesTargetMappingForInitialResults() {
    Query query = query("coll").filter(filter("matches", "==", true));
    QueryData queryData = queryData(query, true);

    addExistingResult(MATCHING_DOC_A);
    addExistingResult(MATCHING_DOC_B);

    ImmutableSortedMap<DocumentKey, Document> docs =
        runQuery(query, queryData, /* expectIndexFree= */ true);
    assertEquals(asList(MATCHING_DOC_A, MATCHING_DOC_B), values(docs));
  }

  private ImmutableSortedMap<DocumentKey, Document> runQuery(
      Query query, QueryData queryData, boolean expectIndexFree) {
    expectIndexFreeExecution = expectIndexFree;
    ImmutableSortedSet<DocumentKey> remoteKeys =
        new ImmutableSortedSet<>(
            new ArrayList<>(existingQueryResults.keySet()), DocumentKey.comparator());
    return queryEngine.getDocumentsMatchingQuery(query, queryData, remoteKeys);
  }

  @Test
  public void filtersNonMatchingInitialResults() {
    Query query = query("coll").filter(filter("matches", "==", true));
    QueryData queryData = queryData(query, /* hasLimboFreeSnapshot= */ true);

    addExistingResult(NON_MATCHING_DOC_A);
    addExistingResult(MATCHING_DOC_B);

    ImmutableSortedMap<DocumentKey, Document> docs =
        runQuery(query, queryData, /* expectIndexFree= */ true);
    assertEquals(asList(MATCHING_DOC_B), values(docs));
  }

  @Test
  public void includesChangesSinceInitialResults() {
    Query query = query("coll").filter(filter("matches", "==", true));
    QueryData originalQueryData = queryData(query, /* hasLimboFreeSnapshot= */ true);

    addExistingResult(MATCHING_DOC_A);
    addExistingResult(NON_MATCHING_DOC_B);

    ImmutableSortedMap<DocumentKey, Document> docs =
        runQuery(query, originalQueryData, /* expectIndexFree= */ true);
    assertEquals(asList(MATCHING_DOC_A), values(docs));

    addUpdatedResult(UPDATED_MATCHING_DOC_B);

    docs = runQuery(query, originalQueryData, /* expectIndexFree= */ true);
    assertEquals(asList(MATCHING_DOC_A, UPDATED_MATCHING_DOC_B), values(docs));
  }

  @Test
  public void doesNotUseInitialResultsWithoutLimboFreeSnapshotVersion() {
    Query query = query("coll").filter(filter("matches", "==", true));
    QueryData queryData = queryData(query, /* hasLimboFreeSnapshot= */ false);

    ImmutableSortedMap<DocumentKey, Document> docs =
        runQuery(query, queryData, /* expectIndexFree= */ false);
    assertEquals(asList(), values(docs));
  }

  @Test
  public void doesNotUseInitialResultsForUnfilteredCollectionQuery() {
    Query query = query("coll");
    QueryData queryData = queryData(query, /* hasLimboFreeSnapshot= */ true);

    ImmutableSortedMap<DocumentKey, Document> docs =
        runQuery(query, queryData, /* expectIndexFree= */ false);
    assertEquals(asList(), values(docs));
  }

  @Test
  public void doesNotUseInitialResultsForLimitQueryWithDocumentRemoval() {
    Query query =
        query("coll").filter(filter("matches", "==", true)).orderBy(orderBy("order")).limit(1);

    addExistingResult(NON_MATCHING_DOC_A);
    addUpdatedResult(MATCHING_DOC_B);

    QueryData queryData = queryData(query, /* hasLimboFreeSnapshot= */ true);

    ImmutableSortedMap<DocumentKey, Document> docs =
        runQuery(query, queryData, /* expectIndexFree= */ false);
    assertEquals(asList(MATCHING_DOC_B), values(docs));
  }

  @Test
  public void doesNotUseInitialResultsForLimitQueryWithPendingWrite() {
    Query query = query("coll").filter(filter("matches", "==", true)).limit(1);

    // Add a query mapping for a document that matches, but that sorts below another document due to
    // a pending write.
    addExistingResult(PENDING_MATCHING_DOC_A);
    addUpdatedResult(MATCHING_DOC_B);

    QueryData queryData = queryData(query, /* hasLimboFreeSnapshot= */ true);

    ImmutableSortedMap<DocumentKey, Document> docs =
        runQuery(query, queryData, /* expectIndexFree= */ false);
    assertEquals(asList(MATCHING_DOC_B), values(docs));
  }

  @Test
  public void doesNotUseInitialResultsForLimitQueryWithDocuemntThatHasBeenUpdatedOutOfBand() {
    Query query = query("coll").filter(filter("matches", "==", true)).limit(1);

    // Add a query mapping for a document that matches, but that sorts below another document based
    // on an update that the SDK received after the query's snapshot was persisted.
    addExistingResult(PENDING_UDPATED_DOC_A);
    addUpdatedResult(MATCHING_DOC_B);

    QueryData queryData = queryData(query, /* hasLimboFreeSnapshot= */ true);

    ImmutableSortedMap<DocumentKey, Document> docs =
        runQuery(query, queryData, /* expectIndexFree= */ false);
    assertEquals(asList(MATCHING_DOC_B), values(docs));
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
