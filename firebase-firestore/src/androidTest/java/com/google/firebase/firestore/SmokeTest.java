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
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.Query.Direction;
import com.google.firebase.firestore.testutil.EventAccumulator;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SmokeTest {

  @After
  public void tearDown() {
    IntegrationTestUtil.tearDown();
  }

  @Test
  public void testCanWriteADocument() {
    Map<String, Object> testData =
        map("name", "Patryk", "message", "We are actually writing data!");
    CollectionReference collection = testCollection();
    waitFor(collection.add(testData));
  }

  @Test
  public void testCanReadAWrittenDocument() {
    Map<String, Object> testData = map("foo", "bar");
    CollectionReference collection = testCollection();

    DocumentReference newRef = waitFor(collection.add(testData));
    DocumentSnapshot result = waitFor(newRef.get());
    assertEquals(testData, result.getData());
  }

  @Test
  public void testObservesExistingDocument() {
    final Map<String, Object> testData = map("foo", "bar");
    CollectionReference collection = testCollection();
    DocumentReference writerRef = collection.document();
    DocumentReference readerRef = collection.document(writerRef.getId());
    waitFor(writerRef.set(testData));
    EventAccumulator<DocumentSnapshot> accumulator = new EventAccumulator<>();
    ListenerRegistration listener =
        readerRef.addSnapshotListener(MetadataChanges.INCLUDE, accumulator.listener());
    DocumentSnapshot doc = accumulator.await();
    assertEquals(testData, doc.getData());
    listener.remove();
  }

  @Test
  public void testObservesNewDocument() {
    CollectionReference collection = testCollection();
    DocumentReference writerRef = collection.document();
    DocumentReference readerRef = collection.document(writerRef.getId());
    EventAccumulator<DocumentSnapshot> accumulator = new EventAccumulator<>();
    ListenerRegistration listener =
        readerRef.addSnapshotListener(MetadataChanges.INCLUDE, accumulator.listener());
    DocumentSnapshot doc = accumulator.await();
    assertFalse(doc.exists());
    final Map<String, Object> testData = map("foo", "bar");
    waitFor(writerRef.set(testData));
    doc = accumulator.await();
    assertEquals(testData, doc.getData());
    assertTrue(doc.getMetadata().hasPendingWrites());
    doc = accumulator.await();
    assertEquals(testData, doc.getData());
    assertFalse(doc.getMetadata().hasPendingWrites());
    listener.remove();
  }

  @Test
  public void testWillFireValueEventsForEmptyCollections() {
    CollectionReference collection = testCollection("empty-collection");
    EventAccumulator<QuerySnapshot> accumulator = new EventAccumulator<>();
    ListenerRegistration listener =
        collection.addSnapshotListener(MetadataChanges.INCLUDE, accumulator.listener());
    QuerySnapshot querySnap = accumulator.await();
    assertEquals(0, querySnap.size());
    assertTrue(querySnap.isEmpty());
    listener.remove();
  }

  @Test
  public void testGetCollectionQuery() {
    Map<String, Map<String, Object>> testData =
        map(
            "1", map("name", "Patryk", "message", "Real data, yo!"),
            "2", map("name", "Gil", "message", "Yep!"),
            "3", map("name", "Jonny", "message", "Back to work!"));
    CollectionReference collection = testCollection();
    List<Task<Void>> tasks = new ArrayList<>();
    for (Map.Entry<String, Map<String, Object>> entry : testData.entrySet()) {
      tasks.add(collection.document(entry.getKey()).set(entry.getValue()));
    }
    waitFor(Tasks.whenAll(tasks));
    QuerySnapshot set = waitFor(collection.get());
    List<DocumentSnapshot> documents = set.getDocuments();
    assertFalse(set.isEmpty());
    assertEquals(3, documents.size());
    assertEquals(testData.get("1"), documents.get(0).getData());
    assertEquals(testData.get("2"), documents.get(1).getData());
    assertEquals(testData.get("3"), documents.get(2).getData());
  }

  // TODO : temporarily disable failed test
  // This broken because it requires a composite index on filter,sort
  @Test
  @Ignore
  public void testGetCollectionQueryByFieldAndOrdering() {
    Map<String, Map<String, Object>> testData =
        map(
            "1", map("sort", 1.0, "filter", true, "key", "1"),
            "2", map("sort", 2.0, "filter", true, "key", "2"),
            "3", map("sort", 2.0, "filter", true, "key", "3"),
            "4", map("sort", 3.0, "filter", false, "key", "4"));
    CollectionReference collection = testCollection();
    List<Task<Void>> tasks = new ArrayList<>();
    for (Map.Entry<String, Map<String, Object>> entry : testData.entrySet()) {
      tasks.add(collection.document(entry.getKey()).set(entry.getValue()));
    }
    waitFor(Tasks.whenAll(tasks));
    Query query = collection.whereEqualTo("filter", true).orderBy("sort", Direction.DESCENDING);
    QuerySnapshot set = waitFor(query.get());
    List<DocumentSnapshot> documents = set.getDocuments();
    assertEquals(3, documents.size());
    assertEquals(testData.get("2"), documents.get(0).getData());
    assertEquals(testData.get("3"), documents.get(1).getData());
    assertEquals(testData.get("1"), documents.get(2).getData());
  }
}
