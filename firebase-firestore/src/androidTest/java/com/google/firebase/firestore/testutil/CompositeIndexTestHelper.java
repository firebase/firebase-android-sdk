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
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testFirestore;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.writeAllDocs;
import static com.google.firebase.firestore.util.Util.autoId;

import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.Query;
import java.util.HashMap;
import java.util.Map;

/**
 * This helper class is designed to facilitate integration testing of Firestore queries that require
 * composite indexes within a controlled testing environment.
 *
 * <p>Key Features: - Runs tests against the dedicated test collection with predefined composite
 * indexes. - Automatically associates a test ID with documents for data isolation. - Constructs
 * Firestore queries with test ID filters.
 */
public class CompositeIndexTestHelper {
  private final String testId;
  private static final String TEST_ID_FIELD = "testId";
  private static final String COMPOSITE_INDEX_TEST_COLLECTION = "composite-index-test-collection";

  public CompositeIndexTestHelper() {
    // Initialize the testId when an instance of the class is created.
    this.testId = "test-id-" + autoId();
  }

  // Runs a test with specified documents in the COMPOSITE_INDEX_TEST_COLLECTION.
  @NonNull
  public CollectionReference withTestDocs(@NonNull Map<String, Map<String, Object>> docs) {
    CollectionReference writer = testFirestore().collection(COMPOSITE_INDEX_TEST_COLLECTION);
    writeAllDocs(writer, hashDocs(docs));
    CollectionReference reader = testFirestore().collection(writer.getPath());
    return reader;
  }

  // Adds a filter on test id for a query.
  @NonNull
  public Query query(@NonNull Query query_) {
    return query_.whereEqualTo(TEST_ID_FIELD, testId);
  }

  // Hash the document key and add testId to documents created under a specific test to support data
  // isolation in parallel testing.
  private Map<String, Map<String, Object>> hashDocs(Map<String, Map<String, Object>> docs) {
    Map<String, Map<String, Object>> result = new HashMap<>();
    for (String key : docs.keySet()) {
      Map<String, Object> doc = docs.get(key);
      doc.put(TEST_ID_FIELD, this.testId);
      result.put(key + "-" + this.testId, doc);
    }
    return result;
  }

  // Hash the document keys with testId.
  public String[] toHashedIds(String[] docs) {
    String[] hashedIds = new String[docs.length];
    for (int i = 0; i < docs.length; i++) {
      hashedIds[i] = docs[i] + "-" + this.testId;
    }
    return hashedIds;
  }

  // Checks that running the query while online (against the backend/emulator) results in the same
  // as running it while offline. The expected document Ids are hashed to match the actual document
  // IDs created by the test helper.
  @NonNull
  public void checkOnlineAndOfflineResults(@NonNull Query query, @NonNull String... expectedDocs) {
    checkOnlineAndOfflineResultsMatch(query, toHashedIds(expectedDocs));
  }

  // Add a document with test id.
  @NonNull
  public Task<DocumentReference> addDoc(
      @NonNull CollectionReference collection, @NonNull Map<String, Object> data) {
    data.put(TEST_ID_FIELD, testId);
    return collection.add(data);
  }

  // Set a document with test id.
  @NonNull
  public Task<Void> setDoc(@NonNull DocumentReference document, @NonNull Map<String, Object> data) {
    data.put(TEST_ID_FIELD, testId);
    return document.set(data);
  }
}
