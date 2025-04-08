// Copyright 2025 Google LLC
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

import static com.google.firebase.firestore.testutil.IntegrationTestUtil.assertSDKQueryResultsConsistentWithBackend;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testCollectionOnNightly;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testCollectionWithDocsOnNightly;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.firestore.Query.Direction;
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
public class BsonTypesTest {

  @Test
  public void writeAndReadBsonTypes() throws ExecutionException, InterruptedException {
    Map<String, Object> expected = new HashMap<>();

    DocumentReference docRef =
        waitFor(
            testCollectionOnNightly()
                .add(
                    map(
                        "bsonObjectId", FieldValue.bsonObjectId("507f191e810c19729de860ea"),
                        "regex", FieldValue.regex("^foo", "i"),
                        "bsonTimestamp", FieldValue.bsonTimestamp(1, 2),
                        "bsonBinary", FieldValue.bsonBinaryData(1, new byte[] {1, 2, 3}),
                        "int32", FieldValue.int32(1),
                        "minKey", FieldValue.minKey(),
                        "maxKey", FieldValue.maxKey())));

    waitFor(
        docRef.set(
            map(
                "bsonObjectId",
                FieldValue.bsonObjectId("507f191e810c19729de860eb"),
                "regex",
                FieldValue.regex("^foo", "m"),
                "bsonTimestamp",
                FieldValue.bsonTimestamp(1, 3)),
            SetOptions.merge()));

    waitFor(docRef.update(map("int32", FieldValue.int32(2))));

    expected.put("bsonObjectId", FieldValue.bsonObjectId("507f191e810c19729de860eb"));
    expected.put("regex", FieldValue.regex("^foo", "m"));
    expected.put("bsonTimestamp", FieldValue.bsonTimestamp(1, 3));
    expected.put("bsonBinary", FieldValue.bsonBinaryData(1, new byte[] {1, 2, 3}));
    expected.put("int32", FieldValue.int32(2));
    expected.put("minKey", FieldValue.minKey());
    expected.put("maxKey", FieldValue.maxKey());

    DocumentSnapshot actual = waitFor(docRef.get());

    assertTrue(actual.get("bsonObjectId") instanceof BsonObjectId);
    assertTrue(actual.get("regex") instanceof RegexValue);
    assertTrue(actual.get("bsonTimestamp") instanceof BsonTimestamp);
    assertTrue(actual.get("bsonBinary") instanceof BsonBinaryData);
    assertTrue(actual.get("int32") instanceof Int32Value);
    assertTrue(actual.get("minKey") instanceof MinKey);
    assertTrue(actual.get("maxKey") instanceof MaxKey);
    assertEquals(expected, actual.getData());
  }

  @Test
  public void listenToDocumentsWithBsonTypes() throws Throwable {
    final Semaphore semaphore = new Semaphore(0);
    ListenerRegistration registration = null;
    CollectionReference randomColl = testCollectionOnNightly();
    DocumentReference ref = randomColl.document();
    AtomicReference<Throwable> failureMessage = new AtomicReference(null);
    int totalPermits = 5;

    try {
      registration =
          randomColl
              .whereEqualTo("purpose", "Bson types tests")
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
                                  "purpose", "Bson types tests",
                                  "bsonObjectId",
                                      FieldValue.bsonObjectId("507f191e810c19729de860ea"),
                                  "regex", FieldValue.regex("^foo", "i"),
                                  "bsonTimestamp", FieldValue.bsonTimestamp(1, 2),
                                  "bsonBinary", FieldValue.bsonBinaryData(1, new byte[] {1, 2, 3}),
                                  "int32", FieldValue.int32(1),
                                  "minKey", FieldValue.minKey(),
                                  "maxKey", FieldValue.maxKey()));
                          break;
                        case 1:
                          assertNotNull(docSnap);

