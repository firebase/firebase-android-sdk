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

import static com.google.firebase.firestore.testutil.TestUtil.key;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.patchMutation;
import static com.google.firebase.firestore.testutil.TestUtil.setMutation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.firebase.firestore.model.mutation.Mutation;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

/**
 * These are tests for any implementation of the DocumentOverlays interface.
 *
 * <p>To test a specific implementation of DocumentOverlays:
 *
 * <ol>
 *   <li>Subclass DocumentOverlaysTestCase.
 *   <li>Override {@link #getPersistence}, creating a new implementation of Persistence.
 * </ol>
 */
public abstract class DocumentOverlaysTestCase {
  @Rule public TestName name = new TestName();

  private Persistence persistence;
  private DocumentOverlays overlays;
  private boolean overlayEnabled = false;

  @Before
  public void setUp() {
    overlayEnabled = Persistence.OVERLAY_SUPPORT_ENABLED;
    Persistence.OVERLAY_SUPPORT_ENABLED = true;
    persistence = getPersistence();
    overlays = persistence.getDocumentOverlays();
  }

  @After
  public void tearDown() {
    Persistence.OVERLAY_SUPPORT_ENABLED = overlayEnabled;
    persistence.shutdown();
  }

  abstract Persistence getPersistence();

  @Test
  public void testReturnsNullWhenOverlayIsNotFound() {
    assertNull(overlays.getOverlayMutation("Alice", key("coll/doc1")));
  }

  @Test
  public void testCanReadSavedOverlay() {
    Mutation m = patchMutation("coll/doc1", map("foo", "bar"));
    overlays.saveOverlayMutation("Alice", key("coll/doc1"), m);

    assertEquals(m, overlays.getOverlayMutation("Alice", key("coll/doc1")));
  }

  @Test
  public void testCannotReadSavedOverlayByOtherUser() {
    Mutation m = patchMutation("coll/doc1", map("foo", "bar"));
    overlays.saveOverlayMutation("Bob", key("coll/doc1"), m);

    assertNull(overlays.getOverlayMutation("Alice", key("coll/doc1")));

    overlays.saveOverlayMutation("Alice", key("coll/doc1"), m);
    assertEquals(m, overlays.getOverlayMutation("Alice", key("coll/doc1")));
  }

  @Test
  public void testSavingOverlayOverwrites() {
    Mutation m1 = patchMutation("coll/doc1", map("foo", "bar"));
    Mutation m2 = setMutation("coll/doc1", map("foo", "set", "bar", 42));
    overlays.saveOverlayMutation("Bob", key("coll/doc1"), m1);
    overlays.saveOverlayMutation("Bob", key("coll/doc1"), m2);

    assertEquals(m2, overlays.getOverlayMutation("Bob", key("coll/doc1")));
  }

  @Test
  public void testDeleteRepeatedlyWorks() {
    Mutation m = patchMutation("coll/doc1", map("foo", "bar"));
    overlays.saveOverlayMutation("Bob", key("coll/doc1"), m);

    overlays.removeOverlayMutation("Bob", key("coll/doc1"));
    assertNull(overlays.getOverlayMutation("Bob", key("coll/doc1")));

    // Repeat
    overlays.removeOverlayMutation("Bob", key("coll/doc1"));
    assertNull(overlays.getOverlayMutation("Bob", key("coll/doc1")));
  }
}
