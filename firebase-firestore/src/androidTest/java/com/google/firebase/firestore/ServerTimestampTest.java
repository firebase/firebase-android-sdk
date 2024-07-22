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

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testDocument;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitForException;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior;
import com.google.firebase.firestore.FirebaseFirestoreException.Code;
import com.google.firebase.firestore.testutil.EventAccumulator;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ServerTimestampTest {

  // Data written in tests via set.
  private static final Map<String, Object> setData =
      map(
          "a",
          42L,
          "when",
          FieldValue.serverTimestamp(),
          "deep",
          map("when", FieldValue.serverTimestamp()));

  // Base and update data used for update tests.
  private static final Map<String, Object> initialData = map("a", 42L);
  private static final Map<String, Object> updateData =
      map("when", FieldValue.serverTimestamp(), "deep", map("when", FieldValue.serverTimestamp()));

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

  // Returns the expected data, with an arbitrary timestamp substituted in.
  private Map<String, Object> expectedDataWithTimestamp(Object timestamp) {
    return map("a", 42L, "when", timestamp, "deep", map("when", timestamp));
  }

  /** Writes initialData and waits for the corresponding snapshot. */
  private void writeInitialData() {
    waitFor(docRef.set(initialData));
    DocumentSnapshot initialDataSnap = accumulator.await();
    assertEquals(initialData, initialDataSnap.getData());
    initialDataSnap = accumulator.await();
    assertEquals(initialData, initialDataSnap.getData());
  }

  /** Verifies a snapshot containing setData but with null for the timestamps. */
  private void verifyTimestampsAreNull(DocumentSnapshot snapshot) {
    assertEquals(expectedDataWithTimestamp(null), snapshot.getData());
  }

  /** Verifies a snapshot containing setData but with resolved server timestamps. */
  private void verifyTimestampsAreResolved(DocumentSnapshot snapshot) {
    assertTrue(snapshot.exists());
    Timestamp when = snapshot.getTimestamp("when");
    assertNotNull(when);
    // Tolerate up to 48*60*60 seconds of clock skew between client and server. This should be more
    // than enough to compensate for timezone issues (even after taking daylight saving into
    // account) and should allow local clocks to deviate from true time slightly and still pass the
    // test.
    int deltaSec = 48 * 60 * 60;
    Timestamp now = Timestamp.now();
    assertTrue(
        "resolved timestamp (" + when + ") should be within " + deltaSec + "s of now (" + now + ")",
        Math.abs(when.getSeconds() - now.getSeconds()) < deltaSec);

    // Validate the rest of the document.
    assertEquals(expectedDataWithTimestamp(when), snapshot.getData());
  }

  /** Verifies a snapshot containing setData but with local estimates for server timestamps. */
  private void verifyTimestampsAreEstimates(DocumentSnapshot snapshot) {
    assertTrue(snapshot.exists());
    Timestamp when = snapshot.getTimestamp("when", ServerTimestampBehavior.ESTIMATE);
    assertNotNull(when);
    assertEquals(
        expectedDataWithTimestamp(when), snapshot.getData(ServerTimestampBehavior.ESTIMATE));
  }

  /**
   * Verifies a snapshot containing setData but using the previous field value for the timestamps.
   */
  private void verifyTimestampsUsePreviousValue(
      DocumentSnapshot current, @Nullable DocumentSnapshot previous) {
    assertTrue(current.exists());
    if (previous != null) {
      Timestamp when = previous.getTimestamp("when");
      assertNotNull(when);
      assertEquals(
          expectedDataWithTimestamp(when), current.getData(ServerTimestampBehavior.PREVIOUS));
    } else {
      assertEquals(
          expectedDataWithTimestamp(null), current.getData(ServerTimestampBehavior.PREVIOUS));
    }
  }

  @Test
  public void testServerTimestampsWorkViaSet() {
    waitFor(docRef.set(setData));
    verifyTimestampsAreNull(accumulator.awaitLocalEvent());
    verifyTimestampsAreResolved(accumulator.awaitRemoteEvent());
  }

  @Test
  public void testServerTimestampsWorkViaUpdate() {
    writeInitialData();
    waitFor(docRef.update(updateData));
    verifyTimestampsAreNull(accumulator.awaitLocalEvent());
    verifyTimestampsAreResolved(accumulator.awaitRemoteEvent());
  }

  @Test
  public void testServerTimestampsCanReturnEstimatedValue() {
    writeInitialData();
    waitFor(docRef.update(updateData));
    verifyTimestampsAreEstimates(accumulator.awaitLocalEvent());
    verifyTimestampsAreResolved(accumulator.awaitRemoteEvent());
  }

  @Test
  public void testServerTimestampsCanReturnPreviousValue() {
    writeInitialData();
    waitFor(docRef.update(updateData));
    verifyTimestampsUsePreviousValue(accumulator.awaitLocalEvent(), null);

    DocumentSnapshot previousSnapshot = accumulator.awaitRemoteEvent();
    verifyTimestampsAreResolved(previousSnapshot);

    // The following update includes an update of the nested map "deep", which updates it to contain
    // a single ServerTimestamp. As such, the update is split into two mutations: One that sets
    // "deep" to an empty map and overwrites the previous ServerTimestamp value and a second
    // transform that writes the new ServerTimestamp. This step in the test verifies that we can
    // still access the old ServerTimestamp value (from `previousSnapshot`) even though it was
    // removed in an intermediate step.
    waitFor(docRef.update(updateData));
    verifyTimestampsUsePreviousValue(accumulator.awaitLocalEvent(), previousSnapshot);
    verifyTimestampsAreResolved(accumulator.awaitRemoteEvent());
  }

  @Test
  public void testServerTimestampsCanReturnPreviousValueOfDifferentType() {
    writeInitialData();
    waitFor(docRef.update("a", FieldValue.serverTimestamp()));

    DocumentSnapshot localSnapshot = accumulator.awaitLocalEvent();
    assertNull(localSnapshot.get("a"));
    assertThat(localSnapshot.get("a", ServerTimestampBehavior.ESTIMATE))
        .isInstanceOf(Timestamp.class);
    assertEquals(42L, localSnapshot.get("a", ServerTimestampBehavior.PREVIOUS));

    DocumentSnapshot remoteSnapshot = accumulator.awaitRemoteEvent();
    assertThat(remoteSnapshot.get("a")).isInstanceOf(Timestamp.class);
    assertThat(remoteSnapshot.get("a", ServerTimestampBehavior.ESTIMATE))
        .isInstanceOf(Timestamp.class);
    assertThat(remoteSnapshot.get("a", ServerTimestampBehavior.PREVIOUS))
        .isInstanceOf(Timestamp.class);
  }

  @Test
  public void testServerTimestampsCanRetainPreviousValueThroughConsecutiveUpdates() {
    writeInitialData();
    waitFor(docRef.getFirestore().getClient().disableNetwork());
    accumulator.awaitRemoteEvent();

    docRef.update("a", FieldValue.serverTimestamp());
    DocumentSnapshot localSnapshot = accumulator.awaitLocalEvent();
    assertEquals(42L, localSnapshot.get("a", ServerTimestampBehavior.PREVIOUS));

    // include b=1 to ensure there's a change resulting in a new snapshot.
    docRef.update("a", FieldValue.serverTimestamp(), "b", 1);
    localSnapshot = accumulator.awaitLocalEvent();
    assertEquals(42L, localSnapshot.get("a", ServerTimestampBehavior.PREVIOUS));

    waitFor(docRef.getFirestore().getClient().enableNetwork());

    DocumentSnapshot remoteSnapshot = accumulator.awaitRemoteEvent();
    assertThat(remoteSnapshot.get("a")).isInstanceOf(Timestamp.class);
  }

  @Test
  public void testServerTimestampsUsesPreviousValueFromLocalMutation() {
    writeInitialData();
    waitFor(docRef.getFirestore().getClient().disableNetwork());
    accumulator.awaitRemoteEvent();

    docRef.update("a", FieldValue.serverTimestamp());
    DocumentSnapshot localSnapshot = accumulator.awaitLocalEvent();
    assertEquals(42L, localSnapshot.get("a", ServerTimestampBehavior.PREVIOUS));

    docRef.update("a", 1337);
    accumulator.awaitLocalEvent();

    docRef.update("a", FieldValue.serverTimestamp());
    localSnapshot = accumulator.awaitLocalEvent();
    assertEquals(1337L, localSnapshot.get("a", ServerTimestampBehavior.PREVIOUS));

    waitFor(docRef.getFirestore().getClient().enableNetwork());

    DocumentSnapshot remoteSnapshot = accumulator.awaitRemoteEvent();
    assertThat(remoteSnapshot.get("a")).isInstanceOf(Timestamp.class);
  }

  @Test
  public void testServerTimestampBehaviorOverloadsOfDocumentSnapshotGet() {
    writeInitialData();
    waitFor(docRef.update(updateData));
    DocumentSnapshot snap = accumulator.awaitLocalEvent();

    // Default behavior should return null timestamp (via any overload).
    assertNull(snap.get("when"));
    assertNull(snap.get(FieldPath.of("when")));
    assertNull(snap.get("when", Timestamp.class));
    assertNull(snap.get(FieldPath.of("when"), Timestamp.class));

    // Estimate should return a Timestamp object (via any overload).
    assertThat(snap.get("when", ServerTimestampBehavior.ESTIMATE)).isInstanceOf(Timestamp.class);
    assertThat(snap.get(FieldPath.of("when"), ServerTimestampBehavior.ESTIMATE))
        .isInstanceOf(Timestamp.class);
    assertThat(snap.get("when", Timestamp.class, ServerTimestampBehavior.ESTIMATE))
        .isInstanceOf(Timestamp.class);
    assertThat(snap.get(FieldPath.of("when"), Timestamp.class, ServerTimestampBehavior.ESTIMATE))
        .isInstanceOf(Timestamp.class);
  }

  @Test
  public void testServerTimestampsWorkViaTransactionSet() {
    waitFor(
        docRef
            .getFirestore()
            .runTransaction(
                transaction -> {
                  transaction.set(docRef, setData);
                  return null;
                }));
    verifyTimestampsAreResolved(accumulator.awaitRemoteEvent());
  }

  @Test
  public void testServerTimestampsWorkViaTransactionUpdate() {
    writeInitialData();
    waitFor(
        docRef
            .getFirestore()
            .runTransaction(
                transaction -> {
                  transaction.update(docRef, updateData);
                  return null;
                }));
    verifyTimestampsAreResolved(accumulator.awaitRemoteEvent());
  }

  @Test
  public void testServerTimestampsFailViaUpdateOnNonexistentDocument() {
    Exception e = waitForException(docRef.update(updateData));
    assertNotNull(e);
    assertTrue(e instanceof FirebaseFirestoreException);
    assertEquals(Code.NOT_FOUND, ((FirebaseFirestoreException) e).getCode());
  }

  @Test
  public void testServerTimestampsFailViaTransactionUpdateOnNonexistentDocument() {
    Task<?> completion =
        docRef
            .getFirestore()
            .runTransaction(
                transaction -> {
                  transaction.update(docRef, updateData);
                  return null;
                });
    Exception e = waitForException(completion);
    assertNotNull(e);
    assertTrue(e instanceof FirebaseFirestoreException);
    assertEquals(Code.NOT_FOUND, ((FirebaseFirestoreException) e).getCode());
  }

  @Test
  public void testPOJOSupport() {
    DocumentReference ref = testDocument();

    // Write empty pojo (nulls should turn into timestamps)
    TimestampPOJO pojo = new TimestampPOJO();
    waitFor(ref.set(pojo));

    // Read it back (timestamps should have been populated).
    pojo = waitFor(ref.get()).toObject(TimestampPOJO.class);
    assertNull(pojo.a);
    Timestamp resolvedTimestamp = pojo.timestamp1;
    assertNotNull(resolvedTimestamp);
    assertEquals(resolvedTimestamp, pojo.timestamp2);

    // Write it back; timestamps shouldn't change since they're non-null.
    pojo.a = 42L;
    waitFor(ref.set(pojo));

    // And read it back again; make sure the timestamps stayed the same.
    pojo = waitFor(ref.get()).toObject(TimestampPOJO.class);
    assertEquals((Long) 42L, pojo.a);
    assertEquals(resolvedTimestamp, pojo.timestamp1);
    assertEquals(resolvedTimestamp, pojo.timestamp2);
  }

  @Test
  public void testPOJOWithWrongType() {
    DocumentReference ref = testDocument();
    try {
      ref.set(new TimestampPOJOWithWrongType());
      fail("Expected exception.");
    } catch (IllegalArgumentException e) {
      assertEquals(
          "Field timestamp is annotated with @ServerTimestamp but is class "
              + "java.lang.String instead of Date or Timestamp.",
          e.getMessage());
    }
  }

  @Test
  public void testPOJOWithAnnotatedSetter() {
    DocumentReference ref = testDocument();
    try {
      ref.set(new TimestampPOJOWithAnnotatedSetter());
      fail("Expected exception.");
    } catch (IllegalArgumentException e) {
      assertEquals(
          "Method setTimestamp is annotated with @ServerTimestamp but should not be. "
              + "@ServerTimestamp can only be applied to fields and getters, not setters.",
          e.getMessage());
    }
  }

  private static class TimestampPOJO {

    public Long a;

    // On a field
    @ServerTimestamp public Timestamp timestamp1;

    // On a getter
    private Timestamp timestamp2;

    @ServerTimestamp
    public Timestamp getTimestamp2() {
      return timestamp2;
    }
  }

  // ServerTimestamp fields must be of type Timestamp.
  private static class TimestampPOJOWithWrongType {
    @ServerTimestamp public String timestamp;
  }

  // ServerTimestamp can't be applied to setters.
  private static class TimestampPOJOWithAnnotatedSetter {
    private Timestamp timestamp;

    public Timestamp getTimestamp() {
      return timestamp;
    }

    @ServerTimestamp
    public void setTimestamp(Timestamp timestamp) {
      this.timestamp = timestamp;
    }
  }
}
