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

import static com.google.firebase.firestore.testutil.TestUtil.assertDoesNotThrow;
import static com.google.firebase.firestore.testutil.TestUtil.assertSetEquals;
import static com.google.firebase.firestore.testutil.TestUtil.filter;
import static com.google.firebase.firestore.testutil.TestUtil.key;
import static com.google.firebase.firestore.testutil.TestUtil.query;
import static com.google.firebase.firestore.testutil.TestUtil.resumeToken;
import static com.google.firebase.firestore.testutil.TestUtil.version;
import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.SnapshotVersion;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * These are tests for any implementation of the QueryCache interface.
 *
 * <p>To test a specific implementation of QueryCache:
 *
 * <ol>
 *   <li>Subclass QueryCacheTestCase.
 *   <li>Override {@link #getPersistence}, creating a new implementation of Persistence.
 * </ol>
 */
public abstract class QueryCacheTestCase {

  protected Persistence persistence;
  private QueryCache queryCache;
  private long previousSequenceNumber;

  @Before
  public void setUp() {
    persistence = getPersistence();
    queryCache = persistence.getQueryCache();
    previousSequenceNumber = 1000;
  }

  @After
  public void tearDown() {
    persistence.shutdown();
  }

  abstract Persistence getPersistence();

  @Test
  public void testReadQueryNotInCache() {
    assertNull(queryCache.getQueryData(query("rooms")));
  }

  @Test
  public void testSetAndReadAQuery() {
    QueryData queryData = newQueryData(query("rooms"), 1, 1);
    addQueryData(queryData);

    QueryData result = queryCache.getQueryData(query("rooms"));
    assertNotNull(result);
    assertEquals(queryData.getQuery(), result.getQuery());
    assertEquals(queryData.getTargetId(), result.getTargetId());
    assertEquals(queryData.getResumeToken(), result.getResumeToken());
  }

  @Test
  public void testCanonicalIdCollision() {
    // Type information is currently lost in our canonicalID implementations so this currently an
    // easy way to force colliding canonicalIDs
    Query q1 = query("a").filter(filter("foo", "==", 1));
    Query q2 = query("a").filter(filter("foo", "==", "1"));
    assertEquals(q1.getCanonicalId(), q2.getCanonicalId());

    QueryData data1 = newQueryData(q1, 1, 1);
    addQueryData(data1);

    // Using the other query should not return the query cache entry despite equal canonicalIDs.
    assertNull(queryCache.getQueryData(q2));
    assertEquals(data1, queryCache.getQueryData(q1));

    QueryData data2 = newQueryData(q2, 2, 1);
    addQueryData(data2);
    assertEquals(2, queryCache.getTargetCount());

    assertEquals(data1, queryCache.getQueryData(q1));
    assertEquals(data2, queryCache.getQueryData(q2));

    removeQueryData(data1);
    assertNull(queryCache.getQueryData(q1));
    assertEquals(data2, queryCache.getQueryData(q2));
    assertEquals(1, queryCache.getTargetCount());

    removeQueryData(data2);
    assertNull(queryCache.getQueryData(q1));
    assertNull(queryCache.getQueryData(q2));
    assertEquals(0, queryCache.getTargetCount());
  }

  @Test
  public void testSetQueryToNewValue() {
    QueryData queryData1 = newQueryData(query("rooms"), 1, 1);
    addQueryData(queryData1);

    QueryData queryData2 = newQueryData(query("rooms"), 1, 2);
    addQueryData(queryData2);

    QueryData result = queryCache.getQueryData(query("rooms"));

    // There's no assertArrayNotEquals
    assertThat(queryData2.getResumeToken(), not(equalTo(queryData1.getResumeToken())));
    assertNotEquals(queryData1.getSnapshotVersion(), queryData2.getSnapshotVersion());
    assertNotNull(result);
    assertEquals(queryData2.getResumeToken(), result.getResumeToken());
    assertEquals(queryData2.getSnapshotVersion(), result.getSnapshotVersion());
  }

  @Test
  public void testRemoveQuery() {
    QueryData queryData1 = newQueryData(query("rooms"), 1, 1);
    addQueryData(queryData1);

    removeQueryData(queryData1);

    QueryData result = queryCache.getQueryData(query("rooms"));
    assertNull(result);
  }

  @Test
  public void testRemoveNonExistentQuery() {
    // no-op, but make sure it doesn't throw.
    assertDoesNotThrow(() -> queryCache.getQueryData(query("rooms")));
  }

  @Test
  public void testRemoveQueryRemovesMatchingKeysToo() {
    QueryData rooms = newQueryData(query("rooms"), 1, 1);
    addQueryData(rooms);

    DocumentKey key1 = key("rooms/foo");
    DocumentKey key2 = key("rooms/bar");
    addMatchingKey(key1, rooms.getTargetId());
    addMatchingKey(key2, rooms.getTargetId());

    assertTrue(queryCache.containsKey(key1));
    assertTrue(queryCache.containsKey(key2));

    removeQueryData(rooms);
    assertFalse(queryCache.containsKey(key1));
    assertFalse(queryCache.containsKey(key2));
  }

  @Test
  public void testAddOrRemoveMatchingKeys() {
    DocumentKey key = key("foo/bar");

    assertFalse(queryCache.containsKey(key));

    addMatchingKey(key, 1);
    assertTrue(queryCache.containsKey(key));

    addMatchingKey(key, 2);
    assertTrue(queryCache.containsKey(key));

    removeMatchingKey(key, 1);
    assertTrue(queryCache.containsKey(key));

    removeMatchingKey(key, 2);
    assertFalse(queryCache.containsKey(key));
  }

  @Test
  public void testRemoveMatchingKeysForTargetId() {
    DocumentKey key1 = key("foo/bar");
    DocumentKey key2 = key("foo/baz");
    DocumentKey key3 = key("foo/blah");

    addMatchingKey(key1, 1);
    addMatchingKey(key2, 1);
    addMatchingKey(key3, 2);
    assertTrue(queryCache.containsKey(key1));
    assertTrue(queryCache.containsKey(key2));
    assertTrue(queryCache.containsKey(key3));

    removeMatchingKeysForTargetId(1);
    assertFalse(queryCache.containsKey(key1));
    assertFalse(queryCache.containsKey(key2));
    assertTrue(queryCache.containsKey(key3));

    removeMatchingKeysForTargetId(2);
    assertFalse(queryCache.containsKey(key1));
    assertFalse(queryCache.containsKey(key2));
    assertFalse(queryCache.containsKey(key3));
  }

  @Test
  public void testMatchingKeysForTargetID() {
    DocumentKey key1 = key("foo/bar");
    DocumentKey key2 = key("foo/baz");
    DocumentKey key3 = key("foo/blah");

    addMatchingKey(key1, 1);
    addMatchingKey(key2, 1);
    addMatchingKey(key3, 2);

    assertSetEquals(asList(key1, key2), queryCache.getMatchingKeysForTargetId(1));
    assertSetEquals(asList(key3), queryCache.getMatchingKeysForTargetId(2));

    addMatchingKey(key1, 2);
    assertSetEquals(asList(key1, key2), queryCache.getMatchingKeysForTargetId(1));
    assertSetEquals(asList(key1, key3), queryCache.getMatchingKeysForTargetId(2));
  }

  @Test
  public void testHighestSequenceNumber() {
    Query rooms = query("rooms");
    Query halls = query("halls");
    Query garages = query("garages");

    QueryData query1 = new QueryData(rooms, 1, 10, QueryPurpose.LISTEN);
    addQueryData(query1);
    QueryData query2 = new QueryData(halls, 2, 20, QueryPurpose.LISTEN);
    addQueryData(query2);
    assertEquals(20, queryCache.getHighestListenSequenceNumber());

    // Sequence numbers never come down
    removeQueryData(query2);
    assertEquals(20, queryCache.getHighestListenSequenceNumber());

    QueryData query3 = new QueryData(garages, 42, 100, QueryPurpose.LISTEN);
    addQueryData(query3);
    assertEquals(100, queryCache.getHighestListenSequenceNumber());

    removeQueryData(query1);
    assertEquals(100, queryCache.getHighestListenSequenceNumber());
    removeQueryData(query3);
    assertEquals(100, queryCache.getHighestListenSequenceNumber());
  }

  @Test
  public void testHighestTargetId() {
    assertEquals(0, queryCache.getHighestTargetId());

    QueryData query1 = new QueryData(query("rooms"), 1, 10, QueryPurpose.LISTEN);
    addQueryData(query1);

    DocumentKey key1 = key("rooms/bar");
    DocumentKey key2 = key("rooms/foo");
    addMatchingKey(key1, 1);
    addMatchingKey(key2, 1);

    QueryData query2 = new QueryData(query("halls"), 2, 20, QueryPurpose.LISTEN);
    addQueryData(query2);
    DocumentKey key3 = key("halls/foo");
    addMatchingKey(key3, 2);
    assertEquals(2, queryCache.getHighestTargetId());

    // TargetIDs never come down.
    removeQueryData(query2);
    assertEquals(2, queryCache.getHighestTargetId());

    // A query with an empty result set still counts.
    QueryData query3 = new QueryData(query("garages"), 42, 100, QueryPurpose.LISTEN);
    addQueryData(query3);
    assertEquals(42, queryCache.getHighestTargetId());

    removeQueryData(query1);
    assertEquals(42, queryCache.getHighestTargetId());

    removeQueryData(query3);
    assertEquals(42, queryCache.getHighestTargetId());

    // Verify that the highestTargetID even survives restarts.
    persistence.shutdown();
    persistence.start();
    queryCache = persistence.getQueryCache();
    assertEquals(42, queryCache.getHighestTargetId());
  }

  @Test
  public void testSnapshotVersion() {
    assertEquals(SnapshotVersion.NONE, queryCache.getLastRemoteSnapshotVersion());

    // Can set the snapshot version.
    queryCache.setLastRemoteSnapshotVersion(version(42));
    assertEquals(version(42), queryCache.getLastRemoteSnapshotVersion());

    // Snapshot version persists restarts.
    persistence.shutdown();
    persistence.start();
    queryCache = persistence.getQueryCache();
    assertEquals(version(42), queryCache.getLastRemoteSnapshotVersion());
  }

  // Helpers

  /**
   * Creates a new QueryData object from the the given parameters, synthesizing a resume token from
   * the snapshot version.
   */
  private QueryData newQueryData(Query query, int targetId, long version) {
    long sequenceNumber = ++previousSequenceNumber;
    return new QueryData(
        query,
        targetId,
        sequenceNumber,
        QueryPurpose.LISTEN,
        version(version),
        resumeToken(version));
  }

  /** Adds the given query data to the queryCache under test, committing immediately. */
  private QueryData addQueryData(QueryData queryData) {
    persistence.runTransaction("addQueryData", () -> queryCache.addQueryData(queryData));
    return queryData;
  }

  /** Removes the given query data from the queryCache under test, committing immediately. */
  private void removeQueryData(QueryData queryData) {
    persistence.runTransaction("removeQueryData", () -> queryCache.removeQueryData(queryData));
  }

  private void addMatchingKey(DocumentKey key, int targetId) {
    final ImmutableSortedSet<DocumentKey> keys = DocumentKey.emptyKeySet().insert(key);

    persistence.runTransaction("addMatchingKeys", () -> queryCache.addMatchingKeys(keys, targetId));
  }

  private void removeMatchingKey(DocumentKey key, int targetId) {
    final ImmutableSortedSet<DocumentKey> keys = DocumentKey.emptyKeySet().insert(key);

    persistence.runTransaction(
        "removeMatchingKeys", () -> queryCache.removeMatchingKeys(keys, targetId));
  }

  private void removeMatchingKeysForTargetId(int targetId) {
    persistence.runTransaction(
        "removeReferencesForTargetId",
        () ->
            queryCache.removeMatchingKeys(
                queryCache.getMatchingKeysForTargetId(targetId), targetId));
  }
}
