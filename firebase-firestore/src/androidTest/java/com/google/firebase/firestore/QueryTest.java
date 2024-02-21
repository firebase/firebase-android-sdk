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

package com.google.firebase.firestore;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.firebase.firestore.Filter.and;
import static com.google.firebase.firestore.Filter.arrayContains;
import static com.google.firebase.firestore.Filter.arrayContainsAny;
import static com.google.firebase.firestore.Filter.equalTo;
import static com.google.firebase.firestore.Filter.inArray;
import static com.google.firebase.firestore.Filter.or;
import static com.google.firebase.firestore.remote.TestingHooksUtil.captureExistenceFilterMismatches;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.checkOnlineAndOfflineResultsMatch;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.isRunningAgainstEmulator;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.nullList;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.querySnapshotToIds;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.querySnapshotToValues;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testCollection;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testCollectionWithDocs;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testFirestore;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static com.google.firebase.firestore.testutil.TestUtil.expectError;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import com.google.common.collect.Lists;
import com.google.firebase.firestore.Query.Direction;
import com.google.firebase.firestore.remote.TestingHooksUtil.ExistenceFilterBloomFilterInfo;
import com.google.firebase.firestore.remote.TestingHooksUtil.ExistenceFilterMismatchInfo;
import com.google.firebase.firestore.testutil.EventAccumulator;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class QueryTest {

  @After
  public void tearDown() {
    IntegrationTestUtil.tearDown();
  }

  @Test
  public void testLimitQueries() {
    CollectionReference collection =
        testCollectionWithDocs(
            map(
                "a", map("k", "a"),
                "b", map("k", "b"),
                "c", map("k", "c")));

    Query query = collection.limit(2);
    QuerySnapshot set = waitFor(query.get());
    List<Map<String, Object>> data = querySnapshotToValues(set);
    assertEquals(asList(map("k", "a"), map("k", "b")), data);
  }

  @Test
  public void testLimitQueriesUsingDescendingSortOrder() {
    CollectionReference collection =
        testCollectionWithDocs(
            map(
                "a", map("k", "a", "sort", 0),
                "b", map("k", "b", "sort", 1),
                "c", map("k", "c", "sort", 1),
                "d", map("k", "d", "sort", 2)));

    Query query = collection.limit(2).orderBy("sort", Direction.DESCENDING);
    QuerySnapshot set = waitFor(query.get());
    List<Map<String, Object>> data = querySnapshotToValues(set);
    assertEquals(asList(map("k", "d", "sort", 2L), map("k", "c", "sort", 1L)), data);
  }

  @Test
  public void testLimitToLastMustAlsoHaveExplicitOrderBy() {
    CollectionReference collection = testCollectionWithDocs(map());

    Query query = collection.limitToLast(2);
    expectError(
        () -> waitFor(query.get()),
        "limitToLast() queries require specifying at least one orderBy() clause");
  }

  // Two queries that mapped to the same target ID are referred to as
  // "mirror queries". An example for a mirror query is a limitToLast()
  // query and a limit() query that share the same backend Target ID.
  // Since limitToLast() queries are sent to the backend with a modified
  // orderBy() clause, they can map to the same target representation as
  // limit() query, even if both queries appear separate to the user.
  @Test
  public void testListenUnlistenRelistenSequenceOfMirrorQueries() {
    CollectionReference collection =
        testCollectionWithDocs(
            map(
                "a", map("k", "a", "sort", 0),
                "b", map("k", "b", "sort", 1),
                "c", map("k", "c", "sort", 1),
                "d", map("k", "d", "sort", 2)));

    // Setup `limit` query.
    Query limit = collection.limit(2).orderBy("sort", Direction.ASCENDING);
    EventAccumulator<QuerySnapshot> limitAccumulator = new EventAccumulator<>();
    ListenerRegistration limitRegistration = limit.addSnapshotListener(limitAccumulator.listener());

    // Setup mirroring `limitToLast` query.
    Query limitToLast = collection.limitToLast(2).orderBy("sort", Direction.DESCENDING);
    EventAccumulator<QuerySnapshot> limitToLastAccumulator = new EventAccumulator<>();
    ListenerRegistration limitToLastRegistration =
        limitToLast.addSnapshotListener(limitToLastAccumulator.listener());

    // Verify both query get expected result.
    List<Map<String, Object>> data = querySnapshotToValues(limitAccumulator.await());
    assertEquals(asList(map("k", "a", "sort", 0L), map("k", "b", "sort", 1L)), data);
    data = querySnapshotToValues(limitToLastAccumulator.await());
    assertEquals(asList(map("k", "b", "sort", 1L), map("k", "a", "sort", 0L)), data);

    // Unlisten then re-listen limit query.
    limitRegistration.remove();
    limit.addSnapshotListener(limitAccumulator.listener());

    // Verify `limit` query still works.
    data = querySnapshotToValues(limitAccumulator.await());
    assertEquals(asList(map("k", "a", "sort", 0L), map("k", "b", "sort", 1L)), data);

    // Add a document that would change the result set.
    waitFor(collection.add(map("k", "e", "sort", -1)));

    // Verify both query get expected result.
    data = querySnapshotToValues(limitAccumulator.await());
    assertEquals(asList(map("k", "e", "sort", -1L), map("k", "a", "sort", 0L)), data);
    data = querySnapshotToValues(limitToLastAccumulator.await());
    assertEquals(asList(map("k", "a", "sort", 0L), map("k", "e", "sort", -1L)), data);

    // Unlisten to limitToLast, update a doc, then relisten to limitToLast
    limitToLastRegistration.remove();
    waitFor(collection.document("a").update(map("k", "a", "sort", -2)));
    limitToLast.addSnapshotListener(limitToLastAccumulator.listener());

    // Verify both query get expected result.
    data = querySnapshotToValues(limitAccumulator.await());
    assertEquals(asList(map("k", "a", "sort", -2L), map("k", "e", "sort", -1L)), data);
    data = querySnapshotToValues(limitToLastAccumulator.await());
    assertEquals(asList(map("k", "e", "sort", -1L), map("k", "a", "sort", -2L)), data);
  }

  @Test
  public void testLimitToLastQueriesWithCursors() {
    CollectionReference collection =
        testCollectionWithDocs(
            map(
                "a", map("k", "a", "sort", 0),
                "b", map("k", "b", "sort", 1),
                "c", map("k", "c", "sort", 1),
                "d", map("k", "d", "sort", 2)));

    Query query = collection.limitToLast(3).orderBy("sort").endBefore(2);
    QuerySnapshot set = waitFor(query.get());
    List<Map<String, Object>> data = querySnapshotToValues(set);
    assertEquals(
        asList(map("k", "a", "sort", 0L), map("k", "b", "sort", 1L), map("k", "c", "sort", 1L)),
        data);

    query = collection.limitToLast(3).orderBy("sort").endAt(1);
    set = waitFor(query.get());
    data = querySnapshotToValues(set);
    assertEquals(
        asList(map("k", "a", "sort", 0L), map("k", "b", "sort", 1L), map("k", "c", "sort", 1L)),
        data);

    query = collection.limitToLast(3).orderBy("sort").startAt(2);
    set = waitFor(query.get());
    data = querySnapshotToValues(set);
    assertEquals(asList(map("k", "d", "sort", 2L)), data);

    query = collection.limitToLast(3).orderBy("sort").startAfter(0);
    set = waitFor(query.get());
    data = querySnapshotToValues(set);
    assertEquals(
        asList(map("k", "b", "sort", 1L), map("k", "c", "sort", 1L), map("k", "d", "sort", 2L)),
        data);

    query = collection.limitToLast(3).orderBy("sort").startAfter(-1);
    set = waitFor(query.get());
    data = querySnapshotToValues(set);
    assertEquals(
        asList(map("k", "b", "sort", 1L), map("k", "c", "sort", 1L), map("k", "d", "sort", 2L)),
        data);
  }

  @Test
  public void testKeyOrderIsDescendingForDescendingInequality() {
    CollectionReference collection =
        testCollectionWithDocs(
            map(
                "a", map("foo", 42),
                "b", map("foo", 42.0),
                "c", map("foo", 42),
                "d", map("foo", 21),
                "e", map("foo", 21.0),
                "f", map("foo", 66),
                "g", map("foo", 66.0)));

    Query query = collection.whereGreaterThan("foo", 21.0).orderBy("foo", Direction.DESCENDING);
    QuerySnapshot result = waitFor(query.get());
    assertEquals(asList("g", "f", "c", "b", "a"), querySnapshotToIds(result));
  }

  @Test
  public void testUnaryFilterQueries() {
    CollectionReference collection =
        testCollectionWithDocs(
            map(
                "a", map("null", null, "nan", Double.NaN),
                "b", map("null", null, "nan", 0),
                "c", map("null", false, "nan", Double.NaN)));
    QuerySnapshot results =
        waitFor(collection.whereEqualTo("null", null).whereEqualTo("nan", Double.NaN).get());
    assertEquals(1, results.size());
    DocumentSnapshot result = results.getDocuments().get(0);
    // Can't use assertEquals() since NaN != NaN.
    assertEquals(null, result.get("null"));
    assertTrue(((Double) result.get("nan")).isNaN());
  }

  @Test
  public void testFilterOnInfinity() {
    CollectionReference collection =
        testCollectionWithDocs(
            map(
                "a", map("inf", Double.POSITIVE_INFINITY),
                "b", map("inf", Double.NEGATIVE_INFINITY)));
    QuerySnapshot results = waitFor(collection.whereEqualTo("inf", Double.POSITIVE_INFINITY).get());
    assertEquals(1, results.size());
    assertEquals(asList(map("inf", Double.POSITIVE_INFINITY)), querySnapshotToValues(results));
  }

  @Test
  public void testWillNotGetMetadataOnlyUpdates() {
    CollectionReference collection = testCollection();
    waitFor(collection.document("a").set(map("v", "a")));
    waitFor(collection.document("b").set(map("v", "b")));

    List<QuerySnapshot> snapshots = new ArrayList<>();

    Semaphore testCounter = new Semaphore(0);
    ListenerRegistration listener =
        collection.addSnapshotListener(
            (snapshot, error) -> {
              assertNull(error);
              snapshots.add(snapshot);
              testCounter.release();
            });

    waitFor(testCounter);
    assertEquals(1, snapshots.size());
    assertEquals(asList(map("v", "a"), map("v", "b")), querySnapshotToValues(snapshots.get(0)));
    waitFor(collection.document("a").set(map("v", "a1")));

    waitFor(testCounter);
    assertEquals(2, snapshots.size());
    assertEquals(asList(map("v", "a1"), map("v", "b")), querySnapshotToValues(snapshots.get(1)));

    listener.remove();
  }

  @Test
  public void testCanListenForTheSameQueryWithDifferentOptions() {
    CollectionReference collection = testCollection();
    waitFor(collection.document("a").set(map("v", "a")));
    waitFor(collection.document("b").set(map("v", "b")));

    List<QuerySnapshot> snapshots = new ArrayList<>();
    List<QuerySnapshot> snapshotsFull = new ArrayList<>();

    Semaphore testCounter = new Semaphore(0);
    Semaphore testCounterFull = new Semaphore(0);
    ListenerRegistration listener =
        collection.addSnapshotListener(
            (snapshot, error) -> {
              assertNull(error);
              snapshots.add(snapshot);
              testCounter.release();
            });

    ListenerRegistration listenerFull =
        collection.addSnapshotListener(
            MetadataChanges.INCLUDE,
            (snapshot, error) -> {
              assertNull(error);
              snapshotsFull.add(snapshot);
              testCounterFull.release();
            });

    waitFor(testCounter);
    waitFor(testCounterFull, 2);
    assertEquals(1, snapshots.size());
    assertEquals(asList(map("v", "a"), map("v", "b")), querySnapshotToValues(snapshots.get(0)));
    assertEquals(2, snapshotsFull.size());
    assertEquals(asList(map("v", "a"), map("v", "b")), querySnapshotToValues(snapshotsFull.get(0)));
    assertEquals(asList(map("v", "a"), map("v", "b")), querySnapshotToValues(snapshotsFull.get(1)));
    assertTrue(snapshotsFull.get(0).getMetadata().isFromCache());
    assertFalse(snapshotsFull.get(1).getMetadata().isFromCache());

    waitFor(collection.document("a").set(map("v", "a1")));

    // Expect two events for the write, once from latency compensation and once from the
    // acknowledgement from the server.
    waitFor(testCounterFull, 2);
    // Only one event without options
    waitFor(testCounter);

    assertEquals(4, snapshotsFull.size());
    assertEquals(
        asList(map("v", "a1"), map("v", "b")), querySnapshotToValues(snapshotsFull.get(2)));
    assertEquals(
        asList(map("v", "a1"), map("v", "b")), querySnapshotToValues(snapshotsFull.get(3)));
    assertTrue(snapshotsFull.get(2).getMetadata().hasPendingWrites());
    assertFalse(snapshotsFull.get(3).getMetadata().hasPendingWrites());

    assertEquals(2, snapshots.size());
    assertEquals(asList(map("v", "a1"), map("v", "b")), querySnapshotToValues(snapshots.get(1)));

    waitFor(collection.document("b").set(map("v", "b1")));

    // Expect two events for the write, once from latency compensation and once from the
    // acknowledgement from the server.
    waitFor(testCounterFull, 2);
    // Only one event without options
    waitFor(testCounter);

    assertEquals(6, snapshotsFull.size());
    assertEquals(
        asList(map("v", "a1"), map("v", "b1")), querySnapshotToValues(snapshotsFull.get(4)));
    assertEquals(
        asList(map("v", "a1"), map("v", "b1")), querySnapshotToValues(snapshotsFull.get(5)));
    assertTrue(snapshotsFull.get(4).getMetadata().hasPendingWrites());
    assertFalse(snapshotsFull.get(5).getMetadata().hasPendingWrites());

    assertEquals(3, snapshots.size());
    assertEquals(asList(map("v", "a1"), map("v", "b1")), querySnapshotToValues(snapshots.get(2)));

    listener.remove();
    listenerFull.remove();
  }

  @Test
  public void testCanListenForQueryMetadataChanges() {
    Map<String, Map<String, Object>> testDocs =
        map(
            "1", map("sort", 1.0, "filter", true, "key", "1"),
            "2", map("sort", 2.0, "filter", true, "key", "2"),
            "3", map("sort", 2.0, "filter", true, "key", "3"),
            "4", map("sort", 3.0, "filter", false, "key", "4"));
    CollectionReference collection = testCollectionWithDocs(testDocs);
    List<QuerySnapshot> snapshots = new ArrayList<>();

    Semaphore testCounter = new Semaphore(0);
    Query query1 = collection.whereLessThan("key", "4");
    ListenerRegistration listener1 =
        query1.addSnapshotListener(
            (snapshot, error) -> {
              assertNull(error);
              snapshots.add(snapshot);
              testCounter.release();
            });

    waitFor(testCounter);
    assertEquals(1, snapshots.size());
    assertEquals(
        asList(testDocs.get("1"), testDocs.get("2"), testDocs.get("3")),
        querySnapshotToValues(snapshots.get(0)));

    Query query2 = collection.whereEqualTo("filter", true);
    ListenerRegistration listener2 =
        query2.addSnapshotListener(
            MetadataChanges.INCLUDE,
            (snapshot, error) -> {
              assertNull(error);
              snapshots.add(snapshot);
              testCounter.release();
            });

    waitFor(testCounter, 2);
    assertEquals(3, snapshots.size());
    assertEquals(
        asList(testDocs.get("1"), testDocs.get("2"), testDocs.get("3")),
        querySnapshotToValues(snapshots.get(1)));
    assertEquals(
        asList(testDocs.get("1"), testDocs.get("2"), testDocs.get("3")),
        querySnapshotToValues(snapshots.get(2)));
    assertTrue(snapshots.get(1).getMetadata().isFromCache());
    assertFalse(snapshots.get(2).getMetadata().isFromCache());

    listener1.remove();
    listener2.remove();
  }

  @Test
  public void testCanExplicitlySortByDocumentId() {
    Map<String, Map<String, Object>> testDocs =
        map(
            "a", map("key", "a"),
            "b", map("key", "b"),
            "c", map("key", "c"));
    CollectionReference collection = testCollectionWithDocs(testDocs);
    // Ideally this would be descending to validate it's different than
    // the default, but that requires an extra index
    QuerySnapshot docs = waitFor(collection.orderBy(FieldPath.documentId()).get());
    assertEquals(
        asList(testDocs.get("a"), testDocs.get("b"), testDocs.get("c")),
        querySnapshotToValues(docs));
  }

  @Test
  public void testCanQueryByDocumentId() {
    Map<String, Map<String, Object>> testDocs =
        map(
            "aa", map("key", "aa"),
            "ab", map("key", "ab"),
            "ba", map("key", "ba"),
            "bb", map("key", "bb"));
    CollectionReference collection = testCollectionWithDocs(testDocs);
    QuerySnapshot docs = waitFor(collection.whereEqualTo(FieldPath.documentId(), "ab").get());
    assertEquals(singletonList(testDocs.get("ab")), querySnapshotToValues(docs));

    docs =
        waitFor(
            collection
                .whereGreaterThan(FieldPath.documentId(), "aa")
                .whereLessThanOrEqualTo(FieldPath.documentId(), "ba")
                .get());
    assertEquals(asList(testDocs.get("ab"), testDocs.get("ba")), querySnapshotToValues(docs));
  }

  @Test
  public void testCanQueryByDocumentIdUsingRefs() {
    Map<String, Map<String, Object>> testDocs =
        map(
            "aa", map("key", "aa"),
            "ab", map("key", "ab"),
            "ba", map("key", "ba"),
            "bb", map("key", "bb"));
    CollectionReference collection = testCollectionWithDocs(testDocs);
    QuerySnapshot docs =
        waitFor(collection.whereEqualTo(FieldPath.documentId(), collection.document("ab")).get());
    assertEquals(singletonList(testDocs.get("ab")), querySnapshotToValues(docs));

    docs =
        waitFor(
            collection
                .whereGreaterThan(FieldPath.documentId(), collection.document("aa"))
                .whereLessThanOrEqualTo(FieldPath.documentId(), collection.document("ba"))
                .get());
    assertEquals(asList(testDocs.get("ab"), testDocs.get("ba")), querySnapshotToValues(docs));
  }

  @Test
  public void testCanQueryWithAndWithoutDocumentKey() {
    CollectionReference collection = testCollection();
    collection.add(map());
    Task<QuerySnapshot> query1 =
        collection.orderBy(FieldPath.documentId(), Direction.ASCENDING).get();
    Task<QuerySnapshot> query2 = collection.get();

    waitFor(query1);
    waitFor(query2);

    assertEquals(
        querySnapshotToValues(query1.getResult()), querySnapshotToValues(query2.getResult()));
  }

  @Test
  public void watchSurvivesNetworkDisconnect() {
    CollectionReference collectionReference = testCollection();
    FirebaseFirestore firestore = collectionReference.getFirestore();

    Semaphore receivedDocument = new Semaphore(0);

    collectionReference.addSnapshotListener(
        MetadataChanges.INCLUDE,
        (snapshot, error) -> {
          if (!snapshot.isEmpty() && !snapshot.getMetadata().isFromCache()) {
            receivedDocument.release();
          }
        });

    waitFor(firestore.disableNetwork());
    collectionReference.add(map("foo", FieldValue.serverTimestamp()));
    waitFor(firestore.enableNetwork());

    waitFor(receivedDocument);
  }

  @Test
  public void testQueriesFireFromCacheWhenOffline() {
    Map<String, Map<String, Object>> testDocs = map("a", map("foo", 1L));
    CollectionReference collection = testCollectionWithDocs(testDocs);
    EventAccumulator<QuerySnapshot> accum = new EventAccumulator<>();
    ListenerRegistration listener =
        collection.addSnapshotListener(MetadataChanges.INCLUDE, accum.listener());

    // initial event
    QuerySnapshot querySnapshot = accum.await();
    assertEquals(singletonList(testDocs.get("a")), querySnapshotToValues(querySnapshot));
    assertFalse(querySnapshot.getMetadata().isFromCache());

    // offline event with fromCache=true
    waitFor(collection.firestore.getClient().disableNetwork());
    querySnapshot = accum.await();
    assertTrue(querySnapshot.getMetadata().isFromCache());

    // back online event with fromCache=false
    waitFor(collection.firestore.getClient().enableNetwork());
    querySnapshot = accum.await();
    assertFalse(querySnapshot.getMetadata().isFromCache());

    listener.remove();
  }

  @Test
  public void testQueriesCanRaiseInitialSnapshotFromCachedEmptyResults() {
    CollectionReference collectionReference = testCollection();

    // Populate the cache with empty query result.
    QuerySnapshot querySnapshotA = waitFor(collectionReference.get());
    assertFalse(querySnapshotA.getMetadata().isFromCache());
    assertEquals(asList(), querySnapshotToValues(querySnapshotA));

    // Add a snapshot listener whose first event should be raised from cache.
    EventAccumulator<QuerySnapshot> accum = new EventAccumulator<>();
    ListenerRegistration listenerRegistration =
        collectionReference.addSnapshotListener(accum.listener());
    QuerySnapshot querySnapshotB = accum.await();
    assertTrue(querySnapshotB.getMetadata().isFromCache());
    assertEquals(asList(), querySnapshotToValues(querySnapshotB));

    listenerRegistration.remove();
  }

  @Test
  public void testQueriesCanRaiseInitialSnapshotFromEmptyDueToDeleteCachedResults() {
    Map<String, Map<String, Object>> testDocs = map("a", map("foo", 1L));
    CollectionReference collectionReference = testCollectionWithDocs(testDocs);
    // Populate the cache with single document.
    QuerySnapshot querySnapshotA = waitFor(collectionReference.get());
    assertFalse(querySnapshotA.getMetadata().isFromCache());
    assertEquals(asList(testDocs.get("a")), querySnapshotToValues(querySnapshotA));

    // delete the document, make cached result empty.
    DocumentReference docRef = collectionReference.document("a");
    waitFor(docRef.delete());

    // Add a snapshot listener whose first event should be raised from cache.
    EventAccumulator<QuerySnapshot> accum = new EventAccumulator<>();
    ListenerRegistration listenerRegistration =
        collectionReference.addSnapshotListener(accum.listener());
    QuerySnapshot querySnapshotB = accum.await();
    assertTrue(querySnapshotB.getMetadata().isFromCache());
    assertEquals(asList(), querySnapshotToValues(querySnapshotB));

    listenerRegistration.remove();
  }

  @Test
  public void testQueriesCanUseNotEqualFilters() {
    // These documents are ordered by value in "zip" since the notEquals filter is an inequality,
    // which results in documents being sorted by value.
    Map<String, Object> docA = map("zip", Double.NaN);
    Map<String, Object> docB = map("zip", 91102L);
    Map<String, Object> docC = map("zip", 98101L);
    Map<String, Object> docD = map("zip", "98101");
    Map<String, Object> docE = map("zip", asList(98101L));
    Map<String, Object> docF = map("zip", asList(98101L, 98102L));
    Map<String, Object> docG = map("zip", asList("98101", map("zip", 98101L)));
    Map<String, Object> docH = map("zip", map("code", 500L));
    Map<String, Object> docI = map("code", 500L);
    Map<String, Object> docJ = map("zip", null);

    Map<String, Map<String, Object>> allDocs =
        map(
            "a", docA, "b", docB, "c", docC, "d", docD, "e", docE, "f", docF, "g", docG, "h", docH,
            "i", docI, "j", docJ);
    CollectionReference collection = testCollectionWithDocs(allDocs);

    // Search for zips not matching 98101.
    Map<String, Map<String, Object>> expectedDocsMap = new LinkedHashMap<>(allDocs);
    expectedDocsMap.remove("c");
    expectedDocsMap.remove("i");
    expectedDocsMap.remove("j");

    QuerySnapshot snapshot = waitFor(collection.whereNotEqualTo("zip", 98101L).get());
    assertEquals(Lists.newArrayList(expectedDocsMap.values()), querySnapshotToValues(snapshot));

    // With objects.
    expectedDocsMap = new LinkedHashMap<>(allDocs);
    expectedDocsMap.remove("h");
    expectedDocsMap.remove("i");
    expectedDocsMap.remove("j");
    snapshot = waitFor(collection.whereNotEqualTo("zip", map("code", 500)).get());
    assertEquals(Lists.newArrayList(expectedDocsMap.values()), querySnapshotToValues(snapshot));

    // With Null.
    expectedDocsMap = new LinkedHashMap<>(allDocs);
    expectedDocsMap.remove("i");
    expectedDocsMap.remove("j");
    snapshot = waitFor(collection.whereNotEqualTo("zip", null).get());
    assertEquals(Lists.newArrayList(expectedDocsMap.values()), querySnapshotToValues(snapshot));

    // With NaN.
    expectedDocsMap = new LinkedHashMap<>(allDocs);
    expectedDocsMap.remove("a");
    expectedDocsMap.remove("i");
    expectedDocsMap.remove("j");
    snapshot = waitFor(collection.whereNotEqualTo("zip", Double.NaN).get());
    assertEquals(Lists.newArrayList(expectedDocsMap.values()), querySnapshotToValues(snapshot));
  }

  @Test
  public void testQueriesCanUseNotEqualFiltersWithDocIds() {
    Map<String, String> docA = map("key", "aa");
    Map<String, String> docB = map("key", "ab");
    Map<String, String> docC = map("key", "ba");
    Map<String, String> docD = map("key", "bb");
    Map<String, Map<String, Object>> testDocs =
        map(
            "aa", docA,
            "ab", docB,
            "ba", docC,
            "bb", docD);
    CollectionReference collection = testCollectionWithDocs(testDocs);
    QuerySnapshot docs = waitFor(collection.whereNotEqualTo(FieldPath.documentId(), "aa").get());
    assertEquals(asList(docB, docC, docD), querySnapshotToValues(docs));
  }

  @Test
  public void testQueriesCanUseArrayContainsFilters() {
    Map<String, Object> docA = map("array", asList(42L));
    Map<String, Object> docB = map("array", asList("a", 42L, "c"));
    Map<String, Object> docC = map("array", asList(41.999, "42", map("a", asList(42))));
    Map<String, Object> docD = map("array", asList(42L), "array2", asList("bingo"));
    Map<String, Object> docE = map("array", nullList());
    Map<String, Object> docF = map("array", asList(Double.NaN));
    CollectionReference collection =
        testCollectionWithDocs(
            map("a", docA, "b", docB, "c", docC, "d", docD, "e", docE, "f", docF));

    // Search for "array" to contain 42
    QuerySnapshot snapshot = waitFor(collection.whereArrayContains("array", 42L).get());
    assertEquals(asList(docA, docB, docD), querySnapshotToValues(snapshot));

    // Note: whereArrayContains() requires a non-null value parameter, so no null test is needed.
    // With NaN.
    snapshot = waitFor(collection.whereArrayContains("array", Double.NaN).get());
    assertEquals(new ArrayList<>(), querySnapshotToValues(snapshot));
  }

  @Test
  public void testQueriesCanUseInFilters() {
    Map<String, Object> docA = map("zip", 98101L);
    Map<String, Object> docB = map("zip", 91102L);
    Map<String, Object> docC = map("zip", 98103L);
    Map<String, Object> docD = map("zip", asList(98101L));
    Map<String, Object> docE = map("zip", asList("98101", map("zip", 98101L)));
    Map<String, Object> docF = map("zip", map("code", 500L));
    Map<String, Object> docG = map("zip", asList(98101L, 98102L));
    Map<String, Object> docH = map("zip", null);
    Map<String, Object> docI = map("zip", Double.NaN);

    CollectionReference collection =
        testCollectionWithDocs(
            map(
                "a", docA, "b", docB, "c", docC, "d", docD, "e", docE, "f", docF, "g", docG, "h",
                docH, "i", docI));

    // Search for zips matching 98101, 98103, or [98101, 98102].
    QuerySnapshot snapshot =
        waitFor(collection.whereIn("zip", asList(98101L, 98103L, asList(98101L, 98102L))).get());
    assertEquals(asList(docA, docC, docG), querySnapshotToValues(snapshot));

    // With objects.
    snapshot = waitFor(collection.whereIn("zip", asList(map("code", 500L))).get());
    assertEquals(asList(docF), querySnapshotToValues(snapshot));

    // With null.
    snapshot = waitFor(collection.whereIn("zip", nullList()).get());
    assertEquals(new ArrayList<>(), querySnapshotToValues(snapshot));

    // With null and a value.
    List<Object> inputList = nullList();
    inputList.add(98101L);
    snapshot = waitFor(collection.whereIn("zip", inputList).get());
    assertEquals(asList(docA), querySnapshotToValues(snapshot));

    // With NaN.
    snapshot = waitFor(collection.whereIn("zip", asList(Double.NaN)).get());
    assertEquals(new ArrayList<>(), querySnapshotToValues(snapshot));

    // With NaN and a value.
    snapshot = waitFor(collection.whereIn("zip", asList(Double.NaN, 98101L)).get());
    assertEquals(asList(docA), querySnapshotToValues(snapshot));
  }

  @Test
  public void testQueriesCanUseInFiltersWithDocIds() {
    Map<String, String> docA = map("key", "aa");
    Map<String, String> docB = map("key", "ab");
    Map<String, String> docC = map("key", "ba");
    Map<String, String> docD = map("key", "bb");
    Map<String, Map<String, Object>> testDocs =
        map(
            "aa", docA,
            "ab", docB,
            "ba", docC,
            "bb", docD);
    CollectionReference collection = testCollectionWithDocs(testDocs);
    QuerySnapshot docs =
        waitFor(collection.whereIn(FieldPath.documentId(), asList("aa", "ab")).get());
    assertEquals(asList(docA, docB), querySnapshotToValues(docs));
  }

  @Test
  public void testQueriesCanUseNotInFilters() {
    // These documents are ordered by value in "zip" since the notEquals filter is an inequality,
    // which results in documents being sorted by value.
    Map<String, Object> docA = map("zip", Double.NaN);
    Map<String, Object> docB = map("zip", 91102L);
    Map<String, Object> docC = map("zip", 98101L);
    Map<String, Object> docD = map("zip", 98103L);
    Map<String, Object> docE = map("zip", asList(98101L));
    Map<String, Object> docF = map("zip", asList(98101L, 98102L));
    Map<String, Object> docG = map("zip", asList("98101", map("zip", 98101L)));
    Map<String, Object> docH = map("zip", map("code", 500L));
    Map<String, Object> docI = map("code", 500L);
    Map<String, Object> docJ = map("zip", null);

    Map<String, Map<String, Object>> allDocs =
        map(
            "a", docA, "b", docB, "c", docC, "d", docD, "e", docE, "f", docF, "g", docG, "h", docH,
            "i", docI, "j", docJ);
    CollectionReference collection = testCollectionWithDocs(allDocs);

    // Search for zips not matching 98101, 98103, or [98101, 98102].
    Map<String, Map<String, Object>> expectedDocsMap = new LinkedHashMap<>(allDocs);
    expectedDocsMap.remove("c");
    expectedDocsMap.remove("d");
    expectedDocsMap.remove("f");
    expectedDocsMap.remove("i");
    expectedDocsMap.remove("j");

    QuerySnapshot snapshot =
        waitFor(collection.whereNotIn("zip", asList(98101L, 98103L, asList(98101L, 98102L))).get());
    assertEquals(Lists.newArrayList(expectedDocsMap.values()), querySnapshotToValues(snapshot));

    // With objects.
    expectedDocsMap = new LinkedHashMap<>(allDocs);
    expectedDocsMap.remove("h");
    expectedDocsMap.remove("i");
    expectedDocsMap.remove("j");
    snapshot = waitFor(collection.whereNotIn("zip", asList(map("code", 500L))).get());
    assertEquals(Lists.newArrayList(expectedDocsMap.values()), querySnapshotToValues(snapshot));

    // With Null.
    snapshot = waitFor(collection.whereNotIn("zip", nullList()).get());
    assertEquals(new ArrayList<>(), querySnapshotToValues(snapshot));

    // With NaN.
    expectedDocsMap = new LinkedHashMap<>(allDocs);
    expectedDocsMap.remove("a");
    expectedDocsMap.remove("i");
    expectedDocsMap.remove("j");
    snapshot = waitFor(collection.whereNotIn("zip", asList(Double.NaN)).get());
    assertEquals(Lists.newArrayList(expectedDocsMap.values()), querySnapshotToValues(snapshot));

    // With NaN and a number.
    expectedDocsMap = new LinkedHashMap<>(allDocs);
    expectedDocsMap.remove("a");
    expectedDocsMap.remove("c");
    expectedDocsMap.remove("i");
    expectedDocsMap.remove("j");
    snapshot = waitFor(collection.whereNotIn("zip", asList(Float.NaN, 98101L)).get());
    assertEquals(Lists.newArrayList(expectedDocsMap.values()), querySnapshotToValues(snapshot));
  }

  @Test
  public void testQueriesCanUseNotInFiltersWithDocIds() {
    Map<String, String> docA = map("key", "aa");
    Map<String, String> docB = map("key", "ab");
    Map<String, String> docC = map("key", "ba");
    Map<String, String> docD = map("key", "bb");
    Map<String, Map<String, Object>> testDocs =
        map(
            "aa", docA,
            "ab", docB,
            "ba", docC,
            "bb", docD);
    CollectionReference collection = testCollectionWithDocs(testDocs);
    QuerySnapshot docs =
        waitFor(collection.whereNotIn(FieldPath.documentId(), asList("aa", "ab")).get());
    assertEquals(asList(docC, docD), querySnapshotToValues(docs));
  }

  @Test
  public void testQueriesCanUseArrayContainsAnyFilters() {
    Map<String, Object> docA = map("array", asList(42L));
    Map<String, Object> docB = map("array", asList("a", 42L, "c"));
    Map<String, Object> docC = map("array", asList(41.999, "42", map("a", asList(42))));
    Map<String, Object> docD = map("array", asList(42L), "array2", asList("bingo"));
    Map<String, Object> docE = map("array", asList(43L));
    Map<String, Object> docF = map("array", asList(map("a", 42L)));
    Map<String, Object> docG = map("array", 42L);
    Map<String, Object> docH = map("array", nullList());
    Map<String, Object> docI = map("array", asList(Double.NaN));

    CollectionReference collection =
        testCollectionWithDocs(
            map(
                "a", docA, "b", docB, "c", docC, "d", docD, "e", docE, "f", docF, "g", docG, "h",
                docH, "i", docI));

    // Search for "array" to contain [42, 43].
    QuerySnapshot snapshot =
        waitFor(collection.whereArrayContainsAny("array", asList(42L, 43L)).get());
    assertEquals(asList(docA, docB, docD, docE), querySnapshotToValues(snapshot));

    // With objects.
    snapshot = waitFor(collection.whereArrayContainsAny("array", asList(map("a", 42L))).get());
    assertEquals(asList(docF), querySnapshotToValues(snapshot));

    // With null.
    snapshot = waitFor(collection.whereArrayContainsAny("array", nullList()).get());
    assertEquals(new ArrayList<>(), querySnapshotToValues(snapshot));

    // With null and a value.
    List<Object> inputList = nullList();
    inputList.add(43L);
    snapshot = waitFor(collection.whereArrayContainsAny("array", inputList).get());
    assertEquals(asList(docE), querySnapshotToValues(snapshot));

    // With NaN.
    snapshot = waitFor(collection.whereArrayContainsAny("array", asList(Double.NaN)).get());
    assertEquals(new ArrayList<>(), querySnapshotToValues(snapshot));

    // With NaN and a value.
    snapshot = waitFor(collection.whereArrayContainsAny("array", asList(Double.NaN, 43L)).get());
    assertEquals(asList(docE), querySnapshotToValues(snapshot));
  }

  @Test
  public void testCollectionGroupQueries() {
    FirebaseFirestore db = testFirestore();
    // Use .document() to get a random collection group name to use but ensure it starts with 'b'
    // for predictable ordering.
    String collectionGroup = "b" + db.collection("foo").document().getId();

    String[] docPaths =
        new String[] {
          "abc/123/${collectionGroup}/cg-doc1",
          "abc/123/${collectionGroup}/cg-doc2",
          "${collectionGroup}/cg-doc3",
          "${collectionGroup}/cg-doc4",
          "def/456/${collectionGroup}/cg-doc5",
          "${collectionGroup}/virtual-doc/nested-coll/not-cg-doc",
          "x${collectionGroup}/not-cg-doc",
          "${collectionGroup}x/not-cg-doc",
          "abc/123/${collectionGroup}x/not-cg-doc",
          "abc/123/x${collectionGroup}/not-cg-doc",
          "abc/${collectionGroup}"
        };
    WriteBatch batch = db.batch();
    for (String path : docPaths) {
      batch.set(db.document(path.replace("${collectionGroup}", collectionGroup)), map("x", 1));
    }
    waitFor(batch.commit());

    QuerySnapshot querySnapshot = waitFor(db.collectionGroup(collectionGroup).get());
    assertEquals(
        asList("cg-doc1", "cg-doc2", "cg-doc3", "cg-doc4", "cg-doc5"),
        querySnapshotToIds(querySnapshot));
  }

  @Test
  public void testCollectionGroupQueriesWithStartAtEndAtWithArbitraryDocumentIds() {
    FirebaseFirestore db = testFirestore();
    // Use .document() to get a random collection group name to use but ensure it starts with 'b'
    // for predictable ordering.
    String collectionGroup = "b" + db.collection("foo").document().getId();

    String[] docPaths =
        new String[] {
          "a/a/${collectionGroup}/cg-doc1",
          "a/b/a/b/${collectionGroup}/cg-doc2",
          "a/b/${collectionGroup}/cg-doc3",
          "a/b/c/d/${collectionGroup}/cg-doc4",
          "a/c/${collectionGroup}/cg-doc5",
          "${collectionGroup}/cg-doc6",
          "a/b/nope/nope"
        };
    WriteBatch batch = db.batch();
    for (String path : docPaths) {
      batch.set(db.document(path.replace("${collectionGroup}", collectionGroup)), map("x", 1));
    }
    waitFor(batch.commit());

    QuerySnapshot querySnapshot =
        waitFor(
            db.collectionGroup(collectionGroup)
                .orderBy(FieldPath.documentId())
                .startAt("a/b")
                .endAt("a/b0")
                .get());
    assertEquals(asList("cg-doc2", "cg-doc3", "cg-doc4"), querySnapshotToIds(querySnapshot));

    querySnapshot =
        waitFor(
            db.collectionGroup(collectionGroup)
                .orderBy(FieldPath.documentId())
                .startAfter("a/b")
                .endBefore("a/b/" + collectionGroup + "/cg-doc3")
                .get());
    assertEquals(asList("cg-doc2"), querySnapshotToIds(querySnapshot));
  }

  @Test
  public void testCollectionGroupQueriesWithWhereFiltersOnArbitraryDocumentIds() {
    FirebaseFirestore db = testFirestore();
    // Use .document() to get a random collection group name to use but ensure it starts with 'b'
    // for predictable ordering.
    String collectionGroup = "b" + db.collection("foo").document().getId();

    String[] docPaths =
        new String[] {
          "a/a/${collectionGroup}/cg-doc1",
          "a/b/a/b/${collectionGroup}/cg-doc2",
          "a/b/${collectionGroup}/cg-doc3",
          "a/b/c/d/${collectionGroup}/cg-doc4",
          "a/c/${collectionGroup}/cg-doc5",
          "${collectionGroup}/cg-doc6",
          "a/b/nope/nope"
        };
    WriteBatch batch = db.batch();
    for (String path : docPaths) {
      batch.set(db.document(path.replace("${collectionGroup}", collectionGroup)), map("x", 1));
    }
    waitFor(batch.commit());

    QuerySnapshot querySnapshot =
        waitFor(
            db.collectionGroup(collectionGroup)
                .whereGreaterThanOrEqualTo(FieldPath.documentId(), "a/b")
                .whereLessThanOrEqualTo(FieldPath.documentId(), "a/b0")
                .get());
    assertEquals(asList("cg-doc2", "cg-doc3", "cg-doc4"), querySnapshotToIds(querySnapshot));

    querySnapshot =
        waitFor(
            db.collectionGroup(collectionGroup)
                .whereGreaterThan(FieldPath.documentId(), "a/b")
                .whereLessThan(FieldPath.documentId(), "a/b/" + collectionGroup + "/cg-doc3")
                .get());
    assertEquals(asList("cg-doc2"), querySnapshotToIds(querySnapshot));
  }

  // See: https://github.com/firebase/firebase-android-sdk/issues/3528
  // TODO(Overlay): These two tests should be part of local store tests instead.
  @Test
  public void testAddThenUpdatesWhileOffline() {
    CollectionReference collection = testCollection();
    collection.getFirestore().disableNetwork();

    collection.add(map("foo", "zzyzx", "bar", "1"));

    QuerySnapshot snapshot1 = waitFor(collection.get(Source.CACHE));
    assertEquals(asList(map("foo", "zzyzx", "bar", "1")), querySnapshotToValues(snapshot1));
    DocumentReference doc = snapshot1.getDocuments().get(0).getReference();

    doc.update(map("bar", "2"));

    QuerySnapshot snapshot2 = waitFor(collection.get(Source.CACHE));
    assertEquals(asList(map("foo", "zzyzx", "bar", "2")), querySnapshotToValues(snapshot2));
  }

  @Test
  public void testMultipleUpdatesWhileOffline() {
    CollectionReference collection = testCollection();
    collection.getFirestore().disableNetwork();

    DocumentReference doc = collection.document();
    doc.set(map("foo", "zzyzx", "bar", "1"), SetOptions.mergeFields("foo", "bar"));

    QuerySnapshot snapshot1 = waitFor(collection.get(Source.CACHE));
    assertEquals(asList(map("foo", "zzyzx", "bar", "1")), querySnapshotToValues(snapshot1));

    doc.update(map("bar", "2"));

    QuerySnapshot snapshot2 = waitFor(collection.get(Source.CACHE));
    assertEquals(asList(map("foo", "zzyzx", "bar", "2")), querySnapshotToValues(snapshot2));
  }

  @Test
  public void resumingAQueryShouldUseBloomFilterToAvoidFullRequery() throws Exception {
    // TODO(b/291365820): Stop skipping this test when running against the Firestore emulator once
    // the emulator is improved to include a bloom filter in the existence filter "messages that it
    // sends.
    assumeFalse(
        "Skip this test when running against the Firestore emulator because the emulator does not "
            + "include a bloom filter when it sends existence filter messages, making it "
            + "impossible for this test to verify the correctness of the bloom filter.",
        isRunningAgainstEmulator());

    // Prepare the names and contents of the 100 documents to create.
    Map<String, Map<String, Object>> testData = new HashMap<>();
    for (int i = 0; i < 100; i++) {
      testData.put("doc" + (1000 + i), map("key", 42));
    }

    // Each iteration of the "while" loop below runs a single iteration of the test. The test will
    // be run multiple times only if a bloom filter false positive occurs.
    int attemptNumber = 0;
    while (true) {
      attemptNumber++;

      // Create 100 documents in a new collection.
      CollectionReference collection = testCollectionWithDocs(testData);

      // Run a query to populate the local cache with the 100 documents and a resume token.
      List<DocumentReference> createdDocuments = new ArrayList<>();
      {
        QuerySnapshot querySnapshot = waitFor(collection.get());
        assertWithMessage("querySnapshot1").that(querySnapshot.size()).isEqualTo(100);
        for (DocumentSnapshot documentSnapshot : querySnapshot.getDocuments()) {
          createdDocuments.add(documentSnapshot.getReference());
        }
      }
      assertWithMessage("createdDocuments").that(createdDocuments).hasSize(100);

      // Delete 50 of the 100 documents. Use a different Firestore instance to avoid affecting the
      // local cache.
      HashSet<String> deletedDocumentIds = new HashSet<>();
      {
        FirebaseFirestore db2 = testFirestore();
        WriteBatch batch = db2.batch();
        for (int i = 0; i < createdDocuments.size(); i += 2) {
          DocumentReference documentToDelete = db2.document(createdDocuments.get(i).getPath());
          batch.delete(documentToDelete);
          deletedDocumentIds.add(documentToDelete.getId());
        }
        waitFor(batch.commit());
      }
      assertWithMessage("deletedDocumentIds").that(deletedDocumentIds).hasSize(50);

      // Wait for 10 seconds, during which Watch will stop tracking the query and will send an
      // existence filter rather than "delete" events when the query is resumed.
      Thread.sleep(10000);

      // Resume the query and save the resulting snapshot for verification. Use some internal
      // testing hooks to "capture" the existence filter mismatches to verify that Watch sent a
      // bloom filter, and it was used to avert a full requery.
      AtomicReference<QuerySnapshot> snapshot2Ref = new AtomicReference<>();
      ArrayList<ExistenceFilterMismatchInfo> existenceFilterMismatches =
          captureExistenceFilterMismatches(
              () -> {
                QuerySnapshot querySnapshot = waitFor(collection.get());
                snapshot2Ref.set(querySnapshot);
              });
      QuerySnapshot snapshot2 = snapshot2Ref.get();

      // Verify that the snapshot from the resumed query contains the expected documents; that is,
      // that it contains the 50 documents that were _not_ deleted.
      HashSet<String> actualDocumentIds = new HashSet<>();
      for (DocumentSnapshot documentSnapshot : snapshot2.getDocuments()) {
        actualDocumentIds.add(documentSnapshot.getId());
      }
      HashSet<String> expectedDocumentIds = new HashSet<>();
      for (DocumentReference documentRef : createdDocuments) {
        if (!deletedDocumentIds.contains(documentRef.getId())) {
          expectedDocumentIds.add(documentRef.getId());
        }
      }
      assertWithMessage("snapshot2.docs")
          .that(actualDocumentIds)
          .containsExactlyElementsIn(expectedDocumentIds);

      // Verify that Watch sent an existence filter with the correct counts when the query was
      // resumed.
      assertWithMessage("Watch should have sent exactly 1 existence filter")
          .that(existenceFilterMismatches)
          .hasSize(1);
      ExistenceFilterMismatchInfo existenceFilterMismatchInfo = existenceFilterMismatches.get(0);
      assertWithMessage("localCacheCount")
          .that(existenceFilterMismatchInfo.localCacheCount())
          .isEqualTo(100);
      assertWithMessage("existenceFilterCount")
          .that(existenceFilterMismatchInfo.existenceFilterCount())
          .isEqualTo(50);

      // Verify that Watch sent a valid bloom filter.
      ExistenceFilterBloomFilterInfo bloomFilter = existenceFilterMismatchInfo.bloomFilter();
      assertWithMessage("The bloom filter specified in the existence filter")
          .that(bloomFilter)
          .isNotNull();
      assertWithMessage("hashCount").that(bloomFilter.hashCount()).isGreaterThan(0);
      assertWithMessage("bitmapLength").that(bloomFilter.bitmapLength()).isGreaterThan(0);
      assertWithMessage("padding").that(bloomFilter.padding()).isGreaterThan(0);
      assertWithMessage("padding").that(bloomFilter.padding()).isLessThan(8);

      // Verify that the bloom filter was successfully used to avert a full requery. If a false
      // positive occurred then retry the entire test. Although statistically rare, false positives
      // are expected to happen occasionally. When a false positive _does_ happen, just retry the
      // test with a different set of documents. If that retry _also_ experiences a false positive,
      // then fail the test because that is so improbable that something must have gone wrong.
      if (attemptNumber == 1 && !bloomFilter.applied()) {
        continue;
      }

      assertWithMessage("bloom filter successfully applied with attemptNumber=" + attemptNumber)
          .that(bloomFilter.applied())
          .isTrue();

      // Break out of the test loop now that the test passes.
      break;
    }
  }

  @Test
  public void
      bloomFilterShouldAvertAFullRequeryWhenDocumentsWereAddedDeletedRemovedUpdatedAndUnchangedSinceTheResumeToken()
          throws Exception {
    // TODO(b/291365820): Stop skipping this test when running against the Firestore emulator once
    // the emulator is improved to include a bloom filter in the existence filter messages that it
    // sends.
    assumeFalse(
        "Skip this test when running against the Firestore emulator because the emulator does not "
            + "include a bloom filter when it sends existence filter messages, making it "
            + "impossible for this test to verify the correctness of the bloom filter.",
        isRunningAgainstEmulator());

    // Prepare the names and contents of the 20 documents to create.
    Map<String, Map<String, Object>> testData = new HashMap<>();
    for (int i = 0; i < 20; i++) {
      testData.put("doc" + (1000 + i), map("key", 42, "removed", false));
    }

    // Each iteration of the "while" loop below runs a single iteration of the test. The test will
    // be run multiple times only if a bloom filter false positive occurs.
    int attemptNumber = 0;
    while (true) {
      attemptNumber++;

      // Create 20 documents in a new collection.
      CollectionReference collection = testCollectionWithDocs(testData);
      Query query = collection.whereEqualTo("removed", false);

      // Run a query to populate the local cache with the 20 documents and a resume token.
      List<DocumentReference> createdDocuments = new ArrayList<>();
      {
        QuerySnapshot querySnapshot = waitFor(query.get());
        assertWithMessage("querySnapshot1").that(querySnapshot.size()).isEqualTo(20);
        for (DocumentSnapshot documentSnapshot : querySnapshot.getDocuments()) {
          createdDocuments.add(documentSnapshot.getReference());
        }
      }
      assertWithMessage("createdDocuments").that(createdDocuments).hasSize(20);

      // Out of the 20 existing documents, leave 5 docs untouched, delete 5 docs, remove 5 docs,
      // update 5 docs, and add 15 new docs.
      HashSet<String> deletedDocumentIds = new HashSet<>();
      HashSet<String> removedDocumentIds = new HashSet<>();
      HashSet<String> updatedDocumentIds = new HashSet<>();
      HashSet<String> addedDocumentIds = new HashSet<>();

      {
        FirebaseFirestore db2 = testFirestore();
        WriteBatch batch = db2.batch();

        for (int i = 0; i < createdDocuments.size(); i += 4) {
          DocumentReference documentToDelete = db2.document(createdDocuments.get(i).getPath());
          batch.delete(documentToDelete);
          deletedDocumentIds.add(documentToDelete.getId());
        }
        assertWithMessage("deletedDocumentIds").that(deletedDocumentIds).hasSize(5);

        // Update 5 documents to no longer match the query.
        for (int i = 1; i < createdDocuments.size(); i += 4) {
          DocumentReference documentToRemove = db2.document(createdDocuments.get(i).getPath());
          batch.update(documentToRemove, map("removed", true));
          removedDocumentIds.add(documentToRemove.getId());
        }
        assertWithMessage("removedDocumentIds").that(removedDocumentIds).hasSize(5);

        // Update 5 documents, but ensure they still match the query.
        for (int i = 2; i < createdDocuments.size(); i += 4) {
          DocumentReference documentToUpdate = db2.document(createdDocuments.get(i).getPath());
          batch.update(documentToUpdate, map("key", 43));
          updatedDocumentIds.add(documentToUpdate.getId());
        }
        assertWithMessage("updatedDocumentIds").that(updatedDocumentIds).hasSize(5);

        for (int i = 0; i < 15; i += 1) {
          DocumentReference documentToUpdate =
              db2.document(collection.getPath() + "/newDoc" + (1000 + i));
          batch.set(documentToUpdate, map("key", 42, "removed", false));
          addedDocumentIds.add(documentToUpdate.getId());
        }

        // Ensure the sets above are disjoint.
        HashSet<String> mergedSet = new HashSet<>();
        mergedSet.addAll(deletedDocumentIds);
        mergedSet.addAll(removedDocumentIds);
        mergedSet.addAll(updatedDocumentIds);
        mergedSet.addAll(addedDocumentIds);
        assertWithMessage("mergedSet").that(mergedSet).hasSize(30);

        waitFor(batch.commit());
      }

      // Wait for 10 seconds, during which Watch will stop tracking the query and will send an
      // existence filter rather than "delete" events when the query is resumed.
      Thread.sleep(10000);

      // Resume the query and save the resulting snapshot for verification. Use some internal
      // testing hooks to "capture" the existence filter mismatches to verify that Watch sent a
      // bloom filter, and it was used to avert a full requery.
      AtomicReference<QuerySnapshot> snapshot2Ref = new AtomicReference<>();
      ArrayList<ExistenceFilterMismatchInfo> existenceFilterMismatches =
          captureExistenceFilterMismatches(
              () -> {
                QuerySnapshot querySnapshot = waitFor(query.get());
                snapshot2Ref.set(querySnapshot);
              });
      QuerySnapshot snapshot2 = snapshot2Ref.get();

      // Verify that the snapshot from the resumed query contains the expected documents; that is,
      // 10 existing documents that still match the query, and 15 documents that are newly added.
      HashSet<String> actualDocumentIds = new HashSet<>();
      for (DocumentSnapshot documentSnapshot : snapshot2.getDocuments()) {
        actualDocumentIds.add(documentSnapshot.getId());
      }
      HashSet<String> expectedDocumentIds = new HashSet<>();
      for (DocumentReference documentRef : createdDocuments) {
        if (!deletedDocumentIds.contains(documentRef.getId())
            && !removedDocumentIds.contains(documentRef.getId())) {
          expectedDocumentIds.add(documentRef.getId());
        }
      }
      expectedDocumentIds.addAll(addedDocumentIds);
      assertWithMessage("snapshot2.docs")
          .that(actualDocumentIds)
          .containsExactlyElementsIn(expectedDocumentIds);
      assertWithMessage("actualDocumentIds").that(actualDocumentIds).hasSize(25);

      // Verify that Watch sent an existence filter with the correct counts when the query was
      // resumed.
      assertWithMessage("Watch should have sent exactly 1 existence filter")
          .that(existenceFilterMismatches)
          .hasSize(1);
      ExistenceFilterMismatchInfo existenceFilterMismatchInfo = existenceFilterMismatches.get(0);
      assertWithMessage("localCacheCount")
          .that(existenceFilterMismatchInfo.localCacheCount())
          .isEqualTo(35);
      assertWithMessage("existenceFilterCount")
          .that(existenceFilterMismatchInfo.existenceFilterCount())
          .isEqualTo(25);

      // Verify that Watch sent a valid bloom filter.
      ExistenceFilterBloomFilterInfo bloomFilter = existenceFilterMismatchInfo.bloomFilter();
      assertWithMessage("The bloom filter specified in the existence filter")
          .that(bloomFilter)
          .isNotNull();

      // Verify that the bloom filter was successfully used to avert a full requery. If a false
      // positive occurred then retry the entire test. Although statistically rare, false positives
      // are expected to happen occasionally. When a false positive _does_ happen, just retry the
      // test with a different set of documents. If that retry _also_ experiences a false positive,
      // then fail the test because that is so improbable that something must have gone wrong.
      if (attemptNumber == 1 && !bloomFilter.applied()) {
        continue;
      }

      assertWithMessage("bloom filter successfully applied with attemptNumber=" + attemptNumber)
          .that(bloomFilter.applied())
          .isTrue();

      // Break out of the test loop now that the test passes.
      break;
    }
  }

  private static String unicodeNormalize(String s) {
    return Normalizer.normalize(s, Normalizer.Form.NFC);
  }

  @Test
  public void bloomFilterShouldCorrectlyEncodeComplexUnicodeCharacters() throws Exception {
    // TODO(b/291365820): Stop skipping this test when running against the Firestore emulator once
    // the emulator is improved to include a bloom filter in the existence filter "messages that it
    // sends.
    assumeFalse(
        "Skip this test when running against the Firestore emulator because the emulator does not "
            + "include a bloom filter when it sends existence filter messages, making it "
            + "impossible for this test to verify the correctness of the bloom filter.",
        isRunningAgainstEmulator());

    // Firestore does not do any Unicode normalization on the document IDs. Therefore, two document
    // IDs that are canonically-equivalent (they visually appear identical) but are represented by a
    // different sequence of Unicode code points are treated as distinct document IDs.
    ArrayList<String> testDocIds = new ArrayList<>();
    testDocIds.add("DocumentToDelete");
    // The next two strings both end with "e" with an accent: the first uses the dedicated Unicode
    // code point for this character, while the second uses the standard lowercase "e" followed by
    // the accent combining character.
    testDocIds.add("LowercaseEWithAcuteAccent_\u00E9");
    testDocIds.add("LowercaseEWithAcuteAccent_\u0065\u0301");
    // The next two strings both end with an "e" with two different accents applied via the
    // following two combining characters. The combining characters are specified in a different
    // order and Firestore treats these document IDs as unique, despite the order of the combining
    // characters being irrelevant.
    testDocIds.add("LowercaseEWithMultipleAccents_\u0065\u0301\u0327");
    testDocIds.add("LowercaseEWithMultipleAccents_\u0065\u0327\u0301");
    // The next string contains a character outside the BMP (the "basic multilingual plane"); that
    // is, its code point is greater than 0xFFFF. Since "The Java programming language represents
    // text in sequences of 16-bit code units, using the UTF-16 encoding" (according to the "Java
    // Language Specification" at https://docs.oracle.com/javase/specs/jls/se11/html/index.html)
    // this requires a surrogate pair, two 16-bit code units, to represent this character. Make sure
    // that its presence is correctly tested in the bloom filter, which uses UTF-8 encoding.
    testDocIds.add("Smiley_\uD83D\uDE00");

    // Verify assumptions about the equivalence of strings in `testDocIds`.
    assertThat(unicodeNormalize(testDocIds.get(1))).isEqualTo(unicodeNormalize(testDocIds.get(2)));
    assertThat(unicodeNormalize(testDocIds.get(3))).isEqualTo(unicodeNormalize(testDocIds.get(4)));
    assertThat(testDocIds.get(5).codePointAt(7)).isEqualTo(0x1F600);

    // Create the mapping from document ID to document data for the document IDs specified in
    // `testDocIds`.
    Map<String, Map<String, Object>> testDocs = new HashMap<>();
    for (String docId : testDocIds) {
      testDocs.put(docId, map("foo", 42));
    }

    // Create the documents whose names contain complex Unicode characters in a new collection.
    CollectionReference collection = testCollectionWithDocs(testDocs);

    // Run a query to populate the local cache with documents that have names with complex Unicode
    // characters.
    List<DocumentReference> createdDocuments = new ArrayList<>();
    {
      QuerySnapshot querySnapshot1 = waitFor(collection.get());
      for (DocumentSnapshot documentSnapshot : querySnapshot1.getDocuments()) {
        createdDocuments.add(documentSnapshot.getReference());
      }
      HashSet<String> createdDocumentIds = new HashSet<>();
      for (DocumentSnapshot documentSnapshot : querySnapshot1.getDocuments()) {
        createdDocumentIds.add(documentSnapshot.getId());
      }
      assertWithMessage("createdDocumentIds")
          .that(createdDocumentIds)
          .containsExactlyElementsIn(testDocIds);
    }

    // Delete one of the documents so that the next call to collection.get() will experience an
    // existence filter mismatch. Use a different Firestore instance to avoid affecting the local
    // cache.
    DocumentReference documentToDelete = collection.document("DocumentToDelete");
    waitFor(testFirestore().document(documentToDelete.getPath()).delete());

    // Wait for 10 seconds, during which Watch will stop tracking the query and will send an
    // existence filter rather than "delete" events when the query is resumed.
    Thread.sleep(10000);

    // Resume the query and save the resulting snapshot for verification. Use some internal testing
    // hooks to "capture" the existence filter mismatches.
    AtomicReference<QuerySnapshot> querySnapshot2Ref = new AtomicReference<>();
    ArrayList<ExistenceFilterMismatchInfo> existenceFilterMismatches =
        captureExistenceFilterMismatches(
            () -> {
              QuerySnapshot querySnapshot = waitFor(collection.get());
              querySnapshot2Ref.set(querySnapshot);
            });
    QuerySnapshot querySnapshot2 = querySnapshot2Ref.get();

    // Verify that the snapshot from the resumed query contains the expected documents; that is,
    // that it contains the documents whose names contain complex Unicode characters and _not_ the
    // document that was deleted.
    HashSet<String> querySnapshot2DocumentIds = new HashSet<>();
    for (DocumentSnapshot documentSnapshot : querySnapshot2.getDocuments()) {
      querySnapshot2DocumentIds.add(documentSnapshot.getId());
    }
    HashSet<String> querySnapshot2ExpectedDocumentIds = new HashSet<>(testDocIds);
    querySnapshot2ExpectedDocumentIds.remove("DocumentToDelete");
    assertWithMessage("querySnapshot2DocumentIds")
        .that(querySnapshot2DocumentIds)
        .containsExactlyElementsIn(querySnapshot2ExpectedDocumentIds);

    // Verify that Watch sent an existence filter with the correct counts.
    assertWithMessage("Watch should have sent exactly 1 existence filter")
        .that(existenceFilterMismatches)
        .hasSize(1);
    ExistenceFilterMismatchInfo existenceFilterMismatchInfo = existenceFilterMismatches.get(0);
    assertWithMessage("localCacheCount")
        .that(existenceFilterMismatchInfo.localCacheCount())
        .isEqualTo(testDocIds.size());
    assertWithMessage("existenceFilterCount")
        .that(existenceFilterMismatchInfo.existenceFilterCount())
        .isEqualTo(testDocIds.size() - 1);

    // Verify that Watch sent a valid bloom filter.
    ExistenceFilterBloomFilterInfo bloomFilter = existenceFilterMismatchInfo.bloomFilter();
    assertWithMessage("The bloom filter specified in the existence filter")
        .that(bloomFilter)
        .isNotNull();

    // The bloom filter application should statistically be successful almost every time; the _only_
    // time when it would _not_ be successful is if there is a false positive when testing for
    // 'DocumentToDelete' in the bloom filter. So verify that the bloom filter application is
    // successful, unless there was a false positive.
    boolean isFalsePositive = bloomFilter.mightContain(documentToDelete);
    assertWithMessage("bloomFilter.applied()")
        .that(bloomFilter.applied())
        .isEqualTo(!isFalsePositive);

    // Verify that the bloom filter contains the document paths with complex Unicode characters.
    for (DocumentSnapshot documentSnapshot : querySnapshot2.getDocuments()) {
      DocumentReference documentReference = documentSnapshot.getReference();
      assertWithMessage("bloomFilter.mightContain() for " + documentReference.getPath())
          .that(bloomFilter.mightContain(documentReference))
          .isTrue();
    }
  }

  @Test
  public void testOrQueries() {
    Map<String, Map<String, Object>> testDocs =
        map(
            "doc1", map("a", 1, "b", 0),
            "doc2", map("a", 2, "b", 1),
            "doc3", map("a", 3, "b", 2),
            "doc4", map("a", 1, "b", 3),
            "doc5", map("a", 1, "b", 1));
    CollectionReference collection = testCollectionWithDocs(testDocs);

    // Two equalities: a==1 || b==1.
    checkOnlineAndOfflineResultsMatch(
        collection.where(or(equalTo("a", 1), equalTo("b", 1))), "doc1", "doc2", "doc4", "doc5");

    // (a==1 && b==0) || (a==3 && b==2)
    checkOnlineAndOfflineResultsMatch(
        collection.where(
            or(and(equalTo("a", 1), equalTo("b", 0)), and(equalTo("a", 3), equalTo("b", 2)))),
        "doc1",
        "doc3");

    // a==1 && (b==0 || b==3).
    checkOnlineAndOfflineResultsMatch(
        collection.where(and(equalTo("a", 1), or(equalTo("b", 0), equalTo("b", 3)))),
        "doc1",
        "doc4");

    // (a==2 || b==2) && (a==3 || b==3)
    checkOnlineAndOfflineResultsMatch(
        collection.where(
            and(or(equalTo("a", 2), equalTo("b", 2)), or(equalTo("a", 3), equalTo("b", 3)))),
        "doc3");

    // Test with limits without orderBy (the __name__ ordering is the tie breaker).
    checkOnlineAndOfflineResultsMatch(
        collection.where(or(equalTo("a", 2), equalTo("b", 1))).limit(1), "doc2");
  }

  @Test
  public void testOrQueriesWithIn() {
    Map<String, Map<String, Object>> testDocs =
        map(
            "doc1", map("a", 1, "b", 0),
            "doc2", map("b", 1),
            "doc3", map("a", 3, "b", 2),
            "doc4", map("a", 1, "b", 3),
            "doc5", map("a", 1),
            "doc6", map("a", 2));
    CollectionReference collection = testCollectionWithDocs(testDocs);

    // a==2 || b in [2,3]
    checkOnlineAndOfflineResultsMatch(
        collection.where(or(equalTo("a", 2), inArray("b", asList(2, 3)))), "doc3", "doc4", "doc6");
  }

  @Test
  public void testOrQueriesWithArrayMembership() {
    Map<String, Map<String, Object>> testDocs =
        map(
            "doc1", map("a", 1, "b", asList(0)),
            "doc2", map("b", asList(1)),
            "doc3", map("a", 3, "b", asList(2, 7)),
            "doc4", map("a", 1, "b", asList(3, 7)),
            "doc5", map("a", 1),
            "doc6", map("a", 2));
    CollectionReference collection = testCollectionWithDocs(testDocs);

    // a==2 || b array-contains 7
    checkOnlineAndOfflineResultsMatch(
        collection.where(or(equalTo("a", 2), arrayContains("b", 7))), "doc3", "doc4", "doc6");

    // a==2 || b array-contains-any [0, 3]
    checkOnlineAndOfflineResultsMatch(
        collection.where(or(equalTo("a", 2), arrayContainsAny("b", asList(0, 3)))),
        "doc1",
        "doc4",
        "doc6");
  }

  @Test
  public void testMultipleInOps() {
    Map<String, Map<String, Object>> testDocs =
        map(
            "doc1", map("a", 1, "b", 0),
            "doc2", map("b", 1),
            "doc3", map("a", 3, "b", 2),
            "doc4", map("a", 1, "b", 3),
            "doc5", map("a", 1),
            "doc6", map("a", 2));
    CollectionReference collection = testCollectionWithDocs(testDocs);

    // Two IN operations on different fields with disjunction.
    Query query1 = collection.where(or(inArray("a", asList(2, 3)), inArray("b", asList(0, 2))));
    checkOnlineAndOfflineResultsMatch(query1, "doc1", "doc3", "doc6");

    // Two IN operations on the same field with disjunction.
    // a IN [0,3] || a IN [0,2] should union them (similar to: a IN [0,2,3]).
    Query query2 = collection.where(or(inArray("a", asList(0, 3)), inArray("a", asList(0, 2))));
    checkOnlineAndOfflineResultsMatch(query2, "doc3", "doc6");
  }

  @Test
  public void testUsingInWithArrayContainsAny() {
    Map<String, Map<String, Object>> testDocs =
        map(
            "doc1", map("a", 1, "b", asList(0)),
            "doc2", map("b", asList(1)),
            "doc3", map("a", 3, "b", asList(2, 7), "c", 10),
            "doc4", map("a", 1, "b", asList(3, 7)),
            "doc5", map("a", 1),
            "doc6", map("a", 2, "c", 20));
    CollectionReference collection = testCollectionWithDocs(testDocs);

    Query query1 =
        collection.where(or(inArray("a", asList(2, 3)), arrayContainsAny("b", asList(0, 7))));
    checkOnlineAndOfflineResultsMatch(query1, "doc1", "doc3", "doc4", "doc6");

    Query query2 =
        collection.where(
            or(
                and(inArray("a", asList(2, 3)), equalTo("c", 10)),
                arrayContainsAny("b", asList(0, 7))));
    checkOnlineAndOfflineResultsMatch(query2, "doc1", "doc3", "doc4");
  }

  @Test
  public void testUsingInWithArrayContains() {
    Map<String, Map<String, Object>> testDocs =
        map(
            "doc1", map("a", 1, "b", asList(0)),
            "doc2", map("b", asList(1)),
            "doc3", map("a", 3, "b", asList(2, 7)),
            "doc4", map("a", 1, "b", asList(3, 7)),
            "doc5", map("a", 1),
            "doc6", map("a", 2));
    CollectionReference collection = testCollectionWithDocs(testDocs);

    Query query1 = collection.where(or(inArray("a", asList(2, 3)), arrayContains("b", 3)));
    checkOnlineAndOfflineResultsMatch(query1, "doc3", "doc4", "doc6");

    Query query2 = collection.where(and(inArray("a", asList(2, 3)), arrayContains("b", 7)));
    checkOnlineAndOfflineResultsMatch(query2, "doc3");

    Query query3 =
        collection.where(
            or(inArray("a", asList(2, 3)), and(arrayContains("b", 3), equalTo("a", 1))));
    checkOnlineAndOfflineResultsMatch(query3, "doc3", "doc4", "doc6");

    Query query4 =
        collection.where(
            and(inArray("a", asList(2, 3)), or(arrayContains("b", 7), equalTo("a", 1))));
    checkOnlineAndOfflineResultsMatch(query4, "doc3");
  }

  @Test
  public void testOrderByEquality() {
    Map<String, Map<String, Object>> testDocs =
        map(
            "doc1", map("a", 1, "b", asList(0)),
            "doc2", map("b", asList(1)),
            "doc3", map("a", 3, "b", asList(2, 7), "c", 10),
            "doc4", map("a", 1, "b", asList(3, 7)),
            "doc5", map("a", 1),
            "doc6", map("a", 2, "c", 20));
    CollectionReference collection = testCollectionWithDocs(testDocs);

    Query query1 = collection.where(equalTo("a", 1)).orderBy("a");
    checkOnlineAndOfflineResultsMatch(query1, "doc1", "doc4", "doc5");

    Query query2 = collection.where(inArray("a", asList(2, 3))).orderBy("a");
    checkOnlineAndOfflineResultsMatch(query2, "doc6", "doc3");
  }
}