                          assertEquals(
                              docSnap.getBsonBinaryData("bsonBinary"),
                              FieldValue.bsonBinaryData(1, new byte[] {1, 2, 3}));
                          assertEquals(
                              docSnap.getBsonObjectId("bsonObjectId"),
                              FieldValue.bsonObjectId("507f191e810c19729de860ea"));
                          assertEquals(
                              docSnap.getRegexValue("regex"), FieldValue.regex("^foo", "i"));
                          assertEquals(
                              docSnap.getBsonTimestamp("bsonTimestamp"),
                              FieldValue.bsonTimestamp(1, 2));
                          assertEquals(docSnap.getInt32Value("int32"), FieldValue.int32(1));
                          assertEquals(docSnap.getMinKey("minKey"), FieldValue.minKey());
                          assertEquals(docSnap.getMaxKey("maxKey"), FieldValue.maxKey());

                          ref.set(
                              map(
                                  "purpose",
                                  "Bson types tests",
                                  "bsonObjectId",
                                  FieldValue.bsonObjectId("507f191e810c19729de860eb"),
                                  "regex",
                                  FieldValue.regex("^foo", "m"),
                                  "bsonTimestamp",
                                  FieldValue.bsonTimestamp(1, 3)),
                              SetOptions.merge());
                          break;
                        case 2:
                          assertNotNull(docSnap);

                          assertEquals(
                              docSnap.getBsonObjectId("bsonObjectId"),
                              FieldValue.bsonObjectId("507f191e810c19729de860eb"));
                          assertEquals(
                              docSnap.getRegexValue("regex"), FieldValue.regex("^foo", "m"));
                          assertEquals(
                              docSnap.getBsonTimestamp("bsonTimestamp"),
                              FieldValue.bsonTimestamp(1, 3));

                          ref.update(map("int32", FieldValue.int32(2)));
                          break;
                        case 3:
                          assertNotNull(docSnap);

                          assertEquals(docSnap.getInt32Value("int32"), FieldValue.int32(2));

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

  /** Verifies that the SDK orders Bson type fields the same way as the backend by comparing the result of Query.get() and
   * Query.addSnapshotListener(), as well as the online and offline results */
  @Test
  public void filterAndOrderBsonObjectIds() throws Exception {
    Map<String, Map<String, Object>> docs =
        map(
            "a",
            map("key", FieldValue.bsonObjectId("507f191e810c19729de860ea")),
            "b",
            map("key", FieldValue.bsonObjectId("507f191e810c19729de860eb")),
            "c",
            map("key", FieldValue.bsonObjectId("507f191e810c19729de860ec")));
    CollectionReference randomColl = testCollectionWithDocsOnNightly(docs);

    Query orderedQuery =
        randomColl
            .orderBy("key", Direction.DESCENDING)
            .whereGreaterThan("key", FieldValue.bsonObjectId("507f191e810c19729de860ea"));

    assertSDKQueryResultsConsistentWithBackend(orderedQuery, docs, Arrays.asList("c", "b"));
  }

  @Test
  public void filterAndOrderBsonTimestamps() throws Exception {
    Map<String, Map<String, Object>> docs =
        map(
            "a",
            map("key", FieldValue.bsonTimestamp(1, 1)),
            "b",
            map("key", FieldValue.bsonTimestamp(1, 2)),
            "c",
            map("key", FieldValue.bsonTimestamp(2, 1)));
    CollectionReference randomColl = testCollectionWithDocsOnNightly(docs);

    Query orderedQuery =
        randomColl
            .orderBy("key", Direction.DESCENDING)
            .whereGreaterThan("key", FieldValue.bsonTimestamp(1, 1));

    assertSDKQueryResultsConsistentWithBackend(orderedQuery, docs, Arrays.asList("c", "b"));
  }

