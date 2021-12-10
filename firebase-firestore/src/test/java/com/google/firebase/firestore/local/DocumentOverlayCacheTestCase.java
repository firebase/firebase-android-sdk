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
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

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
  @Rule public TestName name = new TestName();

  private Persistence persistence;
  private DocumentOverlayCache overlays;
  private static boolean overlayEnabled = false;

  @Before
  public void setUp() {
    persistence = getPersistence();
    overlays = persistence.getDocumentOverlay(User.UNAUTHENTICATED);
  }

  @After
  public void tearDown() {
    persistence.shutdown();
  }

  abstract Persistence getPersistence();

  void saveOverlay(int largestBatch, DocumentKey key, Mutation overlay) {
    Map<DocumentKey, Mutation> data = new HashMap<>();
    data.put(key, overlay);
    overlays.saveOverlays(largestBatch, data);
  }

  @Test
  public void testReturnsNullWhenOverlayIsNotFound() {
    assertNull(overlays.getOverlay(key("coll/doc1")));
  }

  @Test
  public void testCanReadSavedOverlay() {
    Mutation m = patchMutation("coll/doc1", map("foo", "bar"));
    saveOverlay(2, key("coll/doc1"), m);

    assertEquals(m, overlays.getOverlay(key("coll/doc1")).getMutation());
  }

  @Test
  public void testCanReadSavedOverlays() {
    Mutation m1 = patchMutation("coll/doc1", map("foo", "bar"));
    Mutation m2 = setMutation("coll/doc2", map("foo", "bar"));
    Mutation m3 = deleteMutation("coll/doc3");
    Map<DocumentKey, Mutation> m = new HashMap<>();
    m.put(key("coll/doc1"), m1);
    m.put(key("coll/doc2"), m2);
    m.put(key("coll/doc3"), m3);
    overlays.saveOverlays(3, m);

    assertEquals(m1, overlays.getOverlay(key("coll/doc1")).getMutation());
    assertEquals(m2, overlays.getOverlay(key("coll/doc2")).getMutation());
    assertEquals(m3, overlays.getOverlay(key("coll/doc3")).getMutation());
  }

  @Test
  public void testSavingOverlayOverwrites() {
    Mutation m1 = patchMutation("coll/doc1", map("foo", "bar"));
    Mutation m2 = setMutation("coll/doc1", map("foo", "set", "bar", 42));
    saveOverlay(2, key("coll/doc1"), m1);
    saveOverlay(2, key("coll/doc1"), m2);

    assertEquals(m2, overlays.getOverlay(key("coll/doc1")).getMutation());
  }

  @Test
  public void testDeleteRepeatedlyWorks() {
    Mutation m = patchMutation("coll/doc1", map("foo", "bar"));
    saveOverlay(2, key("coll/doc1"), m);

    overlays.removeOverlaysForBatchId(2);
    assertNull(overlays.getOverlay(key("coll/doc1")));

    // Repeat
    overlays.removeOverlaysForBatchId(2);
    assertNull(overlays.getOverlay(key("coll/doc1")));
  }

  @Test
  public void testGetAllOverlaysForCollection() {
    Mutation m1 = patchMutation("coll/doc1", map("foo", "bar"));
    Mutation m2 = setMutation("coll/doc2", map("foo", "bar"));
    Mutation m3 = deleteMutation("coll/doc3");
    // m4 and m5 are not under "coll"
    Mutation m4 = setMutation("coll/doc1/sub/sub_doc", map("foo", "bar"));
    Mutation m5 = setMutation("other/doc1", map("foo", "bar"));
    Map<DocumentKey, Mutation> m = new HashMap<>();
    m.put(key("coll/doc1"), m1);
    m.put(key("coll/doc2"), m2);
    m.put(key("coll/doc3"), m3);
    m.put(key("coll/doc1/sub/sub_doc"), m4);
    m.put(key("other/doc1"), m5);
    overlays.saveOverlays(3, m);

    Map<DocumentKey, Overlay> expected = new HashMap<>();
    expected.put(key("coll/doc1"), Overlay.create(3, m1));
    expected.put(key("coll/doc2"), Overlay.create(3, m2));
    expected.put(key("coll/doc3"), Overlay.create(3, m3));

    verifyOverlayMaps(expected, overlays.getOverlays(path("coll"), -1));
  }

  @Test
  public void testGetAllOverlaysSinceBatchId() {
    Mutation m1 = patchMutation("coll/doc1", map("foo", "bar"));
    Mutation m2 = setMutation("coll/doc2", map("foo", "bar"));
    Map<DocumentKey, Mutation> m = new HashMap<>();
    m.put(key("coll/doc1"), m1);
    m.put(key("coll/doc2"), m2);
    overlays.saveOverlays(2, m);

    Mutation m3 = deleteMutation("coll/doc3");
    m = new HashMap<>();
    m.put(key("coll/doc3"), m3);
    overlays.saveOverlays(3, m);

    Mutation m4 = deleteMutation("coll/doc4");
    m = new HashMap<>();
    m.put(key("coll/doc4"), m4);
    overlays.saveOverlays(4, m);

    Map<DocumentKey, Overlay> expected = new HashMap<>();
    expected.put(key("coll/doc3"), Overlay.create(3, m3));
    expected.put(key("coll/doc4"), Overlay.create(4, m4));
    verifyOverlayMaps(expected, overlays.getOverlays(path("coll"), 2));
  }

  private void verifyOverlayMaps(
      Map<DocumentKey, Overlay> expected, Map<DocumentKey, Overlay> actual) {
    assertEquals(expected.keySet(), actual.keySet());
    for (Map.Entry<DocumentKey, Overlay> entry : expected.entrySet()) {
      Overlay expectedOverlay = entry.getValue();
      Overlay actualOverlay = actual.get(entry.getKey());
      assertEquals(expectedOverlay.getLargestBatchId(), actualOverlay.getLargestBatchId());
      assertEquals(expectedOverlay.getMutation(), actualOverlay.getMutation());
    }
  }
}
