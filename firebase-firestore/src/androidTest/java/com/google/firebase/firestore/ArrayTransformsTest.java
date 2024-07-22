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

import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testDocument;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.firestore.testutil.EventAccumulator;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Note: Transforms are tested pretty thoroughly via ServerTimestampTest (via set, update,
 * transactions, nested in documents, multiple transforms together, etc.) and so these tests mostly
 * focus on the array transform semantics.
 */
@RunWith(AndroidJUnit4.class)
public class ArrayTransformsTest {
  // A document reference to read and write to.
  private DocumentReference docRef;

  // Accumulator used to capture events during the test.
  private EventAccumulator<DocumentSnapshot> accumulator;

  // Listener registration for a listener maintained during the course of the test.
  private ListenerRegistration listenerRegistration;

  @Before
  public void setUp() {
    docRef = testDocument();
    accumulator = new EventAccumulator<>();
    listenerRegistration =
        docRef.addSnapshotListener(MetadataChanges.INCLUDE, accumulator.listener());

    // Wait for initial null snapshot to avoid potential races.
    DocumentSnapshot initialSnapshot = accumulator.await();
    assertFalse(initialSnapshot.exists());
  }

  @After
  public void tearDown() {
    listenerRegistration.remove();
    IntegrationTestUtil.tearDown();
  }

  /** Writes some initialData and consumes the events generated. */
  private void writeInitialData(Map<String, Object> initialData) {
    waitFor(docRef.set(initialData));
    expectLocalAndRemoteEvent(initialData);
  }

  private void expectLocalAndRemoteEvent(Map<String, Object> expectedData) {
    DocumentSnapshot snap = accumulator.awaitLocalEvent();
    assertEquals(expectedData, snap.getData());
    snap = accumulator.awaitRemoteEvent();
    assertEquals(expectedData, snap.getData());
  }

  @Test
  public void createDocumentWithArrayUnion() {
    waitFor(docRef.set(map("array", FieldValue.arrayUnion(1L, 2L))));
    expectLocalAndRemoteEvent(map("array", asList(1L, 2L)));
  }

  @Test
  public void appendToArrayViaUpdate() {
    writeInitialData(map("array", asList(1L, 3L)));
    waitFor(docRef.update(map("array", FieldValue.arrayUnion(2L, 1L, 4L))));
    expectLocalAndRemoteEvent(map("array", asList(1L, 3L, 2L, 4L)));
  }

  @Test
  public void appendToArrayViaSetWithMerge() {
    writeInitialData(map("array", asList(1L, 3L)));
    waitFor(docRef.set(map("array", FieldValue.arrayUnion(2L, 1L, 4L)), SetOptions.merge()));
    expectLocalAndRemoteEvent(map("array", asList(1L, 3L, 2L, 4L)));
  }

  @Test
  public void appendObjectToArrayViaUpdate() {
    writeInitialData(map("array", asList(map("a", "hi"))));
    waitFor(docRef.update(map("array", FieldValue.arrayUnion(map("a", "hi"), map("a", "bye")))));
    expectLocalAndRemoteEvent(map("array", asList(map("a", "hi"), map("a", "bye"))));
  }

  @Test
  public void removeFromArrayViaUpdate() {
    writeInitialData(map("array", asList(1L, 3L, 1L, 3L)));
    waitFor(docRef.update(map("array", FieldValue.arrayRemove(1L, 4L))));
    expectLocalAndRemoteEvent(map("array", asList(3L, 3L)));
  }

  @Test
  public void removeFromArrayViaSetMerge() {
    writeInitialData(map("array", asList(1L, 3L, 1L, 3L)));
    waitFor(docRef.set(map("array", FieldValue.arrayRemove(1L, 4L)), SetOptions.merge()));
    expectLocalAndRemoteEvent(map("array", asList(3L, 3L)));
  }

  @Test
  public void removeObjectFromArrayViaUpdate() {
    writeInitialData(map("array", asList(map("a", "hi"), map("a", "bye"))));
    waitFor(docRef.update(map("array", FieldValue.arrayRemove(map("a", "hi")))));
    expectLocalAndRemoteEvent(map("array", asList(map("a", "bye"))));
  }
}