  @Test
  public void filterAndOrderBsonBinaryData() throws Exception {
    Map<String, Map<String, Object>> docs =
        map(
            "a",
            map("key", FieldValue.bsonBinaryData(1, new byte[] {1, 2, 3})),
            "b",
            map("key", FieldValue.bsonBinaryData(1, new byte[] {1, 2, 4})),
            "c",
            map("key", FieldValue.bsonBinaryData(2, new byte[] {1, 2, 2})));
    CollectionReference randomColl = testCollectionWithDocsOnNightly(docs);

    Query orderedQuery =
        randomColl
            .orderBy("key", Direction.DESCENDING)
            .whereGreaterThan("key", FieldValue.bsonBinaryData(1, new byte[] {1, 2, 3}));

    assertSDKQueryResultsConsistentWithBackend(orderedQuery, docs, Arrays.asList("c", "b"));
  }

  @Test
  public void filterAndOrderRegex() throws Exception {
    Map<String, Map<String, Object>> docs =
        map(
            "a", map("key", FieldValue.regex("^bar", "i")),
            "b", map("key", FieldValue.regex("^bar", "m")),
            "c", map("key", FieldValue.regex("^baz", "i")));
    CollectionReference randomColl = testCollectionWithDocsOnNightly(docs);

    Query orderedQuery =
        randomColl
            .orderBy("key", Direction.DESCENDING)
            .whereGreaterThan("key", FieldValue.regex("^bar", "i"));

    assertSDKQueryResultsConsistentWithBackend(orderedQuery, docs, Arrays.asList("c", "b"));
  }

  @Test
  public void filterAndOrderInt32() throws Exception {
    Map<String, Map<String, Object>> docs =
        map(
            "a", map("key", FieldValue.int32(-1)),
            "b", map("key", FieldValue.int32(1)),
            "c", map("key", FieldValue.int32(2)));
    CollectionReference randomColl = testCollectionWithDocsOnNightly(docs);

    Query orderedQuery =
        randomColl
            .orderBy("key", Direction.DESCENDING)
            .whereGreaterThanOrEqualTo("key", FieldValue.int32(1));

    assertSDKQueryResultsConsistentWithBackend(orderedQuery, docs, Arrays.asList("c", "b"));
  }

  @Test
  public void filterAndOrderMinKey() throws Exception {
    Map<String, Map<String, Object>> docs =
        map(
            "a", map("key", FieldValue.minKey()),
            "b", map("key", FieldValue.minKey()),
            "c", map("key", FieldValue.maxKey()));
    CollectionReference randomColl = testCollectionWithDocsOnNightly(docs);

    Query orderedQuery =
        randomColl
            .orderBy(
                "key",
                Direction
                    .DESCENDING) // minKeys are equal, would sort by documentId as secondary order
            .whereEqualTo("key", FieldValue.minKey());

    assertSDKQueryResultsConsistentWithBackend(orderedQuery, docs, Arrays.asList("b", "a"));
  }

  @Test
  public void filterAndOrderMaxKey() throws Exception {
    Map<String, Map<String, Object>> docs =
        map(
            "a", map("key", FieldValue.minKey()),
            "b", map("key", FieldValue.maxKey()),
            "c", map("key", FieldValue.maxKey()));
    CollectionReference randomColl = testCollectionWithDocsOnNightly(docs);

    Query orderedQuery =
        randomColl
            .orderBy(
                "key",
                Direction
                    .DESCENDING) // maxKeys are equal, would sort by documentId as secondary order
            .whereEqualTo("key", FieldValue.maxKey());

    assertSDKQueryResultsConsistentWithBackend(orderedQuery, docs, Arrays.asList("c", "b"));
  }

