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

import static com.google.firebase.firestore.testutil.IntegrationTestUtil.querySnapshotToValues;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testCollection;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testDocument;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testFirestore;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitForException;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestoreException.Code;
import com.google.firebase.firestore.testutil.EventAccumulator;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
import com.google.firebase.firestore.util.Util;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class WriteBatchTest {

  @After
  public void tearDown() {
    IntegrationTestUtil.tearDown();
  }

  @Test
  public void testSupportEmptyBatches() {
    waitFor(testFirestore().batch().commit());
  }

  @Test
  public void testSetDocuments() {
    DocumentReference doc = testDocument();
    waitFor(doc.getFirestore().batch().set(doc, map("foo", "bar")).commit());
    DocumentSnapshot snapshot = waitFor(doc.get());
    assertTrue(snapshot.exists());
    assertEquals(map("foo", "bar"), snapshot.getData());
  }

  @Test
  public void testSetDocumentsWithMerge() {
    DocumentReference doc = testDocument();
    waitFor(
        doc.getFirestore()
            .batch()
            .set(doc, map("a", "b", "nested", map("a", "remove")), SetOptions.merge())
            .commit());
    waitFor(
        doc.getFirestore()
            .batch()
            .set(
                doc,
                map("c", "d", "ignore", true, "nested", map("c", "d")),
                SetOptions.mergeFields("c", "nested"))
            .commit());
    waitFor(
        doc.getFirestore()
            .batch()
            .set(
                doc,
                map("e", "f", "nested", map("e", "f", "ignore", true)),
                SetOptions.mergeFieldPaths(
                    Arrays.asList(FieldPath.of("e"), FieldPath.of("nested", "e"))))
            .commit());
    DocumentSnapshot snapshot = waitFor(doc.get());
    assertTrue(snapshot.exists());
    assertEquals(
        map("a", "b", "c", "d", "e", "f", "nested", map("c", "d", "e", "f")), snapshot.getData());
  }

  @Test
  public void testUpdateDocuments() {
    DocumentReference doc = testDocument();
    waitFor(doc.set(map("foo", "bar")));
    waitFor(doc.getFirestore().batch().update(doc, map("baz", 42)).commit());
    DocumentSnapshot snapshot = waitFor(doc.get());
    assertTrue(snapshot.exists());
    assertEquals(map("foo", "bar", "baz", 42L), snapshot.getData());
  }

  @Test
  public void testUpdateFieldsWithDots() {
    DocumentReference doc = testDocument();
    waitFor(doc.set(map("a.b", "old", "c.d", "old")));

    waitFor(doc.getFirestore().batch().update(doc, FieldPath.of("a.b"), "new").commit());
    waitFor(doc.getFirestore().batch().update(doc, FieldPath.of("c.d"), "new").commit());

    DocumentSnapshot snapshot = waitFor(doc.get());
    assertTrue(snapshot.exists());
    assertEquals(map("a.b", "new", "c.d", "new"), snapshot.getData());
  }

  @Test
  public void testUpdateNestedFields() {
    DocumentReference doc = testDocument();
    waitFor(doc.set(map("a", map("b", "old"), "c", map("d", "old"))));

    waitFor(doc.getFirestore().batch().update(doc, "a.b", "new").commit());
    waitFor(doc.getFirestore().batch().update(doc, map("c.d", "new")).commit());

    DocumentSnapshot snapshot = waitFor(doc.get());
    assertTrue(snapshot.exists());
    assertEquals(map("a", map("b", "new"), "c", map("d", "new")), snapshot.getData());
  }

  @Test
  public void testDeleteDocuments() {
    DocumentReference doc = testDocument();
    waitFor(doc.set(map("foo", "bar")));
    DocumentSnapshot snapshot = waitFor(doc.get());

    assertTrue(snapshot.exists());
    waitFor(doc.getFirestore().batch().delete(doc).commit());
    snapshot = waitFor(doc.get());
    assertFalse(snapshot.exists());
  }

  @Test
  public void testBatchesCommitAtomicallyRaisingCorrectEvents() {
    CollectionReference collection = testCollection();
    DocumentReference docA = collection.document("a");
    DocumentReference docB = collection.document("b");
    EventAccumulator<QuerySnapshot> accumulator = new EventAccumulator<>();
    collection.addSnapshotListener(MetadataChanges.INCLUDE, accumulator.listener());
    QuerySnapshot initialSnap = accumulator.await();
    assertEquals(0, initialSnap.size());

    // Atomically write two documents.
    waitFor(
        collection.getFirestore().batch().set(docA, map("a", 1)).set(docB, map("b", 2)).commit());

    QuerySnapshot localSnap = accumulator.await();
    assertTrue(localSnap.getMetadata().hasPendingWrites());
    assertEquals(asList(map("a", 1L), map("b", 2L)), querySnapshotToValues(localSnap));

    QuerySnapshot serverSnap = accumulator.await();
    assertFalse(serverSnap.getMetadata().hasPendingWrites());
    assertEquals(asList(map("a", 1L), map("b", 2L)), querySnapshotToValues(serverSnap));
  }

  @Test
  public void testBatchesFailAtomicallyRaisingCorrectEvents() {
    CollectionReference collection = testCollection();
    DocumentReference docA = collection.document("a");
    DocumentReference docB = collection.document("b");
    EventAccumulator<QuerySnapshot> accumulator = new EventAccumulator<>();
    collection.addSnapshotListener(MetadataChanges.INCLUDE, accumulator.listener());
    QuerySnapshot initialSnap = accumulator.await();
    assertEquals(0, initialSnap.size());

    // Atomically write 1 document and update a nonexistent document.
    Exception err =
        waitForException(
            collection
                .getFirestore()
                .batch()
                .set(docA, map("a", 1))
                .update(docB, map("b", 2))
                .commit());

    // Local event with the set document.
    QuerySnapshot localSnap = accumulator.await();
    assertTrue(localSnap.getMetadata().hasPendingWrites());
    assertEquals(asList(map("a", 1L)), querySnapshotToValues(localSnap));

    // Server event with the set reverted
    QuerySnapshot serverSnap = accumulator.await();
    assertFalse(serverSnap.getMetadata().hasPendingWrites());
    assertEquals(0, serverSnap.size());

    assertNotNull(err);
    assertTrue(err instanceof FirebaseFirestoreException);
    assertEquals(Code.NOT_FOUND, ((FirebaseFirestoreException) err).getCode());
  }

  @Test
  public void testWriteTheSameServerTimestampAcrossWrites() {
    CollectionReference collection = testCollection();
    DocumentReference docA = collection.document("a");
    DocumentReference docB = collection.document("b");
    EventAccumulator<QuerySnapshot> accumulator = new EventAccumulator<>();
    collection.addSnapshotListener(MetadataChanges.INCLUDE, accumulator.listener());
    QuerySnapshot initialSnap = accumulator.await();
    assertEquals(0, initialSnap.size());

    // Atomically write two documents with server timestamps.
    waitFor(
        collection
            .getFirestore()
            .batch()
            .set(docA, map("when", FieldValue.serverTimestamp()))
            .set(docB, map("when", FieldValue.serverTimestamp()))
            .commit());

    QuerySnapshot localSnap = accumulator.await();
    assertTrue(localSnap.getMetadata().hasPendingWrites());
    assertEquals(asList(map("when", null), map("when", null)), querySnapshotToValues(localSnap));

    QuerySnapshot serverSnap = accumulator.awaitRemoteEvent();
    assertFalse(serverSnap.getMetadata().hasPendingWrites());
    assertEquals(2, serverSnap.size());
    Timestamp when = serverSnap.getDocuments().get(0).getTimestamp("when");
    assertNotNull(when);
    assertEquals(asList(map("when", when), map("when", when)), querySnapshotToValues(serverSnap));
  }

  @Test
  public void testCanWriteTheSameDocumentMultipleTimes() {
    DocumentReference doc = testDocument();
    EventAccumulator<DocumentSnapshot> accumulator = new EventAccumulator<>();
    doc.addSnapshotListener(MetadataChanges.INCLUDE, accumulator.listener());
    DocumentSnapshot initialSnap = accumulator.await();
    assertFalse(initialSnap.exists());

    waitFor(
        doc.getFirestore()
            .batch()
            .delete(doc)
            .set(doc, map("a", 1, "b", 1, "when", "when"))
            .update(doc, map("b", 2, "when", FieldValue.serverTimestamp()))
            .commit());

    DocumentSnapshot localSnap = accumulator.await();
    assertTrue(localSnap.getMetadata().hasPendingWrites());
    assertEquals(map("a", 1L, "b", 2L, "when", null), localSnap.getData());

    DocumentSnapshot serverSnap = accumulator.await();
    assertFalse(serverSnap.getMetadata().hasPendingWrites());
    Timestamp when = serverSnap.getTimestamp("when");
    assertNotNull(when);
    assertEquals(map("a", 1L, "b", 2L, "when", when), serverSnap.getData());
  }

  @Test
  public void testCanWriteVeryLargeBatches() {
    // On Android, SQLite Cursors are limited reading no more than 2 MB per row (despite being able
    // to write very large values). This test verifies that the SQLiteMutationQueue properly works
    // around this limitation.

    // Create a map containing nearly 1 MB of data. Note that if you use 1024 below this will create
    // a document larger than 1 MB, which will be rejected by the backend as too large.
    String a = Character.toString('a');
    StringBuilder buf = new StringBuilder(1000);
    for (int i = 0; i < 1000; i++) {
      buf.append(a);
    }
    String kb = buf.toString();
    Map<String, Object> values = new HashMap<>();
    for (int j = 0; j < 1000; j++) {
      values.put(Util.autoId(), kb);
    }

    DocumentReference doc = testDocument();
    WriteBatch batch = doc.getFirestore().batch();

    // Write a batch containing 3 copies of the data, creating a ~3 MB batch. Writing to the same
    // document in a batch is allowed and so long as the net size of the document is under 1 MB the
    // batch is allowed.
    batch.set(doc, values);
    for (int i = 0; i < 2; i++) {
      batch.update(doc, values);
    }

    waitFor(batch.commit());
    DocumentSnapshot snap = waitFor(doc.get());
    assertEquals(values, snap.getData());
  }

  @Test
  public void testRunBatch() {
    DocumentReference doc = testDocument();
    waitFor(doc.set(map("foo", "bar")));
    waitFor(
        doc.getFirestore()
            .runBatch(
                batch -> {
                  batch.update(doc, map("baz", 42));
                }));
    DocumentSnapshot snapshot = waitFor(doc.get());
    assertTrue(snapshot.exists());
    assertEquals(map("foo", "bar", "baz", 42L), snapshot.getData());
  }
}
