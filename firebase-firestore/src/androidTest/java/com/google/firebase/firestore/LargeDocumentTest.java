// Copyright 2026 Google LLC
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

import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testFirestore;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import java.util.Arrays;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LargeDocumentTest {

  // These tests require a pre-seeded database containing specific large documents.
  private static final String SEED_COLLECTION = "serverSdkTests";
  private static final String DOC_15_9MB_UNICODE = "doc_15_9MB_unicode";
  private static final String COL_LARGE_DOCS = "col_large_docs";

  // Extended timeout required for large document tests due to gRPC flow control
  // window defaults (64KB), which result in longer read times over the network.
  private static final int TIMEOUT_MS = 60000;

  @After
  public void tearDown() {
    com.google.firebase.firestore.testutil.IntegrationTestUtil.tearDown();
  }

  private String generateString(int sizeInBytes) {
    char[] chars = new char[sizeInBytes];
    Arrays.fill(chars, 'a');
    return new String(chars);
  }

  @Test(timeout = TIMEOUT_MS)
  public void testReadAndCacheLargeUnicodeDocument() {
    FirebaseFirestore db = testFirestore();
    DocumentReference docRef = db.collection(SEED_COLLECTION).document(DOC_15_9MB_UNICODE);

    DocumentSnapshot serverSnapshot = waitFor(docRef.get(Source.SERVER));
    assertTrue(serverSnapshot.exists());

    waitFor(db.disableNetwork());

    DocumentSnapshot cacheSnapshot = waitFor(docRef.get(Source.CACHE));
    assertTrue(cacheSnapshot.exists());

    assertEquals(serverSnapshot.getData(), cacheSnapshot.getData());

    waitFor(db.enableNetwork());
  }

  @Test(timeout = TIMEOUT_MS)
  public void testCacheIntegrityWithMultipleLargeDocuments() {
    FirebaseFirestore db = testFirestore();

    // Copy existing test environment settings but set a normal cache size
    // to ensure we don't accidentally trigger async GC during the test.
    FirebaseFirestoreSettings existingSettings = db.getFirestoreSettings();
    FirebaseFirestoreSettings settings =
        new FirebaseFirestoreSettings.Builder(existingSettings)
            .setLocalCacheSettings(
                PersistentCacheSettings.newBuilder().setSizeBytes(104857600).build()) // 100MB
            .build();
    db.setFirestoreSettings(settings);

    CollectionReference colRef = db.collection(COL_LARGE_DOCS);
    DocumentReference docA = colRef.document("doc_a");
    DocumentReference docB = colRef.document("doc_b");

    waitFor(docA.get(Source.SERVER));
    waitFor(docB.get(Source.SERVER));

    waitFor(db.disableNetwork());

    DocumentSnapshot cacheSnapshotA = waitFor(docA.get(Source.CACHE));
    DocumentSnapshot cacheSnapshotB = waitFor(docB.get(Source.CACHE));

    assertTrue("docA should exist in cache", cacheSnapshotA.exists());
    assertTrue("docB should exist in cache", cacheSnapshotB.exists());

    // Sanity check
    assertTrue(cacheSnapshotA.getData().size() > 0);
    assertTrue(cacheSnapshotB.getData().size() > 0);

    waitFor(db.enableNetwork());
  }

  @Test(timeout = TIMEOUT_MS)
  public void testWatchStreamInitializationAndDiff() throws Exception {
    FirebaseFirestore db = testFirestore();
    DocumentReference docRef = db.collection(SEED_COLLECTION).document(DOC_15_9MB_UNICODE);

    // Verify that the initial snapshot of a large document is received successfully
    // without triggering stream cancellation loops.
    Task<DocumentSnapshot> firstSnapshotTask = docRef.get(Source.SERVER);
    DocumentSnapshot firstSnapshot = waitFor(firstSnapshotTask);
    assertTrue(firstSnapshot.exists());

    // TODO: Enable the differential update assertions below once client SDK write streams
    // support the 16MB limit.
    /*
    Map<String, Object> updateData = new HashMap<>();
    updateData.put("differential_field", "updated_value");
    waitFor(docRef.update(updateData));

    // Wait for the snapshot listener to fire a second time to verify stream continuity.
    */
  }

  // TODO: Enable this test. Currently it times out after not receiving a response from the backend.
  /*
  @Test(timeout = TIMEOUT_MS)
  public void testOversizedPayloadRejection() {
    FirebaseFirestore db = testFirestore();
    DocumentReference docRef = db.collection(SEED_COLLECTION).document("temp_oversized_doc");

    Map<String, Object> data = new HashMap<>();
    // 16.1MB payload
    int oversizedPayloadBytes = (16 * 1024 * 1024) + 102400;
    data.put("largeField", generateString(oversizedPayloadBytes));

    try {
      waitFor(docRef.set(data));
      fail("Setting a document exceeding the maximum size limit should fail.");
    } catch (Exception e) {
      assertTrue(e.getCause() instanceof FirebaseFirestoreException);
      FirebaseFirestoreException firestoreException = (FirebaseFirestoreException) e.getCause();

      assertEquals(Code.INVALID_ARGUMENT, firestoreException.getCode());
    }
  }
  */

  @Test(timeout = TIMEOUT_MS)
  public void testTransactionReadModifyWrite() {
    FirebaseFirestore db = testFirestore();
    DocumentReference docRef = db.collection(SEED_COLLECTION).document(DOC_15_9MB_UNICODE);

    Task<Void> transactionTask =
        db.runTransaction(
            transaction -> {
              DocumentSnapshot snapshot = transaction.get(docRef);
              assertTrue(snapshot.exists());

              transaction.update(docRef, "transaction_timestamp", System.currentTimeMillis());
              return null;
            });

    waitFor(transactionTask);
  }

  @Test(timeout = TIMEOUT_MS)
  public void testQueryLargeDocuments() {
    FirebaseFirestore db = testFirestore();
    CollectionReference colRef = db.collection(COL_LARGE_DOCS);

    Query query = colRef.whereIn(FieldPath.documentId(), Arrays.asList("doc_a", "doc_b"));

    QuerySnapshot serverSnapshot = waitFor(query.get(Source.SERVER));
    assertEquals(
        "Query should return exactly 2 large documents from server", 2, serverSnapshot.size());

    waitFor(db.disableNetwork());

    QuerySnapshot cacheSnapshot = waitFor(query.get(Source.CACHE));
    assertEquals(
        "Query should return exactly 2 large documents from cache", 2, cacheSnapshot.size());

    assertEquals(
        "Cached query payload should exactly match server query payload",
        serverSnapshot.getDocuments().get(0).getData(),
        cacheSnapshot.getDocuments().get(0).getData());

    waitFor(db.enableNetwork());
  }

  @Test(timeout = TIMEOUT_MS)
  public void testQueryLargeDocumentsForcesLocalScan() {
    FirebaseFirestore db = testFirestore();
    CollectionReference colRef = db.collection(COL_LARGE_DOCS);

    waitFor(colRef.document("doc_a").get(Source.SERVER));
    waitFor(colRef.document("doc_b").get(Source.SERVER));

    waitFor(db.disableNetwork());

    Query query = colRef.orderBy(FieldPath.documentId()).limit(2);

    // Execute the query offline
    QuerySnapshot cacheSnapshot = waitFor(query.get(Source.CACHE));

    assertEquals(
        "Query should find and return exactly 2 large documents from cache",
        2,
        cacheSnapshot.size());

    assertTrue(
        "Payload should not be empty", cacheSnapshot.getDocuments().get(0).getData().size() > 0);

    waitFor(db.enableNetwork());
  }
}
