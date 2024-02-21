// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.firestore.testutil;

import static com.google.firebase.firestore.testutil.IntegrationTestUtil.checkOnlineAndOfflineResultsMatch;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.querySnapshotToIds;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testFirestore;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.writeAllDocs;
import static com.google.firebase.firestore.util.Util.autoId;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This helper class is designed to facilitate integration testing of Firestore queries that require
 * composite indexes within a controlled testing environment.
 *
 * <p>Key Features:
 *
 * <ul>
 *   <li>Runs tests against the dedicated test collection with predefined composite indexes.
 *   <li>Automatically associates a test ID with documents for data isolation.
 *   <li>Utilizes TTL policy for automatic test data cleanup.
 *   <li>Constructs Firestore queries with test ID filters.
 * </ul>
 */
public class CompositeIndexTestHelper {
  private final String testId;
  private static final String TEST_ID_FIELD = "testId";
  private static final String TTL_FIELD = "expireAt";
  private static final String COMPOSITE_INDEX_TEST_COLLECTION = "composite-index-test-collection";

  // Creates a new instance of the CompositeIndexTestHelper class, with a unique test
  // identifier for data isolation.
  public CompositeIndexTestHelper() {
    this.testId = "test-id-" + autoId();
  }

  @NonNull
  public CollectionReference withTestCollection() {
    return testFirestore().collection(COMPOSITE_INDEX_TEST_COLLECTION);
  }

  // Runs a test with specified documents in the COMPOSITE_INDEX_TEST_COLLECTION.
  @NonNull
  public CollectionReference withTestDocs(@NonNull Map<String, Map<String, Object>> docs) {
    CollectionReference writer = withTestCollection();
    writeAllDocs(writer, prepareTestDocuments(docs));
    CollectionReference reader = testFirestore().collection(writer.getPath());
    return reader;
  }

  // Hash the document key with testId.
  private String toHashedId(String docId) {
    return docId + '-' + testId;
  }

  private String[] toHashedIds(String[] docs) {
    String[] hashedIds = new String[docs.length];
    for (int i = 0; i < docs.length; i++) {
      hashedIds[i] = toHashedId(docs[i]);
    }
    return hashedIds;
  }

  // Adds test-specific fields to a document, including the testId and expiration date.
  public Map<String, Object> addTestSpecificFieldsToDoc(Map<String, Object> doc) {
    Map<String, Object> updatedDoc = new HashMap<>(doc);
    updatedDoc.put(TEST_ID_FIELD, testId);
    updatedDoc.put(
        TTL_FIELD,
        new Timestamp( // Expire test data after 24 hours
            Timestamp.now().getSeconds() + 24 * 60 * 60, Timestamp.now().getNanoseconds()));
    return updatedDoc;
  }

  // Remove test-specific fields from a document.
  private Map<String, Object> removeTestSpecificFieldsFromDoc(Map<String, Object> doc) {
    doc.remove(TTL_FIELD);
    doc.remove(TEST_ID_FIELD);
    return doc;
  }

  // Helper method to hash document keys and add test-specific fields for the provided documents.
  private Map<String, Map<String, Object>> prepareTestDocuments(
      Map<String, Map<String, Object>> docs) {
    Map<String, Map<String, Object>> result = new HashMap<>();
    for (String key : docs.keySet()) {
      Map<String, Object> doc = addTestSpecificFieldsToDoc(docs.get(key));
      result.put(toHashedId(key), doc);
    }
    return result;
  }

  // Asserts that the result of running the query while online (against the backend/emulator) is
  // the same as running it while offline. The expected document Ids are hashed to match the
  // actual document IDs created by the test helper.
  @NonNull
  public void assertOnlineAndOfflineResultsMatch(
      @NonNull Query query, @NonNull String... expectedDocs) {
    checkOnlineAndOfflineResultsMatch(query, toHashedIds(expectedDocs));
  }

  // Asserts that the IDs in the query snapshot matches the expected Ids. The expected document
  // IDs are hashed to match the actual document IDs created by the test helper.
  @NonNull
  public void assertSnapshotResultIdsMatch(
      @NonNull QuerySnapshot snapshot, @NonNull String... expectedIds) {
    assertEquals(querySnapshotToIds(snapshot), asList(toHashedIds(expectedIds)));
  }

  // Adds a filter on test id for a query.
  @NonNull
  public Query query(@NonNull Query query_) {
    return query_.whereEqualTo(TEST_ID_FIELD, testId);
  }

  // Get document reference from a document key.
  @NonNull
  public DocumentReference getDocRef(
      @NonNull CollectionReference collection, @NonNull String docId) {
    if (!docId.contains("test-id-")) {
      docId = toHashedId(docId);
    }
    return collection.document(docId);
  }

  // Adds a document to a Firestore collection with test-specific fields.
  @NonNull
  public Task<DocumentReference> addDoc(
      @NonNull CollectionReference collection, @NonNull Map<String, Object> data) {
    return collection.add(addTestSpecificFieldsToDoc(data));
  }

  // Sets a document in Firestore with test-specific fields.
  @NonNull
  public Task<Void> setDoc(@NonNull DocumentReference document, @NonNull Map<String, Object> data) {
    return document.set(addTestSpecificFieldsToDoc(data));
  }

  @NonNull
  public Task<Void> updateDoc(
      @NonNull DocumentReference document, @NonNull Map<String, Object> data) {
    return document.update(data);
  }

  @NonNull
  public Task<Void> deleteDoc(@NonNull DocumentReference document) {
    return document.delete();
  }

  // Retrieve a single document from Firestore with test-specific fields removed.
  // TODO(composite-index-testing) Return sanitized DocumentSnapshot instead of its data.
  @NonNull
  public Map<String, Object> getSanitizedDocumentData(@NonNull DocumentReference document) {
    DocumentSnapshot docSnapshot = waitFor(document.get());
    return removeTestSpecificFieldsFromDoc(docSnapshot.getData());
  }

  // Retrieve multiple documents from Firestore with test-specific fields removed.
  // TODO(composite-index-testing) Return sanitized QuerySnapshot instead of its data.
  @NonNull
  public List<Map<String, Object>> getSanitizedQueryData(@NonNull Query query_) {
    QuerySnapshot querySnapshot = waitFor(query(query_).get());
    List<Map<String, Object>> res = new ArrayList<>();
    for (DocumentSnapshot doc : querySnapshot) {
      res.add(removeTestSpecificFieldsFromDoc(doc.getData()));
    }
    return res;
  }
}
