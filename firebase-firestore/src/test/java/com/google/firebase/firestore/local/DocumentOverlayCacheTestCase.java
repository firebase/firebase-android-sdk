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

import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.Overlay;
import com.google.firebase.firestore.testutil.TestUtil;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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

  void verifyOverlayContains(Map<DocumentKey, Overlay> overlays, String... keys) {
    Set<DocumentKey> expected = Arrays.stream(keys).map(TestUtil::key).collect(Collectors.toSet());
    assertThat(overlays.keySet()).containsExactlyElementsIn(expected);
  }
}
