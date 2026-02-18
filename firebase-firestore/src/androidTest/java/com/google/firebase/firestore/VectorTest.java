// Copyright 2024 Google LLC
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

import static com.google.firebase.firestore.NumericTransformsTest.DOUBLE_EPSILON;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.checkOnlineAndOfflineResultsMatch;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testCollection;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testDocument;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.firestore.testutil.EventAccumulator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class VectorTest {

  @Test
  public void writeAndReadVectorEmbeddings() throws ExecutionException, InterruptedException {
    Map<String, VectorValue> expected = new HashMap<>();
    DocumentReference randomDoc = testDocument();

    waitFor(
        randomDoc.set(
            map(
                "vector0",
                FieldValue.vector(new double[] {0.0}),
                "vector1",
                FieldValue.vector(new double[] {1, 2, 3.99}))));
    waitFor(
        randomDoc.set(
            map(
                "vector0",
                FieldValue.vector(new double[] {0.0}),
                "vector1",
                FieldValue.vector(new double[] {1, 2, 3.99}),
                "vector2",
                FieldValue.vector(new double[] {0, 0, 0}))));
    waitFor(randomDoc.update(map("vector3", FieldValue.vector(new double[] {-1, -200, -9999}))));

    expected.put("vector0", FieldValue.vector(new double[] {0.0}));
    expected.put("vector1", FieldValue.vector(new double[] {1, 2, 3.99}));
    expected.put("vector2", FieldValue.vector(new double[] {0, 0, 0}));
    expected.put("vector3", FieldValue.vector(new double[] {-1, -200, -9999}));

    DocumentSnapshot actual = waitFor(randomDoc.get());

    assertTrue(actual.get("vector0") instanceof VectorValue);
    assertTrue(actual.get("vector1") instanceof VectorValue);
    assertTrue(actual.get("vector2") instanceof VectorValue);
    assertTrue(actual.get("vector3") instanceof VectorValue);

    assertArrayEquals(
        expected.get("vector0").toArray(),
        actual.get("vector0", VectorValue.class).toArray(),
        DOUBLE_EPSILON);
    assertArrayEquals(
        expected.get("vector1").toArray(),
        actual.get("vector1", VectorValue.class).toArray(),
        DOUBLE_EPSILON);
    assertArrayEquals(
        expected.get("vector2").toArray(),
        actual.get("vector2", VectorValue.class).toArray(),
        DOUBLE_EPSILON);
    assertArrayEquals(
        expected.get("vector3").toArray(),
        actual.get("vector3", VectorValue.class).toArray(),
        DOUBLE_EPSILON);

    assertArrayEquals(
        expected.get("vector0").toArray(),
        actual.getVectorValue("vector0").toArray(),
        DOUBLE_EPSILON);
    assertArrayEquals(
        expected.get("vector1").toArray(),
        actual.getVectorValue("vector1").toArray(),
        DOUBLE_EPSILON);
    assertArrayEquals(
        expected.get("vector2").toArray(),
        actual.getVectorValue("vector2").toArray(),
        DOUBLE_EPSILON);
    assertArrayEquals(
        expected.get("vector3").toArray(),
        actual.getVectorValue("vector3").toArray(),
        DOUBLE_EPSILON);
  }

  @Test
  public void listenToDocumentsWithVectors() throws Throwable {
    final Semaphore semaphore = new Semaphore(0);
    ListenerRegistration registration = null;
    CollectionReference randomColl = testCollection();
    DocumentReference ref = randomColl.document();
    AtomicReference<Throwable> failureMessage = new AtomicReference(null);
    int totalPermits = 5;

    try {
      registration =
          randomColl
              .whereEqualTo("purpose", "vector tests")
              .addSnapshotListener(
                  (value, error) -> {
                    try {
                      DocumentSnapshot docSnap =
                          value.isEmpty() ? null : value.getDocuments().get(0);

                      switch (semaphore.availablePermits()) {
                        case 0:
                          assertNull(docSnap);
                          ref.set(
                              map(
                                  "purpose", "vector tests",
                                  "vector0", FieldValue.vector(new double[] {0.0}),
                                  "vector1", FieldValue.vector(new double[] {1, 2, 3.99})));
                          break;
                        case 1:
                          assertNotNull(docSnap);

                          assertEquals(
                              docSnap.getVectorValue("vector0"),
                              FieldValue.vector(new double[] {0.0}));
                          assertEquals(
                              docSnap.getVectorValue("vector1"),
                              FieldValue.vector(new double[] {1, 2, 3.99}));

                          ref.set(
                              map(
                                  "purpose",
                                  "vector tests",
                                  "vector0",
                                  FieldValue.vector(new double[] {0.0}),
                                  "vector1",
                                  FieldValue.vector(new double[] {1, 2, 3.99}),
                                  "vector2",
                                  FieldValue.vector(new double[] {0, 0, 0})));
                          break;
                        case 2:
                          assertNotNull(docSnap);

                          assertEquals(
                              docSnap.getVectorValue("vector0"),
                              FieldValue.vector(new double[] {0.0}));
                          assertEquals(
                              docSnap.getVectorValue("vector1"),
                              FieldValue.vector(new double[] {1, 2, 3.99}));
                          assertEquals(
                              docSnap.getVectorValue("vector2"),
                              FieldValue.vector(new double[] {0, 0, 0}));

                          ref.update(
                              map("vector3", FieldValue.vector(new double[] {-1, -200, -999})));
                          break;
                        case 3:
                          assertNotNull(docSnap);

                          assertEquals(
                              docSnap.getVectorValue("vector0"),
                              FieldValue.vector(new double[] {0.0}));
                          assertEquals(
                              docSnap.getVectorValue("vector1"),
                              FieldValue.vector(new double[] {1, 2, 3.99}));
                          assertEquals(
                              docSnap.getVectorValue("vector2"),
                              FieldValue.vector(new double[] {0, 0, 0}));
                          assertEquals(
                              docSnap.getVectorValue("vector3"),
                              FieldValue.vector(new double[] {-1, -200, -999}));

                          ref.delete();
                          break;
                        case 4:
                          assertNull(docSnap);
                          break;
                      }
                    } catch (Throwable t) {
                      failureMessage.set(t);
                      semaphore.release(totalPermits);
                    }

                    semaphore.release();
                  });

      semaphore.acquire(totalPermits);
    } finally {
      if (registration != null) {
        registration.remove();
      }

      if (failureMessage.get() != null) {
        throw failureMessage.get();
      }
    }
  }

  /** Verifies that the SDK orders vector fields the same way as the backend. */
  @Test
  public void vectorFieldOrder() throws Exception {
    // We validate that the SDK orders the vector field the same way as the backend
    // by comparing the sort order of vector fields from a Query.get() and
    // Query.addSnapshotListener(). Query.addSnapshotListener() will return sort order
    // of the SDK, and Query.get() will return sort order of the backend.

    CollectionReference randomColl = testCollection();

    // Test data in the order that we expect the backend to sort it.
    List<Map<String, Object>> docsInOrder =
        Arrays.asList(
            map("embedding", Arrays.asList(1, 2, 3, 4, 5, 6)),
            map("embedding", Arrays.asList(100)),
            map("embedding", FieldValue.vector(new double[] {Double.NEGATIVE_INFINITY})),
            map("embedding", FieldValue.vector(new double[] {-100})),
            map("embedding", FieldValue.vector(new double[] {100})),
            map("embedding", FieldValue.vector(new double[] {Double.POSITIVE_INFINITY})),
            map("embedding", FieldValue.vector(new double[] {1, 2})),
            map("embedding", FieldValue.vector(new double[] {2, 2})),
            map("embedding", FieldValue.vector(new double[] {1, 2, 3})),
            map("embedding", FieldValue.vector(new double[] {1, 2, 3, 4})),
            map("embedding", FieldValue.vector(new double[] {1, 2, 3, 4, 5})),
            map("embedding", FieldValue.vector(new double[] {1, 2, 100, 4, 4})),
            map("embedding", FieldValue.vector(new double[] {100, 2, 3, 4, 5})),
            map("embedding", map()),
            map("embedding", map("HELLO", "WORLD")),
            map("embedding", map("hello", "world")));

    // Add docs and store doc IDs
    List<String> docIds = new ArrayList<String>();
    for (int i = 0; i < docsInOrder.size(); i++) {
      DocumentReference docRef = waitFor(randomColl.add(docsInOrder.get(i)));
      docIds.add(docRef.getId());
    }

    // Test query
    Query orderedQuery = randomColl.orderBy("embedding");

    // Run query with snapshot listener
    EventAccumulator<QuerySnapshot> eventAccumulator = new EventAccumulator<QuerySnapshot>();
    ListenerRegistration registration =
        orderedQuery.addSnapshotListener(eventAccumulator.listener());

    List<String> watchIds = new ArrayList<>();
    try {
      // Get doc IDs from snapshot listener
      QuerySnapshot querySnapshot = eventAccumulator.await();
      watchIds =
          querySnapshot.getDocuments().stream()
              .map(documentSnapshot -> documentSnapshot.getId())
              .collect(Collectors.toList());
    } finally {
      registration.remove();
    }

    // Run query with get() and get doc IDs
    QuerySnapshot querySnapshot = waitFor(orderedQuery.get());
    List<String> getIds =
        querySnapshot.getDocuments().stream().map(ds -> ds.getId()).collect(Collectors.toList());

    // Assert that get and snapshot listener requests sort docs in the same, expected order
    assertArrayEquals(docIds.toArray(new String[0]), getIds.toArray(new String[0]));
    assertArrayEquals(docIds.toArray(new String[0]), watchIds.toArray(new String[0]));
  }

  /** Verifies that the SDK orders vector fields the same way for online and offline queries*/
  @Test
  public void vectorFieldOrderOnlineAndOffline() throws Exception {
    CollectionReference randomColl = testCollection();

    // Test data in the order that we expect the backend to sort it.
    List<Map<String, Object>> docsInOrder =
        Arrays.asList(
            map("embedding", Arrays.asList(1, 2, 3, 4, 5, 6)),
            map("embedding", Arrays.asList(100)),
            map("embedding", FieldValue.vector(new double[] {Double.NEGATIVE_INFINITY})),
            map("embedding", FieldValue.vector(new double[] {-100})),
            map("embedding", FieldValue.vector(new double[] {100})),
            map("embedding", FieldValue.vector(new double[] {Double.POSITIVE_INFINITY})),
            map("embedding", FieldValue.vector(new double[] {1, 2})),
            map("embedding", FieldValue.vector(new double[] {2, 2})),
            map("embedding", FieldValue.vector(new double[] {1, 2, 3})),
            map("embedding", FieldValue.vector(new double[] {1, 2, 3, 4})),
            map("embedding", FieldValue.vector(new double[] {1, 2, 3, 4, 5})),
            map("embedding", FieldValue.vector(new double[] {1, 2, 100, 4, 4})),
            map("embedding", FieldValue.vector(new double[] {100, 2, 3, 4, 5})),
            map("embedding", map()),
            map("embedding", map("HELLO", "WORLD")),
            map("embedding", map("hello", "world")));

    // Add docs and store doc IDs
    List<String> docIds = new ArrayList<String>();
    for (int i = 0; i < docsInOrder.size(); i++) {
      DocumentReference docRef = waitFor(randomColl.add(docsInOrder.get(i)));
      docIds.add(docRef.getId());
    }

    // Test query
    Query orderedQuery = randomColl.orderBy("embedding");

    // Run query with snapshot listener
    checkOnlineAndOfflineResultsMatch(
        randomColl, orderedQuery, docIds.stream().toArray(String[]::new));
  }

  /** Verifies that the SDK filters vector fields the same way for online and offline queries*/
  @Test
  public void vectorFieldFilterOnlineAndOffline() throws Exception {
    CollectionReference randomColl = testCollection();

    // Test data in the order that we expect the backend to sort it.
    List<Map<String, Object>> docsInOrder =
        Arrays.asList(
            map("embedding", Arrays.asList(1, 2, 3, 4, 5, 6)),
            map("embedding", Arrays.asList(100)),
            map("embedding", FieldValue.vector(new double[] {Double.NEGATIVE_INFINITY})),
            map("embedding", FieldValue.vector(new double[] {-100})),
            map("embedding", FieldValue.vector(new double[] {100})),
            map("embedding", FieldValue.vector(new double[] {Double.POSITIVE_INFINITY})),
            map("embedding", FieldValue.vector(new double[] {1, 2})),
            map("embedding", FieldValue.vector(new double[] {2, 2})),
            map("embedding", FieldValue.vector(new double[] {1, 2, 3})),
            map("embedding", FieldValue.vector(new double[] {1, 2, 3, 4})),
            map("embedding", FieldValue.vector(new double[] {1, 2, 3, 4, 5})),
            map("embedding", FieldValue.vector(new double[] {1, 2, 100, 4, 4})),
            map("embedding", FieldValue.vector(new double[] {100, 2, 3, 4, 5})),
            map("embedding", map()),
            map("embedding", map("HELLO", "WORLD")),
            map("embedding", map("hello", "world")));

    // Add docs and store doc IDs
    List<String> docIds = new ArrayList<String>();
    for (int i = 0; i < docsInOrder.size(); i++) {
      DocumentReference docRef = waitFor(randomColl.add(docsInOrder.get(i)));
      docIds.add(docRef.getId());
    }

    Query orderedQueryLessThan =
        randomColl
            .orderBy("embedding")
            .whereLessThan("embedding", FieldValue.vector(new double[] {1, 2, 100, 4, 4}));
    checkOnlineAndOfflineResultsMatch(
        randomColl, orderedQueryLessThan, docIds.subList(2, 11).stream().toArray(String[]::new));

    Query orderedQueryGreaterThan =
        randomColl
            .orderBy("embedding")
            .whereGreaterThan("embedding", FieldValue.vector(new double[] {1, 2, 100, 4, 4}));
    checkOnlineAndOfflineResultsMatch(
        randomColl,
        orderedQueryGreaterThan,
        docIds.subList(12, 13).stream().toArray(String[]::new));
  }
}
