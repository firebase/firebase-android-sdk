// Copyright 2024 Google LLC
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

import static com.google.firebase.firestore.testutil.IntegrationTestUtil.querySnapshotToValues;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testCollection;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testCollectionWithDocs;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testDocumentWithData;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.firestore.Query.Direction;
import com.google.firebase.firestore.testutil.EventAccumulator;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SnapshotListenerSourceTest {

  @After
  public void tearDown() {
    IntegrationTestUtil.tearDown();
  }

  public SnapshotListenOptions optionSourceFromCache() {
    return new SnapshotListenOptions.Builder().setSource(ListenSource.CACHE).build();
  }

  public SnapshotListenOptions optionSourceFromCacheAndIncludeMetadataChanges() {
    return new SnapshotListenOptions.Builder()
        .setMetadataChanges(MetadataChanges.INCLUDE)
        .setSource(ListenSource.CACHE)
        .build();
  }

  @Test
  public void canRaiseSnapshotFromCacheForQuery() {
    CollectionReference collection = testCollectionWithDocs(map("a", map("k", "a")));

    waitFor(collection.get()); // Populate the cache.

    EventAccumulator<QuerySnapshot> accumulator = new EventAccumulator<>();
    SnapshotListenOptions options = optionSourceFromCache();
    ListenerRegistration listener = collection.addSnapshotListener(options, accumulator.listener());

    QuerySnapshot snapshot = accumulator.await();
    assertEquals(asList(map("k", "a")), querySnapshotToValues(snapshot));
    assertTrue(snapshot.getMetadata().isFromCache());

    accumulator.assertNoAdditionalEvents();
    listener.remove();
  }

  @Test
  public void canRaiseSnapshotFromCacheForDocumentReference() {
    DocumentReference docRef = testDocumentWithData(map("k", "a"));

    waitFor(docRef.get()); // Populate the cache.

    EventAccumulator<DocumentSnapshot> accumulator = new EventAccumulator<>();
    SnapshotListenOptions options = optionSourceFromCache();
    ListenerRegistration listener = docRef.addSnapshotListener(options, accumulator.listener());

    DocumentSnapshot snapshot = accumulator.await();
    assertEquals(map("k", "a"), snapshot.getData());
    assertTrue(snapshot.getMetadata().isFromCache());

    accumulator.assertNoAdditionalEvents();
    listener.remove();
  }

  @Test
  public void listenToCacheShouldNotBeAffectedByOnlineStatusChange() {
    CollectionReference collection = testCollectionWithDocs(map("a", map("k", "a")));

    waitFor(collection.get()); // Populate the cache.

    EventAccumulator<QuerySnapshot> accumulator = new EventAccumulator<>();
    SnapshotListenOptions options = optionSourceFromCacheAndIncludeMetadataChanges();
    ListenerRegistration listener = collection.addSnapshotListener(options, accumulator.listener());

    QuerySnapshot snapshot = accumulator.await();
    assertEquals(asList(map("k", "a")), querySnapshotToValues(snapshot));
    assertTrue(snapshot.getMetadata().isFromCache());

    waitFor(collection.firestore.disableNetwork());
    waitFor(collection.firestore.enableNetwork());

    accumulator.assertNoAdditionalEvents();
    listener.remove();
  }

  @Test
  public void multipleListenersSourcedFromCacheCanWorkIndependently() {
    CollectionReference collection =
        testCollectionWithDocs(map("a", map("k", "a", "sort", 0), "b", map("k", "b", "sort", 1)));
    Query query = collection.whereGreaterThan("sort", 0).orderBy("sort", Direction.ASCENDING);

    waitFor(collection.get()); // Populate the cache.

    EventAccumulator<QuerySnapshot> accumulator = new EventAccumulator<>();
    SnapshotListenOptions options = optionSourceFromCache();
    ListenerRegistration listener1 = query.addSnapshotListener(options, accumulator.listener());
    ListenerRegistration listener2 = query.addSnapshotListener(options, accumulator.listener());

    List<QuerySnapshot> snapshots = accumulator.await(2);
    assertEquals(asList(map("k", "b", "sort", 1L)), querySnapshotToValues(snapshots.get(0)));
    assertEquals(querySnapshotToValues(snapshots.get(0)), querySnapshotToValues(snapshots.get(1)));
    assertEquals(snapshots.get(0).getMetadata(), snapshots.get(1).getMetadata());

    // Do a local mutation
    waitFor(collection.add(map("k", "c", "sort", 2)));

    snapshots = accumulator.await(2);
    assertEquals(
        asList(map("k", "b", "sort", 1L), map("k", "c", "sort", 2L)),
        querySnapshotToValues(snapshots.get(0)));
    assertEquals(querySnapshotToValues(snapshots.get(0)), querySnapshotToValues(snapshots.get(1)));
    assertEquals(snapshots.get(0).getMetadata(), snapshots.get(1).getMetadata());

    // Detach one listener, and do a local mutation. The other listener
    // should not be affected.
    listener1.remove();
    waitFor(collection.add(map("k", "d", "sort", 3)));

    QuerySnapshot snapshot = accumulator.await();
    assertEquals(
        asList(map("k", "b", "sort", 1L), map("k", "c", "sort", 2L), map("k", "d", "sort", 3L)),
        querySnapshotToValues(snapshot));
    assertTrue(snapshot.getMetadata().isFromCache());

    accumulator.assertNoAdditionalEvents();
    listener2.remove();
  }

  // Two queries that mapped to the same target ID are referred to as
  // "mirror queries". An example for a mirror query is a limitToLast()
  // query and a limit() query that share the same backend Target ID.
  // Since limitToLast() queries are sent to the backend with a modified
  // orderBy() clause, they can map to the same target representation as
  // limit() query, even if both queries appear separate to the user.
  @Test
  public void canListenUnlistenRelistenMirrorQueriesFromCache() {
    CollectionReference collection =
        testCollectionWithDocs(
            map(
                "a", map("k", "a", "sort", 0),
                "b", map("k", "b", "sort", 1),
                "c", map("k", "c", "sort", 1)));

    waitFor(collection.get()); // Populate the cache.
    SnapshotListenOptions options = optionSourceFromCache();

    // Setup `limit` query.
    Query limit = collection.limit(2).orderBy("sort", Direction.ASCENDING);
    EventAccumulator<QuerySnapshot> limitAccumulator = new EventAccumulator<>();
    ListenerRegistration limitRegistration =
        limit.addSnapshotListener(options, limitAccumulator.listener());

    // Setup mirroring `limitToLast` query.
    Query limitToLast = collection.limitToLast(2).orderBy("sort", Direction.DESCENDING);
    EventAccumulator<QuerySnapshot> limitToLastAccumulator = new EventAccumulator<>();
    ListenerRegistration limitToLastRegistration =
        limitToLast.addSnapshotListener(options, limitToLastAccumulator.listener());

    // Verify both query get expected result.
    QuerySnapshot snapshot = limitAccumulator.await();
    assertEquals(
        asList(map("k", "a", "sort", 0L), map("k", "b", "sort", 1L)),
        querySnapshotToValues(snapshot));
    snapshot = limitToLastAccumulator.await();
    assertEquals(
        asList(map("k", "b", "sort", 1L), map("k", "a", "sort", 0L)),
        querySnapshotToValues(snapshot));

    // Un-listen then re-listen limit query.
    limitRegistration.remove();
    limit.addSnapshotListener(options, limitAccumulator.listener());

    // Verify `limit` query still works.
    snapshot = limitAccumulator.await();
    assertEquals(
        asList(map("k", "a", "sort", 0L), map("k", "b", "sort", 1L)),
        querySnapshotToValues(snapshot));
    assertTrue(snapshot.getMetadata().isFromCache());

    // Do a local mutation
    waitFor(collection.add(map("k", "d", "sort", -1)));

    // Verify both query get expected result.
    snapshot = limitAccumulator.await();
    assertEquals(
        asList(map("k", "d", "sort", -1L), map("k", "a", "sort", 0L)),
        querySnapshotToValues(snapshot));
    assertTrue(snapshot.getMetadata().hasPendingWrites());
    snapshot = limitToLastAccumulator.await();
    assertEquals(
        asList(map("k", "a", "sort", 0L), map("k", "d", "sort", -1L)),
        querySnapshotToValues(snapshot));
    assertTrue(snapshot.getMetadata().hasPendingWrites());

    // Un-listen to limitToLast, update a doc, then re-listen to limitToLast
    limitToLastRegistration.remove();
    waitFor(collection.document("a").update(map("k", "a", "sort", -2)));
    limitToLast.addSnapshotListener(options, limitToLastAccumulator.listener());

    // Verify both query get expected result.
    snapshot = limitAccumulator.await();
    assertEquals(
        asList(map("k", "a", "sort", -2L), map("k", "d", "sort", -1L)),
        querySnapshotToValues(snapshot));
    assertTrue(snapshot.getMetadata().hasPendingWrites());
    snapshot = limitToLastAccumulator.await();
    assertEquals(
        asList(map("k", "d", "sort", -1L), map("k", "a", "sort", -2L)),
        querySnapshotToValues(snapshot));
    // We listened to LimitToLast query after the doc update.
    assertFalse(snapshot.getMetadata().hasPendingWrites());

    limitRegistration.remove();
    limitToLastRegistration.remove();
  }

  @Test
  public void canListenToDefaultSourceFirstAndThenCache() {
    CollectionReference collection =
        testCollectionWithDocs(map("a", map("k", "a", "sort", 0), "b", map("k", "b", "sort", 1)));
    Query query =
        collection.whereGreaterThanOrEqualTo("sort", 1).orderBy("sort", Direction.ASCENDING);

    // Listen to the query with default options, which will also populates the cache
    EventAccumulator<QuerySnapshot> defaultAccumulator = new EventAccumulator<>();
    ListenerRegistration defaultRegistration =
        query.addSnapshotListener(defaultAccumulator.listener());

    QuerySnapshot snapshot = defaultAccumulator.await();
    assertEquals(asList(map("k", "b", "sort", 1L)), querySnapshotToValues(snapshot));
    assertFalse(snapshot.getMetadata().isFromCache());

    // Listen to the same query from cache
    EventAccumulator<QuerySnapshot> cacheAccumulator = new EventAccumulator<>();
    SnapshotListenOptions options = optionSourceFromCache();
    ListenerRegistration cacheRegistration =
        query.addSnapshotListener(options, cacheAccumulator.listener());

    snapshot = cacheAccumulator.await();
    assertEquals(asList(map("k", "b", "sort", 1L)), querySnapshotToValues(snapshot));
    // The metadata is sync with server due to the default listener
    assertFalse(snapshot.getMetadata().isFromCache());

    defaultAccumulator.assertNoAdditionalEvents();
    cacheAccumulator.assertNoAdditionalEvents();

    defaultRegistration.remove();
    cacheRegistration.remove();
  }

  @Test
  public void canListenToCacheSourceFirstAndThenDefault() {
    CollectionReference collection =
        testCollectionWithDocs(map("a", map("k", "a", "sort", 0), "b", map("k", "b", "sort", 1)));
    Query query = collection.whereNotEqualTo("sort", 0).orderBy("sort", Direction.ASCENDING);

    // Listen to the cache
    EventAccumulator<QuerySnapshot> cacheAccumulator = new EventAccumulator<>();
    SnapshotListenOptions options = optionSourceFromCacheAndIncludeMetadataChanges();
    ListenerRegistration cacheRegistration =
        query.addSnapshotListener(options, cacheAccumulator.listener());

    QuerySnapshot snapshot = cacheAccumulator.await();
    // Cache is empty
    assertEquals(asList(), querySnapshotToValues(snapshot));
    assertTrue(snapshot.getMetadata().isFromCache());

    // Listen to the same query from server
    EventAccumulator<QuerySnapshot> defaultAccumulator = new EventAccumulator<>();
    ListenerRegistration defaultRegistration =
        query.addSnapshotListener(MetadataChanges.INCLUDE, defaultAccumulator.listener());

    snapshot = defaultAccumulator.await();
    assertEquals(asList(map("k", "b", "sort", 1L)), querySnapshotToValues(snapshot));
    assertFalse(snapshot.getMetadata().isFromCache());

    // Default listener updates the cache, which triggers cache listener to raise snapshot.
    snapshot = cacheAccumulator.await();
    assertEquals(asList(map("k", "b", "sort", 1L)), querySnapshotToValues(snapshot));
    // The metadata is sync with server due to the default listener
    assertFalse(snapshot.getMetadata().isFromCache());

    defaultAccumulator.assertNoAdditionalEvents();
    cacheAccumulator.assertNoAdditionalEvents();

    defaultRegistration.remove();
    cacheRegistration.remove();
  }

  @Test
  public void willNotGetMetadataOnlyUpdatesIfListeningToCacheOnly() {
    CollectionReference collection =
        testCollectionWithDocs(map("a", map("k", "a", "sort", 0), "b", map("k", "b", "sort", 1)));
    Query query = collection.whereNotEqualTo("sort", 0).orderBy("sort", Direction.ASCENDING);

    waitFor(collection.get()); // Populate the cache.

    EventAccumulator<QuerySnapshot> accumulator = new EventAccumulator<>();
    SnapshotListenOptions options = optionSourceFromCacheAndIncludeMetadataChanges();
    ListenerRegistration listener = query.addSnapshotListener(options, accumulator.listener());

    QuerySnapshot snapshot = accumulator.await();
    assertEquals(asList(map("k", "b", "sort", 1L)), querySnapshotToValues(snapshot));
    assertTrue(snapshot.getMetadata().isFromCache());

    waitFor(collection.add(map("k", "c", "sort", 2)));

    snapshot = accumulator.await();
    assertEquals(
        asList(map("k", "b", "sort", 1L), map("k", "c", "sort", 2L)),
        querySnapshotToValues(snapshot));
    assertTrue(snapshot.getMetadata().isFromCache());
    assertTrue(snapshot.getMetadata().hasPendingWrites());

    // As we are not listening to server, the listener will not get notified
    // when local mutation is acknowledged by server.
    accumulator.assertNoAdditionalEvents();
    listener.remove();
  }

  @Test
  public void willHaveSyncedMetadataUpdatesWhenListeningToBothCacheAndDefaultSource() {
    CollectionReference collection =
        testCollectionWithDocs(map("a", map("k", "a", "sort", 0), "b", map("k", "b", "sort", 1)));
    Query query = collection.whereNotEqualTo("sort", 0).orderBy("sort", Direction.ASCENDING);

    waitFor(collection.get()); // Populate the cache.

    // Listen to the cache
    EventAccumulator<QuerySnapshot> cacheAccumulator = new EventAccumulator<>();
    SnapshotListenOptions options = optionSourceFromCacheAndIncludeMetadataChanges();

    ListenerRegistration cacheRegistration =
        query.addSnapshotListener(options, cacheAccumulator.listener());

    QuerySnapshot snapshot = cacheAccumulator.await();
    List<Map<String, Object>> expected = asList(map("k", "b", "sort", 1L));
    assertEquals(expected, querySnapshotToValues(snapshot));
    assertTrue(snapshot.getMetadata().isFromCache());

    // Listen to the same query from server
    EventAccumulator<QuerySnapshot> defaultAccumulator = new EventAccumulator<>();
    ListenerRegistration defaultRegistration =
        query.addSnapshotListener(MetadataChanges.INCLUDE, defaultAccumulator.listener());

    // First snapshot will be raised from cache.
    snapshot = defaultAccumulator.await();
    assertEquals(expected, querySnapshotToValues(snapshot));
    assertTrue(snapshot.getMetadata().isFromCache());
    // Second snapshot will be raised from server result
    snapshot = defaultAccumulator.await();
    assertEquals(expected, querySnapshotToValues(snapshot));
    assertFalse(snapshot.getMetadata().isFromCache());

    // As listening to metadata changes, the cache listener also gets triggered and synced
    // with default listener.
    snapshot = cacheAccumulator.await();
    assertEquals(expected, querySnapshotToValues(snapshot));
    assertFalse(snapshot.getMetadata().isFromCache());

    waitFor(collection.add(map("k", "c", "sort", 2)));

    // snapshot gets triggered by local mutation
    snapshot = defaultAccumulator.await();
    expected = asList(map("k", "b", "sort", 1L), map("k", "c", "sort", 2L));
    assertEquals(expected, querySnapshotToValues(snapshot));
    assertFalse(snapshot.getMetadata().isFromCache());
    assertTrue(snapshot.getMetadata().hasPendingWrites());
    snapshot = cacheAccumulator.await();
    assertEquals(expected, querySnapshotToValues(snapshot));
    assertFalse(snapshot.getMetadata().isFromCache());
    assertTrue(snapshot.getMetadata().hasPendingWrites());

    // Local mutation gets acknowledged by the server
    snapshot = defaultAccumulator.await();
    assertFalse(snapshot.getMetadata().isFromCache());
    assertFalse(snapshot.getMetadata().hasPendingWrites());
    snapshot = cacheAccumulator.await();
    assertFalse(snapshot.getMetadata().isFromCache());
    assertFalse(snapshot.getMetadata().hasPendingWrites());

    defaultAccumulator.assertNoAdditionalEvents();
    cacheAccumulator.assertNoAdditionalEvents();

    defaultRegistration.remove();
    cacheRegistration.remove();
  }

  @Test
  public void canUnlistenToDefaultSourceWhileStillListeningToCache() {
    CollectionReference collection =
        testCollectionWithDocs(map("a", map("k", "a", "sort", 0), "b", map("k", "b", "sort", 1)));
    Query query = collection.whereNotEqualTo("sort", 0).orderBy("sort", Direction.ASCENDING);

    // Listen to the query with both source options
    EventAccumulator<QuerySnapshot> defaultAccumulator = new EventAccumulator<>();
    ListenerRegistration defaultRegistration =
        query.addSnapshotListener(defaultAccumulator.listener());
    defaultAccumulator.await();

    EventAccumulator<QuerySnapshot> cacheAccumulator = new EventAccumulator<>();
    SnapshotListenOptions options = optionSourceFromCache();
    ListenerRegistration cacheRegistration =
        query.addSnapshotListener(options, cacheAccumulator.listener());
    cacheAccumulator.await();

    // Un-listen to the default listener.
    defaultRegistration.remove();
    defaultAccumulator.assertNoAdditionalEvents();

    // Add a document and verify listener to cache works as expected
    waitFor(collection.add(map("k", "c", "sort", -1)));

    QuerySnapshot snapshot = cacheAccumulator.await();
    assertEquals(
        asList(map("k", "c", "sort", -1L), map("k", "b", "sort", 1L)),
        querySnapshotToValues(snapshot));

    cacheAccumulator.assertNoAdditionalEvents();
    cacheRegistration.remove();
  }

  @Test
  public void canUnlistenToCachetSourceWhileStillListeningToServer() {
    CollectionReference collection =
        testCollectionWithDocs(map("a", map("k", "a", "sort", 0), "b", map("k", "b", "sort", 1)));
    Query query = collection.whereNotEqualTo("sort", 0).orderBy("sort", Direction.ASCENDING);

    // Listen to the query with both source options
    EventAccumulator<QuerySnapshot> defaultAccumulator = new EventAccumulator<>();
    ListenerRegistration defaultRegistration =
        query.addSnapshotListener(defaultAccumulator.listener());
    defaultAccumulator.await();

    EventAccumulator<QuerySnapshot> cacheAccumulator = new EventAccumulator<>();
    SnapshotListenOptions options = optionSourceFromCache();
    ListenerRegistration cacheRegistration =
        query.addSnapshotListener(options, cacheAccumulator.listener());
    cacheAccumulator.await();

    // Un-listen to cache.
    cacheRegistration.remove();
    cacheAccumulator.assertNoAdditionalEvents();

    // Add a document and verify listener to server works as expected.
    waitFor(collection.add(map("k", "c", "sort", -1)));

    QuerySnapshot snapshot = defaultAccumulator.await();
    assertEquals(
        asList(map("k", "c", "sort", -1L), map("k", "b", "sort", 1L)),
        querySnapshotToValues(snapshot));

    defaultAccumulator.assertNoAdditionalEvents();
    defaultRegistration.remove();
  }

  @Test
  public void canListenUnlistenRelistentoSameQueryWithDifferentSourceOptions() {
    CollectionReference collection =
        testCollectionWithDocs(map("a", map("k", "a", "sort", 0), "b", map("k", "b", "sort", 1)));
    Query query = collection.whereGreaterThan("sort", 0).orderBy("sort", Direction.ASCENDING);

    // Listen to the query with default options, which will also populates the cache
    EventAccumulator<QuerySnapshot> defaultAccumulator = new EventAccumulator<>();
    ListenerRegistration defaultRegistration =
        query.addSnapshotListener(defaultAccumulator.listener());

    QuerySnapshot snapshot = defaultAccumulator.await();
    List<Map<String, Object>> expected = asList(map("k", "b", "sort", 1L));
    assertEquals(expected, querySnapshotToValues(snapshot));

    // Listen to the same query from cache
    EventAccumulator<QuerySnapshot> cacheAccumulator = new EventAccumulator<>();
    SnapshotListenOptions options = optionSourceFromCache();
    ListenerRegistration cacheRegistration =
        query.addSnapshotListener(options, cacheAccumulator.listener());

    snapshot = cacheAccumulator.await();
    assertEquals(expected, querySnapshotToValues(snapshot));

    // Un-listen to the default listener, add a doc and re-listen.
    defaultRegistration.remove();
    waitFor(collection.add(map("k", "c", "sort", 2)));

    snapshot = cacheAccumulator.await();
    expected = asList(map("k", "b", "sort", 1L), map("k", "c", "sort", 2L));
    assertEquals(expected, querySnapshotToValues(snapshot));

    defaultRegistration = query.addSnapshotListener(defaultAccumulator.listener());
    snapshot = defaultAccumulator.await();
    assertEquals(expected, querySnapshotToValues(snapshot));

    // Un-listen to cache, update a doc, then re-listen to cache.
    cacheRegistration.remove();
    waitFor(collection.document("b").update(map("k", "b", "sort", 3)));

    snapshot = defaultAccumulator.await();
    expected = asList(map("k", "c", "sort", 2L), map("k", "b", "sort", 3L));
    assertEquals(expected, querySnapshotToValues(snapshot));

    cacheRegistration = query.addSnapshotListener(options, cacheAccumulator.listener());
    snapshot = cacheAccumulator.await();
    assertEquals(expected, querySnapshotToValues(snapshot));

    defaultAccumulator.assertNoAdditionalEvents();
    cacheAccumulator.assertNoAdditionalEvents();

    defaultRegistration.remove();
    cacheRegistration.remove();
  }

  @Test
  public void canListenToCompositeIndexQueriesFromCache() {
    CollectionReference collection =
        testCollectionWithDocs(map("a", map("k", "a", "sort", 0), "b", map("k", "b", "sort", 1)));
    Query query = collection.whereLessThanOrEqualTo("k", "a").whereGreaterThanOrEqualTo("sort", 0);

    waitFor(collection.get()); // Populate the cache.

    EventAccumulator<QuerySnapshot> accumulator = new EventAccumulator<>();
    SnapshotListenOptions options = optionSourceFromCache();
    ListenerRegistration listener = query.addSnapshotListener(options, accumulator.listener());

    QuerySnapshot snapshot = accumulator.await();
    assertEquals(asList(map("k", "a", "sort", 0L)), querySnapshotToValues(snapshot));
    assertTrue(snapshot.getMetadata().isFromCache());

    accumulator.assertNoAdditionalEvents();
    listener.remove();
  }

  @Test
  public void canRaiseInitialSnapshotFromCacheEvenIfItIsEmpty() {
    CollectionReference collection = testCollection();

    QuerySnapshot snapshot = waitFor(collection.get()); // Populate the cache
    assertEquals(asList(), querySnapshotToValues(snapshot)); // Precondition check.

    EventAccumulator<QuerySnapshot> accumulator = new EventAccumulator<>();
    SnapshotListenOptions options = optionSourceFromCache();
    ListenerRegistration listener = collection.addSnapshotListener(options, accumulator.listener());

    snapshot = accumulator.await();
    assertEquals(asList(), querySnapshotToValues(snapshot));
    assertTrue(snapshot.getMetadata().isFromCache());

    accumulator.assertNoAdditionalEvents();
    listener.remove();
  }

  @Test
  public void willNotBeTriggeredByTransactionsWhileListeningToCache() {
    CollectionReference collection = testCollection();

    EventAccumulator<QuerySnapshot> accumulator = new EventAccumulator<>();
    SnapshotListenOptions options = optionSourceFromCacheAndIncludeMetadataChanges();
    ListenerRegistration listener = collection.addSnapshotListener(options, accumulator.listener());

    QuerySnapshot snapshot = accumulator.await();
    assertEquals(asList(), querySnapshotToValues(snapshot));

    DocumentReference docRef = collection.document();
    // Use a transaction to perform a write without triggering any local events.
    docRef
        .getFirestore()
        .runTransaction(
            transaction -> {
              transaction.set(docRef, map("k", "a"));
              return null;
            });

    // There should be no events raised
    accumulator.assertNoAdditionalEvents();
    listener.remove();
  }

  @Test
  public void shareServerSideUpdatesWhenListeningToBothCacheAndDefault() {
    CollectionReference collection =
        testCollectionWithDocs(map("a", map("k", "a", "sort", 0), "b", map("k", "b", "sort", 1)));
    Query query = collection.whereGreaterThan("sort", 0).orderBy("sort", Direction.ASCENDING);

    // Listen to the query with default options, which will also populates the cache
    EventAccumulator<QuerySnapshot> defaultAccumulator = new EventAccumulator<>();
    ListenerRegistration defaultRegistration =
        query.addSnapshotListener(defaultAccumulator.listener());
    QuerySnapshot snapshot = defaultAccumulator.await();
    List<Map<String, Object>> expected = asList(map("k", "b", "sort", 1L));
    assertEquals(expected, querySnapshotToValues(snapshot));

    // Listen to the same query from cache
    EventAccumulator<QuerySnapshot> cacheAccumulator = new EventAccumulator<>();
    SnapshotListenOptions options = optionSourceFromCache();
    ListenerRegistration cacheRegistration =
        query.addSnapshotListener(options, cacheAccumulator.listener());
    snapshot = cacheAccumulator.await();
    assertEquals(expected, querySnapshotToValues(snapshot));

    // Use a transaction to mock server side updates
    DocumentReference docRef = collection.document();
    docRef
        .getFirestore()
        .runTransaction(
            transaction -> {
              transaction.set(docRef, map("k", "c", "sort", 2));
              return null;
            });

    // Default listener receives the server update
    snapshot = defaultAccumulator.await();
    expected = asList(map("k", "b", "sort", 1L), map("k", "c", "sort", 2L));
    assertEquals(expected, querySnapshotToValues(snapshot));
    assertFalse(snapshot.getMetadata().isFromCache());

    // Cache listener raises snapshot as well
    snapshot = cacheAccumulator.await();
    assertEquals(expected, querySnapshotToValues(snapshot));
    assertFalse(snapshot.getMetadata().isFromCache());

    defaultAccumulator.assertNoAdditionalEvents();
    cacheAccumulator.assertNoAdditionalEvents();

    defaultRegistration.remove();
    cacheRegistration.remove();
  }
}
