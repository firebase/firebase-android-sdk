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
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import com.google.android.gms.tasks.Task;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class LargeDocumentTest {

  private static String seedCollection;
  private static String unicodePayload;
  private static String asciiPayload;

  // Exteneded timeout because these tests can be slow.
  private static final int TIMEOUT_MS = 120000;

  private static String generateUnicodeString(int targetUtf8Bytes) {
    StringBuilder sb = new StringBuilder();
    String emoji = "🚀"; // 4 bytes in UTF-8
    int bytes = 0;
    while (bytes < targetUtf8Bytes) {
      if (bytes % 2 == 0 && bytes + 4 <= targetUtf8Bytes) {
        sb.append(emoji);
        bytes += 4;
      } else {
        sb.append('a');
        bytes += 1;
      }
    }
    return sb.toString();
  }

  private static String generateAsciiString(int sizeInBytes) {
    char[] chars = new char[sizeInBytes];
    Arrays.fill(chars, 'a');
    return new String(chars);
  }

  @BeforeClass
  public static void setUpClass() {
    FirebaseFirestore db = testFirestore();
    seedCollection = "large_doc_tests_" + System.currentTimeMillis();

    int targetBytes = (int) Math.floor(15.9 * 1024 * 1024);
    unicodePayload = generateUnicodeString(targetBytes);
    asciiPayload = generateAsciiString(targetBytes);

    DocumentReference docRef = db.collection(seedCollection).document("doc_15_9MB_unicode");
    DocumentReference docA = db.collection(seedCollection).document("doc_a");
    DocumentReference docB = db.collection(seedCollection).document("doc_b");

    Map<String, Object> dataUnicode = new HashMap<>();
    dataUnicode.put("chunk", unicodePayload);
    Map<String, Object> dataAscii = new HashMap<>();
    dataAscii.put("chunk", asciiPayload);

    waitFor(docRef.set(dataUnicode));
    waitFor(docA.set(dataAscii));
    waitFor(docB.set(dataAscii));
  }

  @AfterClass
  public static void tearDownClass() {
    if (seedCollection != null) {
      FirebaseFirestore db = testFirestore();
      try {
        waitFor(db.collection(seedCollection).document("doc_15_9MB_unicode").delete());
        waitFor(db.collection(seedCollection).document("doc_a").delete());
        waitFor(db.collection(seedCollection).document("doc_b").delete());
      } catch (Exception e) {
        // Suppress cleanup exceptions
      }
    }
  }

  @After
  public void tearDown() {
    com.google.firebase.firestore.testutil.IntegrationTestUtil.tearDown();
  }

  @Test(timeout = TIMEOUT_MS)
  public void testReadAndCacheLargeUnicodeDocument() {
    FirebaseFirestore db = testFirestore();
    DocumentReference docRef = db.collection(seedCollection).document("doc_15_9MB_unicode");

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

    CollectionReference colRef = db.collection(seedCollection);
    DocumentReference docA = colRef.document("doc_a");
    DocumentReference docB = colRef.document("doc_b");

    waitFor(docA.get(Source.SERVER));
    waitFor(docB.get(Source.SERVER));

    waitFor(db.disableNetwork());

    DocumentSnapshot cacheSnapshotA = waitFor(docA.get(Source.CACHE));
    DocumentSnapshot cacheSnapshotB = waitFor(docB.get(Source.CACHE));

    assertTrue("docA should exist in cache", cacheSnapshotA.exists());
    assertTrue("docB should exist in cache", cacheSnapshotB.exists());

    assertEquals(asciiPayload, cacheSnapshotA.getString("chunk"));
    assertEquals(asciiPayload, cacheSnapshotB.getString("chunk"));

    waitFor(db.enableNetwork());
  }

  @Test(timeout = TIMEOUT_MS)
  public void testWatchStreamInitializationAndDiff() throws Exception {
    FirebaseFirestore db = testFirestore();
    DocumentReference docRef = db.collection(seedCollection).document("doc_15_9MB_unicode");

    String expectedValue = "updated_val_" + System.currentTimeMillis();

    // Verify that the initial snapshot of a large document is received successfully
    // without triggering stream cancellation loops.
    CountDownLatch updateLatch = new CountDownLatch(1);
    ListenerRegistration registration =
        docRef.addSnapshotListener(
            (snapshot, error) -> {
              if (snapshot != null
                  && snapshot.exists()
                  && expectedValue.equals(snapshot.getString("differential_field"))) {
                updateLatch.countDown();
              }
            });

    try {
      Task<DocumentSnapshot> firstSnapshotTask = docRef.get(Source.SERVER);
      DocumentSnapshot firstSnapshot = waitFor(firstSnapshotTask);
      assertTrue(firstSnapshot.exists());

      Map<String, Object> updateData = new HashMap<>();
      updateData.put("differential_field", expectedValue);
      waitFor(docRef.update(updateData));

      assertTrue(
          "Watch stream should deliver differential update",
          updateLatch.await(60, TimeUnit.SECONDS));
    } finally {
      registration.remove();
    }
  }

  @Test(timeout = TIMEOUT_MS)
  public void testOversizedPayloadRejection() {
    FirebaseFirestore db = testFirestore();
    DocumentReference docRef = db.collection(seedCollection).document("temp_oversized_doc");

    Map<String, Object> data = new HashMap<>();
    // 16.1MB payload
    int oversizedPayloadBytes = (16 * 1024 * 1024) + 102400;
    data.put("largeField", generateAsciiString(oversizedPayloadBytes));

    try {
      waitFor(docRef.set(data));
      fail("Setting a document exceeding the maximum size limit should fail.");
    } catch (Exception e) {
      assertTrue(e.getCause() instanceof FirebaseFirestoreException);
      FirebaseFirestoreException firestoreException = (FirebaseFirestoreException) e.getCause();
      assertEquals(FirebaseFirestoreException.Code.INVALID_ARGUMENT, firestoreException.getCode());
    }
  }

  @Test(timeout = TIMEOUT_MS)
  public void testWriteValidLargeDocument() {
    FirebaseFirestore db = testFirestore();
    String tempDocId = "temp_valid_large_doc_" + System.currentTimeMillis();
    DocumentReference docRef = db.collection(seedCollection).document(tempDocId);

    try {
      int targetBytes = (int) Math.floor(15.9 * 1024 * 1024);
      String largePayload = generateAsciiString(targetBytes);
      Map<String, Object> data = new HashMap<>();
      data.put("chunk", largePayload);

      waitFor(docRef.set(data));

      DocumentSnapshot snapshot = waitFor(docRef.get(Source.SERVER));
      assertTrue(snapshot.exists());
      assertEquals(largePayload, snapshot.getString("chunk"));
    } finally {
      try {
        waitFor(docRef.delete());
      } catch (Exception e) {
        // Suppress cleanup exceptions
      }
    }
  }

  @Test(timeout = TIMEOUT_MS)
  public void testTransactionReadModifyWrite() {
    FirebaseFirestore db = testFirestore();
    DocumentReference docRef = db.collection(seedCollection).document("doc_15_9MB_unicode");

    long timestamp = System.currentTimeMillis();
    Task<Void> transactionTask =
        db.runTransaction(
            transaction -> {
              DocumentSnapshot snapshot = transaction.get(docRef);
              assertTrue(snapshot.exists());

              transaction.update(docRef, "transaction_timestamp", timestamp);
              return null;
            });

    waitFor(transactionTask);

    DocumentSnapshot updatedSnapshot = waitFor(docRef.get(Source.SERVER));
    assertTrue(updatedSnapshot.exists());
    assertEquals(Long.valueOf(timestamp), updatedSnapshot.getLong("transaction_timestamp"));
    assertEquals(unicodePayload, updatedSnapshot.getString("chunk"));
  }

  @Test(timeout = TIMEOUT_MS)
  public void testQueryLargeDocuments() {
    FirebaseFirestore db = testFirestore();
    CollectionReference colRef = db.collection(seedCollection);

    Query query = colRef.whereIn(FieldPath.documentId(), Arrays.asList("doc_a", "doc_b"));

    QuerySnapshot serverSnapshot = waitFor(query.get(Source.SERVER));
    assertEquals(
        "Query should return exactly 2 large documents from server", 2, serverSnapshot.size());

    waitFor(db.disableNetwork());

    QuerySnapshot cacheSnapshot = waitFor(query.get(Source.CACHE));
    assertEquals(
        "Query should return exactly 2 large documents from cache", 2, cacheSnapshot.size());

    for (DocumentSnapshot serverDoc : serverSnapshot.getDocuments()) {
      DocumentSnapshot matchingCacheDoc =
          cacheSnapshot.getDocuments().stream()
              .filter(d -> d.getId().equals(serverDoc.getId()))
              .findFirst()
              .orElse(null);
      assertTrue(
          "Document " + serverDoc.getId() + " should exist in cache snapshot",
          matchingCacheDoc != null);
      assertEquals(
          "Payload for " + serverDoc.getId() + " in cache should match server",
          serverDoc.getData(),
          matchingCacheDoc.getData());
    }

    waitFor(db.enableNetwork());
  }

  @Test(timeout = TIMEOUT_MS)
  public void testQueryLargeDocumentsForcesLocalScan() {
    FirebaseFirestore db = testFirestore();
    CollectionReference colRef = db.collection(seedCollection);

    waitFor(colRef.document("doc_a").get(Source.SERVER));
    waitFor(colRef.document("doc_b").get(Source.SERVER));

    waitFor(db.disableNetwork());

    Query query =
        colRef
            .whereGreaterThanOrEqualTo(FieldPath.documentId(), "doc_a")
            .orderBy(FieldPath.documentId())
            .limit(2);

    // Execute the query offline
    QuerySnapshot cacheSnapshot = waitFor(query.get(Source.CACHE));

    assertEquals(
        "Query should find and return exactly 2 large documents from cache",
        2,
        cacheSnapshot.size());

    List<DocumentSnapshot> docs = cacheSnapshot.getDocuments();
    assertEquals("doc_a", docs.get(0).getId());
    assertEquals("doc_b", docs.get(1).getId());
    assertEquals(asciiPayload, docs.get(0).getString("chunk"));
    assertEquals(asciiPayload, docs.get(1).getString("chunk"));

    waitFor(db.enableNetwork());
  }
}
