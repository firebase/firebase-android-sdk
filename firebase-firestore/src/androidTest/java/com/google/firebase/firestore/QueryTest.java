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

import static com.google.firebase.firestore.testutil.IntegrationTestUtil.querySnapshotToIds;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.querySnapshotToValues;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testCollection;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testCollectionWithDocs;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testFirestore;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.Query.Direction;
import com.google.firebase.firestore.testutil.EventAccumulator;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
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
  public void testQueriesCanUseArrayContainsFilters() {
    Map<String, Object> docA = map("array", asList(42L));
    Map<String, Object> docB = map("array", asList("a", 42L, "c"));
    Map<String, Object> docC = map("array", asList(41.999, "42", map("a", asList(42))));
    Map<String, Object> docD = map("array", asList(42L), "array2", asList("bingo"));
    CollectionReference collection =
        testCollectionWithDocs(map("a", docA, "b", docB, "c", docC, "d", docD));

    // Search for "array" to contain 42
    QuerySnapshot snapshot = waitFor(collection.whereArrayContains("array", 42L).get());
    assertEquals(asList(docA, docB, docD), querySnapshotToValues(snapshot));

    // NOTE: The backend doesn't currently support null, NaN, objects, or arrays, so there isn't
    // much of anything else interesting to test.
  }

  @Test
  public void testQueriesCanUseInFilters() {
    Map<String, Object> docA = map("zip", 98101L);
    Map<String, Object> docB = map("zip", 91102L);
    Map<String, Object> docC = map("zip", 98103L);
    Map<String, Object> docD = map("zip", asList(98101L));
    Map<String, Object> docE = map("zip", asList("98101", map("zip", 98101L)));
    Map<String, Object> docF = map("zip", map("code", 500L));
    CollectionReference collection =
        testCollectionWithDocs(
            map("a", docA, "b", docB, "c", docC, "d", docD, "e", docE, "f", docF));

    // Search for zips matching [98101, 98103].
    QuerySnapshot snapshot = waitFor(collection.whereIn("zip", asList(98101L, 98103L)).get());
    assertEquals(asList(docA, docC), querySnapshotToValues(snapshot));

    // With objects.
    snapshot = waitFor(collection.whereIn("zip", asList(map("code", 500L))).get());
    assertEquals(asList(docF), querySnapshotToValues(snapshot));
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
  public void testQueriesCanUseArrayContainsAnyFilters() {
    Map<String, Object> docA = map("array", asList(42L));
    Map<String, Object> docB = map("array", asList("a", 42L, "c"));
    Map<String, Object> docC = map("array", asList(41.999, "42", map("a", asList(42))));
    Map<String, Object> docD = map("array", asList(42L), "array2", asList("bingo"));
    Map<String, Object> docE = map("array", asList(43L));
    Map<String, Object> docF = map("array", asList(map("a", 42L)));
    Map<String, Object> docG = map("array", 42L);

    CollectionReference collection =
        testCollectionWithDocs(
            map("a", docA, "b", docB, "c", docC, "d", docD, "e", docE, "f", docF));

    // Search for "array" to contain [42, 43].
    QuerySnapshot snapshot =
        waitFor(collection.whereArrayContainsAny("array", asList(42L, 43L)).get());
    assertEquals(asList(docA, docB, docD, docE), querySnapshotToValues(snapshot));

    // With objects.
    snapshot = waitFor(collection.whereArrayContainsAny("array", asList(map("a", 42L))).get());
    assertEquals(asList(docF), querySnapshotToValues(snapshot));
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
}
