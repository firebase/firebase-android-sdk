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
 * These are tests for any implementation of the TargetCache interface.
 *
 * <p>To test a specific implementation of TargetCache:
 *
 * <ol>
 *   <li>Subclass TargetCacheTestCase.
 *   <li>Override {@link #getPersistence}, creating a new implementation of Persistence.
 * </ol>
 */
public abstract class TargetCacheTestCase {

  protected Persistence persistence;
  private TargetCache targetCache;
  private long previousSequenceNumber;

  @Before
  public void setUp() {
    persistence = getPersistence();
    targetCache = persistence.getTargetCache();
    previousSequenceNumber = 1000;
  }

  @After
  public void tearDown() {
    persistence.shutdown();
  }

  abstract Persistence getPersistence();

  @Test
  public void testReadQueryNotInCache() {
    assertNull(targetCache.getTargetData(query("rooms").toTarget()));
  }

  @Test
  public void testSetAndReadAQuery() {
    TargetData targetData = newTargetData(query("rooms"), 1, 1);
    addTargetData(targetData);

    TargetData result = targetCache.getTargetData(query("rooms").toTarget());
    assertNotNull(result);
    assertEquals(targetData.getTarget(), result.getTarget());
    assertEquals(targetData.getTargetId(), result.getTargetId());
    assertEquals(targetData.getResumeToken(), result.getResumeToken());
  }

  @Test
  public void testCanonicalIdCollision() {
    // Type information is currently lost in our canonicalID implementations so this currently an
    // easy way to force colliding canonicalIDs
    Query q1 = query("a").filter(filter("foo", "==", 1));
    Query q2 = query("a").filter(filter("foo", "==", "1"));
    assertEquals(q1.getCanonicalId(), q2.getCanonicalId());

    TargetData data1 = newTargetData(q1, 1, 1);
    addTargetData(data1);

    // Using the other query should not return the query cache entry despite equal canonicalIDs.
    assertNull(targetCache.getTargetData(q2.toTarget()));
    assertEquals(data1, targetCache.getTargetData(q1.toTarget()));

    TargetData data2 = newTargetData(q2, 2, 1);
    addTargetData(data2);
    assertEquals(2, targetCache.getTargetCount());

    assertEquals(data1, targetCache.getTargetData(q1.toTarget()));
    assertEquals(data2, targetCache.getTargetData(q2.toTarget()));

    removeTargetData(data1);
    assertNull(targetCache.getTargetData(q1.toTarget()));
    assertEquals(data2, targetCache.getTargetData(q2.toTarget()));
    assertEquals(1, targetCache.getTargetCount());

    removeTargetData(data2);
    assertNull(targetCache.getTargetData(q1.toTarget()));
    assertNull(targetCache.getTargetData(q2.toTarget()));
    assertEquals(0, targetCache.getTargetCount());
  }

  @Test
  public void testSetQueryToNewValue() {
    TargetData targetData1 = newTargetData(query("rooms"), 1, 1);
    addTargetData(targetData1);

    TargetData targetData2 = newTargetData(query("rooms"), 1, 2);
    addTargetData(targetData2);

    TargetData result = targetCache.getTargetData(query("rooms").toTarget());

    // There's no assertArrayNotEquals
    assertThat(targetData2.getResumeToken(), not(equalTo(targetData1.getResumeToken())));
    assertNotEquals(targetData1.getSnapshotVersion(), targetData2.getSnapshotVersion());
    assertNotNull(result);
    assertEquals(targetData2.getResumeToken(), result.getResumeToken());
    assertEquals(targetData2.getSnapshotVersion(), result.getSnapshotVersion());
  }

  @Test
  public void testRemoveQuery() {
    TargetData targetData1 = newTargetData(query("rooms"), 1, 1);
    addTargetData(targetData1);

    removeTargetData(targetData1);

    TargetData result = targetCache.getTargetData(query("rooms").toTarget());
    assertNull(result);
  }

  @Test
  public void testRemoveNonExistentQuery() {
    // no-op, but make sure it doesn't throw.
    assertDoesNotThrow(() -> targetCache.getTargetData(query("rooms").toTarget()));
  }

  @Test
  public void testRemoveQueryRemovesMatchingKeysToo() {
    TargetData rooms = newTargetData(query("rooms"), 1, 1);
    addTargetData(rooms);

    DocumentKey key1 = key("rooms/foo");
    DocumentKey key2 = key("rooms/bar");
    addMatchingKey(key1, rooms.getTargetId());
    addMatchingKey(key2, rooms.getTargetId());

    assertTrue(targetCache.containsKey(key1));
    assertTrue(targetCache.containsKey(key2));

    removeTargetData(rooms);
    assertFalse(targetCache.containsKey(key1));
    assertFalse(targetCache.containsKey(key2));
  }

  @Test
  public void testAddOrRemoveMatchingKeys() {
    DocumentKey key = key("foo/bar");

    assertFalse(targetCache.containsKey(key));

    addMatchingKey(key, 1);
    assertTrue(targetCache.containsKey(key));

    addMatchingKey(key, 2);
    assertTrue(targetCache.containsKey(key));

    removeMatchingKey(key, 1);
    assertTrue(targetCache.containsKey(key));

    removeMatchingKey(key, 2);
    assertFalse(targetCache.containsKey(key));
  }

  @Test
  public void testRemoveMatchingKeysForTargetId() {
    DocumentKey key1 = key("foo/bar");
    DocumentKey key2 = key("foo/baz");
    DocumentKey key3 = key("foo/blah");

    addMatchingKey(key1, 1);
    addMatchingKey(key2, 1);
    addMatchingKey(key3, 2);
    assertTrue(targetCache.containsKey(key1));
    assertTrue(targetCache.containsKey(key2));
    assertTrue(targetCache.containsKey(key3));

    removeMatchingKeysForTargetId(1);
    assertFalse(targetCache.containsKey(key1));
    assertFalse(targetCache.containsKey(key2));
    assertTrue(targetCache.containsKey(key3));

    removeMatchingKeysForTargetId(2);
    assertFalse(targetCache.containsKey(key1));
    assertFalse(targetCache.containsKey(key2));
    assertFalse(targetCache.containsKey(key3));
  }

  @Test
  public void testMatchingKeysForTargetID() {
    DocumentKey key1 = key("foo/bar");
    DocumentKey key2 = key("foo/baz");
    DocumentKey key3 = key("foo/blah");

    addMatchingKey(key1, 1);
    addMatchingKey(key2, 1);
    addMatchingKey(key3, 2);

    assertSetEquals(asList(key1, key2), targetCache.getMatchingKeysForTargetId(1));
    assertSetEquals(asList(key3), targetCache.getMatchingKeysForTargetId(2));

    addMatchingKey(key1, 2);
    assertSetEquals(asList(key1, key2), targetCache.getMatchingKeysForTargetId(1));
    assertSetEquals(asList(key1, key3), targetCache.getMatchingKeysForTargetId(2));
  }

  @Test
  public void testHighestSequenceNumber() {
    Query rooms = query("rooms");
    Query halls = query("halls");
    Query garages = query("garages");

    TargetData query1 = new TargetData(rooms.toTarget(), 1, 10, QueryPurpose.LISTEN);
    addTargetData(query1);
    TargetData query2 = new TargetData(halls.toTarget(), 2, 20, QueryPurpose.LISTEN);
    addTargetData(query2);
    assertEquals(20, targetCache.getHighestListenSequenceNumber());

    // Sequence numbers never come down
    removeTargetData(query2);
    assertEquals(20, targetCache.getHighestListenSequenceNumber());

    TargetData query3 = new TargetData(garages.toTarget(), 42, 100, QueryPurpose.LISTEN);
    addTargetData(query3);
    assertEquals(100, targetCache.getHighestListenSequenceNumber());

    removeTargetData(query1);
    assertEquals(100, targetCache.getHighestListenSequenceNumber());
    removeTargetData(query3);
    assertEquals(100, targetCache.getHighestListenSequenceNumber());
  }

  @Test
  public void testHighestTargetId() {
    assertEquals(0, targetCache.getHighestTargetId());

    TargetData query1 = new TargetData(query("rooms").toTarget(), 1, 10, QueryPurpose.LISTEN);
    addTargetData(query1);

    DocumentKey key1 = key("rooms/bar");
    DocumentKey key2 = key("rooms/foo");
    addMatchingKey(key1, 1);
    addMatchingKey(key2, 1);

    TargetData query2 = new TargetData(query("halls").toTarget(), 2, 20, QueryPurpose.LISTEN);
    addTargetData(query2);
    DocumentKey key3 = key("halls/foo");
    addMatchingKey(key3, 2);
    assertEquals(2, targetCache.getHighestTargetId());

    // TargetIDs never come down.
    removeTargetData(query2);
    assertEquals(2, targetCache.getHighestTargetId());

    // A query with an empty result set still counts.
    TargetData query3 = new TargetData(query("garages").toTarget(), 42, 100, QueryPurpose.LISTEN);
    addTargetData(query3);
    assertEquals(42, targetCache.getHighestTargetId());

    removeTargetData(query1);
    assertEquals(42, targetCache.getHighestTargetId());

    removeTargetData(query3);
    assertEquals(42, targetCache.getHighestTargetId());

    // Verify that the highestTargetID even survives restarts.
    persistence.shutdown();
    persistence.start();
    targetCache = persistence.getTargetCache();
    assertEquals(42, targetCache.getHighestTargetId());
  }

  @Test
  public void testSnapshotVersion() {
    assertEquals(SnapshotVersion.NONE, targetCache.getLastRemoteSnapshotVersion());

    // Can set the snapshot version.
    targetCache.setLastRemoteSnapshotVersion(version(42));
    assertEquals(version(42), targetCache.getLastRemoteSnapshotVersion());

    // Snapshot version persists restarts.
    persistence.shutdown();
    persistence.start();
    targetCache = persistence.getTargetCache();
    assertEquals(version(42), targetCache.getLastRemoteSnapshotVersion());
  }

  // Helpers

  /**
   * Creates a new TargetData object from the the given parameters, synthesizing a resume token from
   * the snapshot version.
   */
  private TargetData newTargetData(Query query, int targetId, long version) {
    long sequenceNumber = ++previousSequenceNumber;
    return new TargetData(
        query.toTarget(),
        targetId,
        sequenceNumber,
        QueryPurpose.LISTEN,
        version(version),
        version(version),
        resumeToken(version));
  }

  /** Adds the given query data to the targetCache under test, committing immediately. */
  private TargetData addTargetData(TargetData targetData) {
    persistence.runTransaction("addTargetData", () -> targetCache.addTargetData(targetData));
    return targetData;
  }

  /** Removes the given query data from the targetCache under test, committing immediately. */
  private void removeTargetData(TargetData targetData) {
    persistence.runTransaction("removeTargetData", () -> targetCache.removeTargetData(targetData));
  }

  private void addMatchingKey(DocumentKey key, int targetId) {
    final ImmutableSortedSet<DocumentKey> keys = DocumentKey.emptyKeySet().insert(key);

    persistence.runTransaction(
        "addMatchingKeys", () -> targetCache.addMatchingKeys(keys, targetId));
  }

  private void removeMatchingKey(DocumentKey key, int targetId) {
    final ImmutableSortedSet<DocumentKey> keys = DocumentKey.emptyKeySet().insert(key);

    persistence.runTransaction(
        "removeMatchingKeys", () -> targetCache.removeMatchingKeys(keys, targetId));
  }

  private void removeMatchingKeysForTargetId(int targetId) {
    persistence.runTransaction(
        "removeReferencesForTargetId",
        () ->
            targetCache.removeMatchingKeys(
                targetCache.getMatchingKeysForTargetId(targetId), targetId));
  }
}