  @Test
  public void orderBsonTypesTogether() throws Exception {
    Map<String, Map<String, Object>> docs =
        map(
            "bsonObjectId1",
            map("key", FieldValue.bsonObjectId("507f191e810c19729de860ea")),
            "bsonObjectId2",
            map("key", FieldValue.bsonObjectId("507f191e810c19729de860eb")),
            "bsonObjectId3",
            map("key", FieldValue.bsonObjectId("407f191e810c19729de860ea")),
            "regex1",
            map("key", FieldValue.regex("^bar", "m")),
            "regex2",
            map("key", FieldValue.regex("^bar", "i")),
            "regex3",
            map("key", FieldValue.regex("^baz", "i")),
            "bsonTimestamp1",
            map("key", FieldValue.bsonTimestamp(2, 0)),
            "bsonTimestamp2",
            map("key", FieldValue.bsonTimestamp(1, 2)),
            "bsonTimestamp3",
            map("key", FieldValue.bsonTimestamp(1, 1)),
            "bsonBinary1",
            map("key", FieldValue.bsonBinaryData(1, new byte[] {1, 2, 3})),
            "bsonBinary2",
            map("key", FieldValue.bsonBinaryData(1, new byte[] {1, 2, 4})),
            "bsonBinary3",
            map("key", FieldValue.bsonBinaryData(2, new byte[] {1, 2, 2})),
            "int32Value1",
            map("key", FieldValue.int32(-1)),
            "int32Value2",
            map("key", FieldValue.int32(1)),
            "int32Value3",
            map("key", FieldValue.int32(0)),
            "minKey1",
            map("key", FieldValue.minKey()),
            "minKey2",
            map("key", FieldValue.minKey()),
            "maxKey1",
            map("key", FieldValue.maxKey()),
            "maxKey2",
            map("key", FieldValue.maxKey()));
    CollectionReference randomColl = testCollectionWithDocsOnNightly(docs);

    Query orderedQuery = randomColl.orderBy("key", Direction.DESCENDING);
    List<String> expectedDocs =
        Arrays.asList(
            "maxKey2",
            "maxKey1",
            "regex3",
            "regex1",
            "regex2",
            "bsonObjectId2",
            "bsonObjectId1",
            "bsonObjectId3",
            "bsonBinary3",
            "bsonBinary2",
            "bsonBinary1",
            "bsonTimestamp1",
            "bsonTimestamp2",
            "bsonTimestamp3",
            "int32Value2",
            "int32Value3",
            "int32Value1",
            "minKey2",
            "minKey1");

    assertSDKQueryResultsConsistentWithBackend(orderedQuery, docs, expectedDocs);
  }

  @Test
  public void canRunTransactionsOnDocumentsWithBsonTypes() throws Exception {
    Map<String, Map<String, Object>> docs =
        map(
            "a", map("key", FieldValue.bsonObjectId("507f191e810c19729de860ea")),
            "b", map("key", FieldValue.regex("^foo", "i")),
            "c", map("key", FieldValue.bsonBinaryData(1, new byte[] {1, 2, 3})));
    CollectionReference randomColl = testCollectionWithDocsOnNightly(docs);

    waitFor(
        randomColl.firestore.runTransaction(
            transaction -> {
              DocumentSnapshot docSnap = transaction.get(randomColl.document("a"));
              assertEquals(
                  docSnap.getBsonObjectId("key"),
                  FieldValue.bsonObjectId("507f191e810c19729de860ea"));
              transaction.update(randomColl.document("b"), "key", FieldValue.regex("^bar", "i"));
              transaction.delete(randomColl.document("c"));
              return null;
            }));

    QuerySnapshot getSnapshot = waitFor(randomColl.get());

    List<String> getSnapshotDocIds =
        getSnapshot.getDocuments().stream().map(ds -> ds.getId()).collect(Collectors.toList());

    assertTrue(getSnapshotDocIds.equals(Arrays.asList("a", "b")));
    assertEquals(
        getSnapshot.getDocuments().get(0).getBsonObjectId("key"),
        FieldValue.bsonObjectId("507f191e810c19729de860ea"));
    assertEquals(
        getSnapshot.getDocuments().get(1).getRegexValue("key"), FieldValue.regex("^bar", "i"));
  }
}
