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

import static com.google.firebase.firestore.model.DocumentCollections.emptyMutableDocumentMap;
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
import com.google.firebase.Timestamp;
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.core.View;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.DocumentSet;
import com.google.firebase.firestore.model.FieldIndex.IndexOffset;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.ObjectValue;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.model.mutation.DeleteMutation;
import com.google.firebase.firestore.model.mutation.FieldMask;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.MutationBatch;
import com.google.firebase.firestore.model.mutation.PatchMutation;
import com.google.firebase.firestore.model.mutation.Precondition;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public abstract class QueryEngineTestCase {

  private static final int TEST_TARGET_ID = 1;

  private static final MutableDocument MATCHING_DOC_A =
      doc("coll/a", 1, map("matches", true, "order", 1));
  private static final MutableDocument NON_MATCHING_DOC_A =
      doc("coll/a", 1, map("matches", false, "order", 1));
  private static final MutableDocument UPDATED_DOC_A =
      doc("coll/a", 11, map("matches", true, "order", 1));
  private static final MutableDocument MATCHING_DOC_B =
      doc("coll/b", 1, map("matches", true, "order", 2));
  private static final MutableDocument UPDATED_MATCHING_DOC_B =
      doc("coll/b", 11, map("matches", true, "order", 2));
  private static final PatchMutation DOC_A_EMPTY_PATCH =
      new PatchMutation(key("coll/a"), new ObjectValue(), FieldMask.EMPTY, Precondition.NONE);

  private final SnapshotVersion LAST_LIMBO_FREE_SNAPSHOT = version(10);
  private final SnapshotVersion MISSING_LAST_LIMBO_FREE_SNAPSHOT = SnapshotVersion.NONE;

  private Persistence persistence;
  private RemoteDocumentCache remoteDocumentCache;
  private MutationQueue mutationQueue;
  private DocumentOverlayCache documentOverlayCache;
  private TargetCache targetCache;
  protected IndexManager indexManager;
  protected QueryEngine queryEngine;

  private @Nullable Boolean expectFullCollectionScan;

  @Before
  public void setUp() {
    expectFullCollectionScan = null;

    persistence = getPersistence();

    indexManager = persistence.getIndexManager(User.UNAUTHENTICATED);
    mutationQueue = persistence.getMutationQueue(User.UNAUTHENTICATED, indexManager);
    documentOverlayCache = persistence.getDocumentOverlayCache(User.UNAUTHENTICATED);
    remoteDocumentCache = persistence.getRemoteDocumentCache();
    targetCache = persistence.getTargetCache();
    queryEngine = new QueryEngine();

    indexManager.start();
    mutationQueue.start();

    remoteDocumentCache.setIndexManager(indexManager);

    LocalDocumentsView localDocuments =
        new LocalDocumentsView(
            remoteDocumentCache, mutationQueue, documentOverlayCache, indexManager) {
          @Override
          public ImmutableSortedMap<DocumentKey, Document> getDocumentsMatchingQuery(
              Query query, IndexOffset offset) {
            assertEquals(
                "Observed query execution mode did not match expectation",
                expectFullCollectionScan,
                IndexOffset.NONE.equals(offset));
            return super.getDocumentsMatchingQuery(query, offset);
          }
        };
    queryEngine.initialize(localDocuments, indexManager);
  }

  abstract Persistence getPersistence();

  /** Adds the provided documents to the query target mapping. */
  private void persistQueryMapping(DocumentKey... documentKeys) {
    persistence.runTransaction(
        "persistQueryMapping",
        () -> {
          ImmutableSortedSet<DocumentKey> remoteKeys = DocumentKey.emptyKeySet();
          for (DocumentKey documentKey : documentKeys) {
            remoteKeys = remoteKeys.insert(documentKey);
          }
          targetCache.addMatchingKeys(remoteKeys, TEST_TARGET_ID);
        });
  }

  /** Adds the provided documents to the remote document cache. */
  protected void addDocument(MutableDocument... docs) {
    persistence.runTransaction(
        "addDocument",
        () -> {
          for (MutableDocument doc : docs) {
            remoteDocumentCache.add(doc, doc.getVersion());
          }
        });
  }

  /**
   * Adds the provided documents to the remote document cache in a event of the given snapshot
   * version.
   */
  private void addDocumentWithEventVersion(SnapshotVersion eventVersion, MutableDocument... docs) {
    persistence.runTransaction(
        "addDocument",
        () -> {
          for (MutableDocument doc : docs) {
            remoteDocumentCache.add(doc, eventVersion);
          }
        });
  }

  /** Adds a mutation to the mutation queue. */
  protected void addMutation(Mutation mutation) {
    persistence.runTransaction(
        "addMutation",
        () -> {
          MutationBatch batch =
              mutationQueue.addMutationBatch(
                  Timestamp.now(), Collections.emptyList(), Collections.singletonList(mutation));
          Map<DocumentKey, Mutation> overlayMap =
              Collections.singletonMap(mutation.getKey(), mutation);
          documentOverlayCache.saveOverlays(batch.getBatchId(), overlayMap);
        });
  }

  protected <T> T expectOptimizedCollectionScan(Callable<T> c) throws Exception {
    try {
      expectFullCollectionScan = false;
      return c.call();
    } finally {
      expectFullCollectionScan = null;
    }
  }

  private <T> T expectFullCollectionScan(Callable<T> c) throws Exception {
    try {
      expectFullCollectionScan = true;
      return c.call();
    } finally {
      expectFullCollectionScan = null;
    }
  }

  private DocumentSet runQuery(Query query, SnapshotVersion lastLimboFreeSnapshotVersion) {
    Preconditions.checkNotNull(
        expectFullCollectionScan,
        "Encountered runQuery() call not wrapped in expectOptimizedCollectionQuery()/expectFullCollectionQuery()");
    ImmutableSortedMap<DocumentKey, Document> docs =
        queryEngine.getDocumentsMatchingQuery(
            query,
            lastLimboFreeSnapshotVersion,
            targetCache.getMatchingKeysForTargetId(TEST_TARGET_ID));
    View view =
        new View(query, new ImmutableSortedSet<>(Collections.emptyList(), DocumentKey::compareTo));
    View.DocumentChanges viewDocChanges = view.computeDocChanges(docs);
    return view.applyChanges(viewDocChanges).getSnapshot().getDocuments();
  }

  @Test
  public void usesTargetMappingForInitialView() throws Exception {
    Query query = query("coll").filter(filter("matches", "==", true));

    addDocument(MATCHING_DOC_A, MATCHING_DOC_B);
    persistQueryMapping(MATCHING_DOC_A.getKey(), MATCHING_DOC_B.getKey());

    DocumentSet docs =
        expectOptimizedCollectionScan(() -> runQuery(query, LAST_LIMBO_FREE_SNAPSHOT));
    assertEquals(docSet(query.comparator(), MATCHING_DOC_A, MATCHING_DOC_B), docs);
  }

  @Test
  public void filtersNonMatchingInitialResults() throws Exception {
    Query query = query("coll").filter(filter("matches", "==", true));

    addDocument(MATCHING_DOC_A, MATCHING_DOC_B);
    persistQueryMapping(MATCHING_DOC_A.getKey(), MATCHING_DOC_B.getKey());

    // Add a mutated document that is not yet part of query's set of remote keys.
    addDocumentWithEventVersion(version(1), NON_MATCHING_DOC_A);

    DocumentSet docs =
        expectOptimizedCollectionScan(() -> runQuery(query, LAST_LIMBO_FREE_SNAPSHOT));
    assertEquals(docSet(query.comparator(), MATCHING_DOC_B), docs);
  }

  @Test
  public void includesChangesSinceInitialResults() throws Exception {
    Query query = query("coll").filter(filter("matches", "==", true));

    addDocument(MATCHING_DOC_A, MATCHING_DOC_B);
    persistQueryMapping(MATCHING_DOC_A.getKey(), MATCHING_DOC_B.getKey());

    DocumentSet docs =
        expectOptimizedCollectionScan(() -> runQuery(query, LAST_LIMBO_FREE_SNAPSHOT));
    assertEquals(docSet(query.comparator(), MATCHING_DOC_A, MATCHING_DOC_B), docs);

    addDocument(UPDATED_MATCHING_DOC_B);

    docs = expectOptimizedCollectionScan(() -> runQuery(query, LAST_LIMBO_FREE_SNAPSHOT));
    assertEquals(docSet(query.comparator(), MATCHING_DOC_A, UPDATED_MATCHING_DOC_B), docs);
  }

  @Test
  public void doesNotUseInitialResultsWithoutLimboFreeSnapshotVersion() throws Exception {
    Query query = query("coll").filter(filter("matches", "==", true));
    DocumentSet docs =
        expectFullCollectionScan(() -> runQuery(query, MISSING_LAST_LIMBO_FREE_SNAPSHOT));
    assertEquals(docSet(query.comparator()), docs);
  }

  @Test
  public void doesNotUseInitialResultsForUnfilteredCollectionQuery() throws Exception {
    Query query = query("coll");
    DocumentSet docs = expectFullCollectionScan(() -> runQuery(query, LAST_LIMBO_FREE_SNAPSHOT));
    assertEquals(docSet(query.comparator()), docs);
  }

  @Test
  public void doesNotUseInitialResultsForLimitQueryWithDocumentRemoval() throws Exception {
    Query query = query("coll").filter(filter("matches", "==", true)).limitToFirst(1);

    // While the backend would never add DocA to the set of remote keys, this allows us to easily
    // simulate what would happen when a document no longer matches due to an out-of-band update.
    addDocument(NON_MATCHING_DOC_A);
    persistQueryMapping(NON_MATCHING_DOC_A.getKey());

    addDocument(MATCHING_DOC_B);

    DocumentSet docs = expectFullCollectionScan(() -> runQuery(query, LAST_LIMBO_FREE_SNAPSHOT));
    assertEquals(docSet(query.comparator(), MATCHING_DOC_B), docs);
  }

  @Test
  public void doesNotUseInitialResultsForLimitToLastQueryWithDocumentRemoval() throws Exception {
    Query query =
        query("coll")
            .filter(filter("matches", "==", true))
            .orderBy(orderBy("order", "desc"))
            .limitToLast(1);

    // While the backend would never add DocA to the set of remote keys, this allows us to easily
    // simulate what would happen when a document no longer matches due to an out-of-band update.
    addDocument(NON_MATCHING_DOC_A);
    persistQueryMapping(NON_MATCHING_DOC_A.getKey());

    addDocument(MATCHING_DOC_B);

    DocumentSet docs = expectFullCollectionScan(() -> runQuery(query, LAST_LIMBO_FREE_SNAPSHOT));
    assertEquals(docSet(query.comparator(), MATCHING_DOC_B), docs);
  }

  @Test
  public void doesNotUseInitialResultsForLimitQueryWhenLastDocumentHasPendingWrite()
      throws Exception {
    Query query =
        query("coll")
            .filter(filter("matches", "==", true))
            .orderBy(orderBy("order", "desc"))
            .limitToFirst(1);

    // Add a query mapping for a document that matches, but that sorts below another document due to
    // a pending write.
    addDocumentWithEventVersion(version(1), MATCHING_DOC_A);
    addMutation(DOC_A_EMPTY_PATCH);
    persistQueryMapping(MATCHING_DOC_A.getKey());

    addDocument(MATCHING_DOC_B);

    DocumentSet docs = expectFullCollectionScan(() -> runQuery(query, LAST_LIMBO_FREE_SNAPSHOT));
    assertEquals(docSet(query.comparator(), MATCHING_DOC_B), docs);
  }

  @Test
  public void doesNotUseInitialResultsForLimitToLastQueryWhenLastDocumentHasPendingWrite()
      throws Exception {
    Query query =
        query("coll")
            .filter(filter("matches", "==", true))
            .orderBy(orderBy("order", "asc"))
            .limitToLast(1);

    // Add a query mapping for a document that matches, but that sorts below another document due to
    // a pending write.
    addDocumentWithEventVersion(version(1), MATCHING_DOC_A);
    addMutation(DOC_A_EMPTY_PATCH);
    persistQueryMapping(MATCHING_DOC_A.getKey());

    addDocument(MATCHING_DOC_B);

    DocumentSet docs = expectFullCollectionScan(() -> runQuery(query, LAST_LIMBO_FREE_SNAPSHOT));
    assertEquals(docSet(query.comparator(), MATCHING_DOC_B), docs);
  }

  @Test
  public void doesNotUseInitialResultsForLimitQueryWhenLastDocumentHasBeenUpdatedOutOfBand()
      throws Exception {
    Query query =
        query("coll")
            .filter(filter("matches", "==", true))
            .orderBy(orderBy("order", "desc"))
            .limitToFirst(1);

    // Add a query mapping for a document that matches, but that sorts below another document based
    // due to an update that the SDK received after the query's snapshot was persisted.
    addDocument(UPDATED_DOC_A);
    persistQueryMapping(UPDATED_DOC_A.getKey());

    addDocument(MATCHING_DOC_B);

    DocumentSet docs = expectFullCollectionScan(() -> runQuery(query, LAST_LIMBO_FREE_SNAPSHOT));
    assertEquals(docSet(query.comparator(), MATCHING_DOC_B), docs);
  }

  @Test
  public void doesNotUseInitialResultsForLimitToLastQueryWhenFirstDocumentHasBeenUpdatedOutOfBand()
      throws Exception {
    Query query =
        query("coll")
            .filter(filter("matches", "==", true))
            .orderBy(orderBy("order", "asc"))
            .limitToLast(1);

    // Add a query mapping for a document that matches, but that sorts below another document based
    // due to an update that the SDK received after the query's snapshot was persisted.
    addDocument(UPDATED_DOC_A);
    persistQueryMapping(UPDATED_DOC_A.getKey());

    addDocument(MATCHING_DOC_B);

    DocumentSet docs = expectFullCollectionScan(() -> runQuery(query, LAST_LIMBO_FREE_SNAPSHOT));
    assertEquals(docSet(query.comparator(), MATCHING_DOC_B), docs);
  }

  @Test
  public void limitQueriesUseInitialResultsIfLastDocumentInLimitIsUnchanged() throws Exception {
    Query query = query("coll").orderBy(orderBy("order")).limitToFirst(2);

    addDocument(doc("coll/a", 1, map("order", 1)));
    addDocument(doc("coll/b", 1, map("order", 3)));
    persistQueryMapping(key("coll/a"), key("coll/b"));

    // Update "coll/a" but make sure it still sorts before "coll/b"
    addDocumentWithEventVersion(version(1), doc("coll/a", 1, map("order", 2)));
    addMutation(DOC_A_EMPTY_PATCH);

    // Since the last document in the limit didn't change (and hence we know that all documents
    // written prior to query execution still sort after "coll/b"), we should use an Index-Free
    // query.
    DocumentSet docs =
        expectOptimizedCollectionScan(() -> runQuery(query, LAST_LIMBO_FREE_SNAPSHOT));
    assertEquals(
        docSet(
            query.comparator(),
            doc("coll/a", 1, map("order", 2)).setHasLocalMutations(),
            doc("coll/b", 1, map("order", 3))),
        docs);
  }

  @Test
  public void doesNotIncludeDocumentsDeletedByMutation() throws Exception {
    Query query = query("coll");

    addDocument(MATCHING_DOC_A, MATCHING_DOC_B);
    persistQueryMapping(MATCHING_DOC_A.getKey(), MATCHING_DOC_B.getKey());

    // Add an unacknowledged mutation
    addMutation(new DeleteMutation(key("coll/b"), Precondition.NONE));

    ImmutableSortedMap<DocumentKey, Document> docs =
        expectFullCollectionScan(
            () ->
                queryEngine.getDocumentsMatchingQuery(
                    query,
                    LAST_LIMBO_FREE_SNAPSHOT,
                    targetCache.getMatchingKeysForTargetId(TEST_TARGET_ID)));
    assertEquals(emptyMutableDocumentMap().insert(MATCHING_DOC_A.getKey(), MATCHING_DOC_A), docs);
  }
}
