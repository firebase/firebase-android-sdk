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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.testutil.EventAccumulator;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class NumericTransformsTest {
  private static final double DOUBLE_EPSILON = 0.000001;

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
    accumulator.awaitRemoteEvent();
  }

  private void expectLocalAndRemoteValue(double expectedSum) {
    DocumentSnapshot snap = accumulator.awaitLocalEvent();
    assertEquals(expectedSum, snap.getDouble("sum"), DOUBLE_EPSILON);
    snap = accumulator.awaitRemoteEvent();
    assertEquals(expectedSum, snap.getDouble("sum"), DOUBLE_EPSILON);
  }

  private void expectLocalAndRemoteValue(long expectedSum) {
    DocumentSnapshot snap = accumulator.awaitLocalEvent();
    assertEquals(expectedSum, (long) snap.getLong("sum"));
    snap = accumulator.awaitRemoteEvent();
    assertEquals(expectedSum, (long) snap.getLong("sum"));
  }

  @Test
  public void createDocumentWithIncrement() {
    waitFor(docRef.set(map("sum", FieldValue.increment(1337))));
    expectLocalAndRemoteValue(1337L);
  }

  @Test
  public void mergeOnNonExistingDocumentWithIncrement() {
    waitFor(docRef.set(map("sum", FieldValue.increment(1337)), SetOptions.merge()));
    expectLocalAndRemoteValue(1337L);
  }

  @Test
  public void mergeOnExistingDocumentWithIncrement() {
    waitFor(docRef.set(map("sum", 1)));
    expectLocalAndRemoteValue(1);
    waitFor(docRef.set(map("sum", FieldValue.increment(1337)), SetOptions.merge()));
    expectLocalAndRemoteValue(1338L);
  }

  @Test
  public void integerIncrementWithExistingInteger() {
    writeInitialData(map("sum", 1337L));
    waitFor(docRef.update("sum", FieldValue.increment(1)));
    expectLocalAndRemoteValue(1338L);
  }

  @Test
  public void doubleIncrementWithExistingDouble() {
    writeInitialData(map("sum", 13.37D));
    waitFor(docRef.update("sum", FieldValue.increment(0.1)));
    expectLocalAndRemoteValue(13.47D);
  }

  @Test
  public void integerIncrementWithExistingDouble() {
    writeInitialData(map("sum", 13.37D));
    waitFor(docRef.update("sum", FieldValue.increment(1)));
    expectLocalAndRemoteValue(14.37D);
  }

  @Test
  public void doubleIncrementWithExistingInteger() {
    writeInitialData(map("sum", 1337L));
    waitFor(docRef.update("sum", FieldValue.increment(0.1)));
    expectLocalAndRemoteValue(1337.1D);
  }

  @Test
  public void integerIncrementWithExistingString() {
    writeInitialData(map("sum", "overwrite"));
    waitFor(docRef.update("sum", FieldValue.increment(1337)));
    expectLocalAndRemoteValue(1337L);
  }

  @Test
  public void doubleIncrementWithExistingString() {
    writeInitialData(map("sum", "overwrite"));
    waitFor(docRef.update("sum", FieldValue.increment(13.37)));
    expectLocalAndRemoteValue(13.37D);
  }

  @Test
  public void multipleDoubleIncrements() throws ExecutionException, InterruptedException {
    writeInitialData(map("sum", 0.0D));

    Tasks.await(docRef.getFirestore().disableNetwork());

    docRef.update("sum", FieldValue.increment(0.1D));
    docRef.update("sum", FieldValue.increment(0.01D));
    docRef.update("sum", FieldValue.increment(0.001D));

    DocumentSnapshot snap = accumulator.awaitLocalEvent();
    assertEquals(0.1D, snap.getDouble("sum"), DOUBLE_EPSILON);
    snap = accumulator.awaitLocalEvent();
    assertEquals(0.11D, snap.getDouble("sum"), DOUBLE_EPSILON);
    snap = accumulator.awaitLocalEvent();
    assertEquals(0.111D, snap.getDouble("sum"), DOUBLE_EPSILON);

    Tasks.await(docRef.getFirestore().enableNetwork());

    snap = accumulator.awaitRemoteEvent();
    assertEquals(0.111D, snap.getDouble("sum"), DOUBLE_EPSILON);
  }

  @Test
  public void incrementTwiceInABatch() {
    writeInitialData(map("sum", "overwrite"));
    waitFor(
        docRef
            .getFirestore()
            .batch()
            .update(docRef, "sum", FieldValue.increment(1))
            .update(docRef, "sum", FieldValue.increment(1))
            .commit());
    expectLocalAndRemoteValue(2L);
  }

  @Test
  public void incrementDeleteIncrementInABatch() {
    writeInitialData(map("sum", "overwrite"));
    waitFor(
        docRef
            .getFirestore()
            .batch()
            .update(docRef, "sum", FieldValue.increment(1))
            .update(docRef, "sum", FieldValue.delete())
            .update(docRef, "sum", FieldValue.increment(3))
            .commit());
    expectLocalAndRemoteValue(3L);
  }

  @Test
  public void serverTimestampAndIncrement() throws ExecutionException, InterruptedException {
    // This test stacks two pending transforms (a ServerTimestamp and an Increment transform) and
    // reproduces the setup that was reported in
    // https://github.com/firebase/firebase-android-sdk/issues/491
    // In our original code, a NumericIncrementTransformOperation could cause us to decode the
    // ServerTimestamp as part of a PatchMutation, which triggered an assertion failure.
    Tasks.await(docRef.getFirestore().disableNetwork());

    docRef.set(map("val", FieldValue.serverTimestamp()));
    docRef.set(map("val", FieldValue.increment(1)));

    DocumentSnapshot snap = accumulator.awaitLocalEvent();
    assertNotNull(snap.getTimestamp("val", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE));

    snap = accumulator.awaitLocalEvent();
    assertEquals(1, (long) snap.getLong("val"));

    Tasks.await(docRef.getFirestore().enableNetwork());

    snap = accumulator.awaitRemoteEvent();
    assertEquals(1, (long) snap.getLong("val"));
  }
}
