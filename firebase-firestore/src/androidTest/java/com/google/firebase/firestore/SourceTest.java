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

import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testCollection;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testCollectionWithDocs;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testDocument;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testDocumentWithData;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.toDataMap;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitForException;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
import java.util.Map;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class SourceTest {

  @After
  public void tearDown() {
    IntegrationTestUtil.tearDown();
  }

  @Test
  public void getDocumentWhileOnlineWithDefaultGetOptions() {
    Map<String, Object> initialData = map("key", "value");
    DocumentReference docRef = testDocumentWithData(initialData);

    Task<DocumentSnapshot> docTask = docRef.get();
    waitFor(docTask);

    DocumentSnapshot doc = docTask.getResult();
    assertTrue(doc.exists());
    assertFalse(doc.getMetadata().isFromCache());
    assertFalse(doc.getMetadata().hasPendingWrites());
    assertEquals(initialData, doc.getData());
  }

  @Test
  public void getCollectionWhileOnlineWithDefaultGetOptions() {
    Map<String, Map<String, Object>> initialDocs =
        map(
            "doc1", map("key1", "value1"),
            "doc2", map("key2", "value2"),
            "doc3", map("key3", "value3"));
    CollectionReference colRef = testCollectionWithDocs(initialDocs);

    Task<QuerySnapshot> qrySnapTask = colRef.get();
    waitFor(qrySnapTask);

    QuerySnapshot qrySnap = qrySnapTask.getResult();
    assertFalse(qrySnap.getMetadata().isFromCache());
    assertFalse(qrySnap.getMetadata().hasPendingWrites());
    assertEquals(3, qrySnap.getDocumentChanges().size());
    assertEquals(initialDocs, toDataMap(qrySnap));
  }

  @Test
  public void getDocumentWhileOfflineWithDefaultGetOptions() {
    Map<String, Object> initialData = map("key", "value");
    DocumentReference docRef = testDocumentWithData(initialData);

    waitFor(docRef.get());
    waitFor(docRef.getFirestore().disableNetwork());

    Task<DocumentSnapshot> docTask = docRef.get();
    waitFor(docTask);
    DocumentSnapshot doc = docTask.getResult();

    assertTrue(doc.exists());
    assertTrue(doc.getMetadata().isFromCache());
    assertFalse(doc.getMetadata().hasPendingWrites());
    assertEquals(initialData, doc.getData());
  }

  @Test
  public void getCollectionWhileOfflineWithDefaultGetOptions() {
    Map<String, Map<String, Object>> initialDocs =
        map(
            "doc1", map("key1", "value1"),
            "doc2", map("key2", "value2"),
            "doc3", map("key3", "value3"));
    CollectionReference colRef = testCollectionWithDocs(initialDocs);

    waitFor(colRef.get());
    waitFor(colRef.getFirestore().disableNetwork());

    // Since we're offline, the returned promises won't complete
    colRef.document("doc2").set(map("key2b", "value2b"), SetOptions.merge());
    colRef.document("doc3").set(map("key3b", "value3b"));
    colRef.document("doc4").set(map("key4", "value4"));

    Task<QuerySnapshot> qrySnapTask = colRef.get();
    waitFor(qrySnapTask);

    QuerySnapshot qrySnap = qrySnapTask.getResult();
    assertTrue(qrySnap.getMetadata().isFromCache());
    assertTrue(qrySnap.getMetadata().hasPendingWrites());
    assertEquals(4, qrySnap.getDocumentChanges().size());
    assertEquals(
        map(
            "doc1", map("key1", "value1"),
            "doc2", map("key2", "value2", "key2b", "value2b"),
            "doc3", map("key3b", "value3b"),
            "doc4", map("key4", "value4")),
        toDataMap(qrySnap));
  }

  @Test
  public void getDocumentWhileOnlineWithSourceEqualToCache() {
    Map<String, Object> initialData = map("key", "value");
    DocumentReference docRef = testDocumentWithData(initialData);

    waitFor(docRef.get());
    Task<DocumentSnapshot> docTask = docRef.get(Source.CACHE);
    waitFor(docTask);
    DocumentSnapshot doc = docTask.getResult();

    assertTrue(doc.exists());
    assertTrue(doc.getMetadata().isFromCache());
    assertFalse(doc.getMetadata().hasPendingWrites());
    assertEquals(initialData, doc.getData());
  }

  @Test
  public void getCollectionWhileOnlineWithSourceEqualToCache() {
    Map<String, Map<String, Object>> initialDocs =
        map(
            "doc1", map("key1", "value1"),
            "doc2", map("key2", "value2"),
            "doc3", map("key3", "value3"));
    CollectionReference colRef = testCollectionWithDocs(initialDocs);

    waitFor(colRef.get());
    Task<QuerySnapshot> qrySnapTask = colRef.get(Source.CACHE);
    waitFor(qrySnapTask);

    QuerySnapshot qrySnap = qrySnapTask.getResult();
    assertTrue(qrySnap.getMetadata().isFromCache());
    assertFalse(qrySnap.getMetadata().hasPendingWrites());
    assertEquals(3, qrySnap.getDocumentChanges().size());
    assertEquals(initialDocs, toDataMap(qrySnap));
  }

  @Test
  public void getDocumentWhileOfflineWithSourceEqualToCache() {
    Map<String, Object> initialData = map("key", "value");
    DocumentReference docRef = testDocumentWithData(initialData);

    waitFor(docRef.get());
    waitFor(docRef.getFirestore().disableNetwork());
    Task<DocumentSnapshot> docTask = docRef.get(Source.CACHE);
    waitFor(docTask);
    DocumentSnapshot doc = docTask.getResult();

    assertTrue(doc.exists());
    assertTrue(doc.getMetadata().isFromCache());
    assertFalse(doc.getMetadata().hasPendingWrites());
    assertEquals(initialData, doc.getData());
  }

  @Test
  public void getCollectionWhileOfflineWithSourceEqualToCache() {
    Map<String, Map<String, Object>> initialDocs =
        map(
            "doc1", map("key1", "value1"),
            "doc2", map("key2", "value2"),
            "doc3", map("key3", "value3"));
    CollectionReference colRef = testCollectionWithDocs(initialDocs);

    waitFor(colRef.get());
    waitFor(colRef.getFirestore().disableNetwork());

    // Since we're offline, the returned promises won't complete
    colRef.document("doc2").set(map("key2b", "value2b"), SetOptions.merge());
    colRef.document("doc3").set(map("key3b", "value3b"));
    colRef.document("doc4").set(map("key4", "value4"));

    Task<QuerySnapshot> qrySnapTask = colRef.get(Source.CACHE);
    waitFor(qrySnapTask);

    QuerySnapshot qrySnap = qrySnapTask.getResult();
    assertTrue(qrySnap.getMetadata().isFromCache());
    assertTrue(qrySnap.getMetadata().hasPendingWrites());
    assertEquals(4, qrySnap.getDocumentChanges().size());
    assertEquals(
        map(
            "doc1", map("key1", "value1"),
            "doc2", map("key2", "value2", "key2b", "value2b"),
            "doc3", map("key3b", "value3b"),
            "doc4", map("key4", "value4")),
        toDataMap(qrySnap));
  }

  @Test
  public void getDocumentWhileOnlineWithSourceEqualToServer() {
    Map<String, Object> initialData = map("key", "value");
    DocumentReference docRef = testDocumentWithData(initialData);

    Task<DocumentSnapshot> docTask = docRef.get(Source.SERVER);
    waitFor(docTask);

    DocumentSnapshot doc = docTask.getResult();
    assertTrue(doc.exists());
    assertFalse(doc.getMetadata().isFromCache());
    assertFalse(doc.getMetadata().hasPendingWrites());
    assertEquals(initialData, doc.getData());
  }

  @Test
  public void getCollectionWhileOnlineWithSourceEqualToServer() {
    Map<String, Map<String, Object>> initialDocs =
        map(
            "doc1", map("key1", "value1"),
            "doc2", map("key2", "value2"),
            "doc3", map("key3", "value3"));
    CollectionReference colRef = testCollectionWithDocs(initialDocs);

    Task<QuerySnapshot> qrySnapTask = colRef.get(Source.SERVER);
    waitFor(qrySnapTask);

    QuerySnapshot qrySnap = qrySnapTask.getResult();
    assertFalse(qrySnap.getMetadata().isFromCache());
    assertFalse(qrySnap.getMetadata().hasPendingWrites());
    assertEquals(3, qrySnap.getDocumentChanges().size());
    assertEquals(initialDocs, toDataMap(qrySnap));
  }

  @Test
  public void getDocumentWhileOfflineWithSourceEqualToServer() {
    Map<String, Object> initialData = map("key", "value");
    DocumentReference docRef = testDocumentWithData(initialData);

    waitFor(docRef.get());
    waitFor(docRef.getFirestore().disableNetwork());

    Task<DocumentSnapshot> docTask = docRef.get(Source.SERVER);
    waitForException(docTask);
  }

  @Test
  public void getCollectionWhileOfflineWithSourceEqualToServer() {
    Map<String, Map<String, Object>> initialDocs =
        map(
            "doc1", map("key1", "value1"),
            "doc2", map("key2", "value2"),
            "doc3", map("key3", "value3"));
    CollectionReference colRef = testCollectionWithDocs(initialDocs);

    waitFor(colRef.get());
    waitFor(colRef.getFirestore().disableNetwork());

    Task<QuerySnapshot> qrySnapTask = colRef.get(Source.SERVER);
    waitForException(qrySnapTask);
  }

  @Test
  public void getDocumentWhileOfflineWithDifferentGetOptions() {
    Map<String, Object> initialData = map("key", "value");
    DocumentReference docRef = testDocumentWithData(initialData);

    waitFor(docRef.get());
    waitFor(docRef.getFirestore().disableNetwork());

    // Create an initial listener for this query (to attempt to disrupt the gets below) and wait for
    // the listener to deliver its initial snapshot before continuing.
    TaskCompletionSource<Void> source = new TaskCompletionSource<>();
    docRef.addSnapshotListener(
        (docSnap, error) -> {
          if (error != null) {
            source.setException(error);
          } else {
            source.setResult(null);
          }
        });
    waitFor(source.getTask());

    Task<DocumentSnapshot> docTask = docRef.get(Source.CACHE);
    waitFor(docTask);
    DocumentSnapshot doc = docTask.getResult();
    assertTrue(doc.exists());
    assertTrue(doc.getMetadata().isFromCache());
    assertFalse(doc.getMetadata().hasPendingWrites());
    assertEquals(initialData, doc.getData());

    docTask = docRef.get();
    waitFor(docTask);
    doc = docTask.getResult();
    assertTrue(doc.exists());
    assertTrue(doc.getMetadata().isFromCache());
    assertFalse(doc.getMetadata().hasPendingWrites());
    assertEquals(initialData, doc.getData());

    docTask = docRef.get(Source.SERVER);
    waitForException(docTask);
  }

  @Test
  public void getCollectionWhileOfflineWithDifferentGetOptions() {
    Map<String, Map<String, Object>> initialDocs =
        map(
            "doc1", map("key1", "value1"),
            "doc2", map("key2", "value2"),
            "doc3", map("key3", "value3"));
    CollectionReference colRef = testCollectionWithDocs(initialDocs);

    waitFor(colRef.get());
    waitFor(colRef.getFirestore().disableNetwork());

    // since we're offline, the returned promises won't complete
    colRef.document("doc2").set(map("key2b", "value2b"), SetOptions.merge());
    colRef.document("doc3").set(map("key3b", "value3b"));
    colRef.document("doc4").set(map("key4", "value4"));

    // Create an initial listener for this query (to attempt to disrupt the gets below) and wait for
    // the listener to deliver its initial snapshot before continuing.
    TaskCompletionSource<Void> source = new TaskCompletionSource<>();
    colRef.addSnapshotListener(
        (qrySnap, error) -> {
          if (error != null) {
            source.setException(error);
          } else {
            source.setResult(null);
          }
        });
    waitFor(source.getTask());

    Task<QuerySnapshot> qrySnapTask = colRef.get(Source.CACHE);
    waitFor(qrySnapTask);
    QuerySnapshot qrySnap = qrySnapTask.getResult();
    assertTrue(qrySnap.getMetadata().isFromCache());
    assertTrue(qrySnap.getMetadata().hasPendingWrites());
    assertEquals(4, qrySnap.getDocumentChanges().size());
    assertEquals(
        map(
            "doc1", map("key1", "value1"),
            "doc2", map("key2", "value2", "key2b", "value2b"),
            "doc3", map("key3b", "value3b"),
            "doc4", map("key4", "value4")),
        toDataMap(qrySnap));

    qrySnapTask = colRef.get();
    waitFor(qrySnapTask);
    qrySnap = qrySnapTask.getResult();
    assertTrue(qrySnap.getMetadata().isFromCache());
    assertTrue(qrySnap.getMetadata().hasPendingWrites());
    assertEquals(4, qrySnap.getDocumentChanges().size());
    assertEquals(
        map(
            "doc1", map("key1", "value1"),
            "doc2", map("key2", "value2", "key2b", "value2b"),
            "doc3", map("key3b", "value3b"),
            "doc4", map("key4", "value4")),
        toDataMap(qrySnap));

    qrySnapTask = colRef.get(Source.SERVER);
    waitForException(qrySnapTask);
  }

  @Test
  public void getNonExistingDocWhileOnlineWithDefaultGetOptions() {
    DocumentReference docRef = testDocument();

    Task<DocumentSnapshot> docTask = docRef.get();
    waitFor(docTask);

    DocumentSnapshot doc = docTask.getResult();
    assertFalse(doc.exists());
    assertFalse(doc.getMetadata().isFromCache());
    assertFalse(doc.getMetadata().hasPendingWrites());
  }

  @Test
  public void getNonExistingCollectionWhileOnlineWithDefaultGetOptions() {
    CollectionReference colRef = testCollection();

    Task<QuerySnapshot> qrySnapTask = colRef.get();
    waitFor(qrySnapTask);

    QuerySnapshot qrySnap = qrySnapTask.getResult();
    assertTrue(qrySnap.isEmpty());
    assertEquals(0, qrySnap.getDocumentChanges().size());
    assertFalse(qrySnap.getMetadata().isFromCache());
    assertFalse(qrySnap.getMetadata().hasPendingWrites());
  }

  @Test
  public void getNonExistingDocWhileOfflineWithDefaultGetOptions() {
    DocumentReference docRef = testDocument();

    waitFor(docRef.getFirestore().disableNetwork());
    Task<DocumentSnapshot> docTask = docRef.get();
    waitForException(docTask);
  }

  // TODO(b/112267729): We should raise a fromCache=true event with a
  // nonexistent snapshot, but because the default source goes through a normal
  // listener, we do not.
  @Test
  @Ignore
  public void getDeletedDocWhileOfflineWithDefaultGetOptions() {
    DocumentReference docRef = testDocument();
    waitFor(docRef.delete());

    waitFor(docRef.getFirestore().disableNetwork());
    Task<DocumentSnapshot> docTask = docRef.get();
    waitFor(docTask);

    DocumentSnapshot doc = docTask.getResult();
    assertFalse(doc.exists());
    assertNull(doc.getData());
    assertTrue(doc.getMetadata().isFromCache());
    assertFalse(doc.getMetadata().hasPendingWrites());
  }

  @Test
  public void getNonExistingCollectionWhileOfflineWithDefaultGetOptions() {
    CollectionReference colRef = testCollection();

    waitFor(colRef.getFirestore().disableNetwork());
    Task<QuerySnapshot> qrySnapTask = colRef.get();
    waitFor(qrySnapTask);

    QuerySnapshot qrySnap = qrySnapTask.getResult();
    assertTrue(qrySnap.isEmpty());
    assertEquals(0, qrySnap.getDocumentChanges().size());
    assertTrue(qrySnap.getMetadata().isFromCache());
    assertFalse(qrySnap.getMetadata().hasPendingWrites());
  }

  @Test
  public void getNonExistingDocWhileOnlineWithSourceEqualToCache() {
    DocumentReference docRef = testDocument();

    // Attempt to get doc. This will fail since there's nothing in cache.
    Task<DocumentSnapshot> docTask = docRef.get(Source.CACHE);
    waitForException(docTask);
  }

  @Test
  public void getNonExistingCollectionWhileOnlineWithSourceEqualToCache() {
    CollectionReference colRef = testCollection();

    Task<QuerySnapshot> qrySnapTask = colRef.get(Source.CACHE);
    waitFor(qrySnapTask);

    QuerySnapshot qrySnap = qrySnapTask.getResult();
    assertTrue(qrySnap.isEmpty());
    assertEquals(0, qrySnap.getDocumentChanges().size());
    assertTrue(qrySnap.getMetadata().isFromCache());
    assertFalse(qrySnap.getMetadata().hasPendingWrites());
  }

  @Test
  public void getNonExistingDocWhileOfflineWithSourceEqualToCache() {
    DocumentReference docRef = testDocument();

    waitFor(docRef.getFirestore().disableNetwork());
    // Attempt to get doc. This will fail since there's nothing in cache.
    Task<DocumentSnapshot> docTask = docRef.get(Source.CACHE);
    waitForException(docTask);
  }

  @Test
  public void getDeletedDocWhileOfflineWithSourceEqualToCache() {
    DocumentReference docRef = testDocument();
    waitFor(docRef.delete());

    waitFor(docRef.getFirestore().disableNetwork());
    Task<DocumentSnapshot> docTask = docRef.get(Source.CACHE);
    waitFor(docTask);

    DocumentSnapshot doc = docTask.getResult();
    assertFalse(doc.exists());
    assertNull(doc.getData());
    assertTrue(doc.getMetadata().isFromCache());
    assertFalse(doc.getMetadata().hasPendingWrites());
  }

  @Test
  public void getNonExistingCollectionWhileOfflineWithSourceEqualToCache() {
    CollectionReference colRef = testCollection();

    waitFor(colRef.getFirestore().disableNetwork());
    Task<QuerySnapshot> qrySnapTask = colRef.get(Source.CACHE);
    waitFor(qrySnapTask);

    QuerySnapshot qrySnap = qrySnapTask.getResult();
    assertTrue(qrySnap.isEmpty());
    assertEquals(0, qrySnap.getDocumentChanges().size());
    assertTrue(qrySnap.getMetadata().isFromCache());
    assertFalse(qrySnap.getMetadata().hasPendingWrites());
  }

  @Test
  public void getNonExistingDocWhileOnlineWithSourceEqualToServer() {
    DocumentReference docRef = testDocument();

    Task<DocumentSnapshot> docTask = docRef.get(Source.SERVER);
    waitFor(docTask);

    DocumentSnapshot doc = docTask.getResult();
    assertFalse(doc.exists());
    assertFalse(doc.getMetadata().isFromCache());
    assertFalse(doc.getMetadata().hasPendingWrites());
  }

  @Test
  public void getNonExistingCollectionWhileOnlineWithSourceEqualToServer() {
    CollectionReference colRef = testCollection();

    Task<QuerySnapshot> qrySnapTask = colRef.get(Source.SERVER);
    waitFor(qrySnapTask);

    QuerySnapshot qrySnap = qrySnapTask.getResult();
    assertTrue(qrySnap.isEmpty());
    assertEquals(0, qrySnap.getDocumentChanges().size());
    assertFalse(qrySnap.getMetadata().isFromCache());
    assertFalse(qrySnap.getMetadata().hasPendingWrites());
  }

  @Test
  public void getNonExistingDocWhileOfflineWithSourceEqualToServer() {
    DocumentReference docRef = testDocument();

    waitFor(docRef.getFirestore().disableNetwork());
    Task<DocumentSnapshot> docTask = docRef.get(Source.SERVER);
    waitForException(docTask);
  }

  @Test
  public void getNonExistingCollectionWhileOfflineWithSourceEqualToServer() {
    CollectionReference colRef = testCollection();

    waitFor(colRef.getFirestore().disableNetwork());
    Task<QuerySnapshot> qrySnapTask = colRef.get(Source.SERVER);
    waitForException(qrySnapTask);
  }
}
