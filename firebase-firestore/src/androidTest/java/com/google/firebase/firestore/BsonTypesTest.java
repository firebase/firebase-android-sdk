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
import static java.lang.Double.NaN;
import static java.lang.Double.POSITIVE_INFINITY;
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
                        "bsonObjectId", new BsonObjectId("507f191e810c19729de860ea"),
                        "regex", new RegexValue("^foo", "i"),
                        "bsonTimestamp", new BsonTimestamp(1, 2),
                        "bsonBinary", BsonBinaryData.fromBytes(1, new byte[] {1, 2, 3}),
                        "int32", new Int32Value(1),
                        "decimal128", new Decimal128Value("1.2e3"),
                        "minKey", MinKey.instance(),
                        "maxKey", MaxKey.instance())));

    waitFor(
        docRef.set(
            map(
                "bsonObjectId",
                new BsonObjectId("507f191e810c19729de860eb"),
                "regex",
                new RegexValue("^foo", "m"),
                "bsonTimestamp",
                new BsonTimestamp(1, 3)),
            SetOptions.merge()));

    waitFor(docRef.update(map("int32", new Int32Value(2))));

    expected.put("bsonObjectId", new BsonObjectId("507f191e810c19729de860eb"));
    expected.put("regex", new RegexValue("^foo", "m"));
    expected.put("bsonTimestamp", new BsonTimestamp(1, 3));
    expected.put("bsonBinary", BsonBinaryData.fromBytes(1, new byte[] {1, 2, 3}));
    expected.put("int32", new Int32Value(2));
    expected.put("decimal128", new Decimal128Value("1.2e3"));
    expected.put("minKey", MinKey.instance());
    expected.put("maxKey", MaxKey.instance());

    DocumentSnapshot actual = waitFor(docRef.get());

    assertTrue(actual.get("bsonObjectId") instanceof BsonObjectId);
    assertTrue(actual.get("regex") instanceof RegexValue);
    assertTrue(actual.get("bsonTimestamp") instanceof BsonTimestamp);
    assertTrue(actual.get("bsonBinary") instanceof BsonBinaryData);
    assertTrue(actual.get("int32") instanceof Int32Value);
    assertTrue(actual.get("decimal128") instanceof Decimal128Value);
    assertTrue(actual.get("minKey") instanceof MinKey);
    assertTrue(actual.get("maxKey") instanceof MaxKey);
    assertEquals(expected, actual.getData());
  }

  @Test
  public void writeAndReadBsonTypeOffline() throws ExecutionException, InterruptedException {
    CollectionReference randomColl = testCollectionOnNightly();
    DocumentReference docRef = randomColl.document();

    waitFor(randomColl.getFirestore().disableNetwork());

    // Adding docs to cache, do not wait for promise to resolve.
    Map<String, Object> expected = new HashMap<>();
    docRef.set(
        map(
            "bsonObjectId",
            new BsonObjectId("507f191e810c19729de860ea"),
            "regex",
            new RegexValue("^foo", "i"),
            "bsonTimestamp",
            new BsonTimestamp(1, 2),
            "bsonBinary",
            BsonBinaryData.fromBytes(1, new byte[] {1, 2, 3}),
            "int32",
            new Int32Value(1),
            "decimal128",
            new Decimal128Value("1.2e3"),
            "minKey",
            MinKey.instance(),
            "maxKey",
            MaxKey.instance()));

    docRef.update(
        map(
            "bsonObjectId",
            new BsonObjectId("507f191e810c19729de860eb"),
            "regex",
            new RegexValue("^foo", "m"),
            "bsonTimestamp",
            new BsonTimestamp(1, 3)));

    expected.put("bsonObjectId", new BsonObjectId("507f191e810c19729de860eb"));
    expected.put("regex", new RegexValue("^foo", "m"));
    expected.put("bsonTimestamp", new BsonTimestamp(1, 3));
    expected.put("bsonBinary", BsonBinaryData.fromBytes(1, new byte[] {1, 2, 3}));
    expected.put("int32", new Int32Value(1));
    expected.put("decimal128", new Decimal128Value("1.2e3"));
    expected.put("minKey", MinKey.instance());
    expected.put("maxKey", MaxKey.instance());

    DocumentSnapshot actual = waitFor(docRef.get());

    assertTrue(actual.get("bsonObjectId") instanceof BsonObjectId);
    assertTrue(actual.get("regex") instanceof RegexValue);
    assertTrue(actual.get("bsonTimestamp") instanceof BsonTimestamp);
    assertTrue(actual.get("bsonBinary") instanceof BsonBinaryData);
    assertTrue(actual.get("int32") instanceof Int32Value);
    assertTrue(actual.get("decimal128") instanceof Decimal128Value);
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
                                  "purpose",
                                  "Bson types tests",
                                  "bsonObjectId",
                                  new BsonObjectId("507f191e810c19729de860ea"),
                                  "regex",
                                  new RegexValue("^foo", "i"),
                                  "bsonTimestamp",
                                  new BsonTimestamp(1, 2),
                                  "bsonBinary",
                                  BsonBinaryData.fromBytes(1, new byte[] {1, 2, 3}),
                                  "int32",
                                  new Int32Value(1),
                                  "decimal128",
                                  new Decimal128Value("1.2e3"),
                                  "minKey",
                                  MinKey.instance(),
                                  "maxKey",
                                  MaxKey.instance()));
                          break;
                        case 1:
                          assertNotNull(docSnap);

                          assertEquals(
                              docSnap.getBsonBinaryData("bsonBinary"),
                              BsonBinaryData.fromBytes(1, new byte[] {1, 2, 3}));
                          assertEquals(
                              docSnap.getBsonObjectId("bsonObjectId"),
                              new BsonObjectId("507f191e810c19729de860ea"));
                          assertEquals(docSnap.getRegexValue("regex"), new RegexValue("^foo", "i"));
                          assertEquals(
                              docSnap.getBsonTimestamp("bsonTimestamp"), new BsonTimestamp(1, 2));
                          assertEquals(docSnap.getInt32Value("int32"), new Int32Value(1));
                          assertEquals(
                              docSnap.getDecimal128Value("decimal128"),
                              new Decimal128Value("1.2e3"));
                          assertEquals(docSnap.getMinKey("minKey"), MinKey.instance());
                          assertEquals(docSnap.getMaxKey("maxKey"), MaxKey.instance());

                          ref.set(
                              map(
                                  "purpose",
                                  "Bson types tests",
                                  "bsonObjectId",
                                  new BsonObjectId("507f191e810c19729de860eb"),
                                  "regex",
                                  new RegexValue("^foo", "m"),
                                  "bsonTimestamp",
                                  new BsonTimestamp(1, 3)),
                              SetOptions.merge());
                          break;
                        case 2:
                          assertNotNull(docSnap);

                          assertEquals(
                              docSnap.getBsonObjectId("bsonObjectId"),
                              new BsonObjectId("507f191e810c19729de860eb"));
                          assertEquals(docSnap.getRegexValue("regex"), new RegexValue("^foo", "m"));
                          assertEquals(
                              docSnap.getBsonTimestamp("bsonTimestamp"), new BsonTimestamp(1, 3));

                          ref.update(map("int32", new Int32Value(2)));
                          break;
                        case 3:
                          assertNotNull(docSnap);

                          assertEquals(docSnap.getInt32Value("int32"), new Int32Value(2));

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

  @Test
  public void filterAndOrderBsonObjectIds() throws Exception {
    Map<String, Map<String, Object>> docs =
        map(
            "a",
            map("key", new BsonObjectId("507f191e810c19729de860ea")),
            "b",
            map("key", new BsonObjectId("507f191e810c19729de860eb")),
            "c",
            map("key", new BsonObjectId("507f191e810c19729de860ec")));
    CollectionReference randomColl = testCollectionWithDocsOnNightly(docs);

    Query orderedQuery =
        randomColl
            .orderBy("key", Direction.DESCENDING)
            .whereGreaterThan("key", new BsonObjectId("507f191e810c19729de860ea"));
    assertSDKQueryResultsConsistentWithBackend(
        randomColl, orderedQuery, docs, Arrays.asList("c", "b"));

    orderedQuery =
        randomColl
            .orderBy("key", Direction.DESCENDING)
            .whereNotEqualTo("key", new BsonObjectId("507f191e810c19729de860eb"));
    assertSDKQueryResultsConsistentWithBackend(
        randomColl, orderedQuery, docs, Arrays.asList("c", "a"));
  }

  @Test
  public void filterAndOrderBsonTimestamps() throws Exception {
    Map<String, Map<String, Object>> docs =
        map(
            "a",
            map("key", new BsonTimestamp(1, 1)),
            "b",
            map("key", new BsonTimestamp(1, 2)),
            "c",
            map("key", new BsonTimestamp(2, 1)));
    CollectionReference randomColl = testCollectionWithDocsOnNightly(docs);

    Query orderedQuery =
        randomColl
            .orderBy("key", Direction.DESCENDING)
            .whereGreaterThan("key", new BsonTimestamp(1, 1));

    assertSDKQueryResultsConsistentWithBackend(
        randomColl, orderedQuery, docs, Arrays.asList("c", "b"));

    orderedQuery =
        randomColl
            .orderBy("key", Direction.DESCENDING)
            .whereNotEqualTo("key", new BsonTimestamp(1, 2));

    assertSDKQueryResultsConsistentWithBackend(
        randomColl, orderedQuery, docs, Arrays.asList("c", "a"));
  }

  @Test
  public void filterAndOrderBsonBinaryData() throws Exception {
    Map<String, Map<String, Object>> docs =
        map(
            "a",
            map("key", BsonBinaryData.fromBytes(1, new byte[] {1, 2, 3})),
            "b",
            map("key", BsonBinaryData.fromBytes(1, new byte[] {1, 2, 4})),
            "c",
            map("key", BsonBinaryData.fromBytes(2, new byte[] {1, 2, 2})));
    CollectionReference randomColl = testCollectionWithDocsOnNightly(docs);

    Query orderedQuery =
        randomColl
            .orderBy("key", Direction.DESCENDING)
            .whereGreaterThan("key", BsonBinaryData.fromBytes(1, new byte[] {1, 2, 3}));

    assertSDKQueryResultsConsistentWithBackend(
        randomColl, orderedQuery, docs, Arrays.asList("c", "b"));

    orderedQuery =
        randomColl
            .orderBy("key", Direction.DESCENDING)
            .whereNotEqualTo("key", BsonBinaryData.fromBytes(1, new byte[] {1, 2, 4}));

    assertSDKQueryResultsConsistentWithBackend(
        randomColl, orderedQuery, docs, Arrays.asList("c", "a"));
  }

  @Test
  public void filterAndOrderRegex() throws Exception {
    Map<String, Map<String, Object>> docs =
        map(
            "a", map("key", new RegexValue("^bar", "i")),
            "b", map("key", new RegexValue("^bar", "m")),
            "c", map("key", new RegexValue("^baz", "i")));
    CollectionReference randomColl = testCollectionWithDocsOnNightly(docs);

    Query orderedQuery =
        randomColl
            .orderBy("key", Direction.DESCENDING)
            .whereGreaterThan("key", new RegexValue("^bar", "i"));

    assertSDKQueryResultsConsistentWithBackend(
        randomColl, orderedQuery, docs, Arrays.asList("c", "b"));

    orderedQuery =
        randomColl
            .orderBy("key", Direction.DESCENDING)
            .whereNotEqualTo("key", new RegexValue("^bar", "m"));

    assertSDKQueryResultsConsistentWithBackend(
        randomColl, orderedQuery, docs, Arrays.asList("c", "a"));
  }

  @Test
  public void filterAndOrderInt32() throws Exception {
    Map<String, Map<String, Object>> docs =
        map(
            "a", map("key", new Int32Value(-1)),
            "b", map("key", new Int32Value(1)),
            "c", map("key", new Int32Value(2)));
    CollectionReference randomColl = testCollectionWithDocsOnNightly(docs);

    Query orderedQuery =
        randomColl.orderBy("key", Direction.DESCENDING).whereGreaterThan("key", new Int32Value(-1));

    assertSDKQueryResultsConsistentWithBackend(
        randomColl, orderedQuery, docs, Arrays.asList("c", "b"));

    orderedQuery =
        randomColl.orderBy("key", Direction.DESCENDING).whereNotEqualTo("key", new Int32Value(1));

    assertSDKQueryResultsConsistentWithBackend(
        randomColl, orderedQuery, docs, Arrays.asList("c", "a"));
  }

  @Test
  public void filterAndOrderDecimal128() throws Exception {
    Map<String, Map<String, Object>> docs =
        map(
            "a",
            map("key", new Decimal128Value("-1.2e3")),
            "b",
            map("key", new Decimal128Value("0")),
            "c",
            map("key", new Decimal128Value("1.2e3")),
            "d",
            map("key", new Decimal128Value("NaN")),
            "e",
            map("key", new Decimal128Value("-Infinity")),
            "f",
            map("key", new Decimal128Value("Infinity")));
    CollectionReference randomColl = testCollectionWithDocsOnNightly(docs);

    Query orderedQuery =
        randomColl
            .orderBy("key", Direction.DESCENDING)
            .whereGreaterThan("key", new Decimal128Value("-1.2e3"));
    assertSDKQueryResultsConsistentWithBackend(
        randomColl, orderedQuery, docs, Arrays.asList("f", "c", "b"));

    orderedQuery =
        randomColl
            .orderBy("key", Direction.DESCENDING)
            .whereGreaterThan("key", new Decimal128Value("-1.2e-3"));
    assertSDKQueryResultsConsistentWithBackend(
        randomColl, orderedQuery, docs, Arrays.asList("f", "c", "b"));

    orderedQuery =
        randomColl
            .orderBy("key", Direction.DESCENDING)
            .whereNotEqualTo("key", new Decimal128Value("0.0"));
    assertSDKQueryResultsConsistentWithBackend(
        randomColl, orderedQuery, docs, Arrays.asList("f", "c", "a", "e", "d"));

    orderedQuery = randomColl.whereNotEqualTo("key", new Decimal128Value("NaN"));
    assertSDKQueryResultsConsistentWithBackend(
        randomColl, orderedQuery, docs, Arrays.asList("e", "a", "b", "c", "f"));

    orderedQuery =
        randomColl
            .orderBy("key", Direction.DESCENDING)
            .whereEqualTo("key", new Decimal128Value("1.2e3"));
    assertSDKQueryResultsConsistentWithBackend(randomColl, orderedQuery, docs, Arrays.asList("c"));

    orderedQuery =
        randomColl
            .orderBy("key", Direction.DESCENDING)
            .whereNotEqualTo("key", new Decimal128Value("1.2e3"));
    assertSDKQueryResultsConsistentWithBackend(
        randomColl, orderedQuery, docs, Arrays.asList("f", "b", "a", "e", "d"));

    // Note: server is sending NaN incorrectly, but the SDK NotInFilter.matches gracefully handles
    // it and removes the incorrect doc "d".
    orderedQuery =
        randomColl
            .orderBy("key", Direction.DESCENDING)
            .whereNotIn(
                "key",
                Arrays.asList(
                    new Decimal128Value("1.2e3"),
                    new Decimal128Value("Infinity"),
                    new Decimal128Value("NaN")));
    assertSDKQueryResultsConsistentWithBackend(
        randomColl, orderedQuery, docs, Arrays.asList("b", "a", "e"));
  }

  @Test
  public void filterAndOrderMinKey() throws Exception {
    Map<String, Map<String, Object>> docs =
        map(
            "a", map("key", MinKey.instance()),
            "b", map("key", MinKey.instance()),
            "c", map("key", null),
            "d", map("key", 1L),
            "e", map("key", MaxKey.instance()));
    CollectionReference randomColl = testCollectionWithDocsOnNightly(docs);

    Query query =
        randomColl
            .orderBy(
                "key",
                Direction
                    .DESCENDING) // minKeys are equal, would sort by documentId as secondary order
            .whereEqualTo("key", MinKey.instance());

    assertSDKQueryResultsConsistentWithBackend(randomColl, query, docs, Arrays.asList("b", "a"));

    query = randomColl.whereNotEqualTo("key", MinKey.instance());
    assertSDKQueryResultsConsistentWithBackend(randomColl, query, docs, Arrays.asList("d", "e"));

    query = randomColl.whereGreaterThanOrEqualTo("key", MinKey.instance());
    assertSDKQueryResultsConsistentWithBackend(randomColl, query, docs, Arrays.asList("a", "b"));

    query = randomColl.whereLessThanOrEqualTo("key", MinKey.instance());
    assertSDKQueryResultsConsistentWithBackend(randomColl, query, docs, Arrays.asList("a", "b"));

    query = randomColl.whereGreaterThan("key", MinKey.instance());
    assertSDKQueryResultsConsistentWithBackend(randomColl, query, docs, Arrays.asList());

    query = randomColl.whereGreaterThan("key", MinKey.instance());
    assertSDKQueryResultsConsistentWithBackend(randomColl, query, docs, Arrays.asList());
  }

  @Test
  public void filterAndOrderMaxKey() throws Exception {
    Map<String, Map<String, Object>> docs =
        map(
            "a", map("key", MinKey.instance()),
            "b", map("key", 1L),
            "c", map("key", MaxKey.instance()),
            "d", map("key", MaxKey.instance()),
            "e", map("key", null));
    CollectionReference randomColl = testCollectionWithDocsOnNightly(docs);

    Query query =
        randomColl
            .orderBy(
                "key",
                Direction
                    .DESCENDING) // maxKeys are equal, would sort by documentId as secondary order
            .whereEqualTo("key", MaxKey.instance());

    assertSDKQueryResultsConsistentWithBackend(randomColl, query, docs, Arrays.asList("d", "c"));

    query = randomColl.whereNotEqualTo("key", MaxKey.instance());
    assertSDKQueryResultsConsistentWithBackend(randomColl, query, docs, Arrays.asList("a", "b"));

    query = randomColl.whereGreaterThanOrEqualTo("key", MaxKey.instance());
    assertSDKQueryResultsConsistentWithBackend(randomColl, query, docs, Arrays.asList("c", "d"));

    query = randomColl.whereLessThanOrEqualTo("key", MaxKey.instance());
    assertSDKQueryResultsConsistentWithBackend(randomColl, query, docs, Arrays.asList("c", "d"));

    query = randomColl.whereLessThan("key", MaxKey.instance());
    assertSDKQueryResultsConsistentWithBackend(randomColl, query, docs, Arrays.asList());

    query = randomColl.whereGreaterThan("key", MaxKey.instance());
    assertSDKQueryResultsConsistentWithBackend(randomColl, query, docs, Arrays.asList());
  }

  @Test
  public void filterNullValueWithBsonTypes() throws Exception {
    Map<String, Map<String, Object>> docs =
        map(
            "a", map("key", MinKey.instance()),
            "b", map("key", null),
            "c", map("key", null),
            "d", map("key", 1L),
            "e", map("key", MaxKey.instance()));
    CollectionReference randomColl = testCollectionWithDocsOnNightly(docs);

    Query query = randomColl.whereEqualTo("key", null);
    assertSDKQueryResultsConsistentWithBackend(randomColl, query, docs, Arrays.asList("b", "c"));

    query = randomColl.whereNotEqualTo("key", null);
    assertSDKQueryResultsConsistentWithBackend(
        randomColl, query, docs, Arrays.asList("a", "d", "e"));
  }

  @Test
  public void filterAndOrderNumericalValues() throws Exception {
    Map<String, Map<String, Object>> docs =
        map(
            "a",
            map("key", new Decimal128Value("-1.2e3")), // -1200
            "b",
            map("key", new Int32Value(0)),
            "c",
            map("key", new Decimal128Value("1")),
            "d",
            map("key", new Int32Value(1)),
            "e",
            map("key", 1L),
            "f",
            map("key", 1.0),
            "g",
            map("key", new Decimal128Value("1.2e-3")), // 0.0012
            "h",
            map("key", new Int32Value(2)),
            "i",
            map("key", new Decimal128Value("NaN")),
            "j",
            map("key", new Decimal128Value("-Infinity")),
            "k",
            map("key", NaN),
            "l",
            map("key", POSITIVE_INFINITY));
    CollectionReference randomColl = testCollectionWithDocsOnNightly(docs);

    Query orderedQuery = randomColl.orderBy("key", Direction.DESCENDING);
    assertSDKQueryResultsConsistentWithBackend(
        randomColl,
        orderedQuery,
        docs,
        Arrays.asList(
            "l", // Infinity
            "h", // 2
            "f", // 1.0
            "e", // 1
            "d", // 1
            "c", // 1
            "g", // 0.0012
            "b", // 0
            "a", // -1200
            "j", // -Infinity
            "k", // NaN
            "i" // NaN
            ));

    orderedQuery =
        randomColl
            .orderBy("key", Direction.DESCENDING)
            .whereNotEqualTo("key", new Decimal128Value("1.0"));
    assertSDKQueryResultsConsistentWithBackend(
        randomColl, orderedQuery, docs, Arrays.asList("l", "h", "g", "b", "a", "j", "k", "i"));

    orderedQuery = randomColl.orderBy("key", Direction.DESCENDING).whereEqualTo("key", 1);
    assertSDKQueryResultsConsistentWithBackend(
        randomColl, orderedQuery, docs, Arrays.asList("f", "e", "d", "c"));
  }

  @Test
  public void decimal128ValuesWithNo2sComplementRepresentation() throws Exception {
    // For decimal128 values with no 2's complement representation, it is considered not equal to
    // a double with the same value, e.g, 1.1.
    Map<String, Map<String, Object>> docs =
        map(
            "a",
            map("key", new Decimal128Value("-1.1e-3")), // -0.0011
            "b",
            map("key", new Decimal128Value("1.1")),
            "c",
            map("key", 1.1),
            "d",
            map("key", 1.0),
            "e",
            map("key", new Decimal128Value("1.1e-3")) // 0.0011
            );
    CollectionReference randomColl = testCollectionWithDocsOnNightly(docs);

    Query orderedQuery = randomColl.whereEqualTo("key", new Decimal128Value("1.1"));
    assertSDKQueryResultsConsistentWithBackend(randomColl, orderedQuery, docs, Arrays.asList("b"));

    orderedQuery = randomColl.whereNotEqualTo("key", new Decimal128Value("1.1"));
    assertSDKQueryResultsConsistentWithBackend(
        randomColl, orderedQuery, docs, Arrays.asList("a", "e", "d", "c"));

    orderedQuery = randomColl.whereEqualTo("key", 1.1);
    assertSDKQueryResultsConsistentWithBackend(randomColl, orderedQuery, docs, Arrays.asList("c"));

    orderedQuery = randomColl.whereNotEqualTo("key", 1.1);
    assertSDKQueryResultsConsistentWithBackend(
        randomColl, orderedQuery, docs, Arrays.asList("a", "e", "d", "b"));
  }

  @Test
  public void orderBsonTypesTogether() throws Exception {
    Map<String, Map<String, Object>> docs =
        map(
            "bsonObjectId1",
            map("key", new BsonObjectId("507f191e810c19729de860ea")),
            "bsonObjectId2",
            map("key", new BsonObjectId("507f191e810c19729de860eb")),
            "bsonObjectId3",
            map("key", new BsonObjectId("407f191e810c19729de860ea")),
            "regex1",
            map("key", new RegexValue("^bar", "m")),
            "regex2",
            map("key", new RegexValue("^bar", "i")),
            "regex3",
            map("key", new RegexValue("^baz", "i")),
            "bsonTimestamp1",
            map("key", new BsonTimestamp(2, 0)),
            "bsonTimestamp2",
            map("key", new BsonTimestamp(1, 2)),
            "bsonTimestamp3",
            map("key", new BsonTimestamp(1, 1)),
            "bsonBinary1",
            map("key", BsonBinaryData.fromBytes(1, new byte[] {1, 2, 3})),
            "bsonBinary2",
            map("key", BsonBinaryData.fromBytes(1, new byte[] {1, 2, 4})),
            "bsonBinary3",
            map("key", BsonBinaryData.fromBytes(2, new byte[] {1, 2, 2})),
            "int32Value1",
            map("key", new Int32Value(-1)),
            "int32Value2",
            map("key", new Int32Value(1)),
            "int32Value3",
            map("key", new Int32Value(0)),
            "decimal128Value1",
            map("key", new Decimal128Value("-1.2e3")),
            "decimal128Value2",
            map("key", new Decimal128Value("-0.0")),
            "decimal128Value3",
            map("key", new Decimal128Value("1.2e3")),
            "minKey1",
            map("key", MinKey.instance()),
            "minKey2",
            map("key", MinKey.instance()),
            "maxKey1",
            map("key", MaxKey.instance()),
            "maxKey2",
            map("key", MaxKey.instance()));
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
            // Int32Value and Decimal128Value are sorted together
            "decimal128Value3",
            "int32Value2",
            // Int32Value of 0 equals to Decimal128Value of 0, and falls to document key as second
            // order
            "int32Value3",
            "decimal128Value2",
            "int32Value1",
            "decimal128Value1",
            "minKey2",
            "minKey1");

    assertSDKQueryResultsConsistentWithBackend(randomColl, orderedQuery, docs, expectedDocs);
  }

  @Test
  public void canRunTransactionsOnDocumentsWithBsonTypes() throws Exception {
    Map<String, Map<String, Object>> docs =
        map(
            "a", map("key", new BsonObjectId("507f191e810c19729de860ea")),
            "b", map("key", new RegexValue("^foo", "i")),
            "c", map("key", BsonBinaryData.fromBytes(1, new byte[] {1, 2, 3})));
    CollectionReference randomColl = testCollectionWithDocsOnNightly(docs);

    waitFor(
        randomColl.firestore.runTransaction(
            transaction -> {
              DocumentSnapshot docSnap = transaction.get(randomColl.document("a"));
              assertEquals(
                  docSnap.getBsonObjectId("key"), new BsonObjectId("507f191e810c19729de860ea"));
              transaction.update(randomColl.document("b"), "key", new RegexValue("^bar", "i"));
              transaction.delete(randomColl.document("c"));
              return null;
            }));

    QuerySnapshot getSnapshot = waitFor(randomColl.get());

    List<String> getSnapshotDocIds =
        getSnapshot.getDocuments().stream().map(ds -> ds.getId()).collect(Collectors.toList());

    assertTrue(getSnapshotDocIds.equals(Arrays.asList("a", "b")));
    assertEquals(
        getSnapshot.getDocuments().get(0).getBsonObjectId("key"),
        new BsonObjectId("507f191e810c19729de860ea"));
    assertEquals(
        getSnapshot.getDocuments().get(1).getRegexValue("key"), new RegexValue("^bar", "i"));
  }
}
