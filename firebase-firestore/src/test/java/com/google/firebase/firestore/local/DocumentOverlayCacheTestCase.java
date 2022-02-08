// Copyright 2021 Google LLC
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.firestore.testutil.TestUtil.deleteMutation;
import static com.google.firebase.firestore.testutil.TestUtil.key;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.patchMutation;
import static com.google.firebase.firestore.testutil.TestUtil.path;
import static com.google.firebase.firestore.testutil.TestUtil.setMutation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.Overlay;
import com.google.firebase.firestore.testutil.TestUtil;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * These are tests for any implementation of the DocumentOverlayCache interface.
 *
 * <p>To test a specific implementation of DocumentOverlayCache:
 *
 * <ol>
 *   <li>Subclass DocumentOverlayCacheTestCase.
 *   <li>Override {@link #getPersistence}, creating a new implementation of Persistence.
 * </ol>
 */
public abstract class DocumentOverlayCacheTestCase {
  private Persistence persistence;
  private DocumentOverlayCache cache;

  @Before
  public void setUp() {
    persistence = getPersistence();
    cache = persistence.getDocumentOverlay(User.UNAUTHENTICATED);
  }

  @After
  public void tearDown() {
    persistence.shutdown();
  }

  abstract Persistence getPersistence();

  void saveOverlays(int largestBatch, Mutation... mutations) {
    Map<DocumentKey, Mutation> data = new HashMap<>();
    for (Mutation mutation : mutations) {
      data.put(mutation.getKey(), mutation);
    }
    cache.saveOverlays(largestBatch, data);
  }

  void saveOverlays(int largestBatch, String... keys) {
    Map<DocumentKey, Mutation> data = new HashMap<>();
    for (String key : keys) {
      data.put(key(key), setMutation(key, map()));
    }
    cache.saveOverlays(largestBatch, data);
  }

  @Test
  public void testReturnsNullWhenOverlayIsNotFound() {
    assertNull(cache.getOverlay(key("coll/doc1")));
  }

  @Test
  public void testSkipsNonExistingOverlayInBatchLookup() {
    Map<DocumentKey, Overlay> overlays =
        cache.getOverlays(new TreeSet<>(Collections.singleton(key("coll/doc1"))));
    assertTrue(overlays.isEmpty());
  }

  @Test
  public void testCanReadSavedOverlay() {
    Mutation m = patchMutation("coll/doc1", map("foo", "bar"));
    saveOverlays(2, m);

    assertEquals(m, cache.getOverlay(key("coll/doc1")).getMutation());
  }

  @Test
  public void testCanReadSavedOverlays() {
    Mutation m1 = patchMutation("coll/doc1", map("foo", "bar"));
    Mutation m2 = setMutation("coll/doc2", map("foo", "bar"));
    Mutation m3 = deleteMutation("coll/doc3");
    saveOverlays(3, m1, m2, m3);

    assertEquals(m1, cache.getOverlay(key("coll/doc1")).getMutation());
    assertEquals(m2, cache.getOverlay(key("coll/doc2")).getMutation());
    assertEquals(m3, cache.getOverlay(key("coll/doc3")).getMutation());
  }

  @Test
  public void testCanReadSavedOverlaysInBatches() {
    Mutation m1 = setMutation("coll1/a", map("a", 1));
    Mutation m2 = setMutation("coll1/b", map("b", 2));
    Mutation m3 = setMutation("coll2/c", map("c", 3));
    saveOverlays(3, m1, m2, m3);

    Map<DocumentKey, Overlay> overlays =
        cache.getOverlays(
            new TreeSet<>(Arrays.asList(key("coll1/a"), key("coll1/b"), key("coll2/c"))));
    assertEquals(m1, overlays.get(key("coll1/a")).getMutation());
    assertEquals(m2, overlays.get(key("coll1/b")).getMutation());
    assertEquals(m3, overlays.get(key("coll2/c")).getMutation());
  }

  @Test
  public void testCanReadUnlimitedNumberOfOverlays() {
    // This test that we can read more than 1000 overlays, which exceeds the bind var limit in
    // SQLite.
    SortedSet<DocumentKey> keys = new TreeSet<>();
    for (int i = 0; i < 1001; ++i) {
      keys.add(key("coll/" + i));
      saveOverlays(i, setMutation("coll/" + i, map()));
    }
    Map<DocumentKey, Overlay> result = cache.getOverlays(keys);
    assertThat(result).hasSize(1001);
  }

  @Test
  public void testSavingOverlayOverwrites() {
    Mutation m1 = patchMutation("coll/doc1", map("foo", "bar"));
    Mutation m2 = setMutation("coll/doc1", map("foo", "set", "bar", 42));
    saveOverlays(2, m1);
    saveOverlays(2, m2);

    assertEquals(m2, cache.getOverlay(key("coll/doc1")).getMutation());
  }

  @Test
  public void testDeleteRepeatedlyWorks() {
    Mutation m = patchMutation("coll/doc1", map("foo", "bar"));
    saveOverlays(2, m);

    cache.removeOverlaysForBatchId(2);
    assertNull(cache.getOverlay(key("coll/doc1")));

    // Repeat
    cache.removeOverlaysForBatchId(2);
    assertNull(cache.getOverlay(key("coll/doc1")));
  }

  @Test
  public void testGetAllOverlaysForCollection() {
    Mutation m1 = patchMutation("coll/doc1", map("foo", "bar"));
    Mutation m2 = setMutation("coll/doc2", map("foo", "bar"));
    Mutation m3 = deleteMutation("coll/doc3");
    // m4 and m5 are not under "coll"
    Mutation m4 = setMutation("coll/doc1/sub/sub_doc", map("foo", "bar"));
    Mutation m5 = setMutation("other/doc1", map("foo", "bar"));
    saveOverlays(3, m1, m2, m3, m4, m5);

    Map<DocumentKey, Overlay> overlays = cache.getOverlays(path("coll"), -1);
    verifyOverlayContains(overlays, "coll/doc1", "coll/doc2", "coll/doc3");
  }

  @Test
  public void testGetAllOverlaysSinceBatchId() {
    saveOverlays(2, "coll/doc1", "coll/doc2");
    saveOverlays(3, "coll/doc3");
    saveOverlays(4, "coll/doc4");

    Map<DocumentKey, Overlay> overlays = cache.getOverlays(path("coll"), 2);
    verifyOverlayContains(overlays, "coll/doc3", "coll/doc4");
  }

  @Test
  public void testGetAllOverlaysFromCollectionGroupEnforcesCollectionGroup() {
    saveOverlays(2, "coll1/doc1", "coll2/doc1");
    saveOverlays(3, "coll1/doc2");
    saveOverlays(4, "coll2/doc2");

    Map<DocumentKey, Overlay> overlays = cache.getOverlays("coll1", -1, 50);
    verifyOverlayContains(overlays, "coll1/doc1", "coll1/doc2");
  }

  @Test
  public void testGetAllOverlaysFromCollectionGroupEnforcesBatchId() {
    saveOverlays(2, "coll/doc1");
    saveOverlays(3, "coll/doc2");

    Map<DocumentKey, Overlay> overlays = cache.getOverlays("coll", 2, 50);
    verifyOverlayContains(overlays, "coll/doc2");
  }

  @Test
  public void testGetAllOverlaysFromCollectionGroupEnforcesLimit() {
    saveOverlays(1, "coll/doc1");
    saveOverlays(2, "coll/doc2");
    saveOverlays(3, "coll/doc3");

    Map<DocumentKey, Overlay> overlays = cache.getOverlays("coll", -1, 2);
    verifyOverlayContains(overlays, "coll/doc1", "coll/doc2");
  }

  @Test
  public void testGetAllOverlaysFromCollectionGroupWithLimitIncludesFullBatches() {
    saveOverlays(1, "coll/doc1");
    saveOverlays(2, "coll/doc2", "coll/doc3");

    Map<DocumentKey, Overlay> overlays = cache.getOverlays("coll", -1, 2);
    verifyOverlayContains(overlays, "coll/doc1", "coll/doc2", "coll/doc3");
  }

  void verifyOverlayContains(Map<DocumentKey, Overlay> overlays, String... keys) {
    Set<DocumentKey> expected = Arrays.stream(keys).map(TestUtil::key).collect(Collectors.toSet());
    assertThat(overlays.keySet()).containsExactlyElementsIn(expected);
  }
}
